package com.neonsong.app.playback.autodj

import android.os.SystemClock
import android.util.Log
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.data.music.isCooperativeCancellation
import com.neonsong.app.data.music.resolutionFailureDetail
import com.neonsong.app.playback.AutoDJDecision
import com.neonsong.app.playback.AutoDJLatencyStats
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.NativeAutoDJEngine
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.TrackLifecycleStage
import com.neonsong.app.playback.mediaIdKey
import com.neonsong.app.playback.resolve.StreamResolveCoordinator
import com.neonsong.app.playback.resolve.playbackIdentity
import com.neonsong.app.playback.toPlayerStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Host interface implemented by [com.neonsong.app.playback.MusicPlayer] to decouple
 * player mechanics from Auto DJ queue evaluation.
 */
interface AutoDJPlaybackHost {
    fun getTrackAt(index: Int): PlayableTrack?
    fun getQueueSize(): Int
    fun getCurrentIndex(): Int
    fun getNextQueueIndex(): Int = getCurrentIndex() + 1
    fun isCurrentItem(index: Int): Boolean
    suspend fun resolvePreferredStream(track: PlayableTrack): MusicPlayer.ResolvedStream? = null
    fun registerResolvedStream(index: Int, track: PlayableTrack, stream: MusicPlayer.ResolvedStream)
    fun armStandbyForNext(index: Int, track: PlayableTrack)
    fun triggerAutoTransition(overlapMs: Long)
    fun setLateTrackBuffering(isWaiting: Boolean)
    fun skipToNextPlayableItem(failedIndex: Int)
    fun requestQueueRefill()
    fun isStandbyReady(index: Int): Boolean
}

sealed interface AutoDJStatus {
    object Disabled : AutoDJStatus
    object Idle : AutoDJStatus
    data class Resolving(val title: String, val index: Int, val attempt: Int) : AutoDJStatus
    data class Ready(val title: String, val index: Int) : AutoDJStatus
    data class WaitingLate(val title: String, val index: Int) : AutoDJStatus
}

/**
 * High-performance autonomous queue coordinator based on Mixxx AutoDJ architecture.
 * Evaluates queue readiness, manages multi-stage track resolution lifecycle,
 * and tracks predictive resolution latency.
 */
@Singleton
class AutoDJController @Inject constructor(
    private val nativeEngine: NativeAutoDJEngine,
    private val settingsPreferences: SettingsPreferences,
    val autoDJEngine: AutoDJEngine = AutoDJEngine(),
) {
    private var host: AutoDJPlaybackHost? = null
    private var scope: CoroutineScope? = null
    private var streamResolver: StreamResolveCoordinator? = null

    private var settingsCollectorJob: Job? = null

    private val trackStages = ConcurrentHashMap<String, TrackLifecycleStage>()
    private val retryCounts = ConcurrentHashMap<String, Int>()
    private val inFlightJobs = ConcurrentHashMap<String, Job>()

    private val _status = MutableStateFlow<AutoDJStatus>(AutoDJStatus.Idle)
    val status: StateFlow<AutoDJStatus> = _status.asStateFlow()

    @Volatile
    var isEnabled: Boolean = true
        private set

    fun setEnabledDirect(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            cancelAllPrefetches("autodj-disabled")
            _status.value = AutoDJStatus.Disabled
        } else if (_status.value is AutoDJStatus.Disabled) {
            _status.value = AutoDJStatus.Idle
        }
    }

    fun attachHost(
        host: AutoDJPlaybackHost,
        scope: CoroutineScope,
        streamResolver: StreamResolveCoordinator,
    ) {
        this.host = host
        this.scope = scope
        this.streamResolver = streamResolver
        settingsCollectorJob?.cancel()
        settingsCollectorJob = scope.launch(Dispatchers.Main.immediate) {
            settingsPreferences.settings.collect { settings ->
                isEnabled = settings.autoDJEnabled
                if (!isEnabled) {
                    cancelAllPrefetches("autodj-disabled")
                    _status.value = AutoDJStatus.Disabled
                } else if (_status.value is AutoDJStatus.Disabled) {
                    _status.value = AutoDJStatus.Idle
                }
            }
        }
    }

    fun detachHost() {
        cancelAllPrefetches("host-detached")
        settingsCollectorJob?.cancel()
        settingsCollectorJob = null
        this.host = null
        this.scope = null
        this.streamResolver = null
    }

    fun getTrackStage(trackId: String, nextIndex: Int = -1): TrackLifecycleStage {
        val base = trackStages[trackId] ?: TrackLifecycleStage.QUEUED
        if (base == TrackLifecycleStage.PREPARING && nextIndex >= 0 && host?.isStandbyReady(nextIndex) == true) {
            trackStages[trackId] = TrackLifecycleStage.READY
            return TrackLifecycleStage.READY
        }
        return base
    }

    fun setTrackStage(trackId: String, stage: TrackLifecycleStage) {
        trackStages[trackId] = stage
    }

    fun onQueueChanged() {
        // Clear cached stages for items no longer in queue to avoid memory leaks
        val h = host ?: return
        val currentKeys = HashSet<String>()
        val queueSize = h.getQueueSize()
        for (i in 0 until queueSize) {
            h.getTrackAt(i)?.mediaIdKey()?.let { currentKeys.add(it) }
        }
        val iterator = trackStages.keys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in currentKeys) {
                inFlightJobs.remove(key)?.cancel()
                iterator.remove()
                retryCounts.remove(key)
            }
        }
        prefetchUpcoming(h.getCurrentIndex())
    }

    fun onTrackTransition(newIndex: Int) {
        val h = host ?: return
        val newTrack = h.getTrackAt(newIndex)
        val newKey = newTrack?.mediaIdKey()
        if (newKey != null) {
            trackStages[newKey] = TrackLifecycleStage.PLAYING
        }
        _status.value = AutoDJStatus.Idle
        prefetchUpcoming(newIndex)
    }

    fun getTransitionPlan(current: PlayableTrack, incoming: PlayableTrack): AutoDJTransitionPlan {
        return autoDJEngine.planTransition(current, incoming)
    }

    fun optimizeQueue(current: PlayableTrack, upcoming: List<PlayableTrack>): List<PlayableTrack> {
        return autoDJEngine.optimizeQueueHarmonically(current, upcoming)
    }

    fun cancelAllPrefetches(reason: String) {
        inFlightJobs.values.forEach { it.cancel() }
        inFlightJobs.clear()
        Log.d(TAG, "[CANCEL_ALL_PREFETCHES] reason=$reason")
    }

    /**
     * Periodic evaluation tick driven by playback ticker (every 60-250ms).
     */
    fun onPlaybackTick(
        currentPositionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        transitionDurationMs: Long,
    ) {
        if (!isEnabled) return
        val h = host ?: return
        val currentIndex = h.getCurrentIndex()
        val queueSize = h.getQueueSize()
        if (currentIndex < 0 || queueSize <= 0) return

        val nextIndex = h.getNextQueueIndex()
        val unplayedCount = maxOf(0, queueSize - currentIndex - 1)
        val nextTrack = if (nextIndex in 0 until queueSize) h.getTrackAt(nextIndex) else null
        val nextKey = nextTrack?.mediaIdKey() ?: ""
        val nextStage = if (nextKey.isNotEmpty()) getTrackStage(nextKey, nextIndex) else TrackLifecycleStage.FINISHED
        val retryCount = if (nextKey.isNotEmpty()) retryCounts[nextKey] ?: 0 else 0

        val decision = nativeEngine.evaluate(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            transitionDurationMs = transitionDurationMs,
            nextStage = nextStage,
            retryCount = retryCount,
            unplayedCount = unplayedCount,
        )

        when (decision) {
            AutoDJDecision.DO_NOTHING -> {
                // Keep status in sync
                if (nextTrack != null) {
                    when (nextStage) {
                        TrackLifecycleStage.RESOLVED, TrackLifecycleStage.PREPARING, TrackLifecycleStage.READY -> {
                            _status.value = AutoDJStatus.Ready(nextTrack.title, nextIndex)
                        }
                        TrackLifecycleStage.RESOLVING -> {
                            _status.value = AutoDJStatus.Resolving(nextTrack.title, nextIndex, retryCount + 1)
                        }
                        else -> {
                            if (_status.value !is AutoDJStatus.Idle) _status.value = AutoDJStatus.Idle
                        }
                    }
                }
            }

            AutoDJDecision.PREFETCH_NEXT -> {
                if (nextTrack != null && nextIndex in 0 until queueSize) {
                    prefetchTrack(nextIndex, nextTrack)
                }
            }

            AutoDJDecision.PREPARE_STANDBY -> {
                if (nextTrack != null && nextIndex in 0 until queueSize) {
                    trackStages[nextKey] = TrackLifecycleStage.PREPARING
                    h.armStandbyForNext(nextIndex, nextTrack)
                    _status.value = AutoDJStatus.Ready(nextTrack.title, nextIndex)
                }
            }

            AutoDJDecision.TRIGGER_TRANSITION -> {
                h.setLateTrackBuffering(false)
                h.triggerAutoTransition(transitionDurationMs)
            }

            AutoDJDecision.WAIT_FOR_LATE_TRACK -> {
                // Outgoing track finished or at transition edge, but incoming is still in flight
                if (nextTrack != null) {
                    Log.i(TAG, "[WAIT_FOR_LATE_TRACK] Holding for '${nextTrack.title}' (stage=$nextStage)")
                    _status.value = AutoDJStatus.WaitingLate(nextTrack.title, nextIndex)
                    h.setLateTrackBuffering(true)
                }
            }

            AutoDJDecision.SKIP_FAILED_TRACK -> {
                Log.w(TAG, "[SKIP_FAILED_TRACK] Track at index $nextIndex permanently failed ($retryCount retries)")
                h.setLateTrackBuffering(false)
                h.skipToNextPlayableItem(nextIndex)
            }

            AutoDJDecision.REFILL_QUEUE -> {
                Log.i(TAG, "[REFILL_QUEUE] Unplayed tracks running low ($unplayedCount remaining); requesting refill")
                h.requestQueueRefill()
            }
        }
    }

    private fun prefetchUpcoming(currentIndex: Int) {
        if (!isEnabled || currentIndex < 0) return
        val h = host ?: return
        val nextIndex = h.getNextQueueIndex()
        if (nextIndex !in 0 until h.getQueueSize()) return
        val nextTrack = h.getTrackAt(nextIndex) ?: return
        when (getTrackStage(nextTrack.mediaIdKey(), nextIndex)) {
            TrackLifecycleStage.QUEUED,
            TrackLifecycleStage.FAILED,
            TrackLifecycleStage.CANCELLED -> prefetchTrack(nextIndex, nextTrack)
            else -> prefetchWarmUpcoming(nextIndex + 1, WARM_TRACKS_AHEAD)
        }
    }

    private fun prefetchTrack(index: Int, track: PlayableTrack, warmTracksAhead: Int = WARM_TRACKS_AHEAD) {
        val key = track.mediaIdKey()
        if (inFlightJobs[key]?.isActive == true) return
        val currentStage = getTrackStage(key)
        if (currentStage == TrackLifecycleStage.READY ||
            currentStage == TrackLifecycleStage.PLAYING ||
            currentStage == TrackLifecycleStage.RESOLVED ||
            currentStage == TrackLifecycleStage.RESOLVING ||
            currentStage == TrackLifecycleStage.PREPARING
        ) return

        val coroutineScope = scope ?: return
        val attempt = (retryCounts[key] ?: 0) + 1
        trackStages[key] = TrackLifecycleStage.RESOLVING
        _status.value = AutoDJStatus.Resolving(track.title, index, attempt)

        val resolver = streamResolver ?: return
        val job = coroutineScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val startMs = SystemClock.elapsedRealtime()
            var resolverJob: Job? = null
            try {
                Log.d(TAG, "[PREFETCH_START] index=$index title='${track.title}' attempt=$attempt")
                val playbackHost = host ?: return@launch

                fun isFresh(candidate: MusicPlayer.ResolvedStream?): Boolean {
                    if (candidate == null) return false
                    val expiry = candidate.expiresAtEpochMs ?: candidate.youtubeCandidate?.expiresAtEpochMs
                    return expiry == null || expiry - System.currentTimeMillis() > MusicPlayer.RESOLVED_URL_EXPIRY_MARGIN_MS
                }

                var anyRegistered = false
                coroutineScope {
                    // 1. Parallel mein Lossless / Preferred stream dhoondhein
                    val preferredDeferred = async(Dispatchers.IO) {
                        try {
                            playbackHost.resolvePreferredStream(track)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Log.w(TAG, "[PREFETCH_PREFERRED_FAILED] index=$index title='${track.title}': ${resolutionFailureDetail(error)}")
                            null
                        }
                    }

                    // 2. Bina wait kiye turant fast YouTube stream resolve karein
                    val ytStream = runCatching {
                        val identity = track.playbackIdentity()
                            ?: throw java.io.IOException("No playable stream identity for '${track.title}'")
                        val cached = resolver.getPreResolved(identity)
                        if (cached != null) {
                            cached.toPlayerStream()
                        } else {
                            var delivered: MusicPlayer.ResolvedStream? = null
                            resolverJob = resolver.prefetchNext(identity, track.durationMs) { cacheEntry ->
                                delivered = cacheEntry.toPlayerStream()
                            }
                            resolverJob?.join()
                            delivered ?: resolver.getPreResolved(identity)?.toPlayerStream()
                        }
                    }.getOrNull()

                    // Agar fast YouTube stream mil gayi, to turant register kar dein taaki lag na ho!
                    if (isFresh(ytStream) && ytStream != null) {
                        currentCoroutineContext().ensureActive()
                        nativeEngine.recordLatency(SystemClock.elapsedRealtime() - startMs, wasSuccess = true)
                        trackStages[key] = TrackLifecycleStage.RESOLVED
                        _status.value = AutoDJStatus.Ready(track.title, index)
                        Log.i(TAG, "[PREFETCH_SUCCESS_FAST] index=$index title='${track.title}' codec=${ytStream.audioCodec}")
                        playbackHost.registerResolvedStream(index, track, ytStream)
                        anyRegistered = true
                    }

                    // 3. Ab Lossless stream ka result check karein; agar mil gayi to upgrade kar dein
                    val preferredStream = preferredDeferred.await()
                    if (isFresh(preferredStream) && preferredStream != null) {
                        currentCoroutineContext().ensureActive()
                        if (!anyRegistered) {
                            nativeEngine.recordLatency(SystemClock.elapsedRealtime() - startMs, wasSuccess = true)
                        }
                        trackStages[key] = TrackLifecycleStage.RESOLVED
                        _status.value = AutoDJStatus.Ready(track.title, index)
                        Log.i(TAG, "[PREFETCH_SUCCESS_PREFERRED] index=$index title='${track.title}' codec=${preferredStream.audioCodec}")
                        playbackHost.registerResolvedStream(index, track, preferredStream)
                        anyRegistered = true
                    }
                }

                if (!anyRegistered) {
                    throw java.io.IOException("No valid stream resolved for '${track.title}'")
                }
                prefetchWarmUpcoming(index + 1, warmTracksAhead)
            } catch (error: Throwable) {
                if (error.isCooperativeCancellation()) {
                    resolverJob?.cancel()
                    trackStages[key] = TrackLifecycleStage.CANCELLED
                    throw error
                }
                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                nativeEngine.recordLatency(elapsedMs, wasSuccess = false)
                retryCounts[key] = attempt
                trackStages[key] = TrackLifecycleStage.FAILED
                val detail = resolutionFailureDetail(error)
                Log.w(TAG, "[PREFETCH_FAILED] index=$index title='${track.title}' attempt=$attempt: $detail")
            } finally {
                inFlightJobs.remove(key)
            }
        }
        inFlightJobs[key] = job
        job.start()
    }

    private fun prefetchWarmUpcoming(upcomingIndex: Int, remainingTracks: Int) {
        if (remainingTracks <= 0) return
        val h = host ?: return
        if (upcomingIndex !in 0 until h.getQueueSize()) return
        val upcomingTrack = h.getTrackAt(upcomingIndex) ?: return
        val upcomingKey = upcomingTrack.mediaIdKey()
        val nextWarmTracks = remainingTracks - 1
        when (getTrackStage(upcomingKey)) {
            TrackLifecycleStage.QUEUED,
            TrackLifecycleStage.FAILED,
            TrackLifecycleStage.CANCELLED -> prefetchTrack(upcomingIndex, upcomingTrack, nextWarmTracks)
            else -> prefetchWarmUpcoming(upcomingIndex + 1, nextWarmTracks)
        }
    }

    fun getLatencyStats(transitionDurationMs: Long): AutoDJLatencyStats {
        return nativeEngine.getLatencyStats(transitionDurationMs)
    }

    companion object {
        private const val TAG = "AutoDJController"
        private const val WARM_TRACKS_AHEAD = 2
    }
}
