package com.neonsong.app.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface

private val OneUiSmoothEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val LoaderCardShape = SquircleShape(28.dp)
private val NeonWordmarkShadow = Shadow(
    color = Color.White.copy(alpha = 0.42f),
    offset = Offset.Zero,
    blurRadius = 22f,
)

@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val transition = rememberInfiniteTransition(label = "neonOneUiLoader")

    // Zero-recomposition State<Float> objects (read strictly inside draw phase)
    val sheenProgress = transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neonSheenSweep",
    )
    val barProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = OneUiSmoothEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "oneUiPillBar",
    )
    val breatheScale = transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = OneUiSmoothEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neonBreathe",
    )

    // One UI structured frosted glass pill container
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = LoaderCardShape,
                borderWidth = 0.9.dp,
                alphaScale = 1.05f,
            )
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Stylish pure-white "NEON" wordmark with soft pearlescent sweep
            Text(
                text = "NEON",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 10.sp,
                    shadow = NeonWordmarkShadow,
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 10.dp) // optical balance for trailing letterSpacing
                    .graphicsLayer {
                        val s = breatheScale.value
                        scaleX = s
                        scaleY = s
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val cx = size.width * sheenProgress.value
                        val band = size.width * 0.42f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.White.copy(alpha = 0.72f),
                                0.5f to Color.White,
                                1.0f to Color.White.copy(alpha = 0.72f),
                                startX = cx - band,
                                endX = cx + band,
                            ),
                            blendMode = BlendMode.Modulate,
                        )
                    },
            )

            Spacer(Modifier.height(16.dp))

            // Sleek One UI smooth gliding pill track (pure white)
            Spacer(
                modifier = Modifier
                    .width(116.dp)
                    .height(3.5.dp)
                    .drawWithCache {
                        val corner = CornerRadius(size.height * 0.5f, size.height * 0.5f)
                        val trackColor = Color.White.copy(alpha = 0.16f)
                        val capsuleBrush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White,
                                Color.White.copy(alpha = 0.35f),
                            ),
                        )
                        onDrawBehind {
                            drawRoundRect(color = trackColor, cornerRadius = corner)
                            val p = barProgress.value
                            val segWidth = size.width * (0.28f + 0.18f * kotlin.math.sin(p * Math.PI.toFloat()))
                            val travel = size.width + segWidth
                            val left = (p * travel - segWidth).coerceIn(0f, size.width)
                            val right = (p * travel).coerceIn(0f, size.width)
                            val w = (right - left).coerceAtLeast(0f)
                            if (w > 0.5f) {
                                drawRoundRect(
                                    brush = capsuleBrush,
                                    topLeft = Offset(left, 0f),
                                    size = Size(w, size.height),
                                    cornerRadius = corner,
                                )
                            }
                        }
                    },
            )

            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 0.3.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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
