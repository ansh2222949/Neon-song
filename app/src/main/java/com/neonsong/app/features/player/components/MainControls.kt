package com.neonsong.app.features.player.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.core.contracts.QueueController
import com.neonsong.app.features.player.ui.AnimatedPlayPauseIcon
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LiquidGlassSurface
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import com.neonsong.app.util.LocalHapticManager

@Composable
fun MainControls(
    state: MusicPlayerState,
    player: MusicPlayer? = null,
    playbackController: PlaybackController? = null,
    queueController: QueueController? = null,
    isTranslucent: Boolean = false,
    compact: Boolean = false,
    overridePlaying: Boolean? = null,
    onTogglePlay: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    ambientColor: Color = MaterialTheme.colorScheme.primary,
    ambientCompanion: Color = MaterialTheme.colorScheme.secondary,
) {
    val hapticManager = LocalHapticManager.current
    val effectivePlaying = overridePlaying ?: state.isPlaying
    val isGlass = LocalLiquidGlass.current
    val fallbackGlassBorder = remember { BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)) }

    val playGlowAlphaState = animateFloatAsState(
        targetValue = if (effectivePlaying) 0.72f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "playGlowAlpha",
    )
    val playGlowScaleState = animateFloatAsState(
        targetValue = if (effectivePlaying) 1.03f else 0.94f,
        animationSpec = ExpressiveMotion.appleFluidSpring(),
        label = "playGlowScale",
    )
    val outerGlowBrush = remember(ambientColor, ambientCompanion) {
        Brush.radialGradient(listOf(ambientColor.copy(alpha = 0.36f), ambientCompanion.copy(alpha = 0.14f), Color.Transparent))
    }
    val innerGlowBrush = remember(ambientColor, ambientCompanion) {
        Brush.radialGradient(listOf(ambientColor.copy(alpha = 0.65f), ambientCompanion.copy(alpha = 0.28f), Color.Transparent))
    }
    val playBorder = remember {
        BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.50f))))
    }

    val sideButtonSize = if (compact) 40.dp else 46.dp
    val skipButtonSize = if (compact) 48.dp else 54.dp
    val playButtonSize = if (compact) 62.dp else 72.dp
    val sideIconSize = if (compact) 18.dp else 20.dp
    val skipIconSize = if (compact) 24.dp else 28.dp
    val playIconSize = if (compact) 30.dp else 34.dp

    val shuffleInteraction = remember { MutableInteractionSource() }
    val isShufflePressed by shuffleInteraction.collectIsPressedAsState()
    val shuffleScaleState = animateFloatAsState(
        targetValue = if (isShufflePressed) 0.92f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "shuffleScale",
    )
    val shuffleAlphaState = animateFloatAsState(
        targetValue = if (isShufflePressed) 0.70f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "shuffleAlpha",
    )

    val prevInteraction = remember { MutableInteractionSource() }
    val isPrevPressed by prevInteraction.collectIsPressedAsState()
    val prevScaleState = animateFloatAsState(
        targetValue = if (isPrevPressed) 0.92f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "prevScale",
    )
    val prevAlphaState = animateFloatAsState(
        targetValue = if (isPrevPressed) 0.70f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "prevAlpha",
    )

    val playInteraction = remember { MutableInteractionSource() }
    val isPlayPressed by playInteraction.collectIsPressedAsState()
    val playScaleState = animateFloatAsState(
        targetValue = if (isPlayPressed) 0.92f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "playScale",
    )
    val playAlphaState = animateFloatAsState(
        targetValue = if (isPlayPressed) 0.75f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "playAlpha",
    )

    val nextInteraction = remember { MutableInteractionSource() }
    val isNextPressed by nextInteraction.collectIsPressedAsState()
    val nextScaleState = animateFloatAsState(
        targetValue = if (isNextPressed) 0.92f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "nextScale",
    )
    val nextAlphaState = animateFloatAsState(
        targetValue = if (isNextPressed) 0.70f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "nextAlpha",
    )

    val repeatInteraction = remember { MutableInteractionSource() }
    val isRepeatPressed by repeatInteraction.collectIsPressedAsState()
    val repeatScaleState = animateFloatAsState(
        targetValue = if (isRepeatPressed) 0.92f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "repeatScale",
    )
    val repeatAlphaState = animateFloatAsState(
        targetValue = if (isRepeatPressed) 0.70f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "repeatAlpha",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Shuffle Button
        val isShuffleActive = state.shuffleEnabled
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(
                shape = CircleShape,
                enabled = isGlass,
                preset = LiquidGlassPreset.PlayerControls,
                interactionSource = shuffleInteraction,
                accentTint = if (isShuffleActive) ambientColor else Color.Unspecified,
            ),
            onClick = {
                hapticManager.click()
                queueController?.toggleShuffle() ?: player?.toggleShuffle()
            },
            interactionSource = shuffleInteraction,
            shape = CircleShape,
            color = if (isGlass) Color.Transparent else liquidGlassContainerColor(if (isShuffleActive) ambientColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f)),
            contentColor = if (isShuffleActive) Color.White else Color.White.copy(alpha = 0.65f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isGlass) null else (if (isShuffleActive) BorderStroke(1.dp, ambientColor.copy(alpha = 0.38f)) else fallbackGlassBorder),
            modifier = Modifier
                .size(sideButtonSize)
                .graphicsLayer {
                    val s = shuffleScaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = shuffleAlphaState.value
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Shuffle, "Shuffle", Modifier.size(sideIconSize))
                if (isShuffleActive) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp)
                            .size(4.dp)
                            .background(ambientColor, CircleShape)
                    )
                }
            }
        }

        // 2. Previous Track
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(
                shape = CircleShape,
                enabled = isGlass,
                preset = LiquidGlassPreset.PlayerControls,
                interactionSource = prevInteraction,
            ),
            onClick = {
                hapticManager.click()
                if (onPrev != null) onPrev() else playbackController?.previous() ?: player?.previous()
            },
            interactionSource = prevInteraction,
            shape = CircleShape,
            color = if (isGlass) Color.Transparent else liquidGlassContainerColor(Color.White.copy(alpha = 0.12f)),
            contentColor = Color.White.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isGlass) null else fallbackGlassBorder,
            modifier = Modifier
                .size(skipButtonSize)
                .graphicsLayer {
                    val s = prevScaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = prevAlphaState.value
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(skipIconSize))
            }
        }

        // 3. Center Play/Pause Luminous Dial
        Box(
            modifier = Modifier.size(playButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            val outerGlowSize = if (compact) 92.dp else 112.dp
            Box(
                modifier = Modifier
                    .size(outerGlowSize)
                    .graphicsLayer {
                        scaleX = playGlowScaleState.value
                        scaleY = playGlowScaleState.value
                        alpha = playGlowAlphaState.value
                    }
                    .background(outerGlowBrush, CircleShape)
            )
            // Vibrant inner core aura
            Box(
                modifier = Modifier
                    .size(if (compact) 74.dp else 88.dp)
                    .graphicsLayer {
                        val s = (playGlowScaleState.value - 0.02f).coerceAtLeast(0.90f)
                        val a = (playGlowAlphaState.value * 1.15f).coerceAtMost(1f)
                        scaleX = s
                        scaleY = s
                        alpha = a
                    }
                    .background(innerGlowBrush, CircleShape)
            )
            Surface(
                shape = CircleShape,
                color = Color.White,
                contentColor = Color(0xFF0F1116),
                shadowElevation = if (effectivePlaying) 16.dp else 6.dp,
                border = playBorder,
                onClick = {
                    hapticManager.toggle(!effectivePlaying)
                    if (onTogglePlay != null) onTogglePlay() else playbackController?.togglePlayPause() ?: player?.togglePlayPause()
                },
                interactionSource = playInteraction,
                modifier = Modifier
                    .size(playButtonSize)
                    .graphicsLayer {
                        val s = playScaleState.value
                        scaleX = s
                        scaleY = s
                        alpha = playAlphaState.value
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering && overridePlaying == null) {
                        ExpressiveInlineLoadingIndicator(
                            size = if (compact) 26.dp else 32.dp,
                            color = Color(0xFF0F1116),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        AnimatedPlayPauseIcon(
                            isPlaying = effectivePlaying,
                            modifier = Modifier.size(playIconSize),
                        )
                    }
                }
            }
        }

        // 4. Next Track
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(
                shape = CircleShape,
                enabled = isGlass,
                preset = LiquidGlassPreset.PlayerControls,
                interactionSource = nextInteraction,
            ),
            onClick = {
                hapticManager.click()
                if (onNext != null) onNext() else playbackController?.next() ?: player?.next()
            },
            interactionSource = nextInteraction,
            shape = CircleShape,
            color = if (isGlass) Color.Transparent else liquidGlassContainerColor(Color.White.copy(alpha = 0.12f)),
            contentColor = Color.White.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isGlass) null else fallbackGlassBorder,
            modifier = Modifier
                .size(skipButtonSize)
                .graphicsLayer {
                    val s = nextScaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = nextAlphaState.value
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(skipIconSize))
            }
        }

        // 5. Repeat Mode Button
        val isRepeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(
                shape = CircleShape,
                enabled = isGlass,
                preset = LiquidGlassPreset.PlayerControls,
                interactionSource = repeatInteraction,
                accentTint = if (isRepeatActive) ambientColor else Color.Unspecified,
            ),
            onClick = {
                hapticManager.click()
                queueController?.cycleRepeatMode() ?: player?.cycleRepeatMode()
            },
            interactionSource = repeatInteraction,
            shape = CircleShape,
            color = if (isGlass) Color.Transparent else liquidGlassContainerColor(if (isRepeatActive) ambientColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f)),
            contentColor = if (isRepeatActive) Color.White else Color.White.copy(alpha = 0.65f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isGlass) null else (if (isRepeatActive) BorderStroke(1.dp, ambientColor.copy(alpha = 0.38f)) else fallbackGlassBorder),
            modifier = Modifier
                .size(sideButtonSize)
                .graphicsLayer {
                    val s = repeatScaleState.value
                    scaleX = s
                    scaleY = s
                    alpha = repeatAlphaState.value
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    Modifier.size(sideIconSize)
                )
                if (isRepeatActive) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp)
                            .size(4.dp)
                            .background(ambientColor, CircleShape)
                    )
                }
            }
        }
    }
}
