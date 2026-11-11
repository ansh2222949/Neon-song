package com.neonsong.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.liquidGlassChrome

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
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val resolvedAccent = remember(accentTint, tintColor, primary) {
        when {
            accentTint.isSpecified -> accentTint
            tintColor.isSpecified -> tintColor
            else -> primary
        }
    }

    val resolvedContentColor = if (contentColor.isSpecified) contentColor else onSurface

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.5f
            }
            .liquidGlassChrome(
                shape = shape,
                preset = if (onClick != null && enabled) LiquidGlassPreset.PlayerControls else LiquidGlassPreset.Card,
                accentTint = resolvedAccent,
                borderWidth = borderWidth,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            Column(content = content)
        }
    }
}
