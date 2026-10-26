#include "AutoMixEngine.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace lastwave::audio {

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// -45 dBFS threshold in linear float amplitude
static constexpr float kSilenceThresholdLinear = 0.00562341325F;

AutoMixEngine::AutoMixEngine() noexcept {
    configure(48000.0);
}

void AutoMixEngine::configure(double sampleRate) noexcept {
    if (sampleRate <= 0.0) sampleRate = 48000.0;
    sampleRate_ = sampleRate;

    deckAFilter_.configure(sampleRate_, 160.0);
    deckBFilter_.configure(sampleRate_, 160.0);
    deckBStretcher_.configure(sampleRate_, 2);
    deckADetector_.configure(sampleRate_);
    deckBDetector_.configure(sampleRate_);
    deckAEcho_.configure(sampleRate_, 120.0F);

    // Buffer capacity: 30 seconds of stereo Float32
    const std::size_t ringFrames = static_cast<std::size_t>(sampleRate_ * 30.0);
    deckBRingBuffer_.assign(ringFrames * 2, 0.0F);
    deckBReadCounter_.store(0, std::memory_order_relaxed);
    deckBWriteCounter_.store(0, std::memory_order_relaxed);

    // Large enough for the largest Media3 callback we accept. These buffers
    // are sized only during configure(), never from process().
    deckBScratch_.assign(8192 * 2, 0.0F);
    deckBStretchedScratch_.assign(8192 * 2, 0.0F);

    reset();
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

void AutoMixEngine::reset() noexcept {
    state_.store(AutoMixState::Idle, std::memory_order_release);
    std::fill(deckBRingBuffer_.begin(), deckBRingBuffer_.end(), 0.0F);
    deckBReadCounter_.store(0, std::memory_order_release);
    deckBWriteCounter_.store(0, std::memory_order_release);

    deckAFilter_.reset();
    deckBFilter_.reset();
    deckBStretcher_.reset();
    deckADetector_.reset();
    deckBDetector_.reset();
    deckAEcho_.reset();

    deckBLeadingTrimFrames_.store(0, std::memory_order_release);
    deckBTrimCalculated_ = false;
    transitionTotalFrames_ = 0;
    transitionCurrentFrame_ = 0;
    transitionProgress_.store(0.0F, std::memory_order_release);
    bassSwapped_ = false;

    baselineEnergy_ = 0.0F;
    shortTermEnergy_ = 0.0F;
    energyHistoryFrames_ = 0;
}

std::size_t AutoMixEngine::writeDeckBPcm(const float* stereoPcm, std::size_t frames) noexcept {
    if (stereoPcm == nullptr || frames == 0) return 0;

    const std::size_t totalCapacityFrames = deckBRingBuffer_.size() / 2;
    const std::uint64_t writeCounter = deckBWriteCounter_.load(std::memory_order_relaxed);
    const std::uint64_t readCounter = deckBReadCounter_.load(std::memory_order_acquire);
    const std::size_t availableFrames = static_cast<std::size_t>(writeCounter - readCounter);
    const std::size_t roomFrames = totalCapacityFrames > availableFrames
        ? totalCapacityFrames - availableFrames
        : 0;
    const std::size_t framesToWrite = std::min(frames, roomFrames);

    if (framesToWrite == 0) return 0;

    for (std::size_t f = 0; f < framesToWrite; ++f) {
        const std::size_t writePos = static_cast<std::size_t>(writeCounter + f) % totalCapacityFrames;
        deckBRingBuffer_[writePos * 2] = stereoPcm[f * 2];
        deckBRingBuffer_[writePos * 2 + 1] = stereoPcm[f * 2 + 1];
    }
    deckBWriteCounter_.store(writeCounter + framesToWrite, std::memory_order_release);

    // Feed Deck B BPM detector
    deckBDetector_.process(stereoPcm, framesToWrite);

    // Calculate leading silence trim on first chunk
    if (!deckBTrimCalculated_ && availableFrames + framesToWrite >= static_cast<std::size_t>(sampleRate_ * 0.5)) {
        trimDeckBLeadingSilence();
    }

    AutoMixState curr = state_.load(std::memory_order_acquire);
    if (curr == AutoMixState::Idle) {
        state_.store(AutoMixState::MonitoringOutro, std::memory_order_release);
    }

    return framesToWrite;
}

void AutoMixEngine::trimDeckBLeadingSilence() noexcept {
    const std::size_t totalCapacityFrames = deckBRingBuffer_.size() / 2;
    const std::uint64_t readCounter = deckBReadCounter_.load(std::memory_order_acquire);
    const std::uint64_t writeCounter = deckBWriteCounter_.load(std::memory_order_acquire);
    const std::size_t availableFrames = static_cast<std::size_t>(writeCounter - readCounter);
    const std::size_t searchLimit = std::min(availableFrames, static_cast<std::size_t>(sampleRate_ * 5.0)); // up to 5s

    std::size_t trimFrames = 0;
    for (std::size_t f = 0; f < searchLimit; ++f) {
        const std::size_t idx = static_cast<std::size_t>(readCounter + f) % totalCapacityFrames;
        const float l = std::abs(deckBRingBuffer_[idx * 2]);
        const float r = std::abs(deckBRingBuffer_[idx * 2 + 1]);
        if (l >= kSilenceThresholdLinear || r >= kSilenceThresholdLinear) {
            trimFrames = f;
            break;
        }
    }

    if (trimFrames > 0 && trimFrames < availableFrames) {
        deckBReadCounter_.store(readCounter + trimFrames, std::memory_order_release);
        deckBLeadingTrimFrames_.store(trimFrames, std::memory_order_release);
    }
    deckBTrimCalculated_ = true;
}

void AutoMixEngine::triggerFadeNow() noexcept {
    // 4-bar fast blend on user Next button press
    triggerFadeBars(4);
}

void AutoMixEngine::triggerFadeBars(int bars) noexcept {
    startTransition(AutoMixState::FadingFast, std::clamp(bars, 4, 16));
}

void AutoMixEngine::triggerFadeDurationMs(std::int64_t durationMs) noexcept {
    const double seconds = static_cast<double>(std::max<std::int64_t>(1, durationMs)) / 1000.0;
    startTransition(AutoMixState::FadingFast, barMode_.load(std::memory_order_acquire), seconds);
}

void AutoMixEngine::startTransition(AutoMixState mode, int bars, double durationOverrideSeconds) noexcept {
    state_.store(mode, std::memory_order_release);

    float bpmA = deckADetector_.getResult().bpm;
    if (bpmA < 50.0F || bpmA > 220.0F) bpmA = 120.0F;

    float bpmB = deckBDetector_.getResult().bpm;
    if (bpmB < 50.0F || bpmB > 220.0F) bpmB = bpmA;

    // Transition duration = bars * 4 * (60 / bpmA) seconds
    const double barSeconds = 4.0 * (60.0 / static_cast<double>(bpmA));
    const double transitionSeconds = durationOverrideSeconds > 0.0
        ? durationOverrideSeconds
        : std::max(2.0, static_cast<double>(bars) * barSeconds);
    transitionTotalFrames_ = static_cast<std::size_t>(std::round(transitionSeconds * sampleRate_));
    transitionCurrentFrame_ = 0;
    transitionProgress_.store(0.0F, std::memory_order_release);
    bassSwapped_ = false;

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

    deckBStretcher_.setTimeRatio(ratio);

    // Prepare filters and echo
    deckAFilter_.reset();
    deckBFilter_.reset();
    deckAEcho_.setTempo(bpmA);
    deckAEcho_.reset();
    deckAEcho_.setEnabled(true);
}

void AutoMixEngine::process(float* deckAStereo, std::size_t frames) noexcept {
    if (deckAStereo == nullptr || frames == 0) return;

    if (!enabled_.load(std::memory_order_acquire)) {
        return;
    }

    constexpr std::size_t kMaxBlockFrames = 8192;
    for (std::size_t offset = 0; offset < frames; offset += kMaxBlockFrames) {
        processBlock(deckAStereo + offset * 2, std::min(kMaxBlockFrames, frames - offset));
    }
}

void AutoMixEngine::processBlock(float* deckAStereo, std::size_t frames) noexcept {
    // Monitor Deck A BPM
    deckADetector_.process(deckAStereo, frames);

    AutoMixState currState = state_.load(std::memory_order_acquire);

    // Outro energy drop detector during MonitoringOutro
    if (currState == AutoMixState::MonitoringOutro) {
        double frameEnergySum = 0.0;
        for (std::size_t i = 0; i < frames; ++i) {
            const float l = deckAStereo[i * 2];
            const float r = deckAStereo[i * 2 + 1];
            frameEnergySum += (l * l + r * r) * 0.5;
        }
        const float chunkEnergy = static_cast<float>(std::sqrt(frameEnergySum / frames));

        shortTermEnergy_ += 0.05F * (chunkEnergy - shortTermEnergy_);
        if (energyHistoryFrames_ < static_cast<std::size_t>(sampleRate_ * 10.0)) {
            baselineEnergy_ += 0.01F * (chunkEnergy - baselineEnergy_);
            energyHistoryFrames_ += frames;
        }

        if (energyHistoryFrames_ >= static_cast<std::size_t>(sampleRate_ * 3.0) &&
            baselineEnergy_ > 0.02F && shortTermEnergy_ < baselineEnergy_ * 0.35F) {
            startTransition(AutoMixState::FadingAuto, barMode_.load(std::memory_order_acquire));
            currState = state_.load(std::memory_order_acquire);
        }
    }

    // If fading (FadingAuto or FadingFast)
    if (currState == AutoMixState::FadingAuto || currState == AutoMixState::FadingFast) {
        const float p = (transitionTotalFrames_ > 0)
            ? static_cast<float>(transitionCurrentFrame_) / static_cast<float>(transitionTotalFrames_)
            : 1.0F;
        transitionProgress_.store(std::clamp(p, 0.0F, 1.0F), std::memory_order_release);

        // Midpoint bass-swap: occurs at 50% downbeat
        const float progress = transitionProgress_.load(std::memory_order_relaxed);
        if (progress >= 0.5F && !bassSwapped_) {
            bassSwapped_ = true;
        }

        const float deckAGain = static_cast<float>(std::cos(progress * (M_PI * 0.5)));
        const float deckBGain = static_cast<float>(std::sin(progress * (M_PI * 0.5)));

        // Read Deck B frames from prebuffer ring
        std::size_t actualDeckBFrames = 0;
        const std::size_t scratchCapacity = deckBScratch_.size() / 2;
        if (frames > scratchCapacity) return;
        const std::size_t totalCapacityFrames = deckBRingBuffer_.size() / 2;
        const std::uint64_t readCounter = deckBReadCounter_.load(std::memory_order_relaxed);
        const std::uint64_t writeCounter = deckBWriteCounter_.load(std::memory_order_acquire);
        const std::size_t availableFrames = static_cast<std::size_t>(writeCounter - readCounter);
        actualDeckBFrames = std::min(frames, availableFrames);
        for (std::size_t f = 0; f < actualDeckBFrames; ++f) {
            const std::size_t readPos = static_cast<std::size_t>(readCounter + f) % totalCapacityFrames;
            deckBScratch_[f * 2] = deckBRingBuffer_[readPos * 2];
            deckBScratch_[f * 2 + 1] = deckBRingBuffer_[readPos * 2 + 1];
        }
        deckBReadCounter_.store(readCounter + actualDeckBFrames, std::memory_order_release);
        if (actualDeckBFrames < frames) {
            std::fill(deckBScratch_.begin() + actualDeckBFrames * 2, deckBScratch_.begin() + frames * 2, 0.0F);
        }

        // Time-stretch Deck B if ratio != 1.0
        if (frames * 2 > deckBStretchedScratch_.size()) return;
        std::size_t stretchedFrames = deckBStretcher_.process(
            deckBScratch_.data(), frames, deckBStretchedScratch_.data(), frames * 2);
        if (stretchedFrames < frames) {
            std::copy(deckBScratch_.begin() + stretchedFrames * 2, deckBScratch_.begin() + frames * 2,
                      deckBStretchedScratch_.begin() + stretchedFrames * 2);
        }

        // Linkwitz-Riley Bass-Swap
        if (!bassSwapped_) {
            deckBFilter_.processHighPass(deckBStretchedScratch_.data(), frames);
        } else {
            deckAFilter_.processHighPass(deckAStereo, frames);
        }

        // Deck A Echo tail
        const float echoSend = (progress >= 0.5F)
            ? ((progress - 0.5F) * 2.0F)
            : 0.0F;
        deckAEcho_.process(deckAStereo, frames, echoSend, deckAGain);

        // Mix Deck B into deckAStereo
        for (std::size_t i = 0; i < frames * 2; ++i) {
            deckAStereo[i] += deckBStretchedScratch_[i] * deckBGain;
        }

        transitionCurrentFrame_ += frames;

        // Fade complete!
        if (transitionCurrentFrame_ >= transitionTotalFrames_) {
            transitionProgress_.store(1.0F, std::memory_order_release);
            deckBStretcher_.startRecoveryRamp(1.0F, 5.0F);
            deckAEcho_.setEnabled(false);
            state_.store(AutoMixState::PostRestore, std::memory_order_release);
        }
    } else if (currState == AutoMixState::PostRestore) {
        if (deckBStretcher_.currentRatio() == 1.0F) {
            state_.store(AutoMixState::Idle, std::memory_order_release);
        }
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
    return deckADetector_.getResult().bpm;
}

float AutoMixEngine::getDeckBBpm() const noexcept {
    return deckBDetector_.getResult().bpm;
}

} // namespace lastwave::audio
