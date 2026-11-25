package com.neonsong.app.data.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SpotifyAuthManager"

/**
 * Spotify OAuth 2.0 PKCE Configuration & Endpoints.
 */
object SpotifyAuthConfig {
    const val DEFAULT_CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID" // Replace or configure dynamically
    const val REDIRECT_URI = "neonsong://spotify-auth-callback"
    const val AUTH_URL = "https://accounts.spotify.com/authorize"
    const val TOKEN_URL = "https://accounts.spotify.com/api/token"

    val DEFAULT_SCOPES = listOf(
        "user-read-private",
        "user-read-email",
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
    ).joinToString(" ")
}

/**
 * Authentication state machine emitted by [SpotifyAuthManager].
 */
sealed interface SpotifyAuthState {
    data object Unauthenticated : SpotifyAuthState
    data object Authenticating : SpotifyAuthState
    data class Authenticated(val accessToken: String, val expiresAtMs: Long) : SpotifyAuthState
    data class Error(val message: String) : SpotifyAuthState
}

/**
 * Token response payload received from Spotify Accounts Service.
 */
@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("scope") val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/**
 * Senior Architect-grade Spotify Authentication Manager implementing OAuth 2.0 PKCE flow.
 *
 * Characteristics:
 * - RFC 7636 PKCE (Proof Key for Code Exchange) compliant: generates high-entropy `code_verifier`
 *   and SHA-256 `code_challenge` without requiring a client secret on mobile devices.
 * - Thread-safe token refresh guarded by a Coroutine [Mutex] lock to eliminate redundant refresh races.
 * - Secure persistence via Jetpack [DataStore] preferences.
 * - Auto-refreshes `access_token` when within a 60-second safety window before expiration.
 * - Exposes reactive [authState] flow to drive UI sign-in/out states.
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val authClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val refreshMutex = Mutex()

    // Transient in-memory PKCE challenge state for callback validation
    @Volatile private var pendingCodeVerifier: String? = null
    @Volatile private var pendingState: String? = null

    object PreferencesKeys {
        val SPOTIFY_CLIENT_ID = stringPreferencesKey("spotify_client_id")
        val SPOTIFY_REDIRECT_URI = stringPreferencesKey("spotify_redirect_uri")
        val SPOTIFY_ACCESS_TOKEN = stringPreferencesKey("spotify_access_token")
        val SPOTIFY_REFRESH_TOKEN = stringPreferencesKey("spotify_refresh_token")
        val SPOTIFY_EXPIRES_AT_MS = longPreferencesKey("spotify_expires_at_ms")
    }

    /**
     * Observable reactive authentication state driven directly by DataStore tokens.
     */
    val authState: StateFlow<SpotifyAuthState> = dataStore.data
        .map { prefs ->
            val accessToken = prefs[PreferencesKeys.SPOTIFY_ACCESS_TOKEN]
            val refreshToken = prefs[PreferencesKeys.SPOTIFY_REFRESH_TOKEN]
            val expiresAtMs = prefs[PreferencesKeys.SPOTIFY_EXPIRES_AT_MS] ?: 0L

            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                SpotifyAuthState.Authenticated(accessToken, expiresAtMs)
            } else {
                SpotifyAuthState.Unauthenticated
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SpotifyAuthState.Unauthenticated,
        )

    val clientIdFlow: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.SPOTIFY_CLIENT_ID]?.takeIf { it.isNotBlank() } ?: SpotifyAuthConfig.DEFAULT_CLIENT_ID }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SpotifyAuthConfig.DEFAULT_CLIENT_ID,
        )

    val redirectUriFlow: StateFlow<String> = dataStore.data
        .map { prefs -> prefs[PreferencesKeys.SPOTIFY_REDIRECT_URI]?.takeIf { it.isNotBlank() } ?: SpotifyAuthConfig.REDIRECT_URI }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SpotifyAuthConfig.REDIRECT_URI,
        )

    /**
     * Updates or sets a custom Spotify developer Client ID if not using default.
     */
    suspend fun setClientId(clientId: String) {
        dataStore.edit { it[PreferencesKeys.SPOTIFY_CLIENT_ID] = clientId.trim() }
    }

    private suspend fun getClientId(): String {
        var id: String? = null
        dataStore.edit { id = it[PreferencesKeys.SPOTIFY_CLIENT_ID] }
        return id?.takeIf { it.isNotBlank() } ?: SpotifyAuthConfig.DEFAULT_CLIENT_ID
    }

    /**
     * Updates or sets a custom Spotify Redirect URI if configured differently in developer dashboard.
     */
    suspend fun setRedirectUri(redirectUri: String) {
        dataStore.edit { it[PreferencesKeys.SPOTIFY_REDIRECT_URI] = redirectUri.trim() }
    }

    suspend fun getRedirectUri(): String {
        var uri: String? = null
        dataStore.edit { uri = it[PreferencesKeys.SPOTIFY_REDIRECT_URI] }
        return uri?.takeIf { it.isNotBlank() } ?: SpotifyAuthConfig.REDIRECT_URI
    }

    /**
     * Step 1: Generates PKCE parameters and builds the Spotify OAuth 2.0 Authorization URI.
     */
    suspend fun createAuthorizationUri(): Uri = withContext(Dispatchers.Default) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = UUID.randomUUID().toString()

        pendingCodeVerifier = verifier
        pendingState = state

        val clientId = getClientId()
        val redirectUri = getRedirectUri()

        Uri.parse(SpotifyAuthConfig.AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SpotifyAuthConfig.DEFAULT_SCOPES)
            .build()
    }

    /**
     * Launches the authorization intent using Chrome Custom Tabs or browser fallback.
     */
    suspend fun launchAuthIntent(context: Context) {
        val uri = createAuthorizationUri()
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, uri)
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    /**
     * Step 2: Intercepts the redirect URI callback (`neonsong://spotify-auth-callback?...`)
     * and exchanges authorization code for tokens.
     */
    suspend fun handleAuthRedirect(redirectUri: Uri): Result<SpotifyTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val error = redirectUri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Spotify Auth Error: $error"))
            }

            val returnedState = redirectUri.getQueryParameter("state")
            val expectedState = pendingState
            if (expectedState != null && returnedState != expectedState) {
                return@withContext Result.failure(SecurityException("State mismatch: possible CSRF attempt"))
            }

            val authCode = redirectUri.getQueryParameter("code")
                ?: return@withContext Result.failure(IllegalStateException("No authorization code in redirect URI"))

            val verifier = pendingCodeVerifier
                ?: return@withContext Result.failure(IllegalStateException("Missing code_verifier for PKCE exchange"))

            val tokenResult = exchangeAuthCodeForTokens(authCode, verifier)
            tokenResult.onSuccess { response ->
                saveTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresInSec = response.expiresIn,
                )
                pendingCodeVerifier = null
                pendingState = null
            }
            tokenResult
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Spotify auth redirect", e)
            Result.failure(e)
        }
    }

    /**
     * Exchanges code for initial access and refresh tokens.
     */
    private suspend fun exchangeAuthCodeForTokens(code: String, verifier: String): Result<SpotifyTokenResponse> = withContext(Dispatchers.IO) {
        val clientId = getClientId()
        val redirectUri = getRedirectUri()
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url(SpotifyAuthConfig.TOKEN_URL)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        executeTokenRequest(request)
    }

    /**
     * Retrieves a valid `access_token`, automatically refreshing it with a [Mutex] lock
     * if expired or about to expire in < 60s.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val currentTokens = getCurrentTokens()
            val accessToken = currentTokens.first
            val refreshToken = currentTokens.second
            val expiresAtMs = currentTokens.third

            if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
                return@withLock null
            }

            val now = System.currentTimeMillis()
            // If token is valid for more than 60 seconds, reuse it directly
            if (expiresAtMs - now > 60_000L) {
                return@withLock accessToken
            }

            Log.i(TAG, "Spotify access token expired or expiring soon (${(expiresAtMs - now) / 1000}s). Refreshing...")
            val refreshResult = refreshAccessTokenInternal(refreshToken)
            refreshResult.getOrNull()?.accessToken
        }
    }

    /**
     * Forces an immediate token refresh, typically called upon encountering an HTTP 401.
     */
    suspend fun forceRefreshToken(): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val (_, refreshToken, _) = getCurrentTokens()
            if (refreshToken.isNullOrBlank()) return@withLock null
            refreshAccessTokenInternal(refreshToken).getOrNull()?.accessToken
        }
    }

    private suspend fun refreshAccessTokenInternal(refreshToken: String): Result<SpotifyTokenResponse> = withContext(Dispatchers.IO) {
        val clientId = getClientId()
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url(SpotifyAuthConfig.TOKEN_URL)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        val result = executeTokenRequest(request)
        result.onSuccess { response ->
            saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken, // Preserve existing refresh token if not rotated
                expiresInSec = response.expiresIn,
            )
            Log.i(TAG, "Spotify access token refreshed successfully!")
        }.onFailure { error ->
            Log.e(TAG, "Failed to refresh Spotify token. Clearing session", error)
            logout()
        }
        result
    }

    private fun executeTokenRequest(request: Request): Result<SpotifyTokenResponse> {
        return try {
            authClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val tokenResponse = json.decodeFromString<SpotifyTokenResponse>(body)
                    Result.success(tokenResponse)
                } else {
                    val errorMsg = "HTTP ${response.code}: $body"
                    Result.failure(IOException(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Long) {
        val expiresAtMs = System.currentTimeMillis() + (expiresInSec * 1000L)
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SPOTIFY_ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                prefs[PreferencesKeys.SPOTIFY_REFRESH_TOKEN] = refreshToken
            }
            prefs[PreferencesKeys.SPOTIFY_EXPIRES_AT_MS] = expiresAtMs
        }
    }

    private suspend fun getCurrentTokens(): Triple<String?, String?, Long> {
        var access: String? = null
        var refresh: String? = null
        var expiresAt: Long = 0L
        dataStore.edit { prefs ->
            access = prefs[PreferencesKeys.SPOTIFY_ACCESS_TOKEN]
            refresh = prefs[PreferencesKeys.SPOTIFY_REFRESH_TOKEN]
            expiresAt = prefs[PreferencesKeys.SPOTIFY_EXPIRES_AT_MS] ?: 0L
        }
        return Triple(access, refresh, expiresAt)
    }

    /**
     * Clears all Spotify OAuth credentials from DataStore and invalidates session.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.SPOTIFY_ACCESS_TOKEN)
            prefs.remove(PreferencesKeys.SPOTIFY_REFRESH_TOKEN)
            prefs.remove(PreferencesKeys.SPOTIFY_EXPIRES_AT_MS)
        }
        pendingCodeVerifier = null
        pendingState = null
    }

    // --- PKCE Crypto Primitives ---

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(64)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes, 0, bytes.size)
        val hashed = digest.digest()
        return Base64.encodeToString(hashed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
