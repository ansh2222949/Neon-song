package com.neonsong.app.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.neonsong.app.data.generate.GeneratedTrack
import com.neonsong.app.data.generate.youtubeVideoIdOrNull
import com.neonsong.app.data.music.YouTubeAudioStream
import com.neonsong.app.data.plugin.SegmentedDashBridge
import com.neonsong.app.playback.resolve.StreamCache
import com.neonsong.app.playback.resolve.playbackIdentity
import com.neonsong.app.playback.resolve.toYouTubeAudioStream
import java.io.File

internal fun PlayableTrack.withYoutubeArtwork(): PlayableTrack =
    if (artworkUrl.isNullOrBlank() && !videoId.isNullOrBlank()) {
        copy(artworkUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
    } else this

@OptIn(UnstableApi::class)
internal fun PlayableTrack.toMediaItem(resolved: MusicPlayer.ResolvedStream? = null): MediaItem {
    val playbackUri = if (resolved != null) {
        Uri.parse(resolved.url)
    } else if (playbackUrl?.isNotBlank() == true) {
        if (playbackUrl.startsWith("/")) {
            Uri.fromFile(File(playbackUrl))
        } else {
            Uri.parse(playbackUrl)
        }
    } else if (!videoId.isNullOrBlank()) {
        Uri.Builder().scheme("neonsong").authority("youtube").appendPath(videoId)
            .appendQueryParameter("title", title)
            .appendQueryParameter("artist", artist)
            .build()
    } else {
        Uri.Builder().scheme("neonsong").authority("search")
            .appendQueryParameter("title", title)
            .appendQueryParameter("artist", artist)
            .build()
    }
    val mediaIdKey = mediaIdKey()
    return MediaItem.Builder()
        .setMediaId(mediaIdKey)
        .setUri(playbackUri)
        .apply {
            (resolved?.mimeType ?: playbackMimeType)?.takeIf(String::isNotBlank)?.let(::setMimeType)
            resolved?.let {
                setCustomCacheKey(it.cacheKey)
                // Segmented provider module: MPD uri + Widevine config so the
                // stock DASH source decrypts chunk-by-chunk via MediaCrypto.
                it.segmentedDrm?.let { descriptor ->
                    SegmentedDashBridge.drmConfigurationFor(descriptor)?.let(::setDrmConfiguration)
                }
            }
        }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri((artworkUrl?.takeIf(String::isNotBlank)
                    ?: (videoId ?: resolved?.youtubeCandidate?.videoId)?.let {
                        "https://i.ytimg.com/vi/$it/hqdefault.jpg"
                    })?.let(Uri::parse))
                .setIsPlayable(true)
                .setExtras(
                    android.os.Bundle().apply {
                        val d = durationMs ?: resolved?.durationMs
                        if (d != null && d > 0L) putLong("durationMs", d)
                        internalTrackId?.takeIf { it.isNotBlank() }?.let { putString("internalTrackId", it) }
                    }
                )
                .build(),
        )
        .build()
}

internal fun PlayableTrack.mediaIdKey(): String = when {
    !playbackUrl.isNullOrBlank() -> "local:${playbackUrl}"
    !videoId.isNullOrBlank() -> videoId
    else -> "query:${artist.lowercase()}|${title.lowercase()}"
}

internal fun StreamCache.toPlayerStream(): MusicPlayer.ResolvedStream {
    val ytStream = toYouTubeAudioStream()
    val trueBitrate = ytStream.bitrate.takeIf { it > 0 }?.let { (it + 500) / 1_000 }
    val rawCodec = ytStream.codec?.substringBefore(',')?.trim()?.uppercase()?.ifBlank {
        ytStream.mimeType?.substringAfter("audio/")?.substringBefore(';')?.uppercase()?.ifBlank { "WEBM" } ?: "WEBM"
    } ?: "WEBM"
    val codec = when {
        rawCodec.contains("OPUS") || rawCodec == "WEBM" -> "OPUS"
        rawCodec.contains("M4A") || rawCodec.contains("MP4") || rawCodec.contains("MP4A") || rawCodec.contains("AAC") -> "AAC"
        else -> rawCodec
    }
    val cleanMime = ytStream.mimeType?.substringBefore(';')?.trim().orEmpty()
    return MusicPlayer.ResolvedStream(
        url = ytStream.url,
        mimeType = cleanMime,
        bitrateKbps = trueBitrate,
        audioCodec = codec,
        cacheKey = ytStream.mediaCacheKey,
        requestHeaders = ytStream.requestHeaders,
        isLossless = false,
        samplingRateKHz = ytStream.sampleRateHz?.let { it / 1_000.0 },
        youtubeCandidate = ytStream,
        durationMs = ytStream.durationMs?.takeIf { it > 0 },
        expiresAtEpochMs = ytStream.expiresAtEpochMs,
    )
}

internal fun MediaItem.toPlayableTrack(): PlayableTrack {
    val uriStr = localConfiguration?.uri?.toString()
    val localUri = when {
        uriStr?.startsWith("content://") == true || uriStr?.startsWith("file://") == true || uriStr?.startsWith("/") == true -> uriStr
        mediaId.startsWith("local:") -> mediaId.removePrefix("local:")
        else -> null
    }
    val dur = mediaMetadata.extras?.getLong("durationMs")?.takeIf { it > 0L }
    return PlayableTrack(
        title = mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown track" },
        artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" },
        album = mediaMetadata.albumTitle?.toString(),
        artworkUrl = mediaMetadata.artworkUri?.toString(),
        videoId = mediaId.takeUnless { it.startsWith("query:") || it.startsWith("local:") },
        internalTrackId = mediaMetadata.extras?.getString("internalTrackId"),
        playbackUrl = localUri,
        playbackMimeType = localConfiguration?.mimeType,
        durationMs = dur,
    )
}

fun GeneratedTrack.toPlayableTrack(): PlayableTrack {
    val videoId = youtubeVideoIdOrNull()
    return PlayableTrack(
        title = name,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl ?: videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
        videoId = videoId,
    )
}

internal fun PlayableTrack.queueKey(): String =
    playbackIdentity()?.cacheKey ?: mediaIdKey()

internal fun PlayableTrack.searchQueueTitleKey(): String = title
    .lowercase()
    .replace(SEARCH_TITLE_VARIANT, " ")
    .replace(SEARCH_TITLE_NON_CHARACTER, "")

internal val SEARCH_TITLE_VARIANT = Regex(
    """\s*[\[(][^)\]]*\b(?:official|video|audio|lyrics?|cover|karaoke|remaster(?:ed)?|live|version|edit|mix|slowed|reverb)[^)\]]*[])]""",
    RegexOption.IGNORE_CASE,
)
internal val SEARCH_TITLE_NON_CHARACTER = Regex("[^\\p{L}\\p{N}]+")
