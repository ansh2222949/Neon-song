package com.neonsong.app.playback.resolve

import com.neonsong.app.data.music.YouTubeAudioStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.io.File

/**
 * Playback resolver always wins. Prefetch is cancelled when the user selects
 * a track and is limited to a single next-track job.
 */
class StreamResolveCoordinator(
    cacheDir: File,
    private val scope: CoroutineScope,
    private val extract: suspend (youtubeVideoId: String) -> YouTubeAudioStream,
    private val logger: ResolverLogger = AndroidResolverLogger,
) {
    val cache = PlaybackStreamCache(cacheDir, scope = scope)
    val playback = PlaybackResolver(cache, logger)
    val prefetch = PrefetchResolver(cache, logger)
    private val extraction = StreamExtraction(scope, cache, extract)

    fun cancelAll(reason: String) {
        val previous = playback.requested
        playback.invalidate()
        prefetch.cancel()
        logger.event(ResolveEvent.CANCELLED, previous, 0L, null, reason)
    }

    /**
     * Resolves [identity] for the user-selected track.
     * Joins an in-flight resolve of the same identity. Any other identity is stale.
     */
    suspend fun resolvePlayback(identity: TrackIdentity, durationMs: Long?): StreamCache {
        val active = playback.requested
        val token = playback.claim(identity)
        if (active == null) prefetch.cancel()
        return playback.resolve(token, identity, durationMs, extraction::await)
    }

    fun prefetchNext(
        identity: TrackIdentity,
        durationMs: Long?,
        onReady: suspend (StreamCache) -> Unit,
    ): Job? {
        if (playback.requested?.youtubeVideoId == identity.youtubeVideoId) return null
        return prefetch.enqueue(
            scope = scope,
            identity = identity,
            durationMs = durationMs,
            activePlayback = playback::requested,
            extract = extraction::await,
            onReady = onReady,
        )
    }

    /**
     * Synchronously checks whether a pre-resolved stream for [identity]
     * is already cached and not expired. Returns the cached [StreamCache]
     * if available, null otherwise. Zero-cost: no network, no suspend.
     * Used by skipToNext() to bypass the async resolution waterfall.
     */
    fun getPreResolved(identity: TrackIdentity): StreamCache? {
        return cache.get(identity)
    }

    /**
     * Eagerly prefetches the next track when the current track reaches 50%
     * or the queue updates. This ensures the stream URL is pre-resolved
     * before the user presses next, making skips near-instant.
     */
    fun prefetchIfNeeded(
        nextIdentity: TrackIdentity?,
        nextDurationMs: Long?,
        onReady: suspend (StreamCache) -> Unit,
    ) {
        if (nextIdentity == null) return
        // Already cached and valid? Nothing to do.
        if (cache.get(nextIdentity) != null) return
        prefetchNext(nextIdentity, nextDurationMs, onReady)
    }
}
