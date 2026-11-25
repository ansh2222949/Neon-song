package com.neonsong.app.ui.player

import android.os.SystemClock
import kotlin.math.abs

enum class SyncAction {
    IN_SYNC,
    MICRO_ACCELERATE,
    MICRO_DECELERATE,
    HARD_SEEK,
}

data class SyncDecision(
    val action: SyncAction,
    val targetSpeed: Float,
    val seekTargetMs: Long? = null,
    val smoothedDriftMs: Float = 0f,
) {
    val isLocked: Boolean
        get() = action == SyncAction.IN_SYNC || abs(smoothedDriftMs) <= 32f
}

/**
 * Isolated per-player Phase-Locked Loop (PLL) sync controller.
 * Keeps video frames locked to the audible master audio clock within sub-frame accuracy (<16ms)
 * without cross-talk between Dialog and inline Surface instances.
 */
class SyncedVideoClockController {
    private var smoothedDriftMs: Float = 0f
    private var integralErrorMs: Float = 0f
    private var lastAnchorAudioMs: Long = -1L
    private var lastAnchorWallMs: Long = 0L
    private var lastDriftSign: Int = 0

    fun reset() {
        smoothedDriftMs = 0f
        integralErrorMs = 0f
        lastAnchorAudioMs = -1L
        lastAnchorWallMs = 0L
        lastDriftSign = 0
    }

    /**
     * Extrapolates exact sub-millisecond audible timestamp between discrete player ticks
     * and subtracts audio hardware output latency so video hits the display when sound reaches your ears.
     */
    fun getAudibleAudioTimestampMs(
        rawAudioPosMs: Long,
        isPlaying: Boolean,
        speed: Float,
        presentationLatencyMs: Long = 0L,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Long {
        if (rawAudioPosMs != lastAnchorAudioMs || !isPlaying) {
            lastAnchorAudioMs = rawAudioPosMs
            lastAnchorWallMs = nowElapsedMs
        }
        val extrapolated = if (isPlaying && lastAnchorWallMs > 0L) {
            val elapsed = (nowElapsedMs - lastAnchorWallMs).coerceIn(0L, 180L)
            rawAudioPosMs + (elapsed * speed.coerceIn(0.25f, 3.0f)).toLong()
        } else {
            rawAudioPosMs
        }
        return (extrapolated - presentationLatencyMs.coerceIn(0L, 450L)).coerceAtLeast(0L)
    }

    fun evaluateSync(
        videoPosMs: Long,
        audioPosMs: Long,
        baseSpeed: Float = 1.0f,
        allowHardSeek: Boolean = true,
        isPlaying: Boolean = true,
        videoOffsetMs: Long = 0L,
        videoDurationMs: Long = 0L,
    ): SyncDecision {
        val safeBaseSpeed = baseSpeed.coerceIn(0.25f, 3.0f)
        val rawTargetMs = (audioPosMs + videoOffsetMs).coerceAtLeast(0L)
        val targetVideoPosMs = if (videoDurationMs > 500L) {
            rawTargetMs.coerceAtMost(videoDurationMs - 250L)
        } else {
            rawTargetMs
        }
        val rawDrift = (videoPosMs - targetVideoPosMs).toFloat()

        // If paused, snap only if noticeably off so paused frame matches exact audio position
        if (!isPlaying) {
            smoothedDriftMs = rawDrift
            integralErrorMs = 0f
            return if (abs(rawDrift) > 120f && allowHardSeek) {
                SyncDecision(
                    action = SyncAction.HARD_SEEK,
                    targetSpeed = safeBaseSpeed,
                    seekTargetMs = targetVideoPosMs,
                    smoothedDriftMs = 0f,
                )
            } else {
                SyncDecision(
                    action = SyncAction.IN_SYNC,
                    targetSpeed = safeBaseSpeed,
                    seekTargetMs = null,
                    smoothedDriftMs = rawDrift,
                )
            }
        }

        // Adaptive EMA: snappy on sudden drift jumps, smooth when near lock to filter ExoPlayer tick jitter
        val jump = abs(rawDrift - smoothedDriftMs)
        smoothedDriftMs = when {
            jump > SyncedVideoClockSync.HARD_SEEK_THRESHOLD_MS -> rawDrift
            jump > SyncedVideoClockSync.FINE_ZONE_MS -> smoothedDriftMs + 0.55f * (rawDrift - smoothedDriftMs)
            else -> smoothedDriftMs + 0.36f * (rawDrift - smoothedDriftMs)
        }

        val absDrift = abs(smoothedDriftMs)

        // Zero-crossing anti-windup: clear integral term if drift crossed zero to prevent oscillation
        val currentSign = when {
            smoothedDriftMs > 1f -> 1
            smoothedDriftMs < -1f -> -1
            else -> 0
        }
        if (currentSign != 0 && lastDriftSign != 0 && currentSign != lastDriftSign) {
            integralErrorMs = 0f
        }
        if (currentSign != 0) lastDriftSign = currentSign

        if (absDrift <= SyncedVideoClockSync.DEADBAND_MS) {
            integralErrorMs *= 0.45f
            return SyncDecision(
                action = SyncAction.IN_SYNC,
                targetSpeed = safeBaseSpeed,
                seekTargetMs = null,
                smoothedDriftMs = smoothedDriftMs,
            )
        }

        if (absDrift > SyncedVideoClockSync.HARD_SEEK_THRESHOLD_MS && allowHardSeek) {
            smoothedDriftMs = 0f
            integralErrorMs = 0f
            lastDriftSign = 0
            val decodeLead = (SyncedVideoClockSync.SEEK_DECODE_LEAD_MS * safeBaseSpeed).toLong()
            val rawSeek = (targetVideoPosMs + decodeLead).coerceAtLeast(0L)
            val clampedSeek = if (videoDurationMs > 500L) rawSeek.coerceAtMost(videoDurationMs - 250L) else rawSeek
            return SyncDecision(
                action = SyncAction.HARD_SEEK,
                targetSpeed = safeBaseSpeed,
                seekTargetMs = clampedSeek,
                smoothedDriftMs = 0f,
            )
        }

        // Proportional-Integral speed modulation: imperceptible lip-sync lock without frame drops
        integralErrorMs = (integralErrorMs + smoothedDriftMs * 0.08f).coerceIn(-120f, 120f)
        val iTerm = abs(integralErrorMs) * SyncedVideoClockSync.INTEGRAL_GAIN

        val correction = if (absDrift <= SyncedVideoClockSync.FINE_ZONE_MS) {
            val excess = absDrift - SyncedVideoClockSync.DEADBAND_MS
            (SyncedVideoClockSync.MIN_SPEED_CORRECTION + excess * SyncedVideoClockSync.FINE_GAIN + iTerm)
                .coerceIn(SyncedVideoClockSync.MIN_SPEED_CORRECTION, SyncedVideoClockSync.MAX_FINE_CORRECTION)
        } else {
            val maxCap = if (allowHardSeek) {
                SyncedVideoClockSync.MAX_CATCHUP_CORRECTION
            } else {
                SyncedVideoClockSync.COOLDOWN_MAX_CORRECTION
            }
            val excess = absDrift - SyncedVideoClockSync.FINE_ZONE_MS
            (SyncedVideoClockSync.MAX_FINE_CORRECTION + excess * SyncedVideoClockSync.CATCHUP_GAIN + iTerm)
                .coerceIn(SyncedVideoClockSync.MAX_FINE_CORRECTION, maxCap)
        }

        return if (smoothedDriftMs < 0f) {
            SyncDecision(
                action = SyncAction.MICRO_ACCELERATE,
                targetSpeed = safeBaseSpeed * (1f + correction),
                seekTargetMs = null,
                smoothedDriftMs = smoothedDriftMs,
            )
        } else {
            SyncDecision(
                action = SyncAction.MICRO_DECELERATE,
                targetSpeed = (safeBaseSpeed * (1f - correction)).coerceAtLeast(0.25f),
                seekTargetMs = null,
                smoothedDriftMs = smoothedDriftMs,
            )
        }
    }
}

object SyncedVideoClockSync {
    const val DEADBAND_MS = 14L
    const val FINE_ZONE_MS = 75L
    const val HARD_SEEK_THRESHOLD_MS = 280L
    const val SEEK_COOLDOWN_MS = 1_100L

    internal const val SEEK_DECODE_LEAD_MS = 65L
    internal const val FINE_GAIN = 0.00028f
    internal const val CATCHUP_GAIN = 0.00038f
    internal const val INTEGRAL_GAIN = 0.00005f
    internal const val MIN_SPEED_CORRECTION = 0.003f
    internal const val MAX_FINE_CORRECTION = 0.025f
    internal const val MAX_CATCHUP_CORRECTION = 0.095f
    internal const val COOLDOWN_MAX_CORRECTION = 0.14f

    private val defaultController = SyncedVideoClockController()

    fun reset() = defaultController.reset()

    fun calculateDrift(videoPosMs: Long, audioPosMs: Long): Long = videoPosMs - audioPosMs

    fun getAudibleAudioTimestampMs(
        rawAudioPosMs: Long,
        isPlaying: Boolean,
        speed: Float,
        presentationLatencyMs: Long = 0L,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Long = defaultController.getAudibleAudioTimestampMs(
        rawAudioPosMs = rawAudioPosMs,
        isPlaying = isPlaying,
        speed = speed,
        presentationLatencyMs = presentationLatencyMs,
        nowElapsedMs = nowElapsedMs,
    )

    /**
     * Suggests a likely MV intro offset when an Official Music Video is 1.8s..12s longer than the studio track.
     */
    fun estimateIntroOffsetMs(
        audioDurationMs: Long,
        videoDurationMs: Long,
        manualOffsetMs: Long = 0L,
    ): Long {
        if (manualOffsetMs != 0L) return manualOffsetMs
        if (audioDurationMs <= 10_000L || videoDurationMs <= 10_000L) return 0L
        val diffMs = videoDurationMs - audioDurationMs
        return if (diffMs in 1_800L..12_000L) diffMs else 0L
    }

    fun evaluateSync(
        videoPosMs: Long,
        audioPosMs: Long,
        baseSpeed: Float = 1.0f,
        allowHardSeek: Boolean = true,
        isPlaying: Boolean = true,
        videoOffsetMs: Long = 0L,
    ): SyncDecision = defaultController.evaluateSync(
        videoPosMs = videoPosMs,
        audioPosMs = audioPosMs,
        baseSpeed = baseSpeed,
        allowHardSeek = allowHardSeek,
        isPlaying = isPlaying,
        videoOffsetMs = videoOffsetMs,
    )
}
