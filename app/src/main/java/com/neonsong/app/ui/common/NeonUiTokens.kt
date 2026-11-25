package com.neonsong.app.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.ui.theme.LocalIsDarkTheme

object NeonTokens {
    val ScreenPadding = com.neonsong.app.ui.theme.NeonSpacing.ScreenHorizontal
    val SectionGap = 18.dp
    val HeroRadius = com.neonsong.app.ui.theme.NeonSpacing.HeroRadius
    val SectionRadius = com.neonsong.app.ui.theme.NeonSpacing.SectionRadius
    val CardRadius = com.neonsong.app.ui.theme.NeonSpacing.CardRadius
    val ThumbRadius = com.neonsong.app.ui.theme.NeonSpacing.ThumbRadius

    fun <T> smoothSpring() = spring<T>(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> snappyPressSpring() = spring<T>(
        dampingRatio = 0.68f,
        stiffness = 480f,
    )
}

object NeonType {
    val ScreenTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineLarge
    val SectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge
    val Eyebrow: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp)
    val TabularMeta: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
}

/** Shared symmetrical Liquid Glass container for Home, Community, and Detail screens */
@Composable
fun NeonGlassSection(
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = NeonTokens.ScreenPadding,
    verticalPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.56f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.84f)
    }
    val shape = com.neonsong.app.ui.theme.SquircleShape(NeonTokens.SectionRadius)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = 0.9.dp,
            brush = Brush.verticalGradient(
                listOf(
                    (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.22f else 0.16f),
                    (if (isDark) Color.White else primary).copy(alpha = 0.04f),
                ),
            ),
        ),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val topSpecular = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.22f else 0.45f),
                            Color.Transparent,
                        ),
                    )
                    val lineHeight = 1.dp.toPx()
                    onDrawBehind {
                        drawRect(brush = topSpecular, size = Size(size.width, lineHeight))
                    }
                }
                .padding(vertical = verticalPadding),
            content = content,
        )
    }
}
