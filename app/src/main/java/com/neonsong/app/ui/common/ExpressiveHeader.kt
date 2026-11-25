package com.neonsong.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.kyant.backdrop.Backdrop
import com.neonsong.app.ui.theme.LayerBackdrop
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.rememberLayerBackdrop

/** Only the bottom corners are rounded for smooth header bleed with Squircle curvature. */
val HeaderShape = SquircleShape(
    topStart = CornerSize(0.dp),
    topEnd = CornerSize(0.dp),
    bottomEnd = CornerSize(26.dp),
    bottomStart = CornerSize(26.dp),
)

@Composable
fun rememberExpressiveHeaderBackdrop(): LayerBackdrop? {
    return rememberLayerBackdrop {
        drawContent()
    }
}

@Composable
fun Modifier.liquidHeaderEntrance(): Modifier {
    val scale = remember { Animatable(0.985f) }
    LaunchedEffect(scale) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 460f, visibilityThreshold = 0.001f),
        )
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

@Composable
fun ExpressiveHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val songAccent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: MaterialTheme.colorScheme.primary
    val isDark = LocalIsDarkTheme.current
    val isGlassActive = LocalLiquidGlass.current &&
        backdrop != null &&
        isLiquidGlassBackdropSupported()

    Box(modifier.fillMaxWidth().liquidHeaderEntrance().zIndex(1f)) {
        Surface(
            shape = HeaderShape,
            color = if (isGlassActive) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.88f else 0.94f)
            },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = null,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 12.dp else 6.dp,
                    shape = HeaderShape,
                    ambientColor = songAccent.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f),
                    clip = false,
                )
                .then(
                    if (isGlassActive && backdrop != null) {
                        Modifier.liquidGlassChrome(
                            shape = HeaderShape,
                            enabled = true,
                            preset = LiquidGlassPreset.Header,
                            backdrop = backdrop,
                            accentTint = Color.Unspecified, // Lets subtle song-reactive tint apply automatically
                        )
                    } else {
                        Modifier.liquidGlassSurface(
                            shape = HeaderShape,
                            borderWidth = 0.9.dp,
                            accentTint = Color.Unspecified,
                            alphaScale = 1.35f,
                        )
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val topSheenBrush = Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.18f),
                            0.35f to Color.White.copy(alpha = 0.02f),
                            1.0f to Color.Transparent,
                        )
                        val glowRadius = (size.width * 0.85f).coerceAtLeast(1f)
                        val glowBrush = Brush.radialGradient(
                            colors = listOf(
                                songAccent.copy(alpha = if (isDark) 0.08f else 0.05f),
                                songAccent.copy(alpha = if (isDark) 0.02f else 0.01f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.75f, 0f),
                            radius = glowRadius,
                        )
                        val shimmerWidth = size.width * 0.35f
                        val shimmerCenter = size.width * 0.6f
                        val shimmerBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.07f else 0.12f),
                                Color.Transparent,
                            ),
                            startX = shimmerCenter - shimmerWidth / 2f,
                            endX = shimmerCenter + shimmerWidth / 2f,
                        )
                        val shimmerHeight = 2.dp.toPx()

                        onDrawBehind {
                            drawRect(brush = topSheenBrush)
                            drawCircle(brush = glowBrush, radius = glowRadius)
                            drawRect(brush = shimmerBrush, size = Size(size.width, shimmerHeight))
                        }
                    }
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
                            accentTint = Color.Unspecified,
                            contentDescription = "Back",
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = contentColor,
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
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.25).sp,
                            ),
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        subtitle?.takeUnless { it.equals(title, ignoreCase = true) }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryContentColor,
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
