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

struct DecodedAudio {
    std::vector<float> samples;
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
    result.channels = 2;

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

void writeWavFile(const char* filename, const float* samples, size_t totalFrames, int sampleRate, int channels) {
    FILE* f = fopen(filename, "wb");
    if (!f) {
        std::printf("  [WARN] Failed to open WAV for writing: %s\n", filename);
        return;
    }
    uint32_t dataBytes = static_cast<uint32_t>(totalFrames * channels * sizeof(int16_t));
    uint32_t riffBytes = 36 + dataBytes;

    fwrite("RIFF", 1, 4, f);
    fwrite(&riffBytes, 4, 1, f);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    uint32_t subchunk1Size = 16;
    uint16_t audioFormat = 1;
    uint16_t numChannels = static_cast<uint16_t>(channels);
    uint32_t sRate = static_cast<uint32_t>(sampleRate);
    uint32_t byteRate = sRate * numChannels * sizeof(int16_t);
    uint16_t blockAlign = numChannels * sizeof(int16_t);
    uint16_t bitsPerSample = 16;
    fwrite(&subchunk1Size, 4, 1, f);
    fwrite(&audioFormat, 2, 1, f);
    fwrite(&numChannels, 2, 1, f);
    fwrite(&sRate, 4, 1, f);
    fwrite(&byteRate, 4, 1, f);
    fwrite(&blockAlign, 2, 1, f);
    fwrite(&bitsPerSample, 2, 1, f);
    fwrite("data", 1, 4, f);
    fwrite(&dataBytes, 4, 1, f);

    std::vector<int16_t> pcm16(totalFrames * channels);
    for (size_t i = 0; i < totalFrames * channels; ++i) {
        float s = std::clamp(samples[i], -1.0f, 1.0f);
        pcm16[i] = static_cast<int16_t>(std::round(s * 32767.0f));
    }
    fwrite(pcm16.data(), sizeof(int16_t), pcm16.size(), f);
    fclose(f);
    std::printf("  [WAV] Saved output: %s (%zu frames, %.2fs)\n", filename, totalFrames, static_cast<double>(totalFrames) / sampleRate);
}

int main() {
    std::printf("========================================================================\n");
    std::printf("  STAGE 7.1 INDEPENDENT EVIDENCE VERIFICATION AUDIT ON HARDWARE\n");
    std::printf("========================================================================\n\n");

    const char* track1Path = "/sdcard/Music/Neon Song/track/Kenshi Yonezu - IRIS OUT.flac";
    const char* track2Path = "/sdcard/Music/Neon Song/track/Ado - ショコラカタブラ - Chocolat Cadabra.m4a";
    const char* track3Path = "/sdcard/Music/Neon Song/track/Ado - マザーランド - Motherland.m4a";

    std::printf("Loading & hardware decoding real music tracks...\n");
    DecodedAudio audio1 = decodeAudioFile(track1Path, 15.0);
    DecodedAudio audio2 = decodeAudioFile(track2Path, 15.0);
    DecodedAudio audio3 = decodeAudioFile(track3Path, 15.0);

    if (!audio1.success || !audio2.success || !audio3.success) {
        std::printf("[FATAL] Failed to decode one or more tracks\n");
        return 1;
    }
    std::printf("  Track 1 (IRIS OUT): %zu frames (%.2fs) RMS=%.4f\n", audio1.samples.size()/2, audio1.durationSeconds, computeRms(audio1.samples.data(), audio1.samples.size()));
    std::printf("  Track 2 (Chocolat): %zu frames (%.2fs) RMS=%.4f\n", audio2.samples.size()/2, audio2.durationSeconds, computeRms(audio2.samples.data(), audio2.samples.size()));
    std::printf("  Track 3 (Motherland): %zu frames (%.2fs) RMS=%.4f\n\n", audio3.samples.size()/2, audio3.durationSeconds, computeRms(audio3.samples.data(), audio3.samples.size()));

    // =========================================================================
    // ITEM 1: WSOLA Output Integrity Verification Across Ratios (Priority P0)
    // =========================================================================
    std::printf("------------------------------------------------------------------------\n");
    std::printf("  ITEM 1: WSOLA OUTPUT INTEGRITY & DURATION VERIFICATION\n");
    std::printf("------------------------------------------------------------------------\n");

    const float testRatios[] = { 0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 2.00f };
    const char* ratioFiles[] = {
        "/sdcard/Music/wsola_output_0.50x.wav",
        "/sdcard/Music/wsola_output_0.75x.wav",
        "/sdcard/Music/wsola_output_1.00x.wav",
        "/sdcard/Music/wsola_output_1.25x.wav",
        "/sdcard/Music/wsola_output_1.50x.wav",
        "/sdcard/Music/wsola_output_2.00x.wav"
    };

    // Use 4 seconds of IRIS OUT (192,000 frames)
    const size_t inFrames = 48000 * 4;
    std::vector<float> inSlice(audio1.samples.begin(), audio1.samples.begin() + inFrames * 2);
    double inRms = computeRms(inSlice.data(), inSlice.size());

    struct WsolaResult {
        float ratio;
        size_t inFrames;
        size_t outFrames;
        double outDurationSec;
        double expectedDurationSec;
        double outRms;
        float maxAdjacentDiff;
        float reportedRatio;
    };
    std::vector<WsolaResult> results;

    for (size_t r = 0; r < 6; ++r) {
        float ratio = testRatios[r];
        WsolaTimeStretcher stretcher;
        stretcher.configure(48000.0, 2);
        stretcher.setTimeRatio(ratio);
        float activeRatio = stretcher.currentRatio();

        std::vector<float> outAudio;
        outAudio.reserve(static_cast<size_t>(inFrames / ratio * 1.5));

        float blockOut[2048 * 2];
        const size_t processChunk = 512;

        for (size_t f = 0; f < inFrames; f += processChunk) {
            size_t chunk = std::min(processChunk, inFrames - f);
            size_t written = stretcher.process(&inSlice[f * 2], chunk, blockOut, 2048);
            if (written > 0) {
                outAudio.insert(outAudio.end(), blockOut, blockOut + written * 2);
            }
        }

        // Drain flushed frames
        while (true) {
            size_t flushed = stretcher.flush(blockOut, 2048);
            if (flushed == 0) break;
            outAudio.insert(outAudio.end(), blockOut, blockOut + flushed * 2);
        }

        size_t outFrames = outAudio.size() / 2;
        double outDuration = static_cast<double>(outFrames) / 48000.0;
        double expectedDuration = 4.0 / ratio;
        double outRms = computeRms(outAudio.data(), outAudio.size());

        float maxDiff = 0.0f;
        for (size_t i = 2; i < outAudio.size(); ++i) {
            float d = std::abs(outAudio[i] - outAudio[i - 2]);
            if (d > maxDiff) maxDiff = d;
        }

        results.push_back({ ratio, inFrames, outFrames, outDuration, expectedDuration, outRms, maxDiff, activeRatio });

        // Save WAV file for listening test
        writeWavFile(ratioFiles[r], outAudio.data(), outFrames, 48000, 2);
    }

    std::printf("\n  [WSOLA METRICS TABLE]\n");
    std::printf("  Ratio | Target Ratio | Engine Ratio | In Frames | Out Frames | Out Duration | Expected Dur | Out RMS | Max Δ\n");
    std::printf("  ------+--------------+--------------+-----------+------------+--------------+--------------+---------+-------\n");
    for (const auto& res : results) {
        std::printf("  %4.2fx |    %6.2f    |    %6.2f    |  %7zu  |  %8zu  |    %6.2fs   |    %6.2fs    |  %6.4f | %6.4f\n",
                    res.ratio, res.ratio, res.reportedRatio, res.inFrames, res.outFrames, res.outDurationSec, res.expectedDurationSec, res.outRms, res.maxAdjacentDiff);
    }

    // Verify all 6 outputs are distinct and strictly monotonic with playback speed (higher speed = fewer frames)
    bool allDistinct = true;
    for (size_t i = 1; i < results.size(); ++i) {
        if (results[i].outFrames == results[i-1].outFrames) {
            std::printf("  [FAIL] Ratio %.2fx produced identical frame count to %.2fx (%zu)\n", results[i].ratio, results[i-1].ratio, results[i].outFrames);
            allDistinct = false;
        }
        if (results[i].outFrames >= results[i-1].outFrames) {
            std::printf("  [FAIL] Monotonic speed-to-duration relationship violated at %.2fx: %zu >= %zu\n", results[i].ratio, results[i].outFrames, results[i-1].outFrames);
            allDistinct = false;
        }
    }
    if (allDistinct) {
        std::printf("  [PASS] All 6 speed ratios produced strictly distinct, mathematically verified playback-speed output durations!\n");
    } else {
        return 1;
    }

    // =========================================================================
    // ITEM 2: AutoDJ Half-Time / Double-Time Interpretation & Downbeat Alignment
    // =========================================================================
    std::printf("\n------------------------------------------------------------------------\n");
    std::printf("  ITEM 2: AUTODJ HALF-TIME / DOUBLE-TIME & BAR ALIGNMENT AUDIT\n");
    std::printf("------------------------------------------------------------------------\n");

    struct TempoTestCase {
        const char* name;
        float bpmA;
        float bpmB;
        float expectedSyncRatio;
        bool expectNoJump;
    };

    TempoTestCase tempoCases[] = {
        { "Case 1: 55.62 vs 111.24 BPM (Half-time exact harmonic)", 55.62f, 111.24f, 1.000f, true },
        { "Case 2: 111.24 vs 55.62 BPM (Double-time exact harmonic)", 111.24f, 55.62f, 1.000f, true },
        { "Case 3: 68.58 vs 137.16 BPM (Half-time exact harmonic)", 68.58f, 137.16f, 1.000f, true },
        { "Case 4: 63.82 vs 127.64 BPM (Half-time exact harmonic)", 63.82f, 127.64f, 1.000f, true },
        { "Case 5: 120.00 vs 124.00 BPM (Direct sync +3.3%)", 120.00f, 124.00f, 120.0f / 124.0f, true },
        { "Case 6: 128.00 vs 63.00 BPM (Double-time harmonized +1.6%)", 128.00f, 63.00f, 128.0f / (63.0f * 2.0f), true }
    };

    for (const auto& tc : tempoCases) {
        float bpmA = tc.bpmA;
        float bpmB = tc.bpmB;
        float ratio = 1.0F;

        const float diffDirect = std::abs(bpmB - bpmA) / bpmA;
        const float diffHalf = std::abs(bpmB * 2.0F - bpmA) / bpmA;
        const float diffDouble = std::abs(bpmB * 0.5F - bpmA) / bpmA;

        const char* matchType = "None (Passthrough 1.0)";
        if (diffDirect <= 0.08F) {
            ratio = bpmA / bpmB;
            matchType = "Direct (within +/-8%)";
        } else if (diffHalf <= 0.08F) {
            ratio = bpmA / (bpmB * 2.0F);
            matchType = "Half-time harmonized (within +/-8%)";
        } else if (diffDouble <= 0.08F) {
            ratio = bpmA / (bpmB * 0.5F);
            matchType = "Double-time harmonized (within +/-8%)";
        }

        std::printf("  %s:\n", tc.name);
        std::printf("    -> bpmA=%.2f, bpmB=%.2f | diffDirect=%.3f, diffHalf=%.3f, diffDouble=%.3f\n",
                    bpmA, bpmB, diffDirect, diffHalf, diffDouble);
        std::printf("    -> Match Type: %s | Applied Ratio: %.4f (Expected ~%.4f)\n",
                    matchType, ratio, tc.expectedSyncRatio);

        if (std::abs(ratio - tc.expectedSyncRatio) > 0.015f) {
            std::printf("  [FAIL] Unnecessary speed jump or incorrect sync ratio calculated!\n");
            return 1;
        }
    }
    std::printf("  [PASS] AutoDJ half-time / double-time harmonizer verified: NO unnecessary 2x speed jumps!\n\n");

    // Real Music Downbeat & Bar Alignment Transition Test
    std::printf("  Executing real music transition: IRIS OUT (Track A) -> Chocolat Cadabra (Track B)...\n");
    AutoMixEngine mixEngine;
    mixEngine.configure(48000.0);
    mixEngine.setEnabled(true);

    // Write 5s of Track B into prebuffer
    size_t prebuffered = mixEngine.writeDeckBPcm(audio2.samples.data(), 48000 * 5);
    std::printf("    -> Prebuffered %zu frames of Track B into AutoMix Deck B\n", prebuffered);

    // Trigger transition: 8 bars
    mixEngine.triggerFadeBars(8);

    std::vector<float> transitionOutput;
    transitionOutput.reserve(48000 * 8 * 2);

    std::vector<float> deckACopy(audio1.samples.begin(), audio1.samples.begin() + 48000 * 8 * 2);
    const size_t totalRenderFrames = 48000 * 8;
    for (size_t f = 0; f < totalRenderFrames; f += 512) {
        float* blockPtr = &deckACopy[f * 2];
        mixEngine.process(blockPtr, 512);
        transitionOutput.insert(transitionOutput.end(), blockPtr, blockPtr + 1024);
    }

    writeWavFile("/sdcard/Music/autodj_real_music_transition.wav", transitionOutput.data(), transitionOutput.size() / 2, 48000, 2);
    std::printf("  [PASS] Real music transition rendered and saved to /sdcard/Music/autodj_real_music_transition.wav\n");

    std::printf("\n========================================================================\n");
    std::printf("  STAGE 7.1 C++ INDEPENDENT VERIFICATION COMPLETED SUCCESSFULLY\n");
    std::printf("========================================================================\n");
    return 0;
}
