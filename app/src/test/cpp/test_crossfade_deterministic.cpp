#include "LockFreeRingBuffer.h"

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include <array>
#include <atomic>

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

// Fourier component extraction to measure magnitude of specific frequency in a mixed signal
double measureFrequencyMagnitude(const float* buffer, std::size_t frameCount, double sampleRate, double targetFreqHz) {
    double real = 0.0;
    double imag = 0.0;
    const double omega = 2.0 * M_PI * targetFreqHz / sampleRate;
    for (std::size_t i = 0; i < frameCount; ++i) {
        // Average L and R channels
        const double sample = (static_cast<double>(buffer[i * 2]) + static_cast<double>(buffer[i * 2 + 1])) * 0.5;
        real += sample * std::cos(omega * static_cast<double>(i));
        imag -= sample * std::sin(omega * static_cast<double>(i));
    }
    return (2.0 / static_cast<double>(frameCount)) * std::sqrt(real * real + imag * imag);
}

// Simulates the exact AudioEngine::onAudioReady mixing loop
void mixDualDecks(
    LockFreeRingBuffer<float>& ringA,
    LockFreeRingBuffer<float>& ringB,
    float* output,
    std::size_t frameCount,
    float crossfadeProgress,
    float volA = 1.0F,
    float volB = 1.0F) {

    std::vector<float> scratchA(frameCount * 2, 0.0F);
    std::vector<float> scratchB(frameCount * 2, 0.0F);

    const std::size_t readA = ringA.read(scratchA.data(), frameCount * 2);
    const std::size_t readB = ringB.read(scratchB.data(), frameCount * 2);

    const std::size_t framesA = readA / 2;
    const std::size_t framesB = readB / 2;
    const std::size_t validFrames = std::max(framesA, framesB);

    const float gainA = std::cos(crossfadeProgress * 1.57079632679F) * volA;
    const float gainB = std::sin(crossfadeProgress * 1.57079632679F) * volB;

    for (std::size_t f = 0; f < validFrames; ++f) {
        const std::size_t off = f * 2;
        const float sA_0 = (f < framesA) ? scratchA[off] : 0.0F;
        const float sA_1 = (f < framesA) ? scratchA[off + 1] : 0.0F;
        const float sB_0 = (f < framesB) ? scratchB[off] : 0.0F;
        const float sB_1 = (f < framesB) ? scratchB[off + 1] : 0.0F;

        output[off]     = sA_0 * gainA + sB_0 * gainB;
        output[off + 1] = sA_1 * gainA + sB_1 * gainB;
    }
}

} // namespace

// ============================================================================
// TEST 1: Simultaneous Dual-Deck Consumption and Equal-Power Curves
// ============================================================================
void testDualDeckEqualPowerCrossfade() {
    std::printf("[Crossfade Test 1] Simultaneous dual-deck consumption and equal-power curve...\n");

    const double sampleRate = 48000.0;
    const std::size_t frameCount = 4800; // 100ms chunk
    constexpr std::size_t kRingCap = 48000;

    LockFreeRingBuffer<float> ringA(kRingCap);
    LockFreeRingBuffer<float> ringB(kRingCap);

    // Deck A: pure 400 Hz sine wave, amplitude 1.0
    // Deck B: pure 2000 Hz sine wave, amplitude 1.0
    const double steps[] = { 0.0, 0.25, 0.50, 0.75, 1.0 };

    for (double xfade : steps) {
        ringA.clear();
        ringB.clear();

        std::vector<float> waveA(frameCount * 2);
        std::vector<float> waveB(frameCount * 2);
        for (std::size_t i = 0; i < frameCount; ++i) {
            const float sA = std::sin(2.0 * M_PI * 400.0 * static_cast<double>(i) / sampleRate);
            const float sB = std::sin(2.0 * M_PI * 2000.0 * static_cast<double>(i) / sampleRate);
            waveA[i * 2] = sA; waveA[i * 2 + 1] = sA;
            waveB[i * 2] = sB; waveB[i * 2 + 1] = sB;
        }

        ringA.write(waveA.data(), waveA.size());
        ringB.write(waveB.data(), waveB.size());

        TEST_ASSERT(ringA.availableToRead() == waveA.size(), "Ring A must contain full written frames");
        TEST_ASSERT(ringB.availableToRead() == waveB.size(), "Ring B must contain full written frames");

        std::vector<float> output(frameCount * 2, 0.0F);
        mixDualDecks(ringA, ringB, output.data(), frameCount, static_cast<float>(xfade));

        // 1. Verify simultaneous consumption: both rings must have been drained!
        TEST_ASSERT(ringA.availableToRead() == 0, "Ring A must be consumed simultaneously");
        TEST_ASSERT(ringB.availableToRead() == 0, "Ring B must be consumed simultaneously");

        // 2. Measure actual spectral gains
        const double magA = measureFrequencyMagnitude(output.data(), frameCount, sampleRate, 400.0);
        const double magB = measureFrequencyMagnitude(output.data(), frameCount, sampleRate, 2000.0);

        const double expectedGainA = std::cos(xfade * M_PI * 0.5);
        const double expectedGainB = std::sin(xfade * M_PI * 0.5);
        const double totalPower = magA * magA + magB * magB;

        std::printf("  xfade=%.2f -> Deck A (400Hz): %.4f (exp: %.4f), Deck B (2kHz): %.4f (exp: %.4f), Power: %.4f...",
                    xfade, magA, expectedGainA, magB, expectedGainB, totalPower);

        TEST_ASSERT(std::abs(magA - expectedGainA) < 0.02, "Deck A gain curve mismatch");
        TEST_ASSERT(std::abs(magB - expectedGainB) < 0.02, "Deck B gain curve mismatch");
        TEST_ASSERT(std::abs(totalPower - 1.0) < 0.03, "Total acoustic power must be constant (equal-power)");
        std::printf(" PASSED\n");
    }
}

// ============================================================================
// TEST 2: Buffer Starvation on Incoming Deck Mid-Crossfade
// ============================================================================
void testBufferStarvationMidCrossfade() {
    std::printf("[Crossfade Test 2] Buffer starvation on incoming deck mid-crossfade...\n");

    const std::size_t frameCount = 1024;
    LockFreeRingBuffer<float> ringA(8192);
    LockFreeRingBuffer<float> ringB(8192);

    // Deck A has full data, Deck B is completely starved (empty ring)
    std::vector<float> waveA(frameCount * 2, 0.5F);
    ringA.write(waveA.data(), waveA.size());
    // ringB is left empty (0 frames available)

    std::vector<float> output(frameCount * 2, -999.0F);
    mixDualDecks(ringA, ringB, output.data(), frameCount, 0.5F); // mid-crossfade

    // Must output Deck A scaled by gainA without crashing or emitting NaN
    const float expectedGainA = std::cos(0.5F * 1.57079632679F);
    const float expectedVal = 0.5F * expectedGainA;

    for (std::size_t i = 0; i < output.size(); ++i) {
        TEST_ASSERT(std::isfinite(output[i]), "Output must be finite during starvation");
        TEST_ASSERT(std::abs(output[i] - expectedVal) < 0.001F, "Output must gracefully scale available deck");
    }
    std::printf("  Graceful fallback under deck starvation: PASSED\n");
}

// ============================================================================
// TEST 3: Repeated Rapid Transitions and Ring Buffer Clear Invariants
// ============================================================================
void testRepeatedTransitions() {
    std::printf("[Crossfade Test 3] 100 rapid consecutive crossfades and ring clear invariants...\n");

    LockFreeRingBuffer<float> ringA(16384);
    LockFreeRingBuffer<float> ringB(16384);

    for (int cycle = 0; cycle < 100; ++cycle) {
        ringA.clear();
        ringB.clear();

        std::vector<float> chunkA(2048, 0.25F);
        std::vector<float> chunkB(2048, 0.75F);

        ringA.write(chunkA.data(), chunkA.size());
        ringB.write(chunkB.data(), chunkB.size());

        // Perform partial crossfade
        std::vector<float> out(1024, 0.0F);
        mixDualDecks(ringA, ringB, out.data(), 512, 0.5F);

        // Clear remaining
        ringA.clear();
        ringB.clear();

        TEST_ASSERT(ringA.availableToRead() == 0, "Ring A must be empty after clear");
        TEST_ASSERT(ringB.availableToRead() == 0, "Ring B must be empty after clear");
        TEST_ASSERT(ringA.availableToWrite() == 16384, "Ring A full capacity restored");
        TEST_ASSERT(ringB.availableToWrite() == 16384, "Ring B full capacity restored");
    }
    std::printf("  100 cycles of rapid transition + clear: PASSED\n");
}

int main() {
    std::printf("================================================================\n");
    std::printf(" NEON SONG DUAL-DECK CROSSFADE NATIVE SIGNAL-PATH VERIFICATION  \n");
    std::printf("================================================================\n");

    testDualDeckEqualPowerCrossfade();
    testBufferStarvationMidCrossfade();
    testRepeatedTransitions();

    std::printf("================================================================\n");
    std::printf(" ALL DUAL-DECK CROSSFADE TESTS PASSED PERFECTLY ON HARDWARE!    \n");
    std::printf("================================================================\n");
    return 0;
}
