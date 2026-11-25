#include "ParametricEq.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>

namespace neonsong::audio {
namespace {

constexpr double kPi = 3.1415926535897932384626433832795;

// Process-wide published configuration. Written under the mutex by the
// control thread; read lock-free by audio threads, which re-read when the
// revision moves (a torn read is corrected by the next revision bump).
struct SharedConfig final {
    std::atomic<std::uint32_t> revision{0};
    std::atomic<bool> enabled{false};
    std::atomic<float> preampDb{0.0F};
    std::atomic<std::int32_t> count{0};
    std::array<std::atomic<std::int32_t>, ParametricEq::kMaxFilters> types{};
    std::array<std::atomic<float>, ParametricEq::kMaxFilters> frequencies{};
    std::array<std::atomic<float>, ParametricEq::kMaxFilters> gains{};
    std::array<std::atomic<float>, ParametricEq::kMaxFilters> qs{};
    std::array<std::atomic<bool>, ParametricEq::kMaxFilters> filterOn{};
};

SharedConfig& shared() {
    static auto* config = new SharedConfig();
    return *config;
}

std::mutex& publishMutex() {
    static auto* mutex = new std::mutex();
    return *mutex;
}

double clampFrequency(double sampleRate, double frequency) noexcept {
    if (!std::isfinite(frequency)) return 1000.0;
    return std::clamp(frequency, 10.0, sampleRate * 0.49);
}

inline double nextTpdfDither(std::uint32_t& state) noexcept {
    constexpr double kInv24 = 1.0 / 16777216.0;
    state = state * 1664525U + 1013904223U;
    const double r1 = static_cast<double>(state >> 8) * kInv24;
    state = state * 1664525U + 1013904223U;
    const double r2 = static_cast<double>(state >> 8) * kInv24;
    return r1 - r2;
}

float sanitize(float value, float low, float high, float fallback) noexcept {
    if (!std::isfinite(value)) return fallback;
    return std::clamp(value, low, high);
}

inline std::int32_t readInt24(const std::uint8_t* p) noexcept {
    std::int32_t value = static_cast<std::int32_t>(p[0]) |
        (static_cast<std::int32_t>(p[1]) << 8) |
        (static_cast<std::int32_t>(static_cast<std::int8_t>(p[2])) << 16);
    return value;
}

inline void writeInt24(std::uint8_t* p, std::int32_t value) noexcept {
    p[0] = static_cast<std::uint8_t>(value & 0xFF);
    p[1] = static_cast<std::uint8_t>((value >> 8) & 0xFF);
    p[2] = static_cast<std::uint8_t>((value >> 16) & 0xFF);
}

}  // namespace

void ParametricEq::publish(
    bool enabled,
    float preampDb,
    const std::int32_t* types,
    const float* frequenciesHz,
    const float* gainsDb,
    const float* qs,
    const std::uint8_t* filterEnabled,
    std::size_t count) noexcept {
    std::lock_guard<std::mutex> lock(publishMutex());
    auto& config = shared();
    config.revision.fetch_add(1, std::memory_order_acq_rel); // Odd = write in progress
    const std::size_t safeCount = (types == nullptr || frequenciesHz == nullptr ||
        gainsDb == nullptr || qs == nullptr) ? 0U : std::min(count, kMaxFilters);
    for (std::size_t i = 0; i < safeCount; ++i) {
        const std::int32_t type = (types[i] >= kPeaking && types[i] <= kHighPass) ? types[i] : kPeaking;
        config.types[i].store(type, std::memory_order_relaxed);
        config.frequencies[i].store(sanitize(frequenciesHz[i], 10.0F, 40000.0F, 1000.0F), std::memory_order_relaxed);
        config.gains[i].store(sanitize(gainsDb[i], -30.0F, 30.0F, 0.0F), std::memory_order_relaxed);
        config.qs[i].store(sanitize(qs[i], 0.05F, 30.0F, 0.707F), std::memory_order_relaxed);
        config.filterOn[i].store(filterEnabled == nullptr || filterEnabled[i] != 0U, std::memory_order_relaxed);
    }
    config.count.store(static_cast<std::int32_t>(safeCount), std::memory_order_relaxed);
    config.preampDb.store(sanitize(preampDb, -40.0F, 20.0F, 0.0F), std::memory_order_relaxed);
    config.enabled.store(enabled, std::memory_order_relaxed);
    config.revision.fetch_add(1, std::memory_order_release); // Even = write complete
}

bool ParametricEq::isEngaged() noexcept {
    auto& config = shared();
    for (int attempt = 0; attempt < 8; ++attempt) {
        const std::uint32_t rev1 = config.revision.load(std::memory_order_acquire);
        if ((rev1 & 1U) != 0U) continue;

        bool engaged = false;
        if (config.enabled.load(std::memory_order_relaxed)) {
            if (std::abs(config.preampDb.load(std::memory_order_relaxed)) >= 0.001F) {
                engaged = true;
            } else {
                const auto count = std::min(
                    static_cast<std::size_t>(std::max(0, config.count.load(std::memory_order_relaxed))),
                    kMaxFilters);
                for (std::size_t i = 0; i < count; ++i) {
                    if (!config.filterOn[i].load(std::memory_order_relaxed)) continue;
                    const auto type = config.types[i].load(std::memory_order_relaxed);
                    if (type == kLowPass || type == kHighPass ||
                        std::abs(config.gains[i].load(std::memory_order_relaxed)) >= 0.001F) {
                        engaged = true;
                        break;
                    }
                }
            }
        }

        std::atomic_thread_fence(std::memory_order_acquire);
        if (config.revision.load(std::memory_order_relaxed) == rev1) {
            return engaged;
        }
    }
    return config.enabled.load(std::memory_order_acquire);
}

void ParametricEq::configure(double sampleRate, std::int32_t channels) noexcept {
    sampleRate_ = (std::isfinite(sampleRate) && sampleRate >= 8000.0) ? sampleRate : 48000.0;
    channels_ = std::clamp<std::int32_t>(channels, 1, static_cast<std::int32_t>(kMaxChannels));
    appliedRevision_ = 0xFFFFFFFFU;
    reset();
    syncConfiguration();
}

void ParametricEq::reset() noexcept {
    for (auto& state : z1_) state.fill(0.0);
    for (auto& state : z2_) state.fill(0.0);
    slotSource_.fill(-1);
    slotType_.fill(-1);
}

void ParametricEq::syncConfiguration() noexcept {
    auto& config = shared();
    const std::uint32_t rev1 = config.revision.load(std::memory_order_acquire);
    if ((rev1 & 1U) != 0U) return; // Writer active; keep previous coefficients for this block
    if (rev1 == appliedRevision_ && appliedSampleRate_ == sampleRate_) return;

    const bool enabled = config.enabled.load(std::memory_order_relaxed);
    const float preampDb = config.preampDb.load(std::memory_order_relaxed);
    const auto count = std::min(
        static_cast<std::size_t>(std::max(0, config.count.load(std::memory_order_relaxed))),
        kMaxFilters);

    struct FilterItem {
        Spec spec;
        bool on{false};
    };
    std::array<FilterItem, kMaxFilters> items{};
    for (std::size_t i = 0; i < count; ++i) {
        const bool on = config.filterOn[i].load(std::memory_order_relaxed);
        Spec spec;
        spec.type = config.types[i].load(std::memory_order_relaxed);
        spec.frequency = config.frequencies[i].load(std::memory_order_relaxed);
        spec.gainDb = config.gains[i].load(std::memory_order_relaxed);
        spec.q = config.qs[i].load(std::memory_order_relaxed);
        items[i] = {spec, on};
    }

    std::atomic_thread_fence(std::memory_order_acquire);
    if (config.revision.load(std::memory_order_relaxed) != rev1) {
        appliedRevision_ = 0xFFFFFFFFU; // Torn read detected; force re-sync on next block
        return;
    }

    appliedRevision_ = rev1;
    appliedSampleRate_ = sampleRate_;
    enabled_ = enabled;

    if (!enabled_) {
        if (activeCount_ != 0U) {
            reset();
            activeCount_ = 0;
        }
        preampGain_ = 1.0;
        return;
    }

    constexpr double kDbToLn = 0.1151292546497022842;
    preampGain_ = std::exp(static_cast<double>(preampDb) * kDbToLn);

    std::size_t active = 0;
    for (std::size_t i = 0; i < count; ++i) {
        if (!items[i].on) continue;
        const Spec& spec = items[i].spec;
        const bool passFilter = spec.type == kLowPass || spec.type == kHighPass;
        if (!passFilter && std::abs(spec.gainDb) < 0.001) continue;
        // A filter above Nyquist at this rate would alias: skip it.
        if (spec.frequency >= sampleRate_ * 0.49) continue;
        const Coefficients next = design(spec, sampleRate_);
        // Slots are compacted; keep a slot's state only while it still runs
        // the same configured filter and type, so editing one gain never resets the
        // others (no click), while a toggled filter or type change starts from silence.
        if (slotSource_[active] != static_cast<std::int32_t>(i) ||
            slotType_[active] != spec.type) {
            z1_[active].fill(0.0);
            z2_[active].fill(0.0);
            slotSource_[active] = static_cast<std::int32_t>(i);
            slotType_[active] = spec.type;
        }
        coefficients_[active++] = next;
    }
    for (std::size_t i = active; i < kMaxFilters; ++i) {
        z1_[i].fill(0.0);
        z2_[i].fill(0.0);
        slotSource_[i] = -1;
        slotType_[i] = -1;
    }
    activeCount_ = active;
}

void ParametricEq::sanitizeStates(std::size_t channels) noexcept {
    for (std::size_t i = 0; i < activeCount_; ++i) {
        for (std::size_t ch = 0; ch < channels; ++ch) {
            double& z1 = z1_[i][ch];
            double& z2 = z2_[i][ch];
            if (!std::isfinite(z1) || !std::isfinite(z2)) {
                z1 = 0.0;
                z2 = 0.0;
            } else {
                if (std::abs(z1) < 1.0e-20) z1 = 0.0;
                if (std::abs(z2) < 1.0e-20) z2 = 0.0;
            }
        }
    }
}

bool ParametricEq::process(float* samples, std::int32_t frameCount, std::int32_t channelCount) noexcept {
    if (samples == nullptr || frameCount <= 0 || channelCount < 1 ||
        channelCount > static_cast<std::int32_t>(kMaxChannels)) {
        return false;
    }
    syncConfiguration();
    const bool applyPreamp = std::abs(preampGain_ - 1.0) >= 1.0e-6;
    if (!enabled_ || (activeCount_ == 0U && !applyPreamp)) return false;

    const auto channels = static_cast<std::size_t>(channelCount);
    const auto frames = static_cast<std::size_t>(frameCount);

    if (channels == 2U) {
        for (std::size_t i = 0; i < activeCount_; ++i) {
            const Coefficients c = coefficients_[i];
            double z1L = z1_[i][0];
            double z2L = z2_[i][0];
            double z1R = z1_[i][1];
            double z2R = z2_[i][1];
            for (std::size_t frame = 0; frame < frames; ++frame) {
                float* __restrict f = samples + frame * 2U;
                const double inL = std::isfinite(f[0]) ? static_cast<double>(f[0]) : 0.0;
                const double outL = c.b0 * inL + z1L;
                z1L = c.b1 * inL - c.a1 * outL + z2L;
                z2L = c.b2 * inL - c.a2 * outL;
                f[0] = static_cast<float>(outL);

                const double inR = std::isfinite(f[1]) ? static_cast<double>(f[1]) : 0.0;
                const double outR = c.b0 * inR + z1R;
                z1R = c.b1 * inR - c.a1 * outR + z2R;
                z2R = c.b2 * inR - c.a2 * outR;
                f[1] = static_cast<float>(outR);
            }
            z1_[i][0] = z1L;
            z2_[i][0] = z2L;
            z1_[i][1] = z1R;
            z2_[i][1] = z2R;
        }
    } else {
        for (std::size_t i = 0; i < activeCount_; ++i) {
            const Coefficients c = coefficients_[i];
            double* __restrict z1 = z1_[i].data();
            double* __restrict z2 = z2_[i].data();
            for (std::size_t frame = 0; frame < frames; ++frame) {
                float* __restrict f = samples + frame * channels;
                for (std::size_t ch = 0; ch < channels; ++ch) {
                    const double in = std::isfinite(f[ch]) ? static_cast<double>(f[ch]) : 0.0;
                    const double out = c.b0 * in + z1[ch];
                    z1[ch] = c.b1 * in - c.a1 * out + z2[ch];
                    z2[ch] = c.b2 * in - c.a2 * out;
                    f[ch] = static_cast<float>(out);
                }
            }
        }
    }

    if (applyPreamp) {
        const float gain = static_cast<float>(preampGain_);
        const std::size_t totalSamples = frames * channels;
        for (std::size_t s = 0; s < totalSamples; ++s) {
            samples[s] *= gain;
        }
    }

    sanitizeStates(channels);
    return true;
}

bool ParametricEq::processPcm(std::uint8_t* data, std::size_t byteCount, std::int32_t encoding) noexcept {
    if (data == nullptr || byteCount == 0) return false;
    syncConfiguration();
    if (!enabled_ || (activeCount_ == 0 && std::abs(preampGain_ - 1.0) < 1.0e-6)) return false;
    const auto channels = static_cast<std::size_t>(channels_);
    std::size_t bytesPerSample = 0;
    switch (encoding) {
        case kPcm16: bytesPerSample = 2; break;
        case kPcm24Packed: bytesPerSample = 3; break;
        case kPcm32: bytesPerSample = 4; break;
        case kPcmFloat: bytesPerSample = 4; break;
        default: return false;
    }
    const std::size_t frameBytes = bytesPerSample * channels;
    const std::size_t frames = byteCount / frameBytes;

    switch (encoding) {
        case kPcm16: {
            constexpr double kInv16 = 1.0 / 32768.0;
            for (std::size_t frame = 0; frame < frames; ++frame) {
                std::uint8_t* base = data + frame * frameBytes;
                for (std::size_t ch = 0; ch < channels; ++ch) {
                    std::uint8_t* p = base + ch * 2U;
                    std::int16_t raw;
                    std::memcpy(&raw, p, sizeof(raw));
                    const double out = tickDouble(static_cast<double>(raw) * kInv16, ch);
                    const double scaled = out * 32768.0 + nextTpdfDither(ditherState_);
                    const double safe = std::isfinite(scaled) ? std::clamp(scaled, -32768.0, 32767.0) : 0.0;
                    const auto value = static_cast<std::int16_t>(std::lrint(safe));
                    std::memcpy(p, &value, sizeof(value));
                }
            }
            break;
        }
        case kPcm24Packed: {
            constexpr double kInv24 = 1.0 / 8388608.0;
            for (std::size_t frame = 0; frame < frames; ++frame) {
                std::uint8_t* base = data + frame * frameBytes;
                for (std::size_t ch = 0; ch < channels; ++ch) {
                    std::uint8_t* p = base + ch * 3U;
                    const double out = tickDouble(static_cast<double>(readInt24(p)) * kInv24, ch);
                    const double scaled = out * 8388608.0 + nextTpdfDither(ditherState_);
                    const double safe = std::isfinite(scaled) ? std::clamp(scaled, -8388608.0, 8388607.0) : 0.0;
                    writeInt24(p, static_cast<std::int32_t>(std::lrint(safe)));
                }
            }
            break;
        }
        case kPcm32: {
            constexpr double kInv32 = 1.0 / 2147483648.0;
            for (std::size_t frame = 0; frame < frames; ++frame) {
                std::uint8_t* base = data + frame * frameBytes;
                for (std::size_t ch = 0; ch < channels; ++ch) {
                    std::uint8_t* p = base + ch * 4U;
                    std::int32_t raw;
                    std::memcpy(&raw, p, sizeof(raw));
                    const double out = tickDouble(static_cast<double>(raw) * kInv32, ch);
                    const double scaled = out * 2147483648.0;
                    const double rounded = std::isfinite(scaled) ? std::nearbyint(scaled) : 0.0;
                    const auto value = static_cast<std::int32_t>(std::clamp(rounded, -2147483648.0, 2147483647.0));
                    std::memcpy(p, &value, sizeof(value));
                }
            }
            break;
        }
        case kPcmFloat: {
            for (std::size_t frame = 0; frame < frames; ++frame) {
                std::uint8_t* base = data + frame * frameBytes;
                for (std::size_t ch = 0; ch < channels; ++ch) {
                    std::uint8_t* p = base + ch * 4;
                    float raw;
                    std::memcpy(&raw, p, sizeof(raw));
                    float out = tick(raw, ch);
                    out = std::isfinite(out) ? std::clamp(out, -1.0F, 1.0F) : 0.0F;
                    std::memcpy(p, &out, sizeof(out));
                }
            }
            break;
        }
        default:
            return false;
    }

    sanitizeStates(channels);
    return true;
}

ParametricEq::Coefficients ParametricEq::design(const Spec& spec, double sampleRate) noexcept {
    Coefficients c;
    const double frequency = clampFrequency(sampleRate, spec.frequency);
    const double omega = 2.0 * kPi * frequency / sampleRate;
    const double cosine = std::cos(omega);
    const double sine = std::sin(omega);
    const double q = std::max(spec.q, 0.05);
    const double alpha = sine / (2.0 * q);
    // amplitude = 10^(gainDb / 40) = exp(gainDb * ln(10) / 40)
    constexpr double kDb40ToLn = 0.0575646273248511421;
    const double amplitude = std::exp(spec.gainDb * kDb40ToLn);
    double b0 = 1.0, b1 = 0.0, b2 = 0.0, a0 = 1.0, a1 = 0.0, a2 = 0.0;
    switch (spec.type) {
        case kLowShelf: {
            const double twoSqrtAAlpha = 2.0 * std::sqrt(amplitude) * alpha;
            b0 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine + twoSqrtAAlpha);
            b1 = 2.0 * amplitude * ((amplitude - 1.0) - (amplitude + 1.0) * cosine);
            b2 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosine - twoSqrtAAlpha);
            a0 = (amplitude + 1.0) + (amplitude - 1.0) * cosine + twoSqrtAAlpha;
            a1 = -2.0 * ((amplitude - 1.0) + (amplitude + 1.0) * cosine);
            a2 = (amplitude + 1.0) + (amplitude - 1.0) * cosine - twoSqrtAAlpha;
            break;
        }
        case kHighShelf: {
            const double twoSqrtAAlpha = 2.0 * std::sqrt(amplitude) * alpha;
            b0 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine + twoSqrtAAlpha);
            b1 = -2.0 * amplitude * ((amplitude - 1.0) + (amplitude + 1.0) * cosine);
            // MATH FIX: RBJ High-Shelf b2 uses +(A - 1)*cos(w0), NOT -(A - 1)*cos(w0)
            b2 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosine - twoSqrtAAlpha);
            a0 = (amplitude + 1.0) - (amplitude - 1.0) * cosine + twoSqrtAAlpha;
            a1 = 2.0 * ((amplitude - 1.0) - (amplitude + 1.0) * cosine);
            a2 = (amplitude + 1.0) - (amplitude - 1.0) * cosine - twoSqrtAAlpha;
            break;
        }
        case kLowPass: {
            b0 = (1.0 - cosine) * 0.5;
            b1 = 1.0 - cosine;
            b2 = (1.0 - cosine) * 0.5;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosine;
            a2 = 1.0 - alpha;
            break;
        }
        case kHighPass: {
            b0 = (1.0 + cosine) * 0.5;
            b1 = -(1.0 + cosine);
            b2 = (1.0 + cosine) * 0.5;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosine;
            a2 = 1.0 - alpha;
            break;
        }
        case kPeaking:
        default: {
            b0 = 1.0 + alpha * amplitude;
            b1 = -2.0 * cosine;
            b2 = 1.0 - alpha * amplitude;
            a0 = 1.0 + alpha / amplitude;
            a1 = -2.0 * cosine;
            a2 = 1.0 - alpha / amplitude;
            break;
        }
    }
    const double invA0 = 1.0 / a0;
    c.b0 = b0 * invA0;
    c.b1 = b1 * invA0;
    c.b2 = b2 * invA0;
    c.a1 = a1 * invA0;
    c.a2 = a2 * invA0;
    return c;
}

double ParametricEq::magnitude(const Coefficients& c, double sampleRate, double frequency) noexcept {
    // Closed-form Orfanidis / direct cos(w) evaluation (2x faster: avoids sin(w), cos(2w), sin(2w))
    const double omega = 2.0 * kPi * clampFrequency(sampleRate, frequency) / sampleRate;
    const double cos1 = std::cos(omega);
    const double cos2 = 2.0 * cos1 * cos1 - 1.0;
    const double sin1Sq = std::max(0.0, 1.0 - cos1 * cos1);
    const double numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2;
    const double denRe = 1.0 + c.a1 * cos1 + c.a2 * cos2;
    const double numImSq = sin1Sq * (c.b1 + 2.0 * c.b2 * cos1) * (c.b1 + 2.0 * c.b2 * cos1);
    const double denImSq = sin1Sq * (c.a1 + 2.0 * c.a2 * cos1) * (c.a1 + 2.0 * c.a2 * cos1);
    return std::sqrt((numRe * numRe + numImSq) / std::max(denRe * denRe + denImSq, 1.0e-24));
}

double ParametricEq::responseDb(double sampleRate, double frequency) noexcept {
    const double sr = (std::isfinite(sampleRate) && sampleRate >= 8000.0) ? sampleRate : 48000.0;
    auto& config = shared();
    for (int attempt = 0; attempt < 8; ++attempt) {
        const std::uint32_t rev1 = config.revision.load(std::memory_order_acquire);
        if ((rev1 & 1U) != 0U) continue;

        const bool enabled = config.enabled.load(std::memory_order_relaxed);
        const float preampDb = config.preampDb.load(std::memory_order_relaxed);
        const auto count = std::min(
            static_cast<std::size_t>(std::max(0, config.count.load(std::memory_order_relaxed))),
            kMaxFilters);

        std::array<Spec, kMaxFilters> specs{};
        std::array<bool, kMaxFilters> on{};
        for (std::size_t i = 0; i < count; ++i) {
            on[i] = config.filterOn[i].load(std::memory_order_relaxed);
            specs[i].type = config.types[i].load(std::memory_order_relaxed);
            specs[i].frequency = config.frequencies[i].load(std::memory_order_relaxed);
            specs[i].gainDb = config.gains[i].load(std::memory_order_relaxed);
            specs[i].q = config.qs[i].load(std::memory_order_relaxed);
        }

        std::atomic_thread_fence(std::memory_order_acquire);
        if (config.revision.load(std::memory_order_relaxed) != rev1) continue;

        if (!enabled) return 0.0;
        constexpr double kDbToLn = 0.1151292546497022842;
        double gain = std::exp(static_cast<double>(preampDb) * kDbToLn);
        for (std::size_t i = 0; i < count; ++i) {
            if (!on[i]) continue;
            const bool pass = specs[i].type == kLowPass || specs[i].type == kHighPass;
            if (!pass && std::abs(specs[i].gainDb) < 0.001) continue;
            if (specs[i].frequency >= sr * 0.49) continue;
            gain *= magnitude(design(specs[i], sr), sr, frequency);
        }
        return 20.0 * std::log10(std::max(gain, 1.0e-12));
    }
    return 0.0;
}

}  // namespace neonsong::audio
