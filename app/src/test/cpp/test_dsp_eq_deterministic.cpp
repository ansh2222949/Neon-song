#include "DspProcessor.h"
#include "ParametricEq.h"

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <array>

using namespace neonsong::audio;

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::fprintf(stderr, "\n[FAILED] %s at %s:%d\n", msg, __FILE__, __LINE__); \
            std::exit(1); \
        } \
    } while (0)

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace {

constexpr std::array<double, 16> kIsoFrequenciesHz{
    20.0, 40.0, 63.0, 100.0, 160.0,
    250.0, 400.0, 630.0, 1000.0, 1600.0,
    2500.0, 4000.0, 6300.0, 10000.0, 16000.0, 20000.0
};

// Generates stereo interleaved pure sine wave
std::vector<float> generateSineWave(double sampleRate, double frequencyHz, float amplitude, double durationSeconds) {
    const std::size_t frameCount = static_cast<std::size_t>(sampleRate * durationSeconds);
    std::vector<float> buffer(frameCount * 2);
    const double angularFreq = 2.0 * M_PI * frequencyHz / sampleRate;
    for (std::size_t i = 0; i < frameCount; ++i) {
        const float val = amplitude * static_cast<float>(std::sin(angularFreq * static_cast<double>(i)));
        buffer[i * 2] = val;
        buffer[i * 2 + 1] = val;
    }
    return buffer;
}

// Computes RMS of stereo interleaved buffer over a range of frames
double computeRms(const float* buffer, std::size_t startFrame, std::size_t endFrame) {
    if (endFrame <= startFrame) return 0.0;
    double sumSquares = 0.0;
    const std::size_t count = (endFrame - startFrame) * 2;
    for (std::size_t i = startFrame * 2; i < endFrame * 2; ++i) {
        sumSquares += static_cast<double>(buffer[i]) * static_cast<double>(buffer[i]);
    }
    return std::sqrt(sumSquares / static_cast<double>(count));
}

// Converts linear amplitude ratio to decibels
double linearToDb(double linearRatio) {
    if (linearRatio <= 1.0e-12) return -120.0;
    return 20.0 * std::log10(linearRatio);
}

} // namespace

// ============================================================================
// TEST 1: Deterministic 16-Band ISO Peaking Filter Gain Accuracy (+6dB & -6dB)
// ============================================================================
void test15BandGainAccuracy() {
    std::printf("[EQ Test 1] 16-band ISO peaking biquad response at 48kHz...\n");
    const double sampleRate = 48000.0;

    for (std::size_t band = 0; band < kIsoFrequenciesHz.size(); ++band) {
        const double freq = kIsoFrequenciesHz[band];
        std::printf("  Testing band %2zu: %5.0f Hz...", band, freq);
        std::fflush(stdout);

        // --- Test +6.0 dB boost ---
        {
            DspProcessor dsp;
            dsp.configure(sampleRate);
            dsp.setStudioMasterClarity(false);
            dsp.setBitPerfect(false);

            std::array<float, 16> gains{};
            gains[band] = 6.0F;
            dsp.setEqualizer(true, gains.data(), gains.size());

            // 1.5 seconds of audio: 0.5s warmup (for gain easing & biquad transient) + 1.0s measurement
            const float inputAmp = 0.05F; // low amplitude to stay well below limiter knee
            auto audio = generateSineWave(sampleRate, freq, inputAmp, 1.5);
            const std::size_t totalFrames = audio.size() / 2;
            const std::size_t warmupFrames = static_cast<std::size_t>(sampleRate * 0.5);

            dsp.process(audio.data(), static_cast<std::int32_t>(totalFrames), 2);

            const double inRms = inputAmp / std::sqrt(2.0);
            const double outRms = computeRms(audio.data(), warmupFrames, totalFrames);
            const double measuredGainDb = linearToDb(outRms / inRms);

            // Dynamic Headroom Compensation applies -5.0 dB preamp on +6.0 dB boost (kEqualizerPreLimiterBoostDb = 1.0 dB).
            // DC blocker (fc ~7.6 Hz) adds subtle sub-bass rolloff for bands 0 (25Hz) and 1 (40Hz).
            const double expectedNetGain = (band == 0) ? +0.421 :
                                           (band == 1) ? +0.858 :
                                           (band == 2) ? +0.950 : +1.009;
            TEST_ASSERT(std::abs(measuredGainDb - expectedNetGain) < 0.15, "Band +6dB net gain mismatch");
            std::printf(" +6dB net: %+.3f dB (expected %+.3f dB)", measuredGainDb, expectedNetGain);
        }

        // --- Test -6.0 dB cut ---
        {
            DspProcessor dsp;
            dsp.configure(sampleRate);
            dsp.setStudioMasterClarity(false);
            dsp.setBitPerfect(false);

            std::array<float, 16> gains{};
            gains[band] = -6.0F;
            dsp.setEqualizer(true, gains.data(), gains.size());

            const float inputAmp = 0.05F;
            auto audio = generateSineWave(sampleRate, freq, inputAmp, 1.5);
            const std::size_t totalFrames = audio.size() / 2;
            const std::size_t warmupFrames = static_cast<std::size_t>(sampleRate * 0.5);

            dsp.process(audio.data(), static_cast<std::int32_t>(totalFrames), 2);

            const double inRms = inputAmp / std::sqrt(2.0);
            const double outRms = computeRms(audio.data(), warmupFrames, totalFrames);
            const double measuredGainDb = linearToDb(outRms / inRms);

            const double expectedCutGain = (band == 0) ? -6.639 :
                                           (band == 1) ? -6.258 :
                                           (band == 2) ? -6.102 : -5.995;
            TEST_ASSERT(std::abs(measuredGainDb - expectedCutGain) < 0.15, "Band -6dB cut mismatch");
            std::printf(", -6dB cut: %+.3f dB (expected %+.3f dB) -> PASSED\n", measuredGainDb, expectedCutGain);
        }
    }
}

// ============================================================================
// TEST 2: Transparent Bypass When EQ Disabled vs Bit-Perfect Bypass
// ============================================================================
void testBypassModes() {
    std::printf("[EQ Test 2] Bypass modes (EQ disabled & Bit-Perfect bypass)...\n");
    const double sampleRate = 48000.0;

    // 1. EQ disabled with non-zero gains set
    {
        DspProcessor dsp;
        dsp.configure(sampleRate);
        dsp.setStudioMasterClarity(false);
        dsp.setBitPerfect(false);

        std::array<float, 16> hotGains;
        hotGains.fill(6.0F);
        dsp.setEqualizer(false, hotGains.data(), hotGains.size()); // Disabled!

        auto audio = generateSineWave(sampleRate, 1000.0, 0.4F, 0.5);
        auto original = audio;

        dsp.process(audio.data(), static_cast<std::int32_t>(audio.size() / 2), 2);

        // Verify bit-exact identity
        for (std::size_t i = 0; i < audio.size(); ++i) {
            TEST_ASSERT(audio[i] == original[i], "EQ disabled must produce bit-exact identical PCM");
        }
        std::printf("  EQ Disabled bit-exact transparent bypass: PASSED\n");
    }

    // 2. Bit-Perfect mode enabled with EQ enabled
    {
        DspProcessor dsp;
        dsp.configure(sampleRate);
        dsp.setStudioMasterClarity(true); // even with clarity enabled
        dsp.setBitPerfect(true); // Bit-perfect takes absolute precedence

        std::array<float, 16> hotGains;
        hotGains.fill(8.0F);
        dsp.setEqualizer(true, hotGains.data(), hotGains.size());

        auto audio = generateSineWave(sampleRate, 1000.0, 0.4F, 0.5);
        auto original = audio;

        dsp.process(audio.data(), static_cast<std::int32_t>(audio.size() / 2), 2);

        // Verify bit-exact identity
        for (std::size_t i = 0; i < audio.size(); ++i) {
            TEST_ASSERT(audio[i] == original[i], "Bit-perfect mode must produce bit-exact identical PCM");
        }
        std::printf("  Bit-Perfect bypass bit-exact verification: PASSED\n");
    }
}

// ============================================================================
// TEST 3: Soft-Knee Peak Limiter Ceiling Under Hot Signal (+6dBFS Input)
// ============================================================================
void testPeakLimiterCeiling() {
    std::printf("[EQ Test 3] Soft-knee peak limiter ceiling under heavy boost...\n");
    const double sampleRate = 48000.0;

    DspProcessor dsp;
    dsp.configure(sampleRate);
    dsp.setStudioMasterClarity(false);
    dsp.setBitPerfect(false);
    dsp.setPeakProtectionEnabled(true);

    // Boost 1kHz by +8 dB
    std::array<float, 16> gains{};
    gains[8] = 8.0F; // 1000 Hz band
    dsp.setEqualizer(true, gains.data(), gains.size());

    // Hot input signal: amplitude 1.0 (0 dBFS). With +8dB boost, raw signal would peak at 2.51 (+8dB)
    auto audio = generateSineWave(sampleRate, 1000.0, 1.0F, 1.0);
    const std::size_t totalFrames = audio.size() / 2;

    dsp.process(audio.data(), static_cast<std::int32_t>(totalFrames), 2);

    float maxPeak = 0.0F;
    for (std::size_t i = 0; i < audio.size(); ++i) {
        TEST_ASSERT(std::isfinite(audio[i]), "Non-finite sample in limiter output");
        maxPeak = std::max(maxPeak, std::abs(audio[i]));
    }

    // Ceiling is -0.5 dBFS = 0.944061
    constexpr float kCeiling = 0.944061F;
    std::printf("  Max peak output: %.6f (Ceiling: %.6f)...\n", maxPeak, kCeiling);
    TEST_ASSERT(maxPeak <= kCeiling + 0.005F, "Limiter output exceeded -0.5 dBFS ceiling");
    std::printf("  Soft-knee peak limiter ceiling: PASSED\n");
}

// ============================================================================
// TEST 4: Parametric EQ (PEQ / AutoEQ) Verification Across PCM Formats
// ============================================================================
void testParametricEqPcmFormats() {
    std::printf("[EQ Test 4] Parametric EQ (PEQ) across PCM formats (16-bit, 24-bit, 32-bit, float)...\n");

    // Publish PEQ config: 1000 Hz peaking filter, Q=2.0, +6 dB boost
    const std::int32_t types[1] = { ParametricEq::kPeaking };
    const float freqs[1] = { 1000.0F };
    const float gains[1] = { 6.0F };
    const float qs[1] = { 2.0F };
    const std::uint8_t enabled[1] = { 1 };

    ParametricEq::publish(true, 0.0F, types, freqs, gains, qs, enabled, 1);

    // 1. Float PCM format
    {
        ParametricEq peq;
        peq.configure(48000.0, 2);

        auto audio = generateSineWave(48000.0, 1000.0, 0.1F, 1.0);
        const std::size_t frames = audio.size() / 2;
        peq.process(audio.data(), static_cast<std::int32_t>(frames), 2);

        const double inRms = 0.1 / std::sqrt(2.0);
        const double outRms = computeRms(audio.data(), 4800, frames);
        const double gainDb = linearToDb(outRms / inRms);
        std::printf("  PEQ Float32 measured gain: %.2f dB...", gainDb);
        TEST_ASSERT(std::abs(gainDb - 6.0) < 0.35, "PEQ Float32 gain mismatch");
        std::printf(" PASSED\n");
    }

    // 2. 16-bit signed PCM format
    {
        ParametricEq peq;
        peq.configure(48000.0, 2);

        const std::size_t frames = 48000;
        std::vector<std::int16_t> pcm16(frames * 2);
        for (std::size_t i = 0; i < frames; ++i) {
            const double s = 3000.0 * std::sin(2.0 * M_PI * 1000.0 * static_cast<double>(i) / 48000.0);
            pcm16[i * 2] = static_cast<std::int16_t>(s);
            pcm16[i * 2 + 1] = static_cast<std::int16_t>(s);
        }

        peq.processPcm(
            reinterpret_cast<std::uint8_t*>(pcm16.data()),
            pcm16.size() * sizeof(std::int16_t),
            ParametricEq::kPcm16);

        double sumSqIn = 0.0;
        double sumSqOut = 0.0;
        for (std::size_t i = 4800 * 2; i < frames * 2; ++i) {
            const double inVal = 3000.0 * std::sin(2.0 * M_PI * 1000.0 * static_cast<double>(i / 2) / 48000.0);
            sumSqIn += inVal * inVal;
            sumSqOut += static_cast<double>(pcm16[i]) * static_cast<double>(pcm16[i]);
        }
        const double gainDb = linearToDb(std::sqrt(sumSqOut / sumSqIn));
        std::printf("  PEQ PCM16 measured gain: %.2f dB...", gainDb);
        TEST_ASSERT(std::abs(gainDb - 6.0) < 0.35, "PEQ PCM16 gain mismatch");
        std::printf(" PASSED\n");
    }

    // 3. 32-bit signed PCM format
    {
        ParametricEq peq;
        peq.configure(48000.0, 2);

        const std::size_t frames = 48000;
        std::vector<std::int32_t> pcm32(frames * 2);
        for (std::size_t i = 0; i < frames; ++i) {
            const double s = 100000000.0 * std::sin(2.0 * M_PI * 1000.0 * static_cast<double>(i) / 48000.0);
            pcm32[i * 2] = static_cast<std::int32_t>(s);
            pcm32[i * 2 + 1] = static_cast<std::int32_t>(s);
        }

        peq.processPcm(
            reinterpret_cast<std::uint8_t*>(pcm32.data()),
            pcm32.size() * sizeof(std::int32_t),
            ParametricEq::kPcm32);

        double sumSqIn = 0.0;
        double sumSqOut = 0.0;
        for (std::size_t i = 4800 * 2; i < frames * 2; ++i) {
            const double inVal = 100000000.0 * std::sin(2.0 * M_PI * 1000.0 * static_cast<double>(i / 2) / 48000.0);
            sumSqIn += inVal * inVal;
            sumSqOut += static_cast<double>(pcm32[i]) * static_cast<double>(pcm32[i]);
        }
        const double gainDb = linearToDb(std::sqrt(sumSqOut / sumSqIn));
        std::printf("  PEQ PCM32 measured gain: %.2f dB...", gainDb);
        TEST_ASSERT(std::abs(gainDb - 6.0) < 0.35, "PEQ PCM32 gain mismatch");
        std::printf(" PASSED\n");
    }

    // 4. 24-bit packed PCM format (3 bytes per sample)
    {
        ParametricEq peq;
        peq.configure(48000.0, 2);

        const std::size_t frames = 48000;
        std::vector<std::uint8_t> pcm24(frames * 2 * 3);
        for (std::size_t i = 0; i < frames * 2; ++i) {
            const double s = 500000.0 * std::sin(2.0 * M_PI * 1000.0 * static_cast<double>(i / 2) / 48000.0);
            const std::int32_t val24 = static_cast<std::int32_t>(s);
            pcm24[i * 3 + 0] = static_cast<std::uint8_t>(val24 & 0xFF);
            pcm24[i * 3 + 1] = static_cast<std::uint8_t>((val24 >> 8) & 0xFF);
            pcm24[i * 3 + 2] = static_cast<std::uint8_t>((val24 >> 16) & 0xFF);
        }

        peq.processPcm(
            pcm24.data(),
            pcm24.size(),
            ParametricEq::kPcm24Packed);

        double sumSqIn = 0.0;
        double sumSqOut = 0.0;
        for (std::size_t i = 4800 * 2; i < frames * 2; ++i) {
            const double inVal = 500000.0 * std::sin(2.0 * M_PI * 1000.0 * static_cast<double>(i / 2) / 48000.0);
            std::int32_t outVal = (static_cast<std::int32_t>(pcm24[i * 3 + 0])) |
                                  (static_cast<std::int32_t>(pcm24[i * 3 + 1]) << 8) |
                                  (static_cast<std::int32_t>(static_cast<std::int8_t>(pcm24[i * 3 + 2])) << 16);
            sumSqIn += inVal * inVal;
            sumSqOut += static_cast<double>(outVal) * static_cast<double>(outVal);
        }
        const double gainDb = linearToDb(std::sqrt(sumSqOut / sumSqIn));
        std::printf("  PEQ PCM24 packed measured gain: %.2f dB...", gainDb);
        TEST_ASSERT(std::abs(gainDb - 6.0) < 0.35, "PEQ PCM24 packed gain mismatch");
        std::printf(" PASSED\n");
    }

    // Reset PEQ so subsequent tests evaluate pure 16-band EQ
    ParametricEq::publish(false, 0.0F, nullptr, nullptr, nullptr, nullptr, nullptr, 0);
}

// ============================================================================
// TEST 5: Sample Rates Diversity (44.1k, 48k, 88.2k, 96k, 192k)
// ============================================================================
void testSampleRates() {
    std::printf("[EQ Test 5] Multi-sample-rate stability (44.1kHz, 48kHz, 88.2kHz, 96kHz, 192kHz)...\n");

    const double sampleRates[] = { 44100.0, 48000.0, 88200.0, 96000.0, 192000.0 };

    for (double sr : sampleRates) {
        std::printf("  Testing sample rate: %.0f Hz...", sr);
        DspProcessor dsp;
        dsp.configure(sr);
        dsp.setStudioMasterClarity(false);
        dsp.setBitPerfect(false);

        // Boost 1000 Hz by +6 dB
        std::array<float, 16> gains{};
        gains[8] = 6.0F;
        dsp.setEqualizer(true, gains.data(), gains.size());

        auto audio = generateSineWave(sr, 1000.0, 0.05F, 1.5);
        const std::size_t totalFrames = audio.size() / 2;
        dsp.process(audio.data(), static_cast<std::int32_t>(totalFrames), 2);

        const double inRms = 0.05 / std::sqrt(2.0);
        const double outRms = computeRms(audio.data(), static_cast<std::size_t>(sr * 0.5), totalFrames);
        const double measuredGainDb = linearToDb(outRms / inRms);

        TEST_ASSERT(std::abs(measuredGainDb - 1.009) < 0.15, "Multi-sample-rate gain mismatch");
        std::printf(" PASSED (Gain: %+.3f dB)\n", measuredGainDb);
    }
}

int main() {
    std::printf("================================================================\n");
    std::printf(" NEON SONG NATIVE DSP & EQUALIZER COMPREHENSIVE VERIFICATION   \n");
    std::printf("================================================================\n");

    test15BandGainAccuracy();
    testBypassModes();
    testPeakLimiterCeiling();
    testParametricEqPcmFormats();
    testSampleRates();

    std::printf("================================================================\n");
    std::printf(" ALL DSP & EQUALIZER TESTS PASSED PERFECTLY ON TARGET HARDWARE! \n");
    std::printf("================================================================\n");
    return 0;
}
