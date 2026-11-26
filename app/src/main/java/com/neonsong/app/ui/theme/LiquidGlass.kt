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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.ui.unit.Density
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
import com.neonsong.app.ui.common.ArtworkPaletteCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sign

val LocalLiquidGlass = compositionLocalOf { true }
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

@Immutable
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(
        topStart: Dp,
        topEnd: Dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp,
    ) : this(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart),
    )

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
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect.Zero)

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val tl = (if (isLtr) topStart else topEnd).coerceAtLeast(0f)
        val tr = (if (isLtr) topEnd else topStart).coerceAtLeast(0f)
        val br = (if (isLtr) bottomEnd else bottomStart).coerceAtLeast(0f)
        val bl = (if (isLtr) bottomStart else bottomEnd).coerceAtLeast(0f)

        if (tl <= 0f && tr <= 0f && br <= 0f && bl <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, w, h))
        }

        return Outline.Generic(createSquirclePath(w, h, tl, tr, br, bl))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return result
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
    if (w <= 0f || h <= 0f) return Path()
    var tl = tlRadius.coerceAtLeast(0f)
    var tr = trRadius.coerceAtLeast(0f)
    var br = brRadius.coerceAtLeast(0f)
    var bl = blRadius.coerceAtLeast(0f)

    // Clamp proportionally so opposite corners can never cross or invert regardless of aspect ratio
    val topSum = tl + tr
    val bottomSum = bl + br
    val leftSum = tl + bl
    val rightSum = tr + br
    val scale = minOf(
        if (topSum > w && topSum > 0f) w / topSum else 1f,
        if (bottomSum > w && bottomSum > 0f) w / bottomSum else 1f,
        if (leftSum > h && leftSum > 0f) h / leftSum else 1f,
        if (rightSum > h && rightSum > 0f) h / rightSum else 1f,
    )
    if (scale < 1f) {
        tl *= scale; tr *= scale; br *= scale; bl *= scale
    }

    val k = 0.20f
    return Path().apply {
        moveTo(tl, 0f)
        lineTo(w - tr, 0f)
        if (tr > 0f) cubicTo(w - tr * k, 0f, w, tr * k, w, tr)
        lineTo(w, h - br)
        if (br > 0f) cubicTo(w, h - br * k, w - br * k, h, w - br, h)
        lineTo(bl, h)
        if (bl > 0f) cubicTo(bl * k, h, 0f, h - bl * k, 0f, h - bl)
        lineTo(0f, tl)
        if (tl > 0f) cubicTo(0f, tl * k, tl * k, 0f, tl, 0f)
        close()
    }
}

// ── Liquid Glass Design System Core ──────────────────────────────────────────

private val ScratchOutlinePath = object : ThreadLocal<Path>() {
    override fun initialValue(): Path = Path()
}

private val DefaultHighlight = Highlight(width = 0.95.dp)

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
    alpha: Float = 1f,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            alpha = alpha,
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
                    alpha = alpha,
                    style = style,
                    blendMode = blendMode,
                )
            } else {
                val path = ScratchOutlinePath.get()!!.apply {
                    reset()
                    addRoundRect(rrect)
                }
                drawPath(path = path, brush = brush, alpha = alpha, style = style, blendMode = blendMode)
            }
        }
        is Outline.Generic -> drawPath(
            path = outline.path,
            brush = brush,
            alpha = alpha,
            style = style,
            blendMode = blendMode,
        )
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
        isSubtleSongTint: Boolean = false,
    ): Color {
        val hasAccent = accent.isSpecified && accent != Color.Transparent
        val a = if (hasAccent) accent else Color.White
        val tintWeight = when {
            !hasAccent -> 0.03f
            isSubtleSongTint -> if (isDark) 0.10f else 0.06f
            else -> 0.15f
        }
        val baseAlpha = if (isAmoled) 0.44f else if (isDark) 0.38f else 0.76f
        val surfaceAlpha = (baseAlpha * alphaScale).coerceIn(0f, 0.94f)
        return when {
            isAmoled -> Color(
                red = lerp(0.05f, a.red, tintWeight * 0.75f),
                green = lerp(0.06f, a.green, tintWeight * 0.75f),
                blue = lerp(0.08f, a.blue, tintWeight * 0.75f),
                alpha = surfaceAlpha,
            )
            isDark -> Color(
                red = lerp(0.10f, a.red, tintWeight),
                green = lerp(0.11f, a.green, tintWeight),
                blue = lerp(0.14f, a.blue, tintWeight),
                alpha = surfaceAlpha,
            )
            else -> Color(
                red = lerp(0.96f, a.red, tintWeight * 0.65f),
                green = lerp(0.97f, a.green, tintWeight * 0.65f),
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
        val boost = pressBoost.coerceIn(0f, 1f) * 0.24f
        val topLeftAlpha = (if (isDark) 0.52f else 0.82f) + boost
        val upperMidAlpha = (if (isDark) 0.22f else 0.38f) + boost * 0.65f
        val lowerMidAlpha = (if (isDark) 0.06f else 0.14f) + boost * 0.35f
        val bottomBounceAlpha = (if (isDark) 0.24f else 0.44f) + boost * 0.85f

        return Brush.linearGradient(
            0.0f to Color.White.copy(alpha = topLeftAlpha.coerceAtMost(0.98f)),
            0.28f to a.copy(alpha = upperMidAlpha.coerceAtMost(0.85f)),
            0.68f to Color.White.copy(alpha = lowerMidAlpha.coerceAtMost(0.65f)),
            1.0f to a.copy(alpha = bottomBounceAlpha.coerceAtMost(0.92f)),
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
        accent: Color = Color.Unspecified,
    ): Brush {
        val topCatch = if (accent.isSpecified && accent != Color.Transparent) {
            androidx.compose.ui.graphics.lerp(Color.White, accent, 0.18f)
        } else Color.White
        return Brush.linearGradient(
            0.0f to topCatch.copy(alpha = if (isDark) 0.16f else 0.32f),
            0.32f to Color.Transparent,
            0.78f to Color.Black.copy(alpha = if (isDark) 0.10f else 0.03f),
            1.0f to Color.Black.copy(alpha = if (isDark) 0.28f else 0.08f),
            start = Offset.Zero,
            end = Offset(0f, size.height.coerceAtLeast(1f)),
        )
    }

    /**
     * Pre-centered radial caustic brush translated at draw time (zero per-frame allocation).
     */
    internal fun causticBrush(accent: Color, radius: Float): Brush {
        val a = if (accent.isSpecified && accent != Color.Transparent) accent else Color.White
        return Brush.radialGradient(
            0.0f to Color.White.copy(alpha = 0.20f),
            0.38f to a.copy(alpha = 0.10f),
            0.75f to a.copy(alpha = 0.03f),
            1.0f to Color.Transparent,
            center = Offset.Zero,
            radius = radius.coerceAtLeast(1f),
        )
    }
}

/**
 * Tactile Apple spring press + localized optical caustic illumination.
 */
class LiquidTouchInteraction(
    private val scope: CoroutineScope,
) {
    val pressScale = Animatable(1f, visibilityThreshold = 0.0005f)
    val glowAlpha = Animatable(0f, visibilityThreshold = 0.005f)
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    private val pressSpring = spring(
        dampingRatio = 0.80f,
        stiffness = 620f,
        visibilityThreshold = 0.0005f,
    )

    private val releaseSpring = spring(
        dampingRatio = 0.72f,
        stiffness = 460f,
        visibilityThreshold = 0.0005f,
    )

    fun onTouchDown(position: Offset) {
        touchPosition = position
        scope.launch { pressScale.animateTo(0.962f, pressSpring) }
        scope.launch { glowAlpha.animateTo(1f, tween(90, easing = FastOutSlowInEasing)) }
    }

    fun onTouchMove(position: Offset) {
        touchPosition = position
    }

    fun onTouchRelease() {
        scope.launch { pressScale.animateTo(1f, releaseSpring) }
        scope.launch { glowAlpha.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
    }

    fun onTouchCancel() {
        scope.launch { pressScale.animateTo(1f, releaseSpring) }
        scope.launch { glowAlpha.animateTo(0f, tween(150)) }
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
            val resolvedAccent = when {
                accent.isSpecified && accent != Color.Transparent -> accent
                else -> ArtworkPaletteCache.activeSongPalette.value?.primary ?: Color.White
            }
            val radius = (size.maxDimension * 0.85f).coerceAtLeast(1f)
            val causticBrush = LiquidGlassTheme.causticBrush(resolvedAccent, radius)
            onDrawWithContent {
                drawContent()
                val alpha = interaction.glowAlpha.value
                if (alpha > 0.005f) {
                    val pos = interaction.touchPosition
                    translate(left = pos.x, top = pos.y) {
                        drawCircle(
                            brush = causticBrush,
                            radius = radius,
                            center = Offset.Zero,
                            alpha = alpha,
                            blendMode = BlendMode.Plus,
                        )
                    }
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
    val isAmoled = LocalIsAmoled.current
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { borderWidth.toPx() }
    val innerBevelPx = with(density) { 1.6.dp.toPx() }
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val hasExplicitAccent = accentTint.isSpecified && accentTint != Color.Transparent

    val surfaceModifier = this
        .clip(shape)
        .drawWithCache {
            // Read live song palette inside drawWithCache so track changes never recompose the UI tree.
            val currentAccent = when {
                hasExplicitAccent -> accentTint
                else -> ArtworkPaletteCache.activeSongPalette.value?.primary ?: fallbackPrimary
            }

            val bg = if (isGlass) {
                LiquidGlassTheme.backgroundColor(
                    isDark = isDark,
                    accent = currentAccent,
                    isAmoled = isAmoled,
                    alphaScale = alphaScale,
                    isSubtleSongTint = !hasExplicitAccent,
                )
            } else {
                if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            }
            val baseBorderBrush = LiquidGlassTheme.borderBrush(isDark = isDark, accent = currentAccent, size = size)
            val pressedBorderBrush = if (interaction != null) {
                LiquidGlassTheme.borderBrush(isDark = isDark, accent = currentAccent, size = size, pressBoost = 1f)
            } else {
                null
            }
            val sheenBrush = LiquidGlassTheme.specularSheenBrush(isDark = isDark, accent = currentAccent)
            val bevelBrush = LiquidGlassTheme.innerBevelBrush(isDark = isDark, size = size, accent = currentAccent)
            val outline = shape.createOutline(size, layoutDirection, this)
            val innerBevelStroke = if (innerBevelPx > 0f) Stroke(width = innerBevelPx) else null
            val borderStroke = if (strokeWidthPx > 0f) Stroke(width = strokeWidthPx) else null

            onDrawBehind {
                drawOutline(outline, color = bg)
                if (isGlass) {
                    drawOutline(
                        outline = outline,
                        brush = sheenBrush,
                        style = Fill,
                        blendMode = BlendMode.Plus,
                    )
                    if (innerBevelStroke != null) {
                        drawOutline(
                            outline = outline,
                            brush = bevelBrush,
                            style = innerBevelStroke,
                        )
                    }
                }
                if (borderStroke != null) {
                    drawOutline(
                        outline = outline,
                        brush = baseBorderBrush,
                        style = borderStroke,
                    )
                    val glow = interaction?.glowAlpha?.value ?: 0f
                    if (glow > 0.01f && pressedBorderBrush != null) {
                        drawOutline(
                            outline = outline,
                            brush = pressedBorderBrush,
                            style = borderStroke,
                            blendMode = BlendMode.Plus,
                            alpha = glow * 0.55f,
                        )
                    }
                }
            }
        }

    val interactiveModifier = if (interaction != null) {
        surfaceModifier.liquidTouchEffect(
            interaction = interaction,
            accent = if (hasExplicitAccent) accentTint else Color.Unspecified,
        )
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
    val resolvedAccent = if (accentTint.isSpecified) accentTint else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                borderWidth = borderWidth,
                accentTint = resolvedAccent,
                interactive = false,
                onClick = null,
            )
            .bounceClick(
                enabled = enabled,
                scaleDown = 0.90f,
                alphaDown = 0.70f,
                role = Role.Button,
                onClick = onClick,
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
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.bounceScale(
            interactionSource = actualSource,
            enabled = enabled,
            scaleDown = 0.94f,
            alphaDown = 0.75f,
        ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = actualSource,
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

@Volatile
private var cachedLowRamDevice: Boolean? = null

private fun isLowRamDeviceCached(context: Context): Boolean {
    cachedLowRamDevice?.let { return it }
    val lowRam = runCatching {
        (context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true
    }.getOrDefault(false)
    cachedLowRamDevice = lowRam
    return lowRam
}

@Composable
fun isDeviceGlassCapable(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val view = LocalView.current
    if (view.isInEditMode || !view.isHardwareAccelerated) return false
    return !isLowRamDeviceCached(LocalContext.current)
}

@Composable
fun isLiquidGlassBackdropSupported(): Boolean =
    LocalLiquidGlass.current && isDeviceGlassCapable()

@Composable
fun Modifier.liquidGlassSource(
    backdrop: LayerBackdrop?,
): Modifier {
    if (backdrop == null || !isLiquidGlassBackdropSupported()) return this
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
        dampingRatio = 0.78f,
        stiffness = 600f,
        visibilityThreshold = 0.001f,
    )
    private val releaseSpec = spring(
        dampingRatio = 0.70f,
        stiffness = 460f,
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

    suspend fun bindInteractionSource(
        source: MutableInteractionSource,
    ) {
        source.interactions.collect { event ->
            when (event) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                    touchPosition = event.pressPosition
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                }
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> {
                    animationScope.launch { pressAnimation.animateTo(0f, releaseSpec) }
                }
            }
        }
    }
}

@Composable
fun rememberGlassInteraction(
    interactionSource: MutableInteractionSource? = null,
): GlassInteraction {
    val scope = rememberCoroutineScope()
    val interaction = remember(scope) { GlassInteraction(scope) }
    if (interactionSource != null) {
        androidx.compose.runtime.LaunchedEffect(interaction, interactionSource) {
            interaction.bindInteractionSource(interactionSource)
        }
    }
    return interaction
}

class GlassSurfaceDrawCache {
    var size: Size = Size.Unspecified
    var layoutDirection: LayoutDirection? = null
    var isDark: Boolean = true
    var tint: Color = Color.Unspecified
    var shape: Shape? = null
    var outline: Outline? = null
    var sheenBrush: Brush? = null
    var borderBrush: Brush? = null
    var pressedBorderBrush: Brush? = null
    var bevelBrush: Brush? = null
    var causticBrush: Brush? = null
    var causticRadius: Float = 1f
    var bevelStroke: Stroke? = null
    var rimStroke: Stroke? = null

    fun ensure(size: Size, ld: LayoutDirection, isDark: Boolean, tint: Color, shape: Shape, density: Density) {
        if (this.size == size && this.layoutDirection == ld && this.isDark == isDark && this.tint == tint && this.shape == shape) return
        this.size = size
        this.layoutDirection = ld
        this.isDark = isDark
        this.tint = tint
        this.shape = shape
        this.outline = shape.createOutline(size, ld, density)
        this.sheenBrush = LiquidGlassTheme.specularSheenBrush(isDark = isDark, accent = tint)
        this.borderBrush = LiquidGlassTheme.borderBrush(isDark = isDark, accent = tint, size = size)
        this.pressedBorderBrush = LiquidGlassTheme.borderBrush(isDark = isDark, accent = tint, size = size, pressBoost = 1f)
        this.bevelBrush = LiquidGlassTheme.innerBevelBrush(isDark = isDark, size = size, accent = tint)
        val radius = (size.maxDimension * 0.95f).coerceAtLeast(1f)
        this.causticRadius = radius
        this.causticBrush = LiquidGlassTheme.causticBrush(accent = tint, radius = radius)
        with(density) {
            bevelStroke = Stroke(width = 1.6.dp.toPx())
            rimStroke = Stroke(width = 1.05.dp.toPx())
        }
    }
}

/**
 * Real-time optical meniscus backdrop shader:
 * Keeps GPU blur & lens kernels stable across press animations while driving
 * zero-allocation caustic blooms, Fresnel rim boosts, and scale in the draw phase.
 */
@Composable
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: Backdrop,
    layer: GraphicsLayer? = null,
    luminanceAnimation: Float,
    shape: Shape,
    interaction: GlassInteraction?,
    pressedScale: Float = 0.982f,
    highlight: Highlight = DefaultHighlight,
    blurScale: Float = 1f,
    lensIntensity: Float = 1f,
    minScrim: Float = 0.14f,
    maxScrim: Float = 0.38f,
    accentTint: Color = Color.Unspecified,
    attachPressGesture: Boolean = true,
    drawCache: GlassSurfaceDrawCache = remember { GlassSurfaceDrawCache() },
): Modifier =
    this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { highlight },
            effects = {
                val l = (luminanceAnimation * 2f - 1f).let { sign(it) * it * it }
                vibrancy()
                colorControls(
                    brightness = if (isDark) 0.045f else 0.06f,
                    contrast = 1.05f,
                    saturation = 1.58f,
                )
                val baseBlurPx = if (l > 0f) {
                    lerp(14f.dp.toPx(), 24f.dp.toPx(), l)
                } else {
                    lerp(14f.dp.toPx(), 8f.dp.toPx(), -l)
                }
                blur(baseBlurPx * blurScale)

                if (lensIntensity > 0.05f) {
                    val minDim = size.minDimension.coerceAtLeast(1f)
                    val edgeWidth = (minDim * 0.24f * lensIntensity).coerceAtMost(minDim * 0.48f)
                    val refractionDepth = (minDim * 0.46f * lensIntensity).coerceAtMost(minDim * 0.65f)
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

                val songTint = ArtworkPaletteCache.activeSongPalette.value?.primary ?: Color.Unspecified
                val hasExplicitAccent = accentTint.isSpecified && accentTint != Color.Transparent
                val effectiveTint = when {
                    hasExplicitAccent -> accentTint
                    songTint.isSpecified -> songTint
                    else -> Color.Unspecified
                }
                val tintStrength = when {
                    hasExplicitAccent -> if (isDark) 0.22f else 0.14f
                    effectiveTint.isSpecified -> if (isDark) 0.12f else 0.07f
                    else -> 0f
                }

                val surfaceTint = if (effectiveTint.isSpecified && tintStrength > 0f) {
                    Color(
                        red = lerp(if (isDark) 0.08f else 0.95f, effectiveTint.red, tintStrength),
                        green = lerp(if (isDark) 0.09f else 0.96f, effectiveTint.green, tintStrength),
                        blue = lerp(if (isDark) 0.12f else 0.99f, effectiveTint.blue, tintStrength),
                        alpha = baseAlpha,
                    )
                } else {
                    (if (isDark) Color(0xFF0E1015) else Color(0xFFF8FAFF)).copy(alpha = baseAlpha)
                }

                drawCache.ensure(size, layoutDirection, isDark, effectiveTint, shape, this)
                val outline = drawCache.outline!!

                // 1. Song-tinted optical glass body veil
                drawRect(surfaceTint)

                // 2. Cached top meniscus sheen
                drawCache.sheenBrush?.let { drawRect(brush = it, blendMode = BlendMode.Plus) }

                // 3. Cached inner depth bevel
                val bevelBrush = drawCache.bevelBrush
                val bevelStroke = drawCache.bevelStroke
                if (bevelBrush != null && bevelStroke != null) {
                    drawOutline(outline = outline, brush = bevelBrush, style = bevelStroke)
                }

                // 4. Cached Fresnel rim + zero-allocation additive press highlight
                val rimStroke = drawCache.rimStroke
                if (rimStroke != null) {
                    drawCache.borderBrush?.let {
                        drawOutline(outline = outline, brush = it, style = rimStroke)
                    }
                    if (press > 0.01f) {
                        drawCache.pressedBorderBrush?.let {
                            drawOutline(
                                outline = outline,
                                brush = it,
                                style = rimStroke,
                                blendMode = BlendMode.Plus,
                                alpha = press * 0.55f,
                            )
                        }
                    }
                }

                // 5. Zero-allocation localized caustic bloom under pointer
                if (press > 0.005f) {
                    val caustic = drawCache.causticBrush
                    if (caustic != null) {
                        val center = interaction?.touchPosition ?: Offset(size.width * 0.5f, size.height * 0.5f)
                        translate(left = center.x, top = center.y) {
                            drawCircle(
                                brush = caustic,
                                radius = drawCache.causticRadius,
                                center = Offset.Zero,
                                alpha = press,
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
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
            if (interaction != null && attachPressGesture) {
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
    highlight: Highlight = DefaultHighlight,
    accentTint: Color = Color.Unspecified,
    lensIntensity: Float = 0.75f,
    minScrim: Float = 0.16f,
    maxScrim: Float = 0.38f,
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
        lensIntensity = lensIntensity,
        minScrim = minScrim,
        maxScrim = maxScrim,
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
    val interaction = if (interactive) rememberGlassInteraction() else null
    return this.drawInteractiveGlass(
        isDark = isDark,
        backdrop = backdrop,
        layer = layer,
        luminanceAnimation = luminanceAnimation,
        shape = shape,
        interaction = interaction,
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
    highlight: Highlight = DefaultHighlight,
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
        baseModifier.bounceClick(
            enabled = interactive,
            scaleDown = 0.94f,
            alphaDown = 0.70f,
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
        modifier = modifier.liquidGlass(backdrop, shape, true, DefaultHighlight),
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
    highlight: Highlight = DefaultHighlight,
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
        highlight = DefaultHighlight,
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

private val BottomNavProfile = PresetOpticalProfile(
    interactive = false, blurScale = 1.15f, lensIntensity = 0.95f,
    minScrim = 0.18f, maxScrim = 0.42f, highlightWidth = 1.0.dp,
)
private val MiniPlayerProfile = PresetOpticalProfile(
    interactive = true, blurScale = 1.10f, lensIntensity = 0.85f,
    minScrim = 0.20f, maxScrim = 0.44f, highlightWidth = 1.0.dp,
)
private val ControlsProfile = PresetOpticalProfile(
    interactive = true, blurScale = 0.95f, lensIntensity = 0.75f,
    minScrim = 0.14f, maxScrim = 0.34f, highlightWidth = 0.95.dp,
)
private val HeaderProfile = PresetOpticalProfile(
    interactive = false, blurScale = 1.25f, lensIntensity = 0.35f,
    minScrim = 0.24f, maxScrim = 0.50f, highlightWidth = 0.9.dp,
)
private val OverlayProfile = PresetOpticalProfile(
    interactive = false, blurScale = 1.40f, lensIntensity = 0.25f,
    minScrim = 0.34f, maxScrim = 0.58f, highlightWidth = 1.0.dp,
)
private val CardProfile = PresetOpticalProfile(
    interactive = false, blurScale = 0.90f, lensIntensity = 0.20f,
    minScrim = 0.22f, maxScrim = 0.42f, highlightWidth = 0.85.dp,
)

private fun LiquidGlassPreset.opticalProfile(): PresetOpticalProfile = when (this) {
    LiquidGlassPreset.BottomNavigation -> BottomNavProfile
    LiquidGlassPreset.MiniPlayer -> MiniPlayerProfile
    LiquidGlassPreset.PlayerControls,
    LiquidGlassPreset.FloatingControls -> ControlsProfile
    LiquidGlassPreset.Header -> HeaderProfile
    LiquidGlassPreset.ModalSheet,
    LiquidGlassPreset.ContextMenu,
    LiquidGlassPreset.Overlay -> OverlayProfile
    LiquidGlassPreset.Card -> CardProfile
}

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
    if (!enabled) return this

    val profile = preset.opticalProfile()
    val resolvedBorderWidth = borderWidth ?: profile.highlightWidth
    val useLiveBackdrop = preset != LiquidGlassPreset.Card &&
        LocalLiquidGlass.current &&
        backdrop != null &&
        isLiquidGlassBackdropSupported()

    if (useLiveBackdrop && backdrop != null) {
        val isDark = LocalIsDarkTheme.current
        val interaction = if (profile.interactive) rememberGlassInteraction(interactionSource) else null
        val highlight = remember(resolvedBorderWidth) { Highlight(width = resolvedBorderWidth) }
        return this.drawInteractiveGlass(
            isDark = isDark,
            backdrop = backdrop,
            layer = null,
            luminanceAnimation = if (contentBrightness > 0f) contentBrightness else 0.45f,
            shape = shape,
            interaction = interaction,
            pressedScale = if (interactionSource != null) 1f else 0.982f,
            highlight = highlight,
            blurScale = profile.blurScale,
            lensIntensity = profile.lensIntensity,
            minScrim = profile.minScrim,
            maxScrim = profile.maxScrim,
            accentTint = accentTint,
            attachPressGesture = interactionSource == null,
        )
    }
    return this.liquidGlassSurface(
        shape = shape,
        borderWidth = resolvedBorderWidth,
        accentTint = accentTint,
        interactive = profile.interactive && interactionSource == null,
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
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Main)
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
