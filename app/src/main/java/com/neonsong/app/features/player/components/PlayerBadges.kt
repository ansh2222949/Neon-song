package com.neonsong.app.features.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.playback.AutoMixState
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.SignalPathReport
import com.neonsong.app.playback.formatSampleRateKHz
import com.neonsong.app.playback.isSpatialAudioCodec
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.util.LocalHapticManager
import kotlin.math.roundToInt

@Composable
fun AudiophileHeroBadge(
    state: MusicPlayerState,
    signalPath: SignalPathReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isSpatial = remember(state.audioCodec) { isSpatialAudioCodec(state.audioCodec) }
    val isLossless = state.isLossless
    val isHiRes = isLossless && ((state.bitDepth ?: 16) > 16 || (state.samplingRateKHz ?: 44.1) > 48.0)

    val badgeLabel = remember(state.audioCodec, state.bitrateKbps, state.samplingRateKHz, state.bitDepth, isSpatial, isLossless, isHiRes) {
        when {
            isSpatial -> "Dolby Atmos"
            isHiRes -> "Hi-Res Lossless • ${state.bitDepth ?: 24}-bit / ${formatSampleRateKHz(state.samplingRateKHz ?: 96.0)}kHz"
            isLossless -> "Lossless • ${state.bitDepth ?: 16}-bit / ${formatSampleRateKHz(state.samplingRateKHz ?: 44.1)}kHz"
            else -> buildString {
                append(state.audioCodec?.takeIf(String::isNotBlank) ?: "Audio")
                state.bitrateKbps?.let { append(" • $it kbps") }
            }
        }
    }

    val badgeColor = when {
        isSpatial -> Color(0xFF7C4DFF)
        signalPath.bitPerfect -> Color(0xFF00E676)
        isHiRes -> Color(0xFF69F0AE)
        isLossless -> Color(0xFFB2FF59)
        else -> Color.White.copy(alpha = 0.72f)
    }

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "badgeScale",
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = null,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (signalPath.bitPerfect) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676)),
                )
            } else {
                Icon(
                    when {
                        isSpatial -> Icons.Filled.SurroundSound
                        isLossless -> Icons.Filled.HighQuality
                        else -> Icons.Filled.MusicNote
                    },
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                color = badgeColor,
            )
            if (signalPath.bitPerfect) {
                Text(
                    text = "• Bit-Perfect",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFF00E676),
                )
            }
        }
    }
}

@Composable
fun AutoDJLiveMonitorCard(
    state: MusicPlayerState,
    player: MusicPlayer,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val isBlending = state.autoMixState == AutoMixState.FADING_AUTO || state.autoMixState == AutoMixState.FADING_FAST
    val isArmed = state.autoMixState == AutoMixState.MONITORING_OUTRO
    val isAnalyzing = state.autoMixState == AutoMixState.ANALYZING || state.autoMixState == AutoMixState.PREBUFFERING_DECK_B
    val hasActiveAutoDJ = state.isAutoDJActive && state.autoDJStatus != null
    val isAnyActive = state.isAutoMixActive || state.isAutoMixEnabled || state.isAutoDJActive

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row: Icon + Title + Live Status Pill
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBlending) Color(0xFF8B5CF6).copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.tertiaryContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (isBlending) Color(0xFFDDD6FE) else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Auto DJ & Automix",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isBlending) "Live Dual-Deck Outro Blend"
                            else if (isArmed) "Outro Boundary Armed"
                            else if (state.autoDJStatus != null) state.autoDJStatus
                            else "Predictive Queue & Tempo Blends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Live status badge
                val (badgeText, badgeBg, badgeFg) = when {
                    isBlending -> Triple(
                        "LIVE BLEND",
                        Color(0xFF8B5CF6).copy(alpha = 0.30f),
                        Color(0xFFDDD6FE)
                    )
                    isArmed -> Triple(
                        "OUTRO ARMED",
                        Color(0xFF10B981).copy(alpha = 0.25f),
                        Color(0xFFA7F3D0)
                    )
                    isAnalyzing -> Triple(
                        "PREPARING",
                        Color(0xFF3B82F6).copy(alpha = 0.25f),
                        Color(0xFFBFDBFE)
                    )
                    hasActiveAutoDJ -> Triple(
                        "PREFETCHING",
                        Color(0xFFF59E0B).copy(alpha = 0.25f),
                        Color(0xFFFDE68A)
                    )
                    isAnyActive -> Triple(
                        "ACTIVE",
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Triple(
                        "OFF",
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Interactive Automix & Bar controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automix Beat-Matched Transitions",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (state.isAutoMixEnabled) "Dual-deck outro blend active" else "Seamless beat-matched crossfades",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.isAutoMixEnabled,
                        onCheckedChange = {
                            hapticManager.click()
                            player.setAutomixEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFDDD6FE),
                            checkedTrackColor = Color(0xFF8B5CF6),
                        ),
                    )
                }

                if (state.isAutoMixEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Transition Phrase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.autoMixBars == 8,
                                onClick = {
                                    hapticManager.click()
                                    player.setAutomixBars(8)
                                },
                                label = { Text("8 Bars") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                            FilterChip(
                                selected = state.autoMixBars == 16,
                                onClick = {
                                    hapticManager.click()
                                    player.setAutomixBars(16)
                                },
                                label = { Text("16 Bars") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    if (!isBlending && state.queue.size > 1) {
                        Button(
                            onClick = {
                                hapticManager.click()
                                player.triggerManualMix()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isArmed) "Mix Now • Outro Ready" else "Blend to Next Track (Mix Now)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Live Blending Bar & Crossover Details (when blending)
            if (isBlending) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.autoMixState == AutoMixState.FADING_FAST) "Fast 4-bar Skip Blend" else "Deck A ➔ Deck B Cross-Blend",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${(state.autoMixProgress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6),
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.autoMixProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF8B5CF6),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bpmLabel = state.autoMixBpm?.let { "${it.roundToInt()} BPM" } ?: "Tempo Synced"
                        Text(
                            text = "• $bpmLabel (WSOLA)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val bassDeck = if (state.autoMixProgress < 0.5f) "Deck A Bass" else "Deck B Bass"
                        Text(
                            text = "• 160Hz Linkwitz-Riley: $bassDeck",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (isArmed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text(
                            text = "Outro transition armed",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Next track will blend automatically at the outro energy drop",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (isAnalyzing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (state.autoMixState == AutoMixState.PREBUFFERING_DECK_B)
                            "Prebuffering Deck B on FFmpeg..."
                        else "Detecting kick-band BPM & phase alignment...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Auto DJ Queue Lifecycle / Stream Resolution Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            state.autoDJStatus != null -> "Auto DJ: ${state.autoDJStatus}"
                            state.isAutoDJActive -> "Auto DJ Queue: Continuous & Ready"
                            else -> "Auto DJ: Continuous Queue OFF"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isBuffering || state.autoDJStatus != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = Color(0xFFF59E0B),
                        )
                    }
                    Switch(
                        checked = state.isAutoDJActive,
                        onCheckedChange = {
                            hapticManager.click()
                            player.setAutoDJEnabled(it)
                        },
                    )
                }
            }
        }
    }
}
