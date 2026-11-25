package com.neonsong.app.features.player.domain

import com.neonsong.app.data.generate.GeneratedTrack
import com.neonsong.app.playback.PlayableTrack

/**
 * Tab enumeration for the full screen player modes.
 */
enum class FullPlayerTab {
    NOW_PLAYING,
    LYRICS,
    QUEUE
}

/**
 * Format milliseconds into M:SS or MM:SS presentation format.
 */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Convert a [PlayableTrack] to a [GeneratedTrack] for playlist and library operations.
 */
fun PlayableTrack.toGeneratedTrack(): GeneratedTrack = GeneratedTrack(
    name = title,
    artist = artist,
    artworkUrl = artworkUrl,
    album = album,
    url = videoId?.let { "https://music.youtube.com/watch?v=$it" }.orEmpty(),
)
