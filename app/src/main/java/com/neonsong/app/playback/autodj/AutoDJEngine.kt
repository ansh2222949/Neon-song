package com.neonsong.app.playback.autodj

import com.neonsong.app.playback.AutoMixCurveType
import com.neonsong.app.playback.PlayableTrack
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Result of evaluating two tracks for autonomous AutoDJ mixing.
 */
data class AutoDJTransitionPlan(
    val currentKey: CamelotKey?,
    val nextKey: CamelotKey?,
    val relationship: HarmonicRelationship,
    val harmonicScore: Float,
    val bpmScore: Float,
    val overallScore: Float,
    val recommendedCurve: AutoMixCurveType,
    val transitionDurationMs: Long,
    val description: String,
)

/**
 * Production AutoDJ Engine inspired by open-source DJ software (Mixxx, DJay, Rekordbox).
 *
 * Capabilities:
 * 1. Camelot Key Harmonic Compatibility & Energy Flow Management.
 * 2. BPM Beatmatch & Half/Double Tempo Alignment.
 * 3. Autonomous Smart Queue Reordering (harmonic pathfinding).
 * 4. Dynamic Transition Curve & Outro/Intro Trigger Window Calculation.
 */
@Singleton
class AutoDJEngine @Inject constructor() {

    companion object {
        const val DEFAULT_TRANSITION_MS = 10_000L
        const val MIN_TRANSITION_MS = 4_000L
        const val MAX_TRANSITION_MS = 16_000L
        const val OUTRO_TRIGGER_WINDOW_MS = 12_000L
    }

    /**
     * Evaluates harmonic and rhythmic compatibility between current track and incoming track.
     */
    fun planTransition(
        current: PlayableTrack,
        incoming: PlayableTrack,
        preferredCurve: AutoMixCurveType = AutoMixCurveType.EQUAL_POWER,
    ): AutoDJTransitionPlan {
        val keyA = extractKey(current)
        val keyB = extractKey(incoming)

        val harmonicResult = HarmonicMixing.evaluate(keyA, keyB)
        val harmonicScore = harmonicResult?.compatibilityScore ?: 0.75f
        val relationship = harmonicResult?.relationship ?: HarmonicRelationship.ADJACENT_FIFTH

        // BPM evaluation (estimated from track durations and metadata where available)
        val bpmA = estimateBpm(current)
        val bpmB = estimateBpm(incoming)
        val bpmScore = HarmonicMixing.evaluateBpmAlignment(bpmA, bpmB)

        // Composite compatibility score: 65% Harmonic, 35% BPM
        val overallScore = (harmonicScore * 0.65f) + (bpmScore * 0.35f)

        val recommendedCurve = when {
            preferredCurve != AutoMixCurveType.EQUAL_POWER -> preferredCurve
            relationship == HarmonicRelationship.EXACT_MATCH || relationship == HarmonicRelationship.RELATIVE ->
                AutoMixCurveType.SMOOTH_S_CURVE
            relationship == HarmonicRelationship.ENERGY_BOOST ->
                AutoMixCurveType.BASS_SWAP_CLUB
            relationship == HarmonicRelationship.DISSONANT || bpmScore < 0.5f ->
                AutoMixCurveType.FAST_DROP
            else ->
                AutoMixCurveType.EQUAL_POWER
        }

        val transitionDurationMs = when (relationship) {
            HarmonicRelationship.EXACT_MATCH,
            HarmonicRelationship.RELATIVE -> 12_000L
            HarmonicRelationship.ADJACENT_FIFTH -> 10_000L
            HarmonicRelationship.ENERGY_BOOST,
            HarmonicRelationship.SEMITONE_MODULATION,
            HarmonicRelationship.DIAGONAL -> 8_000L
            HarmonicRelationship.DISSONANT -> 5_000L
        }.coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS)

        val description = buildString {
            append(relationship.description)
            if (keyA != null && keyB != null) {
                append(" (${keyA.code} \u2192 ${keyB.code})")
            }
        }

        return AutoDJTransitionPlan(
            currentKey = keyA,
            nextKey = keyB,
            relationship = relationship,
            harmonicScore = harmonicScore,
            bpmScore = bpmScore,
            overallScore = overallScore,
            recommendedCurve = recommendedCurve,
            transitionDurationMs = transitionDurationMs,
            description = description,
        )
    }

    /**
     * Determines whether playback has entered the outro cue-point window for crossfade triggering.
     */
    fun shouldTriggerTransition(
        currentPositionMs: Long,
        totalDurationMs: Long,
        transitionWindowMs: Long = OUTRO_TRIGGER_WINDOW_MS,
    ): Boolean {
        if (totalDurationMs <= 0L || currentPositionMs <= 0L) return false
        val remainingMs = totalDurationMs - currentPositionMs
        return remainingMs in 1L..transitionWindowMs
    }

    /**
     * Optimizes upcoming queue order using greedy harmonic pathfinding:
     * Orders candidate tracks so that key transitions minimize dissonance and preserve energy.
     */
    fun optimizeQueueHarmonically(
        seed: PlayableTrack,
        upcomingQueue: List<PlayableTrack>,
    ): List<PlayableTrack> {
        if (upcomingQueue.size <= 1) return upcomingQueue

        val remaining = upcomingQueue.toMutableList()
        val sorted = mutableListOf<PlayableTrack>()
        var current = seed

        while (remaining.isNotEmpty()) {
            val bestNextIndex = remaining.indices.maxByOrNull { i ->
                val plan = planTransition(current, remaining[i])
                plan.overallScore
            } ?: 0

            val chosen = remaining.removeAt(bestNextIndex)
            sorted.add(chosen)
            current = chosen
        }

        return sorted
    }

    fun extractKey(track: PlayableTrack): CamelotKey? {
        val text = "${track.title} ${track.album.orEmpty()}"
        val camelotMatch = Regex("(?i)\\b([1-9]|1[0-2])([ab])\\b").find(text)
        if (camelotMatch != null) {
            val code = "${camelotMatch.groupValues[1]}${camelotMatch.groupValues[2].uppercase(Locale.ROOT)}"
            CamelotKey.fromCode(code)?.let { return it }
        }
        val keyInBrackets = Regex("(?i)[\\(\\[]([A-G][#b]?(?:m|maj|min|major|minor)?)[\\)\\]]").find(text)
        if (keyInBrackets != null) {
            CamelotKey.parse(keyInBrackets.groupValues[1])?.let { return it }
        }
        return CamelotKey.parse(track.album)
    }

    private fun estimateBpm(track: PlayableTrack): Float {
        // Look for explicit BPM tags in title or album
        val bpmMatch = Regex("(?i)(\\d{2,3})\\s*bpm").find(track.title)
        if (bpmMatch != null) {
            val parsed = bpmMatch.groupValues[1].toFloatOrNull()
            if (parsed != null && parsed in 50f..220f) return parsed
        }
        // Fallback default tempo (typical club / pop standard 124 BPM)
        return 124f
    }
}
