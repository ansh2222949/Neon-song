package com.neonsong.app.features.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.features.player.domain.FullPlayerTab
import com.neonsong.app.features.player.ui.PlayingWaveBars
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.isSpatialAudioCodec
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LiquidGlassSurface
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import com.neonsong.app.util.LocalHapticManager

@Composable
fun PlayerModeButton(
    active: Boolean,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    background: Color,
    foreground: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "modePress",
    )
    val container by animateColorAsState(
        if (active) Color.White.copy(alpha = 0.22f)
        else liquidGlassContainerColor(background),
        tween(200),
        label = "modeContainer",
    )
    val content by animateColorAsState(
        if (active) Color.White else foreground,
        tween(200),
        label = "modeContent",
    )
    LiquidGlassSurface(
        glassModifier = Modifier.liquidGlassChrome(
            CircleShape,
            LocalLiquidGlass.current,
            LiquidGlassPreset.PlayerControls,
            interactionSource = interaction,
        ),
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                selected = active
                stateDescription = description
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(iconSize))
        }
    }
}

@Composable
private fun AppleDockPillItem(
    active: Boolean,
    enabled: Boolean = true,
    compact: Boolean = false,
    activeTint: Color = Color.White,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 650f),
        label = "dockItemScale",
    )
    val pillBg by animateColorAsState(
        targetValue = if (active) {
            primary.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(180),
        label = "dockItemBg",
    )
    val effectiveActiveTint = if (activeTint == Color.White) primary else activeTint
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.26f)
            active -> effectiveActiveTint
            else -> Color.White.copy(alpha = 0.78f)
        },
        animationSpec = tween(180),
        label = "dockItemTint",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        color = pillBg,
        contentColor = tint,
        modifier = Modifier
            .height(if (compact) 36.dp else 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

@Composable
fun PlayerFloatingDock(
    state: MusicPlayerState,
    player: MusicPlayer,
    playbackController: PlaybackController? = null,
    currentTab: FullPlayerTab,
    onTabChange: (FullPlayerTab) -> Unit,
    isVideoMode: Boolean,
    hasVideo: Boolean,
    onToggleVideo: () -> Unit,
    onOpenSignalPath: () -> Unit,
    onOpenAudioControls: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val hapticManager = LocalHapticManager.current
    val signalPath by player.signalPath.collectAsStateWithLifecycle()
    val dockShape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
    val dockHeight = if (compact) 48.dp else 54.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight),
        contentAlignment = Alignment.Center,
    ) {
        val dockBorder = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.20f),
            ),
            start = Offset.Zero,
            end = Offset(450f, 180f),
        )

        Surface(
            shape = dockShape,
            color = Color(0xFF14151A).copy(alpha = 0.52f),
            border = BorderStroke(0.8.dp, dockBorder),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassChrome(dockShape, LocalLiquidGlass.current, LiquidGlassPreset.FloatingControls),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Subtle optical top rim highlight
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(0.8.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.42f),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (compact) 6.dp else 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. Lyrics
                    val isLyricsActive = currentTab == FullPlayerTab.LYRICS
                    AppleDockPillItem(
                        active = isLyricsActive,
                        compact = compact,
                        onClick = {
                            hapticManager.click()
                            onTabChange(if (isLyricsActive) FullPlayerTab.NOW_PLAYING else FullPlayerTab.LYRICS)
                        },
                    ) {
                        Icon(
                            Icons.Filled.FormatQuote,
                            contentDescription = "Lyrics",
                            modifier = Modifier.size(if (compact) 19.dp else 21.dp),
                        )
                    }

                    // 2. Video Mode
                    AppleDockPillItem(
                        active = isVideoMode,
                        enabled = hasVideo,
                        compact = compact,
                        onClick = {
                            if (hasVideo) {
                                hapticManager.click()
                                onToggleVideo()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.SmartDisplay,
                            contentDescription = "Video",
                            modifier = Modifier.size(if (compact) 19.dp else 21.dp),
                        )
                    }

                    // 3. Audio Quality / Signal Path Badge (Apple Music Lossless badge style)
                    val isSpatial = remember(state.audioCodec) { isSpatialAudioCodec(state.audioCodec) }
                    val isHiRes = state.isLossless && ((state.bitDepth ?: 16) > 16 || (state.samplingRateKHz ?: 44.1) > 48.0)
                    val isOpus = remember(state.audioCodec) { state.audioCodec?.contains("opus", ignoreCase = true) == true }
                    val isAac = remember(state.audioCodec) {
                        state.audioCodec?.contains("aac", ignoreCase = true) == true ||
                            state.audioCodec?.contains("mp4a", ignoreCase = true) == true
                    }

                    val activeBadge = remember(state.audioCodec, state.isLossless, isHiRes, signalPath.bitPerfect, isSpatial, isOpus, isAac) {
                        when {
                            isSpatial -> "ATMOS"
                            signalPath.bitPerfect -> "DIRECT"
                            isHiRes -> "HI-RES"
                            state.isLossless || state.audioCodec?.equals("flac", ignoreCase = true) == true -> "LOSSLESS"
                            isOpus -> "OPUS"
                            isAac -> "AAC"
                            !state.audioCodec.isNullOrBlank() -> state.audioCodec!!.uppercase()
                            else -> "AUDIO"
                        }
                    }

                    AppleDockPillItem(
                        active = false,
                        compact = compact,
                        onClick = {
                            hapticManager.click()
                            onOpenSignalPath()
                        },
                    ) {
                        PlayingWaveBars(
                            waveColor = Color.White.copy(alpha = 0.88f),
                            containerColor = Color.Transparent,
                            isPlaying = state.isPlaying,
                            modifier = Modifier.size(15.dp, 11.dp),
                        )
                        AnimatedContent(
                            targetState = activeBadge,
                            transitionSpec = {
                                (slideInVertically { height -> height / 2 } + fadeIn(tween(220)))
                                    .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(220)))
                            },
                            label = "audioBadgeTicker",
                        ) { badgeText ->
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (compact) 9.5.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = Color.White.copy(alpha = 0.90f),
                            )
                        }
                    }

                    // 4. Playing Queue
                    val isQueueActive = currentTab == FullPlayerTab.QUEUE
                    AppleDockPillItem(
                        active = isQueueActive,
                        compact = compact,
                        onClick = {
                            hapticManager.click()
                            onTabChange(if (isQueueActive) FullPlayerTab.NOW_PLAYING else FullPlayerTab.QUEUE)
                        },
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = "Queue",
                            modifier = Modifier.size(if (compact) 19.dp else 21.dp),
                        )
                        if (state.queue.isNotEmpty()) {
                            Text(
                                text = "${state.queue.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }

                    // 5. Sleep Timer
                    val isTimerActive = state.sleepTimerRemainingMs != null && state.sleepTimerRemainingMs > 0L
                    AppleDockPillItem(
                        active = isTimerActive,
                        compact = compact,
                        activeTint = Color(0xFFFFB74D),
                        onClick = {
                            hapticManager.click()
                            playbackController?.cycleSleepTimer() ?: player.cycleSleepTimer()
                        },
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Sleep Timer",
                            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                        )
                        if (isTimerActive) {
                            val minutesLeft = (state.sleepTimerRemainingMs!! / 60000L).coerceAtLeast(1)
                            Text(
                                text = "${minutesLeft}m",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
