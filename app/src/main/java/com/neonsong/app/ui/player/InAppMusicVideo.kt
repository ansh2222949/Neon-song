package com.neonsong.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Parses and sanitizes YouTube video IDs from various URL patterns (shorts, youtu.be, embed, watch)
 * and strictly ensures the result matches the 11-character base64 format [a-zA-Z0-9_-]{11}
 * to prevent any JavaScript string-breakout or injection vulnerability.
 */
internal fun extractYouTubeVideoId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    val shortsMatch = Regex("""shorts/([a-zA-Z0-9_-]{11})""").find(trimmed)
    if (shortsMatch != null) return shortsMatch.groupValues[1]

    val youtuBeMatch = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(trimmed)
    if (youtuBeMatch != null) return youtuBeMatch.groupValues[1]

    val vMatch = Regex("""(?:v=|embed/)([a-zA-Z0-9_-]{11})""").find(trimmed)
    if (vMatch != null) return vMatch.groupValues[1]

    if (Regex("""^[a-zA-Z0-9_-]{11}$""").matches(trimmed)) {
        return trimmed
    }

    val anyMatch = Regex("""([a-zA-Z0-9_-]{11})""").find(trimmed)
    return anyMatch?.groupValues?.get(1) ?: ""
}

internal class YouTubeJsBridge(
    private val onReady: () -> Unit,
    private val onPlaying: () -> Unit,
    private val onProgress: (Float, Float) -> Unit,
    private val onStateChange: (Int) -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onPlayerStateChange(state: Int) {
        mainHandler.post {
            onStateChange(state)
            if (state == 1) { // YT.PlayerState.PLAYING
                onPlaying()
            }
        }
    }

    @JavascriptInterface
    fun onVideoProgress(currentMs: Float, durationMs: Float) {
        mainHandler.post { onProgress(currentMs, durationMs) }
    }

    @JavascriptInterface
    fun onPlayerError(errorCode: Int) {
        mainHandler.post { onError(errorCode) }
    }
}

/**
 * Shared/persistent WebView holder for YouTube video playback.
 * Prevents recreation/rebuffering when toggling fullscreen or rotating.
 */
internal object MusicVideoHolder {
    private var webViewRef: WebView? = null
    var activeVideoId: String = ""
        private set
    var isJsReady: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(
        context: Context,
        videoId: String,
        initialPositionSeconds: Float,
        appOrigin: String,
        initialHtml: String,
        blankPoster: Bitmap,
        bridge: YouTubeJsBridge,
    ): Pair<WebView, Boolean> {
        val cleanId = extractYouTubeVideoId(videoId)
        val existing = webViewRef
        if (existing != null && activeVideoId == cleanId) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return Pair(existing, false)
        }

        release()

        val newView = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            val defaultUa = settings.userAgentString.orEmpty()
            settings.userAgentString = defaultUa
                .replace("; wv", "")
                .replace("Version/4.0 ", "")

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            addJavascriptInterface(bridge, "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request != null && !request.isForMainFrame) {
                        return false
                    }
                    val url = request?.url?.toString().orEmpty()
                    val host = request?.url?.host.orEmpty()
                    if (host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("googlevideo.com") ||
                        host.endsWith("google.com") ||
                        host.endsWith("gstatic.com") ||
                        host.endsWith("doubleclick.net") ||
                        url.contains("embed") ||
                        url.contains("player")
                    ) {
                        return false
                    }
                    return try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request?.url)
                        view?.context?.startActivity(intent)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap? = blankPoster
            }

            loadDataWithBaseURL(
                appOrigin,
                initialHtml,
                "text/html",
                "UTF-8",
                null,
            )
        }

        webViewRef = newView
        activeVideoId = cleanId
        isJsReady = false
        return Pair(newView, true)
    }

    fun release() {
        webViewRef?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.destroy()
        }
        webViewRef = null
        activeVideoId = ""
        isJsReady = false
    }
}

internal fun releaseMusicVideo() {
    MusicVideoHolder.release()
}

/**
 * Plays the official YouTube music video inside the player with:
 * - Two-way timestamp handshake (Audio <-> Video zero-reset sync)
 * - Sanitized 11-char video ID extraction (preventing JS injection)
 * - Raw unencoded origin in playerVars (preventing double-encoding and Error 150/153)
 * - Single persistent WebView instance across track changes
 * - Clean native look without YouTube iframe player controls
 * - Strictly scoped lifecycle hooks without calling global pauseTimers()
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun InAppMusicVideo(
    videoId: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    initialPositionSeconds: Float = 0f,
    seekCommandSeconds: Float? = null,
    onProgressUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
    onVideoEnded: (() -> Unit)? = null,
    onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cleanVideoId = remember(videoId) { extractYouTubeVideoId(videoId) }
    val appOrigin = remember(context) { "https://${context.packageName}" }

    var isJsApiReady by remember { mutableStateOf(false) }
    var isUiReady by remember { mutableStateOf(false) }
    var activeVideoId by remember { mutableStateOf(cleanVideoId) }

    val currentOnError by rememberUpdatedState(onError)
    val currentOnProgress by rememberUpdatedState(onProgressUpdate)
    val currentOnEnded by rememberUpdatedState(onVideoEnded)
    val currentOnPlaybackChanged by rememberUpdatedState(onPlaybackStateChanged)

    val initialHtml = remember(appOrigin, cleanVideoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body {
              width: 100%;
              height: 100%;
              background-color: #000000;
              overflow: hidden;
            }
            #player {
              width: 100%;
              height: 100%;
              position: absolute;
              top: 0;
              left: 0;
            }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player = null;
            var currentVideoId = '$cleanVideoId';
            var startSec = $initialPositionSeconds;
            var isPlayerReady = false;
            var progressTimer = null;

            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                height: '100%',
                width: '100%',
                videoId: currentVideoId,
                playerVars: {
                  'autoplay': 1,
                  'playsinline': 1,
                  'controls': 0,
                  'enablejsapi': 1,
                  'fs': 0,
                  'disablekb': 1,
                  'rel': 0,
                  'modestbranding': 1,
                  'origin': '$appOrigin',
                  'widget_referrer': '$appOrigin'
                },
                events: {
                  'onReady': onPlayerReady,
                  'onStateChange': onPlayerStateChange,
                  'onError': onPlayerError
                }
              });
            }

            function onPlayerReady(event) {
              isPlayerReady = true;
              try {
                if (currentVideoId) {
                  event.target.loadVideoById({
                    videoId: currentVideoId,
                    startSeconds: startSec
                  });
                }
              } catch (e) {}
              if (window.AndroidBridge && window.AndroidBridge.onPlayerReady) {
                window.AndroidBridge.onPlayerReady();
              }
            }

            function startProgressReporting() {
              stopProgressReporting();
              progressTimer = setInterval(function() {
                if (player && typeof player.getCurrentTime === 'function' && window.AndroidBridge && window.AndroidBridge.onVideoProgress) {
                  var cur = player.getCurrentTime() * 1000;
                  var dur = (typeof player.getDuration === 'function' ? player.getDuration() : 0) * 1000;
                  window.AndroidBridge.onVideoProgress(cur, dur);
                }
              }, 250);
            }

            function stopProgressReporting() {
              if (progressTimer) {
                clearInterval(progressTimer);
                progressTimer = null;
              }
            }

            function onPlayerStateChange(event) {
              if (event.data === 1) { // PLAYING
                startProgressReporting();
              } else {
                stopProgressReporting();
              }
              if (window.AndroidBridge && window.AndroidBridge.onPlayerStateChange) {
                window.AndroidBridge.onPlayerStateChange(event.data);
              }
            }

            function onPlayerError(event) {
              stopProgressReporting();
              if (window.AndroidBridge && window.AndroidBridge.onPlayerError) {
                window.AndroidBridge.onPlayerError(event.data);
              }
            }

            function loadNewVideo(newVideoId, newStartSec) {
              currentVideoId = newVideoId;
              startSec = newStartSec || 0;
              if (isPlayerReady && player && typeof player.loadVideoById === 'function') {
                try {
                  player.loadVideoById({
                    videoId: newVideoId,
                    startSeconds: startSec
                  });
                } catch (e) {}
              }
            }

            function seekToSeconds(sec) {
              if (isPlayerReady && player && typeof player.seekTo === 'function') {
                try {
                  player.seekTo(sec, true);
                } catch (e) {}
              }
            }

            function setPlayback(play) {
              if (isPlayerReady && player) {
                try {
                  if (play && typeof player.playVideo === 'function') {
                    player.playVideo();
                  } else if (!play && typeof player.pauseVideo === 'function') {
                    player.pauseVideo();
                  }
                } catch (e) {}
              }
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    val blankPoster = remember {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    val jsBridge = remember {
        YouTubeJsBridge(
            onReady = {
                isJsApiReady = true
                MusicVideoHolder.isJsReady = true
                isUiReady = true
            },
            onPlaying = {
                isUiReady = true
            },
            onProgress = { curMs, durMs ->
                currentOnProgress?.invoke(curMs.toLong(), durMs.toLong())
            },
            onStateChange = { state ->
                if (state == 0) { // ENDED
                    currentOnEnded?.invoke()
                } else if (state == 1) { // PLAYING
                    currentOnPlaybackChanged?.invoke(true)
                } else if (state == 2) { // PAUSED
                    currentOnPlaybackChanged?.invoke(false)
                }
            },
            onError = { code ->
                if (code == 101 || code == 150 || code == 153) {
                    currentOnError?.invoke()
                }
            }
        )
    }

    val (webView, isFresh) = remember(cleanVideoId) {
        MusicVideoHolder.getOrCreate(
            context = context,
            videoId = cleanVideoId,
            initialPositionSeconds = initialPositionSeconds,
            appOrigin = appOrigin,
            initialHtml = initialHtml,
            blankPoster = blankPoster,
            bridge = jsBridge,
        )
    }

    LaunchedEffect(isFresh) {
        if (!isFresh && MusicVideoHolder.isJsReady) {
            isJsApiReady = true
            isUiReady = true
        }
    }

    // Hot video switching: passes sanitized ID without recreating WebView
    LaunchedEffect(cleanVideoId, isJsApiReady) {
        if (cleanVideoId.isNotBlank() && cleanVideoId != activeVideoId) {
            activeVideoId = cleanVideoId
            isUiReady = false
            webView.evaluateJavascript("loadNewVideo('$cleanVideoId', $initialPositionSeconds);", null)
        }
    }

    // Dynamic scrubbing seek command
    LaunchedEffect(seekCommandSeconds, isJsApiReady) {
        if (seekCommandSeconds != null && isJsApiReady) {
            webView.evaluateJavascript("seekToSeconds($seekCommandSeconds);", null)
        }
    }

    // Sync playback state with player
    LaunchedEffect(isPlaying, isJsApiReady) {
        if (isJsApiReady) {
            webView.evaluateJavascript("setPlayback($isPlaying);", null)
        }
    }

    // UI safety fallback: fade out spinner after 3.5s if not already ready
    LaunchedEffect(activeVideoId) {
        delay(3500)
        isUiReady = true
    }

    // Instance-safe lifecycle hooks: Allows background/screen-off playback without abrupt pauses
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    if (isPlaying) {
                        webView.evaluateJavascript("setPlayback(true);", null)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Do NOT pause playback when user is actively playing with screen off!
                    if (!isPlaying) {
                        webView.onPause()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Detach from parent without destroying, allowing seamless move between card and fullscreen!
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = !isUiReady,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(400)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
