package com.neonsong.app.features.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.features.player.domain.formatTime
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.ui.player.WavySeekBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val primary = primaryColor
    val tertiary = tertiaryColor
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.20f else 0.12f)
    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    // Playback progress is time, not directional content: pin to LTR so the
    // Material Slider's touch mapping always matches the LTR custom drawing,
    // even in RTL locales (Arabic, …) where Slider would otherwise mirror.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.drawBehind {
                val inset = 10.dp.toPx()
                val startX = inset
                val endX = (size.width - inset).coerceAtLeast(startX)
                val activeEndX = startX + ((endX - startX) * fraction)
                val centerY = size.height / 2f
                drawLine(
                    color = inactive,
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (activeEndX > startX) {
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                primary.copy(alpha = if (enabled) 1f else 0.42f),
                                tertiary.copy(alpha = if (enabled) 0.92f else 0.36f),
                            ),
                            startX = startX,
                            endX = activeEndX,
                        ),
                        start = Offset(startX, centerY),
                        end = Offset(activeEndX, centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun SeekBar(
    progressState: StateFlow<PlaybackProgressState>,
    isPlaying: Boolean,
    trackKey: String?,
    wavyEnabled: Boolean = true,
    onSeek: (Long) -> Unit,
    isTranslucent: Boolean = false,
    fallbackDurationMs: Long = 0L,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onScrubbingChange: ((Boolean) -> Unit)? = null,
) {
    val progress by progressState.collectAsStateWithLifecycle()
    val effectiveDurationMs = if (progress.durationMs > 0L) progress.durationMs else fallbackDurationMs.coerceAtLeast(0L)

    if (wavyEnabled) {
        WavySeekBar(
            positionMs = progress.positionMs,
            durationMs = effectiveDurationMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            isTranslucent = isTranslucent,
            trackKey = trackKey,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            onScrubbingChange = onScrubbingChange,
        )
        return
    }

    // Current-gesture fraction only; null = finger off, show live position.
    // Nullable (never a stale 0f) so a press without movement seeks nowhere
    // and a gesture that ends without onValueChangeFinished can't pin the bar.
    val seekInteraction = remember(trackKey) { MutableInteractionSource() }
    var isInteracting by remember(trackKey) { mutableStateOf(false) }
    LaunchedEffect(isInteracting) {
        onScrubbingChange?.invoke(isInteracting)
    }
    LaunchedEffect(seekInteraction, trackKey) {
        var dragCount = 0
        var pressCount = 0
        seekInteraction.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragCount++
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragCount = maxOf(0, dragCount - 1)
                is PressInteraction.Press -> pressCount++
                is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = maxOf(0, pressCount - 1)
            }
            isInteracting = dragCount > 0 || pressCount > 0
        }
    }
    var dragFraction by remember(trackKey) { mutableStateOf<Float?>(null) }
    var lastSeekFraction by remember(trackKey) { mutableStateOf<Float?>(null) }
    // Heal: framework reports finger lifted or cancelled but the finished callback
    // never ran -> drop the dead value and resume live position.
    LaunchedEffect(isInteracting, trackKey) {
        if (!isInteracting) {
            delay(120L)
            dragFraction = null
            lastSeekFraction = null
        }
    }
    // Watchdog: if dragFraction was set by a tap/cancelled gesture while not interacting,
    // ensure it never permanently wedges the seekbar.
    LaunchedEffect(dragFraction, isInteracting, trackKey) {
        if (dragFraction != null && !isInteracting) {
            delay(250L)
            dragFraction = null
            lastSeekFraction = null
        }
    }

    val boundedDurationMs = effectiveDurationMs.coerceAtLeast(0L)
    val currentFraction = if (boundedDurationMs > 0L) {
        (progress.positionMs.toDouble() / boundedDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction = (dragFraction ?: currentFraction).coerceIn(0f, 1f)
    val shownMs = dragFraction?.let {
        (it * boundedDurationMs).toLong().coerceIn(0L, boundedDurationMs)
    } ?: progress.positionMs.coerceIn(0L, boundedDurationMs)

    val effectivePrimary = primaryColor
    val inactiveColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.20f)
    } else {
        effectivePrimary.copy(alpha = 0.28f)
    }
    val textColor = if (isTranslucent) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)

    val trackHeightDp by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isInteracting) 8.dp else 3.8.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.78f,
            stiffness = 650f,
        ),
        label = "appleSeekTrackHeight",
    )
    val activeTrackAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isInteracting) 1.0f else 0.82f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "appleSeekTrackAlpha",
    )

    // Same RTL pin as above: custom Canvas draws LTR, invisible Slider must match it.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    val width = size.width
                    val centerY = size.height / 2f
                    val trackHeightPx = trackHeightDp.toPx()
                    val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                    // 1. Frosted Inactive Track Capsule
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (isInteracting) 0.28f else 0.18f),
                        topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                        size = Size(width, trackHeightPx),
                        cornerRadius = cornerRadius,
                    )

                    // 2. Luminous Apple Active Fill Capsule (Expands on touch, no cluttered permanent knob)
                    val activeEndX = (fraction * width).coerceIn(0f, width)
                    if (activeEndX > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = activeTrackAlpha),
                            topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                            size = Size(activeEndX, trackHeightPx),
                            cornerRadius = cornerRadius,
                        )
                    }
                }

                // Invisible Material interaction layer: custom visuals, reliable seeking semantics.
                Slider(
                    value = fraction,
                    onValueChange = {
                        dragFraction = it
                        lastSeekFraction = it
                    },
                    onValueChangeFinished = {
                        // Commit only this gesture's value; no value = no seek.
                        val target = (lastSeekFraction ?: dragFraction)?.let {
                            (it * boundedDurationMs).toLong().coerceIn(0L, boundedDurationMs)
                        }
                        dragFraction = null
                        lastSeekFraction = null
                        if (target != null && boundedDurationMs > 0L) {
                            onSeek(target)
                        }
                    },
                    valueRange = 0f..1f,
                    enabled = boundedDurationMs > 0L,
                    interactionSource = seekInteraction,
                    modifier = Modifier.fillMaxSize(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledThumbColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                        disabledActiveTickColor = Color.Transparent,
                        disabledInactiveTickColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(2.dp))

            // Time labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(shownMs),
                    style = if (isTranslucent) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.labelMedium,
                    color = textColor,
                )
                Text(
                    text = "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0))}",
                    style = if (isTranslucent) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.labelMedium,
                    color = textColor,
                )
            }
        }
    }
}

