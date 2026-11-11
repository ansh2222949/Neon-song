package com.neonsong.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight

private enum class GlassControlVariant {
    FILLED_ACCENT,
    TONAL,
    OUTLINED,
    CHROMELESS,
}

/** Material3-compatible primary button surface backed by Apple-style optical Liquid Glass. */
@Composable
fun GlassActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    contentPadding = contentPadding,
    variant = GlassControlVariant.FILLED_ACCENT,
    content = content,
)

@Composable
fun GlassTonalActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    contentPadding = contentPadding,
    variant = GlassControlVariant.TONAL,
    content = content,
)

@Composable
fun GlassOutlinedActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    contentPadding = contentPadding,
    variant = GlassControlVariant.OUTLINED,
    content = content,
)

/** Clean chromeless text button with tactile spring press & optical touch bloom. */
@Composable
fun GlassTextActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    contentPadding = contentPadding,
    variant = GlassControlVariant.CHROMELESS,
    content = content,
)

@Composable
private fun GlassButtonCore(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: ButtonColors,
    contentPadding: PaddingValues,
    variant: GlassControlVariant,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val useBackdropGlass = variant != GlassControlVariant.CHROMELESS &&
        backdrop != null &&
        isLiquidGlassBackdropSupported()
    val touch = if (!useBackdropGlass) rememberLiquidTouchInteraction() else null
    val requestedAccent = colors.containerColor
    val primary = MaterialTheme.colorScheme.primary
    val accent = when (variant) {
        GlassControlVariant.FILLED_ACCENT -> requestedAccent.takeIf { it.alpha > 0.08f } ?: primary
        GlassControlVariant.TONAL -> requestedAccent.takeIf { it.alpha > 0.08f }
            ?: MaterialTheme.colorScheme.secondaryContainer
        GlassControlVariant.OUTLINED -> Color.Unspecified
        GlassControlVariant.CHROMELESS -> Color.Unspecified
    }
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    val borderWidth: Dp = when (variant) {
        GlassControlVariant.FILLED_ACCENT -> 1.05.dp
        GlassControlVariant.TONAL -> 0.85.dp
        GlassControlVariant.OUTLINED -> 1.15.dp
        GlassControlVariant.CHROMELESS -> 0.dp
    }
    val alphaScale: Float = when (variant) {
        GlassControlVariant.FILLED_ACCENT -> 1.12f
        GlassControlVariant.TONAL -> 0.96f
        GlassControlVariant.OUTLINED -> 0.72f
        GlassControlVariant.CHROMELESS -> 0f
    }

    val baseMod = when {
        variant == GlassControlVariant.CHROMELESS -> {
            modifier
                .clip(shape)
                .liquidTouchEffect(interaction = touch, accent = contentColor, enabled = enabled)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
        backdrop != null && isLiquidGlassBackdropSupported() -> {
            modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = 42.dp)
                .liquidGlass(
                    backdrop = backdrop,
                    shape = shape,
                    interactive = enabled,
                    highlight = Highlight(width = borderWidth),
                    accentTint = accent,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
        else -> {
            modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = 42.dp)
                .liquidGlassSurface(
                    shape = shape,
                    borderWidth = borderWidth,
                    accentTint = accent,
                    alphaScale = alphaScale,
                    interactive = enabled,
                    onClick = if (enabled) onClick else null,
                    interaction = touch,
                )
        }
    }

    Box(
        modifier = baseMod
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/** Clean chromeless icon button with tactile spring & touch glow. */
@Composable
fun GlassIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    variant = GlassControlVariant.CHROMELESS,
    content = content,
)

@Composable
fun GlassFilledIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(46.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    variant = GlassControlVariant.FILLED_ACCENT,
    content = content,
)

@Composable
fun GlassTonalIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(46.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    variant = GlassControlVariant.TONAL,
    content = content,
)

@Composable
fun GlassOutlinedIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(46.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    variant = GlassControlVariant.OUTLINED,
    content = content,
)

@Composable
private fun GlassIconButtonCore(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: IconButtonColors,
    variant: GlassControlVariant,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val useBackdropGlass = variant != GlassControlVariant.CHROMELESS &&
        backdrop != null &&
        isLiquidGlassBackdropSupported()
    val touch = if (!useBackdropGlass) rememberLiquidTouchInteraction() else null
    val primary = MaterialTheme.colorScheme.primary
    val accent = when (variant) {
        GlassControlVariant.FILLED_ACCENT -> colors.containerColor.takeIf { it.alpha > 0.08f } ?: primary
        GlassControlVariant.TONAL -> colors.containerColor.takeIf { it.alpha > 0.08f }
            ?: MaterialTheme.colorScheme.secondaryContainer
        GlassControlVariant.OUTLINED,
        GlassControlVariant.CHROMELESS -> Color.Unspecified
    }
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    val borderWidth: Dp = when (variant) {
        GlassControlVariant.FILLED_ACCENT -> 1.05.dp
        GlassControlVariant.TONAL -> 0.85.dp
        GlassControlVariant.OUTLINED -> 1.15.dp
        GlassControlVariant.CHROMELESS -> 0.dp
    }
    val alphaScale: Float = when (variant) {
        GlassControlVariant.FILLED_ACCENT -> 1.12f
        GlassControlVariant.TONAL -> 0.95f
        GlassControlVariant.OUTLINED -> 0.70f
        GlassControlVariant.CHROMELESS -> 0f
    }

    val surfaceModifier = when {
        variant == GlassControlVariant.CHROMELESS -> {
            modifier
                .clip(shape)
                .liquidTouchEffect(interaction = touch, accent = contentColor, enabled = enabled)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
        backdrop != null && isLiquidGlassBackdropSupported() -> {
            modifier
                .liquidGlass(
                    backdrop = backdrop,
                    shape = shape,
                    interactive = enabled,
                    highlight = Highlight(width = borderWidth),
                    accentTint = accent,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
        }
        else -> {
            modifier.liquidGlassSurface(
                shape = shape,
                borderWidth = borderWidth,
                accentTint = accent,
                alphaScale = alphaScale,
                interactive = enabled,
                onClick = if (enabled) onClick else null,
                interaction = touch,
            )
        }
    }

    Box(
        modifier = surfaceModifier.semantics {
            role = Role.Button
            if (!enabled) disabled()
        },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(contentAlignment = Alignment.Center, content = content)
        }
    }
}

/** Luminous Liquid Glass circular progress ring with specular highlight. */
@Composable
fun GlassCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    strokeWidth: Dp = 4.dp,
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val value = progress?.invoke()?.coerceIn(0f, 1f)
    val rotation = if (value == null) {
        val transition = rememberInfiniteTransition(label = "glassCircularProgress")
        val animatedRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "glassCircularProgressRotation",
        )
        animatedRotation
    } else {
        0f
    }
    Canvas(modifier) {
        val width = strokeWidth.toPx().coerceAtMost(size.minDimension * 0.25f)
        val inset = width / 2f
        val arcSize = Size(
            (size.width - width).coerceAtLeast(0f),
            (size.height - width).coerceAtLeast(0f),
        )
        val topLeft = Offset(inset, inset)
        if (trackColor.alpha > 0f) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = strokeCap),
            )
        }
        val sweep = if (value == null) 275f else 360f * value
        val start = if (value == null) rotation - 90f else -90f
        if (sweep > 0.5f) {
            // Soft outer optical bloom
            drawArc(
                color = color.copy(alpha = 0.26f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width * 1.45f, cap = strokeCap),
                blendMode = BlendMode.Plus,
            )
            // Core luminous arc
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = strokeCap),
            )
            // Specular inner crest
            drawArc(
                color = Color.White.copy(alpha = 0.42f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width * 0.35f, cap = strokeCap),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Rounded, glass-recessed progress track with a glowing liquid bar. */
@Composable
fun GlassLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    strokeCap: StrokeCap = StrokeCap.Round,
) {
    val value = progress?.invoke()?.coerceIn(0f, 1f)
    val segmentStart = if (value == null) {
        val transition = rememberInfiniteTransition(label = "glassLinearProgress")
        val animatedStart by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
            label = "glassLinearProgressTravel",
        )
        animatedStart
    } else {
        0f
    }
    Canvas(modifier.defaultMinSize(minHeight = 5.dp)) {
        val barHeight = size.height.coerceAtLeast(1f)
        val corner = CornerRadius(barHeight / 2f, barHeight / 2f)

        // Frosted glass recessed track + subtle rim
        drawRoundRect(color = trackColor, cornerRadius = corner)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            cornerRadius = corner,
            style = Stroke(width = 0.75.dp.toPx()),
        )

        val start = if (value == null) segmentStart else 0f
        val width = if (value == null) size.width * 0.36f else size.width * value
        val left = (if (value == null) (size.width - width) * start else 0f).coerceAtLeast(0f)
        val clampedWidth = width.coerceIn(0f, (size.width - left).coerceAtLeast(0f))

        if (clampedWidth > 0.5f) {
            val topLeft = Offset(left, 0f)
            val barSize = Size(clampedWidth, barHeight)

            // Liquid progress fill with bright leading edge
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0.0f to color.copy(alpha = 0.85f),
                    0.85f to color,
                    1.0f to Color.White.copy(alpha = 0.92f),
                    startX = left,
                    endX = left + clampedWidth,
                ),
                topLeft = topLeft,
                size = barSize,
                cornerRadius = corner,
            )

            // Top meniscus specular highlight
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.48f),
                    0.55f to Color.Transparent,
                    startY = 0f,
                    endY = barHeight,
                ),
                topLeft = topLeft,
                size = barSize,
                cornerRadius = corner,
                blendMode = BlendMode.Plus,
            )
        }
    }
}
