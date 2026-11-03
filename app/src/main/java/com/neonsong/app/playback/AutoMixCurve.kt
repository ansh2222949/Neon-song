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

    /**
     * Calculates the instantaneous volume gains for Deck A (outgoing) and Deck B (incoming)
     * at normalized progress [t] in [0.0..1.0].
     */
    fun calculateGains(progress: Float, type: AutoMixCurveType = AutoMixCurveType.EQUAL_POWER): DeckGains {
        val t = progress.coerceIn(0.0f, 1.0f)

        return when (type) {
            AutoMixCurveType.EQUAL_POWER -> {
                // Total acoustic power L^2 + R^2 = cos^2(pi/2 * t) + sin^2(pi/2 * t) == 1.0
                val angle = (PI / 2.0) * t
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
                val angle = (PI / 2.0) * t
                val midHighA = cos(angle).toFloat()
                val midHighB = sin(angle).toFloat()
                // Bass snap: Deck A owns 100% low end until midpoint, then snaps to Deck B
                val bassA = if (t < 0.5f) 1.0f else (1.0f - (t - 0.5f) * 2f).coerceIn(0f, 1f)
                val bassB = if (t >= 0.5f) 1.0f else (t * 2f).coerceIn(0f, 1f)
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
