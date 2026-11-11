package com.neonsong.app.features.player.components

import androidx.compose.animation.core.animateFloatAsState
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

    val sideButtonSize = if (compact) 40.dp else 46.dp
    val skipButtonSize = if (compact) 48.dp else 54.dp
    val playButtonSize = if (compact) 62.dp else 72.dp
    val sideIconSize = if (compact) 18.dp else 20.dp
    val skipIconSize = if (compact) 24.dp else 28.dp
    val playIconSize = if (compact) 30.dp else 34.dp

    val shuffleInteraction = remember { MutableInteractionSource() }
    val isShufflePressed by shuffleInteraction.collectIsPressedAsState()
    val shuffleScale by animateFloatAsState(if (isShufflePressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "shuffleScale")

    val prevInteraction = remember { MutableInteractionSource() }
    val isPrevPressed by prevInteraction.collectIsPressedAsState()
    val prevScale by animateFloatAsState(if (isPrevPressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "prevScale")

    val playInteraction = remember { MutableInteractionSource() }
    val isPlayPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.91f else 1.0f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "playScale",
    )

    val nextInteraction = remember { MutableInteractionSource() }
    val isNextPressed by nextInteraction.collectIsPressedAsState()
    val nextScale by animateFloatAsState(if (isNextPressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "nextScale")

    val repeatInteraction = remember { MutableInteractionSource() }
    val isRepeatPressed by repeatInteraction.collectIsPressedAsState()
    val repeatScale by animateFloatAsState(if (isRepeatPressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "repeatScale")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Shuffle Button
        val isShuffleActive = state.shuffleEnabled
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = shuffleInteraction),
            onClick = {
                hapticManager.click()
                queueController?.toggleShuffle() ?: player?.toggleShuffle()
            },
            interactionSource = shuffleInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(if (isShuffleActive) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f)),
            contentColor = if (isShuffleActive) Color.White else Color.White.copy(alpha = 0.65f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (isShuffleActive) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.10f)
            ),
            modifier = Modifier
                .size(sideButtonSize)
                .graphicsLayer {
                    scaleX = shuffleScale
                    scaleY = shuffleScale
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
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        // 2. Previous Track
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = prevInteraction),
            onClick = {
                hapticManager.click()
                if (onPrev != null) onPrev() else playbackController?.previous() ?: player?.previous()
            },
            interactionSource = prevInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(Color.White.copy(alpha = 0.12f)),
            contentColor = Color.White.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .size(skipButtonSize)
                .graphicsLayer {
                    scaleX = prevScale
                    scaleY = prevScale
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
            if (effectivePlaying) {
                val playGlowPulse = 1.01f
                val playGlowAlpha = 0.65f
                // Wide outer ambient bloom (layer-scaled, constant layout bounds)
                val outerGlowSize = if (compact) 92.dp else 110.dp
                Box(
                    modifier = Modifier
                        .size(outerGlowSize)
                        .graphicsLayer {
                            scaleX = playGlowPulse
                            scaleY = playGlowPulse
                            alpha = playGlowAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ambientColor.copy(alpha = 0.35f),
                                    ambientCompanion.copy(alpha = 0.15f),
                                    Color.Transparent,
                                )
                            ),
                            CircleShape,
                        )
                )
                // Vibrant inner core aura
                Box(
                    modifier = Modifier
                        .size(if (compact) 74.dp else 88.dp)
                        .graphicsLayer {
                            alpha = playGlowAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ambientColor.copy(alpha = 0.65f),
                                    ambientCompanion.copy(alpha = 0.30f),
                                    Color.Transparent,
                                )
                            ),
                            CircleShape,
                        )
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White,
                contentColor = Color(0xFF0F1116),
                shadowElevation = if (effectivePlaying) 16.dp else 6.dp,
                border = BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White,
                            Color.White.copy(alpha = 0.50f)
                        )
                    )
                ),
                onClick = {
                    hapticManager.toggle(!effectivePlaying)
                    if (onTogglePlay != null) onTogglePlay() else playbackController?.togglePlayPause() ?: player?.togglePlayPause()
                },
                interactionSource = playInteraction,
                modifier = Modifier
                    .size(playButtonSize)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
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
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = nextInteraction),
            onClick = {
                hapticManager.click()
                if (onNext != null) onNext() else playbackController?.next() ?: player?.next()
            },
            interactionSource = nextInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(Color.White.copy(alpha = 0.12f)),
            contentColor = Color.White.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .size(skipButtonSize)
                .graphicsLayer {
                    scaleX = nextScale
                    scaleY = nextScale
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(skipIconSize))
            }
        }

        // 5. Repeat Mode Button
        val isRepeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = repeatInteraction),
            onClick = {
                hapticManager.click()
                queueController?.cycleRepeatMode() ?: player?.cycleRepeatMode()
            },
            interactionSource = repeatInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(if (isRepeatActive) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f)),
            contentColor = if (isRepeatActive) Color.White else Color.White.copy(alpha = 0.65f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (isRepeatActive) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.10f)
            ),
            modifier = Modifier
                .size(sideButtonSize)
                .graphicsLayer {
                    scaleX = repeatScale
                    scaleY = repeatScale
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
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}
