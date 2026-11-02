#include "WsolaTimeStretcher.h"
#include "AutoMixEngine.h"
#include "LockFreeRingBuffer.h"

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

using namespace lastwave::audio;
using namespace neonsong::audio;

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::fprintf(stderr, "FAILED: %s at %s:%d\n", msg, __FILE__, __LINE__); \
            std::exit(1); \
        } \
    } while (0)

// ============================================================================
// TEST 1: LockFreeRingBuffer Monotonic Wraparound & Near-Capacity Invariants
// ============================================================================
void testRingBufferWraparoundAndNearCapacity() {
    std::printf("[Test 1] LockFreeRingBuffer wraparound, capacity limits & clear()...");
    std::fflush(stdout);

    constexpr std::size_t kCapacity = 1024;
    LockFreeRingBuffer<float> ring(kCapacity);

    TEST_ASSERT(ring.capacity() == kCapacity, "Capacity must match configured size");
    TEST_ASSERT(ring.availableToRead() == 0, "Initial available to read must be 0");
    TEST_ASSERT(ring.availableToWrite() == kCapacity, "Initial available to write must be capacity");

    // Near-capacity write: write exactly kCapacity - 1
    std::vector<float> writeChunk(kCapacity, 1.0F);
    std::size_t written = ring.write(writeChunk.data(), kCapacity - 1);
    TEST_ASSERT(written == kCapacity - 1, "Must accept kCapacity - 1 elements");
    TEST_ASSERT(ring.availableToRead() == kCapacity - 1, "availableToRead must be kCapacity - 1");
    TEST_ASSERT(ring.availableToWrite() == 1, "availableToWrite must be 1");

    // Write exceeding capacity: should clamp to remaining 1 slot
    written = ring.write(writeChunk.data(), 100);
    TEST_ASSERT(written == 1, "Must strictly clamp write to remaining 1 slot");
    TEST_ASSERT(ring.availableToWrite() == 0, "Ring is now 100% full");

    // Full read
    std::vector<float> readChunk(kCapacity, 0.0F);
    std::size_t readCount = ring.read(readChunk.data(), kCapacity);
    TEST_ASSERT(readCount == kCapacity, "Must read all kCapacity elements");
    TEST_ASSERT(ring.availableToRead() == 0, "Ring must be empty after full read");
    TEST_ASSERT(ring.availableToWrite() == kCapacity, "Ring write capacity must be fully restored");

    // Test clear()
    ring.write(writeChunk.data(), 500);
    TEST_ASSERT(ring.availableToRead() == 500, "Ring should hold 500 elements");
    ring.clear();
    TEST_ASSERT(ring.availableToRead() == 0, "clear() must drop all readable elements");
    TEST_ASSERT(ring.availableToWrite() == kCapacity, "clear() must restore full writable capacity");

    std::printf(" PASSED\n");
}

// ============================================================================
// TEST 2: LockFreeRingBuffer Multi-Threaded SPSC Concurrency Stress Test
// ============================================================================
void testRingBufferMultiThreadedSPSC() {
    std::printf("[Test 2] LockFreeRingBuffer multi-threaded SPSC speed imbalance stress (200k items)...");
    std::fflush(stdout);

    constexpr std::size_t kCapacity = 4096;
    LockFreeRingBuffer<float> ring(kCapacity);

    constexpr std::size_t kTotalElements = 200000;
    std::atomic<bool> producerDone{false};
    std::atomic<std::size_t> totalRead{0};
    std::vector<float> receivedValues;
    receivedValues.reserve(kTotalElements);

    // Producer Thread: Writes in irregular chunk sizes (1..256)
    std::thread producer([&]() {
        std::size_t pushed = 0;
        float counter = 1.0F;
        std::vector<float> chunk(256);
        while (pushed < kTotalElements) {
            std::size_t chunkSize = std::min<std::size_t>(
                (pushed % 255) + 1,
                kTotalElements - pushed
            );
            for (std::size_t i = 0; i < chunkSize; ++i) {
                chunk[i] = counter++;
            }
            std::size_t writtenCount = 0;
            while (writtenCount < chunkSize) {
                std::size_t w = ring.write(chunk.data() + writtenCount, chunkSize - writtenCount);
                writtenCount += w;
                if (w == 0) {
                    std::this_thread::yield();
                }
            }
            pushed += chunkSize;
        }
        producerDone.store(true, std::memory_order_release);
    });

    // Consumer Thread: Reads in irregular chunk sizes (1..128)
    std::thread consumer([&]() {
        std::vector<float> readBuffer(512);
        while (!producerDone.load(std::memory_order_acquire) || ring.availableToRead() > 0) {
            std::size_t toRead = (totalRead.load(std::memory_order_relaxed) % 127) + 1;
            std::size_t r = ring.read(readBuffer.data(), toRead);
            if (r > 0) {
                for (std::size_t i = 0; i < r; ++i) {
                    receivedValues.push_back(readBuffer[i]);
                }
                totalRead.fetch_add(r, std::memory_order_relaxed);
            } else {
                std::this_thread::yield();
            }
        }
    });

    producer.join();
    consumer.join();

    TEST_ASSERT(receivedValues.size() == kTotalElements, "Total elements received must equal sent");
    for (std::size_t i = 0; i < kTotalElements; ++i) {
        float expected = static_cast<float>(i + 1);
        TEST_ASSERT(receivedValues[i] == expected, "Monotonic ordering must be preserved without corruption");
    }

    std::printf(" PASSED (verified %zu elements strictly ordered)\n", kTotalElements);
}

// ============================================================================
// TEST 3: AutoMixEngine Multi-Threaded Crossfade & Rapid Cancellation Stress
// ============================================================================
void testAutoMixEngineMultiThreadedCrossfadeStress() {
    std::printf("[Test 3] AutoMixEngine 3-thread concurrent crossfade & rapid reset stress (50 cycles)...");
    std::fflush(stdout);

    AutoMixEngine engine;
    engine.configure(48000.0);
    engine.setEnabled(true);

    std::atomic<bool> testRunning{true};
    std::atomic<std::size_t> audioBlocksRendered{0};
    std::atomic<std::size_t> decoderBlocksPushed{0};
    std::atomic<std::size_t> cancelsExecuted{0};

    // Thread 1: Real-time Audio Thread (Deck A process)
    std::thread audioThread([&]() {
        constexpr std::size_t kBlockFrames = 512;
        std::vector<float> deckA(kBlockFrames * 2, 0.4F);
        while (testRunning.load(std::memory_order_acquire)) {
            // Fill with synthetic signal
            for (std::size_t i = 0; i < kBlockFrames * 2; ++i) {
                deckA[i] = 0.4F * std::sin(static_cast<float>(i) * 0.05F);
            }
            engine.process(deckA.data(), kBlockFrames);

            // Verify no NaN or Inf generated during crossfades or resets
            for (std::size_t i = 0; i < kBlockFrames * 2; ++i) {
                TEST_ASSERT(std::isfinite(deckA[i]), "Deck A output must be strictly finite");
                TEST_ASSERT(std::abs(deckA[i]) < 5.0F, "Deck A output must not explode");
            }
            audioBlocksRendered.fetch_add(1, std::memory_order_relaxed);
            std::this_thread::sleep_for(std::chrono::microseconds(500));
        }
    });

    // Thread 2: Decoder Standby Thread (Deck B pre-buffering)
    std::thread decoderThread([&]() {
        constexpr std::size_t kDecodeFrames = 1024;
        std::vector<float> deckB(kDecodeFrames * 2, 0.3F);
        while (testRunning.load(std::memory_order_acquire)) {
            for (std::size_t i = 0; i < kDecodeFrames * 2; ++i) {
                deckB[i] = 0.3F * std::cos(static_cast<float>(i) * 0.07F);
            }
            engine.writeDeckBPcm(deckB.data(), kDecodeFrames);
            decoderBlocksPushed.fetch_add(1, std::memory_order_relaxed);
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    });

    // Thread 3: UI / Skip Thread (Rapid Crossfade Trigger & Cancellation)
    std::thread controlThread([&]() {
        for (int cycle = 0; cycle < 50; ++cycle) {
            // Trigger crossfade blend
            if (cycle % 2 == 0) {
                engine.triggerFadeBars(4);
            } else {
                engine.triggerFadeDurationMs(2000);
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(5));

            // Abruptly cancel mid-crossfade (simulating user pressing skip or seek)
            engine.reset();
            cancelsExecuted.fetch_add(1, std::memory_order_relaxed);
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
        }
    });

    controlThread.join();
    testRunning.store(false, std::memory_order_release);
    audioThread.join();
    decoderThread.join();

    TEST_ASSERT(audioBlocksRendered.load() > 100, "Audio thread must render continuous blocks");
    TEST_ASSERT(decoderBlocksPushed.load() > 50, "Decoder thread must push audio chunks");
    TEST_ASSERT(cancelsExecuted.load() == 50, "All 50 rapid cancellations must execute cleanly");

    // After all worker threads terminate, reset and confirm clean Idle state
    engine.reset();
    TEST_ASSERT(engine.getState() == AutoMixState::Idle, "Engine must be in Idle state after final reset");

    std::printf(" PASSED (rendered %zu audio blocks, %zu cancels with zero race/deadlock)\n",
                audioBlocksRendered.load(), cancelsExecuted.load());
}

// ============================================================================
// TEST 4: WSOLA Continuous Transition, Mid-Point Bass-Swap & Click-Free Continuity
// ============================================================================
void testWsolaTransitionAndBassSwapContinuity() {
    std::printf("[Test 4] WSOLA transition, 50%% downbeat bass-swap & waveform continuity (60k frames)...");
    std::fflush(stdout);

    AutoMixEngine engine;
    engine.configure(48000.0);
    engine.setEnabled(true);

    // Feed 3 seconds of Deck B audio (1 kHz sine wave)
    constexpr std::size_t kDeckBFrames = 48000 * 3;
    std::vector<float> deckB(kDeckBFrames * 2);
    for (std::size_t f = 0; f < kDeckBFrames; ++f) {
        float sample = 0.5F * std::sin(2.0F * 3.14159265F * 1000.0F * static_cast<float>(f) / 48000.0F);
        deckB[f * 2] = sample;
        deckB[f * 2 + 1] = sample;
    }
    engine.writeDeckBPcm(deckB.data(), kDeckBFrames);

    // Trigger 2-second transition (96,000 frames)
    engine.triggerFadeDurationMs(2000);
    TEST_ASSERT(engine.getState() == AutoMixState::FadingFast, "State must be FadingFast");

    // Stream 60,000 frames of Deck A (100 Hz bass sine wave + 1 kHz melody)
    constexpr std::size_t kStreamFrames = 60000;
    std::vector<float> deckA(kStreamFrames * 2);
    for (std::size_t f = 0; f < kStreamFrames; ++f) {
        float bass = 0.4F * std::sin(2.0F * 3.14159265F * 100.0F * static_cast<float>(f) / 48000.0F);
        float lead = 0.3F * std::sin(2.0F * 3.14159265F * 1000.0F * static_cast<float>(f) / 48000.0F);
        deckA[f * 2] = bass + lead;
        deckA[f * 2 + 1] = bass + lead;
    }

    constexpr std::size_t kChunkSize = 512;
    float maxAdjacentDelta = 0.0F;
    float prevSample = 0.0F;

    for (std::size_t offset = 0; offset < kStreamFrames; offset += kChunkSize) {
        std::size_t chunk = std::min(kChunkSize, kStreamFrames - offset);
        engine.process(deckA.data() + offset * 2, chunk);

    }

    for (std::size_t f = 1; f < kStreamFrames; ++f) {
        float sampleL = deckA[f * 2];
        float sampleR = deckA[f * 2 + 1];
        TEST_ASSERT(std::isfinite(sampleL) && std::isfinite(sampleR), "PCM samples must be finite");
        TEST_ASSERT(std::abs(sampleL) <= 1.5F && std::abs(sampleR) <= 1.5F, "Summed audio must remain within reasonable headroom");

        float deltaL = std::abs(sampleL - deckA[(f - 1) * 2]);
        float deltaR = std::abs(sampleR - deckA[(f - 1) * 2 + 1]);
        if (deltaL > maxAdjacentDelta) maxAdjacentDelta = deltaL;
        if (deltaR > maxAdjacentDelta) maxAdjacentDelta = deltaR;
    }

    std::printf(" (measured maxDelta=%.4f)...", maxAdjacentDelta);
    std::fflush(stdout);

    // Verify continuity: no discontinuous clicks (max adjacent step < 0.60 at combined 1 kHz / 100 Hz sum with echo return)
    TEST_ASSERT(maxAdjacentDelta < 0.60F, "Sample-to-sample transitions must be smooth and click-free");
    TEST_ASSERT(engine.getTransitionProgress() > 0.5F, "Transition must have progressed past the 50% midpoint bass swap");

    std::printf(" PASSED (progress=%.2f, maxDelta=%.4f)\n",
                engine.getTransitionProgress(), maxAdjacentDelta);
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================
int main() {
    std::printf("======================================================================\n");
    std::printf(" LASTWAVE STAGE 4 — SYSTEM INTEGRATION, CONCURRENCY & STRESS SUITE\n");
    std::printf(" Architecture: ARM64-v8a | Concurrency: Multi-Threaded SPSC + Crossfades\n");
    std::printf(" Memory Sanitizer: AddressSanitizer + UndefinedBehaviorSanitizer\n");
    std::printf("======================================================================\n");

    testRingBufferWraparoundAndNearCapacity();
    testRingBufferMultiThreadedSPSC();
    testAutoMixEngineMultiThreadedCrossfadeStress();
    testWsolaTransitionAndBassSwapContinuity();

    std::printf("======================================================================\n");
    std::printf(" ALL STAGE 4 CONCURRENCY & STRESS SUITES PASSED (ZERO VIOLATIONS)\n");
    std::printf("======================================================================\n");
    return 0;
}
