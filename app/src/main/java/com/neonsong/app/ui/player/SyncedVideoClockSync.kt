package com.neonsong.app.ui.player

import kotlin.math.abs

enum class SyncAction {
    /** Drift is within human lip-sync perception threshold (<= 35ms). */
    IN_SYNC,
    /** Video is behind the master audio clock. Speed up video smoothly. */
    MICRO_ACCELERATE,
    /** Video is ahead of the master audio clock. Slow down video smoothly. */
    MICRO_DECELERATE,
    /** Drift exceeds 420ms. Perform a rate-limited hard seek to snap immediately. */
    HARD_SEEK,
}

data class SyncDecision(
    val action: SyncAction,
    val targetSpeed: Float,
    val seekTargetMs: Long? = null,
)

/**
 * Two-Zone Phase-Locked Loop (PLL) evaluator for aligning muted video presentation
 * timestamps (PTS) to the master [com.neonsong.app.playback.MusicPlayer] audio clock.
 */
object SyncedVideoClockSync {
    const val DEADBAND_MS = 35L
    const val FINE_ZONE_MS = 120L
    const val HARD_SEEK_THRESHOLD_MS = 420L
    const val SEEK_COOLDOWN_MS = 1_800L

    private const val FINE_GAIN = 0.00018f
    private const val CATCHUP_GAIN = 0.00022f
    private const val MIN_SPEED_CORRECTION = 0.004f
    private const val MAX_FINE_CORRECTION = 0.020f
    private const val MAX_CATCHUP_CORRECTION = 0.085f
    private const val COOLDOWN_MAX_CORRECTION = 0.12f

    fun calculateDrift(videoPosMs: Long, audioPosMs: Long): Long = videoPosMs - audioPosMs

    fun evaluateSync(
        videoPosMs: Long,
        audioPosMs: Long,
        baseSpeed: Float = 1.0f,
        allowHardSeek: Boolean = true,
    ): SyncDecision {
        val safeBaseSpeed = baseSpeed.coerceIn(0.25f, 3.0f)
        val drift = calculateDrift(videoPosMs, audioPosMs)
        val absDrift = abs(drift)

        if (absDrift <= DEADBAND_MS) {
            return SyncDecision(
                action = SyncAction.IN_SYNC,
                targetSpeed = safeBaseSpeed,
                seekTargetMs = null,
            )
        }

        if (absDrift > HARD_SEEK_THRESHOLD_MS && allowHardSeek) {
            return SyncDecision(
                action = SyncAction.HARD_SEEK,
                targetSpeed = safeBaseSpeed,
                seekTargetMs = audioPosMs.coerceAtLeast(0L),
            )
        }

        val correction = if (absDrift <= FINE_ZONE_MS) {
            val excess = (absDrift - DEADBAND_MS).toFloat()
            (MIN_SPEED_CORRECTION + excess * FINE_GAIN).coerceIn(MIN_SPEED_CORRECTION, MAX_FINE_CORRECTION)
        } else {
            val maxCap = if (allowHardSeek) MAX_CATCHUP_CORRECTION else COOLDOWN_MAX_CORRECTION
            val excess = (absDrift - FINE_ZONE_MS).toFloat()
            (MAX_FINE_CORRECTION + excess * CATCHUP_GAIN).coerceIn(MAX_FINE_CORRECTION, maxCap)
        }

        return if (drift < 0L) {
            SyncDecision(
                action = SyncAction.MICRO_ACCELERATE,
                targetSpeed = safeBaseSpeed * (1f + correction),
                seekTargetMs = null,
            )
        } else {
            SyncDecision(
                action = SyncAction.MICRO_DECELERATE,
                targetSpeed = (safeBaseSpeed * (1f - correction)).coerceAtLeast(0.25f),
                seekTargetMs = null,
            )
        }
    }
}
