package com.neonsong.app.core.audio_api

import com.neonsong.app.core.contracts.AudioEngine
import com.neonsong.app.playback.NativeAudioEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clean adapter layer delegating high-level [AudioEngine] contract calls
 * to the low-level, package-locked [NativeAudioEngine] JNI bridge.
 */
@Singleton
class NativeAudioEngineAdapter @Inject constructor(
    private val nativeEngine: dagger.Lazy<NativeAudioEngine>
) : AudioEngine {

    override fun start(): Boolean {
        return nativeEngine.get().start()
    }

    override fun stop() {
        nativeEngine.get().stop()
    }

    override fun flushOutput() {
        nativeEngine.get().flushOutput()
    }

    override fun setOutputVolume(volume: Float) {
        nativeEngine.get().setOutputVolume(volume)
    }

    override fun setBitPerfect(enabled: Boolean) {
        nativeEngine.get().setBitPerfect(enabled)
    }

    override fun isBitPerfectActive(): Boolean {
        return nativeEngine.get().isBitPerfectActive()
    }

    override fun setEqualizer(enabled: Boolean, gainsDb: FloatArray) {
        nativeEngine.get().setEqualizer(enabled, gainsDb)
    }

    override fun setDeckVolume(deck: Int, volume: Float) {
        nativeEngine.get().setDeckVolume(deck, volume)
    }

    override fun setCrossfade(mix: Float) {
        nativeEngine.get().setCrossfade(mix)
    }

    override fun getCrossfade(): Float {
        return nativeEngine.get().getCrossfade()
    }

    override fun setDeckPlaybackParameters(deck: Int, speed: Float, pitch: Float) {
        nativeEngine.get().setDeckPlaybackParameters(deck, speed, pitch)
    }
}
