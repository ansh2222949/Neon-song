package com.neonsong.app.playback.resolve

import android.util.Log
import com.neonsong.app.data.music.YouTubeAudioStream
import com.neonsong.app.data.music.containedAudioResolutionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * One player extract per YouTube video id.
 * A second caller waits on the same job. Cancelling the caller does not
 * cancel the job, so a prefetch that the user skips still writes the URL.
 */
class StreamExtraction(
    parent: CoroutineScope,
    private val cache: PlaybackStreamCache,
    private val extract: suspend (youtubeVideoId: String) -> YouTubeAudioStream,
) {
    // Independent of the caller's job. Cancelling prefetch or playback
    // must not cancel an extract that is about to write the URL.
    // The scope ends when the player scope ends.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + containedAudioResolutionFailure(TAG),
    )

    init {
        parent.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            scope.cancel()
        }
    }
    private val flights = ConcurrentHashMap<String, Deferred<YouTubeAudioStream>>()
    private val lock = Any()

    suspend fun await(youtubeVideoId: String): YouTubeAudioStream {
        val id = youtubeVideoId.trim()
        if (id.isEmpty()) throw IOException("Blank YouTube video id")

        cache.get(TrackIdentity(id, id))?.let { hit ->
            return hit.toYouTubeAudioStream()
        }

        var shouldStart = false
        val flight = synchronized(lock) {
            val current = flights[id]
            if (current != null && current.isActive) {
                Log.i(TAG, "[EXTRACT_JOIN] youtubeVideoId=$id")
                current
            } else {
                val created = scope.async(start = CoroutineStart.LAZY) { execute(id) }
                flights[id] = created
                created.invokeOnCompletion { flights.remove(id, created) }
                shouldStart = true
                created
            }
        }
        if (shouldStart) flight.start()
        return flight.await()
    }

    private suspend fun execute(youtubeVideoId: String): YouTubeAudioStream {
        val started = System.nanoTime()
        Log.i(TAG, "[EXTRACT_START] youtubeVideoId=$youtubeVideoId")
        try {
            val identity = TrackIdentity(youtubeVideoId, youtubeVideoId)
            cache.get(identity)?.let { hit ->
                Log.i(
                    TAG,
                    "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} cached=true",
                )
                return hit.toYouTubeAudioStream()
            }
            val stream = try {
                withTimeout(EXTRACT_TIMEOUT_MS) { extract(youtubeVideoId) }
            } catch (timeout: TimeoutCancellationException) {
                // Convert to IOException inside the flight so awaiting callers retry instead of cancelling.
                throw IOException("Timed out extracting stream for $youtubeVideoId", timeout)
            }
            if (stream.url.isBlank()) {
                throw IOException("Extracted blank stream URL for $youtubeVideoId")
            }
            val matchesRequested = stream.videoId == youtubeVideoId
            if (matchesRequested) {
                cache.putIfAbsent(stream.toStreamCache(identity))
            }
            Log.i(
                TAG,
                "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} " +
                    "cached=$matchesRequested",
            )
            return stream
        } catch (cancelled: CancellationException) {
            Log.i(
                TAG,
                "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} " +
                    "cached=false cancelled=true",
            )
            throw cancelled
        } catch (error: Throwable) {
            Log.i(
                TAG,
                "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} " +
                    "cached=false error=${error::class.java.simpleName}",
            )
            throw error
        }
    }

    private companion object {
        const val TAG = "NeonSongResolver"
        const val EXTRACT_TIMEOUT_MS = 25_000L
    }
}
