package com.neonsong.app.playback.autodj

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.playback.AutoMixCurveType
import com.neonsong.app.playback.PlayableTrack
import org.junit.Before
import org.junit.Test

class AutoDJEngineTest {

    private lateinit var engine: AutoDJEngine

    @Before
    fun setUp() {
        engine = AutoDJEngine()
    }

    @Test
    fun planTransition_identicalKey_returnsExactMatchAndSmoothCurve() {
        val trackA = PlayableTrack(title = "Song A (8A - Am)", artist = "Artist 1")
        val trackB = PlayableTrack(title = "Song B (8A - Am)", artist = "Artist 2")

        val plan = engine.planTransition(trackA, trackB)

        assertThat(plan.relationship).isEqualTo(HarmonicRelationship.EXACT_MATCH)
        assertThat(plan.harmonicScore).isEqualTo(1.0f)
        assertThat(plan.recommendedCurve).isEqualTo(AutoMixCurveType.SMOOTH_S_CURVE)
        assertThat(plan.transitionDurationMs).isEqualTo(12_000L)
    }

    @Test
    fun planTransition_energyBoost_returnsClubBassSwap() {
        // 8A (Am) -> 10A (Bm) is a +2 energy boost on Camelot Wheel
        val trackA = PlayableTrack(title = "Chill Intro 8A", artist = "Artist 1")
        val trackB = PlayableTrack(title = "Festival Drop 10A", artist = "Artist 2")

        val plan = engine.planTransition(trackA, trackB)

        assertThat(plan.relationship).isEqualTo(HarmonicRelationship.ENERGY_BOOST)
        assertThat(plan.harmonicScore).isEqualTo(0.85f)
        assertThat(plan.recommendedCurve).isEqualTo(AutoMixCurveType.BASS_SWAP_CLUB)
    }

    @Test
    fun planTransition_dissonantKey_returnsFastDropCurve() {
        // 8A (Am) -> 2A (Ebm) is a 6-step tritone clash
        val trackA = PlayableTrack(title = "Track in 8A", artist = "Artist 1")
        val trackB = PlayableTrack(title = "Track in 2A", artist = "Artist 2")

        val plan = engine.planTransition(trackA, trackB)

        assertThat(plan.relationship).isEqualTo(HarmonicRelationship.DISSONANT)
        assertThat(plan.recommendedCurve).isEqualTo(AutoMixCurveType.FAST_DROP)
        assertThat(plan.transitionDurationMs).isEqualTo(5_000L)
    }

    @Test
    fun shouldTriggerTransition_detectsOutroWindowCorrectly() {
        // Remaining 10 seconds in a 180s track with 12s window
        val shouldTrigger = engine.shouldTriggerTransition(
            currentPositionMs = 170_000L,
            totalDurationMs = 180_000L,
            transitionWindowMs = 12_000L,
        )
        assertThat(shouldTrigger).isTrue()

        // 30 seconds remaining -> not yet in outro window
        val tooEarly = engine.shouldTriggerTransition(
            currentPositionMs = 150_000L,
            totalDurationMs = 180_000L,
            transitionWindowMs = 12_000L,
        )
        assertThat(tooEarly).isFalse()

        // Track ended (current >= duration) -> false
        val alreadyEnded = engine.shouldTriggerTransition(
            currentPositionMs = 180_500L,
            totalDurationMs = 180_000L,
            transitionWindowMs = 12_000L,
        )
        assertThat(alreadyEnded).isFalse()
    }

    @Test
    fun optimizeQueueHarmonically_ordersTracksForMinimalDissonance() {
        val seed = PlayableTrack(title = "Opening (8A)", artist = "DJ")
        val trackDissonant = PlayableTrack(title = "Clash (2A)", artist = "DJ")
        val trackRelative = PlayableTrack(title = "Relative (8B)", artist = "DJ")
        val trackFifth = PlayableTrack(title = "Fifth (9A)", artist = "DJ")

        val upcoming = listOf(trackDissonant, trackFifth, trackRelative)
        val optimized = engine.optimizeQueueHarmonically(seed, upcoming)

        // Best first match from 8A is Relative 8B or Fifth 9A (high scores), NOT dissonant 2A
        assertThat(optimized.first().title).isNotEqualTo("Clash (2A)")
        assertThat(optimized.size).isEqualTo(3)
    }
}
