#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstdint>

#include "ParametricEq.h"

namespace neonsong::audio {

class DspProcessor final {
public:
    static constexpr std::size_t kEqualizerBandCount = 16;
    static constexpr std::size_t kMaxChannels = 12; // 7.1.4 Dolby Atmos (FL,FR,FC,LFE,BL,BR,SL,SR,TFL,TFR,TBL,TBR)

    // Anthropometric 3D HRTF Head Radius Scale bounds & presets:
    // 0.88 = Small (~7.7 cm), 1.00 = KEMAR Standard (8.75 cm), 1.12 = Wide (~9.8 cm)
    static constexpr float kHrtfHeadScaleMin = 0.85F;
    static constexpr float kHrtfHeadScaleDefault = 1.00F;
    static constexpr float kHrtfHeadScaleMax = 1.18F;
    static constexpr float kHrtfHeadPresetSmall = 0.88F;
    static constexpr float kHrtfHeadPresetStandard = 1.00F;
    static constexpr float kHrtfHeadPresetWide = 1.12F;

    // 3D Vocal vs. Instrument Separation bounds & presets:
    // 0.40 = Natural Blend, 0.75 = Studio 3D Split (Default), 1.00 = Holographic Isolation
    static constexpr float kVocalSeparationMin = 0.00F;
    static constexpr float kVocalSeparationDefault = 0.75F;
    static constexpr float kVocalSeparationMax = 1.00F;
    static constexpr float kVocalSeparationPresetNatural = 0.40F;
    static constexpr float kVocalSeparationPresetStudio = 0.75F;
    static constexpr float kVocalSeparationPresetHolographic = 1.00F;

    DspProcessor() noexcept;
    ~DspProcessor();

    void configure(double sampleRate) noexcept;
    void reset() noexcept;
    void setStudioMasterClarity(bool enabled) noexcept;
    void setBitPerfect(bool enabled) noexcept;
    void setPeakProtectionEnabled(bool enabled) noexcept;
    void setBauerCrossfeedEnabled(bool enabled) noexcept;
    void setAnalogTubeWarmthEnabled(bool enabled) noexcept;
    void setMonoBassEnabled(bool enabled) noexcept;
    void setSpatialImmersionEnabled(bool enabled) noexcept;
    void setSpatialImmersionStrength(float strength) noexcept;
    void setHrtfHeadScale(float scale) noexcept;
    void setVocalInstrumentSeparation(float separation) noexcept;
    static void broadcastSpatialImmersionEnabled(bool enabled);
    static void broadcastSpatialImmersionStrength(float strength);
    static void broadcastHrtfHeadScale(float scale);
    static void broadcastVocalInstrumentSeparation(float separation);

    [[nodiscard]] bool isSpatialImmersionEnabled() const noexcept {
        return targetSpatialImmersionEnabled_.load(std::memory_order_acquire);
    }

    [[nodiscard]] float getHrtfHeadScale() const noexcept {
        return targetHrtfHeadScale_.load(std::memory_order_acquire);
    }

    [[nodiscard]] float getVocalInstrumentSeparation() const noexcept {
        return targetVocalInstrumentSeparation_.load(std::memory_order_acquire);
    }

    void setEqualizer(
        bool enabled,
        const float* gainsDb,
        std::size_t gainCount) noexcept;
    static constexpr std::size_t kClarityTrimCount = 8;
    static constexpr int kClarityPresetReference = 0;
    static constexpr int kClarityPresetSpeaker = 1;
    static constexpr int kClarityPresetHeadphone = 2;
    static constexpr int kClarityPresetDac = 3;
    void setClarityWet(float wet) noexcept;
    void setClarityTrims(const float* trimsDb, std::size_t trimCount) noexcept;
    void setClarityPreset(int preset) noexcept;
    void setClarityAtmosBypass(bool bypass) noexcept;
    static void broadcastClarityWet(float wet);
    static void broadcastClarityTrims(const float* trimsDb, std::size_t trimCount);
    static void broadcastClarityPreset(int preset);
    static void broadcastClarityAtmosBypass(bool bypass);
    void process(
        float* interleaved,
        std::int32_t frameCount,
        std::int32_t channelCount = 2) noexcept;

    [[nodiscard]] bool isStudioMasterClarityEnabled() const noexcept {
        return targetEnabled_.load(std::memory_order_acquire);
    }

    [[nodiscard]] bool isBitPerfectEnabled() const noexcept {
        return bitPerfectEnabled_.load(std::memory_order_acquire);
    }

    void consumeHapticTelemetry(float& outBassPeak, float& outTransientPeak) noexcept;

private:
    struct Biquad final {
        double b0{1.0};
        double b1{0.0};
        double b2{0.0};
        double a1{0.0};
        double a2{0.0};
        std::array<double, kMaxChannels> z1{};
        std::array<double, kMaxChannels> z2{};

        static Biquad highPass(double sampleRate, double frequency, double q) noexcept;
        static Biquad peaking(
            double sampleRate,
            double frequency,
            double q,
            double gainDb) noexcept;
        static Biquad highShelf(
            double sampleRate,
            double frequency,
            double slope,
            double gainDb) noexcept;
        void setPeaking(
            double sampleRate,
            double frequency,
            double q,
            double gainDb) noexcept;
        void setHighShelf(
            double sampleRate,
            double frequency,
            double slope,
            double gainDb) noexcept;

        [[nodiscard]] inline float tick(float input, std::size_t channel) noexcept {
            const double value = static_cast<double>(input);
            const double output = b0 * value + z1[channel];
            z1[channel] = b1 * value - a1 * output + z2[channel];
            z2[channel] = b2 * value - a2 * output;
            return static_cast<float>(output);
        }

        inline void sanitizeState(std::size_t activeChannels) noexcept {
            for (std::size_t ch = 0; ch < activeChannels; ++ch) {
                if (!std::isfinite(z1[ch]) || !std::isfinite(z2[ch])) {
                    z1[ch] = 0.0;
                    z2[ch] = 0.0;
                } else {
                    if (std::abs(z1[ch]) < 1.0e-20) z1[ch] = 0.0;
                    if (std::abs(z2[ch]) < 1.0e-20) z2[ch] = 0.0;
                }
            }
        }

        [[nodiscard]] double magnitude(double sampleRate, double frequency) const noexcept;

        inline void clear() noexcept {
            z1.fill(0.0);
            z2.fill(0.0);
        }
    };

    struct Crossfeed final {
        double a0Low{0.0};
        double b1Low{0.0};
        double a0High{1.0};
        double a1High{0.0};
        double b1High{0.0};
        double gain{1.0};
        std::array<double, 2> low{};
        std::array<double, 2> high{};
        std::array<double, 2> previousInput{};

        void configure(double sampleRate, double cutoffHz, double levelDb) noexcept;

        inline void process(float& left, float& right) noexcept {
            const double inputLeft = static_cast<double>(left);
            const double inputRight = static_cast<double>(right);
            low[0] = a0Low * inputLeft + b1Low * low[0];
            low[1] = a0Low * inputRight + b1Low * low[1];
            high[0] = a0High * inputLeft + a1High * previousInput[0] + b1High * high[0];
            high[1] = a0High * inputRight + a1High * previousInput[1] + b1High * high[1];
            previousInput[0] = inputLeft;
            previousInput[1] = inputRight;

            if (std::abs(low[0]) < 1.0e-20) low[0] = 0.0;
            if (std::abs(low[1]) < 1.0e-20) low[1] = 0.0;
            if (std::abs(high[0]) < 1.0e-20) high[0] = 0.0;
            if (std::abs(high[1]) < 1.0e-20) high[1] = 0.0;

            left = static_cast<float>((high[0] + low[1]) * gain);
            right = static_cast<float>((high[1] + low[0]) * gain);
        }

        inline void clear() noexcept {
            low.fill(0.0);
            high.fill(0.0);
            previousInput.fill(0.0);
        }
    };

    double sampleRate_{48000.0};
    float currentWet_{0.0F};
    float currentCrossfeedWet_{0.0F};
    float currentTubeWarmthWet_{0.0F};
    float currentMonoBassWet_{0.0F};
    float rampPerFrame_{1.0F / 2400.0F};
    std::atomic<bool> targetEnabled_{false};
    std::atomic<bool> bitPerfectEnabled_{false};
    std::atomic<bool> peakProtectionEnabled_{false};
    std::atomic<bool> targetEqualizerEnabled_{false};
    std::atomic<bool> targetBauerCrossfeedEnabled_{false};
    std::atomic<bool> targetAnalogTubeWarmthEnabled_{false};
    std::atomic<bool> targetMonoBassEnabled_{false};
    std::atomic<std::uint32_t> targetEqualizerRevision_{0};
    std::array<std::atomic<float>, kEqualizerBandCount> targetEqGainsDb_{};
    std::array<float, kEqualizerBandCount> currentEqGainsDb_{};
    std::array<Biquad, kEqualizerBandCount> equalizerBands_{};
    std::int32_t equalizerUpdateCountdown_{0};
    std::int32_t equalizerHeadroomCountdown_{0};
    std::uint32_t appliedEqualizerRevision_{0};
    std::uint32_t activeEqualizerBands_{0};
    float currentPreampDb_{0.0F};
    float currentPreampGain_{1.0F};
    float equalizerMaximumBoostDb_{0.0F};
    float limiterGain_{1.0F};
    float equalizerGainSmoothing_{0.1F};
    float limiterRelease_{0.001F};
    float dcBlockerR_{0.999F};
    std::array<double, kMaxChannels> dcXPrev_{};
    std::array<double, kMaxChannels> dcYPrev_{};
    float tubeDcBlockerR_{0.999F};
    std::array<double, kMaxChannels> tubeDcXPrev_{};
    std::array<double, kMaxChannels> tubeDcYPrev_{};
    std::int32_t microFadeFrameCount_{96};
    std::int32_t microFadePosition_{0};
    bool clarityChainActive_{false};

    float transientFastAttack_{0.016F};
    float transientSlowAttack_{0.0008F};
    float transientRelease_{0.0003F};
    float transientFastEnv_{0.0F};
    float transientSlowEnv_{0.0F};
    float tubeLowPassAlpha_{0.028F};
    float hapticSubAlpha_{0.012F};
    std::array<float, kMaxChannels> tubeLowPassState_{};
    std::array<float, kMaxChannels> claritySubState_{};
    std::atomic<float> hapticBassPeak_{0.0F};
    std::atomic<float> hapticTransientPeak_{0.0F};
    float hapticSubState_{0.0F};

    std::atomic<float> targetClarityWet_{1.0F};
    float currentClarityWet_{1.0F};
    std::array<std::atomic<float>, kClarityTrimCount> targetClarityTrimsDb_{};
    std::array<float, kClarityTrimCount> currentClarityTrimsDb_{};
    std::array<float, kClarityTrimCount> appliedClarityTrimsDb_{};
    std::array<float, kClarityTrimCount> clarityTrimLinear_{
        1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
    float clarityExciterAmount_{0.18F};
    std::atomic<bool> atmosBypassEnabled_{false};
    Biquad subBassHighPass_{};
    Biquad bassFoundation_{};
    Biquad lowMidSeparation_{};
    Biquad boxinessControl_{};
    Biquad presenceDetail_{};
    Biquad airDetail_{};
    Biquad monoBassFilter_{};
    Biquad airExciterFilter_{};
    Crossfeed crossfeed_{};

    struct SpatialImmersionStage final {
        static constexpr std::size_t kMaxDelaySamples = 4096U;
        static constexpr std::size_t kDelayMask = kMaxDelaySamples - 1U;
        static constexpr std::size_t kItdBufferSize = 256U;
        static constexpr std::size_t kItdMask = kItdBufferSize - 1U;

        float bassAlpha{0.023F};
        float lfeAlpha{0.012F};
        float vocalLowAlpha{0.041F};   // 310 Hz lower vocal formant boundary
        float vocalHighAlpha{0.310F};  // 3500 Hz upper vocal formant boundary
        float presenceAlpha{0.220F};   // 2200 Hz vocal articulation focus pole
        float airAlpha{0.32F};
        float hfDampAlpha{0.42F};
        float headShadowAlpha{0.22F};
        float rearShadowAlpha{0.46F};
        float pinnaG{0.72F};
        float pinnaA1{0.5F};
        float pinnaA2{0.36F};
        float pinnaA3{0.26F};
        float pinnaK{1.0F / 2.2F};
        float vocalEnvAlpha{0.004F};

        float hrtfHeadScale{kHrtfHeadScaleDefault};

        float itdSideSamples{31.47F};
        float itdRearSamples{18.27F};
        float itdHeightSamples{12.92F};
        std::size_t itdWriteIdx{0U};

        std::array<std::array<float, kItdBufferSize>, 4> itdDelayL{};
        std::array<std::array<float, kItdBufferSize>, 4> itdDelayR{};
        std::array<float, 4> contraShadowL{};
        std::array<float, 4> contraShadowR{};
        std::array<float, 4> rearShadowL{};
        std::array<float, 4> rearShadowR{};
        std::array<float, 4> pinnaIc1EqL{};
        std::array<float, 4> pinnaIc2EqL{};
        std::array<float, 4> pinnaIc1EqR{};
        std::array<float, 4> pinnaIc2EqR{};
        std::array<float, 4> atmosDecorApL{};
        std::array<float, 4> atmosDecorApR{};

        float lowSideState{0.0F};
        float midBassState{0.0F};
        float vocalLowState{0.0F};
        float vocalHighState{0.0F};
        float vocalPresenceState{0.0F};
        float lfeRecoverState{0.0F};
        float lfeHarmonicState{0.0F};
        float centerPresenceState{0.0F};
        float centerVocalLowState{0.0F};
        float centerVocalHighState{0.0F};
        float centerEnvState{0.0F};
        float airShelfState{0.0F};
        float sidePresenceStateL{0.0F};
        float sidePresenceStateR{0.0F};
        float dampStateL{0.0F};
        float dampStateR{0.0F};
        float midVocalEnv{0.0F};
        float heightApStateL{0.0F};
        float heightApStateR{0.0F};
        float pinnaIc1EqLState{0.0F};
        float pinnaIc2EqLState{0.0F};
        float pinnaIc1EqRState{0.0F};
        float pinnaIc2EqRState{0.0F};
        std::array<float, 4> surroundPairStates{};
        float apStateL{0.0F};
        float apStateR{0.0F};
        std::size_t delaySamplesL{542U};
        std::size_t delaySamplesR{859U};
        std::size_t delayTapRearL{331U};
        std::size_t delayTapRearR{523U};
        std::size_t delayTapHeightL{197U};
        std::size_t delayTapHeightR{307U};
        std::size_t delayTapSideL{127U};
        std::size_t delayTapSideR{193U};
        std::size_t writeIdx{0U};
        std::array<float, kMaxDelaySamples> haasBufferL{};
        std::array<float, kMaxDelaySamples> haasBufferR{};

        inline void updateHeadScale(double sampleRate, float headSizeScale) noexcept {
            const double sr = std::max(sampleRate, 8000.0);
            constexpr double kTwoPi = 6.2831853071795864769;
            hrtfHeadScale = std::clamp(headSizeScale, kHrtfHeadScaleMin, kHrtfHeadScaleMax);
            const double scale = static_cast<double>(hrtfHeadScale);

            headShadowAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * (1750.0 / scale) / sr));
            rearShadowAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * (4400.0 / scale) / sr));

            const double notchHz = std::min((7200.0 / scale), sr * 0.42);
            pinnaG = static_cast<float>(std::tan(3.14159265358979323846 * notchHz / sr));
            pinnaK = 1.0F / 2.2F;
            pinnaA1 = 1.0F / (1.0F + pinnaG * (pinnaG + pinnaK));
            pinnaA2 = pinnaG * pinnaA1;
            pinnaA3 = pinnaG * pinnaA2;

            const float maxItd = static_cast<float>(kItdMask - 2U);
            itdSideSamples = std::clamp(static_cast<float>(sr * 0.0006557 * scale), 1.0F, maxItd);
            itdRearSamples = std::clamp(static_cast<float>(sr * 0.0003807 * scale), 1.0F, maxItd);
            itdHeightSamples = std::clamp(static_cast<float>(sr * 0.0002692 * scale), 1.0F, maxItd);
        }

        void configure(double sampleRate, float headSizeScale = kHrtfHeadScaleDefault) noexcept {
            const double sr = std::max(sampleRate, 8000.0);
            constexpr double kTwoPi = 6.2831853071795864769;

            bassAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 180.0 / sr));
            lfeAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 95.0 / sr));
            vocalLowAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 310.0 / sr));
            vocalHighAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 3500.0 / sr));
            presenceAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 2200.0 / sr));
            airAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 3600.0 / sr));
            hfDampAlpha = static_cast<float>(1.0 - std::exp(-kTwoPi * 4200.0 / sr));
            vocalEnvAlpha = static_cast<float>(1.0 - std::exp(-1.0 / (sr * 0.012)));

            delaySamplesL = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0127)), 1U, kDelayMask);
            delaySamplesR = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0193)), 1U, kDelayMask);
            delayTapRearL = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0079)), 1U, kDelayMask);
            delayTapRearR = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0119)), 1U, kDelayMask);
            delayTapHeightL = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0046)), 1U, kDelayMask);
            delayTapHeightR = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0071)), 1U, kDelayMask);
            delayTapSideL = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0027)), 1U, kDelayMask);
            delayTapSideR = std::clamp<std::size_t>(
                static_cast<std::size_t>(std::llround(sr * 0.0039)), 1U, kDelayMask);

            updateHeadScale(sr, headSizeScale);
            clear();
        }

        inline void clear() noexcept {
            lowSideState = 0.0F;
            midBassState = 0.0F;
            vocalLowState = 0.0F;
            vocalHighState = 0.0F;
            vocalPresenceState = 0.0F;
            lfeRecoverState = 0.0F;
            lfeHarmonicState = 0.0F;
            centerPresenceState = 0.0F;
            centerVocalLowState = 0.0F;
            centerVocalHighState = 0.0F;
            centerEnvState = 0.0F;
            airShelfState = 0.0F;
            sidePresenceStateL = 0.0F;
            sidePresenceStateR = 0.0F;
            dampStateL = 0.0F;
            dampStateR = 0.0F;
            midVocalEnv = 0.0F;
            heightApStateL = 0.0F;
            heightApStateR = 0.0F;
            pinnaIc1EqLState = 0.0F;
            pinnaIc2EqLState = 0.0F;
            pinnaIc1EqRState = 0.0F;
            pinnaIc2EqRState = 0.0F;
            surroundPairStates.fill(0.0F);
            contraShadowL.fill(0.0F);
            contraShadowR.fill(0.0F);
            rearShadowL.fill(0.0F);
            rearShadowR.fill(0.0F);
            pinnaIc1EqL.fill(0.0F);
            pinnaIc2EqL.fill(0.0F);
            pinnaIc1EqR.fill(0.0F);
            pinnaIc2EqR.fill(0.0F);
            atmosDecorApL.fill(0.0F);
            atmosDecorApR.fill(0.0F);
            for (auto& buf : itdDelayL) buf.fill(0.0F);
            for (auto& buf : itdDelayR) buf.fill(0.0F);
            itdWriteIdx = 0U;
            apStateL = 0.0F;
            apStateR = 0.0F;
            writeIdx = 0U;
            haasBufferL.fill(0.0F);
            haasBufferR.fill(0.0F);
        }

        [[nodiscard]] inline float readFractionalItd(
            const std::array<float, kItdBufferSize>& buffer,
            float delaySamples) const noexcept {
            const std::size_t intDelay = static_cast<std::size_t>(delaySamples);
            const float frac = delaySamples - static_cast<float>(intDelay);
            const std::size_t idx0 = (itdWriteIdx - intDelay) & kItdMask;
            const std::size_t idx1 = (idx0 - 1U) & kItdMask;
            return buffer[idx0] + frac * (buffer[idx1] - buffer[idx0]);
        }

        [[nodiscard]] inline float processPinna(
            float input,
            float& ic1eq,
            float& ic2eq,
            bool elevated) const noexcept {
            const float v3 = input - ic2eq;
            const float band = pinnaA1 * ic1eq + pinnaA2 * v3;
            const float low = ic2eq + pinnaA2 * ic1eq + pinnaA3 * v3;
            ic1eq = 2.0F * band - ic1eq;
            ic2eq = 2.0F * low - ic2eq;
            const float high = input - (pinnaK * band) - low;
            return elevated
                ? (high + low) + (0.62F * high) - (0.28F * band)
                : input - (0.48F * band) + (0.14F * high);
        }

        inline void renderBinauralPair(
            std::size_t pair,
            float inL,
            float inR,
            float& outBusL,
            float& outBusR,
            float& outIpsL,
            float& outIpsR) noexcept {
            itdDelayL[pair][itdWriteIdx] = inL;
            itdDelayR[pair][itdWriteIdx] = inR;

            const float itdTap = (pair == 0U) ? itdRearSamples
                : (pair == 1U) ? itdSideSamples
                : itdHeightSamples;
            const float contraDelayedFromL = readFractionalItd(itdDelayL[pair], itdTap);
            const float contraDelayedFromR = readFractionalItd(itdDelayR[pair], itdTap);

            contraShadowR[pair] += headShadowAlpha * (contraDelayedFromL - contraShadowR[pair]);
            contraShadowL[pair] += headShadowAlpha * (contraDelayedFromR - contraShadowL[pair]);
            const float hfShadowFactor = (pair == 1U) ? 0.14F : (pair == 0U ? 0.24F : 0.32F);
            const float diffractedToR =
                contraShadowR[pair] + hfShadowFactor * (contraDelayedFromL - contraShadowR[pair]);
            const float diffractedToL =
                contraShadowL[pair] + hfShadowFactor * (contraDelayedFromR - contraShadowL[pair]);

            float shapedInL = inL;
            float shapedInR = inR;
            if (pair == 0U || pair == 3U) {
                rearShadowL[pair] += rearShadowAlpha * (inL - rearShadowL[pair]);
                rearShadowR[pair] += rearShadowAlpha * (inR - rearShadowR[pair]);
                const float rearBlend = (pair == 0U) ? 0.48F : 0.62F;
                shapedInL = rearShadowL[pair] + (inL - rearShadowL[pair]) * rearBlend;
                shapedInR = rearShadowR[pair] + (inR - rearShadowR[pair]) * rearBlend;
            }

            const bool elevated = (pair >= 2U);
            outIpsL = processPinna(shapedInL, pinnaIc1EqL[pair], pinnaIc2EqL[pair], elevated);
            outIpsR = processPinna(shapedInR, pinnaIc1EqR[pair], pinnaIc2EqR[pair], elevated);

            const float crossWeight = (pair == 1U) ? 0.26F : (pair == 0U ? 0.30F : 0.24F);
            const float highDiffL = contraDelayedFromR - contraShadowL[pair];
            const float highDiffR = contraDelayedFromL - contraShadowR[pair];
            outBusL += outIpsL + crossWeight * (diffractedToL - 0.65F * highDiffL);
            outBusR += outIpsR + crossWeight * (diffractedToR - 0.65F * highDiffR);
        }

        // Virtual 12-Channel (7.1.4) Holographic Bed Upmixer with dedicated Vocal/Instrument Separation:
        // `vocalSeparation` (0..1) controls how aggressively the 310 Hz - 3.5 kHz vocal core is anchored
        // forward at 0° FC while non-vocal mid/side instruments are steered into the 12ch surround field.
        inline void process(
            float& left,
            float& right,
            float wet,
            float strength,
            float vocalSeparation,
            bool isNativeAtmos,
            bool synthesizeBinauralBed = true) noexcept {
            const float rawStrength = std::clamp(strength, 0.0F, 1.0F);
            const float curvedStrength = rawStrength * (1.35F - 0.35F * rawStrength);
            const float intensity = wet * curvedStrength;
            if (intensity <= 0.0001F) return;

            const float sep = std::clamp(vocalSeparation, kVocalSeparationMin, kVocalSeparationMax);
            const float dryL = left;
            const float dryR = right;

            // 1. Decompose Mid into LFE (<180 Hz), Vocal Formant Core (310 Hz - 3.5 kHz), & Mid Instruments
            const float rawMid = 0.5F * (dryL + dryR);
            midBassState += bassAlpha * (rawMid - midBassState);
            if (std::abs(midBassState) < 1.0e-20F) midBassState = 0.0F;

            lfeHarmonicState += lfeAlpha * (midBassState - lfeHarmonicState);
            if (std::abs(lfeHarmonicState) < 1.0e-20F) lfeHarmonicState = 0.0F;
            const float subPunch = std::clamp(lfeHarmonicState, -0.85F, 0.85F);
            const float subHarmonic =
                (subPunch - 0.333333F * subPunch * subPunch * subPunch) * (0.115F * intensity);
            const float virtualLfe = midBassState * (1.0F + 0.14F * intensity) + subHarmonic;

            const float midAboveBass = rawMid - midBassState;
            vocalLowState += vocalLowAlpha * (midAboveBass - vocalLowState);
            if (std::abs(vocalLowState) < 1.0e-20F) vocalLowState = 0.0F;
            const float midWarmInst = vocalLowState; // 180 Hz - 310 Hz instrument body
            const float midAbove310 = midAboveBass - vocalLowState;

            vocalHighState += vocalHighAlpha * (midAbove310 - vocalHighState);
            if (std::abs(vocalHighState) < 1.0e-20F) vocalHighState = 0.0F;
            const float vocalCoreRaw = vocalHighState; // 310 Hz - 3.5 kHz lead vocal formants
            const float midHighInst = midAbove310 - vocalHighState; // >3.5 kHz harmonic transients

            // Scale 2.2 kHz vocal presence clarity & forward anchor by `sep`
            vocalPresenceState += presenceAlpha * (vocalCoreRaw - vocalPresenceState);
            if (std::abs(vocalPresenceState) < 1.0e-20F) vocalPresenceState = 0.0F;
            const float vocalArticulation = vocalCoreRaw - vocalPresenceState;
            const float vocalFocusLift = (0.08F + 0.22F * sep) * intensity;
            const float vocalArticulationLift = (0.12F + 0.32F * sep) * intensity;
            const float vocalCenterFocused =
                vocalCoreRaw * (1.0F + vocalFocusLift) + vocalArticulation * vocalArticulationLift;

            midVocalEnv += vocalEnvAlpha * (std::abs(vocalCoreRaw) - midVocalEnv);
            if (std::abs(midVocalEnv) < 1.0e-20F) midVocalEnv = 0.0F;
            const float vocalActivity = std::clamp(midVocalEnv * (1.8F + 1.2F * sep), 0.0F, 1.0F);
            const float vocalDuck = 1.0F / (1.0F + (0.90F + 0.95F * sep) * midVocalEnv);

            // Recess non-vocal mid instruments from dead-center proportional to `sep` as they move to 3D surround
            const float centerInstRecess = 1.0F - ((0.10F + 0.28F * sep) * intensity);
            const float mid = virtualLfe + vocalCenterFocused +
                (midWarmInst + midHighInst) * centerInstRecess;

            // 2. Decompose Side & carve a dynamic vocal-formant pocket in the side bed proportional to `sep`
            const float side = 0.5F * (dryL - dryR);
            lowSideState += bassAlpha * (side - lowSideState);
            if (std::abs(lowSideState) < 1.0e-20F) lowSideState = 0.0F;
            const float sideLow = lowSideState * (1.0F - 0.28F * intensity);
            const float sideHigh = side - lowSideState;

            airShelfState += airAlpha * (sideHigh - airShelfState);
            if (std::abs(airShelfState) < 1.0e-20F) airShelfState = 0.0F;
            const float sideBody = airShelfState;
            const float sideAir = sideHigh - airShelfState;

            sidePresenceStateL += presenceAlpha * (sideBody - sidePresenceStateL);
            if (std::abs(sidePresenceStateL) < 1.0e-20F) sidePresenceStateL = 0.0F;
            const float sideVocalBand = sideBody - sidePresenceStateL;
            const float pocketDepth = (0.10F + 0.34F * sep) * intensity * vocalActivity;
            const float cleanSideBody = sideBody - sideVocalBand * pocketDepth;

            // 3. Front Left / Front Right (+-30 deg) + Pinna Elevation Shimmer
            const float instSpreadScale = 0.65F + 0.55F * sep;
            const float heightExciterInL = sideAir + (0.38F * instSpreadScale) * midHighInst;
            const float heightExciterInR = -sideAir + (0.38F * instSpreadScale) * midHighInst;
            const float pinnaShapedL = processPinna(
                heightExciterInL, pinnaIc1EqLState, pinnaIc2EqLState, true);
            const float pinnaShapedR = processPinna(
                heightExciterInR, pinnaIc1EqRState, pinnaIc2EqRState, true);

            constexpr float kPinnaCoeff = 0.44F;
            const float elevL = -kPinnaCoeff * pinnaShapedL + heightApStateL;
            heightApStateL = pinnaShapedL + kPinnaCoeff * elevL;
            const float elevR = kPinnaCoeff * pinnaShapedR + heightApStateR;
            heightApStateR = pinnaShapedR - kPinnaCoeff * elevR;

            const float widthBoost = (isNativeAtmos ? 0.58F : 0.74F) * instSpreadScale * intensity;
            const float airWidthBoost = (isNativeAtmos ? 0.70F : 0.88F) * instSpreadScale * intensity;
            const float expandedSideL = sideLow + (cleanSideBody * (1.0F + widthBoost)) +
                (sideAir * (1.0F + airWidthBoost)) + (elevL * 0.42F * intensity);
            const float expandedSideR = sideLow + (cleanSideBody * (1.0F + widthBoost)) +
                (sideAir * (1.0F + airWidthBoost)) - (elevR * 0.42F * intensity);

            // 4. Virtual 7.1.4 Surround & Overhead Array Synthesis
            float virtual12BusL = 0.0F;
            float virtual12BusR = 0.0F;

            if (synthesizeBinauralBed) {
                const std::size_t readLateL = (writeIdx - delaySamplesL) & kDelayMask;
                const std::size_t readLateR = (writeIdx - delaySamplesR) & kDelayMask;
                const std::size_t readRearL = (writeIdx - delayTapRearL) & kDelayMask;
                const std::size_t readRearR = (writeIdx - delayTapRearR) & kDelayMask;
                const std::size_t readHeightL = (writeIdx - delayTapHeightL) & kDelayMask;
                const std::size_t readHeightR = (writeIdx - delayTapHeightR) & kDelayMask;
                const std::size_t readSideL = (writeIdx - delayTapSideL) & kDelayMask;
                const std::size_t readSideR = (writeIdx - delayTapSideR) & kDelayMask;

                const float delLateL = haasBufferL[readLateL];
                const float delLateR = haasBufferR[readLateR];
                const float delRearL = haasBufferL[readRearL];
                const float delRearR = haasBufferR[readRearR];
                const float delHeightL = haasBufferL[readHeightL];
                const float delHeightR = haasBufferR[readHeightR];
                const float delSideL = haasBufferL[readSideL];
                const float delSideR = haasBufferR[readSideR];

                // Higher `sep` extracts more mid-channel instruments into the 12ch surround network
                // while sending less lead vocal into the room reverb so vocals stay razor-sharp in front
                const float vocalRoomBleed = 0.24F * (1.0F - 0.65F * sep);
                const float extractedMidInst =
                    (0.55F * instSpreadScale) * midWarmInst +
                    (0.65F * instSpreadScale) * midHighInst +
                    vocalRoomBleed * vocalCenterFocused;
                const float feedbackGain = isNativeAtmos ? 0.12F : 0.18F;
                const float feedL = sideHigh + extractedMidInst + delLateR * feedbackGain;
                const float feedR = -sideHigh + extractedMidInst - delLateL * feedbackGain;
                dampStateL += hfDampAlpha * (feedL - dampStateL);
                dampStateR += hfDampAlpha * (feedR - dampStateR);

                haasBufferL[writeIdx] = dampStateL;
                haasBufferR[writeIdx] = dampStateR;
                writeIdx = (writeIdx + 1U) & kDelayMask;

                constexpr float kApL = 0.562F;
                constexpr float kApR = -0.514F;
                const float apOutL = -kApL * delLateL + apStateL;
                apStateL = delLateL + kApL * apOutL;
                const float apOutR = -kApR * delLateR + apStateR;
                apStateR = delLateR + kApR * apOutR;

                float dummyIpsL = 0.0F;
                float dummyIpsR = 0.0F;
                const float bedUnmask = (1.0F - 0.30F * sep) + (0.30F * sep) * vocalDuck;
                const float roomScale = isNativeAtmos ? 0.72F : 1.0F;

                // Pair 1 (ch 6,7 SL/SR +-90 deg Side)
                const float virtSL = (0.78F * cleanSideBody + 0.48F * delSideL * roomScale +
                    (0.38F * instSpreadScale) * midWarmInst) * bedUnmask;
                const float virtSR = (-0.78F * cleanSideBody + 0.48F * delSideR * roomScale -
                    (0.38F * instSpreadScale) * midWarmInst) * bedUnmask;
                renderBinauralPair(1U, virtSL, virtSR, virtual12BusL, virtual12BusR, dummyIpsL, dummyIpsR);

                // Pair 0 (ch 4,5 BL/BR +-135 deg Rear)
                const float virtBL = (0.72F * apOutL * roomScale + 0.48F * delRearL * roomScale) * bedUnmask;
                const float virtBR = (0.72F * apOutR * roomScale + 0.48F * delRearR * roomScale) * bedUnmask;
                renderBinauralPair(0U, virtBL, virtBR, virtual12BusL, virtual12BusR, dummyIpsL, dummyIpsR);

                // Pair 2 (ch 8,9 TFL/TFR +-45 deg Az, +45 deg El)
                const float virtTFL = 0.75F * elevL + 0.50F * delHeightL * roomScale +
                    (0.38F * instSpreadScale) * midHighInst;
                const float virtTFR = 0.75F * elevR + 0.50F * delHeightR * roomScale -
                    (0.38F * instSpreadScale) * midHighInst;
                renderBinauralPair(2U, virtTFL, virtTFR, virtual12BusL, virtual12BusR, dummyIpsL, dummyIpsR);

                // Pair 3 (ch 10,11 TBL/TBR +-135 deg Az, +45 deg El)
                const float virtTBL = (0.58F * apOutL * roomScale + 0.62F * elevR) * bedUnmask;
                const float virtTBR = (0.58F * apOutR * roomScale + 0.62F * elevL) * bedUnmask;
                renderBinauralPair(3U, virtTBL, virtTBR, virtual12BusL, virtual12BusR, dummyIpsL, dummyIpsR);

                itdWriteIdx = (itdWriteIdx + 1U) & kItdMask;
            }

            const float send = (isNativeAtmos ? 0.38F : 0.46F) * intensity;
            const float refL = virtual12BusL * send;
            const float refR = virtual12BusR * send;

            const float trim = 1.0F / std::sqrt(1.0F + (0.42F * intensity * intensity));
            left = (mid + expandedSideL + refL) * trim;
            right = (mid - expandedSideR + refR) * trim;
        }

        // True Binaural 7.1.4 (12-Channel) Virtual Speaker Renderer for Dolby Atmos with Vocal/Instrument Split
        inline void processMultichannelAtmos(
            std::array<float, kMaxChannels>& chOut,
            std::size_t channels,
            float wet,
            float strength,
            float vocalSeparation) noexcept {
            const float rawStrength = std::clamp(strength, 0.0F, 1.0F);
            const float curvedStrength = rawStrength * (1.35F - 0.35F * rawStrength);
            const float intensity = wet * curvedStrength;
            if (intensity <= 0.0001F || channels < 6U) return;

            const float sep = std::clamp(vocalSeparation, kVocalSeparationMin, kVocalSeparationMax);
            const float instSpreadScale = 0.65F + 0.55F * sep;

            // 1. Center Channel (ch 2 FC) Lead Vocal Formant Isolation & Forward Anchor
            const float centerIn = chOut[2];
            centerPresenceState += bassAlpha * (centerIn - centerPresenceState);
            if (std::abs(centerPresenceState) < 1.0e-20F) centerPresenceState = 0.0F;
            const float centerHigh = centerIn - centerPresenceState;

            centerVocalLowState += vocalLowAlpha * (centerHigh - centerVocalLowState);
            if (std::abs(centerVocalLowState) < 1.0e-20F) centerVocalLowState = 0.0F;
            const float centerAbove310 = centerHigh - centerVocalLowState;
            centerVocalHighState += vocalHighAlpha * (centerAbove310 - centerVocalHighState);
            if (std::abs(centerVocalHighState) < 1.0e-20F) centerVocalHighState = 0.0F;
            const float centerVocalCore = centerVocalHighState;
            const float centerInstHarmonics = centerAbove310 - centerVocalHighState;

            centerEnvState += vocalEnvAlpha * (std::abs(centerVocalCore) - centerEnvState);
            if (std::abs(centerEnvState) < 1.0e-20F) centerEnvState = 0.0F;
            const float surroundMidDuck =
                1.0F - ((0.14F + 0.24F * sep) * intensity * std::clamp(centerEnvState * 2.4F, 0.0F, 1.0F));

            chOut[2] = centerPresenceState +
                centerHigh * (1.0F + (0.10F + 0.14F * sep) * intensity) +
                centerVocalCore * ((0.12F + 0.26F * sep) * intensity);

            const float centerVocalAnchor =
                (centerVocalCore * (0.12F + 0.16F * sep) + centerHigh * 0.08F) * intensity;
            chOut[0] += centerVocalAnchor;
            chOut[1] += centerVocalAnchor;

            // 2. LFE (ch 3) Sub-Bass Harmonic Reinforcer + Front Stage Recovery Remap
            const float lfeIn = chOut[3];
            lfeRecoverState += lfeAlpha * (lfeIn - lfeRecoverState);
            if (std::abs(lfeRecoverState) < 1.0e-20F) lfeRecoverState = 0.0F;
            const float lfeDrive = std::clamp(lfeRecoverState, -0.9F, 0.9F);
            const float lfeHarmonic =
                (lfeDrive - 0.333333F * lfeDrive * lfeDrive * lfeDrive) * (0.12F * intensity);
            chOut[3] = lfeIn + lfeHarmonic;
            const float lfeSubFeed = (lfeRecoverState + lfeHarmonic) * (0.32F * intensity);
            chOut[0] += lfeSubFeed;
            chOut[1] += lfeSubFeed;

            // 3. Process Front L/R (ch 0 & 1) at +-30 deg azimuth
            process(chOut[0], chOut[1], wet, strength, sep, true, false);

            // 4. Discrete Surround & Overhead Binaural Rendering (BL/BR, SL/SR, TFL/TFR, TBL/TBR)
            float binauralBusL = 0.0F;
            float binauralBusR = 0.0F;

            constexpr std::array<float, 4> kDecorCoeffL{0.47F, 0.53F, 0.41F, 0.59F};
            constexpr std::array<float, 4> kDecorCoeffR{-0.43F, -0.49F, -0.37F, -0.55F};

            for (std::size_t pair = 0U; pair < 4U; ++pair) {
                const std::size_t chL = 4U + (pair * 2U);
                const std::size_t chR = chL + 1U;
                if (chR >= channels) break;

                const float inL = chOut[chL] +
                    (pair >= 2U ? (0.22F * instSpreadScale) * centerInstHarmonics * intensity : 0.0F);
                const float inR = chOut[chR] -
                    (pair >= 2U ? (0.22F * instSpreadScale) * centerInstHarmonics * intensity : 0.0F);

                const float kL = kDecorCoeffL[pair];
                const float kR = kDecorCoeffR[pair];
                const float apL = -kL * inL + atmosDecorApL[pair];
                atmosDecorApL[pair] = inL + kL * apL;
                const float apR = -kR * inR + atmosDecorApR[pair];
                atmosDecorApR[pair] = inR + kR * apR;

                const float decorAmount = (0.35F + 0.25F * sep) * intensity;
                const float decorInL = inL + (apL - inL) * decorAmount;
                const float decorInR = inR + (apR - inR) * decorAmount;

                float pairBusL = 0.0F;
                float pairBusR = 0.0F;
                float ipsiL = 0.0F;
                float ipsiR = 0.0F;
                renderBinauralPair(pair, decorInL, decorInR, pairBusL, pairBusR, ipsiL, ipsiR);

                const float pairGain = (pair >= 2U) ? 1.28F : 1.15F;
                binauralBusL += pairBusL * surroundMidDuck * pairGain;
                binauralBusR += pairBusR * surroundMidDuck * pairGain;

                const float mid = 0.5F * (ipsiL + ipsiR) * surroundMidDuck;
                const float side = 0.5F * (ipsiL - ipsiR) + (0.24F * instSpreadScale) * (apL - apR) * intensity;
                float& state = surroundPairStates[pair];
                state += bassAlpha * (side - state);
                const float sideHigh = side - state;
                const float pairWidth = ((pair >= 2U) ? 0.62F : 0.50F) * instSpreadScale * intensity;
                const float expandedSide = state + sideHigh * (1.0F + pairWidth);
                const float trim = 1.0F / std::sqrt(1.0F + (0.22F * intensity * intensity));
                chOut[chL] = (mid + expandedSide) * trim;
                chOut[chR] = (mid - expandedSide) * trim;
            }

            itdWriteIdx = (itdWriteIdx + 1U) & kItdMask;

            const float binauralSend = 0.42F * intensity;
            const float frontNorm = 1.0F / std::sqrt(1.0F + 0.24F * intensity * intensity);
            chOut[0] = (chOut[0] + binauralBusL * binauralSend) * frontNorm;
            chOut[1] = (chOut[1] + binauralBusR * binauralSend) * frontNorm;
        }

        inline void processSurroundPair(
            std::size_t pairIndex,
            float& surroundL,
            float& surroundR,
            float wet,
            float strength) noexcept {
            const float intensity = wet * std::clamp(strength, 0.0F, 1.0F);
            if (intensity <= 0.0001F || pairIndex >= surroundPairStates.size()) return;
            const float mid = 0.5F * (surroundL + surroundR);
            const float side = 0.5F * (surroundL - surroundR);
            float& state = surroundPairStates[pairIndex];
            state += bassAlpha * (side - state);
            const float sideHigh = side - state;
            const float expandedSide = state + sideHigh * (1.0F + 0.52F * intensity);
            const float trim = 1.0F / std::sqrt(1.0F + (0.18F * intensity * intensity));
            surroundL = (mid + expandedSide) * trim;
            surroundR = (mid - expandedSide) * trim;
        }

        inline void sanitizeState() noexcept {
            auto scrub = [](float& v) noexcept {
                if (!std::isfinite(v) || std::abs(v) < 1.0e-20F) v = 0.0F;
            };
            scrub(lowSideState);
            scrub(midBassState);
            scrub(vocalLowState);
            scrub(vocalHighState);
            scrub(vocalPresenceState);
            scrub(lfeRecoverState);
            scrub(lfeHarmonicState);
            scrub(centerPresenceState);
            scrub(centerVocalLowState);
            scrub(centerVocalHighState);
            scrub(centerEnvState);
            scrub(airShelfState);
            scrub(sidePresenceStateL);
            scrub(sidePresenceStateR);
            scrub(dampStateL);
            scrub(dampStateR);
            scrub(midVocalEnv);
            scrub(heightApStateL);
            scrub(heightApStateR);
            scrub(pinnaIc1EqLState);
            scrub(pinnaIc2EqLState);
            scrub(pinnaIc1EqRState);
            scrub(pinnaIc2EqRState);
            scrub(apStateL);
            scrub(apStateR);
            for (std::size_t i = 0; i < 4U; ++i) {
                scrub(surroundPairStates[i]);
                scrub(contraShadowL[i]);
                scrub(contraShadowR[i]);
                scrub(rearShadowL[i]);
                scrub(rearShadowR[i]);
                scrub(pinnaIc1EqL[i]);
                scrub(pinnaIc2EqL[i]);
                scrub(pinnaIc1EqR[i]);
                scrub(pinnaIc2EqR[i]);
                scrub(atmosDecorApL[i]);
                scrub(atmosDecorApR[i]);
            }
        }
    };

    SpatialImmersionStage spatialImmersion_{};
    std::atomic<bool> targetSpatialImmersionEnabled_{false};
    std::atomic<float> targetSpatialImmersionStrength_{0.85F};
    std::atomic<float> targetHrtfHeadScale_{kHrtfHeadScaleDefault};
    std::atomic<float> targetVocalInstrumentSeparation_{kVocalSeparationDefault};
    float currentHrtfHeadScale_{kHrtfHeadScaleDefault};
    float appliedHrtfHeadScale_{kHrtfHeadScaleDefault};
    float currentSpatialWet_{0.0F};
    ParametricEq parametricEq_{};

    void applyClarityTrim(std::size_t stage, float trimDb) noexcept;
};

}  // namespace neonsong::audio
