package com.neonsong.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.highlight.Highlight
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.LayerBackdrop
import com.neonsong.app.ui.theme.drawInteractiveGlass
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import com.kyant.backdrop.Backdrop

/** Only the bottom corners are rounded for smooth header bleed. */
val HeaderShape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)

@Composable
fun rememberExpressiveHeaderBackdrop(): LayerBackdrop? {
    val background = MaterialTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

@Composable
fun Modifier.liquidHeaderEntrance(): Modifier {
    val scale = remember { Animatable(0.96f) }
    LaunchedEffect(scale) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.56f, stiffness = 520f),
        )
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

/**
 * Premium Liquid Glass Global Header container.
 * Features:
 * - Translucent glass material with depth and dynamic accent glow.
 * - Soft reflective specular bottom border separating header from content.
 * - Static specular sheen with a one-shot spring entrance.
 * - Responsive water-like touch interactions on back button and action icons.
 * - Window-inset aware: content pads for status bar, glass bleeds edge-to-edge.
 */
@Composable
fun ExpressiveHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.tertiary
    val isDark = LocalIsDarkTheme.current
    val isGlassActive = LocalLiquidGlass.current &&
        backdrop != null &&
        isLiquidGlassBackdropSupported()

    Box(modifier.fillMaxWidth().liquidHeaderEntrance().zIndex(1f)) {
        Surface(
            shape = HeaderShape,
            color = Color.Transparent,
            tonalElevation = if (isGlassActive) 0.dp else 4.dp,
            shadowElevation = if (isGlassActive) 0.dp else 8.dp,
            border = if (isGlassActive) {
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = if (isDark) 0.32f else 0.50f),
                        0.45f to Color.White.copy(alpha = if (isDark) 0.08f else 0.15f),
                        1.0f to Color.White.copy(alpha = if (isDark) 0.20f else 0.35f),
                    ),
                )
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    backdrop?.takeIf { isGlassActive }?.let { glassBackdrop ->
                        Modifier.drawInteractiveGlass(
                            isDark = isDark,
                            backdrop = glassBackdrop,
                            luminanceAnimation = 0.5f,
                            shape = HeaderShape,
                            interaction = null,
                            highlight = Highlight(width = 1.dp),
                            blurScale = 1.15f,
                            minScrim = if (isDark) 0.22f else 0.15f,
                            maxScrim = if (isDark) 0.55f else 0.40f,
                        )
                    } ?: Modifier.liquidGlassSurface(
                        shape = HeaderShape,
                        borderWidth = 1.dp,
                        accentTint = glow,
                    )
                ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawGlowBackground(glow, secondaryGlow)
                    .drawHeaderSheen()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(top = 6.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        LiquidGlassIconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp),
                            accentTint = glow,
                            contentDescription = "Back",
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    NeonAppLogo(size = 36.dp)
                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.25).sp,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.takeUnless { it.equals(title, ignoreCase = true) }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

/** Static specular sheen preserves the glass highlight without a perpetual frame loop. */
@Composable
private fun Modifier.drawHeaderSheen(): Modifier {
    val isDark = LocalIsDarkTheme.current

    return this.drawWithCache {
        val sheenBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.08f else 0.14f),
                Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(size.width * 0.7f, size.height * 0.35f),
        )
        onDrawWithContent {
            drawContent()
            drawRect(
                brush = sheenBrush,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** Cached accent glows for [ExpressiveHeader]. Keeping these static avoids
 * repainting two full-width gradients every frame on every screen. */
@Composable
private fun Modifier.drawGlowBackground(color: Color, secondaryColor: Color): Modifier {
    return this.drawWithCache {
        val cx = size.width * 0.58f
        val cy = size.height * 0.46f
        val radius = (size.width.coerceAtLeast(size.height) * 1.08f).coerceAtLeast(1f)
        val secondaryCx = size.width * 0.38f
        val secondaryRadius = (size.width.coerceAtLeast(size.height) * 0.72f).coerceAtLeast(1f)
        val secondaryBrush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.08f),
                secondaryColor.copy(alpha = 0.02f),
                secondaryColor.copy(alpha = 0f),
            ),
            center = Offset(secondaryCx, size.height * 0.78f),
            radius = secondaryRadius,
        )
        val primaryBrush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.08f),
                color.copy(alpha = 0.02f),
                color.copy(alpha = 0f),
            ),
            center = Offset(cx, cy),
            radius = radius,
        )
        onDrawBehind {
            drawRect(brush = secondaryBrush)
            drawRect(brush = primaryBrush)
        }
    }
}

/** A trailing action icon for [ExpressiveHeader] (or any other header-style
 *  surface) with liquid glass surface and water-like touch response. */
@Composable
fun HeaderActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    LiquidGlassIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        contentDescription = contentDescription,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}
