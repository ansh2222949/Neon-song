#pragma once

#include <cmath>
#include <cstddef>
#include <vector>
#include <array>
#include <algorithm>

namespace neonsong::audio {

class EchoTailProcessor final {
public:
    EchoTailProcessor() noexcept {
        configure(48000.0, 120.0F);
    }

    void configure(double sampleRate, float bpm = 120.0F) noexcept {
        if (!std::isfinite(sampleRate) || sampleRate < 8000.0 || sampleRate > 384000.0) {
            sampleRate = 48000.0;
        }
        sampleRate_ = sampleRate;

        // Max delay is 0.45 s; allocate power-of-2 >= 0.5 s for branchless masking and lower RAM.
        const std::size_t minFrames = static_cast<std::size_t>(std::ceil(sampleRate_ * 0.50)) + 16U;
        capacityFrames_ = roundUpPow2(minFrames);
        capacityMask_ = capacityFrames_ - 1U;

        const double omega = 2.0 * kPi * 4500.0 / sampleRate_;
        dampFactor_ = static_cast<float>(std::clamp(1.0 - std::exp(-omega), 0.05, 0.95));

        buffer_.assign(capacityFrames_ * 2U, 0.0F);
        setTempo(bpm);
        reset();
    }

    void setTempo(float bpm) noexcept {
        if (!std::isfinite(bpm)) bpm = 120.0F;
        bpm = std::clamp(bpm, 50.0F, 220.0F);
        const double beatSeconds = 60.0 / static_cast<double>(bpm);
        double delaySeconds = beatSeconds * 0.5;
        if (delaySeconds > 0.45) delaySeconds = beatSeconds * 0.25;
        delaySeconds = std::clamp(delaySeconds, 0.18, 0.45);
        delayFrames_ = (capacityFrames_ > 1U)
            ? std::clamp<std::size_t>(
                  static_cast<std::size_t>(std::llround(delaySeconds * sampleRate_)),
                  1U,
                  capacityFrames_ - 1U)
            : 0U;
        feedbackGain_ = static_cast<float>(std::exp(-6.907755 * (delaySeconds / 1.2)));
    }

    void reset() noexcept {
        std::fill(buffer_.begin(), buffer_.end(), 0.0F);
        writeIndex_ = 0;
        filterState_ = {0.0F, 0.0F};
        prevDryGain_ = 1.0F;
        prevSendGain_ = 0.0F;
        tailActive_ = false;
    }

    void setEnabled(bool enabled) noexcept {
        enabled_ = enabled;
    }

    [[nodiscard]] bool isEnabled() const noexcept {
        return enabled_;
    }

    [[nodiscard]] std::size_t delayFrames() const noexcept {
        return delayFrames_;
    }

    void process(float* stereoInterleaved, std::size_t frames, float sendGain, float dryGain) noexcept {
        if (stereoInterleaved == nullptr || frames == 0) return;
        if (!std::isfinite(sendGain)) sendGain = 0.0F;
        if (!std::isfinite(dryGain)) dryGain = 1.0F;
        sendGain = std::clamp(sendGain, 0.0F, 1.0F);
        dryGain = std::clamp(dryGain, 0.0F, 1.0F);

        if (enabled_ && sendGain > 1.0e-6F) tailActive_ = true;

        // Fast path: skip delay line completely while echo tail is empty.
        if (!tailActive_ || capacityFrames_ == 0U) {
            const float startDry = prevDryGain_;
            if (std::abs(dryGain - startDry) > 1.0e-6F) {
                const float step = (dryGain - startDry) / static_cast<float>(frames);
                for (std::size_t f = 0; f < frames; ++f) {
                    const float g = startDry + step * static_cast<float>(f + 1U);
                    stereoInterleaved[f * 2U] *= g;
                    stereoInterleaved[f * 2U + 1U] *= g;
                }
            } else if (std::abs(dryGain - 1.0F) > 1.0e-6F) {
                for (std::size_t i = 0; i < frames * 2U; ++i) {
                    stereoInterleaved[i] *= dryGain;
                }
            }
            prevDryGain_ = dryGain;
            prevSendGain_ = sendGain;
            return;
        }

        const float invFrames = 1.0F / static_cast<float>(frames);
        const float startDry = prevDryGain_;
        const float dryStep = (dryGain - startDry) * invFrames;
        const float startSend = prevSendGain_;
        const float sendStep = (sendGain - startSend) * invFrames;

        std::size_t writeIdx = writeIndex_;
        const std::size_t mask = capacityMask_;
        const std::size_t delay = delayFrames_;
        float filtL = filterState_[0];
        float filtR = filterState_[1];
        const float damp = dampFactor_;
        const float fbGain = feedbackGain_;
        float* __restrict const buf = buffer_.data();

        for (std::size_t f = 0; f < frames; ++f) {
            const float stepN = static_cast<float>(f + 1U);
            const float curDry = startDry + dryStep * stepN;
            const float curSend = enabled_ ? (startSend + sendStep * stepN) : 0.0F;

            const std::size_t readPos = (writeIdx - delay) & mask;
            const float echoL = buf[readPos * 2U];
            const float echoR = buf[readPos * 2U + 1U];

            filtL += damp * (echoL - filtL);
            filtR += damp * (echoR - filtR);

            const std::size_t off = f * 2U;
            const float inL = std::isfinite(stereoInterleaved[off]) ? stereoInterleaved[off] : 0.0F;
            const float inR = std::isfinite(stereoInterleaved[off + 1U]) ? stereoInterleaved[off + 1U] : 0.0F;

            float fbWriteL = inL * curSend + filtL * fbGain;
            float fbWriteR = inR * curSend + filtR * fbGain;
            if (std::abs(fbWriteL) < 1.0e-20F) fbWriteL = 0.0F;
            if (std::abs(fbWriteR) < 1.0e-20F) fbWriteR = 0.0F;

            buf[writeIdx * 2U] = fbWriteL;
            buf[writeIdx * 2U + 1U] = fbWriteR;
            writeIdx = (writeIdx + 1U) & mask;

            stereoInterleaved[off] = inL * curDry + echoL * 0.75F;
            stereoInterleaved[off + 1U] = inR * curDry + echoR * 0.75F;
        }

        writeIndex_ = writeIdx;
        filterState_[0] = (!std::isfinite(filtL) || std::abs(filtL) < 1.0e-20F) ? 0.0F : filtL;
        filterState_[1] = (!std::isfinite(filtR) || std::abs(filtR) < 1.0e-20F) ? 0.0F : filtR;
        prevDryGain_ = dryGain;
        prevSendGain_ = enabled_ ? sendGain : 0.0F;
    }

private:
    static constexpr double kPi = 3.1415926535897932384626433832795;

    static constexpr std::size_t roundUpPow2(std::size_t v) noexcept {
        if (v <= 2U) return 2U;
        --v;
        for (std::size_t s = 1U; s < sizeof(std::size_t) * 8U; s <<= 1U) v |= (v >> s);
        return v + 1U;
    }

    double sampleRate_{48000.0};
    std::size_t delayFrames_{18000};
    float feedbackGain_{0.3F};
    float dampFactor_{0.45F};
    bool enabled_{false};

    std::vector<float> buffer_;
    std::size_t writeIndex_{0};
    std::array<float, 2> filterState_{0.0F, 0.0F};

    std::size_t capacityFrames_{0};
    std::size_t capacityMask_{0};
    float prevDryGain_{1.0F};
    float prevSendGain_{0.0F};
    bool tailActive_{false};
};

} // namespace neonsong::audio
