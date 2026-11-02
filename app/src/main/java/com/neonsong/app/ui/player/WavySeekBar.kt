package com.neonsong.app.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.util.LocalHapticManager
import android.content.Context
import android.os.PowerManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * Shifts the lightness and saturation of a Compose Color to produce
 * rich, dynamic tonal variations within the theme palette.
 */
private fun Color.shiftTonal(lightnessDelta: Float, saturationScale: Float = 1.0f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    val originalSaturation = hsv[1]
    hsv[1] = (hsv[1] * saturationScale).coerceIn(if (originalSaturation > 0.05f) 0.15f else 0.0f, 1.0f)
    hsv[2] = (hsv[2] + lightnessDelta).coerceIn(if (originalSaturation > 0.05f) 0.15f else 0.0f, 1.0f)
    val rgb = android.graphics.Color.HSVToColor(hsv)
    return Color(rgb)
}

/**
 * Ensures a color has sufficient brightness and saturation to stand out
 * vibrantly on dark scrims and backgrounds without washing out.
 */
private fun Color.ensureLuminousForDarkBackground(minBrightness: Float = 0.55f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    if (hsv[2] < minBrightness) {
        hsv[2] = minBrightness
    }
    if (hsv[1] in 0.05f..0.35f) {
        hsv[1] = 0.35f
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Material Design 3 Dynamic Counter-Gradient Frosted Glass Waveform Seekbar.
 * Features 3 frosted glass waves that dynamically transition across playback progress:
 * - Layer 1: Ambient soft wave with translucent liquid fill
 * - Layer 2: Harmonic wave with medium liquid fill
 * - Layer 3: Vibrant luminous contour with rich liquid glass fill
 * creating a rich, iridescent multi-depth liquid optical effect.
 */
@Composable
fun WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isTranslucent: Boolean = false,
    trackKey: String? = null,
    showTimeLabels: Boolean = true,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onScrubbingChange: ((Boolean) -> Unit)? = null,
) {
    val interactionSource = remember(trackKey) { MutableInteractionSource() }
    var isInteracting by remember(trackKey) { mutableStateOf(false) }

    LaunchedEffect(interactionSource, trackKey) {
        var dragCount = 0
        var pressCount = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragCount++
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragCount = maxOf(0, dragCount - 1)
                is PressInteraction.Press -> pressCount++
                is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = maxOf(0, pressCount - 1)
            }
            isInteracting = dragCount > 0 || pressCount > 0
        }
    }

    // Active scrubbing values
    var dragPositionMs by remember(trackKey) { mutableStateOf<Float?>(null) }
    var lastSeekValue by remember(trackKey) { mutableStateOf<Float?>(null) }
    var pendingSeekTargetMs by remember(trackKey) { mutableStateOf<Long?>(null) }

    // Heal: framework reports finger lifted or cancelled without onValueChangeFinished
    LaunchedEffect(isInteracting, trackKey) {
        if (!isInteracting) {
            delay(300L)
            dragPositionMs = null
            lastSeekValue = null
        }
    }

    // Retain pending seek target until ExoPlayer catches up within 1500ms to eliminate thumb snap-back
    LaunchedEffect(pendingSeekTargetMs, if (pendingSeekTargetMs != null) positionMs else 0L) {
        val target = pendingSeekTargetMs ?: return@LaunchedEffect
        if (kotlin.math.abs(positionMs - target) < 1500L) {
            pendingSeekTargetMs = null
        }
    }

    // Safety timeout: release pending target after 600ms if player took longer to catch up
    LaunchedEffect(pendingSeekTargetMs) {
        if (pendingSeekTargetMs != null) {
            delay(600L)
            pendingSeekTargetMs = null
        }
    }

    val dragging = isInteracting || dragPositionMs != null
    val activeSeekMs = dragPositionMs?.toLong() ?: pendingSeekTargetMs

    val haptics = LocalHapticManager.current
    var lastHapticStep by remember(trackKey) { mutableLongStateOf(-1L) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(dragging) {
        onScrubbingChange?.invoke(dragging)
        if (!dragging) {
            lastHapticStep = -1L
        }
    }

    val boundedDurationMs = durationMs.coerceAtLeast(0L)
    val boundedPositionMs = if (boundedDurationMs > 0L) {
        positionMs.coerceIn(0L, boundedDurationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val shownMs = if (boundedDurationMs > 0L) {
        (activeSeekMs ?: boundedPositionMs).coerceIn(0L, boundedDurationMs)
    } else {
        (activeSeekMs ?: boundedPositionMs).coerceAtLeast(0L)
    }
    val shownFraction = if (boundedDurationMs > 0L) {
        (shownMs.toDouble() / boundedDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(if (dragging) shownMs else -1L, dragging) {
        if (dragging && boundedDurationMs > 0L) {
            val step = shownMs / 5000L
            if (step != lastHapticStep) {
                lastHapticStep = step
                haptics.sliderDetent()
            }
        }
    }

    // Ensure colors stand out vibrantly on dark scrim/translucent backgrounds
    val effectivePrimary = remember(primaryColor, isTranslucent) {
        if (isTranslucent) primaryColor.ensureLuminousForDarkBackground(0.60f) else primaryColor
    }
    val effectiveSecondary = remember(secondaryColor, isTranslucent) {
        if (isTranslucent) secondaryColor.ensureLuminousForDarkBackground(0.52f) else secondaryColor
    }
    val effectiveTertiary = remember(tertiaryColor, isTranslucent) {
        if (isTranslucent) tertiaryColor.ensureLuminousForDarkBackground(0.48f) else tertiaryColor
    }

    val inactiveColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    }
    val textColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Pre-calculated tonal palette (zero allocation in draw loop)
    val layer1Light = remember(effectiveTertiary) { effectiveTertiary.shiftTonal(lightnessDelta = +0.18f, saturationScale = 0.85f) }
    val layer1Dark = remember(effectiveTertiary) { effectiveTertiary.shiftTonal(lightnessDelta = -0.15f, saturationScale = 1.30f) }
    val layer2Dark = remember(effectiveSecondary) { effectiveSecondary.shiftTonal(lightnessDelta = -0.16f, saturationScale = 1.30f) }
    val layer2Light = remember(effectiveSecondary) { effectiveSecondary.shiftTonal(lightnessDelta = +0.18f, saturationScale = 0.85f) }
    val layer3Light = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = +0.20f, saturationScale = 0.90f) }
    val layer3Dark = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = -0.14f, saturationScale = 1.35f) }
    val thumbColor = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = -0.06f, saturationScale = 1.15f) }

    // Pre-allocated gradient color lists to eliminate garbage collector churn
    val layer1ContourColors = remember(layer1Light, layer1Dark, isTranslucent) {
        listOf(
            layer1Light.copy(alpha = if (isTranslucent) 0.42f else 0.38f),
            layer1Dark.copy(alpha = if (isTranslucent) 0.32f else 0.28f),
        )
    }
    val layer2ContourColors = remember(layer2Dark, layer2Light, isTranslucent) {
        listOf(
            layer2Dark.copy(alpha = if (isTranslucent) 0.72f else 0.66f),
            layer2Light.copy(alpha = if (isTranslucent) 0.62f else 0.56f),
        )
    }
    val layer3ContourColors = remember(layer3Light, layer3Dark, isTranslucent) {
        listOf(
            layer3Light.copy(alpha = if (isTranslucent) 0.98f else 0.95f),
            layer3Dark.copy(alpha = if (isTranslucent) 0.92f else 0.88f),
        )
    }
    val baselineColors = remember(layer3Light, layer3Dark) {
        listOf(layer3Light, layer3Dark)
    }

    // Frosted liquid fill gradients
    val layer1FillColors = remember(layer1Light, layer1Dark, isTranslucent) {
        listOf(
            layer1Light.copy(alpha = if (isTranslucent) 0.16f else 0.12f),
            layer1Dark.copy(alpha = 0.03f),
        )
    }
    val layer2FillColors = remember(layer2Light, layer2Dark, isTranslucent) {
        listOf(
            layer2Light.copy(alpha = if (isTranslucent) 0.20f else 0.16f),
            layer2Dark.copy(alpha = 0.04f),
        )
    }
    val layer3FillColors = remember(layer3Light, layer3Dark, isTranslucent) {
        listOf(
            layer3Light.copy(alpha = if (isTranslucent) 0.26f else 0.22f),
            layer3Dark.copy(alpha = 0.06f),
        )
    }

    // Accumulated phases: Freeze in place when paused/scrubbing instead of violently snapping
    var phase1 by remember { mutableFloatStateOf(2.2f) }
    var phase2 by remember { mutableFloatStateOf(1.2f) }
    var phase3 by remember { mutableFloatStateOf(0f) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    val isScreenInteractive = powerManager?.isInteractive ?: true

    val wavesActive = isPlaying && !dragging

    LaunchedEffect(wavesActive, lifecycleState, isScreenInteractive) {
        if (!wavesActive || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED) || !isScreenInteractive) return@LaunchedEffect
        var lastNanos = 0L
        val twoPi = (2 * PI).toFloat()
        val speed1 = twoPi / 2.4f
        val speed2 = twoPi / 1.8f
        val speed3 = twoPi / 1.3f
        val frameDelayMs = 41L // 24 FPS cap for power efficiency and thermal stability
        while (isActive) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.08f)
                    phase1 = (phase1 + dt * speed1) % twoPi
                    phase2 = (phase2 + dt * speed2) % twoPi
                    phase3 = (phase3 + dt * speed3) % twoPi
                }
                lastNanos = now
            }
            delay(frameDelayMs - 6L)
        }
    }

    // 3-Tier Amplitudes with smooth dampening on seek - slim, elegant audiophile contour
    val density = LocalDensity.current
    val baseAmp1Px = with(density) { 6.8.dp.toPx() } // Deep ambient glass layer (refined slim)
    val baseAmp2Px = with(density) { 5.0.dp.toPx() } // Middle frosted glass layer
    val baseAmp3Px = with(density) { 3.4.dp.toPx() } // Foreground luminous glass layer
    val draggingAmpPx = with(density) { 0.8.dp.toPx() }

    val amp1 by animateFloatAsState(
        targetValue = if (dragging) draggingAmpPx else baseAmp1Px,
        animationSpec = tween(durationMillis = 180),
        label = "Amp1",
    )
    val amp2 by animateFloatAsState(
        targetValue = if (dragging) draggingAmpPx else baseAmp2Px,
        animationSpec = tween(durationMillis = 180),
        label = "Amp2",
    )
    val amp3 by animateFloatAsState(
        targetValue = if (dragging) draggingAmpPx else baseAmp3Px,
        animationSpec = tween(durationMillis = 180),
        label = "Amp3",
    )

    // Pre-allocated Stroke instances - razor-thin, crisp glass contours
    val stroke1 = remember(density) { Stroke(width = with(density) { 0.85.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val stroke2 = remember(density) { Stroke(width = with(density) { 1.0.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val stroke3 = remember(density) { Stroke(width = with(density) { 1.35.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }

    // Broad wavelengths for smooth, elegant rolling hills
    val waveLength1Px = with(density) { 160.dp.toPx() }
    val waveLength2Px = with(density) { 125.dp.toPx() }
    val waveLength3Px = with(density) { 95.dp.toPx() }

    // Slim, modern seekbar baseline strip (3.0dp)
    val baseTrackThicknessPx = with(density) { 3.0.dp.toPx() }
    val thumbRadiusPx = with(density) { 5.2.dp.toPx() }
    val transitionLengthPx = with(density) { 40.dp.toPx() }
    val waveSampleStepPx = with(density) { 4.0.dp.toPx() }
    val horizontalPaddingPx = with(density) { 10.dp.toPx() }

    // Pre-calculate vertical gradient brushes to eliminate allocations inside drawPath
    val boxHeightPx = with(density) { 46.dp.toPx() }
    val centerYPx = boxHeightPx / 2f + with(density) { 5.dp.toPx() }
    val halfThicknessPx = baseTrackThicknessPx / 2f
    val bottomYPx = centerYPx + halfThicknessPx
    val topBaselineYPx = centerYPx - halfThicknessPx

    val layer1FillBrush = remember(layer1FillColors, density) {
        Brush.verticalGradient(
            colors = layer1FillColors,
            startY = topBaselineYPx - baseAmp1Px,
            endY = bottomYPx,
        )
    }
    val layer2FillBrush = remember(layer2FillColors, density) {
        Brush.verticalGradient(
            colors = layer2FillColors,
            startY = topBaselineYPx - baseAmp2Px,
            endY = bottomYPx,
        )
    }
    val layer3FillBrush = remember(layer3FillColors, density) {
        Brush.verticalGradient(
            colors = layer3FillColors,
            startY = topBaselineYPx - baseAmp3Px,
            endY = bottomYPx,
        )
    }

    // Reusable Path caches
    val pathFilled1 = remember { Path() }
    val pathContour1 = remember { Path() }
    val pathFilled2 = remember { Path() }
    val pathContour2 = remember { Path() }
    val pathFilled3 = remember { Path() }
    val pathContour3 = remember { Path() }
    val clipPathBounds = remember { Path() }

    // Dynamic width tracking for pixel-perfect floating scrubber bubble centering
    var bubbleWidthPx by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .onSizeChanged { barWidthPx = it.width.toFloat() },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f + 7.dp.toPx()

                    // Match Material 3 Slider's 10dp internal thumb padding so touch and visual points align exactly
                    val trackStartX = horizontalPaddingPx
                    val trackEndX = width - horizontalPaddingPx
                    val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                    val thumbX = trackStartX + (shownFraction * trackWidth).coerceIn(0f, trackWidth)

                    val halfThickness = baseTrackThicknessPx / 2f
                    val bottomY = centerY + halfThickness
                    val topBaselineY = centerY - halfThickness

                    // 1. Inactive background track: Full smooth capsule bar
                    drawLine(
                        color = inactiveColor,
                        start = Offset(trackStartX, centerY),
                        end = Offset(trackEndX, centerY),
                        strokeWidth = baseTrackThicknessPx,
                        cap = StrokeCap.Round,
                    )

                    // 2. Active 3-Layer Material Frosted Glass Waves with Frosted Liquid Fills
                    if (thumbX > trackStartX) {
                        clipPathBounds.reset()
                        clipPathBounds.addRoundRect(
                            RoundRect(
                                rect = androidx.compose.ui.geometry.Rect(
                                    left = trackStartX - halfThickness,
                                    top = 0f,
                                    right = thumbX + halfThickness,
                                    bottom = height,
                                ),
                                topLeft = CornerRadius(halfThickness, halfThickness),
                                bottomLeft = CornerRadius(halfThickness, halfThickness),
                                topRight = CornerRadius(halfThickness, halfThickness),
                                bottomRight = CornerRadius(halfThickness, halfThickness),
                            )
                        )

                        clipPath(clipPathBounds) {
                            fun smootherstep(t: Float): Float {
                                val c = t.coerceIn(0f, 1f)
                                return c * c * c * (c * (c * 6f - 15f) + 10f)
                            }

                            fun populateWave(
                                filled: Path,
                                contour: Path,
                                wavelength: Float,
                                amplitude: Float,
                                phase: Float,
                            ) {
                                filled.reset()
                                contour.reset()
                                filled.moveTo(trackStartX, bottomY)
                                filled.lineTo(trackStartX, topBaselineY)
                                contour.moveTo(trackStartX, topBaselineY)

                                var x = trackStartX
                                val waveLength = thumbX - trackStartX
                                val effectiveTransition = minOf(transitionLengthPx, waveLength * 0.48f)
                                val invTransition = if (effectiveTransition > 0f) 1f / effectiveTransition else 0f
                                val invWavelength2Pi = (2 * PI / wavelength).toFloat()
                                while (x < thumbX) {
                                    val distFromStart = x - trackStartX
                                    val distFromEnd = thumbX - x
                                    val startEnv = if (invTransition > 0f) smootherstep(distFromStart * invTransition) else 1f
                                    val endEnv = if (invTransition > 0f) smootherstep(distFromEnd * invTransition) else 1f
                                    val envelope = startEnv * endEnv

                                    val angle = distFromStart * invWavelength2Pi - phase
                                    val waveHeight = (0.5f + 0.5f * sin(angle)) * amplitude * envelope
                                    val y = topBaselineY - waveHeight

                                    filled.lineTo(x, y)
                                    contour.lineTo(x, y)
                                    x += waveSampleStepPx
                                }
                                filled.lineTo(thumbX, topBaselineY)
                                contour.lineTo(thumbX, topBaselineY)
                                filled.lineTo(thumbX, bottomY)
                                filled.close()
                            }

                            populateWave(pathFilled1, pathContour1, waveLength1Px, amp1, phase1)
                            populateWave(pathFilled2, pathContour2, waveLength2Px, amp2, phase2)
                            populateWave(pathFilled3, pathContour3, waveLength3Px, amp3, phase3)

                            val activeWidth = thumbX.coerceAtLeast(trackStartX + 1f)

                            // Layer 1: Frosted liquid fill + contour
                            drawPath(
                                path = pathFilled1,
                                brush = layer1FillBrush,
                            )
                            drawPath(
                                path = pathContour1,
                                brush = Brush.horizontalGradient(
                                    colors = layer1ContourColors,
                                    startX = trackStartX,
                                    endX = activeWidth,
                                ),
                                style = stroke1,
                            )

                            // Layer 2: Medium harmonic liquid fill + contour
                            drawPath(
                                path = pathFilled2,
                                brush = layer2FillBrush,
                            )
                            drawPath(
                                path = pathContour2,
                                brush = Brush.horizontalGradient(
                                    colors = layer2ContourColors,
                                    startX = trackStartX,
                                    endX = activeWidth,
                                ),
                                style = stroke2,
                            )

                            // Layer 3: Vibrant foreground liquid fill + contour
                            drawPath(
                                path = pathFilled3,
                                brush = layer3FillBrush,
                            )
                            drawPath(
                                path = pathContour3,
                                brush = Brush.horizontalGradient(
                                    colors = layer3ContourColors,
                                    startX = trackStartX,
                                    endX = activeWidth,
                                ),
                                style = stroke3,
                            )

                            // 3. Crisp Baseline Bar with Light -> Dark dynamic gradient
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = baselineColors,
                                    startX = trackStartX,
                                    endX = activeWidth,
                                ),
                                start = Offset(trackStartX, centerY),
                                end = Offset(thumbX, centerY),
                                strokeWidth = baseTrackThicknessPx,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    // 4. Leading Thumb Indicator: Refined floating jewel bead with soft neon halo
                    if (boundedDurationMs > 0) {
                        val activeRadius = if (dragging) thumbRadiusPx * 1.3f else thumbRadiusPx
                        // Outer soft neon aura
                        drawCircle(
                            color = thumbColor.copy(alpha = 0.32f),
                            radius = activeRadius + 4.5.dp.toPx(),
                            center = Offset(thumbX, centerY),
                        )
                        // Outer crisp diamond/glass rim
                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f),
                            radius = activeRadius,
                            center = Offset(thumbX, centerY),
                        )
                        // Inner vibrant colored core
                        drawCircle(
                            color = thumbColor,
                            radius = (activeRadius - 1.2.dp.toPx()).coerceAtLeast(1f),
                            center = Offset(thumbX, centerY),
                        )
                        // Specular micro-reflection glint (top-left)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = 1.0.dp.toPx(),
                            center = Offset(thumbX - activeRadius * 0.32f, centerY - activeRadius * 0.32f),
                        )
                    }
                }

                // Apple-Grade Floating Time Scrubber Magnifier Bubble
                val bubbleAlpha by animateFloatAsState(
                    targetValue = if (dragging && boundedDurationMs > 0L) 1f else 0f,
                    animationSpec = ExpressiveMotion.appleFluidSpring(),
                    label = "bubbleAlpha",
                )
                val bubbleScale by animateFloatAsState(
                    targetValue = if (dragging && boundedDurationMs > 0L) 1f else 0.65f,
                    animationSpec = ExpressiveMotion.appleBouncySpring(),
                    label = "bubbleScale",
                )

                if (bubbleAlpha > 0.01f && barWidthPx > 0f) {
                    val trackStartX = horizontalPaddingPx
                    val trackEndX = barWidthPx - horizontalPaddingPx
                    val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                    val thumbPx = trackStartX + (shownFraction * trackWidth).coerceIn(0f, trackWidth)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xE6141414),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .onSizeChanged { bubbleWidthPx = it.width.toFloat() }
                            .graphicsLayer {
                                val effBubbleWidth = if (bubbleWidthPx > 0f) bubbleWidthPx else with(density) { 92.dp.toPx() }
                                val minX = with(density) { 4.dp.toPx() }
                                val maxX = (barWidthPx - effBubbleWidth - minX).coerceAtLeast(minX)
                                translationX = (thumbPx - (effBubbleWidth / 2f)).coerceIn(minX, maxX)
                                translationY = -32.dp.toPx()
                                alpha = bubbleAlpha
                                scaleX = bubbleScale
                                scaleY = bubbleScale
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = formatTime(shownMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.5f),
                            )
                            Text(
                                text = "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0L))}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    fontSize = 10.5.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                Slider(
                    value = if (boundedDurationMs > 0L) shownMs.toFloat().coerceIn(0f, boundedDurationMs.toFloat()) else 0f,
                    onValueChange = {
                        dragPositionMs = it
                        lastSeekValue = it
                    },
                    onValueChangeFinished = {
                        if (boundedDurationMs <= 0L) {
                            dragPositionMs = null
                            lastSeekValue = null
                            return@Slider
                        }
                        val target = (lastSeekValue ?: dragPositionMs)?.toLong()?.coerceIn(0L, boundedDurationMs)
                        if (target != null) {
                            pendingSeekTargetMs = target
                            onSeek(target)
                        }
                        dragPositionMs = null
                        lastSeekValue = null
                    },
                    valueRange = 0f..boundedDurationMs.coerceAtLeast(1L).toFloat(),
                    enabled = boundedDurationMs > 0L,
                    interactionSource = interactionSource,
                    modifier = Modifier.matchParentSize(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledThumbColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                    ),
                )
            }

            if (showTimeLabels) {
                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(shownMs),
                        style = (if (isTranslucent) {
                            MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        } else {
                            MaterialTheme.typography.labelMedium
                        }).copy(fontFeatureSettings = "tnum"),
                        color = textColor,
                    )
                    Text(
                        text = if (boundedDurationMs > 0L) "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0))}" else "--:--",
                        style = (if (isTranslucent) {
                            MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                        } else {
                            MaterialTheme.typography.labelMedium
                        }).copy(fontFeatureSettings = "tnum"),
                        color = textColor,
                    )
                }
            }
        }
    }
}

@Composable
fun WavySeekBar(
    state: MusicPlayerState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isTranslucent: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onScrubbingChange: ((Boolean) -> Unit)? = null,
) {
    WavySeekBar(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        isPlaying = state.isPlaying,
        onSeek = onSeek,
        modifier = modifier,
        isTranslucent = isTranslucent,
        trackKey = state.current?.let { it.videoId ?: "${it.artist}|${it.title}" },
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        tertiaryColor = tertiaryColor,
        onScrubbingChange = onScrubbingChange,
    )
}
