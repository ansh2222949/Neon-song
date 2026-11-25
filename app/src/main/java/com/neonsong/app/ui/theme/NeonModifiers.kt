package com.neonsong.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

object NeonSpacing {
    val ScreenHorizontal = 20.dp
    val SectionGap = 28.dp
    val CardGap = 14.dp
    val CardInner = 16.dp
    val HeroRadius = 28.dp
    val SectionRadius = 24.dp
    val CardRadius = 18.dp
    val ThumbRadius = 10.dp

    fun concentricRadius(
        outerRadius: androidx.compose.ui.unit.Dp,
        insetPadding: androidx.compose.ui.unit.Dp,
        minRadius: androidx.compose.ui.unit.Dp = 6.dp,
    ): androidx.compose.ui.unit.Dp = (outerRadius - insetPadding).coerceAtLeast(minRadius)
}

// MainShell iski value provide karega, aur har LazyColumn bottom padding mein ise padhega
val LocalBottomChromePadding = compositionLocalOf { 120.dp }

// 1. Specular 3D Glass Edge (Flat M3 border ki jagah)
fun Modifier.neonGlassSurface(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.08f),
): Modifier = this
    .clip(shape)
    .background(tint)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f), // Top-left light catch
                Color.White.copy(alpha = 0.04f), // Bottom-right fade
            ),
        ),
        shape = shape,
    )

// 2. Spring Bounce + Haptic Click (M3 ripple ki jagah)
fun Modifier.neonTactileClick(
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "neonScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interactionSource, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}
