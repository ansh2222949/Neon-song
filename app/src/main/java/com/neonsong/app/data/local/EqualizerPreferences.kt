package com.neonsong.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The 16 standard ISO 2/3-octave center frequencies (Hz) the equalizer's
 * band sliders map onto, spanning full 20 Hz (sub-bass foundation) to
 * 20,000 Hz (ultra-air presence).
 */
val EQ_BAND_FREQS_HZ = intArrayOf(
    20, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000, 20000
)
const val EQ_MAX_GAIN_DB = 8f

/** Compact frequency label under each band slider ("20", "63", "1K", "6.3K", "16K", "20K"...). */
fun eqBandLabel(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000}K"
    hz >= 1000 -> {
        val kValue = hz / 1000.0
        if (kValue == kValue.toLong().toDouble()) "${kValue.toLong()}K"
        else "%.1fK".format(java.util.Locale.ROOT, kValue)
    }
    else -> "$hz"
}

data class EqPreset(
    val name: String,
    /** Gain in dB per band, same order as [EQ_BAND_FREQS_HZ]. */
    val gainsDb: List<Float>,
    val isAutoEq: Boolean = false,
)

/**
 * Curated 16-band tone presets. Measured AutoEq profiles are loaded separately
 * through AutoEqCatalog; these built-in curves are not headphone measurements.
 * Values are strictly bounded in the ±8 dB range to prevent digital clipping while
 * maximizing dynamic headroom.
 */
object EqualizerPresets {
    const val CUSTOM_NAME = "Custom"

    val FLAT = EqPreset("Default", List(EQ_BAND_FREQS_HZ.size) { 0f })

    /**
     * Crystal-open audiophile reference curve for Studio Master Clarity.
     * Tight, controlled sub-bass depth, deliberate low-mid mud reduction,
     * natural vocal body, strong presence lift and a silky 20kHz air shelf.
     */
    val STUDIO_MASTER = EqPreset(
        "Studio Master",
        listOf(
            2.4f, 2.8f, 2.2f, 0.6f, -1.8f,
            -2.6f, -1.2f, 0.0f, 1.2f, 2.4f,
            3.6f, 4.0f, 4.2f, 4.5f, 4.8f, 5.0f
        ),
    )

    // Standard Genre & Character Presets (16 bands)
    val ACOUSTIC = EqPreset("Acoustic", listOf(2.8f, 2.5f, 2.0f, 1.5f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 1.0f, 1.0f, 1.5f, 1.5f, 1.8f))
    val VOCAL_CLARITY = EqPreset("Vocal Clarity", listOf(-1.8f, -1.0f, -0.5f, 0f, 0.5f, 1.0f, 2.0f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.5f, 1.0f, 0.5f))
    val CLASSICAL = EqPreset("Classical", listOf(2.8f, 2.5f, 2.0f, 1.5f, 0.5f, 0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f, 3.2f))
    val JAZZ = EqPreset("Jazz", listOf(2.2f, 2.0f, 1.5f, 1.0f, 0f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.0f, 2.5f, 2.5f, 2.5f, 2.6f))
    val ROCK = EqPreset("Rock", listOf(3.8f, 3.5f, 3.0f, 1.5f, 0f, -1.0f, -1.0f, -0.5f, 0.5f, 1.5f, 2.5f, 3.0f, 3.5f, 4.0f, 4.0f, 4.2f))
    val POP = EqPreset("Pop", listOf(-1.2f, -0.5f, 0.0f, 1.0f, 1.5f, 2.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 2.8f))
    val TREBLE_AIR = EqPreset("Treble Air", listOf(0f, 0f, 0f, -0.5f, -0.5f, -0.5f, 0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 3.5f, 4.5f, 5.0f, 5.5f))
    val DEEP_BASS = EqPreset("Deep Bass Clean", listOf(5.5f, 4.5f, 3.5f, 2.5f, 1.0f, 0f, -0.5f, -0.5f, 0f, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f, 0.5f))
    val ELECTRONIC = EqPreset("Electronic", listOf(4.8f, 4.0f, 3.0f, 2.0f, 0.5f, -0.5f, 0f, 1.0f, 2.0f, 2.5f, 2.5f, 2.5f, 3.0f, 3.5f, 4.0f, 4.5f))
    val R_AND_B = EqPreset("R&B", listOf(5.2f, 4.5f, 3.5f, 2.0f, 1.0f, -0.5f, -1.0f, -0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f, 3.0f, 3.2f))

    // Approximate tone curves. Keep the legacy names resolvable for saved preferences.
    val AUTOEQ_HARMAN_OVER_EAR = EqPreset(
        "Harman-Style Over-Ear (Approx.)",
        listOf(4.2f, 4.0f, 3.4f, 2.0f, 0.5f, -0.2f, -0.5f, -0.4f, 0.0f, 0.6f, 1.5f, 2.2f, 1.8f, 0.5f, -0.8f, -1.2f),
    )
    val AUTOEQ_HARMAN_IN_EAR = EqPreset(
        "Harman-Style In-Ear (Approx.)",
        listOf(5.6f, 5.2f, 4.2f, 2.5f, 0.8f, 0.0f, -0.6f, -0.5f, 0.0f, 0.8f, 2.0f, 3.2f, 2.4f, 1.0f, -0.5f, -1.0f),
    )
    val AUTOEQ_SONY_XM4_XM5 = EqPreset(
        "Bass Reduction (Approx.)",
        listOf(-1.5f, -2.2f, -2.8f, -2.4f, -1.2f, 0.2f, 0.6f, 0.8f, 0.5f, 1.2f, 2.5f, 3.6f, 2.8f, 1.8f, 2.2f, 2.5f),
    )
    val AUTOEQ_AIRPODS_PRO = EqPreset(
        "Airy Bass (Approx.)",
        listOf(2.5f, 2.2f, 1.5f, 0.6f, -0.2f, -0.5f, -0.2f, 0.0f, 0.2f, 0.5f, 1.0f, 0.8f, -1.2f, 0.5f, 1.8f, 2.0f),
    )
    val AUTOEQ_SENNHEISER_HD600 = EqPreset(
        "Sub-Bass Lift (Approx.)",
        listOf(5.5f, 4.8f, 3.2f, 1.5f, 0.4f, -0.2f, -0.4f, -0.2f, 0.0f, 0.2f, -0.5f, -1.2f, 0.5f, 1.2f, 1.5f, 1.8f),
    )
    val AUTOEQ_ATH_M50X = EqPreset(
        "Midrange Adjustment (Approx.)",
        listOf(0.5f, -0.5f, -1.8f, -2.2f, -1.2f, 0.2f, 0.5f, 0.6f, 0.2f, 0.8f, 1.5f, 0.6f, -1.8f, 0.5f, 1.2f, 1.5f),
    )
    val AUTOEQ_MOONDROP = EqPreset(
        "Neutral Tilt (Approx.)",
        listOf(1.8f, 1.5f, 0.8f, 0.2f, -0.2f, -0.4f, -0.2f, 0.0f, 0.2f, 0.4f, -0.5f, -0.8f, 0.8f, 1.5f, 2.0f, 2.2f),
    )

    val GENRE_PRESETS: List<EqPreset> = listOf(
        FLAT,
        STUDIO_MASTER,
        ACOUSTIC,
        VOCAL_CLARITY,
        CLASSICAL,
        JAZZ,
        ROCK,
        POP,
        TREBLE_AIR,
        DEEP_BASS,
        ELECTRONIC,
        R_AND_B,
    )

    val AUTOEQ_APPROX_PRESETS: List<Pair<EqPreset, String>> = listOf(
        AUTOEQ_AIRPODS_PRO to "Apple AirPods Pro",
        AUTOEQ_SONY_XM4_XM5 to "Sony WH-1000XM4/XM5",
        AUTOEQ_SENNHEISER_HD600 to "Sennheiser HD600/650",
        AUTOEQ_ATH_M50X to "Audio-Technica ATH-M50x",
        AUTOEQ_MOONDROP to "Moondrop Harman Target",
        AUTOEQ_HARMAN_OVER_EAR to "Harman Over-Ear Target",
        AUTOEQ_HARMAN_IN_EAR to "Harman In-Ear Target",
    )

    val ALL: List<EqPreset> = GENRE_PRESETS + listOf(
        AUTOEQ_HARMAN_OVER_EAR,
        AUTOEQ_HARMAN_IN_EAR,
        AUTOEQ_SONY_XM4_XM5,
        AUTOEQ_AIRPODS_PRO,
        AUTOEQ_SENNHEISER_HD600,
        AUTOEQ_ATH_M50X,
        AUTOEQ_MOONDROP,
    )

    private val LEGACY_NAMES = mapOf(
        "AutoEq Harman Over-Ear" to AUTOEQ_HARMAN_OVER_EAR,
        "AutoEq Harman In-Ear" to AUTOEQ_HARMAN_IN_EAR,
        "AutoEq Sony WH-1000XM4/XM5" to AUTOEQ_SONY_XM4_XM5,
        "AutoEq Apple AirPods Pro" to AUTOEQ_AIRPODS_PRO,
        "AutoEq Sennheiser HD600/650" to AUTOEQ_SENNHEISER_HD600,
        "AutoEq ATH-M50x" to AUTOEQ_ATH_M50X,
        "AutoEq Moondrop Aria/Chu" to AUTOEQ_MOONDROP,
    )

    fun byName(name: String): EqPreset? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: LEGACY_NAMES.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetName: String = EqualizerPresets.FLAT.name,
    /** Per-band gain in dB, index-aligned with [EQ_BAND_FREQS_HZ]. */
    val gainsDb: List<Float> = EqualizerPresets.FLAT.gainsDb,
)

@Singleton
class EqualizerPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("lw_eq_enabled")
        val PRESET_NAME = stringPreferencesKey("lw_eq_preset")
        val GAINS_DB = stringPreferencesKey("lw_eq_gains")
    }

    val settings: Flow<EqualizerSettings> = dataStore.data
        .recoverPreferences("EqualizerPreferences")
        .map { p ->
            val storedGains = p.readSafely(Keys.GAINS_DB)?.split(',')
                ?.mapNotNull(String::toFloatOrNull)
                ?.let { migrateGains(it) }

            val storedName = p.readSafely(Keys.PRESET_NAME)
            val resolvedName = if (storedName == EqualizerPresets.CUSTOM_NAME) {
                storedName
            } else {
                storedName?.let(EqualizerPresets::byName)?.name ?: EqualizerPresets.FLAT.name
            }
            val gains = storedGains
                ?: EqualizerPresets.byName(resolvedName)?.gainsDb
                ?: EqualizerPresets.FLAT.gainsDb
            EqualizerSettings(
                enabled = p.readSafely(Keys.ENABLED) ?: false,
                presetName = resolvedName,
                gainsDb = gains,
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun applyPreset(preset: EqPreset) {
        dataStore.edit {
            it[Keys.PRESET_NAME] = preset.name
            it[Keys.GAINS_DB] = encodeGains(preset.gainsDb)
            it[Keys.ENABLED] = true
        }
    }

    suspend fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in EQ_BAND_FREQS_HZ.indices) return
        dataStore.edit {
            val current = it.readSafely(Keys.GAINS_DB)?.split(',')
                ?.mapNotNull(String::toFloatOrNull)
                ?.let { raw -> migrateGains(raw) }
                ?: EqualizerPresets.FLAT.gainsDb
            val safeGain = if (gainDb.isFinite()) gainDb.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
            val next = current.toMutableList().also { list -> list[bandIndex] = safeGain }
            it[Keys.PRESET_NAME] = EqualizerPresets.CUSTOM_NAME
            it[Keys.GAINS_DB] = encodeGains(next)
        }
    }

    suspend fun setAllBands(gainsDb: List<Float>, presetName: String = EqualizerPresets.CUSTOM_NAME) {
        dataStore.edit {
            it[Keys.PRESET_NAME] = presetName
            it[Keys.GAINS_DB] = encodeGains(migrateGains(gainsDb))
        }
    }

    suspend fun reset() {
        applyPreset(EqualizerPresets.FLAT)
    }

    companion object {
        fun migrateGains(raw: List<Float>): List<Float> {
            if (raw.size == EQ_BAND_FREQS_HZ.size) {
                return raw.map { if (it.isFinite()) it.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f }
            }
            if (raw.size == 15) {
                // Smoothly migrate legacy 15-band curve to 16 bands:
                // Legacy bands: 25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000
                // New bands:    20, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000, 20000
                return buildList(EQ_BAND_FREQS_HZ.size) {
                    add(if (raw[0].isFinite()) raw[0].coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f)
                    for (i in 1..14) {
                        val g = raw[i]
                        add(if (g.isFinite()) g.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f)
                    }
                    add(if (raw[14].isFinite()) raw[14].coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f)
                }
            }
            return List(EQ_BAND_FREQS_HZ.size) { index ->
                val g = raw.getOrElse(index) { 0f }
                if (g.isFinite()) g.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
            }
        }
    }

    private fun encodeGains(gains: List<Float>): String =
        gains.joinToString(",") { gain ->
            "%.1f".format(java.util.Locale.ROOT, if (gain.isFinite()) gain.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f)
        }
}
