package com.neonsong.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DraggableSleepTimerOverlay(
    sleepTimerRemainingMs: Long?,
    onCycleTimer: () -> Unit,
    onCloseTimer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = sleepTimerRemainingMs != null,
        enter = fadeIn(tween(250)) + scaleIn(tween(250)),
        exit = fadeOut(tween(250)) + scaleOut(tween(250)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val haptic = LocalHapticFeedback.current
            val coroutineScope = rememberCoroutineScope()

            // Preserve last non-null time during the 250ms fade-out transition so it doesn't abruptly flash 00:00
            var lastNonNullRemainingMs by remember { mutableLongStateOf(0L) }
            if (sleepTimerRemainingMs != null && sleepTimerRemainingMs > 0L) {
                lastNonNullRemainingMs = sleepTimerRemainingMs
            }
            val displayRemainingMs = sleepTimerRemainingMs ?: lastNonNullRemainingMs

            val hours = (displayRemainingMs / 3600000).toInt()
            val minutes = ((displayRemainingMs % 3600000) / 60000).toInt()
            val seconds = ((displayRemainingMs % 60000) / 1000).toInt()
            val timeText = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }

            // Circle dynamically scales with content length:
            // 74dp for hours (e.g. 1:15:30) and 64dp for minutes/seconds (e.g. 24:59)
            val diameterDp = if (hours > 0) 74.dp else 64.dp
            val diameterPx = with(density) { diameterDp.toPx() }
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }

            val minMarginX = with(density) { 12.dp.toPx() }
            val maxMarginX = (screenWidthPx - diameterPx - minMarginX).coerceAtLeast(minMarginX)
            val minMarginY = with(density) { 60.dp.toPx() }
            val maxMarginY = (screenHeightPx - diameterPx - with(density) { 110.dp.toPx() }).coerceAtLeast(minMarginY)
            // Allow dragging down near close target
            val dragMaxY = (screenHeightPx - diameterPx - with(density) { 16.dp.toPx() }).coerceAtLeast(minMarginY)

            // State holders for gesture coordinates
            val animOffsetX = remember { Animatable(maxMarginX) }
            val animOffsetY = remember { Animatable(screenHeightPx / 2f) }
            var isDragging by remember { mutableStateOf(false) }
            var hoveringClose by remember { mutableStateOf(false) }

            // Drag-to-close target: bottom-center
            val closeSizeDp = 64.dp
            val closeSizePx = with(density) { closeSizeDp.toPx() }
            val closeCenterX = screenWidthPx / 2f
            val closeCenterY = (screenHeightPx - with(density) { 190.dp.toPx() })
                .coerceIn(minMarginY, (screenHeightPx - closeSizePx / 2f).coerceAtLeast(minMarginY))
            val closeHitRadiusPx = with(density) { 72.dp.toPx() }
            val closeScale by animateFloatAsState(
                targetValue = if (hoveringClose) 1.20f else 1f,
                animationSpec = tween(150),
                label = "sleepTimerCloseScale",
            )

            // Freshened references for gesture callbacks
            val currentOnCycleTimer by rememberUpdatedState(onCycleTimer)
            val currentOnCloseTimer by rememberUpdatedState(onCloseTimer)
            val currentMinMarginX by rememberUpdatedState(minMarginX)
            val currentMaxMarginX by rememberUpdatedState(maxMarginX)
            val currentMinMarginY by rememberUpdatedState(minMarginY)
            val currentMaxMarginY by rememberUpdatedState(maxMarginY)
            val currentDragMaxY by rememberUpdatedState(dragMaxY)
            val currentDiameterPx by rememberUpdatedState(diameterPx)
            val currentScreenWidthPx by rememberUpdatedState(screenWidthPx)
            val currentCloseCenterX by rememberUpdatedState(closeCenterX)
            val currentCloseCenterY by rememberUpdatedState(closeCenterY)
            val currentCloseHitRadiusPx by rememberUpdatedState(closeHitRadiusPx)

            // Keep position valid when screen dimensions/orientation or diameter changes
            LaunchedEffect(minMarginX, maxMarginX, minMarginY, maxMarginY) {
                if (!isDragging) {
                    val targetX = if (animOffsetX.value + diameterPx / 2f < screenWidthPx / 2f) minMarginX else maxMarginX
                    val targetY = animOffsetY.value.coerceIn(minMarginY, maxMarginY)
                    animOffsetX.snapTo(targetX)
                    animOffsetY.snapTo(targetY)
                }
            }

            // Track max duration accurately: resets when cycled to shorter or longer presets
            var maxDurationMs by remember { mutableLongStateOf(displayRemainingMs.coerceAtLeast(1L)) }
            var previousRemainingMs by remember { mutableLongStateOf(displayRemainingMs) }

            LaunchedEffect(sleepTimerRemainingMs) {
                val current = sleepTimerRemainingMs ?: return@LaunchedEffect
                val delta = previousRemainingMs - current
                if (current > maxDurationMs || delta < -1500L || delta > 5000L) {
                    maxDurationMs = current
                }
                previousRemainingMs = current
            }

            val progressFraction = (displayRemainingMs.toFloat() / maxDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "sleepTimerProgress",
            )

            // 1. Close Target (rendered BEHIND the draggable bubble so the bubble floats over it)
            AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                exit = fadeOut(tween(180)) + scaleOut(tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (closeCenterX - closeSizePx / 2f).roundToInt(),
                                (closeCenterY - closeSizePx / 2f).roundToInt(),
                            )
                        }
                        .size(closeSizeDp)
                        .graphicsLayer {
                            scaleX = closeScale
                            scaleY = closeScale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (hoveringClose) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                        shadowElevation = if (hoveringClose) 12.dp else 6.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(
                                width = if (hoveringClose) 2.dp else 1.dp,
                                color = if (hoveringClose) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = CircleShape,
                            ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel sleep timer",
                                tint = if (hoveringClose) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }

            // Compute bubble coordinates with subtle magnetic pull towards close target when hovering
            val renderX = if (hoveringClose) {
                val targetBubbleX = closeCenterX - diameterPx / 2f
                animOffsetX.value * 0.55f + targetBubbleX * 0.45f
            } else {
                animOffsetX.value
            }
            val renderY = if (hoveringClose) {
                val targetBubbleY = closeCenterY - diameterPx / 2f
                animOffsetY.value * 0.55f + targetBubbleY * 0.45f
            } else {
                animOffsetY.value
            }

            // 2. Draggable Timer Bubble (rendered ON TOP of close target)
            Box(
                modifier = Modifier
                    .offset { IntOffset(renderX.roundToInt(), renderY.roundToInt()) }
                    .size(diameterDp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnCycleTimer()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                                if (hoveringClose) {
                                    hoveringClose = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnCloseTimer()
                                } else {
                                    // Snap to closest screen edge (left or right)
                                    val targetX = if (animOffsetX.value + currentDiameterPx / 2f < currentScreenWidthPx / 2f) {
                                        currentMinMarginX
                                    } else {
                                        currentMaxMarginX
                                    }
                                    val clampedY = animOffsetY.value.coerceIn(currentMinMarginY, currentMaxMarginY)
                                    coroutineScope.launch {
                                        launch {
                                            animOffsetX.animateTo(
                                                targetValue = targetX,
                                                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                        launch {
                                            animOffsetY.animateTo(
                                                targetValue = clampedY,
                                                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                hoveringClose = false
                                val targetX = if (animOffsetX.value + currentDiameterPx / 2f < currentScreenWidthPx / 2f) {
                                    currentMinMarginX
                                } else {
                                    currentMaxMarginX
                                }
                                val clampedY = animOffsetY.value.coerceIn(currentMinMarginY, currentMaxMarginY)
                                coroutineScope.launch {
                                    launch { animOffsetX.animateTo(targetX, spring(0.75f, Spring.StiffnessMediumLow)) }
                                    launch { animOffsetY.animateTo(clampedY, spring(0.75f, Spring.StiffnessMediumLow)) }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newX = (animOffsetX.value + dragAmount.x).coerceIn(currentMinMarginX, currentMaxMarginX)
                                val newY = (animOffsetY.value + dragAmount.y).coerceIn(currentMinMarginY, currentDragMaxY)
                                coroutineScope.launch {
                                    animOffsetX.snapTo(newX)
                                    animOffsetY.snapTo(newY)
                                }

                                val pillCx = newX + currentDiameterPx / 2f
                                val pillCy = newY + currentDiameterPx / 2f
                                val dist = kotlin.math.hypot(pillCx - currentCloseCenterX, pillCy - currentCloseCenterY)
                                val hovering = dist <= currentCloseHitRadiusPx
                                if (hovering && !hoveringClose) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                hoveringClose = hovering
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                    shadowElevation = if (isDragging) 12.dp else 6.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            shape = CircleShape,
                        ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Circular progress ring showing time remaining
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round,
                        )

                        // Timer text and icon inside the ring
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Sleep Timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (hours > 0) 12.dp else 14.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = if (hours > 0) 10.5.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
