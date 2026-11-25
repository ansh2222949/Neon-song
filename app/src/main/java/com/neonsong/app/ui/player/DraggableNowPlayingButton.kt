package com.neonsong.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.liquidGlassChrome
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.ui.common.ArtworkImage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Experimental "Button Now Playing": a draggable floating button that replaces
 * the bottom mini player card when enabled. Shows the song's album art in a circle
 * with an interactive progress ring around it. Clicking opens the full player screen.
 */
@Composable
fun DraggableNowPlayingButton(
    track: PlayableTrack,
    progressState: StateFlow<PlaybackProgressState>?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(250)) + scaleIn(tween(250)),
        exit = fadeOut(tween(250)) + scaleOut(tween(250)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val haptic = LocalHapticFeedback.current
            val scope = rememberCoroutineScope()

            val buttonSizeDp = 64.dp
            val buttonSizePx = with(density) { buttonSizeDp.toPx() }
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val minMarginX = with(density) { 14.dp.toPx() }
            val maxMarginX = (screenWidthPx - buttonSizePx - minMarginX).coerceAtLeast(minMarginX)
            val minMarginY = with(density) { 70.dp.toPx() }
            val maxMarginY = (screenHeightPx - buttonSizePx - with(density) { 96.dp.toPx() }).coerceAtLeast(minMarginY)

            // Initial placement: bottom right above bottom nav
            val offsetX = remember { Animatable(maxMarginX) }
            var offsetY by remember { mutableFloatStateOf(maxMarginY - with(density) { 20.dp.toPx() }) }
            var isDragging by remember { mutableStateOf(false) }

            LaunchedEffect(maxMarginX, maxMarginY) {
                val targetX = if (offsetX.value > screenWidthPx / 2f) maxMarginX else minMarginX
                offsetX.snapTo(targetX.coerceIn(minMarginX, maxMarginX))
                offsetY = offsetY.coerceIn(minMarginY, maxMarginY)
            }

            val dragScaleState = animateFloatAsState(
                targetValue = if (isDragging) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
                label = "nowPlayingDragScale",
            )

            val pulseScaleState = if (isPlaying && !isDragging) {
                val infiniteTransition = rememberInfiniteTransition(label = "now_playing_btn_pulse")
                infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "btn_pulse",
                )
            } else {
                null
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.roundToInt()) }
                    .size(buttonSizeDp)
                    .pointerInput(onExpand) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExpand()
                            },
                        )
                    }
                    .pointerInput(minMarginX, maxMarginX, minMarginY, maxMarginY) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = {
                                isDragging = false
                                val targetX =
                                    if (offsetX.value + buttonSizePx / 2f < screenWidthPx / 2f) {
                                        minMarginX
                                    } else {
                                        maxMarginX
                                    }
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val nextX = (offsetX.value + dragAmount.x).coerceIn(minMarginX, maxMarginX)
                                scope.launch { offsetX.snapTo(nextX) }
                                offsetY = (offsetY + dragAmount.y).coerceIn(minMarginY, maxMarginY)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Floating bubble container with Liquid Glass FloatingControls
                Box(
                    modifier = Modifier
                        .size(buttonSizeDp)
                        .graphicsLayer {
                            val s = dragScaleState.value * (pulseScaleState?.value ?: 1f)
                            scaleX = s
                            scaleY = s
                        }
                        .liquidGlassChrome(
                            shape = CircleShape,
                            preset = LiquidGlassPreset.FloatingControls,
                            accentTint = MaterialTheme.colorScheme.primary,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Isolated progress ring so 250ms ticks do not recompose the parent button or art
                        NowPlayingProgressRing(
                            track = track,
                            progressState = progressState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp),
                        )

                        // Album artwork in center
                        Box(
                            modifier = Modifier
                                .size(buttonSizeDp - 14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            ArtworkImage(
                                name = track.title,
                                artist = track.artist,
                                embeddedUrl = track.artworkUrl,
                                fallbackIcon = Icons.Filled.MusicNote,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingProgressRing(
    track: PlayableTrack,
    progressState: StateFlow<PlaybackProgressState>?,
    modifier: Modifier = Modifier,
) {
    val liveProgress by progressState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(PlaybackProgressState(0L, 0L)) }

    val progressFraction = remember(liveProgress.positionMs, liveProgress.durationMs, track.durationMs) {
        val dur = liveProgress.durationMs.takeIf { it > 0 } ?: track.durationMs ?: 0L
        if (dur > 0) (liveProgress.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        else 0f
    }

    CircularProgressIndicator(
        progress = { progressFraction },
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
        strokeWidth = 3.5.dp,
    )
}
