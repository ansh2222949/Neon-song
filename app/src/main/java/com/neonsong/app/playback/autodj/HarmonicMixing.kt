package com.neonsong.app.playback.autodj

import java.util.Locale
import kotlin.math.abs

/**
 * Standard Camelot Wheel definition for harmonic DJ mixing.
 *
 * Each musical key is identified by a number (1-12) and a mode letter:
 * - 'A': Minor keys
 * - 'B': Major keys
 */
enum class CamelotKey(val code: String, val musicalName: String, val number: Int, val isMinor: Boolean) {
    KEY_1A("1A", "Abm", 1, true),
    KEY_1B("1B", "B", 1, false),
    KEY_2A("2A", "Ebm", 2, true),
    KEY_2B("2B", "F#", 2, false),
    KEY_3A("3A", "Bbm", 3, true),
    KEY_3B("3B", "Db", 3, false),
    KEY_4A("4A", "Fm", 4, true),
    KEY_4B("4B", "Ab", 4, false),
    KEY_5A("5A", "Cm", 5, true),
    KEY_5B("5B", "Eb", 5, false),
    KEY_6A("6A", "Gm", 6, true),
    KEY_6B("6B", "Bb", 6, false),
    KEY_7A("7A", "Dm", 7, true),
    KEY_7B("7B", "F", 7, false),
    KEY_8A("8A", "Am", 8, true),
    KEY_8B("8B", "C", 8, false),
    KEY_9A("9A", "Em", 9, true),
    KEY_9B("9B", "G", 9, false),
    KEY_10A("10A", "Bm", 10, true),
    KEY_10B("10B", "D", 10, false),
    KEY_11A("11A", "F#m", 11, true),
    KEY_11B("11B", "A", 11, false),
    KEY_12A("12A", "Dbm", 12, true),
    KEY_12B("12B", "E", 12, false);

    companion object {
        private val BY_CODE = entries.associateBy { it.code.uppercase(Locale.ROOT) }

        fun fromCode(code: String?): CamelotKey? {
            if (code.isNullOrBlank()) return null
            return BY_CODE[code.trim().uppercase(Locale.ROOT)]
        }

        /**
         * Resolves standard musical key representations (e.g., "Am", "A minor", "C#m", "Eb Maj")
         * to a canonical Camelot key.
         */
        fun parse(keyString: String?): CamelotKey? {
            if (keyString.isNullOrBlank()) return null
            val clean = keyString.trim().uppercase(Locale.ROOT)

            // Direct Camelot code match (e.g., "8A", "11B")
            fromCode(clean)?.let { return it }

            val isMinor = clean.contains("M") && !clean.contains("MAJ") ||
                    clean.contains("MIN") || clean.endsWith("M")

            val root = clean
                .replace("MAJOR", "")
                .replace("MINOR", "")
                .replace("MAJ", "")
                .replace("MIN", "")
                .replace("M", "")
                .trim()

            return when (root) {
                "AB", "G#" -> if (isMinor) KEY_1A else KEY_4B
                "B" -> if (isMinor) KEY_10A else KEY_1B
                "EB", "D#" -> if (isMinor) KEY_2A else KEY_5B
                "F#", "GB" -> if (isMinor) KEY_11A else KEY_2B
                "BB", "A#" -> if (isMinor) KEY_3A else KEY_6B
                "DB", "C#" -> if (isMinor) KEY_12A else KEY_3B
                "F" -> if (isMinor) KEY_4A else KEY_7B
                "C" -> if (isMinor) KEY_5A else KEY_8B
                "G" -> if (isMinor) KEY_6A else KEY_9B
                "D" -> if (isMinor) KEY_7A else KEY_10B
                "A" -> if (isMinor) KEY_8A else KEY_11B
                "E" -> if (isMinor) KEY_9A else KEY_12B
                else -> null
            }
        }
    }
}

enum class HarmonicRelationship(
    val score: Float,
    val description: String,
    val recommendedTransition: TransitionStyle,
) {
    EXACT_MATCH(1.0f, "Identical Key: 100% Harmonic Match", TransitionStyle.HARMONIC_LONG_BLEND),
    RELATIVE(0.95f, "Relative Major/Minor: Seamless Mood Shift", TransitionStyle.HARMONIC_LONG_BLEND),
    ADJACENT_FIFTH(0.90f, "Adjacent 5th (±1 Hour): Perfect Progression", TransitionStyle.HARMONIC_LONG_BLEND),
    ENERGY_BOOST(0.85f, "Energy Boost (+2 Hours): Crowd Excitement Lift", TransitionStyle.BASS_SWAP_CLUB),
    SEMITONE_MODULATION(0.80f, "Semitone Lift (+1 Semitone): Euphoric Key Lift", TransitionStyle.BASS_SWAP_CLUB),
    DIAGONAL(0.75f, "Diagonal Blend: Expressive Shift", TransitionStyle.BASS_SWAP_CLUB),
    DISSONANT(0.40f, "Key Clash: Requires Frequency Filter or Cut", TransitionStyle.ECHO_DROP_CUT),
}

enum class TransitionStyle {
    HARMONIC_LONG_BLEND, // 8-16 bars smooth phrase blend
    BASS_SWAP_CLUB,      // 4-8 bars with kick/bass crossover snap
    ECHO_DROP_CUT,       // Quick drop with echo tail out
}

data class KeyCompatibilityResult(
    val currentKey: CamelotKey,
    val nextKey: CamelotKey,
    val relationship: HarmonicRelationship,
    val compatibilityScore: Float,
)

object HarmonicMixing {

    /**
     * Evaluates harmonic compatibility between two tracks on the Camelot wheel.
     */
    fun evaluate(keyA: CamelotKey?, keyB: CamelotKey?): KeyCompatibilityResult? {
        if (keyA == null || keyB == null) return null

        val sameNumber = keyA.number == keyB.number
        val sameMode = keyA.isMinor == keyB.isMinor

        val diff = abs(keyA.number - keyB.number)
        val circularDiff = minOf(diff, 12 - diff)
        val stepForward = (keyB.number - keyA.number + 12) % 12

        val relationship = when {
            sameNumber && sameMode -> HarmonicRelationship.EXACT_MATCH
            sameNumber && !sameMode -> HarmonicRelationship.RELATIVE
            circularDiff == 1 && sameMode -> HarmonicRelationship.ADJACENT_FIFTH
            stepForward == 2 && sameMode -> HarmonicRelationship.ENERGY_BOOST
            // Semitone modulation: 8A (Am) -> 3A (Bbm) is +5 steps circularly
            stepForward == 7 && sameMode -> HarmonicRelationship.SEMITONE_MODULATION
            circularDiff == 1 && !sameMode -> HarmonicRelationship.DIAGONAL
            else -> HarmonicRelationship.DISSONANT
        }

        return KeyCompatibilityResult(
            currentKey = keyA,
            nextKey = keyB,
            relationship = relationship,
            compatibilityScore = relationship.score,
        )
    }

    /**
     * Checks BPM tempo alignment and stretch headroom.
     * Within ±4% is transparent; within ±8% is club standard; half/double tempo is matched.
     */
    fun evaluateBpmAlignment(bpmA: Float, bpmB: Float): Float {
        if (bpmA <= 0f || bpmB <= 0f) return 1.0f

        // Check 1:1, 1:2, and 2:1 ratios
        val ratio1 = bpmB / bpmA
        val ratioHalf = (bpmB * 2f) / bpmA
        val ratioDouble = (bpmB / 2f) / bpmA

        val bestRatio = listOf(ratio1, ratioHalf, ratioDouble)
            .minByOrNull { abs(it - 1.0f) } ?: 1.0f

        val deviation = abs(bestRatio - 1.0f)
        return when {
            deviation <= 0.04f -> 1.0f // Transparent (<4%)
            deviation <= 0.08f -> 0.85f // Good club standard (<8%)
            deviation <= 0.15f -> 0.65f // Noticeable stretch
            else -> 0.35f // Needs cut or slow ramp
        }
    }
}
