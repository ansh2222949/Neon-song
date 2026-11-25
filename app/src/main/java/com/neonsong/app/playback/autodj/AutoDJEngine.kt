package com.neonsong.app.playback.autodj

import com.neonsong.app.playback.AutoMixCurveType
import com.neonsong.app.playback.PlayableTrack
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

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
        private const val DEFAULT_BPM = 124f
        private val CAMELOT_CODE_REGEX = Regex("(?i)\\b(1[0-2]|[1-9])([ab])\\b")
        private val BRACKET_KEY_REGEX =
            Regex("(?i)[\\(\\[]([A-G][#b♯♭]?(?:\\s*(?:maj|major|min|minor|m))?)[\\)\\]]")
        private val BPM_REGEX = Regex("(?i)(?:\\b(\\d{2,3})\\s*bpm\\b|\\bbpm\\s*(\\d{2,3})\\b)")
    }

    /**
     * Evaluates harmonic and rhythmic compatibility between current track and incoming track.
     */
    fun planTransition(
        current: PlayableTrack,
        incoming: PlayableTrack,
        preferredCurve: AutoMixCurveType = AutoMixCurveType.EQUAL_POWER,
        bpmAOverride: Float? = null,
        bpmBOverride: Float? = null,
    ): AutoDJTransitionPlan {
        val keyA = extractKey(current)
        val keyB = extractKey(incoming)

        val harmonicResult = HarmonicMixing.evaluate(keyA, keyB)
        val harmonicScore = harmonicResult?.compatibilityScore ?: 0.75f
        val relationship = harmonicResult?.relationship ?: HarmonicRelationship.ADJACENT_FIFTH

        val bpmA = bpmAOverride?.takeIf { it.isFinite() && it in 50f..220f } ?: estimateBpm(current)
        val bpmB = bpmBOverride?.takeIf { it.isFinite() && it in 50f..220f } ?: estimateBpm(incoming)
        val bpmScore = HarmonicMixing.evaluateBpmAlignment(bpmA, bpmB)

        val overallScore = (harmonicScore * 0.65f) + (bpmScore * 0.35f)

        val recommendedCurve = when {
            preferredCurve != AutoMixCurveType.EQUAL_POWER -> preferredCurve
            harmonicResult == null -> if (bpmScore < 0.5f) AutoMixCurveType.FAST_DROP else AutoMixCurveType.EQUAL_POWER
            relationship == HarmonicRelationship.EXACT_MATCH || relationship == HarmonicRelationship.RELATIVE ->
                AutoMixCurveType.SMOOTH_S_CURVE
            relationship == HarmonicRelationship.ENERGY_BOOST ->
                AutoMixCurveType.BASS_SWAP_CLUB
            relationship == HarmonicRelationship.DISSONANT || bpmScore < 0.5f ->
                AutoMixCurveType.FAST_DROP
            else -> AutoMixCurveType.EQUAL_POWER
        }

        val transitionDurationMs = (
            if (harmonicResult == null) {
                DEFAULT_TRANSITION_MS
            } else {
                when (relationship) {
                    HarmonicRelationship.EXACT_MATCH,
                    HarmonicRelationship.RELATIVE -> 12_000L
                    HarmonicRelationship.ADJACENT_FIFTH -> 10_000L
                    HarmonicRelationship.ENERGY_BOOST,
                    HarmonicRelationship.SEMITONE_MODULATION,
                    HarmonicRelationship.DIAGONAL -> 8_000L
                    HarmonicRelationship.DISSONANT -> 5_000L
                }
            }
        ).coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS)

        val description = if (keyA != null && keyB != null) {
            "${relationship.description} (${keyA.code} \u2192 ${keyB.code})"
        } else {
            "Tempo & Energy Blend"
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

        // Pre-extract key & BPM once in O(N) instead of inside the O(N^2) selection loop.
        val count = upcomingQueue.size
        val keys = Array(count) { extractKey(upcomingQueue[it]) }
        val bpms = FloatArray(count) { estimateBpm(upcomingQueue[it]) }
        val used = BooleanArray(count)

        val sorted = ArrayList<PlayableTrack>(count)
        var currentKey = extractKey(seed)
        var currentBpm = estimateBpm(seed)

        repeat(count) {
            var bestIdx = -1
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in 0 until count) {
                if (used[i]) continue
                val harmonicScore = HarmonicMixing.evaluate(currentKey, keys[i])?.compatibilityScore ?: 0.75f
                val bpmScore = HarmonicMixing.evaluateBpmAlignment(currentBpm, bpms[i])
                val score = (harmonicScore * 0.65f) + (bpmScore * 0.35f)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
            if (bestIdx < 0) return@repeat
            used[bestIdx] = true
            sorted.add(upcomingQueue[bestIdx])
            currentKey = keys[bestIdx]
            currentBpm = bpms[bestIdx]
        }
        return sorted
    }

    fun extractKey(track: PlayableTrack): CamelotKey? {
        val album = track.album.orEmpty()
        val text = if (album.isEmpty()) track.title else "${track.title} $album"
        CAMELOT_CODE_REGEX.find(text)?.let { match ->
            val code = "${match.groupValues[1]}${match.groupValues[2].uppercase(Locale.ROOT)}"
            CamelotKey.fromCode(code)?.let { return it }
        }
        BRACKET_KEY_REGEX.find(text)?.let { match ->
            CamelotKey.parse(match.groupValues[1])?.let { return it }
        }
        return CamelotKey.parse(track.album)
    }

    private fun estimateBpm(track: PlayableTrack): Float {
        val sources = arrayOf(track.title, track.album.orEmpty())
        for (src in sources) {
            if (src.isEmpty()) continue
            val match = BPM_REGEX.find(src) ?: continue
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val parsed = raw.toFloatOrNull()
            if (parsed != null && parsed in 50f..220f) return parsed
        }
        return DEFAULT_BPM
    }
}
