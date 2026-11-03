package com.neonsong.app.data.music

/**
 * Metadata and signed direct playback URL for an extracted YouTube video stream.
 */
data class YouTubeVideoStream(
    val videoId: String,
    val url: String,
    val itag: Int? = null,
    val mimeType: String? = null,
    val codec: String? = null,
    val resolution: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val bitrate: Int? = null,
    val durationMs: Long? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val expiresAtEpochMs: Long? = null,
) {
    val is60Fps: Boolean
        get() = (fps ?: 0) >= 50 || resolution?.contains("60") == true

    val displayQuality: String
        get() = when (height) {
            2160 -> if (is60Fps) "2160p60 (4K)" else "2160p (4K)"
            1440 -> if (is60Fps) "1440p60 (2K)" else "1440p (2K)"
            1080 -> if (is60Fps) "1080p60 (FHD)" else "1080p (FHD)"
            720 -> if (is60Fps) "720p60 (HD)" else "720p (HD)"
            480 -> "480p"
            360 -> "360p"
            240 -> "240p"
            144 -> "144p"
            else -> resolution?.takeIf { it.isNotBlank() }
                ?: if (height != null && height > 0) "${height}p" else "Auto"
        }
}

/**
 * A bundle of all available streams extracted for a YouTube video,
 * allowing instant user quality selection from 144p up to 2160p (4K).
 */
data class SyncedVideoStreamBundle(
    val videoId: String,
    val defaultStream: YouTubeVideoStream,
    val availableStreams: List<YouTubeVideoStream>,
)
