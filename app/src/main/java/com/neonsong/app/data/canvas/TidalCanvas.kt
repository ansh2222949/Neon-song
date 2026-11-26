package com.neonsong.app.data.canvas

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Tidal's video cover — square 1280x1280 looping MP4 clips.
 */
object TidalCanvas {

    private const val TAG = "TidalCanvas"
    private const val SEARCH = "https://api.tidal.com/v1/search"
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"
    private const val AUTH_COOLDOWN_MS = 30L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var authBlockedUntilMs: Long = 0L

    private val countryCode: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"
    }

    fun search(client: OkHttpClient, title: String, artist: String, album: String?): CanvasArtwork? {
        if (System.currentTimeMillis() < authBlockedUntilMs) return null
        val cleanTitle = title.cleanCanvasTitle()
        val cleanArtist = artist.cleanCanvasArtist()
        val primaryArtist = splitArtists(cleanArtist).firstOrNull().orEmpty().ifBlank { cleanArtist }

        // 1. Search with clean primary artist + clean title (avoids YouTube album metadata polluting Tidal search)
        queryTracks(client, "$primaryArtist $cleanTitle", cleanTitle, cleanArtist)?.let { return it }

        if (System.currentTimeMillis() < authBlockedUntilMs) return null

        // 2. Fallback with album if provided and distinct
        if (!album.isNullOrBlank()) {
            val cleanAlbum = album.cleanCanvasTitle()
            queryTracks(client, "$primaryArtist $cleanTitle $cleanAlbum", cleanTitle, cleanArtist)?.let { return it }
        }

        return null
    }

    private fun tidalGet(client: OkHttpClient, url: String): String? {
        val now = System.currentTimeMillis()
        if (now < authBlockedUntilMs) return null
        val request = Request.Builder()
            .url(url)
            .header("X-Tidal-Token", EMBED_TOKEN)
            .header("User-Agent", CANVAS_UA)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.string()
                    response.code == 401 || response.code == 403 -> {
                        authBlockedUntilMs = now + AUTH_COOLDOWN_MS
                        Log.w(TAG, "Tidal search unauthorized (${response.code}); cooling down for 30m")
                        null
                    }
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun queryTracks(
        client: OkHttpClient,
        query: String,
        wantTitle: String,
        wantArtist: String,
    ): CanvasArtwork? {
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", "10")
            .addQueryParameter("types", "TRACKS")
            .addQueryParameter("countryCode", countryCode)
            .build()
            .toString()

        val body = tidalGet(client, url)
            ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null

        val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track["title"]?.jsonPrimitive?.contentOrNull ?: continue

            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()

            if (!isMatch(trackTitle, artists, wantTitle, wantArtist)) continue

            val albumObj = track["album"]?.jsonObject
            val videoCover = albumObj?.get("videoCover")?.jsonPrimitive?.contentOrNull
            if (videoCover.isNullOrBlank()) continue
            val videoUrl = coverUrl(videoCover) ?: continue

            Log.d(TAG, "Video cover found for '$trackTitle' by ${artists.joinToString()}")
            return CanvasArtwork(
                url = videoUrl,
                title = trackTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = albumObj["title"]?.jsonPrimitive?.contentOrNull,
                source = CanvasSource.TIDAL,
            )
        }
        return null
    }

    fun searchAlbum(client: OkHttpClient, album: String, artist: String): CanvasArtwork? {
        val cleanAlbum = album.cleanCanvasTitle()
        val cleanArtist = artist.cleanCanvasArtist()
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", "$cleanAlbum $cleanArtist")
            .addQueryParameter("limit", "10")
            .addQueryParameter("types", "ALBUMS")
            .addQueryParameter("countryCode", countryCode)
            .build()
            .toString()

        val body = tidalGet(client, url)
            ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val items = root["albums"]?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record["title"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()

            if (!isMatch(recordTitle, artists, cleanAlbum, cleanArtist)) continue

            val videoCover = record["videoCover"]?.jsonPrimitive?.contentOrNull
            if (videoCover.isNullOrBlank()) continue
            val videoUrl = coverUrl(videoCover) ?: continue

            Log.d(TAG, "Video cover found for album '$recordTitle' by ${artists.joinToString()}")
            return CanvasArtwork(
                url = videoUrl,
                title = null,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
                source = CanvasSource.TIDAL,
            )
        }
        return null
    }

    private fun isMatch(
        gotName: String,
        gotArtists: List<String>,
        wantName: String,
        wantArtist: String,
    ): Boolean {
        val normGot = gotName.cleanCanvasTitle().normalizeForMatch()
        val normWant = wantName.cleanCanvasTitle().normalizeForMatch()
        val titleOk = normGot == normWant || normGot.contains(normWant) || normWant.contains(normGot)
        if (!titleOk) return false

        val wanted = splitArtists(wantArtist)
        val credited = gotArtists.flatMap { splitArtists(it) }
        if (wanted.isEmpty() || credited.isEmpty()) return true
        return wanted.any { want -> credited.any { got -> got == want || got.contains(want) || want.contains(got) } }
    }

    internal fun coverUrl(id: String): String? {
        val parts = id.split("-")
        if (parts.size != 5) return null
        return "https://resources.tidal.com/videos/${parts.joinToString("/")}/1280x1280.mp4"
    }
}
