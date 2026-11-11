package com.neonsong.app.ui.player

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import com.neonsong.app.util.LocalHapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
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
    hsv[1] = (hsv[1] * saturationScale).coerceIn(if (originalSaturation > 0.05f) 0.18f else 0.0f, 1.0f)
    hsv[2] = (hsv[2] + lightnessDelta).coerceIn(if (originalSaturation > 0.05f) 0.18f else 0.0f, 1.0f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Ensures a color has sufficient brightness and saturation to stand out
 * vibrantly as a refractive liquid glass tint over dark/blurred artwork scrims.
 */
private fun Color.ensureLiquidGlassLuminosity(minBrightness: Float = 0.76f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        hsv,
    )
    if (hsv[2] < minBrightness) hsv[2] = minBrightness
    if (hsv[1] in 0.08f..0.52f) hsv[1] = 0.54f
    if (hsv[1] > 0.90f && hsv[2] > 0.86f) hsv[1] = 0.86f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Aesthetic Liquid Glass Waveform Seekbar with Dynamic Artwork Tinting.
 *
 * Features:
 * - Recessed frosted glass capillary tube for the inactive track with subtle dynamic tinting.
 * - Sub-surface ambient neon-glass bloom along the active progress spine.
 * - 3 harmonic counter-flowing liquid glass waves with specular top-edge refraction.
 * - Refractive glass lens thumb indicator that blooms smoothly on touch.
 * - True Liquid Glass floating scrubber pill with dynamic tint border and live time delta.
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

    var dragPositionMs by remember(trackKey) { mutableStateOf<Float?>(null) }
    var lastSeekValue by remember(trackKey) { mutableStateOf<Float?>(null) }
    var pendingSeekTargetMs by remember(trackKey) { mutableStateOf<Long?>(null) }

    LaunchedEffect(isInteracting, trackKey) {
        if (!isInteracting) {
            delay(300L)
            dragPositionMs = null
            lastSeekValue = null
        }
    }

    LaunchedEffect(pendingSeekTargetMs, if (pendingSeekTargetMs != null) positionMs else 0L) {
        val target = pendingSeekTargetMs ?: return@LaunchedEffect
        if (abs(positionMs - target) < 1500L) {
            pendingSeekTargetMs = null
        }
    }

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

    // Smoothly animate dynamic artwork tints so track transitions feel fluid
    val animatedPrimary by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "wavyDynamicPrimary",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "wavyDynamicSecondary",
    )
    val animatedTertiary by animateColorAsState(
        targetValue = tertiaryColor,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "wavyDynamicTertiary",
    )

    val effectivePrimary = remember(animatedPrimary, isTranslucent) {
        if (isTranslucent) animatedPrimary.ensureLiquidGlassLuminosity(0.76f) else animatedPrimary
    }
    val effectiveSecondary = remember(animatedSecondary, effectivePrimary, isTranslucent) {
        val base = if (animatedSecondary == animatedPrimary) {
            animatedPrimary.shiftTonal(lightnessDelta = 0.14f, saturationScale = 0.92f)
        } else {
            animatedSecondary
        }
        if (isTranslucent) base.ensureLiquidGlassLuminosity(0.68f) else base
    }
    val effectiveTertiary = remember(animatedTertiary, effectivePrimary, isTranslucent) {
        val base = if (animatedTertiary == animatedPrimary) {
            animatedPrimary.shiftTonal(lightnessDelta = -0.14f, saturationScale = 1.15f)
        } else {
            animatedTertiary
        }
        if (isTranslucent) base.ensureLiquidGlassLuminosity(0.60f) else base
    }

    // Dynamic-tinted frosted glass track colors
    val inactiveGlassFill = remember(effectivePrimary, isTranslucent) {
        if (isTranslucent) {
            lerp(Color.White, effectivePrimary, 0.25f).copy(alpha = 0.16f)
        } else {
            lerp( Color.Gray, effectivePrimary, 0.20f).copy(alpha = 0.14f)
        }
    }
    val inactiveGlassSpecularRim = remember(effectivePrimary, isTranslucent) {
        if (isTranslucent) {
            lerp(Color.White, effectivePrimary, 0.18f).copy(alpha = 0.34f)
        } else {
            Color.White.copy(alpha = 0.45f)
        }
    }
    val textColor = remember(effectivePrimary, isTranslucent) {
        if (isTranslucent) {
            lerp(Color.White, effectivePrimary, 0.16f).copy(alpha = 0.88f)
        } else {
            effectivePrimary.shiftTonal(lightnessDelta = -0.15f, saturationScale = 0.65f)
        }
    }

    // Pre-calculated Liquid Glass tonal palette
    val layer1Light = remember(effectiveTertiary) { effectiveTertiary.shiftTonal(lightnessDelta = +0.20f, saturationScale = 0.88f) }
    val layer1Dark = remember(effectiveTertiary) { effectiveTertiary.shiftTonal(lightnessDelta = -0.12f, saturationScale = 1.25f) }
    val layer2Light = remember(effectiveSecondary) { effectiveSecondary.shiftTonal(lightnessDelta = +0.22f, saturationScale = 0.90f) }
    val layer2Dark = remember(effectiveSecondary) { effectiveSecondary.shiftTonal(lightnessDelta = -0.12f, saturationScale = 1.25f) }
    val layer3Light = remember(effectivePrimary) {
        lerp(effectivePrimary.shiftTonal(lightnessDelta = +0.24f, saturationScale = 0.92f), Color.White, 0.38f)
    }
    val layer3Dark = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = -0.08f, saturationScale = 1.30f) }
    val thumbColor = remember(effectivePrimary) { effectivePrimary.shiftTonal(lightnessDelta = +0.05f, saturationScale = 1.12f) }

    // Accumulated phases
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
        val speed1 = twoPi / 2.5f
        val speed2 = twoPi / 1.85f
        val speed3 = twoPi / 1.35f
        val frameDelayMs = 33L // Smooth 30 FPS liquid motion with low CPU/GPU overhead
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
            delay(frameDelayMs - 5L)
        }
    }

    val density = LocalDensity.current
    val baseAmp1Px = with(density) { 7.2.dp.toPx() }
    val baseAmp2Px = with(density) { 5.2.dp.toPx() }
    val baseAmp3Px = with(density) { 3.6.dp.toPx() }
    val pausedAmpScale = 0.42f
    val draggingAmpPx = with(density) { 0.9.dp.toPx() }

    val targetAmp1 = when {
        dragging -> draggingAmpPx
        !isPlaying -> baseAmp1Px * pausedAmpScale
        else -> baseAmp1Px
    }
    val targetAmp2 = when {
        dragging -> draggingAmpPx
        !isPlaying -> baseAmp2Px * pausedAmpScale
        else -> baseAmp2Px
    }
    val targetAmp3 = when {
        dragging -> draggingAmpPx
        !isPlaying -> baseAmp3Px * pausedAmpScale
        else -> baseAmp3Px
    }

    val amp1 by animateFloatAsState(
        targetValue = targetAmp1,
        animationSpec = ExpressiveMotion.appleFluidSpring(),
        label = "Amp1",
    )
    val amp2 by animateFloatAsState(
        targetValue = targetAmp2,
        animationSpec = ExpressiveMotion.appleFluidSpring(),
        label = "Amp2",
    )
    val amp3 by animateFloatAsState(
        targetValue = targetAmp3,
        animationSpec = ExpressiveMotion.appleFluidSpring(),
        label = "Amp3",
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.38f else 1.0f,
        animationSpec = ExpressiveMotion.appleBouncySpring(),
        label = "thumbGlassScale",
    )
    val glowIntensity by animateFloatAsState(
        targetValue = if (dragging) 1.35f else if (isPlaying) 1.0f else 0.65f,
        animationSpec = tween(260),
        label = "liquidGlowIntensity",
    )

    val stroke1 = remember(density) { Stroke(width = with(density) { 1.0.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val stroke2 = remember(density) { Stroke(width = with(density) { 1.25.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val stroke3 = remember(density) { Stroke(width = with(density) { 1.65.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val specularRimStroke = remember(density) { Stroke(width = with(density) { 0.75.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }

    val waveLength1Px = with(density) { 162.dp.toPx() }
    val waveLength2Px = with(density) { 124.dp.toPx() }
    val waveLength3Px = with(density) { 92.dp.toPx() }

    val baseTrackThicknessPx = with(density) { 4.2.dp.toPx() }
    val thumbRadiusPx = with(density) { 5.8.dp.toPx() }
    val transitionLengthPx = with(density) { 38.dp.toPx() }
    val waveSampleStepPx = with(density) { 3.5.dp.toPx() }
    val horizontalPaddingPx = with(density) { 10.dp.toPx() }

    // Exact 50.dp Box geometry alignment for vertical & horizontal brushes
    val boxHeightPx = with(density) { 50.dp.toPx() }
    val centerYPx = boxHeightPx / 2f + with(density) { 6.dp.toPx() }
    val halfThicknessPx = baseTrackThicknessPx / 2f
    val bottomYPx = centerYPx + halfThicknessPx
    val topBaselineYPx = centerYPx - halfThicknessPx

    val layer1FillBrush = remember(layer1Light, layer1Dark, isTranslucent, density) {
        Brush.verticalGradient(
            colors = listOf(
                layer1Light.copy(alpha = if (isTranslucent) 0.24f else 0.18f),
                layer1Dark.copy(alpha = 0.03f),
            ),
            startY = topBaselineYPx - baseAmp1Px,
            endY = bottomYPx,
        )
    }
    val layer2FillBrush = remember(layer2Light, layer2Dark, isTranslucent, density) {
        Brush.verticalGradient(
            colors = listOf(
                layer2Light.copy(alpha = if (isTranslucent) 0.32f else 0.24f),
                layer2Dark.copy(alpha = 0.05f),
            ),
            startY = topBaselineYPx - baseAmp2Px,
            endY = bottomYPx,
        )
    }
    val layer3FillBrush = remember(layer3Light, layer3Dark, isTranslucent, density) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isTranslucent) 0.42f else 0.30f),
                layer3Light.copy(alpha = if (isTranslucent) 0.34f else 0.26f),
                layer3Dark.copy(alpha = 0.08f),
            ),
            startY = topBaselineYPx - baseAmp3Px,
            endY = bottomYPx,
        )
    }

    val safeSpanEndX = (barWidthPx - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx + 100f)

    val layer1ContourBrush = remember(layer1Light, layer1Dark, isTranslucent, safeSpanEndX) {
        Brush.horizontalGradient(
            colors = listOf(
                layer1Light.copy(alpha = if (isTranslucent) 0.52f else 0.45f),
                layer1Dark.copy(alpha = if (isTranslucent) 0.40f else 0.34f),
            ),
            startX = horizontalPaddingPx,
            endX = safeSpanEndX,
        )
    }
    val layer2ContourBrush = remember(layer2Dark, layer2Light, isTranslucent, safeSpanEndX) {
        Brush.horizontalGradient(
            colors = listOf(
                layer2Dark.copy(alpha = if (isTranslucent) 0.78f else 0.72f),
                layer2Light.copy(alpha = if (isTranslucent) 0.72f else 0.64f),
            ),
            startX = horizontalPaddingPx,
            endX = safeSpanEndX,
        )
    }
    val layer3ContourBrush = remember(layer3Light, layer3Dark, effectivePrimary, isTranslucent, safeSpanEndX) {
        Brush.horizontalGradient(
            colors = listOf(
                layer3Dark.copy(alpha = 0.94f),
                effectivePrimary.copy(alpha = 0.98f),
                layer3Light,
            ),
            startX = horizontalPaddingPx,
            endX = safeSpanEndX,
        )
    }
    val specularCrestBrush = remember(layer3Light, safeSpanEndX) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                layer3Light.copy(alpha = 0.78f),
                Color.White.copy(alpha = 0.92f),
            ),
            startX = horizontalPaddingPx,
            endX = safeSpanEndX,
        )
    }
    val baselineGlassBrush = remember(effectiveTertiary, effectiveSecondary, effectivePrimary, layer3Light, safeSpanEndX) {
        Brush.horizontalGradient(
            colors = listOf(
                effectiveTertiary.copy(alpha = 0.90f),
                effectiveSecondary.copy(alpha = 0.95f),
                effectivePrimary,
                layer3Light,
            ),
            startX = horizontalPaddingPx,
            endX = safeSpanEndX,
        )
    }

    val pathFilled1 = remember { Path() }
    val pathContour1 = remember { Path() }
    val pathFilled2 = remember { Path() }
    val pathContour2 = remember { Path() }
    val pathFilled3 = remember { Path() }
    val pathContour3 = remember { Path() }
    val clipPathBounds = remember { Path() }

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
                    val centerY = height / 2f + 6.dp.toPx()

                    val trackStartX = horizontalPaddingPx
                    val trackEndX = width - horizontalPaddingPx
                    val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                    val thumbX = trackStartX + (shownFraction * trackWidth).coerceIn(0f, trackWidth)

                    val halfThickness = baseTrackThicknessPx / 2f
                    val bottomY = centerY + halfThickness
                    val topBaselineY = centerY - halfThickness
                    val cornerRadius = CornerRadius(halfThickness, halfThickness)

                    // 1. Recessed Frosted Liquid Glass Capsule (Inactive Track)
                    drawRoundRect(
                        color = inactiveGlassFill,
                        topLeft = Offset(trackStartX, topBaselineY),
                        size = Size(trackWidth, baseTrackThicknessPx),
                        cornerRadius = cornerRadius,
                    )
                    // Top specular glass rim on the inactive tube
                    drawLine(
                        color = inactiveGlassSpecularRim,
                        start = Offset(trackStartX + halfThickness, topBaselineY + 0.5.dp.toPx()),
                        end = Offset(trackEndX - halfThickness, topBaselineY + 0.5.dp.toPx()),
                        strokeWidth = 0.75.dp.toPx(),
                        cap = StrokeCap.Round,
                    )

                    // 2. Active Dynamic-Tinted Liquid Glass Waves & Spine
                    if (thumbX > trackStartX) {
                        // Sub-surface dynamic ambient bloom underneath the active progress
                        drawLine(
                            color = effectivePrimary.copy(alpha = (0.20f * glowIntensity).coerceIn(0f, 0.38f)),
                            start = Offset(trackStartX, centerY),
                            end = Offset(thumbX, centerY),
                            strokeWidth = baseTrackThicknessPx * 3.4f,
                            cap = StrokeCap.Round,
                        )

                        clipPathBounds.reset()
                        clipPathBounds.addRoundRect(
                            RoundRect(
                                rect = Rect(
                                    left = trackStartX - halfThickness,
                                    top = 0f,
                                    right = thumbX + halfThickness,
                                    bottom = height,
                                ),
                                topLeft = cornerRadius,
                                bottomLeft = cornerRadius,
                                topRight = cornerRadius,
                                bottomRight = cornerRadius,
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
                                val span = thumbX - trackStartX
                                val effectiveTransition = minOf(transitionLengthPx, span * 0.48f)
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

                            // Layer 1: Deep Tertiary Liquid Glass Swell
                            drawPath(path = pathFilled1, brush = layer1FillBrush)
                            drawPath(path = pathContour1, brush = layer1ContourBrush, style = stroke1)

                            // Layer 2: Mid Secondary Harmonic Liquid Wave
                            drawPath(path = pathFilled2, brush = layer2FillBrush)
                            drawPath(path = pathContour2, brush = layer2ContourBrush, style = stroke2)

                            // Layer 3: Foreground Primary Dynamic Wave + Specular Glass Crest
                            drawPath(path = pathFilled3, brush = layer3FillBrush)
                            drawPath(path = pathContour3, brush = layer3ContourBrush, style = stroke3)
                            drawPath(path = pathContour3, brush = specularCrestBrush, style = specularRimStroke)

                            // Luminous Liquid Glass Spine Bar
                            drawLine(
                                brush = baselineGlassBrush,
                                start = Offset(trackStartX, centerY),
                                end = Offset(thumbX, centerY),
                                strokeWidth = baseTrackThicknessPx,
                                cap = StrokeCap.Round,
                            )
                            // Razor-thin inner white glass reflection along the active spine
                            drawLine(
                                color = Color.White.copy(alpha = 0.55f),
                                start = Offset(trackStartX, topBaselineY + 0.9.dp.toPx()),
                                end = Offset(thumbX, topBaselineY + 0.9.dp.toPx()),
                                strokeWidth = 0.85.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    // 3. Refractive Liquid Glass Jewel Thumb Indicator
                    if (boundedDurationMs > 0) {
                        val activeRadius = thumbRadiusPx * thumbScale
                        val thumbCenter = Offset(thumbX, centerY)

                        // Outer dynamic neon-glass halo
                        drawCircle(
                            color = effectivePrimary.copy(alpha = (0.34f * glowIntensity).coerceIn(0f, 0.55f)),
                            radius = activeRadius + 5.5.dp.toPx(),
                            center = thumbCenter,
                        )
                        // Frosted glass outer bezel
                        drawCircle(
                            color = Color.White.copy(alpha = 0.96f),
                            radius = activeRadius,
                            center = thumbCenter,
                        )
                        // Dynamic-tinted inner glass lens
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    layer3Light,
                                    thumbColor,
                                    layer3Dark,
                                ),
                                center = Offset(thumbX - activeRadius * 0.25f, centerY - activeRadius * 0.25f),
                                radius = activeRadius * 1.15f,
                            ),
                            radius = (activeRadius - 1.35.dp.toPx()).coerceAtLeast(1f),
                            center = thumbCenter,
                        )
                        // Primary top-left specular glass refraction glint
                        drawCircle(
                            color = Color.White.copy(alpha = 0.92f),
                            radius = (activeRadius * 0.24f).coerceAtLeast(1.1.dp.toPx()),
                            center = Offset(thumbX - activeRadius * 0.30f, centerY - activeRadius * 0.30f),
                        )
                    }
                }

                // 4. True Liquid Glass Floating Time Scrubber Pill with Dynamic Tint
                val bubbleAlpha by animateFloatAsState(
                    targetValue = if (dragging && boundedDurationMs > 0L) 1f else 0f,
                    animationSpec = ExpressiveMotion.appleFluidSpring(),
                    label = "bubbleAlpha",
                )
                val bubbleScale by animateFloatAsState(
                    targetValue = if (dragging && boundedDurationMs > 0L) 1f else 0.72f,
                    animationSpec = ExpressiveMotion.appleBouncySpring(),
                    label = "bubbleScale",
                )

                if (bubbleAlpha > 0.01f && barWidthPx > 0f) {
                    val trackStartX = horizontalPaddingPx
                    val trackEndX = barWidthPx - horizontalPaddingPx
                    val trackWidth = (trackEndX - trackStartX).coerceAtLeast(1f)
                    val thumbPx = trackStartX + (shownFraction * trackWidth).coerceIn(0f, trackWidth)

                    val backdrop = LocalLiquidGlassOverlayBackdrop.current ?: LocalLiquidGlassBackdrop.current
                    val useGlass = LocalLiquidGlass.current && isLiquidGlassBackdropSupported() && backdrop != null
                    val pillShape = RoundedCornerShape(50)

                    Surface(
                        shape = pillShape,
                        color = liquidGlassContainerColor(
                            color = lerp(Color(0xD9121216), effectivePrimary, 0.22f),
                            enabled = useGlass,
                            backdrop = backdrop,
                        ),
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    effectivePrimary.copy(alpha = 0.60f),
                                    Color.White.copy(alpha = 0.18f),
                                ),
                            ),
                        ),
                        shadowElevation = if (useGlass) 0.dp else 10.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .onSizeChanged { bubbleWidthPx = it.width.toFloat() }
                            .graphicsLayer {
                                val effBubbleWidth = if (bubbleWidthPx > 0f) bubbleWidthPx else with(density) { 98.dp.toPx() }
                                val minX = with(density) { 4.dp.toPx() }
                                val maxX = (barWidthPx - effBubbleWidth - minX).coerceAtLeast(minX)
                                translationX = (thumbPx - (effBubbleWidth / 2f)).coerceIn(minX, maxX)
                                translationY = -34.dp.toPx()
                                alpha = bubbleAlpha
                                scaleX = bubbleScale
                                scaleY = bubbleScale
                            }
                            .then(
                                if (useGlass) {
                                    Modifier.liquidGlassChrome(
                                        shape = pillShape,
                                        enabled = true,
                                        preset = LiquidGlassPreset.MiniPlayer,
                                        backdrop = backdrop,
                                        accentTint = effectivePrimary.copy(alpha = 0.28f),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.5.dp)
                                    .clip(CircleShape)
                                    .background(effectivePrimary),
                            )
                            Text(
                                text = formatTime(shownMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.55f),
                            )
                            Text(
                                text = "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0L))}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.5.sp,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = lerp(Color.White, layer3Light, 0.45f).copy(alpha = 0.90f),
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
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.labelMedium
                        }).copy(fontFeatureSettings = "tnum"),
                        color = textColor,
                    )
                    Text(
                        text = if (boundedDurationMs > 0L) "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0))}" else "--:--",
                        style = (if (isTranslucent) {
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
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
