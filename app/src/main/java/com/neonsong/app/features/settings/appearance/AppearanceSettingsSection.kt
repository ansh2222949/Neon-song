package com.neonsong.app.features.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neonsong.app.R
import com.neonsong.app.core.designsystem.components.SectionLabel
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.repository.ThemeUiState
import com.neonsong.app.ui.settings.SettingsActionCard
import com.neonsong.app.ui.settings.SettingsGroup
import com.neonsong.app.ui.settings.SettingsPhotoCard
import com.neonsong.app.ui.settings.SettingsToggleCard

@Composable
fun AppearanceContent(
    themeUiState: ThemeUiState?,
    miscSettings: MiscSettings,
    highlightedSettingId: String?,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetAmoled: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDynamicNowPlaying: (Boolean) -> Unit,
    onSetUseCustomFont: (Boolean) -> Unit,
    onOpenHomeSections: () -> Unit,
    onPickSettingsPhoto: () -> Unit,
    onRemoveSettingsPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        SectionLabel(stringResource(R.string.settings_section_appearance))
        AppearanceStudioHero(
            themeMode = themeUiState?.themeMode ?: ThemeMode.SYSTEM,
            dynamicColorsEnabled = themeUiState?.mode == AccentMode.DYNAMIC,
            liquidGlassEnabled = true,
            nowPlayingColorsEnabled = miscSettings.dynamicNowPlayingEnabled,
        )
        SettingsGroup(7) { index, position ->
            when (index) {
                0 -> ThemeModeSelectorCard(
                    currentThemeMode = themeUiState?.themeMode ?: ThemeMode.SYSTEM,
                    onSelectThemeMode = onSelectThemeMode,
                    position = position,
                    isHighlighted = (highlightedSettingId == "appearance.theme_mode"),
                )
                1 -> SettingsToggleCard(
                    icon = Icons.Filled.Contrast,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "Full AMOLED Black",
                    subtitle = "Pitch black surfaces & true zero-power OLED pixels",
                    checked = (themeUiState?.amoled ?: false) || themeUiState?.themeMode == ThemeMode.AMOLED,
                    onCheckedChange = onSetAmoled,
                    position = position,
                    enabled = themeUiState?.themeMode != ThemeMode.LIGHT,
                    isHighlighted = (highlightedSettingId == "appearance.amoled")
                )
                2 -> SettingsToggleCard(
                    icon = Icons.Filled.Palette,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_sub),
                    checked = themeUiState?.mode == AccentMode.DYNAMIC,
                    onCheckedChange = onSetDynamicColor,
                    position = position,
                    isHighlighted = (highlightedSettingId == "appearance.dynamic_color")
                )
                3 -> SettingsToggleCard(
                    icon = Icons.Filled.Album,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = stringResource(R.string.settings_dynamic_now_playing),
                    subtitle = stringResource(R.string.settings_dynamic_now_playing_sub),
                    checked = miscSettings.dynamicNowPlayingEnabled,
                    onCheckedChange = onSetDynamicNowPlaying,
                    position = position,
                    isHighlighted = (highlightedSettingId == "appearance.dynamic_now_playing")
                )
                4 -> SettingsToggleCard(
                    icon = Icons.Filled.TextFields,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.settings_app_font),
                    subtitle = stringResource(R.string.settings_app_font_sub),
                    checked = miscSettings.useCustomFont,
                    onCheckedChange = onSetUseCustomFont,
                    position = position,
                    isHighlighted = (highlightedSettingId == "appearance.app_font")
                )
                5 -> SettingsPhotoCard(
                    photoUri = miscSettings.settingsPhotoUri,
                    onClick = onPickSettingsPhoto,
                    onRemove = onRemoveSettingsPhoto,
                    position = position,
                    isHighlighted = (highlightedSettingId == "appearance.settings_photo")
                )
                else -> SettingsActionCard(
                    icon = Icons.Filled.Dashboard,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.settings_home_sections),
                    subtitle = run {
                        val total = com.neonsong.app.data.local.HomeSection.entries.size
                        val visible = total - miscSettings.hiddenHomeSections.size
                        stringResource(R.string.home_sections_visible, visible, total)
                    },
                    onClick = onOpenHomeSections,
                    position = position,
                    isHighlighted = (highlightedSettingId == "appearance.home_sections")
                )
            }
        }
    }
}
