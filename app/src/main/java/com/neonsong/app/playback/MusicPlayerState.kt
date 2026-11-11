package com.neonsong.app.playback

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player

@Immutable
data class MusicPlayerState(
    val connected: Boolean = true,
    val current: PlayableTrack? = null,
    val queue: List<PlayableTrack> = emptyList(),
    val currentIndex: Int = -1,
    val sourceLabel: String = "Neon Song",
    val isEndlessQueue: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val bitrateKbps: Int? = null,
    val audioCodec: String? = null,
    val isLossless: Boolean = false,
    val bitDepth: Int? = null,
    val samplingRateKHz: Double? = null,
    val sleepTimerRemainingMs: Long? = null,
    val isAutoMixActive: Boolean = false,
    val isAutoMixEnabled: Boolean = false,
    val autoMixBars: Int = 8,
    val autoMixState: AutoMixState = AutoMixState.IDLE,
    val autoMixProgress: Float = 0f,
    val autoMixBpm: Float? = null,
    val isAutoDJActive: Boolean = true,
    val autoDJStatus: String? = null,
    val error: String? = null,
)

/**
 * Playback fields used by list rows and collapsed player chrome. Unlike
 * [MusicPlayerState], this does not contain the 60 ms position ticker, so a
 * playing track no longer invalidates every visible track list 16 times/sec.
 */
@Immutable
data class PlaybackChromeState(
    val current: PlayableTrack? = null,
    val sourceLabel: String = "Neon Song",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val queueSize: Int = 0,
    val shuffleEnabled: Boolean = false,
    val isLossless: Boolean = false,
    val audioCodec: String? = null,
    val sleepTimerRemainingMs: Long? = null,
)

/** The small, frequently changing state consumed only by progress UI. */
@Immutable
data class PlaybackProgressState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

