package com.neonsong.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface

/**
 * Premium Liquid Glass Card with translucent material depth, directional specular border,
 * optical curved-glass top sheen, and water-like touch response.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(24.dp),
    enabled: Boolean = true,
    tintColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    accentTint: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedAccent = when {
        accentTint.isSpecified -> accentTint
        tintColor.isSpecified -> tintColor
        else -> MaterialTheme.colorScheme.primary
    }

    val resolvedContentColor = if (contentColor.isSpecified) {
        contentColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
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
