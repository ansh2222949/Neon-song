package com.neonsong.app.data.repository

import com.neonsong.app.core.contracts.AudioSettingsRepository
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.EqualizerSettings
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.SettingsPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioSettingsRepository @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
    private val equalizerPreferences: EqualizerPreferences
) : AudioSettingsRepository {

    override val miscSettings: Flow<MiscSettings> = settingsPreferences.settings

    override val equalizerSettings: Flow<EqualizerSettings> = equalizerPreferences.settings

    override suspend fun setPreferLosslessStreaming(enabled: Boolean) {
        settingsPreferences.setPreferLosslessStreaming(enabled)
    }

    override suspend fun setEqualizerEnabled(enabled: Boolean) {
        equalizerPreferences.setEnabled(enabled)
    }

    override suspend fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        equalizerPreferences.setBandGain(bandIndex, gainDb)
    }

    override suspend fun resetEqualizer() {
        equalizerPreferences.reset()
    }
}
