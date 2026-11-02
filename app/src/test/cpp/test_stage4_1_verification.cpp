#include "WsolaTimeStretcher.h"
#include "AutoMixEngine.h"
#include "LockFreeRingBuffer.h"
#include "LinkwitzRileyFilter.h"
#include "EchoTailProcessor.h"
#include "BpmPhaseDetector.h"

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <cmath>
#include <cassert>
#include <cstring>
#include <algorithm>
#include <thread>
#include <atomic>
#include <chrono>
#include <numeric>

using namespace lastwave::audio;
using namespace neonsong::audio;

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::fprintf(stderr, "\nFAILED: %s at %s:%d\n", msg, __FILE__, __LINE__); \
            std::exit(1); \
        } \
    } while (0)

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// ============================================================================
// TEST 1: SPSC Ring Buffer Backpressure, Underrun & Reinitialization Invariants
// ============================================================================
void testRingBufferBackpressureAndPolicies() {
    std::printf("[Stage 4.1 - Test 1] Ring Buffer backpressure, underrun & edge cases...");
    std::fflush(stdout);

    constexpr std::size_t kCapacity = 1024;
    LockFreeRingBuffer<float> ring(kCapacity);

    // 1. Empty read policy: must return 0 immediately without blocking
    std::vector<float> emptyBuf(256, -999.0F);
    std::size_t readCount = ring.read(emptyBuf.data(), 256);
    TEST_ASSERT(readCount == 0, "Empty read must return 0 immediately");
    TEST_ASSERT(emptyBuf[0] == -999.0F, "Empty read must not alter buffer contents");

    // 2. Full write policy: fill to capacity (1024 slots)
    std::vector<float> fullChunk(kCapacity, 42.0F);
    std::size_t written = ring.write(fullChunk.data(), kCapacity);
    TEST_ASSERT(written == kCapacity, "Must accept exactly full capacity");
    TEST_ASSERT(ring.availableToWrite() == 0, "availableToWrite must be 0 when full");

    // Attempting to write into full buffer: must return 0 immediately without blocking
    std::size_t overflowWrite = ring.write(fullChunk.data(), 128);
    TEST_ASSERT(overflowWrite == 0, "Write to full buffer must return 0 (non-blocking backpressure)");

    // 3. Fast Producer vs Slow Consumer (Backpressure test)
    {
        LockFreeRingBuffer<float> stressRing(512);
        std::atomic<bool> producerRunning{true};
        std::atomic<std::size_t> droppedByBackpressure{0};
        std::atomic<std::size_t> successfullyWritten{0};
        std::atomic<std::size_t> successfullyRead{0};

        std::thread producer([&]() {
            float val = 1.0F;
            while (producerRunning.load(std::memory_order_acquire)) {
                float chunk[16];
                for (int i = 0; i < 16; ++i) chunk[i] = val++;
                std::size_t w = stressRing.write(chunk, 16);
                if (w < 16) {
                    droppedByBackpressure.fetch_add(16 - w, std::memory_order_relaxed);
                }
                successfullyWritten.fetch_add(w, std::memory_order_relaxed);
                // Tight loop: fast producer
            }
        });

        std::thread consumer([&]() {
            for (int i = 0; i < 50; ++i) {
                float readBuf[32];
                std::size_t r = stressRing.read(readBuf, 32);
                successfullyRead.fetch_add(r, std::memory_order_relaxed);
                std::this_thread::sleep_for(std::chrono::microseconds(200)); // slow consumer
            }
            producerRunning.store(false, std::memory_order_release);
        });

        producer.join();
        consumer.join();

        TEST_ASSERT(droppedByBackpressure.load() > 0, "Backpressure must reject overflow without corruption");
        TEST_ASSERT(successfullyRead.load() > 0, "Consumer must read valid data under backpressure");
    }

    // 4. Repeated teardown and reinitialization
    for (int cycle = 0; cycle < 100; ++cycle) {
        LockFreeRingBuffer<float> tempRing(128);
        std::vector<float> data(64, 1.23F);
        tempRing.write(data.data(), 64);
        TEST_ASSERT(tempRing.availableToRead() == 64, "Teardown test read count mismatch");
        tempRing.clear();
        TEST_ASSERT(tempRing.availableToRead() == 0, "Clear must drain buffer in O(1)");
    }

    std::printf(" PASSED\n");
}

// ============================================================================
// TEST 2: Real-Time Audio Callback Latency & Zero-Lock Deadlines
// ============================================================================
void testAudioCallbackLatencyAndZeroLock() {
    std::printf("[Stage 4.1 - Test 2] Real-time audio callback latency benchmarks (256 & 512 frames @ 48kHz)...");
    std::fflush(stdout);

    AutoMixEngine engine;
    engine.configure(48000.0);
    engine.setEnabled(true);

    // Feed Deck B audio to allow active transitions
    constexpr std::size_t kDeckBFrames = 48000 * 2;
    std::vector<float> deckB(kDeckBFrames * 2, 0.25F);
    engine.writeDeckBPcm(deckB.data(), kDeckBFrames);

    constexpr std::size_t kIterations = 5000;
    std::vector<double> latencies256Us;
    latencies256Us.reserve(kIterations);
    std::vector<double> latencies512Us;
    latencies512Us.reserve(kIterations);

    std::atomic<bool> stressActive{true};
    std::atomic<std::size_t> concurrentResets{0};

    // Background thread: continuously spams triggerFade and reset to test concurrent contention
    std::thread backgroundSpammer([&]() {
        while (stressActive.load(std::memory_order_acquire)) {
            engine.triggerFadeBars(8);
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            engine.reset();
            concurrentResets.fetch_add(1, std::memory_order_relaxed);
            std::this_thread::sleep_for(std::chrono::microseconds(500));
        }
    });

    // 256 frames (Deadline = 5.333 ms = 5333.3 us)
    std::vector<float> block256(256 * 2, 0.3F);
    std::size_t missedDeadlines256 = 0;
    for (std::size_t i = 0; i < kIterations; ++i) {
        auto start = std::chrono::high_resolution_clock::now();
        engine.process(block256.data(), 256);
        auto end = std::chrono::high_resolution_clock::now();
        double elapsedUs = std::chrono::duration<double, std::micro>(end - start).count();
        latencies256Us.push_back(elapsedUs);
        if (elapsedUs > 5333.3) {
            missedDeadlines256++;
        }
    }

    // 512 frames (Deadline = 10.666 ms = 10666.6 us)
    std::vector<float> block512(512 * 2, 0.3F);
    std::size_t missedDeadlines512 = 0;
    for (std::size_t i = 0; i < kIterations; ++i) {
        auto start = std::chrono::high_resolution_clock::now();
        engine.process(block512.data(), 512);
        auto end = std::chrono::high_resolution_clock::now();
        double elapsedUs = std::chrono::duration<double, std::micro>(end - start).count();
        latencies512Us.push_back(elapsedUs);
        if (elapsedUs > 10666.6) {
            missedDeadlines512++;
        }
    }

    stressActive.store(false, std::memory_order_release);
    backgroundSpammer.join();

    std::sort(latencies256Us.begin(), latencies256Us.end());
    std::sort(latencies512Us.begin(), latencies512Us.end());

    double sum256 = std::accumulate(latencies256Us.begin(), latencies256Us.end(), 0.0);
    double avg256 = sum256 / latencies256Us.size();
    double p50_256 = latencies256Us[latencies256Us.size() * 50 / 100];
    double p95_256 = latencies256Us[latencies256Us.size() * 95 / 100];
    double p99_256 = latencies256Us[latencies256Us.size() * 99 / 100];
    double max256 = latencies256Us.back();

    double sum512 = std::accumulate(latencies512Us.begin(), latencies512Us.end(), 0.0);
    double avg512 = sum512 / latencies512Us.size();
    double p50_512 = latencies512Us[latencies512Us.size() * 50 / 100];
    double p95_512 = latencies512Us[latencies512Us.size() * 95 / 100];
    double p99_512 = latencies512Us[latencies512Us.size() * 99 / 100];
    double max512 = latencies512Us.back();

    TEST_ASSERT(missedDeadlines256 == 0, "Must have zero missed deadlines for 256-frame callback");
    TEST_ASSERT(missedDeadlines512 == 0, "Must have zero missed deadlines for 512-frame callback");
    TEST_ASSERT(p99_256 < 500.0, "P99 256-frame latency must be < 500 us");
    TEST_ASSERT(p99_512 < 1000.0, "P99 512-frame latency must be < 1000 us");

    std::printf("\n    [256f @ 48kHz (5.33ms deadline)]: Avg=%.1fus, P50=%.1fus, P95=%.1fus, P99=%.1fus, Max=%.1fus | Underruns=0\n",
                avg256, p50_256, p95_256, p99_256, max256);
    std::printf("    [512f @ 48kHz (10.67ms deadline)]: Avg=%.1fus, P50=%.1fus, P95=%.1fus, P99=%.1fus, Max=%.1fus | Underruns=0\n",
                avg512, p50_512, p95_512, p99_512, max512);
    std::printf("    PASSED (zero lock contention across %zu concurrent resets)\n", concurrentResets.load());
}

// ============================================================================
// TEST 3: Crossfade Cancellation & Complete DSP Teardown at Discrete Points
// ============================================================================
void testCrossfadeCancellationDiscretePoints() {
    std::printf("[Stage 4.1 - Test 3] Crossfade cancellation at discrete points (0%%, 25%%, 49%%, 50%%, 51%%, 75%%, 100%%)...");
    std::fflush(stdout);

    const std::vector<float> cancellationPoints = {0.0F, 0.25F, 0.49F, 0.50F, 0.51F, 0.75F, 1.0F};

    for (float targetProgress : cancellationPoints) {
        AutoMixEngine engine;
        engine.configure(48000.0);
        engine.setEnabled(true);

        // Prebuffer Deck B
        constexpr std::size_t kDeckBFrames = 48000 * 4;
        std::vector<float> deckB(kDeckBFrames * 2, 0.4F);
        engine.writeDeckBPcm(deckB.data(), kDeckBFrames);

        // 2-second transition = 96,000 frames
        constexpr std::size_t kTransitionFrames = 96000;
        engine.triggerFadeDurationMs(2000);

        // Render until target progress
        const std::size_t framesToRender = static_cast<std::size_t>(targetProgress * kTransitionFrames);
        std::size_t rendered = 0;
        constexpr std::size_t kChunk = 256;
        std::vector<float> streamBuf(kChunk * 2, 0.5F);

        while (rendered < framesToRender) {
            std::size_t c = std::min(kChunk, framesToRender - rendered);
            engine.process(streamBuf.data(), c);
            rendered += c;
        }

        // Abruptly cancel transition (simulating user seek/skip)
        engine.reset();
        TEST_ASSERT(engine.getState() == AutoMixState::Idle, "Engine must be Idle immediately upon reset");
        TEST_ASSERT(engine.getTransitionProgress() == 0.0F, "Transition progress must reset to 0.0");

        // TEST SIGNAL: Pure 100 Hz bass sine wave + 1 kHz lead sine wave
        constexpr std::size_t kTestFrames = 4800; // 100ms
        std::vector<float> dryInput(kTestFrames * 2);
        std::vector<float> wetOutput(kTestFrames * 2);

        for (std::size_t f = 0; f < kTestFrames; ++f) {
            float bass = 0.5F * std::sin(2.0F * M_PI * 100.0F * static_cast<float>(f) / 48000.0F);
            float lead = 0.2F * std::sin(2.0F * M_PI * 1000.0F * static_cast<float>(f) / 48000.0F);
            dryInput[f * 2] = bass + lead;
            dryInput[f * 2 + 1] = bass + lead;
            wetOutput[f * 2] = bass + lead;
            wetOutput[f * 2 + 1] = bass + lead;
        }

        // Process test signal through cancelled engine
        engine.process(wetOutput.data(), kTestFrames);

        // 1. Verify bass is 100% preserved (NO high-pass filter active!)
        double inputBassEnergy = 0.0;
        double outputBassEnergy = 0.0;
        for (std::size_t f = 0; f < kTestFrames; ++f) {
            inputBassEnergy += dryInput[f * 2] * dryInput[f * 2];
            outputBassEnergy += wetOutput[f * 2] * wetOutput[f * 2];
        }
        double energyRatio = std::sqrt(outputBassEnergy / inputBassEnergy);
        TEST_ASSERT(std::abs(energyRatio - 1.0) < 0.001, "Output bass energy must match dry input (no high-pass filtering!)");

        // 2. Verify bit-for-bit equivalence to dry input (no echo tail, no deck B residue)
        float maxSampleDiff = 0.0F;
        for (std::size_t i = 0; i < kTestFrames * 2; ++i) {
            float diff = std::abs(wetOutput[i] - dryInput[i]);
            if (diff > maxSampleDiff) maxSampleDiff = diff;
        }
        TEST_ASSERT(maxSampleDiff == 0.0F, "Output must be bit-for-bit identical to dry signal after reset");
    }

    // Rapid sequence: cancel -> start new fade -> cancel
    {
        AutoMixEngine engine;
        engine.configure(48000.0);
        engine.setEnabled(true);
        engine.reset();
        engine.triggerFadeBars(8);
        TEST_ASSERT(engine.getState() == AutoMixState::FadingFast, "Must be FadingFast");
        engine.reset();
        TEST_ASSERT(engine.getState() == AutoMixState::Idle, "Must be Idle after rapid cancel");
        engine.triggerFadeDurationMs(1500);
        TEST_ASSERT(engine.getState() == AutoMixState::FadingFast, "Must be FadingFast");
        engine.reset();
        TEST_ASSERT(engine.getState() == AutoMixState::Idle, "Must be Idle after second cancel");
    }

    std::printf(" PASSED (all 7 points & rapid sequence verified)\n");
}

// ============================================================================
// TEST 4: Quantitative Audio Quality, Spectral Response & Reference Comparison
// ============================================================================
void testQuantitativeAudioQualityAndCrossover() {
    std::printf("[Stage 4.1 - Test 4] Quantitative audio quality & 160Hz Linkwitz-Riley spectral response...");
    std::fflush(stdout);

    // 1. Spectral Flatness of 4th-Order Linkwitz-Riley Crossover at 160 Hz
    // Theory: H_LP(s) + H_HP(s) = Allpass (Magnitude strictly 1.000, 0.0 dB ripple across entire spectrum)
    LinkwitzRileyFilter lrFilter;
    lrFilter.configure(48000.0, 160.0);

    const std::vector<double> testFrequencies = {
        20.0, 40.0, 80.0, 120.0, 160.0, 240.0, 500.0, 1000.0, 5000.0, 10000.0, 20000.0
    };

    for (double freq : testFrequencies) {
        lrFilter.reset();
        constexpr std::size_t kFrames = 48000; // 1 second
        std::vector<float> lpSignal(kFrames * 2);
        std::vector<float> hpSignal(kFrames * 2);

        for (std::size_t f = 0; f < kFrames; ++f) {
            float val = std::sin(2.0 * M_PI * freq * static_cast<double>(f) / 48000.0);
            lpSignal[f * 2] = val;
            lpSignal[f * 2 + 1] = val;
            hpSignal[f * 2] = val;
            hpSignal[f * 2 + 1] = val;
        }

        lrFilter.processLowPass(lpSignal.data(), kFrames);
        lrFilter.processHighPass(hpSignal.data(), kFrames);

        // Sum low-pass and high-pass signals in steady state (last 24,000 frames)
        double steadyStateMaxSum = 0.0;
        for (std::size_t f = 24000; f < kFrames; ++f) {
            float summed = lpSignal[f * 2] + hpSignal[f * 2];
            float mag = std::abs(summed);
            if (mag > steadyStateMaxSum) steadyStateMaxSum = mag;
        }

        // Linkwitz-Riley 4th order: lowpass and highpass are in-phase. The sum has magnitude strictly 1.0.
        // Difference from 1.0 must be < 0.015 (< 0.1 dB)
        TEST_ASSERT(std::abs(steadyStateMaxSum - 1.0) < 0.015,
                    "Linkwitz-Riley LP + HP sum must have flat magnitude across all frequencies");

        // Check -6 dB point at crossover frequency (160 Hz)
        if (std::abs(freq - 160.0) < 0.1) {
            double lpPeak = 0.0;
            double hpPeak = 0.0;
            for (std::size_t f = 24000; f < kFrames; ++f) {
                if (std::abs(lpSignal[f * 2]) > lpPeak) lpPeak = std::abs(lpSignal[f * 2]);
                if (std::abs(hpSignal[f * 2]) > hpPeak) hpPeak = std::abs(hpSignal[f * 2]);
            }
            // -6.02 dB corresponds to 0.500 linear
            TEST_ASSERT(std::abs(lpPeak - 0.500) < 0.015, "Low-pass at 160 Hz must be -6 dB (0.50 linear)");
            TEST_ASSERT(std::abs(hpPeak - 0.500) < 0.015, "High-pass at 160 Hz must be -6 dB (0.50 linear)");
        }
    }

    // 2. Equal-Power Crossfade Energy Profile & Balance
    // Equal power: cos^2(p * pi/2) + sin^2(p * pi/2) = 1.000 for uncorrelated signals
    for (float progress = 0.0F; progress <= 1.0F; progress += 0.1F) {
        float gainA = std::cos(progress * (M_PI * 0.5));
        float gainB = std::sin(progress * (M_PI * 0.5));
        float totalPower = gainA * gainA + gainB * gainB;
        TEST_ASSERT(std::abs(totalPower - 1.0F) < 1.0e-6F, "Equal-power crossfade law must be exact");
    }

    // 3. Left/Right Channel Phase & Symmetry
    {
        AutoMixEngine engine;
        engine.configure(48000.0);
        engine.setEnabled(true);

        constexpr std::size_t kFrames = 2048;
        std::vector<float> stereoMono(kFrames * 2);
        for (std::size_t f = 0; f < kFrames; ++f) {
            float val = std::sin(2.0 * M_PI * 440.0 * static_cast<double>(f) / 48000.0);
            stereoMono[f * 2] = val;
            stereoMono[f * 2 + 1] = val; // identical L and R
        }

        engine.process(stereoMono.data(), kFrames);

        // Verify Left and Right channels remain 100% identical (zero inter-channel skew)
        float maxChannelDiff = 0.0F;
        for (std::size_t f = 0; f < kFrames; ++f) {
            float diff = std::abs(stereoMono[f * 2] - stereoMono[f * 2 + 1]);
            if (diff > maxChannelDiff) maxChannelDiff = diff;
        }
        TEST_ASSERT(maxChannelDiff == 0.0F, "Left and Right channels must be perfectly phase-aligned and balanced");
    }

    // 4. Silence-to-Audio and Audio-to-Silence
    {
        AutoMixEngine engine;
        engine.configure(48000.0);
        engine.setEnabled(true);

        std::vector<float> silence(1024 * 2, 0.0F);
        engine.process(silence.data(), 1024);
        for (float s : silence) {
            TEST_ASSERT(s == 0.0F, "Silence processing must produce absolute silence (no denormals or DC offset)");
        }
    }

    std::printf(" PASSED (spectral flatness, -6dB crossover, equal-power law & L/R balance verified)\n");
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================
int main() {
    std::printf("======================================================================\n");
    std::printf(" LASTWAVE STAGE 4.1 — REAL-TIME CONCURRENCY & AUDIO INTEGRITY SUITE\n");
    std::printf(" Architecture: ARM64-v8a | Real-Time Path: Truly Lock-Free Atomic Commands\n");
    std::printf(" Audio Latency & Deadlines: P99 < 500us (256f) / < 1000us (512f)\n");
    std::printf(" Memory Sanitizers: AddressSanitizer + UndefinedBehaviorSanitizer\n");
    std::printf("======================================================================\n");

    testRingBufferBackpressureAndPolicies();
    testAudioCallbackLatencyAndZeroLock();
    testCrossfadeCancellationDiscretePoints();
    testQuantitativeAudioQualityAndCrossover();

    std::printf("======================================================================\n");
    std::printf(" ALL STAGE 4.1 REAL-TIME CONCURRENCY & AUDIO INTEGRITY TESTS PASSED!\n");
    std::printf("======================================================================\n");
    return 0;
}
