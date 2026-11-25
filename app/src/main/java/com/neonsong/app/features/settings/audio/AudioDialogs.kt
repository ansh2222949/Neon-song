package com.neonsong.app.features.settings.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neonsong.app.R
import com.neonsong.app.playback.ClarityPresets
import com.neonsong.app.playback.LoudnessMode
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface

fun loudnessModeTitle(mode: LoudnessMode): String = when (mode) {
    LoudnessMode.TRACK -> "Track"
    LoudnessMode.ALBUM -> "Album"
    else -> "Off"
}

fun loudnessModeSubtitle(mode: LoudnessMode): String = when (mode) {
    LoudnessMode.TRACK -> "Match every track to -14 LUFS (needs ReplayGain tags)"
    LoudnessMode.ALBUM -> "Keep intentional album dynamics (falls back to track tags)"
    else -> "Play tracks at their original level"
}

@Composable
private fun AudioGlassPickerDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    com.neonsong.app.ui.common.NeonPopupCardDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_done),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun LoudnessModeDialog(
    current: LoudnessMode,
    onSelect: (LoudnessMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AudioGlassPickerDialog(
        title = "Loudness Normalization",
        subtitle = "Evens out volume jumps between tracks. Bypassed in Bit-Perfect and USB exclusive modes.",
        onDismiss = onDismiss,
    ) {
        for (mode in LoudnessMode.entries) {
            val selected = mode == current
            val rowShape = remember { SquircleShape(14.dp) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = rowShape,
                        accentTint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Unspecified,
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onSelect(mode) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        loudnessModeTitle(mode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                    Text(
                        loudnessModeSubtitle(mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ClarityPresetDialog(
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AudioGlassPickerDialog(
        title = "Clarity Output Preset",
        subtitle = "Tunes the Studio Master Clarity chain to the output. Reference is the unmodified shipping curve.",
        onDismiss = onDismiss,
    ) {
        for (preset in ClarityPresets.ALL) {
            val selected = preset.index == currentIndex
            val rowShape = remember { SquircleShape(14.dp) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(
                        shape = rowShape,
                        accentTint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Unspecified,
                    )
                    .clickable { onSelect(preset.index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onSelect(preset.index) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        preset.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
