package com.neonsong.app.features.settings.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsong.app.core.designsystem.components.GroupPosition
import com.neonsong.app.core.designsystem.components.IconBadge
import com.neonsong.app.core.designsystem.components.groupShape
import com.neonsong.app.core.designsystem.components.settingHighlightGlow
import com.neonsong.app.util.LocalHapticManager
import kotlin.math.roundToInt

@Composable
fun AutomixBarsRow(
    bars: Int,
    onBarsChange: (Int) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = groupShape(position)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Filled.AutoAwesome,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Automix Transition Length", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        if (bars >= 16) "16 bars • Extended club blend" else "8 bars • Standard DJ transition",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = bars <= 8,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onBarsChange(8)
                    },
                    label = { Text("8 Bars (Standard)", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = bars >= 16,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onBarsChange(16)
                    },
                    label = { Text("16 Bars (Extended)", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun CrossfadeDurationRow(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
) {
    val hapticManager = LocalHapticManager.current
    var sliderValue by remember { mutableStateOf(seconds.coerceIn(1, 12).toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(seconds) {
        if (!isDragging) sliderValue = seconds.coerceIn(1, 12).toFloat()
    }
    val shape = groupShape(position)

    val blendStyle = when (sliderValue.roundToInt()) {
        in 1..2 -> "Quick DJ overlap"
        in 3..5 -> "Standard smooth blend"
        in 6..8 -> "Long musical transition"
        else -> "Extended cinematic crossfade"
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Filled.Tune,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Crossfade duration", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "${sliderValue.roundToInt()}s \u2022 $blendStyle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "${sliderValue.roundToInt()}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    isDragging = true
                    val rounded = value.roundToInt().coerceIn(1, 12)
                    if (rounded.toFloat() != sliderValue) {
                        hapticManager.sliderDetent()
                        sliderValue = rounded.toFloat()
                        onSecondsChange(rounded)
                    }
                },
                onValueChangeFinished = {
                    isDragging = false
                },
                valueRange = 1f..12f,
                steps = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("1s (Tight)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("6s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("12s (Long)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

