package com.neonsong.app.playback

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class PlayableTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val videoId: String? = null,
    /** Stable library id when one exists. Never a title or artist string. */
    val internalTrackId: String? = null,
    val playbackUrl: String? = null,
    val playbackMimeType: String? = null,
    val durationMs: Long? = null,
) {
    val stableId: String
        get() = videoId?.takeIf(String::isNotBlank)
            ?: internalTrackId?.takeIf(String::isNotBlank)
            ?: playbackUrl?.takeIf(String::isNotBlank)
            ?: "${title.lowercase().trim()}|${artist.lowercase().trim()}|${durationMs ?: 0L}"
}

@Serializable
internal data class PersistedPlaybackSession(
    val version: Int = 2,
    val queue: List<PlayableTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val sourceLabel: String = "Neon Song",
    val isEndlessQueue: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val durationMs: Long = 0L,
)

