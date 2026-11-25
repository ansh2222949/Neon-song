package com.neonsong.app.core.contracts

import androidx.compose.ui.graphics.Color
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.repository.ThemeUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Visual styling and theme contract.
 * Exposes dynamic color, liquid glass, and dark mode state to presentation components.
 */
interface ThemeProvider {
    /** Stream of current resolved theme UI state. */
    val uiState: StateFlow<ThemeUiState>

    /** Set theme mode (SYSTEM, LIGHT, DARK). */
    suspend fun setThemeMode(mode: ThemeMode)

    /** Set manual accent color. */
    suspend fun setManualAccent(color: Color)

    /** Set accent selection mode. */
    suspend fun setMode(mode: AccentMode)

    /** Toggle pure black AMOLED mode for OLED displays. */
    suspend fun setAmoled(enabled: Boolean)

    /** Toggle liquid glass blur and specular sheen rendering. */
    suspend fun setLiquidGlass(enabled: Boolean)

    /** Toggle dynamic artwork-driven accent colors. */
    suspend fun setDynamicNowPlaying(enabled: Boolean)
}
