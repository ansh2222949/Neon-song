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
import java.util.Locale

/**
 * Tidal's video cover — square 1280x1280 looping MP4 clips.
 */
object TidalCanvas {

    private const val TAG = "TidalCanvas"
    private const val SEARCH = "https://api.tidal.com/v1/search"
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val countryCode: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"
    }

    fun search(client: OkHttpClient, title: String, artist: String, album: String?): CanvasArtwork? {
        val cleanTitle = title.cleanCanvasTitle()
        val cleanArtist = artist.cleanCanvasArtist()
        val primaryArtist = splitArtists(cleanArtist).firstOrNull().orEmpty().ifBlank { cleanArtist }

        // 1. Search with clean primary artist + clean title (avoids YouTube album metadata polluting Tidal search)
        queryTracks(client, "$primaryArtist $cleanTitle", cleanTitle, cleanArtist)?.let { return it }

        // 2. Fallback with album if provided and distinct
        if (!album.isNullOrBlank()) {
            val cleanAlbum = album.cleanCanvasTitle()
            queryTracks(client, "$primaryArtist $cleanTitle $cleanAlbum", cleanTitle, cleanArtist)?.let { return it }
        }

        return null
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

        val body = canvasGet(client, url, mapOf("X-Tidal-Token" to EMBED_TOKEN, "User-Agent" to CANVAS_UA))
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

        val body = canvasGet(client, url, mapOf("X-Tidal-Token" to EMBED_TOKEN, "User-Agent" to CANVAS_UA))
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
