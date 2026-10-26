#include "ParametricEq.h"

#include <jni.h>

#include <cstdint>
#include <new>
#include <vector>

namespace {

using neonsong::audio::ParametricEq;

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
    const jsize count = env->GetArrayLength(types);
    if (env->GetArrayLength(frequencies) != count || env->GetArrayLength(gains) != count ||
        env->GetArrayLength(qs) != count || env->GetArrayLength(filterEnabled) != count) {
        return;
    }
    std::vector<jint> typeValues(static_cast<std::size_t>(count));
    std::vector<jfloat> frequencyValues(static_cast<std::size_t>(count));
    std::vector<jfloat> gainValues(static_cast<std::size_t>(count));
    std::vector<jfloat> qValues(static_cast<std::size_t>(count));
    std::vector<jboolean> onValues(static_cast<std::size_t>(count));
    if (count > 0) {
        env->GetIntArrayRegion(types, 0, count, typeValues.data());
        env->GetFloatArrayRegion(frequencies, 0, count, frequencyValues.data());
        env->GetFloatArrayRegion(gains, 0, count, gainValues.data());
        env->GetFloatArrayRegion(qs, 0, count, qValues.data());
        env->GetBooleanArrayRegion(filterEnabled, 0, count, onValues.data());
    }
    std::vector<std::int32_t> typeInts(typeValues.begin(), typeValues.end());
    std::vector<std::uint8_t> onBytes(onValues.begin(), onValues.end());
    ParametricEq::publish(
        enabled == JNI_TRUE,
        preampDb,
        typeInts.data(),
        frequencyValues.data(),
        gainValues.data(),
        qValues.data(),
        onBytes.data(),
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
    if (eq == nullptr || buffer == nullptr || offset < 0 || length <= 0) return JNI_FALSE;
    auto* base = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (base == nullptr || capacity < 0 ||
        static_cast<jlong>(offset) + static_cast<jlong>(length) > capacity) {
        return JNI_FALSE;
    }
    return eq->processPcm(base + offset, static_cast<std::size_t>(length), encoding) ? JNI_TRUE : JNI_FALSE;
}

jboolean doProcessArray(
    JNIEnv* env,
    jlong handle,
    jbyteArray array,
    jint offset,
    jint length,
    jint encoding) {
    auto* eq = fromHandle(handle);
    if (eq == nullptr || array == nullptr || offset < 0 || length <= 0) return JNI_FALSE;
    const jsize size = env->GetArrayLength(array);
    if (static_cast<jlong>(offset) + static_cast<jlong>(length) > static_cast<jlong>(size)) return JNI_FALSE;
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

// Backward-compatible aliases for com.lastwave.app.playback.eq.ParametricEqNative
extern "C" JNIEXPORT void JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativePublish(
    JNIEnv* env, jobject thiz, jboolean enabled, jfloat preampDb, jintArray types,
    jfloatArray frequencies, jfloatArray gains, jfloatArray qs, jbooleanArray filterEnabled) {
    doPublish(env, enabled, preampDb, types, frequencies, gains, qs, filterEnabled);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeIsEngaged(JNIEnv* env, jobject thiz) {
    return doIsEngaged();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeCreate(JNIEnv* env, jobject thiz) {
    return doCreate();
}

extern "C" JNIEXPORT void JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    doDestroy(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeConfigure(
    JNIEnv* env, jobject thiz, jlong handle, jint sampleRate, jint channels) {
    doConfigure(handle, sampleRate, channels);
}

extern "C" JNIEXPORT void JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeReset(JNIEnv* env, jobject thiz, jlong handle) {
    doReset(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeProcessDirect(
    JNIEnv* env, jobject thiz, jlong handle, jobject buffer, jint offset, jint length, jint encoding) {
    return doProcessDirect(env, handle, buffer, offset, length, encoding);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lastwave_app_playback_eq_ParametricEqNative_nativeProcessArray(
    JNIEnv* env, jobject thiz, jlong handle, jbyteArray array, jint offset, jint length, jint encoding) {
    return doProcessArray(env, handle, array, offset, length, encoding);
}
