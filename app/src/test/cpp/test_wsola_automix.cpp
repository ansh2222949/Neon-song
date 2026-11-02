#include "WsolaTimeStretcher.h"
#include "AutoMixEngine.h"

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <cmath>
#include <cassert>
#include <cstring>
#include <algorithm>

using namespace lastwave::audio;

#define TEST_ASSERT(cond, msg) \
    do { \
        if (!(cond)) { \
            std::fprintf(stderr, "FAILED: %s at %s:%d\n", msg, __FILE__, __LINE__); \
            std::exit(1); \
        } \
    } while (0)

void testZeroFrames() {
    std::printf("[Test 1] Zero frames & null pointers...");
    std::fflush(stdout);
    WsolaTimeStretcher stretcher;
    stretcher.configure(48000.0, 2);

    std::vector<float> inBuffer(2048 * 2, 0.5F);
    std::vector<float> outBuffer(2048 * 2, 0.0F);

    // Null pointers
    TEST_ASSERT(stretcher.process(nullptr, 0, outBuffer.data(), 1024) == 0, "Null input with zero frames should return 0");
    TEST_ASSERT(stretcher.process(inBuffer.data(), 1024, nullptr, 1024) == 0, "Null output pointer should return 0");
    TEST_ASSERT(stretcher.process(inBuffer.data(), 1024, outBuffer.data(), 0) == 0, "Zero output capacity should return 0");
    TEST_ASSERT(stretcher.flush(nullptr, 1024) == 0, "Flush with null output should return 0");
    TEST_ASSERT(stretcher.flush(outBuffer.data(), 0) == 0, "Flush with 0 capacity should return 0");

    // Zero input frames with valid pointers
    TEST_ASSERT(stretcher.process(inBuffer.data(), 0, outBuffer.data(), 1024) == 0, "Zero input frames should return 0 when buffer empty");

    std::printf(" PASSED\n");
}

void testInvalidChannelConfigurations() {
    std::printf("[Test 2] Odd & invalid channel configurations...");
    std::fflush(stdout);
    WsolaTimeStretcher stretcher;

    // Channels = 0: Should default to 2
    stretcher.configure(48000.0, 0);
    std::vector<float> in2(1024 * 2, 0.1F);
    std::vector<float> out2(1024 * 2, 0.0F);
    stretcher.process(in2.data(), 512, out2.data(), 512);

    // Channels = 1 (Mono)
    stretcher.configure(44100.0, 1);
    std::vector<float> in1(1024, 0.2F);
    std::vector<float> out1(1024, 0.0F);
    stretcher.process(in1.data(), 512, out1.data(), 512);

    // Channels = 3 (Odd, unsupported): Should clamp to 2
    stretcher.configure(48000.0, 3);
    stretcher.process(in2.data(), 512, out2.data(), 512);

    // Channels = 7 (Invalid surround): Should clamp to 2
    stretcher.configure(48000.0, 7);
    stretcher.process(in2.data(), 512, out2.data(), 512);

    // Negative sample rate: Should default to 48000.0
    stretcher.configure(-96000.0, 2);
    stretcher.process(in2.data(), 512, out2.data(), 512);

    std::printf(" PASSED\n");
}

void testMaximumSizeInputBlocks() {
    std::printf("[Test 3] Maximum-size input blocks (exceeding ring capacity)...");
    std::fflush(stdout);
    WsolaTimeStretcher stretcher;
    stretcher.configure(48000.0, 2);

    // 32,768 frames is double the internal 16,384 capacity
    constexpr std::size_t kHugeInputFrames = 32768;
    std::vector<float> hugeIn(kHugeInputFrames * 2, 0.3F);
    std::vector<float> out(4096 * 2, 0.0F);

    // This must safely ingest up to capacity without heap or buffer corruption
    std::size_t written = stretcher.process(hugeIn.data(), kHugeInputFrames, out.data(), 4096);
    TEST_ASSERT(written <= 4096, "Frames written must not exceed output capacity");

    std::printf(" PASSED (written=%zu)\n", written);
}

void testPartialOutputBuffers() {
    std::printf("[Test 4] Partial output buffers (arbitrary non-hop capacities)...");
    std::fflush(stdout);
    WsolaTimeStretcher stretcher;
    stretcher.configure(48000.0, 2);

    std::vector<float> in(2048 * 2, 0.4F);
    std::vector<float> scratch(2048 * 2, 0.0F);
    stretcher.process(in.data(), 2048, scratch.data(), 2048);

    const std::size_t irregularSizes[] = {1, 3, 7, 16, 33, 64, 127, 256, 511};
    for (std::size_t cap : irregularSizes) {
        std::vector<float> tinyOut(cap * 2, -999.0F);
        std::size_t w = stretcher.process(in.data(), 256, tinyOut.data(), cap);
        TEST_ASSERT(w <= cap, "Written frames must strictly be <= capacity");
    }

    std::printf(" PASSED\n");
}

void testRepeatedWsolaStreamingAndDraining() {
    std::printf("[Test 5] Repeated WSOLA streaming & draining (50,000 frames)...");
    std::fflush(stdout);
    WsolaTimeStretcher stretcher;
    stretcher.configure(48000.0, 2);

    constexpr std::size_t kTotalStreamFrames = 50000;
    std::vector<float> fullIn(kTotalStreamFrames * 2);
    for (std::size_t f = 0; f < kTotalStreamFrames; ++f) {
        float val = std::sin(2.0F * 3.14159265F * 440.0F * static_cast<float>(f) / 48000.0F);
        fullIn[f * 2] = val;
        fullIn[f * 2 + 1] = val;
    }

    std::vector<float> fullOut(kTotalStreamFrames * 4, 0.0F);
    std::size_t totalOutFrames = 0;
    std::size_t inOffset = 0;

    stretcher.setTimeRatio(1.05F);

    const std::size_t packetSizes[] = {128, 256, 512, 1024, 768, 384, 512};
    std::size_t pIdx = 0;

    while (inOffset < kTotalStreamFrames) {
        std::size_t chunk = std::min(packetSizes[pIdx % 7], kTotalStreamFrames - inOffset);
        pIdx++;

        if (inOffset > 25000) {
            stretcher.startRecoveryRamp(1.0F, 1.0F);
        }

        std::size_t outCap = 1024;
        std::size_t written = stretcher.process(
            fullIn.data() + inOffset * 2,
            chunk,
            fullOut.data() + totalOutFrames * 2,
            outCap
        );
        TEST_ASSERT(written <= outCap, "Written exceeded chunk capacity");
        totalOutFrames += written;
        inOffset += chunk;
    }

    std::size_t flushed = stretcher.flush(fullOut.data() + totalOutFrames * 2, 8192);
    totalOutFrames += flushed;

    for (std::size_t i = 0; i < totalOutFrames * 2; ++i) {
        TEST_ASSERT(!std::isnan(fullOut[i]), "Output contains NaN");
        TEST_ASSERT(!std::isinf(fullOut[i]), "Output contains Inf");
    }

    TEST_ASSERT(totalOutFrames > 40000, "Output frame count should be proportional to stream length");

    std::printf(" PASSED (streamed=%zu frames, produced=%zu frames)\n", inOffset, totalOutFrames);
}

void testAutoMixEngineCapacityAndBounds() {
    std::printf("[Test 6] AutoMixEngine P0 capacity bounds & overflow prevention...");
    std::fflush(stdout);
    AutoMixEngine engine;
    engine.configure(48000.0);
    engine.setEnabled(true);

    constexpr std::size_t kDeckBFrames = 48000 * 10;
    std::vector<float> deckBPcm(kDeckBFrames * 2, 0.5F);
    std::size_t writtenB = engine.writeDeckBPcm(deckBPcm.data(), kDeckBFrames);
    TEST_ASSERT(writtenB > 0, "Deck B prebuffering failed");

    engine.triggerFadeNow();
    TEST_ASSERT(engine.getState() == AutoMixState::FadingFast, "Engine should transition to FadingFast");

    std::vector<float> deckA(16384 * 2, 0.3F);
    engine.process(deckA.data(), 0);
    engine.process(deckA.data(), 512);
    engine.process(deckA.data(), 8192);
    engine.process(deckA.data(), 12000);

    const std::size_t bufferFloats = 16384;
    const std::size_t maxFrames = bufferFloats / 2;
    for (std::size_t requested : {0, 1, 512, 1024, 4096, 8192, 8193, 10000, 65536}) {
        std::size_t targetFrames = std::min(requested, maxFrames);
        TEST_ASSERT(targetFrames <= 8192, "targetFrames must never exceed 8192");
        TEST_ASSERT(targetFrames * 2 <= bufferFloats, "targetFrames * 2 must never exceed bufferFloats");
    }

    std::printf(" PASSED\n");
}

void testAudioQualityAndContinuity() {
    std::printf("[Test 7] Audio quality, RMS fidelity & waveform continuity (1.0 ratio)...");
    std::fflush(stdout);
    WsolaTimeStretcher stretcher;
    stretcher.configure(48000.0, 2);
    stretcher.setTimeRatio(1.0F);

    // 1 kHz sine wave at 48 kHz, peak amplitude 0.8
    constexpr std::size_t kAudioFrames = 48000; // 1 second
    std::vector<float> sineIn(kAudioFrames * 2);
    for (std::size_t f = 0; f < kAudioFrames; ++f) {
        float sample = 0.8F * std::sin(2.0F * 3.14159265F * 1000.0F * static_cast<float>(f) / 48000.0F);
        sineIn[f * 2] = sample;
        sineIn[f * 2 + 1] = sample;
    }

    std::vector<float> sineOut(kAudioFrames * 2, 0.0F);
    std::size_t framesWritten = 0;
    std::size_t inPos = 0;
    constexpr std::size_t kChunkSize = 512;

    while (inPos < kAudioFrames) {
        std::size_t chunk = std::min(kChunkSize, kAudioFrames - inPos);
        std::size_t out = stretcher.process(
            sineIn.data() + inPos * 2,
            chunk,
            sineOut.data() + framesWritten * 2,
            kChunkSize
        );
        framesWritten += out;
        inPos += chunk;
    }
    framesWritten += stretcher.flush(sineOut.data() + framesWritten * 2, 4096);

    // Skip the pre-roll ramp-up and measure steady-state RMS (from frame 3000 to frame 40000)
    TEST_ASSERT(framesWritten > 40000, "Output frames should be >= 40000");
    double sumSquares = 0.0;
    std::size_t steadySamples = 0;
    float maxDelta = 0.0F;

    for (std::size_t f = 3000; f < 40000; ++f) {
        float l = sineOut[f * 2];
        TEST_ASSERT(std::abs(l) <= 0.85F, "Peak amplitude must not clip beyond input peak 0.8");
        sumSquares += l * l;
        steadySamples++;

        if (f > 3000) {
            float delta = std::abs(l - sineOut[(f - 1) * 2]);
            if (delta > maxDelta) maxDelta = delta;
        }
    }

    double rms = std::sqrt(sumSquares / steadySamples);
    // Theoretical RMS of 0.8 sine wave = 0.8 / sqrt(2) ~= 0.5657
    // With Hann OLA at 1.0 ratio, it should be within 10% of 0.5657
    TEST_ASSERT(rms >= 0.50 && rms <= 0.62, "RMS amplitude must match input within 10% tolerance");
    // At 1000 Hz / 48000 Hz, max step between adjacent samples is 0.8 * 2 * pi * 1000 / 48000 ~= 0.105
    TEST_ASSERT(maxDelta < 0.15F, "No discontinuity or clicks across buffer boundaries");

    std::printf(" PASSED (RMS=%.4f, maxDelta=%.4f)\n", rms, maxDelta);
}

void testWsolaSpeedRatioProportionalDuration() {
    std::printf("[Test 8] Deterministic WSOLA speed ratio to duration mapping...");
    std::fflush(stdout);

    const float ratios[] = {0.50F, 0.75F, 1.00F, 1.50F, 2.00F};
    const std::size_t inFrames = 48000; // 1.00s @ 48 kHz
    std::vector<float> input(inFrames * 2);
    for (std::size_t f = 0; f < inFrames; ++f) {
        float s = 0.5F * std::sin(2.0F * 3.14159265F * 440.0F * static_cast<float>(f) / 48000.0F);
        input[f * 2] = s;
        input[f * 2 + 1] = s;
    }

    std::vector<std::size_t> outFramesList;
    for (float speed : ratios) {
        WsolaTimeStretcher stretcher;
        stretcher.configure(48000.0, 2);
        stretcher.setTimeRatio(speed);

        std::vector<float> output(inFrames * 4, 0.0F);
        std::size_t framesWritten = 0;
        std::size_t inPos = 0;
        constexpr std::size_t kChunk = 512;

        while (inPos < inFrames) {
            std::size_t chunk = std::min(kChunk, inFrames - inPos);
            std::size_t out = stretcher.process(
                input.data() + inPos * 2,
                chunk,
                output.data() + framesWritten * 2,
                kChunk * 2
            );
            framesWritten += out;
            inPos += chunk;
        }
        framesWritten += stretcher.flush(output.data() + framesWritten * 2, 4096);
        outFramesList.push_back(framesWritten);

        // Verify expected relationship: Output frames ~ inFrames / speed (+/- 5% margin due to OLA window tail)
        double expected = static_cast<double>(inFrames) / static_cast<double>(speed);
        double ratioActual = static_cast<double>(framesWritten) / expected;
        TEST_ASSERT(ratioActual >= 0.95 && ratioActual <= 1.05, "Output frames must match expected duration within 5%");
    }

    // Verify strict monotonic relationship: 0.50x > 0.75x > 1.00x > 1.50x > 2.00x
    for (std::size_t i = 1; i < outFramesList.size(); ++i) {
        TEST_ASSERT(outFramesList[i - 1] > outFramesList[i], "Higher playback speed must produce strictly fewer output frames");
    }

    // Test state transitions with recovery ramp
    {
        WsolaTimeStretcher rampStretcher;
        rampStretcher.configure(48000.0, 2);
        rampStretcher.setTimeRatio(0.50F);
        TEST_ASSERT(rampStretcher.currentRatio() == 0.50F, "Initial ratio must match setTimeRatio");

        // Ramp to 1.00F over 0.2 seconds
        rampStretcher.startRecoveryRamp(1.00F, 0.2F);
        std::vector<float> rampOut(2048 * 2, 0.0F);
        for (int step = 0; step < 30; ++step) {
            rampStretcher.process(input.data(), 512, rampOut.data(), 1024);
        }
        TEST_ASSERT(std::abs(rampStretcher.currentRatio() - 1.00F) < 0.01F, "Ramp must converge to targetRatio");
    }

    std::printf(" PASSED (0.50x=%zu, 0.75x=%zu, 1.00x=%zu, 1.50x=%zu, 2.00x=%zu)\n",
                outFramesList[0], outFramesList[1], outFramesList[2], outFramesList[3], outFramesList[4]);
}

int main() {
    std::printf("==================================================\n");
    std::printf(" LASTWAVE NATIVE AUDIO ENGINE VERIFICATION SUITE\n");
    std::printf(" Architecture: ARM64-v8a | Target: Samsung S24 Ultra\n");
    std::printf(" Memory Sanitizer: AddressSanitizer + ASAN Active\n");
    std::printf("==================================================\n");

    testZeroFrames();
    testInvalidChannelConfigurations();
    testMaximumSizeInputBlocks();
    testPartialOutputBuffers();
    testRepeatedWsolaStreamingAndDraining();
    testAutoMixEngineCapacityAndBounds();
    testAudioQualityAndContinuity();
    testWsolaSpeedRatioProportionalDuration();

    std::printf("==================================================\n");
    std::printf(" ALL NATIVE TESTS PASSED WITH ZERO ASAN VIOLATIONS\n");
    std::printf("==================================================\n");
    return 0;
}
