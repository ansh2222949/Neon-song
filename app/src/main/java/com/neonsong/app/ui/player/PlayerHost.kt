@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package com.neonsong.app.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.drawOutline
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.neonsong.app.data.artwork.ArtworkNormalizer
import com.neonsong.app.data.local.CanvasStyle
import com.neonsong.app.data.local.LyricsAnimation
import com.neonsong.app.data.local.LyricsUiVersion
import com.neonsong.app.data.local.PlayerFaceStyle
import com.neonsong.app.data.local.SpatialImmersionPreset
import com.neonsong.app.data.lyrics.LyricsRepository
import com.neonsong.app.data.lyrics.LyricsResult
import com.neonsong.app.data.playlist.LIKED_SONGS_MODE
import com.neonsong.app.data.playlist.PlaylistRepository
import com.neonsong.app.data.playlist.SavedPlaylist
import com.neonsong.app.features.lyrics.LyricsUiState
import com.neonsong.app.features.player.components.AddToPlaylistDialogHost
import com.neonsong.app.features.player.components.AudioControlsSheet
import com.neonsong.app.features.player.components.MainControls
import com.neonsong.app.features.player.components.PlayerArtwork
import com.neonsong.app.features.player.components.PlayerFloatingDock
import com.neonsong.app.features.player.components.PlayerProgressSlider
import com.neonsong.app.features.player.components.SeekBar
import com.neonsong.app.features.player.components.rememberSpatialTilt
import com.neonsong.app.features.player.domain.FullPlayerTab
import com.neonsong.app.features.player.domain.formatTime
import com.neonsong.app.features.player.domain.toGeneratedTrack
import com.neonsong.app.features.player.ui.MiniPlayer
import com.neonsong.app.features.queue.QueuePanel
import com.neonsong.app.playback.AutoMixState
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.playback.resolve.MetadataLog
import com.neonsong.app.ui.common.ArtworkViewModel
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.PredictiveBackScreen
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.theme.AnimatedIconButton
import com.neonsong.app.ui.theme.bounceClick
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import com.neonsong.app.util.LocalHapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

typealias FullPlayerTab = com.neonsong.app.features.player.domain.FullPlayerTab

private const val ACTION_PIP_CONTROL = "com.neonsong.app.ACTION_PIP_CONTROL"
private const val EXTRA_PIP_CMD = "pip_cmd"
private const val PIP_CMD_PREV = 1
private const val PIP_CMD_PLAY_PAUSE = 2
private const val PIP_CMD_NEXT = 3

val LocalMusicPlayer = staticCompositionLocalOf<MusicPlayer> {
    error("MusicPlayer is only available inside PlayerHost")
}

val LocalAddToPlaylist = staticCompositionLocalOf<(PlayableTrack) -> Unit> {
    error("Add-to-playlist is only available inside PlayerHost")
}

val LocalMiniPlayerScrollClearance = staticCompositionLocalOf { 0.dp }

internal fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? android.content.ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

/**
 * Tracks whether the Activity is currently in Picture-in-Picture (PiP) mode
 * and registers live RemoteAction controls (Previous, Play/Pause, Next).
 */
@Composable
private fun rememberPipController(
    player: MusicPlayer,
    isPlaying: Boolean,
    isVideoMode: Boolean,
): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current

    var isInPipMode by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity?.isInPictureInPictureMode == true,
        )
    }

    // Fallback sync on configuration change
    LaunchedEffect(configuration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            isInPipMode = activity.isInPictureInPictureMode
        }
    }

    // Listen to instant PiP mode transitions on ComponentActivity
    DisposableEffect(activity) {
        val compActivity = activity as? ComponentActivity
            ?: return@DisposableEffect onDispose {}
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        compActivity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            compActivity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    // BroadcastReceiver for PiP RemoteAction clicks (Previous / Play-Pause / Next)
    val currentPlayer by rememberUpdatedState(player)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != ACTION_PIP_CONTROL) return
                when (intent.getIntExtra(EXTRA_PIP_CMD, 0)) {
                    PIP_CMD_PREV -> currentPlayer.previous()
                    PIP_CMD_PLAY_PAUSE -> currentPlayer.togglePlayPause()
                    PIP_CMD_NEXT -> currentPlayer.next()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val buildPipParams = remember(context, isPlaying, isVideoMode) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fun makeRemoteAction(cmd: Int, iconRes: Int, title: String): RemoteAction {
                    val intent = Intent(ACTION_PIP_CONTROL).apply {
                        setPackage(context.packageName)
                        putExtra(EXTRA_PIP_CMD, cmd)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        cmd,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    return RemoteAction(
                        AndroidIcon.createWithResource(context, iconRes),
                        title,
                        title,
                        pendingIntent,
                    )
                }

                val actions = listOf(
                    makeRemoteAction(PIP_CMD_PREV, android.R.drawable.ic_media_previous, "Previous"),
                    makeRemoteAction(
                        PIP_CMD_PLAY_PAUSE,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (isPlaying) "Pause" else "Play",
                    ),
                    makeRemoteAction(PIP_CMD_NEXT, android.R.drawable.ic_media_next, "Next"),
                )

                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(actions)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setSeamlessResizeEnabled(true)
                            setAutoEnterEnabled(isVideoMode && isPlaying)
                        }
                    }
                    .build()
            } else {
                null
            }
        }
    }

    // Keep PiP Play/Pause icon & Auto-PiP state synced with live playback
    LaunchedEffect(isPlaying, isVideoMode, isInPipMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            runCatching {
                buildPipParams()?.let { activity.setPictureInPictureParams(it) }
            }
        }
    }

    val enterPip = remember(activity, buildPipParams) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                runCatching {
                    buildPipParams()?.let { activity.enterPictureInPictureMode(it) }
                }
            }
        }
    }

    return isInPipMode to enterPip
}

@Stable
class AppleSheetController(
    private val scope: CoroutineScope,
    initialExpanded: Boolean = false,
) {
    val progress = Animatable(if (initialExpanded) 1f else 0f)

    var isDragging by mutableStateOf(false)
        private set

    var miniArtBounds by mutableStateOf(Rect.Zero)
    var fullArtBounds by mutableStateOf(Rect.Zero)

    // Shared 3D horizontal swipe state so both Audio and Video cards respond 1:1 to finger swipes
    var cardDragX by mutableFloatStateOf(0f)

    private var targetProgress = if (initialExpanded) 1f else 0f
    private var snapJob: Job? = null
    private val dragChannel = Channel<Float>(Channel.CONFLATED)
    private var dragLoopJob: Job? = null

    val isExpanded: Boolean
        get() = progress.targetValue > 0.5f || progress.value > 0.5f

    private fun ensureDragLoop() {
        if (dragLoopJob?.isActive != true) {
            dragLoopJob = scope.launch(Dispatchers.Main.immediate) {
                for (target in dragChannel) {
                    progress.snapTo(target)
                }
            }
        }
    }

    private fun stopDragLoop() {
        dragLoopJob?.cancel()
        dragLoopJob = null
    }

    fun onDragBy(deltaY: Float, totalTravelPx: Float) {
        if (totalTravelPx <= 1f) return
        if (!isDragging) {
            isDragging = true
            targetProgress = progress.value
            snapJob?.cancel()
            ensureDragLoop()
        }
        val deltaFraction = -deltaY / totalTravelPx
        targetProgress += deltaFraction
        val clamped = when {
            targetProgress < 0f -> targetProgress * 0.18f
            targetProgress > 1f -> 1f + (targetProgress - 1f) * 0.15f
            else -> targetProgress
        }.coerceIn(-0.08f, 1.06f)

        dragChannel.trySend(clamped)
    }

    fun settle(velocityY: Float, totalTravelPx: Float, onDismissMini: (() -> Unit)? = null) {
        isDragging = false
        stopDragLoop()
        snapJob?.cancel()
        val safeTravel = totalTravelPx.coerceAtLeast(1f)
        val fractionVelocity = (-velocityY / safeTravel).coerceIn(-12f, 12f)
        val current = progress.value

        if (current < -0.03f && velocityY > 650f && onDismissMini != null) {
            targetProgress = 0f
            snapJob = scope.launch(Dispatchers.Main.immediate) {
                progress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
            }
            onDismissMini()
            return
        }

        val shouldExpand = when {
            fractionVelocity > 1.35f -> true
            fractionVelocity < -1.35f -> false
            else -> current > 0.45f
        }

        targetProgress = if (shouldExpand) 1f else 0f
        expandTo(shouldExpand, initialVelocity = fractionVelocity)
    }

    fun expandTo(
        expand: Boolean,
        initialVelocity: Float = 0f,
        faceStyle: com.neonsong.app.data.local.PlayerFaceStyle = com.neonsong.app.data.local.PlayerFaceStyle.DEFAULT,
    ) {
        isDragging = false
        stopDragLoop()
        snapJob?.cancel()
        targetProgress = if (expand) 1f else 0f
        val spec = when (faceStyle) {
            com.neonsong.app.data.local.PlayerFaceStyle.BACKGROUND_ONLY -> spring(
                dampingRatio = 0.94f,
                stiffness = if (expand) 300f else 340f,
                visibilityThreshold = 0.0005f,
            )
            com.neonsong.app.data.local.PlayerFaceStyle.FULLSCREEN_HERO -> spring(
                dampingRatio = 0.85f,
                stiffness = if (expand) 350f else 390f,
                visibilityThreshold = 0.0005f,
            )
            com.neonsong.app.data.local.PlayerFaceStyle.CARD_ONLY -> spring(
                dampingRatio = 0.98f,
                stiffness = if (expand) 560f else 600f,
                visibilityThreshold = 0.0005f,
            )
            com.neonsong.app.data.local.PlayerFaceStyle.COMPACT -> spring(
                dampingRatio = 0.86f,
                stiffness = if (expand) 490f else 520f,
                visibilityThreshold = 0.0005f,
            )
            com.neonsong.app.data.local.PlayerFaceStyle.DEFAULT -> spring(
                dampingRatio = 0.88f,
                stiffness = 440f,
                visibilityThreshold = 0.0005f,
            )
        }
        snapJob = scope.launch(Dispatchers.Main.immediate) {
            progress.animateTo(
                targetValue = if (expand) 1f else 0f,
                initialVelocity = initialVelocity,
                animationSpec = spec,
            )
        }
    }
}

/**
 * Renders the 3D background deck peek cards (Previous & Next tracks in queue)
 * behind the active Audio or Video card during a 3D horizontal swipe.
 */
@Composable
private fun SwipeDeckPeekCards(
    prevTrack: PlayableTrack?,
    nextTrack: PlayableTrack?,
    shownDragX: () -> Float,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 220.dp.toPx().coerceAtLeast(1f) }
    val basePeekOffsetPx = with(density) { 36.dp.toPx() }
    val maxShadowPx = with(density) { 16.dp.toPx() }
    val cardShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    // Only recomposes if swipe flips direction (left <-> right), never on every pixel
    val targetTrack by remember(prevTrack, nextTrack) {
        androidx.compose.runtime.derivedStateOf {
            val dx = shownDragX()
            if (abs(dx / maxSwipePx) <= 0.015f) null
            else if (dx > 0f) prevTrack else nextTrack
        }
    }
    val track = targetTrack ?: return

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f),
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
            .size(width = cardWidth, height = cardHeight)
            .graphicsLayer {
                val dragX = shownDragX()
                val swipeFraction = (dragX / maxSwipePx).coerceIn(-1f, 1f)
                val absFraction = abs(swipeFraction)
                val directionSign = if (swipeFraction > 0f) -1f else 1f
                val peekAlpha = (absFraction * 1.35f).coerceIn(0f, 0.92f)
                val peekScale = androidx.compose.ui.util.lerp(0.84f, 0.96f, absFraction)

                translationX = basePeekOffsetPx * (1f - absFraction) * directionSign - (dragX * 0.22f)
                scaleX = peekScale
                scaleY = peekScale
                rotationY = androidx.compose.ui.util.lerp(directionSign * -18f, 0f, absFraction)
                rotationZ = -swipeFraction * 3.5f
                cameraDistance = 18f * this.density
                alpha = peekAlpha
                shadowElevation = maxShadowPx * absFraction
                shape = cardShape
                clip = false
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            PlayerArtwork(
                track = track,
                modifier = Modifier.fillMaxSize(),
                corner = cornerRadius,
                isPlaying = false,
                pausedForTransition = true,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val absFraction = abs(shownDragX() / maxSwipePx).coerceIn(0f, 1f)
                        alpha = 0.28f * (1f - absFraction)
                    }
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun FloatingMorphArtworkOverlay(
    track: PlayableTrack,
    sheetController: AppleSheetController,
    isPlaying: Boolean,
    isMiniPlayerVisible: Boolean,
    progressState: StateFlow<PlaybackProgressState>,
    prevTrack: PlayableTrack? = null,
    nextTrack: PlayableTrack? = null,
    isVideoMode: Boolean = false,
    activeVideoId: String? = null,
    musicPlayer: MusicPlayer? = null,
    videoViewModel: SyncedVideoViewModel,
    videoFullscreen: Boolean = false,
    isInPipMode: Boolean = false,
    onEnterPipMode: () -> Unit = {},
    videoScalingMode: VideoScalingMode = VideoScalingMode.FIT,
    onToggleVideoScalingMode: () -> Unit = {},
    videoQualityLabel: String = "Auto",
    onOpenVideoQuality: () -> Unit = {},
    onSetVideoFullscreen: (Boolean) -> Unit = {},
    onCloseVideoMode: () -> Unit = {},
    isAudioMorphActive: Boolean = false,
    isCardHiddenForCanvas: Boolean = false,
    playerFaceStyle: PlayerFaceStyle = PlayerFaceStyle.DEFAULT,
    sleeveCanvas: com.neonsong.app.data.canvas.CanvasArtwork? = null,
    ambientColor: Color = Color.White,
    ambientCompanion: Color = Color.White,
) {
    val hasActiveVideo = isVideoMode && !activeVideoId.isNullOrBlank() && musicPlayer != null

    // If not video mode and morph is not active, do not compose (zero overhead and no touch occlusion)
    if (!hasActiveVideo && !isAudioMorphActive) return
    val isOverlaySettledCollapsed by remember {
        androidx.compose.runtime.derivedStateOf {
            sheetController.progress.value <= 0.005f
        }
    }
    if (hasActiveVideo && isOverlaySettledCollapsed && !isMiniPlayerVisible && !videoFullscreen && !isInPipMode) return

    val overlayVisible = hasActiveVideo || isAudioMorphActive

    val density = LocalDensity.current
    val view = LocalView.current
    val hapticManager = LocalHapticManager.current
    val trackIdentity = remember(track.videoId, track.title, track.artist) {
        "${track.videoId}_${track.title}_${track.artist}"
    }

    // Pinch-to-Zoom & Pan (P2P) state for Fullscreen Video Mode
    var pinchZoom by remember(trackIdentity, videoFullscreen) { mutableFloatStateOf(1f) }
    var panOffset by remember(trackIdentity, videoFullscreen) { mutableStateOf(Offset.Zero) }
    var isControlsLocked by remember(trackIdentity, videoFullscreen) { mutableStateOf(false) }
    var manualSyncOffsetMs by rememberSaveable(trackIdentity) { mutableLongStateOf(0L) }

    LaunchedEffect(videoScalingMode) {
        pinchZoom = 1f
        panOffset = Offset.Zero
    }

    // Smooth spring for 3D horizontal swipe on both Audio Morph & Video Card
    val shownCardDragXState = animateFloatAsState(
        targetValue = sheetController.cardDragX,
        animationSpec = ExpressiveMotion.FloatFluidSpring,
        label = "floatingMorphCard3DSwipeX",
    )
    val showVideoPeekDeck by remember {
        androidx.compose.runtime.derivedStateOf { abs(shownCardDragXState.value) > 2f }
    }

    // Subtle spatial tilt in Expanded Video Mode & Floating Morph Overlay
    val isOverlaySettledExpanded by remember {
        androidx.compose.runtime.derivedStateOf {
            sheetController.progress.value > 0.85f
        }
    }
    val rawVideoTilt by rememberSpatialTilt(
        enabled = isOverlaySettledExpanded && !sheetController.isDragging && !videoFullscreen && !isInPipMode && isPlaying,
    )
    val videoTiltXState = animateFloatAsState(
        targetValue = rawVideoTilt.x * 4.2f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy, visibilityThreshold = 0.05f),
        label = "videoTiltX",
    )
    val videoTiltYState = animateFloatAsState(
        targetValue = rawVideoTilt.y * 3.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy, visibilityThreshold = 0.05f),
        label = "videoTiltY",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (videoFullscreen || isInPipMode) 10f else 0.2f),
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val miniRect = sheetController.miniArtBounds.takeIf { !it.isEmpty } ?: run {
            val sizePx = with(density) { 48.dp.toPx() }
            val leftPx = with(density) { 28.dp.toPx() }
            val topPx = screenHeightPx - with(density) { 148.dp.toPx() }
            Rect(leftPx, topPx, leftPx + sizePx, topPx + sizePx)
        }

        val fullRect = sheetController.fullArtBounds
        val rawFullRect = if (!fullRect.isEmpty) {
            fullRect
        } else if (hasActiveVideo) {
            val estimatedWidth = minOf(screenWidthPx - with(density) { 40.dp.toPx() }, with(density) { 420.dp.toPx() })
            val estimatedHeight = estimatedWidth * (9f / 16f)
            val estimatedLeft = (screenWidthPx - estimatedWidth) / 2f
            val estimatedTop = screenHeightPx * 0.20f
            Rect(estimatedLeft, estimatedTop, estimatedLeft + estimatedWidth, estimatedTop + estimatedHeight)
        } else {
            val maxArtPx = with(density) { 370.dp.toPx() }
            val minDim = minOf(screenWidthPx, screenHeightPx) - with(density) { 48.dp.toPx() }
            val artSize = minDim.coerceIn(1f, maxArtPx)
            val estimatedLeft = (screenWidthPx - artSize) / 2f
            val estimatedTop = screenHeightPx * 0.14f
            Rect(estimatedLeft, estimatedTop, estimatedLeft + artSize, estimatedTop + artSize)
        }

        // Match FullPlayer's paused (0.88f) vs playing (1.0f) scale at p == 1f for seamless handoff
        val fullScaleFactor = if (hasActiveVideo || isPlaying) 1f else 0.88f
        val effectiveFullRect = if (fullScaleFactor == 1f) {
            rawFullRect
        } else {
            val w = rawFullRect.width * fullScaleFactor
            val h = rawFullRect.height * fullScaleFactor
            val cx = rawFullRect.center.x
            val cy = rawFullRect.center.y
            Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        }

        val targetFullFraction = if (hasActiveVideo && (videoFullscreen || isInPipMode)) 1f else 0f
        val fullscreenProgress by animateFloatAsState(
            targetValue = targetFullFraction,
            animationSpec = if (isInPipMode) tween(0) else tween(220, easing = FastOutSlowInEasing),
            label = "videoSurfaceFullscreenBounds",
        )

        val baseWidth = effectiveFullRect.width.coerceAtLeast(1f)
        val baseHeight = effectiveFullRect.height.coerceAtLeast(1f)
        val targetWidth = androidx.compose.ui.util.lerp(baseWidth, screenWidthPx, fullscreenProgress)
        val targetHeight = androidx.compose.ui.util.lerp(baseHeight, screenHeightPx, fullscreenProgress)

        val expandedCornerDp = when {
            hasActiveVideo -> 24.dp
            playerFaceStyle == PlayerFaceStyle.CARD_ONLY -> 22.dp
            playerFaceStyle == PlayerFaceStyle.COMPACT -> 22.dp
            playerFaceStyle == PlayerFaceStyle.FULLSCREEN_HERO -> 16.dp
            else -> 32.dp
        }
        val cornerDp = androidx.compose.ui.unit.lerp(
            expandedCornerDp,
            0.dp,
            fullscreenProgress,
        )
        val shadowDp = if (overlayVisible) {
            (if (isPlaying) 36.dp else 18.dp) * (1f - fullscreenProgress)
        } else {
            0.dp
        }

        val cardWidthDp = with(density) { targetWidth.toDp() }
        val cardHeightDp = with(density) { targetHeight.toDp() }

        // 3D Background Peek Deck Cards during 3D horizontal swipe
        if (overlayVisible && fullscreenProgress < 0.1f && !isInPipMode && showVideoPeekDeck) {
            SwipeDeckPeekCards(
                prevTrack = prevTrack,
                nextTrack = nextTrack,
                shownDragX = { shownCardDragXState.value },
                cardWidth = cardWidthDp,
                cardHeight = cardHeightDp,
                cornerRadius = cornerDp,
                modifier = Modifier.offset {
                    IntOffset(
                        x = effectiveFullRect.left.roundToInt(),
                        y = effectiveFullRect.top.roundToInt(),
                    )
                },
            )
        }

        Surface(
            shape = SquircleShape(cornerDp),
            color = if (hasActiveVideo) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)
            },
            shadowElevation = shadowDp,
            border = if (fullscreenProgress < 0.5f && !isInPipMode) {
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            ambientCompanion.copy(alpha = 0.30f),
                            ambientColor.copy(alpha = 0.40f),
                        ),
                    ),
                )
            } else {
                null
            },
            modifier = Modifier
                .size(
                    width = cardWidthDp,
                    height = cardHeightDp,
                )
                .graphicsLayer {
                    val morphProgress = sheetController.progress.value.coerceIn(0f, 1f)
                    val curLeft = androidx.compose.ui.util.lerp(
                        androidx.compose.ui.util.lerp(miniRect.left, effectiveFullRect.left, morphProgress),
                        0f,
                        fullscreenProgress,
                    )
                    val curTop = androidx.compose.ui.util.lerp(
                        androidx.compose.ui.util.lerp(miniRect.top, effectiveFullRect.top, morphProgress),
                        0f,
                        fullscreenProgress,
                    )
                    val curW = androidx.compose.ui.util.lerp(
                        androidx.compose.ui.util.lerp(miniRect.width, effectiveFullRect.width, morphProgress),
                        screenWidthPx,
                        fullscreenProgress,
                    )
                    val curH = androidx.compose.ui.util.lerp(
                        androidx.compose.ui.util.lerp(miniRect.height, effectiveFullRect.height, morphProgress),
                        screenHeightPx,
                        fullscreenProgress,
                    )
                    transformOrigin = TransformOrigin(0f, 0f)
                    val styleScaleAdjust = when {
                        hasActiveVideo -> 1f
                        playerFaceStyle == PlayerFaceStyle.BACKGROUND_ONLY ->
                            androidx.compose.ui.util.lerp(1f, 0.78f, morphProgress)
                        playerFaceStyle == PlayerFaceStyle.FULLSCREEN_HERO ->
                            androidx.compose.ui.util.lerp(1f, 1.14f, morphProgress)
                        else -> 1f
                    }
                    val sx = (curW / targetWidth) * styleScaleAdjust
                    val sy = (curH / targetHeight) * styleScaleAdjust
                    scaleX = sx
                    scaleY = sy
                    val shownDragX = shownCardDragXState.value
                    val tiltX = videoTiltXState.value
                    val tiltY = videoTiltYState.value
                    val active3DWeight = (morphProgress * (1f - fullscreenProgress)).coerceIn(0f, 1f)
                    translationX = curLeft + (shownDragX + (tiltX * 0.75f)) * active3DWeight
                    translationY = curTop + (tiltY * 0.55f) * active3DWeight
                    rotationZ = ((shownDragX / 68f) + (tiltX * 0.10f)) * active3DWeight
                    rotationY = ((tiltX - (shownDragX / 36f)).coerceIn(-14f, 14f)) * active3DWeight
                    rotationX = ((-tiltY).coerceIn(-6f, 6f)) * active3DWeight
                    cameraDistance = 18f * this.density
                    alpha = when {
                        !overlayVisible -> 0f
                        hasActiveVideo -> 1f
                        playerFaceStyle == PlayerFaceStyle.BACKGROUND_ONLY || isCardHiddenForCanvas ->
                            (1f - (morphProgress / 0.55f)).coerceIn(0f, 1f)
                        playerFaceStyle == PlayerFaceStyle.FULLSCREEN_HERO ->
                            (1f - ((morphProgress - 0.45f) / 0.40f)).coerceIn(0f, 1f)
                        else -> 1f
                    }
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                if (hasActiveVideo) {
                    SyncedVideoSurface(
                    track = track,
                    musicPlayer = musicPlayer!!,
                    videoId = activeVideoId!!,
                    videoScalingMode = videoScalingMode,
                    manualSyncOffsetMs = manualSyncOffsetMs,
                    viewModel = videoViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(SquircleShape(cornerDp))
                        .graphicsLayer {
                            if (videoFullscreen && !isInPipMode) {
                                scaleX = pinchZoom
                                scaleY = pinchZoom
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                        },
                )

                // 1. Normal Video Card HUD (hidden in Fullscreen & PiP)
                if (!isInPipMode && fullscreenProgress < 0.85f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val curP = sheetController.progress.value.coerceIn(0f, 1f)
                                val hudAlpha = ((curP - 0.65f) / 0.35f).coerceIn(0f, 1f) * (1f - fullscreenProgress)
                                alpha = hudAlpha
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.52f), Color.Transparent),
                                    ),
                                ),
                        )

                        Surface(
                            onClick = onOpenVideoQuality,
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.48f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676)),
                                )
                                Text(
                                    text = "SYNC • $videoQualityLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                LiquidGlassIconButton(
                                    onClick = onEnterPipMode,
                                    modifier = Modifier.size(34.dp),
                                    shape = CircleShape,
                                    contentDescription = "Picture in picture",
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SmartDisplay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                            val isZoom = videoScalingMode == VideoScalingMode.ZOOM
                            LiquidGlassIconButton(
                                onClick = onToggleVideoScalingMode,
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                contentDescription = if (isZoom) "Fit video" else "Zoom video",
                            ) {
                                Icon(
                                    imageVector = if (isZoom) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            LiquidGlassIconButton(
                                onClick = { onSetVideoFullscreen(true) },
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                accentTint = MaterialTheme.colorScheme.primary,
                                contentDescription = "Expand fullscreen video",
                            ) {
                                Icon(
                                    Icons.Filled.Fullscreen,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            LiquidGlassIconButton(
                                onClick = onCloseVideoMode,
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                contentDescription = "Switch to audio artwork",
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }

                        // Compact manual A/V sync nudge capsule for recordings with intro/codec offset
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { manualSyncOffsetMs = (manualSyncOffsetMs - 250L).coerceIn(-5_000L, 5_000L) },
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.50f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            ) {
                                Text(
                                    text = "−",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                            Surface(
                                onClick = { manualSyncOffsetMs = 0L },
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.50f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            ) {
                                val secText = if (manualSyncOffsetMs == 0L) {
                                    "0.0s"
                                } else {
                                    val sign = if (manualSyncOffsetMs > 0L) "+" else ""
                                    String.format(java.util.Locale.US, "%s%.2fs", sign, manualSyncOffsetMs / 1000.0)
                                }
                                Text(
                                    text = secText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Surface(
                                onClick = { manualSyncOffsetMs = (manualSyncOffsetMs + 250L).coerceIn(-5_000L, 5_000L) },
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.50f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            ) {
                                Text(
                                    text = "+",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                // 2. Fullscreen Video HUD + Pinch-to-Zoom/Pan + PiP Launch (Directly inside FloatingMorphArtworkOverlay!)
                AnimatedVisibility(
                    visible = videoFullscreen && !isInPipMode,
                    enter = fadeIn(tween(200, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val videoProgress by progressState.collectAsStateWithLifecycle()
                    var fullscreenControlsVisible by remember(videoFullscreen) { mutableStateOf(true) }
                    var scrubFraction by remember(trackIdentity) { mutableFloatStateOf(-1f) }
                    var brightnessHudValue by remember { mutableFloatStateOf(-1f) }
                    var volumeHudFraction by remember { mutableFloatStateOf(-1f) }
                    var doubleTapSeekFeedback by remember { mutableStateOf<String?>(null) }
                    var doubleTapSeekSide by remember { mutableStateOf<Boolean?>(null) }

                    val activity = remember(view) { view.context.findActivity() }
                    DisposableEffect(activity) {
                        onDispose {
                            activity?.window?.let { win ->
                                val lp = win.attributes
                                if (lp.screenBrightness >= 0f) {
                                    lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                    win.attributes = lp
                                }
                            }
                        }
                    }
                    val audioManager = remember(view.context) {
                        view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    }

                    LaunchedEffect(fullscreenControlsVisible, videoFullscreen, scrubFraction) {
                        if (videoFullscreen && fullscreenControlsVisible && scrubFraction < 0f) {
                            delay(3400L)
                            fullscreenControlsVisible = false
                        }
                    }
                    LaunchedEffect(brightnessHudValue) {
                        if (brightnessHudValue >= 0f) {
                            delay(1200L)
                            brightnessHudValue = -1f
                        }
                    }
                    LaunchedEffect(volumeHudFraction) {
                        if (volumeHudFraction >= 0f) {
                            delay(1200L)
                            volumeHudFraction = -1f
                        }
                    }
                    LaunchedEffect(doubleTapSeekFeedback) {
                        if (doubleTapSeekFeedback != null) {
                            delay(650L)
                            doubleTapSeekFeedback = null
                            doubleTapSeekSide = null
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // 2-Finger Pinch-to-Zoom & Pan (P2P) anywhere on Fullscreen Video
                            .pointerInput(trackIdentity, isControlsLocked) {
                                if (isControlsLocked) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val newZoom = (pinchZoom * zoomChange).coerceIn(1f, 3f)
                                            pinchZoom = newZoom
                                            if (newZoom > 1.02f) {
                                                val maxX = (size.width * (newZoom - 1f)) / 2f
                                                val maxY = (size.height * (newZoom - 1f)) / 2f
                                                panOffset = Offset(
                                                    x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                    y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY),
                                                )
                                            } else {
                                                panOffset = Offset.Zero
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                    ) {
                        // Left & Right 1-Finger Gesture Zones (Tap, Double-Tap Seek, Brightness & Volume)
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(trackIdentity, isControlsLocked) {
                                        detectTapGestures(
                                            onTap = { fullscreenControlsVisible = !fullscreenControlsVisible },
                                            onDoubleTap = {
                                                if (isControlsLocked) return@detectTapGestures
                                                hapticManager.click()
                                                val upperBound = videoProgress.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                                musicPlayer.seekTo((videoProgress.positionMs - 10_000L).coerceIn(0L, upperBound))
                                                doubleTapSeekFeedback = "-10s"
                                                doubleTapSeekSide = false
                                                fullscreenControlsVisible = true
                                            },
                                        )
                                    }
                                    .pointerInput(trackIdentity, isControlsLocked) {
                                        if (isControlsLocked) return@pointerInput
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
                                    },
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(trackIdentity, isControlsLocked) {
                                        detectTapGestures(
                                            onTap = { fullscreenControlsVisible = !fullscreenControlsVisible },
                                            onDoubleTap = {
                                                if (isControlsLocked) return@detectTapGestures
                                                hapticManager.click()
                                                val upperBound = videoProgress.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                                musicPlayer.seekTo((videoProgress.positionMs + 10_000L).coerceIn(0L, upperBound))
                                                doubleTapSeekFeedback = "+10s"
                                                doubleTapSeekSide = true
                                                fullscreenControlsVisible = true
                                            },
                                        )
                                    }
                                    .pointerInput(trackIdentity, isControlsLocked) {
                                        if (isControlsLocked) return@pointerInput
                                        var dragFraction = -1f
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                if (audioManager != null) {
                                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                    dragFraction = cur.toFloat() / max.toFloat()
                                                }
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                if (audioManager != null && dragFraction >= 0f) {
                                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                                    dragFraction = (dragFraction - dragAmount / 480f).coerceIn(0f, 1f)
                                                    val targetVol = (dragFraction * max).roundToInt().coerceIn(0, max)
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                                    volumeHudFraction = dragFraction
                                                }
                                            },
                                        )
                                    },
                            )
                        }

                        // Screen Lock Toggle Pill (always accessible when tapped in Fullscreen)
                        AnimatedVisibility(
                            visible = fullscreenControlsVisible,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(240)),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(start = 16.dp),
                        ) {
                            if (brightnessHudValue < 0f) {
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        isControlsLocked = !isControlsLocked
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isControlsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                        contentDescription = if (isControlsLocked) "Unlock controls" else "Lock controls",
                                        tint = if (isControlsLocked) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        // Brightness HUD
                        AnimatedVisibility(
                            visible = brightnessHudValue >= 0f && !isControlsLocked,
                            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.9f),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 36.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.3f)),
                                contentColor = Color.White,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Filled.LightMode, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.BottomCenter,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(brightnessHudValue.coerceIn(0f, 1f))
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                    Text(
                                        text = "${(brightnessHudValue * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        // Volume HUD
                        AnimatedVisibility(
                            visible = volumeHudFraction >= 0f && !isControlsLocked,
                            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.9f),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 36.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.3f)),
                                contentColor = Color.White,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    val volIcon = when {
                                        volumeHudFraction <= 0.01f -> Icons.Filled.VolumeMute
                                        volumeHudFraction < 0.5f -> Icons.Filled.VolumeDown
                                        else -> Icons.AutoMirrored.Filled.VolumeUp
                                    }
                                    Icon(volIcon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.BottomCenter,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(volumeHudFraction.coerceIn(0f, 1f))
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                    Text(
                                        text = "${(volumeHudFraction * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        // Double-Tap ±10s Seek Pill
                        AnimatedVisibility(
                            visible = doubleTapSeekFeedback != null && !isControlsLocked,
                            enter = fadeIn(tween(90)) + scaleIn(initialScale = 0.85f),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier
                                .align(if (doubleTapSeekSide == true) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(horizontal = 54.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.70f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
                                contentColor = Color.White,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = if (doubleTapSeekSide == true) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = doubleTapSeekFeedback.orEmpty(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        // Top Fullscreen Bar
                        AnimatedVisibility(
                            visible = fullscreenControlsVisible && !isControlsLocked,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(240)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                                    ),
                                )
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        onSetVideoFullscreen(false)
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.FullscreenExit,
                                        contentDescription = "Exit fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.78f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFF00E676).copy(alpha = 0.22f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E676)),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (pinchZoom > 1.05f) {
                                                    "${"%.1f".format(pinchZoom)}x • $videoQualityLabel"
                                                } else {
                                                    "SYNC • $videoQualityLabel"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF00E676),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                val isZoom = videoScalingMode == VideoScalingMode.ZOOM || pinchZoom > 1.05f
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        if (pinchZoom > 1.05f) {
                                            pinchZoom = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            onToggleVideoScalingMode()
                                        }
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isZoom) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                                        contentDescription = if (isZoom) "Reset / Fit video" else "Fill screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    FullscreenVideoGlassButton(
                                        onClick = {
                                            hapticManager.click()
                                            onEnterPipMode()
                                        },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.SmartDisplay,
                                            contentDescription = "Picture in picture",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        onOpenVideoQuality()
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.HighQuality,
                                        contentDescription = "Video quality",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }

                        // Center Transport Controls (Previous, -10s, Play/Pause, +10s, Next)
                        AnimatedVisibility(
                            visible = fullscreenControlsVisible && !isControlsLocked,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(240)),
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        musicPlayer.previous()
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Icon(Icons.Filled.SkipPrevious, "Previous track", tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        val upperBound = videoProgress.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                        musicPlayer.seekTo((videoProgress.positionMs - 10_000L).coerceIn(0L, upperBound))
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(54.dp),
                                ) {
                                    Icon(Icons.Filled.FastRewind, "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(25.dp))
                                }
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        musicPlayer.togglePlayPause()
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(68.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause video" else "Play video",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp),
                                    )
                                }
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        val upperBound = videoProgress.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                        musicPlayer.seekTo((videoProgress.positionMs + 10_000L).coerceIn(0L, upperBound))
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(54.dp),
                                ) {
                                    Icon(Icons.Filled.FastForward, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(25.dp))
                                }
                                FullscreenVideoGlassButton(
                                    onClick = {
                                        hapticManager.click()
                                        musicPlayer.next()
                                        fullscreenControlsVisible = true
                                    },
                                    modifier = Modifier.size(46.dp),
                                ) {
                                    Icon(Icons.Filled.SkipNext, "Next track", tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                        }

                        // Bottom Scrubber Pill
                        AnimatedVisibility(
                            visible = fullscreenControlsVisible && !isControlsLocked,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(240)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                        ) {
                            val durationMs = videoProgress.durationMs.takeIf { it > 0L }
                                ?: track.durationMs?.takeIf { it > 0L }
                                ?: 0L
                            val positionMs = videoProgress.positionMs.coerceAtLeast(0L)
                                .let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
                            val progressFraction = if (durationMs > 0L) {
                                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val shownFraction = if (scrubFraction >= 0f) scrubFraction else progressFraction
                            val shownPositionMs = if (scrubFraction >= 0f && durationMs > 0L) {
                                (scrubFraction * durationMs).toLong()
                            } else {
                                positionMs
                            }
                            val isDark = LocalIsDarkTheme.current
                            val controlsShape = RoundedCornerShape(22.dp)
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.92f),
                                shape = controlsShape,
                                color = Color.Black.copy(alpha = 0.54f),
                                border = BorderStroke(
                                    0.8.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDark) 0.28f else 0.48f),
                                            Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
                                        ),
                                    ),
                                ),
                                contentColor = Color.White,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = formatTime(shownPositionMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                    )
                                    Slider(
                                        value = shownFraction,
                                        onValueChange = { value ->
                                            scrubFraction = value
                                            fullscreenControlsVisible = true
                                        },
                                        onValueChangeFinished = {
                                            if (durationMs > 0L && scrubFraction >= 0f) {
                                                musicPlayer.seekTo((scrubFraction.coerceIn(0f, 1f) * durationMs).toLong())
                                            }
                                            scrubFraction = -1f
                                        },
                                        enabled = durationMs > 0L,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.82f),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                PlayerArtwork(
                    track = track,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(SquircleShape(cornerDp)),
                    corner = cornerDp,
                    canvas = sleeveCanvas,
                    isPlaying = isPlaying,
                    pausedForTransition = true,
                )
            }
        }
    }
}
}

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
    private val syncedVideoRepository: com.neonsong.app.data.video.SyncedVideoRepository,
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

    init {
        // 1. Reactively sync saved head scale & vocal separation into native C++ engine
        viewModelScope.launch {
            settingsPreferences.settings
                .map { it.hrtfHeadScale to it.vocalInstrumentSeparation }
                .distinctUntilChanged()
                .collect { (scale, separation) ->
                    com.neonsong.audio.AudioEngineBridge.setHrtfHeadScale(scale)
                    com.neonsong.audio.AudioEngineBridge.setVocalInstrumentSeparation(separation)
                }
        }
        // 2. Persist live slider/preset changes back to SettingsPreferences
        viewModelScope.launch {
            com.neonsong.audio.AudioEngineBridge.hrtfHeadScale
                .collect { scale ->
                    settingsPreferences.setHrtfHeadScale(scale)
                }
        }
        viewModelScope.launch {
            com.neonsong.audio.AudioEngineBridge.vocalInstrumentSeparation
                .collect { separation ->
                    settingsPreferences.setVocalInstrumentSeparation(separation)
                }
        }
        // 3. Parallel 0ms video prefetch: pre-resolve current + upcoming track simultaneously
        viewModelScope.launch {
            player.state
                .map { s ->
                    val cur = s.current
                    val curDur = cur?.durationMs?.takeIf { it > 0L } ?: s.durationMs.takeIf { it > 0L }
                    val next = if (!s.shuffleEnabled) s.queue.getOrNull(s.currentIndex + 1) else null
                    Triple(cur, curDur, next)
                }
                .distinctUntilChanged()
                .collect { (currentTrack, curDur, upcomingTrack) ->
                    if (currentTrack != null) {
                        syncedVideoRepository.prefetchVideo(
                            trackTitle = currentTrack.title,
                            trackArtist = currentTrack.artist,
                            videoId = currentTrack.videoId,
                            targetDurationMs = curDur,
                        )
                    }
                    if (upcomingTrack != null && upcomingTrack != currentTrack) {
                        syncedVideoRepository.prefetchVideo(
                            trackTitle = upcomingTrack.title,
                            trackArtist = upcomingTrack.artist,
                            videoId = upcomingTrack.videoId,
                            targetDurationMs = upcomingTrack.durationMs,
                        )
                    }
                }
        }
    }

    fun navigateToDownloads() {
        routeNavigator.navigateTo(com.neonsong.app.ui.navigation.Screen.Downloads.route)
    }

    private val _customPlaylists = MutableStateFlow<List<SavedPlaylist>>(emptyList())
    val customPlaylists = _customPlaylists.asStateFlow()
    private var customPlaylistsLoaded = false
    val likedTrackKeys = likedSongsManager.likedTrackKeys

    private data class ResolvedVideoEntry(
        val videoId: String,
        val verifiedWithDuration: Boolean,
    )

    private val _resolvedMusicVideoIds = MutableStateFlow<Map<String, ResolvedVideoEntry>>(emptyMap())
    val resolvedMusicVideoIds: StateFlow<Map<String, String>> = _resolvedMusicVideoIds
        .map { map -> map.mapValues { it.value.videoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var activeVideoResolveJob: Job? = null
    private var prefetchVideoResolveJob: Job? = null

    fun videoLookupKey(track: PlayableTrack): String =
        "${track.videoId.orEmpty()}|${track.title.trim().lowercase()}|${track.artist.trim().lowercase()}"

    fun isSpecialVersionTrack(title: String, artist: String): Boolean {
        val combined = "$title $artist".lowercase()
        return VERSION_MARKERS.any { combined.contains(it) }
    }

    private fun stripAudioOnlyNoise(title: String): String =
        title
            .replace(BRACKET_NOISE_REGEX, "")
            .replace(SUFFIX_NOISE_REGEX, "")
            .trim()
            .ifBlank { title.trim() }

    fun resolveMusicVideoForTrack(
        track: PlayableTrack,
        isVideoModeActive: Boolean = false,
    ) {
        val key = videoLookupKey(track)
        val liveState = player.state.value
        val knownDurationMs = track.durationMs?.takeIf { it > 0L }
            ?: liveState.durationMs.takeIf { it > 0L && liveState.current?.title == track.title }

        val existing = _resolvedMusicVideoIds.value[key]
        if (existing != null && (existing.verifiedWithDuration || knownDurationMs == null)) {
            return
        }

        val directTrackVideoId = track.videoId?.takeIf { it.isNotBlank() }?.let { extractYouTubeVideoId(it) }
        if (directTrackVideoId != null) {
            _resolvedMusicVideoIds.update {
                it + (key to ResolvedVideoEntry(directTrackVideoId, verifiedWithDuration = true))
            }
            syncedVideoRepository.prefetchVideo(
                trackTitle = track.title,
                trackArtist = track.artist,
                videoId = directTrackVideoId,
                targetDurationMs = knownDurationMs,
                trackAlbum = track.album,
            )
            if (isVideoModeActive) {
                val upcoming = liveState.queue.getOrNull(liveState.currentIndex + 1)
                if (upcoming != null && _resolvedMusicVideoIds.value[videoLookupKey(upcoming)] == null) {
                    prefetchVideoForUpcomingTrack(upcoming)
                }
            }
            return
        }

        activeVideoResolveJob?.cancel()
        activeVideoResolveJob = viewModelScope.launch(Dispatchers.IO) {
            coroutineContext.ensureActive()

            val latestState = player.state.value
            val latestCurrent = latestState.current
            if (latestCurrent != null &&
                (!latestCurrent.title.equals(track.title, ignoreCase = true) ||
                    !latestCurrent.artist.equals(track.artist, ignoreCase = true))
            ) {
                return@launch
            }

            val effectiveDurationMs = track.durationMs?.takeIf { it > 0L }
                ?: latestCurrent?.durationMs?.takeIf { it > 0L }
                ?: latestState.durationMs.takeIf { it > 0L }
            val fallbackVideoId = (track.videoId?.takeIf { it.isNotBlank() }
                ?: latestCurrent?.videoId?.takeIf { it.isNotBlank() })
                ?.let { extractYouTubeVideoId(it) }

            if (fallbackVideoId != null && isSpecialVersionTrack(track.title, track.artist)) {
                _resolvedMusicVideoIds.update {
                    it + (videoLookupKey(latestCurrent ?: track) to ResolvedVideoEntry(fallbackVideoId, true))
                }
                syncedVideoRepository.prefetchVideo(
                    trackTitle = track.title,
                    trackArtist = track.artist,
                    videoId = fallbackVideoId,
                    targetDurationMs = effectiveDurationMs,
                )
                return@launch
            }

            val resolvedId = findAccurateMusicVideoId(
                title = track.title,
                artist = track.artist,
                targetDurationMs = effectiveDurationMs,
                fallbackVideoId = fallbackVideoId,
            )

            coroutineContext.ensureActive()
            if (!resolvedId.isNullOrBlank()) {
                val entry = ResolvedVideoEntry(
                    videoId = resolvedId,
                    verifiedWithDuration = effectiveDurationMs != null,
                )
                _resolvedMusicVideoIds.update { map ->
                    val updated = map + (key to entry)
                    if (latestCurrent != null) {
                        updated + (videoLookupKey(latestCurrent) to entry)
                    } else {
                        updated
                    }
                }
                syncedVideoRepository.prefetchVideo(
                    trackTitle = track.title,
                    trackArtist = track.artist,
                    videoId = resolvedId,
                    targetDurationMs = effectiveDurationMs,
                )
            }

            val upcoming = latestState.queue.getOrNull(latestState.currentIndex + 1)
            if (upcoming != null && _resolvedMusicVideoIds.value[videoLookupKey(upcoming)] == null) {
                prefetchVideoForUpcomingTrack(upcoming)
            }
        }
    }

    private suspend fun findAccurateMusicVideoId(
        title: String,
        artist: String,
        targetDurationMs: Long?,
        fallbackVideoId: String?,
    ): String? {
        val repo = searchRepository.get()
        val cleanTitle = stripAudioOnlyNoise(title)
        val primaryArtist = com.neonsong.app.util.ArtistHelper.splitArtists(artist)
            .firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: artist.trim()

        val pass1 = runCatching {
            repo.findMusicVideo(cleanTitle, artist, targetDurationMs = targetDurationMs)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { extractYouTubeVideoId(it) }
        if (!pass1.isNullOrBlank()) return pass1

        if (!primaryArtist.equals(artist.trim(), ignoreCase = true)) {
            val pass2 = runCatching {
                repo.findMusicVideo(cleanTitle, primaryArtist, targetDurationMs = targetDurationMs)
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { extractYouTubeVideoId(it) }
            if (!pass2.isNullOrBlank()) return pass2
        }

        return fallbackVideoId
    }

    private fun prefetchVideoForUpcomingTrack(upcoming: PlayableTrack) {
        prefetchVideoResolveJob?.cancel()
        prefetchVideoResolveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1_500L)
            val key = videoLookupKey(upcoming)
            if (_resolvedMusicVideoIds.value.containsKey(key)) return@launch
            val directId = upcoming.videoId?.takeIf { it.isNotBlank() }?.let { extractYouTubeVideoId(it) }
            if (directId != null && isSpecialVersionTrack(upcoming.title, upcoming.artist)) {
                _resolvedMusicVideoIds.update {
                    it + (key to ResolvedVideoEntry(directId, verifiedWithDuration = true))
                }
                syncedVideoRepository.prefetchVideo(
                    trackTitle = upcoming.title,
                    trackArtist = upcoming.artist,
                    videoId = directId,
                    targetDurationMs = upcoming.durationMs?.takeIf { it > 0L },
                )
                return@launch
            }
            val resolvedId = findAccurateMusicVideoId(
                title = upcoming.title,
                artist = upcoming.artist,
                targetDurationMs = upcoming.durationMs?.takeIf { it > 0L },
                fallbackVideoId = directId,
            )
            if (!resolvedId.isNullOrBlank()) {
                _resolvedMusicVideoIds.update {
                    it + (key to ResolvedVideoEntry(resolvedId, upcoming.durationMs != null))
                }
                syncedVideoRepository.prefetchVideo(
                    trackTitle = upcoming.title,
                    trackArtist = upcoming.artist,
                    videoId = resolvedId,
                    targetDurationMs = upcoming.durationMs?.takeIf { it > 0L },
                )
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
        const val LOADING_SPINNER_DELAY_MS = 250L
        const val CANVAS_PREFETCH_DELAY_MS = 2_000L
        const val INTERACTIVE_SETTLE_DEBOUNCE_MS = 280L

        val VERSION_MARKERS = listOf(
            "slowed", "reverb", "lofi", "lo-fi", "remix", "mashup", "mash-up", "mash up",
            "nightcore", "sped up", "speed up", "8d", "bass boosted", "live", "unplugged",
            "acoustic", "cover", "karaoke", "instrumental", "reprise", "dj ", "club mix",
            "extended mix", "radio edit",
        )
        val BRACKET_NOISE_REGEX = Regex(
            """[\(\[\{]\s*(?:official\s*(?:music\s*)?(?:audio|video|lyric\s*video|visualizer)|audio|lyrics?|visualizer|full\s*audio|hd|4k)\s*[\)\]\}]""",
            RegexOption.IGNORE_CASE,
        )
        val SUFFIX_NOISE_REGEX = Regex(
            """\s*[-–—|]\s*(?:official\s*(?:music\s*)?(?:audio|video|lyric\s*video|visualizer)|full\s*audio|audio|lyrics?|visualizer)\s*$""",
            RegexOption.IGNORE_CASE,
        )
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
            var lastTrackIdentityKey: String? = null
            combine(
                player.chromeState.map { it.current }.distinctUntilChanged(),
                settingsPreferences.settings.map { it.wordByWordLyrics }.distinctUntilChanged(),
            ) { track, wordByWord -> track to wordByWord }.collect { (track, wordByWord) ->
                val trackIdKey = track?.let { "${it.videoId ?: ""}|${it.artist}|${it.title}" }
                val key = track?.let { "$trackIdKey|$wordByWord" }
                if (key != currentTrackLyricsKey) {
                    val isTrackChange = trackIdKey != lastTrackIdentityKey
                    lastTrackIdentityKey = trackIdKey
                    currentTrackLyricsKey = key
                    if (track != null) {
                        if (isTrackChange) {
                            // Clear previous song's lyrics immediately so old lines don't linger 1-2s on skip
                            lyricsJob?.cancel()
                            _lyricsState.value = LyricsUiState.Loading
                        }
                        loadLyrics(track, forceRefresh = false, wordByWord = wordByWord)
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
                settingsPreferences.settings.map {
                    Triple(it.canvasEnabled, it.canvasOverCellular, it.canvasStyle)
                }.distinctUntilChanged(),
            ) { track, (enabled, cellular, style) ->
                val preferSpotify = style == CanvasStyle.SPOTIFY
                Triple(track, enabled to cellular, preferSpotify)
            }.collect { (track, flags, preferSpotify) ->
                val (enabled, cellular) = flags
                val key = track?.let {
                    "${it.videoId ?: ""}|${it.artist}|${it.title}|$enabled|$cellular|$preferSpotify"
                }
                if (key != currentCanvasTrackKey) {
                    currentCanvasTrackKey = key
                    canvasJob?.cancel()
                    if (track != null && enabled) {
                        val cachedCanvas = canvasRepository.cached(track, preferSpotify = preferSpotify)
                        _canvasState.value = cachedCanvas
                        canvasJob = viewModelScope.launch {
                            if (cachedCanvas == null) {
                                waitUntilAudible(track, timeoutMs = 1_600L)
                                delay(INTERACTIVE_SETTLE_DEBOUNCE_MS)
                                if (currentCanvasTrackKey != key) return@launch
                                val result = canvasRepository.canvasFor(
                                    track = track,
                                    cellularAllowed = cellular,
                                    preferSpotify = preferSpotify,
                                )
                                if (currentCanvasTrackKey != key) return@launch
                                _canvasState.value = result
                            }
                            val upcoming = player.state.value.let { snapshot ->
                                if (snapshot.shuffleEnabled) {
                                    null
                                } else {
                                    snapshot.queue.getOrNull(snapshot.currentIndex + 1)
                                }
                            }?.takeIf {
                                it.videoId != track.videoId || it.title != track.title || it.artist != track.artist
                            } ?: return@launch
                            delay(CANVAS_PREFETCH_DELAY_MS)
                            if (currentCanvasTrackKey != key) return@launch
                            canvasRepository.canvasFor(
                                track = upcoming,
                                cellularAllowed = cellular,
                                preferSpotify = preferSpotify,
                            )
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

    private suspend fun waitUntilAudible(track: PlayableTrack, timeoutMs: Long = 4_000L) {
        withTimeoutOrNull(timeoutMs) {
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

    fun loadLyrics(
        track: PlayableTrack,
        forceRefresh: Boolean = false,
        wordByWord: Boolean = settings.value.wordByWordLyrics,
    ) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            waitUntilAudible(track, timeoutMs = 1_500L)
            delay(INTERACTIVE_SETTLE_DEBOUNCE_MS)
            MetadataLog.started("lyrics", track.videoId)
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
                        wordByWord = wordByWord,
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

val LocalBottomNavSlot = staticCompositionLocalOf<androidx.compose.runtime.MutableState<@Composable () -> Unit>> {
    error("LocalBottomNavSlot not provided")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerHost(
    viewModel: PlayerViewModel = hiltViewModel(),
    hasBottomNavigation: Boolean = false,
    content: @Composable () -> Unit,
) {
    val state by viewModel.chromeState.collectAsStateWithLifecycle()
    val fullPlayerState by viewModel.fullPlayerState.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncedVideoViewModel: SyncedVideoViewModel = hiltViewModel()
    val syncedVideoUiState by syncedVideoViewModel.uiState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(FullPlayerTab.NOW_PLAYING) }
    var playlistTrack by remember { mutableStateOf<PlayableTrack?>(null) }
    val context = LocalContext.current
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
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeOpen = WindowInsets.isImeVisible || imeBottom > 0

    val resolvedMusicVideoIds by viewModel.resolvedMusicVideoIds.collectAsStateWithLifecycle()
    val activeVideoId = state.current?.let { current ->
        current.videoId?.takeIf { it.isNotBlank() }?.let { extractYouTubeVideoId(it) }
            ?: resolvedMusicVideoIds[viewModel.videoLookupKey(current)]
    }
    val trackIdentity = remember(state.current?.videoId, state.current?.title, state.current?.artist) {
        "${state.current?.videoId}_${state.current?.title}_${state.current?.artist}"
    }
    var isVideoMode by rememberSaveable { mutableStateOf(false) }
    var isVideoFullscreen by rememberSaveable(trackIdentity) { mutableStateOf(false) }
    var showVideoQualityDialog by rememberSaveable(trackIdentity) { mutableStateOf(false) }
    var videoScalingMode by rememberSaveable(trackIdentity) { mutableStateOf(VideoScalingMode.FIT) }

    // Picture-in-Picture (PiP) state and RemoteAction controls
    val (isInPipMode, enterPipMode) = rememberPipController(
        player = viewModel.player,
        isPlaying = state.isPlaying,
        isVideoMode = isVideoMode && !activeVideoId.isNullOrBlank(),
    )

    val isMiniPlayerVisible = state.current != null && !settings.buttonNowPlayingEnabled && !isImeOpen && !isInPipMode

    LaunchedEffect(isVideoMode) {
        viewModel.player.setMusicVideoModeActive(isVideoMode)
    }

    LaunchedEffect(
        state.current?.videoId,
        state.current?.title,
        state.current?.artist,
        isVideoMode,
    ) {
        state.current?.let { current ->
            val liveDuration = viewModel.progressState.value.durationMs
            val trackWithLiveDuration = if (current.durationMs == null && liveDuration > 0L) {
                current.copy(durationMs = liveDuration)
            } else {
                current
            }
            viewModel.resolveMusicVideoForTrack(trackWithLiveDuration, isVideoModeActive = isVideoMode)
        }
    }

    val miniBackdrop = rememberLayerBackdrop {
        drawContent()
    }
    val miniGlass = LocalLiquidGlass.current && isLiquidGlassBackdropSupported()

    val bottomNavSlot = remember { mutableStateOf<@Composable () -> Unit>({}) }
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val scope = rememberCoroutineScope()
    val sheetController = remember { AppleSheetController(scope, initialExpanded = expanded) }
    val activeFaceStyle = settings.playerFaceStyle

    LaunchedEffect(expanded, activeFaceStyle) {
        if (sheetController.isExpanded != expanded && !sheetController.isDragging) {
            sheetController.expandTo(expanded, faceStyle = activeFaceStyle)
        }
    }
    LaunchedEffect(sheetController.progress.isRunning, sheetController.isDragging) {
        if (!sheetController.progress.isRunning && !sheetController.isDragging) {
            expanded = sheetController.progress.value > 0.5f
        }
    }

    val isSheetSettledExpanded by remember {
        androidx.compose.runtime.derivedStateOf {
            sheetController.progress.value >= 0.96f && !sheetController.isDragging
        }
    }
    val isSheetSettledCollapsed by remember {
        androidx.compose.runtime.derivedStateOf {
            sheetController.progress.value <= 0.04f && !sheetController.isDragging
        }
    }
    val isSheetActive by remember {
        androidx.compose.runtime.derivedStateOf {
            sheetController.progress.value > 0.001f || sheetController.isDragging || expanded
        }
    }
    val isMiniAlphaVisible by remember {
        androidx.compose.runtime.derivedStateOf {
            sheetController.progress.value < 0.32f || sheetController.isDragging || sheetController.miniArtBounds.isEmpty
        }
    }
    val hasActiveVideo = isVideoMode && !activeVideoId.isNullOrBlank()

    // Morph card is active whenever Video Mode is on OR the sheet is dragging/transitioning between Mini and Full
    val isAudioMorphActive = !hasActiveVideo &&
        currentTab == FullPlayerTab.NOW_PLAYING &&
        !isInPipMode &&
        (!isSheetSettledExpanded && !isSheetSettledCollapsed)
    val shouldHideStaticArtworkForMorph = currentTab == FullPlayerTab.NOW_PLAYING &&
        !sheetController.miniArtBounds.isEmpty &&
        (hasActiveVideo || isAudioMorphActive)
    val activeCanvas by viewModel.canvasState.collectAsStateWithLifecycle()
    val hostConfiguration = LocalConfiguration.current
    val isSpotifyFullscreenCanvasOverlay = currentTab == FullPlayerTab.NOW_PLAYING &&
        !hasActiveVideo &&
        settings.canvasEnabled &&
        settings.canvasStyle == CanvasStyle.SPOTIFY &&
        settings.canvasFullBleed &&
        activeCanvas != null &&
        hostConfiguration.orientation != Configuration.ORIENTATION_LANDSCAPE

    val prevQueueTrack = remember(fullPlayerState.queue, fullPlayerState.currentIndex) {
        fullPlayerState.queue.getOrNull(fullPlayerState.currentIndex - 1)
    }
    val nextQueueTrack = remember(fullPlayerState.queue, fullPlayerState.currentIndex) {
        fullPlayerState.queue.getOrNull(fullPlayerState.currentIndex + 1)
    }

    val navEnterExitProgressState = animateFloatAsState(
        targetValue = if (hasBottomNavigation) 1f else 0f,
        animationSpec = ExpressiveMotion.FloatFluidSpring,
        label = "navEnterExitProgress",
    )
    val showBottomNavSlot by remember {
        androidx.compose.runtime.derivedStateOf { navEnterExitProgressState.value > 0.001f }
    }
    val animatedMiniBottomPadding by animateDpAsState(
        targetValue = if (hasBottomNavigation) 84.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 380f,
        ),
        label = "miniPlayerBottomPadding",
    )

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockHeight = if (hasBottomNavigation) 68.dp else 0.dp
    val miniPlayerHeight = if (isMiniPlayerVisible) 72.dp else 0.dp
    val targetBottomPadding = navBarBottom + dockHeight + miniPlayerHeight + 24.dp
    val animatedHostBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "hostBottomChromePadding"
    )

    CompositionLocalProvider(
        LocalMusicPlayer provides viewModel.player,
        LocalAddToPlaylist provides requestAddToPlaylist,
        LocalMiniPlayerScrollClearance provides if (isMiniPlayerVisible) 88.dp else 0.dp,
        LocalBottomNavSlot provides bottomNavSlot,
        com.neonsong.app.ui.theme.LocalBottomChromePadding provides animatedHostBottomPadding,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val totalTravelPx = screenHeightPx
            val appScaleClipShape = remember { com.neonsong.app.ui.theme.SquircleShape(26.dp) }
            val sheetClipShape = remember { com.neonsong.app.ui.theme.SquircleShape(32.dp) }

            // 1. Main App Content (Hidden in PiP Mode)
            if (!isInPipMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = sheetController.progress.value.coerceIn(0f, 1f)
                            val scale = androidx.compose.ui.util.lerp(1f, 0.94f, progress)
                            scaleX = scale
                            scaleY = scale
                            if (progress > 0.005f) {
                                shape = appScaleClipShape
                                clip = true
                            } else {
                                clip = false
                            }
                            alpha = androidx.compose.ui.util.lerp(1f, 0.78f, progress)
                        }
                        .liquidGlassSource(if (miniGlass) miniBackdrop else null),
                ) {
                    com.neonsong.app.ui.common.NeonLivingCanvas(
                        currentTrack = state.current,
                        isPlaying = state.isPlaying && !isSheetActive && !isVideoMode,
                        isScrolling = false,
                        rotatingBackgroundEnabled = settings.rotatingBackgroundEnabled,
                        subtleMotion = false,
                    ) {
                        content()
                    }
                }
            }

            // 2. MiniPlayer (Hidden in PiP Mode)
            if (isMiniPlayerVisible) {
                if (isMiniAlphaVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val progress = sheetController.progress.value.coerceIn(0f, 1f)
                                when (activeFaceStyle) {
                                    PlayerFaceStyle.DEFAULT -> {
                                        alpha = (1f - (progress / 0.32f)).coerceIn(0f, 1f)
                                        translationY = -progress * (screenHeightPx * 0.25f)
                                        val miniScale = androidx.compose.ui.util.lerp(1f, 1.04f, (progress / 0.35f).coerceIn(0f, 1f))
                                        scaleX = miniScale
                                        scaleY = miniScale
                                    }
                                    PlayerFaceStyle.BACKGROUND_ONLY -> {
                                        // Ambient Living Canvas: MiniPlayer softly sinks & dissolves into ambient haze
                                        alpha = (1f - (progress / 0.28f)).coerceIn(0f, 1f)
                                        translationY = progress * 56.dp.toPx()
                                        val miniScale = androidx.compose.ui.util.lerp(1f, 0.90f, (progress / 0.30f).coerceIn(0f, 1f))
                                        scaleX = miniScale
                                        scaleY = miniScale
                                    }
                                    PlayerFaceStyle.FULLSCREEN_HERO -> {
                                        // Immersive Fullscreen Hero: MiniPlayer lifts & widens dramatically into the banner
                                        alpha = (1f - (progress / 0.36f)).coerceIn(0f, 1f)
                                        translationY = -progress * (screenHeightPx * 0.16f)
                                        val miniScale = androidx.compose.ui.util.lerp(1f, 1.12f, (progress / 0.38f).coerceIn(0f, 1f))
                                        scaleX = miniScale
                                        scaleY = miniScale
                                    }
                                    PlayerFaceStyle.CARD_ONLY -> {
                                        // Studio Clean: MiniPlayer drops cleanly downward below dock with zero horizontal bloom
                                        alpha = (1f - (progress / 0.24f)).coerceIn(0f, 1f)
                                        translationY = progress * 110.dp.toPx()
                                        val miniScale = androidx.compose.ui.util.lerp(1f, 0.96f, (progress / 0.25f).coerceIn(0f, 1f))
                                        scaleX = miniScale
                                        scaleY = miniScale
                                    }
                                    PlayerFaceStyle.COMPACT -> {
                                        // Compact Studio: Crisp upward fade tuck
                                        alpha = (1f - (progress / 0.26f)).coerceIn(0f, 1f)
                                        translationY = -progress * 44.dp.toPx()
                                        val miniScale = androidx.compose.ui.util.lerp(1f, 0.94f, (progress / 0.28f).coerceIn(0f, 1f))
                                        scaleX = miniScale
                                        scaleY = miniScale
                                    }
                                }
                            },
                    ) {
                        MiniPlayer(
                            state = state,
                            progressState = viewModel.progressState,
                            isLossless = state.isLossless,
                            audioCodec = state.audioCodec,
                            onExpand = {
                                expanded = true
                                sheetController.expandTo(true, faceStyle = activeFaceStyle)
                            },
                            onToggle = viewModel.player::togglePlayPause,
                            onPrevious = viewModel.player::previous,
                            onNext = viewModel.player::next,
                            onClose = viewModel.player::stopAndClear,
                            bottomPadding = animatedMiniBottomPadding,
                            edgeToEdge = false,
                            backdrop = if (miniGlass) miniBackdrop else null,
                            onVerticalDrag = { dy -> sheetController.onDragBy(dy, totalTravelPx) },
                            onVerticalDragEnd = { velY ->
                                sheetController.settle(
                                    velocityY = velY,
                                    totalTravelPx = totalTravelPx,
                                    onDismissMini = viewModel.player::stopAndClear,
                                )
                            },
                            hideArtworkForMorph = shouldHideStaticArtworkForMorph,
                            onArtworkBoundsChanged = {
                                if (sheetController.miniArtBounds.isEmpty || sheetController.progress.value <= 0.005f) {
                                    sheetController.miniArtBounds = it
                                }
                            },
                        )
                    }
                }
            }

            val currentTrack = state.current
            if (currentTrack != null && !expanded && settings.buttonNowPlayingEnabled && !isInPipMode) {
                DraggableNowPlayingButton(
                    track = currentTrack,
                    progressState = viewModel.progressState,
                    isPlaying = state.isPlaying,
                    onExpand = {
                        expanded = true
                        sheetController.expandTo(true, faceStyle = activeFaceStyle)
                    },
                )
            }
            if (activeDownloads.isNotEmpty() && !expanded && !isInPipMode) {
                val latestProgress = activeDownloads.firstOrNull()?.progressPercent ?: 0
                com.neonsong.app.ui.download.DraggableDownloadOverlay(
                    activeDownloadsCount = activeDownloads.size,
                    latestProgressPercent = latestProgress,
                    onOpenDownloads = { viewModel.navigateToDownloads() },
                )
            }

            var playerDismissProgress by remember { mutableFloatStateOf(0f) }
            val isPlayerExpanded = (expanded || sheetController.isExpanded || sheetController.progress.value > 0.5f) && !isVideoFullscreen && !isInPipMode && state.current != null

            PredictiveBackHandler(enabled = isPlayerExpanded) { progressFlow: Flow<BackEventCompat> ->
                try {
                    progressFlow.collect { backEvent ->
                        playerDismissProgress = backEvent.progress
                    }
                    if (currentTab != FullPlayerTab.NOW_PLAYING) {
                        currentTab = FullPlayerTab.NOW_PLAYING
                    } else {
                        expanded = false
                        sheetController.expandTo(false, faceStyle = activeFaceStyle)
                    }
                    playerDismissProgress = 0f
                } catch (_: CancellationException) {
                    playerDismissProgress = 0f
                }
            }

            // 3. FullPlayer Sheet (Stays at zIndex = 0f so it NEVER covers FloatingMorphArtworkOverlay)
            if (!isInPipMode && state.current != null && isSheetActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = sheetController.progress.value.coerceIn(0f, 1f)
                            if (isVideoFullscreen) {
                                translationY = playerDismissProgress * 180f
                                alpha = if (progress <= 0.001f) 0f else (progress / 0.18f).coerceIn(0f, 1f)
                                val fsScale = 1f - (playerDismissProgress * 0.06f)
                                scaleX = fsScale
                                scaleY = fsScale
                                clip = false
                            } else {
                                when (activeFaceStyle) {
                                    PlayerFaceStyle.DEFAULT -> {
                                        translationY = (1f - progress) * screenHeightPx + (playerDismissProgress * 180f)
                                        alpha = if (progress <= 0.001f) 0f else (progress / 0.18f).coerceIn(0f, 1f)
                                        val sheetScale = androidx.compose.ui.util.lerp(0.92f, 1.0f, (progress / 0.85f).coerceIn(0f, 1f)) *
                                            (1f - (playerDismissProgress * 0.06f))
                                        scaleX = sheetScale
                                        scaleY = sheetScale
                                        transformOrigin = TransformOrigin(0.5f, 0.96f)
                                    }
                                    PlayerFaceStyle.BACKGROUND_ONLY -> {
                                        // Ambient Living Canvas: Dreamy center-out curtain bloom (zooms gently in from 1.12f -> 1.0f)
                                        translationY = (1f - progress) * screenHeightPx * 0.24f + (playerDismissProgress * 140f)
                                        alpha = if (progress <= 0.001f) 0f else (progress / 0.36f).coerceIn(0f, 1f)
                                        val bloomScale = androidx.compose.ui.util.lerp(1.12f, 1.0f, progress) *
                                            (1f - (playerDismissProgress * 0.04f))
                                        scaleX = bloomScale
                                        scaleY = bloomScale
                                        transformOrigin = TransformOrigin(0.5f, 0.45f)
                                    }
                                    PlayerFaceStyle.FULLSCREEN_HERO -> {
                                        // Immersive Fullscreen Art: Dramatic theatrical zoom expansion from lower deck
                                        translationY = (1f - progress) * screenHeightPx * 0.56f + (playerDismissProgress * 160f)
                                        alpha = if (progress <= 0.001f) 0f else (progress / 0.24f).coerceIn(0f, 1f)
                                        val heroScale = androidx.compose.ui.util.lerp(0.76f, 1.0f, progress) *
                                            (1f - (playerDismissProgress * 0.06f))
                                        scaleX = heroScale
                                        scaleY = heroScale
                                        transformOrigin = TransformOrigin(0.5f, 0.82f)
                                    }
                                    PlayerFaceStyle.CARD_ONLY -> {
                                        // Studio Clean: Precision 3D studio deck rise & snap with crisp tilt
                                        translationY = (1f - progress) * screenHeightPx * 0.84f + (playerDismissProgress * 180f)
                                        alpha = if (progress <= 0.001f) 0f else (progress / 0.12f).coerceIn(0f, 1f)
                                        val studioScale = androidx.compose.ui.util.lerp(0.84f, 1.0f, progress) *
                                            (1f - (playerDismissProgress * 0.05f))
                                        scaleX = studioScale
                                        scaleY = studioScale
                                        rotationX = (1f - progress) * 14f
                                        cameraDistance = 20f * this.density
                                        transformOrigin = TransformOrigin(0.5f, 0.92f)
                                    }
                                    PlayerFaceStyle.COMPACT -> {
                                        // Compact Studio: Balanced compact glide from mid-lower deck
                                        translationY = (1f - progress) * screenHeightPx * 0.42f + (playerDismissProgress * 160f)
                                        alpha = if (progress <= 0.001f) 0f else (progress / 0.20f).coerceIn(0f, 1f)
                                        val compactScale = androidx.compose.ui.util.lerp(0.90f, 1.0f, progress) *
                                            (1f - (playerDismissProgress * 0.05f))
                                        scaleX = compactScale
                                        scaleY = compactScale
                                        transformOrigin = TransformOrigin(0.5f, 0.72f)
                                    }
                                }
                                if (progress < 0.995f || playerDismissProgress > 0f) {
                                    shape = sheetClipShape
                                    clip = true
                                } else {
                                    clip = false
                                }
                            }
                        }
                        .zIndex(0f),
                ) {
                    PredictiveBackScreen(
                        enabled = expanded && state.current != null && currentTab == FullPlayerTab.NOW_PLAYING && sheetController.isExpanded && !isVideoFullscreen,
                        backgroundColor = Color.Transparent,
                        onBack = {
                            expanded = false
                            sheetController.expandTo(false, faceStyle = activeFaceStyle)
                        },
                    ) {
                        ExpandedPlayer(
                            viewModel = viewModel,
                            currentTab = currentTab,
                            onTabChange = { currentTab = it },
                            onRetryLyrics = viewModel::retryLyrics,
                            onCollapse = {
                                expanded = false
                                sheetController.expandTo(false, faceStyle = activeFaceStyle)
                            },
                            onOpenArtist = { artist ->
                                expanded = false
                                sheetController.expandTo(false, faceStyle = activeFaceStyle)
                                viewModel.openArtist(artist)
                            },
                            isVideoMode = isVideoMode,
                            isVideoFullscreen = isVideoFullscreen,
                            onVideoFullscreenChange = { isVideoFullscreen = it },
                            videoScalingMode = videoScalingMode,
                            onVideoScalingModeChange = { videoScalingMode = it },
                            onOpenVideoQuality = { showVideoQualityDialog = true },
                            onToggleVideoMode = { isVideoMode = !isVideoMode },
                            onCloseVideoMode = {
                                isVideoFullscreen = false
                                isVideoMode = false
                            },
                            activeVideoId = activeVideoId,
                            isSheetSettled = isSheetSettledExpanded,
                            sheetProgress = { sheetController.progress.value.coerceIn(0f, 1f) },
                            onVerticalDrag = { dy -> sheetController.onDragBy(dy, totalTravelPx) },
                            onVerticalDragEnd = { velY ->
                                sheetController.settle(velocityY = velY, totalTravelPx = totalTravelPx)
                            },
                            onHorizontalDrag = { dx -> sheetController.cardDragX = dx },
                            hideArtworkForMorph = shouldHideStaticArtworkForMorph,
                            onArtworkBoundsChanged = { rawRect ->
                                val curP = sheetController.progress.value.coerceIn(0f, 1f)
                                val curScale = if (isVideoFullscreen) {
                                    1f
                                } else {
                                    androidx.compose.ui.util.lerp(0.88f, 1.0f, (curP / 0.90f).coerceIn(0f, 1f))
                                }
                                val curSheetTranslationY = (1f - curP) * screenHeightPx
                                val pivotX = screenWidthPx * 0.5f
                                val pivotY = screenHeightPx * 0.96f
                                val safeScale = curScale.coerceAtLeast(0.5f)
                                val canonicalLeft = pivotX + (rawRect.left - pivotX) / safeScale
                                val canonicalTop = pivotY + ((rawRect.top - curSheetTranslationY) - pivotY) / safeScale
                                sheetController.fullArtBounds = Rect(
                                    left = canonicalLeft,
                                    top = canonicalTop,
                                    right = canonicalLeft + rawRect.width,
                                    bottom = canonicalTop + rawRect.height,
                                )
                            },
                        )
                    }
                }
            }

            // 4. Floating Morph Card (Audio Artwork + Video Surface + Fullscreen HUD + PiP)
            if (currentTab == FullPlayerTab.NOW_PLAYING || isVideoFullscreen || isInPipMode) {
                state.current?.let { currentTrack ->
                    FloatingMorphArtworkOverlay(
                        track = currentTrack,
                        prevTrack = prevQueueTrack,
                        nextTrack = nextQueueTrack,
                        sheetController = sheetController,
                        isPlaying = state.isPlaying,
                        isMiniPlayerVisible = isMiniPlayerVisible,
                        progressState = viewModel.progressState,
                        isVideoMode = isVideoMode,
                        activeVideoId = activeVideoId,
                        musicPlayer = viewModel.player,
                        videoViewModel = syncedVideoViewModel,
                        videoFullscreen = isVideoFullscreen,
                        isInPipMode = isInPipMode,
                        onEnterPipMode = enterPipMode,
                        videoScalingMode = videoScalingMode,
                        onToggleVideoScalingMode = {
                            videoScalingMode = if (videoScalingMode == VideoScalingMode.ZOOM) {
                                VideoScalingMode.FIT
                            } else {
                                VideoScalingMode.ZOOM
                            }
                        },
                        videoQualityLabel = (syncedVideoUiState as? SyncedVideoUiState.Ready)?.currentQualityLabel ?: "Auto",
                        onOpenVideoQuality = { showVideoQualityDialog = true },
                        onSetVideoFullscreen = { isVideoFullscreen = it },
                        onCloseVideoMode = {
                            isVideoFullscreen = false
                            isVideoMode = false
                        },
                        isAudioMorphActive = isAudioMorphActive,
                        isCardHiddenForCanvas = isSpotifyFullscreenCanvasOverlay,
                        playerFaceStyle = activeFaceStyle,
                        sleeveCanvas = null,
                        ambientColor = MaterialTheme.colorScheme.primary,
                        ambientCompanion = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            // 5. Bottom Navigation Dock (Hidden in PiP Mode or when navigated to non-dock screen)
            if (!isSheetSettledExpanded && !isInPipMode && showBottomNavSlot) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val navProgress = navEnterExitProgressState.value
                            val progress = sheetController.progress.value.coerceIn(0f, 1f)
                            val sheetNavVisibility = (1f - (progress / 0.45f)).coerceIn(0f, 1f)
                            alpha = sheetNavVisibility * navProgress
                            val sheetTranslationY = (1f - sheetNavVisibility) * 80.dp.toPx()
                            val routeExitTranslationY = (1f - navProgress) * 74.dp.toPx()
                            translationY = sheetTranslationY + routeExitTranslationY
                        }
                        .then(
                            if (!hasBottomNavigation) {
                                Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                    }
                                }
                            } else Modifier
                        ),
                ) {
                    bottomNavSlot.value()
                }
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
            val readyVideo = syncedVideoUiState as? SyncedVideoUiState.Ready
            if (showVideoQualityDialog && readyVideo != null && !isInPipMode) {
                VideoQualityDialog(
                    bundle = readyVideo.bundle,
                    selectedHeight = readyVideo.selectedHeight,
                    onSelectQuality = syncedVideoViewModel::selectQuality,
                    onDismiss = { showVideoQualityDialog = false },
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
    isVideoMode: Boolean = false,
    isVideoFullscreen: Boolean = false,
    onVideoFullscreenChange: (Boolean) -> Unit = {},
    videoScalingMode: VideoScalingMode = VideoScalingMode.FIT,
    onVideoScalingModeChange: (VideoScalingMode) -> Unit = {},
    onOpenVideoQuality: () -> Unit = {},
    onToggleVideoMode: () -> Unit = {},
    onCloseVideoMode: () -> Unit = {},
    activeVideoId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isSheetSettled: Boolean = true,
    sheetProgress: () -> Float = { 1f },
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: (Float) -> Unit = {},
    onHorizontalDrag: (Float) -> Unit = {},
    hideArtworkForMorph: Boolean = false,
    onArtworkBoundsChanged: (Rect) -> Unit = {},
) {
    val state by viewModel.fullPlayerState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val canvas by viewModel.canvasState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val likedTrackKeys by viewModel.likedTrackKeys.collectAsStateWithLifecycle()
    val currentTrack = state.current
    val isLiked = currentTrack != null &&
        currentTrack.toGeneratedTrack().key in likedTrackKeys
    val lyricsOffsetMs by viewModel.liveLyricsOffsetMs.collectAsStateWithLifecycle()
    FullPlayer(
        state = state,
        progressState = viewModel.progressState,
        player = viewModel.player,
        spatialImmersionPreset = settings.spatialImmersionPreset,
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
        canvasStyle = settings.canvasStyle,
        canvasFullBleedEnabled = settings.canvasFullBleed,
        playerFaceStyle = settings.playerFaceStyle,
        inlinePlayerLyricsEnabled = settings.inlinePlayerLyricsEnabled,
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
        isVideoMode = isVideoMode,
        isVideoFullscreen = isVideoFullscreen,
        onVideoFullscreenChange = onVideoFullscreenChange,
        videoScalingMode = videoScalingMode,
        onVideoScalingModeChange = onVideoScalingModeChange,
        onOpenVideoQuality = onOpenVideoQuality,
        onToggleVideoMode = onToggleVideoMode,
        onCloseVideoMode = onCloseVideoMode,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        isSheetSettled = isSheetSettled,
        sheetProgress = sheetProgress,
        onVerticalDrag = onVerticalDrag,
        onVerticalDragEnd = onVerticalDragEnd,
        onHorizontalDrag = onHorizontalDrag,
        hideArtworkForMorph = hideArtworkForMorph,
        onArtworkBoundsChanged = onArtworkBoundsChanged,
    )
}

@Composable
internal fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) = com.neonsong.app.features.player.ui.AnimatedPlayPauseIcon(isPlaying, modifier, tint)

@Composable
fun PlayingWaveBars(
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = CircleShape,
    isPlaying: Boolean = true,
    animated: Boolean = true,
) = com.neonsong.app.features.player.ui.PlayingWaveBars(modifier, waveColor, containerColor, shape, isPlaying, animated)

@Composable
private fun FullscreenVideoGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassOverlayBackdrop.current ?: LocalLiquidGlassBackdrop.current
    val isGlass = LocalLiquidGlass.current &&
        isLiquidGlassBackdropSupported() &&
        backdrop != null
    val isDark = LocalIsDarkTheme.current

    Surface(
        onClick = onClick,
        modifier = modifier.then(
            if (isGlass) {
                Modifier.liquidGlassChrome(
                    shape = CircleShape,
                    enabled = true,
                    preset = LiquidGlassPreset.MiniPlayer,
                    backdrop = backdrop,
                )
            } else {
                Modifier
            },
        ),
        shape = CircleShape,
        color = liquidGlassContainerColor(
            color = Color.Black.copy(alpha = 0.48f),
            enabled = isGlass,
            backdrop = backdrop,
        ),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (isGlass) null else BorderStroke(
            0.85.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.36f),
                    Color.White.copy(alpha = 0.10f),
                ),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

private enum class SeekDirection { REWIND, FORWARD }

@Composable
private fun SheetGrabberPill(isDragging: Boolean) {
    val scaleXState = animateFloatAsState(
        targetValue = if (isDragging) 1.22f else 1f,
        animationSpec = ExpressiveMotion.FloatReleaseSpring,
        label = "grabberScaleX",
    )
    val alphaState = animateFloatAsState(
        targetValue = if (isDragging) 0.85f else 0.42f,
        animationSpec = tween(150),
        label = "grabberAlpha",
    )
    Box(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 2.dp)
            .size(width = 36.dp, height = 4.5.dp)
            .graphicsLayer {
                scaleX = scaleXState.value
                alpha = alphaState.value
            }
            .clip(CircleShape)
            .background(Color.White),
    )
}

@Composable
private fun FullPlayer(
    state: MusicPlayerState,
    progressState: StateFlow<PlaybackProgressState>,
    player: MusicPlayer,
    spatialImmersionPreset: SpatialImmersionPreset,
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
    canvasStyle: CanvasStyle = CanvasStyle.SPOTIFY,
    canvasFullBleedEnabled: Boolean = true,
    playerFaceStyle: PlayerFaceStyle = PlayerFaceStyle.DEFAULT,
    inlinePlayerLyricsEnabled: Boolean = false,
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
    isVideoMode: Boolean = false,
    isVideoFullscreen: Boolean = false,
    onVideoFullscreenChange: (Boolean) -> Unit = {},
    videoScalingMode: VideoScalingMode = VideoScalingMode.FIT,
    onVideoScalingModeChange: (VideoScalingMode) -> Unit = {},
    onOpenVideoQuality: () -> Unit = {},
    onToggleVideoMode: (() -> Unit)? = null,
    onCloseVideoMode: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isSheetSettled: Boolean = true,
    sheetProgress: () -> Float = { 1f },
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: (Float) -> Unit = {},
    onHorizontalDrag: (Float) -> Unit = {},
    hideArtworkForMorph: Boolean = false,
    onArtworkBoundsChanged: (Rect) -> Unit = {},
) {
    val track = state.current ?: return
    val hapticManager = LocalHapticManager.current
    val effectiveVideoId = activeVideoId?.takeIf { it.isNotBlank() }
    val hasVideo = !effectiveVideoId.isNullOrBlank()
    val view = LocalView.current
    val enterVideoMode = {
        if (hasVideo && !isVideoMode) {
            onToggleVideoMode?.invoke()
        }
    }

    val closeVideoMode = {
        view.context.findActivity()?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onVideoFullscreenChange(false)
        onCloseVideoMode?.invoke()
    }
    BackHandler(enabled = isVideoMode) {
        if (isVideoFullscreen) {
            onVideoFullscreenChange(false)
        } else {
            closeVideoMode()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(view.context, isVideoMode, lifecycleOwner) {
        if (!isVideoMode) return@DisposableEffect onDispose {}
        val ctx = view.context
        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager

        fun switchToVoice() {
            if (isVideoMode) {
                closeVideoMode()
            }
        }

        val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    switchToVoice()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            ctx,
            screenOffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
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

    var showLyricsOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var showSignalPath by rememberSaveable { mutableStateOf(false) }
    var showAudioControls by rememberSaveable { mutableStateOf(false) }
    val signalPath by player.signalPath.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscapeScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactStyle = playerFaceStyle == PlayerFaceStyle.COMPACT
    val isCanvasActive = canvasEnabled && canvas != null && !isVideoMode
    // Spotify Canvas mode renders a full-screen 9:16 vertical looping video behind player controls
    // and hides the large center album card; Apple Music mode keeps the framed sleeve or Apple hero.
    val showSpotifyFullscreenCanvas = isCanvasActive &&
        canvasStyle == CanvasStyle.SPOTIFY &&
        canvasFullBleedEnabled &&
        !isLandscapeScreen
    val showAppleFullBleedCanvas = canvasEnabled &&
        canvasStyle == CanvasStyle.APPLE_MUSIC &&
        canvasFullBleedEnabled &&
        !isLandscapeScreen &&
        !isVideoMode &&
        (playerFaceStyle == PlayerFaceStyle.BACKGROUND_ONLY || playerFaceStyle == PlayerFaceStyle.FULLSCREEN_HERO)
    val showFullBleedCanvas = showSpotifyFullscreenCanvas || showAppleFullBleedCanvas
    val showBackgroundCoverHero = !showSpotifyFullscreenCanvas &&
        canvasFullBleedEnabled &&
        !isLandscapeScreen &&
        !isVideoMode &&
        (playerFaceStyle == PlayerFaceStyle.DEFAULT || playerFaceStyle == PlayerFaceStyle.BACKGROUND_ONLY)
    val showSleeveCanvas = isCanvasActive && !showFullBleedCanvas &&
        playerFaceStyle != PlayerFaceStyle.FULLSCREEN_HERO &&
        playerFaceStyle != PlayerFaceStyle.BACKGROUND_ONLY
    val prevTrack = remember(state.queue, state.currentIndex) {
        state.queue.getOrNull(state.currentIndex - 1)
    }
    val nextTrack = remember(state.queue, state.currentIndex) {
        state.queue.getOrNull(state.currentIndex + 1)
    }
    val activeCanvas = remember(canvas, showFullBleedCanvas, canvasEnabled, isVideoMode) {
        if (!canvasEnabled || isVideoMode) {
            null
        } else {
            val tall = canvas?.tallUrl
            if (showFullBleedCanvas && canvas != null && !tall.isNullOrBlank() && tall != canvas.url) {
                canvas.copy(url = tall, fallbackUrl = canvas.fallbackUrl ?: canvas.url)
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
        val activity = view.context.findActivity()

        val listener = object : android.view.OrientationEventListener(view.context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val now = SystemClock.uptimeMillis()
                if (isVideoFullscreen && now - lastFullscreenTime > 700L) {
                    val isStraightUp = orientation in 335..360 || orientation in 0..25
                    if (isStraightUp) {
                        onVideoFullscreenChange(false)
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

    // Use LaunchedEffect for orientation switching so config-change recompositions never cancel landscape!
    LaunchedEffect(view, lyricsFullscreen, isVideoFullscreen) {
        val fullscreenActive = lyricsFullscreen || isVideoFullscreen
        val activity = view.context.findActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreenActive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = if (isVideoFullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = view.context.findActivity()
            activity?.window?.let { WindowCompat.getInsetsController(it, view) }
                ?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var showTrackMenu by rememberSaveable(track.videoId, track.title) { mutableStateOf(false) }
    var artworkDragX by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }
    var isDismissDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDismissDragging) { onDragStatusChange(isDismissDragging) }
    var seekOverlayDirection by remember(track.videoId, track.title) { mutableStateOf<SeekDirection?>(null) }
    var seekOverlaySeconds by remember(track.videoId, track.title) { mutableIntStateOf(0) }
    var lastTapTimestamp by remember(track.videoId, track.title) { mutableLongStateOf(0L) }
    var lastTapSide by remember(track.videoId, track.title) { mutableStateOf<SeekDirection?>(null) }
    var lastLikeTapTimestamp by remember(track.videoId, track.title) { mutableLongStateOf(0L) }
    var seekResetJob by remember(track.videoId, track.title) { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val shownArtworkXState = animateFloatAsState(
        targetValue = artworkDragX,
        animationSpec = ExpressiveMotion.FloatFluidSpring,
        label = "fullPlayerArtworkX",
    )
    val showArtworkPeekDeck by remember {
        androidx.compose.runtime.derivedStateOf { abs(shownArtworkXState.value) > 2f }
    }
    val swipeThreshold = with(LocalDensity.current) { 54.dp.toPx() }

    val context = LocalContext.current
    val artworkViewModel: ArtworkViewModel = hiltViewModel()
    val artworkKey = remember(track.title, track.artist) { ArtworkNormalizer.cacheKey(track.title, track.artist) }
    val resolvedAmbientUrl by remember(artworkKey) {
        artworkViewModel.resolved.map { it[artworkKey] }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = artworkViewModel.resolved.value[artworkKey])

    val paletteUrl = remember(resolvedAmbientUrl, track.artworkUrl) {
        resolvedAmbientUrl?.takeIf { it.isNotBlank() }
            ?: ArtworkNormalizer.upscaleYoutubeArtwork(track.artworkUrl)?.takeIf { it.isNotBlank() }
    }
    val cachedPalette = remember(paletteUrl) {
        com.neonsong.app.ui.common.ArtworkPaletteCache.peek(paletteUrl)
    }
    val initialPrimary = cachedPalette?.primary
        ?: com.neonsong.app.ui.common.ArtworkPaletteCache.lastValidPalette?.primary
        ?: com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value?.primary
    val initialSecondary = cachedPalette?.secondary
        ?: com.neonsong.app.ui.common.ArtworkPaletteCache.lastValidPalette?.secondary
        ?: com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value?.secondary
    val initialTertiary = cachedPalette?.tertiary
        ?: com.neonsong.app.ui.common.ArtworkPaletteCache.lastValidPalette?.tertiary
        ?: com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value?.tertiary

    var ambientPrimary by remember { mutableStateOf(initialPrimary) }
    var ambientSecondary by remember { mutableStateOf(initialSecondary) }
    var ambientTertiary by remember { mutableStateOf(initialTertiary) }

    // Synchronous Frame-0 adoption of cached palette colors on song change
    if (cachedPalette != null && cachedPalette.primary != ambientPrimary) {
        ambientPrimary = cachedPalette.primary
        ambientSecondary = cachedPalette.secondary
        ambientTertiary = cachedPalette.tertiary
    }

    LaunchedEffect(paletteUrl) {
        val url = paletteUrl ?: return@LaunchedEffect
        val colors = cachedPalette ?: com.neonsong.app.ui.common.ArtworkPaletteCache.getOrExtract(context, url)
        if (colors != null) {
            ambientPrimary = colors.primary
            ambientSecondary = colors.secondary
            ambientTertiary = colors.tertiary
            com.neonsong.app.ui.common.ArtworkPaletteCache.setActiveSongPalette(colors)
        }
    }
    val ambientSpec = tween<Color>(durationMillis = 140, easing = FastOutSlowInEasing)
    val ambientColor by animateColorAsState(
        targetValue = ambientPrimary ?: MaterialTheme.colorScheme.primary,
        animationSpec = ambientSpec,
        label = "playerAmbientColor",
    )
    val ambientCompanion by animateColorAsState(
        targetValue = ambientSecondary ?: androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.tertiary, ambientColor, 0.48f),
        animationSpec = ambientSpec,
        label = "playerAmbientCompanion",
    )
    val ambientDeep by animateColorAsState(
        targetValue = ambientTertiary ?: androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.secondary, ambientColor, 0.25f),
        animationSpec = ambientSpec,
        label = "playerAmbientDeep",
    )

    fun Modifier.playerVerticalSwipe(enabled: Boolean): Modifier = if (!enabled) this else pointerInput(track.videoId, track.title, currentTab) {
        val velocityTracker = VelocityTracker()
        var accUpDrag = 0f
        detectVerticalDragGestures(
            onDragStart = {
                isDismissDragging = true
                accUpDrag = 0f
            },
            onDragCancel = {
                isDismissDragging = false
                onVerticalDragEnd(0f)
            },
            onDragEnd = {
                isDismissDragging = false
                val velocityY = velocityTracker.calculateVelocity().y
                if (currentTab == FullPlayerTab.NOW_PLAYING && (accUpDrag < -swipeThreshold || velocityY < -900f)) {
                    onVerticalDragEnd(0f)
                    onTabChange(FullPlayerTab.QUEUE)
                } else {
                    onVerticalDragEnd(velocityY)
                }
            },
        ) { change, amount ->
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            accUpDrag += amount
            change.consume()
            onVerticalDrag(amount)
        }
    }

    val capturePlayerBackdrop = false
    CompositionLocalProvider(
        LocalLiquidGlassBackdrop provides null,
        LocalLiquidGlassOverlayBackdrop provides null,
    ) {
        Surface(
            color = if (playerFaceStyle == PlayerFaceStyle.CARD_ONLY) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .playerVerticalSwipe(enabled = !isLandscapeScreen && !isVideoFullscreen),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val bgWidth = constraints.maxWidth.toFloat()
                val bgHeight = constraints.maxHeight.toFloat()
                val bgMaxDimension = maxOf(bgWidth, bgHeight, 1f)
                val isLandscape = constraints.maxWidth > constraints.maxHeight
                var heroBottomPx by remember { mutableFloatStateOf(0f) }

                Box(
                    Modifier.matchParentSize(),
                ) {
                    when (playerFaceStyle) {
                        PlayerFaceStyle.CARD_ONLY -> {
                            // Studio Clean: Pure solid dark minimal surface with ZERO background art or blur
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            )
                        }
                        PlayerFaceStyle.FULLSCREEN_HERO -> {
                            // Immersive Fullscreen Art: High-res edge-to-edge album artwork fills the whole screen
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            )
                            val heroAlphaState = animateFloatAsState(
                                targetValue = if (currentTab == FullPlayerTab.NOW_PLAYING) 1f else 0f,
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                                label = "fullscreenHeroAlpha",
                            )
                            val showFullscreenHero by remember {
                                androidx.compose.runtime.derivedStateOf { heroAlphaState.value > 0.001f }
                            }
                            if (showFullscreenHero && !isLandscape && !isVideoMode) {
                                val artUrl = paletteUrl ?: track.artworkUrl
                                if (!artUrl.isNullOrBlank()) {
                                    val fullscreenArtModel = remember(artUrl, context) {
                                        coil.request.ImageRequest.Builder(context)
                                            .data(artUrl)
                                            .crossfade(350)
                                            .allowHardware(true)
                                            .build()
                                    }
                                    coil.compose.AsyncImage(
                                        model = fullscreenArtModel,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = heroAlphaState.value * ((sheetProgress() - 0.80f) / 0.20f).coerceIn(0f, 1f)
                                            },
                                    )
                                }
                                if (!showSpotifyFullscreenCanvas && activeCanvas != null && currentTab == FullPlayerTab.NOW_PLAYING) {
                                    CanvasArtworkPlayer(
                                        canvas = activeCanvas,
                                        isPlaying = state.isPlaying,
                                        contentMode = CanvasContentMode.CROP,
                                        alignPortraitTop = false,
                                        bottomFade = 0f,
                                        presentationAlpha = { heroAlphaState.value * ((sheetProgress() - 0.80f) / 0.20f).coerceIn(0f, 1f) },
                                        onAspectRatioChanged = { canvasAspect = it },
                                        onRenderedChanged = { canvasRendered = it },
                                        pausedForTransition = isVideoMode || isDismissDragging || currentTab != FullPlayerTab.NOW_PLAYING || !isSheetSettled,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                // Gradient protection scrim for top icons and bottom playback controls
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0.00f to Color.Black.copy(alpha = 0.45f),
                                                0.22f to Color.Transparent,
                                                0.50f to Color.Black.copy(alpha = 0.25f),
                                                0.72f to Color.Black.copy(alpha = 0.65f),
                                                1.00f to Color.Black.copy(alpha = 0.88f),
                                            ),
                                        ),
                                )
                            }
                        }
                        PlayerFaceStyle.DEFAULT, PlayerFaceStyle.BACKGROUND_ONLY, PlayerFaceStyle.COMPACT -> {
                            FluidArtworkBackground(
                                track = track,
                                modifier = Modifier.fillMaxSize(),
                                extraBlur = currentTab == FullPlayerTab.LYRICS,
                                rotatingBackgroundEnabled = rotatingBackgroundEnabled,
                                isPlaying = state.isPlaying &&
                                    !isVideoMode &&
                                    isSheetSettled &&
                                    !isDismissDragging &&
                                    !(showSpotifyFullscreenCanvas && canvasRendered && currentTab == FullPlayerTab.NOW_PLAYING) &&
                                    !(showAppleFullBleedCanvas && canvasRendered && currentTab == FullPlayerTab.NOW_PLAYING),
                                fallback = {
                                    val staticBlurTransform = remember(currentTab) {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                            listOf(
                                                BlurTransformation(
                                                    radius = if (currentTab == FullPlayerTab.LYRICS) 25 else 18,
                                                    maxDimension = 100,
                                                ),
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
                                                },
                                            ),
                                        corner = 0.dp,
                                        decodeSizePx = 500,
                                        transformations = staticBlurTransform,
                                    )
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    0f to ambientColor.copy(alpha = 0.62f),
                                                    0.45f to ambientColor.copy(alpha = 0.24f),
                                                    1f to Color.Transparent,
                                                    center = Offset(bgWidth * 0.25f, bgHeight * 0.20f),
                                                    radius = bgMaxDimension * 0.85f,
                                                ),
                                            ),
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    0f to ambientCompanion.copy(alpha = 0.55f),
                                                    0.50f to ambientCompanion.copy(alpha = 0.22f),
                                                    1f to Color.Transparent,
                                                    center = Offset(bgWidth * 0.88f, bgHeight * 0.65f),
                                                    radius = bgMaxDimension * 0.80f,
                                                ),
                                            ),
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    0f to ambientDeep.copy(alpha = 0.45f),
                                                    0.55f to ambientDeep.copy(alpha = 0.16f),
                                                    1f to Color.Transparent,
                                                    center = Offset(bgWidth * 0.15f, bgHeight * 0.82f),
                                                    radius = bgMaxDimension * 0.72f,
                                                ),
                                            ),
                                    )
                                },
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        val verticalDepth = Brush.verticalGradient(
                                            0.00f to Color.Black.copy(alpha = 0.18f),
                                            0.32f to Color.Black.copy(alpha = 0.06f),
                                            0.68f to Color.Black.copy(alpha = 0.32f),
                                            1.00f to Color.Black.copy(alpha = 0.64f),
                                        )
                                        val edgeVignette = Brush.radialGradient(
                                            0.0f to Color.Transparent,
                                            0.68f to Color.Transparent,
                                            1.0f to Color.Black.copy(alpha = 0.26f),
                                            center = Offset(size.width * 0.50f, size.height * 0.40f),
                                            radius = maxOf(size.width, size.height) * 0.82f,
                                        )
                                        onDrawBehind {
                                            drawRect(verticalDepth)
                                            drawRect(edgeVignette)
                                        }
                                    },
                            )
                            if (showBackgroundCoverHero || showFullBleedCanvas) {
                                val heroHeight = if (heroBottomPx > 0f) {
                                    with(LocalDensity.current) { heroBottomPx.toDp() }
                                } else {
                                    with(LocalDensity.current) { (bgHeight * 0.54f).toDp() }
                                }
                                val heroAlphaState = animateFloatAsState(
                                    targetValue = if (currentTab == FullPlayerTab.NOW_PLAYING) 1f else 0f,
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    label = "heroAlpha",
                                )
                                val showHero by remember {
                                    androidx.compose.runtime.derivedStateOf { heroAlphaState.value > 0.001f }
                                }
                                val backdropAlphaFactor = if (playerFaceStyle == PlayerFaceStyle.DEFAULT) 0.54f else 1f
                                if (showHero && showBackgroundCoverHero) {
                                    StaticArtworkHero(
                                        name = track.title,
                                        artist = track.artist,
                                        embeddedUrl = track.artworkUrl,
                                        resolvedUrl = resolvedAmbientUrl,
                                        bottomFade = if (playerFaceStyle == PlayerFaceStyle.DEFAULT) 0.56f else 0.42f,
                                        alpha = 1f,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .fillMaxWidth()
                                            .height(heroHeight)
                                            .graphicsLayer {
                                                alpha = backdropAlphaFactor * heroAlphaState.value * ((sheetProgress() - 0.80f) / 0.20f).coerceIn(0f, 1f)
                                            },
                                    )
                                }
                                if (showAppleFullBleedCanvas && activeCanvas != null && currentTab == FullPlayerTab.NOW_PLAYING) {
                                    CanvasArtworkPlayer(
                                        canvas = activeCanvas,
                                        isPlaying = state.isPlaying,
                                        contentMode = CanvasContentMode.CROP,
                                        alignPortraitTop = true,
                                        bottomFade = 0.38f,
                                        presentationAlpha = { heroAlphaState.value * ((sheetProgress() - 0.80f) / 0.20f).coerceIn(0f, 1f) },
                                        onAspectRatioChanged = { canvasAspect = it },
                                        onRenderedChanged = { canvasRendered = it },
                                        pausedForTransition = isVideoMode || isDismissDragging || currentTab != FullPlayerTab.NOW_PLAYING || !isSheetSettled,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .fillMaxWidth()
                                            .height(heroHeight),
                                    )
                                }
                            }
                        }
                    }
                    if (showSpotifyFullscreenCanvas && activeCanvas != null && currentTab == FullPlayerTab.NOW_PLAYING) {
                        val spotifyCanvasAlphaState = animateFloatAsState(
                            targetValue = if (currentTab == FullPlayerTab.NOW_PLAYING) 1f else 0f,
                            animationSpec = tween(280, easing = FastOutSlowInEasing),
                            label = "spotifyCanvasAlpha",
                        )
                        Box(Modifier.fillMaxSize()) {
                            CanvasArtworkPlayer(
                                canvas = activeCanvas,
                                isPlaying = state.isPlaying,
                                contentMode = CanvasContentMode.CROP,
                                alignPortraitTop = false,
                                bottomFade = 0f,
                                presentationAlpha = {
                                    spotifyCanvasAlphaState.value *
                                        ((sheetProgress() - 0.72f) / 0.28f).coerceIn(0f, 1f)
                                },
                                onAspectRatioChanged = { canvasAspect = it },
                                onRenderedChanged = { canvasRendered = it },
                                pausedForTransition = isVideoMode || isDismissDragging || currentTab != FullPlayerTab.NOW_PLAYING || !isSheetSettled,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Spotify Canvas readability gradient scrims (keeps top icons & bottom controls legible)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.00f to Color.Black.copy(alpha = 0.52f),
                                            0.18f to Color.Black.copy(alpha = 0.16f),
                                            0.46f to Color.Transparent,
                                            0.64f to Color.Black.copy(alpha = 0.36f),
                                            0.80f to Color.Black.copy(alpha = 0.74f),
                                            1.00f to Color.Black.copy(alpha = 0.92f),
                                        ),
                                    ),
                            )
                        }
                    }
                    val lyricsVeilState = animateFloatAsState(
                        targetValue = if (currentTab == FullPlayerTab.LYRICS) 1f else 0f,
                        animationSpec = tween(260),
                        label = "lyricsVeil",
                    )
                    val showLyricsVeil by remember { androidx.compose.runtime.derivedStateOf { lyricsVeilState.value > 0.01f } }
                    val lyricsVeilBrush = remember {
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.62f),
                            0.35f to Color.Black.copy(alpha = 0.52f),
                            0.70f to Color.Black.copy(alpha = 0.58f),
                            1.00f to Color.Black.copy(alpha = 0.72f),
                        )
                    }
                    if (showLyricsVeil) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = lyricsVeilState.value }
                                .background(lyricsVeilBrush),
                        )
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!lyricsFullscreen) {
                        SheetGrabberPill(isDragging = isDismissDragging)
                    }

                    if (!lyricsFullscreen) Box(
                        Modifier
                            .fillMaxWidth()
                            .adaptiveContentWidth(maxWidth = if (isLandscape) 1100.dp else 640.dp)
                            .height(if (isLandscape) 44.dp else 52.dp)
                            .padding(horizontal = 20.dp)
                            .playerVerticalSwipe(enabled = !isLandscapeScreen && !isVideoFullscreen),
                    ) {
                        LiquidGlassIconButton(
                            onClick = {
                                if (currentTab != FullPlayerTab.NOW_PLAYING) {
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
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 96.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                when (currentTab) {
                                    FullPlayerTab.NOW_PLAYING -> if (isVideoMode) "MUSIC VIDEO" else "NOW PLAYING"
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
                                                .background(Color(0xFF8B5CF6), CircleShape),
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
                            val dur = 260
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
                                var isScrubbing by remember { mutableStateOf(false) }
                                val rawSpatialTilt by rememberSpatialTilt(
                                    enabled = currentTab == FullPlayerTab.NOW_PLAYING &&
                                        state.isPlaying &&
                                        !isDismissDragging &&
                                        !isScrubbing &&
                                        isSheetSettled &&
                                        !hideArtworkForMorph,
                                )
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
                                val isCardHidden = playerFaceStyle == PlayerFaceStyle.BACKGROUND_ONLY ||
                                    playerFaceStyle == PlayerFaceStyle.FULLSCREEN_HERO ||
                                    showSpotifyFullscreenCanvas

                                val artworkContent = @Composable { artworkMod: Modifier ->
                                    val sleeveAlpha by animateFloatAsState(
                                        targetValue = if (isCardHidden) 0f else 1f,
                                        animationSpec = tween(350, easing = FastOutSlowInEasing),
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
                                            .then(if (sleeveAlpha <= 0.01f) Modifier.pointerInput(Unit) {} else Modifier)
                                            .pointerInput(track.videoId, track.title, isVideoMode) {
                                                val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    var dragDirection = 0 // 0 = NONE/TAP, 1 = HORIZONTAL (track skip), 2 = VERTICAL (drag to mini player)
                                                    val touchSlop = viewConfiguration.touchSlop
                                                    val initialX = down.position.x
                                                    val initialY = down.position.y
                                                    var lastY = initialY
                                                    velocityTracker.resetTracking()
                                                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                        if (!change.pressed) {
                                                            when (dragDirection) {
                                                                1 -> {
                                                                    when {
                                                                        artworkDragX < -swipeThreshold -> player.next()
                                                                        artworkDragX > swipeThreshold -> player.previous()
                                                                    }
                                                                    artworkDragX = 0f
                                                                    onHorizontalDrag(0f)
                                                                }
                                                                2 -> {
                                                                    isDismissDragging = false
                                                                    val velY = velocityTracker.calculateVelocity().y
                                                                    val accUpDrag = initialY - change.position.y
                                                                    if (currentTab == FullPlayerTab.NOW_PLAYING && (accUpDrag > swipeThreshold * 1.5f || velY < -900f)) {
                                                                        onVerticalDragEnd(0f)
                                                                        onTabChange(FullPlayerTab.QUEUE)
                                                                    } else {
                                                                        onVerticalDragEnd(velY)
                                                                    }
                                                                }
                                                                0 -> {
                                                                    val now = SystemClock.elapsedRealtime()
                                                                    val side = when {
                                                                        initialX < size.width * 0.34f -> SeekDirection.REWIND
                                                                        initialX > size.width * 0.66f -> SeekDirection.FORWARD
                                                                        else -> null
                                                                    }

                                                                    if (side == null) {
                                                                        lastTapSide = null
                                                                        lastTapTimestamp = 0L
                                                                        if (lastLikeTapTimestamp != 0L && now - lastLikeTapTimestamp < 450L) {
                                                                            lastLikeTapTimestamp = 0L
                                                                            onDoubleTapLike()
                                                                        } else {
                                                                            lastLikeTapTimestamp = now
                                                                        }
                                                                    } else {
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
                                                                            hapticManager.click()
                                                                            val liveProgress = progressState.value
                                                                            val durationBound = liveProgress.durationMs.takeIf { it > 0L }
                                                                                ?: state.durationMs.coerceAtLeast(0L)
                                                                            val newPos = (liveProgress.positionMs + deltaMs).coerceIn(0L, durationBound)
                                                                            player.seekTo(newPos)

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
                                                            }
                                                            break
                                                        }

                                                        if (change.isConsumed) {
                                                            if (dragDirection == 2) {
                                                                isDismissDragging = false
                                                                onVerticalDragEnd(0f)
                                                            }
                                                            artworkDragX = 0f
                                                            onHorizontalDrag(0f)
                                                            lastLikeTapTimestamp = 0L
                                                            lastTapTimestamp = 0L
                                                            lastTapSide = null
                                                            break
                                                        }

                                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                                        val dx = change.position.x - initialX
                                                        val dy = change.position.y - initialY

                                                        if (dragDirection == 0) {
                                                            if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                                                                dragDirection = 1
                                                                lastLikeTapTimestamp = 0L
                                                                lastTapTimestamp = 0L
                                                                lastTapSide = null
                                                                change.consume()
                                                            } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                                                                dragDirection = 2
                                                                isDismissDragging = true
                                                                lastLikeTapTimestamp = 0L
                                                                lastTapTimestamp = 0L
                                                                lastTapSide = null
                                                                val stepDy = change.position.y - lastY
                                                                lastY = change.position.y
                                                                onVerticalDrag(stepDy)
                                                                change.consume()
                                                            }
                                                        } else if (dragDirection == 1) {
                                                            change.consume()
                                                            artworkDragX = dx
                                                            onHorizontalDrag(dx)
                                                        } else if (dragDirection == 2) {
                                                            change.consume()
                                                            val stepDy = change.position.y - lastY
                                                            lastY = change.position.y
                                                            onVerticalDrag(stepDy)
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isVideoMode && hasVideo) {
                                            val maxVidW = minOf(maxWidth, if (isLandscape) ((maxHeight - 20.dp) * 16f / 9f) else 420.dp)
                                            val videoCardWidth = (maxVidW - 4.dp).coerceAtLeast(240.dp)

                                            if (playerFaceStyle != PlayerFaceStyle.CARD_ONLY) Box(
                                                modifier = Modifier
                                                    .width(videoCardWidth + 44.dp)
                                                    .aspectRatio(16f / 9f)
                                                    .background(
                                                        Brush.radialGradient(
                                                            0.0f to ambientColor.copy(alpha = 0.55f),
                                                            0.55f to ambientCompanion.copy(alpha = 0.25f),
                                                            1.0f to Color.Transparent,
                                                        ),
                                                        shape = RoundedCornerShape(36.dp),
                                                    ),
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .width(videoCardWidth)
                                                    .aspectRatio(16f / 9f)
                                                    .align(Alignment.Center)
                                                    .onGloballyPositioned { coords ->
                                                        val pos = coords.positionInRoot()
                                                        val sz = coords.size
                                                        if (sz.width > 0 && sz.height > 0 && !isVideoFullscreen) {
                                                            onArtworkBoundsChanged(
                                                                Rect(
                                                                    left = pos.x,
                                                                    top = pos.y,
                                                                    right = pos.x + sz.width,
                                                                    bottom = pos.y + sz.height,
                                                                ),
                                                            )
                                                        }
                                                    },
                                            )
                                        } else {
                                            val maxArtCap = when {
                                                isCompactStyle && isLandscape -> 190.dp
                                                isCompactStyle -> 216.dp
                                                isLandscape -> 275.dp
                                                inlinePlayerLyricsEnabled && !isVideoMode -> 308.dp
                                                else -> 370.dp
                                            }
                                            val artworkSize = (minOf(maxWidth, maxHeight) - if (isLandscape) 28.dp else if (isCompactStyle) 12.dp else 8.dp)
                                                .coerceAtLeast(0.dp)
                                                .coerceAtMost(maxArtCap)

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
                                                        ),
                                                    )
                                                }
                                                val hasInCardMotion = showSleeveCanvas && activeCanvas != null
                                                val glowAlphaState = animateFloatAsState(
                                                    targetValue = when {
                                                        state.isPlaying && hasInCardMotion -> 0.88f
                                                        state.isPlaying -> 0.70f
                                                        else -> 0.35f
                                                    },
                                                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                                                    label = "artworkGlowAlpha",
                                                )
                                                if (playerFaceStyle != PlayerFaceStyle.CARD_ONLY) {
                                                    val haloSpread = if (hasInCardMotion) 76.dp else 56.dp
                                                    Box(
                                                        modifier = Modifier
                                                            .size(artworkSize + haloSpread)
                                                            .graphicsLayer {
                                                                translationX = shownArtworkXState.value * 0.7f - (spatialTiltX.value * 0.9f)
                                                                translationY = -(spatialTiltY.value * 0.7f)
                                                                alpha = glowAlphaState.value * sleeveAlpha
                                                            }
                                                            .background(
                                                                ambientHaloBrush,
                                                                shape = CircleShape,
                                                            ),
                                                    )
                                                }

                                                val baseArtworkScale = when {
                                                    isScrubbing -> 0.84f
                                                    state.isPlaying -> 1.0f
                                                    else -> 0.88f
                                                }
                                                val artworkPlayingScaleState = animateFloatAsState(
                                                    targetValue = baseArtworkScale,
                                                    animationSpec = ExpressiveMotion.appleSheetSpring(),
                                                    label = "artworkPlayingScale",
                                                )

                                                val morphCorner = if (isCompactStyle) 22.dp else 28.dp
                                                val artworkShape = remember(morphCorner) { SquircleShape(morphCorner) }

                                                // 3D Background Peek Deck Cards during horizontal swipe
                                                if (showArtworkPeekDeck) {
                                                    SwipeDeckPeekCards(
                                                        prevTrack = prevTrack,
                                                        nextTrack = nextTrack,
                                                        shownDragX = { shownArtworkXState.value },
                                                        cardWidth = artworkSize,
                                                        cardHeight = artworkSize,
                                                        cornerRadius = morphCorner,
                                                        modifier = Modifier.align(Alignment.Center),
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(artworkSize - 10.dp)
                                                        .graphicsLayer {
                                                            scaleX = artworkPlayingScaleState.value
                                                            scaleY = artworkPlayingScaleState.value
                                                            translationX = shownArtworkXState.value + (spatialTiltX.value * 0.4f)
                                                            translationY = 16f + (spatialTiltY.value * 0.5f)
                                                            alpha = if (hideArtworkForMorph) 0f else (if (state.isPlaying) 0.65f else 0.40f) * sleeveAlpha
                                                        }
                                                        .background(
                                                            shadowOcclusionBrush,
                                                            shape = artworkShape,
                                                        ),
                                                )

                                                val artworkSharedKey = "player_artwork_${track.videoId ?: track.title}"
                                                val sharedArtworkModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                                    with(sharedTransitionScope) {
                                                        Modifier.sharedBounds(
                                                            sharedContentState = rememberSharedContentState(key = artworkSharedKey),
                                                            animatedVisibilityScope = animatedVisibilityScope,
                                                            boundsTransform = { _, _ ->
                                                                spring(
                                                                    dampingRatio = 0.84f,
                                                                    stiffness = 380f,
                                                                )
                                                            },
                                                            clipInOverlayDuringTransition = OverlayClip(SquircleShape(morphCorner)),
                                                        )
                                                    }
                                                } else Modifier

                                                Surface(
                                                    shape = artworkShape,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f * sleeveAlpha),
                                                    tonalElevation = 10.dp * sleeveAlpha,
                                                    shadowElevation = if (state.isPlaying) 42.dp * sleeveAlpha else 18.dp * sleeveAlpha,
                                                    modifier = Modifier
                                                        .size(artworkSize)
                                                        .then(sharedArtworkModifier)
                                                        .onGloballyPositioned { coords ->
                                                            val pos = coords.positionInRoot()
                                                            val sz = coords.size
                                                            if (sz.width > 0 && sz.height > 0 && !isVideoFullscreen) {
                                                                onArtworkBoundsChanged(
                                                                    Rect(
                                                                        left = pos.x,
                                                                        top = pos.y,
                                                                        right = pos.x + sz.width,
                                                                        bottom = pos.y + sz.height,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                        .graphicsLayer {
                                                            val artworkX = shownArtworkXState.value
                                                            scaleX = artworkPlayingScaleState.value
                                                            scaleY = artworkPlayingScaleState.value
                                                            translationX = artworkX + (spatialTiltX.value * 0.75f)
                                                            translationY = spatialTiltY.value * 0.55f
                                                            rotationZ = (artworkX / 75f) + (spatialTiltX.value * 0.10f)
                                                            rotationY = (spatialTiltX.value - (artworkX / 45f)).coerceIn(-6f, 6f)
                                                            rotationX = (-spatialTiltY.value).coerceIn(-5f, 5f)
                                                            cameraDistance = 18f * this.density
                                                            alpha = if (hideArtworkForMorph) 0f else sleeveAlpha
                                                        }
                                                        .then(if (sleeveAlpha <= 0.01f) Modifier.pointerInput(Unit) {} else Modifier)
                                                        .drawWithCache {
                                                            val strokePx = 1.3.dp.toPx()
                                                            val cornerPx = morphCorner.toPx()
                                                            val outline = artworkShape.createOutline(size, layoutDirection, this)
                                                            val prismBorder = Brush.linearGradient(
                                                                listOf(
                                                                    Color.White.copy(alpha = 0.62f),
                                                                    Color.White.copy(alpha = 0.16f),
                                                                    ambientCompanion.copy(alpha = 0.36f),
                                                                    Color.Black.copy(alpha = 0.22f),
                                                                    ambientColor.copy(alpha = 0.44f),
                                                                ),
                                                                start = Offset.Zero,
                                                                end = Offset(size.width, size.height),
                                                            )
                                                            val topRimBrush = Brush.horizontalGradient(
                                                                listOf(
                                                                    Color.Transparent,
                                                                    Color.White.copy(alpha = 0.72f),
                                                                    Color.White.copy(alpha = 0.90f),
                                                                    Color.White.copy(alpha = 0.38f),
                                                                    Color.Transparent,
                                                                ),
                                                            )
                                                            val rimHeight = 1.5.dp.toPx()
                                                            onDrawWithContent {
                                                                drawContent()
                                                                if (hideArtworkForMorph || sleeveAlpha <= 0.001f) return@onDrawWithContent
                                                                drawOutline(
                                                                    outline = outline,
                                                                    brush = prismBorder,
                                                                    style = Stroke(width = strokePx),
                                                                )
                                                                translate(left = spatialTiltX.value * 4f) {
                                                                    drawRect(
                                                                        brush = topRimBrush,
                                                                        topLeft = Offset(cornerPx * 0.4f, 0f),
                                                                        size = Size((size.width - cornerPx * 0.8f).coerceAtLeast(0f), rimHeight),
                                                                    )
                                                                }
                                                            }
                                                        },
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer {
                                                                shape = RoundedCornerShape(morphCorner)
                                                                clip = true
                                                            },
                                                    ) {
                                                        PlayerArtwork(
                                                            track = track,
                                                            modifier = Modifier.fillMaxSize(),
                                                            corner = morphCorner,
                                                            canvas = if (showSleeveCanvas) activeCanvas else null,
                                                            isPlaying = state.isPlaying,
                                                            pausedForTransition = isVideoMode || isDismissDragging || currentTab != FullPlayerTab.NOW_PLAYING,
                                                            onAspectRatioChanged = { canvasAspect = it },
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
                                        val verticalGap = when {
                                            isLandscape -> 5.dp
                                            isCompactStyle -> 7.dp
                                            else -> 12.dp
                                        }
                                        Column(modifier = controlsMod) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        horizontal = if (isLandscape) 4.dp else 6.dp,
                                                        vertical = if (isLandscape) 2.dp else 4.dp,
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AnimatedVisibility(
                                                    visible = showSpotifyFullscreenCanvas,
                                                    enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.85f),
                                                    exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.85f),
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(46.dp)
                                                                .clip(SquircleShape(10.dp))
                                                                .background(Color.White.copy(alpha = 0.10f))
                                                                .border(
                                                                    width = 0.8.dp,
                                                                    color = Color.White.copy(alpha = 0.24f),
                                                                    shape = SquircleShape(10.dp),
                                                                ),
                                                        ) {
                                                            com.neonsong.app.ui.common.ArtworkImage(
                                                                name = track.title,
                                                                artist = track.artist,
                                                                embeddedUrl = track.artworkUrl,
                                                                fallbackIcon = Icons.Filled.MusicNote,
                                                                decodeSizePx = 180,
                                                                enable3DGlass = false,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        }
                                                        Spacer(Modifier.width(12.dp))
                                                    }
                                                }
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
                                                                    .bounceClick { onOpenArtist(artName) },
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

                                                val likeHeartPulseState = animateFloatAsState(
                                                    targetValue = if (isLiked) 1.22f else 1.0f,
                                                    animationSpec = ExpressiveMotion.appleBouncySpring(),
                                                    label = "likeHeartPulse",
                                                )
                                                LiquidGlassIconButton(
                                                    onClick = { hapticManager.toggle(!isLiked); onToggleLiked() },
                                                    modifier = Modifier.size(if (isLandscape) 38.dp else 48.dp),
                                                    shape = CircleShape,
                                                    accentTint = if (isLiked) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.12f),
                                                    contentDescription = if (isLiked) "Unlike song" else "Like song",
                                                ) {
                                                    Icon(
                                                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                        contentDescription = null,
                                                        tint = if (isLiked) Color(0xFFFF2D55) else Color.White.copy(alpha = 0.85f),
                                                        modifier = Modifier
                                                            .size(if (isLandscape) 19.dp else 24.dp)
                                                            .graphicsLayer {
                                                                scaleX = likeHeartPulseState.value
                                                                scaleY = likeHeartPulseState.value
                                                            },
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(verticalGap))
                                            SeekBar(
                                                progressState = progressState,
                                                isPlaying = state.isPlaying &&
                                                    currentTab == FullPlayerTab.NOW_PLAYING &&
                                                    isSheetSettled &&
                                                    !isVideoFullscreen,
                                                trackKey = track.videoId ?: "${track.artist}|${track.title}",
                                                wavyEnabled = wavySeekbarEnabled,
                                                onSeek = { targetMs -> player.seekTo(targetMs) },
                                                isTranslucent = LocalLiquidGlass.current,
                                                fallbackDurationMs = track.durationMs ?: state.durationMs,
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
                                                overridePlaying = null,
                                                onTogglePlay = null,
                                                onNext = if (isVideoMode) { { player.next() } } else null,
                                                onPrev = if (isVideoMode) { { player.previous() } } else null,
                                                ambientColor = ambientColor,
                                                ambientCompanion = ambientCompanion,
                                            )
                                        }
                                        Spacer(Modifier.height(if (isLandscape) 6.dp else if (isCompactStyle) 10.dp else 16.dp))
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
                                            compact = isLandscape || isCompactStyle,
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
                                                if (inlinePlayerLyricsEnabled && !isVideoMode && !isVideoFullscreen) {
                                                    InlinePlayerLyricsCornerOverlay(
                                                        lyricsState = lyricsState,
                                                        progressState = progressState,
                                                        isPlaying = state.isPlaying,
                                                        playbackSpeed = state.speed,
                                                        lyricsOffsetMs = lyricsOffsetMs,
                                                        ambientColor = ambientColor,
                                                        onOpenFullLyrics = { onTabChange(FullPlayerTab.LYRICS) },
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(start = 6.dp, end = 16.dp, bottom = 6.dp),
                                                    )
                                                }
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .weight(1.15f)
                                                    .fillMaxHeight(),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                controlsContent(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .graphicsLayer {
                                                            val p = sheetProgress()
                                                            alpha = ((p - 0.24f) / 0.42f).coerceIn(0f, 1f)
                                                            translationY = (1f - p) * 28.dp.toPx()
                                                        },
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 20.dp)
                                                .padding(bottom = if (isCompactStyle) 12.dp else 18.dp)
                                                .playerVerticalSwipe(enabled = !isLandscapeScreen && !isVideoFullscreen),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                artworkContent(Modifier.fillMaxSize())
                                                if (inlinePlayerLyricsEnabled && !isVideoMode && !isVideoFullscreen && isCardHidden) {
                                                    InlinePlayerLyricsCornerOverlay(
                                                        lyricsState = lyricsState,
                                                        progressState = progressState,
                                                        isPlaying = state.isPlaying,
                                                        playbackSpeed = state.speed,
                                                        lyricsOffsetMs = lyricsOffsetMs,
                                                        ambientColor = ambientColor,
                                                        onOpenFullLyrics = { onTabChange(FullPlayerTab.LYRICS) },
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .padding(start = 2.dp, end = 24.dp, bottom = 6.dp),
                                                    )
                                                }
                                            }
                                            if (inlinePlayerLyricsEnabled && !isVideoMode && !isVideoFullscreen && !isCardHidden) {
                                                InlinePlayerLyricsCornerOverlay(
                                                    lyricsState = lyricsState,
                                                    progressState = progressState,
                                                    isPlaying = state.isPlaying,
                                                    playbackSpeed = state.speed,
                                                    lyricsOffsetMs = lyricsOffsetMs,
                                                    ambientColor = ambientColor,
                                                    onOpenFullLyrics = { onTabChange(FullPlayerTab.LYRICS) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                                )
                                            }
                                            Spacer(Modifier.height(if (isCompactStyle) 4.dp else 8.dp))
                                            controlsContent(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        val p = sheetProgress()
                                                        alpha = ((p - 0.24f) / 0.42f).coerceIn(0f, 1f)
                                                        translationY = (1f - p) * 28.dp.toPx()
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        state.error?.takeUnless { lyricsFullscreen }?.let { message ->
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
            spatialImmersionPreset = spatialImmersionPreset,
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
) = com.neonsong.app.features.player.components.PlayerProgressSlider(
    value, onValueChange, onValueChangeFinished, valueRange, enabled, modifier, interactionSource, primaryColor, tertiaryColor
)

internal var isQueueLockedState by mutableStateOf(false)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlayerArtwork(
    track: PlayableTrack,
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp,
    decodeSizePx: Int? = null,
    canvas: com.neonsong.app.data.canvas.CanvasArtwork? = null,
    isPlaying: Boolean = false,
    pausedForTransition: Boolean = false,
    transformations: List<coil.transform.Transformation> = emptyList(),
    onAspectRatioChanged: (Float) -> Unit = {},
) = com.neonsong.app.features.player.components.PlayerArtwork(
    track, modifier, corner, decodeSizePx, canvas, isPlaying, pausedForTransition, transformations, onAspectRatioChanged
)

internal fun formatTime(ms: Long): String = com.neonsong.app.features.player.domain.formatTime(ms)

/**
 * 120Hz frame-interpolated Apple Music-style live glowing lyric line anchored cleanly
 * to the bottom-left corner of FullPlayer with zero card background or label.
 * Uses the same hardware-accelerated clip-wipe & withFrameNanos PLL as ModernLyricsView.
 */
@Composable
private fun InlinePlayerLyricsCornerOverlay(
    lyricsState: LyricsUiState,
    progressState: StateFlow<PlaybackProgressState>,
    isPlaying: Boolean,
    playbackSpeed: Float,
    lyricsOffsetMs: Long,
    ambientColor: Color,
    onOpenFullLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val success = lyricsState as? LyricsUiState.Success ?: return
    if (success.isInstrumental) return
    val syncedLines = success.lines
    val fallbackPlainLine = remember(success.plainLyrics) {
        success.plainLyrics
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }
    if (syncedLines.isEmpty() && fallbackPlainLine.isEmpty()) return

    val offsetState by rememberUpdatedState(lyricsOffsetMs)
    val speedState by rememberUpdatedState(playbackSpeed.coerceIn(0.5f, 2f))
    val framePositionMs: MutableLongState = remember(syncedLines) {
        mutableLongStateOf(progressState.value.positionMs)
    }
    val hasAnySyllables = remember(syncedLines) { syncedLines.any { it.hasSyllables } }

    LaunchedEffect(syncedLines, progressState, isPlaying, hasAnySyllables) {
        if (syncedLines.isEmpty()) return@LaunchedEffect
        if (!isPlaying || !hasAnySyllables) {
            progressState.collect { framePositionMs.longValue = it.positionMs }
            return@LaunchedEffect
        }

        var latestSampleMs = progressState.value.positionMs
        val collectorJob = launch {
            progressState.collect { latestSampleMs = it.positionMs }
        }
        try {
            var lastSample = latestSampleMs
            var sampleFrameNanos = -1L
            var lastFrameNanos = -1L
            var display = latestSampleMs.toDouble()
            while (true) {
                withFrameNanos { now ->
                    val sample = latestSampleMs
                    val spd = speedState.toDouble()
                    if (sample != lastSample || sampleFrameNanos < 0L) {
                        lastSample = sample
                        sampleFrameNanos = now
                    }
                    val target = sample + (((now - sampleFrameNanos) / 1_000_000.0) * spd).coerceAtMost(240.0)
                    val frameDt = if (lastFrameNanos < 0L) 0.0 else ((now - lastFrameNanos) / 1_000_000.0) * spd
                    lastFrameNanos = now
                    val predicted = display + frameDt
                    val err = target - predicted
                    display = if (abs(err) > 180.0) target else (predicted + err * 0.28)
                    framePositionMs.longValue = display.toLong()
                }
            }
        } finally {
            collectorJob.cancel()
        }
    }

    val activeIndex by remember(syncedLines) {
        derivedStateOf {
            if (syncedLines.isEmpty()) -1
            else activeLineIndex(syncedLines, (framePositionMs.longValue + offsetState).coerceAtLeast(0L))
        }
    }

    val activeLine = if (syncedLines.isNotEmpty()) {
        val resolvedIdx = if (activeIndex in syncedLines.indices) activeIndex else 0
        syncedLines[resolvedIdx]
    } else null

    val lineText = activeLine?.text?.trim()?.ifBlank { "♪" } ?: fallbackPlainLine
    if (lineText.isBlank()) return

    val glowColor = remember(ambientColor) { lerp(ambientColor, Color.White, 0.55f) }
    val tapInteraction = remember { MutableInteractionSource() }
    val baseStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.25).sp,
        fontWeight = FontWeight.ExtraBold,
        textMotion = TextMotion.Animated,
    )
    val glowStyle = remember(baseStyle, glowColor) {
        baseStyle.copy(
            shadow = Shadow(
                color = glowColor.copy(alpha = 0.78f),
                offset = Offset.Zero,
                blurRadius = 20f,
            ),
        )
    }

    Box(
        modifier = modifier
            .widthIn(max = 312.dp)
            .clickable(
                interactionSource = tapInteraction,
                indication = null,
                onClick = onOpenFullLyrics,
            ),
    ) {
        AnimatedContent(
            targetState = activeIndex to (activeLine?.timeMs ?: 0L),
            transitionSpec = {
                (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 3 })
                    .togetherWith(
                        fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                            slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 3 },
                    )
            },
            label = "appleInlineCornerLyric",
        ) { _ ->
            if (activeLine != null && activeLine.hasSyllables) {
                val displayTexts = remember(activeLine) { resolveSyllableDisplayTexts(activeLine) }
                val fullText = remember(activeLine, displayTexts) {
                    activeLine.syllables.indices.joinToString("") {
                        displayTexts.getOrElse(it) { activeLine.syllables[it].text }
                    }.ifBlank { lineText }
                }
                val charRanges = remember(activeLine, displayTexts) {
                    var offset = 0
                    activeLine.syllables.indices.map { i ->
                        val start = offset
                        offset += displayTexts.getOrElse(i) { activeLine.syllables[i].text }.length
                        start to offset
                    }
                }
                var textLayout by remember(fullText) { mutableStateOf<TextLayoutResult?>(null) }
                val isLineRtl = remember(activeLine) { activeLine.isRtl }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = fullText,
                        style = baseStyle,
                        color = Color.White.copy(alpha = 0.36f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = fullText,
                        style = glowStyle,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                val l = textLayout ?: return@drawWithContent
                                val textLength = fullText.length
                                if (textLength == 0) return@drawWithContent
                                val pos = (framePositionMs.longValue + offsetState).coerceAtLeast(0L)
                                var fill = 0f
                                var sylIdx = -1
                                var sylP = 0f
                                for (i in activeLine.syllables.indices) {
                                    val syl = activeLine.syllables[i]
                                    if (pos < syl.timeMs) break
                                    val (cs, ce) = charRanges[i]
                                    val linear = if (syl.durationMs <= 0L) 1f
                                    else ((pos - syl.timeMs).toFloat() / syl.durationMs).coerceIn(0f, 1f)
                                    val inv = 1f - linear
                                    val p = 1f - inv * inv * (0.65f + 0.35f * inv)
                                    fill = cs + (ce - cs) * p
                                    sylIdx = i
                                    sylP = p
                                    if (p < 1f) break
                                }
                                if (fill <= 0f) return@drawWithContent
                                if (fill >= textLength) {
                                    drawContent()
                                    return@drawWithContent
                                }

                                val (sCs, sCe) = charRanges[sylIdx]
                                val startIdx = sCs.coerceIn(0, textLength - 1)
                                val lastIdx = (sCe - 1).coerceIn(startIdx, textLength - 1)
                                val sameLine = l.getLineForOffset(startIdx) == l.getLineForOffset(lastIdx)
                                val activeVisualLine: Int
                                val wipeX: Float
                                if (sameLine) {
                                    activeVisualLine = l.getLineForOffset(startIdx)
                                    val xs = l.getHorizontalPosition(startIdx, true)
                                    val xe = if (sCe < textLength && l.getLineForOffset(sCe) == activeVisualLine) {
                                        l.getHorizontalPosition(sCe, true)
                                    } else if (isLineRtl) l.getLineLeft(activeVisualLine) else l.getLineRight(activeVisualLine)
                                    wipeX = xs + (xe - xs) * sylP
                                } else {
                                    val charIdx = fill.toInt().coerceIn(0, textLength - 1)
                                    val frac = fill - charIdx
                                    activeVisualLine = l.getLineForOffset(charIdx)
                                    val x0 = l.getHorizontalPosition(charIdx, true)
                                    val x1 = if (charIdx + 1 < textLength && l.getLineForOffset(charIdx + 1) == activeVisualLine) {
                                        l.getHorizontalPosition(charIdx + 1, true)
                                    } else if (isLineRtl) l.getLineLeft(activeVisualLine) else l.getLineRight(activeVisualLine)
                                    wipeX = x0 + (x1 - x0) * frac
                                }

                                val lineTop = l.getLineTop(activeVisualLine)
                                val lineBottom = l.getLineBottom(activeVisualLine)
                                if (lineTop > 0f) {
                                    clipRect(left = 0f, top = 0f, right = size.width, bottom = lineTop) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                                if (isLineRtl) {
                                    clipRect(left = wipeX.coerceIn(0f, size.width), top = lineTop, right = size.width, bottom = lineBottom) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    clipRect(left = 0f, top = lineTop, right = wipeX.coerceIn(0f, size.width), bottom = lineBottom) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            },
                    )
                }
            } else {
                Text(
                    text = lineText,
                    style = glowStyle,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
