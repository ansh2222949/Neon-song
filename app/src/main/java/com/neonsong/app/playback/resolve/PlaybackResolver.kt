package com.neonsong.app.playback.resolve

import com.neonsong.app.data.music.YouTubeAudioStream
import com.neonsong.app.data.music.isCooperativeCancellation
import com.neonsong.app.data.music.resolutionFailureDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.util.concurrent.TimeoutException

class StaleResolveException(val identity: TrackIdentity) : CancellationException(
    "Stale resolve for ${identity.cacheKey}",
)

class MismatchedStreamException(
    val requested: TrackIdentity,
    resolvedVideoId: String,
) : IOException(
    "Rejected mismatched stream requested=${requested.youtubeVideoId} resolved=$resolvedVideoId",
)

/**
 * Highest-priority resolver. One user selection at a time.
 * A result is applied only when its token still matches the selection that started it.
 */
class PlaybackResolver(
    private val cache: PlaybackStreamCache,
    private val logger: ResolverLogger,
) {
    private data class Selection(
        val token: Long,
        val identity: TrackIdentity?,
    )

    private val gate = Any()

    @Volatile
    private var selection = Selection(0L, null)

    val requested: TrackIdentity?
        get() = selection.identity

    val token: Long
        get() = selection.token

    fun begin(identity: TrackIdentity): Long = synchronized(gate) {
        val current = selection
        if (current.identity == identity) return current.token
        val next = Selection(current.token + 1L, identity)
        selection = next
        next.token
    }

    fun claim(identity: TrackIdentity): Long = synchronized(gate) {
        val current = selection
        if (current.identity != null && current.identity != identity) {
            throw StaleResolveException(identity)
        }
        if (current.identity == null) {
            val next = Selection(current.token + 1L, identity)
            selection = next
            return next.token
        }
        current.token
    }

    fun invalidate() {
        synchronized(gate) {
            selection = Selection(selection.token + 1L, null)
        }
    }

    fun isCurrent(token: Long, identity: TrackIdentity): Boolean {
        val snap = selection
        return snap.token == token && snap.identity == identity
    }

    suspend fun resolve(
        token: Long,
        identity: TrackIdentity,
        durationMs: Long?,
        extract: suspend (youtubeVideoId: String) -> YouTubeAudioStream,
    ): StreamCache {
        if (identity.youtubeVideoId.isBlank()) {
            throw IOException("Blank youtubeVideoId for ${identity.trackId}")
        }
        val started = System.nanoTime()
        logger.event(ResolveEvent.START, identity, 0L, durationMs, "playback")
        ensureCurrent(token, identity, started, durationMs)
        cache.get(identity)?.let { hit ->
            if (!hit.matches(identity)) {
                cache.invalidate(identity)
            } else {
                logger.event(
                    ResolveEvent.CACHE_HIT,
                    identity,
                    elapsedSince(started),
                    hit.durationMs ?: durationMs,
                    "playback",
                )
                ensureCurrent(token, identity, started, durationMs)
                return hit
            }
        }
        logger.event(ResolveEvent.CACHE_MISS, identity, elapsedSince(started), durationMs, "playback")
        var failure: Exception? = null
        repeat(2) {
            ensureCurrent(token, identity, started, durationMs)
            val extracted = try {
                PlaybackStartupLog.extractStart()
                try {
                    extract(identity.youtubeVideoId)
                } finally {
                    PlaybackStartupLog.extractEnd()
                }
            } catch (error: Throwable) {
                if (error is TimeoutCancellationException || error is TimeoutException) {
                    logger.event(
                        ResolveEvent.FAILED,
                        identity,
                        elapsedSince(started),
                        durationMs,
                        resolutionFailureDetail(error),
                    )
                    throw IOException("Timed out resolving ${identity.youtubeVideoId}", error)
                }
                if (error.isCooperativeCancellation()) {
                    logger.event(ResolveEvent.CANCELLED, identity, elapsedSince(started), durationMs, "playback")
                    throw error
                }
                logger.event(
                    ResolveEvent.FAILED,
                    identity,
                    elapsedSince(started),
                    durationMs,
                    resolutionFailureDetail(error),
                )
                throw error
            }
            if (extracted.videoId != identity.youtubeVideoId) {
                cache.invalidate(identity)
                failure = MismatchedStreamException(identity, extracted.videoId)
                logger.event(
                    ResolveEvent.IGNORED_STALE,
                    identity,
                    elapsedSince(started),
                    durationMs,
                    "resolvedTrackId=${extracted.videoId}",
                )
                return@repeat
            }
            if (extracted.url.isBlank()) {
                cache.invalidate(identity)
                failure = IOException("Empty stream URL resolved for ${identity.youtubeVideoId}")
                return@repeat
            }
            val entry = extracted.toStreamCache(identity)
            // A skip can invalidate the token in the gap after the player
            // response returns. Keep the URL anyway so the next tap of this
            // video is a cache hit instead of another youtubei/player call.
            if (!isCurrent(token, identity)) {
                cache.putIfAbsent(entry)
                logger.event(
                    ResolveEvent.IGNORED_STALE,
                    identity,
                    elapsedSince(started),
                    durationMs,
                    "cached-after-complete",
                )
                throw StaleResolveException(identity)
            }
            publish(token, identity, entry, started, durationMs)
            logger.event(
                ResolveEvent.SUCCESS,
                identity,
                elapsedSince(started),
                entry.durationMs ?: durationMs,
                "playback",
            )
            return entry
        }
        throw failure ?: IOException("Unable to resolve ${identity.youtubeVideoId}")
    }

    private fun publish(
        token: Long,
        identity: TrackIdentity,
        entry: StreamCache,
        started: Long,
        durationMs: Long?,
    ) {
        if (!isCurrent(token, identity)) {
            cache.putIfAbsent(entry)
            logger.event(
                ResolveEvent.IGNORED_STALE,
                identity,
                elapsedSince(started),
                durationMs,
                "cached-after-complete requestedTrackId=${requested?.trackId.orEmpty()}",
            )
            throw StaleResolveException(identity)
        }
        cache.putFromPlayback(entry)
    }

    private fun ensureCurrent(
        token: Long,
        identity: TrackIdentity,
        started: Long,
        durationMs: Long?,
    ) {
        if (isCurrent(token, identity)) return
        logger.event(
            ResolveEvent.IGNORED_STALE,
            identity,
            elapsedSince(started),
            durationMs,
            "requestedTrackId=${requested?.trackId.orEmpty()}",
        )
        throw StaleResolveException(identity)
    }
}
