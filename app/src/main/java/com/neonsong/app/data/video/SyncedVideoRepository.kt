package com.neonsong.app.data.video

import android.util.Log
import com.neonsong.app.data.music.InnerTubeMusicApi
import com.neonsong.app.data.music.SyncedVideoStreamBundle
import com.neonsong.app.data.music.YouTubeMusicTrack
import com.neonsong.app.data.music.YouTubeStreamExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "SyncedVideoRepo"
private const val CACHE_EXPIRY_MS = 3 * 60 * 60 * 1000L // 3 hours
private const val FAILED_ID_TTL_MS = 90_000L // 90 seconds negative cache
private const val MAX_CACHE_ENTRIES = 120

/**
 * Ultra-fast, high-accuracy music video resolver with:
 * - In-flight request deduplication (prefetch + UI share the exact same coroutine)
 * - Parallel direct-ID extraction + Official Music Video search
 * - Automatic 1:1 square Art-Track detection so real Official Music Videos always win
 * - Variant-aware scoring (rejects unwanted covers, remixes, lyrics, slowed/reverb, teasers, shorts)
 */
@Singleton
class SyncedVideoRepository @Inject constructor(
    private val innerTube: InnerTubeMusicApi,
    private val streamExtractor: YouTubeStreamExtractor,
) {
    private val cache = ConcurrentHashMap<String, CachedVideoBundle>()
    private val failedVideoIds = ConcurrentHashMap<String, Long>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Result<SyncedVideoStreamBundle>>>()
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class CachedVideoBundle(
        val bundle: SyncedVideoStreamBundle,
        val expiresAtMs: Long,
        val verifiedWithDuration: Boolean = false,
    )

    private data class ScoredCandidate(
        val track: YouTubeMusicTrack,
        val score: Double,
        val originalIndex: Int,
        val isExplicitOfficialMv: Boolean,
    )

    private fun trackCacheKey(title: String, artist: String, videoId: String? = null): String {
        val clean = normalize(cleanTrackTitle(title))
        val normArtist = normalize(artist)
        return if (clean.isNotBlank() || normArtist.isNotBlank()) {
            "track:$clean|$normArtist"
        } else {
            "id:${videoId.orEmpty()}"
        }
    }

    private fun videoCacheKey(videoId: String): String = "video:$videoId"

    private fun normalize(value: String): String =
        value.lowercase()
            .replace("’", "'")
            .replace(Regex("[^\\p{L}\\p{N}\\s'/&-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Strips streaming metadata suffixes like "(Official Audio)", "(From 'Movie')", "[Remastered]"
     * to produce a clean core song title for ultra-accurate search and token matching.
     */
    private fun cleanTrackTitle(title: String): String {
        var cleaned = title
            .replace(Regex("\\((?i)(official|audio|lyric|lyrics|video|hd|4k|hq|full\\s*song|from\\s+[^)]+|feat\\.?[^)]+|ft\\.?[^)]+|featuring[^)]+|remastered[^)]*|single\\s*version|album\\s*version|radio\\s*edit)\\)"), " ")
            .replace(Regex("\\[(?i)(official|audio|lyric|lyrics|video|hd|4k|hq|full\\s*song|from\\s+[^\\]]+|feat\\.?[^\\]]+|ft\\.?[^\\]]+|remastered[^\\]]*|single\\s*version|album\\s*version)]"), " ")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*$"), " ")
            .replace(Regex("\\s*[|•—–]\\s*.*$"), " ")
            .replace(Regex("\\s+-\\s+(?i)(single|from\\s+.*|remaster.*|stereo|mono|explicit)$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 2) cleaned = title.trim()
        return cleaned
    }

    private fun primaryArtists(artist: String): List<String> =
        artist.split(Regex("\\s*(?:,|&|\\bx\\b|(?i)\\bfeat\\.?\\b|(?i)\\bft\\.?\\b|(?i)\\band\\b|(?i)\\bwith\\b)\\s*"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

    private fun contentWords(value: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+")
            .findAll(normalize(value))
            .map { it.value }
            .filter { it.length > 1 || it.all(Char::isDigit) }
            .filterNot { it in VIDEO_MARKER_WORDS }
            .toSet()

    private fun matchRatio(target: Set<String>, candidate: Set<String>): Double {
        if (target.isEmpty() || candidate.isEmpty()) return 0.0
        val overlap = target.intersect(candidate).size.toDouble()
        return overlap / target.size
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union > 0.0) inter / union else 0.0
    }

    private fun detectVariantTags(text: String): Set<String> {
        val lower = normalize(text)
        return VARIANT_KEYWORDS.filter { keyword ->
            Regex("(^|\\s)${Regex.escape(keyword)}($|\\s)").containsMatchIn(lower)
        }.toSet()
    }

    private fun isStaticOrBlockedCandidate(
        candidate: YouTubeMusicTrack,
        allowedVariants: Set<String>,
    ): Boolean {
        val title = normalize(candidate.title)
        val artist = normalize(candidate.artist)
        if (STATIC_VIDEO_MARKERS.any { title.contains(it) }) return true
        if (title.endsWith(" - topic") || artist.endsWith(" - topic") || artist == "topic" || artist.contains("art track")) {
            return true
        }
        // Reject unexpected derivative versions (e.g., remix/cover/live/slowed) unless requested by the original track
        val candVariants = detectVariantTags(candidate.title)
        val unwantedVariants = candVariants - allowedVariants
        if (unwantedVariants.any { it in HARD_REJECT_UNREQUESTED_VARIANTS }) return true

        // Reject ultra-short teasers/shorts (<45s) unless duration is unknown
        val durSec = candidate.durationSeconds?.toLong()
        if (durSec != null && durSec in 1L..45L) return true

        return false
    }

    private fun officialVideoScore(title: String, artist: String): Pair<Double, Boolean> {
        val lowerTitle = normalize(title)
        val lowerArtist = normalize(artist)
        val labelBonus = if (
            lowerArtist.contains("vevo") ||
            lowerArtist.contains("t-series") ||
            lowerArtist.contains("sony music") ||
            lowerArtist.contains("zee music") ||
            lowerArtist.contains("yrf") ||
            lowerArtist.contains("tips official") ||
            lowerArtist.contains("saregama") ||
            lowerArtist.contains("speed records") ||
            lowerArtist.contains("official")
        ) 3.5 else 0.0

        return when {
            lowerTitle.contains("official music video") -> (14.0 + labelBonus) to true
            lowerTitle.contains("official video") || lowerTitle.contains("full video song") || lowerTitle.contains("official full video") ->
                (12.5 + labelBonus) to true
            lowerTitle.contains("music video") || lowerTitle.contains("m/v") || lowerTitle.contains("official mv") ->
                (10.5 + labelBonus) to true
            lowerTitle.contains("video song") -> (9.0 + labelBonus) to true
            lowerTitle.contains("official") && lowerTitle.contains("video") -> (8.5 + labelBonus) to true
            lowerTitle.contains("video") -> (3.5 + labelBonus) to false
            else -> labelBonus to false
        }
    }

    private fun durationAlignmentScore(targetDurationMs: Long?, candidateDurationSec: Long?): Double {
        val targetMs = targetDurationMs?.takeIf { it > 0L } ?: return 0.0
        val candMs = candidateDurationSec?.takeIf { it > 0L }?.times(1_000L) ?: return 0.0
        val signedDiffMs = candMs - targetMs // positive = video slightly longer (normal for MV intro/outro)
        val absDiffMs = abs(signedDiffMs)
        return when {
            absDiffMs <= 2_500L -> 12.0                         // Exact timestamp lock (+/- 2.5s)
            signedDiffMs in 2_501L..12_000L -> 8.5              // Standard MV with short intro/outro
            signedDiffMs in -6_000L..-2_501L -> 5.0             // Slightly trimmed video
            absDiffMs <= 22_000L -> 0.0                         // Extended intro/credits
            absDiffMs <= 45_000L -> -8.0                        // Noticeably different edit
            else -> -20.0                                       // Wrong version / compilation / jukebox
        }
    }

    private fun rankedMusicVideoCandidates(
        trackTitle: String,
        trackArtist: String,
        candidates: List<YouTubeMusicTrack>,
        preferredVideoId: String?,
        targetDurationMs: Long? = null,
        trackAlbum: String? = null,
    ): List<ScoredCandidate> {
        val cleanTitle = cleanTrackTitle(trackTitle)
        val normCleanTitle = normalize(cleanTitle)
        val titleWords = contentWords(cleanTitle)
        val rawTitleWords = contentWords(trackTitle)
        val artistList = primaryArtists(trackArtist)
        val artistWords = contentWords(trackArtist)
        val albumWords = contentWords(trackAlbum.orEmpty())
        val allowedVariants = detectVariantTags(trackTitle)

        return candidates.withIndex()
            .filterNot { isStaticOrBlockedCandidate(it.value, allowedVariants) }
            .mapNotNull { (index, candidate) ->
                val candCleanTitle = cleanTrackTitle(candidate.title)
                val normCandTitle = normalize(candCleanTitle)
                val candTitleWords = contentWords(candCleanTitle)
                val candArtistNorm = normalize(candidate.artist)
                val candTitleNorm = normalize(candidate.title)
                val candCombinedWords = contentWords("${candidate.title} ${candidate.artist}")

                // 1. Hard Duration Guard: reject edits that can never stay in sync
                val targetMs = targetDurationMs?.takeIf { it > 10_000L }
                val candDurMs = candidate.durationSeconds?.takeIf { it > 0 }?.toLong()?.times(1_000L)
                if (targetMs != null && candDurMs != null) {
                    val diffMs = candDurMs - targetMs
                    // Allow up to +35s (MV intro/outro) or -20s; reject 1-hour loops, teasers, extended skits
                    if (diffMs > 35_000L || diffMs < -20_000L) return@mapNotNull null
                }

                // 2. Title Coverage + Strict Extra-Word Check (stops "Stay" matching "Stay With Me")
                val titleCoverage = maxOf(
                    matchRatio(titleWords, candTitleWords),
                    matchRatio(rawTitleWords, candTitleWords),
                )
                val titleJaccard = jaccardSimilarity(titleWords, candTitleWords)
                if (titleCoverage < 0.60) return@mapNotNull null
                if (titleWords.size <= 2 && titleJaccard < 0.35 && normCandTitle != normCleanTitle) {
                    return@mapNotNull null
                }

                // 3. Hard Artist Verification Gate (stops wrong artist with same song title!)
                val artistMatchInChannel = matchRatio(artistWords, contentWords(candidate.artist))
                val artistMatchCombined = matchRatio(artistWords, candCombinedWords)
                val primaryArtistHit = artistList.any { a ->
                    val na = normalize(a)
                    na.length >= 2 && (candArtistNorm.contains(na) || candTitleNorm.contains(na))
                }
                val isOfficialLabel = OFFICIAL_LABEL_HINTS.any { candArtistNorm.contains(it) }
                // Movie/soundtrack fallback: official label channel + album/movie name in video title
                val albumMatchHit = isOfficialLabel && albumWords.isNotEmpty() &&
                    matchRatio(albumWords, candTitleWords) >= 0.50

                if (artistWords.isNotEmpty() && !primaryArtistHit && artistMatchCombined < 0.45 && !albumMatchHit) {
                    return@mapNotNull null
                }

                val artistScore = maxOf(artistMatchInChannel, artistMatchCombined) * 9.0 +
                    (if (primaryArtistHit) 5.0 else 0.0) +
                    (if (albumMatchHit) 4.0 else 0.0)

                val (officialScore, isExplicitOfficialMv) = officialVideoScore(candidate.title, candidate.artist)

                // Exact phrase match bonus + penalty for leftover unrelated title words
                val extraTitleWords = (candTitleWords - titleWords - artistWords - albumWords).size
                val extraWordPenalty = if (extraTitleWords > 3) (extraTitleWords - 3) * 1.6 else 0.0
                val exactTitleBonus = when {
                    normCandTitle == normCleanTitle -> 9.0
                    normCandTitle.contains(normCleanTitle) && normCleanTitle.length >= 4 -> 4.5
                    else -> 0.0
                }

                // Variant alignment bonus (e.g. if track IS Acoustic/Remix, reward matching clip)
                val candVariants = detectVariantTags(candidate.title)
                val variantAdjustment = when {
                    allowedVariants.isNotEmpty() && allowedVariants.any { it in candVariants } -> 8.0
                    allowedVariants.isNotEmpty() && candVariants.isEmpty() -> -5.0
                    else -> 0.0
                }

                val preferredIdBonus = if (candidate.videoId == preferredVideoId) 2.5 else 0.0
                val durationScore = durationAlignmentScore(targetDurationMs, candidate.durationSeconds?.toLong())

                val totalScore = (titleCoverage * 14.0) +
                    (titleJaccard * 7.0) +
                    exactTitleBonus +
                    artistScore +
                    officialScore +
                    preferredIdBonus +
                    durationScore +
                    variantAdjustment -
                    extraWordPenalty

                // Confidence floor: never play a weak/random video
                if (totalScore < 20.0) return@mapNotNull null

                ScoredCandidate(
                    track = candidate,
                    score = totalScore,
                    originalIndex = index,
                    isExplicitOfficialMv = isExplicitOfficialMv,
                )
            }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.score }
                    .thenBy { it.originalIndex },
            )
    }

    private fun isRecentlyFailed(videoId: String, now: Long): Boolean {
        val failedAt = failedVideoIds[videoId] ?: return false
        if (now - failedAt > FAILED_ID_TTL_MS) {
            failedVideoIds.remove(videoId, failedAt)
            return false
        }
        return true
    }

    private fun cacheBundle(
        trackKey: String,
        videoId: String,
        bundle: SyncedVideoStreamBundle,
        now: Long,
        verifiedWithDuration: Boolean = false,
    ) {
        val minTtlMs = 60_000L
        val streamExpiry = bundle.defaultStream.expiresAtEpochMs
        val safeStreamExpiry = if (streamExpiry != null && streamExpiry > now + minTtlMs) {
            streamExpiry - 30_000L
        } else {
            now + CACHE_EXPIRY_MS
        }
        val cachedExpiry = minOf(now + CACHE_EXPIRY_MS, safeStreamExpiry)
        val cached = CachedVideoBundle(bundle, cachedExpiry, verifiedWithDuration)
        pruneCacheIfNeeded()
        cache[videoCacheKey(videoId)] = cached
        cache[trackKey] = cached
    }

    private fun isDurationReasonable(targetDurationMs: Long?, streamDurationMs: Long?): Boolean {
        val target = targetDurationMs?.takeIf { it > 10_000L } ?: return true
        val actual = streamDurationMs?.takeIf { it > 10_000L } ?: return true
        return abs(target - actual) <= 22_000L
    }

    private suspend fun searchAndRank(
        trackTitle: String,
        trackArtist: String,
        preferredId: String?,
        targetDurationMs: Long?,
        trackAlbum: String? = null,
    ): List<ScoredCandidate> {
        val cleanTitle = cleanTrackTitle(trackTitle)
        val artistQuery = primaryArtists(trackArtist).take(2).joinToString(" ").ifBlank { trackArtist }
        val hasSpecialVariant = detectVariantTags(trackTitle).isNotEmpty()

        val primaryQuery = if (hasSpecialVariant) {
            "$cleanTitle $artistQuery official video"
        } else {
            "$cleanTitle $artistQuery official music video"
        }
        val primaryResults = runCatching {
            innerTube.searchVideos(primaryQuery, limit = 15)
        }.getOrDefault(emptyList())

        var ranked = rankedMusicVideoCandidates(
            trackTitle = trackTitle,
            trackArtist = trackArtist,
            candidates = primaryResults,
            preferredVideoId = preferredId,
            targetDurationMs = targetDurationMs,
            trackAlbum = trackAlbum,
        )

        // If first query didn't produce a strong explicit Official MV match, query movie/video-song or clean title+artist and merge
        val needsFallback = ranked.isEmpty() ||
            ranked.first().score < 28.0 ||
            !ranked.first().isExplicitOfficialMv
        if (needsFallback) {
            val cleanAlbum = trackAlbum?.let(::cleanTrackTitle)?.takeIf {
                it.length >= 3 && !it.equals(cleanTitle, ignoreCase = true)
            }
            val fallbackQuery = if (cleanAlbum != null) {
                "$cleanTitle $artistQuery $cleanAlbum video song"
            } else {
                "$cleanTitle $artistQuery"
            }
            val fallbackResults = runCatching {
                innerTube.searchVideos(fallbackQuery, limit = 15)
            }.getOrDefault(emptyList())
            val merged = (primaryResults + fallbackResults).distinctBy { it.videoId }
            ranked = rankedMusicVideoCandidates(
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                candidates = merged,
                preferredVideoId = preferredId,
                targetDurationMs = targetDurationMs,
                trackAlbum = trackAlbum,
            )
        }
        return ranked
    }

    fun invalidateTrack(trackTitle: String, trackArtist: String, videoId: String?) {
        val trackKey = trackCacheKey(trackTitle, trackArtist, videoId)
        cache.remove(trackKey)?.let { removed ->
            cache.remove(videoCacheKey(removed.bundle.videoId))
            failedVideoIds[removed.bundle.videoId] = System.currentTimeMillis()
        }
        videoId?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
            cache.remove(videoCacheKey(id))
            failedVideoIds[id] = System.currentTimeMillis()
        }
    }

    private fun isDirectBundleAuthenticMv(
        bundle: SyncedVideoStreamBundle,
        trackTitle: String,
        targetDurationMs: Long?,
    ): Boolean {
        if (bundle.defaultStream.isSquareArtTrack && bundle.availableStreams.none { it.isWidescreen }) {
            return false
        }
        if (!bundle.defaultStream.isWidescreen && bundle.availableStreams.none { it.isWidescreen }) {
            return false
        }
        if (!isDurationReasonable(targetDurationMs, bundle.defaultStream.durationMs)) {
            return false
        }
        // Reject 16:9 widescreen static audio, lyric videos, and auto-generated Topic uploads
        val allowedVariants = detectVariantTags(trackTitle)
        val titleLower = normalize(bundle.videoTitle.orEmpty())
        val uploaderLower = normalize(bundle.uploaderName.orEmpty())
        if (uploaderLower.endsWith(" - topic") || uploaderLower == "topic" || uploaderLower.contains("art track")) {
            return false
        }
        if (titleLower.isNotBlank()) {
            if (DIRECT_AUDIO_ONLY_MARKERS.any { titleLower.contains(it) }) return false
            val bundleVariants = detectVariantTags(titleLower)
            if ((bundleVariants - allowedVariants).any { it in HARD_REJECT_UNREQUESTED_VARIANTS }) return false
        }
        return true
    }

    /**
     * Prefetches and caches the video stream bundle asynchronously in the background.
     * Deduplicated with [resolveVideo] so UI opening mid-prefetch joins instantly.
     */
    fun prefetchVideo(
        trackTitle: String,
        trackArtist: String,
        videoId: String?,
        targetDurationMs: Long? = null,
        trackAlbum: String? = null,
    ) {
        val trackKey = trackCacheKey(trackTitle, trackArtist, videoId)
        val now = System.currentTimeMillis()
        if (cache[trackKey]?.let { it.expiresAtMs > now } == true) return
        if (inFlightRequests.containsKey(trackKey)) return

        prefetchScope.launch {
            runCatching {
                resolveVideo(trackTitle, trackArtist, videoId, targetDurationMs, trackAlbum)
            }
        }
    }

    suspend fun resolveVideo(
        trackTitle: String,
        trackArtist: String,
        videoId: String?,
        targetDurationMs: Long? = null,
        trackAlbum: String? = null,
    ): Result<SyncedVideoStreamBundle> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val trackKey = trackCacheKey(trackTitle, trackArtist, videoId)

        // 1. Instant memory cache hit
        val hasKnownDuration = targetDurationMs != null && targetDurationMs > 10_000L
        cache[trackKey]?.let { cached ->
            val durationValid = cached.verifiedWithDuration || !hasKnownDuration
            if (cached.expiresAtMs > now && durationValid) {
                return@withContext Result.success(cached.bundle)
            }
            if (cached.expiresAtMs <= now) cache.remove(trackKey, cached)
        }

        // 2. Join existing in-flight prefetch/load for zero duplicate latency
        inFlightRequests[trackKey]?.let { existing ->
            if (existing.isActive) {
                return@withContext runCatching { existing.await() }
                    .getOrElse { Result.failure(it) }
            }
        }

        val deferred = prefetchScope.async {
            try {
                resolveVideoInternal(
                    trackKey = trackKey,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    videoId = videoId,
                    targetDurationMs = targetDurationMs,
                    trackAlbum = trackAlbum,
                )
            } finally {
                inFlightRequests.remove(trackKey)
            }
        }
        inFlightRequests[trackKey] = deferred
        try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) {
                inFlightRequests.remove(trackKey, deferred)
            }
        }
    }

    private suspend fun resolveVideoInternal(
        trackKey: String,
        trackTitle: String,
        trackArtist: String,
        videoId: String?,
        targetDurationMs: Long?,
        trackAlbum: String? = null,
    ): Result<SyncedVideoStreamBundle> = coroutineScope {
        try {
            val now = System.currentTimeMillis()
            val preferredId = videoId?.trim()?.takeIf { it.isNotBlank() }

            // Check cached direct videoId first (only if it's a real widescreen video)
            if (preferredId != null) {
                cache[videoCacheKey(preferredId)]?.let { cached ->
                    if (cached.expiresAtMs > now && !cached.bundle.defaultStream.isSquareArtTrack) {
                        cache[trackKey] = cached
                        return@coroutineScope Result.success(cached.bundle)
                    }
                    if (cached.expiresAtMs <= now) cache.remove(videoCacheKey(preferredId), cached)
                }
            }

            // Launch direct extraction AND Official MV search IN PARALLEL for fast load + high accuracy!
            val directDeferred = if (preferredId != null && !isRecentlyFailed(preferredId, now)) {
                async {
                    runCatching { streamExtractor.resolveVideoStreamBundle(preferredId) }.getOrNull()
                }
            } else {
                null
            }

            val searchDeferred = async {
                searchAndRank(trackTitle, trackArtist, preferredId, targetDurationMs, trackAlbum)
            }

            val directBundle = directDeferred?.await()
            val directLooksLikeRealMv = directBundle != null &&
                isDirectBundleAuthenticMv(directBundle, trackTitle, targetDurationMs)
            val isDurationVerified = targetDurationMs != null && targetDurationMs > 10_000L

            // Fast-path: if the playing track's own videoId is ALREADY an authentic widescreen MV
            // with matching duration (±3.5s), it IS the exact audio source -> 100% sync & 0ms wait!
            if (directLooksLikeRealMv && directBundle != null && preferredId != null) {
                val streamDur = directBundle.defaultStream.durationMs?.takeIf { it > 10_000L }
                val targetDur = targetDurationMs?.takeIf { it > 10_000L }
                val exactSameCut = targetDur == null || streamDur == null || abs(targetDur - streamDur) <= 3_500L

                if (exactSameCut) {
                    searchDeferred.cancel()
                    cacheBundle(
                        trackKey = trackKey,
                        videoId = preferredId,
                        bundle = directBundle,
                        now = now,
                        verifiedWithDuration = targetDur != null,
                    )
                    return@coroutineScope Result.success(directBundle)
                }
            }

            // Otherwise (square Art Track, Topic upload, or Lossless studio audio with different duration):
            // use parallel Official MV search to find the best matching widescreen clip.
            val rankedCandidates = if (directLooksLikeRealMv) {
                withTimeoutOrNull(650L) { searchDeferred.await() }
            } else {
                searchDeferred.await()
            }.orEmpty()

            if (directLooksLikeRealMv && directBundle != null && preferredId != null) {
                val topCandidate = rankedCandidates.firstOrNull()
                val betterDurationMatch = topCandidate != null &&
                    topCandidate.track.videoId != preferredId &&
                    topCandidate.isExplicitOfficialMv &&
                    topCandidate.score >= 28.0

                if (!betterDurationMatch) {
                    searchDeferred.cancel()
                    cacheBundle(
                        trackKey = trackKey,
                        videoId = preferredId,
                        bundle = directBundle,
                        now = now,
                        verifiedWithDuration = isDurationVerified,
                    )
                    return@coroutineScope Result.success(directBundle)
                }
            }

            // Extract top 2 candidates IN PARALLEL for fastest fallback if #1 is blocked or square art
            var lastFailure: Exception? = null
            var squareFallback: SyncedVideoStreamBundle? = directBundle
            val eligibleCandidates = rankedCandidates
                .take(MAX_STREAM_CANDIDATES)
                .filter { it.track.videoId.isNotBlank() && !isRecentlyFailed(it.track.videoId.trim(), now) }

            val topBatch = eligibleCandidates.take(2)
            val batchResults = topBatch.map { scored ->
                val targetId = scored.track.videoId.trim()
                async {
                    if (targetId == preferredId && directBundle != null) {
                        return@async targetId to Result.success(directBundle)
                    }
                    cache[videoCacheKey(targetId)]?.takeIf {
                        it.expiresAtMs > now && !it.bundle.defaultStream.isSquareArtTrack
                    }?.let { return@async targetId to Result.success(it.bundle) }

                    targetId to runCatching { streamExtractor.resolveVideoStreamBundle(targetId) }
                }
            }.awaitAll()

            for ((targetId, res) in batchResults) {
                res.onSuccess { bundle ->
                    if (!bundle.defaultStream.isSquareArtTrack || bundle.availableStreams.any { it.isWidescreen }) {
                        cacheBundle(trackKey, targetId, bundle, now, verifiedWithDuration = isDurationVerified)
                        return@coroutineScope Result.success(bundle)
                    } else if (squareFallback == null) {
                        squareFallback = bundle
                    }
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    failedVideoIds[targetId] = System.currentTimeMillis()
                    lastFailure = err as? Exception
                }
            }

            // Remaining candidates (3..5) only if both top-2 failed
            for (scored in eligibleCandidates.drop(2)) {
                val targetVideoId = scored.track.videoId.trim()
                try {
                    val bundle = streamExtractor.resolveVideoStreamBundle(targetVideoId)
                    if (bundle.defaultStream.isSquareArtTrack && bundle.availableStreams.none { it.isWidescreen }) {
                        if (squareFallback == null) squareFallback = bundle
                        continue
                    }
                    cacheBundle(trackKey, targetVideoId, bundle, now, verifiedWithDuration = isDurationVerified)
                    return@coroutineScope Result.success(bundle)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    failedVideoIds[targetVideoId] = System.currentTimeMillis()
                    lastFailure = error
                }
            }

            // Ultimate fallback: if we at least resolved a playable direct stream, return it rather than failing blank
            if (squareFallback != null) {
                cacheBundle(trackKey, squareFallback.videoId, squareFallback, now, verifiedWithDuration = isDurationVerified)
                return@coroutineScope Result.success(squareFallback)
            }

            throw lastFailure ?: IllegalStateException("Could not find a music video for $trackTitle — $trackArtist")
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
        cache.entries.filter { it.value.expiresAtMs <= now }.forEach { cache.remove(it.key) }
        while (cache.size > MAX_CACHE_ENTRIES) {
            val oldestKey = cache.entries.minByOrNull { it.value.expiresAtMs }?.key ?: break
            cache.remove(oldestKey)
        }
    }

    private companion object {
        val VIDEO_MARKER_WORDS = setOf(
            "official", "music", "video", "mv", "lyrics", "lyric", "audio",
            "hd", "4k", "hq", "full", "song", "feat", "ft", "featuring", "by", "the",
        )
        val STATIC_VIDEO_MARKERS = listOf(
            "lyric", "lyrics", "lyrical", "official audio", "audio only", "(audio)", "[audio]",
            "audio version", "full audio", "visualizer", "art track", "provided to youtube by", "full album",
            "jukebox", "mashup", "whatsapp status", "status video", "ringtone", "1 hour", "loop",
            "8d audio", "3d audio", "bass boosted", "reaction", "fan made", "fanmade",
            "static image", "still image", "poster", "teaser", "trailer", "promo",
            "behind the scenes", "making of", "interview", "dance cover", "choreography",
            "tutorial", "guitar lesson", "piano tutorial", "shorts", "#shorts",
        )
        // Direct-played track ke liye sirf static audio/cover uploads skip karo; real Fan MVs allow raho
        val DIRECT_AUDIO_ONLY_MARKERS = listOf(
            "official audio", "audio only", "(audio)", "[audio]",
            "audio version", "art track", "provided to youtube by",
            "static image", "still image", "poster",
        )
        val VARIANT_KEYWORDS = setOf(
            "live", "remix", "acoustic", "unplugged", "instrumental",
            "karaoke", "slowed", "reverb", "nightcore", "sped up", "lofi", "lo-fi", "cover",
        )
        val HARD_REJECT_UNREQUESTED_VARIANTS = setOf(
            "live", "remix", "acoustic", "unplugged", "karaoke", "instrumental",
            "slowed", "reverb", "nightcore", "sped up", "lofi", "lo-fi", "cover",
        )
        val OFFICIAL_LABEL_HINTS = listOf(
            "vevo", "t-series", "sony music", "zee music", "yrf", "tips official",
            "saregama", "speed records", "universal music", "warner music", "atlantic",
            "hybe labels", "smtown", "jyp entertainment", "yg entertainment", "official",
        )
        const val MAX_STREAM_CANDIDATES = 5
    }
}
