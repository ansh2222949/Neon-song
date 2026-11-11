package com.neonsong.app.data.video

import android.util.Log
import com.neonsong.app.data.music.InnerTubeMusicApi
import com.neonsong.app.data.music.SyncedVideoStreamBundle
import com.neonsong.app.data.music.YouTubeMusicTrack
import com.neonsong.app.data.music.YouTubeStreamExtractor
import com.neonsong.app.data.music.YouTubeVideoStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncedVideoRepo"
private const val CACHE_EXPIRY_MS = 3 * 60 * 60 * 1000L // 3 hours
private const val MAX_CACHE_ENTRIES = 100

/**
 * Resolves and caches direct video streams for synchronized music video playback.
 */
@Singleton
class SyncedVideoRepository @Inject constructor(
    private val innerTube: InnerTubeMusicApi,
    private val streamExtractor: YouTubeStreamExtractor,
) {
    private val cache = ConcurrentHashMap<String, CachedVideoBundle>()
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class CachedVideoBundle(
        val bundle: SyncedVideoStreamBundle,
        val expiresAtMs: Long,
    )

    private fun trackCacheKey(title: String, artist: String): String =
        "track:${normalize(title)}|${normalize(artist)}"

    private fun videoCacheKey(videoId: String): String = "video:$videoId"

    private fun normalize(value: String): String = value.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun contentWords(value: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+")
            .findAll(normalize(value))
            .map { it.value }
            .filterNot { it in VIDEO_MARKER_WORDS }
            .toSet()

    private fun matchRatio(target: Set<String>, candidate: Set<String>): Double {
        if (target.isEmpty() || candidate.isEmpty()) return 0.0
        return target.intersect(candidate).size.toDouble() / target.size
    }

    private fun isStaticOrLyricsCandidate(candidate: YouTubeMusicTrack): Boolean {
        val title = normalize(candidate.title)
        val artist = normalize(candidate.artist)
        return STATIC_VIDEO_MARKERS.any { marker -> title.contains(marker) } ||
            title.endsWith(" - topic") ||
            artist.endsWith(" - topic") || artist == "topic" || artist.contains("art track")
    }

    private fun officialVideoScore(title: String): Int {
        val lower = normalize(title)
        return when {
            lower.contains("official music video") -> 12
            lower.contains("official video") -> 10
            lower.contains("music video") || lower.contains("m/v") -> 8
            lower.contains("official") && lower.contains("video") -> 7
            lower.contains("video") -> 3
            else -> 0
        }
    }

    private fun rankedMusicVideoCandidates(
        trackTitle: String,
        trackArtist: String,
        candidates: List<YouTubeMusicTrack>,
        preferredVideoId: String?,
    ): List<YouTubeMusicTrack> {
        val titleWords = contentWords(trackTitle)
        val artistWords = contentWords(trackArtist)
        return candidates.withIndex()
            .filterNot { isStaticOrLyricsCandidate(it.value) }
            .mapNotNull { (index, candidate) ->
                val titleMatch = matchRatio(titleWords, contentWords(candidate.title))
                val artistMatch = matchRatio(artistWords, contentWords(candidate.artist))
                val officialScore = officialVideoScore(candidate.title)
                if (titleMatch < 0.4 && !(officialScore >= 7 && artistMatch >= 0.4)) return@mapNotNull null
                val preferredIdBonus = if (candidate.videoId == preferredVideoId) 1.5 else 0.0
                Triple(candidate, titleMatch * 10.0 + artistMatch * 4.0 + officialScore + preferredIdBonus, index)
            }
            .sortedWith(
                compareByDescending<Triple<YouTubeMusicTrack, Double, Int>> { it.second }
                    .thenBy { it.third },
            )
            .map { it.first }
    }

    /**
     * Prefetches and caches the video stream bundle asynchronously in the background.
     */
    fun prefetchVideo(trackTitle: String, trackArtist: String, videoId: String?) {
        val trackKey = trackCacheKey(trackTitle, trackArtist)
        val now = System.currentTimeMillis()
        if (cache[trackKey]?.let { it.expiresAtMs > now } == true) return
        prefetchScope.launch {
            runCatching {
                resolveVideo(trackTitle, trackArtist, videoId)
            }
        }
    }

    suspend fun resolveVideo(
        trackTitle: String,
        trackArtist: String,
        videoId: String?,
    ): Result<SyncedVideoStreamBundle> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val trackKey = trackCacheKey(trackTitle, trackArtist)
            cache[trackKey]?.let { cached ->
                if (cached.expiresAtMs > now) return@withContext Result.success(cached.bundle)
                cache.remove(trackKey, cached)
            }

            // Fast-path: If an explicit videoId is provided, check videoKey cache or try direct extraction first!
            val preferredId = videoId?.trim()?.takeIf { it.isNotBlank() }
            if (preferredId != null) {
                val videoKey = videoCacheKey(preferredId)
                cache[videoKey]?.let { cached ->
                    if (cached.expiresAtMs > now) {
                        cache[trackKey] = cached
                        return@withContext Result.success(cached.bundle)
                    }
                    cache.remove(videoKey, cached)
                }

                // If not marked static, attempt direct stream extraction first (bypasses 800ms search)
                val directResult = runCatching {
                    streamExtractor.resolveVideoStreamBundle(preferredId)
                }.getOrNull()

                if (directResult != null) {
                    val cached = CachedVideoBundle(directResult, now + CACHE_EXPIRY_MS)
                    pruneCacheIfNeeded()
                    cache[videoKey] = cached
                    cache[trackKey] = cached
                    return@withContext Result.success(directResult)
                }
            }

            // Fallback to searching video candidates
            val videoCandidates = innerTube.searchVideos("$trackTitle $trackArtist", limit = 12)
            val rankedCandidates = rankedMusicVideoCandidates(
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                candidates = videoCandidates,
                preferredVideoId = preferredId,
            )
            if (rankedCandidates.isEmpty()) {
                throw IllegalStateException("Could not find a music video for $trackTitle — $trackArtist")
            }

            var lastFailure: Exception? = null
            for (candidate in rankedCandidates.take(MAX_STREAM_CANDIDATES)) {
                val targetVideoId = candidate.videoId.trim()
                if (targetVideoId.isBlank()) continue

                val videoKey = videoCacheKey(targetVideoId)
                cache[videoKey]?.let { cached ->
                    if (cached.expiresAtMs > now) {
                        cache[trackKey] = cached
                        return@withContext Result.success(cached.bundle)
                    }
                    cache.remove(videoKey, cached)
                }

                try {
                    val bundle = streamExtractor.resolveVideoStreamBundle(targetVideoId)
                    val cached = CachedVideoBundle(bundle, now + CACHE_EXPIRY_MS)
                    pruneCacheIfNeeded()
                    cache[videoKey] = cached
                    cache[trackKey] = cached
                    return@withContext Result.success(bundle)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    lastFailure = error
                    Log.i(TAG, "Video candidate $targetVideoId was not playable; trying the next match")
                }
            }

            throw lastFailure ?: IllegalStateException("Could not resolve a music video for $trackTitle — $trackArtist")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Failed to resolve video stream for $trackTitle: ${error.message}", error)
            Result.failure(error)
        }
    }

    private fun pruneCacheIfNeeded() {
        if (cache.size <= MAX_CACHE_ENTRIES) return
        val now = System.currentTimeMillis()
        val expired = cache.entries.filter { it.value.expiresAtMs <= now }.map { it.key }
        expired.forEach { cache.remove(it) }
        if (cache.size > MAX_CACHE_ENTRIES) {
            val oldest = cache.entries.minByOrNull { it.value.expiresAtMs }?.key
            oldest?.let { cache.remove(it) }
        }
    }

    private companion object {
        val VIDEO_MARKER_WORDS = setOf("official", "music", "video", "mv", "lyrics", "lyric", "audio")
        val STATIC_VIDEO_MARKERS = listOf(
            "lyric", "lyrics", "official audio", "audio only", "(audio)", "[audio]",
            "audio version", "visualizer", "art track", "provided to youtube by", "full album",
            "karaoke", "instrumental", "slowed", "reverb", "nightcore", "cover version",
            "piano cover", "guitar cover", "acoustic cover", "cover by", "reaction", "fan made", "fanmade",
            "static image", "still image", "poster",
        )
        const val MAX_STREAM_CANDIDATES = 4
    }
}
