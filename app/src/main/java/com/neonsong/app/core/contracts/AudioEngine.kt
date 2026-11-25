package com.neonsong.app.core.contracts

/**
 * High-level contract isolating the native C++20 Oboe audio engine from Kotlin layers.
 */
interface AudioEngine {
    /** Initialize and start low-level Oboe audio stream. */
    fun start(): Boolean

    /** Stop and tear down audio stream. */
    fun stop()

    /** Flush any pending frames queued in the hardware output stream. */
    fun flushOutput()

    /** Set master output volume attenuation (0.0f to 1.0f). */
    fun setOutputVolume(volume: Float)

    /** Toggle bit-perfect DAC pass-through (bypasses Android AudioTrack mixer). */
    fun setBitPerfect(enabled: Boolean)

    /** Check if bit-perfect direct output is currently engaged. */
    fun isBitPerfectActive(): Boolean

    /** Set 10-band equalizer gains in decibels. */
    fun setEqualizer(enabled: Boolean, gainsDb: FloatArray)

    /** Set individual deck volume multiplier for dual ExoPlayer mixing. */
    fun setDeckVolume(deck: Int, volume: Float)

    /** Set crossfade mix ratio (0.0f = Deck A only, 1.0f = Deck B only). */
    fun setCrossfade(mix: Float)

    /** Query current crossfade mix ratio. */
    fun getCrossfade(): Float

    /** Set playback tempo and pitch for a specific deck. */
    fun setDeckPlaybackParameters(deck: Int, speed: Float, pitch: Float)
}
