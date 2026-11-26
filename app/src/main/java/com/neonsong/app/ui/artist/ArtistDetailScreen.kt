package com.neonsong.app.ui.artist

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.neonsong.app.data.artwork.ArtworkNormalizer
import com.neonsong.app.data.model.ArtistAlbumItem
import com.neonsong.app.data.model.ArtistSummaryItem
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.genres.GenreExplorer
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.PlayingWaveBars
import com.neonsong.app.ui.theme.ExpressivePillShape
import com.neonsong.app.ui.theme.GlassIconActionButton
import com.neonsong.app.ui.theme.LiquidGlassButton
import com.neonsong.app.ui.theme.LiquidGlassChip
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import com.neonsong.app.util.ArtistHelper

private val ArtistLatestCardShape = SquircleShape(22.dp)
private val ArtistTrackRowShape = SquircleShape(18.dp)
private val ArtistBioCardShape = SquircleShape(22.dp)
private val ArtistCarouselCardShape = SquircleShape(22.dp)
private val ArtistCarouselThumbShape = SquircleShape(15.dp)
private val ArtistHeaderShape = SquircleShape(
    topStart = CornerSize(0.dp),
    topEnd = CornerSize(0.dp),
    bottomEnd = CornerSize(24.dp),
    bottomStart = CornerSize(24.dp),
)
private val ArtistHeroScrimBrush = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.34f),
    0.32f to Color.Transparent,
    0.62f to Color.Black.copy(alpha = 0.56f),
    0.86f to Color.Black.copy(alpha = 0.88f),
    1.00f to Color.Black.copy(alpha = 0.96f),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    browseId: String? = null,
    onBack: () -> Unit,
    onOpenAlbum: (title: String, artist: String, browseId: String?) -> Unit = { _, _, _ -> },
    onOpenArtist: (name: String, browseId: String?) -> Unit = { _, _ -> },
    viewModel: ArtistViewModel = hiltViewModel(),
    genreBridge: ArtistDetailGenreBridge = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val musicPlayer = LocalMusicPlayer.current
    val playbackState by musicPlayer.chromeState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(artistName, browseId) {
        viewModel.loadArtist(artistName, browseId)
    }

    var selectedTrackMenu by remember { mutableStateOf<PlayableTrack?>(null) }
    var showAllSongs by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val showScrolledHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }
    val isHeroVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }

    val artistBackdrop = rememberLayerBackdrop()
    val isGlass = isLiquidGlassBackdropSupported()
    val loadedArtist = (uiState as? ArtistUiState.Success)?.data
    val fallbackArtistUrl = remember(loadedArtist?.artworkUrl, loadedArtist?.bannerUrl, loadedArtist?.fallbackArtworkUrl) {
        listOfNotNull(loadedArtist?.artworkUrl, loadedArtist?.bannerUrl, loadedArtist?.fallbackArtworkUrl)
            .firstOrNull(ArtworkNormalizer::isRealImage)
    }
    val fallbackArtistTrack = remember(fallbackArtistUrl, loadedArtist?.name) {
        fallbackArtistUrl?.let { PlayableTrack(title = loadedArtist?.name.orEmpty(), artist = loadedArtist?.name.orEmpty(), artworkUrl = it) }
    }

    // Synchronous Frame-0 palette peek + smooth draw-phase transition inside NeonLivingCanvas (exact same picture colors, zero recomposition)
    val peekedDominantColor = remember(fallbackArtistUrl) {
        com.neonsong.app.ui.common.ArtworkPaletteCache.peek(fallbackArtistUrl)?.primary
    }
    var dominantColor by remember(artistName, browseId) { mutableStateOf(peekedDominantColor) }
    val ambientColor = dominantColor ?: peekedDominantColor ?: MaterialTheme.colorScheme.primary

    com.neonsong.app.ui.common.NeonLivingCanvas(
        currentTrack = playbackState.current,
        fallbackTrack = fallbackArtistTrack,
        isPlaying = playbackState.isPlaying || playbackState.current == null,
        accentOverride = ambientColor,
        isScrolling = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassSource(if (isGlass) artistBackdrop else null),
        ) {
            when (val state = uiState) {
                is ArtistUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(message = "Loading $artistName...")
                    }
                }
                is ArtistUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            LiquidGlassButton(
                                onClick = { viewModel.loadArtist(artistName, browseId) },
                                shape = ExpressivePillShape,
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }
                is ArtistUiState.Success -> {
                    val data = state.data
                    val isArtistPlaying = playbackState.isPlaying &&
                        (playbackState.sourceLabel == data.name || playbackState.current?.artist.equals(data.name, ignoreCase = true))

                    val context = LocalContext.current
                    val artworkCandidates = remember(data) {
                        listOfNotNull(data.artworkUrl, data.bannerUrl, data.fallbackArtworkUrl)
                            .filter(ArtworkNormalizer::isRealImage)
                            .distinct()
                    }
                    var artworkIndex by remember(data) { mutableIntStateOf(0) }
                    val artistArtwork = artworkCandidates.getOrNull(artworkIndex)

                    LaunchedEffect(artistArtwork) {
                        val url = artistArtwork?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                        com.neonsong.app.ui.common.ArtworkPaletteCache.getOrExtract(context, url)?.primary?.let {
                            dominantColor = it
                        }
                    }

                    // Zero-recomposition Parallax Hero Image
                    if (isHeroVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                                .graphicsLayer {
                                    val offset = if (listState.firstVisibleItemIndex == 0) {
                                        listState.firstVisibleItemScrollOffset.toFloat()
                                    } else 650f
                                    translationY = -offset * 0.42f
                                    alpha = (1f - (offset / 620f)).coerceIn(0f, 1f)
                                },
                        ) {
                            if (artistArtwork != null) {
                                AsyncImage(
                                    model = artistArtwork,
                                    contentDescription = data.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onError = { artworkIndex++ },
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1C1C1E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(96.dp),
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(ArtistHeroScrimBrush),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                ambientColor.copy(alpha = 0.16f),
                                                Color.Black.copy(alpha = 0.55f),
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 28.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .adaptiveContentWidth(maxWidth = 860.dp)
                        .align(Alignment.TopCenter),
                ) {
                    // 1. Artist Hero Section
                    item(key = "artist_hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 198.dp,
                                    bottom = 8.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isArtistPlaying) {
                                Surface(
                                    shape = ExpressivePillShape,
                                    color = ambientColor.copy(alpha = 0.26f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                    modifier = Modifier.padding(bottom = 10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        PlayingWaveBars(
                                            modifier = Modifier.size(18.dp, 14.dp),
                                            containerColor = Color.Transparent,
                                            waveColor = Color.White,
                                        )
                                        Text(
                                            text = "NOW PLAYING",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 0.9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            ),
                                            color = Color.White,
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = ExpressivePillShape,
                                    color = Color.White.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                                    modifier = Modifier.padding(bottom = 10.dp),
                                ) {
                                    Text(
                                        text = "ARTIST",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Text(
                                text = ArtistHelper.primaryArtist(data.name),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1.5).sp,
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(Modifier.height(10.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val audienceStat = data.subscribers ?: data.monthlyListeners
                                if (!audienceStat.isNullOrBlank()) {
                                    Surface(
                                        shape = ExpressivePillShape,
                                        color = Color.White.copy(alpha = 0.10f),
                                        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.16f)),
                                    ) {
                                        Text(
                                            text = audienceStat,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.86f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                                if (data.topSongs.isNotEmpty()) {
                                    Surface(
                                        shape = ExpressivePillShape,
                                        color = Color.White.copy(alpha = 0.10f),
                                        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.16f)),
                                    ) {
                                        Text(
                                            text = "${data.topSongs.size} songs",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.86f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                                if (data.albums.isNotEmpty()) {
                                    Surface(
                                        shape = ExpressivePillShape,
                                        color = Color.White.copy(alpha = 0.10f),
                                        border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.16f)),
                                    ) {
                                        Text(
                                            text = "${data.albums.size} albums",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.86f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(22.dp))

                            // Modern Editorial Pill Action Bar (Play hero CTA + Shuffle + Radio)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isArtistPlaying) musicPlayer.togglePlayPause() else viewModel.playAll()
                                    },
                                    enabled = data.topSongs.isNotEmpty(),
                                    shape = ExpressivePillShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shadowElevation = 12.dp,
                                    modifier = Modifier.height(50.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            if (isArtistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isArtistPlaying) "Pause artist" else "Play artist",
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Text(
                                            text = if (isArtistPlaying) "Pause" else "Play",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                LiquidGlassButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.playShuffle()
                                    },
                                    enabled = data.topSongs.isNotEmpty(),
                                    shape = ExpressivePillShape,
                                    accentTint = ambientColor.copy(alpha = 0.16f),
                                    modifier = Modifier.height(50.dp),
                                ) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = "Shuffle",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }

                                LiquidGlassIconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.startArtistMix()
                                    },
                                    modifier = Modifier.size(50.dp),
                                    accentTint = ambientColor.copy(alpha = 0.16f),
                                    contentDescription = "Start artist radio",
                                ) {
                                    Icon(Icons.Filled.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                    }

                    // 1b. Latest Release Section
                    val latestAlbum = data.albums.firstOrNull()
                    if (latestAlbum != null) {
                        item(key = "latest_release") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .liquidGlassSurface(
                                            shape = ArtistLatestCardShape,
                                            borderWidth = 0.9.dp,
                                            accentTint = ambientColor.copy(alpha = 0.14f),
                                        )
                                        .clip(ArtistLatestCardShape)
                                        .clickable {
                                            onOpenAlbum(latestAlbum.title, data.name, latestAlbum.browseId)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ArtworkImage(
                                            name = latestAlbum.title,
                                            artist = data.name,
                                            embeddedUrl = latestAlbum.artworkUrl,
                                            fallbackIcon = Icons.Filled.Album,
                                            decodeSizePx = 220,
                                            modifier = Modifier
                                                .size(76.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Surface(
                                                shape = ExpressivePillShape,
                                                color = ambientColor.copy(alpha = 0.24f),
                                                modifier = Modifier.padding(bottom = 6.dp),
                                            ) {
                                                Text(
                                                    text = "LATEST RELEASE",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.5.sp,
                                                        letterSpacing = 0.8.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                    ),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                            Text(
                                                text = latestAlbum.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = listOfNotNull(
                                                    latestAlbum.year,
                                                    latestAlbum.type ?: "Album",
                                                    ArtistHelper.primaryArtist(data.name),
                                                ).joinToString(" \u2022 "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.72f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "Open",
                                            tint = Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Top Songs Section
                    if (data.topSongs.isNotEmpty()) {
                        val initialDisplayLimit = 5
                        val visibleSongs = if (showAllSongs || data.topSongs.size <= initialDisplayLimit) {
                            data.topSongs
                        } else {
                            data.topSongs.take(initialDisplayLimit)
                        }

                        item(key = "top_songs_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Top Songs",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            visibleSongs,
                            key = { index, track -> "${track.videoId ?: track.title}_$index" },
                            contentType = { _, _ -> "artist_top_song" },
                        ) { index, track ->
                            val isPlayingThis = playbackState.isPlaying &&
                                playbackState.current?.title.equals(track.title, ignoreCase = true) &&
                                playbackState.current?.artist.equals(track.artist, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassSurface(
                                        shape = ArtistTrackRowShape,
                                        borderWidth = if (isPlayingThis) 1.dp else 0.dp,
                                        accentTint = if (isPlayingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                    )
                                    .clip(ArtistTrackRowShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val fullIndex = data.topSongs.indexOf(track).takeIf { it >= 0 } ?: index
                                        viewModel.playAll(startIndex = fullIndex)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.width(28.dp),
                                        textAlign = TextAlign.Center,
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ArtworkImage(
                                            name = track.title,
                                            artist = ArtistHelper.primaryArtist(track.artist),
                                            embeddedUrl = track.artworkUrl,
                                            fallbackIcon = Icons.Filled.MusicNote,
                                            decodeSizePx = 160,
                                            crossfade = false,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (isPlayingThis) {
                                            PlayingWaveBars(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(2.dp)
                                                    .size(22.dp, 16.dp),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isPlayingThis) MaterialTheme.colorScheme.primary else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = track.album ?: ArtistHelper.primaryArtist(track.artist),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    GlassIconActionButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedTrackMenu = track
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = "Track options",
                                            tint = Color.White.copy(alpha = 0.72f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (data.topSongs.size > initialDisplayLimit) {
                            item(key = "top_songs_toggle_button") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    LiquidGlassChip(
                                        selected = false,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showAllSongs = !showAllSongs
                                        },
                                        shape = CircleShape,
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(
                                                    text = if (showAllSongs) "Show Less" else "See All ${data.topSongs.size} Songs",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                )
                                                Icon(
                                                    imageVector = if (showAllSongs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 3. Albums Carousel
                    if (data.albums.isNotEmpty()) {
                        item(key = "albums_header") {
                            Text(
                                text = "Albums",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        item(key = "albums_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(data.albums, key = { it.browseId.ifBlank { it.title } }) { album ->
                                    ArtistAlbumCard(
                                        item = album,
                                        onClick = {
                                            onOpenAlbum(album.title, ArtistHelper.primaryArtist(data.name), album.browseId)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 4. Singles & EPs Carousel
                    if (data.singles.isNotEmpty()) {
                        item(key = "singles_header") {
                            Text(
                                text = "Singles & EPs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        item(key = "singles_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(data.singles, key = { it.browseId.ifBlank { it.title } }) { single ->
                                    ArtistAlbumCard(
                                        item = single,
                                        onClick = {
                                            onOpenAlbum(single.title, ArtistHelper.primaryArtist(data.name), single.browseId)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 5. Similar Artists Carousel
                    if (data.similarArtists.isNotEmpty()) {
                        item(key = "similar_artists_header") {
                            Text(
                                text = "Fans Also Like",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        item(key = "similar_artists_row") {
                            val individualArtists = remember(data.similarArtists) {
                                data.similarArtists.flatMap { item ->
                                    val split = ArtistHelper.splitArtists(item.name)
                                    if (split.size <= 1) listOf(item)
                                    else split.map { singleName ->
                                        item.copy(name = singleName, browseId = "")
                                    }
                                }.distinctBy { it.name.lowercase().trim() }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(individualArtists, key = { it.browseId.ifBlank { it.name } }) { artist ->
                                    SimilarArtistCard(
                                        artist = artist,
                                        onClick = {
                                            onOpenArtist(ArtistHelper.primaryArtist(artist.name), artist.browseId)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 6. Genre Tags & Bio at Bottom (Apple Music layout)
                    if (data.tags.isNotEmpty() || !data.bio.isNullOrBlank()) {
                        item(key = "artist_tags_bio") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                if (data.tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        data.tags.take(6).forEach { tag ->
                                            LiquidGlassChip(
                                                selected = false,
                                                onClick = { genreBridge.genreExplorer.explore(tag) },
                                                shape = RoundedCornerShape(12.dp),
                                                label = { Text(tag, color = Color.White) },
                                            )
                                        }
                                    }
                                }

                                if (!data.bio.isNullOrBlank()) {
                                    var isBioExpanded by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .liquidGlassSurface(
                                                shape = ArtistBioCardShape,
                                                borderWidth = 0.8.dp,
                                                interactive = true,
                                                onClick = { isBioExpanded = !isBioExpanded },
                                            )
                                            .padding(16.dp),
                                    ) {
                                        Column {
                                            Text(
                                                text = "About ${ArtistHelper.primaryArtist(data.name)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = data.bio,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White.copy(alpha = 0.78f),
                                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                                maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (data.bio.length > 120) {
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = if (isBioExpanded) "Show Less" else "Read More",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Top Navigation Bar (Pinned Glass Header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
                .align(Alignment.TopCenter)
                .then(
                    if (showScrolledHeader) {
                        Modifier.liquidGlassChrome(
                            shape = ArtistHeaderShape,
                            preset = LiquidGlassPreset.Header,
                            backdrop = artistBackdrop,
                            accentTint = ambientColor.copy(alpha = 0.25f),
                        )
                    } else Modifier,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .adaptiveContentWidth(maxWidth = 860.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    contentDescription = "Back",
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = showScrolledHeader,
                        enter = fadeIn() + scaleIn(initialScale = 0.92f),
                        exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    ) {
                        Text(
                            text = ArtistHelper.primaryArtist((uiState as? ArtistUiState.Success)?.data?.name ?: artistName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showScrolledHeader && (uiState as? ArtistUiState.Success)?.data?.topSongs?.isNotEmpty() == true,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    LiquidGlassIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playAll()
                        },
                        modifier = Modifier.size(40.dp),
                        accentTint = MaterialTheme.colorScheme.primary,
                        contentDescription = "Play artist",
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }

    selectedTrackMenu?.let { track ->
        TrackContextMenuSheet(
            target = TrackMenuTarget.Track(track.title, track.artist, ""),
            capabilities = TrackMenuCapabilities(showCopyActions = true, showDeleteScrobble = false),
            playableTrack = track,
            playbackSourceLabel = artistName,
            onDismiss = { selectedTrackMenu = null },
        )
    }
}

@Composable
private fun ArtistAlbumCard(
    item: ArtistAlbumItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(164.dp)
            .liquidGlassSurface(
                shape = ArtistCarouselCardShape,
                borderWidth = 0.8.dp,
            )
            .clip(ArtistCarouselCardShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(ArtistCarouselThumbShape)
                    .border(0.7.dp, Color.White.copy(alpha = 0.14f), ArtistCarouselThumbShape),
            ) {
                ArtworkImage(
                    name = item.title,
                    artist = "",
                    embeddedUrl = item.artworkUrl,
                    fallbackIcon = Icons.Filled.Album,
                    decodeSizePx = 280,
                    crossfade = false,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!item.year.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = item.year,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.type ?: "Album",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SimilarArtistCard(
    artist: ArtistSummaryItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(142.dp)
            .liquidGlassSurface(
                shape = ArtistCarouselCardShape,
                borderWidth = 0.8.dp,
            )
            .clip(ArtistCarouselCardShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            ) {
                if (!artist.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = ArtistHelper.primaryArtist(artist.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = ArtistHelper.primaryArtist(artist.name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ArtistDetailGenreBridge @javax.inject.Inject constructor(
    val genreExplorer: GenreExplorer,
) : androidx.lifecycle.ViewModel()
