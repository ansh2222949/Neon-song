package com.neonsong.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neonsong.app.R
import com.neonsong.app.data.music.SyncedVideoStreamBundle
import com.neonsong.app.data.music.YOUTUBE_WEB_USER_AGENT
import com.neonsong.app.data.music.YouTubeVideoStream
import com.neonsong.app.data.video.SyncedVideoRepository
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.EdgeToEdgeDialogWindow
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface
import androidx.compose.ui.graphics.RectangleShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import com.neonsong.app.ui.theme.GlassTonalActionButton as FilledTonalButton

private const val TAG = "SyncedVideoPlayer"

sealed interface SyncedVideoUiState {
    data object Loading : SyncedVideoUiState
    data class Ready(
        val bundle: SyncedVideoStreamBundle,
        val activeStream: YouTubeVideoStream,
        val selectedHeight: Int? = null,
        val requestKey: String = "",
    ) : SyncedVideoUiState {
        val stream: YouTubeVideoStream get() = activeStream
        val isAuto: Boolean get() = selectedHeight == null || selectedHeight <= 0
        val currentQualityLabel: String
            get() = if (isAuto) "Auto • ${activeStream.height?.let { "${it}p" } ?: activeStream.displayQuality}" else activeStream.displayQuality
    }
    data class Error(val message: String, val requestKey: String = "") : SyncedVideoUiState
}

@HiltViewModel
class SyncedVideoViewModel @Inject constructor(
    private val videoRepository: SyncedVideoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SyncedVideoUiState>(SyncedVideoUiState.Loading)
    val uiState: StateFlow<SyncedVideoUiState> = _uiState.asStateFlow()

    private var currentKey: String? = null
    private var loadJob: Job? = null

    fun trackRequestKey(title: String, artist: String, videoId: String?): String {
        val normTitle = title.trim().lowercase()
        val normArtist = artist.trim().lowercase()
        return if (normTitle.isNotBlank() || normArtist.isNotBlank()) {
            "$normTitle|$normArtist"
        } else {
            "id:${videoId.orEmpty()}"
        }
    }

    fun loadVideo(
        title: String,
        artist: String,
        videoId: String?,
        targetDurationMs: Long? = null,
        forceRefresh: Boolean = false,
    ) {
        val key = trackRequestKey(title, artist, videoId)
        val curState = _uiState.value
        if (!forceRefresh && currentKey == key && (
                (curState is SyncedVideoUiState.Ready && curState.requestKey == key) ||
                    loadJob?.isActive == true
                )
        ) {
            return
        }
        loadJob?.cancel()
        currentKey = key
        if (forceRefresh) {
            videoRepository.invalidateTrack(title, artist, videoId)
        }

        loadJob = viewModelScope.launch {
            _uiState.value = SyncedVideoUiState.Loading
            val result = videoRepository.resolveVideo(title, artist, videoId, targetDurationMs)
            if (!isActive || currentKey != key) return@launch
            result
                .onSuccess { bundle ->
                    _uiState.value = SyncedVideoUiState.Ready(
                        bundle = bundle,
                        activeStream = bundle.fastStartStream,
                        selectedHeight = null,
                        requestKey = key,
                    )
                }
                .onFailure { error ->
                    _uiState.value = SyncedVideoUiState.Error(
                        message = error.message ?: "Unable to resolve video stream",
                        requestKey = key,
                    )
                }
        }
    }

    fun selectQuality(height: Int?) {
        val current = _uiState.value as? SyncedVideoUiState.Ready ?: return
        val targetStream = if (height == null || height <= 0) {
            current.bundle.fastStartStream
        } else {
            current.bundle.availableStreams
                .filter { (it.height ?: 0) == height }
                .maxByOrNull { (if (it.isHardwareFriendlyCodec) 10 else 0) + (if (it.is60Fps) 4 else 0) }
                ?: current.bundle.availableStreams.minByOrNull { abs((it.height ?: 0) - height) }
                ?: current.bundle.fastStartStream
        }
        _uiState.value = current.copy(
            activeStream = targetStream,
            selectedHeight = height,
        )
    }

    fun retry(title: String, artist: String, videoId: String?, targetDurationMs: Long? = null) {
        currentKey = null
        loadVideo(title, artist, videoId, targetDurationMs, forceRefresh = true)
    }

    fun cancelPendingLoad() {
        loadJob?.cancel()
        loadJob = null
        currentKey = null
    }
}

/**
 * High-performance, frame-accurate synchronized video viewer dialog with immersive fullscreen,
 * instant fast-start decoding, 30Hz audio-master PLL lock, and live sync offset fine-tuning.
 */
@OptIn(UnstableApi::class)
@Composable
fun SyncedVideoPlayerDialog(
    track: PlayableTrack,
    musicPlayer: MusicPlayer,
    onDismiss: () -> Unit,
    viewModel: SyncedVideoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by musicPlayer.state.collectAsStateWithLifecycle()
    val progressState by musicPlayer.progressState.collectAsStateWithLifecycle()

    var isFullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isRendered by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isSyncLocked by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }
    var doubleTapSeekSide by remember { mutableStateOf<Boolean?>(null) } // false = left, true = right
    var showQualityDialog by remember { mutableStateOf(false) }
    var showSyncTuneBar by remember { mutableStateOf(false) }
    var manualOffsetMs by remember(track.videoId, track.title) { mutableLongStateOf(0L) }
    var videoScalingMode by remember { mutableStateOf(VideoScalingMode.FIT) }
    var brightnessHudValue by remember { mutableFloatStateOf(-1f) }
    var volumeHudFraction by remember { mutableFloatStateOf(-1f) }

    val clockController = remember(track.videoId, track.title) { SyncedVideoClockController() }
    val activeRequestHeaders = remember { ConcurrentHashMap<String, String>() }

    // Detect if the MV has a classic 1.8s..12s opening studio logo intro vs the audio track
    val suggestedIntroOffsetMs = remember(uiState, progressState.durationMs) {
        val streamDur = (uiState as? SyncedVideoUiState.Ready)?.activeStream?.durationMs ?: 0L
        val audioDur = (progressState.durationMs.takeIf { it > 0L } ?: track.durationMs) ?: 0L
        SyncedVideoClockSync.estimateIntroOffsetMs(audioDur, streamDur, manualOffsetMs = 0L)
    }

    val activity = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val audioManager = remember(view.context) {
        view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    LaunchedEffect(brightnessHudValue) {
        if (brightnessHudValue >= 0f) {
            delay(1100L)
            brightnessHudValue = -1f
        }
    }
    LaunchedEffect(volumeHudFraction) {
        if (volumeHudFraction >= 0f) {
            delay(1100L)
            volumeHudFraction = -1f
        }
    }

    // Auto-hide controls in fullscreen after inactivity (paused while adjusting sync or scrubbing)
    LaunchedEffect(controlsVisible, isFullscreen, isUserScrubbing, showSyncTuneBar) {
        if (controlsVisible && isFullscreen && !isUserScrubbing && !showSyncTuneBar) {
            delay(3200L)
            controlsVisible = false
        }
    }

    // Fullscreen immersive system bars handler
    DisposableEffect(view, isFullscreen) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior

        if (isFullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (isFullscreen) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                previousBehavior?.let { controller?.systemBarsBehavior = it }
            }
        }
    }

    BackHandler {
        when {
            showSyncTuneBar -> showSyncTuneBar = false
            isFullscreen -> isFullscreen = false
            else -> onDismiss()
        }
    }

    // Ultra-fast-start, frame-accurate ExoPlayer dedicated strictly to video rendering
    val videoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 150,
                /* bufferForPlaybackAfterRebufferMs = */ 450,
            )
            .setBackBuffer(15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()

        val dynamicDataSourceFactory = DataSource.Factory {
            val mergedHeaders = buildMap {
                put("Origin", "https://www.youtube.com")
                put("Referer", "https://www.youtube.com/")
                putAll(activeRequestHeaders)
            }
            val ua = mergedHeaders["User-Agent"] ?: YOUTUBE_WEB_USER_AGENT
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setConnectTimeoutMs(3_500)
                .setReadTimeoutMs(6_000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(mergedHeaders)
            DefaultDataSource.Factory(context, httpFactory).createDataSource()
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dynamicDataSourceFactory))
            .setLoadControl(loadControl)
            // EXACT seek ensures frame-accurate lock instead of snapping 2-5s away to nearest keyframe
            .setSeekParameters(SeekParameters.EXACT)
            .build()
            .apply {
                volume = 0f
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    LaunchedEffect(track.videoId, track.title, track.artist, track.durationMs) {
        isRendered = false
        isBuffering = false
        isSyncLocked = false
        clockController.reset()
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
        viewModel.loadVideo(track.title, track.artist, track.videoId, track.durationMs)
    }

    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isRendered = true
                isBuffering = false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.w(TAG, "Video player error: ${error.message}", error)
            }
        }
        videoPlayer.addListener(listener)
        onDispose {
            videoPlayer.removeListener(listener)
            videoPlayer.release()
        }
    }

    // Load stream into video player when ready or when switching quality
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is SyncedVideoUiState.Ready) {
            val stream = state.activeStream
            val currentMediaUri = videoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentMediaUri == stream.url) return@LaunchedEffect

            isRendered = false
            isSyncLocked = false
            clockController.reset()

            activeRequestHeaders.clear()
            activeRequestHeaders.putAll(stream.requestHeaders)

            val startTargetMs = (musicPlayer.currentPositionMs() + manualOffsetMs).coerceAtLeast(0L)
            val mediaItem = MediaItem.Builder()
                .setUri(stream.url)
                .apply {
                    stream.mimeType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                }
                .build()
            videoPlayer.setMediaItem(mediaItem, startTargetMs)
            videoPlayer.prepare()
            videoPlayer.playWhenReady = playerState.isPlaying
        }
    }

    // Video scaling mode reaction
    LaunchedEffect(videoScalingMode) {
        videoPlayer.setVideoScalingMode(
            if (videoScalingMode == VideoScalingMode.ZOOM) {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
        )
    }

    // Instant seek on manual offset change
    LaunchedEffect(manualOffsetMs) {
        if (videoPlayer.playbackState == Player.STATE_READY) {
            clockController.reset()
            val target = (musicPlayer.currentPositionMs() + manualOffsetMs).coerceAtLeast(0L)
            videoPlayer.seekTo(target)
        }
    }

    // Immediate discontinuous seek sync (>350ms jump vs wall clock)
    val currentAudioPosMs = progressState.positionMs
    var lastObservedAudioPosMs by remember { mutableLongStateOf(currentAudioPosMs) }
    var lastObservedWallMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(currentAudioPosMs) {
        val now = SystemClock.elapsedRealtime()
        val expectedAudioDelta = if (playerState.isPlaying) {
            ((now - lastObservedWallMs) * playerState.speed).toLong()
        } else 0L
        val actualAudioDelta = currentAudioPosMs - lastObservedAudioPosMs
        lastObservedAudioPosMs = currentAudioPosMs
        lastObservedWallMs = now
        if (abs(actualAudioDelta - expectedAudioDelta) > 350L && videoPlayer.playbackState != Player.STATE_IDLE) {
            clockController.reset()
            videoPlayer.seekTo((currentAudioPosMs + manualOffsetMs).coerceAtLeast(0L))
        }
    }

    // 30Hz High-Precision Audio-Master PLL Synchronization Loop
    LaunchedEffect(videoPlayer, playerState.isPlaying, playerState.speed, uiState, manualOffsetMs) {
        if (uiState !is SyncedVideoUiState.Ready) return@LaunchedEffect
        var lastHardSeekAtMs = Long.MIN_VALUE
        while (isActive) {
            val audioSpeed = playerState.speed

            if (videoPlayer.playWhenReady != playerState.isPlaying) {
                videoPlayer.playWhenReady = playerState.isPlaying
            }

            if (videoPlayer.playbackState == Player.STATE_READY) {
                val now = SystemClock.elapsedRealtime()
                val rawAudioPos = musicPlayer.currentPositionMs().coerceAtLeast(0L)
                val latency = musicPlayer.presentationLatencyMs().takeIf { it > 0L } ?: 45L
                val audibleAudioPos = clockController.getAudibleAudioTimestampMs(
                    rawAudioPosMs = rawAudioPos,
                    isPlaying = playerState.isPlaying,
                    speed = audioSpeed,
                    presentationLatencyMs = latency,
                    nowElapsedMs = now,
                )
                val videoPos = videoPlayer.currentPosition.coerceAtLeast(0L)
                val allowHardSeek = lastHardSeekAtMs == Long.MIN_VALUE ||
                    now - lastHardSeekAtMs >= SyncedVideoClockSync.SEEK_COOLDOWN_MS

                val decision = clockController.evaluateSync(
                    videoPosMs = videoPos,
                    audioPosMs = audibleAudioPos,
                    baseSpeed = audioSpeed,
                    allowHardSeek = allowHardSeek,
                    isPlaying = playerState.isPlaying,
                    videoOffsetMs = manualOffsetMs,
                )
                isSyncLocked = decision.isLocked

                if (decision.action == SyncAction.HARD_SEEK) {
                    decision.seekTargetMs?.let(videoPlayer::seekTo)
                    lastHardSeekAtMs = now
                }
                if (abs(videoPlayer.playbackParameters.speed - decision.targetSpeed) >= 0.002f) {
                    videoPlayer.playbackParameters = PlaybackParameters(decision.targetSpeed)
                }
            } else {
                isSyncLocked = false
            }

            delay(if (playerState.isPlaying) 33L else 120L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val videoAlpha by animateFloatAsState(
        targetValue = if (isRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "videoFade",
    )

    val currentPosition = if (isUserScrubbing) {
        (scrubProgress * progressState.durationMs).toLong()
    } else {
        progressState.positionMs
    }

    val syncBadgeColor = if (isSyncLocked) Color(0xFF34D399) else Color(0xFFFBBF24)
    val syncBadgeText = when {
        manualOffsetMs != 0L -> {
            val sign = if (manualOffsetMs > 0) "+" else ""
            "$sign${"%.2f".format(manualOffsetMs / 1000f)}s"
        }
        isSyncLocked -> stringResource(R.string.video_sync_tag)
        else -> "SYNCING"
    }

    Dialog(
        onDismissRequest = {
            if (isFullscreen) isFullscreen = false else onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        EdgeToEdgeDialogWindow()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFullscreen) Color.Black else Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!isFullscreen) onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            val contentModifier = if (isFullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth(0.95f)
                    .adaptiveContentWidth(maxWidth = 680.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
            }

            val dialogShape = remember { SquircleShape(28.dp) }
            val viewportShape = remember { SquircleShape(22.dp) }

            Surface(
                modifier = contentModifier.then(
                    if (!isFullscreen) {
                        Modifier.liquidGlassChrome(
                            shape = dialogShape,
                            preset = LiquidGlassPreset.ModalSheet,
                            backdrop = LocalLiquidGlassOverlayBackdrop.current,
                        )
                    } else Modifier
                ),
                shape = if (isFullscreen) RectangleShape else dialogShape,
                color = if (isFullscreen) Color.Black else Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) Modifier.fillMaxSize()
                            else Modifier.padding(16.dp)
                        ),
                ) {
                    // Dialog Header (compact mode)
                    if (!isFullscreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer,
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.SmartDisplay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    // Live Sync Status Pill (tap to fine-tune offset)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { showSyncTuneBar = !showSyncTuneBar }
                                            .background(syncBadgeColor.copy(alpha = 0.16f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(syncBadgeColor.copy(alpha = pulseAlpha)),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = syncBadgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = syncBadgeColor,
                                        )
                                    }
                                    // Quality Badge Pill
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { showQualityDialog = true }
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = if (uiState is SyncedVideoUiState.Ready) {
                                                (uiState as SyncedVideoUiState.Ready).activeStream.height?.let { "${it}p" } ?: "Auto"
                                            } else {
                                                "Auto"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showSyncTuneBar = !showSyncTuneBar },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Sync offset",
                                    tint = if (showSyncTuneBar || manualOffsetMs != 0L) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            IconButton(
                                onClick = { showQualityDialog = true },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Filled.HighQuality,
                                    contentDescription = stringResource(R.string.video_quality),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = { isFullscreen = true },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Fullscreen,
                                    contentDescription = stringResource(R.string.video_enter_fullscreen),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.common_cancel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // Video Viewport
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isFullscreen) Modifier.weight(1f)
                                else Modifier
                                    .aspectRatio(16f / 9f)
                                    .clip(viewportShape)
                            )
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                SurfaceView(viewContext).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    alpha = videoAlpha
                                    videoPlayer.setVideoSurfaceView(this)
                                }
                            },
                            update = { surfaceView ->
                                surfaceView.alpha = videoAlpha
                            },
                        )

                        // Interactive gesture zones (Left: Brightness + -10s, Right: Volume + +10s)
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(track.videoId) {
                                        detectTapGestures(
                                            onTap = { controlsVisible = !controlsVisible },
                                            onDoubleTap = {
                                                val newPos = (musicPlayer.currentPositionMs() - 10_000L).coerceAtLeast(0L)
                                                clockController.reset()
                                                musicPlayer.seekTo(newPos)
                                                videoPlayer.seekTo((newPos + manualOffsetMs).coerceAtLeast(0L))
                                                doubleTapFeedback = "-10s"
                                                doubleTapSeekSide = false
                                            },
                                        )
                                    }
                                    .pointerInput(track.videoId) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (activity != null) {
                                                val lp = activity.window.attributes
                                                val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                                val delta = -dragAmount / 500f
                                                val newBrightness = (current + delta).coerceIn(0.02f, 1.0f)
                                                lp.screenBrightness = newBrightness
                                                activity.window.attributes = lp
                                                brightnessHudValue = newBrightness
                                            }
                                        }
                                    }
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(track.videoId) {
                                        detectTapGestures(
                                            onTap = { controlsVisible = !controlsVisible },
                                            onDoubleTap = {
                                                val newPos = (musicPlayer.currentPositionMs() + 10_000L)
                                                    .coerceIn(0L, progressState.durationMs.coerceAtLeast(1L))
                                                clockController.reset()
                                                musicPlayer.seekTo(newPos)
                                                videoPlayer.seekTo((newPos + manualOffsetMs).coerceAtLeast(0L))
                                                doubleTapFeedback = "+10s"
                                                doubleTapSeekSide = true
                                            },
                                        )
                                    }
                                    .pointerInput(track.videoId) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (audioManager != null) {
                                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                                val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                val stepChange = if (dragAmount < -12f) 1 else if (dragAmount > 12f) -1 else 0
                                                if (stepChange != 0) {
                                                    val target = (cur + stepChange).coerceIn(0, max)
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                                    volumeHudFraction = target.toFloat() / max.toFloat()
                                                }
                                            }
                                        }
                                    }
                            )
                        }

                        // Brightness HUD
                        androidx.compose.animation.AnimatedVisibility(
                            visible = brightnessHudValue >= 0f,
                            enter = fadeIn(tween(130)) + scaleIn(initialScale = 0.9f),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 24.dp),
                        ) {
                            GestureMeterHud(
                                icon = Icons.Filled.LightMode,
                                fraction = brightnessHudValue,
                            )
                        }

                        // Volume HUD
                        androidx.compose.animation.AnimatedVisibility(
                            visible = volumeHudFraction >= 0f,
                            enter = fadeIn(tween(130)) + scaleIn(initialScale = 0.9f),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 24.dp),
                        ) {
                            val volIcon = when {
                                volumeHudFraction <= 0.01f -> Icons.Filled.VolumeMute
                                volumeHudFraction < 0.5f -> Icons.Filled.VolumeDown
                                else -> Icons.Filled.VolumeUp
                            }
                            GestureMeterHud(
                                icon = volIcon,
                                fraction = volumeHudFraction,
                            )
                        }

                        // Polished Loading / Buffering Overlay
                        val showInitialLoader = uiState is SyncedVideoUiState.Loading ||
                            (!isRendered && uiState is SyncedVideoUiState.Ready)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showInitialLoader || (isRendered && isBuffering),
                            enter = fadeIn(tween(160)),
                            exit = fadeOut(tween(220)),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (showInitialLoader) {
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                    Color.Black.copy(alpha = 0.92f),
                                                )
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.25f))
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = Color.Black.copy(alpha = 0.62f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.18f)),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                                    ) {
                                        ExpressiveLoadingIndicator(modifier = Modifier.size(38.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = when {
                                                uiState is SyncedVideoUiState.Loading -> "Finding Official Video…"
                                                !isRendered -> "Locking Audio Sync…"
                                                else -> stringResource(R.string.video_buffering)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.9f),
                                        )
                                    }
                                }
                            }
                        }

                        // Error State
                        if (uiState is SyncedVideoUiState.Error) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.video_stream_unavailable),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
                                Spacer(Modifier.height(12.dp))
                                FilledTonalButton(
                                    onClick = { viewModel.retry(track.title, track.artist, track.videoId, track.durationMs) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Text(stringResource(R.string.video_retry))
                                }
                            }
                        }

                        // Directional Double-Tap Seek Overlay (Left / Right)
                        LaunchedEffect(doubleTapFeedback) {
                            if (doubleTapFeedback != null) {
                                delay(650L)
                                doubleTapFeedback = null
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = doubleTapFeedback != null,
                            enter = fadeIn(tween(110)) + scaleIn(initialScale = 0.85f),
                            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.9f),
                            modifier = Modifier
                                .align(
                                    when (doubleTapSeekSide) {
                                        false -> Alignment.CenterStart
                                        true -> Alignment.CenterEnd
                                        null -> Alignment.Center
                                    }
                                )
                                .padding(horizontal = 36.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.68f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                ) {
                                    if (doubleTapSeekSide == false) {
                                        Icon(Icons.Filled.FastRewind, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        text = doubleTapFeedback.orEmpty(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    if (doubleTapSeekSide == true) {
                                        Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // Fullscreen Top Glass Scrim Bar
                        if (isFullscreen) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = controlsVisible,
                                enter = fadeIn(tween(160)),
                                exit = fadeOut(tween(180)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.82f),
                                                    Color.Transparent,
                                                )
                                            )
                                        )
                                        .padding(horizontal = 16.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { isFullscreen = false }) {
                                        Icon(
                                            Icons.Filled.FullscreenExit,
                                            contentDescription = stringResource(R.string.video_exit_fullscreen),
                                            tint = Color.White,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.78f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    // Live Sync Pill (tappable to open offset fine-tune)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { showSyncTuneBar = !showSyncTuneBar }
                                            .background(syncBadgeColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(syncBadgeColor.copy(alpha = pulseAlpha)),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = syncBadgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = syncBadgeColor,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    val isZoom = videoScalingMode == VideoScalingMode.ZOOM
                                    IconButton(
                                        onClick = {
                                            videoScalingMode = if (isZoom) VideoScalingMode.FIT else VideoScalingMode.ZOOM
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isZoom) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                                            contentDescription = if (isZoom) "Fit" else "Fill",
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        IconButton(
                                            onClick = {
                                                runCatching {
                                                    val params = PictureInPictureParams.Builder()
                                                        .setAspectRatio(Rational(16, 9))
                                                        .build()
                                                    activity?.enterPictureInPictureMode(params)
                                                }
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.SmartDisplay,
                                                contentDescription = "PiP",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { showQualityDialog = true }
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.HighQuality,
                                            contentDescription = stringResource(R.string.video_quality),
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (uiState is SyncedVideoUiState.Ready) {
                                                (uiState as SyncedVideoUiState.Ready).activeStream.height?.let { "${it}p" } ?: "Auto"
                                            } else {
                                                "Auto"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = onDismiss) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.common_cancel),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        // Center Playback Controls Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = controlsVisible && !showInitialLoader && uiState !is SyncedVideoUiState.Error,
                            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.92f),
                            exit = fadeOut(tween(150)),
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(26.dp),
                            ) {
                                Surface(
                                    onClick = {
                                        val newPos = (musicPlayer.currentPositionMs() - 10_000L).coerceAtLeast(0L)
                                        clockController.reset()
                                        musicPlayer.seekTo(newPos)
                                        videoPlayer.seekTo((newPos + manualOffsetMs).coerceAtLeast(0L))
                                    },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.24f)),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Replay10,
                                            contentDescription = "-10s",
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp),
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { musicPlayer.togglePlayPause() },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        val newPos = (musicPlayer.currentPositionMs() + 10_000L)
                                            .coerceAtMost(progressState.durationMs.coerceAtLeast(1L))
                                        clockController.reset()
                                        musicPlayer.seekTo(newPos)
                                        videoPlayer.seekTo((newPos + manualOffsetMs).coerceAtLeast(0L))
                                    },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.52f),
                                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.24f)),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Forward10,
                                            contentDescription = "+10s",
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Floating Glass Sync Fine-Tune Bar (inside viewport in fullscreen)
                        if (isFullscreen) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showSyncTuneBar && controlsVisible,
                                enter = fadeIn() + slideInVertically { it / 2 },
                                exit = fadeOut() + slideOutVertically { it / 2 },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 86.dp, start = 20.dp, end = 20.dp),
                            ) {
                                SyncOffsetTuneBar(
                                    offsetMs = manualOffsetMs,
                                    suggestedIntroOffsetMs = suggestedIntroOffsetMs,
                                    onAdjust = { delta ->
                                        manualOffsetMs = (manualOffsetMs + delta).coerceIn(-12_000L, 12_000L)
                                    },
                                    onSetExact = { manualOffsetMs = it },
                                    darkSurface = true,
                                )
                            }
                        }

                        // Fullscreen Bottom Seekbar overlay
                        if (isFullscreen) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = controlsVisible,
                                enter = fadeIn(tween(160)),
                                exit = fadeOut(tween(180)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.88f),
                                                )
                                            )
                                        )
                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                ) {
                                    VideoTimelineRow(
                                        currentPosition = currentPosition,
                                        durationMs = progressState.durationMs,
                                        isUserScrubbing = isUserScrubbing,
                                        scrubProgress = scrubProgress,
                                        onScrubChange = {
                                            isUserScrubbing = true
                                            scrubProgress = it
                                        },
                                        onScrubFinished = {
                                            isUserScrubbing = false
                                            val target = (scrubProgress * progressState.durationMs).toLong()
                                            clockController.reset()
                                            musicPlayer.seekTo(target)
                                            videoPlayer.seekTo((target + manualOffsetMs).coerceAtLeast(0L))
                                        },
                                        onToggleFullscreen = { isFullscreen = false },
                                        isFullscreen = true,
                                    )
                                }
                            }
                        }
                    }

                    // Compact Mode: Expandable Sync Offset Fine-Tuner Bar
                    if (!isFullscreen) {
                        AnimatedVisibility(
                            visible = showSyncTuneBar,
                            enter = fadeIn() + slideInVertically { -it / 3 },
                            exit = fadeOut() + slideOutVertically { -it / 3 },
                        ) {
                            Box(modifier = Modifier.padding(top = 10.dp)) {
                                SyncOffsetTuneBar(
                                    offsetMs = manualOffsetMs,
                                    suggestedIntroOffsetMs = suggestedIntroOffsetMs,
                                    onAdjust = { delta ->
                                        manualOffsetMs = (manualOffsetMs + delta).coerceIn(-12_000L, 12_000L)
                                    },
                                    onSetExact = { manualOffsetMs = it },
                                    darkSurface = false,
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        VideoTimelineRow(
                            currentPosition = currentPosition,
                            durationMs = progressState.durationMs,
                            isUserScrubbing = isUserScrubbing,
                            scrubProgress = scrubProgress,
                            onScrubChange = {
                                isUserScrubbing = true
                                scrubProgress = it
                            },
                            onScrubFinished = {
                                isUserScrubbing = false
                                val target = (scrubProgress * progressState.durationMs).toLong()
                                clockController.reset()
                                musicPlayer.seekTo(target)
                                videoPlayer.seekTo((target + manualOffsetMs).coerceAtLeast(0L))
                            },
                            onToggleFullscreen = { isFullscreen = true },
                            isFullscreen = false,
                        )
                    }
                }
            }
        }
    }

    if (showQualityDialog && uiState is SyncedVideoUiState.Ready) {
        val ready = uiState as SyncedVideoUiState.Ready
        VideoQualityDialog(
            bundle = ready.bundle,
            selectedHeight = ready.selectedHeight,
            onSelectQuality = { height ->
                viewModel.selectQuality(height)
            },
            onDismiss = { showQualityDialog = false },
        )
    }
}

@Composable
private fun GestureMeterHud(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    fraction: Float,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.68f),
        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.28f)),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(86.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SyncOffsetTuneBar(
    offsetMs: Long,
    suggestedIntroOffsetMs: Long,
    onAdjust: (Long) -> Unit,
    onSetExact: (Long) -> Unit,
    darkSurface: Boolean,
) {
    val containerColor = if (darkSurface) {
        Color.Black.copy(alpha = 0.76f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = if (darkSurface) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (darkSurface) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallStepChip(label = "-250ms", dark = darkSurface) { onAdjust(-250L) }
                    SmallStepChip(label = "-50ms", dark = darkSurface) { onAdjust(-50L) }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sync Offset",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                    val sign = if (offsetMs > 0) "+" else ""
                    Text(
                        text = if (offsetMs == 0L) "0 ms" else "$sign${offsetMs} ms",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = if (offsetMs == 0L) contentColor else MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallStepChip(label = "+50ms", dark = darkSurface) { onAdjust(50L) }
                    SmallStepChip(label = "+250ms", dark = darkSurface) { onAdjust(250L) }
                }
            }

            if (offsetMs != 0L || suggestedIntroOffsetMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (suggestedIntroOffsetMs > 0L && offsetMs != suggestedIntroOffsetMs) {
                        Surface(
                            onClick = { onSetExact(suggestedIntroOffsetMs) },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        ) {
                            Text(
                                text = "Auto MV Intro (+${"%.1f".format(suggestedIntroOffsetMs / 1000f)}s)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (offsetMs != 0L) {
                        Surface(
                            onClick = { onSetExact(0L) },
                            shape = RoundedCornerShape(50),
                            color = contentColor.copy(alpha = 0.12f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, null, tint = contentColor, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
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
private fun SmallStepChip(
    label: String,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (dark) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun VideoTimelineRow(
    currentPosition: Long,
    durationMs: Long,
    isUserScrubbing: Boolean,
    scrubProgress: Float,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
) {
    val durationSafe = durationMs.coerceAtLeast(1L)
    val sliderValue = if (isUserScrubbing) scrubProgress else (currentPosition.toFloat() / durationSafe).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(currentPosition),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (isFullscreen) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = onScrubChange,
            onValueChangeFinished = onScrubFinished,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = if (isFullscreen) Color.White.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (isFullscreen) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isFullscreen) stringResource(R.string.video_exit_fullscreen) else stringResource(R.string.video_enter_fullscreen),
                tint = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun VideoQualityDialog(
    bundle: SyncedVideoStreamBundle,
    selectedHeight: Int?,
    onSelectQuality: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    com.neonsong.app.ui.common.NeonPopupCardDialog(
        onDismissRequest = onDismiss,
        maxWidth = 380.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.video_quality_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(14.dp))

                val isAutoSelected = selectedHeight == null || selectedHeight <= 0
                val autoStream = bundle.fastStartStream
                QualityOptionRow(
                    label = stringResource(R.string.video_quality_auto_recommended),
                    subtitle = "${autoStream.displayQuality} • Fastest load & smooth sync",
                    badge = autoStream.codecLabel,
                    isSelected = isAutoSelected,
                    onClick = {
                        onSelectQuality(null)
                        onDismiss()
                    },
                )

                Spacer(Modifier.height(6.dp))

                val heights = bundle.availableStreams
                    .mapNotNull { it.height }
                    .distinct()
                    .sortedDescending()

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(heights) { h ->
                        val isSelected = !isAutoSelected && selectedHeight == h
                        val stream = bundle.availableStreams
                            .filter { it.height == h }
                            .maxByOrNull { (if (it.isHardwareFriendlyCodec) 10 else 0) + (if (it.is60Fps) 4 else 0) }
                        val label = stream?.displayQuality ?: "${h}p"
                        val subtitle = when {
                            h >= 2160 -> "Ultra HD 4K"
                            h >= 1440 -> "Quad HD 2K"
                            h >= 1080 -> "Full HD"
                            h >= 720 -> "High Definition • Fast"
                            else -> "Data Saver"
                        }
                        QualityOptionRow(
                            label = label,
                            subtitle = subtitle,
                            badge = stream?.codecLabel,
                            isSelected = isSelected,
                            onClick = {
                                onSelectQuality(h)
                                onDismiss()
                            },
                        )
                    }
                }
            }
    }
}

@Composable
private fun QualityOptionRow(
    label: String,
    subtitle: String? = null,
    badge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val rowShape = remember { SquircleShape(14.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = rowShape,
                borderWidth = if (isSelected) 1.15.dp else 0.7.dp,
                accentTint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                alphaScale = if (isSelected) 1.24f else 0.90f,
                interactive = true,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
