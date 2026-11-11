package com.neonsong.app.playback

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.EQ_BAND_FREQS_HZ
import com.neonsong.app.data.local.EQ_MAX_GAIN_DB
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.EqualizerPresets
import com.neonsong.app.data.local.eqBandLabel
import org.junit.Test

class Equalizer16BandTest {

    @Test
    fun equalizerHasExactly16StandardIsoBandsSpanningFullAudibleSpectrum() {
        assertThat(EQ_BAND_FREQS_HZ.size).isEqualTo(16)
        assertThat(EQ_BAND_FREQS_HZ.first()).isEqualTo(20)
        assertThat(EQ_BAND_FREQS_HZ.last()).isEqualTo(20000)

        // Verify strictly monotonic ordering
        for (i in 0 until EQ_BAND_FREQS_HZ.lastIndex) {
            assertThat(EQ_BAND_FREQS_HZ[i]).isLessThan(EQ_BAND_FREQS_HZ[i + 1])
        }
    }

    @Test
    fun bandLabelsFormatCorrectlyForStudioMastering() {
        assertThat(eqBandLabel(20)).isEqualTo("20")
        assertThat(eqBandLabel(63)).isEqualTo("63")
        assertThat(eqBandLabel(100)).isEqualTo("100")
        assertThat(eqBandLabel(1000)).isEqualTo("1K")
        assertThat(eqBandLabel(1600)).isEqualTo("1.6K")
        assertThat(eqBandLabel(2500)).isEqualTo("2.5K")
        assertThat(eqBandLabel(6300)).isEqualTo("6.3K")
        assertThat(eqBandLabel(10000)).isEqualTo("10K")
        assertThat(eqBandLabel(16000)).isEqualTo("16K")
        assertThat(eqBandLabel(20000)).isEqualTo("20K")
    }

    @Test
    fun legacy15BandCurveMigratesTo16BandsSmoothlyWithoutDataLoss() {
        val legacy15Gains = listOf(
            2.6f, 2.8f, 2.2f, 0.6f, -1.8f,
            -2.6f, -1.2f, 0.0f, 1.2f, 2.4f,
            3.6f, 4.0f, 4.2f, 4.5f, 4.8f,
        )

        val migrated = EqualizerPreferences.migrateGains(legacy15Gains)
        assertThat(migrated.size).isEqualTo(16)

        // 20 Hz takes the 25 Hz sub-bass value
        assertThat(migrated[0]).isEqualTo(2.6f)
        // 40 Hz to 16 kHz are exact 1-to-1 matches
        for (i in 1..14) {
            assertThat(migrated[i]).isEqualTo(legacy15Gains[i])
        }
        // 20 kHz takes 16 kHz air extension
        assertThat(migrated[15]).isEqualTo(4.8f)
    }

    @Test
    fun arbitrarySizeOrCorruptedGainsAreSafelyNormalized() {
        val corrupted = listOf(Float.NaN, Float.POSITIVE_INFINITY, 12.5f, -15.0f)
        val normalized = EqualizerPreferences.migrateGains(corrupted)

        assertThat(normalized.size).isEqualTo(16)
        for (gain in normalized) {
            assertThat(gain.isFinite()).isTrue()
            assertThat(gain).isAtLeast(-EQ_MAX_GAIN_DB)
            assertThat(gain).isAtMost(EQ_MAX_GAIN_DB)
        }
    }

    @Test
    fun allPresetsHaveExactly16BandsAndComplyWithHeadroomBounds() {
        assertThat(EqualizerPresets.ALL).isNotEmpty()

        for (preset in EqualizerPresets.ALL) {
            assertThat(preset.gainsDb.size).isEqualTo(16)

            for ((index, gain) in preset.gainsDb.withIndex()) {
                assertThat(gain.isFinite()).isTrue()
                assertThat(gain).isAtLeast(-EQ_MAX_GAIN_DB)
                assertThat(gain).isAtMost(EQ_MAX_GAIN_DB)
            }
        }
    }

    @Test
    fun approximateCurvesAreNotMisrepresentedAsMeasuredAutoEqProfiles() {
        val approximate = EqualizerPresets.byName("Bass Reduction (Approx.)")
        assertThat(approximate).isNotNull()
        assertThat(approximate?.isAutoEq).isFalse()
        assertThat(approximate?.gainsDb?.size).isEqualTo(16)

        assertThat(EqualizerPresets.byName("AutoEq Sony WH-1000XM4/XM5"))
            .isSameInstanceAs(approximate)
        assertThat(EqualizerPresets.byName("AutoEq Apple AirPods Pro")?.name)
            .isEqualTo("Airy Bass (Approx.)")
    }
}
