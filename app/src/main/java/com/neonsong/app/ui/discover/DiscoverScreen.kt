package com.neonsong.app.ui.discover

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.generate.GeneratedTrack
import com.neonsong.app.playback.toPlayableTrack
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.NeonLivingCanvas
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.common.ExpressiveHeader
import com.neonsong.app.ui.common.rememberExpressiveHeaderBackdrop
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.GroupGap
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.HeaderActionIcon
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.common.safeHorizontalContentPadding
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.theme.ExpressivePillShape
import com.neonsong.app.ui.theme.LiquidGlassButton
import com.neonsong.app.ui.theme.liquidGlassSource

private val DiscoverHeroShape = SquircleShape(24.dp)
private val DiscoverHeroIconShape = SquircleShape(16.dp)
private val DiscoverThumbShape = SquircleShape(14.dp)

private fun Modifier.shimmerFill(
    progressProvider: () -> Float,
    baseColor: Color,
    highlightColor: Color,
): Modifier = this.drawWithCache {
    val bandWidth = (size.width * 0.55f).coerceAtLeast(120f)
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset.Zero,
        end = Offset(bandWidth, size.height),
    )
    onDrawBehind {
        drawRect(color = baseColor)
        val x = progressProvider() * (size.width + bandWidth) - bandWidth
        clipRect {
            translate(left = x, top = 0f) {
                drawRect(brush = brush, topLeft = Offset.Zero, size = Size(bandWidth, size.height))
            }
        }
    }
}

@Composable
fun DiscoverScreen(onBack: () -> Unit = {}, viewModel: DiscoverViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val headerBackdrop = rememberExpressiveHeaderBackdrop()
    val musicPlayer = LocalMusicPlayer.current
    val playbackState by musicPlayer.chromeState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var menuTrack by remember { mutableStateOf<GeneratedTrack?>(null) }
    val playbackQueue = remember(state.tracks) { state.tracks.map(GeneratedTrack::toPlayableTrack) }
    val playFromDiscover: (GeneratedTrack) -> Unit = { track ->
        val index = state.tracks.indexOfFirst { it.key == track.key }.coerceAtLeast(0)
        musicPlayer.playDiscoverQueue(playbackQueue, index)
    }

    val shouldLoadMore by remember(listState, state.tracks.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // The hero and section title occupy the first two lazy-list
            // positions before the track rows.
            last >= state.tracks.size
        }
    }
    LaunchedEffect(shouldLoadMore, state.tracks.size) {
        if (shouldLoadMore && !state.isLoading && !state.isLoadingMore && !state.endReached && state.tracks.isNotEmpty()) viewModel.loadMore()
    }

    NeonLivingCanvas(
        currentTrack = playbackState.current,
        isPlaying = playbackState.isPlaying,
        isScrolling = false,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 860.dp)
                .align(Alignment.TopCenter),
        ) {
            ExpressiveHeader(
            title = "Discover",
            subtitle = if (state.isYtConnected) {
                "YouTube Music picks shaped around your taste"
            } else {
                "Fresh music shaped around your listening taste"
            },
            onBack = onBack,
            backdrop = headerBackdrop,
            actions = {
                HeaderActionIcon(Icons.Filled.Refresh, "Refresh recommendations", viewModel::refresh)
                HeaderActionIcon(Icons.Filled.BookmarkAdd, "Save as playlist", viewModel::saveAsPlaylist)
                HeaderActionIcon(Icons.Filled.Shuffle, "Shuffle Recommendations", viewModel::surpriseMe)
            },
        )

        Box(
            Modifier
                .fillMaxSize()
                .safeHorizontalContentPadding()
                .liquidGlassSource(headerBackdrop),
        ) {
            Crossfade(
                targetState = state.isLoading && state.tracks.isEmpty(),
                animationSpec = tween(ExpressiveMotion.Standard, easing = FastOutSlowInEasing),
                label = "discoverState",
            ) { isLoading ->
                if (isLoading) {
                    val transition = rememberInfiniteTransition(label = "shimmer")
                    val shimmerProgress = transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                        label = "shimmerProgress",
                    )
                    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                    val highlightColor = androidx.compose.ui.graphics.lerp(baseColor, MaterialTheme.colorScheme.primary, 0.22f).copy(alpha = 0.85f)
                    val shimmerMod = Modifier.shimmerFill({ shimmerProgress.value }, baseColor, highlightColor)
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
                        ),
                        verticalArrangement = Arrangement.spacedBy(GroupGap),
                    ) {
                        items(10) { index ->
                            SkeletonCard(
                                shimmerModifier = shimmerMod,
                                position = when (index) {
                                    0 -> GroupPosition.TOP
                                    9 -> GroupPosition.BOTTOM
                                    else -> GroupPosition.MIDDLE
                                },
                                titleWidthFraction = listOf(0.62f, 0.48f, 0.7f, 0.55f)[index % 4],
                                subtitleWidthFraction = listOf(0.4f, 0.3f, 0.45f, 0.35f)[index % 4],
                            )
                        }
                    }
                } else when {
                    state.error != null && state.tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
                            Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LiquidGlassButton(
                                onClick = viewModel::loadInitial,
                                shape = ExpressivePillShape,
                            ) { Text("Retry") }
                        }
                    }
                    state.tracks.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No recommendations yet", style = MaterialTheme.typography.titleMedium)
                            Text("Listen to more music, then pull to refresh.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(GroupGap),
                        modifier = Modifier,
                    ) {
                        item(key = "discover_hero", contentType = "discover_hero") {
                            DiscoverHeroCard(
                                isYtConnected = state.isYtConnected,
                                trackCount = state.tracks.size,
                            )
                        }
                        item(key = "discover_section_title", contentType = "section_title") {
                            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(
                                    "Your next favorites",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    if (state.isYtConnected) "Selected from your connected music taste" else "Picked from your listening history",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        itemsIndexed(
                            state.tracks,
                            key = { _, t -> t.key },
                            contentType = { _, _ -> "discover_track" },
                        ) { index, track ->
                            val position = when {
                                state.tracks.size == 1 -> GroupPosition.SINGLE
                                index == 0 -> GroupPosition.TOP
                                index == state.tracks.lastIndex -> GroupPosition.BOTTOM
                                else -> GroupPosition.MIDDLE
                            }
                            val isPlayingThisSong = playbackState.isPlaying &&
                                playbackState.current?.title.equals(track.name, ignoreCase = true) &&
                                playbackState.current?.artist.equals(track.artist, ignoreCase = true)

                            DiscoverCard(
                                track = track,
                                position = position,
                                isPlaying = isPlayingThisSong,
                                onPlay = { playFromDiscover(track) },
                                onMenu = { menuTrack = track },
                                modifier = Modifier,
                            )
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading_more", contentType = "loading") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        }

        // Anchored properly above MiniPlayer/gesture bar instead of clipped under fillMaxSize()
        state.saveResultMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                viewModel.dismissSaveResult()
            }
            Surface(
                shape = ExpressivePillShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding()),
            ) {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    menuTrack?.let { track ->
        TrackContextMenuSheet(
            target = TrackMenuTarget.Track(track.name, track.artist, track.url),
            capabilities = TrackMenuCapabilities(showCopyActions = true, showDeleteScrobble = true),
            onPlayInNeonSong = { playFromDiscover(track) },
            onDismiss = { menuTrack = null },
        )
    }
}

@Composable
private fun DiscoverHeroCard(isYtConnected: Boolean, trackCount: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val heroGradient = remember(primary, tertiary) {
        Brush.horizontalGradient(
            listOf(
                primary.copy(alpha = 0.22f),
                tertiary.copy(alpha = 0.10f),
                androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = DiscoverHeroShape,
                borderWidth = 0.95.dp,
                accentTint = primary,
                alphaScale = 1.15f,
            )
            .background(heroGradient),
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = DiscoverHeroIconShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "A little more you",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isYtConnected) "New YouTube Music finds, alongside familiar favorites." else "Discover tracks that fit the music you already love.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Text(
                        text = if (trackCount > 0) "$trackCount tracks ready" else "Building your recommendations",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverCard(
    track: GeneratedTrack,
    position: GroupPosition,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.neonsong.app.ui.common.ExpressiveGroupTrackRow(
        title = track.name,
        subtitle = track.artist,
        position = position,
        isPlaying = isPlaying,
        onClick = onPlay,
        modifier = modifier,
        leading = {
            Box(modifier = Modifier.size(52.dp)) {
                ArtworkImage(
                    name = track.name,
                    artist = track.artist,
                    embeddedUrl = track.artworkUrl,
                    fallbackIcon = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                    decodeSizePx = 160,
                    crossfade = false,
                    modifier = Modifier.fillMaxSize().clip(DiscoverThumbShape),
                )
                if (isPlaying) {
                    com.neonsong.app.ui.player.PlayingWaveBars(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(24.dp, 18.dp),
                    )
                }
            }
        },
        trailing = { com.neonsong.app.ui.common.OverflowMenuButton(onClick = onMenu) },
    )
}

@Composable
private fun SkeletonCard(
    shimmerModifier: Modifier,
    position: GroupPosition = GroupPosition.SINGLE,
    titleWidthFraction: Float = 0.6f,
    subtitleWidthFraction: Float = 0.4f,
) {
    Card(
        shape = groupShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.48f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).then(shimmerModifier))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(titleWidthFraction).height(16.dp).clip(RoundedCornerShape(8.dp)).then(shimmerModifier))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(subtitleWidthFraction).height(12.dp).clip(RoundedCornerShape(6.dp)).then(shimmerModifier))
            }
        }
    }
}
