package com.neonsong.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface

private val DefaultLiquidCardShape = SquircleShape(24.dp)

/**
 * Premium Liquid Glass Card with translucent material depth, directional specular border,
 * optical curved-glass top sheen, and water-like touch response.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultLiquidCardShape,
    enabled: Boolean = true,
    tintColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp = 0.95.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val shadowElevationPx = with(density) { (if (isDark) 10.dp else 6.dp).toPx() }
    val spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.18f)

    val resolvedAccent = remember(accentTint, tintColor) {
        when {
            accentTint.isSpecified -> accentTint
            tintColor.isSpecified -> tintColor
            else -> Color.Unspecified
        }
    }

    val resolvedContentColor = if (contentColor.isSpecified) contentColor else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            // Single RenderNode combining alpha + shadow, with song-palette read deferred to draw phase
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.5f
                shadowElevation = shadowElevationPx
                this.shape = shape
                clip = false
                val liveAccent = if (resolvedAccent.isSpecified) {
                    resolvedAccent
                } else {
                    ArtworkPaletteCache.activeSongPalette.value?.primary ?: fallbackPrimary
                }
                ambientShadowColor = liveAccent.copy(alpha = 0.22f)
                spotShadowColor = spotColor
            }
            .liquidGlassSurface(
                shape = shape,
                borderWidth = borderWidth,
                accentTint = resolvedAccent,
                interactive = onClick != null && enabled,
                onClick = if (enabled) onClick else null,
            )
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            Column(content = content)
        }
    }
}
