package com.neonsong.app.data.canvas

import android.util.Log
import com.neonsong.app.data.network.NetworkMonitor
import com.neonsong.app.playback.PlayableTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the looping video that belongs behind a track's or a release's cover
 * art (Apple motion artwork, Tidal video cover, or Community Canvas manifest).
 */
@Singleton
class CanvasRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor,
) {

    private class Entry(
        val artwork: CanvasArtwork?,
        val withAlbum: Boolean,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > CACHE_SIZE
    }

    private val lock = Mutex()

    suspend fun canvasFor(track: PlayableTrack, cellularAllowed: Boolean = true): CanvasArtwork? {
        val rawTitle = track.title.cleaned()
        val rawArtist = track.artist.cleaned()
        if (rawTitle.isBlank() || rawArtist.isBlank()) return null

        // If YouTube title has "Artist - Song Title", strip the artist prefix
        val title = stripLeadingArtistPrefix(rawTitle, rawArtist)
        val album = track.album?.cleaned()?.takeIf { it.isNotBlank() }
        val id = track.videoId?.takeIf { it.isNotBlank() } ?: "$rawArtist-$title"

        return canvasFor(
            title = title,
            artist = rawArtist,
            album = album,
            trackId = id,
            cellularAllowed = cellularAllowed,
        )
    }

    suspend fun canvasFor(
        title: String,
        artist: String,
        album: String?,
        trackId: String? = null,
        cellularAllowed: Boolean = true,
    ): CanvasArtwork? {
        val cleanTitle = stripLeadingArtistPrefix(title.cleaned(), artist.cleaned())
        val cleanArtist = artist.cleaned()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        val id = trackId ?: "$cleanArtist-$cleanTitle"
        val key = "song|$id"

        val now = System.currentTimeMillis()
        val cachedAnswer = synchronized(cache) { cache[key] }
        if (cachedAnswer != null && cachedAnswer.reusable(album != null, now)) {
            return cachedAnswer.artwork
        }

        if (!cellularAllowed && networkMonitor.isOnCellular()) {
            Log.d(TAG, "Skipping online canvas fetch on cellular connection (cellularAllowed=false)")
            return cachedAnswer?.artwork
        }

        return resolve(key, album != null) {
            raceSources(
                cleanTitle = cleanTitle,
                cleanArtist = cleanArtist,
                album = album,
                appleBlock = { AppleMusicCanvas.search(okHttpClient, cleanTitle, cleanArtist, album) },
                tidalBlock = { TidalCanvas.search(okHttpClient, cleanTitle, cleanArtist, album) },
                communityBlock = { CommunityCanvas.search(okHttpClient, cleanTitle, cleanArtist, album) },
            )
        }
    }

    fun cached(track: PlayableTrack): CanvasArtwork? {
        val cleanTitle = stripLeadingArtistPrefix(track.title.cleaned(), track.artist.cleaned())
        val id = track.videoId?.takeIf { it.isNotBlank() } ?: "${track.artist.cleaned()}-$cleanTitle"
        val key = "song|$id"
        return synchronized(cache) { cache[key]?.artwork }
    }

    suspend fun canvasForAlbum(
        album: String,
        artist: String,
        cellularAllowed: Boolean = true,
    ): CanvasArtwork? {
        val name = album.cleaned()
        val credit = artist.cleaned()
        if (name.isBlank() || credit.isBlank()) return null

        val key = "album|$name|$credit"
        val now = System.currentTimeMillis()
        val cachedAnswer = synchronized(cache) { cache[key] }
        if (cachedAnswer != null && cachedAnswer.reusable(withAlbum = true, now = now)) {
            return cachedAnswer.artwork
        }

        if (!cellularAllowed && networkMonitor.isOnCellular()) {
            return null
        }

        return resolve(key, withAlbum = true) {
            raceSources(
                cleanTitle = name,
                cleanArtist = credit,
                album = name,
                appleBlock = { AppleMusicCanvas.searchAlbum(okHttpClient, name, credit) },
                tidalBlock = { TidalCanvas.searchAlbum(okHttpClient, name, credit) },
                communityBlock = { CommunityCanvas.search(okHttpClient, name, credit, name) },
            )
        }
    }

    private suspend fun raceSources(
        cleanTitle: String,
        cleanArtist: String,
        album: String?,
        appleBlock: suspend () -> CanvasArtwork?,
        tidalBlock: suspend () -> CanvasArtwork?,
        communityBlock: suspend () -> CanvasArtwork?,
    ): CanvasArtwork? = coroutineScope {
        val appleDeferred = async(Dispatchers.IO) {
            runCatching { withTimeoutOrNull(5_500L) { appleBlock() } }
                .getOrNull()
                ?.takeIf { it.matches(cleanTitle, cleanArtist, album) }
        }
        val tidalDeferred = async(Dispatchers.IO) {
            runCatching { withTimeoutOrNull(4_000L) { tidalBlock() } }
                .getOrNull()
                ?.takeIf { it.matches(cleanTitle, cleanArtist, album) }
        }
        val communityDeferred = async(Dispatchers.IO) {
            runCatching { withTimeoutOrNull(4_000L) { communityBlock() } }
                .getOrNull()
                ?.takeIf { it.matches(cleanTitle, cleanArtist, album) }
        }

        val appleHit = appleDeferred.await()
        if (appleHit != null) {
            tidalDeferred.cancel()
            communityDeferred.cancel()
            return@coroutineScope appleHit
        }

        val tidalHit = tidalDeferred.await()
        if (tidalHit != null) {
            communityDeferred.cancel()
            return@coroutineScope tidalHit
        }

        communityDeferred.await()
    }

    private suspend fun resolve(
        key: String,
        withAlbum: Boolean,
        lookUp: suspend () -> CanvasArtwork?,
    ): CanvasArtwork? = lock.withLock {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[key]?.let { if (it.reusable(withAlbum, now)) return@withLock it.artwork }
        }
        val found = withContext(Dispatchers.IO) { lookUp() }
        synchronized(cache) { cache[key] = Entry(found, withAlbum, System.currentTimeMillis()) }
        found
    }

    private fun Entry.reusable(withAlbum: Boolean, now: Long): Boolean {
        if (artwork != null) return true
        // Misses expire after 45 seconds so temporary network stalls can recover
        if (now - timestampMs > MISS_TTL_MS) return false
        return this.withAlbum || !withAlbum
    }

    private fun stripLeadingArtistPrefix(title: String, artist: String): String {
        val primaryArtist = splitArtists(artist).firstOrNull().orEmpty()
        for (sep in listOf(" - ", " – ", " — ")) {
            if (title.contains(sep)) {
                val before = title.substringBefore(sep).trim()
                val after = title.substringAfter(sep).trim()
                val normBefore = before.normalizeForMatch()
                if (after.isNotBlank() && primaryArtist.isNotBlank() &&
                    (normBefore == primaryArtist || normBefore.contains(primaryArtist) || primaryArtist.contains(normBefore))
                ) {
                    return after
                }
            }
        }
        return title
    }

    private fun String.cleaned(): String = cleanCanvasTitle()
        .replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this.trim() }

    private companion object {
        private const val TAG = "CanvasRepository"
        private const val CACHE_SIZE = 64
        private const val MISS_TTL_MS = 45_000L

        private val NOISE = Regex(
            """\((?:from|official|lyrical|video|audio|visualizer)[^)]*\)|\[[^]]*]|""" +
                """\b(?:official (?:video|audio|music video|lyric video)|lyrical|full song|4k video|visualizer)\b""",
            RegexOption.IGNORE_CASE,
        )
    }
}
