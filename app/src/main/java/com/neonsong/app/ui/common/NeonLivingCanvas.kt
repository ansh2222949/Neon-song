package com.neonsong.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.neonsong.app.data.artwork.ArtworkNormalizer
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.theme.LocalIsDarkTheme

@Suppress("UNUSED_PARAMETER")
@Composable
fun NeonLivingCanvas(
    currentTrack: PlayableTrack? = null,
    isPlaying: Boolean = true,
    isScrolling: Boolean = false,
    rotatingBackgroundEnabled: Boolean = true,
    fallbackTrack: PlayableTrack? = null,
    accentOverride: Color? = null,
    subtleMotion: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val baseBg = MaterialTheme.colorScheme.background
    val isAmoled = isDark && baseBg == Color.Black

    val activeTrack = currentTrack ?: fallbackTrack
    val artUrl = remember(activeTrack?.artworkUrl) {
        activeTrack?.artworkUrl?.takeIf { ArtworkNormalizer.isRealImage(it) }
            ?.let { ArtworkNormalizer.upscaleYoutubeArtwork(it) ?: it }
    }

    val peeked = remember(artUrl) { ArtworkPaletteCache.peek(artUrl) }
    var multiColors by remember {
        mutableStateOf(peeked ?: ArtworkPaletteCache.lastValidPalette ?: ArtworkPaletteCache.activeSongPalette.value)
    }

    // Read synchronously without mutating state during composition (avoids double recomposition)
    val resolvedColors = peeked ?: multiColors

    LaunchedEffect(artUrl) {
        val url = artUrl ?: return@LaunchedEffect
        val extracted = peeked ?: ArtworkPaletteCache.getOrExtract(context, url)
        if (extracted != null) {
            if (multiColors != extracted) multiColors = extracted
            if (currentTrack != null) {
                ArtworkPaletteCache.setActiveSongPalette(extracted)
            }
        }
    }

    LaunchedEffect(resolvedColors, currentTrack) {
        if (currentTrack != null && resolvedColors != null) {
            ArtworkPaletteCache.setActiveSongPalette(resolvedColors)
        }
    }

    val targetPrimary = accentOverride
        ?: resolvedColors?.primary
        ?: ArtworkPaletteCache.lastValidPalette?.primary
        ?: MaterialTheme.colorScheme.primary
    val targetSecondary = resolvedColors?.secondary
        ?: ArtworkPaletteCache.lastValidPalette?.secondary
        ?: MaterialTheme.colorScheme.secondary
    val targetTertiary = resolvedColors?.tertiary
        ?: ArtworkPaletteCache.lastValidPalette?.tertiary
        ?: MaterialTheme.colorScheme.tertiary

    Box(modifier = modifier.fillMaxSize()) {
        NeonLivingBackdropLayer(
            targetPrimary = targetPrimary,
            targetSecondary = targetSecondary,
            targetTertiary = targetTertiary,
            baseBg = baseBg,
            isDark = isDark,
            isAmoled = isAmoled,
        )
        content()
    }
}

@Composable
private fun NeonLivingBackdropLayer(
    targetPrimary: Color,
    targetSecondary: Color,
    targetTertiary: Color,
    baseBg: Color,
    isDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorSpec = remember { tween<Color>(durationMillis = 260, easing = FastOutSlowInEasing) }
    val c1State = animateColorAsState(targetPrimary, colorSpec, label = "mcPrimary")
    val c2State = animateColorAsState(targetSecondary, colorSpec, label = "mcSecondary")
    val c3State = animateColorAsState(targetTertiary, colorSpec, label = "mcTertiary")
    val topWavePath = remember { Path() }
    val topContourPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBg)
            .drawWithCache {
                val c1 = c1State.value
                val c2 = c2State.value
                val c3 = c3State.value
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val maxDim = maxOf(w, h)

                // Rich, vibrant alpha levels tuned per theme
                val waveAlpha = when {
                    isAmoled -> 0.34f
                    isDark -> 0.42f
                    else -> 0.22f
                }
                val prismAlpha = when {
                    isAmoled -> 0.24f
                    isDark -> 0.30f
                    else -> 0.16f
                }
                val orbAlpha = when {
                    isAmoled -> 0.28f
                    isDark -> 0.36f
                    else -> 0.18f
                }
                val strokeAlpha = if (isDark) 0.16f else 0.18f

                // 1. Reuse Paths instead of allocating new Path objects on every color frame
                topWavePath.reset()
                topWavePath.moveTo(w * 0.04f, 0f)
                topWavePath.lineTo(w, 0f)
                topWavePath.lineTo(w, h * 0.46f)
                topWavePath.cubicTo(w * 0.70f, h * 0.50f, w * 0.42f, h * 0.26f, w * 0.04f, 0f)
                topWavePath.close()

                topContourPath.reset()
                topContourPath.moveTo(w, h * 0.46f)
                topContourPath.cubicTo(w * 0.70f, h * 0.50f, w * 0.42f, h * 0.26f, w * 0.04f, 0f)
                val topWaveBrush = Brush.linearGradient(
                    0.0f to c1.copy(alpha = waveAlpha),
                    0.52f to c2.copy(alpha = waveAlpha * 0.65f),
                    1.0f to Color.Transparent,
                    start = Offset(w, 0f),
                    end = Offset(w * 0.10f, h * 0.48f),
                )
                val topContourBrush = Brush.linearGradient(
                    0.0f to Color.Transparent,
                    0.45f to c1.copy(alpha = strokeAlpha * 1.4f),
                    0.85f to c2.copy(alpha = strokeAlpha),
                    1.0f to Color.Transparent,
                    start = Offset(w * 0.06f, 0f),
                    end = Offset(w, h * 0.46f),
                )

                // 2. Mid-Left Tilted Glass Prism Facet (Color 2 -> Color 3)
                val prismSize = Size(w * 0.76f, w * 0.76f)
                val prismTopLeft = Offset(-w * 0.34f, h * 0.32f)
                val prismPivot = Offset(prismTopLeft.x + prismSize.width / 2f, prismTopLeft.y + prismSize.height / 2f)
                val prismRadius = CornerRadius(w * 0.22f, w * 0.22f)
                val prismFill = Brush.linearGradient(
                    0.0f to c2.copy(alpha = prismAlpha),
                    0.58f to c3.copy(alpha = prismAlpha * 0.62f),
                    1.0f to Color.Transparent,
                    start = prismTopLeft,
                    end = Offset(prismTopLeft.x + prismSize.width, prismTopLeft.y + prismSize.height),
                )
                val prismStrokeColor = c2.copy(alpha = strokeAlpha * 0.8f)

                // 3. Bottom-Right Multi-Color Orbital Glow (Color 3 -> Color 1)
                val bottomOrbCenter = Offset(w * 0.88f, h * 0.90f)
                val bottomOrbRadius = maxDim * 0.58f
                val bottomOrbBrush = Brush.radialGradient(
                    0.0f to c3.copy(alpha = orbAlpha),
                    0.46f to c1.copy(alpha = orbAlpha * 0.45f),
                    1.0f to Color.Transparent,
                    center = bottomOrbCenter,
                    radius = bottomOrbRadius,
                )
                val ringRadius = w * 0.52f
                val ringBrush = Brush.linearGradient(
                    0.0f to c3.copy(alpha = strokeAlpha * 1.1f),
                    0.55f to c1.copy(alpha = strokeAlpha * 0.5f),
                    1.0f to Color.Transparent,
                    start = Offset(w * 0.32f, h * 0.62f),
                    end = Offset(w, h),
                )

                // Soft radial lift behind top header
                val topAmbientWash = Brush.radialGradient(
                    0.0f to c1.copy(alpha = waveAlpha * 0.55f),
                    0.55f to c2.copy(alpha = waveAlpha * 0.20f),
                    1.0f to Color.Transparent,
                    center = Offset(w * 0.72f, h * 0.08f),
                    radius = maxDim * 0.62f,
                )

                // Soft edge grounding vignette so floating glass cards pop forward in 3D depth
                val depthVignette = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.68f to Color.Transparent,
                    1.00f to (if (isDark) Color.Black.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.04f)),
                )

                val contourStroke = Stroke(width = 1.dp.toPx())
                val prismStroke = Stroke(width = 0.85.dp.toPx())

                onDrawBehind {
                    drawRect(topAmbientWash)
                    drawPath(topWavePath, topWaveBrush)
                    drawPath(topContourPath, topContourBrush, style = contourStroke)
                    rotate(degrees = 24f, pivot = prismPivot) {
                        drawRoundRect(prismFill, prismTopLeft, prismSize, prismRadius)
                        drawRoundRect(prismStrokeColor, prismTopLeft, prismSize, prismRadius, style = prismStroke)
                    }
                    drawCircle(bottomOrbBrush, bottomOrbRadius, bottomOrbCenter)
                    drawCircle(ringBrush, ringRadius, bottomOrbCenter, style = contourStroke)
                    drawRect(depthVignette)
                }
            },
    )
}
