package com.neonsong.app.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mathematical curve profiles for dual-deck DJ transitions.
 */
enum class AutoMixCurveType {
    EQUAL_POWER,     // Acoustically transparent: constant perceived RMS power (cos/sin)
    SMOOTH_S_CURVE,  // Smoothstep (3t^2 - 2t^3): gentle onset and release
    LINEAR,          // Standard linear crossfade
    BASS_SWAP_CLUB,  // Mid/High equal power with sharp kick/bass snap at midpoint
    FAST_DROP,       // Rapid 1-bar drop mix
}

data class DeckGains(
    val deckAGain: Float,
    val deckBGain: Float,
    val bassDeckA: Float = deckAGain,
    val bassDeckB: Float = deckBGain,
)

object AutoMixCurve {

    private const val HALF_PI = PI * 0.5

    /**
     * Calculates the instantaneous volume gains for Deck A (outgoing) and Deck B (incoming)
     * at normalized progress [t] in [0.0..1.0].
     */
    fun calculateGains(progress: Float, type: AutoMixCurveType = AutoMixCurveType.EQUAL_POWER): DeckGains {
        val t = if (progress.isFinite()) progress.coerceIn(0.0f, 1.0f) else 0.0f

        return when (type) {
            AutoMixCurveType.EQUAL_POWER -> {
                // Total acoustic power L^2 + R^2 = cos^2(pi/2 * t) + sin^2(pi/2 * t) == 1.0
                val angle = HALF_PI * t
                DeckGains(
                    deckAGain = cos(angle).toFloat(),
                    deckBGain = sin(angle).toFloat(),
                )
            }
            AutoMixCurveType.SMOOTH_S_CURVE -> {
                val s = t * t * (3.0f - 2.0f * t)
                DeckGains(
                    deckAGain = 1.0f - s,
                    deckBGain = s,
                )
            }
            AutoMixCurveType.LINEAR -> {
                DeckGains(
                    deckAGain = 1.0f - t,
                    deckBGain = t,
                )
            }
            AutoMixCurveType.BASS_SWAP_CLUB -> {
                val angle = HALF_PI * t
                val midHighA = cos(angle).toFloat()
                val midHighB = sin(angle).toFloat()
                // Bass snap: Deck A owns 100% low end until 45%, equal-power swaps across [0.45..0.55], then Deck B owns 100%
                val swapT = ((t - 0.45f) / 0.10f).coerceIn(0.0f, 1.0f)
                val swapAngle = HALF_PI * swapT
                val bassA = cos(swapAngle).toFloat()
                val bassB = sin(swapAngle).toFloat()
                DeckGains(
                    deckAGain = midHighA,
                    deckBGain = midHighB,
                    bassDeckA = bassA,
                    bassDeckB = bassB,
                )
            }
            AutoMixCurveType.FAST_DROP -> {
                // Quick drop in the final 20% or quick fade in
                val a = if (t >= 0.8f) (1.0f - (t - 0.8f) / 0.2f).coerceIn(0f, 1f) else 1.0f
                val b = if (t >= 0.8f) 1.0f else (t / 0.8f).coerceIn(0f, 1f)
                DeckGains(
                    deckAGain = a,
                    deckBGain = b,
                )
            }
        }
    }
}
