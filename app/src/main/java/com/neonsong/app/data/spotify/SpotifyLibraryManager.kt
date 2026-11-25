package com.neonsong.app.data.spotify

import android.content.Context
import android.util.Log
import com.neonsong.app.data.generate.GeneratedTrack
import com.neonsong.app.data.playlist.LIKED_SONGS_MODE
import com.neonsong.app.data.playlist.LIKED_SONGS_TITLE
import com.neonsong.app.data.playlist.SavedPlaylist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SpotifyLibraryManager"
private const val SPOTIFY_API_BASE_URL = "https://api.spotify.com/"
private const val CACHE_EXPIRATION_MS = 60 * 60 * 1000L // 1 hour TTL

// --- Retrofit API Definition ---

interface SpotifyApiService {

    @GET("v1/me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Header("Authorization") authHeader: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<ResponseBody>

    @GET("v1/playlists/{playlist_id}/tracks")
    suspend fun getPlaylistTracks(
        @Header("Authorization") authHeader: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<ResponseBody>

    @GET("v1/me/tracks")
    suspend fun getLikedTracks(
        @Header("Authorization") authHeader: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Response<ResponseBody>
}

// --- Spotify Web API DTOs (Kotlinx.Serialization) ---

@Serializable
data class SpotifyPageDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val next: String? = null,
    val offset: Int = 0,
)

@Serializable
data class SpotifyPlaylistItemDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImageDto> = emptyList(),
    val tracks: SpotifyTracksCountDto? = null,
)

@Serializable
data class SpotifyTracksCountDto(
    val total: Int = 0,
)

@Serializable
data class SpotifyTrackContainerDto(
    val track: SpotifyTrackDto? = null,
)

@Serializable
data class SpotifyTrackDto(
    val id: String? = null,
    val name: String = "",
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("external_ids") val externalIds: SpotifyExternalIdsDto? = null,
)

@Serializable
data class SpotifyArtistDto(
    val name: String = "",
)

@Serializable
data class SpotifyAlbumDto(
    val name: String = "",
    val images: List<SpotifyImageDto> = emptyList(),
)

@Serializable
data class SpotifyImageDto(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)

@Serializable
data class SpotifyExternalIdsDto(
    val isrc: String? = null,
)

/**
 * Clean extracted Spotify metadata containing ISRC and album art.
 */
@Serializable
data class SpotifyTrackMetadata(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String?,
    val isrc: String?,
    val durationMs: Long,
)

@Serializable
data class CacheEnvelope<T>(
    val timestamp: Long,
    val data: T,
)

/**
 * Senior Architect-grade Spotify Library Manager.
 *
 * Responsibilities:
 * - Fetches user playlists and Liked Songs using Retrofit.
 * - Extracts and maps tracks (with ISRC, Title, Artist, Album Art, Duration) into
 *   Neon Song's domain models: [SavedPlaylist] and [GeneratedTrack].
 * - Implements resilient disk caching with TTL in [Context.getCacheDir].
 * - Handles HTTP 401 Unauthorized via transparent [SpotifyAuthManager] token refresh.
 * - Handles HTTP 429 Too Many Requests rate limiting with exponential backoff and `Retry-After`.
 * - Exposes reactive cold [Flow] streams with offline-first emission semantics.
 */
@Singleton
class SpotifyLibraryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: SpotifyAuthManager,
    okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDirectory = File(context.cacheDir, "spotify_library_cache").apply { mkdirs() }

    private val api: SpotifyApiService by lazy {
        val client = okHttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(SPOTIFY_API_BASE_URL)
            .client(client)
            .build()
            .create(SpotifyApiService::class.java)
    }

    /**
     * Fetches all user playlists.
     * Emits cached state first (if present), then emits fresh network data.
     */
    fun getUserPlaylists(forceRefresh: Boolean = false): Flow<Result<List<SavedPlaylist>>> = flow {
        val cacheFile = File(cacheDirectory, "playlists_summary.json")

        // 1. Emit cached playlists if available and not explicitly forced
        if (!forceRefresh && cacheFile.exists()) {
            readCache<List<SpotifyPlaylistItemDto>>(cacheFile)?.let { cachedItems ->
                val cachedPlaylists = cachedItems.map { it.toSavedPlaylistSummary() }
                emit(Result.success(cachedPlaylists))
            }
        }

        // 2. Fetch fresh playlists from network with pagination
        val networkResult = fetchAllPlaylistsFromNetwork()
        if (networkResult.isSuccess) {
            val freshItems = networkResult.getOrThrow()
            writeCache(cacheFile, freshItems)
            emit(Result.success(freshItems.map { it.toSavedPlaylistSummary() }))
        } else {
            // If network failed but cache was not emitted earlier, emit failure
            if (forceRefresh || !cacheFile.exists()) {
                emit(Result.failure(networkResult.exceptionOrNull() ?: IOException("Failed to fetch Spotify playlists")))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches all tracks for a specific Spotify playlist by ID.
     */
    fun getPlaylistTracks(
        playlistId: String,
        playlistTitle: String,
        forceRefresh: Boolean = false,
    ): Flow<Result<SavedPlaylist>> = flow {
        val cacheFile = File(cacheDirectory, "playlist_$playlistId.json")

        if (!forceRefresh && cacheFile.exists()) {
            readCache<List<SpotifyTrackMetadata>>(cacheFile)?.let { cachedTracks ->
                val playlist = SavedPlaylist(
                    id = playlistId.hashCode().toLong(),
                    title = playlistTitle,
                    subtitle = "${cachedTracks.size} tracks • Spotify",
                    mode = "spotify",
                    tracks = cachedTracks.map { it.toGeneratedTrack() },
                    createdAtMillis = System.currentTimeMillis(),
                    remotePlaylistId = playlistId,
                    remoteTrackCount = cachedTracks.size,
                )
                emit(Result.success(playlist))
            }
        }

        val networkResult = fetchPlaylistTracksFromNetwork(playlistId)
        if (networkResult.isSuccess) {
            val freshTracks = networkResult.getOrThrow()
            writeCache(cacheFile, freshTracks)
            val playlist = SavedPlaylist(
                id = playlistId.hashCode().toLong(),
                title = playlistTitle,
                subtitle = "${freshTracks.size} tracks • Spotify",
                mode = "spotify",
                tracks = freshTracks.map { it.toGeneratedTrack() },
                createdAtMillis = System.currentTimeMillis(),
                remotePlaylistId = playlistId,
                remoteTrackCount = freshTracks.size,
            )
            emit(Result.success(playlist))
        } else {
            if (forceRefresh || !cacheFile.exists()) {
                emit(Result.failure(networkResult.exceptionOrNull() ?: IOException("Failed to load tracks for playlist $playlistId")))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches Spotify Liked Songs (`GET /v1/me/tracks`).
     */
    fun getLikedSongs(forceRefresh: Boolean = false): Flow<Result<SavedPlaylist>> = flow {
        val cacheFile = File(cacheDirectory, "liked_songs.json")

        if (!forceRefresh && cacheFile.exists()) {
            readCache<List<SpotifyTrackMetadata>>(cacheFile)?.let { cachedTracks ->
                val playlist = SavedPlaylist(
                    id = -9999L,
                    title = LIKED_SONGS_TITLE,
                    subtitle = "${cachedTracks.size} tracks • Spotify",
                    mode = LIKED_SONGS_MODE,
                    tracks = cachedTracks.map { it.toGeneratedTrack() },
                    createdAtMillis = System.currentTimeMillis(),
                    remotePlaylistId = "spotify_liked_songs",
                    remoteTrackCount = cachedTracks.size,
                )
                emit(Result.success(playlist))
            }
        }

        val networkResult = fetchLikedTracksFromNetwork()
        if (networkResult.isSuccess) {
            val freshTracks = networkResult.getOrThrow()
            writeCache(cacheFile, freshTracks)
            val playlist = SavedPlaylist(
                id = -9999L,
                title = LIKED_SONGS_TITLE,
                subtitle = "${freshTracks.size} tracks • Spotify",
                mode = LIKED_SONGS_MODE,
                tracks = freshTracks.map { it.toGeneratedTrack() },
                createdAtMillis = System.currentTimeMillis(),
                remotePlaylistId = "spotify_liked_songs",
                remoteTrackCount = freshTracks.size,
            )
            emit(Result.success(playlist))
        } else {
            if (forceRefresh || !cacheFile.exists()) {
                emit(Result.failure(networkResult.exceptionOrNull() ?: IOException("Failed to load Spotify Liked Songs")))
            }
        }
    }.flowOn(Dispatchers.IO)

    // --- Network Orchestration & Pagination ---

    private suspend fun fetchAllPlaylistsFromNetwork(): Result<List<SpotifyPlaylistItemDto>> = withContext(Dispatchers.IO) {
        val allPlaylists = mutableListOf<SpotifyPlaylistItemDto>()
        var offset = 0
        val limit = 50
        var hasMore = true

        while (hasMore) {
            val pageResult = executeWithRetry { token ->
                api.getCurrentUserPlaylists(authHeader = token, limit = limit, offset = offset)
            }

            if (pageResult.isFailure) {
                return@withContext Result.failure(pageResult.exceptionOrNull() ?: IOException("Playlists fetch error"))
            }

            val bodyString = pageResult.getOrThrow()
            val pageDto = runCatching {
                json.decodeFromString<SpotifyPageDto<SpotifyPlaylistItemDto>>(bodyString)
            }.getOrElse { return@withContext Result.failure(it) }

            allPlaylists.addAll(pageDto.items)
            offset += pageDto.items.size
            hasMore = pageDto.next != null && pageDto.items.isNotEmpty()
        }

        Result.success(allPlaylists)
    }

    private suspend fun fetchPlaylistTracksFromNetwork(playlistId: String): Result<List<SpotifyTrackMetadata>> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SpotifyTrackMetadata>()
        var offset = 0
        val limit = 50
        var hasMore = true

        while (hasMore) {
            val pageResult = executeWithRetry { token ->
                api.getPlaylistTracks(authHeader = token, playlistId = playlistId, limit = limit, offset = offset)
            }

            if (pageResult.isFailure) {
                return@withContext Result.failure(pageResult.exceptionOrNull() ?: IOException("Playlist tracks error"))
            }

            val bodyString = pageResult.getOrThrow()
            val pageDto = runCatching {
                json.decodeFromString<SpotifyPageDto<SpotifyTrackContainerDto>>(bodyString)
            }.getOrElse { return@withContext Result.failure(it) }

            val mapped = pageDto.items.mapNotNull { it.track?.toMetadata() }
            tracks.addAll(mapped)
            offset += pageDto.items.size
            hasMore = pageDto.next != null && pageDto.items.isNotEmpty()
        }

        Result.success(tracks)
    }

    private suspend fun fetchLikedTracksFromNetwork(): Result<List<SpotifyTrackMetadata>> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SpotifyTrackMetadata>()
        var offset = 0
        val limit = 50
        var hasMore = true

        while (hasMore) {
            val pageResult = executeWithRetry { token ->
                api.getLikedTracks(authHeader = token, limit = limit, offset = offset)
            }

            if (pageResult.isFailure) {
                return@withContext Result.failure(pageResult.exceptionOrNull() ?: IOException("Liked tracks error"))
            }

            val bodyString = pageResult.getOrThrow()
            val pageDto = runCatching {
                json.decodeFromString<SpotifyPageDto<SpotifyTrackContainerDto>>(bodyString)
            }.getOrElse { return@withContext Result.failure(it) }

            val mapped = pageDto.items.mapNotNull { it.track?.toMetadata() }
            tracks.addAll(mapped)
            offset += pageDto.items.size
            hasMore = pageDto.next != null && pageDto.items.isNotEmpty()
        }

        Result.success(tracks)
    }

    /**
     * Executes Spotify API requests with robust HTTP 401 auto-recovery and HTTP 429 rate limit backoff.
     */
    private suspend fun executeWithRetry(
        maxRetries: Int = 3,
        requestCall: suspend (token: String) -> Response<ResponseBody>,
    ): Result<String> {
        var attempts = 0
        while (attempts < maxRetries) {
            attempts++
            val token = authManager.getValidAccessToken()
                ?: return Result.failure(IllegalStateException("Spotify account is not authenticated"))

            val response = try {
                requestCall("Bearer $token")
            } catch (e: Exception) {
                if (attempts >= maxRetries) return Result.failure(e)
                delay(1000L * attempts)
                continue
            }

            when (response.code()) {
                in 200..299 -> {
                    val bodyString = response.body()?.string().orEmpty()
                    return Result.success(bodyString)
                }

                401 -> {
                    Log.w(TAG, "Spotify returned 401 Unauthorized. Forcing token refresh (attempt $attempts)...")
                    val refreshedToken = authManager.forceRefreshToken()
                    if (refreshedToken == null) {
                        return Result.failure(IOException("Spotify session expired. Please log in again."))
                    }
                    // Loop will retry with refreshed token
                }

                403 -> {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "Spotify API Error 403: $errorBody")
                    val message = if (errorBody.contains("premium", ignoreCase = true)) {
                        "Active Spotify Premium subscription required for the app owner in Spotify Developer Dashboard. You can import playlists via URL for free without Premium."
                    } else {
                        "Spotify Access Forbidden (403): $errorBody"
                    }
                    return Result.failure(IOException(message))
                }

                429 -> {
                    val retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull() ?: (2L * attempts)
                    Log.w(TAG, "Spotify Rate Limit (429). Backing off for ${retryAfterSeconds}s...")
                    delay((retryAfterSeconds * 1000L).coerceIn(1000L, 30_000L))
                }

                else -> {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    Log.e(TAG, "Spotify API Error ${response.code()}: $errorBody")
                    if (attempts >= maxRetries) {
                        return Result.failure(IOException("Spotify API Error ${response.code()}: $errorBody"))
                    }
                    delay(1000L * attempts)
                }
            }
        }

        return Result.failure(IOException("Spotify request exceeded maximum retries ($maxRetries)"))
    }

    // --- Disk Cache Helpers ---

    private inline fun <reified T> readCache(file: File): T? {
        return try {
            if (!file.exists()) return null
            val raw = file.readText(Charsets.UTF_8)
            val envelope = json.decodeFromString<CacheEnvelope<T>>(raw)
            if (System.currentTimeMillis() - envelope.timestamp < CACHE_EXPIRATION_MS) {
                envelope.data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cache file: ${file.name}", e)
            null
        }
    }

    private inline fun <reified T> writeCache(file: File, data: T) {
        try {
            val envelope = CacheEnvelope(timestamp = System.currentTimeMillis(), data = data)
            file.writeText(json.encodeToString(envelope), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write cache file: ${file.name}", e)
        }
    }

    fun clearDiskCache() {
        try {
            cacheDirectory.deleteRecursively()
            cacheDirectory.mkdirs()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing Spotify library cache", e)
        }
    }
}

// --- Domain Model Mappers ---

fun SpotifyTrackDto.toMetadata(): SpotifyTrackMetadata {
    val artistNames = artists.map { it.name }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Unknown Artist" }
    val bestImage = album?.images?.firstOrNull()?.url
    return SpotifyTrackMetadata(
        id = id.orEmpty(),
        title = name.ifBlank { "Untitled" },
        artist = artistNames,
        album = album?.name.orEmpty(),
        albumArtUrl = bestImage,
        isrc = externalIds?.isrc?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
    )
}

fun SpotifyTrackMetadata.toGeneratedTrack(): GeneratedTrack {
    return GeneratedTrack(
        name = title,
        artist = artist,
        artworkUrl = albumArtUrl,
        album = album.takeIf { it.isNotBlank() },
        url = "", // To be resolved to YouTube stream via SpotifyIsrcMatcher
    )
}

fun SpotifyPlaylistItemDto.toSavedPlaylistSummary(): SavedPlaylist {
    val image = images.firstOrNull()?.url
    val count = tracks?.total ?: 0
    return SavedPlaylist(
        id = id.hashCode().toLong(),
        title = name,
        subtitle = "$count tracks • Spotify",
        mode = "spotify",
        tracks = emptyList(),
        createdAtMillis = System.currentTimeMillis(),
        remotePlaylistId = id,
        remoteArtworkUrl = image,
        remoteTrackCount = count,
    )
}
