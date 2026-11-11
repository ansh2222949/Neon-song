package com.neonsong.audio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.ui.common.LiquidGlassCard
import com.neonsong.app.ui.theme.LiquidGlassChip
import com.neonsong.audio.AudioEngineBridge
import com.neonsong.audio.HrtfHeadPreset
import com.neonsong.audio.HrtfHeadScaleConfig
import com.neonsong.audio.VocalSeparationConfig
import com.neonsong.audio.VocalSeparationPreset
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HrtfHeadScaleSelector(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showVocalSeparationControl: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val headScale by AudioEngineBridge.hrtfHeadScale.collectAsStateWithLifecycle()
    val matchedPreset = HrtfHeadPreset.fromScale(headScale)

    val radiusCm = HrtfHeadScaleConfig.radiusCm(headScale)
    val sideItdUs = HrtfHeadScaleConfig.sideItdMicros(headScale)
    val pinnaKhz = HrtfHeadScaleConfig.pinnaNotchKhz(headScale)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showVocalSeparationControl) {
            VocalInstrumentSeparationSelector(enabled = enabled)
        }

        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header + Live Scale Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = "3D HRTF Head Geometry",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = matchedPreset?.description
                                ?: "Custom spherical Woodworth ITD & 7.1.4 pinna notch calibration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s · %.2fx",
                                matchedPreset?.title?.substringBefore(" ") ?: "Custom",
                                headScale,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // 3-Preset Selector Row (Small 0.88 | Standard 1.00 | Wide 1.12)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HrtfHeadPreset.entries.forEach { preset ->
                        val isSelected = matchedPreset == preset
                        LiquidGlassChip(
                            selected = isSelected,
                            onClick = {
                                if (enabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    AudioEngineBridge.setHrtfHeadPreset(preset)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            label = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                ) {
                                    Text(
                                        text = preset.title.substringBefore(" "),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%.2fx (%.1fcm)",
                                            preset.scale,
                                            preset.radiusCm,
                                        ),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                    )
                                }
                            },
                        )
                    }
                }

                // Fine-Tune Continuous Slider (0.85f .. 1.18f in 0.01f steps -> 32 steps)
                Slider(
                    value = headScale,
                    onValueChange = { raw ->
                        val stepped = (raw * 100f).roundToInt() / 100f
                        if (stepped != headScale) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            AudioEngineBridge.setHrtfHeadScale(stepped)
                        }
                    },
                    enabled = enabled,
                    valueRange = HrtfHeadScaleConfig.MIN_SCALE..HrtfHeadScaleConfig.MAX_SCALE,
                    steps = 32,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )

                // Acoustic Telemetry Footer (Radius cm | Side ITD µs | Pinna Notch kHz | Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Radius: %.2f cm  •  ITD: %.0f µs  •  Pinna: %.2f kHz",
                            radiusCm,
                            sideItdUs,
                            pinnaKhz,
                        ),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    AnimatedVisibility(
                        visible = matchedPreset != HrtfHeadPreset.STANDARD,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(CircleShape)
                                .clickable(enabled = enabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    AudioEngineBridge.setHrtfHeadPreset(HrtfHeadPreset.STANDARD)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VocalInstrumentSeparationSelector(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val separation by AudioEngineBridge.vocalInstrumentSeparation.collectAsStateWithLifecycle()
    val matchedPreset = VocalSeparationPreset.fromValue(separation)

    val vocalFocusDb = VocalSeparationConfig.vocalFocusDb(separation)
    val surroundPocketDb = VocalSeparationConfig.surroundUnmaskDb(separation)
    val instSpreadPct = VocalSeparationConfig.instrumentSpreadPercent(separation)

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header + Live Percentage Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = "Vocal vs. Instrument 3D Split",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = matchedPreset?.description
                            ?: "Custom 0° FC vocal formant lock & 12-channel instrument extraction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s · %d%%",
                            matchedPreset?.title ?: "Custom",
                            (separation * 100f).roundToInt(),
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // 3-Preset Selector Row (Natural 40% | Studio 3D 75% | Holographic 100%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VocalSeparationPreset.entries.forEach { preset ->
                    val isSelected = matchedPreset == preset
                    LiquidGlassChip(
                        selected = isSelected,
                        onClick = {
                            if (enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                AudioEngineBridge.setVocalSeparationPreset(preset)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        label = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = preset.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%d%% Split",
                                        (preset.separation * 100f).roundToInt(),
                                    ),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
                        },
                    )
                }
            }

            // Continuous Slider (0% .. 100% in 5% steps -> 19 steps)
            Slider(
                value = separation,
                onValueChange = { raw ->
                    val stepped = (raw * 20f).roundToInt() / 20f
                    if (stepped != separation) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        AudioEngineBridge.setVocalInstrumentSeparation(stepped)
                    }
                },
                enabled = enabled,
                valueRange = VocalSeparationConfig.MIN_SEPARATION..VocalSeparationConfig.MAX_SEPARATION,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            )

            // Live Acoustic Telemetry Footer (Vocal Focus dB | Surround Pocket dB | 12ch Spread % | Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Vocal: +%.1f dB  •  Unmask: %.1f dB  •  12ch Spread: %d%%",
                        vocalFocusDb,
                        surroundPocketDb,
                        instSpreadPct,
                    ),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                AnimatedVisibility(
                    visible = matchedPreset != VocalSeparationPreset.STUDIO_3D,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                AudioEngineBridge.setVocalSeparationPreset(VocalSeparationPreset.STUDIO_3D)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
