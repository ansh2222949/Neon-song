package com.neonsong.app.features.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.data.local.SpatialImmersionPreset
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.ui.common.SpatialImmersionPresetSelector
import com.neonsong.app.ui.player.VolumeSlider
import com.neonsong.app.ui.theme.GlassTonalActionButton as GlassTonalButton
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.audio.ui.HrtfHeadScaleSelector
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class StudioSpeedPreset(
    val label: String,
    val speed: Float,
    val pitch: Float,
    val lockVarispeed: Boolean = true,
)

private val StudioSpeedPresetsRow1 = listOf(
    StudioSpeedPreset("Slowed", speed = 0.85f, pitch = 0.85f, lockVarispeed = true),
    StudioSpeedPreset("Deep Slow", speed = 0.75f, pitch = 0.75f, lockVarispeed = true),
    StudioSpeedPreset("Normal", speed = 1.00f, pitch = 1.00f, lockVarispeed = true),
)

private val StudioSpeedPresetsRow2 = listOf(
    StudioSpeedPreset("Nightcore", speed = 1.15f, pitch = 1.15f, lockVarispeed = true),
    StudioSpeedPreset("Sped Up", speed = 1.25f, pitch = 1.25f, lockVarispeed = true),
    StudioSpeedPreset("Vaporwave", speed = 0.80f, pitch = 0.70f, lockVarispeed = false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlsSheet(
    player: MusicPlayer,
    state: MusicPlayerState,
    spatialImmersionPreset: SpatialImmersionPreset,
    playbackController: PlaybackController? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val signalPath by player.signalPath.collectAsStateWithLifecycle()
    val isBitPerfect = signalPath.bitPerfect

    var isLocked by rememberSaveable {
        mutableStateOf(abs(state.speed - state.pitch) < 0.005f)
    }
    var currentSpeed by remember(state.speed) { mutableFloatStateOf(state.speed) }
    var currentPitch by remember(state.pitch) { mutableFloatStateOf(state.pitch) }

    fun applySpeedAndPitch(speed: Float, pitch: Float) {
        currentSpeed = speed
        currentPitch = pitch
        if (speed == 1.0f && pitch == 1.0f) {
            playbackController?.resetPlaybackParameters() ?: player.resetPlaybackParameters()
        } else {
            playbackController?.setPlaybackParameters(speed, pitch)
                ?: player.setPlaybackParameters(speed, pitch)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Audio & Studio Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Volume, 3D Spatial HRTF, Auto DJ & Pitch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LiquidGlassIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    contentDescription = "Close",
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Volume Section Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "System Volume",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    VolumeSlider(
                        isTranslucent = false,
                        primaryColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Auto DJ & Automix Live Section Card
            AutoDJLiveMonitorCard(
                state = state,
                player = player,
            )

            // 3D Spatial Audio & Studio Immersion Card
            val isSpatialActive = signalPath.spatialImmersionActive
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSpatialActive) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (isSpatialActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "3D Spatial & Studio Immersion",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when {
                                    isBitPerfect -> "Bypassed in Bit-Perfect DAC mode"
                                    isSpatialActive -> "Phase-pure vocal lock & 7.1.4 HRTF stage"
                                    else -> "Off \u2022 Standard stereo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Switch(
                        checked = isSpatialActive,
                        enabled = !isBitPerfect,
                        onCheckedChange = { enabled ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            player.setSpatialImmersionEnabled(enabled)
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = isSpatialActive,
                enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(200)),
                exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) +
                    fadeOut(tween(150)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpatialImmersionPresetSelector(
                        selectedPreset = spatialImmersionPreset,
                        onPresetSelected = player::setSpatialImmersionPreset,
                    )
                    HrtfHeadScaleSelector(
                        enabled = isSpatialActive && !isBitPerfect,
                    )
                }
            }

            // Studio Slowed & Pitch Section Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Slowed & Pitch Control",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isLocked) "Linked tape varispeed mode" else "Independent time/pitch stretch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = isLocked,
                                enabled = !isBitPerfect,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isLocked = !isLocked
                                    if (isLocked) {
                                        applySpeedAndPitch(currentSpeed, currentSpeed)
                                    }
                                },
                                label = {
                                    Text(
                                        if (isLocked) "Linked" else "Free",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                border = null,
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )

                            GlassTonalButton(
                                onClick = {
                                    if (!isBitPerfect) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isLocked = true
                                        applySpeedAndPitch(1.0f, 1.0f)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .graphicsLayer { alpha = if (isBitPerfect) 0.5f else 1f },
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isBitPerfect) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HighQuality,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Bit-Perfect Mode: DSP time-stretch and pitch shifting are bypassed for bit-exact DAC output.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00E676),
                                )
                            }
                        }
                    }

                    // Speed Slider
                    Column(
                        modifier = Modifier.graphicsLayer { alpha = if (isBitPerfect) 0.5f else 1f },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", currentSpeed)}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Slider(
                            value = currentSpeed,
                            enabled = !isBitPerfect,
                            onValueChange = { value ->
                                val rounded = ((value * 100).roundToInt() / 100f).coerceIn(0.50f, 1.50f)
                                if (rounded != currentSpeed) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val nextPitch = if (isLocked) rounded else currentPitch
                                    applySpeedAndPitch(rounded, nextPitch)
                                }
                            },
                            valueRange = 0.50f..1.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Pitch Slider
                    Column(
                        modifier = Modifier.graphicsLayer { alpha = if (isBitPerfect) 0.5f else 1f },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Audio Pitch (Tone)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", currentPitch)}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Slider(
                            value = currentPitch,
                            enabled = !isBitPerfect,
                            onValueChange = { value ->
                                val rounded = ((value * 100).roundToInt() / 100f).coerceIn(0.50f, 1.50f)
                                if (rounded != currentPitch) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val nextSpeed = if (isLocked) rounded else currentSpeed
                                    applySpeedAndPitch(nextSpeed, rounded)
                                }
                            },
                            valueRange = 0.50f..1.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Quick Presets
                    Text(
                        text = "Studio Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    listOf(StudioSpeedPresetsRow1, StudioSpeedPresetsRow2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowPresets.forEach { preset ->
                                val isPresetActive = abs(currentSpeed - preset.speed) < 0.005f &&
                                    abs(currentPitch - preset.pitch) < 0.005f
                                val chipColors = if (isPresetActive) {
                                    SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                } else {
                                    SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                SuggestionChip(
                                    enabled = !isBitPerfect,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isLocked = preset.lockVarispeed
                                        applySpeedAndPitch(preset.speed, preset.pitch)
                                    },
                                    label = {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                        )
                                    },
                                    border = null,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = chipColors,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
