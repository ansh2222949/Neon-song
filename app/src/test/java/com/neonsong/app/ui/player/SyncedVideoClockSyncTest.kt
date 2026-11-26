package com.neonsong.app.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SyncedVideoClockSyncTest {

    @Before
    fun setUp() {
        SyncedVideoClockSync.reset()
    }

    @Test
    fun driftWithinDeadbandResultsInSyncAction() {
        val decisionExact = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decisionExact.action).isEqualTo(SyncAction.IN_SYNC)
        assertThat(decisionExact.targetSpeed).isEqualTo(1.0f)
        assertThat(decisionExact.seekTargetMs).isNull()

        val decisionLeadingSlightly = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_015L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decisionLeadingSlightly.action).isEqualTo(SyncAction.IN_SYNC)
        assertThat(decisionLeadingSlightly.targetSpeed).isEqualTo(1.0f)

        val decisionLaggingSlightly = SyncedVideoClockSync.evaluateSync(videoPosMs = 9_985L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decisionLaggingSlightly.action).isEqualTo(SyncAction.IN_SYNC)
        assertThat(decisionLaggingSlightly.targetSpeed).isEqualTo(1.0f)
    }

    @Test
    fun laggingVideoMicroAccelerates() {
        // Video is 200ms behind audio (drift = -200ms)
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_200L, baseSpeed = 1.0f)
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_ACCELERATE)
        assertThat(decision.targetSpeed).isGreaterThan(1.0f)
        assertThat(decision.seekTargetMs).isNull()
    }

    @Test
    fun leadingVideoMicroDecelerates() {
        // Video is 200ms ahead of audio (drift = +200ms)
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_200L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_DECELERATE)
        assertThat(decision.targetSpeed).isLessThan(1.0f)
        assertThat(decision.seekTargetMs).isNull()
    }

    @Test
    fun excessiveDriftTriggersHardSeekToAudioPosition() {
        // Video is 800ms behind audio (e.g. user seek or buffer underrun)
        // Includes 65ms keyframe decode lead compensation when playing
        val decisionPlaying = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_800L, baseSpeed = 1.0f, isPlaying = true)
        assertThat(decisionPlaying.action).isEqualTo(SyncAction.HARD_SEEK)
        assertThat(decisionPlaying.seekTargetMs).isEqualTo(10_865L)
        assertThat(decisionPlaying.targetSpeed).isEqualTo(1.0f)

        SyncedVideoClockSync.reset()
        val decisionPaused = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_800L, baseSpeed = 1.0f, isPlaying = false)
        assertThat(decisionPaused.action).isEqualTo(SyncAction.HARD_SEEK)
        assertThat(decisionPaused.seekTargetMs).isEqualTo(10_800L)
    }

    @Test
    fun hardSeekCanBeRateLimitedWhileSmallSpeedCorrectionContinues() {
        val decision = SyncedVideoClockSync.evaluateSync(
            videoPosMs = 10_000L,
            audioPosMs = 10_800L,
            baseSpeed = 1.0f,
            allowHardSeek = false,
        )
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_ACCELERATE)
        assertThat(decision.seekTargetMs).isNull()
        assertThat(decision.targetSpeed).isEqualTo(1.14f)
    }

    @Test
    fun baseSpeedScalingPreservedDuringMicroAdjustment() {
        // When audio is running at 1.5x speed and video lags
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_200L, baseSpeed = 1.5f)
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_ACCELERATE)
        assertThat(decision.targetSpeed).isGreaterThan(1.5f)
    }

    @Test
    fun audibleTimestampExtrapolatesAndCompensatesLatency() {
        val now = 100_000L
        // Initial anchor:
        val t0 = SyncedVideoClockSync.getAudibleAudioTimestampMs(
            rawAudioPosMs = 1_000L,
            isPlaying = true,
            speed = 1.0f,
            presentationLatencyMs = 50L,
            nowElapsedMs = now,
        )
        assertThat(t0).isEqualTo(950L) // 1000 - 50 = 950

        // 100ms later with same raw anchor:
        val t1 = SyncedVideoClockSync.getAudibleAudioTimestampMs(
            rawAudioPosMs = 1_000L,
            isPlaying = true,
            speed = 1.0f,
            presentationLatencyMs = 50L,
            nowElapsedMs = now + 100L,
        )
        assertThat(t1).isEqualTo(1_050L) // (1000 + 100) - 50 = 1050
    }

    @Test
    fun estimateIntroOffsetDetectsOpeningBumper() {
        // 5 second MV bumper
        val offset = SyncedVideoClockSync.estimateIntroOffsetMs(
            audioDurationMs = 210_000L,
            videoDurationMs = 215_000L,
        )
        assertThat(offset).isEqualTo(5_000L)

        // Too long (25s extended storyline cut) -> returns 0L
        val longOffset = SyncedVideoClockSync.estimateIntroOffsetMs(
            audioDurationMs = 210_000L,
            videoDurationMs = 235_000L,
        )
        assertThat(longOffset).isEqualTo(0L)

        // Manual override takes precedence
        val manual = SyncedVideoClockSync.estimateIntroOffsetMs(
            audioDurationMs = 210_000L,
            videoDurationMs = 235_000L,
            manualOffsetMs = 25_000L,
        )
        assertThat(manual).isEqualTo(25_000L)
    }
}
