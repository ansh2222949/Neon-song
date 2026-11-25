#include "WsolaTimeStretcher.h"

#include <algorithm>
#include <cmath>
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
    if (!std::isfinite(sampleRate) || sampleRate <= 0.0) sampleRate = 48000.0;
    sampleRate_ = sampleRate;
    channels_ = std::clamp<std::size_t>(channels, 1U, kMaxChannels);

    // Scale window/search proportionally for 96/192/384 kHz (power-of-2 keeps masks valid).
    const std::size_t rawScale = std::clamp<std::size_t>(
        static_cast<std::size_t>(std::llround(sampleRate_ / 48000.0)), 1U, 8U);
    std::size_t pow2Scale = 1U;
    while (pow2Scale < rawScale) pow2Scale <<= 1U;

    windowSize_ = 1024U * pow2Scale;       // ~21.3 ms
    synthesisHop_ = windowSize_ / 2U;      // 50% exact COLA overlap
    searchWindow_ = 256U * pow2Scale;      // +/- 5.3 ms (covers down to ~95 Hz)
    inputCapacityFrames_ = windowSize_ * 16U;
    outputCapacityFrames_ = windowSize_ * 16U;

    hannWindow_.resize(windowSize_);
    for (std::size_t i = 0; i < windowSize_; ++i) {
        hannWindow_[i] = static_cast<float>(
            0.5 * (1.0 - std::cos(2.0 * kPi * static_cast<double>(i) / static_cast<double>(windowSize_))));
    }

    inputBuffer_.assign(inputCapacityFrames_ * channels_, 0.0F);
    outputBuffer_.assign(outputCapacityFrames_ * channels_, 0.0F);
    prevTemplateMono_.assign(windowSize_, 0.0F);
    monoSearchScratch_.assign(windowSize_ + searchWindow_ * 2U + 4U, 0.0F);

    reset();
}

void WsolaTimeStretcher::reset() noexcept {
    inputReadPos_ = 0;
    inputWritePos_ = 0;
    inputFramesAvailable_ = 0;
    outputReadPos_ = 0;
    outputFramesAvailable_ = 0;
    std::fill(prevTemplateMono_.begin(), prevTemplateMono_.end(), 0.0F);
    // Start at 0.0 so beat-1 transients at the start of a track are never clipped.
    nominalAnalysisPos_ = 0.0;
    prevTemplateEnergy_ = 0.0F;
    prevTemplateFullEnergy_ = 0.0F;
    hasPreviousTemplate_ = false;
    tailPadded_ = false;
    currentRatio_ = targetRatio_;
    rampActive_ = false;
}

void WsolaTimeStretcher::setTimeRatio(float ratio) noexcept {
    if (!std::isfinite(ratio)) ratio = 1.0F;
    targetRatio_ = std::clamp(ratio, 0.25F, 3.0F);
    rampActive_ = false;
    currentRatio_ = targetRatio_;
}

void WsolaTimeStretcher::startRecoveryRamp(float targetRatio, float durationSeconds) noexcept {
    if (!std::isfinite(targetRatio)) targetRatio = 1.0F;
    if (!std::isfinite(durationSeconds)) durationSeconds = 5.0F;
    rampTarget_ = std::clamp(targetRatio, 0.25F, 3.0F);
    durationSeconds = std::max(0.05F, durationSeconds);
    const double totalHops = (durationSeconds * sampleRate_) / static_cast<double>(synthesisHop_);
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

float WsolaTimeStretcher::inputMonoMix(std::size_t frame) const noexcept {
    const float* framePtr =
        &inputBuffer_[((inputReadPos_ + frame) & (inputCapacityFrames_ - 1U)) * channels_];
    if (channels_ == 1U) return framePtr[0];
    if (channels_ == 2U) return (framePtr[0] + framePtr[1]) * 0.5F;
    return (framePtr[0] + framePtr[1] + framePtr[2]) * 0.33333334F;
}

std::size_t WsolaTimeStretcher::findBestOffset(std::size_t nominalPos) noexcept {
    if (!hasPreviousTemplate_ || prevTemplateEnergy_ <= kEnergyEpsilon ||
        inputFramesAvailable_ < nominalPos + windowSize_) {
        return std::min(nominalPos, inputFramesAvailable_ > windowSize_ ? inputFramesAvailable_ - windowSize_ : 0U);
    }

    const std::size_t minPos = (nominalPos >= searchWindow_) ? (nominalPos - searchWindow_) : 0U;
    const std::size_t maxAllowed = inputFramesAvailable_ - windowSize_;
    const std::size_t clampedMax = std::min(nominalPos + searchWindow_, maxAllowed);

    if (minPos >= clampedMax) return std::min(nominalPos, maxAllowed);

    const std::size_t searchSpan = (clampedMax - minPos) + windowSize_;
    if (searchSpan > monoSearchScratch_.size()) {
        return std::min(nominalPos, maxAllowed);
    }

    // 1. Linearize the circular buffer slice into contiguous L1-cache-friendly mono scratch
    for (std::size_t i = 0; i < searchSpan; ++i) {
        monoSearchScratch_[i] = inputMonoMix(minPos + i);
    }

    // 2. Compute decimated running energy for candidate 0 (step = 2)
    constexpr std::size_t kDecim = 2U;
    float runningCandEnergy = 0.0F;
    for (std::size_t i = 0; i < windowSize_; i += kDecim) {
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
            const float incoming = monoSearchScratch_[rel + windowSize_ - kDecim];
            runningCandEnergy = std::max(0.0F, runningCandEnergy - outgoing * outgoing + incoming * incoming);
        }

        float dot = 0.0F;
        const float* candPtr = monoSearchScratch_.data() + rel;
        for (std::size_t i = 0; i < windowSize_; i += kDecim) {
            dot += prevPtr[i] * candPtr[i];
        }

        const float norm = std::sqrt(prevTemplateEnergy_ * runningCandEnergy + kEnergyEpsilon);
        const float rawNcc = dot / norm;
        const float distRatio = static_cast<float>(
            (minPos + rel > nominalPos) ? (minPos + rel - nominalPos) : (nominalPos - (minPos + rel))) /
            static_cast<float>(searchWindow_);
        // Slight center bias (0.015) keeps timing tight when multiple pitch periods have equal correlation
        const float ncc = rawNcc - (0.015F * distRatio * distRatio);

        if (ncc > bestNcc) {
            bestNcc = ncc;
            bestRelOffset = rel;
        }
    }

    // 4. Full-resolution (step = 1) +/- 1 sample refinement with consistent center bias.
    auto evaluateFullRes = [&](std::size_t rel) noexcept -> float {
        float dot = 0.0F;
        float energy = 0.0F;
        const float* candPtr = monoSearchScratch_.data() + rel;
        for (std::size_t i = 0; i < windowSize_; ++i) {
            const float c = candPtr[i];
            dot += prevPtr[i] * c;
            energy += c * c;
        }
        const float raw = dot / std::sqrt(prevTemplateFullEnergy_ * energy + kEnergyEpsilon);
        const std::size_t pos = minPos + rel;
        const float distRatio = static_cast<float>(
            (pos > nominalPos) ? (pos - nominalPos) : (nominalPos - pos)) /
            static_cast<float>(searchWindow_);
        return raw - (0.015F * distRatio * distRatio);
    };

    std::size_t refinedRelOffset = bestRelOffset;
    float refinedBestScore = evaluateFullRes(bestRelOffset);

    if (bestRelOffset > 0U) {
        const float leftScore = evaluateFullRes(bestRelOffset - 1U);
        if (leftScore > refinedBestScore) {
            refinedBestScore = leftScore;
            refinedRelOffset = bestRelOffset - 1U;
        }
    }
    if (bestRelOffset + 1U <= numOffsets) {
        const float rightScore = evaluateFullRes(bestRelOffset + 1U);
        if (rightScore > refinedBestScore) {
            refinedRelOffset = bestRelOffset + 1U;
        }
    }

    return minPos + refinedRelOffset;
}

std::size_t WsolaTimeStretcher::appendInput(
    const float* inputInterleaved,
    std::size_t inputFrames) noexcept {
    if (inputInterleaved == nullptr || inputFrames == 0) return 0;
    tailPadded_ = false;
    const std::size_t framesToCopy = std::min(inputFrames, inputCapacityFrames_ - inputFramesAvailable_);
    if (framesToCopy == 0) return 0;

    const std::size_t first = std::min(framesToCopy, inputCapacityFrames_ - inputWritePos_);
    std::memcpy(
        inputBuffer_.data() + inputWritePos_ * channels_,
        inputInterleaved,
        first * channels_ * sizeof(float));
    if (first < framesToCopy) {
        std::memcpy(
            inputBuffer_.data(),
            inputInterleaved + first * channels_,
            (framesToCopy - first) * channels_ * sizeof(float));
    }
    inputWritePos_ = (inputWritePos_ + framesToCopy) & (inputCapacityFrames_ - 1U);
    inputFramesAvailable_ += framesToCopy;
    return framesToCopy;
}

void WsolaTimeStretcher::appendSilence(std::size_t frames) noexcept {
    const std::size_t count = std::min(frames, inputCapacityFrames_ - inputFramesAvailable_);
    if (count == 0) return;

    const std::size_t first = std::min(count, inputCapacityFrames_ - inputWritePos_);
    std::memset(
        inputBuffer_.data() + inputWritePos_ * channels_,
        0,
        first * channels_ * sizeof(float));
    if (first < count) {
        std::memset(
            inputBuffer_.data(),
            0,
            (count - first) * channels_ * sizeof(float));
    }
    inputWritePos_ = (inputWritePos_ + count) & (inputCapacityFrames_ - 1U);
    inputFramesAvailable_ += count;
}

void WsolaTimeStretcher::consumeInput(std::size_t frames) noexcept {
    const std::size_t consumed = std::min(frames, inputFramesAvailable_);
    inputReadPos_ = (inputReadPos_ + consumed) & (inputCapacityFrames_ - 1U);
    inputFramesAvailable_ -= consumed;
}

void WsolaTimeStretcher::drainOutput(
    float* outputInterleaved,
    std::size_t& framesWritten,
    std::size_t outputCapacityFrames,
    std::size_t keepFrames) noexcept {
    if (outputInterleaved == nullptr || framesWritten >= outputCapacityFrames) return;
    const std::size_t drainable = (outputFramesAvailable_ > keepFrames)
        ? (outputFramesAvailable_ - keepFrames)
        : 0U;
    const std::size_t count = std::min(drainable, outputCapacityFrames - framesWritten);
    if (count == 0) return;

    const std::size_t first = std::min(count, outputCapacityFrames_ - outputReadPos_);
    const std::size_t firstBytes = first * channels_ * sizeof(float);
    float* outDst = outputInterleaved + framesWritten * channels_;
    float* ringSrc = outputBuffer_.data() + outputReadPos_ * channels_;

    std::memcpy(outDst, ringSrc, firstBytes);
    std::memset(ringSrc, 0, firstBytes);

    if (first < count) {
        const std::size_t secondBytes = (count - first) * channels_ * sizeof(float);
        std::memcpy(outDst + first * channels_, outputBuffer_.data(), secondBytes);
        std::memset(outputBuffer_.data(), 0, secondBytes);
    }

    outputReadPos_ = (outputReadPos_ + count) & (outputCapacityFrames_ - 1U);
    outputFramesAvailable_ -= count;
    framesWritten += count;
}

void WsolaTimeStretcher::overlapAddSegment(
    std::size_t bestPos,
    std::size_t synthesisStart) noexcept {
    const std::size_t inMask = inputCapacityFrames_ - 1U;
    const std::size_t outMask = outputCapacityFrames_ - 1U;
    std::size_t inPos = (inputReadPos_ + bestPos) & inMask;
    std::size_t outPos = (outputReadPos_ + synthesisStart) & outMask;
    std::size_t remaining = windowSize_;
    std::size_t winOffset = 0;

    while (remaining > 0) {
        const std::size_t chunk = std::min({
            remaining,
            inputCapacityFrames_ - inPos,
            outputCapacityFrames_ - outPos,
        });
        const float* inPtr = inputBuffer_.data() + inPos * channels_;
        float* outPtr = outputBuffer_.data() + outPos * channels_;
        const float* winPtr = hannWindow_.data() + winOffset;

        if (channels_ == 2U) {
            for (std::size_t i = 0; i < chunk; ++i) {
                const float w = winPtr[i];
                const std::size_t idx = i * 2U;
                outPtr[idx] += inPtr[idx] * w;
                outPtr[idx + 1U] += inPtr[idx + 1U] * w;
            }
        } else {
            for (std::size_t i = 0; i < chunk; ++i) {
                const float w = winPtr[i];
                const std::size_t base = i * channels_;
                for (std::size_t ch = 0; ch < channels_; ++ch) {
                    outPtr[base + ch] += inPtr[base + ch] * w;
                }
            }
        }

        inPos = (inPos + chunk) & inMask;
        outPos = (outPos + chunk) & outMask;
        winOffset += chunk;
        remaining -= chunk;
    }
}

void WsolaTimeStretcher::synthesizePending(
    float* outputInterleaved,
    std::size_t& framesWritten,
    std::size_t outputCapacityFrames) noexcept {
    const std::size_t overlapKeep = windowSize_ - synthesisHop_;
    while (true) {
        drainOutput(outputInterleaved, framesWritten, outputCapacityFrames, overlapKeep);
        if (framesWritten >= outputCapacityFrames && outputFramesAvailable_ >= windowSize_) {
            break;
        }

        const std::size_t nominalPos =
            static_cast<std::size_t>(std::max(0.0, std::floor(nominalAnalysisPos_)));
        if (inputFramesAvailable_ < nominalPos + windowSize_ + searchWindow_) break;

        updateRamp();

        const std::size_t bestPos = findBestOffset(nominalPos);
        const std::size_t currentOutFrames = outputFramesAvailable_;
        const std::size_t synthesisStart =
            (currentOutFrames >= overlapKeep) ? (currentOutFrames - overlapKeep) : 0U;
        const std::size_t requiredFrames = synthesisStart + windowSize_;
        if (requiredFrames > outputCapacityFrames_) break;

        // Zero newly exposed tail slots before OLA.
        std::size_t clearPos = (outputReadPos_ + currentOutFrames) & (outputCapacityFrames_ - 1U);
        std::size_t toClear = requiredFrames - currentOutFrames;
        while (toClear > 0) {
            const std::size_t chunk = std::min(toClear, outputCapacityFrames_ - clearPos);
            std::memset(
                outputBuffer_.data() + clearPos * channels_,
                0,
                chunk * channels_ * sizeof(float));
            clearPos = (clearPos + chunk) & (outputCapacityFrames_ - 1U);
            toClear -= chunk;
        }

        overlapAddSegment(bestPos, synthesisStart);

        // Save natural continuation as mono template (both decimated and full energy).
        float nextDecimEnergy = 0.0F;
        float nextFullEnergy = 0.0F;
        for (std::size_t i = 0; i < windowSize_; ++i) {
            const std::size_t srcIndex = bestPos + synthesisHop_ + i;
            if (srcIndex < inputFramesAvailable_) {
                const float s = inputMonoMix(srcIndex);
                prevTemplateMono_[i] = s;
                const float sq = s * s;
                nextFullEnergy += sq;
                if ((i & 1U) == 0U) nextDecimEnergy += sq;
            } else {
                prevTemplateMono_[i] = 0.0F;
            }
        }
        prevTemplateEnergy_ = nextDecimEnergy;
        prevTemplateFullEnergy_ = nextFullEnergy;
        hasPreviousTemplate_ = (nextDecimEnergy > kEnergyEpsilon);

        const double analysisHop =
            static_cast<double>(synthesisHop_) * static_cast<double>(currentRatio_);
        nominalAnalysisPos_ += analysisHop;

        if (nominalAnalysisPos_ > static_cast<double>(searchWindow_)) {
            const std::size_t framesToConsume = static_cast<std::size_t>(
                std::floor(nominalAnalysisPos_ - static_cast<double>(searchWindow_)));
            consumeInput(framesToConsume);
            nominalAnalysisPos_ -= static_cast<double>(framesToConsume);
        }

        outputFramesAvailable_ = requiredFrames;
    }
}

std::size_t WsolaTimeStretcher::process(
    const float* inputInterleaved,
    std::size_t inputFrames,
    float* outputInterleaved,
    std::size_t outputCapacityFrames) noexcept {
    if (outputInterleaved == nullptr || outputCapacityFrames == 0) return 0;

    std::size_t framesWritten = 0;
    std::size_t inputOffset = 0;

    do {
        if (inputInterleaved != nullptr && inputOffset < inputFrames) {
            inputOffset += appendInput(
                inputInterleaved + inputOffset * channels_,
                inputFrames - inputOffset);
        }
        const std::size_t prevAvail = inputFramesAvailable_;
        synthesizePending(outputInterleaved, framesWritten, outputCapacityFrames);
        if (inputOffset >= inputFrames || inputFramesAvailable_ == prevAvail) {
            break;
        }
    } while (true);

    drainOutput(outputInterleaved, framesWritten, outputCapacityFrames, windowSize_ - synthesisHop_);
    return framesWritten;
}

std::size_t WsolaTimeStretcher::flush(
    float* outputInterleaved,
    std::size_t outputCapacityFrames) noexcept {
    if (outputInterleaved == nullptr || outputCapacityFrames == 0) return 0;

    std::size_t framesWritten = 0;
    if (!tailPadded_ && inputFramesAvailable_ > 0) {
        tailPadded_ = true;
        appendSilence(windowSize_ + searchWindow_);
    }
    synthesizePending(outputInterleaved, framesWritten, outputCapacityFrames);
    drainOutput(outputInterleaved, framesWritten, outputCapacityFrames, 0U);
    return framesWritten;
}

} // namespace neonsong::audio
