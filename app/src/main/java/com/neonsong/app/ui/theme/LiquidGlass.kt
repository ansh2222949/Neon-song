package com.neonsong.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sign

val LocalLiquidGlass = staticCompositionLocalOf { true }
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalLiquidGlassOverlayBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalLiquidGlassContentBrightness = compositionLocalOf { 0f }

typealias LayerBackdrop = LayerBackdrop
typealias Backdrop = Backdrop

@Composable
fun rememberLayerBackdrop(
    onDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit = { drawContent() },
): LayerBackdrop? = com.kyant.backdrop.backdrops.rememberLayerBackdrop(onDraw = onDraw)

fun Modifier.layerBackdropCompat(backdrop: LayerBackdrop): Modifier = this.nativeBackdrop(backdrop)

@Composable
fun rememberBackdrop(color: Color): LayerBackdrop? =
    rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }

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

private val ScratchOutlinePath = object : ThreadLocal<Path>() {
    override fun initialValue(): Path = Path()
}

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
                val path = ScratchOutlinePath.get()!!.apply {
                    reset()
                    addRoundRect(rrect)
                }
                drawPath(path = path, color = color)
            }
        }
        is Outline.Generic -> drawPath(path = outline.path, color = color)
    }
}

fun DrawScope.drawOutline(
    outline: Outline,
    brush: Brush,
    style: DrawStyle = Fill,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = style,
            blendMode = blendMode,
        )
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
                    blendMode = blendMode,
                )
            } else {
                val path = ScratchOutlinePath.get()!!.apply {
                    reset()
                    addRoundRect(rrect)
                }
                drawPath(path = path, brush = brush, style = style, blendMode = blendMode)
            }
        }
        is Outline.Generic -> drawPath(path = outline.path, brush = brush, style = style, blendMode = blendMode)
    }
}

/**
 * Apple iOS 26 / visionOS Optical Liquid Glass Tokens:
 * Multi-stop Fresnel rim refraction, curved meniscus top sheen, and translucent chromatic depth.
 */
object LiquidGlassTheme {
    fun backgroundColor(
        isDark: Boolean,
        accent: Color,
        isAmoled: Boolean = false,
        alphaScale: Float = 1f,
    ): Color {
        val hasCustomAccent = accent.isSpecified && accent != Color.Transparent
        val a = if (hasCustomAccent) accent else Color.White
        val tintWeight = if (hasCustomAccent) 0.14f else 0.04f
        // Match the MiniPlayer's default glass veil at its neutral content brightness.
        val surfaceAlpha = (0.25f * alphaScale).coerceIn(0f, 0.92f)
        return when {
            isAmoled -> Color(
                red = lerp(0.06f, a.red, tintWeight * 0.7f),
                green = lerp(0.07f, a.green, tintWeight * 0.7f),
                blue = lerp(0.09f, a.blue, tintWeight * 0.7f),
                alpha = surfaceAlpha,
            )
            isDark -> Color(
                red = lerp(0.11f, a.red, tintWeight),
                green = lerp(0.12f, a.green, tintWeight),
                blue = lerp(0.15f, a.blue, tintWeight),
                alpha = surfaceAlpha,
            )
            else -> Color(
                red = lerp(0.95f, a.red, tintWeight * 0.65f),
                green = lerp(0.96f, a.green, tintWeight * 0.65f),
                blue = lerp(0.99f, a.blue, tintWeight * 0.65f),
                alpha = surfaceAlpha,
            )
        }
    }

    /**
     * Optical Fresnel rim gradient: bright overhead specular crest at top-left,
     * subtle chromatic refraction along the mid-wall, and a secondary bottom-right bounce reflection.
     */
    fun borderBrush(
        isDark: Boolean,
        accent: Color,
        size: Size,
        pressBoost: Float = 0f,
    ): Brush {
        val a = if (accent.isSpecified && accent != Color.Transparent) accent else Color.White
        val boost = pressBoost.coerceIn(0f, 1f) * 0.18f
        val topLeftAlpha = (if (isDark) 0.52f else 0.82f) + boost
        val upperMidAlpha = (if (isDark) 0.22f else 0.38f) + boost * 0.6f
        val lowerMidAlpha = (if (isDark) 0.06f else 0.14f) + boost * 0.3f
        val bottomBounceAlpha = (if (isDark) 0.24f else 0.44f) + boost * 0.8f

        return Brush.linearGradient(
            0.0f to Color.White.copy(alpha = topLeftAlpha.coerceAtMost(0.98f)),
            0.28f to a.copy(alpha = upperMidAlpha.coerceAtMost(0.85f)),
            0.68f to Color.White.copy(alpha = lowerMidAlpha.coerceAtMost(0.60f)),
            1.0f to a.copy(alpha = bottomBounceAlpha.coerceAtMost(0.90f)),
            start = Offset.Zero,
            end = Offset(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f)),
        )
    }

    /**
     * Inner meniscus depth gradient: brightens the top optical lip and adds subtle lower-edge thickness.
     */
    fun specularSheenBrush(
        isDark: Boolean,
        accent: Color = Color.Unspecified,
        pressBoost: Float = 0f,
    ): Brush {
        val a = if (accent.isSpecified && accent != Color.Transparent) accent else Color.White
        val boost = pressBoost.coerceIn(0f, 1f) * 0.06f
        return Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = (if (isDark) 0.16f else 0.32f) + boost),
            0.18f to Color.White.copy(alpha = (if (isDark) 0.05f else 0.11f) + boost * 0.5f),
            0.55f to Color.Transparent,
            0.88f to a.copy(alpha = if (isDark) 0.02f else 0.04f),
            1.0f to a.copy(alpha = (if (isDark) 0.06f else 0.10f) + boost * 0.5f),
        )
    }

    /**
     * Soft inner thickness bevel that gives glass pills and cards 3D curvature.
     */
    fun innerBevelBrush(
        isDark: Boolean,
        size: Size,
    ): Brush {
        return Brush.linearGradient(
            0.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.25f),
            0.40f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = if (isDark) 0.18f else 0.05f),
            start = Offset(0f, 0f),
            end = Offset(0f, size.height.coerceAtLeast(1f)),
        )
    }
}

/**
 * Tactile Apple spring press + localized optical caustic illumination.
 */
class LiquidTouchInteraction(
    private val scope: CoroutineScope,
) {
    val pressScale = Animatable(1f)
    val glowAlpha = Animatable(0f)
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    private val pressSpring = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 620f,
    )

    private val releaseSpring = spring<Float>(
        dampingRatio = 0.68f,
        stiffness = 480f,
    )

    fun onTouchDown(position: Offset) {
        touchPosition = position
        scope.launch {
            pressScale.animateTo(0.962f, pressSpring)
        }
        scope.launch {
            glowAlpha.animateTo(1f, tween(95, easing = FastOutSlowInEasing))
        }
    }

    fun onTouchMove(position: Offset) {
        touchPosition = position
    }

    fun onTouchRelease() {
        scope.launch { pressScale.animateTo(1f, releaseSpring) }
        scope.launch { glowAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing)) }
    }

    fun onTouchCancel() {
        scope.launch { pressScale.animateTo(1f, releaseSpring) }
        scope.launch { glowAlpha.animateTo(0f, tween(160)) }
    }
}

@Composable
fun rememberLiquidTouchInteraction(): LiquidTouchInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { LiquidTouchInteraction(scope) }
}

fun Modifier.liquidTouchEffect(
    interaction: LiquidTouchInteraction?,
    accent: Color = Color.Unspecified,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || interaction == null) return this

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
                    interaction.onTouchMove(change.position)
                }
            }
        }
        .drawWithCache {
            val resolvedAccent = if (accent.isSpecified && accent != Color.Transparent) accent else Color.White
            onDrawWithContent {
                drawContent()
                val alpha = interaction.glowAlpha.value
                if (alpha > 0.005f) {
                    val radius = (size.maxDimension * 0.85f).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.20f * alpha),
                            0.38f to resolvedAccent.copy(alpha = 0.10f * alpha),
                            0.75f to resolvedAccent.copy(alpha = 0.03f * alpha),
                            1.0f to Color.Transparent,
                            center = interaction.touchPosition,
                            radius = radius,
                        ),
                        radius = radius,
                        center = interaction.touchPosition,
                        blendMode = BlendMode.Plus,
                    )
                }
            }
        }
}

@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = SquircleShape(20.dp),
    borderWidth: Dp = 0.9.dp,
    accentTint: Color = Color.Unspecified,
    alphaScale: Float = 1f,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    interaction: LiquidTouchInteraction? = if (interactive || onClick != null) rememberLiquidTouchInteraction() else null,
): Modifier {
    val isGlass = LocalLiquidGlass.current
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { borderWidth.toPx() }
    val innerBevelPx = with(density) { 1.6.dp.toPx() }
    val resolvedAccent = if (accentTint.isSpecified) accentTint else MaterialTheme.colorScheme.primary

    val surfaceModifier = this
        .clip(shape)
        .drawWithCache {
            val bg = if (isGlass) {
                LiquidGlassTheme.backgroundColor(
                    isDark = isDark,
                    accent = resolvedAccent,
                    alphaScale = alphaScale,
                )
            } else {
                if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            }
            val baseBorderBrush = LiquidGlassTheme.borderBrush(isDark = isDark, accent = resolvedAccent, size = size)
            val sheenBrush = LiquidGlassTheme.specularSheenBrush(isDark = isDark, accent = resolvedAccent)
            val bevelBrush = LiquidGlassTheme.innerBevelBrush(isDark = isDark, size = size)
            val outline = shape.createOutline(size, layoutDirection, this)

            onDrawBehind {
                drawOutline(outline, color = bg)
                if (isGlass) {
                    drawOutline(
                        outline = outline,
                        brush = sheenBrush,
                        style = Fill,
                        blendMode = BlendMode.Plus,
                    )
                    if (innerBevelPx > 0f) {
                        drawOutline(
                            outline = outline,
                            brush = bevelBrush,
                            style = Stroke(width = innerBevelPx),
                        )
                    }
                }
                if (strokeWidthPx > 0f) {
                    val glow = interaction?.glowAlpha?.value ?: 0f
                    val activeBorderBrush = if (glow > 0.02f && interaction != null) {
                        val radius = (size.maxDimension * 0.95f).coerceAtLeast(1f)
                        Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = (if (isDark) 0.78f else 0.95f)),
                            0.45f to resolvedAccent.copy(alpha = (if (isDark) 0.42f else 0.58f)),
                            1.0f to Color.White.copy(alpha = if (isDark) 0.14f else 0.28f),
                            center = interaction.touchPosition,
                            radius = radius,
                        )
                    } else {
                        baseBorderBrush
                    }
                    drawOutline(
                        outline = outline,
                        brush = activeBorderBrush,
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
        interactiveModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        interactiveModifier
    }
}

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp = 0.9.dp,
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

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp = 0.9.dp,
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
            )
            .semantics(mergeDescendants = true) {
                if (!contentDescription.isNullOrBlank()) this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

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
    val interaction = rememberLiquidTouchInteraction()

    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                borderWidth = if (selected) 1.25.dp else 0.85.dp,
                accentTint = if (selected) resolvedAccent else Color.Unspecified,
                alphaScale = if (selected) 1.15f else 0.92f,
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

@Composable
fun LiquidGlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    accentTint: Color = Color.Unspecified,
    leadingIcon: (@Composable () -> Unit)? = null,
) = LiquidGlassChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    accentTint = accentTint,
    leadingIcon = leadingIcon,
)

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

// ── Interactive Real-Time Backdrop Liquid Glass Core ─────────────────────────

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

class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring(
        dampingRatio = 0.74f,
        stiffness = 580f,
        visibilityThreshold = 0.001f,
    )
    private val releaseSpec = spring(
        dampingRatio = 0.66f,
        stiffness = 440f,
        visibilityThreshold = 0.001f,
    )
    private val pressAnimation = Animatable(0f, 0.001f)

    val pressProgress: Float get() = pressAnimation.value

    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, releaseSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, releaseSpec) } },
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
 * Real-time optical meniscus backdrop shader:
 * Combines vibrancy + saturation lift, luminance-adaptive Gaussian blur,
 * edge-meniscus lens refraction, Fresnel specular rim, and pointer-tracking caustics.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    layer: GraphicsLayer? = null,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 0.982f,
    highlight: Highlight = Highlight(width = 0.95.dp),
    blurScale: Float = 1f,
    lensIntensity: Float = 1f,
    minScrim: Float = 0.14f,
    maxScrim: Float = 0.38f,
    accentTint: Color = Color.Unspecified,
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
                    brightness = if (isDark) 0.045f + 0.02f * press else 0.06f + 0.02f * press,
                    contrast = 1.05f,
                    saturation = 1.58f + 0.12f * press,
                )
                val baseBlurPx = if (l > 0f) {
                    lerp(14f.dp.toPx(), 24f.dp.toPx(), l)
                } else {
                    lerp(14f.dp.toPx(), 8f.dp.toPx(), -l)
                }
                blur(baseBlurPx * blurScale + 2.5f.dp.toPx() * press)

                val minDim = size.minDimension.coerceAtLeast(1f)
                val edgeWidth = (minDim * 0.24f * lensIntensity + 2.dp.toPx() * press)
                    .coerceAtMost(minDim * 0.48f)
                val refractionDepth = (minDim * 0.46f * lensIntensity + 3.dp.toPx() * press)
                    .coerceAtMost(minDim * 0.65f)
                if (lensIntensity > 0.05f) {
                    lens(edgeWidth, refractionDepth, false)
                }
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
            },
            onDrawSurface = {
                val press = interaction?.pressProgress ?: 0f
                val lumFactor = ((luminanceAnimation - 0.25f) / 0.55f).coerceIn(0f, 1f)
                val baseAlpha = lerp(minScrim, maxScrim, lumFactor)
                val tintColor = if (accentTint.isSpecified && accentTint != Color.Transparent) {
                    accentTint
                } else {
                    if (isDark) Color(0xFF14161C) else Color(0xFFF7F9FC)
                }

                // 1. Optical glass body veil (preserves background color vibrancy without muddy grey)
                val surfaceTint = if (accentTint.isSpecified && accentTint != Color.Transparent) {
                    Color(
                        red = lerp(if (isDark) 0.08f else 0.94f, tintColor.red, if (isDark) 0.24f else 0.16f),
                        green = lerp(if (isDark) 0.09f else 0.95f, tintColor.green, if (isDark) 0.24f else 0.16f),
                        blue = lerp(if (isDark) 0.12f else 0.98f, tintColor.blue, if (isDark) 0.24f else 0.16f),
                        alpha = baseAlpha,
                    )
                } else {
                    (if (isDark) Color(0xFF0E1015) else Color(0xFFF8FAFF)).copy(alpha = baseAlpha)
                }
                drawRect(surfaceTint)

                // 2. Curved meniscus top sheen + subtle bottom bounce reflection
                drawRect(
                    brush = LiquidGlassTheme.specularSheenBrush(
                        isDark = isDark,
                        accent = tintColor,
                        pressBoost = press,
                    ),
                    blendMode = BlendMode.Plus,
                )

                // 3. Crisp Fresnel outer rim + inner thickness bevel following the exact shape outline
                val outline = shape.createOutline(size, layoutDirection, this)
                val rimStrokePx = 1.05.dp.toPx()
                val rimBrush = if (press > 0.02f && interaction != null) {
                    val radius = max(size.width, size.height).coerceAtLeast(1f)
                    Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = if (isDark) 0.82f else 0.96f),
                        0.45f to tintColor.copy(alpha = if (isDark) 0.44f else 0.62f),
                        1.0f to Color.White.copy(alpha = if (isDark) 0.14f else 0.28f),
                        center = interaction.touchPosition,
                        radius = radius,
                    )
                } else {
                    LiquidGlassTheme.borderBrush(
                        isDark = isDark,
                        accent = tintColor,
                        size = size,
                        pressBoost = press,
                    )
                }
                drawOutline(
                    outline = outline,
                    brush = rimBrush,
                    style = Stroke(width = rimStrokePx),
                )

                // 4. Interactive localized caustic bloom under pointer
                if (press > 0.005f) {
                    val center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.maxDimension * 0.95f).coerceAtLeast(1f)
                    drawRect(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.20f * press),
                            0.42f to tintColor.copy(alpha = 0.10f * press),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = radius,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val p = interaction.pressProgress
                        val scale = lerp(1f, pressedScale, p)
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

@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight(width = 0.95.dp),
    accentTint: Color = Color.Unspecified,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) {
        return this.liquidGlassSurface(
            shape = shape,
            accentTint = accentTint,
            interactive = interactive,
        )
    }
    val isDark = LocalIsDarkTheme.current
    val interaction = if (interactive) rememberGlassInteraction() else null
    val brightness = LocalLiquidGlassContentBrightness.current
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = null,
        luminanceAnimation = if (brightness > 0f) brightness else 0.45f,
        shape = shape,
        interaction = interaction,
        highlight = highlight,
        accentTint = accentTint,
    )
}

@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    layer: GraphicsLayer,
    luminanceAnimation: Float,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    blurScale: Float = 1f,
    minScrim: Float = 0.14f,
    maxScrim: Float = 0.38f,
    accentTint: Color = Color.Unspecified,
): Modifier {
    if (!LocalLiquidGlass.current || !isLiquidGlassBackdropSupported()) {
        return this.liquidGlassSurface(
            shape = shape,
            accentTint = accentTint,
            interactive = interactive,
        )
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
        pressedScale = 0.982f,
        blurScale = blurScale,
        minScrim = minScrim,
        maxScrim = maxScrim,
        accentTint = accentTint,
    )
}

@Composable
fun LiquidGlassContainer(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight(width = 0.95.dp),
    accentTint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Box(
            modifier = modifier.liquidGlassSurface(
                shape = shape,
                accentTint = accentTint,
                interactive = interactive,
                onClick = onClick,
            ),
            contentAlignment = contentAlignment,
            content = content,
        )
        return
    }
    val baseModifier = modifier.liquidGlass(
        backdrop = backdrop,
        shape = shape,
        interactive = interactive,
        highlight = highlight,
        accentTint = accentTint,
    )
    val clickModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        baseModifier
    }
    Box(
        modifier = clickModifier,
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun LiquidGlassActionPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable RowScope.() -> Unit,
) {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) {
        Row(
            modifier = modifier.liquidGlassSurface(
                shape = shape,
                interactive = true,
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    Row(
        modifier = modifier.liquidGlass(backdrop, shape, true, Highlight(width = 0.95.dp)),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun LiquidGlassIconButton(
    backdrop: Backdrop?,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    interactive: Boolean = true,
    highlight: Highlight = Highlight(width = 0.95.dp),
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        interactive = interactive,
        highlight = highlight,
        onClick = onClick,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

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
        modifier = modifier.semantics {
            if (!contentDescription.isNullOrBlank()) {
                this.contentDescription = contentDescription
            }
        },
        shape = shape,
        interactive = true,
        highlight = Highlight(width = 0.95.dp),
        onClick = onClick,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

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

private data class PresetOpticalProfile(
    val interactive: Boolean,
    val blurScale: Float,
    val lensIntensity: Float,
    val minScrim: Float,
    val maxScrim: Float,
    val highlightWidth: Dp,
)

private val miniPlayerOpticalProfile = PresetOpticalProfile(
    interactive = false,
    blurScale = 1f,
    lensIntensity = 1f,
    minScrim = 0.14f,
    maxScrim = 0.38f,
    highlightWidth = 0.95.dp,
)

private fun LiquidGlassPreset.opticalProfile(): PresetOpticalProfile =
    miniPlayerOpticalProfile.copy(
        // Preserve press feedback only on controls that respond to taps.
        interactive = when (this) {
            LiquidGlassPreset.MiniPlayer,
            LiquidGlassPreset.PlayerControls,
            LiquidGlassPreset.FloatingControls -> true
            LiquidGlassPreset.BottomNavigation,
            LiquidGlassPreset.ModalSheet,
            LiquidGlassPreset.ContextMenu,
            LiquidGlassPreset.Overlay,
            LiquidGlassPreset.Card,
            LiquidGlassPreset.Header -> false
        },
    )

@Composable
fun Modifier.liquidGlassChrome(
    shape: Shape,
    enabled: Boolean = true,
    preset: LiquidGlassPreset = LiquidGlassPreset.Card,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    interactionSource: MutableInteractionSource? = null,
    exportedBackdrop: LayerBackdrop? = null,
    ambientMotion: Boolean = false,
    contentBrightness: Float = LocalLiquidGlassContentBrightness.current,
    onPointerPosition: ((Offset?) -> Unit)? = null,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp? = null,
): Modifier {
    if (!enabled) {
        return this
    }
    val profile = preset.opticalProfile()
    val resolvedBorderWidth = borderWidth ?: profile.highlightWidth
    if (LocalLiquidGlass.current && backdrop != null && isLiquidGlassBackdropSupported()) {
        val isDark = LocalIsDarkTheme.current
        val interaction = if (profile.interactive) rememberGlassInteraction() else null
        return this.drawInteractiveGlass(
            isDark = isDark,
            backdrop = backdrop,
            layer = null,
            luminanceAnimation = if (contentBrightness > 0f) contentBrightness else 0.45f,
            shape = shape,
            interaction = interaction,
            highlight = Highlight(width = resolvedBorderWidth),
            blurScale = profile.blurScale,
            lensIntensity = profile.lensIntensity,
            minScrim = profile.minScrim,
            maxScrim = profile.maxScrim,
            accentTint = accentTint,
        )
    }
    return this.liquidGlassSurface(
        shape = shape,
        borderWidth = resolvedBorderWidth,
        accentTint = accentTint,
        interactive = profile.interactive,
    )
}

@Composable
fun BackdropBlur(
    radius: Dp,
    modifier: Modifier = Modifier,
    veil: Color = Color.Unspecified,
    veilAlpha: Float = 0.68f,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val resolvedVeil = if (veil == Color.Unspecified) MaterialTheme.colorScheme.surface else veil
    Box(modifier) {
        Box(Modifier.matchParentSize(), content = content)
        Box(
            Modifier
                .matchParentSize()
                .background(resolvedVeil.copy(alpha = veilAlpha))
                .drawWithCache {
                    val sheen = LiquidGlassTheme.specularSheenBrush(isDark = isDark)
                    onDrawBehind {
                        drawRect(brush = sheen, blendMode = BlendMode.Plus)
                    }
                },
        )
    }
}

/**
 * Single awaitFirstDown on Initial pass so first tap never freezes.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
        // Observe Main pass so scroll containers that consume drag events release our press state
        val event = awaitPointerEvent(pass = PointerEventPass.Main)
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
