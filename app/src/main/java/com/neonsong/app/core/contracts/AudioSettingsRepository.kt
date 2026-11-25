package com.neonsong.app.core.contracts

import com.neonsong.app.data.local.EqualizerSettings
import com.neonsong.app.data.local.MiscSettings
import kotlinx.coroutines.flow.Flow

/**
 * Audio settings and DSP preferences contract.
 * Exposes audio quality, lossless streaming preferences, and equalizer controls.
 */
interface AudioSettingsRepository {
    /** Stream of general application and audio streaming preferences. */
    val miscSettings: Flow<MiscSettings>

    /** Stream of active equalizer settings (preset, band gains, enabled flag). */
    val equalizerSettings: Flow<EqualizerSettings>

    /** Toggle lossless/Hi-Res preferred stream resolution. */
    suspend fun setPreferLosslessStreaming(enabled: Boolean)

    /** Toggle parametric equalizer active state. */
    suspend fun setEqualizerEnabled(enabled: Boolean)

    /** Set specific equalizer band gain in decibels. */
    suspend fun setEqualizerBandGain(bandIndex: Int, gainDb: Float)

    /** Reset equalizer to flat baseline. */
    suspend fun resetEqualizer()
}
