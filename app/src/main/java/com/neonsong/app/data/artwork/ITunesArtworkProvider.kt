package com.neonsong.app.data.artwork

import android.util.Log
import com.neonsong.app.data.canvas.AppleMusicCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ArtworkPipeline"
private const val CRASH_TAG = "ArtworkCrash"
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@Serializable
private data class ITunesResult(
    val artworkUrl100: String? = null,
    val artworkUrl60: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val trackTimeMillis: Long? = null,
)

@Serializable
private data class ITunesSearchResponse(val results: List<ITunesResult> = emptyList())

/**
 * High-resolution Apple Music / iTunes Store artwork resolver:
 * 1. Queries iTunes Search API with device storefront and desktop browser headers.
 * 2. Multi-artist aware matching (splits collaborations/features so collabs aren't rejected).
 * 3. Master upscale (1200x1200bb) with resilient format & query param handling.
 * 4. Fallback to Apple Music AMP Catalog API for 100% catalog coverage.
 */
@Singleton
class ITunesArtworkProvider @Inject constructor(
    okHttpClient: OkHttpClient,
) {
    private val client = okHttpClient.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val upscalePattern = Regex(
        """/(\d+x\d+[^/]*\.(?:jpg|jpeg|png|webp)|\{w\}x\{h\}[^/]*\.(?:jpg|jpeg|png|webp))(?:\?.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun fetchArtworkUrl(track: String, artist: String): String? = withContext(Dispatchers.IO) {
        // 1. Direct query via iTunes Search API (with device storefront country)
        fetchByTrackAndArtist(track, artist)
            // 2. Cleaned title & artist (strips "(Official Video)", "feat.", etc.)
            ?: run {
                val cleanedTrack = ArtworkNormalizer.cleanTitle(track)
                val cleanedArtist = ArtworkNormalizer.cleanArtist(artist)
                if (cleanedTrack != track || cleanedArtist != artist) {
                    fetchByTrackAndArtist(cleanedTrack, cleanedArtist)
                } else null
            }
            // 3. Fallback: Query Apple Music AMP Catalog API directly using web player guest token
            ?: run {
                AppleMusicCanvas.searchMasterArtworkUrl(client, track, artist)
            }
            ?: run {
                val cleanedTrack = ArtworkNormalizer.cleanTitle(track)
                val cleanedArtist = ArtworkNormalizer.cleanArtist(artist)
                if (cleanedTrack != track || cleanedArtist != artist) {
                    AppleMusicCanvas.searchMasterArtworkUrl(client, cleanedTrack, cleanedArtist)
                } else null
            }
    }

    private suspend fun fetchByTrackAndArtist(track: String, artist: String): String? {
        val query = if (artist.isNotBlank()) "$track $artist" else track
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val countryCode = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT)

        // Try local storefront first (if non-US), then global/US catalog
        if (!countryCode.isNullOrBlank() && countryCode != "US") {
            val localUrl = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=5&country=$countryCode"
            val localHit = queryItunesUrl(localUrl, track, artist)
            if (localHit != null) return localHit
        }

        val globalUrl = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=5"
        return queryItunesUrl(globalUrl, track, artist)
    }

    private suspend fun queryItunesUrl(url: String, track: String, artist: String): String? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            val body = client.newCall(request).awaitSuccessfulBodyOrNull() ?: return null
            val parsed = json.decodeFromString<ITunesSearchResponse>(body)
            val best = parsed.results
                .filter { !it.artworkUrl100.isNullOrBlank() || !it.artworkUrl60.isNullOrBlank() }
                .maxByOrNull { score(it, track, artist) }
                ?.takeIf { isVerified(it, track, artist) } ?: return null
            val raw = best.artworkUrl100 ?: best.artworkUrl60
            return raw?.let { upscale(it) }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            return null
        }
    }

    /** Both title and artist must agree; homonyms and wrong version recordings are strictly rejected. */
    private fun isVerified(candidate: ITunesResult, track: String, artist: String): Boolean {
        if (!titleOk(candidate.trackName.orEmpty(), track)) return false
        if (artist.isNotBlank() && !artistOk(candidate.artistName.orEmpty(), artist)) return false
        return true
    }

    private fun score(candidate: ITunesResult, track: String, artist: String): Int {
        var score = if (candidate.trackName?.equals(track, ignoreCase = true) == true) 3
        else if (titleOk(candidate.trackName.orEmpty(), track)) 1 else 0
        if (artist.isNotBlank() && artistOk(candidate.artistName.orEmpty(), artist)) score += 2
        return score
    }

    private fun titleOk(songTitle: String, title: String): Boolean {
        if (songTitle.isBlank() || title.isBlank()) return false
        if (songTitle.equals(title, ignoreCase = true)) return true
        if (!com.neonsong.app.data.lyrics.LrclibLyricsApi.sameVersion(title, songTitle)) return false
        return com.neonsong.app.data.lyrics.LrclibLyricsApi.titlesMatch(
            com.neonsong.app.data.lyrics.LrclibLyricsApi.cleanTrackTitle(songTitle),
            com.neonsong.app.data.lyrics.LrclibLyricsApi.cleanTrackTitle(title),
        )
    }

    private fun artistOk(songArtist: String, artist: String): Boolean {
        if (artist.isBlank() || songArtist.isBlank()) return false
        val cleanSong = com.neonsong.app.data.lyrics.LrclibLyricsApi.cleanArtistName(songArtist)
        val cleanReq = com.neonsong.app.data.lyrics.LrclibLyricsApi.cleanArtistName(artist)
        if (com.neonsong.app.data.lyrics.LrclibLyricsApi.artistMatches(cleanSong, cleanReq)) return true

        // Multi-artist / collaborator splitting (e.g. "Pritam, Arijit Singh & Amitabh Bhattacharya")
        val splitRegex = Regex("""(?i)\s*(?:,|&|/|\bx\b|\bwith\b|\band\b|\bft\.?\b|\bfeat\.?\b|\bfeaturing\b)\s*""")
        val songArtists = cleanSong.split(splitRegex).map { it.trim() }.filter { it.length >= 2 }
        val reqArtists = cleanReq.split(splitRegex).map { it.trim() }.filter { it.length >= 2 }

        for (sa in songArtists) {
            for (ra in reqArtists) {
                if (sa.equals(ra, ignoreCase = true)) return true
                if (com.neonsong.app.data.lyrics.LrclibLyricsApi.artistMatches(sa, ra)) return true
            }
        }
        return false
    }

    /** …/100x100bb.jpg -> …/1200x1200bb.jpg master artwork */
    private fun upscale(rawUrl: String): String {
        val upscaled = upscalePattern.replace(rawUrl, "/1200x1200bb.jpg")
        return if (upscaled.contains("{w}x{h}")) {
            upscaled.replace("{w}x{h}", "1200x1200")
        } else {
            upscaled
        }
    }
}
