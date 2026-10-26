#include "WsolaTimeStretcher.h"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace lastwave::audio {

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

WsolaTimeStretcher::WsolaTimeStretcher() noexcept {
    configure(48000.0, 2);
}

void WsolaTimeStretcher::configure(double sampleRate, std::size_t channels) noexcept {
    if (sampleRate <= 0.0) sampleRate = 48000.0;
    sampleRate_ = sampleRate;
    channels_ = (channels == 1) ? 1 : 2;

    hannWindow_.resize(kWindowSize);
    for (std::size_t i = 0; i < kWindowSize; ++i) {
        // Periodic Hann window
        hannWindow_[i] = static_cast<float>(
            0.5 * (1.0 - std::cos(2.0 * M_PI * static_cast<double>(i) / static_cast<double>(kWindowSize))));
    }

    inputBuffer_.assign(kInputCapacityFrames * channels_, 0.0F);
    outputBuffer_.assign(kOutputCapacityFrames * channels_, 0.0F);
    prevTemplate_.resize(kWindowSize * channels_, 0.0F);

    reset();
}

void WsolaTimeStretcher::reset() noexcept {
    inputReadPos_ = 0;
    inputWritePos_ = 0;
    inputFramesAvailable_ = 0;
    outputReadPos_ = 0;
    outputWritePos_ = 0;
    outputFramesAvailable_ = 0;
    std::fill(prevTemplate_.begin(), prevTemplate_.end(), 0.0F);
    nominalAnalysisPos_ = 0.0;
    hasPreviousTemplate_ = false;
    currentRatio_ = targetRatio_;
    rampActive_ = false;
}

void WsolaTimeStretcher::setTimeRatio(float ratio) noexcept {
    targetRatio_ = std::clamp(ratio, 0.85F, 1.15F);
    if (!rampActive_) {
        currentRatio_ = targetRatio_;
    }
}

void WsolaTimeStretcher::startRecoveryRamp(float targetRatio, float durationSeconds) noexcept {
    rampTarget_ = std::clamp(targetRatio, 0.85F, 1.15F);
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

std::size_t WsolaTimeStretcher::findBestOffset(std::size_t nominalPos) const noexcept {
    if (!hasPreviousTemplate_ || inputFramesAvailable_ < nominalPos + kWindowSize + kSearchWindow) {
        return nominalPos;
    }

    const std::size_t minPos = (nominalPos >= kSearchWindow) ? (nominalPos - kSearchWindow) : 0;
    const std::size_t maxPos = nominalPos + kSearchWindow;
    const std::size_t maxAllowed = inputFramesAvailable_ - kWindowSize;
    const std::size_t clampedMax = std::min(maxPos, maxAllowed);

    if (minPos >= clampedMax) return nominalPos;

    float bestScore = -1.0e30F;
    std::size_t bestPos = nominalPos;

    for (std::size_t cand = minPos; cand <= clampedMax; cand += 2) {
        float score = 0.0F;
        const float* prevPtr = prevTemplate_.data();

        for (std::size_t i = 0; i < kWindowSize; i += 2) {
            score += prevPtr[i * channels_] * inputSample(cand + i, 0);
        }

        if (score > bestScore) {
            bestScore = score;
            bestPos = cand;
        }
    }

    return bestPos;
}

void WsolaTimeStretcher::appendInput(const float* inputStereo, std::size_t inputFrames) noexcept {
    if (inputStereo == nullptr || inputFrames == 0) return;
    const std::size_t capacity = inputBuffer_.size() / channels_;
    const std::size_t framesToCopy = std::min(inputFrames, capacity - inputFramesAvailable_);
    for (std::size_t f = 0; f < framesToCopy; ++f) {
        const std::size_t dst = inputWritePos_ * channels_;
        for (std::size_t ch = 0; ch < channels_; ++ch) {
            inputBuffer_[dst + ch] = inputStereo[f * channels_ + ch];
        }
        inputWritePos_ = (inputWritePos_ + 1) % capacity;
    }
    inputFramesAvailable_ += framesToCopy;
}

void WsolaTimeStretcher::consumeInput(std::size_t frames) noexcept {
    const std::size_t consumed = std::min(frames, inputFramesAvailable_);
    const std::size_t capacity = inputBuffer_.size() / channels_;
    inputReadPos_ = (inputReadPos_ + consumed) % capacity;
    inputFramesAvailable_ -= consumed;
}

float WsolaTimeStretcher::inputSample(std::size_t frame, std::size_t channel) const noexcept {
    const std::size_t capacity = inputBuffer_.size() / channels_;
    return inputBuffer_[((inputReadPos_ + frame) % capacity) * channels_ + channel];
}

void WsolaTimeStretcher::drainOutput(
    float* outputStereo,
    std::size_t& framesWritten,
    std::size_t outputCapacityFrames,
    std::size_t keepFrames) noexcept {
    if (outputStereo == nullptr || framesWritten >= outputCapacityFrames) return;
    const std::size_t drainable = outputFramesAvailable_ > keepFrames
        ? outputFramesAvailable_ - keepFrames
        : 0;
    const std::size_t count = std::min(drainable, outputCapacityFrames - framesWritten);
    const std::size_t capacity = outputBuffer_.size() / channels_;
    for (std::size_t f = 0; f < count; ++f) {
        const std::size_t src = outputReadPos_ * channels_;
        for (std::size_t ch = 0; ch < channels_; ++ch) {
            outputStereo[(framesWritten + f) * channels_ + ch] = outputBuffer_[src + ch];
            outputBuffer_[src + ch] = 0.0F;
        }
        outputReadPos_ = (outputReadPos_ + 1) % capacity;
    }
    outputFramesAvailable_ -= count;
    outputWritePos_ = outputReadPos_;
    framesWritten += count;
}

std::size_t WsolaTimeStretcher::process(
    const float* inputStereo,
    std::size_t inputFrames,
    float* outputStereo,
    std::size_t outputCapacityFrames) noexcept {
    if (outputStereo == nullptr || outputCapacityFrames == 0) return 0;

    appendInput(inputStereo, inputFrames);

    const std::size_t minInputNeeded = (kWindowSize + kSearchWindow * 2) * channels_;

    // Generate synthesis hops
    std::size_t framesWritten = 0;
    while (inputFramesAvailable_ >= minInputNeeded) {
        updateRamp();

        const double analysisHop = static_cast<double>(kSynthesisHop) / static_cast<double>(currentRatio_);
        const std::size_t nominalPos = static_cast<std::size_t>(std::max(0.0, nominalAnalysisPos_));
        const std::size_t bestPos = findBestOffset(nominalPos);

        // Current start of new window in outputBuffer
        // The overlap region is the last (kWindowSize - kSynthesisHop) frames
        // Drain output that is outside the overlap region before adding the
        // next synthesis hop. This keeps the fixed ring bounded while the
        // overlap region remains available for overlap-add.
        drainOutput(outputStereo, framesWritten, outputCapacityFrames, kWindowSize - kSynthesisHop);
        const std::size_t currentOutFrames = outputFramesAvailable_;
        const std::size_t synthesisStart = (currentOutFrames >= (kWindowSize - kSynthesisHop))
            ? (currentOutFrames - (kWindowSize - kSynthesisHop))
            : 0;

        // Resize outputBuffer to accommodate synthesisStart + kWindowSize frames
        const std::size_t requiredFrames = synthesisStart + kWindowSize;
        if (requiredFrames > kOutputCapacityFrames) break;
        for (std::size_t f = currentOutFrames; f < requiredFrames; ++f) {
            const std::size_t idx = ((outputWritePos_ + f) % kOutputCapacityFrames) * channels_;
            for (std::size_t ch = 0; ch < channels_; ++ch) outputBuffer_[idx + ch] = 0.0F;
        }

        // Overlap-add windowed segment
        for (std::size_t i = 0; i < kWindowSize; ++i) {
            const float w = hannWindow_[i];
            const std::size_t outIdx = ((outputWritePos_ + synthesisStart + i) % kOutputCapacityFrames) * channels_;
            for (std::size_t ch = 0; ch < channels_; ++ch) {
                outputBuffer_[outIdx + ch] += inputSample(bestPos + i, ch) * w;
            }
        }

        // Save synthesis segment (shifted forward by kSynthesisHop) as template for next hop
        for (std::size_t i = 0; i < kWindowSize; ++i) {
            const std::size_t srcIndex = bestPos + kSynthesisHop + i;
            for (std::size_t ch = 0; ch < channels_; ++ch) {
                if (srcIndex < inputFramesAvailable_) {
                    prevTemplate_[i * channels_ + ch] = inputSample(srcIndex, ch) * hannWindow_[i];
                } else {
                    prevTemplate_[i * channels_ + ch] = 0.0F;
                }
            }
        }
        hasPreviousTemplate_ = true;

        // Advance input buffer by analysisHop
        const std::size_t consumedFrames = static_cast<std::size_t>(analysisHop);
        consumeInput(consumedFrames);
        outputFramesAvailable_ = requiredFrames;
        nominalAnalysisPos_ = 0.0;
    }

    drainOutput(outputStereo, framesWritten, outputCapacityFrames);

    return framesWritten;
}

std::size_t WsolaTimeStretcher::flush(float* outputStereo, std::size_t outputCapacityFrames) noexcept {
    if (outputStereo == nullptr || outputCapacityFrames == 0) return 0;

    std::size_t framesWritten = 0;
    drainOutput(outputStereo, framesWritten, outputCapacityFrames);
    return framesWritten;
}

} // namespace lastwave::audio
