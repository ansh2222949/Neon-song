package com.neonsong.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassChrome

/**
 * Material 3 Expressive Floating Toolbar.
 * A compact, floating pill-shaped container styled dynamically with M3 elevation, liquid glass chrome,
 * and subtle specular rim border.
 */
@Composable
fun FloatingToolbar(
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val backdrop = LocalLiquidGlassBackdrop.current
    val isGlass = LocalLiquidGlass.current &&
        isLiquidGlassBackdropSupported() &&
        backdrop != null

    Surface(
        modifier = modifier.then(
            if (isGlass) {
                Modifier.liquidGlassChrome(
                    shape = CircleShape,
                    enabled = true,
                    preset = LiquidGlassPreset.FloatingControls,
                    backdrop = backdrop,
                )
            } else {
                Modifier
            },
        ),
        shape = CircleShape,
        color = if (isGlass) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f)
        },
        tonalElevation = 0.dp,
        shadowElevation = if (isGlass) 0.dp else elevation,
        border = if (isGlass) null else BorderStroke(
            0.85.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.28f else 0.55f),
                    Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
                ),
            ),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
