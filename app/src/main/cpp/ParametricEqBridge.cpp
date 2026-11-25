#include "ParametricEq.h"

#include <jni.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <new>

namespace {

using neonsong::audio::ParametricEq;

static_assert(sizeof(jint) == sizeof(std::int32_t), "jint must match int32_t");
static_assert(sizeof(jfloat) == sizeof(float), "jfloat must match float");
static_assert(sizeof(jboolean) == sizeof(std::uint8_t), "jboolean must match uint8_t");

ParametricEq* fromHandle(jlong handle) noexcept {
    return reinterpret_cast<ParametricEq*>(static_cast<std::uintptr_t>(handle));
}

void doPublish(
    JNIEnv* env,
    jboolean enabled,
    jfloat preampDb,
    jintArray types,
    jfloatArray frequencies,
    jfloatArray gains,
    jfloatArray qs,
    jbooleanArray filterEnabled) {
    if (types == nullptr || frequencies == nullptr || gains == nullptr || qs == nullptr ||
        filterEnabled == nullptr) {
        ParametricEq::publish(false, 0.0F, nullptr, nullptr, nullptr, nullptr, nullptr, 0);
        return;
    }
    const jsize rawCount = env->GetArrayLength(types);
    if (rawCount < 0 || env->GetArrayLength(frequencies) != rawCount ||
        env->GetArrayLength(gains) != rawCount || env->GetArrayLength(qs) != rawCount ||
        env->GetArrayLength(filterEnabled) != rawCount) {
        return;
    }
    const jsize count = std::min(rawCount, static_cast<jsize>(ParametricEq::kMaxFilters));
    std::array<jint, ParametricEq::kMaxFilters> typeValues{};
    std::array<jfloat, ParametricEq::kMaxFilters> frequencyValues{};
    std::array<jfloat, ParametricEq::kMaxFilters> gainValues{};
    std::array<jfloat, ParametricEq::kMaxFilters> qValues{};
    std::array<jboolean, ParametricEq::kMaxFilters> onValues{};
    if (count > 0) {
        env->GetIntArrayRegion(types, 0, count, typeValues.data());
        if (env->ExceptionCheck()) return;
        env->GetFloatArrayRegion(frequencies, 0, count, frequencyValues.data());
        if (env->ExceptionCheck()) return;
        env->GetFloatArrayRegion(gains, 0, count, gainValues.data());
        if (env->ExceptionCheck()) return;
        env->GetFloatArrayRegion(qs, 0, count, qValues.data());
        if (env->ExceptionCheck()) return;
        env->GetBooleanArrayRegion(filterEnabled, 0, count, onValues.data());
        if (env->ExceptionCheck()) return;
    }
    ParametricEq::publish(
        enabled == JNI_TRUE,
        preampDb,
        reinterpret_cast<const std::int32_t*>(typeValues.data()),
        frequencyValues.data(),
        gainValues.data(),
        qValues.data(),
        reinterpret_cast<const std::uint8_t*>(onValues.data()),
        static_cast<std::size_t>(count));
}

jboolean doIsEngaged() {
    return ParametricEq::isEngaged() ? JNI_TRUE : JNI_FALSE;
}

jlong doCreate() {
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(new (std::nothrow) ParametricEq()));
}

void doDestroy(jlong handle) {
    delete fromHandle(handle);
}

void doConfigure(jlong handle, jint sampleRate, jint channels) {
    if (auto* eq = fromHandle(handle)) eq->configure(static_cast<double>(sampleRate), channels);
}

void doReset(jlong handle) {
    if (auto* eq = fromHandle(handle)) eq->reset();
}

jboolean doProcessDirect(
    JNIEnv* env,
    jlong handle,
    jobject buffer,
    jint offset,
    jint length,
    jint encoding) {
    auto* eq = fromHandle(handle);
    if (eq == nullptr || !eq->syncAndIsActive() || buffer == nullptr || offset < 0 || length <= 0) {
        return JNI_FALSE;
    }
    auto* base = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (base == nullptr || capacity < 0 ||
        static_cast<jlong>(offset) + static_cast<jlong>(length) > capacity) {
        return JNI_FALSE;
    }
    return eq->processPcm(base + offset, static_cast<std::size_t>(length), encoding)
        ? JNI_TRUE
        : JNI_FALSE;
}

jboolean doProcessArray(
    JNIEnv* env,
    jlong handle,
    jbyteArray array,
    jint offset,
    jint length,
    jint encoding) {
    auto* eq = fromHandle(handle);
    if (eq == nullptr || !eq->syncAndIsActive() || array == nullptr || offset < 0 || length <= 0) {
        return JNI_FALSE;
    }
    const jsize size = env->GetArrayLength(array);
    if (static_cast<jlong>(offset) + static_cast<jlong>(length) > static_cast<jlong>(size)) {
        return JNI_FALSE;
    }

    // Copy only the active [offset, offset + length) slice for typical audio buffers
    // instead of pinning/copying the entire backing Java byte[].
    constexpr jint kStackSliceLimit = 16384;
    if (length <= kStackSliceLimit) {
        alignas(16) std::array<std::uint8_t, static_cast<std::size_t>(kStackSliceLimit)> slice{};
        env->GetByteArrayRegion(array, offset, length, reinterpret_cast<jbyte*>(slice.data()));
        if (env->ExceptionCheck()) return JNI_FALSE;
        const bool applied = eq->processPcm(slice.data(), static_cast<std::size_t>(length), encoding);
        if (applied) {
            env->SetByteArrayRegion(array, offset, length, reinterpret_cast<const jbyte*>(slice.data()));
            if (env->ExceptionCheck()) return JNI_FALSE;
        }
        return applied ? JNI_TRUE : JNI_FALSE;
    }

    jbyte* elements = env->GetByteArrayElements(array, nullptr);
    if (elements == nullptr) return JNI_FALSE;
    const bool applied = eq->processPcm(
        reinterpret_cast<std::uint8_t*>(elements) + offset,
        static_cast<std::size_t>(length),
        encoding);
    env->ReleaseByteArrayElements(array, elements, applied ? 0 : JNI_ABORT);
    return applied ? JNI_TRUE : JNI_FALSE;
}

}  // namespace

// Exports for com.neonsong.app.playback.eq.ParametricEqNative
extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativePublish(
    JNIEnv* env, jobject, jboolean enabled, jfloat preampDb, jintArray types,
    jfloatArray frequencies, jfloatArray gains, jfloatArray qs, jbooleanArray filterEnabled) {
    doPublish(env, enabled, preampDb, types, frequencies, gains, qs, filterEnabled);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeIsEngaged(JNIEnv*, jobject) {
    return doIsEngaged();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeCreate(JNIEnv*, jobject) {
    return doCreate();
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    doDestroy(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeConfigure(
    JNIEnv*, jobject, jlong handle, jint sampleRate, jint channels) {
    doConfigure(handle, sampleRate, channels);
}

extern "C" JNIEXPORT void JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeReset(JNIEnv*, jobject, jlong handle) {
    doReset(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeProcessDirect(
    JNIEnv* env, jobject, jlong handle, jobject buffer, jint offset, jint length, jint encoding) {
    return doProcessDirect(env, handle, buffer, offset, length, encoding);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeProcessArray(
    JNIEnv* env, jobject, jlong handle, jbyteArray array, jint offset, jint length, jint encoding) {
    return doProcessArray(env, handle, array, offset, length, encoding);
}
