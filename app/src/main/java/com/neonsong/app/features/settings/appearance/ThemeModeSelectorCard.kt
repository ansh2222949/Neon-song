package com.neonsong.app.features.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsong.app.R
import com.neonsong.app.core.designsystem.components.GroupPosition
import com.neonsong.app.core.designsystem.components.IconBadge
import com.neonsong.app.core.designsystem.components.groupShape
import com.neonsong.app.core.designsystem.components.settingHighlightGlow
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.ui.common.ConnectedButtonGroup
import com.neonsong.app.ui.common.ConnectedButtonItem
import com.neonsong.app.util.LocalHapticManager

@Composable
fun ThemeModeSelectorCard(
    currentThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val hapticManager = LocalHapticManager.current
    val shape = groupShape(position)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.38f),
                0.55f to Color.White.copy(alpha = 0.10f),
                1.0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Filled.BrightnessAuto,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_theme_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        stringResource(R.string.settings_theme_mode_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val items = listOf(
                ConnectedButtonItem(stringResource(R.string.theme_mode_system)),
                ConnectedButtonItem(stringResource(R.string.theme_mode_light)),
                ConnectedButtonItem(stringResource(R.string.theme_mode_dark)),
                ConnectedButtonItem(stringResource(R.string.theme_mode_amoled)),
            )
            val selectedIdx = when (currentThemeMode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
                ThemeMode.AMOLED -> 3
            }
            ConnectedButtonGroup(
                items = items,
                selectedIndex = selectedIdx,
                onSelect = { idx ->
                    hapticManager.click()
                    val mode = when (idx) {
                        0 -> ThemeMode.SYSTEM
                        1 -> ThemeMode.LIGHT
                        2 -> ThemeMode.DARK
                        else -> ThemeMode.AMOLED
                    }
                    onSelectThemeMode(mode)
                },
            )
        }
    }
}

