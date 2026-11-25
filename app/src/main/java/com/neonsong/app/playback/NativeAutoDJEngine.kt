package com.neonsong.app.playback

import android.util.Log
import java.io.Closeable
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt

enum class TrackLifecycleStage(val nativeValue: Int) {
    QUEUED(0),
    RESOLVING(1),
    RESOLVED(2),
    PREPARING(3),
    READY(4),
    PLAYING(5),
    FINISHED(6),
    WAITING_LATE(7),
    FAILED(8),
    CANCELLED(9);

    companion object {
        private val BY_VALUE = entries.toTypedArray()
        fun fromNative(value: Int): TrackLifecycleStage =
            if (value in BY_VALUE.indices) BY_VALUE[value] else QUEUED
    }
}

enum class AutoDJDecision(val nativeValue: Int) {
    DO_NOTHING(0),
    PREFETCH_NEXT(1),
    PREPARE_STANDBY(2),
    TRIGGER_TRANSITION(3),
    WAIT_FOR_LATE_TRACK(4),
    SKIP_FAILED_TRACK(5),
    REFILL_QUEUE(6);

    companion object {
        private val BY_VALUE = entries.toTypedArray()
        fun fromNative(value: Int): AutoDJDecision =
            if (value in BY_VALUE.indices) BY_VALUE[value] else DO_NOTHING
    }
}

data class AutoDJLatencyStats(
    val meanMs: Float,
    val stdDevMs: Float,
    val leadWindowMs: Float,
)

/**
 * Kotlin owner and JNI bridge for the native C++ AutoDJ engine.
 * Coordinates Mixxx-style predictive prefetching, statistical latency estimation,
 * lifecycle state tracking, and autonomous queue decision-making.
 */
@Singleton
class NativeAutoDJEngine @Inject constructor() : Closeable {
    private val handleLock = ReentrantReadWriteLock()

    @Volatile
    private var nativeHandle = if (NativeAudioEngine.libraryLoaded) {
        try {
            nativeCreate(maxRetries = DEFAULT_MAX_RETRIES, refillThreshold = DEFAULT_REFILL_THRESHOLD)
        } catch (error: LinkageError) {
            Log.e(TAG, "Native AutoDJ engine unavailable", error)
            0L
        }
    } else {
        0L
    }

    val isAvailable: Boolean
        get() = nativeHandle != 0L

    // Pure Kotlin fallback state if native library is unavailable
    private val fallbackLock = Any()
    private var fallbackMeanMs = 2500.0
    private var fallbackVarianceMs = 1000.0 * 1000.0

    fun recordLatency(latencyMs: Long, wasSuccess: Boolean) {
        val safeLatency = latencyMs.coerceAtLeast(0L)
        if (nativeHandle != 0L) {
            withHandle(Unit) { nativeRecordLatency(it, safeLatency, wasSuccess) }
        } else {
            synchronized(fallbackLock) {
                if (!wasSuccess) {
                    fallbackMeanMs = (fallbackMeanMs * 1.15).coerceAtMost(15000.0)
                    fallbackVarianceMs = (fallbackVarianceMs * 1.25).coerceAtMost(25000000.0)
                } else {
                    val alpha = 0.2
                    val diff = safeLatency.toDouble() - fallbackMeanMs
                    fallbackMeanMs += alpha * diff
                    fallbackVarianceMs = (1.0 - alpha) * fallbackVarianceMs + alpha * (diff * diff)
                }
            }
        }
    }

    fun evaluate(
        currentPositionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        transitionDurationMs: Long,
        nextStage: TrackLifecycleStage,
        retryCount: Int,
        unplayedCount: Int,
    ): AutoDJDecision {
        if (nativeHandle != 0L) {
            val decisionInt = withHandle(AutoDJDecision.DO_NOTHING.nativeValue) {
                nativeEvaluate(
                    it,
                    currentPositionMs,
                    durationMs,
                    isPlaying,
                    transitionDurationMs,
                    nextStage.nativeValue,
                    retryCount,
                    unplayedCount,
                )
            }
            return AutoDJDecision.fromNative(decisionInt)
        }

        // Fallback pure Kotlin evaluation (matches C++ AutoDJProcessor priority)
        // 1. Terminal failure handling
        if (nextStage == TrackLifecycleStage.FAILED) {
            return if (retryCount >= DEFAULT_MAX_RETRIES) {
                AutoDJDecision.SKIP_FAILED_TRACK
            } else {
                AutoDJDecision.PREFETCH_NEXT
            }
        }

        val safePosMs = max(0L, currentPositionMs)
        val safeTransitionMs = max(0L, transitionDurationMs)
        val remainingMs = if (durationMs > 0L) max(0L, durationMs - safePosMs) else 0L

        // 2. Unknown duration or live stream
        if (durationMs <= 0L) {
            if (nextStage == TrackLifecycleStage.QUEUED) return AutoDJDecision.PREFETCH_NEXT
            if (nextStage == TrackLifecycleStage.RESOLVED) return AutoDJDecision.PREPARE_STANDBY
            if (unplayedCount <= DEFAULT_REFILL_THRESHOLD) return AutoDJDecision.REFILL_QUEUE
            return AutoDJDecision.DO_NOTHING
        }

        // 3. Track has reached or exceeded its duration
        if (remainingMs <= 0L) {
            if (nextStage == TrackLifecycleStage.READY) return AutoDJDecision.TRIGGER_TRANSITION
            if (nextStage == TrackLifecycleStage.QUEUED) return AutoDJDecision.PREFETCH_NEXT
            if (nextStage == TrackLifecycleStage.RESOLVED) return AutoDJDecision.PREPARE_STANDBY
            if (nextStage == TrackLifecycleStage.RESOLVING ||
                nextStage == TrackLifecycleStage.PREPARING ||
                nextStage == TrackLifecycleStage.WAITING_LATE) {
                return AutoDJDecision.WAIT_FOR_LATE_TRACK
            }
            if (unplayedCount <= DEFAULT_REFILL_THRESHOLD) return AutoDJDecision.REFILL_QUEUE
            return AutoDJDecision.DO_NOTHING
        }

        // 4. Normal active playback progression
        if (isPlaying) {
            if (remainingMs <= safeTransitionMs) {
                if (nextStage == TrackLifecycleStage.READY) return AutoDJDecision.TRIGGER_TRANSITION
                if (nextStage == TrackLifecycleStage.RESOLVING ||
                    nextStage == TrackLifecycleStage.PREPARING ||
                    nextStage == TrackLifecycleStage.WAITING_LATE) {
                    return AutoDJDecision.WAIT_FOR_LATE_TRACK
                }
            }
            if (nextStage == TrackLifecycleStage.QUEUED || nextStage == TrackLifecycleStage.RESOLVED) {
                val leadWindowMs = computeFallbackLeadWindowMs(safeTransitionMs)
                if (remainingMs <= leadWindowMs) {
                    return if (nextStage == TrackLifecycleStage.QUEUED) {
                        AutoDJDecision.PREFETCH_NEXT
                    } else {
                        AutoDJDecision.PREPARE_STANDBY
                    }
                }
            }
        }

        // 5. Refill check: if queue is running low in second half or last 60s
        if (unplayedCount <= DEFAULT_REFILL_THRESHOLD &&
            (safePosMs >= (durationMs / 2) || remainingMs <= 60000L)) {
            return AutoDJDecision.REFILL_QUEUE
        }

        return AutoDJDecision.DO_NOTHING
    }

    private fun computeFallbackLeadWindowMs(transitionDurationMs: Long): Long {
        val (mean, variance) = synchronized(fallbackLock) {
            fallbackMeanMs to fallbackVarianceMs
        }
        val stdDev = sqrt(max(0.0, variance))
        val lead = transitionDurationMs.toDouble() + mean + (2.5 * stdDev) + 5000.0
        return lead.coerceIn(15000.0, 60000.0).toLong()
    }

    fun getLatencyStats(transitionDurationMs: Long): AutoDJLatencyStats {
        val safeTransitionMs = max(0L, transitionDurationMs)
        if (nativeHandle != 0L) {
            val stats = withHandle(null) {
                nativeGetLatencyStats(it, safeTransitionMs)
            }
            if (stats != null && stats.size >= 3) {
                return AutoDJLatencyStats(stats[0], stats[1], stats[2])
            }
        }
        val (mean, variance) = synchronized(fallbackLock) {
            fallbackMeanMs to fallbackVarianceMs
        }
        val stdDev = sqrt(max(0.0, variance)).toFloat()
        val lead = (safeTransitionMs.toDouble() + mean + (2.5 * stdDev) + 5000.0)
            .coerceIn(15000.0, 60000.0).toFloat()
        return AutoDJLatencyStats(mean.toFloat(), stdDev, lead)
    }

    override fun close() {
        val writeLock = handleLock.writeLock()
        writeLock.lock()
        val handleToDestroy = try {
            val handle = nativeHandle
            nativeHandle = 0L
            handle
        } finally {
            writeLock.unlock()
        }
        if (handleToDestroy != 0L) {
            try {
                nativeDestroy(handleToDestroy)
            } catch (error: Throwable) {
                Log.e(TAG, "Native AutoDJ destroy failed", error)
            }
        }
    }

    private inline fun <T> withHandle(fallback: T, block: (Long) -> T): T {
        val readLock = handleLock.readLock()
        readLock.lock()
        return try {
            val handle = nativeHandle
            if (handle != 0L) {
                block(handle)
            } else {
                fallback
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Native AutoDJ call failed", error)
            fallback
        } finally {
            readLock.unlock()
        }
    }

    companion object {
        private const val TAG = "NativeAutoDJEngine"
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_REFILL_THRESHOLD = 2

        @JvmStatic
        private external fun nativeCreate(maxRetries: Int, refillThreshold: Int): Long

        @JvmStatic
        private external fun nativeDestroy(handle: Long)

        @JvmStatic
        private external fun nativeRecordLatency(handle: Long, latencyMs: Long, wasSuccess: Boolean)

        @JvmStatic
        private external fun nativeEvaluate(
            handle: Long,
            currentPositionMs: Long,
            durationMs: Long,
            isPlaying: Boolean,
            transitionDurationMs: Long,
            nextStage: Int,
            retryCount: Int,
            unplayedCount: Int,
        ): Int

        @JvmStatic
        private external fun nativeGetLatencyStats(handle: Long, transitionDurationMs: Long): FloatArray
    }
}
