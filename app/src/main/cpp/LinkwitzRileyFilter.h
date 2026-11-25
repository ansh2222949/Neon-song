#pragma once

#include <cmath>
#include <cstddef>
#include <array>
#include <algorithm>

namespace neonsong::audio {

class LinkwitzRileyFilter final {
public:
    struct Biquad final {
        double b0{1.0};
        double b1{0.0};
        double b2{0.0};
        double a1{0.0};
        double a2{0.0};
        std::array<double, 2> z1{0.0, 0.0};
        std::array<double, 2> z2{0.0, 0.0};

        inline void clear() noexcept {
            z1.fill(0.0);
            z2.fill(0.0);
        }

        inline void sanitize() noexcept {
            for (std::size_t ch = 0; ch < 2U; ++ch) {
                if (!std::isfinite(z1[ch]) || std::abs(z1[ch]) < 1.0e-20) z1[ch] = 0.0;
                if (!std::isfinite(z2[ch]) || std::abs(z2[ch]) < 1.0e-20) z2[ch] = 0.0;
            }
        }

        [[nodiscard]] inline double tickDouble(double x, std::size_t channel) noexcept {
            const double out = b0 * x + z1[channel];
            z1[channel] = b1 * x - a1 * out + z2[channel];
            z2[channel] = b2 * x - a2 * out;
            return out;
        }
    };

    LinkwitzRileyFilter() noexcept {
        configure(48000.0, 160.0);
    }

    void configure(double sampleRate, double crossoverHz = 160.0) noexcept {
        if (!std::isfinite(sampleRate) || sampleRate < 8000.0 || sampleRate > 384000.0) {
            sampleRate = 48000.0;
        }
        if (!std::isfinite(crossoverHz)) crossoverHz = 160.0;
        sampleRate_ = sampleRate;
        crossoverHz_ = std::clamp(crossoverHz, 20.0, sampleRate_ * 0.45);

        const double omega = 2.0 * kPi * crossoverHz_ / sampleRate_;
        const double sinOmega = std::sin(omega);
        const double cosOmega = std::cos(omega);
        // Butterworth Q = 1/sqrt(2)
        constexpr double q = 0.7071067811865475;
        const double alpha = sinOmega / (2.0 * q);

        // 2nd-order Butterworth Low-Pass
        const double a0Lp = 1.0 + alpha;
        const double b0Lp = ((1.0 - cosOmega) * 0.5) / a0Lp;
        const double b1Lp = (1.0 - cosOmega) / a0Lp;
        const double b2Lp = ((1.0 - cosOmega) * 0.5) / a0Lp;
        const double a1Lp = (-2.0 * cosOmega) / a0Lp;
        const double a2Lp = (1.0 - alpha) / a0Lp;

        // 2nd-order Butterworth High-Pass
        const double a0Hp = 1.0 + alpha;
        const double b0Hp = ((1.0 + cosOmega) * 0.5) / a0Hp;
        const double b1Hp = (-(1.0 + cosOmega)) / a0Hp;
        const double b2Hp = ((1.0 + cosOmega) * 0.5) / a0Hp;
        const double a1Hp = (-2.0 * cosOmega) / a0Hp;
        const double a2Hp = (1.0 - alpha) / a0Hp;

        // Cascade two Butterworth filters to create 4th-order Linkwitz-Riley (LR4)
        for (std::size_t i = 0; i < 2U; ++i) {
            lpStage_[i].b0 = b0Lp;
            lpStage_[i].b1 = b1Lp;
            lpStage_[i].b2 = b2Lp;
            lpStage_[i].a1 = a1Lp;
            lpStage_[i].a2 = a2Lp;

            hpStage_[i].b0 = b0Hp;
            hpStage_[i].b1 = b1Hp;
            hpStage_[i].b2 = b2Hp;
            hpStage_[i].a1 = a1Hp;
            hpStage_[i].a2 = a2Hp;
        }
        reset();
    }

    void reset() noexcept {
        for (std::size_t i = 0; i < 2U; ++i) {
            lpStage_[i].clear();
            hpStage_[i].clear();
        }
    }

    void processHighPass(float* stereo, std::size_t frames) noexcept {
        if (stereo == nullptr || frames == 0) return;
        processCascadeStereo(hpStage_, stereo, frames);
    }

    void processLowPass(float* stereo, std::size_t frames) noexcept {
        if (stereo == nullptr || frames == 0) return;
        processCascadeStereo(lpStage_, stereo, frames);
    }

    // Splits interleaved stereo input into low (<crossover) and high (>=crossover) stereo streams
    void processSplit(
        const float* inStereo,
        float* outLowStereo,
        float* outHighStereo,
        std::size_t frames) noexcept {
        if (inStereo == nullptr || frames == 0) return;
        for (std::size_t f = 0; f < frames; ++f) {
            const std::size_t off = f * 2U;
            for (std::size_t ch = 0; ch < 2U; ++ch) {
                const float raw = inStereo[off + ch];
                const double x = std::isfinite(raw) ? static_cast<double>(raw) : 0.0;
                if (outLowStereo != nullptr) {
                    outLowStereo[off + ch] = static_cast<float>(
                        lpStage_[1].tickDouble(lpStage_[0].tickDouble(x, ch), ch));
                }
                if (outHighStereo != nullptr) {
                    outHighStereo[off + ch] = static_cast<float>(
                        hpStage_[1].tickDouble(hpStage_[0].tickDouble(x, ch), ch));
                }
            }
        }
        for (std::size_t i = 0; i < 2U; ++i) {
            if (outLowStereo != nullptr) lpStage_[i].sanitize();
            if (outHighStereo != nullptr) hpStage_[i].sanitize();
        }
    }

    [[nodiscard]] double crossoverFrequency() const noexcept {
        return crossoverHz_;
    }

private:
    static constexpr double kPi = 3.1415926535897932384626433832795;

    static inline void processCascadeStereo(
        std::array<Biquad, 2>& stages,
        float* __restrict stereo,
        std::size_t frames) noexcept {
        const double b0 = stages[0].b0, b1 = stages[0].b1, b2 = stages[0].b2;
        const double a1 = stages[0].a1, a2 = stages[0].a2;
        double z10L = stages[0].z1[0], z20L = stages[0].z2[0];
        double z10R = stages[0].z1[1], z20R = stages[0].z2[1];
        double z11L = stages[1].z1[0], z21L = stages[1].z2[0];
        double z11R = stages[1].z1[1], z21R = stages[1].z2[1];

        for (std::size_t f = 0; f < frames; ++f) {
            float* p = stereo + f * 2U;
            const double inL = std::isfinite(p[0]) ? static_cast<double>(p[0]) : 0.0;
            const double y0L = b0 * inL + z10L;
            z10L = b1 * inL - a1 * y0L + z20L;
            z20L = b2 * inL - a2 * y0L;
            const double y1L = b0 * y0L + z11L;
            z11L = b1 * y0L - a1 * y1L + z21L;
            z21L = b2 * y0L - a2 * y1L;
            p[0] = static_cast<float>(y1L);

            const double inR = std::isfinite(p[1]) ? static_cast<double>(p[1]) : 0.0;
            const double y0R = b0 * inR + z10R;
            z10R = b1 * inR - a1 * y0R + z20R;
            z20R = b2 * inR - a2 * y0R;
            const double y1R = b0 * y0R + z11R;
            z11R = b1 * y0R - a1 * y1R + z21R;
            z21R = b2 * y0R - a2 * y1R;
            p[1] = static_cast<float>(y1R);
        }

        stages[0].z1[0] = z10L; stages[0].z2[0] = z20L;
        stages[0].z1[1] = z10R; stages[0].z2[1] = z20R;
        stages[1].z1[0] = z11L; stages[1].z2[0] = z21L;
        stages[1].z1[1] = z11R; stages[1].z2[1] = z21R;
        stages[0].sanitize();
        stages[1].sanitize();
    }

    double sampleRate_{48000.0};
    double crossoverHz_{160.0};
    std::array<Biquad, 2> lpStage_{};
    std::array<Biquad, 2> hpStage_{};
};

} // namespace neonsong::audio
