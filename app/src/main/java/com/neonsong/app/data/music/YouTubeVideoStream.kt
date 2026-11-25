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

    val aspectRatio: Float?
        get() = if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            null
        }

    /**
     * YouTube Music auto-generated "Art Tracks" (static album covers) are almost always 1:1 square.
     */
    val isSquareArtTrack: Boolean
        get() = aspectRatio?.let { it in 0.92f..1.08f } == true

    /**
     * Real music videos are widescreen (16:9, 21:9, or 4:3+ landscape).
     */
    val isWidescreen: Boolean
        get() = aspectRatio?.let { it >= 1.25f } == true

    val codecLabel: String?
        get() {
            val raw = "${codec.orEmpty()} ${mimeType.orEmpty()}".lowercase()
            return when {
                raw.contains("avc") || raw.contains("h264") -> "H.264"
                raw.contains("vp9") || raw.contains("vp09") -> "VP9"
                raw.contains("av01") || raw.contains("av1") -> "AV1"
                raw.contains("hev") || raw.contains("hvc") || raw.contains("h265") -> "HEVC"
                else -> null
            }
        }

    /**
     * H.264 and VP9 decode almost instantaneously in hardware on Android devices,
     * avoiding AV1 software decode stalls that hurt initial load & lip-sync lock.
     */
    val isHardwareFriendlyCodec: Boolean
        get() {
            val raw = "${codec.orEmpty()} ${mimeType.orEmpty()}".lowercase()
            return !raw.contains("av01") && !raw.contains("av1")
        }

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
    val videoTitle: String? = null,
    val uploaderName: String? = null,
) {
    /**
     * Picks the fastest-loading, crispest hardware-decoded stream for "Auto" mode:
     * prefers 720p/1080p H.264 or VP9 so first-frame time and PLL sync are instantaneous.
     */
    val fastStartStream: YouTubeVideoStream
        get() {
            if (availableStreams.isEmpty()) return defaultStream
            val hwFriendly = availableStreams.filter { it.isHardwareFriendlyCodec }
            val pool = hwFriendly.ifEmpty { availableStreams }

            return pool.maxByOrNull { stream ->
                val h = stream.height ?: 0
                val resScore = when (h) {
                    720 -> 100
                    1080 -> 95
                    480 -> 72
                    1440 -> 60
                    360 -> 50
                    2160 -> 40
                    else -> 20
                }
                val codecBonus = when (stream.codecLabel) {
                    "H.264" -> 18
                    "VP9" -> 12
                    "HEVC" -> 8
                    else -> 0
                }
                val mp4Bonus = if (stream.mimeType?.contains("mp4", ignoreCase = true) == true) 6 else 0
                val fpsBonus = if (stream.is60Fps && h <= 1080) 4 else 0
                resScore + codecBonus + mp4Bonus + fpsBonus
            } ?: defaultStream
        }
}
