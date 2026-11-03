package com.neonsong.app.ui.theme

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Material3-compatible button surface backed by the app's animated glass control. */
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
) = GlassButtonCore(onClick, modifier, enabled, shape, colors, contentPadding, content)

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
) = GlassButtonCore(onClick, modifier, enabled, shape, colors, contentPadding, content)

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
) = GlassButtonCore(onClick, modifier, enabled, shape, colors, contentPadding, content)

@Composable
fun GlassTextActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = GlassButtonCore(onClick, modifier, enabled, shape, colors, contentPadding, content)

@Composable
private fun GlassButtonCore(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: ButtonColors,
    contentPadding: PaddingValues,
    content: @Composable RowScope.() -> Unit,
) {
    val touch = rememberLiquidTouchInteraction()
    val requestedAccent = colors.containerColor
    val accent = requestedAccent.takeIf { it.alpha > 0.08f } ?: MaterialTheme.colorScheme.primary
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .liquidGlassSurface(
                shape = shape,
                accentTint = accent,
                interactive = enabled,
                onClick = if (enabled) onClick else null,
                interaction = touch,
            )
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

/** Accessible 48dp glass icon control with the same touch spring as player controls. */
@Composable
fun GlassIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(onClick, modifier, enabled, shape, colors, content)

@Composable
fun GlassFilledIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(onClick, modifier, enabled, shape, colors, content)

@Composable
fun GlassTonalIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(onClick, modifier, enabled, shape, colors, content)

@Composable
fun GlassOutlinedIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassIconButtonCore(onClick, modifier, enabled, shape, colors, content)

@Composable
private fun GlassIconButtonCore(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: IconButtonColors,
    content: @Composable BoxScope.() -> Unit,
) {
    val touch = rememberLiquidTouchInteraction()
    val accent = colors.containerColor.takeIf { it.alpha > 0.08f } ?: MaterialTheme.colorScheme.primary
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = shape,
                accentTint = accent,
                interactive = enabled,
                onClick = if (enabled) onClick else null,
                interaction = touch,
            )
            .semantics {
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

/** Liquid progress ring for loading and determinate task progress. */
@Composable
fun GlassCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp,
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
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
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
        drawArc(
            color = color,
            startAngle = if (value == null) rotation - 90f else -90f,
            sweepAngle = if (value == null) 278f else 360f * value,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = width, cap = strokeCap),
        )
    }
}

/** Rounded, glass-tinted progress track with a moving segment while indeterminate. */
@Composable
fun GlassLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
    Canvas(modifier.defaultMinSize(minHeight = 4.dp)) {
        val barHeight = size.height.coerceAtLeast(1f)
        val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
        drawRoundRect(trackColor, cornerRadius = corner)
        val start = if (value == null) segmentStart else 0f
        val width = if (value == null) size.width * 0.34f else size.width * value
        val left = if (value == null) (size.width - width) * start else 0f
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left.coerceAtLeast(0f), 0f),
            size = Size(width.coerceAtLeast(0f), barHeight),
            cornerRadius = corner,
        )
    }
}
