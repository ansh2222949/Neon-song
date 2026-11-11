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

    // High-resolution master artwork from Spotify -> Apple Music -> Tidal -> Deezer takes priority.
    // While resolving in background, embeddedUrl (upscaled) is shown seamlessly.
    val displayUrl = if (useHighRes) {
        highResUrl
    } else if (ArtworkNormalizer.isRealImage(embeddedUrl) && !embeddedFailed) {
        ArtworkNormalizer.upscaleYoutubeArtwork(embeddedUrl)
    } else {
        null
    }

    Box(modifier = modifier, contentAlignment = alignment) {
        if (!displayUrl.isNullOrBlank()) {
            val model = remember(displayUrl, decodeSizePx, transformations, context, crossfade) {
                val builder = ImageRequest.Builder(context)
                    .data(displayUrl)
                    .crossfade(crossfade)
                if (decodeSizePx != null) builder.size(decodeSizePx)
                if (transformations.isNotEmpty()) builder.transformations(transformations)
                builder.build()
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (useHighRes) {
                        resolvedFailed = true
                    } else {
                        embeddedFailed = true
                    }
                },
            )

            if (enable3DGlass) {
                // 3D Liquid Glass diagonal specular sheen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                0.0f to Color.Transparent,
                                0.36f to Color.White.copy(alpha = 0.02f),
                                0.50f to Color.White.copy(alpha = 0.16f),
                                0.64f to Color.White.copy(alpha = 0.02f),
                                1.0f to Color.Transparent,
                                start = Offset(0f, 0f),
                                end = Offset(250f, 250f),
                            )
                        )
                )

                // Razor-Thin 3D Specular Top Bevel Rim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.65f),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                // 3D Glass Perimeter Border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            BorderStroke(
                                0.7.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.28f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                    )
                                ),
                            )
                        )
                )
            }
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
