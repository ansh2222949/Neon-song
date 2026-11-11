package com.neonsong.app.features.settings.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.data.local.EQ_MAX_GAIN_DB
import com.neonsong.app.ui.theme.liquidGlassSurface

fun eqBandCategory(hz: Int): String = when {
    hz <= 60 -> "Sub-Bass"
    hz <= 250 -> "Bass"
    hz <= 500 -> "Low-Mid"
    hz <= 2000 -> "Mid"
    hz <= 6000 -> "Presence"
    else -> "Brilliance"
}

@Composable
fun EqualizerCurveGraph(
    gains: FloatArray,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
    val onSurface = MaterialTheme.colorScheme.onSurface

    val baselineDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
    val gridDash = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f) }
    val curvePath = remember { Path() }
    val fillCurvePath = remember { Path() }

    val strokeGradient = remember(primary, secondary, tertiary) {
        Brush.horizontalGradient(listOf(primary, secondary, tertiary))
    }
    val fillGradientColors = remember(primary, secondary) {
        listOf(
            primary.copy(alpha = 0.42f),
            secondary.copy(alpha = 0.14f),
            Color.Transparent,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(22.dp),
                borderWidth = 1.dp,
                accentTint = if (enabled) primary else Color.Unspecified,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val paddingX = 14.dp.toPx()
            val availableW = w - paddingX * 2f
            val baselineY = h / 2f
            val maxDbPx = (h - 22.dp.toPx()) / 2f

            val topY = baselineY - maxDbPx
            val bottomY = baselineY + maxDbPx

            drawLine(
                color = if (enabled) outlineVariant else outlineVariant.copy(alpha = 0.12f),
                start = Offset(paddingX, baselineY),
                end = Offset(w - paddingX, baselineY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = baselineDash,
            )
            drawLine(
                color = gridColor,
                start = Offset(paddingX, topY),
                end = Offset(w - paddingX, topY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridDash,
            )
            drawLine(
                color = gridColor,
                start = Offset(paddingX, bottomY),
                end = Offset(w - paddingX, bottomY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = gridDash,
            )

            if (gains.isEmpty()) return@Canvas

            val count = gains.size
            val stepX = availableW / (count - 1).coerceAtLeast(1)

            curvePath.reset()
            fillCurvePath.reset()

            fun pointX(i: Int) = paddingX + i * stepX
            fun pointY(i: Int): Float {
                val gain = if (enabled) gains[i].coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
                return baselineY - (gain / EQ_MAX_GAIN_DB) * maxDbPx
            }

            val p0x = pointX(0)
            val p0y = pointY(0)
            curvePath.moveTo(p0x, p0y)
            fillCurvePath.moveTo(p0x, baselineY)
            fillCurvePath.lineTo(p0x, p0y)

            var prevX = p0x
            var prevY = p0y
            for (i in 1 until count) {
                val currX = pointX(i)
                val currY = pointY(i)
                val midX = prevX + (currX - prevX) / 2f
                curvePath.cubicTo(midX, prevY, midX, currY, currX, currY)
                fillCurvePath.cubicTo(midX, prevY, midX, currY, currX, currY)
                prevX = currX
                prevY = currY
            }

            fillCurvePath.lineTo(prevX, baselineY)
            fillCurvePath.close()

            if (enabled) {
                drawPath(
                    path = fillCurvePath,
                    brush = Brush.verticalGradient(
                        colors = fillGradientColors,
                        startY = topY,
                        endY = bottomY,
                    ),
                )
                // Soft outer neon bloom along the EQ curve
                drawPath(
                    path = curvePath,
                    brush = strokeGradient,
                    alpha = 0.28f,
                    style = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawPath(
                    path = curvePath,
                    brush = strokeGradient,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            } else {
                drawPath(
                    path = curvePath,
                    color = onSurface.copy(alpha = 0.30f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            for (i in 0 until count) {
                val px = pointX(i)
                val py = pointY(i)
                val centerOffset = Offset(px, py)
                val hasBoostOrCut = kotlin.math.abs(py - baselineY) > 2f && enabled
                if (hasBoostOrCut) {
                    drawCircle(
                        color = primary.copy(alpha = 0.35f),
                        radius = 7.5.dp.toPx(),
                        center = centerOffset,
                    )
                }
                drawCircle(
                    color = if (hasBoostOrCut) primary else if (enabled) primary.copy(alpha = 0.7f) else onSurface.copy(alpha = 0.3f),
                    radius = if (hasBoostOrCut) 4.5.dp.toPx() else 3.dp.toPx(),
                    center = centerOffset,
                )
                if (hasBoostOrCut) {
                    drawCircle(
                        color = onPrimary,
                        radius = 2.dp.toPx(),
                        center = centerOffset,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text("+8dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = primary)
            Text("0dB", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-8dB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
