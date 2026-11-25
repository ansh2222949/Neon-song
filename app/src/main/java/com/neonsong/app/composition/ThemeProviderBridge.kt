package com.neonsong.app.composition

import androidx.compose.ui.graphics.Color
import com.neonsong.app.core.contracts.ThemeProvider
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.repository.ThemeRepository
import com.neonsong.app.data.repository.ThemeUiState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composition bridge binding [ThemeRepository] to [ThemeProvider] contract.
 */
@Singleton
class ThemeProviderBridge @Inject constructor(
    private val themeRepository: ThemeRepository
) : ThemeProvider {

    override val uiState: StateFlow<ThemeUiState>
        get() = themeRepository.uiState

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeRepository.setThemeMode(mode)
    }

    override suspend fun setManualAccent(color: Color) {
        themeRepository.setManualAccent(color)
    }

    override suspend fun setMode(mode: AccentMode) {
        themeRepository.setMode(mode)
    }

    override suspend fun setAmoled(enabled: Boolean) {
        themeRepository.setAmoled(enabled)
    }

    override suspend fun setLiquidGlass(enabled: Boolean) {
        themeRepository.setLiquidGlass(enabled)
    }

    override suspend fun setDynamicNowPlaying(enabled: Boolean) {
        themeRepository.setDynamicNowPlaying(enabled)
    }
}
