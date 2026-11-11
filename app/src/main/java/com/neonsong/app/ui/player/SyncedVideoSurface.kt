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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neonsong.app.data.music.YOUTUBE_WEB_USER_AGENT
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

enum class VideoScalingMode {
    FIT,   // Standard 16:9 aspect ratio with letterboxing
    ZOOM,  // Fills full display, cropping edges for immersive playback
}

/**
 * Samsung Browser "Video Assistant" Style Native Music Video Surface:
 *
 * 1. Resolves direct MP4/WebM streams via [SyncedVideoViewModel] (InnerTube).
 * 2. If direct extraction ever fails, launches a headless 1x1 Samsung-Browser-style
 *    <video> stream sniffer to capture the raw googlevideo.com URL automatically.
 * 3. Renders in Neon Song's OWN native [ExoPlayer] using [TextureView] (so rounded
 *    card corners, MiniPlayer, and 1:1 morph animations clip smoothly without black flashes).
 * 4. Audio renderer is disabled (0% audio CPU overhead); playhead is phase-locked (PLL)
 *    to [MusicPlayer]'s Hi-Res FLAC / Dolby Atmos / C++ 12-Ch DSP master clock!
 * 5. Supports instant Fill/Zoom screen expansion without decoder re-creation.
 */
@OptIn(UnstableApi::class)
@Composable
fun SyncedVideoSurface(
    track: PlayableTrack,
    musicPlayer: MusicPlayer,
    modifier: Modifier = Modifier,
    videoId: String? = track.videoId,
    videoScalingMode: VideoScalingMode = VideoScalingMode.FIT,
    viewModel: SyncedVideoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerState by musicPlayer.state.collectAsStateWithLifecycle()
    val progressState by musicPlayer.progressState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effectiveVideoId = videoId?.takeIf { it.isNotBlank() } ?: track.videoId.orEmpty()

    var sniffedFallbackUrl by remember(track.title, track.artist, effectiveVideoId) {
        mutableStateOf<String?>(null)
    }
    var isFirstFrameRendered by remember(track.title, track.artist, effectiveVideoId) {
        mutableStateOf(false)
    }
    var videoNaturalWidth by remember { mutableIntStateOf(16) }
    var videoNaturalHeight by remember { mutableIntStateOf(9) }

    // Native Video-Only ExoPlayer (Audio track disabled: MusicPlayer owns Hi-Res/Atmos audio)
    val videoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 4_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 350,
                /* bufferForPlaybackAfterRebufferMs = */ 1_200,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(YOUTUBE_WEB_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/",
                ),
            )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
            }
    }

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
        }
        videoPlayer.addListener(listener)
        onDispose {
            videoPlayer.removeListener(listener)
            videoPlayer.release()
        }
    }

    // Trigger direct stream resolution via SyncedVideoRepository
    LaunchedEffect(track.title, track.artist, effectiveVideoId) {
        isFirstFrameRendered = false
        sniffedFallbackUrl = null
        viewModel.loadVideo(track.title, track.artist, effectiveVideoId.takeIf { it.isNotBlank() })
    }

    // Determine active stream URL (Repository primary -> Samsung Browser Sniffer fallback)
    val activeStreamUrl = when (val s = uiState) {
        is SyncedVideoUiState.Ready -> s.activeStream.url
        else -> sniffedFallbackUrl
    }
    val activeMimeType = (uiState as? SyncedVideoUiState.Ready)
        ?.activeStream
        ?.mimeType
        ?.substringBefore(';')
        ?.trim()

    // Feed resolved/intercepted URL into our native ExoPlayer
    LaunchedEffect(activeStreamUrl) {
        val url = activeStreamUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val currentUri = videoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri == url) return@LaunchedEffect

        val startPosMs = musicPlayer.currentPositionMs().coerceAtLeast(0L)
        val itemBuilder = MediaItem.Builder().setUri(url)
        if (!activeMimeType.isNullOrBlank()) {
            itemBuilder.setMimeType(activeMimeType)
        }
        videoPlayer.setMediaItem(itemBuilder.build(), startPosMs)
        videoPlayer.prepare()
        videoPlayer.playWhenReady = playerState.isPlaying
    }

    // Immediate seek response: if audio position jumps by > 450ms, hard seek video immediately!
    val audioPosMs = progressState.positionMs
    LaunchedEffect(audioPosMs) {
        if (videoPlayer.playbackState == Player.STATE_READY) {
            val drift = abs(videoPlayer.currentPosition - audioPosMs)
            if (drift > 450L) {
                videoPlayer.seekTo(audioPosMs)
            }
        }
    }

    // Phase-Locked Loop (PLL) Synchronization with Master Audio Clock (SyncedVideoClockSync)
    LaunchedEffect(videoPlayer, playerState.isPlaying, playerState.speed, activeStreamUrl) {
        if (activeStreamUrl.isNullOrBlank()) return@LaunchedEffect
        var lastHardSeekAtMs = Long.MIN_VALUE
        while (isActive) {
            if (videoPlayer.playWhenReady != playerState.isPlaying) {
                videoPlayer.playWhenReady = playerState.isPlaying
            }
            if (videoPlayer.playbackState == Player.STATE_READY) {
                val audioPos = musicPlayer.currentPositionMs().coerceAtLeast(0L)
                val videoPos = videoPlayer.currentPosition.coerceAtLeast(0L)
                val now = SystemClock.elapsedRealtime()
                val allowHardSeek = lastHardSeekAtMs == Long.MIN_VALUE ||
                    now - lastHardSeekAtMs >= SyncedVideoClockSync.SEEK_COOLDOWN_MS

                val decision = SyncedVideoClockSync.evaluateSync(
                    videoPosMs = videoPos,
                    audioPosMs = audioPos,
                    baseSpeed = playerState.speed,
                    allowHardSeek = allowHardSeek,
                )
                if (decision.action == SyncAction.HARD_SEEK && decision.seekTargetMs != null) {
                    videoPlayer.seekTo(decision.seekTargetMs)
                    lastHardSeekAtMs = now
                }
                if (videoPlayer.playbackParameters.speed != decision.targetSpeed) {
                    videoPlayer.playbackParameters = PlaybackParameters(decision.targetSpeed)
                }
            }
            delay(180L)
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // TextureView participates in Compose's hardware layer pipeline (smooth rounded corners & morphing)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    videoPlayer.setVideoTextureView(this)
                    addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                        val w = right - left
                        val h = bottom - top
                        applyTextureTransform(this, videoNaturalWidth, videoNaturalHeight, w, h, videoScalingMode)
                    }
                }
            },
            update = { textureView ->
                videoPlayer.setVideoTextureView(textureView)
                applyTextureTransform(
                    textureView = textureView,
                    vWidth = videoNaturalWidth,
                    vHeight = videoNaturalHeight,
                    viewWidth = textureView.width,
                    viewHeight = textureView.height,
                    mode = videoScalingMode,
                )
            },
        )

        // Subtle loading indicator until first native video frame renders
        AnimatedVisibility(
            visible = !isFirstFrameRendered,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(38.dp))
            }
        }

        // Samsung Browser-style Headless Stream Sniffer: engages automatically if direct repository fails
        if (uiState is SyncedVideoUiState.Error && sniffedFallbackUrl == null && effectiveVideoId.isNotBlank()) {
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
) {
    if (viewWidth <= 0 || viewHeight <= 0 || vWidth <= 0 || vHeight <= 0) return
    val matrix = Matrix()
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
        // FIT mode: Maintain 16:9 aspect ratio with clean pillarboxing / letterboxing
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
 * Mimics Samsung Internet Browser's "Video Assistant":
 * Runs a 1x1 headless WebView only when needed, mutes & pauses the HTML5 <video> element,
 * extracts its signed googlevideo.com stream URL, and hands it to our native ExoPlayer!
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SamsungVideoAssistantSniffer(
    videoId: String,
    onStreamCaptured: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = YOUTUBE_WEB_USER_AGENT

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onSrcCaptured(url: String) {
                            if (url.startsWith("http") && url.contains("googlevideo.com")) {
                                post { onStreamCaptured(url) }
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
                            view?.post { onStreamCaptured(cleanUrl) }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            (function() {
                                setInterval(function() {
                                    var v = document.querySelector('video');
                                    if (v) {
                                        v.muted = true;
                                        v.pause();
                                        if (v.currentSrc && v.currentSrc.indexOf('http') === 0) {
                                            window.NeonSamsungVideoAssistant.onSrcCaptured(v.currentSrc);
                                        }
                                    }
                                }, 200);
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
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        },
    )
}
