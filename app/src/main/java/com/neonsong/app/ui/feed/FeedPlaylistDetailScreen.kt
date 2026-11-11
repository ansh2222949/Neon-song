package com.neonsong.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.music.YouTubeMusicTrack
import com.neonsong.app.data.music.YouTubePlaylistResult
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ExpressiveGroupTrackRow
import com.neonsong.app.ui.common.ExpressiveHeader
import com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.common.GroupGap
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.TrackMiniTrayData
import com.neonsong.app.ui.common.TrackMiniTraySheet
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.groupPositionFor
import com.neonsong.app.ui.common.rememberExpressiveHeaderBackdrop
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.PlayingWaveBars
import com.neonsong.app.ui.theme.GlassActionButton as Button
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import com.neonsong.app.ui.theme.GlassTonalActionButton as FilledTonalButton
import com.neonsong.app.ui.theme.LayerBackdrop
import com.neonsong.app.ui.theme.liquidGlassSource

@Composable
fun FeedPlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    viewModel: FeedPlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val headerBackdrop = rememberExpressiveHeaderBackdrop()
    val musicPlayer = LocalMusicPlayer.current
    val chromeState by musicPlayer.chromeState.collectAsStateWithLifecycle()

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerHeight = if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() } else 64.dp

    LaunchedEffect(playlistId) {
        viewModel.load(playlistId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 860.dp),
        ) {
            val playlist = (state as? FeedPlaylistDetailUiState.Success)?.playlist

            when (val current = state) {
                FeedPlaylistDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = headerHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveLoadingIndicator(message = "Opening playlist…")
                    }
                }

                is FeedPlaylistDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = headerHeight)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        FilledTonalButton(onClick = { viewModel.load(playlistId) }) {
                            Text("Try again")
                        }
                    }
                }

                is FeedPlaylistDetailUiState.Success -> {
                    PlaylistContent(
                        playlist = current.playlist,
                        onPlay = viewModel::playFrom,
                        onShuffle = viewModel::shuffle,
                        onSave = viewModel::saveToLibrary,
                        onRetry = { viewModel.load(playlistId, force = true) },
                        isSaving = current.isSaving,
                        savedToLibrary = current.savedToLibrary,
                        saveError = current.saveError,
                        isLoadingMore = current.isLoadingMore,
                        loadError = current.loadError,
                        backdrop = headerBackdrop,
                        headerHeight = headerHeight,
                        currentPlayingVideoId = chromeState.current?.videoId,
                        isPlaying = chromeState.isPlaying,
                    )
                }
            }

            ExpressiveHeader(
                title = playlist?.title?.takeIf(String::isNotBlank) ?: "Playlist",
                subtitle = playlist?.author ?: "YouTube Music",
                onBack = onBack,
                backdrop = headerBackdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
                    .onSizeChanged { headerHeightPx = it.height },
            )
        }
    }
}

@Composable
private fun PlaylistContent(
    playlist: YouTubePlaylistResult,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    isSaving: Boolean,
    savedToLibrary: Boolean,
    saveError: String?,
    isLoadingMore: Boolean,
    loadError: String?,
    backdrop: LayerBackdrop?,
    headerHeight: Dp,
    currentPlayingVideoId: String?,
    isPlaying: Boolean,
) {
    var menuTrack by remember { mutableStateOf<YouTubeMusicTrack?>(null) }
    var miniTrayIndex by remember { mutableStateOf<Int?>(null) }

    val totalDurationSeconds = remember(playlist.tracks) {
        playlist.tracks.mapNotNull { it.durationSeconds }.sum()
    }
    val totalDurationLabel = remember(totalDurationSeconds) {
        if (totalDurationSeconds > 0) formatTotalDuration(totalDurationSeconds) else null
    }

    val subtitleInfo = remember(playlist.author, playlist.tracks.size, totalDurationLabel) {
        listOfNotNull(
            playlist.author?.takeIf(String::isNotBlank),
            "${playlist.tracks.size} tracks",
            totalDurationLabel,
        ).joinToString(" · ")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .liquidGlassSource(backdrop),
        contentPadding = PaddingValues(
            top = headerHeight + 12.dp,
            bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GroupGap),
    ) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(196.dp),
                ) {
                    ArtworkImage(
                        name = playlist.title,
                        artist = playlist.author.orEmpty(),
                        embeddedUrl = playlist.artworkUrl ?: playlist.tracks.firstOrNull()?.artworkUrl,
                        fallbackIcon = if (playlist.id == "yt_liked") Icons.Filled.Favorite else Icons.Filled.MusicNote,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = playlist.title.ifBlank { "Playlist" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { onPlay(0) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    FilledTonalButton(
                        onClick = onShuffle,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = onSave,
                    enabled = !isSaving && !savedToLibrary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (savedToLibrary) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Saved to library")
                    } else if (isSaving) {
                        Text("Saving…")
                    } else {
                        Text("Save to library")
                    }
                }
                saveError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (isLoadingMore) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            ExpressiveInlineLoadingIndicator(size = 18.dp, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Loading remaining tracks…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (loadError != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Playlist incomplete · Tap to retry",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "TRACKS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        itemsIndexed(
            items = playlist.tracks,
            key = { index, track -> "${track.videoId}:$index" },
        ) { index, track ->
            val isCurrent = track.videoId.isNotBlank() && track.videoId == currentPlayingVideoId
            ExpressiveGroupTrackRow(
                title = track.title,
                subtitle = track.artistAndAlbum(),
                position = groupPositionFor(index, playlist.tracks.size),
                onClick = { onPlay(index) },
                onLongClick = { miniTrayIndex = index },
                modifier = Modifier.padding(horizontal = 16.dp),
                leading = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10131E),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ArtworkImage(
                                name = track.title,
                                artist = track.artist,
                                embeddedUrl = track.artworkUrl,
                                fallbackIcon = Icons.Filled.MusicNote,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            if (isCurrent && isPlaying) {
                                PlayingWaveBars(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(24.dp, 18.dp),
                                )
                            }
                        }
                    }
                },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        track.durationSeconds?.let { duration ->
                            Text(
                                text = duration.toDurationLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { menuTrack = track },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Track options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
            )
        }
    }

    menuTrack?.let { track ->
        TrackContextMenuSheet(
            target = TrackMenuTarget.Track(track.title, track.artist, ""),
            capabilities = TrackMenuCapabilities(showCopyActions = true, showDeleteScrobble = false),
            playableTrack = PlayableTrack(
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                videoId = track.videoId.takeIf(String::isNotBlank),
            ),
            playbackSourceLabel = playlist.title.ifBlank { "Mix" },
            onDismiss = { menuTrack = null },
        )
    }

    miniTrayIndex?.let { trayIndex ->
        playlist.tracks.getOrNull(trayIndex)?.let { track ->
            TrackMiniTraySheet(
                data = TrackMiniTrayData(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl,
                    videoId = track.videoId.takeIf(String::isNotBlank),
                    sourceLabel = playlist.title.ifBlank { "Mix" },
                    onPlay = { onPlay(trayIndex) },
                ),
                onDismiss = { miniTrayIndex = null },
            )
        }
    }
}

private fun formatTotalDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours} hr ${minutes} min"
    } else {
        "${minutes} min"
    }
}

private fun YouTubeMusicTrack.artistAndAlbum(): String =
    listOfNotNull(artist.takeIf(String::isNotBlank), album?.takeIf(String::isNotBlank))
        .joinToString(" · ")
        .ifBlank { "YouTube Music" }

private fun Int.toDurationLabel(): String = "%d:%02d".format(this / 60, this % 60)
