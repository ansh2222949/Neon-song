package com.neonsong.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun extractYouTubeVideoId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    val patterns = listOf(
        Regex("""shorts/([a-zA-Z0-9_-]{11})"""),
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
        Regex("""(?:v=|embed/|vi/|v/)([a-zA-Z0-9_-]{11})""")
    )
    for (pattern in patterns) {
        pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
    }

    return if (Regex("""^[a-zA-Z0-9_-]{11}$""").matches(trimmed)) trimmed else ""
}

internal class YouTubeJsBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    var onReady: (() -> Unit)? = null
    var onPlaying: (() -> Unit)? = null
    var onProgress: ((Float, Float) -> Unit)? = null
    var onStateChange: ((Int) -> Unit)? = null
    var onError: ((Int) -> Unit)? = null

    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post { onReady?.invoke() }
    }

    @JavascriptInterface
    fun onPlayerStateChange(state: Int) {
        mainHandler.post {
            onStateChange?.invoke(state)
            if (state == 1) {
                onPlaying?.invoke()
            }
        }
    }

    @JavascriptInterface
    fun onVideoProgress(currentMs: Float, durationMs: Float) {
        mainHandler.post { onProgress?.invoke(currentMs, durationMs) }
    }

    @JavascriptInterface
    fun onPlayerError(errorCode: Int) {
        mainHandler.post { onError?.invoke(errorCode) }
    }
}

internal object MusicVideoHolder {
    const val BASE_URL = "https://www.youtube-nocookie.com"

    private var webViewRef: WebView? = null
    val bridge = YouTubeJsBridge()
    var activeVideoId: String = ""
    var isJsReady: Boolean = false

    private val blankPoster: Bitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(
        context: Context,
        videoId: String,
        initialPositionSeconds: Float,
    ): Pair<WebView, Boolean> {
        val cleanId = extractYouTubeVideoId(videoId)
        val existing = webViewRef
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            if (cleanId.isNotBlank() && cleanId != activeVideoId) {
                activeVideoId = cleanId
                if (isJsReady) {
                    existing.evaluateJavascript("loadNewVideo('$cleanId', $initialPositionSeconds);", null)
                } else {
                    existing.loadDataWithBaseURL(BASE_URL, buildHtml(cleanId, initialPositionSeconds), "text/html", "UTF-8", null)
                }
            }
            return Pair(existing, false)
        }

        val initialHtml = buildHtml(cleanId, initialPositionSeconds)

        val newView = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(AndroidColor.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

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
                    val url = request?.url?.toString().orEmpty()
                    if (request?.isForMainFrame == true) {
                        if (url.startsWith("https://www.youtube.com/embed") ||
                            url.startsWith("https://www.youtube-nocookie.com/embed")
                        ) {
                            return false
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, request.url).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            view?.context?.startActivity(intent)
                        } catch (_: Exception) {}
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): Bitmap = blankPoster
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d("MusicVideoWeb", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            loadDataWithBaseURL(BASE_URL, initialHtml, "text/html", "UTF-8", null)
        }

        webViewRef = newView
        activeVideoId = cleanId
        isJsReady = false
        return Pair(newView, true)
    }

    fun release() {
        bridge.onReady = null
        bridge.onPlaying = null
        bridge.onProgress = null
        bridge.onStateChange = null
        bridge.onError = null

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

    internal fun buildHtml(cleanVideoId: String, initialPositionSeconds: Float): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; position: fixed; }
            #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }
          </style>
          <script defer src="https://www.youtube.com/iframe_api"></script>
        </head>
        <body>
          <div id="player"></div>
          <script>
            var player = null;
            var currentVideoId = '$cleanVideoId';
            var startSec = $initialPositionSeconds;
            var isPlayerReady = false;
            var progressTimer = null;
            var userPaused = false;

            // Prevent YouTube player from pausing when screen is off or window blurs
            try {
              Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
              Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
              Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
              ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide', 'focusout'].forEach(function(evt) {
                window.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
                document.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
              });
            } catch(e) {}

            function onYouTubeIframeAPIReady() {
              if (currentVideoId && currentVideoId.length > 0) {
                initPlayer(currentVideoId, startSec);
              }
            }

            function initPlayer(vidId, startTime) {
              if (player) {
                try { player.destroy(); } catch (e) {}
                player = null;
              }
              isPlayerReady = false;
              player = new YT.Player('player', {
                height: '100%',
                width: '100%',
                videoId: vidId,
                host: 'https://www.youtube-nocookie.com',
                playerVars: {
                  'autoplay': 1,
                  'playsinline': 1,
                  'controls': 0,
                  'enablejsapi': 1,
                  'fs': 0,
                  'disablekb': 1,
                  'cc_load_policy': 0,
                  'rel': 0,
                  'iv_load_policy': 3,
                  'autohide': 1,
                  'start': Math.floor(startTime),
                  'origin': 'https://www.youtube-nocookie.com'
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
              // The app's audio engine remains the only audible track source.
              if (typeof event.target.mute === 'function') {
                event.target.mute();
              }
              if (startSec > 0 && typeof event.target.seekTo === 'function') {
                event.target.seekTo(startSec, true);
              }
              if (typeof event.target.playVideo === 'function') {
                event.target.playVideo();
              }
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
              if (event.data === 1) { // Playing
                userPaused = false;
                startProgressReporting();
              } else if (event.data === 2) { // Paused
                // If paused unexpectedly (screen-off / lockscreen) and user didn't request pause:
                if (!userPaused && isPlayerReady && player) {
                  setTimeout(function() {
                    if (!userPaused && player && typeof player.playVideo === 'function') {
                      player.playVideo();
                    }
                  }, 120);
                  return;
                }
                stopProgressReporting();
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
              userPaused = false;
              if (isPlayerReady && player && typeof player.loadVideoById === 'function') {
                try {
                  player.loadVideoById({ videoId: newVideoId, startSeconds: startSec });
                } catch (e) {
                  initPlayer(newVideoId, startSec);
                }
              } else {
                initPlayer(newVideoId, startSec);
              }
            }

            function seekToSeconds(sec) {
              if (isPlayerReady && player && typeof player.seekTo === 'function') {
                try { player.seekTo(sec, true); } catch (e) {}
              }
            }

            function setPlayback(play) {
              userPaused = !play;
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

internal fun releaseMusicVideo() {
    MusicVideoHolder.release()
}

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

    var isJsApiReady by remember { mutableStateOf(MusicVideoHolder.isJsReady) }

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnProgress by rememberUpdatedState(onProgressUpdate)
    val currentOnEnded by rememberUpdatedState(onVideoEnded)
    val currentOnPlaybackChanged by rememberUpdatedState(onPlaybackStateChanged)

    val (webView, _) = remember {
        MusicVideoHolder.getOrCreate(
            context = context,
            videoId = cleanVideoId,
            initialPositionSeconds = initialPositionSeconds,
        )
    }

    // Re-bind callbacks on the singleton bridge so fullscreen/rotation never loses listeners
    DisposableEffect(Unit) {
        val bridge = MusicVideoHolder.bridge
        bridge.onReady = {
            isJsApiReady = true
            MusicVideoHolder.isJsReady = true
        }
        bridge.onProgress = { curMs, durMs ->
            currentOnProgress?.invoke(curMs.toLong(), durMs.toLong())
        }
        bridge.onStateChange = { state ->
            when (state) {
                0 -> currentOnEnded?.invoke()
                1 -> currentOnPlaybackChanged?.invoke(true)
                2 -> currentOnPlaybackChanged?.invoke(false)
            }
        }
        bridge.onError = { code ->
            if (code == 2 || code == 5 || code == 100 || code == 101 || code == 150 || code == 153) {
                currentOnError?.invoke()
            }
        }
        onDispose {}
    }

    // True hot video switching without destroying the WebView
    LaunchedEffect(cleanVideoId, isJsApiReady) {
        if (cleanVideoId.isNotBlank() && cleanVideoId != MusicVideoHolder.activeVideoId) {
            MusicVideoHolder.activeVideoId = cleanVideoId
            if (isJsApiReady) {
                webView.evaluateJavascript("loadNewVideo('$cleanVideoId', $initialPositionSeconds);", null)
            } else {
                val html = MusicVideoHolder.buildHtml(cleanVideoId, initialPositionSeconds)
                webView.loadDataWithBaseURL(MusicVideoHolder.BASE_URL, html, "text/html", "UTF-8", null)
            }
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

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.resumeTimers()
                    if (currentIsPlaying && isJsApiReady) {
                        webView.evaluateJavascript("setPlayback(true);", null)
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Do not call webView.onPause() while playing so audio continues in background
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(AndroidColor.BLACK)
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    addView(
                        webView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            update = { frameLayout ->
                if (webView.parent != frameLayout) {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    frameLayout.removeAllViews()
                    frameLayout.addView(
                        webView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

    }
}
