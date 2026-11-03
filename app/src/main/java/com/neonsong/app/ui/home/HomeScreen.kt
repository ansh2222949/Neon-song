package com.neonsong.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.theme.GlassActionButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.neonsong.app.ui.common.OverflowMenuButton
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.TrackMiniTrayData
import com.neonsong.app.ui.common.TrackMiniTraySheet
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.safeHorizontalContentPadding
import com.neonsong.app.ui.navigation.ArtistAlbumNavigator
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.PlayingWaveBars
import com.neonsong.app.ui.shell.FloatingNavDefaults
import com.neonsong.app.ui.theme.ArtworkShape
import com.neonsong.app.ui.theme.BadgePillShape
import com.neonsong.app.ui.theme.ExpressiveHeroShape
import com.neonsong.app.ui.theme.HeroInnerShape
import com.neonsong.app.ui.theme.NowPlayingCardShape
import com.neonsong.app.ui.theme.StatPillShape
import com.neonsong.app.ui.theme.TrackRowShape
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.liquidTouchEffect
import com.neonsong.app.ui.theme.rememberLiquidTouchInteraction
import com.neonsong.app.ui.common.liquidHeaderEntrance
import com.neonsong.app.util.ArtistHelper
import com.neonsong.app.util.LocalHapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val TrackThumbnailShape = RoundedCornerShape(12.dp)
private val DropdownMenuShape = RoundedCornerShape(24.dp)
private val ActiveSortItemShape = RoundedCornerShape(14.dp)
private val ActionCardShape = RoundedCornerShape(18.dp)

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
        contract = ActivityResultContracts.PickVisualMedia()
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
            }
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
            else -> "Neon Song User"
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
        containerColor = Color.Black,
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
                val topPicksTracks = remember(rows) {
                    rows.filterIsInstance<HomeRow.Track>().map { it.track }.take(6)
                }
                val recentlyPlayedTracks = remember(rows) {
                    rows.filterIsInstance<HomeRow.Track>().map { it.track }.drop(2).take(10)
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = FloatingNavDefaults.contentBottomPadding() + LocalMiniPlayerScrollClearance.current,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    // 1. 1:1 Apple Music Stats Header (Large 34sp Title + Top-Right Circular Avatar)
                    item(key = "apple_home_header", contentType = "header") {
                        AppleHomeHeader(
                            title = "Stats",
                            avatarUrl = uiState.stats?.avatarUrl,
                            customPhotoUri = if (uiState.isViewingFriend) null else uiState.customPhotoUri,
                            onAvatarClick = {
                                hapticManager.click()
                                if (uiState.isViewingFriend) {
                                    onOpenFriends()
                                } else {
                                    showProfileSheet = true
                                }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // 2. Apple Music Section: Recently Played (156dp Square Cards)
                    if (recentlyPlayedTracks.isNotEmpty()) {
                        item(key = "apple_recently_played_section", contentType = "section") {
                            AppleSectionHeader(
                                title = "Recently Played",
                                onChevronClick = {
                                    if (latestPlaybackQueue.isNotEmpty()) {
                                        musicPlayer.playQueue(
                                            tracks = latestPlaybackQueue,
                                            startIndex = 0,
                                            sourceLabel = "Recently Played",
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            AppleRecentlyPlayedCarousel(
                                tracks = recentlyPlayedTracks,
                                onTrackClick = { track ->
                                    val targetIdx = latestPlaybackQueue.indexOfFirst {
                                        (track.videoId != null && it.videoId == track.videoId) ||
                                        (it.title == track.name && it.artist == track.artist)
                                    }
                                    if (targetIdx >= 0 && latestPlaybackQueue.isNotEmpty()) {
                                        musicPlayer.playQueue(
                                            tracks = latestPlaybackQueue,
                                            startIndex = targetIdx,
                                            sourceLabel = "Recently Played",
                                        )
                                    } else {
                                        musicPlayer.play(
                                            PlayableTrack(
                                                title = track.name,
                                                artist = track.artist,
                                                artworkUrl = track.artworkUrl,
                                                videoId = track.videoId,
                                            ),
                                            sourceLabel = "Recently Played",
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    // Optional Error Banner
                    if (uiState.error != null) {
                        item(key = "home_error_banner", contentType = "error") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = uiState.error ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = viewModel::refresh) {
                                        Text("Retry", color = MaterialTheme.colorScheme.error)
                                    }
                                    TextButton(onClick = viewModel::dismissError) {
                                        Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Quick Actions Row
                    item(key = "quick_actions", contentType = "actions") {
                        QuickActionsRow(
                            onOpenDiscover = onOpenDiscover,
                            onOpenFriends = onOpenFriends,
                            onOpenDownloads = onOpenDownloads,
                            isViewingFriend = uiState.isViewingFriend,
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    // 3. Optional Local Mode Banner
                    if (uiState.isLocalStatsMode && !uiState.isViewingFriend) {
                        item(key = "local_stats_banner", contentType = "banner") {
                            LocalStatsBanner(
                                onOpenSettings = onOpenSettings,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }

                    // 4. Bento Hero Stats Card
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
                            Spacer(Modifier.height(18.dp))
                        }
                    }

                    // 5. Sticky Mix & Sort Header
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

                    // 6. Track / Artist / Album Feed
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
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "No listening activity yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .clickable {
                            hapticManager.click()
                            dpPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
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
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
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
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
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
                        if (uiState.customPhotoUri.isNotBlank()) "Change Profile Picture (DP)" else "Add Profile Picture (DP)",
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

                Surface(
                    onClick = {
                        hapticManager.click()
                        showProfileSheet = false
                        onOpenSettings()
                    },
                    shape = ActionCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
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
    avatarUrl: String?,
    customPhotoUri: String?,
    onAvatarClick: () -> Unit,
    title: String = "Stats",
) {
    val avatarInteraction = remember { MutableInteractionSource() }
    val isAvatarPressed by avatarInteraction.collectIsPressedAsState()
    val avatarScale by animateFloatAsState(
        targetValue = if (isAvatarPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 700f),
        label = "avatarScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            ),
            color = Color.White,
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                }
                .size(38.dp)
                .clip(CircleShape)
                .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
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
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            ),
            color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = Color(0xFF8E8E93),
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun AppleTopPicksCarousel(
    tracks: List<HomeTrack>,
    onTrackClick: (HomeTrack) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(tracks, key = { "top_pick_${it.key}" }) { track ->
            AppleTopPickCard(
                track = track,
                onClick = { onTrackClick(track) },
            )
        }
    }
}

@Composable
private fun AppleTopPickCard(
    track: HomeTrack,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1200f),
        label = "topPickCardScale",
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1C1C1E),
        border = BorderStroke(0.5.dp, Color(0x26FFFFFF)),
        modifier = Modifier
            .width(260.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "MADE FOR YOU",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                color = Color(0xFF8E8E93),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2C2E)),
            ) {
                ArtworkImage(
                    name = track.name,
                    artist = track.artist,
                    embeddedUrl = track.artworkUrl,
                    fallbackIcon = Icons.Filled.MusicNote,
                    decodeSizePx = 250,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1200f),
        label = "recentCardScale",
    )

    Column(
        modifier = Modifier
            .width(156.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E)),
        ) {
            ArtworkImage(
                name = track.name,
                artist = track.artist,
                embeddedUrl = track.artworkUrl,
                fallbackIcon = Icons.Filled.MusicNote,
                decodeSizePx = 200,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
            ),
            color = Color(0xFF8E8E93),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Redesigned unified header combining Avatar, Greeting, Active User / Friend Switcher,
 * and Live Listen Timer into a single clean row.
 */
@Composable
private fun RedesignedTopBar(
    displayName: String,
    isViewingFriend: Boolean,
    avatarUrl: String?,
    customPhotoUri: String?,
    viewModel: HomeViewModel,
    onSearchClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
) {
    var currentHour by remember { mutableStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentHour = LocalTime.now().hour
        }
    }
    val greeting = when (currentHour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    val avatarInteraction = remember { MutableInteractionSource() }
    val isAvatarPressed by avatarInteraction.collectIsPressedAsState()
    val avatarScale by animateFloatAsState(
        targetValue = if (isAvatarPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 700f),
        label = "avatarScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(24.dp),
                borderWidth = 1.dp,
                accentTint = MaterialTheme.colorScheme.primary,
            )
            .liquidHeaderEntrance()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = avatarInteraction,
                        indication = null,
                        onClick = onAvatarClick,
                    )
            ) {
                ProfileAvatar(
                    avatarUrl = avatarUrl,
                    customPhotoUri = customPhotoUri,
                    modifier = Modifier.size(46.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Profile & Settings",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onSwitchProfileClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (isViewingFriend) "Viewing friend's stats" else greeting,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isViewingFriend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isViewingFriend) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.People,
                            contentDescription = "Switch profile",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        LiquidGlassIconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(40.dp),
            accentTint = MaterialTheme.colorScheme.primary,
            contentDescription = "Search",
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }

        if (isViewingFriend) {
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = {
                    viewModel.returnToOwnProfile()
                },
                shape = BadgePillShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "My Profile",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Return to own profile",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        LiveListenTimer(viewModel)
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
            borderWidth = 1.dp,
            accentTint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Icon(
                Icons.Filled.Headset,
                contentDescription = "Estimated lifetime listening time",
                modifier = Modifier.size(16.dp),
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                formatTimer(totalSeconds),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
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
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m ${s}s"
        else -> "${m}m ${s}s"
    }
}

@Composable
fun ProfileAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    customPhotoUri: String? = null,
) {
    val photo = customPhotoUri?.takeIf { it.isNotBlank() } ?: avatarUrl?.takeIf { it.isNotBlank() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val borderBrush = remember(primaryColor, tertiaryColor) {
        Brush.sweepGradient(listOf(primaryColor, tertiaryColor, primaryColor))
    }

    Box(
        modifier = modifier
            .border(width = 1.5.dp, brush = borderBrush, shape = CircleShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!photo.isNullOrBlank()) {
                val context = LocalContext.current
                val imageRequest = remember(photo, context) {
                    val cleanPath = photo.substringBefore('?')
                    val dataModel: Any = when {
                        cleanPath.startsWith("http://") ||
                        cleanPath.startsWith("https://") ||
                        cleanPath.startsWith("content://") -> {
                            photo
                        }
                        cleanPath.startsWith("file://") -> {
                            val pathOnly = Uri.parse(cleanPath).path
                            if (pathOnly != null) File(pathOnly) else File(cleanPath.removePrefix("file://"))
                        }
                        else -> {
                            File(cleanPath)
                        }
                    }

                    val cacheKey = photo

                    ImageRequest.Builder(context)
                        .data(dataModel)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
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
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
    Surface(
        shape = ActionCardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
        onClick = onOpenSettings,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Connect Last.fm in Settings to sync global scrobbles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickActionChip(
            icon = Icons.Filled.Explore,
            label = "Discover",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = {
                hapticManager.click()
                onOpenDiscover()
            },
            modifier = Modifier.weight(1f),
        )
        QuickActionChip(
            icon = Icons.Filled.People,
            label = "Friends",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = {
                hapticManager.click()
                onOpenFriends()
            },
            modifier = Modifier.weight(1f),
        )
        QuickActionChip(
            icon = if (isViewingFriend) Icons.Filled.PlayArrow else Icons.Filled.Download,
            label = if (isViewingFriend) "Friend Mix" else "Saved",
            containerColor = if (isViewingFriend) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (isViewingFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val chipScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 700f),
        label = "chipScale",
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ActionCardShape,
        color = containerColor,
        tonalElevation = 2.dp,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 7.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(contentColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Redesigned Bento-style Hero Stats Card with left-aligned primary counter
 * and a dedicated "Genres" action pill on the right.
 */
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
            delay(900L)
            onStatsAnimated()
        }
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val backgroundBrush = remember(primaryContainer, tertiaryContainer) {
        Brush.linearGradient(
            colors = listOf(
                primaryContainer,
                tertiaryContainer.copy(alpha = 0.65f),
                primaryContainer,
            )
        )
    }
    val borderBrush = remember(primary, tertiary) {
        Brush.sweepGradient(
            listOf(
                primary.copy(alpha = 0.45f),
                tertiary.copy(alpha = 0.3f),
                primary.copy(alpha = 0.45f),
            )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
        modifier = modifier,
    ) {
        Surface(
            shape = ExpressiveHeroShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(HeroInnerShape)
                        .background(backgroundBrush)
                        .border(width = 0.5.dp, brush = borderBrush, shape = HeroInnerShape)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    headlineLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    formatCount(rememberAnimatedCount(scrobbles, shouldAnimate = !hasAnimatedStats)),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }

                            Surface(
                                onClick = onOpenGenres,
                                shape = BadgePillShape,
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 4.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Genres",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "View genres",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        if (topTags.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
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
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

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

@Composable
private fun rememberAnimatedCount(target: Long, shouldAnimate: Boolean = true): Long {
    val animated = remember { Animatable(if (shouldAnimate) 0f else target.toFloat()) }
    LaunchedEffect(target, shouldAnimate) {
        if (shouldAnimate) {
            animated.animateTo(
                targetValue = target.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessVeryLow,
                ),
            )
        } else {
            animated.snapTo(target.toFloat())
        }
    }
    return animated.value.toLong()
}

@Composable
private fun StatPill(label: String, value: Long, shouldAnimate: Boolean = true, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = StatPillShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                formatCount(rememberAnimatedCount(value, shouldAnimate)),
                style = MaterialTheme.typography.titleLarge,
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
    val haptics = LocalHapticFeedback.current

    Surface(
        color = Color.Black,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = sortSectionHeading(sortMode),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = Color.White,
                )

                if (onPlayAll != null) {
                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayAll()
                        },
                        shape = BadgePillShape,
                        color = Color(0xFFFA2D48),
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play all",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                            )
                            Text(
                                text = "Play All",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Horizontally scrollable filter pills with smooth spring feedback
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeSortMode.entries.forEach { mode ->
                    val isSelected = sortMode == mode
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1600f),
                        label = "pillScale_${mode.name}",
                    )

                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSortModeChange(mode)
                        },
                        interactionSource = interactionSource,
                        shape = BadgePillShape,
                        color = if (isSelected) {
                            Color.White
                        } else {
                            Color(0xFF1C1C1E)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, Color.White)
                        } else {
                            BorderStroke(0.5.dp, Color(0x33FFFFFF))
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                iconForSortMode(mode),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isSelected) {
                                    Color.Black
                                } else {
                                    Color(0xFF8E8E93)
                                },
                            )
                            Text(
                                text = sortModeLabel(mode),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) {
                                    Color.Black
                                } else {
                                    Color.White
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = Color(0x33545458),
                thickness = 0.5.dp,
            )
        }
    }
}

private fun sortSectionHeading(mode: HomeSortMode): String = when (mode) {
    HomeSortMode.RECENT -> "Recently Played"
    HomeSortMode.MOST_PLAYED -> "Most Played"
    HomeSortMode.LAST_7_DAYS -> "Last 7 Days"
    HomeSortMode.LAST_30_DAYS -> "Last 30 Days"
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

@Composable
private fun SortOption(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (active) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
        modifier = if (active) {
            Modifier
                .padding(horizontal = 6.dp)
                .height(40.dp)
                .clip(ActiveSortItemShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            Modifier
                .padding(horizontal = 6.dp)
                .height(40.dp)
        },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    )
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
    val primary = MaterialTheme.colorScheme.primary
    val lineBrush = remember(primary) {
        Brush.horizontalGradient(
            listOf(primary.copy(alpha = 0.3f), Color.Transparent)
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
            fontWeight = FontWeight.SemiBold,
            color = primary.copy(alpha = 0.85f),
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val isNowPlaying = track.isNowPlaying
    val secondaryTextColor = if (isNowPlaying) {
        Color(0xFFFA2D48)
    } else {
        Color(0xFF8E8E93)
    }

    val borderStroke = remember(isNowPlaying) {
        if (isNowPlaying) {
            BorderStroke(1.dp, Color(0xFFFA2D48).copy(alpha = 0.60f))
        } else {
            BorderStroke(0.5.dp, Color(0x26FFFFFF))
        }
    }
    val displayArtist = remember(track.artist) {
        ArtistHelper.primaryArtist(track.artist)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isNowPlaying) {
            Color(0xFFFA2D48).copy(alpha = 0.14f)
        } else {
            Color(0xFF1C1C1E)
        },
        tonalElevation = 0.dp,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = 6.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2E)),
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
                        waveColor = Color(0xFFFA2D48),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayArtist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        displayArtist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                        ),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isNowPlaying) {
                NowPlayingPill()
            }
            if (badge != null) {
                RowBadgePill(badge)
            }
            OverflowMenuButton(onClick = onMenuClick)
        }
    }
}

@Composable
private fun NowPlayingPill() {
    val infiniteTransition = rememberInfiniteTransition(label = "nowPlayingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Surface(
        shape = BadgePillShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Now Playing",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun RowBadgePill(badge: String) {
    Surface(
        shape = BadgePillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            badge,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val borderStroke = remember(outlineVariant) {
        BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.18f))
    }
    val displayArtist = remember(album.artist) {
        ArtistHelper.primaryArtist(album.artist)
    }

    Surface(
        shape = TrackRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = borderStroke,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(ArtworkShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayArtist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
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
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val borderStroke = remember(outlineVariant) {
        BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.18f))
    }

    Surface(
        shape = TrackRowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = borderStroke,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
