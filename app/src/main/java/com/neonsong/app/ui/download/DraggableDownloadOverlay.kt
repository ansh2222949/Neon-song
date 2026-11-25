package com.neonsong.app.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.liquidGlassChrome
import kotlin.math.roundToInt

/**
 * A draggable, movable floating download icon that remains visible on-screen
 * whenever downloads are active. Clicking it navigates directly to the downloads page.
 */
@Composable
fun DraggableDownloadOverlay(
    activeDownloadsCount: Int,
    latestProgressPercent: Int,
    modifier: Modifier = Modifier,
    onOpenDownloads: () -> Unit,
) {
    AnimatedVisibility(
        visible = activeDownloadsCount > 0,
        enter = fadeIn(tween(250)) + scaleIn(tween(250)),
        exit = fadeOut(tween(250)) + scaleOut(tween(250)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val haptic = LocalHapticFeedback.current

            val iconSizeDp = 58.dp
            val iconSizePx = with(density) { iconSizeDp.toPx() }
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val minMarginX = with(density) { 12.dp.toPx() }
            val maxMarginX = (screenWidthPx - iconSizePx - minMarginX).coerceAtLeast(minMarginX)
            val minMarginY = with(density) { 60.dp.toPx() }
            val maxMarginY = (screenHeightPx - iconSizePx - with(density) { 110.dp.toPx() }).coerceAtLeast(minMarginY)

            // Initial position: top-right area
            var targetOffsetX by remember { mutableFloatStateOf(maxMarginX) }
            var offsetY by remember { mutableFloatStateOf(with(density) { 140.dp.toPx() }) }
            var isDragging by remember { mutableStateOf(false) }
            var dragDistance by remember { mutableFloatStateOf(0f) }

            val animatedOffsetX by androidx.compose.animation.core.animateFloatAsState(
                targetValue = targetOffsetX,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.72f,
                    stiffness = 420f,
                ),
                label = "downloadOrbSnapX",
            )

            // A position is preserved across recompositions, but the available
            // space is not: rotating, entering split-screen, or resizing the
            // window can make a previously valid position fall outside it.
            // Re-clamp instead of resetting so the user's chosen position is
            // kept whenever possible.
            LaunchedEffect(minMarginX, maxMarginX, minMarginY, maxMarginY) {
                targetOffsetX = targetOffsetX.coerceIn(minMarginX, maxMarginX)
                offsetY = offsetY.coerceIn(minMarginY, maxMarginY)
            }

            val infiniteTransition = rememberInfiniteTransition(label = "download_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "scale_pulse",
            )
            val arrowTranslation by infiniteTransition.animateFloat(
                initialValue = -2f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "arrow_bounce",
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset((if (isDragging) targetOffsetX else animatedOffsetX).roundToInt(), offsetY.roundToInt()) }
                    .size(iconSizeDp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenDownloads
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragDistance = 0f
                            },
                            onDragEnd = {
                                isDragging = false
                                if (dragDistance < 15f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenDownloads()
                                } else {
                                    // Magnetic snap to closest horizontal edge
                                    val midX = (minMarginX + maxMarginX) / 2f
                                    targetOffsetX = if (targetOffsetX < midX) minMarginX else maxMarginX
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDistance += kotlin.math.hypot(dragAmount.x, dragAmount.y)
                                targetOffsetX = (targetOffsetX + dragAmount.x).coerceIn(minMarginX, maxMarginX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(minMarginY, maxMarginY)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Background bubble with Liquid Glass FloatingControls
                Box(
                    modifier = Modifier
                        .size(iconSizeDp)
                        .liquidGlassChrome(
                            shape = CircleShape,
                            preset = LiquidGlassPreset.FloatingControls,
                            accentTint = MaterialTheme.colorScheme.primary,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Circular progress indicator
                    if (latestProgressPercent in 1..99) {
                        CircularProgressIndicator(
                            progress = { latestProgressPercent / 100f },
                            modifier = Modifier
                                .size(iconSizeDp)
                                .padding(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(iconSizeDp)
                                .padding(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            strokeWidth = 2.5.dp,
                        )
                    }

                    // Animated Download Icon
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Downloads in progress",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                translationY = arrowTranslation
                                scaleX = if (isDragging) 1.15f else pulseScale
                                scaleY = if (isDragging) 1.15f else pulseScale
                            },
                    )
                }

                // Badge showing count of remaining songs
                if (activeDownloadsCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(20.dp)
                            .shadow(4.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (activeDownloadsCount > 99) "99+" else activeDownloadsCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
