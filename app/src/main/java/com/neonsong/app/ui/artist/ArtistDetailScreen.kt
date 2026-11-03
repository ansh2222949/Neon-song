package com.neonsong.app.ui.artist
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.neonsong.app.data.model.ArtistAlbumItem
import com.neonsong.app.data.model.ArtistPageData
import com.neonsong.app.data.model.ArtistSummaryItem
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.theme.LiquidGlassButton
import com.neonsong.app.ui.theme.LiquidGlassChip
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.ExpressivePillShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.genres.GenreExplorer
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.PlayingWaveBars

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

    // System back / predictive-back gesture is owned by the wrapping
    // PredictiveBackScreen in NavGraph (single handler per screen) — the
    // toolbar button below still pops directly via onBack.

    LaunchedEffect(artistName, browseId) {
        viewModel.loadArtist(artistName, browseId)
    }

    var selectedTrackMenu by remember { mutableStateOf<PlayableTrack?>(null) }
    var showAllSongs by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val showScrolledHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }

    var dominantColor by remember { mutableStateOf<Color?>(null) }
    val ambientColor = dominantColor ?: MaterialTheme.colorScheme.primary
    val animatedBg by animateColorAsState(targetValue = ambientColor, animationSpec = tween(600), label = "artistBg")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is ArtistUiState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    ExpressiveLoadingIndicator(
                        message = "Loading $artistName...",
                        modifier = Modifier.fillMaxSize(),
                    )
                    LiquidGlassIconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp).size(40.dp),
                        contentDescription = "Back",
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            is ArtistUiState.Error -> {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
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
                            Text("Retry")
                        }
                    }
                    LiquidGlassIconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp).size(40.dp),
                        contentDescription = "Back",
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
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
                        .filter(com.neonsong.app.data.artwork.ArtworkNormalizer::isRealImage).distinct()
                }
                var artworkIndex by remember(data) { mutableStateOf(0) }
                val artistArtwork = artworkCandidates.getOrNull(artworkIndex)

                LaunchedEffect(artistArtwork) {
                    if (!artistArtwork.isNullOrBlank()) {
                        val loader = ImageLoader(context)
                        val req = ImageRequest.Builder(context)
                            .data(artistArtwork)
                            .allowHardware(false)
                            .build()
                        val res = loader.execute(req)
                        if (res is SuccessResult) {
                            val bitmap = (res.drawable as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                val palette = Palette.from(bitmap).clearFilters().generate()
                                val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch
                                if (swatch != null) {
                                    dominantColor = Color(swatch.rgb)
                                }
                            }
                        }
                    }
                }

                // Dynamic Backdrop Mesh Gradient flowing through the entire screen calibrated to artist photo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    animatedBg.copy(alpha = 0.75f),
                                    animatedBg.copy(alpha = 0.40f),
                                    animatedBg.copy(alpha = 0.15f),
                                    Color.Black,
                                ),
                                startY = 0f,
                                endY = 2400f,
                            ),
                        ),
                )

                // Apple Music Full-Bleed Edge-to-Edge Hero Image pinned to top behind the list
                val scrollOffset = listState.firstVisibleItemScrollOffset
                val firstIndex = listState.firstVisibleItemIndex
                val heroParallax = if (firstIndex == 0) (scrollOffset * 0.45f) else 480f
                val heroAlpha = if (firstIndex == 0) ((1f - (scrollOffset / 650f)).coerceIn(0f, 1f)) else 0f

                if (heroAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                            .graphicsLayer {
                                translationY = -heroParallax
                                alpha = heroAlpha
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
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(96.dp),
                                )
                            }
                        }
                    }

                    // Apple Music dynamic gradient scrim blending hero artwork into dominant color and black canvas
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.35f),
                                        0.40f to Color.Transparent,
                                        0.70f to animatedBg.copy(alpha = 0.65f),
                                        1.0f to Color.Black,
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
                        bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .adaptiveContentWidth(maxWidth = 860.dp)
                        .align(Alignment.TopCenter),
                ) {
                    // 1. Artist Hero Section: Apple Music Style edge-to-edge artwork & centered hero
                    item(key = "artist_hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 200.dp,
                                    bottom = 8.dp,
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isArtistPlaying) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(bottom = 10.dp),
                                ) {
                                    PlayingWaveBars(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        containerColor = Color.Transparent,
                                        waveColor = animatedBg,
                                    )
                                }
                            }

                            // Centered Artist Title & Stats (Apple Music typography)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = com.neonsong.app.util.ArtistHelper.primaryArtist(data.name),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Text(
                                text = "${data.topSongs.size} popular tracks  •  ${data.albums.size} albums",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 5.dp),
                            )

                            if (!data.subscribers.isNullOrBlank() || !data.monthlyListeners.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = data.subscribers ?: data.monthlyListeners.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            // Circular glass controls keep the hero actions compact and easy to scan.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    LiquidGlassIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.startArtistMix()
                                        },
                                        modifier = Modifier.size(58.dp),
                                        accentTint = Color.White,
                                        contentDescription = "Start artist radio",
                                    ) {
                                        Icon(Icons.Filled.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text("Radio", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                }

                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    LiquidGlassIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isArtistPlaying) musicPlayer.togglePlayPause() else viewModel.playAll()
                                        },
                                        enabled = data.topSongs.isNotEmpty(),
                                        modifier = Modifier.size(64.dp),
                                        accentTint = MaterialTheme.colorScheme.primary,
                                        contentDescription = if (isArtistPlaying) "Pause artist" else "Play artist",
                                    ) {
                                        Icon(
                                            if (isArtistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(27.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(if (isArtistPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                }

                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    LiquidGlassIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.playShuffle()
                                        },
                                        enabled = data.topSongs.isNotEmpty(),
                                        modifier = Modifier.size(58.dp),
                                        accentTint = Color.White,
                                        contentDescription = "Shuffle artist tracks",
                                    ) {
                                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text("Shuffle", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                }
                            }
                        }
                    }

                    // 1b. Latest Release Section (Apple Music pattern)
                    val latestAlbum = data.albums.firstOrNull()
                    if (latestAlbum != null) {
                        item(key = "latest_release") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(
                                    text = "Latest Release",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
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
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            latestAlbum.year?.let { year ->
                                                Text(
                                                    text = year,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Text(
                                                text = latestAlbum.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "Release \u2022 ${data.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "Open",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }


                    // 2. Genre Tags & Bio
                    if (data.tags.isNotEmpty() || !data.bio.isNullOrBlank()) {
                        item(key = "artist_tags_bio") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (data.tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        data.tags.take(6).forEach { tag ->
                                            LiquidGlassChip(
                                                selected = false,
                                                onClick = { genreBridge.genreExplorer.explore(tag) },
                                                shape = RoundedCornerShape(12.dp),
                                                label = { Text(tag) },
                                            )
                                        }
                                    }
                                }

                                if (!data.bio.isNullOrBlank()) {
                                    var isBioExpanded by remember { mutableStateOf(false) }
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(
                                                text = "About",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = data.bio,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                                maxLines = if (isBioExpanded) Int.MAX_VALUE else 4,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (data.bio.length > 120) {
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = if (isBioExpanded) "Show less" else "... Read more",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            isBioExpanded = !isBioExpanded
                                                        }
                                                        .padding(vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Top Songs Section Header
                    if (data.topSongs.isNotEmpty()) {
                        val initialDisplayLimit = 10
                        val visibleSongs = if (showAllSongs || data.topSongs.size <= initialDisplayLimit) {
                            data.topSongs
                        } else {
                            data.topSongs.take(initialDisplayLimit)
                        }

                        item(key = "top_songs_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Top Songs",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        // Top Songs List
                        itemsIndexed(
                            visibleSongs,
                            key = { index, track -> "${track.videoId ?: track.title}_$index" },
                            contentType = { _, _ -> "artist_top_song" },
                        ) { index, track ->
                            val isPlayingThis = playbackState.isPlaying &&
                                playbackState.current?.title.equals(track.title, ignoreCase = true) &&
                                playbackState.current?.artist.equals(track.artist, ignoreCase = true)

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val fullIndex = data.topSongs.indexOf(track).takeIf { it >= 0 } ?: index
                                        viewModel.playAll(startIndex = fullIndex)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Track number / Artwork
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ArtworkImage(
                                            name = track.title,
                                            artist = com.neonsong.app.util.ArtistHelper.primaryArtist(track.artist),
                                            embeddedUrl = track.artworkUrl,
                                            fallbackIcon = Icons.Filled.MusicNote,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (isPlayingThis) {
                                            PlayingWaveBars(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(2.dp)
                                                    .size(24.dp, 18.dp),
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(14.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = track.album ?: com.neonsong.app.util.ArtistHelper.primaryArtist(track.artist).ifBlank { com.neonsong.app.util.ArtistHelper.primaryArtist(data.name) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    LiquidGlassIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedTrackMenu = track
                                        },
                                        modifier = Modifier.size(40.dp),
                                        contentDescription = "Track options",
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showAllSongs = !showAllSongs
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = if (showAllSongs) "Show less" else "Show all songs",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Icon(
                                                imageVector = if (showAllSongs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Albums Carousel
                    if (data.albums.isNotEmpty()) {
                        item(key = "albums_header") {
                            Text(
                                text = "Albums",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp),
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
                                            onOpenAlbum(album.title, com.neonsong.app.util.ArtistHelper.primaryArtist(data.name), album.browseId)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 5. Singles & EPs Carousel
                    if (data.singles.isNotEmpty()) {
                        item(key = "singles_header") {
                            Text(
                                text = "Singles & EPs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp),
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
                                            onOpenAlbum(single.title, com.neonsong.app.util.ArtistHelper.primaryArtist(data.name), single.browseId)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 6. Similar Artists Carousel
                    if (data.similarArtists.isNotEmpty()) {
                        item(key = "similar_artists_header") {
                            Text(
                                text = "Fans Also Like",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }

                        item(key = "similar_artists_row") {
                            val individualArtists = remember(data.similarArtists) {
                                data.similarArtists.flatMap { item ->
                                    val split = com.neonsong.app.util.ArtistHelper.splitArtists(item.name)
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
                                            onOpenArtist(com.neonsong.app.util.ArtistHelper.primaryArtist(artist.name), artist.browseId)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
                .align(Alignment.TopCenter)
                .then(
                    if (showScrolledHeader) {
                        Modifier
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        animatedBg.copy(alpha = 0.85f),
                                        Color(0xFF1C1C1E).copy(alpha = 0.95f),
                                    ),
                                ),
                            )
                            .border(BorderStroke(0.5.dp, Color(0x33545458)))
                    } else Modifier
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .adaptiveContentWidth(maxWidth = 860.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = showScrolledHeader,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    ) {
                        Text(
                            text = com.neonsong.app.util.ArtistHelper.primaryArtist((uiState as? ArtistUiState.Success)?.data?.name ?: artistName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Quick Play Button on Scroll
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
                        modifier = Modifier.size(42.dp),
                        accentTint = MaterialTheme.colorScheme.secondary,
                        contentDescription = "Play artist",
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }

    // Context Menu Sheet for Tracks
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
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                ArtworkImage(
                    name = item.title,
                    artist = "",
                    embeddedUrl = item.artworkUrl,
                    fallbackIcon = Icons.Filled.Album,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!item.year.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
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

            Spacer(Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.type ?: "Album",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(130.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape),
            ) {
                if (!artist.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = com.neonsong.app.util.ArtistHelper.primaryArtist(artist.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = com.neonsong.app.util.ArtistHelper.primaryArtist(artist.name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ArtistDetailGenreBridge @javax.inject.Inject constructor(
    val genreExplorer: GenreExplorer,
) : androidx.lifecycle.ViewModel()
