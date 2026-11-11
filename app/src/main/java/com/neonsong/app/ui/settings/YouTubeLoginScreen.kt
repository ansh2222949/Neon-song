package com.neonsong.app.ui.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import com.neonsong.app.ui.theme.GlassActionButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.theme.LocalIsDarkTheme

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com%2F&hl=en"

private fun readYouTubeCookies(): String? = try {
    CookieManager.getInstance().getCookie("https://music.youtube.com")
} catch (error: Exception) {
    null
} catch (error: LinkageError) {
    null
}

/**
 * Native-feeling YouTube Music sign-in redesign.
 *
 * Implements:
 * 1. Song-Reactive Liquid Glass Scaffold.
 * 2. Embedded secure WebView with curved glass container and directional specular border.
 * 3. Liquid Glass helper security panel and status cards.
 */
@Composable
fun YouTubeLoginScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: YouTubeLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    var loadProgressVisible by remember { mutableStateOf(true) }
    var webViewError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cookies = readYouTubeCookies()
        val hasSession = listOf("__Secure-3PAPISID=", "SAPISID=").any { token ->
            cookies?.contains(token) == true
        } && cookies?.contains("LOGIN_INFO=") == true
        if (hasSession && !state.verifying && state.connectedName == null) {
            viewModel.attemptConnect(cookies)
        }
    }

    SettingsGlassScaffold(
        title = "Connect YouTube Music",
        subtitle = "Sign in with your Google account",
        onBack = onBack,
    ) { contentPadding, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .adaptiveContentWidth(maxWidth = 780.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. WebView Frame in Liquid Glass Card
            val frameBorder = remember(isDark, primary) {
                Brush.verticalGradient(
                    listOf(
                        (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.16f else 0.14f),
                        Color.Transparent,
                    ),
                )
            }

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, frameBorder),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(26.dp)),
                        factory = { ctx ->
                            var createdWebView: WebView? = null
                            try {
                                WebView(ctx).also { createdWebView = it }.apply {
                                    @SuppressLint("SetJavaScriptEnabled")
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            loadProgressVisible = false
                                            val current = viewModel.uiState.value
                                            if (current.verifying || current.connectedName != null) return
                                            val cookies = readYouTubeCookies()
                                            val hasSession = listOf("__Secure-3PAPISID=", "SAPISID=").any { token ->
                                                cookies?.contains(token) == true
                                            } && cookies?.contains("LOGIN_INFO=") == true
                                            if (hasSession && url?.startsWith("https://music.youtube.com") == true) {
                                                viewModel.attemptConnect(cookies)
                                            }
                                        }
                                    }
                                    loadUrl(LOGIN_URL)
                                }
                            } catch (error: Exception) {
                                runCatching { createdWebView?.destroy() }
                                loadProgressVisible = false
                                webViewError = "Android System WebView is unavailable. Install or enable a WebView provider to connect YouTube Music."
                                android.widget.FrameLayout(ctx)
                            } catch (error: LinkageError) {
                                runCatching { createdWebView?.destroy() }
                                loadProgressVisible = false
                                webViewError = "This ROM's WebView implementation is incompatible. YouTube Music sign-in is disabled safely."
                                android.widget.FrameLayout(ctx)
                            }
                        },
                    )

                    if (webViewError != null) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        ) {
                            Text(
                                text = webViewError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.connectedName != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            tonalElevation = 6.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = "Connected Successfully",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = state.connectedName.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                                Button(
                                    onClick = onConnected,
                                    shape = CircleShape,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text("Continue to Settings")
                                }
                            }
                        }
                    }

                    if (loadProgressVisible && state.connectedName == null) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                        )
                    }
                }
            }

            // 2. Bottom Helper Bar — Privacy & Action Card
            val barBorder = remember(isDark, primary) {
                Brush.verticalGradient(
                    listOf(
                        (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                        Color.Transparent,
                    ),
                )
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
                border = BorderStroke(0.8.dp, barBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Neon Song never sees your password — sign-in occurs strictly inside Google's official page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                    if (state.errorMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.attemptConnect(readYouTubeCookies())
                        },
                        enabled = webViewError == null && !state.verifying && state.connectedName == null,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (state.verifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Verifying session...")
                        } else {
                            Text("I'm signed in")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Once signed in, you will be authenticated and returned automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
