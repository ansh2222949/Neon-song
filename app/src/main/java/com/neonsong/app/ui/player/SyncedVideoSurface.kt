package com.neonsong.app.ui.player

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.os.SystemClock
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neonsong.app.data.music.YOUTUBE_WEB_USER_AGENT
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

enum class VideoScalingMode {
    FIT,   // Standard 16:9 aspect ratio with letterboxing
    ZOOM,  // Fills full display, cropping edges for immersive playback
}

/**
 * Ultra-fast, frame-accurate native Music Video Surface with:
 * - Dynamic signed stream headers + 150ms fast-start buffer
 * - Exact seek & isolated 30Hz PLL clock controller
 * - TextureView hardware pipeline for smooth rounded corners & morphing
 * - One-shot headless sniffer fallback that blocks heavy images/CSS for fast extraction
 */
@OptIn(UnstableApi::class)
@Composable
fun SyncedVideoSurface(
    track: PlayableTrack,
    musicPlayer: MusicPlayer,
    modifier: Modifier = Modifier,
    videoId: String? = track.videoId,
    videoScalingMode: VideoScalingMode = VideoScalingMode.FIT,
    manualSyncOffsetMs: Long = 0L,
    viewModel: SyncedVideoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Isolated playback state -> zero 15Hz recompositions from positionMs ticks!
    val playbackSyncState by remember(musicPlayer) {
        musicPlayer.state
            .map { Triple(it.isPlaying, it.isBuffering, it.speed) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = with(musicPlayer.state.value) { Triple(isPlaying, isBuffering, speed) },
    )
    val (isAudioPlaying, isAudioBuffering, audioSpeed) = playbackSyncState
    val shouldPlayVideo = isAudioPlaying && !isAudioBuffering

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effectiveVideoId = videoId?.takeIf { it.isNotBlank() } ?: track.videoId.orEmpty()
    val requestKey = remember(track.title, track.artist, effectiveVideoId) {
        viewModel.trackRequestKey(track.title, track.artist, effectiveVideoId)
    }

    // Include activeVideoId so even a resolved-MV swap rebuilds the surface cleanly.
    val surfaceInstanceKey = remember(track.title, track.artist, effectiveVideoId) {
        "$requestKey|$effectiveVideoId"
    }

    var sniffedFallbackUrl by remember(surfaceInstanceKey) { mutableStateOf<String?>(null) }
    var isFirstFrameRendered by remember(surfaceInstanceKey) { mutableStateOf(false) }
    var hasRetriedOnError by remember(surfaceInstanceKey) { mutableStateOf(false) }
    var lastLoadedUrl by remember(surfaceInstanceKey) { mutableStateOf<String?>(null) }
    var videoNaturalWidth by remember(surfaceInstanceKey) { mutableIntStateOf(16) }
    var videoNaturalHeight by remember(surfaceInstanceKey) { mutableIntStateOf(9) }

    // Only accept Ready/Error belonging to THIS track (prevents old song stream bleeding into Next song!)
    val matchingReady = (uiState as? SyncedVideoUiState.Ready)?.takeIf { it.requestKey == requestKey }
    val matchingError = (uiState as? SyncedVideoUiState.Error)?.takeIf { it.requestKey == requestKey }

    LaunchedEffect(matchingReady?.activeStream) {
        val stream = matchingReady?.activeStream ?: return@LaunchedEffect
        if ((stream.width ?: 0) > 0 && (stream.height ?: 0) > 0) {
            videoNaturalWidth = stream.width!!
            videoNaturalHeight = stream.height!!
        }
    }

    val clockController = remember(surfaceInstanceKey) { SyncedVideoClockController() }
    val activeRequestHeaders = remember(surfaceInstanceKey) { ConcurrentHashMap<String, String>() }

    // Key ExoPlayer per track instance so Samsung/MediaCodec async surface state is always fresh on Next/Prev.
    val videoPlayer = remember(context, surfaceInstanceKey) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 120,
                /* bufferForPlaybackAfterRebufferMs = */ 350,
            )
            .setBackBuffer(/* backBufferDurationMs = */ 15_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

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

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dynamicDataSourceFactory))
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.EXACT)
            .build()
            .apply {
                volume = 0f
                // REPEAT_MODE_OFF prevents infinite 0:00 seek loops when a video is slightly shorter than audio
                repeatMode = Player.REPEAT_MODE_OFF
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    // Track current metadata for error auto-recovery inside Player.Listener
    val latestTrack by androidx.compose.runtime.rememberUpdatedState(track)
    val latestVideoId by androidx.compose.runtime.rememberUpdatedState(effectiveVideoId)

    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoNaturalWidth = videoSize.width
                    videoNaturalHeight = videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Auto-recover from expired 403/network stream URLs instead of freezing black
                if (!hasRetriedOnError) {
                    hasRetriedOnError = true
                    lastLoadedUrl = null
                    isFirstFrameRendered = false
                    viewModel.retry(
                        title = latestTrack.title,
                        artist = latestTrack.artist,
                        videoId = latestVideoId.takeIf { it.isNotBlank() },
                        targetDurationMs = latestTrack.durationMs,
                        trackAlbum = latestTrack.album,
                    )
                }
            }
        }
        videoPlayer.addListener(listener)
        onDispose {
            videoPlayer.removeListener(listener)
            videoPlayer.clearVideoTextureView(null)
            videoPlayer.stop()
            videoPlayer.release()
        }
    }

    // Resolve stream bundle cleanly whenever track changes (cancel & reload immediately on track skip)
    LaunchedEffect(surfaceInstanceKey) {
        isFirstFrameRendered = false
        sniffedFallbackUrl = null
        hasRetriedOnError = false
        lastLoadedUrl = null
        clockController.reset()
        val liveDuration = track.durationMs?.takeIf { it > 0L }
            ?: musicPlayer.progressState.value.durationMs.takeIf { it > 0L }
        viewModel.loadVideo(
            title = track.title,
            artist = track.artist,
            videoId = effectiveVideoId.takeIf { it.isNotBlank() },
            targetDurationMs = liveDuration,
            trackAlbum = track.album,
        )
    }

    val activeStream = matchingReady?.activeStream
    val activeStreamUrl = activeStream?.url?.takeIf { it.isNotBlank() } ?: sniffedFallbackUrl?.takeIf { it.isNotBlank() }
    val activeMimeType = activeStream?.mimeType?.substringBefore(';')?.trim()
    var observedAudioDurMs by remember(surfaceInstanceKey) {
        mutableLongStateOf(musicPlayer.progressState.value.durationMs.takeIf { it > 0L } ?: (track.durationMs ?: 0L))
    }
    var observedVideoDurMs by remember(surfaceInstanceKey, activeStream?.durationMs) {
        mutableLongStateOf(activeStream?.durationMs?.takeIf { it > 0L } ?: 0L)
    }
    val estimatedIntroOffsetMs = remember(
        observedAudioDurMs,
        observedVideoDurMs,
        activeStream?.durationMs,
        track.durationMs,
        manualSyncOffsetMs,
    ) {
        val streamDur = observedVideoDurMs.takeIf { it > 0L } ?: (activeStream?.durationMs?.takeIf { it > 0L } ?: 0L)
        val audioDur = observedAudioDurMs.takeIf { it > 0L } ?: (track.durationMs ?: 0L)
        SyncedVideoClockSync.estimateIntroOffsetMs(audioDur, streamDur, manualOffsetMs = manualSyncOffsetMs)
    }

    LaunchedEffect(surfaceInstanceKey, manualSyncOffsetMs) {
        if (videoPlayer.playbackState == Player.STATE_READY) {
            clockController.reset()
            val vDur = videoPlayer.duration
            val rawTarget = musicPlayer.currentPositionMs().coerceAtLeast(0L) + estimatedIntroOffsetMs
            val target = if (vDur > 500L) rawTarget.coerceIn(0L, vDur - 250L) else rawTarget.coerceAtLeast(0L)
            videoPlayer.seekTo(target)
        }
    }

    LaunchedEffect(surfaceInstanceKey, activeStreamUrl) {
        val url = activeStreamUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (lastLoadedUrl == url && videoPlayer.playbackState != Player.STATE_IDLE) {
            if (videoPlayer.playbackState == Player.STATE_READY) {
                isFirstFrameRendered = true
            }
            return@LaunchedEffect
        }

        lastLoadedUrl = url
        isFirstFrameRendered = false
        clockController.reset()
        activeRequestHeaders.clear()
        activeStream?.requestHeaders?.let { activeRequestHeaders.putAll(it) }

        val startupLead = if (shouldPlayVideo) (180L * audioSpeed).toLong() else 0L
        val rawStartMs = (musicPlayer.currentPositionMs().coerceAtLeast(0L) + startupLead + estimatedIntroOffsetMs)
        val streamDurMs = activeStream?.durationMs?.takeIf { it > 1_000L }
        val startPosMs = if (streamDurMs != null) rawStartMs.coerceAtMost(streamDurMs - 250L).coerceAtLeast(0L) else rawStartMs
        val itemBuilder = MediaItem.Builder().setUri(url)
        if (!activeMimeType.isNullOrBlank()) {
            itemBuilder.setMimeType(activeMimeType)
        }
        videoPlayer.setMediaItem(itemBuilder.build(), startPosMs)
        videoPlayer.prepare()
        videoPlayer.playWhenReady = shouldPlayVideo
    }

    // Zero-recomposition discontinuous seek detector (collects progressState inside LaunchedEffect)
    LaunchedEffect(surfaceInstanceKey, videoPlayer, activeStreamUrl, estimatedIntroOffsetMs) {
        if (activeStreamUrl.isNullOrBlank()) return@LaunchedEffect
        var isFirstSample = true
        var lastObservedAudioPosMs = musicPlayer.progressState.value.positionMs
        var lastObservedWallMs = SystemClock.elapsedRealtime()
        musicPlayer.progressState.collect { prog ->
            if (prog.durationMs > 0L && abs(prog.durationMs - observedAudioDurMs) > 250L) {
                observedAudioDurMs = prog.durationMs
            }
            val now = SystemClock.elapsedRealtime()
            if (isFirstSample) {
                isFirstSample = false
                lastObservedAudioPosMs = prog.positionMs
                lastObservedWallMs = now
                return@collect
            }
            val liveState = musicPlayer.state.value
            val expectedDelta = if (liveState.isPlaying && !liveState.isBuffering) {
                ((now - lastObservedWallMs) * liveState.speed).toLong()
            } else 0L
            val actualDelta = prog.positionMs - lastObservedAudioPosMs
            lastObservedAudioPosMs = prog.positionMs
            lastObservedWallMs = now

            if (isFirstFrameRendered && abs(actualDelta - expectedDelta) > 350L && videoPlayer.playbackState == Player.STATE_READY) {
                clockController.reset()
                val vDur = videoPlayer.duration
                val target = (prog.positionMs.coerceAtLeast(0L) + estimatedIntroOffsetMs).let {
                    if (vDur > 500L) it.coerceAtMost(vDur - 250L) else it
                }
                videoPlayer.seekTo(target.coerceAtLeast(0L))
            }
        }
    }

    // 30Hz Audio-Master PLL Loop (pauses cleanly while audio buffers & clamps to video end)
    LaunchedEffect(surfaceInstanceKey, videoPlayer, shouldPlayVideo, audioSpeed, activeStreamUrl, estimatedIntroOffsetMs) {
        if (activeStreamUrl.isNullOrBlank()) return@LaunchedEffect
        var lastHardSeekAtMs = Long.MIN_VALUE
        while (isActive) {
            val vDur = videoPlayer.duration
            if (vDur > 1_000L && abs(vDur - observedVideoDurMs) > 250L) {
                observedVideoDurMs = vDur
            }
            val rawAudioPos = musicPlayer.currentPositionMs().coerceAtLeast(0L)
            val pastVideoEnd = vDur > 5_000L && (rawAudioPos + estimatedIntroOffsetMs) >= vDur - 250L
            val effectivePlay = shouldPlayVideo && !pastVideoEnd

            if (videoPlayer.playWhenReady != effectivePlay) {
                videoPlayer.playWhenReady = effectivePlay
            }
            if (videoPlayer.playbackState == Player.STATE_READY && !pastVideoEnd) {
                if (!isFirstFrameRendered && (videoPlayer.isPlaying || !effectivePlay) &&
                    videoPlayer.videoSize.width > 0 && videoPlayer.currentPosition > 60L
                ) {
                    isFirstFrameRendered = true
                }
                val now = SystemClock.elapsedRealtime()
                val latency = musicPlayer.presentationLatencyMs().takeIf { it > 0L } ?: 45L
                val audibleAudioPos = clockController.getAudibleAudioTimestampMs(
                    rawAudioPosMs = rawAudioPos,
                    isPlaying = effectivePlay,
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
                    isPlaying = effectivePlay,
                    videoOffsetMs = estimatedIntroOffsetMs,
                    videoDurationMs = vDur.takeIf { it > 0L } ?: 0L,
                )
                if (decision.action == SyncAction.HARD_SEEK && decision.seekTargetMs != null) {
                    videoPlayer.seekTo(decision.seekTargetMs)
                    lastHardSeekAtMs = now
                }
                if (abs(videoPlayer.playbackParameters.speed - decision.targetSpeed) >= 0.002f) {
                    videoPlayer.playbackParameters = PlaybackParameters(decision.targetSpeed)
                }
            }
            delay(if (effectivePlay) 33L else 120L)
        }
    }

    val transformMatrix = remember { Matrix() }
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "surfaceFade",
    )

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        key(surfaceInstanceKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        alpha = surfaceAlpha
                        videoPlayer.setVideoTextureView(this)
                        addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                            val w = right - left
                            val h = bottom - top
                            applyTextureTransform(this, videoNaturalWidth, videoNaturalHeight, w, h, videoScalingMode, transformMatrix)
                        }
                    }
                },
                update = { textureView ->
                    textureView.alpha = surfaceAlpha
                    applyTextureTransform(
                        textureView = textureView,
                        vWidth = videoNaturalWidth,
                        vHeight = videoNaturalHeight,
                        viewWidth = textureView.width,
                        viewHeight = textureView.height,
                        mode = videoScalingMode,
                        matrix = transformMatrix,
                    )
                },
                onRelease = { textureView ->
                    runCatching { videoPlayer.clearVideoTextureView(textureView) }
                },
            )
        }

        AnimatedVisibility(
            visible = !isFirstFrameRendered,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }

        if (matchingError != null && sniffedFallbackUrl == null && effectiveVideoId.isNotBlank()) {
            SamsungVideoAssistantSniffer(
                videoId = effectiveVideoId,
                onStreamCaptured = { url -> sniffedFallbackUrl = url },
            )
        }
    }
}

private fun applyTextureTransform(
    textureView: TextureView,
    vWidth: Int,
    vHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    mode: VideoScalingMode,
    matrix: Matrix,
) {
    if (viewWidth <= 0 || viewHeight <= 0 || vWidth <= 0 || vHeight <= 0) return
    matrix.reset()
    val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
    val videoRatio = vWidth.toFloat() / vHeight.toFloat()
    if (mode == VideoScalingMode.ZOOM) {
        val scaleX: Float
        val scaleY: Float
        if (viewRatio > videoRatio) {
            scaleX = 1f
            scaleY = (viewWidth.toFloat() / videoRatio) / viewHeight.toFloat()
        } else {
            scaleX = (viewHeight.toFloat() * videoRatio) / viewWidth.toFloat()
            scaleY = 1f
        }
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    } else {
        val scaleX: Float
        val scaleY: Float
        if (viewRatio > videoRatio) {
            scaleX = (viewHeight.toFloat() * videoRatio) / viewWidth.toFloat()
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = (viewWidth.toFloat() / videoRatio) / viewHeight.toFloat()
        }
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    }
    textureView.setTransform(matrix)
}

/**
 * Fast one-shot headless stream sniffer:
 * Blocks heavy images/fonts/CSS so the signed googlevideo.com stream URL is captured in minimal time,
 * delivers it once, and immediately halts the WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SamsungVideoAssistantSniffer(
    videoId: String,
    onStreamCaptured: (String) -> Unit,
) {
    val capturedOnce = remember(videoId) { AtomicBoolean(false) }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.userAgentString = YOUTUBE_WEB_USER_AGENT

                fun deliverOnce(url: String) {
                    if (capturedOnce.compareAndSet(false, true)) {
                        post {
                            stopLoading()
                            onStreamCaptured(url)
                        }
                    }
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onSrcCaptured(url: String) {
                            if (url.startsWith("http") && url.contains("googlevideo.com")) {
                                deliverOnce(url)
                            }
                        }
                    },
                    "NeonSamsungVideoAssistant",
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        if (reqUrl.contains("googlevideo.com/videoplayback") &&
                            (reqUrl.contains("mime=video") || reqUrl.contains("itag=18") || reqUrl.contains("itag=22"))
                        ) {
                            val cleanUrl = reqUrl.replace(Regex("&range=[^&]+"), "")
                            deliverOnce(cleanUrl)
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        // Block heavy non-essential assets for 2-3x faster fallback capture
                        val lower = reqUrl.lowercase()
                        if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
                            lower.contains(".webp") || lower.contains(".woff") || lower.contains("ytimg.com/vi/")
                        ) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            (function() {
                                var tries = 0;
                                var timer = setInterval(function() {
                                    tries++;
                                    var v = document.querySelector('video');
                                    if (v) {
                                        v.muted = true;
                                        v.pause();
                                        if (v.currentSrc && v.currentSrc.indexOf('http') === 0) {
                                            clearInterval(timer);
                                            window.NeonSamsungVideoAssistant.onSrcCaptured(v.currentSrc);
                                        }
                                    }
                                    if (tries > 40) clearInterval(timer);
                                }, 150);
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }
                loadUrl("https://m.youtube.com/watch?v=$videoId")
            }
        },
        onRelease = { webView ->
            runCatching {
                webView.removeJavascriptInterface("NeonSamsungVideoAssistant")
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        },
    )
}
