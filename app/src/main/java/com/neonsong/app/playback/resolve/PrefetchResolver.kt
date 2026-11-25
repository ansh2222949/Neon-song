package com.neonsong.app.playback.resolve

import android.util.Log
import com.neonsong.app.data.music.YouTubeAudioStream
import com.neonsong.app.data.music.isCooperativeCancellation
import com.neonsong.app.data.music.isExpectedAudioResolutionFailure
import com.neonsong.app.data.music.resolutionFailureDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Resolves at most the next track. It never claims the playback generation
 * and never replaces a cache entry playback has already stored.
 */
class PrefetchResolver(
    private val cache: PlaybackStreamCache,
    private val logger: ResolverLogger,
) {
    private val lock = Any()
    private var job: Job? = null
    private var activeVideoId: String? = null
    private val delivered = LinkedHashSet<String>()

    fun cancel() {
        val toCancel = synchronized(lock) {
            val current = job
            job = null
            activeVideoId = null
            current
        }
        toCancel?.cancel()
    }

    fun enqueue(
        scope: CoroutineScope,
        identity: TrackIdentity,
        durationMs: Long?,
        activePlayback: () -> TrackIdentity?,
        extract: suspend (youtubeVideoId: String) -> YouTubeAudioStream,
        onReady: suspend (StreamCache) -> Unit,
    ): Job? {
        val videoId = identity.youtubeVideoId
        if (videoId.isBlank() || activePlayback()?.youtubeVideoId == videoId) return null

        val wasDelivered = synchronized(lock) {
            if (job?.isActive == true && activeVideoId == videoId) {
                Log.i(TAG, "[PREFETCH_SKIPPED_ALREADY_ACTIVE] youtubeVideoId=$videoId")
                return job
            }
            videoId in delivered
        }
        if (wasDelivered) {
            if (cache.get(identity) != null) {
                Log.i(TAG, "[PREFETCH_SKIPPED_CACHED] youtubeVideoId=$videoId")
                return null
            }
            synchronized(lock) { delivered.remove(videoId) }
        }

        lateinit var launched: Job
        launched = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val started = System.nanoTime()
            try {
                logger.event(ResolveEvent.START, identity, 0L, durationMs, "prefetch")
                val hit = cache.get(identity)
                if (hit != null) {
                    logger.event(
                        ResolveEvent.CACHE_HIT,
                        identity,
                        elapsedSince(started),
                        hit.durationMs ?: durationMs,
                        "prefetch",
                    )
                    ensureActive()
                    if (activePlayback()?.youtubeVideoId == videoId) return@launch
                    onReady(hit)
                    recordDelivered(videoId)
                    Log.i(
                        TAG,
                        "[PREFETCH_COMPLETED] youtubeVideoId=$videoId elapsedMs=${elapsedSince(started)}",
                    )
                    return@launch
                }

                logger.event(ResolveEvent.CACHE_MISS, identity, elapsedSince(started), durationMs, "prefetch")
                val extracted = extract(videoId)
                ensureActive()
                if (extracted.videoId != videoId || extracted.url.isBlank()) {
                    logger.event(
                        ResolveEvent.IGNORED_STALE,
                        identity,
                        elapsedSince(started),
                        durationMs,
                        "resolvedTrackId=${extracted.videoId} blankUrl=${extracted.url.isBlank()}",
                    )
                    return@launch
                }
                if (activePlayback()?.youtubeVideoId == videoId) {
                    logger.event(
                        ResolveEvent.IGNORED_STALE,
                        identity,
                        elapsedSince(started),
                        durationMs,
                        "playback-owns",
                    )
                    return@launch
                }

                val entry = extracted.toStreamCache(identity)
                val stored = if (cache.putIfAbsent(entry)) entry else cache.get(identity) ?: return@launch
                logger.event(
                    ResolveEvent.SUCCESS,
                    identity,
                    elapsedSince(started),
                    stored.durationMs ?: durationMs,
                    "prefetch",
                )
                ensureActive()
                if (activePlayback()?.youtubeVideoId == videoId) return@launch
                onReady(stored)
                recordDelivered(videoId)
                Log.i(
                    TAG,
                    "[PREFETCH_COMPLETED] youtubeVideoId=$videoId elapsedMs=${elapsedSince(started)}",
                )
            } catch (error: Throwable) {
                if (error.isCooperativeCancellation()) {
                    logger.event(
                        ResolveEvent.CANCELLED,
                        identity,
                        elapsedSince(started),
                        durationMs,
                        "prefetch flight-continues",
                    )
                    throw error
                }
                val detail = resolutionFailureDetail(error)
                logger.event(ResolveEvent.FAILED, identity, elapsedSince(started), durationMs, "prefetch $detail")
                Log.w(TAG, "[RESOLVE_FAILED] youtubeVideoId=$videoId $detail")
                if (!error.isExpectedAudioResolutionFailure()) throw error
            } finally {
                synchronized(lock) {
                    if (job === launched) {
                        job = null
                        activeVideoId = null
                    }
                }
            }
        }

        val previousJob = synchronized(lock) {
            if (job?.isActive == true && activeVideoId == videoId) {
                launched.cancel()
                return job
            }
            val prev = job
            job = launched
            activeVideoId = videoId
            prev
        }
        previousJob?.cancel()
        Log.i(TAG, "[PREFETCH_SCHEDULED] youtubeVideoId=$videoId")
        launched.start()
        return launched
    }

    private fun recordDelivered(videoId: String) {
        synchronized(lock) {
            delivered.remove(videoId)
            delivered.add(videoId)
            while (delivered.size > MAX_DELIVERED) {
                val oldest = delivered.iterator().next()
                delivered.remove(oldest)
            }
        }
    }

    private companion object {
        const val TAG = "NeonSongResolver"
        const val MAX_DELIVERED = 128
    }
}
