package com.neonsong.app.features.settings.appearance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.Icon
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.util.LocalHapticManager

@Composable
fun AccentPresetGrid(
    currentMode: AccentMode,
    selectedHex: String?,
    onPickPreset: (String) -> Unit,
    onPickMono: () -> Unit,
    onPickCustom: () -> Unit,
) {
    fun isPresetSelected(hex: String) =
        currentMode == AccentMode.MANUAL && selectedHex?.equals(hex, ignoreCase = true) == true
    val customSelected = currentMode == AccentMode.MANUAL &&
        selectedHex != null &&
        ACCENT_PRESETS.none { it.hex.equals(selectedHex, ignoreCase = true) }
    val monoSelected = currentMode == AccentMode.MONOCHROME

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        ACCENT_PRESETS.take(4).forEach { preset ->
            ColorTile(
                label = preset.name,
                selected = isPresetSelected(preset.hex),
                modifier = Modifier.weight(1f),
                onClick = { onPickPreset(preset.hex) },
            ) {
                PaletteTilePreview(preset.hex)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        ACCENT_PRESETS.drop(4).forEach { preset ->
            ColorTile(
                label = preset.name,
                selected = isPresetSelected(preset.hex),
                modifier = Modifier.weight(1f),
                onClick = { onPickPreset(preset.hex) },
            ) {
                PaletteTilePreview(preset.hex)
            }
        }
        ColorTile(
            label = "Mono",
            selected = monoSelected,
            modifier = Modifier.weight(1f),
            onClick = onPickMono,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Contrast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        ColorTile(
            label = "Custom",
            selected = customSelected,
            modifier = Modifier.weight(1f),
            onClick = onPickCustom,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (customSelected && selectedHex != null) {
                            try {
                                Color(android.graphics.Color.parseColor(selectedHex))
                            } catch (_: Throwable) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ColorLens,
                    contentDescription = null,
                    tint = if (customSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun accentShades(hex: String): List<Color> {
    val base = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Throwable) {
        return List(4) { Color.Gray }
    }
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
    fun shade(saturation: Float, value: Float): Color {
        val s = (hsv[1] * saturation).coerceIn(0f, 1f)
        val v = (hsv[2] * value).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], s, v)))
    }
    return listOf(
        shade(0.70f, 1.00f),
        base,
        shade(1.05f, 0.78f),
        shade(1.10f, 0.45f),
    )
}

@Composable
fun PaletteTilePreview(hex: String) {
    val shades = remember(hex) { accentShades(hex) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxHeight().weight(1f).background(shades[0]))
            Box(Modifier.fillMaxHeight().weight(1f).background(shades[1]))
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxHeight().weight(1f).background(shades[2]))
            Box(Modifier.fillMaxHeight().weight(1f).background(shades[3]))
        }
    }
}

@Composable
fun ColorTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    val hapticManager = LocalHapticManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 6.dp else 2.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tileElevation",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "tileBorder",
    )
    val tileShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier.scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(elevation = elevation, shape = tileShape, clip = false)
                .clip(tileShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        hapticManager.click()
                        onClick()
                    },
                )
                .border(2.5.dp, borderColor, tileShape),
            contentAlignment = Alignment.Center,
        ) {
            content(selected)
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
