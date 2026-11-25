package com.neonsong.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.neonsong.app.ui.theme.LocalBottomChromePadding
import com.neonsong.app.ui.theme.NeonSpacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neonsong.app.data.repository.HomeAlbum
import com.neonsong.app.data.repository.HomeArtistItem
import com.neonsong.app.data.repository.HomeSortMode
import com.neonsong.app.data.repository.HomeTrack
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.DpCropDialog
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.common.NeonPopupCardSheet
import com.neonsong.app.ui.common.OverflowMenuButton
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.TrackMiniTrayData
import com.neonsong.app.ui.common.TrackMiniTraySheet
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.rememberAdaptiveCardWidth
import com.neonsong.app.ui.common.safeHorizontalContentPadding
import com.neonsong.app.ui.navigation.ArtistAlbumNavigator
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.PlayingWaveBars
import com.neonsong.app.ui.shell.FloatingNavDefaults
import com.neonsong.app.ui.theme.BadgePillShape
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.theme.GlassActionButton as Button
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import com.neonsong.app.ui.theme.LiquidGlassChip
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.util.ArtistHelper
import com.neonsong.app.util.LocalHapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val ActionCardShape = SquircleShape(18.dp)
private val RowShape = SquircleShape(16.dp)
private val ThumbShape = SquircleShape(12.dp)
private val StatsCardShape = SquircleShape(24.dp)
private val MixHeaderShape = SquircleShape(22.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenGenres: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    artistAlbumNavigator: ArtistAlbumNavigator = hiltViewModel<ArtistAlbumNavBridgeHome>().navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pollWhileActive()
        }
    }

    var menuTrack by remember { mutableStateOf<HomeTrack?>(null) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val hapticManager = LocalHapticManager.current

    val dpPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            hapticManager.click()
            pendingCropUri = uri
        }
    }

    pendingCropUri?.let { cropUri ->
        DpCropDialog(
            sourceUri = cropUri,
            onDismiss = { pendingCropUri = null },
            onCropSuccess = { croppedFile ->
                pendingCropUri = null
                viewModel.setCustomPhotoUri(Uri.fromFile(croppedFile), context)
            },
        )
    }

    val activeUsername = remember(
        uiState.isViewingFriend,
        uiState.viewingUsername,
        uiState.username,
        uiState.isLocalStatsMode,
    ) {
        when {
            uiState.isViewingFriend -> uiState.viewingUsername
            uiState.username.isNotBlank() -> uiState.username
            uiState.isLocalStatsMode -> "Guest"
            else -> "Neon Song"
        }
    }

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val onLoadNextPage by rememberUpdatedState(viewModel::loadNextPage)
    var hasAnimatedStats by rememberSaveable { mutableStateOf(false) }
    var isInitialComposition by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlowNearEnd(listState) { onLoadNextPage() }
    }

    LaunchedEffect(uiState.sortMode, activeUsername) {
        if (isInitialComposition) {
            isInitialComposition = false
        } else {
            listState.scrollToItem(0)
        }
    }

    val playbackQueue = remember(rows) {
        rows.mapNotNull { row ->
            (row as? HomeRow.Track)?.track?.let { track ->
                PlayableTrack(
                    title = track.name,
                    artist = track.artist,
                    artworkUrl = track.artworkUrl,
                    videoId = track.videoId,
                )
            }
        }
    }
    val playbackIndexByRow = remember(rows) {
        var nextPlaybackIndex = 0
        IntArray(rows.size) { rowIndex ->
            if (rows[rowIndex] is HomeRow.Track) nextPlaybackIndex++ else -1
        }
    }

    val musicPlayer = LocalMusicPlayer.current
    var miniTrayTrack by remember { mutableStateOf<HomeTrack?>(null) }
    val latestPlaybackQueue by rememberUpdatedState(playbackQueue)
    val latestPlaybackIndexByRow by rememberUpdatedState(playbackIndexByRow)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->
        if (uiState.isLoading && rows.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .safeHorizontalContentPadding(),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveLoadingIndicator(message = "Loading your listening history")
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .adaptiveContentWidth(maxWidth = 860.dp)
                    .safeHorizontalContentPadding(),
            ) {
                // Heavy Rotation carousel uses top tracks (or first 10 tracks without skipping index 0 & 1!)
                val heavyRotationTracks = remember(uiState.topTracks7Days, uiState.topTracksOverall, rows) {
                    val base = uiState.topTracks7Days.ifEmpty { uiState.topTracksOverall }
                    if (base.isNotEmpty()) {
                        base.take(10)
                    } else {
                        rows.filterIsInstance<HomeRow.Track>().map { it.track }.take(10)
                    }
                }

                val bottomPadding = LocalBottomChromePadding.current
                val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = NeonSpacing.ScreenHorizontal,
                        end = NeonSpacing.ScreenHorizontal,
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(NeonSpacing.SectionGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 1. Apple Music Header with Live Timer, Search, Friend Switcher & Avatar
                    item(key = "apple_home_header", contentType = "header") {
                        AppleHomeHeader(
                            title = if (uiState.isViewingFriend) activeUsername else "Stats",
                            subtitle = if (uiState.isViewingFriend) "Friend's Listening Profile" else "Listening Insights & History",
                            isViewingFriend = uiState.isViewingFriend,
                            avatarUrl = uiState.stats?.avatarUrl,
                            customPhotoUri = if (uiState.isViewingFriend) null else uiState.customPhotoUri,
                            viewModel = viewModel,
                            onSearchClick = {
                                hapticManager.click()
                                onOpenSearch()
                            },
                            onReturnToOwnProfile = {
                                hapticManager.click()
                                viewModel.returnToOwnProfile()
                            },
                            onAvatarClick = {
                                hapticManager.click()
                                if (uiState.isViewingFriend) {
                                    onOpenFriends()
                                } else {
                                    showProfileSheet = true
                                }
                            },
                        )
                    }

                    // Optional Error Banner
                    if (uiState.error != null) {
                        item(key = "home_error_banner", contentType = "error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(16.dp),
                                        borderWidth = 0.8.dp,
                                        accentTint = MaterialTheme.colorScheme.error,
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = uiState.error ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = viewModel::refresh) {
                                        Text("Retry", color = MaterialTheme.colorScheme.primary)
                                    }
                                    TextButton(onClick = viewModel::dismissError) {
                                        Text("Dismiss", color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }

                    // 2. Quick Actions Row (Apple Liquid Glass Pills)
                    item(key = "quick_actions", contentType = "actions") {
                        QuickActionsRow(
                            onOpenDiscover = onOpenDiscover,
                            onOpenFriends = onOpenFriends,
                            onOpenDownloads = onOpenDownloads,
                            isViewingFriend = uiState.isViewingFriend,
                        )
                    }

                    // 3. Optional Local Mode Banner
                    if (uiState.isLocalStatsMode && !uiState.isViewingFriend) {
                        item(key = "local_stats_banner", contentType = "banner") {
                            LocalStatsBanner(
                                onOpenSettings = onOpenSettings,
                            )
                        }
                    }

                    // 4. Bento Hero Stats Card (Smooth Non-Bouncy Counter + Liquid Glass)
                    uiState.stats?.let { stats ->
                        item(key = "stats_bento_card", contentType = "stats") {
                            StatsCard(
                                scrobbles = stats.scrobbles,
                                trackCount = stats.trackCount,
                                artistCount = stats.artistCount,
                                albumCount = stats.albumCount,
                                topTags = uiState.topTags,
                                hasAnimatedStats = hasAnimatedStats,
                                onStatsAnimated = { hasAnimatedStats = true },
                                headlineLabel = if (uiState.isLocalStatsMode && !uiState.isViewingFriend) {
                                    "Total Plays"
                                } else {
                                    "Total Scrobbles"
                                },
                                onOpenGenres = onOpenGenres,
                            )
                        }
                    }

                    // 5. Heavy Rotation Carousel (156dp Square Apple Cards)
                    if (heavyRotationTracks.isNotEmpty()) {
                        item(key = "apple_heavy_rotation_section", contentType = "section") {
                            AppleSectionHeader(
                                title = "Heavy Rotation",
                                onChevronClick = {
                                    val queue = heavyRotationTracks.map { track ->
                                        PlayableTrack(
                                            title = track.name,
                                            artist = track.artist,
                                            artworkUrl = track.artworkUrl,
                                            videoId = track.videoId,
                                        )
                                    }
                                    if (queue.isNotEmpty()) {
                                        musicPlayer.playQueue(
                                            tracks = queue,
                                            startIndex = 0,
                                            sourceLabel = "Heavy Rotation",
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            AppleRecentlyPlayedCarousel(
                                tracks = heavyRotationTracks,
                                onTrackClick = { track ->
                                    val queue = heavyRotationTracks.map {
                                        PlayableTrack(
                                            title = it.name,
                                            artist = it.artist,
                                            artworkUrl = it.artworkUrl,
                                            videoId = it.videoId,
                                        )
                                    }
                                    val idx = queue.indexOfFirst {
                                        (track.videoId != null && it.videoId == track.videoId) ||
                                            (it.title == track.name && it.artist == track.artist)
                                    }.coerceAtLeast(0)
                                    musicPlayer.playQueue(
                                        tracks = queue,
                                        startIndex = idx,
                                        sourceLabel = "Heavy Rotation",
                                    )
                                },
                            )
                            Spacer(Modifier.height(22.dp))
                        }
                    }

                    // 6. Sticky Mix & Sort Header
                    stickyHeader(key = "sticky_mix_header", contentType = "sticky_header") {
                        MixHeader(
                            sortMode = uiState.sortMode,
                            onSortModeChange = viewModel::setSortMode,
                            onPlayAll = {
                                if (latestPlaybackQueue.isNotEmpty()) {
                                    musicPlayer.playQueue(
                                        tracks = latestPlaybackQueue,
                                        startIndex = 0,
                                        sourceLabel = "Home",
                                    )
                                }
                            },
                        )
                    }

                    // 7. Track / Artist / Album Feed
                    itemsIndexed(
                        items = rows,
                        key = { index, row -> row.stableKey(index) },
                        contentType = { _, row -> row.type },
                    ) { rowIndex, row ->
                        val currentRowIndex by rememberUpdatedState(rowIndex)

                        when (row) {
                            is HomeRow.DateHeader -> DateHeaderRow(row.label)
                            is HomeRow.Track -> {
                                val onTrackClick = remember(row.track.key) {
                                    {
                                        val targetIdx = latestPlaybackQueue.indexOfFirst {
                                            (row.track.videoId != null && it.videoId == row.track.videoId) ||
                                                (it.title == row.track.name && it.artist == row.track.artist)
                                        }.takeIf { it >= 0 } ?: latestPlaybackIndexByRow.getOrElse(currentRowIndex) { -1 }

                                        if (targetIdx >= 0 && latestPlaybackQueue.isNotEmpty()) {
                                            musicPlayer.playQueue(
                                                tracks = latestPlaybackQueue,
                                                startIndex = targetIdx,
                                                sourceLabel = "Home",
                                            )
                                        } else {
                                            musicPlayer.play(
                                                PlayableTrack(
                                                    title = row.track.name,
                                                    artist = row.track.artist,
                                                    artworkUrl = row.track.artworkUrl,
                                                    videoId = row.track.videoId,
                                                ),
                                                sourceLabel = "Home",
                                            )
                                        }
                                    }
                                }
                                val onLongClick = remember(row.track) {
                                    { miniTrayTrack = row.track }
                                }
                                val onMenuClick = remember(row.track) {
                                    { menuTrack = row.track }
                                }
                                TrackRow(
                                    track = row.track,
                                    badge = row.badge,
                                    isPlaying = uiState.nowPlaying != null,
                                    onClick = onTrackClick,
                                    onLongClick = onLongClick,
                                    onMenuClick = onMenuClick,
                                )
                            }
                            is HomeRow.Album -> {
                                val onAlbumClick = remember(row.album.artist, row.album.name) {
                                    {
                                        artistAlbumNavigator.openAlbum(
                                            title = row.album.name,
                                            artist = row.album.artist,
                                            browseId = "",
                                        )
                                    }
                                }
                                AlbumRow(
                                    album = row.album,
                                    badge = row.badge,
                                    onClick = onAlbumClick,
                                )
                            }
                            is HomeRow.Artist -> {
                                val onArtistClick = remember(row.artist.name) {
                                    {
                                        artistAlbumNavigator.openArtist(
                                            name = row.artist.name,
                                            browseId = "",
                                        )
                                    }
                                }
                                ArtistRow(
                                    artist = row.artist,
                                    badge = row.badge,
                                    onClick = onArtistClick,
                                )
                            }
                        }
                    }

                    if (rows.isEmpty()) {
                        item(key = "empty_state", contentType = "empty") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No listening activity yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }
                    }
                }

                miniTrayTrack?.let { track ->
                    TrackMiniTraySheet(
                        data = TrackMiniTrayData(
                            title = track.name,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            sourceLabel = "Home",
                            onPlay = {
                                val startIdx = latestPlaybackQueue.indexOfFirst {
                                    it.title == track.name && it.artist == track.artist
                                }
                                if (startIdx >= 0) {
                                    musicPlayer.playQueue(
                                        tracks = latestPlaybackQueue,
                                        startIndex = startIdx,
                                        sourceLabel = "Home",
                                    )
                                }
                            },
                        ),
                        onDismiss = { miniTrayTrack = null },
                    )
                }
            }
        }
    }

    menuTrack?.let { track ->
        TrackContextMenuSheet(
            target = TrackMenuTarget.Track(track.name, track.artist, track.artworkUrl.orEmpty()),
            capabilities = TrackMenuCapabilities(
                showCopyActions = true,
                showDeleteScrobble = !uiState.isViewingFriend,
            ),
            playbackSourceLabel = "Home",
            onDismiss = { menuTrack = null },
        )
    }

    if (showProfileSheet) {
        NeonPopupCardSheet(
            onDismissRequest = { showProfileSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .clickable {
                            hapticManager.click()
                            dpPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileAvatar(
                        avatarUrl = uiState.stats?.avatarUrl,
                        customPhotoUri = uiState.customPhotoUri,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Change photo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = uiState.username.ifBlank { "Neon Song User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = if (uiState.customPhotoUri.isNotBlank()) {
                        "Tap avatar to change profile picture"
                    } else {
                        "Tap avatar to choose photo from gallery"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        hapticManager.click()
                        dpPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = ActionCardShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (uiState.customPhotoUri.isNotBlank()) "Change Profile Picture" else "Add Profile Picture",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (uiState.customPhotoUri.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            hapticManager.click()
                            viewModel.setCustomPhotoUri(null, context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Remove Custom Picture", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidGlassSurface(
                            shape = ActionCardShape,
                            borderWidth = 0.8.dp,
                            interactive = true,
                            onClick = {
                                hapticManager.click()
                                showProfileSheet = false
                                onOpenSettings()
                            },
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Open App Settings",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimer(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "--"
    val d = totalSeconds / 86400
    val h = (totalSeconds % 86400) / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m ${s}s"
    }
}

private suspend fun snapshotFlowNearEnd(listState: LazyListState, onNearEnd: () -> Unit) {
    snapshotFlow {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && lastVisible >= total - 5) total else -1
    }
        .distinctUntilChanged()
        .filter { it > 0 }
        .collect { onNearEnd() }
}

@Composable
private fun AppleHomeHeader(
    title: String,
    subtitle: String? = null,
    isViewingFriend: Boolean = false,
    avatarUrl: String?,
    customPhotoUri: String?,
    viewModel: HomeViewModel,
    onSearchClick: () -> Unit,
    onReturnToOwnProfile: () -> Unit,
    onAvatarClick: () -> Unit,
) {
    val avatarInteraction = remember { MutableInteractionSource() }
    val isAvatarPressed by avatarInteraction.collectIsPressedAsState()
    val avatarScaleState = animateFloatAsState(
        targetValue = if (isAvatarPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "avatarScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isViewingFriend) {
                LiquidGlassChip(
                    selected = true,
                    onClick = onReturnToOwnProfile,
                    accentTint = MaterialTheme.colorScheme.primary,
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "My Profile",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Return to own profile",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            } else {
                LiveListenTimer(viewModel)
            }

            LiquidGlassIconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                contentDescription = "Search",
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val s = avatarScaleState.value
                        scaleX = s
                        scaleY = s
                    }
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = avatarInteraction,
                        indication = null,
                        onClick = onAvatarClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ProfileAvatar(
                    avatarUrl = avatarUrl,
                    customPhotoUri = customPhotoUri,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiveListenTimer(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listenElapsedSeconds by viewModel.listenElapsedSeconds.collectAsStateWithLifecycle()
    val totalSeconds = (uiState.stats?.timerBaseSeconds ?: 0L) + listenElapsedSeconds.toLong()
    val isPlaying = uiState.nowPlaying != null

    Box(
        modifier = Modifier.liquidGlassSurface(
            shape = BadgePillShape,
            borderWidth = 0.8.dp,
            accentTint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                Icons.Filled.Headset,
                contentDescription = "Listening time",
                modifier = Modifier.size(15.dp),
                tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                formatTimer(totalSeconds),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AppleSectionHeader(
    title: String,
    onChevronClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .then(if (onChevronClick != null) Modifier.clickable(onClick = onChevronClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun AppleRecentlyPlayedCarousel(
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(tracks, key = { "recent_${it.key}" }) { track ->
            AppleRecentlyPlayedCard(
                track = track,
                onClick = { onTrackClick(track) },
            )
        }
    }
}

@Composable
private fun AppleRecentlyPlayedCard(
    track: HomeTrack,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = if (isPressed) ExpressiveMotion.FloatPressSpring else ExpressiveMotion.FloatReleaseSpring,
        label = "recentCardScale",
    )
    val cardWidth = rememberAdaptiveCardWidth(
        smallPhone = 145.dp,
        standardPhone = 160.dp,
        largePhone = 175.dp,
        tablet = 190.dp,
    )

    Column(
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = 10.dp,
                    shape = RowShape,
                    ambientColor = Color.Black.copy(alpha = 0.20f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                    clip = false,
                )
                .clip(RowShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ArtworkImage(
                name = track.name,
                artist = track.artist,
                embeddedUrl = track.artworkUrl,
                fallbackIcon = Icons.Filled.MusicNote,
                decodeSizePx = 240,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = ArtistHelper.primaryArtist(track.artist),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ProfileAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    customPhotoUri: String? = null,
) {
    val photo = customPhotoUri?.takeIf { it.isNotBlank() } ?: avatarUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.28f), shape = CircleShape)
            .padding(1.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF2C2C2E),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!photo.isNullOrBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(photo, context) {
                    val cleanPath = photo.substringBefore('?')
                    val dataModel: Any = when {
                        cleanPath.startsWith("http://") ||
                            cleanPath.startsWith("https://") ||
                            cleanPath.startsWith("content://") -> photo
                        cleanPath.startsWith("file://") -> {
                            val pathOnly = Uri.parse(cleanPath).path
                            if (pathOnly != null) File(pathOnly) else File(cleanPath.removePrefix("file://"))
                        }
                        else -> File(cleanPath)
                    }
                    ImageRequest.Builder(context)
                        .data(dataModel)
                        .memoryCacheKey(photo)
                        .diskCacheKey(photo)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Profile avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalStatsBanner(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = ActionCardShape,
                borderWidth = 0.8.dp,
                interactive = true,
                onClick = onOpenSettings,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Connect Last.fm in Settings to sync global scrobbles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onOpenDiscover: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenDownloads: () -> Unit,
    isViewingFriend: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickActionChip(
            icon = Icons.Filled.Explore,
            label = "Discover",
            accent = MaterialTheme.colorScheme.primary,
            onClick = {
                hapticManager.click()
                onOpenDiscover()
            },
            modifier = Modifier.weight(1f),
        )
        QuickActionChip(
            icon = Icons.Filled.People,
            label = "Friends",
            accent = Color(0xFF0A84FF),
            onClick = {
                hapticManager.click()
                onOpenFriends()
            },
            modifier = Modifier.weight(1f),
        )
        QuickActionChip(
            icon = if (isViewingFriend) Icons.Filled.PlayArrow else Icons.Filled.Download,
            label = if (isViewingFriend) "Friend Mix" else "Saved",
            accent = Color(0xFF30D158),
            onClick = {
                hapticManager.click()
                onOpenDownloads()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(62.dp)
            .liquidGlassSurface(
                shape = ActionCardShape,
                borderWidth = 0.8.dp,
                accentTint = accent,
                interactive = true,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = accent,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatsCard(
    scrobbles: Long,
    trackCount: Long,
    artistCount: Long,
    albumCount: Long,
    topTags: List<String> = emptyList(),
    onOpenGenres: () -> Unit,
    hasAnimatedStats: Boolean,
    onStatsAnimated: () -> Unit,
    modifier: Modifier = Modifier,
    headlineLabel: String = "Total Scrobbles",
) {
    var visible by rememberSaveable { mutableStateOf(hasAnimatedStats) }
    LaunchedEffect(Unit) {
        if (!hasAnimatedStats) {
            visible = true
            delay(700L)
            onStatsAnimated()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(320)) + expandVertically(animationSpec = tween(320)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = StatsCardShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                    clip = false,
                )
                .liquidGlassSurface(
                    shape = StatsCardShape,
                    borderWidth = 0.8.dp,
                    accentTint = MaterialTheme.colorScheme.primary,
                )
                .padding(16.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            headlineLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        AnimatedStatText(
                            target = scrobbles,
                            shouldAnimate = !hasAnimatedStats,
                            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    LiquidGlassChip(
                        selected = true,
                        onClick = onOpenGenres,
                        accentTint = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Genres",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }

                if (topTags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        topTags.forEach { tag ->
                            Surface(
                                onClick = onOpenGenres,
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.40f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatPill("Tracks", trackCount, shouldAnimate = !hasAnimatedStats, modifier = Modifier.weight(1f))
                    StatPill("Artists", artistCount, shouldAnimate = !hasAnimatedStats, modifier = Modifier.weight(1f))
                    StatPill("Albums", albumCount, shouldAnimate = !hasAnimatedStats, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * FIXED: Non-bouncy spring so stat numbers never overshoot and count backwards.
 */
@Composable
private fun rememberAnimatedCount(target: Long, shouldAnimate: Boolean = true): Long {
    val animated = remember { Animatable(if (shouldAnimate) 0f else target.toFloat()) }
    LaunchedEffect(target, shouldAnimate) {
        if (shouldAnimate) {
            animated.animateTo(
                targetValue = target.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            animated.snapTo(target.toFloat())
        }
    }
    return animated.value.toLong()
}

@Composable
private fun AnimatedStatText(
    target: Long,
    shouldAnimate: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight,
    color: Color,
    maxLines: Int = 1,
) {
    val animatedValue = rememberAnimatedCount(target, shouldAnimate)
    Text(
        text = formatCount(animatedValue),
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
    )
}

@Composable
private fun StatPill(label: String, value: Long, shouldAnimate: Boolean = true, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.40f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedStatText(
                target = value,
                shouldAnimate = shouldAnimate,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCount(value: Long): String = if (value <= 0) "—" else "%,d".format(value)

@Composable
private fun MixHeader(
    sortMode: HomeSortMode,
    onSortModeChange: (HomeSortMode) -> Unit,
    onPlayAll: (() -> Unit)? = null,
) {
    val hapticManager = LocalHapticManager.current
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .liquidGlassSurface(
                shape = MixHeaderShape,
                borderWidth = 0.85.dp,
                alphaScale = 1.25f,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = sortSectionHeading(sortMode),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (onPlayAll != null) {
                    Surface(
                        onClick = {
                            hapticManager.click()
                            onPlayAll()
                        },
                        shape = BadgePillShape,
                        color = primary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, "Play all", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            Text("Play All", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSortMode.entries.forEach { mode ->
                    val isSelected = sortMode == mode
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                        animationSpec = tween(200),
                        label = "sortPillBg",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(200),
                        label = "sortPillContent",
                    )
                    Surface(
                        onClick = {
                            hapticManager.click()
                            onSortModeChange(mode)
                        },
                        shape = BadgePillShape,
                        color = bgColor,
                        border = BorderStroke(
                            0.7.dp,
                            if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(iconForSortMode(mode), null, Modifier.size(15.dp), tint = contentColor)
                            Text(
                                text = sortModeLabel(mode),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sortSectionHeading(mode: HomeSortMode): String = when (mode) {
    HomeSortMode.RECENT -> "Recent Activity"
    HomeSortMode.MOST_PLAYED -> "Most Played"
    HomeSortMode.LAST_7_DAYS -> "Top Last 7 Days"
    HomeSortMode.LAST_30_DAYS -> "Top Last 30 Days"
    HomeSortMode.TOP_ARTISTS -> "Top Artists"
    HomeSortMode.TOP_ALBUMS -> "Top Albums"
}

private fun iconForSortMode(mode: HomeSortMode): ImageVector = when (mode) {
    HomeSortMode.RECENT -> Icons.Filled.Schedule
    HomeSortMode.MOST_PLAYED -> Icons.Filled.BarChart
    HomeSortMode.TOP_ALBUMS -> Icons.Filled.Album
    HomeSortMode.TOP_ARTISTS -> Icons.Filled.People
    HomeSortMode.LAST_7_DAYS -> Icons.Filled.DateRange
    HomeSortMode.LAST_30_DAYS -> Icons.Filled.CalendarMonth
}

private fun sortModeLabel(mode: HomeSortMode): String = when (mode) {
    HomeSortMode.RECENT -> "Recent"
    HomeSortMode.MOST_PLAYED -> "Most Played"
    HomeSortMode.TOP_ALBUMS -> "Top Albums"
    HomeSortMode.TOP_ARTISTS -> "Top Artists"
    HomeSortMode.LAST_7_DAYS -> "Last 7 Days"
    HomeSortMode.LAST_30_DAYS -> "Last 30 Days"
}

private fun HomeRow.stableKey(index: Int): String = "${rowId}_$index"

private val HomeRow.type: String
    get() = when (this) {
        is HomeRow.DateHeader -> "date"
        is HomeRow.Track -> "track"
        is HomeRow.Album -> "album"
        is HomeRow.Artist -> "artist"
    }

@Composable
private fun DateHeaderRow(label: String) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val lineBrush = remember(outlineColor) {
        Brush.horizontalGradient(
            listOf(outlineColor.copy(alpha = 0.45f), Color.Transparent),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(lineBrush),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackRow(
    track: HomeTrack,
    badge: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val isNowPlaying = track.isNowPlaying
    val isActuallyPlaying = isNowPlaying && isPlaying
    val secondaryTextColor = if (isNowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val displayArtist = remember(track.artist) { ArtistHelper.primaryArtist(track.artist) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidGlassSurface(
                shape = RowShape,
                borderWidth = 0.dp,
                accentTint = if (isNowPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
            )
            .clip(RowShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ThumbShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                ArtworkImage(
                    name = track.name,
                    artist = displayArtist,
                    embeddedUrl = track.artworkUrl,
                    fallbackIcon = if (isNowPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                    decodeSizePx = 160,
                    crossfade = false,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isNowPlaying) {
                    PlayingWaveBars(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(22.dp, 16.dp),
                        containerColor = Color.Transparent,
                        waveColor = MaterialTheme.colorScheme.primary,
                        isPlaying = isActuallyPlaying,
                        animated = false,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        fontWeight = if (isNowPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = if (isNowPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayArtist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        displayArtist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isNowPlaying) {
                NowPlayingPill(isPlaying = isActuallyPlaying)
            }
            if (badge != null) {
                RowBadgePill(badge)
            }
            OverflowMenuButton(onClick = onMenuClick)
        }
    }
}

@Composable
private fun NowPlayingPill(isPlaying: Boolean) {
    val dotAlpha = if (isPlaying) 1f else 0.65f
    Surface(
        shape = BadgePillShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha), CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "LIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RowBadgePill(badge: String) {
    Surface(
        shape = BadgePillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.50f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Text(
            badge,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AlbumRow(
    album: HomeAlbum,
    badge: String?,
    onClick: () -> Unit,
) {
    val displayArtist = remember(album.artist) { ArtistHelper.primaryArtist(album.artist) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidGlassSurface(
                shape = RowShape,
                borderWidth = 0.dp,
            )
            .clip(RowShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(ThumbShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                ArtworkImage(
                    name = album.name,
                    artist = displayArtist,
                    embeddedUrl = album.artworkUrl,
                    fallbackIcon = Icons.Filled.Album,
                    decodeSizePx = 160,
                    crossfade = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayArtist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        displayArtist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                RowBadgePill(badge)
            }
        }
    }
}

@Composable
private fun ArtistRow(
    artist: HomeArtistItem,
    badge: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .liquidGlassSurface(
                shape = RowShape,
                borderWidth = 0.dp,
            )
            .clip(RowShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                ArtworkImage(
                    name = artist.name,
                    artist = artist.name,
                    embeddedUrl = artist.artworkUrl,
                    fallbackIcon = Icons.Filled.People,
                    decodeSizePx = 160,
                    crossfade = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                RowBadgePill(badge)
            }
        }
    }
}

@HiltViewModel
class ArtistAlbumNavBridgeHome @Inject constructor(
    val navigator: ArtistAlbumNavigator,
) : ViewModel()
