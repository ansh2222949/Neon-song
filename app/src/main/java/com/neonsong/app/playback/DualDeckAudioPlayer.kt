@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.neonsong.app.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink

/**
 * Hybrid Dual-ExoPlayer audio manager for Neon Song.
 *
 * Implements the next-generation dual-deck audio architecture:
 * - Two independent Media3 ExoPlayer instances: Deck A ([DECK_A] = 0) and Deck B ([DECK_B] = 1).
 * - Decoded PCM from each ExoPlayer is captured by a dedicated [NativeDeckAudioSink].
 * - PCM is transferred via JNI into separate native lock-free SPSC ring buffers:
 *     Deck A → Native Ring A (Deck 0)
 *     Deck B → Native Ring B (Deck 1)
 * - The C++ engine consumes both buffers and performs:
 *     - Resampling to native hardware rate via libsoxr
 *     - Real-time WSOLA time-stretching on Deck B
 *     - Equal-power crossfade and per-deck volume scaling
 *     - Post-mix DSP processing
 *     - Zero-allocation, lock-free real-time Oboe output streaming
 *
 * No platform AudioTrack is created, preventing duplicate playback and clock domain conflicts.
 */
class DualDeckAudioPlayer(
    val context: Context,
    val nativeAudioEngine: NativeAudioEngine,
    playerFactory: ((deckId: Int, sink: NativeDeckAudioSink) -> ExoPlayer)? = null,
) {
    companion object {
        const val DECK_A = 0
        const val DECK_B = 1
        const val DECK_COUNT = 2
    }

    val sinkA = NativeDeckAudioSink(deckId = DECK_A, nativeEngine = nativeAudioEngine)
    val sinkB = NativeDeckAudioSink(deckId = DECK_B, nativeEngine = nativeAudioEngine)

    val playerA: ExoPlayer = playerFactory?.invoke(DECK_A, sinkA) ?: createDefaultPlayer(DECK_A, sinkA)
    val playerB: ExoPlayer = playerFactory?.invoke(DECK_B, sinkB) ?: createDefaultPlayer(DECK_B, sinkB)

    private fun createDefaultPlayer(deckId: Int, sink: NativeDeckAudioSink): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return sink
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setEnableAudioTrackPlaybackParams(false)
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
                        .build(),
                    /* handleAudioFocus = */ deckId == DECK_A,
                )
                volume = 1f
            }
    }

    fun playerFor(deckId: Int): ExoPlayer {
        return when (deckId) {
            DECK_A -> playerA
            DECK_B -> playerB
            else -> throw IllegalArgumentException("Invalid deckId: $deckId. Must be 0 (Deck A) or 1 (Deck B).")
        }
    }

    fun sinkFor(deckId: Int): NativeDeckAudioSink {
        return when (deckId) {
            DECK_A -> sinkA
            DECK_B -> sinkB
            else -> throw IllegalArgumentException("Invalid deckId: $deckId. Must be 0 (Deck A) or 1 (Deck B).")
        }
    }

    /**
     * Sets equal-power crossfade progress between Deck A and Deck B.
     * 0.0f = 100% Deck A, 0% Deck B.
     * 1.0f = 0% Deck A, 100% Deck B.
     */
    fun setCrossfade(progress: Float) {
        nativeAudioEngine.setCrossfade(progress.coerceIn(0f, 1f))
    }

    fun getCrossfade(): Float {
        return nativeAudioEngine.getCrossfade()
    }

    /**
     * Sets individual deck volume scale in range 0.0f..1.0f.
     */
    fun setDeckVolume(deckId: Int, volume: Float) {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        sinkFor(deckId).setVolume(volume.coerceIn(0f, 1f))
    }

    /**
     * Sets independent playback speed and pitch parameters for a deck.
     * Speed controls time-stretching; pitch controls pitch-shifting.
     */
    fun setDeckPlaybackParameters(deckId: Int, speed: Float, pitch: Float) {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        val clampedSpeed = if (speed > 0f) speed.coerceIn(0.25f, 3.0f) else 1.0f
        val clampedPitch = if (pitch > 0f) pitch.coerceIn(0.25f, 3.0f) else 1.0f
        val params = PlaybackParameters(clampedSpeed, clampedPitch)
        sinkFor(deckId).setPlaybackParameters(params)
        playerFor(deckId).playbackParameters = params
    }

    fun getDeckSpeed(deckId: Int): Float {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        val speed = nativeAudioEngine.getDeckSpeed(deckId)
        return if (speed > 0f) speed else 1.0f
    }

    fun getDeckPitch(deckId: Int): Float {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        val pitch = nativeAudioEngine.getDeckPitch(deckId)
        return if (pitch > 0f) pitch else 1.0f
    }

    /**
     * Sets playback speed / time ratio for a deck (clamped to 0.25f..3.0f).
     * C++ performs WSOLA time-stretching in real-time.
     */
    fun setDeckTimeRatio(deckId: Int, ratio: Float) {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        val clamped = if (ratio > 0f) ratio.coerceIn(0.25f, 3.0f) else 1.0f
        nativeAudioEngine.setDeckTimeRatio(deckId, clamped)
        val currentPitch = getDeckPitch(deckId)
        val params = if (currentPitch == 1.0f) {
            PlaybackParameters(clamped)
        } else {
            PlaybackParameters(clamped, currentPitch)
        }
        sinkFor(deckId).setPlaybackParameters(params)
        playerFor(deckId).playbackParameters = params
    }

    fun getDeckTimeRatio(deckId: Int): Float {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        return nativeAudioEngine.getDeckTimeRatio(deckId)
    }

    /**
     * Flushes a specific deck's native ring buffer and sink state without tearing down Oboe.
     */
    fun flushDeck(deckId: Int) {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        sinkFor(deckId).flush()
    }

    /**
     * Returns frames currently buffered in the native C++ SPSC ring buffer for this deck.
     */
    fun getDeckBufferedFrames(deckId: Int): Long {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        return nativeAudioEngine.getDeckBufferedFrames(deckId)
    }

    /**
     * Sets native ring-buffer consumption state independently from ExoPlayer decoding state.
     * When consuming is false, ExoPlayer can decode and prebuffer PCM into the ring buffer
     * without Oboe consuming or discarding frames.
     */
    fun setDeckConsuming(deckId: Int, consuming: Boolean) {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        sinkFor(deckId).isNativeConsuming = consuming
    }

    fun isDeckConsuming(deckId: Int): Boolean {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        return sinkFor(deckId).isNativeConsuming
    }

    /**
     * Verifies whether a deck (e.g. standby Deck B) is genuinely decoded and prebuffered
     * before crossfade or playback transition begins.
     */
    fun isDeckPrebuffered(deckId: Int, minFrames: Long = 4800L): Boolean {
        require(deckId in 0 until DECK_COUNT) { "Invalid deckId: $deckId" }
        return getDeckBufferedFrames(deckId) >= minFrames
    }

    /**
     * Prepares and loads a media item on a given deck.
     */
    fun loadDeck(deckId: Int, mediaItem: MediaItem, playWhenReady: Boolean = false) {
        val player = playerFor(deckId)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /**
     * Releases both ExoPlayer instances and associated native deck sinks.
     */
    fun release() {
        playerA.release()
        playerB.release()
        sinkA.reset()
        sinkB.reset()
        nativeAudioEngine.flushDeck(DECK_A)
        nativeAudioEngine.flushDeck(DECK_B)
    }
}
