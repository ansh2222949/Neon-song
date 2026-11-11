package com.neonsong.app.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator
import com.neonsong.app.ui.theme.liquidGlassSurface

/**
 * Full-screen or section loading indicator with breathing liquid-glass housing.
 * Reads animation values strictly inside [graphicsLayer] to avoid per-frame recomposition.
 */
@Composable
fun ExpressiveLoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier,
    containerSize: Dp = 58.dp,
) {
    val transition = rememberInfiniteTransition(label = "expressiveLoader")
    val breatheState = transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loaderBreathe",
    )
    val turnState = transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loaderTurn",
    )

    val spinnerSize = (containerSize * 0.48f).coerceAtLeast(18.dp)
    val cornerRadius = (containerSize * 0.34f).coerceAtLeast(12.dp)

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(containerSize)
                    .graphicsLayer {
                        val b = breatheState.value
                        scaleX = b
                        scaleY = b
                        rotationZ = turnState.value
                    }
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(cornerRadius),
                        borderWidth = 1.2.dp,
                        accentTint = MaterialTheme.colorScheme.primary,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                GlassCircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                    strokeWidth = 3.dp,
                )
            }
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact shared loader for pagination, buttons and row-level work. */
@Composable
fun ExpressiveInlineLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 22.dp,
    strokeWidth: Dp = 2.5.dp,
) {
    GlassCircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}
