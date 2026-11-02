@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.neonsong.app.ui.player

import androidx.compose.foundation.border
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.neonsong.app.ui.common.PredictiveBackScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.isTabletOrWideScreen
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import kotlin.math.roundToInt
import com.neonsong.app.playback.AutoMixState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.changedToUp
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import com.neonsong.app.util.LocalHapticManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.media3.common.Player
import com.neonsong.app.data.generate.GeneratedTrack
import com.neonsong.app.data.lyrics.LyricsRepository
import com.neonsong.app.data.lyrics.LyricsResult
import com.neonsong.app.data.playlist.PlaylistRepository
import com.neonsong.app.data.playlist.SavedPlaylist
import com.neonsong.app.data.playlist.LIKED_SONGS_MODE
import com.neonsong.app.data.local.LyricsAnimation
import com.neonsong.app.data.local.LyricsUiVersion
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlaybackChromeState
import com.neonsong.app.playback.formatSampleRateKHz
import com.neonsong.app.playback.isSpatialAudioCodec
import com.neonsong.app.playback.qualityBadgeLabel
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.playback.SignalPathReport
import androidx.hilt.navigation.compose.hiltViewModel
import com.neonsong.app.data.artwork.ArtworkNormalizer
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.resolve.MetadataLog
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ArtworkViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import androidx.compose.material.icons.filled.Timer
import com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.PlaylistCover
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LiquidGlassSurface
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.liquidGlass
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.Backdrop
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.IntBuffer
import kotlin.time.Duration.Companion.seconds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

enum class FullPlayerTab {
    NOW_PLAYING,
    LYRICS,
    QUEUE
}

val LocalMusicPlayer = staticCompositionLocalOf<MusicPlayer> {
    error("MusicPlayer is only available inside PlayerHost")
}

val LocalAddToPlaylist = staticCompositionLocalOf<(PlayableTrack) -> Unit> {
    error("Add-to-playlist is only available inside PlayerHost")
}

/**
 * Extra list-end clearance needed while the collapsed player overlays a
 * screen. The Material 3 bar is about 86dp tall; 88dp clears it without
 * leaving a large empty band at the end of short lists.
 */
val LocalMiniPlayerScrollClearance = staticCompositionLocalOf { 0.dp }

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: MusicPlayer,
    private val playlistRepository: PlaylistRepository,
    private val lyricsRepository: LyricsRepository,
    private val canvasRepository: com.neonsong.app.data.canvas.CanvasRepository,
    private val settingsPreferences: com.neonsong.app.data.local.SettingsPreferences,
    val navigator: com.neonsong.app.ui.navigation.ArtistAlbumNavigator,
    val genreExplorer: com.neonsong.app.ui.genres.GenreExplorer,
    val mixLauncher: com.neonsong.app.ui.generate.MixLauncher,
    private val ytMusicLibraryManager: com.neonsong.app.data.ytmusic.YtMusicLibraryManager,
    private val likedSongsManager: com.neonsong.app.data.playlist.LikedSongsManager,
    private val scrobbleRepository: com.neonsong.app.data.repository.ScrobbleRepository,
    private val downloadManager: com.neonsong.app.data.download.TrackDownloadManager,
    private val routeNavigator: com.neonsong.app.ui.navigation.AppRouteNavigator,
    private val searchRepository: dagger.Lazy<com.neonsong.app.data.search.SearchRepository>,
) : ViewModel() {
    val navEvents = navigator.events
    val state = player.state
    val chromeState = player.chromeState
    val progressState = player.progressState
    val fullPlayerState = player.state
        .map { it.copy(positionMs = 0L, bufferedPositionMs = 0L) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, player.state.value.copy(positionMs = 0L, bufferedPositionMs = 0L))
    val settings: StateFlow<com.neonsong.app.data.local.MiscSettings> = settingsPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.neonsong.app.data.local.MiscSettings())
    val activeDownloads = downloadManager.downloads
        .map { map -> map.values.filter { !it.isFinished && it.error == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun navigateToDownloads() {
        routeNavigator.navigateTo(com.neonsong.app.ui.navigation.Screen.Downloads.route)
    }
    private val _customPlaylists = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    val customPlaylists = _customPlaylists.asStateFlow()
    private var customPlaylistsLoaded = false
    val likedTrackKeys = likedSongsManager.likedTrackKeys
    private val _resolvedMusicVideoIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedMusicVideoIds = _resolvedMusicVideoIds.asStateFlow()

    fun resolveMusicVideoForTrack(track: PlayableTrack) {
        val key = "${track.title}|${track.artist}"
        if (_resolvedMusicVideoIds.value.containsKey(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val mvId = searchRepository.get().findMusicVideo(track.title, track.artist, targetDurationMs = track.durationMs)
                    ?: track.videoId
                val cleanId = mvId?.takeIf { it.isNotBlank() }?.let { extractYouTubeVideoId(it) }
                if (!cleanId.isNullOrBlank()) {
                    _resolvedMusicVideoIds.update { it + (key to cleanId) }
                }
            }
        }
    }

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _liveLyricsOffsetMs = MutableStateFlow<Long?>(null)
    val liveLyricsOffsetMs: StateFlow<Long> = combine(
        _liveLyricsOffsetMs,
        settingsPreferences.settings.map { it.lyricsOffsetMs },
    ) { override, pref -> override ?: pref }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun openArtist(name: String, browseId: String? = null) {
        navigator.openArtist(name, browseId)
    }

    fun openAlbum(title: String, artist: String = "", browseId: String? = null) {
        navigator.openAlbum(title, artist, browseId)
    }

    fun setLyricsOffsetMs(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-10000L, 10000L)
        _liveLyricsOffsetMs.value = clamped
        viewModelScope.launch {
            settingsPreferences.setLyricsOffsetMs(clamped)
        }
    }

    fun setLyricsFontScale(scale: Float) {
        viewModelScope.launch {
            settingsPreferences.setLyricsFontScale(scale)
        }
    }

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState = _lyricsState.asStateFlow()

    private var currentTrackLyricsKey: String? = null
    private var lyricsJob: Job? = null

    private val _canvasState = MutableStateFlow<com.neonsong.app.data.canvas.CanvasArtwork?>(null)
    val canvasState = _canvasState.asStateFlow()
    private var currentCanvasTrackKey: String? = null
    private var canvasJob: Job? = null

    private companion object {
        /** Spinner only appears when loading actually takes time; cache
         *  hits resolve well inside this window with no flash. */
        const val LOADING_SPINNER_DELAY_MS = 250L
        /** Next-track canvas warm-up waits this long after the current track's
         *  lookup resolves. Rapid skips cancel the job inside this window, so a
         *  prefetch never spends network/decoder time on a track the user has
         *  already left behind. */
        const val CANVAS_PREFETCH_DELAY_MS = 4_000L
    }

    init {
        viewModelScope.launch {
            playlistRepository.changes.collect {
                if (customPlaylistsLoaded) refreshCustomPlaylists()
            }
        }
        viewModelScope.launch {
            ytMusicLibraryManager.playlists.collect {
                if (customPlaylistsLoaded) refreshCustomPlaylists()
            }
        }
        viewModelScope.launch {
            combine(
                player.chromeState.map { it.current }.distinctUntilChanged(),
                settingsPreferences.settings.map { it.wordByWordLyrics }.distinctUntilChanged(),
            ) { track, wordByWord -> track to wordByWord }.collect { (track, wordByWord) ->
                val key = track?.let { "${it.videoId ?: ""}|${it.artist}|${it.title}|$wordByWord" }
                if (key != currentTrackLyricsKey) {
                    currentTrackLyricsKey = key
                    if (track != null) {
                        loadLyrics(track, forceRefresh = false)
                    } else {
                        lyricsJob?.cancel()
                        _lyricsState.value = LyricsUiState.Idle
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                player.chromeState.map { it.current }.distinctUntilChanged(),
                settingsPreferences.settings.map { it.canvasEnabled to it.canvasOverCellular }.distinctUntilChanged(),
            ) { track, (enabled, cellular) -> Triple(track, enabled, cellular) }.collect { (track, enabled, cellular) ->
                val key = track?.let { "${it.videoId ?: ""}|${it.artist}|${it.title}|$enabled|$cellular" }
                if (key != currentCanvasTrackKey) {
                    currentCanvasTrackKey = key
                    canvasJob?.cancel()
                    if (track != null && enabled) {
                        _canvasState.value = canvasRepository.cached(track)
                        canvasJob = viewModelScope.launch {
                            waitUntilAudible(track)
                            MetadataLog.delayed("canvas", track.videoId)
                            delay(MetadataLog.SETTLE_MS)
                            if (currentCanvasTrackKey != key) {
                                MetadataLog.skipped("canvas", track.videoId)
                                return@launch
                            }
                            MetadataLog.started("canvas", track.videoId)
                            val result = canvasRepository.canvasFor(track, cellularAllowed = cellular)
                            // A skip during the lookup cancels this job; only the
                            // winner publishes.
                            if (currentCanvasTrackKey != key) return@launch
                            _canvasState.value = result
                            // Warm the next track's cache entry while this one plays,
                            // so a skip usually lands on an in-memory hit instead of a
                            // cold network waterfall. Chained in this same job, strictly
                            // after the current lookup: the repository serializes on one
                            // mutex, so a standalone prefetch job could otherwise hold
                            // the lock inside a blocking HTTP call right when the next
                            // track needs it. The delay means rapid skips cancel before
                            // any prefetch traffic starts.
                            val upcoming = player.state.value.let { snapshot ->
                                if (snapshot.shuffleEnabled) {
                                    // Engine shuffle order is not visible here; warming
                                    // the wrong track would only spend lookups.
                                    null
                                } else {
                                    snapshot.queue.getOrNull(snapshot.currentIndex + 1)
                                }
                            }?.takeIf {
                                it.videoId != track.videoId || it.title != track.title || it.artist != track.artist
                            } ?: return@launch
                            delay(CANVAS_PREFETCH_DELAY_MS)
                            if (currentCanvasTrackKey != key) return@launch
                            // Result discarded: canvasFor caches it, which is the
                            // whole point. Never published to _canvasState here.
                            canvasRepository.canvasFor(upcoming, cellularAllowed = cellular)
                        }
                    } else {
                        _canvasState.value = null
                    }
                }
            }
        }
    }

    private suspend fun refreshCustomPlaylists() {
        try {
            _customPlaylists.value = (playlistRepository.getAll()
                .filter { it.mode == "custom" || it.mode == LIKED_SONGS_MODE }
                .sortedByDescending { it.mode == LIKED_SONGS_MODE } + ytMusicLibraryManager.playlists.value)
                .distinctBy { it.id }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            customPlaylistsLoaded = false
            android.util.Log.e("PlayerViewModel", "Couldn't refresh playlists", error)
        }
    }

    fun prepareCustomPlaylists() {
        if (customPlaylistsLoaded) return
        customPlaylistsLoaded = true
        viewModelScope.launch { refreshCustomPlaylists() }
    }

    /**
     * Holds lyrics and canvas fetches until this track is actually playing.
     * The state at subscription still describes the previous song, which is
     * not buffering, so the first value is skipped.
     */
    private suspend fun waitUntilAudible(track: PlayableTrack) {
        withTimeoutOrNull(8_000L) {
            player.state.first { state ->
                val same = if (!track.videoId.isNullOrBlank()) {
                    state.current?.videoId == track.videoId
                } else {
                    state.current?.title == track.title && state.current?.artist == track.artist
                }
                same && state.isPlaying && !state.isBuffering && state.error == null
            }
        }
    }

    fun loadLyrics(track: PlayableTrack, forceRefresh: Boolean = false) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            // Lyrics hit other hosts (LRCLIB, Apple, SimpMusic). Starting
            // them in the same moment as youtubei/player steals connections
            // from the stream extract. Wait until this track is actually
            // playing, then fetch.
            waitUntilAudible(track)
            MetadataLog.delayed("lyrics", track.videoId)
            delay(MetadataLog.SETTLE_MS)
            MetadataLog.started("lyrics", track.videoId)
            // Hold the previous track's lyrics instead of flashing the
            // spinner on every change: cache hits resolve in milliseconds,
            // so the indicator only appears when loading actually takes time.
            val loadingIndicator = launch {
                delay(LOADING_SPINNER_DELAY_MS)
                _lyricsState.value = LyricsUiState.Loading
            }
            try {
                val durationSeconds = if (player.state.value.durationMs > 0) {
                    (player.state.value.durationMs / 1000).toInt()
                } else null

                val result = try {
                    lyricsRepository.getLyrics(
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationSeconds = durationSeconds
                            ?: track.durationMs?.takeIf { it > 0 }?.let { (it / 1000).toInt() },
                        forceRefresh = forceRefresh,
                        wordByWord = settingsPreferences.settings.first().wordByWordLyrics,
                        videoId = track.videoId,
                        onPartialResult = { partial ->
                            withContext(Dispatchers.Main.immediate) {
                                coroutineContext.ensureActive()
                                publishLyrics(partial, wanted = track)
                            }
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    LyricsResult.Error(error.message ?: "Couldn't load lyrics")
                }
                coroutineContext.ensureActive()
                if (result is LyricsResult.Success || _lyricsState.value !is LyricsUiState.Success) {
                    publishLyrics(result, wanted = track)
                }
            } finally {
                loadingIndicator.cancel()
            }
        }
    }

    private fun publishLyrics(result: LyricsResult, wanted: PlayableTrack? = null) {
        // A superseded fetch (user skipped while it was in flight) must
        // never paint the previous song's lyrics over the new one. The job
        // cancel covers most of it; this covers the already-posted tail.
        if (wanted != null) {
            val current = player.state.value.current
            val same = if (!wanted.videoId.isNullOrBlank() || !current?.videoId.isNullOrBlank()) {
                !wanted.videoId.isNullOrBlank() && wanted.videoId == current?.videoId
            } else {
                current != null && current.title.equals(wanted.title, ignoreCase = true) &&
                    current.artist.equals(wanted.artist, ignoreCase = true)
            }
            if (!same) return
        }
        when (result) {
            is LyricsResult.Success -> {
                // Single funnel for everything the views draw, mirroring the
                // reference clients (Metrolist/SimpMusic): one provider
                // timestamp stays one drawn row. The timeline is de-overlapped
                // once so word fill, line focus and auto-scroll all read the
                // same edge-to-edge clock, then provider fragments are merged
                // into whitespace-true words so spacing and punctuation render
                // as authored. Rows are never merged or re-timed here — that
                // grouping is what broke line-by-line sync (two sung lines
                // shown as one, focus arriving late). Word-sync rows render
                // word-by-word; rows without syllables fall back to
                // line-by-line focus on the same clock.
                val lines = if (result.isSynced && result.lines.isNotEmpty() && !result.isInstrumental) {
                    com.neonsong.app.ui.player.normalizeWordSpacing(
                        com.neonsong.app.data.lyrics.LyricsRepository.normalizeLyricTiming(result.lines),
                    )
                } else result.lines
                _lyricsState.value = LyricsUiState.Success(
                    lines = lines,
                    isSynced = result.isSynced,
                    isWordSynced = result.isWordSynced || lines.any { it.hasSyllables },
                    plainLyrics = result.plainLyrics,
                    isInstrumental = result.isInstrumental,
                    source = result.source,
                )
            }
            is LyricsResult.Empty -> {
                _lyricsState.value = LyricsUiState.Empty
            }
            is LyricsResult.Error -> {
                _lyricsState.value = LyricsUiState.Error(result.message)
            }
        }
    }

    fun retryLyrics() {
        val track = player.state.value.current ?: return
        loadLyrics(track, forceRefresh = true)
    }

    fun addToPlaylists(
        playlistIds: Set<Long>,
        duplicatePlaylistIds: Set<Long>,
        track: PlayableTrack,
    ) {
        if (playlistIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val generatedTrack = track.toGeneratedTrack()
                var remoteChanged = false
                var anyAdded = false
                playlistIds.forEach { playlistId ->
                    val allowDuplicate = playlistId in duplicatePlaylistIds
                    if (playlistId < 0L) {
                        val added = ytMusicLibraryManager.addTrack(playlistId, generatedTrack, allowDuplicate)
                        remoteChanged = remoteChanged || added
                        anyAdded = anyAdded || added
                    } else {
                        anyAdded = playlistRepository.addTrack(
                            id = playlistId,
                            track = generatedTrack,
                            allowDuplicate = allowDuplicate,
                        ) != null || anyAdded
                    }
                }
                if (remoteChanged) {
                    ytMusicLibraryManager.refresh()
                    refreshCustomPlaylists()
                }
                _toastMessage.emit(if (anyAdded) "Added to playlist" else "Couldn't add to playlist")
            }
        }
    }

    suspend fun findDuplicatePlaylistIds(playlistIds: Set<Long>, track: PlayableTrack): Set<Long> {
        val generatedTrack = track.toGeneratedTrack()
        val localDuplicates = _customPlaylists.value
            .filter { playlist ->
                playlist.id in playlistIds &&
                    playlist.remotePlaylistId == null &&
                    playlist.tracks.any {
                        it.key == generatedTrack.key ||
                            (it.name.equals(generatedTrack.name, ignoreCase = true) && it.artist.equals(generatedTrack.artist, ignoreCase = true))
                    }
            }
            .mapTo(mutableSetOf(), SavedPlaylist::id)
        val remoteIds = playlistIds.filterTo(mutableSetOf()) { it < 0L }
        return localDuplicates + ytMusicLibraryManager.findDuplicatePlaylistIds(remoteIds, generatedTrack)
    }

    suspend fun findCachedDuplicatePlaylistIds(playlistIds: Set<Long>, track: PlayableTrack): Set<Long> {
        val generatedTrack = track.toGeneratedTrack()
        val remoteIds = playlistIds.filterTo(mutableSetOf()) { it < 0L }
        return ytMusicLibraryManager.findCachedDuplicatePlaylistIds(remoteIds, generatedTrack)
    }

    fun createPlaylistAndAdd(title: String, track: PlayableTrack) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val playlist = playlistRepository.createCustom(title)
                playlistRepository.addTrack(playlist.id, track.toGeneratedTrack())
                _toastMessage.emit("Added to playlist")
            }
        }
    }

    fun toggleLiked(track: PlayableTrack) {
        viewModelScope.launch {
            val loved = likedSongsManager.toggle(track.toGeneratedTrack())
            mirrorLastFmLove(track, loved)
        }
    }

    fun like(track: PlayableTrack) {
        viewModelScope.launch {
            likedSongsManager.like(track.toGeneratedTrack())
            mirrorLastFmLove(track, loved = true)
        }
    }

    private suspend fun mirrorLastFmLove(track: PlayableTrack, loved: Boolean) {
        when (val result = scrobbleRepository.setTrackLoved(track.artist, track.title, loved)) {
            com.neonsong.app.data.repository.ScrobbleRepository.Result.Success,
            com.neonsong.app.data.repository.ScrobbleRepository.Result.NoSessionKey -> Unit
            is com.neonsong.app.data.repository.ScrobbleRepository.Result.Failed ->
                android.util.Log.w("PlayerViewModel", "Last.fm love sync failed: ${result.message}")
        }
    }
}

val LocalBottomNavSlot = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<@androidx.compose.runtime.Composable () -> Unit>> {
    error("LocalBottomNavSlot not provided")
}

/** App-wide collapsed + maximized player layered over every navigation route. */
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel = hiltViewModel(),
    hasBottomNavigation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val state by viewModel.chromeState.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(FullPlayerTab.NOW_PLAYING) }
    var playlistTrack by remember { mutableStateOf<PlayableTrack?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val requestAddToPlaylist = remember(viewModel) {
        { track: PlayableTrack ->
            viewModel.prepareCustomPlaylists()
            playlistTrack = track
        }
    }
    val trackKey = state.current?.let { it.videoId ?: "${it.artist}|${it.title}" }
    LaunchedEffect(trackKey) {
        if (trackKey == null) {
            expanded = false
            currentTab = FullPlayerTab.NOW_PLAYING
        }
    }
    LaunchedEffect(expanded) {
        if (!expanded) {
            currentTab = FullPlayerTab.NOW_PLAYING
        }
    }
    LaunchedEffect(Unit) {
        viewModel.navEvents.collect {
            expanded = false
            currentTab = FullPlayerTab.NOW_PLAYING
        }
    }
    LaunchedEffect(Unit) {
        viewModel.mixLauncher.requests.collect {
            expanded = false
            currentTab = FullPlayerTab.NOW_PLAYING
        }
    }
    LaunchedEffect(Unit) {
        viewModel.genreExplorer.pendingGenre.collect { genre ->
            if (genre != null) {
                expanded = false
                currentTab = FullPlayerTab.NOW_PLAYING
            }
        }
    }
    val miniPlayerVisible = state.current != null && !expanded && !settings.buttonNowPlayingEnabled

    // Sibling pattern: ONE backdrop capturing feed content,
    // consumed by the sibling MiniPlayer. Unconditional remember keeps composition stable.
    val miniBackdropColor = MaterialTheme.colorScheme.background
    val miniBackdrop = rememberLayerBackdrop {
        drawRect(miniBackdropColor)
        drawContent()
    }
    val miniGlass = LocalLiquidGlass.current && isLiquidGlassBackdropSupported()

    val bottomNavSlot = remember { mutableStateOf<@Composable () -> Unit>({}) }
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    CompositionLocalProvider(
        LocalMusicPlayer provides viewModel.player,
        LocalAddToPlaylist provides requestAddToPlaylist,
        LocalMiniPlayerScrollClearance provides if (state.current != null && !settings.buttonNowPlayingEnabled) 88.dp else 0.dp,
        LocalBottomNavSlot provides bottomNavSlot,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().liquidGlassSource(if (miniGlass) miniBackdrop else null)) {
                content()
            }
            if (miniPlayerVisible) {
                MiniPlayer(
                    state = state,
                    progressState = viewModel.progressState,
                    isLossless = state.isLossless,
                    audioCodec = state.audioCodec,
                    onExpand = { expanded = true },
                    onToggle = viewModel.player::togglePlayPause,
                    onPrevious = viewModel.player::previous,
                    onNext = viewModel.player::next,
                    onClose = viewModel.player::stopAndClear,
                    bottomPadding = if (hasBottomNavigation) 92.dp else 12.dp,
                    edgeToEdge = !hasBottomNavigation,
                    backdrop = if (miniGlass) miniBackdrop else null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                
                // Sleep Timer Button
                if (!expanded) {
                    DraggableSleepTimerOverlay(
                        sleepTimerRemainingMs = state.sleepTimerRemainingMs,
                        onCycleTimer = { viewModel.player.cycleSleepTimer() },
                        onCloseTimer = { viewModel.player.setSleepTimerMinutes(0) },
                    )
                }
            }
            val currentTrack = state.current
            if (currentTrack != null && !expanded && settings.buttonNowPlayingEnabled) {
                DraggableNowPlayingButton(
                    track = currentTrack,
                    progressState = viewModel.progressState,
                    isPlaying = state.isPlaying,
                    onExpand = { expanded = true },
                )
            }
            if (activeDownloads.isNotEmpty() && !expanded) {
                val latestProgress = activeDownloads.firstOrNull()?.progressPercent ?: 0
                com.neonsong.app.ui.download.DraggableDownloadOverlay(
                    activeDownloadsCount = activeDownloads.size,
                    latestProgressPercent = latestProgress,
                    onOpenDownloads = { viewModel.navigateToDownloads() },
                )
            }
            var isPlayerDragging by remember { mutableStateOf(false) }
            AnimatedVisibility(
                modifier = Modifier,
                visible = expanded && state.current != null,
                enter = slideInVertically(
                    animationSpec = ExpressiveMotion.appleSheetSpring(),
                    initialOffsetY = { it },
                ) + scaleIn(
                    animationSpec = ExpressiveMotion.appleSheetSpring(),
                    initialScale = 0.90f,
                    transformOrigin = TransformOrigin(0.5f, 0.95f)
                ) + fadeIn(ExpressiveMotion.effectsDefault()),
                exit = slideOutVertically(
                    animationSpec = ExpressiveMotion.appleSheetSpring(),
                    targetOffsetY = { it },
                ) + scaleOut(
                    animationSpec = ExpressiveMotion.appleSheetSpring(),
                    targetScale = 0.90f,
                    transformOrigin = TransformOrigin(0.5f, 0.95f)
                ) + fadeOut(ExpressiveMotion.effectsFast()),
            ) {
                PredictiveBackScreen(
                    enabled = expanded && state.current != null && currentTab == FullPlayerTab.NOW_PLAYING,
                    onBack = { expanded = false },
                ) {
                    ExpandedPlayer(
                        viewModel = viewModel,
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        onRetryLyrics = viewModel::retryLyrics,
                        onCollapse = { expanded = false },
                        onOpenArtist = { artist ->
                            expanded = false
                            viewModel.openArtist(artist)
                        },
                        onDragStatusChange = { isPlayerDragging = it },
                    )
                }
            }
            
            AnimatedVisibility(
                visible = !expanded || state.current == null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                bottomNavSlot.value()
            }
            
            playlistTrack?.let { track ->
                AddToPlaylistDialogHost(
                    viewModel = viewModel,
                    track = track,
                    onDismiss = { playlistTrack = null },
                    onAdd = { playlistIds, duplicatePlaylistIds ->
                        viewModel.addToPlaylists(playlistIds, duplicatePlaylistIds, track)
                        playlistTrack = null
                    },
                    onFindDuplicates = { playlistIds ->
                        viewModel.findDuplicatePlaylistIds(playlistIds, track)
                    },
                    onFindCachedDuplicates = { playlistIds ->
                        viewModel.findCachedDuplicatePlaylistIds(playlistIds, track)
                    },
                    onCreate = { title ->
                        viewModel.createPlaylistAndAdd(title, track)
                        playlistTrack = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpandedPlayer(
    viewModel: PlayerViewModel,
    currentTab: FullPlayerTab,
    onTabChange: (FullPlayerTab) -> Unit,
    onRetryLyrics: () -> Unit,
    onCollapse: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onDragStatusChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.fullPlayerState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val canvas by viewModel.canvasState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val likedTrackKeys by viewModel.likedTrackKeys.collectAsStateWithLifecycle()
    val resolvedMusicVideoIds by viewModel.resolvedMusicVideoIds.collectAsStateWithLifecycle()
    val currentTrack = state.current
    val activeVideoId = currentTrack?.let { resolvedMusicVideoIds["${it.title}|${it.artist}"] }
        ?: currentTrack?.videoId?.takeIf { it.isNotBlank() }
    LaunchedEffect(currentTrack?.title, currentTrack?.artist) {
        if (currentTrack != null) {
            viewModel.resolveMusicVideoForTrack(currentTrack)
        }
    }
    // Normalized key: raw "title|artist".lowercase() would miss liked entries
    // whose spacing differs (e.g. trailing space from another source).
    val isLiked = currentTrack != null &&
        currentTrack.toGeneratedTrack().key in likedTrackKeys
    val lyricsOffsetMs by viewModel.liveLyricsOffsetMs.collectAsStateWithLifecycle()
    FullPlayer(
        state = state,
        progressState = viewModel.progressState,
        player = viewModel.player,
        lyricsState = lyricsState,
        lyricsUiVersion = settings.lyricsUiVersion,
        lyricsAnimation = settings.lyricsAnimation,
        wavySeekbarEnabled = settings.wavySeekbarEnabled,
        rotatingBackgroundEnabled = settings.rotatingBackgroundEnabled,
        lyricsOffsetMs = lyricsOffsetMs,
        onSetLyricsOffsetMs = viewModel::setLyricsOffsetMs,
        lyricsFontScale = settings.lyricsFontScale,
        onSetLyricsFontScale = viewModel::setLyricsFontScale,
        canvas = canvas,
        canvasEnabled = settings.canvasEnabled,
        canvasFullBleedEnabled = settings.canvasFullBleed,
        currentTab = currentTab,
        onTabChange = onTabChange,
        onRetryLyrics = onRetryLyrics,
        onCollapse = onCollapse,
        onOpenArtist = onOpenArtist,
        isLiked = isLiked,
        onToggleLiked = { currentTrack?.let(viewModel::toggleLiked) },
        onDoubleTapLike = { currentTrack?.let(viewModel::like) },
        onDragStatusChange = onDragStatusChange,
        activeVideoId = activeVideoId,
    )
}

@Composable
private fun AddToPlaylistDialogHost(
    viewModel: PlayerViewModel,
    track: PlayableTrack,
    onDismiss: () -> Unit,
    onAdd: (Set<Long>, Set<Long>) -> Unit,
    onFindDuplicates: suspend (Set<Long>) -> Set<Long>,
    onFindCachedDuplicates: suspend (Set<Long>) -> Set<Long>,
    onCreate: (String) -> Unit,
) {
    val playlists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    AddToPlaylistDialog(
        track = track,
        playlists = playlists,
        onDismiss = onDismiss,
        onAdd = onAdd,
        onFindDuplicates = onFindDuplicates,
        onFindCachedDuplicates = onFindCachedDuplicates,
        onCreate = onCreate,
    )
}

@Composable
internal fun AnimatedPlayPauseIcon(isPlaying: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(ExpressiveMotion.effectsFast()) +
                scaleIn(ExpressiveMotion.appleBouncySpring(), initialScale = 0.75f)) togetherWith
                (fadeOut(ExpressiveMotion.effectsFast()) +
                    scaleOut(ExpressiveMotion.effectsFast(), targetScale = 0.75f))
        },
        label = "playPauseIcon",
        modifier = modifier,
    ) { playing ->
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = "Play or pause",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MiniPlayer(
    state: PlaybackChromeState,
    progressState: StateFlow<PlaybackProgressState>,
    isLossless: Boolean = false,
    audioCodec: String? = null,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    edgeToEdge: Boolean,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hapticManager = LocalHapticManager.current
    val haptics = LocalHapticFeedback.current
    val settleScope = rememberCoroutineScope()
    val track = state.current ?: return
    // MiniPlayer: single glass card sampling sibling feed content.
    // Luminance loop (5x5 avg, 0.3..0.8, tween 500, 1s).
    val liquidGlass = LocalLiquidGlass.current
    val isGlass = liquidGlass && isLiquidGlassBackdropSupported() && backdrop != null
    val layer = rememberGraphicsLayer()
    val luminance = 0.5f // Fixed luminance to avoid heavy GPU readback which causes stuttering
    val barInteraction = remember { MutableInteractionSource() }
    val isBarPressed by barInteraction.collectIsPressedAsState()
    val barPressScale by animateFloatAsState(
        targetValue = if (isBarPressed) 0.985f else 1f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "miniBarPressScale",
    )

    var dragX by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }
    var dragY by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }

    // BUG FIX: Ensure offsets cleanly reset when MiniPlayer mounts or collapses back on same track
    LaunchedEffect(Unit) {
        dragX = 0f
        dragY = 0f
    }

    val dragSettleSpec = remember {
        ExpressiveMotion.appleFluidSpring<Float>()
    }
    val shownX by animateFloatAsState(dragX, dragSettleSpec, label = "miniPlayerX")
    val shownY by animateFloatAsState(dragY, dragSettleSpec, label = "miniPlayerY")
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val isTablet = isTabletOrWideScreen()
    val shape = if (edgeToEdge && !isTablet) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    } else {
        RoundedCornerShape(28.dp)
    }
    val positionedModifier = if (edgeToEdge && !isTablet) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 12.dp)
            .padding(bottom = if (isTablet && edgeToEdge) 14.dp else bottomPadding)
            .widthIn(max = 680.dp)
            .fillMaxWidth()
    }
    Box(
        modifier = positionedModifier
            .graphicsLayer {
                translationX = shownX
                translationY = shownY.coerceAtLeast(-threshold * 0.45f)
                scaleX = barPressScale
                scaleY = barPressScale
                alpha = (1f - (abs(shownX) + shownY.coerceAtLeast(0f)) / (threshold * 4f)).coerceIn(0.55f, 1f)
            }
            .pointerInput(track.videoId, track.title, threshold) {
                val flingPxPerSec = 1250f
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var lockedX: Boolean? = null
                    var didThresholdHaptic = false
                    var accX = 0f
                    var accY = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                // BUG FIX: Only consume UP if we actually entered a drag gesture.
                                // Otherwise child IconButtons / Surface clickable never receive their click!
                                if (lockedX != null) {
                                    change.consume()
                                }
                                break
                            }
                            val delta = change.positionChange()
                            accX += delta.x
                            accY += delta.y
                            tracker.addPosition(change.uptimeMillis, change.position)

                            if (lockedX == null && (abs(accX) > touchSlopPx || abs(accY) > touchSlopPx)) {
                                lockedX = abs(accX) > abs(accY)
                            }
                            if (lockedX != null) {
                                change.consume()
                                if (lockedX == true) {
                                    dragX += delta.x
                                } else {
                                    dragY += delta.y
                                }
                                val crossed = abs(dragX) > threshold || abs(dragY) > threshold
                                if (crossed && !didThresholdHaptic) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    didThresholdHaptic = true
                                }
                            }
                        }
                        if (lockedX != null) {
                            val velocity = tracker.calculateVelocity()
                            val flingX = abs(velocity.x) >= flingPxPerSec
                            val flingY = abs(velocity.y) >= flingPxPerSec
                            val commitOff = threshold * 1.35f
                            when {
                                dragY > threshold || (flingY && velocity.y > 0f && dragY > 0f) -> {
                                    dragY = commitOff
                                    settleScope.launch {
                                        delay(90L)
                                        onClose()
                                        dragX = 0f
                                        dragY = 0f
                                    }
                                }
                                dragY < -threshold || (flingY && velocity.y < 0f && dragY < 0f) -> {
                                    dragY = -commitOff * 0.4f
                                    settleScope.launch {
                                        delay(60L)
                                        dragX = 0f
                                        dragY = 0f
                                        onExpand()
                                    }
                                }
                                dragX < -threshold || (flingX && velocity.x < 0f && dragX < 0f) -> {
                                    dragX = -commitOff
                                    settleScope.launch {
                                        delay(90L)
                                        onNext()
                                        dragX = 0f
                                        dragY = 0f
                                    }
                                }
                                dragX > threshold || (flingX && velocity.x > 0f && dragX > 0f) -> {
                                    dragX = commitOff
                                    settleScope.launch {
                                        delay(90L)
                                        onPrevious()
                                        dragX = 0f
                                        dragY = 0f
                                    }
                                }
                                else -> {
                                    dragX = 0f
                                    dragY = 0f
                                }
                            }
                        } else {
                            dragX = 0f
                            dragY = 0f
                        }
                    } catch (_: CancellationException) {
                        dragX = 0f
                        dragY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val isDark = LocalIsDarkTheme.current
        Surface(
            shape = shape,
            color = if (isGlass) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            },
            tonalElevation = if (edgeToEdge || isGlass) 0.dp else 6.dp,
            shadowElevation = if (edgeToEdge || isGlass) 0.dp else 16.dp,
            border = if (isGlass) {
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.28f else 0.55f),
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.20f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        )
                    )
                )
            } else {
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f),
                        )
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isGlass) {
                        Modifier.liquidGlass(backdrop, layer, luminance, shape)
                    } else Modifier
                )
                .clickable(
                    interactionSource = barInteraction,
                    indication = null,
                    onClick = {
                        hapticManager.click()
                        onExpand()
                    }
                ),
        ) {
            Column(
                modifier = if (edgeToEdge) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                } else {
                    Modifier
                },
            ) {
                // Subtle top grabber pill indicating swipe-up capability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(
                                (if (isGlass && !isDark) Color.Black else Color.White).copy(alpha = 0.18f)
                            )
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Artwork with subtle elevation & playing badge
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 4.dp,
                            shadowElevation = 6.dp,
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            PlayerArtwork(track, Modifier.fillMaxSize(), 14.dp)
                        }
                    }

                    val miniTitleColor = if (isGlass) {
                        if (isDark) Color.White else Color.Black
                    } else MaterialTheme.colorScheme.onSurface
                    val miniArtistColor = if (isGlass) {
                        if (isDark) Color.White.copy(alpha = 0.72f)
                        else Color.Black.copy(alpha = 0.68f)
                    } else MaterialTheme.colorScheme.onSurfaceVariant

                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 13.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.5.sp,
                                    letterSpacing = (-0.2).sp,
                                ),
                                fontWeight = FontWeight.Bold,
                                color = miniTitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (state.isPlaying) {
                                PlayingWaveBars(
                                    waveColor = MaterialTheme.colorScheme.primary,
                                    containerColor = Color.Transparent,
                                    isPlaying = true,
                                    modifier = Modifier.size(18.dp, 13.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Medium,
                                color = miniArtistColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isLossless) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, Color(0xFF00E676).copy(alpha = 0.55f)),
                                ) {
                                    Text(
                                        text = if (audioCodec?.uppercase() == "FLAC") "FLAC" else "HI-RES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.4.sp,
                                        ),
                                        color = Color(0xFF00E676),
                                        modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }

                    val playPauseInteraction = remember { MutableInteractionSource() }
                    val isPlayPausePressed by playPauseInteraction.collectIsPressedAsState()
                    val playPauseScale by animateFloatAsState(
                        targetValue = if (isPlayPausePressed) 0.88f else 1.0f,
                        animationSpec = ExpressiveMotion.appleTactileSpring(),
                        label = "miniPlayPauseScale",
                    )
                    Surface(
                        onClick = {
                            hapticManager.click()
                            onToggle()
                        },
                        interactionSource = playPauseInteraction,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = playPauseScale
                                scaleY = playPauseScale
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isBuffering) {
                                ExpressiveInlineLoadingIndicator(
                                    size = 22.dp,
                                    color = androidx.compose.material3.LocalContentColor.current,
                                    strokeWidth = 2.4.dp,
                                )
                            } else {
                                AnimatedPlayPauseIcon(state.isPlaying, Modifier.size(24.dp))
                            }
                        }
                    }

                    val nextInteraction = remember { MutableInteractionSource() }
                    val isNextPressed by nextInteraction.collectIsPressedAsState()
                    val nextScale by animateFloatAsState(
                        targetValue = if (isNextPressed) 0.88f else 1.0f,
                        animationSpec = ExpressiveMotion.appleTactileSpring(),
                        label = "miniNextScale",
                    )
                    IconButton(
                        onClick = {
                            hapticManager.click()
                            onNext()
                        },
                        interactionSource = nextInteraction,
                        enabled = state.queueSize > 1,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = nextScale
                                scaleY = nextScale
                            },
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(26.dp),
                            tint = if (state.queueSize > 1) {
                                if (isGlass) {
                                    if (isDark) Color.White else Color.Black
                                } else MaterialTheme.colorScheme.onSurface
                            } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                        )
                    }
                }
                MiniPlayerProgress(progressState)
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(progressState: StateFlow<PlaybackProgressState>) {
    val progressFlowState = progressState.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val brush = remember(primary, tertiary) {
        Brush.horizontalGradient(listOf(primary, tertiary))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 3.dp)
            .height(2.5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = progressFlowState.value
                    scaleX = if (s.durationMs > 0) {
                        (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f)
                    } else 0f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(brush),
        )
    }
}

@Composable
fun PlayingWaveBars(
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = CircleShape,
    isPlaying: Boolean = true,
) {
    val firstState: androidx.compose.runtime.State<Float>?
    val secondState: androidx.compose.runtime.State<Float>?
    val thirdState: androidx.compose.runtime.State<Float>?

    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "miniArtworkWave")
        firstState = transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "miniWaveFirst",
        )
        secondState = transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 0.30f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 640, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "miniWaveSecond",
        )
        thirdState = transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 530, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "miniWaveThird",
        )
    } else {
        firstState = null
        secondState = null
        thirdState = null
    }

    val content: @Composable () -> Unit = {
        // Fixed small badge content so it can never expand to fill the cover.
        // Outer Box is 26x22 (16+5+5, 12+5+5) — always tiny, bottom-end aligned by caller.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(horizontal = 5.dp, vertical = 5.dp)
                .size(16.dp, 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(
                Modifier.fillMaxSize(),
            ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val barCount = 3
            val barWidth = (size.width / 5.2f).coerceAtLeast(1.5f)
            val barGap = barWidth * 0.9f
            val totalContentWidth = barCount * barWidth + (barCount - 1) * barGap
            val startX = ((size.width - totalContentWidth) / 2f).coerceAtLeast(0f)

            repeat(barCount) { index ->
                val fraction = if (isPlaying && firstState != null && secondState != null && thirdState != null) {
                    when (index) {
                        0 -> firstState.value
                        1 -> secondState.value
                        else -> thirdState.value
                    }
                } else {
                    0.20f
                }
                val barHeight = (size.height * fraction).coerceIn(minOf(barWidth, size.height), size.height)
                drawRoundRect(
                    color = waveColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = startX + index * (barWidth + barGap),
                        y = size.height - barHeight,
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
            }
        }
    }

    if (containerColor == Color.Transparent) {
        Box(
            modifier = modifier.defaultMinSize(minWidth = 24.dp, minHeight = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            modifier = modifier.defaultMinSize(minWidth = 26.dp, minHeight = 22.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistDialog(
    track: PlayableTrack,
    playlists: List<SavedPlaylist>,
    onDismiss: () -> Unit,
    onAdd: (Set<Long>, Set<Long>) -> Unit,
    onFindDuplicates: suspend (Set<Long>) -> Set<Long>,
    onFindCachedDuplicates: suspend (Set<Long>) -> Set<Long>,
    onCreate: (String) -> Unit,
) {
    val sanitizedPlaylists = remember(playlists) { playlists.distinctBy { it.id } }
    var newPlaylistName by remember(track) { mutableStateOf("") }
    var selectedPlaylistIds by remember(track) { mutableStateOf(emptySet<Long>()) }
    var duplicateConfirmation by remember(track) { mutableStateOf<Set<Long>?>(null) }
    var duplicatePlaylistIds by remember(track) { mutableStateOf(emptySet<Long>()) }
    var knownDuplicatePlaylistIds by remember(track) { mutableStateOf(emptySet<Long>()) }
    var isCheckingDuplicates by remember(track) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val trackKey = remember(track.title, track.artist) { track.toGeneratedTrack().key }
    val selectedPlaylists = sanitizedPlaylists.filter { it.id in selectedPlaylistIds }
    val config = LocalConfiguration.current
    val isLandscapeDialog = config.screenWidthDp > config.screenHeightDp
    val playlistListMaxHeight = if (isLandscapeDialog) {
        (config.screenHeightDp.dp - 210.dp).coerceIn(96.dp, 220.dp)
    } else {
        (config.screenHeightDp.dp - 360.dp).coerceIn(180.dp, 410.dp)
    }

    LaunchedEffect(sanitizedPlaylists.map(SavedPlaylist::id)) {
        selectedPlaylistIds = selectedPlaylistIds.intersect(sanitizedPlaylists.mapTo(mutableSetOf(), SavedPlaylist::id))
    }

    LaunchedEffect(trackKey, sanitizedPlaylists.map(SavedPlaylist::id)) {
        runCatching {
            val remoteIds = sanitizedPlaylists
                .filterTo(mutableListOf()) { it.remotePlaylistId != null }
                .mapTo(mutableSetOf(), SavedPlaylist::id)
            knownDuplicatePlaylistIds = knownDuplicatePlaylistIds + onFindCachedDuplicates(remoteIds)
        }
    }

    fun requestAdd(playlistIds: Set<Long>) {
        if (playlistIds.isEmpty() || isCheckingDuplicates) return
        val immediateDuplicates = playlistIds.filter { id ->
            id in knownDuplicatePlaylistIds ||
                sanitizedPlaylists.firstOrNull { it.id == id }?.tracks?.any {
                    it.key == trackKey ||
                        (it.name.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true))
                } == true
        }.toSet()
        if (immediateDuplicates.isNotEmpty()) {
            duplicatePlaylistIds = emptySet()
            duplicateConfirmation = immediateDuplicates
            return
        }
        scope.launch {
            runCatching {
                isCheckingDuplicates = true
                val duplicates = onFindDuplicates(playlistIds)
                isCheckingDuplicates = false
                knownDuplicatePlaylistIds = (knownDuplicatePlaylistIds - playlistIds) + duplicates
                if (duplicates.isEmpty()) {
                    onAdd(playlistIds, emptySet())
                } else {
                    duplicatePlaylistIds = emptySet()
                    duplicateConfirmation = duplicates
                }
            }.onFailure {
                isCheckingDuplicates = false
                onAdd(playlistIds, emptySet())
            }
        }
    }

    duplicateConfirmation?.let { duplicates ->
        val duplicatePlaylists = sanitizedPlaylists.filter { it.id in duplicates }
        if (duplicatePlaylists.size == 1) {
            val playlist = duplicatePlaylists.first()
            AlertDialog(
                onDismissRequest = { duplicateConfirmation = null },
                title = { Text("Song already in playlist") },
                text = {
                    Text(
                        "${track.title} by ${track.artist} is already present in ${playlist.title}. " +
                            "You can leave the playlist unchanged or add another copy.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onAdd(selectedPlaylistIds, setOf(playlist.id))
                            duplicateConfirmation = null
                        },
                    ) {
                        Text("Add anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { duplicateConfirmation = null }) {
                        Text("Leave unchanged")
                    }
                },
            )
            return
        }

        val missingCount = selectedPlaylistIds.size - duplicates.size
        AlertDialog(
            onDismissRequest = { duplicateConfirmation = null },
            title = {
                Text("${duplicates.size} playlists already have this song")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Existing copies stay unchanged. Select only the playlists where you want another copy.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            duplicatePlaylists,
                            key = { index, playlist -> "${playlist.id}_${playlist.remotePlaylistId ?: ""}_$index" },
                        ) { _, playlist ->
                            val addAgain = playlist.id in duplicatePlaylistIds
                            val canAddAnother = playlist.mode != LIKED_SONGS_MODE
                            Surface(
                                onClick = {
                                    duplicatePlaylistIds = if (addAgain) {
                                        duplicatePlaylistIds - playlist.id
                                    } else {
                                        duplicatePlaylistIds + playlist.id
                                    }
                                },
                                enabled = canAddAnother,
                                shape = RoundedCornerShape(14.dp),
                                color = if (addAgain) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            playlist.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (canAddAnother) "Add another copy" else "Already liked",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Checkbox(checked = addAgain, onCheckedChange = null, enabled = canAddAnother)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAdd(selectedPlaylistIds, duplicatePlaylistIds)
                        duplicateConfirmation = null
                    },
                ) {
                    Text(
                        when {
                            duplicatePlaylistIds.isNotEmpty() -> "Add anyway"
                            missingCount > 0 -> "Add missing"
                            else -> "Leave unchanged"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateConfirmation = null }) {
                    Text("Leave unchanged")
                }
            },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // Opaque sheet: player controls / quality pill behind must not bleed through.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
    ) {
        var showCreateDialog by rememberSaveable { mutableStateOf(false) }
        
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = newPlaylistName.isNotBlank(),
                        onClick = {
                            val cleanName = newPlaylistName.trim()
                            val existingPlaylist = sanitizedPlaylists.firstOrNull {
                                it.mode == "custom" && it.title.equals(cleanName, ignoreCase = true)
                            }
                            if (existingPlaylist == null) {
                                onCreate(cleanName)
                            } else {
                                selectedPlaylistIds = setOf(existingPlaylist.id)
                                requestAdd(setOf(existingPlaylist.id))
                            }
                            showCreateDialog = false
                        },
                    ) {
                        Text("Create & Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                },
            )
        }

        com.neonsong.app.ui.common.EdgeToEdgeDialogWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .align(Alignment.CenterHorizontally)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(27.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        "Add to playlist",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (selectedPlaylistIds.isEmpty()) "Choose one or more playlists" else "${selectedPlaylistIds.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create Playlist")
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (sanitizedPlaylists.isEmpty()) {
                Text(
                    "No playlists yet. Create your first playlist below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = playlistListMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    itemsIndexed(
                        sanitizedPlaylists,
                        key = { index, playlist -> "${playlist.id}_${playlist.remotePlaylistId ?: ""}_$index" },
                    ) { _, playlist ->
                        val selected = playlist.id in selectedPlaylistIds
                        val alreadyAdded = playlist.id in knownDuplicatePlaylistIds ||
                            playlist.tracks.any {
                                it.key == trackKey ||
                                    (it.name.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true))
                            }
                        val trackCount = playlist.remoteTrackCount
                            ?: playlist.tracks.size.takeIf { playlist.remotePlaylistId == null }
                        val countLabel = when (trackCount) {
                            null -> "Track count unavailable"
                            1 -> "1 track"
                            else -> "$trackCount tracks"
                        }
                        val rowColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = tween(ExpressiveMotion.Quick),
                            label = "playlistSelectionColor",
                        )
                        Surface(
                            onClick = {
                                selectedPlaylistIds = if (selected) {
                                    selectedPlaylistIds - playlist.id
                                } else {
                                    selectedPlaylistIds + playlist.id
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = rowColor,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlaylistCover(
                                    playlist = playlist,
                                    modifier = Modifier.size(52.dp),
                                    cornerRadius = 14.dp,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                                    Text(
                                        playlist.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        when {
                                            isCheckingDuplicates && selected -> "$countLabel · Checking contents…"
                                            alreadyAdded -> "$countLabel · Already added"
                                            else -> countLabel
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (alreadyAdded) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Checkbox(checked = selected, onCheckedChange = null)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { requestAdd(selectedPlaylistIds) },
                    enabled = selectedPlaylistIds.isNotEmpty() && !isCheckingDuplicates,
                ) {
                    if (isCheckingDuplicates) {
                        ExpressiveInlineLoadingIndicator(
                            size = 18.dp,
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            selectedPlaylistIds.isEmpty() -> "Add"
                            isCheckingDuplicates -> "Checking"
                            else -> "Add to ${selectedPlaylists.size}"
                        },
                    )
                }
            }
        }
    }
}

private enum class SeekDirection { REWIND, FORWARD }

@Composable
private fun SongVideoSwitcher(
    isVideoMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val pillShape = RoundedCornerShape(100)
    val indicatorProgress by animateFloatAsState(
        targetValue = if (isVideoMode) 1f else 0f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "songVideoPillOffset",
    )

    Surface(
        shape = pillShape,
        color = Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier
            .height(38.dp)
            .width(200.dp)
            .clip(pillShape),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val halfWidth = totalWidth / 2

            // Active sliding indicator bubble with spring physics
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .width(halfWidth - 3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = indicatorProgress * (halfWidth.toPx() - 3.dp.toPx() / 2f)
                    }
                    .clip(pillShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.16f),
                            )
                        )
                    )
                    .border(0.8.dp, Color.White.copy(alpha = 0.30f), pillShape),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Song Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (isVideoMode) {
                                hapticManager.toggle(false)
                                onModeChange(false)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = if (!isVideoMode) Color.White else Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "Song",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (!isVideoMode) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                letterSpacing = 0.2.sp,
                            ),
                            color = if (!isVideoMode) Color.White else Color.White.copy(alpha = 0.60f),
                        )
                    }
                }

                // Video Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!isVideoMode) {
                                hapticManager.toggle(true)
                                onModeChange(true)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Filled.SmartDisplay,
                            contentDescription = null,
                            tint = if (isVideoMode) Color.White else Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "Video",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isVideoMode) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                letterSpacing = 0.2.sp,
                            ),
                            color = if (isVideoMode) Color.White else Color.White.copy(alpha = 0.60f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(
    state: MusicPlayerState,
    progressState: StateFlow<PlaybackProgressState>,
    player: MusicPlayer,
    lyricsState: LyricsUiState,
    lyricsUiVersion: LyricsUiVersion = LyricsUiVersion.MODERN,
    lyricsAnimation: LyricsAnimation = LyricsAnimation.APPLE_FLUID,
    wavySeekbarEnabled: Boolean = true,
    rotatingBackgroundEnabled: Boolean = true,
    lyricsOffsetMs: Long = 0L,
    onSetLyricsOffsetMs: ((Long) -> Unit)? = null,
    lyricsFontScale: Float = 1.0f,
    onSetLyricsFontScale: ((Float) -> Unit)? = null,
    canvas: com.neonsong.app.data.canvas.CanvasArtwork? = null,
    canvasEnabled: Boolean = true,
    canvasFullBleedEnabled: Boolean = true,
    currentTab: FullPlayerTab,
    onTabChange: (FullPlayerTab) -> Unit,
    onRetryLyrics: () -> Unit,
    onCollapse: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    isLiked: Boolean = false,
    onToggleLiked: () -> Unit = {},
    onDoubleTapLike: () -> Unit = {},
    onDragStatusChange: (Boolean) -> Unit = {},
    activeVideoId: String? = null,
) {
    val track = state.current ?: return
    val hapticManager = LocalHapticManager.current
    // Video sessions are deliberately opt-in: the dedicated video WebView owns
    // audio only while it is visible, then hands the user back to their exact
    // music-player state without restarting the track.
    val effectiveVideoId = activeVideoId?.takeIf { it.isNotBlank() } ?: track.videoId?.takeIf { it.isNotBlank() }
    val hasVideo = !effectiveVideoId.isNullOrBlank()
    val trackIdentity = remember(track.videoId, track.title, track.artist) { "${track.videoId}_${track.title}_${track.artist}" }
    var isVideoMode by rememberSaveable(trackIdentity) { mutableStateOf(false) }
    var isVideoFullscreen by rememberSaveable(trackIdentity) { mutableStateOf(false) }
    var resumeAudioAfterVideo by rememberSaveable(trackIdentity) { mutableStateOf(false) }
    var videoPositionMs by rememberSaveable(trackIdentity) { mutableLongStateOf(0L) }
    var videoDurationMs by rememberSaveable(trackIdentity) { mutableLongStateOf(0L) }
    var isVideoPlaying by rememberSaveable(trackIdentity) { mutableStateOf(true) }
    var videoSeekCommandSeconds by remember(trackIdentity) { mutableStateOf<Float?>(null) }

    val view = LocalView.current
    val enterVideoMode = {
        if (hasVideo && !isVideoMode) {
            resumeAudioAfterVideo = state.isPlaying || state.isBuffering
            videoPositionMs = progressState.value.positionMs.coerceAtLeast(0L)
            player.pause()
            isVideoPlaying = true
            isVideoMode = true
        }
    }

    val closeVideoMode = {
        releaseMusicVideo()
        val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        isVideoFullscreen = false
        isVideoMode = false
        if (videoPositionMs > 0L) {
            player.seekTo(videoPositionMs)
        }
        if (resumeAudioAfterVideo) player.resume()
        resumeAudioAfterVideo = false
    }
    BackHandler(enabled = isVideoMode) {
        if (isVideoFullscreen) {
            isVideoFullscreen = false
        } else {
            closeVideoMode()
        }
    }

    // Auto-switch from video to background voice/audio on screen off or device lock
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(view.context, isVideoMode, lifecycleOwner) {
        if (!isVideoMode) return@DisposableEffect onDispose {}
        val ctx = view.context
        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

        fun switchToVoice() {
            if (isVideoMode) {
                val currentPos = videoPositionMs.coerceAtLeast(0L)
                closeVideoMode()
                player.seekTo(currentPos)
                player.resume()
            }
        }

        val screenOffReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) {
                    switchToVoice()
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
        androidx.core.content.ContextCompat.registerReceiver(
            ctx,
            screenOffReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (powerManager?.isInteractive == false) {
                    switchToVoice()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            runCatching { ctx.unregisterReceiver(screenOffReceiver) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // Ensure WebView never leaks audio if track changes or FullPlayer is closed
    DisposableEffect(trackIdentity) {
        onDispose {
            releaseMusicVideo()
        }
    }

    // Stable StateFlow for video progress so we don't re-allocate a StateFlow on every progress tick
    val videoProgressFlow = remember {
        MutableStateFlow(PlaybackProgressState(positionMs = 0L, durationMs = 0L))
    }
    LaunchedEffect(videoPositionMs, videoDurationMs, track.durationMs, state.durationMs) {
        videoProgressFlow.value = PlaybackProgressState(
            positionMs = videoPositionMs,
            durationMs = if (videoDurationMs > 0L) videoDurationMs else (track.durationMs ?: state.durationMs),
        )
    }

    var showLyricsOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var showSignalPath by rememberSaveable { mutableStateOf(false) }
    var showAudioControls by rememberSaveable { mutableStateOf(false) }
    val signalPath by player.signalPath.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscapeScreen = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isCanvasActive = canvasEnabled && canvas != null
    val showFullBleed = canvasFullBleedEnabled && !isLandscapeScreen
    val showSleeveCanvas = isCanvasActive && !showFullBleed
    // activeCanvas is the single gate every render branch below keys off, so it also
    // carries the Animated Album Canvas toggle. The ViewModel nulls canvasState
    // asynchronously, so without this a disabled toggle still paints one frame of
    // motion artwork over the cover.
    val activeCanvas = remember(canvas, showFullBleed, canvasEnabled) {
        if (!canvasEnabled) {
            null
        } else {
            val tall = canvas?.tallUrl
            if (showFullBleed && canvas != null && !tall.isNullOrBlank()) {
                canvas.copy(url = tall)
            } else {
                canvas
            }
        }
    }
    var canvasAspect by remember(activeCanvas?.url) { mutableFloatStateOf(0f) }
    var canvasRendered by remember(activeCanvas?.url) { mutableStateOf(false) }
    var lyricsFullscreen by rememberSaveable(currentTab) { mutableStateOf(false) }
    var lastFullscreenTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isVideoFullscreen) {
        if (isVideoFullscreen) {
            lastFullscreenTime = SystemClock.uptimeMillis()
        }
    }

    DisposableEffect(view, isVideoMode, isVideoFullscreen) {
        if (!isVideoMode) return@DisposableEffect onDispose {}
        val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()

        val listener = object : android.view.OrientationEventListener(view.context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val now = SystemClock.uptimeMillis()
                // Only auto-exit fullscreen when user holds phone upright after entering fullscreen
                if (isVideoFullscreen && now - lastFullscreenTime > 700L) {
                    val isStraightUp = orientation in 335..360 || orientation in 0..25
                    if (isStraightUp) {
                        isVideoFullscreen = false
                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    DisposableEffect(view, lyricsFullscreen, isVideoFullscreen) {
        val fullscreenActive = lyricsFullscreen || isVideoFullscreen
        val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        if (fullscreenActive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            if (isVideoFullscreen) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        onDispose {
            if (fullscreenActive) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                previousBehavior?.let { controller.systemBarsBehavior = it }
                if (isVideoFullscreen) {
                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }
    var showTrackMenu by rememberSaveable(track.videoId, track.title) { mutableStateOf(false) }
    var artworkDragX by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }
    var dismissDragY by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }
    var isDismissDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDismissDragging) { onDragStatusChange(isDismissDragging) }
    var seekOverlayDirection by remember(track.videoId, track.title) { mutableStateOf<SeekDirection?>(null) }
    var seekOverlaySeconds by remember(track.videoId, track.title) { mutableIntStateOf(0) }
    var lastTapTimestamp by remember(track.videoId, track.title) { mutableLongStateOf(0L) }
    var lastTapSide by remember(track.videoId, track.title) { mutableStateOf<SeekDirection?>(null) }
    var lastLikeTapTimestamp by remember(track.videoId, track.title) { mutableLongStateOf(0L) }
    var seekResetJob by remember(track.videoId, track.title) { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val shownArtworkX by animateFloatAsState(
        artworkDragX,
        ExpressiveMotion.spatialSpring(),
        label = "fullPlayerArtworkX",
    )
    val shownDismissY by animateFloatAsState(
        targetValue = dismissDragY,
        animationSpec = if (isDismissDragging) snap() else ExpressiveMotion.spatialSpring(),
        label = "fullPlayerDismissY",
    )
    val swipeThreshold = with(LocalDensity.current) { 88.dp.toPx() }

    // Dominant cover-art color for the ambient background glow. Extracted
    // once per track through the shared Coil loader (normally a cache hit)
    // with Palette; any failure leaves the standard surface gradient.
    val context = LocalContext.current
    val artworkViewModel: ArtworkViewModel = hiltViewModel()
    val artworkKey = remember(track.title, track.artist) { ArtworkNormalizer.cacheKey(track.title, track.artist) }
    val resolvedAmbientUrl by remember(artworkKey) {
        artworkViewModel.resolved.map { it[artworkKey] }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = artworkViewModel.resolved.value[artworkKey])

    var ambientPrimary by remember(track.videoId, track.artworkUrl, track.title, track.artist) { mutableStateOf<Color?>(null) }
    var ambientSecondary by remember(track.videoId, track.artworkUrl, track.title, track.artist) { mutableStateOf<Color?>(null) }
    var ambientTertiary by remember(track.videoId, track.artworkUrl, track.title, track.artist) { mutableStateOf<Color?>(null) }
    LaunchedEffect(track.videoId, track.artworkUrl, track.title, track.artist, resolvedAmbientUrl) {
        val url = resolvedAmbientUrl?.takeIf { it.isNotBlank() }
            ?: ArtworkNormalizer.upscaleYoutubeArtwork(track.artworkUrl)?.takeIf { it.isNotBlank() }
            ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(128)
                    .build()
                val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    ?: return@runCatching
                val palette = Palette.from(bitmap).clearFilters().generate()
                val p = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
                val s = palette.lightVibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                val t = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
                fun boostVibrance(colorInt: Int, satBoost: Float = 1.35f, minSat: Float = 0.65f, minLight: Float = 0.38f, maxLight: Float = 0.60f): Color {
                    val hsl = FloatArray(3)
                    androidx.core.graphics.ColorUtils.colorToHSL(colorInt, hsl)
                    hsl[1] = (hsl[1] * satBoost).coerceIn(minSat, 1.0f)
                    hsl[2] = hsl[2].coerceIn(minLight, maxLight)
                    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                }
                if (p != null) ambientPrimary = boostVibrance(p.rgb, satBoost = 1.40f, minSat = 0.70f, minLight = 0.40f, maxLight = 0.62f)
                if (s != null) ambientSecondary = boostVibrance(s.rgb, satBoost = 1.30f, minSat = 0.60f, minLight = 0.45f, maxLight = 0.70f)
                if (t != null) ambientTertiary = boostVibrance(t.rgb, satBoost = 1.30f, minSat = 0.60f, minLight = 0.22f, maxLight = 0.42f)
            }
        }
    }
    val ambientColor by animateColorAsState(
        targetValue = ambientPrimary ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(700),
        label = "playerAmbientColor",
    )
    val ambientCompanion by animateColorAsState(
        targetValue = ambientSecondary ?: androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.tertiary, ambientColor, 0.48f),
        animationSpec = tween(700),
        label = "playerAmbientCompanion",
    )
    val ambientDeep by animateColorAsState(
        targetValue = ambientTertiary ?: androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.secondary, ambientColor, 0.25f),
        animationSpec = tween(700),
        label = "playerAmbientDeep",
    )

    fun Modifier.playerVerticalSwipe(enabled: Boolean): Modifier = if (!enabled) this else pointerInput(track.videoId, track.title, currentTab) {
        detectVerticalDragGestures(
            onDragStart = { isDismissDragging = true },
            onDragCancel = {
                isDismissDragging = false
                dismissDragY = 0f
            },
            onDragEnd = {
                isDismissDragging = false
                when {
                    currentTab != FullPlayerTab.NOW_PLAYING && dismissDragY > swipeThreshold -> onTabChange(FullPlayerTab.NOW_PLAYING)
                    currentTab == FullPlayerTab.NOW_PLAYING && dismissDragY < -swipeThreshold -> onTabChange(FullPlayerTab.QUEUE)
                    currentTab == FullPlayerTab.NOW_PLAYING && dismissDragY > swipeThreshold -> {
                        if (isVideoMode) closeVideoMode()
                        onCollapse()
                    }
                }
                dismissDragY = 0f
            },
        ) { change, amount ->
            val updatedDrag = if (currentTab != FullPlayerTab.NOW_PLAYING) {
                (dismissDragY + amount).coerceAtLeast(0f)
            } else {
                dismissDragY + amount
            }
            if (updatedDrag != dismissDragY) change.consume()
            dismissDragY = updatedDrag
        }
    }

    // Unconditional remember keeps composition stable; usage gated by glass flag.
    // Single backdrop, sibling source below — never nest glass inside its own capture.
    val playerBackdrop = rememberLayerBackdrop()
    val fullGlass = LocalLiquidGlass.current && isLiquidGlassBackdropSupported()
    CompositionLocalProvider(
        LocalLiquidGlassBackdrop provides if (fullGlass) playerBackdrop else null,
        LocalLiquidGlassOverlayBackdrop provides if (fullGlass) playerBackdrop else null,
    ) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = shownDismissY.coerceAtLeast(0f)
                val playerHeight = size.height.coerceAtLeast(1f)
                alpha = (1f - shownDismissY.coerceAtLeast(0f) / (playerHeight * 1.5f)).coerceIn(0.72f, 1f)
            }
            .playerVerticalSwipe(enabled = currentTab == FullPlayerTab.NOW_PLAYING && !isLandscapeScreen),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bgWidth = constraints.maxWidth.toFloat()
            val bgHeight = constraints.maxHeight.toFloat()
            val bgMaxDimension = maxOf(bgWidth, bgHeight, 1f)
            val isLandscape = constraints.maxWidth > constraints.maxHeight
            var heroBottomPx by remember { mutableFloatStateOf(0f) }

            Box(Modifier.matchParentSize()) {
                FluidArtworkBackground(
                    track = track,
                    modifier = Modifier.fillMaxSize(),
                    // Lyrics legibility lives or dies on background
                    // suppression; the Now Playing tab keeps its light blur.
                    // When extraBlur is active, the fluid animation loop pauses
                    // to keep 120Hz scrolling perfectly fluid without tearing down shader state.
                    extraBlur = currentTab == FullPlayerTab.LYRICS,
                    rotatingBackgroundEnabled = rotatingBackgroundEnabled,
                    isPlaying = state.isPlaying && !isVideoMode,
                    fallback = {
                        val staticBlurTransform = remember(currentTab) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                listOf(
                                    BlurTransformation(
                                        radius = if (currentTab == FullPlayerTab.LYRICS) 25 else 18,
                                        maxDimension = 100,
                                    )
                                )
                            } else {
                                emptyList()
                            }
                        }
                        PlayerArtwork(
                            track = track,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.35f
                                    scaleY = 1.35f
                                    alpha = 0.9f
                                }
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        if (currentTab == FullPlayerTab.LYRICS) {
                                            Modifier.blur(36.dp)
                                        } else {
                                            Modifier.blur(20.dp)
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            corner = 0.dp,
                            decodeSizePx = 500,
                            transformations = staticBlurTransform,
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
                        val bgPulse = 1f
                        val bgShiftX = 0f
                        val bgShiftY = 0f
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        0f to ambientColor.copy(alpha = 0.62f),
                                        0.45f to ambientColor.copy(alpha = 0.24f),
                                        1f to Color.Transparent,
                                        center = androidx.compose.ui.geometry.Offset(bgWidth * (0.25f + bgShiftX), bgHeight * (0.20f + bgShiftY)),
                                        radius = bgMaxDimension * 0.85f * bgPulse,
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        0f to ambientCompanion.copy(alpha = 0.55f),
                                        0.50f to ambientCompanion.copy(alpha = 0.22f),
                                        1f to Color.Transparent,
                                        center = androidx.compose.ui.geometry.Offset(bgWidth * (0.88f - bgShiftX), bgHeight * (0.65f - bgShiftY)),
                                        radius = bgMaxDimension * 0.80f * bgPulse,
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        0f to ambientDeep.copy(alpha = 0.45f),
                                        0.55f to ambientDeep.copy(alpha = 0.16f),
                                        1f to Color.Transparent,
                                        center = androidx.compose.ui.geometry.Offset(bgWidth * (0.15f + bgShiftY), bgHeight * (0.82f + bgShiftX)),
                                        radius = bgMaxDimension * 0.72f * bgPulse,
                                    )
                                )
                        )
                    }
                )
                // Contrast scrim gradient (richer clarity and vibrance)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to Color.Black.copy(alpha = 0.25f),
                                0.30f to Color.Black.copy(alpha = 0.10f),
                                0.66f to Color.Black.copy(alpha = 0.36f),
                                1.00f to Color.Black.copy(alpha = 0.68f),
                            )
                        )
                )
                // Subtle edge vignette
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                0f to Color.Transparent,
                                0.65f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.30f),
                                center = androidx.compose.ui.geometry.Offset(
                                    bgWidth * 0.50f,
                                    bgHeight * 0.40f,
                                ),
                                radius = bgMaxDimension * 0.80f,
                            ),
                        ),
                )
                if (showFullBleed) {
                    val heroHeight = if (heroBottomPx > 0f) {
                        with(LocalDensity.current) { heroBottomPx.toDp() }
                    } else {
                        with(LocalDensity.current) { (bgHeight * 0.54f).toDp() }
                    }
                    val heroAlpha by animateFloatAsState(
                        targetValue = if (currentTab == FullPlayerTab.NOW_PLAYING) 1f else 0f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        label = "heroAlpha",
                    )
                    if (heroAlpha > 0.001f) {
                        StaticArtworkHero(
                            name = track.title,
                            artist = track.artist,
                            embeddedUrl = track.artworkUrl,
                            resolvedUrl = resolvedAmbientUrl,
                            bottomFade = 0.42f,
                            alpha = heroAlpha,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(heroHeight),
                        )
                    }
                    if (activeCanvas != null) {
                        CanvasArtworkPlayer(
                            canvas = activeCanvas,
                            isPlaying = state.isPlaying,
                            contentMode = CanvasContentMode.CROP,
                            alignPortraitTop = true,
                            bottomFade = 0.38f,
                            presentationAlpha = { heroAlpha },
                            onAspectRatioChanged = { canvasAspect = it },
                            onRenderedChanged = { canvasRendered = it },
                            pausedForTransition = isVideoMode || shownDismissY > 0f || currentTab != FullPlayerTab.NOW_PLAYING,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(heroHeight),
                        )
                    }
                }

                // Lyrics-only readability veil: heavy blur still can't tame a
                // bright face behind small text, so fade in extra dim on the
                // lyrics tab. Now Playing tab is untouched.
                val lyricsVeil by animateFloatAsState(
                    targetValue = if (currentTab == FullPlayerTab.LYRICS) 1f else 0f,
                    animationSpec = tween(350),
                    label = "lyricsVeil",
                )
                if (lyricsVeil > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.00f to Color.Black.copy(alpha = 0.62f * lyricsVeil),
                                    0.35f to Color.Black.copy(alpha = 0.52f * lyricsVeil),
                                    0.70f to Color.Black.copy(alpha = 0.58f * lyricsVeil),
                                    1.00f to Color.Black.copy(alpha = 0.72f * lyricsVeil),
                                )
                            )
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Header: slimmer, calmer, premium ─────────────────────
                if (!lyricsFullscreen && !isVideoFullscreen) Box(
                    Modifier
                        .fillMaxWidth()
                        .adaptiveContentWidth(maxWidth = if (isLandscape) 1100.dp else 640.dp)
                        .height(if (isLandscape) 44.dp else 52.dp)
                        .padding(horizontal = 20.dp)
                        .playerVerticalSwipe(enabled = currentTab != FullPlayerTab.NOW_PLAYING),
                ) {
                    // FullPlayer sits on a dark blurred-artwork scrim in both light and
                    // dark mode, so foreground must use the white overlay palette —
                    // never MaterialTheme onSurface (near-black in light mode).
                    LiquidGlassIconButton(
                        onClick = {
                            if (isVideoMode) {
                                closeVideoMode()
                            } else if (currentTab != FullPlayerTab.NOW_PLAYING) {
                                onTabChange(FullPlayerTab.NOW_PLAYING)
                            } else {
                                onCollapse()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp),
                        shape = CircleShape,
                        contentDescription = if (currentTab != FullPlayerTab.NOW_PLAYING) "Back to player" else "Minimize player",
                    ) {
                        Icon(
                            if (currentTab != FullPlayerTab.NOW_PLAYING) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = Color.White.copy(alpha = 0.94f),
                        )
                    }
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when (currentTab) {
                                FullPlayerTab.NOW_PLAYING -> "NOW PLAYING"
                                FullPlayerTab.LYRICS -> "LYRICS"
                                FullPlayerTab.QUEUE -> "PLAYING QUEUE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.9.sp,
                        )
                        Text(
                            if (currentTab == FullPlayerTab.LYRICS) {
                                "${track.title} • ${track.artist}"
                            } else {
                                state.sourceLabel.takeIf { it.isNotBlank() } ?: "Neon Song"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (currentTab == FullPlayerTab.LYRICS && onSetLyricsOffsetMs != null) {
                            LiquidGlassIconButton(
                                onClick = { showLyricsOffsetDialog = true },
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                accentTint = if (lyricsOffsetMs != 0L) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                contentDescription = "Lyrics sync offset",
                            ) {
                                Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (lyricsOffsetMs != 0L) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.94f),
                                )
                            }
                        }
                        val isCustomAudio = state.speed != 1f || state.pitch != 1f || state.autoMixState != AutoMixState.IDLE || state.isAutoMixEnabled
                        LiquidGlassIconButton(
                            onClick = {
                                hapticManager.click()
                                showAudioControls = true
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            accentTint = if (isCustomAudio) Color(0xFF8B5CF6) else Color.Unspecified,
                            contentDescription = "Auto DJ & Audio Studio",
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isCustomAudio) Color(0xFFDDD6FE) else Color.White.copy(alpha = 0.94f),
                                )
                                if (isCustomAudio) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .size(5.dp)
                                            .background(Color(0xFF8B5CF6), CircleShape)
                                    )
                                }
                            }
                        }
                        LiquidGlassIconButton(
                            onClick = { showTrackMenu = true },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            contentDescription = "Song options",
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.White.copy(alpha = 0.94f),
                            )
                        }
                    }
                }

                if (!lyricsFullscreen) Spacer(Modifier.height(6.dp))

                AnimatedContent(
                    targetState = currentTab,
                    modifier = Modifier.weight(1f).adaptiveContentWidth(maxWidth = if (isLandscape) 1100.dp else 680.dp),
                    transitionSpec = {
                        // Matched fade durations keep both layers visible
                        // throughout the crossfade so the background never
                        // flashes through between cover and canvas/lyrics.
                        val dur = 280
                        if (targetState != FullPlayerTab.NOW_PLAYING) {
                            (slideInVertically(tween(dur, easing = FastOutSlowInEasing)) { it / 12 } +
                                fadeIn(tween(dur))) togetherWith
                                (slideOutVertically(tween(dur, easing = FastOutSlowInEasing)) { -it / 12 } +
                                    fadeOut(tween(dur)))
                        } else {
                            (slideInVertically(tween(dur, easing = FastOutSlowInEasing)) { -it / 12 } +
                                fadeIn(tween(dur))) togetherWith
                                (slideOutVertically(tween(dur, easing = FastOutSlowInEasing)) { it / 12 } +
                                    fadeOut(tween(dur)))
                        }
                    },
                    label = "playerTabContent",
                ) { tab ->
                    when (tab) {
                        FullPlayerTab.LYRICS -> {
                            PredictiveBackScreen(
                                enabled = currentTab == FullPlayerTab.LYRICS,
                                backgroundColor = Color.Transparent,
                                onBack = {
                                    if (lyricsFullscreen) {
                                        lyricsFullscreen = false
                                    } else {
                                        onTabChange(FullPlayerTab.NOW_PLAYING)
                                    }
                                },
                            ) {
                                if (lyricsUiVersion == LyricsUiVersion.MODERN) {
                                    ModernLyricsPanel(
                                        state = state,
                                        player = player,
                                        lyricsState = lyricsState,
                                        progressState = progressState,
                                        wavySeekbarEnabled = wavySeekbarEnabled,
                                        lyricsOffsetMs = lyricsOffsetMs,
                                        lyricsFontScale = lyricsFontScale,
                                        onLyricsFontScaleChange = onSetLyricsFontScale ?: {},
                                        onRetry = onRetryLyrics,
                                        onToggleFullscreen = { lyricsFullscreen = !lyricsFullscreen },
                                        isFullscreen = lyricsFullscreen,
                                        onOpenLyricsOffset = { showLyricsOffsetDialog = true },
                                        primaryColor = ambientColor,
                                        secondaryColor = ambientCompanion,
                                        tertiaryColor = ambientDeep,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .adaptiveContentWidth(maxWidth = 720.dp),
                                    )
                                } else {
                                    LyricsPanel(
                                        state = state,
                                        progressState = progressState,
                                        player = player,
                                        lyricsState = lyricsState,
                                        lyricsAnimation = lyricsAnimation,
                                        wavySeekbarEnabled = wavySeekbarEnabled,
                                        lyricsOffsetMs = lyricsOffsetMs,
                                        lyricsFontScale = lyricsFontScale,
                                        onLyricsFontScaleChange = onSetLyricsFontScale ?: {},
                                        onRetry = onRetryLyrics,
                                        onToggleFullscreen = { lyricsFullscreen = !lyricsFullscreen },
                                        isFullscreen = lyricsFullscreen,
                                        onOpenLyricsOffset = { showLyricsOffsetDialog = true },
                                        primaryColor = ambientColor,
                                        secondaryColor = ambientCompanion,
                                        tertiaryColor = ambientDeep,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .adaptiveContentWidth(maxWidth = 720.dp),
                                    )
                                }
                            }
                        }

                        FullPlayerTab.QUEUE -> {
                            PredictiveBackScreen(
                                enabled = currentTab == FullPlayerTab.QUEUE,
                                backgroundColor = Color.Transparent,
                                onBack = { onTabChange(FullPlayerTab.NOW_PLAYING) },
                            ) {
                                QueuePanel(
                                    state = state,
                                    player = player,
                                    ambientColor = ambientColor,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .adaptiveContentWidth(maxWidth = 720.dp)
                                        .padding(horizontal = 14.dp),
                                )
                            }
                        }

                        FullPlayerTab.NOW_PLAYING -> {
                                // ── Standard layout (lifted and balanced) ──────
                                var isScrubbing by remember { mutableStateOf(false) }
                                val rawSpatialTilt by rememberSpatialTilt(enabled = currentTab == FullPlayerTab.NOW_PLAYING)
                                val spatialTiltX = animateFloatAsState(
                                    targetValue = rawSpatialTilt.x * 5.5f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        visibilityThreshold = 0.08f,
                                    ),
                                    label = "spatialTiltX",
                                )
                                val spatialTiltY = animateFloatAsState(
                                    targetValue = rawSpatialTilt.y * 4.5f,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        visibilityThreshold = 0.08f,
                                    ),
                                    label = "spatialTiltY",
                                )
                                val artworkContent = @Composable { artworkMod: Modifier ->
                                    val hasArtwork = !track.artworkUrl.isNullOrBlank() || !resolvedAmbientUrl.isNullOrBlank() || activeCanvas != null
                                    val sleeveAlpha by animateFloatAsState(
                                        targetValue = if (showFullBleed && activeCanvas != null) 0f else 1f,
                                        animationSpec = tween(350),
                                        label = "sleeveAlpha",
                                    )

                                    BoxWithConstraints(
                                        modifier = artworkMod
                                            .onGloballyPositioned { coordinates ->
                                                val rootPos = coordinates.positionInRoot()
                                                val bottom = rootPos.y + coordinates.size.height
                                                if (bottom > 0f && bottom != heroBottomPx) {
                                                    heroBottomPx = bottom
                                                }
                                            }
                                            .pointerInput(track.videoId, track.title) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    var isDrag = false
                                                    val touchSlop = viewConfiguration.touchSlop
                                                    val initialX = down.position.x
                                                    val initialY = down.position.y

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                        if (!change.pressed) {
                                                            if (isDrag) {
                                                                when {
                                                                    artworkDragX < -swipeThreshold -> {
                                                                        if (isVideoMode) closeVideoMode()
                                                                        player.next()
                                                                    }
                                                                    artworkDragX > swipeThreshold -> {
                                                                        if (isVideoMode) closeVideoMode()
                                                                        player.previous()
                                                                    }
                                                                }
                                                                artworkDragX = 0f
                                                            } else {
                                                                val now = SystemClock.elapsedRealtime()
                                                                val side = when {
                                                                    initialX < size.width * 0.34f -> SeekDirection.REWIND
                                                                    initialX > size.width * 0.66f -> SeekDirection.FORWARD
                                                                    else -> null
                                                                }

                                                                if (side == null) {
                                                                    // The center third owns Like only. Clear any pending
                                                                    // side sequence so it can never complete a seek.
                                                                    lastTapSide = null
                                                                    lastTapTimestamp = 0L
                                                                    if (lastLikeTapTimestamp != 0L && now - lastLikeTapTimestamp < 450L) {
                                                                        lastLikeTapTimestamp = 0L
                                                                        onDoubleTapLike()
                                                                    } else {
                                                                        lastLikeTapTimestamp = now
                                                                    }
                                                                } else {
                                                                    // Preserve the existing edge double-tap seek behavior.
                                                                    // An edge tap cannot complete a center Like sequence.
                                                                    lastLikeTapTimestamp = 0L
                                                                    if (lastTapSide != side) {
                                                                        seekResetJob?.cancel()
                                                                        seekOverlayDirection = null
                                                                        lastTapSide = side
                                                                        lastTapTimestamp = now
                                                                    } else if (now - lastTapTimestamp < 450L) {
                                                                        val newSeconds = if (seekOverlayDirection == side) seekOverlaySeconds + 5 else 5
                                                                        seekOverlaySeconds = newSeconds
                                                                        seekOverlayDirection = side
                                                                        lastTapTimestamp = now
                                                                        val deltaMs = if (side == SeekDirection.FORWARD) 5_000L else -5_000L
                                                                        if (isVideoMode) {
                                                                            val maxDur = if (videoDurationMs > 0L) videoDurationMs else (track.durationMs ?: state.durationMs).coerceAtLeast(0L)
                                                                            val newPos = (videoPositionMs + deltaMs).coerceIn(0L, maxDur)
                                                                            videoPositionMs = newPos
                                                                            videoSeekCommandSeconds = newPos / 1000f
                                                                        } else {
                                                                            val newPos = (player.state.value.positionMs + deltaMs).coerceIn(0L, player.state.value.durationMs.coerceAtLeast(0L))
                                                                            player.seekTo(newPos)
                                                                        }

                                                                        seekResetJob?.cancel()
                                                                        seekResetJob = coroutineScope.launch {
                                                                            delay(700L)
                                                                            seekOverlayDirection = null
                                                                            lastTapSide = null
                                                                        }
                                                                    } else {
                                                                        lastTapTimestamp = now
                                                                        lastTapSide = side
                                                                        seekOverlayDirection = null
                                                                    }
                                                                }
                                                            }
                                                            break
                                                        }

                                                        if (change.isConsumed) {
                                                            artworkDragX = 0f
                                                            lastLikeTapTimestamp = 0L
                                                            lastTapTimestamp = 0L
                                                            lastTapSide = null
                                                            break
                                                        }

                                                        val dx = change.position.x - initialX
                                                        val dy = change.position.y - initialY
                                                        if (!isDrag) {
                                                            if (kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                                                isDrag = true
                                                                lastLikeTapTimestamp = 0L
                                                                lastTapTimestamp = 0L
                                                                lastTapSide = null
                                                                change.consume()
                                                            }
                                                        } else {
                                                            change.consume()
                                                            artworkDragX = dx
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isVideoMode && hasVideo) {
                                            val maxVidW = minOf(maxWidth, if (isLandscape) ((maxHeight - 20.dp) * 16f / 9f) else 420.dp)
                                            val videoCardWidth = (maxVidW - 8.dp).coerceAtLeast(240.dp)
                                            Box(
                                                modifier = Modifier
                                                    .width(videoCardWidth)
                                                    .aspectRatio(16f / 9f)
                                                    .align(Alignment.Center),
                                            ) {
                                                // Authentic optical glass contact drop shadow
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            scaleX = 1.01f
                                                            scaleY = 1.01f
                                                            translationY = 8f
                                                        }
                                                        .background(
                                                            Brush.radialGradient(
                                                                listOf(
                                                                    Color.Black.copy(alpha = 0.55f),
                                                                    Color.Black.copy(alpha = 0.20f),
                                                                    Color.Transparent,
                                                                )
                                                            ),
                                                            shape = RoundedCornerShape(26.dp),
                                                        ),
                                                )
                                                val videoGlassBorder = Brush.linearGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = 0.32f),
                                                        Color.White.copy(alpha = 0.08f),
                                                        Color.White.copy(alpha = 0.20f),
                                                    ),
                                                    start = Offset(0f, 0f),
                                                    end = Offset(400f, 400f),
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(24.dp),
                                                    color = Color.Black,
                                                    border = BorderStroke(1.2.dp, videoGlassBorder),
                                                    shadowElevation = 18.dp,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                                                ) {
                                                    Box(Modifier.fillMaxSize()) {
                                                        if (!isVideoFullscreen) {
                                                            InAppMusicVideo(
                                                                videoId = effectiveVideoId.orEmpty(),
                                                                modifier = Modifier.fillMaxSize(),
                                                                isPlaying = isVideoPlaying,
                                                                initialPositionSeconds = (videoPositionMs.takeIf { it > 0L } ?: player.state.value.positionMs)
                                                                    .let { (it / 1000f).coerceAtLeast(0f) },
                                                                seekCommandSeconds = videoSeekCommandSeconds,
                                                                onProgressUpdate = { curMs, durMs ->
                                                                    videoPositionMs = curMs
                                                                    if (durMs > 0L) videoDurationMs = durMs
                                                                },
                                                                onPlaybackStateChanged = { playing ->
                                                                    isVideoPlaying = playing
                                                                },
                                                                onVideoEnded = {
                                                                    closeVideoMode()
                                                                    player.next()
                                                                },
                                                            )
                                                        }
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color.Black.copy(alpha = 0.52f),
                                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(10.dp)
                                                                .size(38.dp),
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    hapticManager.click()
                                                                    isVideoFullscreen = true
                                                                },
                                                                modifier = Modifier.fillMaxSize(),
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Fullscreen,
                                                                    contentDescription = "Expand fullscreen video",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(20.dp),
                                                                )
                                                            }
                                                        }
                                                        Surface(
                                                            shape = RoundedCornerShape(100),
                                                            color = Color.Black.copy(alpha = 0.52f),
                                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                                            modifier = Modifier
                                                                .align(Alignment.BottomStart)
                                                                .padding(10.dp),
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(6.dp)
                                                                        .background(Color(0xFFFF0033), CircleShape),
                                                                )
                                                                Text(
                                                                    text = "HD Video",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                                    color = Color.White.copy(alpha = 0.95f),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val artworkSize = (minOf(maxWidth, maxHeight) - if (isLandscape) 28.dp else 8.dp)
                                                .coerceAtLeast(0.dp)
                                                .coerceAtMost(if (isLandscape) 275.dp else 370.dp)

                                        if (sleeveAlpha > 0.001f) {
                                            val ambientHaloBrush = remember(ambientColor, ambientCompanion, ambientDeep) {
                                                Brush.radialGradient(
                                                    0.0f to ambientColor.copy(alpha = 0.62f),
                                                    0.48f to ambientCompanion.copy(alpha = 0.28f),
                                                    0.82f to ambientDeep.copy(alpha = 0.14f),
                                                    1.0f to Color.Transparent,
                                                )
                                            }
                                            val shadowOcclusionBrush = remember {
                                                Brush.radialGradient(
                                                    listOf(
                                                        Color.Black.copy(alpha = 0.80f),
                                                        Color.Black.copy(alpha = 0.40f),
                                                        Color.Transparent,
                                                    )
                                                )
                                            }
                                            val glowAlpha by animateFloatAsState(
                                                targetValue = if (state.isPlaying) 0.70f else 0.35f,
                                                animationSpec = tween(600),
                                                label = "artworkGlowAlpha",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(artworkSize + 56.dp)
                                                    .graphicsLayer {
                                                        scaleX = 1f
                                                        scaleY = 1f
                                                        // Inverse parallax: halo moves in the opposite direction from the floating card
                                                        translationX = shownArtworkX * 0.7f - (spatialTiltX.value * 0.9f)
                                                        translationY = -(spatialTiltY.value * 0.7f)
                                                        alpha = glowAlpha * sleeveAlpha
                                                    }
                                                    .background(
                                                        ambientHaloBrush,
                                                        shape = CircleShape,
                                                    ),
                                            )

                                            val targetArtworkScale = when {
                                                isScrubbing -> 0.84f
                                                state.isPlaying -> 1.0f
                                                else -> 0.88f
                                            }
                                            val artworkPlayingScale by animateFloatAsState(
                                                targetValue = targetArtworkScale,
                                                animationSpec = ExpressiveMotion.appleSheetSpring(),
                                                label = "artworkPlayingScale",
                                            )

                                            // 3D Ambient Contact Occlusion Drop Shadow Layer
                                            Box(
                                                modifier = Modifier
                                                    .size(artworkSize - 10.dp)
                                                    .graphicsLayer {
                                                        scaleX = artworkPlayingScale
                                                        scaleY = artworkPlayingScale
                                                        translationX = shownArtworkX + (spatialTiltX.value * 0.4f)
                                                        translationY = 16f + (spatialTiltY.value * 0.5f)
                                                        alpha = (if (state.isPlaying) 0.65f else 0.40f) * sleeveAlpha
                                                    }
                                                    .background(
                                                        shadowOcclusionBrush,
                                                        shape = RoundedCornerShape(36.dp),
                                                    )
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(32.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f * sleeveAlpha),
                                                tonalElevation = 10.dp * sleeveAlpha,
                                                shadowElevation = if (state.isPlaying) 42.dp * sleeveAlpha else 18.dp * sleeveAlpha,
                                                modifier = Modifier
                                                    .size(artworkSize)
                                                    .graphicsLayer {
                                                        scaleX = artworkPlayingScale
                                                        scaleY = artworkPlayingScale
                                                        // Parallax translation forward - minimalist & subtle
                                                        translationX = shownArtworkX + (spatialTiltX.value * 0.75f)
                                                        translationY = spatialTiltY.value * 0.55f
                                                        // 3D perspective rotation (pitch and roll) - minimalist clamp
                                                        rotationZ = (shownArtworkX / 75f) + (spatialTiltX.value * 0.10f)
                                                        rotationY = (spatialTiltX.value - (shownArtworkX / 45f)).coerceIn(-6f, 6f)
                                                        rotationX = (-spatialTiltY.value).coerceIn(-5f, 5f)
                                                        cameraDistance = 18f * density
                                                        alpha = sleeveAlpha
                                                    }
                                                    .drawWithContent {
                                                        drawContent()
                                                        val tiltX = spatialTiltX.value
                                                        val tiltY = spatialTiltY.value
                                                        // 3D Liquid Glass Beveled Prism Border reacting dynamically to device tilt
                                                        val prismBorder = Brush.linearGradient(
                                                            listOf(
                                                                Color.White.copy(alpha = (0.65f + (tiltX * 0.02f)).coerceIn(0.25f, 0.90f)),
                                                                Color.White.copy(alpha = 0.20f),
                                                                ambientCompanion.copy(alpha = (0.35f + (tiltY * 0.02f)).coerceIn(0.15f, 0.65f)),
                                                                Color.Black.copy(alpha = 0.25f),
                                                                ambientColor.copy(alpha = (0.45f - (tiltY * 0.02f)).coerceIn(0.15f, 0.70f)),
                                                            ),
                                                            start = Offset(tiltX * 16f, tiltY * 16f),
                                                            end = Offset(700f + tiltX * 16f, 700f + tiltY * 16f),
                                                        )
                                                        val strokePx = 1.4.dp.toPx()
                                                        val halfStroke = strokePx / 2f
                                                        drawRoundRect(
                                                            brush = prismBorder,
                                                            topLeft = Offset(halfStroke, halfStroke),
                                                            size = Size(size.width - strokePx, size.height - strokePx),
                                                            cornerRadius = CornerRadius(32.dp.toPx()),
                                                            style = Stroke(width = strokePx),
                                                        )
                                                    },
                                            ) {
                                                Box(Modifier.fillMaxSize()) {
                                                    PlayerArtwork(
                                                        track = track,
                                                        modifier = Modifier.fillMaxSize(),
                                                        corner = 32.dp,
                                                        canvas = if (showSleeveCanvas) canvas else null,
                                                        isPlaying = state.isPlaying,
                                                        pausedForTransition = isVideoMode || shownDismissY > 0f || currentTab != FullPlayerTab.NOW_PLAYING,
                                                        onAspectRatioChanged = { canvasAspect = it },
                                                    )

                                                    // 4D Dynamic Specular Sheen / Vivid Glass Reflection (Lustrous & Prominent)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(RoundedCornerShape(32.dp))
                                                            .drawBehind {
                                                                val tiltX = spatialTiltX.value
                                                                val tiltY = spatialTiltY.value
                                                                val sheenBrush = Brush.linearGradient(
                                                                    0.0f to Color.Transparent,
                                                                    0.32f to Color.White.copy(alpha = 0.04f),
                                                                    0.46f to Color.White.copy(alpha = 0.28f),
                                                                    0.50f to Color.White.copy(alpha = 0.45f),
                                                                    0.54f to Color.White.copy(alpha = 0.25f),
                                                                    0.66f to Color.White.copy(alpha = 0.05f),
                                                                    1.0f to Color.Transparent,
                                                                    start = Offset(tiltX * 36f, tiltY * 30f),
                                                                    end = Offset(620f + tiltX * 36f, 620f + tiltY * 30f),
                                                                )
                                                                drawRect(sheenBrush)
                                                            }
                                                    )

                                                    // Razor-thin 3D Specular Top Rim Light
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(1.5.dp)
                                                            .align(Alignment.TopCenter)
                                                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                                                            .drawBehind {
                                                                val tiltX = spatialTiltX.value
                                                                val tiltY = spatialTiltY.value
                                                                val rimBrush = Brush.horizontalGradient(
                                                                    listOf(
                                                                        Color.Transparent,
                                                                        Color.White.copy(alpha = (0.65f + tiltX * 0.02f).coerceIn(0.3f, 0.85f)),
                                                                        Color.White.copy(alpha = (0.85f + tiltY * 0.02f).coerceIn(0.4f, 0.95f)),
                                                                        Color.White.copy(alpha = 0.40f),
                                                                        Color.Transparent,
                                                                    )
                                                                )
                                                                drawRect(rimBrush)
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                        }

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = seekOverlayDirection == SeekDirection.REWIND,
                                            enter = fadeIn(tween(100)) + scaleIn(ExpressiveMotion.spatialSpring(), initialScale = 0.88f),
                                            exit = fadeOut(tween(200)),
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .fillMaxWidth(0.42f)
                                                .height(130.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 72.dp, bottomEnd = 72.dp))
                                                    .background(Color.Black.copy(alpha = 0.58f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.White.copy(alpha = 0.22f),
                                                        modifier = Modifier.size(52.dp),
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Filled.FastRewind,
                                                                contentDescription = "Seek rewind",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(28.dp),
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        "-${seekOverlaySeconds}s",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                    )
                                                }
                                            }
                                        }

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = seekOverlayDirection == SeekDirection.FORWARD,
                                            enter = fadeIn(tween(100)) + scaleIn(ExpressiveMotion.spatialSpring(), initialScale = 0.88f),
                                            exit = fadeOut(tween(200)),
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxWidth(0.42f)
                                                .height(130.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp, topStart = 72.dp, bottomStart = 72.dp))
                                                    .background(Color.Black.copy(alpha = 0.58f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color.White.copy(alpha = 0.22f),
                                                        modifier = Modifier.size(52.dp),
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Filled.FastForward,
                                                                contentDescription = "Seek forward",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(28.dp),
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        "+${seekOverlaySeconds}s",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                val controlsContent = @Composable { controlsMod: Modifier ->
                                    val verticalGap = if (isLandscape) 5.dp else 12.dp
                                    Column(
                                        modifier = controlsMod
                                            .graphicsLayer {
                                                translationX = spatialTiltX.value * 0.35f
                                                translationY = spatialTiltY.value * 0.20f
                                            },
                                    ) {
                                    val trackInfoShape = RoundedCornerShape(24.dp)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(trackInfoShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = 0.10f),
                                                        Color.White.copy(alpha = 0.03f),
                                                        Color.Black.copy(alpha = 0.24f),
                                                    )
                                                )
                                            )
                                            .drawWithContent {
                                                drawContent()
                                                val tiltX = spatialTiltX.value
                                                val tiltY = spatialTiltY.value
                                                val trackInfoBorderBrush = Brush.linearGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = (0.35f + tiltX * 0.01f).coerceIn(0.18f, 0.55f)),
                                                        Color.White.copy(alpha = 0.08f),
                                                        Color.White.copy(alpha = (0.22f + tiltY * 0.01f).coerceIn(0.10f, 0.40f)),
                                                    ),
                                                    start = Offset(tiltX * 12f, tiltY * 8f),
                                                    end = Offset(600f + tiltX * 12f, 200f + tiltY * 8f),
                                                )
                                                val strokePx = 1.dp.toPx()
                                                val halfStroke = strokePx / 2f
                                                drawRoundRect(
                                                    brush = trackInfoBorderBrush,
                                                    topLeft = Offset(halfStroke, halfStroke),
                                                    size = Size(size.width - strokePx, size.height - strokePx),
                                                    cornerRadius = CornerRadius(24.dp.toPx()),
                                                    style = Stroke(width = strokePx),
                                                )
                                            }
                                            .padding(
                                                horizontal = if (isLandscape) 14.dp else 18.dp,
                                                vertical = if (isLandscape) 6.dp else 12.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                track.title,
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontSize = if (isLandscape) 18.sp else 22.sp,
                                                    letterSpacing = (-0.4).sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                ),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        velocity = 35.dp,
                                                        initialDelayMillis = 900,
                                                    ),
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            val splitArtists = remember(track.artist) {
                                                com.neonsong.app.util.ArtistHelper.splitArtists(track.artist)
                                            }
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                splitArtists.forEachIndexed { index, artName ->
                                                    Text(
                                                        text = artName,
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontSize = if (isLandscape) 13.sp else 15.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        ),
                                                        color = Color.White.copy(alpha = 0.82f),
                                                        modifier = Modifier
                                                            .clickable(
                                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                                indication = null,
                                                            ) { onOpenArtist(artName) },
                                                    )
                                                    if (index < splitArtists.lastIndex) {
                                                        Text(
                                                            text = " • ",
                                                            style = MaterialTheme.typography.titleMedium.copy(
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Normal,
                                                            ),
                                                            color = Color.White.copy(alpha = 0.45f),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.width(14.dp))

                                        val pressHaptics = LocalHapticFeedback.current
                                        val likeInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                        val isLikePressed by likeInteraction.collectIsPressedAsState()
                                        LaunchedEffect(isLikePressed) {
                                            if (isLikePressed) {
                                                pressHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                        val likeHeartPulse by animateFloatAsState(
                                            targetValue = if (isLiked) 1.22f else 1.0f,
                                            animationSpec = ExpressiveMotion.appleBouncySpring(),
                                            label = "likeHeartPulse",
                                        )
                                        val likeScale by animateFloatAsState(
                                            targetValue = if (isLikePressed) 0.90f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = 0.75f,
                                                stiffness = 1800f,
                                            ),
                                            label = "likeScale",
                                        )
                                        LiquidGlassSurface(
                                            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = likeInteraction),
                                            onClick = { hapticManager.toggle(!isLiked); onToggleLiked() },
                                            interactionSource = likeInteraction,
                                            shape = CircleShape,
                                            color = liquidGlassContainerColor(
                                                if (isLiked) {
                                                    Color.White.copy(alpha = 0.95f)
                                                } else {
                                                    Color.White.copy(alpha = 0.12f)
                                                }
                                            ),
                                            contentColor = if (isLiked) {
                                                Color(0xFFFF2D55)
                                            } else {
                                                Color.White.copy(alpha = 0.85f)
                                            },
                                            tonalElevation = 0.dp,
                                            shadowElevation = if (isLiked) 8.dp else 0.dp,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isLiked) Color(0xFFFF2D55).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.18f),
                                            ),
                                            modifier = Modifier
                                                .size(if (isLandscape) 38.dp else 48.dp)
                                                .graphicsLayer {
                                                    scaleX = likeScale
                                                    scaleY = likeScale
                                                },
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                    contentDescription = if (isLiked) "Unlike song" else "Like song",
                                                    modifier = Modifier
                                                        .size(if (isLandscape) 19.dp else 24.dp)
                                                        .graphicsLayer {
                                                            scaleX = likeHeartPulse
                                                            scaleY = likeHeartPulse
                                                        },
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(verticalGap))
                                    val effectiveProgressState = if (isVideoMode) videoProgressFlow else progressState
                                    SeekBar(
                                        progressState = effectiveProgressState,
                                        isPlaying = if (isVideoMode) isVideoPlaying else state.isPlaying,
                                        trackKey = if (isVideoMode) "video_$effectiveVideoId" else (track.videoId ?: "${track.artist}|${track.title}"),
                                        wavyEnabled = wavySeekbarEnabled,
                                        onSeek = { targetMs ->
                                            if (isVideoMode) {
                                                videoPositionMs = targetMs
                                                videoSeekCommandSeconds = targetMs / 1000f
                                            } else {
                                                player.seekTo(targetMs)
                                            }
                                        },
                                        isTranslucent = LocalLiquidGlass.current,
                                        fallbackDurationMs = if (isVideoMode && videoDurationMs > 0L) videoDurationMs else (track.durationMs ?: state.durationMs),
                                        primaryColor = ambientColor,
                                        secondaryColor = ambientCompanion,
                                        tertiaryColor = ambientDeep,
                                        onScrubbingChange = { isScrubbing = it },
                                    )
                                    Spacer(Modifier.height(verticalGap))
                                    MainControls(
                                        state = state,
                                        player = player,
                                        isTranslucent = true,
                                        compact = isLandscape,
                                        overridePlaying = if (isVideoMode) isVideoPlaying else null,
                                        onTogglePlay = if (isVideoMode) { { isVideoPlaying = !isVideoPlaying } } else null,
                                        onNext = if (isVideoMode) { { closeVideoMode(); player.next() } } else null,
                                        onPrev = if (isVideoMode) { { closeVideoMode(); player.previous() } } else null,
                                        ambientColor = ambientColor,
                                        ambientCompanion = ambientCompanion,
                                    )
                                    }
                                    Spacer(Modifier.height(if (isLandscape) 6.dp else 16.dp))
                                    PlayerFloatingDock(
                                        state = state,
                                        player = player,
                                        currentTab = currentTab,
                                        onTabChange = onTabChange,
                                        isVideoMode = isVideoMode,
                                        hasVideo = hasVideo,
                                        onToggleVideo = {
                                            if (isVideoMode) closeVideoMode() else enterVideoMode()
                                        },
                                        onOpenSignalPath = { showSignalPath = true },
                                        onOpenAudioControls = { showAudioControls = true },
                                        compact = isLandscape,
                                    )
                                }

                                if (isLandscape) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                            .padding(horizontal = 20.dp)
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1.05f)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            artworkContent(Modifier.fillMaxSize())
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(1.15f)
                                                .fillMaxHeight(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            controlsContent(Modifier.fillMaxWidth())
                                        }
                                    }
                                } else {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 20.dp)
                                            .padding(bottom = 18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            artworkContent(Modifier.fillMaxSize())
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        controlsContent(Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                state.error?.takeUnless { lyricsFullscreen || isVideoFullscreen }?.let { message ->
                    Surface(
                        onClick = player::retry,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.ErrorOutline, null, modifier = Modifier.size(21.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    )
                                Text(
                                    "Tap to retry",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                )
                            }
                            IconButton(
                                onClick = player::clearError,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Filled.Close, "Dismiss error", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isVideoMode && isVideoFullscreen,
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + scaleIn(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
                    initialScale = 0.92f,
                ),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + scaleOut(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
                    targetScale = 0.94f,
                ),
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(100f),
            ) {
                var fullscreenControlsVisible by remember(isVideoFullscreen) { mutableStateOf(true) }
                LaunchedEffect(fullscreenControlsVisible, isVideoFullscreen) {
                    if (isVideoFullscreen && fullscreenControlsVisible) {
                        delay(2800L)
                        fullscreenControlsVisible = false
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            fullscreenControlsVisible = !fullscreenControlsVisible
                        },
                ) {
                    if (isVideoFullscreen) {
                        InAppMusicVideo(
                            videoId = effectiveVideoId.orEmpty(),
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center),
                            isPlaying = isVideoPlaying,
                            initialPositionSeconds = (videoPositionMs.takeIf { it > 0L } ?: player.state.value.positionMs)
                                .let { (it / 1000f).coerceAtLeast(0f) },
                            seekCommandSeconds = videoSeekCommandSeconds,
                            onProgressUpdate = { curMs, durMs ->
                                videoPositionMs = curMs
                                if (durMs > 0L) videoDurationMs = durMs
                            },
                            onPlaybackStateChanged = { playing ->
                                isVideoPlaying = playing
                            },
                            onVideoEnded = {
                                closeVideoMode()
                                player.next()
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = fullscreenControlsVisible,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(240)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.58f),
                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.30f)),
                            modifier = Modifier.size(46.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    hapticManager.click()
                                    isVideoFullscreen = false
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    Icons.Filled.FullscreenExit,
                                    contentDescription = "Exit fullscreen",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = fullscreenControlsVisible,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(240)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(bottom = 20.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100),
                            color = Color.Black.copy(alpha = 0.62f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                            contentColor = Color.White.copy(alpha = 0.88f),
                        ) {
                            Text(
                                text = "Full Screen • Tap back or exit to return to player",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showTrackMenu) {
        TrackContextMenuSheet(
            target = TrackMenuTarget.Track(track.title, track.artist, ""),
            capabilities = TrackMenuCapabilities(
                showCopyActions = true,
                showDeleteScrobble = false,
            ),
            playableTrack = track,
            onDismiss = { showTrackMenu = false },
            onPlayInNeonSong = { player.play(track, sourceLabel = state.sourceLabel) },
        )
    }
    if (showLyricsOffsetDialog && onSetLyricsOffsetMs != null) {
        LyricsOffsetDialog(
            currentMs = lyricsOffsetMs,
            onSelect = onSetLyricsOffsetMs,
            onDismiss = { showLyricsOffsetDialog = false },
        )
    }
    if (showSignalPath) {
        val usbDac by player.usbDacState.collectAsStateWithLifecycle()
        val dac = usbDac.dac
        SignalPathDialog(
            report = signalPath,
            needsUsbPermission = dac != null && dac.hasUsbPeripheral && !dac.usbPermissionGranted,
            onRequestUsbAccess = player::requestUsbPermission,
            onDismiss = { showSignalPath = false },
        )
    }
    if (showAudioControls) {
        AudioControlsSheet(
            player = player,
            state = state,
            onDismiss = { showAudioControls = false },
        )
    }
}

}
@Composable
internal fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val primary = primaryColor
    val tertiary = tertiaryColor
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.20f else 0.12f)
    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    // Playback progress is time, not directional content: pin to LTR so the
    // Material Slider's touch mapping always matches the LTR custom drawing,
    // even in RTL locales (Arabic, …) where Slider would otherwise mirror.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.drawBehind {
            val inset = 10.dp.toPx()
            val startX = inset
            val endX = (size.width - inset).coerceAtLeast(startX)
            val activeEndX = startX + ((endX - startX) * fraction)
            val centerY = size.height / 2f
            drawLine(
                color = inactive,
                start = androidx.compose.ui.geometry.Offset(startX, centerY),
                end = androidx.compose.ui.geometry.Offset(endX, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (activeEndX > startX) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            primary.copy(alpha = if (enabled) 1f else 0.42f),
                            tertiary.copy(alpha = if (enabled) 0.92f else 0.36f),
                        ),
                        startX = startX,
                        endX = activeEndX,
                    ),
                    start = androidx.compose.ui.geometry.Offset(startX, centerY),
                    end = androidx.compose.ui.geometry.Offset(activeEndX, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        },
        colors = SliderDefaults.colors(
            thumbColor = primary,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
            disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
            disabledActiveTrackColor = Color.Transparent,
            disabledInactiveTrackColor = Color.Transparent,
            disabledActiveTickColor = Color.Transparent,
            disabledInactiveTickColor = Color.Transparent,
        ),
    )
    }
}

@Composable
private fun SeekBar(
    progressState: StateFlow<PlaybackProgressState>,
    isPlaying: Boolean,
    trackKey: String?,
    wavyEnabled: Boolean = true,
    onSeek: (Long) -> Unit,
    isTranslucent: Boolean = false,
    fallbackDurationMs: Long = 0L,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onScrubbingChange: ((Boolean) -> Unit)? = null,
) {
    val progress by progressState.collectAsStateWithLifecycle()
    val effectiveDurationMs = if (progress.durationMs > 0L) progress.durationMs else fallbackDurationMs.coerceAtLeast(0L)

    if (wavyEnabled) {
        WavySeekBar(
            positionMs = progress.positionMs,
            durationMs = effectiveDurationMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            isTranslucent = isTranslucent,
            trackKey = trackKey,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            onScrubbingChange = onScrubbingChange,
        )
        return
    }

    // Current-gesture fraction only; null = finger off, show live position.
    // Nullable (never a stale 0f) so a press without movement seeks nowhere
    // and a gesture that ends without onValueChangeFinished can't pin the bar.
    val seekInteraction = remember(trackKey) { MutableInteractionSource() }
    var isInteracting by remember(trackKey) { mutableStateOf(false) }
    LaunchedEffect(isInteracting) {
        onScrubbingChange?.invoke(isInteracting)
    }
    LaunchedEffect(seekInteraction, trackKey) {
        var dragCount = 0
        var pressCount = 0
        seekInteraction.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragCount++
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragCount = maxOf(0, dragCount - 1)
                is PressInteraction.Press -> pressCount++
                is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = maxOf(0, pressCount - 1)
            }
            isInteracting = dragCount > 0 || pressCount > 0
        }
    }
    var dragFraction by remember(trackKey) { mutableStateOf<Float?>(null) }
    var lastSeekFraction by remember(trackKey) { mutableStateOf<Float?>(null) }
    // Heal: framework reports finger lifted or cancelled but the finished callback
    // never ran -> drop the dead value and resume live position.
    LaunchedEffect(isInteracting, trackKey) {
        if (!isInteracting) {
            delay(120L)
            dragFraction = null
            lastSeekFraction = null
        }
    }
    // Watchdog: if dragFraction was set by a tap/cancelled gesture while not interacting,
    // ensure it never permanently wedges the seekbar.
    LaunchedEffect(dragFraction, isInteracting, trackKey) {
        if (dragFraction != null && !isInteracting) {
            delay(250L)
            dragFraction = null
            lastSeekFraction = null
        }
    }

    val boundedDurationMs = effectiveDurationMs.coerceAtLeast(0L)
    val currentFraction = if (boundedDurationMs > 0L) {
        (progress.positionMs.toDouble() / boundedDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction = (dragFraction ?: currentFraction).coerceIn(0f, 1f)
    val shownMs = dragFraction?.let {
        (it * boundedDurationMs).toLong().coerceIn(0L, boundedDurationMs)
    } ?: progress.positionMs.coerceIn(0L, boundedDurationMs)

    val effectivePrimary = primaryColor
    val inactiveColor = if (isTranslucent) {
        Color.White.copy(alpha = 0.20f)
    } else {
        effectivePrimary.copy(alpha = 0.28f)
    }
    val textColor = if (isTranslucent) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)

    // Same RTL pin as above: custom Canvas draws LTR, invisible Slider must match it.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f
                val trackHeightPx = 3.2.dp.toPx()
                val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
                val thumbRadiusPx = 5.2.dp.toPx()

                // Inactive full background track capsule
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(width, trackHeightPx),
                    cornerRadius = cornerRadius,
                )

                // Active glowing baseline track capsule
                val activeEndX = (fraction * width).coerceIn(0f, width)
                if (activeEndX > 0f) {
                    drawRoundRect(
                        color = effectivePrimary,
                        topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                        size = Size(activeEndX, trackHeightPx),
                        cornerRadius = cornerRadius,
                    )
                }

                // Sleek jewel bead thumb with soft neon halo
                val thumbCenterX = (fraction * width).coerceIn(thumbRadiusPx, width - thumbRadiusPx)
                drawCircle(
                    color = effectivePrimary.copy(alpha = 0.32f),
                    radius = thumbRadiusPx + 4.dp.toPx(),
                    center = Offset(thumbCenterX, centerY),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = thumbRadiusPx,
                    center = Offset(thumbCenterX, centerY),
                )
                drawCircle(
                    color = effectivePrimary,
                    radius = (thumbRadiusPx - 1.2.dp.toPx()).coerceAtLeast(1f),
                    center = Offset(thumbCenterX, centerY),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 1.0.dp.toPx(),
                    center = Offset(thumbCenterX - thumbRadiusPx * 0.32f, centerY - thumbRadiusPx * 0.32f),
                )
            }

            // Invisible Material interaction layer: custom visuals, reliable seeking semantics.
            Slider(
                value = fraction,
                onValueChange = {
                    dragFraction = it
                    lastSeekFraction = it
                },
                onValueChangeFinished = {
                    // Commit only this gesture's value; no value = no seek.
                    val target = (lastSeekFraction ?: dragFraction)?.let {
                        (it * boundedDurationMs).toLong().coerceIn(0L, boundedDurationMs)
                    }
                    dragFraction = null
                    lastSeekFraction = null
                    if (target != null && boundedDurationMs > 0L) {
                        onSeek(target)
                    }
                },
                valueRange = 0f..1f,
                enabled = boundedDurationMs > 0L,
                interactionSource = seekInteraction,
                modifier = Modifier.fillMaxSize(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                ),
            )
        }

        Spacer(Modifier.height(2.dp))

        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(shownMs),
                style = if (isTranslucent) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.labelMedium,
                color = textColor,
            )
            Text(
                text = "−${formatTime((boundedDurationMs - shownMs).coerceAtLeast(0))}",
                style = if (isTranslucent) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
    }
}

@Composable
private fun MainControls(
    state: MusicPlayerState,
    player: MusicPlayer,
    isTranslucent: Boolean = false,
    compact: Boolean = false,
    overridePlaying: Boolean? = null,
    onTogglePlay: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    ambientColor: Color = MaterialTheme.colorScheme.primary,
    ambientCompanion: Color = MaterialTheme.colorScheme.secondary,
) {
    val hapticManager = LocalHapticManager.current
    val effectivePlaying = overridePlaying ?: state.isPlaying

    val sideButtonSize = if (compact) 40.dp else 46.dp
    val skipButtonSize = if (compact) 48.dp else 54.dp
    val playButtonSize = if (compact) 62.dp else 72.dp
    val sideIconSize = if (compact) 18.dp else 20.dp
    val skipIconSize = if (compact) 24.dp else 28.dp
    val playIconSize = if (compact) 30.dp else 34.dp

    val shuffleInteraction = remember { MutableInteractionSource() }
    val isShufflePressed by shuffleInteraction.collectIsPressedAsState()
    val shuffleScale by animateFloatAsState(if (isShufflePressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "shuffleScale")

    val prevInteraction = remember { MutableInteractionSource() }
    val isPrevPressed by prevInteraction.collectIsPressedAsState()
    val prevScale by animateFloatAsState(if (isPrevPressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "prevScale")

    val playInteraction = remember { MutableInteractionSource() }
    val isPlayPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.91f else 1.0f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "playScale",
    )

    val nextInteraction = remember { MutableInteractionSource() }
    val isNextPressed by nextInteraction.collectIsPressedAsState()
    val nextScale by animateFloatAsState(if (isNextPressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "nextScale")

    val repeatInteraction = remember { MutableInteractionSource() }
    val isRepeatPressed by repeatInteraction.collectIsPressedAsState()
    val repeatScale by animateFloatAsState(if (isRepeatPressed) 0.90f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "repeatScale")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Shuffle Button
        val isShuffleActive = state.shuffleEnabled
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = shuffleInteraction),
            onClick = {
                hapticManager.click()
                player.toggleShuffle()
            },
            interactionSource = shuffleInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(if (isShuffleActive) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f)),
            contentColor = if (isShuffleActive) Color.White else Color.White.copy(alpha = 0.65f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (isShuffleActive) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.10f)
            ),
            modifier = Modifier
                .size(sideButtonSize)
                .graphicsLayer {
                    scaleX = shuffleScale
                    scaleY = shuffleScale
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Shuffle, "Shuffle", Modifier.size(sideIconSize))
                if (isShuffleActive) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp)
                            .size(4.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        // 2. Previous Track
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = prevInteraction),
            onClick = {
                hapticManager.click()
                if (onPrev != null) onPrev() else player.previous()
            },
            interactionSource = prevInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(Color.White.copy(alpha = 0.12f)),
            contentColor = Color.White.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .size(skipButtonSize)
                .graphicsLayer {
                    scaleX = prevScale
                    scaleY = prevScale
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(skipIconSize))
            }
        }

        // 3. Center Play/Pause Luminous Dial
        Box(
            modifier = Modifier.size(playButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            if (effectivePlaying) {
                val playGlowPulse = 1.01f
                val playGlowAlpha = 0.65f
                // Wide outer ambient bloom (layer-scaled, constant layout bounds)
                val outerGlowSize = if (compact) 92.dp else 110.dp
                Box(
                    modifier = Modifier
                        .size(outerGlowSize)
                        .graphicsLayer {
                            scaleX = playGlowPulse
                            scaleY = playGlowPulse
                            alpha = playGlowAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ambientColor.copy(alpha = 0.35f),
                                    ambientCompanion.copy(alpha = 0.15f),
                                    Color.Transparent,
                                )
                            ),
                            CircleShape,
                        )
                )
                // Vibrant inner core aura
                Box(
                    modifier = Modifier
                        .size(if (compact) 74.dp else 88.dp)
                        .graphicsLayer {
                            alpha = playGlowAlpha
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ambientColor.copy(alpha = 0.65f),
                                    ambientCompanion.copy(alpha = 0.30f),
                                    Color.Transparent,
                                )
                            ),
                            CircleShape,
                        )
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White,
                contentColor = Color(0xFF0F1116),
                shadowElevation = if (effectivePlaying) 16.dp else 6.dp,
                border = BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White,
                            Color.White.copy(alpha = 0.50f)
                        )
                    )
                ),
                onClick = {
                    hapticManager.toggle(!effectivePlaying)
                    if (onTogglePlay != null) onTogglePlay() else player.togglePlayPause()
                },
                interactionSource = playInteraction,
                modifier = Modifier
                    .size(playButtonSize)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering && overridePlaying == null) {
                        ExpressiveInlineLoadingIndicator(
                            size = if (compact) 26.dp else 32.dp,
                            color = Color(0xFF0F1116),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        AnimatedPlayPauseIcon(
                            isPlaying = effectivePlaying,
                            modifier = Modifier.size(playIconSize),
                        )
                    }
                }
            }
        }

        // 4. Next Track
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = nextInteraction),
            onClick = {
                hapticManager.click()
                if (onNext != null) onNext() else player.next()
            },
            interactionSource = nextInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(Color.White.copy(alpha = 0.12f)),
            contentColor = Color.White.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .size(skipButtonSize)
                .graphicsLayer {
                    scaleX = nextScale
                    scaleY = nextScale
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(skipIconSize))
            }
        }

        // 5. Repeat Mode Button
        val isRepeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
        LiquidGlassSurface(
            glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = repeatInteraction),
            onClick = {
                hapticManager.click()
                player.cycleRepeatMode()
            },
            interactionSource = repeatInteraction,
            shape = CircleShape,
            color = liquidGlassContainerColor(if (isRepeatActive) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f)),
            contentColor = if (isRepeatActive) Color.White else Color.White.copy(alpha = 0.65f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (isRepeatActive) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.10f)
            ),
            modifier = Modifier
                .size(sideButtonSize)
                .graphicsLayer {
                    scaleX = repeatScale
                    scaleY = repeatScale
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    Modifier.size(sideIconSize)
                )
                if (isRepeatActive) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp)
                            .size(4.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerModeButton(
    active: Boolean,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    background: Color,
    foreground: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 1800f),
        label = "modePress",
    )
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
        else liquidGlassContainerColor(background), label = "modeContainer",
    )
    val content by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else foreground, label = "modeContent",
    )
    LiquidGlassSurface(
glassModifier = Modifier.liquidGlassChrome(CircleShape, LocalLiquidGlass.current, LiquidGlassPreset.PlayerControls, interactionSource = interaction),
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                selected = active
                stateDescription = description
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(iconSize))
            if (active) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp)
                        .size(4.dp).background(content, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun AudiophileHeroBadge(
    state: MusicPlayerState,
    signalPath: SignalPathReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isSpatial = remember(state.audioCodec) { isSpatialAudioCodec(state.audioCodec) }
    val isLossless = state.isLossless
    val isHiRes = isLossless && ((state.bitDepth ?: 16) > 16 || (state.samplingRateKHz ?: 44.1) > 48.0)

    val badgeLabel = remember(state.audioCodec, state.bitrateKbps, state.samplingRateKHz, state.bitDepth, isSpatial, isLossless, isHiRes) {
        when {
            isSpatial -> "Dolby Atmos"
            isHiRes -> "Hi-Res Lossless • ${state.bitDepth ?: 24}-bit / ${formatSampleRateKHz(state.samplingRateKHz ?: 96.0)}kHz"
            isLossless -> "Lossless • ${state.bitDepth ?: 16}-bit / ${formatSampleRateKHz(state.samplingRateKHz ?: 44.1)}kHz"
            else -> buildString {
                append(state.audioCodec?.takeIf(String::isNotBlank) ?: "Audio")
                state.bitrateKbps?.let { append(" • $it kbps") }
            }
        }
    }

    val badgeColor = when {
        isSpatial -> Color(0xFF7C4DFF)
        signalPath.bitPerfect -> Color(0xFF00E676)
        isHiRes -> Color(0xFF69F0AE)
        isLossless -> Color(0xFFB2FF59)
        else -> Color.White.copy(alpha = 0.72f)
    }

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "badgeScale",
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interaction,
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(
            0.8.dp,
            if (signalPath.bitPerfect) {
                Brush.horizontalGradient(listOf(Color(0xFF00E676).copy(alpha = 0.8f), Color(0xFF00B0FF).copy(alpha = 0.6f)))
            } else if (isSpatial) {
                Brush.horizontalGradient(listOf(Color(0xFF7C4DFF).copy(alpha = 0.8f), Color(0xFF00E5FF).copy(alpha = 0.6f)))
            } else if (isLossless) {
                Brush.horizontalGradient(listOf(Color(0xFF69F0AE).copy(alpha = 0.6f), Color(0xFFB2FF59).copy(alpha = 0.5f)))
            } else {
                Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.12f)))
            }
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (signalPath.bitPerfect) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676)),
                )
            } else {
                Icon(
                    when {
                        isSpatial -> Icons.Filled.SurroundSound
                        isLossless -> Icons.Filled.HighQuality
                        else -> Icons.Filled.MusicNote
                    },
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                color = badgeColor,
            )
            if (signalPath.bitPerfect) {
                Text(
                    text = "• Bit-Perfect",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color(0xFF00E676),
                )
            }
        }
    }
}

@Composable
private fun PlayerFloatingDock(
    state: MusicPlayerState,
    player: MusicPlayer,
    currentTab: FullPlayerTab,
    onTabChange: (FullPlayerTab) -> Unit,
    isVideoMode: Boolean,
    hasVideo: Boolean,
    onToggleVideo: () -> Unit,
    onOpenSignalPath: () -> Unit,
    onOpenAudioControls: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val hapticManager = LocalHapticManager.current
    val signalPath by player.signalPath.collectAsStateWithLifecycle()
    val dockShape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
    val dockHeight = if (compact) 48.dp else 56.dp
    val dockItemHeight = if (compact) 36.dp else 42.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Authentic glass contact drop shadow
        Box(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.96f else 0.94f)
                .height(if (compact) 42.dp else 50.dp)
                .graphicsLayer {
                    translationY = if (compact) 3.dp.toPx() else 5.dp.toPx()
                    alpha = 0.55f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.70f),
                            Color.Black.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    ),
                    dockShape,
                )
        )

        val dockBorder = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.35f),
            ),
            start = Offset(0f, 0f),
            end = Offset(450f, 220f),
        )

        Surface(
            shape = dockShape,
            color = Color.Black.copy(alpha = 0.38f),
            border = BorderStroke(1.2.dp, dockBorder),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassChrome(dockShape, LocalLiquidGlass.current, LiquidGlassPreset.FloatingControls),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Specular incident light highlight across top rim
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(1.4.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.50f),
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.30f),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (compact) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1. Lyrics
                    val isLyricsActive = currentTab == FullPlayerTab.LYRICS
                    val lyricsInteraction = remember { MutableInteractionSource() }
                    val isLyricsPressed by lyricsInteraction.collectIsPressedAsState()
                    val lyricsScale by animateFloatAsState(if (isLyricsPressed) 0.88f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "lyricsScale")
                    val activeLyricsBorder = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                    Surface(
                        onClick = {
                            hapticManager.click()
                            if (isLyricsActive) onTabChange(FullPlayerTab.NOW_PLAYING) else onTabChange(FullPlayerTab.LYRICS)
                        },
                        interactionSource = lyricsInteraction,
                        shape = RoundedCornerShape(100),
                        color = if (isLyricsActive) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (isLyricsActive) activeLyricsBorder else null,
                        contentColor = if (isLyricsActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.88f),
                        modifier = Modifier
                            .height(dockItemHeight)
                            .graphicsLayer { scaleX = lyricsScale; scaleY = lyricsScale },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (compact) 9.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.FormatQuote, "Lyrics", Modifier.size(if (compact) 19.dp else 21.dp))
                            if (isLyricsActive) {
                                Box(Modifier.size(5.dp).background(Color(0xFF00E5FF), CircleShape))
                            }
                        }
                    }

                    // 2. Video Mode
                    val videoInteraction = remember { MutableInteractionSource() }
                    val isVideoPressed by videoInteraction.collectIsPressedAsState()
                    val videoScale by animateFloatAsState(if (isVideoPressed) 0.88f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "videoScale")
                    val activeVideoBorder = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.50f))
                    Surface(
                        onClick = {
                            if (hasVideo) {
                                hapticManager.click()
                                onToggleVideo()
                            }
                        },
                        interactionSource = videoInteraction,
                        shape = RoundedCornerShape(100),
                        enabled = hasVideo,
                        color = if (isVideoMode) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (isVideoMode) activeVideoBorder else null,
                        contentColor = when {
                            isVideoMode -> Color(0xFFFF5252)
                            hasVideo -> Color.White.copy(alpha = 0.88f)
                            else -> Color.White.copy(alpha = 0.28f)
                        },
                        modifier = Modifier
                            .height(dockItemHeight)
                            .graphicsLayer { scaleX = videoScale; scaleY = videoScale },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (compact) 9.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.SmartDisplay, "Video", Modifier.size(if (compact) 19.dp else 21.dp))
                            if (isVideoMode) {
                                Box(Modifier.size(5.dp).background(Color(0xFFFF5252), CircleShape))
                            }
                        }
                    }

                    // 3. Audio / Signal Path
                    val audioInteraction = remember { MutableInteractionSource() }
                    val isAudioPressed by audioInteraction.collectIsPressedAsState()
                    val audioScale by animateFloatAsState(if (isAudioPressed) 0.88f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "audioScale")
                    val isSpatial = remember(state.audioCodec) { isSpatialAudioCodec(state.audioCodec) }
                    val isHiRes = state.isLossless && ((state.bitDepth ?: 16) > 16 || (state.samplingRateKHz ?: 44.1) > 48.0)
                    val isOpus = remember(state.audioCodec) { state.audioCodec?.contains("opus", ignoreCase = true) == true }
                    val isAac = remember(state.audioCodec) { state.audioCodec?.contains("aac", ignoreCase = true) == true || state.audioCodec?.contains("mp4a", ignoreCase = true) == true }

                    val activeBadge = remember(state.audioCodec, state.isLossless, isHiRes, signalPath.bitPerfect, isSpatial, isOpus, isAac) {
                        when {
                            isSpatial -> Pair("ATMOS", Color(0xFFB388FF))
                            signalPath.bitPerfect -> Pair("DIRECT", Color(0xFF00E676))
                            isHiRes -> Pair("HI-RES", Color(0xFF00E5FF))
                            state.isLossless || state.audioCodec?.equals("flac", ignoreCase = true) == true -> Pair("LOSSLESS", Color(0xFF00E676))
                            isOpus -> Pair("OPUS", Color(0xFFFFB300))
                            isAac -> Pair("AAC", Color(0xFF64B5F6))
                            !state.audioCodec.isNullOrBlank() -> Pair(state.audioCodec!!.uppercase(), Color(0xFFE0E0E0))
                            else -> Pair("AUDIO", Color.White.copy(alpha = 0.75f))
                        }
                    }

                    Surface(
                        onClick = {
                            hapticManager.click()
                            onOpenSignalPath()
                        },
                        interactionSource = audioInteraction,
                        shape = RoundedCornerShape(100),
                        color = activeBadge.second.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    activeBadge.second.copy(alpha = 0.50f),
                                    Color.White.copy(alpha = 0.16f),
                                ),
                            ),
                        ),
                        contentColor = activeBadge.second,
                        modifier = Modifier
                            .height(dockItemHeight)
                            .graphicsLayer { scaleX = audioScale; scaleY = audioScale },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            PlayingWaveBars(
                                waveColor = activeBadge.second,
                                containerColor = Color.Transparent,
                                isPlaying = state.isPlaying,
                                modifier = Modifier.size(16.dp, 12.dp),
                            )
                            AnimatedContent(
                                targetState = activeBadge,
                                transitionSpec = {
                                    (slideInVertically { height -> height / 2 } + fadeIn(tween(300)))
                                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(300)))
                                },
                                label = "audioBadgeTicker",
                            ) { badge ->
                                Text(
                                    text = badge.first,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (compact) 9.5.sp else 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp,
                                    ),
                                    color = badge.second,
                                )
                            }
                        }
                    }

                    // 4. Playing Queue
                    val isQueueActive = currentTab == FullPlayerTab.QUEUE
                    val queueInteraction = remember { MutableInteractionSource() }
                    val isQueuePressed by queueInteraction.collectIsPressedAsState()
                    val queueScale by animateFloatAsState(if (isQueuePressed) 0.88f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "queueScale")
                    val activeQueueBorder = BorderStroke(1.dp, Color(0xFF69F0AE).copy(alpha = 0.50f))
                    Surface(
                        onClick = {
                            hapticManager.click()
                            if (isQueueActive) onTabChange(FullPlayerTab.NOW_PLAYING) else onTabChange(FullPlayerTab.QUEUE)
                        },
                        interactionSource = queueInteraction,
                        shape = RoundedCornerShape(100),
                        color = if (isQueueActive) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (isQueueActive) activeQueueBorder else null,
                        contentColor = if (isQueueActive) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.88f),
                        modifier = Modifier
                            .height(dockItemHeight)
                            .graphicsLayer { scaleX = queueScale; scaleY = queueScale },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (compact) 9.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.QueueMusic, "Queue", Modifier.size(if (compact) 19.dp else 21.dp))
                            if (state.queue.isNotEmpty()) {
                                Text(
                                    text = "${state.queue.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                    color = if (isQueueActive) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.72f),
                                )
                            }
                        }
                    }

                    // 5. Sleep Timer
                    val isTimerActive = state.sleepTimerRemainingMs != null && state.sleepTimerRemainingMs > 0L
                    val timerInteraction = remember { MutableInteractionSource() }
                    val isTimerPressed by timerInteraction.collectIsPressedAsState()
                    val timerScale by animateFloatAsState(if (isTimerPressed) 0.88f else 1.0f, ExpressiveMotion.appleTactileSpring(), label = "timerScale")
                    val activeTimerBorder = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.50f))
                    Surface(
                        onClick = {
                            hapticManager.click()
                            player.cycleSleepTimer()
                        },
                        interactionSource = timerInteraction,
                        shape = RoundedCornerShape(100),
                        color = if (isTimerActive) Color(0xFFFFB74D).copy(alpha = 0.20f) else Color.Transparent,
                        border = if (isTimerActive) activeTimerBorder else null,
                        contentColor = if (isTimerActive) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.88f),
                        modifier = Modifier
                            .height(dockItemHeight)
                            .graphicsLayer { scaleX = timerScale; scaleY = timerScale },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (compact) 9.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.Timer, "Sleep Timer", Modifier.size(if (compact) 18.dp else 20.dp))
                            if (isTimerActive) {
                                val minutesLeft = (state.sleepTimerRemainingMs!! / 60000L).coerceAtLeast(1)
                                Text(
                                    text = "${minutesLeft}m",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFFB74D),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoDJLiveMonitorCard(
    state: MusicPlayerState,
    player: MusicPlayer,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val isBlending = state.autoMixState == AutoMixState.FADING_AUTO || state.autoMixState == AutoMixState.FADING_FAST
    val isArmed = state.autoMixState == AutoMixState.MONITORING_OUTRO
    val isAnalyzing = state.autoMixState == AutoMixState.ANALYZING || state.autoMixState == AutoMixState.PREBUFFERING_DECK_B
    val hasActiveAutoDJ = state.isAutoDJActive && state.autoDJStatus != null
    val isAnyActive = state.isAutoMixActive || state.isAutoMixEnabled || state.isAutoDJActive

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row: Icon + Title + Live Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBlending) Color(0xFF8B5CF6).copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.tertiaryContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = if (isBlending) Color(0xFFDDD6FE) else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Auto DJ & Automix",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isBlending) "Live Dual-Deck Outro Blend"
                            else if (isArmed) "Outro Boundary Armed"
                            else if (state.autoDJStatus != null) state.autoDJStatus
                            else "Predictive Queue & Tempo Blends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Live status badge
                val (badgeText, badgeBg, badgeFg) = when {
                    isBlending -> Triple(
                        "LIVE BLEND",
                        Color(0xFF8B5CF6).copy(alpha = 0.30f),
                        Color(0xFFDDD6FE)
                    )
                    isArmed -> Triple(
                        "OUTRO ARMED",
                        Color(0xFF10B981).copy(alpha = 0.25f),
                        Color(0xFFA7F3D0)
                    )
                    isAnalyzing -> Triple(
                        "PREPARING",
                        Color(0xFF3B82F6).copy(alpha = 0.25f),
                        Color(0xFFBFDBFE)
                    )
                    hasActiveAutoDJ -> Triple(
                        "PREFETCHING",
                        Color(0xFFF59E0B).copy(alpha = 0.25f),
                        Color(0xFFFDE68A)
                    )
                    isAnyActive -> Triple(
                        "ACTIVE",
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Triple(
                        "OFF",
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Interactive Automix & Bar controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automix Beat-Matched Transitions",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (state.isAutoMixEnabled) "Dual-deck outro blend active" else "Seamless beat-matched crossfades",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.isAutoMixEnabled,
                        onCheckedChange = {
                            hapticManager.click()
                            player.setAutomixEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFDDD6FE),
                            checkedTrackColor = Color(0xFF8B5CF6),
                        ),
                    )
                }

                if (state.isAutoMixEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Transition Phrase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.autoMixBars == 8,
                                onClick = {
                                    hapticManager.click()
                                    player.setAutomixBars(8)
                                },
                                label = { Text("8 Bars") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                            FilterChip(
                                selected = state.autoMixBars == 16,
                                onClick = {
                                    hapticManager.click()
                                    player.setAutomixBars(16)
                                },
                                label = { Text("16 Bars") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }

                    if (!isBlending && state.queue.size > 1) {
                        Button(
                            onClick = {
                                hapticManager.click()
                                player.triggerManualMix()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF8B5CF6),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isArmed) "Mix Now \u2022 Outro Ready" else "Blend to Next Track (Mix Now)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Live Blending Bar & Crossover Details (when blending)
            if (isBlending) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.autoMixState == AutoMixState.FADING_FAST) "Fast 4-bar Skip Blend" else "Deck A ➔ Deck B Cross-Blend",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${(state.autoMixProgress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6),
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.autoMixProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF8B5CF6),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bpmLabel = state.autoMixBpm?.let { "${it.roundToInt()} BPM" } ?: "Tempo Synced"
                        Text(
                            text = "• $bpmLabel (WSOLA)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val bassDeck = if (state.autoMixProgress < 0.5f) "Deck A Bass" else "Deck B Bass"
                        Text(
                            text = "• 160Hz Linkwitz-Riley: $bassDeck",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (isArmed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text(
                            text = "Outro transition armed",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Next track will blend automatically at the outro energy drop",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (isAnalyzing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (state.autoMixState == AutoMixState.PREBUFFERING_DECK_B)
                            "Prebuffering Deck B on FFmpeg..."
                        else "Detecting kick-band BPM & phase alignment...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Auto DJ Queue Lifecycle / Stream Resolution Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            state.autoDJStatus != null -> "Auto DJ: ${state.autoDJStatus}"
                            state.isAutoDJActive -> "Auto DJ Queue: Continuous & Ready"
                            else -> "Auto DJ: Continuous Queue OFF"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isBuffering || state.autoDJStatus != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = Color(0xFFF59E0B),
                        )
                    }
                    Switch(
                        checked = state.isAutoDJActive,
                        onCheckedChange = {
                            hapticManager.click()
                            player.setAutoDJEnabled(it)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioControlsSheet(
    player: MusicPlayer,
    state: MusicPlayerState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val signalPath by player.signalPath.collectAsStateWithLifecycle()
    var isLocked by rememberSaveable { mutableStateOf(true) }
    var currentSpeed by remember(state.speed) { mutableFloatStateOf(state.speed) }
    var currentPitch by remember(state.pitch) { mutableFloatStateOf(state.pitch) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Audio & Studio Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Volume, Slowed & Pitch Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LiquidGlassIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    contentDescription = "Close",
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Volume Section Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "System Volume",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    VolumeSlider(
                        isTranslucent = false,
                        primaryColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Auto DJ & Automix Live Section Card
            AutoDJLiveMonitorCard(
                state = state,
                player = player,
            )

            // Studio Slowed & Pitch Section Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Slowed & Pitch Control",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isLocked) "Linked tape varispeed mode" else "Independent time/pitch stretch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Lock Button Toggle
                            FilterChip(
                                selected = isLocked,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isLocked = !isLocked
                                    if (isLocked) {
                                        // Snap pitch to speed
                                        currentPitch = currentSpeed
                                        player.setPlaybackParameters(currentSpeed, currentSpeed)
                                    }
                                },
                                label = {
                                    Text(
                                        if (isLocked) "Linked" else "Free",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                border = null,
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )

                            // Reset Button placed right next to Lock button
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentSpeed = 1.0f
                                    currentPitch = 1.0f
                                    player.resetPlaybackParameters()
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (signalPath.bitPerfect) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HighQuality,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Bit-Perfect Mode: DSP time-stretch and pitch shifting are bypassed for bit-exact DAC output.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00E676),
                                )
                            }
                        }
                    }

                    // Speed Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.2f", currentSpeed)}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Slider(
                            value = currentSpeed,
                            onValueChange = { value ->
                                val rounded = ((value * 100).roundToInt() / 100f).coerceIn(0.50f, 1.50f)
                                currentSpeed = rounded
                                if (isLocked) {
                                    currentPitch = rounded
                                    player.setPlaybackParameters(rounded, rounded)
                                } else {
                                    player.setPlaybackParameters(rounded, currentPitch)
                                }
                            },
                            valueRange = 0.50f..1.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Pitch Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Audio Pitch (Tone)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.2f", currentPitch)}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Slider(
                            value = currentPitch,
                            onValueChange = { value ->
                                val rounded = ((value * 100).roundToInt() / 100f).coerceIn(0.50f, 1.50f)
                                currentPitch = rounded
                                if (isLocked) {
                                    currentSpeed = rounded
                                    player.setPlaybackParameters(rounded, rounded)
                                } else {
                                    player.setPlaybackParameters(currentSpeed, rounded)
                                }
                            },
                            valueRange = 0.50f..1.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Quick Presets - Material You Expressive Design without outline/stroke
                    Text(
                        text = "Studio Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val presetColors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSpeed = 0.85f
                                currentPitch = 0.85f
                                player.setPlaybackParameters(0.85f, 0.85f)
                            },
                            label = { Text("Slowed", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            border = null,
                            shape = RoundedCornerShape(16.dp),
                            colors = presetColors,
                            modifier = Modifier.weight(1f),
                        )
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSpeed = 0.75f
                                currentPitch = 0.75f
                                player.setPlaybackParameters(0.75f, 0.75f)
                            },
                            label = { Text("Deep Slow", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            border = null,
                            shape = RoundedCornerShape(16.dp),
                            colors = presetColors,
                            modifier = Modifier.weight(1f),
                        )
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSpeed = 1.0f
                                currentPitch = 1.0f
                                player.resetPlaybackParameters()
                            },
                            label = { Text("Normal", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            border = null,
                            shape = RoundedCornerShape(16.dp),
                            colors = presetColors,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSpeed = 1.15f
                                currentPitch = 1.15f
                                player.setPlaybackParameters(1.15f, 1.15f)
                            },
                            label = { Text("Nightcore", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            border = null,
                            shape = RoundedCornerShape(16.dp),
                            colors = presetColors,
                            modifier = Modifier.weight(1f),
                        )
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSpeed = 1.25f
                                currentPitch = 1.25f
                                player.setPlaybackParameters(1.25f, 1.25f)
                            },
                            label = { Text("Sped Up", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            border = null,
                            shape = RoundedCornerShape(16.dp),
                            colors = presetColors,
                            modifier = Modifier.weight(1f),
                        )
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSpeed = 0.80f
                                currentPitch = 0.70f
                                player.setPlaybackParameters(0.80f, 0.70f)
                            },
                            label = { Text("Vaporwave", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            border = null,
                            shape = RoundedCornerShape(16.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

internal var isQueueLockedState by mutableStateOf(false)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueuePanel(
    state: MusicPlayerState,
    player: MusicPlayer,
    ambientColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val isQueueLocked = isQueueLockedState
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var menuIndex by remember { mutableStateOf<Int?>(null) }

    // Stable keys so animateItem() can animate moves instead of treating
    // every shifted row as a new item. Duplicates get occurrence suffixes.
    val queueKeys = remember(state.queue) {
        val counts = mutableMapOf<String, Int>()
        state.queue.map { item ->
            val base = item.videoId ?: item.playbackUrl
                ?: "${item.artist}|${item.title}|${item.album}"
            val n = counts.getOrDefault(base, 0)
            counts[base] = n + 1
            "$base#$n"
        }
    }
    // Abort a stale drag if the queue itself changes underneath us
    // (track ended, clear-upcoming, fresh playQueue, …).
    LaunchedEffect(state.queue.size) {
        if (draggingIndex >= state.queue.size) {
            draggingIndex = -1
            dragOffsetY = 0f
        }
    }
    // Follow the now-playing song in long queues: auto-scroll on every track
    // change (and on first open), unless the user is dragging or has manually
    // scrolled within the last few seconds. The eye button still jumps on demand.
    var userScrollHoldUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) userScrollHoldUntil = System.currentTimeMillis() + 3_000L
    }
    LaunchedEffect(state.currentIndex, state.queue.size) {
        if (draggingIndex != -1 || state.queue.isEmpty()) return@LaunchedEffect
        if (System.currentTimeMillis() < userScrollHoldUntil) return@LaunchedEffect
        val target = state.currentIndex.coerceIn(0, state.queue.size - 1)
        runCatching { listState.animateScrollToItem(target) }
    }
    // True upcoming play order (shuffle/repeat aware). Under shuffle the list
    // below stays in playlist order, so without this strip you can never tell
    // what plays next — and Play-next adds look "lost".
    val shuffleUpcoming = remember(state.queue, state.currentIndex, state.shuffleEnabled, state.repeatMode) {
        if (state.shuffleEnabled) runCatching { player.peekUpcomingIndices(3) }.getOrDefault(emptyList())
        else emptyList()
    }.filter { it in state.queue.indices && it != state.currentIndex }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    if (state.isEndlessQueue) {
                        "Unlimited songs · ${state.currentIndex.coerceAtLeast(0) + 1} playing"
                    } else {
                        "${state.queue.size} songs · ${state.currentIndex.coerceAtLeast(0) + 1} playing"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.70f),
                )
                if (state.queue.size > 1 && !isQueueLocked) {
                    Text(
                        stringResource(com.neonsong.app.R.string.queue_rearrange_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statsText = remember(state.queue) {
                    QueueStatsFormatter.formatQueueStats(state.queue)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = statsText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.90f),
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = {
                        isQueueLockedState = !isQueueLockedState
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isQueueLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .border(
                            0.8.dp,
                            if (isQueueLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.16f),
                            RoundedCornerShape(16.dp)
                        ),
                ) {
                    Icon(
                        imageVector = if (isQueueLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (isQueueLocked) "Unlock queue" else "Lock queue",
                        tint = if (isQueueLocked) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.90f),
                    )
                }
                if (state.queue.size > state.currentIndex + 1 && !isQueueLocked) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            player.clearUpcoming()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                    ) {
                        Icon(
                            Icons.Filled.ClearAll,
                            contentDescription = "Clear upcoming songs",
                            tint = Color.White.copy(alpha = 0.90f),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        userScrollHoldUntil = 0L
                        scope.launch { listState.animateScrollToItem(state.currentIndex.coerceAtLeast(0)) }
                    },
                    enabled = state.currentIndex >= 0 && state.currentIndex < state.queue.size,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = "Scroll to current song",
                        tint = Color.White.copy(alpha = 0.90f),
                    )
                }
            }
        }
        if (shuffleUpcoming.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Up next in shuffle order",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.60f),
                    fontWeight = FontWeight.Bold,
                )
                shuffleUpcoming.forEachIndexed { order, queueIndex ->
                    val item = state.queue[queueIndex]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { player.seekToQueueItem(queueIndex) }
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${order + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(20.dp),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                            Text(
                                item.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.60f),
                            )
                        }
                        Text(
                            "#${queueIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.40f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.queue, key = { index, _ -> queueKeys.getOrNull(index) ?: index.toString() }) { index, item ->
                val isCurrent = index == state.currentIndex
                val isDragging = index == draggingIndex
                val stableKey = queueKeys.getOrNull(index) ?: index.toString()
                val currentIndex by rememberUpdatedState(index)
                @Suppress("DEPRECATION")
                val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                            player.removeQueueItem(currentIndex)
                            true
                        } else {
                            false
                        }
                    }
                )
                androidx.compose.material3.SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = !isQueueLocked,
                    enableDismissFromEndToStart = !isQueueLocked,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        // The delete background must only exist while a swipe
                        // is actually in progress: the queue card is
                        // translucent (liquid glass), so a permanently
                        // composed trash icon ghosts through the card and
                        // visually merges with the drag handle at the same
                        // CenterEnd spot.
                        if (dismissState.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                            val alignment = if (dismissState.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = alignment
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    },
                    content = {
                LiquidGlassSurface(
                    glassModifier = Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), LocalLiquidGlass.current),
                    onClick = { player.seekToQueueItem(index) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isCurrent) {
                        ambientColor.copy(alpha = 0.32f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    },
                    contentColor = Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) ambientColor.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.14f),
                    ),
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            shadowElevation = if (isDragging) 18f else 0f
                            val s = if (isDragging) 1.025f else 1f
                            scaleX = s
                            scaleY = s
                            alpha = if (isDragging) 0.96f else 1f
                        },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) ambientColor else Color.White.copy(alpha = 0.60f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(30.dp),
                        )
                        PlayerArtwork(item, Modifier.size(50.dp), 13.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Text(
                                item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.60f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { menuIndex = index },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White.copy(alpha = 0.75f),
                            )
                        }
                        if (!isQueueLocked) {
                            Icon(
                                Icons.Filled.DragHandle,
                                stringResource(com.neonsong.app.R.string.queue_drag_hint),
                                tint = if (isDragging) ambientColor else Color.White.copy(alpha = 0.60f),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isDragging) ambientColor.copy(alpha = 0.28f)
                                        else Color.White.copy(alpha = 0.08f),
                                    )
                                    .padding(8.dp)
                                    .pointerInput(stableKey) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragOffsetY = 0f
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                draggingIndex = -1
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = -1
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val source = draggingIndex
                                                if (source < 0) return@detectDragGesturesAfterLongPress
                                                dragOffsetY += dragAmount.y
                                                val layoutInfo = listState.layoutInfo
                                                val draggedInfo = layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.index == source }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val draggedCenter = draggedInfo.offset + draggedInfo.size / 2 + dragOffsetY.toInt()
                                                val target = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                    info.index != source &&
                                                        info.index in state.queue.indices &&
                                                        draggedCenter in info.offset..(info.offset + info.size)
                                                }?.index
                                                if (target != null && target != source) {
                                                    player.moveQueueItem(source, target)
                                                    // Keep the row glued under the finger across
                                                    // the layout shift caused by the move.
                                                    val targetInfo = layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.index == target }
                                                    if (targetInfo != null) {
                                                        dragOffsetY += (draggedInfo.offset - targetInfo.offset).toFloat()
                                                    }
                                                    draggingIndex = target
                                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                                }
                                                // Edge auto-scroll while dragging.
                                                val viewportStart = layoutInfo.viewportStartOffset
                                                val viewportEnd = layoutInfo.viewportEndOffset
                                                val edgeZone = 180
                                                when {
                                                    draggedCenter < viewportStart + edgeZone ->
                                                        scope.launch { listState.scrollBy(-28f) }
                                                    draggedCenter > viewportEnd - edgeZone ->
                                                        scope.launch { listState.scrollBy(28f) }
                                                }
                                            },
                                        )
                                    },
                            )
                        }
                    }
                }
                }
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    menuIndex?.let { index ->
        val itemTrack = state.queue.getOrNull(index)
        if (itemTrack != null) {
            com.neonsong.app.ui.common.TrackContextMenuSheet(
                target = com.neonsong.app.ui.common.TrackMenuTarget.Track(itemTrack.title, itemTrack.artist, itemTrack.album ?: ""),
                capabilities = com.neonsong.app.ui.common.TrackMenuCapabilities(showCopyActions = true),
                playableTrack = itemTrack,
                onDismiss = { menuIndex = null },
                onRemoveFromQueue = if (isQueueLocked) null else ({ player.removeQueueItem(index) })
            )
        } else {
            menuIndex = null
        }
    }
}

@Composable
private fun PlayerArtwork(
    track: PlayableTrack,
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp,
    decodeSizePx: Int? = null,
    canvas: com.neonsong.app.data.canvas.CanvasArtwork? = null,
    isPlaying: Boolean = false,
    pausedForTransition: Boolean = false,
    transformations: List<coil.transform.Transformation> = emptyList(),
    onAspectRatioChanged: (Float) -> Unit = {},
) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        ArtworkImage(
            name = track.title,
            artist = track.artist,
            embeddedUrl = track.artworkUrl,
            fallbackIcon = Icons.Filled.MusicNote,
            modifier = Modifier.fillMaxSize(),
            decodeSizePx = decodeSizePx,
            transformations = transformations,
        )
        if (canvas != null) {
            CanvasArtworkPlayer(
                canvas = canvas,
                isPlaying = isPlaying,
                pausedForTransition = pausedForTransition,
                onAspectRatioChanged = onAspectRatioChanged,
                contentMode = CanvasContentMode.CROP,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun PlayableTrack.toGeneratedTrack() = com.neonsong.app.data.generate.GeneratedTrack(
    name = title,
    artist = artist,
    artworkUrl = artworkUrl,
    album = album,
    url = videoId?.let { "https://music.youtube.com/watch?v=$it" }.orEmpty(),
)

/**
 * Captures 4D spatial device tilt (pitch and roll) via ROTATION_VECTOR or GRAVITY sensors,
 * normalized to [-1f, 1f] for buttery-smooth perspective parallax.
 */
@Composable
private fun rememberSpatialTilt(enabled: Boolean = true): androidx.compose.runtime.State<Offset> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val isResumed = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val tiltState = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled, view, isResumed) {
        if (!enabled || !isResumed) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val remappedMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            private var smoothedX = 0f
            private var smoothedY = 0f
            private var initialized = false

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val displayRotation = view.display?.rotation ?: android.view.Surface.ROTATION_0

                val (targetX, targetY) = if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val (axisX, axisY) = when (displayRotation) {
                        android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                    SensorManager.getOrientation(remappedMatrix, orientation)
                    val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    (roll / 32f).coerceIn(-1f, 1f) to (pitch / 32f).coerceIn(-1f, 1f)
                } else if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                    val rawX = event.values[0]
                    val rawY = event.values[1]
                    val (gx, gy) = when (displayRotation) {
                        android.view.Surface.ROTATION_90 -> -rawY to rawX
                        android.view.Surface.ROTATION_180 -> -rawX to -rawY
                        android.view.Surface.ROTATION_270 -> rawY to -rawX
                        else -> rawX to rawY
                    }
                    (-gx / 9.8f).coerceIn(-1f, 1f) to (gy / 9.8f).coerceIn(-1f, 1f)
                } else return

                if (!initialized) {
                    smoothedX = targetX
                    smoothedY = targetY
                    initialized = true
                } else {
                    // Exponential low-pass filter rejects involuntary muscle micro-jitter
                    smoothedX = smoothedX * 0.70f + targetX * 0.30f
                    smoothedY = smoothedY * 0.70f + targetY * 0.30f
                }

                val prev = tiltState.value
                if (abs(smoothedX - prev.x) > 0.065f || abs(smoothedY - prev.y) > 0.065f) {
                    tiltState.value = Offset(smoothedX, smoothedY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return tiltState
}
