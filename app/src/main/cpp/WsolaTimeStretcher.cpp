#include "WsolaTimeStretcher.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace neonsong::audio {

namespace {
constexpr double kPi = 3.1415926535897932384626433832795;
constexpr float kEnergyEpsilon = 1.0e-7F;
} // namespace

WsolaTimeStretcher::WsolaTimeStretcher() noexcept {
    configure(48000.0, 2);
}

void WsolaTimeStretcher::configure(double sampleRate, std::size_t channels) noexcept {
    if (sampleRate <= 0.0) sampleRate = 48000.0;
    sampleRate_ = sampleRate;
    channels_ = std::clamp<std::size_t>(channels, 1U, kMaxChannels);

    hannWindow_.resize(kWindowSize);
    for (std::size_t i = 0; i < kWindowSize; ++i) {
        // Periodic Hann window: w[n] + w[n + N/2] == 1.0 (Exact COLA for 50% overlap)
        hannWindow_[i] = static_cast<float>(
            0.5 * (1.0 - std::cos(2.0 * kPi * static_cast<double>(i) / static_cast<double>(kWindowSize))));
    }

    inputBuffer_.assign(kInputCapacityFrames * channels_, 0.0F);
    outputBuffer_.assign(kOutputCapacityFrames * channels_, 0.0F);
    prevTemplateMono_.assign(kWindowSize, 0.0F);
    monoSearchScratch_.assign(kWindowSize + kSearchWindow * 2U + 4U, 0.0F);

    reset();
}

void WsolaTimeStretcher::reset() noexcept {
    inputReadPos_ = 0;
    inputWritePos_ = 0;
    inputFramesAvailable_ = 0;
    outputReadPos_ = 0;
    outputWritePos_ = 0;
    outputFramesAvailable_ = 0;
    std::fill(prevTemplateMono_.begin(), prevTemplateMono_.end(), 0.0F);
    // Anchor nominal analysis position at kSearchWindow so the first hop
    // has a full [-kSearchWindow, +kSearchWindow] look-behind/look-ahead margin.
    nominalAnalysisPos_ = static_cast<double>(kSearchWindow);
    prevTemplateEnergy_ = 0.0F;
    hasPreviousTemplate_ = false;
    currentRatio_ = targetRatio_;
    rampActive_ = false;
}

void WsolaTimeStretcher::setTimeRatio(float ratio) noexcept {
    targetRatio_ = std::clamp(ratio, 0.25F, 3.0F);
    if (!rampActive_) {
        currentRatio_ = targetRatio_;
    }
}

void WsolaTimeStretcher::startRecoveryRamp(float targetRatio, float durationSeconds) noexcept {
    rampTarget_ = std::clamp(targetRatio, 0.25F, 3.0F);
    durationSeconds = std::max(0.05F, durationSeconds);
    const double totalHops = (durationSeconds * sampleRate_) / static_cast<double>(kSynthesisHop);
    rampStepPerHop_ = static_cast<float>((rampTarget_ - currentRatio_) / std::max(1.0, totalHops));
    rampActive_ = true;
}

void WsolaTimeStretcher::updateRamp() noexcept {
    if (!rampActive_) return;
    currentRatio_ += rampStepPerHop_;
    if ((rampStepPerHop_ > 0.0F && currentRatio_ >= rampTarget_) ||
        (rampStepPerHop_ < 0.0F && currentRatio_ <= rampTarget_)) {
        currentRatio_ = rampTarget_;
        targetRatio_ = rampTarget_;
        rampActive_ = false;
    }
}

float WsolaTimeStretcher::inputSample(std::size_t frame, std::size_t channel) const noexcept {
    const std::size_t capacity = kInputCapacityFrames;
    return inputBuffer_[((inputReadPos_ + frame) % capacity) * channels_ + channel];
}

float WsolaTimeStretcher::inputMonoMix(std::size_t frame) const noexcept {
    const std::size_t capacity = kInputCapacityFrames;
    const float* framePtr = &inputBuffer_[((inputReadPos_ + frame) % capacity) * channels_];
    if (channels_ == 1U) return framePtr[0];
    if (channels_ == 2U) return (framePtr[0] + framePtr[1]) * 0.5F;
    // For 5.1/7.1 multichannel, correlate on Front Left + Front Right + Center
    return (framePtr[0] + framePtr[1] + framePtr[2]) * 0.33333334F;
}

std::size_t WsolaTimeStretcher::findBestOffset(std::size_t nominalPos) noexcept {
    if (!hasPreviousTemplate_ || prevTemplateEnergy_ <= kEnergyEpsilon ||
        inputFramesAvailable_ < nominalPos + kWindowSize) {
        return std::min(nominalPos, inputFramesAvailable_ > kWindowSize ? inputFramesAvailable_ - kWindowSize : 0U);
    }

    const std::size_t minPos = (nominalPos >= kSearchWindow) ? (nominalPos - kSearchWindow) : 0U;
    const std::size_t maxAllowed = inputFramesAvailable_ - kWindowSize;
    const std::size_t clampedMax = std::min(nominalPos + kSearchWindow, maxAllowed);

    if (minPos >= clampedMax) return std::min(nominalPos, maxAllowed);

    const std::size_t searchSpan = (clampedMax - minPos) + kWindowSize;
    if (monoSearchScratch_.size() < searchSpan) {
        monoSearchScratch_.resize(searchSpan);
    }

    // 1. Linearize the circular buffer slice into contiguous L1-cache-friendly mono scratch
    for (std::size_t i = 0; i < searchSpan; ++i) {
        monoSearchScratch_[i] = inputMonoMix(minPos + i);
    }

    // 2. Compute decimated running energy for candidate 0 (step = 2)
    constexpr std::size_t kDecim = 2U;
    float runningCandEnergy = 0.0F;
    for (std::size_t i = 0; i < kWindowSize; i += kDecim) {
        const float s = monoSearchScratch_[i];
        runningCandEnergy += s * s;
    }

    const float* prevPtr = prevTemplateMono_.data();
    const std::size_t numOffsets = clampedMax - minPos;
    float bestNcc = -2.0F;
    std::size_t bestRelOffset = nominalPos - minPos;

    // 3. Coarse search (step = 2) with Normalized Cross-Correlation (NCC)
    // and small preference weight toward nominalPos to prevent random walk on silence.
    for (std::size_t rel = 0; rel <= numOffsets; rel += kDecim) {
        if (rel > 0) {
            const float outgoing = monoSearchScratch_[rel - kDecim];
            const float incoming = monoSearchScratch_[rel + kWindowSize - kDecim];
            runningCandEnergy = std::max(0.0F, runningCandEnergy - outgoing * outgoing + incoming * incoming);
        }

        float dot = 0.0F;
        const float* candPtr = monoSearchScratch_.data() + rel;
        for (std::size_t i = 0; i < kWindowSize; i += kDecim) {
            dot += prevPtr[i] * candPtr[i];
        }

        const float norm = std::sqrt(prevTemplateEnergy_ * runningCandEnergy + kEnergyEpsilon);
        const float distRatio = static_cast<float>(
            (minPos + rel > nominalPos) ? (minPos + rel - nominalPos) : (nominalPos - (minPos + rel))) /
            static_cast<float>(kSearchWindow);
        // Slight center bias (0.015) keeps timing tight when multiple pitch periods have equal correlation
        const float ncc = (dot / norm) - (0.015F * distRatio * distRatio);

        if (ncc > bestNcc) {
            bestNcc = ncc;
            bestRelOffset = rel;
        }
    }

    // 4. Fine +/- 1 sample refinement around the coarse winner (eliminates HF Comb Filtering)
    auto evaluateExactOffset = [&](std::size_t rel) noexcept -> float {
        float dot = 0.0F;
        float energy = 0.0F;
        const float* candPtr = monoSearchScratch_.data() + rel;
        for (std::size_t i = 0; i < kWindowSize; i += kDecim) {
            dot += prevPtr[i] * candPtr[i];
            energy += candPtr[i] * candPtr[i];
        }
        return dot / std::sqrt(prevTemplateEnergy_ * energy + kEnergyEpsilon);
    };

    float refinedBestScore = evaluateExactOffset(bestRelOffset);
    std::size_t refinedRelOffset = bestRelOffset;

    if (bestRelOffset > 0U) {
        const float leftScore = evaluateExactOffset(bestRelOffset - 1U);
        if (leftScore > refinedBestScore) {
            refinedBestScore = leftScore;
            refinedRelOffset = bestRelOffset - 1U;
        }
    }
    if (bestRelOffset + 1U <= numOffsets) {
        const float rightScore = evaluateExactOffset(bestRelOffset + 1U);
        if (rightScore > refinedBestScore) {
            refinedRelOffset = bestRelOffset + 1U;
        }
    }

    return minPos + refinedRelOffset;
}

void WsolaTimeStretcher::appendInput(const float* inputInterleaved, std::size_t inputFrames) noexcept {
    if (inputInterleaved == nullptr || inputFrames == 0) return;
    const std::size_t capacity = kInputCapacityFrames;
    const std::size_t framesToCopy = std::min(inputFrames, capacity - inputFramesAvailable_);
    for (std::size_t f = 0; f < framesToCopy; ++f) {
        const std::size_t dst = inputWritePos_ * channels_;
        const std::size_t src = f * channels_;
        for (std::size_t ch = 0; ch < channels_; ++ch) {
            inputBuffer_[dst + ch] = inputInterleaved[src + ch];
        }
        inputWritePos_ = (inputWritePos_ + 1U) % capacity;
    }
    inputFramesAvailable_ += framesToCopy;
}

void WsolaTimeStretcher::consumeInput(std::size_t frames) noexcept {
    const std::size_t consumed = std::min(frames, inputFramesAvailable_);
    inputReadPos_ = (inputReadPos_ + consumed) % kInputCapacityFrames;
    inputFramesAvailable_ -= consumed;
}

void WsolaTimeStretcher::drainOutput(
    float* outputInterleaved,
    std::size_t& framesWritten,
    std::size_t outputCapacityFrames,
    std::size_t keepFrames) noexcept {
    if (outputInterleaved == nullptr || framesWritten >= outputCapacityFrames) return;
    const std::size_t drainable = outputFramesAvailable_ > keepFrames
        ? outputFramesAvailable_ - keepFrames
        : 0U;
    const std::size_t count = std::min(drainable, outputCapacityFrames - framesWritten);
    const std::size_t capacity = kOutputCapacityFrames;
    for (std::size_t f = 0; f < count; ++f) {
        const std::size_t src = outputReadPos_ * channels_;
        const std::size_t dst = (framesWritten + f) * channels_;
        for (std::size_t ch = 0; ch < channels_; ++ch) {
            outputInterleaved[dst + ch] = outputBuffer_[src + ch];
            outputBuffer_[src + ch] = 0.0F;
        }
        outputReadPos_ = (outputReadPos_ + 1U) % capacity;
    }
    outputFramesAvailable_ -= count;
    outputWritePos_ = outputReadPos_;
    framesWritten += count;
}

std::size_t WsolaTimeStretcher::process(
    const float* inputInterleaved,
    std::size_t inputFrames,
    float* outputInterleaved,
    std::size_t outputCapacityFrames) noexcept {
    if (outputInterleaved == nullptr || outputCapacityFrames == 0) return 0;

    appendInput(inputInterleaved, inputFrames);

    // Require enough input to cover nominalAnalysisPos_ + kWindowSize + kSearchWindow
    std::size_t framesWritten = 0;
    while (true) {
        const std::size_t nominalPos = static_cast<std::size_t>(std::max(0.0, std::floor(nominalAnalysisPos_)));
        const std::size_t minInputNeeded = nominalPos + kWindowSize + kSearchWindow;
        if (inputFramesAvailable_ < minInputNeeded) break;

        updateRamp();

        const std::size_t bestPos = findBestOffset(nominalPos);

        // Drain completed frames before overlap-add
        drainOutput(outputInterleaved, framesWritten, outputCapacityFrames, kWindowSize - kSynthesisHop);
        const std::size_t currentOutFrames = outputFramesAvailable_;
        const std::size_t synthesisStart = (currentOutFrames >= (kWindowSize - kSynthesisHop))
            ? (currentOutFrames - (kWindowSize - kSynthesisHop))
            : 0U;

        const std::size_t requiredFrames = synthesisStart + kWindowSize;
        if (requiredFrames > kOutputCapacityFrames) break;

        for (std::size_t f = currentOutFrames; f < requiredFrames; ++f) {
            const std::size_t idx = ((outputWritePos_ + f) % kOutputCapacityFrames) * channels_;
            for (std::size_t ch = 0; ch < channels_; ++ch) {
                outputBuffer_[idx + ch] = 0.0F;
            }
        }

        // Overlap-add Hann-windowed segment across all channels
        for (std::size_t i = 0; i < kWindowSize; ++i) {
            const float w = hannWindow_[i];
            const std::size_t outIdx = ((outputWritePos_ + synthesisStart + i) % kOutputCapacityFrames) * channels_;
            const std::size_t inIdx = ((inputReadPos_ + bestPos + i) % kInputCapacityFrames) * channels_;
            for (std::size_t ch = 0; ch < channels_; ++ch) {
                outputBuffer_[outIdx + ch] += inputBuffer_[inIdx + ch] * w;
            }
        }

        // Save natural continuation (bestPos + kSynthesisHop) as Mono template for next hop's NCC
        float nextTemplateEnergy = 0.0F;
        for (std::size_t i = 0; i < kWindowSize; ++i) {
            const std::size_t srcIndex = bestPos + kSynthesisHop + i;
            if (srcIndex < inputFramesAvailable_) {
                const float tSample = inputMonoMix(srcIndex);
                prevTemplateMono_[i] = tSample;
                if ((i & 1U) == 0U) {
                    nextTemplateEnergy += tSample * tSample;
                }
            } else {
                prevTemplateMono_[i] = 0.0F;
            }
        }
        prevTemplateEnergy_ = nextTemplateEnergy;
        hasPreviousTemplate_ = (nextTemplateEnergy > kEnergyEpsilon);

        // Advance nominal analysis position by exact floating-point hop,
        // while keeping kSearchWindow frames in the buffer for backward search on next hop!
        const double analysisHop = static_cast<double>(kSynthesisHop) * static_cast<double>(currentRatio_);
        nominalAnalysisPos_ += analysisHop;

        if (nominalAnalysisPos_ > static_cast<double>(kSearchWindow)) {
            const std::size_t framesToConsume = static_cast<std::size_t>(
                std::floor(nominalAnalysisPos_ - static_cast<double>(kSearchWindow)));
            consumeInput(framesToConsume);
            nominalAnalysisPos_ -= static_cast<double>(framesToConsume);
        }

        outputFramesAvailable_ = requiredFrames;
    }

    drainOutput(outputInterleaved, framesWritten, outputCapacityFrames, kWindowSize - kSynthesisHop);
    return framesWritten;
}

std::size_t WsolaTimeStretcher::flush(float* outputInterleaved, std::size_t outputCapacityFrames) noexcept {
    if (outputInterleaved == nullptr || outputCapacityFrames == 0) return 0;

    std::size_t framesWritten = 0;
    drainOutput(outputInterleaved, framesWritten, outputCapacityFrames, 0U);
    return framesWritten;
}

} // namespace neonsong::audio
