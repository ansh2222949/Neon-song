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
import java.net.URLDecoder
import java.util.Base64
import java.util.Locale

/**
 * Apple Music's motion artwork — animated sleeves exposed on the catalog API as `editorialVideo`.
 */
object AppleMusicCanvas {

    private const val TAG = "AppleMusicCanvas"
    private const val AMP = "https://amp-api.music.apple.com/v1/catalog"
    private val WEB_PLAYER_URLS = listOf(
        "https://music.apple.com/us/new",
        "https://music.apple.com/us/browse",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val storefront: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
    }

    fun search(client: OkHttpClient, title: String, artist: String, album: String?): CanvasArtwork? {
        val bearer = token(client) ?: return null

        val cleanTitle = title.cleanCanvasTitle()
        val cleanArtist = artist.cleanCanvasArtist()
        val primaryArtist = splitArtists(cleanArtist).firstOrNull().orEmpty().ifBlank { cleanArtist }

        // Pass 1: Clean artist + clean title
        searchCatalog(client, bearer, "$primaryArtist $cleanTitle", cleanTitle, cleanArtist, album)?.let { return it }

        // Pass 2: If original title had extra context, try raw title
        if (!cleanTitle.equals(title.trim(), ignoreCase = true)) {
            searchCatalog(client, bearer, "$primaryArtist $title", cleanTitle, cleanArtist, album)?.let { return it }
        }

        return null
    }

    private fun searchCatalog(
        client: OkHttpClient,
        bearer: String,
        term: String,
        title: String,
        artist: String,
        album: String?,
    ): CanvasArtwork? {
        val stores = listOf(storefront, "us").distinct()
        for (store in stores) {
            val url = "$AMP/$store/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("types", "songs")
                .addQueryParameter("limit", "10")
                .addQueryParameter("extend", "editorialVideo")
                .addQueryParameter("include", "albums")
                .build()
                .toString()

            val body = get(client, url, bearer) ?: continue
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: continue
            val hits = root["results"]?.jsonObject
                ?.get("songs")?.jsonObject
                ?.get("data")?.jsonArray
                ?: continue

            val ranked = hits.mapNotNull { hit ->
                val song = hit as? JsonObject ?: return@mapNotNull null
                val s = score(song, title, artist, album) ?: return@mapNotNull null
                s to song
            }.sortedByDescending { it.first }

            for ((hitScore, song) in ranked) {
                if (hitScore < MIN_SCORE) break
                val attributes = song["attributes"]?.jsonObject ?: continue
                val songName = attributes["name"]?.jsonPrimitive?.contentOrNull
                val songArtist = attributes["artistName"]?.jsonPrimitive?.contentOrNull
                val albumName = attributes["albumName"]?.jsonPrimitive?.contentOrNull

                attributes["editorialVideo"]?.jsonObject?.let { video ->
                    motionUrls(video)?.let { assets ->
                        Log.d(TAG, "Inline motion artwork found for '$songName'")
                        return CanvasArtwork(
                            url = assets.primary,
                            fallbackUrl = assets.alternate,
                            tallUrl = assets.tall,
                            title = songName,
                            artist = songArtist,
                            album = albumName,
                            source = CanvasSource.APPLE,
                        )
                    }
                }

                val albumId = albumId(song) ?: continue
                fetchAlbum(client, store, albumId, bearer, songName, songArtist)?.let { return it }
            }
        }
        return null
    }

    fun searchMasterArtworkUrl(client: OkHttpClient, title: String, artist: String): String? {
        val bearer = token(client) ?: return null
        val cleanTitle = title.cleanCanvasTitle()
        val cleanArtist = artist.cleanCanvasArtist()

        val term = buildString {
            if (cleanArtist.isNotBlank() && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
                append(cleanArtist).append(' ')
            }
            append(cleanTitle)
        }

        val url = "$AMP/$storefront/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "5")
            .build()
            .toString()

        val body = get(client, url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hits = root["results"]?.jsonObject
            ?.get("songs")?.jsonObject
            ?.get("data")?.jsonArray
            ?: return null

        for (hit in hits) {
            val song = hit as? JsonObject ?: continue
            val hitScore = score(song, cleanTitle, cleanArtist, null) ?: continue
            if (hitScore < MIN_SCORE) continue
            val attributes = song["attributes"]?.jsonObject ?: continue
            val artworkObj = attributes["artwork"]?.jsonObject ?: continue
            val rawArtUrl = artworkObj["url"]?.jsonPrimitive?.contentOrNull ?: continue
            if (rawArtUrl.isNotBlank()) {
                return rawArtUrl.replace("{w}x{h}", "1200x1200")
            }
        }
        return null
    }

    fun searchAlbum(client: OkHttpClient, album: String, artist: String): CanvasArtwork? {
        val bearer = token(client) ?: return null
        val cleanAlbum = album.cleanCanvasTitle()
        val cleanArtist = artist.cleanCanvasArtist()
        val term = if (cleanAlbum.contains(cleanArtist, ignoreCase = true)) cleanAlbum else "$cleanArtist $cleanAlbum"

        val url = "$AMP/$storefront/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("types", "albums")
            .addQueryParameter("limit", "10")
            .addQueryParameter("extend", "editorialVideo")
            .build()
            .toString()

        val body = get(client, url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hits = root["results"]?.jsonObject
            ?.get("albums")?.jsonObject
            ?.get("data")?.jsonArray
            ?: return null

        val ranked = hits.mapNotNull { hit ->
            val record = hit as? JsonObject ?: return@mapNotNull null
            val s = score(record, cleanAlbum, cleanArtist, cleanAlbum, albumIsSelf = true) ?: return@mapNotNull null
            s to record
        }.sortedByDescending { it.first }

        for ((hitScore, record) in ranked) {
            if (hitScore < MIN_SCORE) break
            val attributes = record["attributes"]?.jsonObject ?: continue
            val name = attributes["name"]?.jsonPrimitive?.contentOrNull
            if (name != null && isCompilation(name)) continue
            val video = attributes["editorialVideo"]?.jsonObject ?: continue
            val assets = motionUrls(video) ?: continue

            Log.d(TAG, "Motion artwork found for album '$name'")
            return CanvasArtwork(
                url = assets.primary,
                fallbackUrl = assets.alternate,
                tallUrl = assets.tall,
                // Keep title null on album-level canvas so song-level matches() never rejects it
                title = null,
                artist = attributes["artistName"]?.jsonPrimitive?.contentOrNull,
                album = name,
                source = CanvasSource.APPLE,
            )
        }
        return null
    }

    private const val MIN_SCORE = 12

    private fun score(
        song: JsonObject,
        title: String,
        artist: String,
        album: String?,
        albumIsSelf: Boolean = false,
    ): Int? {
        val attributes = song["attributes"]?.jsonObject ?: return null
        val hitName = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hitArtist = attributes["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hitAlbum = if (albumIsSelf) {
            hitName
        } else {
            attributes["albumName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }

        if (isCompilation(hitName) || isCompilation(hitAlbum)) return null

        val wanted = splitArtists(artist)
        val credited = splitArtists(hitArtist)
        if (wanted.isNotEmpty() && credited.isNotEmpty()) {
            val anyArtistMatch = wanted.any { want ->
                credited.any { got -> got == want || got.contains(want) || want.contains(got) }
            }
            if (!anyArtistMatch) return null
        }

        var score = 10

        val wantTitle = title.cleanCanvasTitle().normalizeForMatch()
        val gotTitle = hitName.cleanCanvasTitle().normalizeForMatch()
        score += when {
            gotTitle == wantTitle -> 16
            gotTitle.contains(wantTitle) || wantTitle.contains(gotTitle) -> 9
            else -> -10
        }

        if (!album.isNullOrBlank() && hitAlbum.isNotBlank()) {
            val wantAlbum = album.cleanCanvasTitle().normalizeForMatch()
            val gotAlbum = hitAlbum.cleanCanvasTitle().normalizeForMatch()
            score += when {
                gotAlbum == wantAlbum -> 15
                gotAlbum.contains(wantAlbum) || wantAlbum.contains(gotAlbum) -> 8
                else -> 0
            }
        }

        for (word in EDITION_WORDS) {
            val inWanted = title.contains(word, ignoreCase = true)
            val inHit = hitName.contains(word, ignoreCase = true)
            if (inWanted && inHit) score += 5 else if (inHit) score -= 2
        }

        return score
    }

    private val EDITION_WORDS =
        listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")

    private fun isCompilation(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return COMPILATION_MARKERS.any { lower.contains(it) }
    }

    private val COMPILATION_MARKERS = listOf(
        "playlist", "set list", "essentials", "dj mix", "mixed",
        "apple music", "today's hits", "session",
    )

    private fun albumId(song: JsonObject): String? {
        val fromRelationship = song["relationships"]?.jsonObject
            ?.get("albums")?.jsonObject
            ?.get("data")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        if (fromRelationship != null) return fromRelationship.takeUnless { it.startsWith("pl.") }

        val url = song["attributes"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: return null
        return url.substringAfter("/album/", "")
            .substringBefore("?")
            .substringAfterLast("/")
            .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    private fun fetchAlbum(
        client: OkHttpClient,
        store: String,
        albumId: String,
        bearer: String,
        songTitle: String?,
        songArtist: String?,
    ): CanvasArtwork? {
        val url = "$AMP/$store/albums/$albumId".toHttpUrl().newBuilder()
            .addQueryParameter("extend", "editorialVideo")
            .build()
            .toString()

        val body = get(client, url, bearer) ?: return null
        val album = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull() ?: return null

        val attributes = album["attributes"]?.jsonObject ?: return null
        val albumName = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (isCompilation(albumName)) return null

        val video = attributes["editorialVideo"]?.jsonObject ?: return null
        val assets = motionUrls(video) ?: return null

        Log.d(TAG, "Motion artwork found on album '$albumName' ($albumId)")
        return CanvasArtwork(
            url = assets.primary,
            fallbackUrl = assets.alternate,
            tallUrl = assets.tall,
            title = songTitle,
            artist = songArtist ?: attributes["artistName"]?.jsonPrimitive?.contentOrNull,
            album = albumName,
            source = CanvasSource.APPLE,
        )
    }

    private data class MotionUrls(
        val primary: String,
        val alternate: String?,
        val tall: String?,
    )

    private fun motionUrls(video: JsonObject): MotionUrls? {
        fun link(key: String): String? = video[key]?.jsonObject?.let { asset ->
            asset["video"]?.jsonPrimitive?.contentOrNull
                ?: asset["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["url"]?.jsonPrimitive?.contentOrNull
        }?.takeIf { it.isNotBlank() }

        val square = link("motionDetailSquare") ?: link("motionSquareVideo1x1")
        val raw = link("motionDetailRaw")
        val tall = link("motionDetailTall") ?: link("motionTallVideo3x4")
        val primary = square ?: raw ?: tall ?: return null
        val alternate = listOfNotNull(square, raw, tall).firstOrNull { it != primary }
        return MotionUrls(primary, alternate, tall)
    }

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAtMs = 0L
    @Volatile private var retryTokenAfterMs = 0L
    private val rejected = mutableSetOf<String>()

    private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}""")
    private val SCRIPT_REGEX = Regex("""/assets/[A-Za-z0-9_~.-]+\.js""")

    @Synchronized
    private fun token(client: OkHttpClient): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAtMs - 60_000) return it }
        if (now < retryTokenAfterMs) return null

        for (pageUrl in WEB_PLAYER_URLS) {
            val html = canvasGet(client, pageUrl, mapOf("User-Agent" to CANVAS_UA)) ?: continue

            // 1. Check if JWT is embedded directly in HTML / meta environment config
            val decodedHtml = runCatching { URLDecoder.decode(html, "UTF-8") }.getOrDefault(html)
            findValidJwt(decodedHtml, now)?.let { (jwt, exp) ->
                cachedToken = jwt
                tokenExpiresAtMs = exp
                return jwt
            }

            // 2. Scrape JS bundles referenced by the page
            val scripts = SCRIPT_REGEX.findAll(html)
                .map(MatchResult::value)
                .distinct()
                .sortedByDescending { it.contains("index") }
                .take(6)
                .toList()

            for (path in scripts) {
                val script = canvasGet(client, "https://music.apple.com$path", mapOf("User-Agent" to CANVAS_UA))
                    ?: continue
                findValidJwt(script, now)?.let { (jwt, expiresAt) ->
                    cachedToken = jwt
                    tokenExpiresAtMs = expiresAt
                    Log.d(TAG, "Apple Music web player token good until ${java.util.Date(expiresAt)}")
                    return jwt
                }
            }
        }

        Log.w(TAG, "No usable Apple Music web player token found; backing off for 20s")
        retryTokenAfterMs = now + TOKEN_RETRY_MS
        return null
    }

    private fun findValidJwt(source: String, now: Long): Pair<String, Long>? {
        val candidates = JWT_REGEX.findAll(source)
            .map(MatchResult::value)
            .distinct()
            .filter { it !in rejected }
            .mapNotNull { jwt -> expiry(jwt)?.let { jwt to it } }
            .filter { it.second > now }
            .toList()
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { isWebPlayerToken(it.first) } ?: candidates.first()
    }

    // 20-second backoff instead of 30 minutes so transient network drops recover immediately
    private const val TOKEN_RETRY_MS = 20_000L

    private fun get(client: OkHttpClient, url: String, bearer: String): String? {
        val request = Request.Builder().url(url).apply {
            authHeaders(bearer).forEach { (name, value) -> header(name, value) }
        }.build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.string()
                    response.code == 401 || response.code == 403 -> {
                        Log.w(TAG, "Apple Music token rejected (${response.code}); will re-scrape")
                        synchronized(this) {
                            rejected += bearer
                            if (cachedToken == bearer) {
                                cachedToken = null
                                tokenExpiresAtMs = 0L
                                retryTokenAfterMs = 0L
                            }
                        }
                        null
                    }
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun isWebPlayerToken(jwt: String): Boolean = runCatching {
        val parts = jwt.split(".")
        val header = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        header.contains("WebPlayKid") || payload.contains("AMPWebPlay") || payload.contains("music.apple.com")
    }.getOrDefault(false)

    private fun expiry(jwt: String): Long? = runCatching {
        val payload = String(
            Base64.getUrlDecoder().decode(jwt.split(".")[1]),
            Charsets.UTF_8,
        )
        val seconds = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)
        seconds?.toLong()?.times(1000)
    }.getOrDefault(null)

    private fun authHeaders(bearer: String) = mapOf(
        "Authorization" to "Bearer $bearer",
        "Origin" to "https://music.apple.com",
        "Referer" to "https://music.apple.com/",
        "User-Agent" to CANVAS_UA,
    )
}
