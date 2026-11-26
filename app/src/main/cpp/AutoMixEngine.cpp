#include "AutoMixEngine.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <thread>

namespace neonsong::audio {

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// -45 dBFS threshold in linear float amplitude
static constexpr float kSilenceThresholdLinear = 0.00562341325F;

AutoMixEngine::AutoMixEngine() noexcept {
    configure(48000.0);
}

void AutoMixEngine::configure(double sampleRate) noexcept {
    std::lock_guard<std::mutex> lock(configureMutex_);
    isConfiguring_.store(true, std::memory_order_release);

    while (processingCount_.load(std::memory_order_acquire) > 0) {
        std::this_thread::yield();
    }

    if (!std::isfinite(sampleRate) || sampleRate < 8000.0 || sampleRate > 384000.0) {
        sampleRate = 48000.0;
    }
    sampleRate_ = sampleRate;

    deckAFilter_.configure(sampleRate_, 160.0);
    deckBFilter_.configure(sampleRate_, 160.0);
    deckBStretcher_.configure(sampleRate_, 2);
    deckADetector_.configure(sampleRate_);
    deckAEcho_.configure(sampleRate_, 120.0F);

    const std::size_t ringFrames = static_cast<std::size_t>(sampleRate_ * 30.0);
    {
        std::lock_guard<std::mutex> deckLock(deckBMutex_);
        deckBDetector_.configure(sampleRate_);
        deckBRingBuffer_.assign(ringFrames * 2U, 0.0F);
        deckBReadCounter_.store(0, std::memory_order_relaxed);
        deckBWriteCounter_.store(0, std::memory_order_relaxed);
        deckBLeadingTrimFrames_.store(0, std::memory_order_relaxed);
        deckBTrimmedCount_ = 0;
        deckBMaxTrimFrames_ = static_cast<std::size_t>(sampleRate_ * 5.0);
        deckBTrimCalculated_ = false;
        deckBProducerResetRequested_.store(false, std::memory_order_relaxed);
    }

    deckBScratch_.assign(kMaxScratchInputFrames * 2U, 0.0F);
    deckBStretchedScratch_.assign(kMaxBlockFrames * 2U, 0.0F);
    deckAFilterScratch_.assign(kMaxBlockFrames * 2U, 0.0F);
    deckBFilterScratch_.assign(kMaxBlockFrames * 2U, 0.0F);

    resetDspState();
    state_.store(AutoMixState::Idle, std::memory_order_release);
    resetRequested_.store(false, std::memory_order_release);
    pendingFadeBars_.store(0, std::memory_order_release);
    pendingFadeDurationMs_.store(-1, std::memory_order_release);

    isConfiguring_.store(false, std::memory_order_release);
}

void AutoMixEngine::setEnabled(bool enabled) noexcept {
    enabled_.store(enabled, std::memory_order_release);
    if (!enabled) {
        reset();
    }
}

bool AutoMixEngine::isEnabled() const noexcept {
    return enabled_.load(std::memory_order_acquire);
}

void AutoMixEngine::setBarMode(int bars) noexcept {
    barMode_.store((bars >= 16) ? 16 : 8, std::memory_order_release);
}

int AutoMixEngine::getBarMode() const noexcept {
    return barMode_.load(std::memory_order_acquire);
}

void AutoMixEngine::setCurveType(int curveType) noexcept {
    curveType_.store(std::clamp(curveType, 0, 3), std::memory_order_release);
}

void AutoMixEngine::reset() noexcept {
    state_.store(AutoMixState::Idle, std::memory_order_release);
    transitionProgress_.store(0.0F, std::memory_order_release);
    pendingFadeBars_.store(0, std::memory_order_release);
    pendingFadeDurationMs_.store(-1, std::memory_order_release);
    resetRequested_.store(true, std::memory_order_release);
}

void AutoMixEngine::resetDspState() noexcept {
    deckAFilter_.reset();
    deckBFilter_.reset();
    deckBStretcher_.reset();
    deckADetector_.reset();
    deckAEcho_.reset();
    deckAEcho_.setEnabled(false);
    deckABpm_.store(120.0F, std::memory_order_release);
    deckBBpm_.store(120.0F, std::memory_order_release);

    // Strictly lock-free SPSC consumer flush (never lock deckBMutex_ on RT thread).
    const auto write = deckBWriteCounter_.load(std::memory_order_acquire);
    deckBReadCounter_.store(write, std::memory_order_release);
    deckBProducerResetRequested_.store(true, std::memory_order_release);

    transitionTotalFrames_ = 0;
    transitionCurrentFrame_ = 0;
    transitionProgress_.store(0.0F, std::memory_order_release);
    bassSwapBlend_ = 0.0F;
    prevDeckBGain_ = 0.0F;
    deckBStretchActive_ = false;
    deckAFilterWarmed_ = false;

    baselineEnergy_ = 0.0F;
    shortTermEnergy_ = 0.0F;
    energyHistoryFrames_ = 0;
}

std::size_t AutoMixEngine::writeDeckBPcm(const float* stereoPcm, std::size_t frames) noexcept {
    if (stereoPcm == nullptr || frames == 0) return 0;

    std::lock_guard<std::mutex> lock(deckBMutex_);

    if (deckBProducerResetRequested_.exchange(false, std::memory_order_acq_rel)) {
        deckBDetector_.reset();
        deckBLeadingTrimFrames_.store(0, std::memory_order_release);
        deckBTrimmedCount_ = 0;
        deckBTrimCalculated_ = false;
    }

    const std::size_t totalCapacityFrames = deckBRingBuffer_.size() / 2U;
    if (totalCapacityFrames == 0) return 0;

    // Trim leading silence BEFORE writing into the ring so deckBReadCounter_ stays SPSC-consumer-only
    // and silence longer than 0.5 s (up to 5 s) is trimmed accurately.
    std::size_t skippedFrames = 0;
    if (!deckBTrimCalculated_) {
        const std::size_t remainingTrimBudget = (deckBMaxTrimFrames_ > deckBTrimmedCount_)
            ? (deckBMaxTrimFrames_ - deckBTrimmedCount_)
            : 0U;
        const std::size_t scanLimit = std::min(frames, remainingTrimBudget);
        while (skippedFrames < scanLimit) {
            const float l = std::abs(stereoPcm[skippedFrames * 2U]);
            const float r = std::abs(stereoPcm[skippedFrames * 2U + 1U]);
            if ((std::isfinite(l) && l >= kSilenceThresholdLinear) ||
                (std::isfinite(r) && r >= kSilenceThresholdLinear)) {
                deckBTrimCalculated_ = true;
                break;
            }
            ++skippedFrames;
        }
        deckBTrimmedCount_ += skippedFrames;
        deckBLeadingTrimFrames_.store(deckBTrimmedCount_, std::memory_order_release);
        if (deckBTrimmedCount_ >= deckBMaxTrimFrames_) {
            deckBTrimCalculated_ = true;
        }
        stereoPcm += skippedFrames * 2U;
        frames -= skippedFrames;
        if (frames == 0) {
            return skippedFrames;
        }
    }

    const std::uint64_t writeCounter = deckBWriteCounter_.load(std::memory_order_relaxed);
    const std::uint64_t readCounter = deckBReadCounter_.load(std::memory_order_acquire);
    const std::size_t usedFrames = (writeCounter > readCounter)
        ? std::min<std::size_t>(static_cast<std::size_t>(writeCounter - readCounter), totalCapacityFrames)
        : 0U;
    const std::size_t framesToWrite = std::min(frames, totalCapacityFrames - usedFrames);
    if (framesToWrite == 0) return skippedFrames;

    const std::size_t startIdx = static_cast<std::size_t>(writeCounter % totalCapacityFrames);
    const std::size_t firstChunk = std::min(framesToWrite, totalCapacityFrames - startIdx);
    const std::size_t secondChunk = framesToWrite - firstChunk;

    std::memcpy(&deckBRingBuffer_[startIdx * 2U], stereoPcm, firstChunk * 2U * sizeof(float));
    if (secondChunk > 0) {
        std::memcpy(deckBRingBuffer_.data(), stereoPcm + firstChunk * 2U, secondChunk * 2U * sizeof(float));
    }
    deckBWriteCounter_.store(writeCounter + framesToWrite, std::memory_order_release);

    deckBDetector_.process(stereoPcm, framesToWrite);
    deckBBpm_.store(deckBDetector_.getResult().bpm, std::memory_order_release);

    AutoMixState expected = AutoMixState::Idle;
    (void) state_.compare_exchange_strong(
        expected, AutoMixState::MonitoringOutro, std::memory_order_acq_rel);

    return skippedFrames + framesToWrite;
}

void AutoMixEngine::triggerFadeNow() noexcept {
    // 4-bar fast blend on user Next button press
    triggerFadeBars(4);
}

void AutoMixEngine::triggerFadeBars(int bars) noexcept {
    pendingFadeBars_.store(std::clamp(bars, 4, 16), std::memory_order_release);
}

void AutoMixEngine::triggerFadeDurationMs(std::int64_t durationMs) noexcept {
    pendingFadeDurationMs_.store(std::max<std::int64_t>(1, durationMs), std::memory_order_release);
}

void AutoMixEngine::startTransition(AutoMixState mode, int bars, double durationOverrideSeconds) noexcept {
    state_.store(mode, std::memory_order_release);

    float bpmA = deckABpm_.load(std::memory_order_acquire);
    if (!std::isfinite(bpmA) || bpmA < 50.0F || bpmA > 220.0F) bpmA = 120.0F;

    float bpmB = deckBBpm_.load(std::memory_order_acquire);
    if (!std::isfinite(bpmB) || bpmB < 50.0F || bpmB > 220.0F) bpmB = bpmA;

    // Transition duration = bars * 4 * (60 / bpmA) seconds
    const double barSeconds = 4.0 * (60.0 / static_cast<double>(bpmA));
    const double transitionSeconds = (std::isfinite(durationOverrideSeconds) && durationOverrideSeconds > 0.0)
        ? durationOverrideSeconds
        : std::max(2.0, static_cast<double>(bars) * barSeconds);
    transitionTotalFrames_ = std::max<std::size_t>(
        1U, static_cast<std::size_t>(std::llround(transitionSeconds * sampleRate_)));
    transitionCurrentFrame_ = 0;
    transitionProgress_.store(0.0F, std::memory_order_release);
    bassSwapBlend_ = 0.0F;
    prevDeckBGain_ = 0.0F;
    deckAFilterWarmed_ = false;

    // Tempo sync if within +/- 8% (including half/double time)
    float ratio = 1.0F;
    const float diffDirect = std::abs(bpmB - bpmA) / bpmA;
    const float diffHalf = std::abs(bpmB * 2.0F - bpmA) / bpmA;
    const float diffDouble = std::abs(bpmB * 0.5F - bpmA) / bpmA;

    if (diffDirect <= 0.08F) {
        ratio = bpmA / bpmB;
    } else if (diffHalf <= 0.08F) {
        ratio = bpmA / (bpmB * 2.0F);
    } else if (diffDouble <= 0.08F) {
        ratio = bpmA / (bpmB * 0.5F);
    }

    deckBStretcher_.reset();
    deckBStretcher_.setTimeRatio(ratio);
    deckBStretchActive_ = std::abs(ratio - 1.0F) > 0.001F;

    // Prepare filters and echo
    deckAFilter_.reset();
    deckBFilter_.reset();
    deckAEcho_.setTempo(bpmA);
    deckAEcho_.reset();
    deckAEcho_.setEnabled(true);
}

void AutoMixEngine::readDeckBFrames(float* dstStereo, std::size_t frames) noexcept {
    const std::size_t totalCapacityFrames = deckBRingBuffer_.size() / 2U;
    if (totalCapacityFrames == 0 || frames == 0) {
        if (frames > 0) std::memset(dstStereo, 0, frames * 2U * sizeof(float));
        return;
    }

    auto readRing = [&](float* target, std::size_t requested) noexcept -> std::size_t {
        const std::uint64_t readCounter = deckBReadCounter_.load(std::memory_order_relaxed);
        const std::uint64_t writeCounter = deckBWriteCounter_.load(std::memory_order_acquire);
        const std::size_t avail = (writeCounter > readCounter)
            ? std::min<std::size_t>(static_cast<std::size_t>(writeCounter - readCounter), totalCapacityFrames)
            : 0U;
        const std::size_t count = std::min(requested, avail);
        if (count == 0) return 0;

        const std::size_t startIdx = static_cast<std::size_t>(readCounter % totalCapacityFrames);
        const std::size_t firstChunk = std::min(count, totalCapacityFrames - startIdx);
        const std::size_t secondChunk = count - firstChunk;
        std::memcpy(target, &deckBRingBuffer_[startIdx * 2U], firstChunk * 2U * sizeof(float));
        if (secondChunk > 0) {
            std::memcpy(target + firstChunk * 2U, deckBRingBuffer_.data(), secondChunk * 2U * sizeof(float));
        }
        deckBReadCounter_.store(readCounter + count, std::memory_order_release);
        return count;
    };

    if (!deckBStretchActive_) {
        const std::size_t got = readRing(dstStereo, frames);
        if (got < frames) {
            std::memset(dstStereo + got * 2U, 0, (frames - got) * 2U * sizeof(float));
        }
        return;
    }

    const std::size_t scratchCap = deckBScratch_.size() / 2U;
    const float ratio = deckBStretcher_.currentRatio();
    const std::uint64_t readNow = deckBReadCounter_.load(std::memory_order_relaxed);
    const std::uint64_t writeNow = deckBWriteCounter_.load(std::memory_order_acquire);
    const std::size_t ringAvail = (writeNow > readNow)
        ? std::min<std::size_t>(static_cast<std::size_t>(writeNow - readNow), totalCapacityFrames)
        : 0U;
    const std::size_t stretchDesired = static_cast<std::size_t>(
        std::ceil(static_cast<double>(frames) * ratio));
    // Prevent real-time ringbuffer starvation when Deck B AudioTrack clocks at 1.0x
    // while tempo sync tries to consume at ratio > 1.0x: fall back cleanly to 1.0x read
    // once headroom runs tight rather than padding silent dropouts.
    if (ratio > 1.01F && ringAvail < stretchDesired + kMaxBlockFrames) {
        const std::size_t got = readRing(dstStereo, frames);
        if (got < frames) {
            std::memset(dstStereo + got * 2U, 0, (frames - got) * 2U * sizeof(float));
        }
        return;
    }

    std::size_t produced = 0;
    for (int pass = 0; pass < 4 && produced < frames; ++pass) {
        const std::size_t remainingOut = frames - produced;
        const std::size_t desiredIn = std::clamp<std::size_t>(
            static_cast<std::size_t>(std::ceil(static_cast<double>(remainingOut) * ratio)),
            1U,
            scratchCap);
        const std::size_t gotIn = readRing(deckBScratch_.data(), desiredIn);
        if (gotIn == 0) {
            if (produced == 0) {
                produced += deckBStretcher_.flush(dstStereo, remainingOut);
            }
            break;
        }
        produced += deckBStretcher_.process(
            deckBScratch_.data(), gotIn, dstStereo + produced * 2U, remainingOut);
    }
    if (produced < frames) {
        std::memset(dstStereo + produced * 2U, 0, (frames - produced) * 2U * sizeof(float));
    }
}

void AutoMixEngine::process(float* deckAStereo, std::size_t frames) noexcept {
    if (deckAStereo == nullptr || frames == 0) return;

    if (!enabled_.load(std::memory_order_acquire)) {
        return;
    }
    processingCount_.fetch_add(1, std::memory_order_acq_rel);

    if (isConfiguring_.load(std::memory_order_acquire)) {
        processingCount_.fetch_sub(1, std::memory_order_acq_rel);
        return;
    }

    // 1. Check for requested DSP reset
    if (resetRequested_.exchange(false, std::memory_order_acq_rel)) {
        resetDspState();
    }

    // 2. Check for requested fade commands
    const int pendingBars = pendingFadeBars_.exchange(0, std::memory_order_acq_rel);
    const std::int64_t pendingMs = pendingFadeDurationMs_.exchange(-1, std::memory_order_acq_rel);
    if (pendingBars > 0 || pendingMs > 0) {
        const double pendingSec = (pendingMs > 0) ? (static_cast<double>(pendingMs) / 1000.0) : -1.0;
        startTransition(
            AutoMixState::FadingFast,
            pendingBars > 0 ? pendingBars : barMode_.load(std::memory_order_acquire),
            pendingSec);
    }

    for (std::size_t offset = 0; offset < frames; offset += kMaxBlockFrames) {
        processBlock(deckAStereo + offset * 2, std::min(kMaxBlockFrames, frames - offset));
    }

    processingCount_.fetch_sub(1, std::memory_order_acq_rel);
}

void AutoMixEngine::processBlock(float* deckAStereo, std::size_t frames) noexcept {
    // Monitor Deck A BPM
    deckADetector_.process(deckAStereo, frames);
    deckABpm_.store(deckADetector_.getResult().bpm, std::memory_order_release);

    AutoMixState currState = state_.load(std::memory_order_acquire);

    // Outro energy drop detector during MonitoringOutro
    if (currState == AutoMixState::MonitoringOutro) {
        double frameEnergySum = 0.0;
        for (std::size_t i = 0; i < frames; ++i) {
            const float l = deckAStereo[i * 2];
            const float r = deckAStereo[i * 2 + 1];
            frameEnergySum += (l * l + r * r) * 0.5;
        }
        float chunkEnergy = static_cast<float>(std::sqrt(frameEnergySum / static_cast<double>(frames)));
        if (!std::isfinite(chunkEnergy)) chunkEnergy = 0.0F;

        const double blockSec = static_cast<double>(frames) / sampleRate_;
        const float shortAlpha = static_cast<float>(1.0 - std::exp(-blockSec / 0.20));
        const float baseAlpha = static_cast<float>(1.0 - std::exp(-blockSec / 1.0));
        shortTermEnergy_ += shortAlpha * (chunkEnergy - shortTermEnergy_);
        const std::uint64_t readCounter = deckBReadCounter_.load(std::memory_order_acquire);
        const std::uint64_t writeCounter = deckBWriteCounter_.load(std::memory_order_acquire);
        const std::uint64_t minDeckBReady = static_cast<std::uint64_t>(sampleRate_ * 0.25);
        if (energyHistoryFrames_ < static_cast<std::size_t>(sampleRate_ * 10.0)) {
            baselineEnergy_ += baseAlpha * (chunkEnergy - baselineEnergy_);
            energyHistoryFrames_ += frames;
        } else if (baselineEnergy_ > kSilenceThresholdLinear &&
                   shortTermEnergy_ < baselineEnergy_ * 0.45F &&
                   writeCounter > readCounter &&
                   (writeCounter - readCounter) >= minDeckBReady) {
            startTransition(AutoMixState::FadingAuto, barMode_.load(std::memory_order_relaxed));
            currState = AutoMixState::FadingAuto;
        }
    }

    // If fading (FadingAuto or FadingFast)
    if (currState == AutoMixState::FadingAuto || currState == AutoMixState::FadingFast) {
        const float p = (transitionTotalFrames_ > 0)
            ? static_cast<float>(transitionCurrentFrame_) / static_cast<float>(transitionTotalFrames_)
            : 1.0F;
        transitionProgress_.store(std::clamp(p, 0.0F, 1.0F), std::memory_order_release);

        const float progress = transitionProgress_.load(std::memory_order_relaxed);
        const int curveType = curveType_.load(std::memory_order_acquire);

        float deckAGain = 1.0F;
        float deckBGain = 0.0F;
        switch (curveType) {
            case 1: {
                const float smooth = progress * progress * (3.0F - 2.0F * progress);
                deckAGain = 1.0F - smooth;
                deckBGain = smooth;
                break;
            }
            case 3: {
                deckAGain = progress >= 0.8F
                    ? std::clamp(1.0F - (progress - 0.8F) / 0.2F, 0.0F, 1.0F)
                    : 1.0F;
                deckBGain = progress >= 0.8F
                    ? 1.0F
                    : std::clamp(progress / 0.8F, 0.0F, 1.0F);
                break;
            }
            case 0:
            case 2:
            default:
                deckAGain = static_cast<float>(std::cos(progress * (M_PI * 0.5)));
                deckBGain = static_cast<float>(std::sin(progress * (M_PI * 0.5)));
                break;
        }

        readDeckBFrames(deckBStretchedScratch_.data(), frames);

        // Smooth Club Bass Swap (~60 ms handoff around midpoint, zero IIR pop).
        if (curveType == 2) {
            if (progress < 0.45F) {
                deckBFilter_.processHighPass(deckBStretchedScratch_.data(), frames);
            } else if (bassSwapBlend_ < 1.0F) {
                // Warm Deck A HPF starting at 0.45 so filter state is ready at 0.50.
                std::memcpy(deckAFilterScratch_.data(), deckAStereo, frames * 2U * sizeof(float));
                deckAFilter_.processHighPass(deckAFilterScratch_.data(), frames);
                deckAFilterWarmed_ = true;

                std::memcpy(deckBFilterScratch_.data(), deckBStretchedScratch_.data(), frames * 2U * sizeof(float));
                deckBFilter_.processHighPass(deckBFilterScratch_.data(), frames);

                if (progress >= 0.50F) {
                    const float step = static_cast<float>(1.0 / (sampleRate_ * 0.060));
                    for (std::size_t f = 0; f < frames; ++f) {
                        bassSwapBlend_ = std::min(1.0F, bassSwapBlend_ + step);
                        const float inv = 1.0F - bassSwapBlend_;
                        const std::size_t idx = f * 2U;
                        deckAStereo[idx]     = deckAStereo[idx]     * inv + deckAFilterScratch_[idx]     * bassSwapBlend_;
                        deckAStereo[idx + 1] = deckAStereo[idx + 1] * inv + deckAFilterScratch_[idx + 1] * bassSwapBlend_;
                        deckBStretchedScratch_[idx]     = deckBFilterScratch_[idx]     * inv + deckBStretchedScratch_[idx]     * bassSwapBlend_;
                        deckBStretchedScratch_[idx + 1] = deckBFilterScratch_[idx + 1] * inv + deckBStretchedScratch_[idx + 1] * bassSwapBlend_;
                    }
                } else {
                    std::memcpy(deckBStretchedScratch_.data(), deckBFilterScratch_.data(), frames * 2U * sizeof(float));
                }
            } else {
                deckAFilter_.processHighPass(deckAStereo, frames);
            }
        }

        const float echoSend = (progress >= 0.5F) ? ((progress - 0.5F) * 2.0F) : 0.0F;
        deckAEcho_.process(deckAStereo, frames, echoSend, deckAGain);

        // Per-frame linear ramp for Deck B gain eliminates zipper noise on fast curves.
        const float startGainB = prevDeckBGain_;
        const float gainStepB = (deckBGain - startGainB) / static_cast<float>(frames);
        for (std::size_t f = 0; f < frames; ++f) {
            const float g = startGainB + gainStepB * static_cast<float>(f + 1U);
            const std::size_t idx = f * 2U;
            deckAStereo[idx]     += deckBStretchedScratch_[idx]     * g;
            deckAStereo[idx + 1] += deckBStretchedScratch_[idx + 1] * g;
        }
        prevDeckBGain_ = deckBGain;

        transitionCurrentFrame_ += frames;

        // Fade complete!
        if (transitionCurrentFrame_ >= transitionTotalFrames_) {
            transitionProgress_.store(1.0F, std::memory_order_release);
            deckBStretcher_.startRecoveryRamp(1.0F, 1.5F);
            deckAEcho_.setEnabled(false);
            state_.store(AutoMixState::PostRestore, std::memory_order_release);
        }
    } else if (currState == AutoMixState::PostRestore) {
        // Keep playing Deck B at 100% gain through Deck A's sink until Kotlin calls cancelCrossfade() / reset()
        // so there is never a dead/silent gap between C++ fade completion and Kotlin player role swap.
        readDeckBFrames(deckAStereo, frames);
    }
}

AutoMixState AutoMixEngine::getState() const noexcept {
    return state_.load(std::memory_order_acquire);
}

float AutoMixEngine::getTransitionProgress() const noexcept {
    return transitionProgress_.load(std::memory_order_acquire);
}

std::size_t AutoMixEngine::getDeckBLeadingTrimFrames() const noexcept {
    return deckBLeadingTrimFrames_.load(std::memory_order_acquire);
}

float AutoMixEngine::getDeckABpm() const noexcept {
    return deckABpm_.load(std::memory_order_acquire);
}

float AutoMixEngine::getDeckBBpm() const noexcept {
    return deckBBpm_.load(std::memory_order_acquire);
}

} // namespace neonsong::audio
