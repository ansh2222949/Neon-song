package com.neonsong.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.Transformation
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import coil.size.Precision
import com.neonsong.app.data.artwork.ArtworkNormalizer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Faithful port of home.js's art-loading behavior for a single row:
 *  - If the track already carries a real (non-placeholder) Last.fm image,
 *    show it directly — zero network calls, matching `!t.image` gating in
 *    enrichTracksWithArt()/​_enrichHomeArt().
 *  - Otherwise, request resolution (Last.fm track.getInfo -> iTunes) the
 *    moment this row enters composition, and recompose automatically the
 *    instant the shared resolved-map StateFlow reports a result — whether
 *    that's this frame or several seconds later.
 *  - Only shows the fallback icon once every provider has genuinely
 *    reported no art (resolved value == ""); shows nothing but the
 *    caller's own background while still resolving.
 */
@Composable
fun ArtworkImage(
    name: String,
    artist: String,
    embeddedUrl: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    decodeSizePx: Int? = null,
    alignment: Alignment = Alignment.Center,
    transformations: List<Transformation> = emptyList(),
    crossfade: Boolean = true,
    enable3DGlass: Boolean = true,
    artworkViewModel: ArtworkViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val key = remember(name, artist) { ArtworkNormalizer.cacheKey(name, artist) }
    // Collect ONLY this row's slot of the shared resolved-map. Collecting the
    // whole map meant every resolution anywhere recomposed every visible
    // artwork row; with distinctUntilChanged each row recomposes exactly once —
    // when its own URL resolves.
    val resolvedUrl by remember(artworkViewModel, key) {
        artworkViewModel.urlForKey(key)
    }.collectAsStateWithLifecycle(initialValue = artworkViewModel.resolved.value[key])
    var resolvedFailed by remember(key, resolvedUrl) { mutableStateOf(false) }
    var embeddedFailed by remember(name, artist, embeddedUrl) { mutableStateOf(false) }
    var refreshRequested by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, resolvedFailed) {
        if (resolvedFailed && !refreshRequested) {
            refreshRequested = true
            artworkViewModel.refresh(name, artist)
        } else if (resolvedUrl == null) {
            artworkViewModel.resolve(name, artist)
        }
    }

    val highResUrl = resolvedUrl?.takeIf { it.isNotBlank() }
    val useHighRes = highResUrl != null && !resolvedFailed

    val displayUrl = remember(useHighRes, highResUrl, embeddedUrl, embeddedFailed) {
        when {
            useHighRes -> highResUrl
            ArtworkNormalizer.isRealImage(embeddedUrl) && !embeddedFailed ->
                ArtworkNormalizer.upscaleYoutubeArtwork(embeddedUrl)
            else -> null
        }
    }

    Box(modifier = modifier, contentAlignment = alignment) {
        if (!displayUrl.isNullOrBlank()) {
            val model = remember(displayUrl, decodeSizePx, transformations, context, crossfade) {
                val builder = ImageRequest.Builder(context)
                    .data(displayUrl)
                    .precision(Precision.INEXACT)
                    .crossfade(if (crossfade) 140 else 0)
                if (decodeSizePx != null) builder.size(decodeSizePx)
                if (transformations.isNotEmpty()) builder.transformations(transformations)
                builder.build()
            }

            val glassOverlayModifier = if (enable3DGlass) {
                Modifier.drawWithCache {
                    // Skip 3-gradient glass overdraw on small list thumbnails (48-56dp) for smooth 120fps scrolling
                    if (size.minDimension < 72.dp.toPx()) {
                        return@drawWithCache onDrawWithContent { drawContent() }
                    }
                    val sheenBrush = Brush.linearGradient(
                        0.0f to Color.Transparent,
                        0.38f to Color.White.copy(alpha = 0.02f),
                        0.50f to Color.White.copy(alpha = 0.14f),
                        0.62f to Color.White.copy(alpha = 0.02f),
                        1.0f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val topRimBrush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    )
                    val borderBrush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.30f),
                        ),
                    )
                    val topRimHeight = 1.dp.toPx()
                    val borderStroke = Stroke(width = 0.75.dp.toPx())

                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = sheenBrush)
                        drawRect(brush = topRimBrush, size = Size(size.width, topRimHeight))
                        drawRect(brush = borderBrush, style = borderStroke)
                    }
                }
            } else {
                Modifier
            }

            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier
                    .fillMaxSize()
                    .then(glassOverlayModifier),
                onError = {
                    if (useHighRes) {
                        resolvedFailed = true
                    } else {
                        embeddedFailed = true
                    }
                },
            )
        } else {
            Icon(
                fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (resolvedUrl == null) 0.35f else 0.6f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}
