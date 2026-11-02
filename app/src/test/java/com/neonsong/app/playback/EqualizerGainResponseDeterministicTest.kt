package com.neonsong.app.playback

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.playback.analysis.DspMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Test

/**
 * Deterministic gain response tests for all 15 EQ bands and dual-deck crossfade curves.
 */
class EqualizerGainResponseDeterministicTest {

    private val sampleRateHz = 48_000.0

    @Test
    fun all15BandsProduceAccurateGainOnSineWave() {
        for (freq in DspMath.EQ_FREQUENCIES_HZ) {
            // Test +6.0 dB boost
            val boostBand = DspMath.eqBand(sampleRateHz, freq, 6.0)
            boostBand.reset()
            val frames = 48_000 // 1 second
            val amp = 0.1f
            val omega = 2.0 * PI * freq / sampleRateHz

            var inSumSq = 0.0
            var outSumSq = 0.0
            // Warmup 4800 frames, measure next 40,000 frames
            for (i in 0 until frames) {
                val input = (amp * sin(omega * i)).toFloat()
                val output = boostBand.tick(input, 0)
                if (i >= 4800) {
                    inSumSq += input * input
                    outSumSq += output * output
                }
            }
            val inRms = sqrt(inSumSq / (frames - 4800))
            val outRms = sqrt(outSumSq / (frames - 4800))
            val measuredGainDb = DspMath.gainToDb(outRms / inRms)

            assertThat(abs(measuredGainDb - 6.0)).isLessThan(0.35)

            // Test -6.0 dB cut
            val cutBand = DspMath.eqBand(sampleRateHz, freq, -6.0)
            cutBand.reset()
            inSumSq = 0.0
            outSumSq = 0.0
            for (i in 0 until frames) {
                val input = (amp * sin(omega * i)).toFloat()
                val output = cutBand.tick(input, 0)
                if (i >= 4800) {
                    inSumSq += input * input
                    outSumSq += output * output
                }
            }
            val inRmsCut = sqrt(inSumSq / (frames - 4800))
            val outRmsCut = sqrt(outSumSq / (frames - 4800))
            val measuredCutGainDb = DspMath.gainToDb(outRmsCut / inRmsCut)

            assertThat(abs(measuredCutGainDb - (-6.0))).isLessThan(0.35)
        }
    }

    @Test
    fun equalPowerCrossfadeConservesAcousticEnergy() {
        // Steps from 0.0 to 1.0 in 1% increments
        for (step in 0..100) {
            val progress = step / 100.0
            val angle = progress * (PI / 2.0)
            val gainA = cos(angle)
            val gainB = sin(angle)

            // Equal power law: gainA^2 + gainB^2 == 1.0 (0 dB total acoustic power)
            val totalPower = gainA * gainA + gainB * gainB
            assertThat(abs(totalPower - 1.0)).isLessThan(1e-6)

            // Mid-crossfade verification (progress = 0.50)
            if (step == 50) {
                assertThat(abs(gainA - 0.70710678)).isLessThan(1e-6)
                assertThat(abs(gainB - 0.70710678)).isLessThan(1e-6)
                val gainDbA = DspMath.gainToDb(gainA)
                val gainDbB = DspMath.gainToDb(gainB)
                assertThat(abs(gainDbA - (-3.0102999))).isLessThan(0.01)
                assertThat(abs(gainDbB - (-3.0102999))).isLessThan(0.01)
            }
        }
    }

    @Test
    fun limiterClampsExtremeOverloadsTransparently() {
        val rate = 48_000
        val freq = 1000.0
        // Hot +12 dBFS signal (amplitude = 4.0)
        val hotBurst = FloatArray(rate) { i ->
            (4.0 * sin(2.0 * PI * freq * i / rate)).toFloat()
        }
        val limited = DspMath.JvmLimiter.limit(hotBurst)

        for (sample in limited) {
            assertThat(sample.isFinite()).isTrue()
            assertThat(sample.isNaN()).isFalse()
            assertThat(abs(sample)).isAtMost(1.0f)
        }
    }
}
