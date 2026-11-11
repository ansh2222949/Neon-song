package com.neonsong.app.ui.player

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neonsong.app.data.canvas.CANVAS_UA
import com.neonsong.app.data.canvas.CanvasArtwork
import com.neonsong.app.data.canvas.CanvasCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TAG = "CanvasArtworkPlayer"
private const val FRAME_CAPTURE_PX = 128

/**
 * How a motion artwork clip fills its container bounds.
 */
enum class CanvasContentMode {
    CROP,
    FIT_PORTRAIT,
}

@OptIn(UnstableApi::class)
@Composable
fun CanvasArtworkPlayer(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentMode: CanvasContentMode = CanvasContentMode.CROP,
    alignPortraitTop: Boolean = false,
    onAspectRatioChanged: (Float) -> Unit = {},
    portraitRevealBounds: IntSize = IntSize.Zero,
    presentationAlpha: () -> Float = { 1f },
    onRenderedChanged: (Boolean) -> Unit = {},
    onFrameCaptured: (Bitmap) -> Unit = {},
    refreshFrameEveryMs: Long? = null,
    frameCapturePx: Int = FRAME_CAPTURE_PX,
    onCoverChanged: (Float) -> Unit = {},
    bottomFade: Float = 0f,
    bottomFadeEndPx: Float? = null,
    pausedForTransition: Boolean = false,
) {
    val context = LocalContext.current

    var url by remember(canvas.url) { mutableStateOf(canvas.url) }
    var rendered by remember(canvas.url) { mutableStateOf(false) }
    var clipAspect by remember(canvas.url) { mutableFloatStateOf(1f) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    val currentCanvas by rememberUpdatedState(canvas)
    val currentContentMode by rememberUpdatedState(contentMode)
    val currentAlignPortraitTop by rememberUpdatedState(alignPortraitTop)
    val currentPresentationAlpha by rememberUpdatedState(presentationAlpha)
    val currentOnFrameCaptured by rememberUpdatedState(onFrameCaptured)
    val reportAspect by rememberUpdatedState(onAspectRatioChanged)
    val reportRendered by rememberUpdatedState(onRenderedChanged)
    val reportCover by rememberUpdatedState(onCoverChanged)

    // Separate upstream vs disk-cached factories:
    // MP4 clips (Tidal/Community) use SimpleCache; HLS (.m3u8) streams (Apple Music) use direct HTTP
    // so segment/playlist reloads never collide in SimpleCache.
    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(CANVAS_UA)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://music.apple.com",
                    "Referer" to "https://music.apple.com/",
                ),
            )
    }
    val directMediaSourceFactory = remember(context) {
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
    }
    val cachedMediaSourceFactory = remember(context) {
        val upstream = DefaultDataSource.Factory(context, httpFactory)
        DefaultMediaSourceFactory(CanvasCache.dataSourceFactory(context, upstream))
    }

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_500,
                /* maxBufferMs = */ 6_000,
                /* bufferForPlaybackMs = */ 150,
                /* bufferForPlaybackAfterRebufferMs = */ 300,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(cachedMediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                val aspect = if (width.isFinite() && width > 0f && videoSize.height > 0) {
                    width / videoSize.height
                } else 1f
                clipAspect = aspect
                reportAspect(aspect)
                textureView?.applyContentTransform(aspect, currentContentMode, currentAlignPortraitTop)
            }

            override fun onRenderedFirstFrame() {
                textureView?.applyContentTransform(
                    clipAspect.takeIf { it > 0f } ?: 1f,
                    currentContentMode,
                    currentAlignPortraitTop,
                )
                rendered = true
            }

            override fun onPlayerError(error: PlaybackException) {
                val alternate = currentCanvas.fallbackUrl
                val tall = currentCanvas.tallUrl
                val nextFallback = when {
                    !alternate.isNullOrBlank() && alternate != url -> alternate
                    !tall.isNullOrBlank() && tall != url && tall != alternate -> tall
                    else -> null
                }
                if (nextFallback != null) {
                    Log.d(TAG, "Primary canvas error (${error.message}), falling back to: $nextFallback")
                    url = nextFallback
                } else {
                    Log.w(TAG, "Canvas playback error for $url: ${error.message}")
                    rendered = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(url) {
        rendered = false
        clipAspect = 1f
        reportAspect(1f)
        val mime = mimeTypeOf(url)
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (mime != null) setMimeType(mime) }
            .build()

        val isHls = mime == MimeTypes.APPLICATION_M3U8 || url.contains(".m3u8", ignoreCase = true)
        val mediaSource = if (isHls) {
            directMediaSourceFactory.createMediaSource(item)
        } else {
            cachedMediaSourceFactory.createMediaSource(item)
        }
        player.setMediaSource(mediaSource)
        player.prepare()
    }

    val foreground = rememberIsForeground()
    LaunchedEffect(foreground, isPlaying, pausedForTransition, rendered) {
        // Allow ExoPlayer to decode the very first frame even during a sheet transition so the
        // canvas is immediately visible, then pause decoding while transitioning if needed.
        player.playWhenReady = foreground && isPlaying && (!pausedForTransition || !rendered)
    }

    LaunchedEffect(rendered) {
        reportRendered(rendered)
        if (!rendered) return@LaunchedEffect
        withFrameMillis { }
        val view = textureView ?: return@LaunchedEffect
        val bitmap = view.captureAt(frameCapturePx, clipAspect, contentMode, alignPortraitTop)
        if (bitmap != null) {
            currentOnFrameCaptured(bitmap)
        }
    }

    LaunchedEffect(rendered, refreshFrameEveryMs, frameCapturePx, clipAspect, contentMode, alignPortraitTop) {
        val interval = refreshFrameEveryMs ?: return@LaunchedEffect
        if (!rendered) return@LaunchedEffect
        while (isActive) {
            delay(interval)
            val view = textureView ?: continue
            val bitmap = view.captureAt(frameCapturePx, clipAspect, contentMode, alignPortraitTop)
            if (bitmap != null) {
                currentOnFrameCaptured(bitmap)
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (rendered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "canvasAlpha",
    )

    LaunchedEffect(Unit) {
        snapshotFlow { alpha * currentPresentationAlpha() }.collect { reportCover(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            reportRendered(false)
            reportCover(0f)
            reportAspect(0f)
        }
    }

    val effectiveAlpha = alpha * currentPresentationAlpha()

    AndroidView(
        factory = { viewContext ->
            TextureView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isOpaque = false
                player.setVideoTextureView(this)

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        applyContentTransform(clipAspect, currentContentMode, currentAlignPortraitTop)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        applyContentTransform(clipAspect, currentContentMode, currentAlignPortraitTop)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        rendered = false
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        if (!rendered) {
                            applyContentTransform(
                                clipAspect.takeIf { it > 0f } ?: 1f,
                                currentContentMode,
                                currentAlignPortraitTop,
                            )
                            rendered = true
                        }
                    }
                }
                textureView = this
            }
        },
        update = { view ->
            if (textureView !== view) {
                textureView = view
                player.setVideoTextureView(view)
            }
            view.applyContentTransform(clipAspect, contentMode, alignPortraitTop)
        },
        modifier = modifier
            .graphicsLayer {
                this.alpha = effectiveAlpha
            }
            .bottomFadeMask(bottomFade, bottomFadeEndPx),
    )
}

private fun Modifier.bottomFadeMask(
    fraction: Float,
    endPx: Float? = null,
): Modifier = if (fraction <= 0.001f) this else this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val endY = endPx?.coerceIn(0f, size.height) ?: size.height
        val startY = endY * (1f - clampedFraction)
        val brush = Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = startY,
            endY = endY,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }
    }

private fun TextureView.captureAt(
    maxPx: Int,
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Bitmap? {
    val viewWidth = width
    val viewHeight = height
    if (viewWidth <= 0 || viewHeight <= 0) return null
    val scale = maxPx.toFloat() / maxOf(viewWidth, viewHeight)
    return runCatching {
        val frame = if (scale >= 1f) {
            getBitmap()
        } else {
            getBitmap(
                (viewWidth * scale).roundToInt().coerceAtLeast(1),
                (viewHeight * scale).roundToInt().coerceAtLeast(1),
            )
        } ?: return null
        if (contentMode != CanvasContentMode.FIT_PORTRAIT || clipAspect >= 1f || clipAspect <= 0f) {
            return frame
        }

        val viewAspect = viewWidth.toFloat() / viewHeight
        val contentWidth = if (clipAspect < viewAspect) frame.height * clipAspect else frame.width.toFloat()
        val contentHeight = if (clipAspect < viewAspect) frame.height.toFloat() else frame.width / clipAspect
        val left = ceil((frame.width - contentWidth) / 2f).toInt().coerceIn(0, frame.width - 1)
        val top = if (alignPortraitTop) 0 else
            ceil((frame.height - contentHeight) / 2f).toInt().coerceIn(0, frame.height - 1)
        val right = (frame.width - left).coerceAtLeast(left + 1)
        val bottom = if (alignPortraitTop) contentHeight.toInt().coerceIn(1, frame.height) else
            (frame.height - top).coerceAtLeast(top + 1)
        val cropped = try {
            Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        } catch (error: RuntimeException) {
            frame.recycle()
            throw error
        }
        if (cropped !== frame) frame.recycle()
        cropped
    }.getOrNull()
}

private fun TextureView.applyContentTransform(
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Boolean {
    val bounds = IntSize(width, height)
    val safeAspect = if (clipAspect.isFinite() && clipAspect > 0f) clipAspect else 1f
    if (bounds.width <= 0 || bounds.height <= 0) {
        setTransform(Matrix())
        return false
    }
    val viewAspect = bounds.width.toFloat() / bounds.height
    if (abs(safeAspect - viewAspect) < 0.01f) {
        setTransform(Matrix())
        return true
    }
    val pivotX = bounds.width / 2f
    val pivotY = if (alignPortraitTop && safeAspect < 1f) {
        0f
    } else bounds.height / 2f
    val matrix = Matrix().apply {
        val fit = contentMode == CanvasContentMode.FIT_PORTRAIT && safeAspect < 1f
        if (fit && safeAspect > viewAspect) {
            setScale(1f, viewAspect / safeAspect, pivotX, pivotY)
        } else if (fit) {
            setScale(safeAspect / viewAspect, 1f, pivotX, pivotY)
        } else if (safeAspect > viewAspect) {
            setScale(safeAspect / viewAspect, 1f, pivotX, pivotY)
        } else {
            setScale(1f, viewAspect / safeAspect, pivotX, pivotY)
        }
    }
    setTransform(matrix)
    return true
}

private fun mimeTypeOf(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".m3u8") || url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") || url.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
