#include "DspProcessor.h"

#include <algorithm>
#include <cmath>
#include <mutex>
#include <vector>

namespace neonsong::audio {
namespace {

constexpr double kPi = 3.1415926535897932384626433832795;
constexpr std::array<double, DspProcessor::kEqualizerBandCount> kEqFrequenciesHz{
    20.0, 40.0, 63.0, 100.0, 160.0,
    250.0, 400.0, 630.0, 1000.0, 1600.0,
    2500.0, 4000.0, 6300.0, 10000.0, 16000.0, 20000.0
};
// Updating 16 biquads every 128 samples is well below perceptual control
// latency, while headroom analysis runs less frequently because the linked
// limiter covers transitions seamlessly.
constexpr std::int32_t kEqCoefficientIntervalFrames = 128;
constexpr std::int32_t kEqHeadroomIntervalFrames = 1024;
// Optimal Q = sqrt(2) eliminates resonant ripple between 2/3-octave ISO bands for studio-grade smoothness.
constexpr double kEqQ = 1.4142135623730951;
// -0.2 dBFS true-peak ceiling & -0.8 dBFS linear knee: preserves 100% natural
// dynamic transients on unboosted tracks while preventing 0 dBFS clipping.
constexpr float kOutputCeiling = 0.977237221F;
constexpr float kSoftKneeThreshold = 0.912010839F;
constexpr float kEqualizerPreLimiterBoostDb = 1.0F;
constexpr float kClarityMakeupGain = 1.035F;
constexpr float kAirExciterAmount = 0.18F;
// Studio Master Clarity design gains (dB). Single source for configure()
// and trim rebuilds; per-stage trims add to these values.
constexpr double kClarityBassGainDb = 3.2;
constexpr double kClarityLowMidGainDb = -3.0;
constexpr double kClarityBoxinessGainDb = -1.4;
constexpr double kClarityPresenceGainDb = 3.8;
constexpr double kClarityAirGainDb = 4.8;
constexpr float kClarityTrimMinDb = -12.0F;
constexpr float kClarityTrimMaxDb = 12.0F;
// Native preset trim vectors in header-documented stage order 0..7.
// Mirrored as data in ClarityPresets.kt; REFERENCE is all zeros.
constexpr std::array<float, DspProcessor::kClarityTrimCount> kClarityPresetReferenceTrims{
    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
// SPEAKER: reduced sub-bass lift for small drivers, slightly gentler air.
constexpr std::array<float, DspProcessor::kClarityTrimCount> kClarityPresetSpeakerTrims{
    0.0F, -1.5F, 0.0F, 0.0F, 0.0F, -0.5F, 0.0F, 0.0F};
// HEADPHONE: close-coupled drivers exaggerate bass and presence.
constexpr std::array<float, DspProcessor::kClarityTrimCount> kClarityPresetHeadphoneTrims{
    0.0F, -0.5F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F};
// DAC: revealing downstream chain, gentler top octave.
constexpr std::array<float, DspProcessor::kClarityTrimCount> kClarityPresetDacTrims{
    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.5F, 0.0F, -1.0F};

double safeFrequency(double sampleRate, double frequency) noexcept {
    return std::clamp(frequency, 1.0, sampleRate * 0.45);
}

float sanitizedClarityTrim(float trimDb) noexcept {
    if (!std::isfinite(trimDb)) return 0.0F;
    return std::clamp(trimDb, kClarityTrimMinDb, kClarityTrimMaxDb);
}

float sanitizedHrtfHeadScale(float scale) noexcept {
    if (!std::isfinite(scale)) return DspProcessor::kHrtfHeadScaleDefault;
    return std::clamp(scale, DspProcessor::kHrtfHeadScaleMin, DspProcessor::kHrtfHeadScaleMax);
}

float sanitizedVocalSeparation(float separation) noexcept {
    if (!std::isfinite(separation)) return DspProcessor::kVocalSeparationDefault;
    return std::clamp(
        separation,
        DspProcessor::kVocalSeparationMin,
        DspProcessor::kVocalSeparationMax);
}

float clarityTrimLinearGain(float trimDb) noexcept {
    if (trimDb == 0.0F) return 1.0F;
    return std::pow(10.0F, trimDb / 20.0F);
}

std::mutex& clarityRegistryMutex() {
    static std::mutex mutex;
    return mutex;
}

std::vector<DspProcessor*>& clarityRegistry() {
    static std::vector<DspProcessor*> instances;
    return instances;
}

std::atomic<float>& globalLastBroadcastHeadScale() {
    static std::atomic<float> scale{DspProcessor::kHrtfHeadScaleDefault};
    return scale;
}

std::atomic<float>& globalLastBroadcastVocalSeparation() {
    static std::atomic<float> separation{DspProcessor::kVocalSeparationDefault};
    return separation;
}

}  // namespace

DspProcessor::DspProcessor() noexcept {
    targetHrtfHeadScale_.store(
        globalLastBroadcastHeadScale().load(std::memory_order_relaxed),
        std::memory_order_relaxed);
    targetVocalInstrumentSeparation_.store(
        globalLastBroadcastVocalSeparation().load(std::memory_order_relaxed),
        std::memory_order_relaxed);
    for (auto& gain : targetEqGainsDb_) gain.store(0.0F, std::memory_order_relaxed);
    for (auto& trim : targetClarityTrimsDb_) trim.store(0.0F, std::memory_order_relaxed);
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    clarityRegistry().push_back(this);
}

DspProcessor::~DspProcessor() {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    auto& instances = clarityRegistry();
    instances.erase(
        std::remove(instances.begin(), instances.end(), this),
        instances.end());
}

void DspProcessor::configure(double sampleRate) noexcept {
    sampleRate_ = std::max(sampleRate, 8000.0);
    currentClarityWet_ = std::clamp(
        targetClarityWet_.load(std::memory_order_acquire), 0.0F, 1.0F);
    for (std::size_t stage = 0; stage < kClarityTrimCount; ++stage) {
        const float trim = sanitizedClarityTrim(
            targetClarityTrimsDb_[stage].load(std::memory_order_acquire));
        currentClarityTrimsDb_[stage] = trim;
        appliedClarityTrimsDb_[stage] = trim;
        clarityTrimLinear_[stage] = clarityTrimLinearGain(trim);
    }
    clarityExciterAmount_ = kAirExciterAmount * clarityTrimLinear_[7];
    // Studio Master Clarity Acoustic Contouring:
    // 1. Subsonic highpass: tight 24 Hz cutoff removes rumble and saves amp headroom
    subBassHighPass_ = Biquad::highPass(sampleRate_, 24.0, 0.7071067811865476);
    // 2. Bass foundation: punchy 72 Hz body with controlled bandwidth
    bassFoundation_ = Biquad::peaking(
        sampleRate_, 72.0, 0.80,
        kClarityBassGainDb + static_cast<double>(currentClarityTrimsDb_[1]));
    // 3. Low-mid anti-mud: surgical 280 Hz dip removes boxiness and unmasks vocals
    lowMidSeparation_ = Biquad::peaking(
        sampleRate_, 280.0, 0.90,
        kClarityLowMidGainDb + static_cast<double>(currentClarityTrimsDb_[2]));
    // 4. Boxiness & resonance control: smooth 750 Hz control
    boxinessControl_ = Biquad::peaking(
        sampleRate_, 750.0, 0.85,
        kClarityBoxinessGainDb + static_cast<double>(currentClarityTrimsDb_[3]));
    // 5. Vocal presence & instrument detail: articulate 3400 Hz lift
    presenceDetail_ = Biquad::peaking(
        sampleRate_, 3400.0, 0.85,
        kClarityPresenceGainDb + static_cast<double>(currentClarityTrimsDb_[4]));
    // 6. Silky air shelf: pristine 10.5 kHz high-frequency extension
    airDetail_ = Biquad::highShelf(
        sampleRate_, 10500.0, 0.85,
        kClarityAirGainDb + static_cast<double>(currentClarityTrimsDb_[5]));
    // 7. Mono-Bass filter: 130 Hz highpass for Side channel (locks low-end to center, zero blur)
    monoBassFilter_ = Biquad::highPass(sampleRate_, 130.0, 0.7071067811865476);
    // 8. Harmonic air exciter: 6000 Hz highpass to isolate highs for tape-style harmonic sheen
    airExciterFilter_ = Biquad::highPass(sampleRate_, 6000.0, 0.7071067811865476);
    crossfeed_.configure(sampleRate_, 700.0, 4.5);

    const float initialHeadScale = sanitizedHrtfHeadScale(
        targetHrtfHeadScale_.load(std::memory_order_acquire));
    currentHrtfHeadScale_ = initialHeadScale;
    appliedHrtfHeadScale_ = initialHeadScale;
    spatialImmersion_.configure(sampleRate_, initialHeadScale);

    currentSpatialWet_ = targetSpatialImmersionEnabled_.load(std::memory_order_acquire) ? 1.0F : 0.0F;
    rampPerFrame_ = static_cast<float>(1.0 / (sampleRate_ * 0.050));
    equalizerGainSmoothing_ = static_cast<float>(1.0 - std::exp(
        -static_cast<double>(kEqCoefficientIntervalFrames) / (sampleRate_ * 0.010)));
    limiterRelease_ = static_cast<float>(
        1.0 - std::exp(-1.0 / (sampleRate_ * 0.120)));
    transientFastAttack_ = static_cast<float>(1.0 - std::exp(-1.0 / (sampleRate_ * 0.0015)));
    transientSlowAttack_ = static_cast<float>(1.0 - std::exp(-1.0 / (sampleRate_ * 0.0250)));
    transientRelease_ = static_cast<float>(1.0 - std::exp(-1.0 / (sampleRate_ * 0.0700)));
    tubeLowPassAlpha_ = static_cast<float>(1.0 - std::exp(-2.0 * kPi * 200.0 / sampleRate_));
    hapticSubAlpha_ = static_cast<float>(1.0 - std::exp(-2.0 * kPi * 95.0 / sampleRate_));
    dcBlockerR_ = static_cast<float>(
        std::exp(-2.0 * kPi * 5.0 / sampleRate_));
    tubeDcBlockerR_ = static_cast<float>(
        std::exp(-2.0 * kPi * 7.0 / sampleRate_));
    microFadeFrameCount_ = std::max(
        1,
        static_cast<std::int32_t>(std::llround(sampleRate_ * 0.002)));
    currentWet_ = targetEnabled_.load(std::memory_order_acquire) ? 1.0F : 0.0F;
    currentCrossfeedWet_ = targetBauerCrossfeedEnabled_.load(std::memory_order_acquire) ? 1.0F : 0.0F;
    currentTubeWarmthWet_ = targetAnalogTubeWarmthEnabled_.load(std::memory_order_acquire) ? 1.0F : 0.0F;
    currentMonoBassWet_ = targetMonoBassEnabled_.load(std::memory_order_acquire) ? 1.0F : 0.0F;
    const bool equalizerEnabled = targetEqualizerEnabled_.load(std::memory_order_acquire);
    activeEqualizerBands_ = 0;
    for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
        const bool bandFitsOutputRate = kEqFrequenciesHz[band] < sampleRate_ * 0.45;
        currentEqGainsDb_[band] = equalizerEnabled && bandFitsOutputRate
            ? targetEqGainsDb_[band].load(std::memory_order_acquire)
            : 0.0F;
        if (std::abs(currentEqGainsDb_[band]) >= 0.0005F) {
            equalizerBands_[band] = Biquad::peaking(
                sampleRate_, kEqFrequenciesHz[band], kEqQ, currentEqGainsDb_[band]);
            activeEqualizerBands_ |= static_cast<std::uint32_t>(1U << band);
        } else {
            equalizerBands_[band] = Biquad{};
        }
    }
    equalizerMaximumBoostDb_ = 0.0F;
    if (activeEqualizerBands_ != 0U) {
        constexpr std::size_t kSubSteps = 4U;
        constexpr std::size_t kTotalPoints = (kEqualizerBandCount - 1U) * kSubSteps + 1U;
        for (std::size_t point = 0; point < kTotalPoints; ++point) {
            const std::size_t idx = point / kSubSteps;
            const std::size_t rem = point % kSubSteps;
            double frequency = kEqFrequenciesHz[idx];
            if (rem != 0U) {
                const double ratio = kEqFrequenciesHz[idx + 1U] / kEqFrequenciesHz[idx];
                frequency *= std::pow(ratio, static_cast<double>(rem) / static_cast<double>(kSubSteps));
            }
            if (frequency >= sampleRate_ * 0.48) break;
            double magnitude = 1.0;
            for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
                if ((activeEqualizerBands_ & (1U << band)) != 0U) {
                    magnitude *= equalizerBands_[band].magnitude(sampleRate_, frequency);
                }
            }
            equalizerMaximumBoostDb_ = std::max(
                equalizerMaximumBoostDb_,
                static_cast<float>(20.0 * std::log10(std::max(magnitude, 1.0e-12))));
        }
    }
    currentPreampDb_ = activeEqualizerBands_ != 0U
        ? -std::max(0.0F, equalizerMaximumBoostDb_ - kEqualizerPreLimiterBoostDb)
        : 0.0F;
    currentPreampGain_ = std::pow(10.0F, currentPreampDb_ / 20.0F);
    equalizerUpdateCountdown_ = 0;
    equalizerHeadroomCountdown_ = 0;
    appliedEqualizerRevision_ = targetEqualizerRevision_.load(std::memory_order_acquire);
    limiterGain_ = 1.0F;
    clarityChainActive_ = currentWet_ > 0.0F;
    parametricEq_.configure(sampleRate_, static_cast<int>(kMaxChannels));
    reset();
}

void DspProcessor::reset() noexcept {
    subBassHighPass_.clear();
    bassFoundation_.clear();
    lowMidSeparation_.clear();
    boxinessControl_.clear();
    presenceDetail_.clear();
    airDetail_.clear();
    monoBassFilter_.clear();
    airExciterFilter_.clear();
    crossfeed_.clear();
    spatialImmersion_.clear();
    for (auto& band : equalizerBands_) band.clear();
    parametricEq_.reset();
    limiterGain_ = 1.0F;
    microFadePosition_ = 0;
    dcXPrev_.fill(0.0);
    dcYPrev_.fill(0.0);
    tubeDcXPrev_.fill(0.0);
    tubeDcYPrev_.fill(0.0);
    transientFastEnv_ = 0.0F;
    transientSlowEnv_ = 0.0F;
    tubeLowPassState_.fill(0.0F);
    claritySubState_.fill(0.0F);
    hapticSubState_ = 0.0F;
    hapticBassPeak_.store(0.0F, std::memory_order_relaxed);
    hapticTransientPeak_.store(0.0F, std::memory_order_relaxed);
}

void DspProcessor::setStudioMasterClarity(bool enabled) noexcept {
    targetEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setBitPerfect(bool enabled) noexcept {
    bitPerfectEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setPeakProtectionEnabled(bool enabled) noexcept {
    peakProtectionEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setBauerCrossfeedEnabled(bool enabled) noexcept {
    targetBauerCrossfeedEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setAnalogTubeWarmthEnabled(bool enabled) noexcept {
    targetAnalogTubeWarmthEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setMonoBassEnabled(bool enabled) noexcept {
    targetMonoBassEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setSpatialImmersionEnabled(bool enabled) noexcept {
    targetSpatialImmersionEnabled_.store(enabled, std::memory_order_release);
}

void DspProcessor::setSpatialImmersionStrength(float strength) noexcept {
    if (!std::isfinite(strength)) strength = 0.85F;
    targetSpatialImmersionStrength_.store(std::clamp(strength, 0.0F, 1.0F), std::memory_order_release);
}

void DspProcessor::setHrtfHeadScale(float scale) noexcept {
    targetHrtfHeadScale_.store(sanitizedHrtfHeadScale(scale), std::memory_order_release);
}

void DspProcessor::broadcastSpatialImmersionEnabled(bool enabled) {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setSpatialImmersionEnabled(enabled);
    }
}

void DspProcessor::broadcastSpatialImmersionStrength(float strength) {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setSpatialImmersionStrength(strength);
    }
}

void DspProcessor::broadcastHrtfHeadScale(float scale) {
    const float sanitized = sanitizedHrtfHeadScale(scale);
    globalLastBroadcastHeadScale().store(sanitized, std::memory_order_release);
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setHrtfHeadScale(sanitized);
    }
}

void DspProcessor::setVocalInstrumentSeparation(float separation) noexcept {
    targetVocalInstrumentSeparation_.store(
        sanitizedVocalSeparation(separation),
        std::memory_order_release);
}

void DspProcessor::broadcastVocalInstrumentSeparation(float separation) {
    const float sanitized = sanitizedVocalSeparation(separation);
    globalLastBroadcastVocalSeparation().store(sanitized, std::memory_order_release);
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setVocalInstrumentSeparation(sanitized);
    }
}

void DspProcessor::setEqualizer(
    bool enabled,
    const float* gainsDb,
    std::size_t gainCount) noexcept {
    if (gainsDb != nullptr && gainCount == kEqualizerBandCount) {
        for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
            const float safeGain = std::isfinite(gainsDb[band]) ? gainsDb[band] : 0.0F;
            targetEqGainsDb_[band].store(
                std::clamp(safeGain, -8.0F, 8.0F),
                std::memory_order_release);
        }
    }
    targetEqualizerEnabled_.store(enabled, std::memory_order_release);
    targetEqualizerRevision_.fetch_add(1, std::memory_order_release);
}

void DspProcessor::setClarityWet(float wet) noexcept {
    if (!std::isfinite(wet)) wet = 1.0F;
    targetClarityWet_.store(std::clamp(wet, 0.0F, 1.0F), std::memory_order_release);
}

void DspProcessor::setClarityTrims(const float* trimsDb, std::size_t trimCount) noexcept {
    if (trimsDb != nullptr && trimCount == kClarityTrimCount) {
        for (std::size_t stage = 0; stage < kClarityTrimCount; ++stage) {
            targetClarityTrimsDb_[stage].store(
                sanitizedClarityTrim(trimsDb[stage]),
                std::memory_order_release);
        }
    }
}

void DspProcessor::setClarityPreset(int preset) noexcept {
    const float* trims = nullptr;
    switch (preset) {
        case kClarityPresetReference:
            trims = kClarityPresetReferenceTrims.data();
            break;
        case kClarityPresetSpeaker:
            trims = kClarityPresetSpeakerTrims.data();
            break;
        case kClarityPresetHeadphone:
            trims = kClarityPresetHeadphoneTrims.data();
            break;
        case kClarityPresetDac:
            trims = kClarityPresetDacTrims.data();
            break;
        default:
            return;
    }
    setClarityTrims(trims, kClarityTrimCount);
}

void DspProcessor::setClarityAtmosBypass(bool bypass) noexcept {
    atmosBypassEnabled_.store(bypass, std::memory_order_release);
}

void DspProcessor::consumeHapticTelemetry(
    float& outBassPeak,
    float& outTransientPeak) noexcept {
    outBassPeak = hapticBassPeak_.exchange(0.0F, std::memory_order_relaxed);
    outTransientPeak = hapticTransientPeak_.exchange(0.0F, std::memory_order_relaxed);
}

void DspProcessor::broadcastClarityWet(float wet) {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setClarityWet(wet);
    }
}

void DspProcessor::broadcastClarityTrims(const float* trimsDb, std::size_t trimCount) {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setClarityTrims(trimsDb, trimCount);
    }
}

void DspProcessor::broadcastClarityPreset(int preset) {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setClarityPreset(preset);
    }
}

void DspProcessor::broadcastClarityAtmosBypass(bool bypass) {
    std::lock_guard<std::mutex> registryLock(clarityRegistryMutex());
    for (auto* instance : clarityRegistry()) {
        if (instance != nullptr) instance->setClarityAtmosBypass(bypass);
    }
}

void DspProcessor::applyClarityTrim(std::size_t stage, float trimDb) noexcept {
    const double trim = static_cast<double>(trimDb);
    switch (stage) {
        case 1:
            bassFoundation_.setPeaking(
                sampleRate_, 72.0, 0.80, kClarityBassGainDb + trim);
            break;
        case 2:
            lowMidSeparation_.setPeaking(
                sampleRate_, 280.0, 0.90, kClarityLowMidGainDb + trim);
            break;
        case 3:
            boxinessControl_.setPeaking(
                sampleRate_, 750.0, 0.85, kClarityBoxinessGainDb + trim);
            break;
        case 4:
            presenceDetail_.setPeaking(
                sampleRate_, 3400.0, 0.85, kClarityPresenceGainDb + trim);
            break;
        case 5:
            airDetail_.setHighShelf(
                sampleRate_, 10500.0, 0.85, kClarityAirGainDb + trim);
            break;
        case 0:
        case 6:
        case 7:
            clarityTrimLinear_[stage] = clarityTrimLinearGain(trimDb);
            clarityExciterAmount_ = kAirExciterAmount * clarityTrimLinear_[7];
            break;
        default:
            break;
    }
}

void DspProcessor::process(
    float* samples,
    std::int32_t frameCount,
    std::int32_t channelCount) noexcept {
    if (samples == nullptr || frameCount <= 0 ||
        channelCount < 1 || channelCount > static_cast<std::int32_t>(kMaxChannels)) {
        return;
    }

    const std::size_t channels = static_cast<std::size_t>(channelCount);
    const bool parametricApplied = parametricEq_.process(samples, frameCount, channelCount);

    const auto clampParametricOutput = [&]() noexcept {
        if (!parametricApplied) return;
        const std::size_t count = static_cast<std::size_t>(frameCount) * channels;
        for (std::size_t i = 0; i < count; ++i) {
            const float value = samples[i];
            samples[i] = std::isfinite(value) ? std::clamp(value, -1.0F, 1.0F) : 0.0F;
        }
    };

    const auto updatePassiveHaptics = [&]() noexcept {
        float maxB = 0.0F;
        float maxT = 0.0F;
        const bool hasLfe = (channels >= 6U);
        for (std::int32_t f = 0; f < frameCount; ++f) {
            const std::size_t off = static_cast<std::size_t>(f) * channels;
            const float l = samples[off];
            const float r = samples[off + (channels > 1U ? 1U : 0U)];
            const float mono = 0.5F * (l + r);
            float& lp = hapticSubState_;
            lp += hapticSubAlpha_ * (mono - lp);
            const float lfe = hasLfe ? std::abs(samples[off + 3U]) : 0.0F;
            const float bassEnv = std::max(std::abs(lp) * 1.4F, lfe);
            maxB = std::max(maxB, bassEnv);

            const float pk = std::max(std::abs(l), std::abs(r));
            const float fastRelease = std::min(transientRelease_ * 5.5F, 1.0F);
            const float fastC = (pk > transientFastEnv_) ? transientFastAttack_ : fastRelease;
            const float slowC = (pk > transientSlowEnv_) ? transientSlowAttack_ : transientRelease_;
            transientFastEnv_ += fastC * (pk - transientFastEnv_);
            transientSlowEnv_ += slowC * (pk - transientSlowEnv_);
            const float diff = std::max(0.0F, transientFastEnv_ - transientSlowEnv_);
            maxT = std::max(maxT, std::clamp(diff / (transientSlowEnv_ + 0.025F), 0.0F, 1.0F));
        }
        if (maxB > hapticBassPeak_.load(std::memory_order_relaxed)) {
            hapticBassPeak_.store(std::clamp(maxB, 0.0F, 1.0F), std::memory_order_relaxed);
        }
        if (maxT > hapticTransientPeak_.load(std::memory_order_relaxed)) {
            hapticTransientPeak_.store(std::clamp(maxT, 0.0F, 1.0F), std::memory_order_relaxed);
        }
    };

    if (bitPerfectEnabled_.load(std::memory_order_acquire)) {
        updatePassiveHaptics();
        clampParametricOutput();
        return;
    }

    const double safeSampleRate = std::max(static_cast<double>(sampleRate_), 8000.0);
    const bool atmosBypassed = atmosBypassEnabled_.load(std::memory_order_acquire);
    // Keep high vocal/instrument definition on Dolby Atmos tracks (0.78x on multichannel,
    // 0.72x on 2ch Atmos) so 280 Hz mud removal and 3.4 kHz vocal presence stay effective.
    const bool userClarityEnabled = targetEnabled_.load(std::memory_order_acquire);
    const float target = userClarityEnabled
        ? (atmosBypassed ? (channels >= 6U ? 0.78F : 0.72F) : 1.0F)
        : 0.0F;
    const bool targetCrossfeed = targetBauerCrossfeedEnabled_.load(std::memory_order_acquire);
    const bool targetTubeWarmth = targetAnalogTubeWarmthEnabled_.load(std::memory_order_acquire);
    const bool targetMonoBass = targetMonoBassEnabled_.load(std::memory_order_acquire);
    const bool targetSpatial = targetSpatialImmersionEnabled_.load(std::memory_order_acquire);
    const bool peakProtectionEnabled = peakProtectionEnabled_.load(std::memory_order_acquire);

    const auto equalizerRevision = targetEqualizerRevision_.load(std::memory_order_acquire);
    if (equalizerRevision != appliedEqualizerRevision_) {
        appliedEqualizerRevision_ = equalizerRevision;
        equalizerUpdateCountdown_ = 0;
        equalizerHeadroomCountdown_ = 0;
    }
    if (target > 0.0F) clarityChainActive_ = true;

    bool targetEqualizerHasGain = false;
    if (targetEqualizerEnabled_.load(std::memory_order_acquire)) {
        for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
            if (kEqFrequenciesHz[band] < safeSampleRate * 0.45 &&
                std::abs(targetEqGainsDb_[band].load(std::memory_order_acquire)) >= 0.0005F) {
                targetEqualizerHasGain = true;
                break;
            }
        }
    }

    const bool enhancementTargeted = target > 0.0F || targetEqualizerHasGain ||
        targetCrossfeed || targetTubeWarmth || targetMonoBass || targetSpatial;
    const bool enhancementStateActive = currentWet_ > 0.0F ||
        currentCrossfeedWet_ > 0.0F || currentTubeWarmthWet_ > 0.0F ||
        currentMonoBassWet_ > 0.0F || currentSpatialWet_ > 0.0F ||
        activeEqualizerBands_ != 0U ||
        std::abs(currentPreampGain_ - 1.0F) >= 0.00001F ||
        std::abs(limiterGain_ - 1.0F) >= 0.00001F;
    const bool protectionRequired = peakProtectionEnabled &&
        (enhancementTargeted || enhancementStateActive);
    const bool controlsBypassed = !enhancementTargeted && !protectionRequired;
    const bool stateBypassed = currentWet_ == 0.0F &&
        currentCrossfeedWet_ == 0.0F && currentTubeWarmthWet_ == 0.0F &&
        currentMonoBassWet_ == 0.0F && currentSpatialWet_ == 0.0F &&
        activeEqualizerBands_ == 0U &&
        std::abs(currentPreampGain_ - 1.0F) < 0.00001F &&
        std::abs(limiterGain_ - 1.0F) < 0.00001F;

    if (controlsBypassed && stateBypassed) {
        updatePassiveHaptics();
        clampParametricOutput();
        return;
    }

    const auto tubeSaturate = [this](float input, std::size_t channel) noexcept -> float {
        const float x = std::clamp(input, -1.5F, 1.5F);
        float& lp = tubeLowPassState_[channel];
        lp += tubeLowPassAlpha_ * (x - lp);
        if (std::abs(lp) < 1.0e-20F) lp = 0.0F;
        const float lfDrive = std::clamp(lp, -1.0F, 1.0F);
        const float lfHarmonic =
            (lfDrive - 0.333333F * lfDrive * lfDrive * lfDrive + 0.08F * lfDrive * lfDrive) * 0.052F;

        const float x2 = x * x;
        const float shaped = x + (0.032F * x2) - (0.038F * x2 * x) - 0.015F * x2 / (1.0F + x2);
        return (shaped + lfHarmonic) * 0.984F;
    };

    const auto coupleTubeOutput = [this](float input, std::size_t channel) noexcept -> float {
        const double dcInput = static_cast<double>(input);
        const double dcOutput = dcInput - tubeDcXPrev_[channel] +
            static_cast<double>(tubeDcBlockerR_) * tubeDcYPrev_[channel];
        tubeDcXPrev_[channel] = dcInput;
        tubeDcYPrev_[channel] = dcOutput;
        return std::isfinite(dcOutput) ? static_cast<float>(dcOutput) : 0.0F;
    };

    auto softSaturate = [](float x) noexcept -> float {
        const float absX = std::abs(x);
        if (absX <= kSoftKneeThreshold) return x;
        constexpr float range = kOutputCeiling - kSoftKneeThreshold;
        const float u = (absX - kSoftKneeThreshold) / range;
        float ratio = 1.0F;
        if (u < 3.0F) {
            const float u2 = u * u;
            ratio = u * (27.0F + u2) / (27.0F + 9.0F * u2);
        }
        const float saturated = kSoftKneeThreshold + range * ratio;
        return x >= 0.0F ? saturated : -saturated;
    };

    constexpr float kDbToLinScale = 0.11512925464970228F;
    std::array<float, kMaxChannels> chOut{};
    const bool hasDiscreteLfe = (channels >= 6U);
    float blockMaxBass = 0.0F;
    float blockMaxTransient = 0.0F;

    for (std::int32_t frame = 0; frame < frameCount; ++frame) {
        if (currentWet_ < target) {
            currentWet_ = std::min(target, currentWet_ + rampPerFrame_);
        } else if (currentWet_ > target) {
            currentWet_ = std::max(target, currentWet_ - rampPerFrame_);
        }

        const float crossfeedTarget = targetCrossfeed ? 1.0F : 0.0F;
        const float tubeWarmthTarget = targetTubeWarmth ? 1.0F : 0.0F;
        const float monoBassTarget = targetMonoBass ? 1.0F : 0.0F;
        const float spatialTarget = targetSpatial ? 1.0F : 0.0F;

        currentCrossfeedWet_ += std::clamp(
            crossfeedTarget - currentCrossfeedWet_, -rampPerFrame_, rampPerFrame_);
        currentTubeWarmthWet_ += std::clamp(
            tubeWarmthTarget - currentTubeWarmthWet_, -rampPerFrame_, rampPerFrame_);
        currentMonoBassWet_ += std::clamp(
            monoBassTarget - currentMonoBassWet_, -rampPerFrame_, rampPerFrame_);
        currentSpatialWet_ += std::clamp(
            spatialTarget - currentSpatialWet_, -rampPerFrame_, rampPerFrame_);

        const float targetClarityMix = targetClarityWet_.load(std::memory_order_acquire);
        if (currentClarityWet_ < targetClarityMix) {
            currentClarityWet_ = std::min(targetClarityMix, currentClarityWet_ + rampPerFrame_);
        } else if (currentClarityWet_ > targetClarityMix) {
            currentClarityWet_ = std::max(targetClarityMix, currentClarityWet_ - rampPerFrame_);
        }

        if (equalizerUpdateCountdown_-- <= 0) {
            const bool eqEnabled = targetEqualizerEnabled_.load(std::memory_order_acquire);
            bool coefficientsChanged = false;
            std::uint32_t nextActiveBands = 0;

            if (eqEnabled || activeEqualizerBands_ != 0U) {
                for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
                    const bool bandFitsOutputRate = kEqFrequenciesHz[band] < safeSampleRate * 0.45;
                    const float targetGain = eqEnabled && bandFitsOutputRate
                        ? targetEqGainsDb_[band].load(std::memory_order_acquire)
                        : 0.0F;
                    const float previousGain = currentEqGainsDb_[band];
                    currentEqGainsDb_[band] +=
                        (targetGain - currentEqGainsDb_[band]) * equalizerGainSmoothing_;
                    if (std::abs(targetGain - currentEqGainsDb_[band]) < 0.0005F) {
                        currentEqGainsDb_[band] = targetGain;
                    }
                    const bool bandChanged =
                        std::abs(previousGain - currentEqGainsDb_[band]) > 0.000001F;
                    coefficientsChanged = coefficientsChanged || bandChanged;
                    if (bandChanged) {
                        equalizerBands_[band].setPeaking(
                            sampleRate_, kEqFrequenciesHz[band], kEqQ, currentEqGainsDb_[band]);
                    }
                    if (std::abs(currentEqGainsDb_[band]) >= 0.0005F ||
                        std::abs(targetGain) >= 0.0005F) {
                        nextActiveBands |= static_cast<std::uint32_t>(1U << band);
                    }
                }
            }

            const auto deactivatedBands = static_cast<std::uint32_t>(
                activeEqualizerBands_ & static_cast<std::uint32_t>(~nextActiveBands));
            for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
                if ((deactivatedBands & static_cast<std::uint32_t>(1U << band)) != 0U) {
                    equalizerBands_[band].clear();
                }
            }

            activeEqualizerBands_ = nextActiveBands;
            if (activeEqualizerBands_ == 0U) {
                equalizerMaximumBoostDb_ = 0.0F;
                equalizerHeadroomCountdown_ = 0;
            } else if (coefficientsChanged && equalizerHeadroomCountdown_ <= 0) {
                equalizerMaximumBoostDb_ = 0.0F;
                constexpr std::size_t kSubSteps = 4U;
                constexpr std::size_t kTotalPoints = (kEqualizerBandCount - 1U) * kSubSteps + 1U;
                for (std::size_t point = 0; point < kTotalPoints; ++point) {
                    const std::size_t idx = point / kSubSteps;
                    const std::size_t rem = point % kSubSteps;
                    double frequency = kEqFrequenciesHz[idx];
                    if (rem != 0U) {
                        const double ratio = kEqFrequenciesHz[idx + 1U] / kEqFrequenciesHz[idx];
                        frequency *= std::pow(ratio, static_cast<double>(rem) / static_cast<double>(kSubSteps));
                    }
                    if (frequency >= safeSampleRate * 0.48) break;

                    double magnitude = 1.0;
                    for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
                        if ((activeEqualizerBands_ & (1U << band)) != 0U) {
                            magnitude *= equalizerBands_[band].magnitude(sampleRate_, frequency);
                        }
                    }
                    const float rawBoostDb = static_cast<float>(
                        20.0 * std::log10(std::max(magnitude, 1.0e-12)));
                    float spectralWeight = 1.0F;
                    if (frequency < 45.0) {
                        spectralWeight = 0.85F + 0.15F * static_cast<float>((frequency - 20.0) / 25.0);
                    } else if (frequency > 12000.0) {
                        spectralWeight = 1.0F - 0.18F * static_cast<float>(
                            std::clamp((frequency - 12000.0) / 8000.0, 0.0, 1.0));
                    }
                    equalizerMaximumBoostDb_ = std::max(
                        equalizerMaximumBoostDb_,
                        rawBoostDb * spectralWeight);
                }
                equalizerHeadroomCountdown_ = kEqHeadroomIntervalFrames;
            } else {
                equalizerHeadroomCountdown_ -= kEqCoefficientIntervalFrames;
            }

            const float targetPreampDb = activeEqualizerBands_ != 0U
                ? -std::max(
                    0.0F,
                    equalizerMaximumBoostDb_ - kEqualizerPreLimiterBoostDb)
                : 0.0F;
            const float preampDelta = targetPreampDb - currentPreampDb_;
            if (preampDelta != 0.0F) {
                currentPreampDb_ = std::abs(preampDelta) < 0.00001F
                    ? targetPreampDb
                    : currentPreampDb_ + preampDelta * equalizerGainSmoothing_;
                currentPreampGain_ = std::exp(currentPreampDb_ * kDbToLinScale);
            }

            for (std::size_t stage = 0; stage < kClarityTrimCount; ++stage) {
                const float trimTarget =
                    targetClarityTrimsDb_[stage].load(std::memory_order_acquire);
                float& trimCurrent = currentClarityTrimsDb_[stage];
                trimCurrent += (trimTarget - trimCurrent) * equalizerGainSmoothing_;
                if (std::abs(trimTarget - trimCurrent) < 0.0005F) {
                    trimCurrent = trimTarget;
                }
                if (trimCurrent != appliedClarityTrimsDb_[stage]) {
                    appliedClarityTrimsDb_[stage] = trimCurrent;
                    applyClarityTrim(stage, trimCurrent);
                }
            }

            const float headScaleTarget = sanitizedHrtfHeadScale(
                targetHrtfHeadScale_.load(std::memory_order_acquire));
            currentHrtfHeadScale_ +=
                (headScaleTarget - currentHrtfHeadScale_) * equalizerGainSmoothing_;
            if (std::abs(headScaleTarget - currentHrtfHeadScale_) < 0.0005F) {
                currentHrtfHeadScale_ = headScaleTarget;
            }
            if (currentHrtfHeadScale_ != appliedHrtfHeadScale_) {
                appliedHrtfHeadScale_ = currentHrtfHeadScale_;
                spatialImmersion_.updateHeadScale(sampleRate_, currentHrtfHeadScale_);
            }

            equalizerUpdateCountdown_ = kEqCoefficientIntervalFrames - 1;
        }

        const std::size_t offset = static_cast<std::size_t>(frame) * channels;
        const float clarityMix = std::clamp(currentWet_ * currentClarityWet_, 0.0F, 1.0F);

        float framePeak = 0.0F;
        const std::size_t linkedChannels = std::min<std::size_t>(channels, 3U);
        for (std::size_t ch = 0; ch < linkedChannels; ++ch) {
            framePeak = std::max(framePeak, std::abs(samples[offset + ch]));
        }
        const float fastRelease = std::min(transientRelease_ * 5.5F, 1.0F);
        const float fastCoeff = (framePeak > transientFastEnv_) ? transientFastAttack_ : fastRelease;
        const float slowCoeff = (framePeak > transientSlowEnv_) ? transientSlowAttack_ : transientRelease_;
        transientFastEnv_ += fastCoeff * (framePeak - transientFastEnv_);
        transientSlowEnv_ += slowCoeff * (framePeak - transientSlowEnv_);
        if (transientFastEnv_ < 1.0e-20F) transientFastEnv_ = 0.0F;
        if (transientSlowEnv_ < 1.0e-20F) transientSlowEnv_ = 0.0F;

        const float transientDiff = std::max(0.0F, transientFastEnv_ - transientSlowEnv_);
        const float transientSnap = std::clamp(
            transientDiff / (transientSlowEnv_ + 0.025F), 0.0F, 1.0F);
        blockMaxTransient = std::max(blockMaxTransient, transientSnap);

        const float monoSigned = 0.5F * (samples[offset] + samples[offset + (channels > 1U ? 1U : 0U)]);
        float& hapLp = hapticSubState_;
        hapLp += hapticSubAlpha_ * (monoSigned - hapLp);
        if (std::abs(hapLp) < 1.0e-20F) hapLp = 0.0F;
        const float lfeBass = hasDiscreteLfe ? std::abs(samples[offset + 3U]) : 0.0F;
        blockMaxBass = std::max({blockMaxBass, std::abs(hapLp) * 1.45F, lfeBass});

        const float transientGain = 1.0F + (0.135F * transientSnap * clarityMix);

        for (std::size_t ch = 0; ch < channels; ++ch) {
            const double dcIn = static_cast<double>(samples[offset + ch]);
            const double dcOut = dcIn - dcXPrev_[ch] +
                static_cast<double>(dcBlockerR_) * dcYPrev_[ch];
            dcXPrev_[ch] = dcIn;
            dcYPrev_[ch] = dcOut;
            float eqSample = static_cast<float>(dcOut) * currentPreampGain_;

            if (activeEqualizerBands_ != 0U) {
                for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
                    if ((activeEqualizerBands_ & static_cast<std::uint32_t>(1U << band)) == 0U) {
                        continue;
                    }
                    eqSample = equalizerBands_[band].tick(eqSample, ch);
                }
            }

            const float dry = eqSample;
            float out = dry;

            if (clarityChainActive_) {
                const bool isLfeChannel = hasDiscreteLfe && (ch == 3U);
                const bool isCenterChannel = hasDiscreteLfe && (ch == 2U);

                const float hpBase = subBassHighPass_.tick(dry, ch);
                const float hpOut = hpBase * clarityTrimLinear_[0];

                float& subEnv = claritySubState_[ch];
                subEnv += hapticSubAlpha_ * (hpOut - subEnv);
                const float subDrive = std::clamp(subEnv, -0.85F, 0.85F);
                const float subHarmonic =
                    (subDrive - 0.333333F * subDrive * subDrive * subDrive) * 0.055F * clarityTrimLinear_[1];

                if (isLfeChannel) {
                    float wet = bassFoundation_.tick(hpOut, ch) + subHarmonic * 1.35F;
                    wet *= (1.0F + 0.10F * transientSnap * clarityMix);
                    out = hpBase + (wet - hpBase) * clarityMix;
                } else {
                    const float bassOut = bassFoundation_.tick(hpOut, ch) + subHarmonic;
                    float wet = isCenterChannel ? (0.5F * (hpOut + bassOut)) : bassOut;
                    wet = lowMidSeparation_.tick(wet, ch);
                    wet = boxinessControl_.tick(wet, ch);
                    wet = presenceDetail_.tick(wet, ch);
                    wet = airDetail_.tick(wet, ch);

                    const float rawExciter = airExciterFilter_.tick(hpOut, ch);
                    const float absExciter = std::abs(rawExciter);
                    const float deEssDamp = 1.0F / (1.0F + 1.65F * absExciter);
                    const float exciterIn = std::clamp(rawExciter, -1.1F, 1.1F);
                    const float excited =
                        (exciterIn / (1.0F + 0.28F * exciterIn * exciterIn)) * deEssDamp;

                    wet += excited * clarityExciterAmount_;
                    wet *= kClarityMakeupGain * transientGain;
                    out = hpBase + (wet - hpBase) * clarityMix;
                }
            }

            chOut[ch] = out;
        }

        if (channels == 2U && currentCrossfeedWet_ > 0.0F && !atmosBypassed && currentSpatialWet_ <= 0.001F) {
            float crossfedLeft = chOut[0];
            float crossfedRight = chOut[1];
            crossfeed_.process(crossfedLeft, crossfedRight);
            chOut[0] += (crossfedLeft - chOut[0]) * currentCrossfeedWet_;
            chOut[1] += (crossfedRight - chOut[1]) * currentCrossfeedWet_;
        }

        if (currentTubeWarmthWet_ > 0.0F) {
            for (std::size_t ch = 0; ch < channels; ++ch) {
                const float tubeSample = coupleTubeOutput(tubeSaturate(chOut[ch], ch), ch);
                chOut[ch] += (tubeSample - chOut[ch]) * currentTubeWarmthWet_;
            }
        }

        if (channels == 2U) {
            if (currentMonoBassWet_ > 0.0F) {
                const float mid = (chOut[0] + chOut[1]) * 0.5F;
                const float side = (chOut[0] - chOut[1]) * 0.5F;
                const float filteredSide = monoBassFilter_.tick(side, 0) * clarityTrimLinear_[6];
                const float centeredSide = side + (filteredSide - side) * currentMonoBassWet_;
                chOut[0] = mid + centeredSide;
                chOut[1] = mid - centeredSide;
            }

            if (currentSpatialWet_ > 0.0F) {
                spatialImmersion_.process(
                    chOut[0],
                    chOut[1],
                    currentSpatialWet_,
                    targetSpatialImmersionStrength_.load(std::memory_order_relaxed),
                    targetVocalInstrumentSeparation_.load(std::memory_order_relaxed),
                    atmosBypassed);
            }
        } else if (channels >= 6U && currentSpatialWet_ > 0.0F) {
            const float strength = targetSpatialImmersionStrength_.load(std::memory_order_relaxed);
            const float separation = targetVocalInstrumentSeparation_.load(std::memory_order_relaxed);
            spatialImmersion_.processMultichannelAtmos(
                chOut, channels, currentSpatialWet_, strength, separation);
        }

        float peak = 0.0F;
        for (std::size_t ch = 0; ch < channels; ++ch) {
            peak = std::max(peak, std::abs(chOut[ch]));
        }

        constexpr float kLimiterEngageThreshold = 0.935F;
        const float requiredLimiterGain = (protectionRequired && peak > kLimiterEngageThreshold)
            ? (kLimiterEngageThreshold / peak)
            : 1.0F;
        if (requiredLimiterGain < limiterGain_) {
            limiterGain_ = requiredLimiterGain + 0.15F * (limiterGain_ - requiredLimiterGain);
        } else {
            limiterGain_ += (1.0F - limiterGain_) * limiterRelease_;
            if (1.0F - limiterGain_ < 0.00001F) limiterGain_ = 1.0F;
        }

        float finalGain = limiterGain_;
        if (microFadePosition_ < microFadeFrameCount_) {
            const double phase = kPi * static_cast<double>(microFadePosition_ + 1) /
                static_cast<double>(microFadeFrameCount_);
            finalGain *= static_cast<float>(0.5 * (1.0 - std::cos(phase)));
            ++microFadePosition_;
        }

        for (std::size_t ch = 0; ch < channels; ++ch) {
            float out = chOut[ch] * finalGain;
            if (protectionRequired) {
                out = softSaturate(out);
            }
            samples[offset + ch] = std::isfinite(out)
                ? std::clamp(out, -kOutputCeiling, kOutputCeiling)
                : 0.0F;
        }
    }

    const float prevBass = hapticBassPeak_.load(std::memory_order_relaxed);
    if (blockMaxBass > prevBass) {
        hapticBassPeak_.store(std::clamp(blockMaxBass, 0.0F, 1.0F), std::memory_order_relaxed);
    }
    const float prevTrans = hapticTransientPeak_.load(std::memory_order_relaxed);
    if (blockMaxTransient > prevTrans) {
        hapticTransientPeak_.store(std::clamp(blockMaxTransient, 0.0F, 1.0F), std::memory_order_relaxed);
    }

    if (activeEqualizerBands_ != 0U) {
        for (std::size_t band = 0; band < kEqualizerBandCount; ++band) {
            if ((activeEqualizerBands_ & (1U << band)) != 0U) {
                equalizerBands_[band].sanitizeState(channels);
            }
        }
    }
    if (clarityChainActive_) {
        subBassHighPass_.sanitizeState(channels);
        bassFoundation_.sanitizeState(channels);
        lowMidSeparation_.sanitizeState(channels);
        boxinessControl_.sanitizeState(channels);
        presenceDetail_.sanitizeState(channels);
        airDetail_.sanitizeState(channels);
        airExciterFilter_.sanitizeState(channels);
        for (std::size_t ch = 0; ch < channels; ++ch) {
            if (!std::isfinite(claritySubState_[ch]) || std::abs(claritySubState_[ch]) < 1.0e-20F) {
                claritySubState_[ch] = 0.0F;
            }
        }
    }
    if (channels == 2U && currentMonoBassWet_ > 0.0F) {
        monoBassFilter_.sanitizeState(1U);
    }
    if (currentSpatialWet_ > 0.0F) {
        spatialImmersion_.sanitizeState();
    }
    if (currentTubeWarmthWet_ > 0.0F) {
        for (std::size_t ch = 0; ch < channels; ++ch) {
            if (!std::isfinite(tubeLowPassState_[ch]) || std::abs(tubeLowPassState_[ch]) < 1.0e-20F) {
                tubeLowPassState_[ch] = 0.0F;
            }
        }
    }
    if (!std::isfinite(transientFastEnv_) || transientFastEnv_ < 1.0e-20F) transientFastEnv_ = 0.0F;
    if (!std::isfinite(transientSlowEnv_) || transientSlowEnv_ < 1.0e-20F) transientSlowEnv_ = 0.0F;

    if (target == 0.0F && currentWet_ == 0.0F && clarityChainActive_) {
        subBassHighPass_.clear();
        bassFoundation_.clear();
        lowMidSeparation_.clear();
        boxinessControl_.clear();
        presenceDetail_.clear();
        airDetail_.clear();
        airExciterFilter_.clear();
        claritySubState_.fill(0.0F);
        clarityChainActive_ = false;
    }
    if (!targetCrossfeed && currentCrossfeedWet_ == 0.0F) crossfeed_.clear();
    if (!targetMonoBass && currentMonoBassWet_ == 0.0F) monoBassFilter_.clear();
    if (!targetSpatial && currentSpatialWet_ == 0.0F) spatialImmersion_.clear();
    if (!targetTubeWarmth && currentTubeWarmthWet_ == 0.0F) {
        tubeDcXPrev_.fill(0.0);
        tubeDcYPrev_.fill(0.0);
        tubeLowPassState_.fill(0.0F);
    }
}

DspProcessor::Biquad DspProcessor::Biquad::highPass(
    double sampleRate,
    double frequency,
    double q) noexcept {
    Biquad filter;
    const double omega = 2.0 * kPi * safeFrequency(sampleRate, frequency) / sampleRate;
    const double cosine = std::cos(omega);
    const double alpha = std::sin(omega) / (2.0 * std::max(q, 0.01));
    const double a0 = 1.0 + alpha;
    filter.b0 = ((1.0 + cosine) * 0.5) / a0;
    filter.b1 = -(1.0 + cosine) / a0;
    filter.b2 = filter.b0;
    filter.a1 = (-2.0 * cosine) / a0;
    filter.a2 = (1.0 - alpha) / a0;
    return filter;
}

DspProcessor::Biquad DspProcessor::Biquad::peaking(
    double sampleRate,
    double frequency,
    double q,
    double gainDb) noexcept {
    Biquad filter;
    const double omega = 2.0 * kPi * safeFrequency(sampleRate, frequency) / sampleRate;
    const double cosine = std::cos(omega);
    const double alpha = std::sin(omega) / (2.0 * std::max(q, 0.01));
    const double amplitude = std::pow(10.0, gainDb / 40.0);
    const double a0 = 1.0 + alpha / amplitude;
    filter.b0 = (1.0 + alpha * amplitude) / a0;
    filter.b1 = (-2.0 * cosine) / a0;
    filter.b2 = (1.0 - alpha * amplitude) / a0;
    filter.a1 = filter.b1;
    filter.a2 = (1.0 - alpha / amplitude) / a0;
    return filter;
}

DspProcessor::Biquad DspProcessor::Biquad::highShelf(
    double sampleRate,
    double frequency,
    double slope,
    double gainDb) noexcept {
    Biquad filter;
    const double omega = 2.0 * kPi * safeFrequency(sampleRate, frequency) / sampleRate;
    const double cosine = std::cos(omega);
    const double sine = std::sin(omega);
    const double amplitude = std::pow(10.0, gainDb / 40.0);
    const double safeSlope = std::clamp(slope, 0.1, 1.0);
    const double alpha = (sine * 0.5) * std::sqrt(
        (amplitude + 1.0 / amplitude) * (1.0 / safeSlope - 1.0) + 2.0);
    const double beta = 2.0 * std::sqrt(amplitude) * alpha;
    const double a0 =
        (amplitude + 1.0) - (amplitude - 1.0) * cosine + beta;
    filter.b0 = amplitude *
        ((amplitude + 1.0) + (amplitude - 1.0) * cosine + beta) / a0;
    filter.b1 = -2.0 * amplitude *
        ((amplitude - 1.0) + (amplitude + 1.0) * cosine) / a0;
    filter.b2 = amplitude *
        ((amplitude + 1.0) - (amplitude - 1.0) * cosine - beta) / a0;
    filter.a1 = 2.0 *
        ((amplitude - 1.0) - (amplitude + 1.0) * cosine) / a0;
    filter.a2 =
        ((amplitude + 1.0) - (amplitude - 1.0) * cosine - beta) / a0;
    return filter;
}

void DspProcessor::Biquad::setPeaking(
    double sampleRate,
    double frequency,
    double q,
    double gainDb) noexcept {
    const Biquad coefficients = peaking(sampleRate, frequency, q, gainDb);
    b0 = coefficients.b0;
    b1 = coefficients.b1;
    b2 = coefficients.b2;
    a1 = coefficients.a1;
    a2 = coefficients.a2;
}

void DspProcessor::Biquad::setHighShelf(
    double sampleRate,
    double frequency,
    double slope,
    double gainDb) noexcept {
    const Biquad coefficients = highShelf(sampleRate, frequency, slope, gainDb);
    b0 = coefficients.b0;
    b1 = coefficients.b1;
    b2 = coefficients.b2;
    a1 = coefficients.a1;
    a2 = coefficients.a2;
}

double DspProcessor::Biquad::magnitude(double sampleRate, double frequency) const noexcept {
    const double omega = 2.0 * kPi * safeFrequency(sampleRate, frequency) / sampleRate;
    const double cos1 = std::cos(omega);
    const double cos2 = 2.0 * cos1 * cos1 - 1.0;
    const double sin1Sq = std::max(0.0, 1.0 - cos1 * cos1);
    const double numRe = b0 + b1 * cos1 + b2 * cos2;
    const double denRe = 1.0 + a1 * cos1 + a2 * cos2;
    const double numImSq = sin1Sq * (b1 + 2.0 * b2 * cos1) * (b1 + 2.0 * b2 * cos1);
    const double denImSq = sin1Sq * (a1 + 2.0 * a2 * cos1) * (a1 + 2.0 * a2 * cos1);
    return std::sqrt((numRe * numRe + numImSq) / std::max(denRe * denRe + denImSq, 1.0e-24));
}

void DspProcessor::Crossfeed::configure(
    double sampleRate,
    double cutoffHz,
    double levelDb) noexcept {
    const double frequencyLow = safeFrequency(sampleRate, cutoffHz);
    const double feedLevel = std::abs(levelDb);
    const double gainDbLow = feedLevel * (-5.0 / 6.0) - 3.0;
    const double gainDbHigh = feedLevel / 6.0 - 3.0;
    const double gainLow = std::pow(10.0, gainDbLow / 20.0);
    const double gainHigh = 1.0 - std::pow(10.0, gainDbHigh / 20.0);
    const double frequencyHigh = frequencyLow * std::pow(
        2.0,
        (gainDbLow - 20.0 * std::log10(gainHigh)) / 12.0);

    const double xLow = std::exp(-2.0 * kPi * frequencyLow / sampleRate);
    b1Low = xLow;
    a0Low = gainLow * (1.0 - xLow);

    const double xHigh = std::exp(
        -2.0 * kPi * safeFrequency(sampleRate, frequencyHigh) / sampleRate);
    b1High = xHigh;
    a0High = 1.0 - gainHigh * (1.0 - xHigh);
    a1High = -xHigh;
    gain = 1.0 / (1.0 - gainHigh + gainLow);
    clear();
}

}  // namespace neonsong::audio
