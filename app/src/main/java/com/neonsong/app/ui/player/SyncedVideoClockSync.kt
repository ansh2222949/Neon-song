package com.neonsong.app.ui.player

import kotlin.math.abs

/**
 * High-precision Phase-Locked Loop (PLL) synchronization actions for aligning
 * silent video rendering to the primary audio playback clock.
 */
enum class SyncAction {
    /** Drift is small enough to leave alone (<= 65ms). Match base audio speed. */
    IN_SYNC,
    /** Video is behind the master clock. Apply a small, proportional speed trim. */
    MICRO_ACCELERATE,
    /** Video is ahead of the master clock. Apply a small, proportional speed trim. */
    MICRO_DECELERATE,
    /** Drift is large enough to justify a rate-limited hard seek. */
    HARD_SEEK,
}

data class SyncDecision(
    val action: SyncAction,
    val targetSpeed: Float,
    val seekTargetMs: Long? = null,
)

/**
 * Phase-Locked Loop (PLL) evaluator for aligning video presentation timestamps (PTS)
 * to the master [com.neonsong.app.playback.MusicPlayer] audio clock.
 */
object SyncedVideoClockSync {
    const val DEADBAND_MS = 65L
    const val HARD_SEEK_THRESHOLD_MS = 750L
    // A hard seek flushes the video decoder. Rate-limit recovery seeks so small
    // clock offsets do not repeatedly interrupt frame presentation.
    const val SEEK_COOLDOWN_MS = 5_000L
    private const val CORRECTION_GAIN = 0.00004f
    private const val MIN_SPEED_CORRECTION = 0.01f
    private const val MAX_SPEED_CORRECTION = 0.03f

    fun calculateDrift(videoPosMs: Long, audioPosMs: Long): Long = videoPosMs - audioPosMs

    fun evaluateSync(
        videoPosMs: Long,
        audioPosMs: Long,
        baseSpeed: Float = 1.0f,
        allowHardSeek: Boolean = true,
    ): SyncDecision {
        val drift = calculateDrift(videoPosMs, audioPosMs)
        val correction = (abs(drift).toFloat() * CORRECTION_GAIN)
            .coerceIn(MIN_SPEED_CORRECTION, MAX_SPEED_CORRECTION)
        return when {
            abs(drift) > HARD_SEEK_THRESHOLD_MS && allowHardSeek -> {
                SyncDecision(
                    action = SyncAction.HARD_SEEK,
                    targetSpeed = baseSpeed,
                    seekTargetMs = audioPosMs.coerceAtLeast(0L),
                )
            }
            drift < -DEADBAND_MS -> {
                SyncDecision(
                    action = SyncAction.MICRO_ACCELERATE,
                    targetSpeed = baseSpeed * (1f + correction),
                    seekTargetMs = null,
                )
            }
            drift > DEADBAND_MS -> {
                SyncDecision(
                    action = SyncAction.MICRO_DECELERATE,
                    targetSpeed = baseSpeed * (1f - correction),
                    seekTargetMs = null,
                )
            }
            else -> {
                SyncDecision(
                    action = SyncAction.IN_SYNC,
                    targetSpeed = baseSpeed,
                    seekTargetMs = null,
                )
            }
        }
    }
}
