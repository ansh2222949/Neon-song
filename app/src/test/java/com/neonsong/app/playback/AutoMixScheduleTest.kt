package com.neonsong.app.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoMixScheduleTest {

    private fun input(
        enabled: Boolean = true,
        bitPerfect: Boolean = false,
        isPlaying: Boolean = true,
        repeatOne: Boolean = false,
        durationMs: Long = 240_000L,
        positionMs: Long = 0L,
        bars: Int = 8,
        bpm: Float = 120f,
        hasNext: Boolean = true,
        nextResolved: Boolean = true,
        streamExpired: Boolean = false,
        armed: Boolean = false,
        ready: Boolean = false,
        standbyDurationMs: Long = 180_000L,
        standbyBufferedMs: Long = 12_000L,
    ) = AutoMixPlanInput(
        automixEnabled = enabled,
        bitPerfectEnabled = bitPerfect,
        isPlaying = isPlaying,
        repeatOne = repeatOne,
        durationMs = durationMs,
        positionMs = positionMs,
        barMode = bars,
        bpm = bpm,
        hasNextItem = hasNext,
        nextItemResolved = nextResolved,
        streamExpired = streamExpired,
        standbyArmedForNext = armed,
        standbyReady = ready,
        standbyDurationMs = standbyDurationMs,
        standbyBufferedMs = standbyBufferedMs,
    )

    @Test
    fun transitionWindowUsesSelectedPhraseAndFallsBackForInvalidTempo() {
        assertThat(input(bars = 8).transitionMs).isEqualTo(16_000L)
        assertThat(input(bars = 16).transitionMs).isEqualTo(32_000L)
        assertThat(input(bars = 16, bpm = Float.NaN).transitionMs).isEqualTo(32_000L)
    }

    @Test
    fun mixWaitsForNextDeckAndPhraseWindowBeforeHandingOff() {
        assertThat(planAutoMix(input())).isEqualTo(AutoMixDecision.Arm)
        assertThat(planAutoMix(input(armed = true))).isEqualTo(AutoMixDecision.WaitingForReady)

        assertThat(
            planAutoMix(input(positionMs = 223_999L, armed = true, ready = true)),
        ).isEqualTo(AutoMixDecision.WaitingForWindow)

        assertThat(
            planAutoMix(input(positionMs = 230_000L, armed = true, ready = true)),
        ).isEqualTo(AutoMixDecision.HandOff(overlapMs = 10_000L, bars = 8))
    }

    @Test
    fun handoffIsBoundedByIncomingTrackAndAvailableBuffer() {
        val plan = planAutoMix(
            input(
                durationMs = 24_000L,
                positionMs = 16_000L,
                armed = true,
                ready = true,
                standbyDurationMs = 9_000L,
                standbyBufferedMs = 6_000L,
            ),
        )

        assertThat(plan).isEqualTo(AutoMixDecision.HandOff(overlapMs = 3_000L, bars = 8))
    }

    @Test
    fun unresolvedOrExpiredNextTrackIsRefreshedOnlyInsideLeadWindow() {
        val tooEarly = planAutoMix(input(positionMs = 203_999L, nextResolved = false))
        assertThat(tooEarly).isEqualTo(AutoMixDecision.Idle(AutoMixIdleReason.TooEarly))

        val inWindow = planAutoMix(input(positionMs = 204_000L, nextResolved = false))
        assertThat(inWindow).isEqualTo(
            AutoMixDecision.ResolveNext(AutoMixResolveReason.Placeholder),
        )

        val expired = planAutoMix(
            input(positionMs = 204_000L, streamExpired = true, armed = true, ready = true),
        )
        assertThat(expired).isEqualTo(
            AutoMixDecision.ResolveNext(AutoMixResolveReason.ExpiredSignature),
        )
    }

    @Test
    fun disabledPausedRepeatOneAndBitPerfectModesDoNotMix() {
        assertThat(planAutoMix(input(enabled = false)))
            .isEqualTo(AutoMixDecision.Idle(AutoMixIdleReason.Disabled))
        assertThat(planAutoMix(input(bitPerfect = true)))
            .isEqualTo(AutoMixDecision.Idle(AutoMixIdleReason.BitPerfect))
        assertThat(planAutoMix(input(isPlaying = false)))
            .isEqualTo(AutoMixDecision.Idle(AutoMixIdleReason.Paused))
        assertThat(planAutoMix(input(repeatOne = true)))
            .isEqualTo(AutoMixDecision.Idle(AutoMixIdleReason.RepeatOne))
        assertThat(planAutoMix(input(hasNext = false)))
            .isEqualTo(AutoMixDecision.Idle(AutoMixIdleReason.NoNextItem))
    }
}
