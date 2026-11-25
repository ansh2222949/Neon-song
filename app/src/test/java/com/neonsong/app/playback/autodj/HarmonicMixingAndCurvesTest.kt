package com.neonsong.app.playback.autodj

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.playback.AutoMixCurve
import com.neonsong.app.playback.AutoMixCurveType
import org.junit.Test
import kotlin.math.abs

class HarmonicMixingAndCurvesTest {

    @Test
    fun camelotKeyParsesVariousFormats() {
        assertThat(CamelotKey.parse("8A")).isEqualTo(CamelotKey.KEY_8A)
        assertThat(CamelotKey.parse("8a")).isEqualTo(CamelotKey.KEY_8A)
        assertThat(CamelotKey.parse("Am")).isEqualTo(CamelotKey.KEY_8A)
        assertThat(CamelotKey.parse("A minor")).isEqualTo(CamelotKey.KEY_8A)
        assertThat(CamelotKey.parse("Amin")).isEqualTo(CamelotKey.KEY_8A)

        assertThat(CamelotKey.parse("8B")).isEqualTo(CamelotKey.KEY_8B)
        assertThat(CamelotKey.parse("C")).isEqualTo(CamelotKey.KEY_8B)
        assertThat(CamelotKey.parse("C Major")).isEqualTo(CamelotKey.KEY_8B)
        assertThat(CamelotKey.parse("C Maj")).isEqualTo(CamelotKey.KEY_8B)

        assertThat(CamelotKey.parse("11A")).isEqualTo(CamelotKey.KEY_11A)
        assertThat(CamelotKey.parse("F#m")).isEqualTo(CamelotKey.KEY_11A)

        assertThat(CamelotKey.parse("invalid_key")).isNull()
        assertThat(CamelotKey.parse(null)).isNull()
    }

    @Test
    fun exactKeyMatchYieldsPerfectHarmonicCompatibility() {
        val result = HarmonicMixing.evaluate(CamelotKey.KEY_8A, CamelotKey.KEY_8A)
        assertThat(result).isNotNull()
        assertThat(result?.relationship).isEqualTo(HarmonicRelationship.EXACT_MATCH)
        assertThat(result?.compatibilityScore).isEqualTo(1.0f)
        assertThat(result?.relationship?.recommendedTransition).isEqualTo(TransitionStyle.HARMONIC_LONG_BLEND)
    }

    @Test
    fun relativeKeyMatchYieldsHighMoodShiftCompatibility() {
        val result = HarmonicMixing.evaluate(CamelotKey.KEY_8A, CamelotKey.KEY_8B)
        assertThat(result).isNotNull()
        assertThat(result?.relationship).isEqualTo(HarmonicRelationship.RELATIVE)
        assertThat(result?.compatibilityScore).isEqualTo(0.95f)
    }

    @Test
    fun adjacentKeyOnWheelYieldsCircleOfFifthsCompatibility() {
        val fifthUp = HarmonicMixing.evaluate(CamelotKey.KEY_8A, CamelotKey.KEY_9A)
        assertThat(fifthUp?.relationship).isEqualTo(HarmonicRelationship.ADJACENT_FIFTH)
        assertThat(fifthUp?.compatibilityScore).isEqualTo(0.90f)

        val fifthDown = HarmonicMixing.evaluate(CamelotKey.KEY_8A, CamelotKey.KEY_7A)
        assertThat(fifthDown?.relationship).isEqualTo(HarmonicRelationship.ADJACENT_FIFTH)
        assertThat(fifthDown?.compatibilityScore).isEqualTo(0.90f)

        // Circular boundary wrap: 12A to 1A
        val wrap = HarmonicMixing.evaluate(CamelotKey.KEY_12A, CamelotKey.KEY_1A)
        assertThat(wrap?.relationship).isEqualTo(HarmonicRelationship.ADJACENT_FIFTH)
    }

    @Test
    fun energyBoostStepYieldsExcitementLift() {
        val boost = HarmonicMixing.evaluate(CamelotKey.KEY_8A, CamelotKey.KEY_10A)
        assertThat(boost?.relationship).isEqualTo(HarmonicRelationship.ENERGY_BOOST)
        assertThat(boost?.compatibilityScore).isEqualTo(0.85f)
        assertThat(boost?.relationship?.recommendedTransition).isEqualTo(TransitionStyle.BASS_SWAP_CLUB)
    }

    @Test
    fun dissonantKeyClashRequiresEchoDropCut() {
        // 8A (Am) vs 2A (Ebm) - tritone clash
        val clash = HarmonicMixing.evaluate(CamelotKey.KEY_8A, CamelotKey.KEY_2A)
        assertThat(clash?.relationship).isEqualTo(HarmonicRelationship.DISSONANT)
        assertThat(clash?.compatibilityScore).isEqualTo(0.40f)
        assertThat(clash?.relationship?.recommendedTransition).isEqualTo(TransitionStyle.ECHO_DROP_CUT)
    }

    @Test
    fun bpmAlignmentScoringReflectsTempoHeadroom() {
        // Exactly identical BPM
        assertThat(HarmonicMixing.evaluateBpmAlignment(128f, 128f)).isEqualTo(1.0f)
        // 2% difference -> transparent stretch
        assertThat(HarmonicMixing.evaluateBpmAlignment(128f, 126f)).isEqualTo(1.0f)
        // 6% difference -> standard club stretch
        assertThat(HarmonicMixing.evaluateBpmAlignment(128f, 120f)).isEqualTo(0.85f)
        // Half-time / Double-time (70 bpm vs 140 bpm) matches cleanly
        assertThat(HarmonicMixing.evaluateBpmAlignment(70f, 140f)).isEqualTo(1.0f)
    }

    @Test
    fun equalPowerCurveConservesEnergyAcrossEntireCrossfade() {
        for (i in 0..100) {
            val progress = i / 100.0f
            val gains = AutoMixCurve.calculateGains(progress, AutoMixCurveType.EQUAL_POWER)

            val power = gains.deckAGain * gains.deckAGain + gains.deckBGain * gains.deckBGain
            assertThat(abs(power - 1.0f)).isLessThan(1e-5f)

            if (i == 0) {
                assertThat(gains.deckAGain).isEqualTo(1.0f)
                assertThat(gains.deckBGain).isEqualTo(0.0f)
            } else if (i == 100) {
                assertThat(gains.deckAGain).isLessThan(1e-5f)
                assertThat(gains.deckBGain).isEqualTo(1.0f)
            } else if (i == 50) {
                assertThat(abs(gains.deckAGain - 0.7071f)).isLessThan(1e-3f)
                assertThat(abs(gains.deckBGain - 0.7071f)).isLessThan(1e-3f)
            }
        }
    }

    @Test
    fun smoothSCurveProvidesSmoothstepBlend() {
        val start = AutoMixCurve.calculateGains(0.0f, AutoMixCurveType.SMOOTH_S_CURVE)
        assertThat(start.deckAGain).isEqualTo(1.0f)
        assertThat(start.deckBGain).isEqualTo(0.0f)

        val mid = AutoMixCurve.calculateGains(0.5f, AutoMixCurveType.SMOOTH_S_CURVE)
        assertThat(mid.deckAGain).isEqualTo(0.5f)
        assertThat(mid.deckBGain).isEqualTo(0.5f)

        val end = AutoMixCurve.calculateGains(1.0f, AutoMixCurveType.SMOOTH_S_CURVE)
        assertThat(end.deckAGain).isEqualTo(0.0f)
        assertThat(end.deckBGain).isEqualTo(1.0f)
    }

    @Test
    fun bassSwapClubSnapsLowEndAtMidpoint() {
        // At 25% progress: Deck A owns 100% bass
        val early = AutoMixCurve.calculateGains(0.25f, AutoMixCurveType.BASS_SWAP_CLUB)
        assertThat(early.bassDeckA).isEqualTo(1.0f)
        assertThat(early.bassDeckB).isEqualTo(0.5f)

        // At 75% progress: Deck B owns 100% bass
        val late = AutoMixCurve.calculateGains(0.75f, AutoMixCurveType.BASS_SWAP_CLUB)
        assertThat(late.bassDeckA).isEqualTo(0.5f)
        assertThat(late.bassDeckB).isEqualTo(1.0f)
    }
}
