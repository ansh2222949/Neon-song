package com.neonsong.app.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncedVideoClockSyncTest {

    @Test
    fun driftWithinDeadbandResultsInSyncAction() {
        val decisionExact = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decisionExact.action).isEqualTo(SyncAction.IN_SYNC)
        assertThat(decisionExact.targetSpeed).isEqualTo(1.0f)
        assertThat(decisionExact.seekTargetMs).isNull()

        val decisionLeadingSlightly = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_060L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decisionLeadingSlightly.action).isEqualTo(SyncAction.IN_SYNC)
        assertThat(decisionLeadingSlightly.targetSpeed).isEqualTo(1.0f)

        val decisionLaggingSlightly = SyncedVideoClockSync.evaluateSync(videoPosMs = 9_940L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decisionLaggingSlightly.action).isEqualTo(SyncAction.IN_SYNC)
        assertThat(decisionLaggingSlightly.targetSpeed).isEqualTo(1.0f)
    }

    @Test
    fun laggingVideoMicroAccelerates() {
        // Video is 200ms behind audio (drift = -200ms)
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_200L, baseSpeed = 1.0f)
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_ACCELERATE)
        assertThat(decision.targetSpeed).isEqualTo(1.01f)
        assertThat(decision.seekTargetMs).isNull()
    }

    @Test
    fun leadingVideoMicroDecelerates() {
        // Video is 200ms ahead of audio (drift = +200ms)
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_200L, audioPosMs = 10_000L, baseSpeed = 1.0f)
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_DECELERATE)
        assertThat(decision.targetSpeed).isEqualTo(0.99f)
        assertThat(decision.seekTargetMs).isNull()
    }

    @Test
    fun excessiveDriftTriggersHardSeekToAudioPosition() {
        // Video is 800ms behind audio (e.g. user seek or buffer underrun)
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_800L, baseSpeed = 1.0f)
        assertThat(decision.action).isEqualTo(SyncAction.HARD_SEEK)
        assertThat(decision.seekTargetMs).isEqualTo(10_800L)
        assertThat(decision.targetSpeed).isEqualTo(1.0f)
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
        assertThat(decision.targetSpeed).isEqualTo(1.03f)
    }

    @Test
    fun baseSpeedScalingPreservedDuringMicroAdjustment() {
        // When audio is running at 1.5x speed and video lags
        val decision = SyncedVideoClockSync.evaluateSync(videoPosMs = 10_000L, audioPosMs = 10_200L, baseSpeed = 1.5f)
        assertThat(decision.action).isEqualTo(SyncAction.MICRO_ACCELERATE)
        assertThat(decision.targetSpeed).isWithin(0.001f).of(1.5f * 1.01f)
    }
}
