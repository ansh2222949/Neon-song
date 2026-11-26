package com.neonsong.app.features.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsong.app.R
import com.neonsong.app.core.designsystem.components.GroupPosition
import com.neonsong.app.core.designsystem.components.IconBadge
import com.neonsong.app.core.designsystem.components.groupShape
import com.neonsong.app.core.designsystem.components.settingHighlightGlow
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.repository.ThemeUiState
import com.neonsong.app.ui.settings.SettingsActionCard
import com.neonsong.app.ui.settings.SettingsGlassSection
import com.neonsong.app.ui.settings.SettingsGroup
import com.neonsong.app.ui.settings.SettingsPhotoCard
import com.neonsong.app.ui.settings.SettingsToggleCard
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.MAX_CORNER_SCALE
import com.neonsong.app.ui.theme.MIN_CORNER_SCALE
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.clampCornerScale
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.util.LocalHapticManager
import kotlin.math.roundToInt

@Composable
fun AppearanceContent(
    themeUiState: ThemeUiState?,
    miscSettings: MiscSettings,
    highlightedSettingId: String?,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSetAmoled: (Boolean) -> Unit,
    onSetLiquidGlass: (Boolean) -> Unit,
    onSetCornerScale: (Float) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDynamicNowPlaying: (Boolean) -> Unit,
    onSetUseCustomFont: (Boolean) -> Unit,
    onOpenHomeSections: () -> Unit,
    onPickSettingsPhoto: () -> Unit,
    onRemoveSettingsPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liquidGlassEnabled = themeUiState?.liquidGlass ?: true
    val cornerScale = clampCornerScale(themeUiState?.cornerScale ?: 1.0f)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        SettingsGlassSection(
            title = stringResource(R.string.settings_section_appearance),
            subtitle = "Theme mode, Liquid Glass, corner geometry & dynamic accents",
        ) {
            SettingsGroup(9) { index, position ->
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
                        icon = Icons.Filled.AutoAwesome,
                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = stringResource(R.string.settings_liquid_glass),
                        subtitle = if (liquidGlassEnabled) {
                            stringResource(R.string.settings_liquid_glass_sub)
                        } else {
                            "Standard opaque Material surfaces & crisp solid borders"
                        },
                        checked = liquidGlassEnabled,
                        onCheckedChange = onSetLiquidGlass,
                        position = position,
                        isHighlighted = (highlightedSettingId == "appearance.liquid_glass")
                    )
                    3 -> CornerScaleSliderCard(
                        scale = cornerScale,
                        onScaleChange = onSetCornerScale,
                        position = com.neonsong.app.core.designsystem.components.GroupPosition.valueOf(position.name),
                        isHighlighted = (highlightedSettingId == "appearance.corner_scale"),
                    )
                    4 -> SettingsToggleCard(
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
                    5 -> SettingsToggleCard(
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
                    6 -> SettingsToggleCard(
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
                    7 -> SettingsPhotoCard(
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
}

@Composable
private fun CornerScaleSliderCard(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    position: GroupPosition,
    isHighlighted: Boolean,
) {
    val hapticManager = LocalHapticManager.current
    var sliderValue by remember { mutableFloatStateOf(clampCornerScale(scale)) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(scale) {
        if (!isDragging) sliderValue = clampCornerScale(scale)
    }
    val shape = remember(position) { groupShape(position) }
    val percent = (sliderValue * 100f).roundToInt()
    val descriptor = when {
        percent <= 75 -> "Crisp geometric edges"
        percent < 95 -> "Subtle rounded corners"
        percent in 95..105 -> "Balanced studio squircles"
        percent <= 125 -> "Soft expressive curves"
        else -> "Ultra-round pill geometry"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = shape,
                preset = LiquidGlassPreset.Card,
                accentTint = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Unspecified,
            )
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Filled.RoundedCorner,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Corner Radius Scale",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "$percent% • $descriptor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = SquircleShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { raw ->
                    isDragging = true
                    val snapped = ((raw * 20f).roundToInt() / 20f).coerceIn(MIN_CORNER_SCALE, MAX_CORNER_SCALE)
                    if (snapped != sliderValue) {
                        hapticManager.sliderDetent()
                        sliderValue = snapped
                        onScaleChange(snapped)
                    }
                },
                onValueChangeFinished = { isDragging = false },
                valueRange = MIN_CORNER_SCALE..MAX_CORNER_SCALE,
                steps = 15,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}
