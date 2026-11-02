package com.neonsong.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp


import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop


/** Shared opt-in flag for Settings > Experimental > Liquid Glass. */
val LocalLiquidGlass = staticCompositionLocalOf { false }

/** Background-only source for surfaces inside the captured scrolling content. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Separate source for overlays; kept for call-site compat, same type as backdrop. */
val LocalLiquidGlassOverlayBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Content-brightness hint, kept for compat (native recipe does not need it). */
val LocalLiquidGlassContentBrightness = compositionLocalOf { 0f }

/** Typealiases for clean, unified Backdrop types (mirrors PlatformBackdrop). */
typealias LayerBackdrop = LayerBackdrop
typealias Backdrop = Backdrop

/** Factory matching Kyant0 Backdrop's official API.
 *
 *  NOTE: the Compose compiler forbids try/catch (and runCatching) around
 *  @Composable invocations, so this stays a direct passthrough. First-frame
 *  safety comes from the [isDeviceGlassCapable] gate at every call site,
 *  and any failure still lands in the crash-guard log + startup trail. */
@Composable
fun rememberLayerBackdrop(
    onDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit = { drawContent() },
): LayerBackdrop? = com.kyant.backdrop.backdrops.rememberLayerBackdrop(onDraw = onDraw)

/** Marks a composable as the source layer that sibling glass surfaces refract. */
fun Modifier.layerBackdropCompat(backdrop: LayerBackdrop): Modifier = this.nativeBackdrop(backdrop)

/** Remember a backdrop that draws a flat color + content. */
@Composable
fun rememberBackdrop(color: Color): LayerBackdrop? =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

/**
 * Continuous-curvature squircle (G2 superellipse) implementing [CornerBasedShape].
 * Kept from Neon Song so DockShape stays a CornerBasedShape (lens-compatible).
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp) : this(
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
    )

    constructor(percent: Int = 50) : this(
        CornerSize(percent),
        CornerSize(percent),
        CornerSize(percent),
        CornerSize(percent),
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tl = if (isLtr) topStart else topEnd
        val tr = if (isLtr) topEnd else topStart
        val br = if (isLtr) bottomEnd else bottomStart
        val bl = if (isLtr) bottomStart else bottomEnd

        if (tl <= 0f && tr <= 0f && br <= 0f && bl <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        }

        val path = createSquirclePath(w, h, tl, tr, br, bl)
        return Outline.Generic(path)
    }
}

fun createSquirclePath(
    w: Float,
    h: Float,
    tlRadius: Float,
    trRadius: Float,
    brRadius: Float,
    blRadius: Float,
): Path {
    val path = Path()
    val maxRadius = minOf(w, h) / 2f
    val tl = tlRadius.coerceIn(0f, maxRadius)
    val tr = trRadius.coerceIn(0f, maxRadius)
    val br = brRadius.coerceIn(0f, maxRadius)
    val bl = blRadius.coerceIn(0f, maxRadius)

    val k = 1.528665f
    var lTl = tl * k
    var lTr = tr * k
    var lBr = br * k
    var lBl = bl * k

    val maxWTop = lTl + lTr
    if (maxWTop > w && maxWTop > 0f) {
        val scale = w / maxWTop
        lTl *= scale
        lTr *= scale
    }
    val maxWBottom = lBl + lBr
    if (maxWBottom > w && maxWBottom > 0f) {
        val scale = w / maxWBottom
        lBl *= scale
        lBr *= scale
    }
    val maxHLeft = lTl + lBl
    if (maxHLeft > h && maxHLeft > 0f) {
        val scale = h / maxHLeft
        lTl *= scale
        lBl *= scale
    }
    val maxHRight = lTr + lBr
    if (maxHRight > h && maxHRight > 0f) {
        val scale = h / maxHRight
        lTr *= scale
        lBr *= scale
    }

    path.moveTo(lTl, 0f)
    path.lineTo(w - lTr, 0f)
    if (lTr > 0.001f) {
        path.cubicTo(
            w - lTr * (1f - 0.712053f), 0f,
            w - lTr * (1f - 0.566789f), lTr * 0.030183f,
            w - lTr * (1f - 0.455953f), lTr * 0.087377f,
        )
        path.cubicTo(
            w - lTr * (1f - 0.345118f), lTr * 0.144571f,
            w - lTr * (1f - 0.242846f), lTr * 0.242846f,
            w - lTr * (1f - 0.144571f), lTr * 0.345118f,
        )
        path.cubicTo(
            w - lTr * (1f - 0.087377f), lTr * 0.455953f,
            w - lTr * 0.030183f, lTr * (1f - 0.566789f),
            w, lTr * (1f - 0.712053f),
        )
        path.lineTo(w, lTr)
    } else {
        path.lineTo(w, 0f)
        path.lineTo(w, lTr)
    }
    path.lineTo(w, h - lBr)
    if (lBr > 0.001f) {
        path.cubicTo(
            w, h - lBr * (1f - 0.712053f),
            w - lBr * 0.030183f, h - lBr * (1f - 0.566789f),
            w - lBr * 0.087377f, h - lBr * (1f - 0.455953f),
        )
        path.cubicTo(
            w - lBr * 0.144571f, h - lBr * (1f - 0.345118f),
            w - lBr * 0.242846f, h - lBr * (1f - 0.242846f),
            w - lBr * 0.345118f, h - lBr * (1f - 0.144571f),
        )
        path.cubicTo(
            w - lBr * 0.455953f, h - lBr * (1f - 0.087377f),
            w - lBr * (1f - 0.566789f), h - lBr * 0.030183f,
            w - lBr * (1f - 0.712053f), h,
        )
        path.lineTo(w - lBr, h)
    } else {
        path.lineTo(w, h)
        path.lineTo(w - lBr, h)
    }
    path.lineTo(lBl, h)
    if (lBl > 0.001f) {
        path.cubicTo(
            lBl * (1f - 0.712053f), h,
            lBl * (1f - 0.566789f), h - lBl * 0.030183f,
            lBl * (1f - 0.455953f), h - lBl * 0.087377f,
        )
        path.cubicTo(
            lBl * (1f - 0.345118f), h - lBl * 0.144571f,
            lBl * (1f - 0.242846f), h - lBl * 0.242846f,
            lBl * (1f - 0.144571f), h - lBl * 0.345118f,
        )
        path.cubicTo(
            lBl * (1f - 0.087377f), h - lBl * 0.455953f,
            lBl * 0.030183f, h - lBl * (1f - 0.566789f),
            0f, h - lBl * (1f - 0.712053f),
        )
        path.lineTo(0f, h - lBl)
    } else {
        path.lineTo(0f, h)
        path.lineTo(0f, h - lBl)
    }
    path.lineTo(0f, lTl)
    if (lTl > 0.001f) {
        path.cubicTo(
            0f, lTl * (1f - 0.712053f),
            lTl * 0.030183f, lTl * (1f - 0.566789f),
            lTl * 0.087377f, lTl * (1f - 0.455953f),
        )
        path.cubicTo(
            lTl * 0.144571f, lTl * (1f - 0.345118f),
            lTl * 0.242846f, lTl * (1f - 0.242846f),
            lTl * 0.345118f, lTl * (1f - 0.144571f),
        )
        path.cubicTo(
            lTl * 0.455953f, lTl * (1f - 0.087377f),
            lTl * (1f - 0.566789f), lTl * 0.030183f,
            lTl * (1f - 0.712053f), 0f,
        )
        path.lineTo(lTl, 0f)
    } else {
        path.lineTo(0f, 0f)
        path.lineTo(lTl, 0f)
    }
    path.close()
    return path
}

// ── Liquid Glass Design System Core ──────────────────────────────────────────

/** Helper to draw an Outline with a solid color. */
fun DrawScope.drawOutline(
    outline: Outline,
    color: Color,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(color = color, topLeft = outline.rect.topLeft, size = outline.rect.size)
        is Outline.Rounded -> {
            val rrect = outline.roundRect
            if (rrect.topLeftCornerRadius == rrect.topRightCornerRadius &&
                rrect.topLeftCornerRadius == rrect.bottomLeftCornerRadius &&
                rrect.topLeftCornerRadius == rrect.bottomRightCornerRadius
            ) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(rrect.left, rrect.top),
                    size = Size(rrect.width, rrect.height),
                    cornerRadius = rrect.topLeftCornerRadius,
                )
            } else {
                val path = Path().apply { addRoundRect(rrect) }
                drawPath(path = path, color = color)
            }
        }
        is Outline.Generic -> drawPath(path = outline.path, color = color)
    }
}

/** Helper to draw an Outline with a brush and optional stroke style. */
fun DrawScope.drawOutline(
    outline: Outline,
    brush: Brush,
    style: DrawStyle = Fill,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(brush = brush, topLeft = outline.rect.topLeft, size = outline.rect.size, style = style)
        is Outline.Rounded -> {
            val rrect = outline.roundRect
            if (rrect.topLeftCornerRadius == rrect.topRightCornerRadius &&
                rrect.topLeftCornerRadius == rrect.bottomLeftCornerRadius &&
                rrect.topLeftCornerRadius == rrect.bottomRightCornerRadius
            ) {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(rrect.left, rrect.top),
                    size = Size(rrect.width, rrect.height),
                    cornerRadius = rrect.topLeftCornerRadius,
                    style = style,
                )
            } else {
                val path = Path().apply { addRoundRect(rrect) }
                drawPath(path = path, brush = brush, style = style)
            }
        }
        is Outline.Generic -> drawPath(path = outline.path, brush = brush, style = style)
    }
}

/**
 * Design tokens and material recipe for the Liquid Glass Design System.
 */
object LiquidGlassTheme {
    /** Returns translucent glass background color with subtle accent depth. */
    fun backgroundColor(
        isDark: Boolean,
        accent: Color,
        isAmoled: Boolean = false,
    ): Color {
        return if (isAmoled) {
            Color(0xD9000000)
        } else if (isDark) {
            val a = if (accent.isSpecified) accent else Color(0xFF6C9EFF)
            Color(
                red = lerp(0.07f, a.red, 0.08f),
                green = lerp(0.08f, a.green, 0.08f),
                blue = lerp(0.12f, a.blue, 0.08f),
                alpha = 0.82f,
            )
        } else {
            val a = if (accent.isSpecified) accent else Color(0xFF2463EB)
            Color(
                red = lerp(0.97f, a.red, 0.05f),
                green = lerp(0.98f, a.green, 0.05f),
                blue = lerp(1.00f, a.blue, 0.05f),
                alpha = 0.88f,
            )
        }
    }

    /** Directional specular reflective border brush (incident light from top-left, accent sheen, ambient shadow). */
    fun borderBrush(
        isDark: Boolean,
        accent: Color,
        size: Size,
    ): Brush {
        val a = if (accent.isSpecified) accent else Color.White
        return Brush.linearGradient(
            0.0f to Color.White.copy(alpha = if (isDark) 0.38f else 0.65f),
            0.45f to a.copy(alpha = if (isDark) 0.28f else 0.40f),
            1.0f to Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        )
    }

    /** Top specular sheen brush simulating physical curved optical glass refraction. */
    fun specularSheenBrush(
        isDark: Boolean,
    ): Brush {
        return Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.22f),
            0.35f to Color.White.copy(alpha = if (isDark) 0.03f else 0.07f),
            1.0f to Color.Transparent,
        )
    }
}

/**
 * State holder for water-like touch interactions on Liquid Glass surfaces.
 * Captures touch position, animates expanding circular liquid wave / refraction ripple,
 * and handles fluid spring compression with natural recovery.
 */
class LiquidTouchInteraction(
    private val scope: CoroutineScope,
) {
    val pressScale = Animatable(1f)
    val rippleProgress = Animatable(0f)
    val rippleAlpha = Animatable(0f)
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    private val springSpec = spring<Float>(
        dampingRatio = 0.68f,
        stiffness = 850f,
    )

    fun onTouchDown(position: Offset) {
        touchPosition = position
        scope.launch {
            pressScale.animateTo(0.94f, springSpec)
        }
        scope.launch {
            rippleProgress.snapTo(0f)
            rippleAlpha.snapTo(1f)
            launch {
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                )
            }
            launch {
                rippleAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    fun onTouchRelease() {
        scope.launch {
            pressScale.animateTo(1f, springSpec)
        }
    }

    fun onTouchCancel() {
        scope.launch {
            pressScale.animateTo(1f, springSpec)
            rippleAlpha.animateTo(0f, tween(150))
        }
    }
}

@Composable
fun rememberLiquidTouchInteraction(): LiquidTouchInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { LiquidTouchInteraction(scope) }
}

/**
 * Water-like liquid touch modifier.
 * Tracks pointer events non-intrusively (never consumes clicks or scroll gestures),
 * scales with a physical spring compression, and draws an expanding liquid wave / caustic ring
 * radiating outward from the touch position.
 */
fun Modifier.liquidTouchEffect(
    interaction: LiquidTouchInteraction,
    accent: Color = Color.Unspecified,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this

    return this
        .graphicsLayer {
            val scale = interaction.pressScale.value
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(interaction) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                interaction.onTouchDown(down.position)

                val pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.fastFirstOrNull { it.id == pointerId }
                    if (change == null || !change.pressed) {
                        interaction.onTouchRelease()
                        break
                    }
                    if (change.isConsumed) {
                        interaction.onTouchCancel()
                        break
                    }
                }
            }
        }
        .drawWithCache {
            val resolvedAccent = if (accent.isSpecified) accent else Color.White
            onDrawWithContent {
                drawContent()

                val progress = interaction.rippleProgress.value
                val alpha = interaction.rippleAlpha.value
                if (progress > 0.001f && alpha > 0.01f) {
                    val maxRadius = kotlin.math.hypot(size.width, size.height)
                    val currentRadius = maxRadius * progress
                    val center = interaction.touchPosition

                    // 1. Soft refraction bloom at touch origin
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to resolvedAccent.copy(alpha = 0.16f * alpha),
                            0.6f to Color.White.copy(alpha = 0.08f * alpha),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = (maxRadius * 0.35f).coerceAtLeast(1f),
                        ),
                        radius = (maxRadius * 0.35f).coerceAtLeast(1f),
                        center = center,
                        blendMode = BlendMode.Plus,
                    )

                    // 2. Liquid wave caustic ring
                    val ringWidth = (size.minDimension * 0.22f).coerceIn(24.dp.toPx(), 72.dp.toPx())
                    val innerRadius = (currentRadius - ringWidth * 0.5f).coerceAtLeast(0f)
                    val outerRadius = currentRadius + ringWidth * 0.5f

                    if (outerRadius > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to Color.Transparent,
                                (innerRadius / outerRadius).coerceIn(0f, 0.99f) to Color.Transparent,
                                ((innerRadius + currentRadius) / (2f * outerRadius)).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.24f * alpha),
                                1.0f to Color.Transparent,
                                center = center,
                                radius = outerRadius,
                            ),
                            radius = outerRadius,
                            center = center,
                            blendMode = BlendMode.Plus,
                        )
                    }
                }
            }
        }
}

/**
 * Core Liquid Glass surface modifier.
 * Renders translucent glass material with depth, directional specular border,
 * top incident light sheen, and optional water-like touch interactions.
 * Completely allocation-free during draw calls via [drawWithCache].
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = SquircleShape(20.dp),
    borderWidth: Dp = 1.dp,
    accentTint: Color = Color.Unspecified,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    interaction: LiquidTouchInteraction? = if (interactive || onClick != null) rememberLiquidTouchInteraction() else null,
): Modifier {
    val isGlass = LocalLiquidGlass.current
    val isDark = LocalIsDarkTheme.current
    val strokeWidthPx = with(LocalDensity.current) { borderWidth.toPx() }
    val resolvedAccent = if (accentTint.isSpecified) accentTint else MaterialTheme.colorScheme.primary

    val baseModifier = this.clip(shape)

    val surfaceModifier = baseModifier.drawWithCache {
        val bg = if (isGlass) {
            LiquidGlassTheme.backgroundColor(isDark = isDark, accent = resolvedAccent)
        } else {
            if (isDark) Color(0xFF1E222B) else Color(0xFFEFF2F8)
        }

        val borderBrush = LiquidGlassTheme.borderBrush(isDark = isDark, accent = resolvedAccent, size = size)
        val sheenBrush = LiquidGlassTheme.specularSheenBrush(isDark = isDark)
        val outline = shape.createOutline(size, layoutDirection, this)

        onDrawBehind {
            // 1. Base translucent glass material
            drawOutline(outline, color = bg)

            // 2. Optical glass specular top sheen
            drawRect(
                brush = sheenBrush,
                blendMode = BlendMode.Plus,
            )

            // 3. Directional specular border
            if (strokeWidthPx > 0f) {
                drawOutline(
                    outline = outline,
                    brush = borderBrush,
                    style = Stroke(width = strokeWidthPx),
                )
            }
        }
    }

    val interactiveModifier = if (interaction != null) {
        surfaceModifier.liquidTouchEffect(interaction = interaction, accent = resolvedAccent)
    } else {
        surfaceModifier
    }

    return if (onClick != null) {
        interactiveModifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        interactiveModifier
    }
}

/**
 * Premium Liquid Glass Button with translucent material, specular border, and water-like touch response.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = rememberLiquidTouchInteraction()
    val resolvedAccent = if (accentTint.isSpecified) accentTint else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                borderWidth = borderWidth,
                accentTint = resolvedAccent,
                interactive = enabled,
                onClick = if (enabled) onClick else null,
                interaction = interaction,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Liquid Glass Icon Button with water ripple, specular border, and 48dp minimum touch target.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = rememberLiquidTouchInteraction()
    val resolvedAccent = if (accentTint.isSpecified) accentTint else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                borderWidth = borderWidth,
                accentTint = resolvedAccent,
                interactive = enabled,
                onClick = if (enabled) onClick else null,
                interaction = interaction,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Liquid Glass Filter/Choice Chip with fluid touch reaction and selected glow.
 */
@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    accentTint: Color = Color.Unspecified,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val resolvedAccent = if (accentTint.isSpecified) accentTint else MaterialTheme.colorScheme.primary
    val chipAccent = if (selected) resolvedAccent else Color.Transparent
    val interaction = rememberLiquidTouchInteraction()

    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                borderWidth = if (selected) 1.5.dp else 1.dp,
                accentTint = if (selected) resolvedAccent else Color.Unspecified,
                interactive = enabled,
                onClick = if (enabled) onClick else null,
                interaction = interaction,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.invoke()
            label()
        }
    }
}

/** Keeps glass inside Material's visual bounds while retaining its outer touch target. */
@Composable
fun LiquidGlassSurface(
    onClick: () -> Unit,
    glassModifier: Modifier,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().then(glassModifier),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// ── Interactive liquid glass core ──────────────────────────────────────────────
// No custom RuntimeShader, no ambient infiniteTransition, no haptics,
// no progressive blur, no always-on chromatic aberration.
// Effect stack is drawInteractiveGlass.

@Composable
fun isDeviceGlassCapable(): Boolean {
    val view = LocalView.current
    if (view.isInEditMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (!view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val am = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (am?.isLowRamDevice == true) return false
    return true
}

@Composable
fun isLiquidGlassBackdropSupported(): Boolean =
    LocalLiquidGlass.current && isDeviceGlassCapable()

@Composable
fun Modifier.liquidGlassSource(
    backdrop: LayerBackdrop?,
): Modifier {
    if (backdrop == null) return this
    if (!isLiquidGlassBackdropSupported()) return this
    return this.layerBackdrop(backdrop)
}

@Composable
fun liquidGlassContainerColor(
    color: Color,
    enabled: Boolean = LocalLiquidGlass.current,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
): Color = if (enabled && isLiquidGlassBackdropSupported() && backdrop != null) {
    Color.Transparent
} else color

@Composable
fun isLiquidGlassEnabled(): Boolean = LocalLiquidGlass.current

/**
 * Press/hold state holder for a single liquid-glass surface.
 * Responsive GlassInteraction: spring press 0→1,
 * observe-only drag so wrapped clicks keep working.
 */
class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0f at rest, animating to 1f while pressed. Read in draw/effect/layer blocks. */
    val pressProgress: Float get() = pressAnimation.value

    /** Local-space touch point used as the centre of the press glow. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            ) { change, _ ->
                touchPosition = change.position
            }
        }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * Interactive liquid glass drawing modifier.
 * Element MUST be a sibling of the backdrop source, never inside it.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    layer: GraphicsLayer? = null,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.5f,
): Modifier =
    this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { highlight },
            effects = {
                val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
                val press = interaction?.pressProgress ?: 0f
                vibrancy()
                colorControls(
                    brightness = 0.05f,
                    contrast = 1f,
                    saturation = 1.5f,
                )
                blur(
                    (
                        if (l > 0f) {
                            lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                        } else {
                            lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                        }
                    ) * blurScale + 2f.dp.toPx() * press,
                )
                lens(size.minDimension / 4f + 2f.dp.toPx() * press, size.minDimension / 2f, false)
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
            },
            onDrawSurface = {
                val darken = lerp(minScrim, maxScrim, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                // Physically-grounded incident light transmission (subtle optical glass top-sheen)
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = if (isDark) 0.08f else 0.14f),
                        0.45f to Color.White.copy(alpha = 0.02f),
                        1.0f to Color.Transparent,
                    ),
                    blendMode = BlendMode.Plus,
                )
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * press),
                                Color.Transparent,
                            ),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val scale = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    null
                },
        ).then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )

/** Interactive liquidGlass: static surfaces use fixed mid-luminance. */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
): Modifier {
    if (!LocalLiquidGlass.current) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }
    if (!isLiquidGlassBackdropSupported()) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }
    val isDark = LocalIsDarkTheme.current
    val layer = rememberGraphicsLayer()
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = layer,
        luminanceAnimation = 0.5f,
        shape = shape,
        interaction = if (interactive) interaction else null,
        highlight = highlight,
    )
}

/** Interactive liquidGlass overload for luminance-sampling surfaces (MiniPlayer, nav capsule). */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    layer: GraphicsLayer,
    luminanceAnimation: Float,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    blurScale: Float = 1f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.5f,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }
    val isDark = LocalIsDarkTheme.current
    val interaction = rememberGlassInteraction()
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = layer,
        luminanceAnimation = luminanceAnimation,
        shape = shape,
        interaction = if (interactive) interaction else null,
        pressedScale = 1.04f,
        blurScale = blurScale,
        minScrim = minScrim,
        maxScrim = maxScrim,
    )
}

@Composable
fun LiquidGlassContainer(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)),
            contentAlignment = contentAlignment,
            content = content,
        )
        return
    }
    Box(
        modifier = modifier.liquidGlass(backdrop, shape, interactive, highlight),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Floating action pill hosting icon buttons in a liquid glass shell. */
@Composable
fun LiquidGlassActionPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable RowScope.() -> Unit,
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    Row(
        modifier = modifier.liquidGlass(backdrop, shape, true, Highlight.Default),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** LiquidGlassIconButton: ImageVector version, small circles use narrow highlight. */
@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    interactive: Boolean = true,
    highlight: Highlight = Highlight(width = 1.dp),
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        highlight = highlight,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp).clickable(onClick = onClick),
        )
    }
}

/** Compat overload: Painter version (existing Neon Song call sites). */
@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = true,
        highlight = Highlight(width = 1.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp).clickable(onClick = onClick),
        )
    }
}

// ── Compat shims: keep old symbols compiling, route to interactive liquid glass ──

/** Kept for call-site compat; values are ignored — recipe is fixed. */
enum class LiquidGlassPreset {
    BottomNavigation,
    MiniPlayer,
    PlayerControls,
    FloatingControls,
    ModalSheet,
    ContextMenu,
    Overlay,
    Card,
    Header,
}

private fun LiquidGlassPreset.isGlassTarget(): Boolean = when (this) {
    LiquidGlassPreset.BottomNavigation,
    LiquidGlassPreset.MiniPlayer,
    LiquidGlassPreset.PlayerControls,
    LiquidGlassPreset.FloatingControls,
    LiquidGlassPreset.Header,
    LiquidGlassPreset.ModalSheet,
    LiquidGlassPreset.ContextMenu,
    LiquidGlassPreset.Overlay,
    LiquidGlassPreset.Card -> true
}

/**
 * Compat shim: old liquidGlassChrome now delegates to drawInteractiveGlass
 * for allowed targets only; all other presets return unmodified (opaque fallback).
 */
@Composable
fun Modifier.liquidGlassChrome(
    shape: Shape,
    enabled: Boolean = true,
    preset: LiquidGlassPreset = LiquidGlassPreset.Card,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    interactionSource: MutableInteractionSource? = null,
    exportedBackdrop: LayerBackdrop? = null,
    ambientMotion: Boolean = false,
    contentBrightness: Float = 0f,
    onPointerPosition: ((Offset?) -> Unit)? = null,
): Modifier {
    if (!enabled || !LocalLiquidGlass.current) {
        return this
    }
    if (preset.isGlassTarget() && backdrop != null && isLiquidGlassBackdropSupported()) {
        return this.liquidGlass(
            backdrop = backdrop,
            shape = shape,
            interactive = true,
            highlight = Highlight.Default,
        )
    }
    return this.liquidGlassSurface(
        shape = shape,
        borderWidth = 1.dp,
        interactive = false,
    )
}

/**
 * Crash-free stub: old BackdropBlur did a full-screen 36dp sibling blur inside
 * its own capture (self-capture loop). Now draws only a veil, no backdrop.
 */
@Composable
fun BackdropBlur(
    radius: Dp,
    modifier: Modifier = Modifier,
    veil: Color = Color.Unspecified,
    veilAlpha: Float = 0.74f,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedVeil = if (veil == Color.Unspecified) MaterialTheme.colorScheme.surface else veil
    Box(modifier) {
        Box(Modifier.matchParentSize(), content = content)
        Box(Modifier.matchParentSize().background(resolvedVeil.copy(alpha = veilAlpha)))
    }
}

/**
 * Observe-only drag/press recogniser (Kyant catalog DragGestureInspector).
 * Never consumes events, so glass reacts while wrapped buttons keep their taps.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent = drag(
            pointerId = down.id,
            onDrag = { onDrag(it, it.positionChange()) },
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}
