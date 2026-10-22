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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neonsong.app.data.canvas.CANVAS_UA
import com.neonsong.app.data.canvas.CanvasArtwork
import com.neonsong.app.data.canvas.CanvasCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
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

/**
 * A looping video player that renders animated album artwork over a still sleeve or background.
 *
 * Dedicated, silent ExoPlayer:
 * - Audio tracks disabled entirely (no network bandwidth spent on audio streams).
 * - Zero volume, no audio focus requested (zero ducking or interference with the main music player).
 * - Loops via REPEAT_MODE_ONE.
 * - Cached via [CanvasCache] so repeated loops stream purely from disk.
 * - Smooth 320ms crossfade over still artwork once the first frame arrives.
 * - Pauses decoding when the screen is off, app is backgrounded, or [isPlaying] is false.
 */
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
    var clipAspect by remember(canvas.url) { mutableFloatStateOf(0f) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }

    val currentCanvas by rememberUpdatedState(canvas)
    val currentContentMode by rememberUpdatedState(contentMode)
    val currentAlignPortraitTop by rememberUpdatedState(alignPortraitTop)
    val currentPortraitRevealBounds by rememberUpdatedState(portraitRevealBounds)
    val currentPresentationAlpha by rememberUpdatedState(presentationAlpha)
    val currentOnFrameCaptured by rememberUpdatedState(onFrameCaptured)
    val reportAspect by rememberUpdatedState(onAspectRatioChanged)
    val reportRendered by rememberUpdatedState(onRenderedChanged)
    val reportCover by rememberUpdatedState(onCoverChanged)

    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(CANVAS_UA)
            .setAllowCrossProtocolRedirects(true)
        val upstream = DefaultDataSource.Factory(context, httpFactory)
        val cachedFactory = CanvasCache.dataSourceFactory(context, upstream)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedFactory))
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
                } else 0f
                if (aspect != clipAspect) rendered = false
                clipAspect = aspect
                reportAspect(aspect)
                textureView?.applyContentTransform(aspect, currentContentMode, currentAlignPortraitTop)
            }

            override fun onRenderedFirstFrame() {
                val transformed = textureView?.applyContentTransform(
                    clipAspect, currentContentMode, currentAlignPortraitTop,
                ) ?: false
                val portrait = currentContentMode == CanvasContentMode.FIT_PORTRAIT &&
                    clipAspect > 0f && clipAspect < 1f
                val expected = currentPortraitRevealBounds
                val view = textureView
                val layoutReady = !portrait ||
                    (expected != IntSize.Zero && view?.width == expected.width && view?.height == expected.height)
                if ((currentContentMode == CanvasContentMode.CROP || transformed) && layoutReady) {
                    rendered = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val alternate = currentCanvas.fallbackUrl
                if (alternate != null && alternate != url) {
                    Log.d(TAG, "Primary canvas error, falling back to: $alternate")
                    url = alternate
                } else {
                    Log.w(TAG, "Canvas playback error: ${error.message}")
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
        clipAspect = 0f
        reportAspect(0f)
        val item = MediaItem.Builder().setUri(url)
        mimeTypeOf(url)?.let { item.setMimeType(it) }
        player.setMediaItem(item.build())
        player.prepare()
    }

    val foreground = rememberIsForeground()
    LaunchedEffect(foreground, isPlaying, pausedForTransition) {
        player.playWhenReady = foreground && isPlaying && !pausedForTransition
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

    val effectiveAlpha = if (contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect <= 0f) {
        0f
    } else {
        alpha * currentPresentationAlpha()
    }

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
                        if (currentContentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect in 0f..1f) {
                            rendered = false
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        rendered = false
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        if (!rendered) {
                            val transformed = applyContentTransform(
                                clipAspect, currentContentMode, currentAlignPortraitTop,
                            )
                            val portrait = currentContentMode == CanvasContentMode.FIT_PORTRAIT &&
                                clipAspect > 0f && clipAspect < 1f
                            val expected = currentPortraitRevealBounds
                            val layoutReady = !portrait ||
                                (expected != IntSize.Zero && width == expected.width && height == expected.height)
                            if ((currentContentMode == CanvasContentMode.CROP || transformed) && layoutReady) {
                                rendered = true
                            }
                        }
                    }
                }
                textureView = this
            }
        },
        update = { view ->
            view.applyContentTransform(clipAspect, contentMode, alignPortraitTop)
        },
        modifier = modifier
            .graphicsLayer {
                this.alpha = effectiveAlpha
            }
            .bottomFadeMask(bottomFade, bottomFadeEndPx),
    )
}

/**
 * Pure Compose hardware-accelerated bottom fade mask.
 * Eliminates custom FrameLayout, RenderEffect reflection, and works identically across all API levels.
 */
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
        Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
    }.getOrNull()
}

private fun TextureView.applyContentTransform(
    clipAspect: Float,
    contentMode: CanvasContentMode,
    alignPortraitTop: Boolean,
): Boolean {
    val bounds = IntSize(width, height)
    if (bounds.width <= 0 || bounds.height <= 0 || !clipAspect.isFinite() || clipAspect <= 0f) {
        setTransform(Matrix())
        return false
    }
    val viewAspect = bounds.width.toFloat() / bounds.height
    val pivotX = bounds.width / 2f
    val pivotY = if (alignPortraitTop && clipAspect < 1f) {
        0f
    } else bounds.height / 2f
    val matrix = Matrix().apply {
        val fit = contentMode == CanvasContentMode.FIT_PORTRAIT && clipAspect < 1f
        if (fit && clipAspect > viewAspect) {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        } else if (fit) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else if (clipAspect > viewAspect) {
            setScale(clipAspect / viewAspect, 1f, pivotX, pivotY)
        } else {
            setScale(1f, viewAspect / clipAspect, pivotX, pivotY)
        }
    }
    setTransform(matrix)
    return true
}

private fun mimeTypeOf(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
