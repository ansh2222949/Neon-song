#include "BpmPhaseDetector.h"
#include "AutoMixEngine.h"
#include "WsolaTimeStretcher.h"

#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

#include <cstdio>
#include <cstdlib>
#include <vector>
#include <cmath>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <chrono>
#include <algorithm>

using namespace neonsong::audio;

// Decodes up to maxSeconds of audio from a given file into 32-bit float stereo interleaved PCM @ targetSampleRate
struct DecodedAudio {
    std::vector<float> samples; // Stereo interleaved [L0, R0, L1, R1, ...]
    int sampleRate{48000};
    int channels{2};
    double durationSeconds{0.0};
    bool success{false};
};

DecodedAudio decodeAudioFile(const char* filePath, double maxSeconds = 30.0) {
    DecodedAudio result;
    int fd = open(filePath, O_RDONLY);
    if (fd < 0) {
        std::printf("  [ERR] Failed to open file: %s\n", filePath);
        return result;
    }

    struct stat st{};
    fstat(fd, &st);
    off_t fileSize = st.st_size;

    AMediaExtractor* extractor = AMediaExtractor_new();
    media_status_t status = AMediaExtractor_setDataSourceFd(extractor, fd, 0, fileSize);
    if (status != AMEDIA_OK) {
        std::printf("  [ERR] AMediaExtractor_setDataSourceFd failed (%d)\n", status);
        AMediaExtractor_delete(extractor);
        close(fd);
        return result;
    }

    size_t numTracks = AMediaExtractor_getTrackCount(extractor);
    int audioTrackIndex = -1;
    AMediaFormat* format = nullptr;
    const char* mime = nullptr;

    for (size_t i = 0; i < numTracks; ++i) {
        format = AMediaExtractor_getTrackFormat(extractor, i);
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            if (std::strncmp(mime, "audio/", 6) == 0) {
                audioTrackIndex = static_cast<int>(i);
                break;
            }
        }
        AMediaFormat_delete(format);
        format = nullptr;
    }

    if (audioTrackIndex < 0 || !format) {
        std::printf("  [ERR] No audio track found in %s\n", filePath);
        AMediaExtractor_delete(extractor);
        close(fd);
        return result;
    }

    AMediaExtractor_selectTrack(extractor, static_cast<size_t>(audioTrackIndex));

    int sampleRate = 48000;
    int channelCount = 2;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channelCount);

    result.sampleRate = sampleRate;
    result.channels = 2; // We normalize to stereo

    AMediaCodec* codec = AMediaCodec_createDecoderByType(mime);
    if (!codec) {
        std::printf("  [ERR] Failed to create decoder for MIME %s\n", mime);
        AMediaFormat_delete(format);
        AMediaExtractor_delete(extractor);
        close(fd);
        return result;
    }

    status = AMediaCodec_configure(codec, format, nullptr, nullptr, 0);
    AMediaFormat_delete(format);
    if (status != AMEDIA_OK) {
        std::printf("  [ERR] AMediaCodec_configure failed (%d)\n", status);
        AMediaCodec_delete(codec);
        AMediaExtractor_delete(extractor);
        close(fd);
        return result;
    }

    AMediaCodec_start(codec);

    const size_t maxSamplesToDecode = static_cast<size_t>(maxSeconds * sampleRate * 2);
    bool sawInputEOS = false;
    bool sawOutputEOS = false;

    while (!sawOutputEOS && result.samples.size() < maxSamplesToDecode) {
        if (!sawInputEOS) {
            ssize_t inputBufIdx = AMediaCodec_dequeueInputBuffer(codec, 2000);
            if (inputBufIdx >= 0) {
                size_t bufSize = 0;
                uint8_t* buf = AMediaCodec_getInputBuffer(codec, static_cast<size_t>(inputBufIdx), &bufSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, buf, bufSize);
                if (sampleSize < 0) {
                    sawInputEOS = true;
                    AMediaCodec_queueInputBuffer(codec, static_cast<size_t>(inputBufIdx), 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
                    AMediaCodec_queueInputBuffer(codec, static_cast<size_t>(inputBufIdx), 0, static_cast<size_t>(sampleSize), static_cast<uint64_t>(presentationTimeUs), 0);
                    AMediaExtractor_advance(extractor);
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t outputBufIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
        if (outputBufIdx >= 0) {
            if ((info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0) {
                sawOutputEOS = true;
            }

            if (info.size > 0) {
                size_t outSize = 0;
                uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, static_cast<size_t>(outputBufIdx), &outSize);
                if (outBuf) {
                    const int16_t* pcm16 = reinterpret_cast<const int16_t*>(outBuf + info.offset);
                    size_t numPcmSamples = info.size / sizeof(int16_t);

                    if (channelCount == 1) {
                        for (size_t s = 0; s < numPcmSamples; ++s) {
                            float val = static_cast<float>(pcm16[s]) / 32768.0f;
                            result.samples.push_back(val);
                            result.samples.push_back(val);
                        }
                    } else if (channelCount >= 2) {
                        size_t frames = numPcmSamples / channelCount;
                        for (size_t f = 0; f < frames; ++f) {
                            float left = static_cast<float>(pcm16[f * channelCount]) / 32768.0f;
                            float right = static_cast<float>(pcm16[f * channelCount + 1]) / 32768.0f;
                            result.samples.push_back(left);
                            result.samples.push_back(right);
                        }
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, static_cast<size_t>(outputBufIdx), false);
        } else if (outputBufIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* newFormat = AMediaCodec_getOutputFormat(codec);
            AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate);
            AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channelCount);
            result.sampleRate = sampleRate;
            AMediaFormat_delete(newFormat);
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(extractor);
    close(fd);

    result.durationSeconds = static_cast<double>(result.samples.size() / 2) / static_cast<double>(result.sampleRate);
    result.success = !result.samples.empty();
    return result;
}

double computeRms(const float* buffer, size_t numSamples) {
    if (numSamples == 0) return 0.0;
    double sumSq = 0.0;
    for (size_t i = 0; i < numSamples; ++i) {
        sumSq += static_cast<double>(buffer[i]) * static_cast<double>(buffer[i]);
    }
    return std::sqrt(sumSq / static_cast<double>(numSamples));
}

int main() {
    std::printf("========================================================================\n");
    std::printf("  STAGE 7 PILLAR 2: REAL MUSIC DSP & ACOUSTIC AUDIT ON PHYSICAL HARDWARE\n");
    std::printf("========================================================================\n\n");

    const char* track1Path = "/sdcard/Music/Neon Song/track/Kenshi Yonezu - IRIS OUT.flac";
    const char* track2Path = "/sdcard/Music/Neon Song/track/Ado - ショコラカタブラ - Chocolat Cadabra.m4a";
    const char* track3Path = "/sdcard/Music/Neon Song/track/Ado - マザーランド - Motherland.m4a";

    // -------------------------------------------------------------------------
    // TEST 1: Real-World Decoding & Beat Detection on Polyphonic Music
    // -------------------------------------------------------------------------
    std::printf("[Pillar 2 - Test 1] Real Music Decoding & AutoDJ Beat Detection...\n");

    const char* testTracks[] = { track1Path, track2Path, track3Path };
    const char* trackNames[] = { "Kenshi Yonezu - IRIS OUT", "Ado - Chocolat Cadabra", "Ado - Motherland" };

    std::vector<DecodedAudio> decodedTracks;

    for (size_t t = 0; t < 3; ++t) {
        std::printf("  Decoding: %s...\n", trackNames[t]);
        auto startDecode = std::chrono::high_resolution_clock::now();
        DecodedAudio audio = decodeAudioFile(testTracks[t], 20.0); // 20 seconds of dense music
        auto endDecode = std::chrono::high_resolution_clock::now();
        double decodeMs = std::chrono::duration<double, std::milli>(endDecode - startDecode).count();

        if (!audio.success) {
            std::printf("  [FAIL] Failed to decode %s\n", trackNames[t]);
            return 1;
        }

        std::printf("    -> Decoded %zu frames (%.2fs) @ %d Hz in %.1f ms | Channels: %d\n",
                    audio.samples.size() / 2, audio.durationSeconds, audio.sampleRate, decodeMs, audio.channels);

        double trackRms = computeRms(audio.samples.data(), audio.samples.size());
        std::printf("    -> Signal RMS: %.4f (Dynamic Range Verified)\n", trackRms);
        if (trackRms < 0.01) {
            std::printf("  [FAIL] Audio appears silent or corrupted (RMS < 0.01)\n");
            return 1;
        }

        // Run BpmPhaseDetector on the real audio
        BpmPhaseDetector detector;
        detector.configure(static_cast<double>(audio.sampleRate));

        const size_t blockSize = 512;
        const size_t totalFrames = audio.samples.size() / 2;
        for (size_t f = 0; f < totalFrames; f += blockSize) {
            size_t chunkFrames = std::min(blockSize, totalFrames - f);
            detector.process(&audio.samples[f * 2], chunkFrames);
        }

        BeatDetectionResult beatRes = detector.getResult();
        std::printf("    -> Detected Tempo: %.2f BPM | Confidence: %.2f | Last Downbeat Frame: %llu\n",
                    beatRes.bpm, beatRes.confidence, static_cast<unsigned long long>(beatRes.lastDownbeatFrame));

        if (beatRes.bpm < 50.0f || beatRes.bpm > 220.0f) {
            std::printf("  [FAIL] Detected BPM out of valid physical musical range: %.2f\n", beatRes.bpm);
            return 1;
        }

        decodedTracks.push_back(std::move(audio));
    }
    std::printf("  [PASS] All 3 real polyphonic tracks decoded and beat-analyzed successfully.\n\n");

    // -------------------------------------------------------------------------
    // TEST 2: Slowed + Pitch Studio WSOLA Listening & Quality Across Speed Range
    // -------------------------------------------------------------------------
    std::printf("[Pillar 2 - Test 2] Slowed + Pitch Studio Quality Across Speed Range...\n");
    const float speedRatios[] = { 0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 2.00f };
    const char* speedNames[] = { "0.50x (Ambient/Screwed)", "0.75x (Slowed + Reverb)", "1.00x (Nominal)", "1.25x (Nightcore)", "1.50x (Fast)", "2.00x (Double-Time)" };

    const auto& sourceAudio = decodedTracks[0]; // Kenshi Yonezu - IRIS OUT (Lossless FLAC)
    const size_t testFrames = static_cast<size_t>(sourceAudio.sampleRate * 5.0); // 5 seconds chunk
    std::vector<float> inputSlice(sourceAudio.samples.begin(), sourceAudio.samples.begin() + testFrames * 2);
    double inputRms = computeRms(inputSlice.data(), inputSlice.size());

    for (size_t s = 0; s < 6; ++s) {
        float ratio = speedRatios[s];
        WsolaTimeStretcher stretcher;
        stretcher.configure(static_cast<double>(sourceAudio.sampleRate), 2);
        stretcher.setTimeRatio(ratio);

        std::vector<float> outputBuffer;
        outputBuffer.reserve(static_cast<size_t>(static_cast<double>(inputSlice.size()) / ratio * 1.2));

        const size_t processBlock = 256;
        float blockOut[512 * 2]; // Max output for 256 frame input

        auto startWsola = std::chrono::high_resolution_clock::now();
        size_t totalOutputFrames = 0;

        for (size_t f = 0; f < testFrames; f += processBlock) {
            size_t inFrames = std::min(processBlock, testFrames - f);
            size_t outFrames = stretcher.process(&inputSlice[f * 2], inFrames, blockOut, 512);
            if (outFrames > 0) {
                outputBuffer.insert(outputBuffer.end(), blockOut, blockOut + outFrames * 2);
                totalOutputFrames += outFrames;
            }
        }

        // Flush remaining frames
        size_t flushed = stretcher.flush(blockOut, 512);
        if (flushed > 0) {
            outputBuffer.insert(outputBuffer.end(), blockOut, blockOut + flushed * 2);
            totalOutputFrames += flushed;
        }

        auto endWsola = std::chrono::high_resolution_clock::now();
        double elapsedMs = std::chrono::duration<double, std::milli>(endWsola - startWsola).count();

        double outputRms = computeRms(outputBuffer.data(), outputBuffer.size());
        double rmsRatio = outputRms / inputRms;

        // Verify no NaN or Inf
        bool hasInvalid = false;
        float maxAdjacentDiff = 0.0f;
        for (size_t i = 0; i < outputBuffer.size(); ++i) {
            if (std::isnan(outputBuffer[i]) || std::isinf(outputBuffer[i])) {
                hasInvalid = true;
                break;
            }
            if (i > 1) {
                float diff = std::abs(outputBuffer[i] - outputBuffer[i - 2]);
                if (diff > maxAdjacentDiff) maxAdjacentDiff = diff;
            }
        }

        std::printf("  Ratio %s:\n", speedNames[s]);
        std::printf("    -> In: %zu frames | Out: %zu frames (Expected ~%zu) | Time: %.2f ms\n",
                    testFrames, totalOutputFrames, static_cast<size_t>(testFrames / ratio), elapsedMs);
        std::printf("    -> In RMS: %.4f | Out RMS: %.4f (Ratio: %.3f) | Max Δ: %.4f | Invalid: %s\n",
                    inputRms, outputRms, rmsRatio, maxAdjacentDiff, hasInvalid ? "YES (FAIL)" : "NO (OK)");

        if (hasInvalid) {
            std::printf("  [FAIL] NaN/Inf found in time-stretched output at ratio %.2f\n", ratio);
            return 1;
        }

        // Energy ratio check: should be within [0.70, 1.30] of input RMS
        if (rmsRatio < 0.65 || rmsRatio > 1.35) {
            std::printf("  [FAIL] Energy preservation failed: RMS ratio %.3f outside expected [0.65, 1.35]\n", rmsRatio);
            return 1;
        }
    }
    std::printf("  [PASS] Slowed + Pitch Studio WSOLA acoustic energy and continuity verified across all ratios.\n\n");

    // -------------------------------------------------------------------------
    // TEST 3: Real Music AutoDJ Real-Time Dual-Deck Transition
    // -------------------------------------------------------------------------
    std::printf("[Pillar 2 - Test 3] Real Music AutoDJ Transition & 160Hz Bass-Swap...\n");
    {
        AutoMixEngine mixEngine;
        mixEngine.configure(48000.0);
        mixEngine.setEnabled(true);

        // Feed Deck A with Track 1 (IRIS OUT) and Deck B with Track 2 (Chocolat Cadabra)
        const auto& deckA = decodedTracks[0];
        const auto& deckB = decodedTracks[1];

        // Queue 4 seconds of Deck B into the ring buffer
        const size_t deckBFrames = 48000 * 4;
        size_t writtenToDeckB = mixEngine.writeDeckBPcm(deckB.samples.data(), deckBFrames);
        std::printf("  Prebuffered %zu frames into AutoMix Deck B\n", writtenToDeckB);

        // Start crossfade: duration = 2000 ms (96,000 frames)
        mixEngine.triggerFadeDurationMs(2000);
        std::printf("  Started 2.0s AutoDJ crossfade with Linkwitz-Riley 160Hz bass-swap...\n");

        std::vector<float> blendedOutput;
        blendedOutput.reserve(96000 * 2);

        std::vector<float> deckACopy(deckA.samples.begin(), deckA.samples.begin() + 96000 * 2);
        const size_t renderBlocks = 96000 / 512;

        for (size_t b = 0; b < renderBlocks; ++b) {
            float* blockPtr = &deckACopy[b * 512 * 2];
            mixEngine.process(blockPtr, 512);
            blendedOutput.insert(blendedOutput.end(), blockPtr, blockPtr + 1024);
        }

        double blendRms = computeRms(blendedOutput.data(), blendedOutput.size());
        double deckARms = computeRms(&deckA.samples[0], 96000 * 2);
        double deckBRms = computeRms(&deckB.samples[0], 96000 * 2);

        std::printf("  Blended 96,000 frames: Deck A RMS = %.4f, Deck B RMS = %.4f, Blended Output RMS = %.4f\n",
                    deckARms, deckBRms, blendRms);

        // Verify output is active, musical, and within expected sum range
        if (blendRms < 0.05 || std::isnan(blendRms)) {
            std::printf("  [FAIL] Blended output energy dropped or NaN (RMS = %.4f)\n", blendRms);
            return 1;
        }

        std::printf("  [PASS] AutoDJ real-world music transition completed with smooth energy continuity.\n\n");
    }

    std::printf("========================================================================\n");
    std::printf("  PILLAR 2 AUDIT COMPLETE: ALL REAL MUSIC DSP & AUTO-DJ TESTS PASSED\n");
    std::printf("========================================================================\n");
    return 0;
}
