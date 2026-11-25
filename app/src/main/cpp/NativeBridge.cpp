#include "AudioEngine.h"
#include "DspProcessor.h"
#include "PcmConverter.h"

#include <jni.h>

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <limits>
#include <new>

namespace {

using neonsong::audio::AudioEngine;
using neonsong::audio::PcmFormat;

AudioEngine* fromHandle(jlong handle) noexcept {
    return reinterpret_cast<AudioEngine*>(static_cast<std::uintptr_t>(handle));
}

bool parseFormat(jint value, PcmFormat& format) noexcept {
    switch (value) {
        case 0: format = PcmFormat::Int16; return true;
        case 1: format = PcmFormat::PackedInt24; return true;
        case 2: format = PcmFormat::Int32; return true;
        case 3: format = PcmFormat::Float32; return true;
        default: return false;
    }
}

struct DirectSlice {
    std::uint8_t* data{nullptr};
    std::size_t remainingBytes{0};
};

[[nodiscard]] bool resolveDirectSlice(
    JNIEnv* env,
    jobject buffer,
    jint byteOffset,
    std::size_t requiredAlignment,
    DirectSlice& out) noexcept {
    if (buffer == nullptr || byteOffset < 0) return false;
    auto* base = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (base == nullptr || capacity < 0) return false;
    const auto offset = static_cast<std::size_t>(byteOffset);
    const auto cap = static_cast<std::size_t>(capacity);
    if (offset > cap) return false;
    std::uint8_t* ptr = base + offset;
    if (requiredAlignment > 1U &&
        (reinterpret_cast<std::uintptr_t>(ptr) % requiredAlignment) != 0U) {
        return false;
    }
    out = {ptr, cap - offset};
    return true;
}

[[nodiscard]] bool rangesOverlap(
    const void* a,
    std::size_t aLen,
    const void* b,
    std::size_t bLen) noexcept {
    const auto aStart = reinterpret_cast<std::uintptr_t>(a);
    const auto bStart = reinterpret_cast<std::uintptr_t>(b);
    return (aStart < bStart + bLen) && (bStart < aStart + aLen);
}

template <typename Fn>
void runJniSafe(Fn&& fn) noexcept {
    try {
        fn();
    } catch (...) {
    }
}

template <typename R, typename Fn>
[[nodiscard]] R runJniSafe(R fallback, Fn&& fn) noexcept {
    try {
        return fn();
    } catch (...) {
        return fallback;
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeCreate(
    JNIEnv*,
    jobject) {
    const auto pointer = reinterpret_cast<std::uintptr_t>(new (std::nothrow) AudioEngine());
    return static_cast<jlong>(pointer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeStart(
    JNIEnv*,
    jobject,
    jlong handle,
    jint preferredOutputSampleRate) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return JNI_FALSE;
    try {
        return engine->start(preferredOutputSampleRate) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception&) {
        return JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeStop(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (auto* engine = fromHandle(handle); engine != nullptr) engine->stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsRunning(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return (engine != nullptr && engine->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushOutput(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (auto* engine = fromHandle(handle); engine != nullptr) engine->flushOutput();
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetPlaying(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean playing) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setPlaying(playing == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetOutputVolume(
    JNIEnv*,
    jobject,
    jlong handle,
    jfloat volume) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setOutputVolume(volume);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetStudioMasterClarity(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setStudioMasterClarity(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetBauerCrossfeedEnabled(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setBauerCrossfeedEnabled(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetAnalogTubeWarmthEnabled(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setAnalogTubeWarmthEnabled(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetMonoBassEnabled(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setMonoBassEnabled(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetSpatialImmersionEnabled(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setSpatialImmersionEnabled(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetSpatialImmersionStrength(
    JNIEnv*,
    jobject,
    jlong handle,
    jfloat strength) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setSpatialImmersionStrength(strength);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsSpatialImmersionEnabled(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->isSpatialImmersionEnabled() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetHrtfHeadScale(
    JNIEnv*,
    jobject,
    jlong,
    jfloat headScale) {
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastHrtfHeadScale(static_cast<float>(headScale));
    });
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetHrtfHeadScale(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->getHrtfHeadScale();
    }
    return neonsong::audio::DspProcessor::kHrtfHeadScaleDefault;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_audio_AudioEngineBridge_nativeSetHrtfHeadScale(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jfloat headScale) {
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastHrtfHeadScale(static_cast<float>(headScale));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_audio_AudioEngineBridge_nativeSetVocalInstrumentSeparation(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jfloat separation) {
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastVocalInstrumentSeparation(
            static_cast<float>(separation));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_audio_AudioEngineBridge_nativeSetSpatialImmersionEnabled(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jboolean enabled) {
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastSpatialImmersionEnabled(enabled == JNI_TRUE);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_audio_AudioEngineBridge_nativeSetSpatialImmersionStrength(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jfloat strength) {
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastSpatialImmersionStrength(static_cast<float>(strength));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityWet(
    JNIEnv*,
    jobject,
    jlong handle,
    jfloat wet) {
    if (fromHandle(handle) == nullptr) return;
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastClarityWet(wet);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityTrims(
    JNIEnv* env,
    jobject,
    jlong handle,
    jfloatArray trimsDb) {
    if (fromHandle(handle) == nullptr || trimsDb == nullptr ||
        env->GetArrayLength(trimsDb) !=
            static_cast<jsize>(neonsong::audio::DspProcessor::kClarityTrimCount)) {
        return;
    }
    std::array<float, neonsong::audio::DspProcessor::kClarityTrimCount> trims{};
    env->GetFloatArrayRegion(trimsDb, 0, static_cast<jsize>(trims.size()), trims.data());
    if (env->ExceptionCheck()) return;
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastClarityTrims(trims.data(), trims.size());
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityPreset(
    JNIEnv*,
    jobject,
    jlong handle,
    jint preset) {
    if (fromHandle(handle) == nullptr) return;
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastClarityPreset(preset);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityAtmosBypass(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (fromHandle(handle) == nullptr) return;
    runJniSafe([&]() {
        neonsong::audio::DspProcessor::broadcastClarityAtmosBypass(enabled == JNI_TRUE);
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetBitPerfect(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setBitPerfect(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetBtAacOptimization(
    JNIEnv*,
    jobject,
    jlong handle,
    jboolean enabled) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setBtAacOptimizationEnabled(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsBitPerfect(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return (engine != nullptr && engine->isBitPerfect()) ? JNI_TRUE : JNI_FALSE;
}



extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetEqualizer(
    JNIEnv* env,
    jobject,
    jlong handle,
    jboolean enabled,
    jfloatArray gainsDb) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || gainsDb == nullptr ||
        env->GetArrayLength(gainsDb) !=
            static_cast<jsize>(neonsong::audio::DspProcessor::kEqualizerBandCount)) {
        return;
    }
    std::array<float, neonsong::audio::DspProcessor::kEqualizerBandCount> gains{};
    env->GetFloatArrayRegion(gainsDb, 0, static_cast<jsize>(gains.size()), gains.data());
    if (env->ExceptionCheck()) return;
    engine->setEqualizer(enabled == JNI_TRUE, gains.data(), gains.size());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeConfigureMediaProcessor(
    JNIEnv*,
    jobject,
    jlong handle,
    jint inputSampleRate,
    jint outputSampleRate,
    jint channelCount) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        try {
            return engine->configureMediaProcessor(
                inputSampleRate, outputSampleRate, channelCount) ? JNI_TRUE : JNI_FALSE;
        } catch (const std::exception&) {
            return JNI_FALSE;
        } catch (...) {
            return JNI_FALSE;
        }
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeProcessMediaPcm(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject inputBuffer,
    jint inputByteOffset,
    jobject outputBuffer,
    jint outputByteOffset,
    jint frameCount,
    jint encoding,
    jint channelCount) {
    auto* engine = fromHandle(handle);
    PcmFormat format{};
    DirectSlice inSlice{};
    DirectSlice outSlice{};
    if (engine == nullptr || frameCount <= 0 ||
        channelCount < 1 ||
        channelCount > static_cast<jint>(neonsong::audio::DspProcessor::kMaxChannels) ||
        channelCount != engine->mediaChannelCount() ||
        !parseFormat(encoding, format) ||
        !resolveDirectSlice(env, inputBuffer, inputByteOffset, 1U, inSlice) ||
        !resolveDirectSlice(env, outputBuffer, outputByteOffset, alignof(float), outSlice)) {
        return -1;
    }

    const std::size_t frames = static_cast<std::size_t>(frameCount);
    const std::size_t channels = static_cast<std::size_t>(channelCount);
    const std::size_t inputBytesPerFrame =
        neonsong::audio::PcmConverter::bytesPerSample(format) * channels;
    const std::size_t outputBytesPerFrame = sizeof(float) * channels;
    if (inputBytesPerFrame == 0U ||
        frames > std::numeric_limits<std::size_t>::max() / inputBytesPerFrame ||
        frames > std::numeric_limits<std::size_t>::max() / outputBytesPerFrame) {
        return -1;
    }
    const std::size_t requiredInput = frames * inputBytesPerFrame;
    if (requiredInput > inSlice.remainingBytes || outSlice.remainingBytes < outputBytesPerFrame) {
        return -1;
    }
    if (rangesOverlap(inSlice.data, requiredInput, outSlice.data, outSlice.remainingBytes)) {
        return -1;
    }

    const std::size_t outputFrameCapacity = outSlice.remainingBytes / outputBytesPerFrame;
    return runJniSafe<jint>(-1, [&]() -> jint {
        std::size_t outputFrames = 0;
        if (!engine->processMediaPcm(
                inSlice.data,
                frames,
                format,
                reinterpret_cast<float*>(outSlice.data),
                outputFrameCapacity,
                outputFrames)) {
            return -1;
        }
        return outputFrames <= static_cast<std::size_t>(std::numeric_limits<jint>::max())
            ? static_cast<jint>(outputFrames)
            : -1;
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushMediaProcessor(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject outputBuffer,
    jint outputByteOffset,
    jint channelCount) {
    auto* engine = fromHandle(handle);
    DirectSlice outSlice{};
    if (engine == nullptr ||
        channelCount < 1 ||
        channelCount > static_cast<jint>(neonsong::audio::DspProcessor::kMaxChannels) ||
        channelCount != engine->mediaChannelCount() ||
        !resolveDirectSlice(env, outputBuffer, outputByteOffset, alignof(float), outSlice)) {
        return -1;
    }
    const std::size_t bytesPerFrame = sizeof(float) * static_cast<std::size_t>(channelCount);
    if (outSlice.remainingBytes < bytesPerFrame) return -1;

    const std::size_t frameCapacity = outSlice.remainingBytes / bytesPerFrame;
    return runJniSafe<jint>(-1, [&]() -> jint {
        std::size_t outputFrames = 0;
        if (!engine->flushMediaProcessor(
                reinterpret_cast<float*>(outSlice.data),
                frameCapacity,
                outputFrames)) {
            return -1;
        }
        return outputFrames <= static_cast<std::size_t>(std::numeric_limits<jint>::max())
            ? static_cast<jint>(outputFrames)
            : -1;
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeResetMediaProcessor(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        runJniSafe([&]() {
            engine->resetMediaProcessor();
        });
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeWriteDeckPcm(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint deckId,
    jobject directBuffer,
    jint byteOffset,
    jint frameCount,
    jint encoding,
    jint inputSampleRate,
    jint inputChannelCount) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return -3;
    if (deckId < 0 || deckId >= static_cast<jint>(AudioEngine::kDeckCount)) return -4;

    DirectSlice slice{};
    if (!resolveDirectSlice(env, directBuffer, byteOffset, 1U, slice)) return -1;

    PcmFormat format{};
    if (frameCount <= 0 || inputSampleRate <= 0 ||
        (inputChannelCount != 1 && inputChannelCount != 2) ||
        !parseFormat(encoding, format)) {
        return -2;
    }

    const std::size_t bytesPerSample = neonsong::audio::PcmConverter::bytesPerSample(format);
    const std::size_t bytesPerFrame = bytesPerSample * static_cast<std::size_t>(inputChannelCount);
    const std::size_t frames = static_cast<std::size_t>(frameCount);
    if (bytesPerFrame == 0U || frames > std::numeric_limits<std::size_t>::max() / bytesPerFrame) {
        return -2;
    }
    if ((static_cast<std::size_t>(byteOffset) % bytesPerSample) != 0U ||
        frames * bytesPerFrame > slice.remainingBytes) {
        return -1;
    }

    return runJniSafe<jint>(-3, [&]() -> jint {
        const std::int64_t consumed = engine->writeDeckPcm(
            deckId, slice.data, frames, format, inputSampleRate, inputChannelCount);
        if (consumed < 0) return static_cast<jint>(consumed);
        return static_cast<jint>(std::min<std::int64_t>(
            consumed,
            static_cast<std::int64_t>(std::numeric_limits<jint>::max())));
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeWritePcm(
    JNIEnv* env,
    jobject obj,
    jlong handle,
    jobject directBuffer,
    jint byteOffset,
    jint frameCount,
    jint encoding,
    jint inputSampleRate,
    jint inputChannelCount) {
    const jint result = Java_com_neonsong_app_playback_NativeAudioEngine_nativeWriteDeckPcm(
        env, obj, handle, AudioEngine::kDeckA,
        directBuffer, byteOffset, frameCount, encoding, inputSampleRate, inputChannelCount);
    return result > 0 ? result : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushDeck(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->flushDeck(deckId);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckBufferedFrames(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return static_cast<jlong>(engine->deckBufferedFrames(deckId));
    }
    return 0L;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckVolume(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId,
    jfloat volume) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setDeckVolume(deckId, volume);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetCrossfade(
    JNIEnv*,
    jobject,
    jlong handle,
    jfloat progress) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setCrossfadeProgress(progress);
    }
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetCrossfade(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->crossfadeProgress();
    }
    return 0.0F;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckPlaybackParameters(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId,
    jfloat speed,
    jfloat pitch) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setDeckPlaybackParameters(deckId, speed, pitch);
    }
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckSpeed(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->deckSpeed(deckId);
    }
    return 1.0F;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckPitch(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->deckPitch(deckId);
    }
    return 1.0F;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckTimeRatio(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId,
    jfloat ratio) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setDeckTimeRatio(deckId, ratio);
    }
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckTimeRatio(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->deckTimeRatio(deckId);
    }
    return 1.0F;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckPlaying(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId,
    jboolean playing) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->setDeckPlaying(deckId, playing == JNI_TRUE);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsDeckPlaying(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->isDeckPlaying(deckId) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeDrainDeckResampler(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        engine->drainDeckResampler(deckId);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsDeckResamplerDrained(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->isDeckResamplerDrained(deckId) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckFramesRendered(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return static_cast<jlong>(engine->deckFramesRendered(deckId));
    }
    return 0L;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsDeckWsolaDrained(
    JNIEnv*,
    jobject,
    jlong handle,
    jint deckId) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return engine->isDeckWsolaDrained(deckId) ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetPresentationLatencyUs(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (const auto* engine = fromHandle(handle); engine != nullptr) {
        return static_cast<jlong>(engine->presentationLatencyUs());
    }
    return 0L;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeWriteProcessedPcm(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject directBuffer,
    jint byteOffset,
    jint frameCount,
    jint inputSampleRate,
    jint inputChannelCount) {
    auto* engine = fromHandle(handle);
    DirectSlice slice{};
    if (engine == nullptr || frameCount <= 0 || inputSampleRate <= 0 ||
        (inputChannelCount != 1 && inputChannelCount != 2) ||
        !resolveDirectSlice(env, directBuffer, byteOffset, alignof(float), slice)) {
        return 0;
    }

    const std::size_t frames = static_cast<std::size_t>(frameCount);
    const std::size_t channels = static_cast<std::size_t>(inputChannelCount);
    if (frames > std::numeric_limits<std::size_t>::max() / channels / sizeof(float) ||
        frames * channels * sizeof(float) > slice.remainingBytes) {
        return 0;
    }

    return runJniSafe<jint>(0, [&]() -> jint {
        const std::size_t accepted = engine->writeProcessedPcm(
            reinterpret_cast<const float*>(slice.data),
            frames,
            inputSampleRate,
            inputChannelCount);
        return static_cast<jint>(std::min<std::size_t>(
            accepted,
            static_cast<std::size_t>(std::numeric_limits<jint>::max())));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushResampler(
    JNIEnv*,
    jobject,
    jlong handle) {
    if (auto* engine = fromHandle(handle); engine != nullptr) {
        runJniSafe([&]() {
            engine->flushResampler();
        });
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeOutputSampleRate(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : engine->outputSampleRate();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeBufferedFrames(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->bufferedFrames());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeUnderrunCount(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->underrunCount());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeRenderedFrames(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->renderedFrames());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeRequestedSampleRate(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : engine->requestedSampleRate();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeStreamOpenCount(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->streamOpenCount());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeStreamRestartCount(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->streamRestartCount());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeRateAdaptationCount(
    JNIEnv*,
    jobject,
    jlong handle) {
    const auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jlong>(engine->rateAdaptationCount());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neonsong_app_playback_NativeAudioEngine_nativeConsumeHapticTelemetry(
    JNIEnv*,
    jobject,
    jlong handle) {
    auto* engine = fromHandle(handle);
    return engine == nullptr ? 0 : static_cast<jint>(engine->consumeHapticTelemetry());
}
