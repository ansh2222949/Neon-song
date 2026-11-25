#include "BpmPhaseDetector.h"
#include <cmath>
#include <algorithm>

namespace neonsong::audio {

namespace {
constexpr double kPi = 3.1415926535897932384626433832795;
} // namespace

BpmPhaseDetector::BpmPhaseDetector() noexcept {
    configure(48000.0);
}

void BpmPhaseDetector::configure(double sampleRate) noexcept {
    if (!std::isfinite(sampleRate) || sampleRate < 8000.0 || sampleRate > 384000.0) {
        sampleRate = 48000.0;
    }
    sampleRate_ = sampleRate;
    decimationFactor_ = static_cast<std::size_t>(std::max<long long>(1LL, std::llround(sampleRate_ / 200.0)));

    const double envRate = sampleRate_ / static_cast<double>(decimationFactor_);
    minLag_ = std::clamp<std::size_t>(
        static_cast<std::size_t>(std::llround((60.0 / 185.0) * envRate)), 1U, kCorrelationBufferSize - 3U);
    maxLag_ = std::clamp<std::size_t>(
        static_cast<std::size_t>(std::llround((60.0 / 55.0) * envRate)), minLag_ + 1U, kCorrelationBufferSize - 2U);
    minBeatGapFrames_ = static_cast<std::uint64_t>(std::llround(sampleRate_ * 0.25));

    const double omega = 2.0 * kPi * 100.0 / sampleRate_;
    const double alpha = std::sin(omega) / 2.0;  // Q = 1.0
    const double invA0 = 1.0 / (1.0 + alpha);
    b0_ = alpha * invA0;
    b1_ = 0.0;
    b2_ = -alpha * invA0;
    a1_ = (-2.0 * std::cos(omega)) * invA0;
    a2_ = (1.0 - alpha) * invA0;

    reset();
}

void BpmPhaseDetector::reset() noexcept {
    z1_ = 0.0;
    z2_ = 0.0;
    decimationAccumulator_ = 0.0F;
    decimationCounter_ = 0;
    totalFramesProcessed_ = 0;
    nextAnalysisFrame_ = static_cast<std::uint64_t>((maxLag_ + 100U) * decimationFactor_);
    confidenceLocked_ = false;
    envelopeWritePos_ = 0;
    envelopeFull_ = false;
    previousFiltered_ = 0.0F;
    envMovingAvg_ = 0.0F;
    lastPeakFrame_ = 0;
    currentBpm_.store(120.0F, std::memory_order_release);
    currentConfidence_.store(0.0F, std::memory_order_release);
    currentDownbeatFrame_.store(0, std::memory_order_release);
    envelopeBuffer_.fill(0.0F);
}

void BpmPhaseDetector::process(const float* stereoInterleaved, std::size_t frames) noexcept {
    if (stereoInterleaved == nullptr || frames == 0) return;

    double z1 = z1_;
    double z2 = z2_;
    const double b0 = b0_, b2 = b2_, a1 = a1_, a2 = a2_;
    float accum = decimationAccumulator_;
    std::size_t counter = decimationCounter_;
    std::size_t writePos = envelopeWritePos_;
    float prevFiltered = previousFiltered_;
    std::uint64_t peakFrame = lastPeakFrame_;
    const float invDecim = 1.0F / static_cast<float>(decimationFactor_);
    float envAvg = envMovingAvg_;

    for (std::size_t i = 0; i < frames; ++i) {
        const float rawL = stereoInterleaved[i * 2U];
        const float rawR = stereoInterleaved[i * 2U + 1U];
        const float mono = (std::isfinite(rawL) && std::isfinite(rawR))
            ? ((rawL + rawR) * 0.5F) : 0.0F;
        const double x = static_cast<double>(mono);
        const double out = b0 * x + z1;
        z1 = -a1 * out + z2;
        z2 = b2 * x - a2 * out;

        accum += std::max(0.0F, static_cast<float>(out));
        if (++counter >= decimationFactor_) {
            const float envVal = accum * invDecim;
            envelopeBuffer_[writePos] = envVal;
            writePos = (writePos + 1U) & (kEnvelopeBufferSize - 1U);
            if (writePos == 0) envelopeFull_ = true;

            // Adaptive beat onset peak on 200 Hz envelope with 250 ms refractory window.
            envAvg += 0.04F * (envVal - envAvg);
            const std::uint64_t frameNow = totalFramesProcessed_ + i;
            if (envVal > std::max(onsetThreshold_, envAvg * 1.45F) &&
                envVal > prevFiltered &&
                (frameNow >= peakFrame + minBeatGapFrames_)) {
                peakFrame = frameNow;
            }
            prevFiltered = envVal;
            accum = 0.0F;
            counter = 0;
        }
    }

    z1_ = (!std::isfinite(z1) || std::abs(z1) < 1.0e-20) ? 0.0 : z1;
    z2_ = (!std::isfinite(z2) || std::abs(z2) < 1.0e-20) ? 0.0 : z2;
    decimationAccumulator_ = accum;
    decimationCounter_ = counter;
    envelopeWritePos_ = writePos;
    previousFiltered_ = prevFiltered;
    envMovingAvg_ = (std::abs(envAvg) < 1.0e-20F) ? 0.0F : envAvg;
    lastPeakFrame_ = peakFrame;
    totalFramesProcessed_ += frames;
    currentDownbeatFrame_.store(lastPeakFrame_, std::memory_order_release);

    if (totalFramesProcessed_ >= nextAnalysisFrame_) {
        updateAutocorrelation();
        const double intervalSec = confidenceLocked_ ? 5.0 : (static_cast<double>(kAnalysisIntervalMs) / 1000.0);
        nextAnalysisFrame_ = totalFramesProcessed_ + static_cast<std::uint64_t>(sampleRate_ * intervalSec);
    }
}

void BpmPhaseDetector::updateAutocorrelation() noexcept {
    const std::size_t numBins = envelopeFull_ ? kEnvelopeBufferSize : envelopeWritePos_;
    const double envRate = sampleRate_ / static_cast<double>(decimationFactor_);
    const std::size_t minLag = minLag_;
    const std::size_t maxLag = maxLag_;

    if (maxLag + 2U > correlationValues_.size() || numBins < maxLag + 100U) {
        return;
    }
    const std::size_t windowSize = numBins - maxLag;

    float* linearEnv = linearEnvelope_.data();
    if (envelopeFull_) {
        const std::size_t tail = kEnvelopeBufferSize - envelopeWritePos_;
        std::copy_n(envelopeBuffer_.data() + envelopeWritePos_, tail, linearEnv);
        std::copy_n(envelopeBuffer_.data(), envelopeWritePos_, linearEnv + tail);
    } else {
        std::copy_n(envelopeBuffer_.data(), numBins, linearEnv);
    }

    // Mean-center envelope
    double sum = 0.0;
    for (std::size_t i = 0; i < numBins; ++i) sum += linearEnv[i];
    const float mean = static_cast<float>(sum / static_cast<double>(numBins));
    for (std::size_t i = 0; i < numBins; ++i) {
        linearEnv[i] = std::max(0.0F, linearEnv[i] - mean);
    }

    float bestWeightedCorr = 0.0F;
    float maxCorr = 0.0F;
    std::size_t bestLag = minLag;
    correlationValues_.fill(0.0F);
    const float* __restrict env = linearEnv;

    for (std::size_t lag = minLag; lag <= maxLag; ++lag) {
        float corr = 0.0F;
        const float* __restrict shifted = env + lag;
        for (std::size_t n = 0; n < windowSize; ++n) {
            corr += env[n] * shifted[n];
        }
        correlationValues_[lag] = corr;

        // Mild perceptual tempo prior centered at 120 BPM prevents half/double-time octave flips.
        const double bpm = (60.0 * envRate) / static_cast<double>(lag);
        const double octDist = std::log2(bpm / 120.0);
        const float prior = static_cast<float>(std::exp(-0.5 * (octDist / 0.75) * (octDist / 0.75)));
        const float weighted = corr * (0.85F + 0.15F * prior);

        if (weighted > bestWeightedCorr) {
            bestWeightedCorr = weighted;
            maxCorr = corr;
            bestLag = lag;
        }
    }

    if (maxCorr <= 1.0e-6F) return;

    // Parabolic interpolation around peak
    double refinedLag = static_cast<double>(bestLag);
    if (bestLag > minLag && bestLag < maxLag) {
        const double y1 = correlationValues_[bestLag - 1U];
        const double y2 = correlationValues_[bestLag];
        const double y3 = correlationValues_[bestLag + 1U];
        const double denom = (y1 - 2.0 * y2 + y3);
        if (denom < -1.0e-9) {
            const double delta = (y1 - y3) / (2.0 * denom);
            refinedLag += std::clamp(delta, -0.5, 0.5);
        }
    }

    const float detectedBpm = static_cast<float>((60.0 * envRate) / refinedLag);

    // Compute confidence: ratio of peak to mean correlation
    double corrSum = 0.0;
    std::size_t count = 0;
    for (std::size_t l = minLag; l <= maxLag; ++l) {
        corrSum += correlationValues_[l];
        count++;
    }
    const float avgCorr = (count > 0)
        ? static_cast<float>(corrSum / static_cast<double>(count))
        : 1.0F;
    const float confidence = std::clamp((maxCorr - avgCorr) / (maxCorr + 1.0e-5F), 0.0F, 1.0F);

    currentBpm_.store(std::clamp(detectedBpm, 50.0F, 220.0F), std::memory_order_release);
    currentConfidence_.store(confidence, std::memory_order_release);
    currentDownbeatFrame_.store(lastPeakFrame_, std::memory_order_release);
    confidenceLocked_ = (envelopeFull_ && confidence >= kConfidenceLockThreshold);
}

BeatDetectionResult BpmPhaseDetector::getResult() const noexcept {
    return BeatDetectionResult{
        currentBpm_.load(std::memory_order_acquire),
        currentConfidence_.load(std::memory_order_acquire),
        currentDownbeatFrame_.load(std::memory_order_acquire),
    };
}

} // namespace neonsong::audio
