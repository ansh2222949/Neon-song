package com.neonsong.app.data.canvas

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale

/**
 * Which provider a motion artwork clip came from.
 */
enum class CanvasSource {
    APPLE,
    TIDAL,
    COMMUNITY,
}

/**
 * A looping video that stands in for a track's cover art (Apple motion artwork,
 * Tidal video cover, or Community Canvas manifest).
 *
 * [url] is the primary stream/file to mount; [fallbackUrl] is tried if that fails.
 */
data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val tallUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.COMMUNITY,
) {
    /**
     * Whether this clip really belongs to the track we asked about.
     * Strips YouTube/streaming noise so "(Official Audio)", "- Topic", or multi-artist
     * credits do not falsely reject valid motion artwork.
     */
    fun matches(wantTitle: String, wantArtist: String, wantAlbum: String?): Boolean {
        val cleanWantTitle = wantTitle.cleanCanvasTitle().normalizeForMatch()
        val cleanGotTitle = title?.cleanCanvasTitle()?.normalizeForMatch().orEmpty()

        val titleOk = title.isNullOrBlank() || cleanWantTitle.isBlank() ||
            cleanGotTitle == cleanWantTitle ||
            cleanGotTitle.contains(cleanWantTitle) ||
            cleanWantTitle.contains(cleanGotTitle)

        val wantArtists = splitArtists(wantArtist.cleanCanvasArtist())
        val gotArtists = splitArtists(artist.orEmpty().cleanCanvasArtist())
        val artistOk = artist.isNullOrBlank() || wantArtists.isEmpty() || gotArtists.isEmpty() ||
            wantArtists.any { want ->
                gotArtists.any { got -> got == want || got.contains(want) || want.contains(got) }
            }

        return titleOk && artistOk
    }
}

private val YT_TITLE_NOISE_REGEX = Regex(
    """[\(\[\{]\s*(?:official\s*(?:music\s*)?(?:audio|video|lyric\s*video|visualizer|hd|4k)|audio|lyrics?|visualizer|full\s*(?:audio|video|song)|from\s+["']?.*?["']?|feat\.?\s+.*?|ft\.?\s+.*?|with\s+.*?|prod\.?\s+.*?)\s*[\)\]\}]""",
    RegexOption.IGNORE_CASE,
)

private val YT_TRAILING_NOISE_REGEX = Regex(
    """\s*[-–—|]\s*(?:official\s*(?:music\s*)?(?:audio|video|lyric\s*video|visualizer)|full\s*(?:audio|video|song)|audio|lyrics?|visualizer|hd|4k)\s*$""",
    RegexOption.IGNORE_CASE,
)

private val ARTIST_NOISE_REGEX = Regex(
    """(?i)\s*(?:-\s*topic\b|vevo\b|official\b)""",
)

internal fun String.cleanCanvasTitle(): String =
    this.replace(YT_TITLE_NOISE_REGEX, "")
        .replace(YT_TRAILING_NOISE_REGEX, "")
        .trim()
        .ifBlank { this.trim() }

internal fun String.cleanCanvasArtist(): String =
    this.replace(ARTIST_NOISE_REGEX, "")
        .trim()
        .ifBlank { this.trim() }

/**
 * Normalizes case, accents and punctuation between platforms.
 */
internal fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Splits credited artists across varying separators (commas, ampersands, "feat.", "ft.", "x", "with").
 */
internal fun splitArtists(raw: String): List<String> =
    raw.cleanCanvasArtist()
        .split(ARTIST_SEPARATORS)
        .map { it.normalizeForMatch() }
        .filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\s*/\\s*|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)

internal const val CANVAS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * A plain GET returning the response body string, or null on non-2xx or failure.
 */
internal fun canvasGet(client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): String? {
    val requestBuilder = Request.Builder()
        .url(url)
        .header("User-Agent", CANVAS_UA)

    headers.forEach { (name, value) ->
        requestBuilder.header(name, value)
    }

    return try {
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.w("CanvasArtwork", "GET $url failed: ${e.message}")
        null
    }
}
