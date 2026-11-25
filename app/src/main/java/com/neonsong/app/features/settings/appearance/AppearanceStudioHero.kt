package com.neonsong.app.features.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neonsong.app.core.designsystem.components.StatusPill
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassChrome

@Composable
fun AppearanceStudioHero(
    themeMode: ThemeMode,
    dynamicColorsEnabled: Boolean,
    liquidGlassEnabled: Boolean,
    nowPlayingColorsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val shape = SquircleShape(28.dp)
    val themeModeLabel = when (themeMode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.AMOLED -> "AMOLED"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = shape,
                preset = LiquidGlassPreset.Card,
                accentTint = tertiary.copy(alpha = 0.22f),
            )
            .background(
                Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.16f),
                        tertiary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.12f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = SquircleShape(16.dp),
                    color = tertiary.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Visual Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (dynamicColorsEnabled) {
                            if (nowPlayingColorsEnabled) "Dynamic palette • artwork accents" else "Dynamic palette • system accents"
                        } else {
                            if (nowPlayingColorsEnabled) "Custom palette • artwork accents" else "Custom palette • your chosen accent"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    label = "$themeModeLabel theme",
                    tint = primary,
                )
                StatusPill(
                    label = if (liquidGlassEnabled) "Liquid Glass on" else "Liquid Glass off",
                    tint = if (liquidGlassEnabled) tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

