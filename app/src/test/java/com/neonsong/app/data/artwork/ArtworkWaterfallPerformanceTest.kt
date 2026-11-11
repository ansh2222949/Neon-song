package com.neonsong.app.data.artwork

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.db.ArtworkCacheDao
import com.neonsong.app.data.local.db.ArtworkCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkWaterfallPerformanceTest {

    @Test
    fun appleMusicHitAvoidsSecondaryProviderCalls() = runTest {
        val mockDao = mockk<ArtworkCacheDao>(relaxed = true)
        val mockLastFm = mockk<LastFmTrackInfoProvider>(relaxed = true)
        val mockItunes = mockk<ITunesArtworkProvider>()
        val mockInnerTube = mockk<com.neonsong.app.data.music.InnerTubeMusicApi>(relaxed = true)
        val mockHttp = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>(relaxed = true)

        io.mockk.every { mockHttp.newCall(any()) } returns mockCall
        io.mockk.every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onFailure(mockCall, java.io.IOException("Simulated mock failure"))
        }

        coEvery { mockDao.getNewest(any()) } returns emptyList()
        coEvery { mockDao.get(any()) } returns null

        val testUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/cover/1200x1200bb.jpg"
        coEvery { mockItunes.fetchArtworkUrl("Anti-Hero", "Taylor Swift") } returns testUrl

        val repository = ArtworkRepository(
            cacheDao = mockDao,
            lastFm = mockLastFm,
            itunes = mockItunes,
            innerTube = mockInnerTube,
            http = mockHttp,
        )

        repository.resolve("Anti-Hero", "Taylor Swift")

        val key = ArtworkNormalizer.cacheKey("Anti-Hero", "Taylor Swift")
        val resolvedUrl = repository.resolved.value[key]

        assertThat(resolvedUrl).isEqualTo(testUrl)

        // Apple Music provider was called exactly once
        coVerify(exactly = 1) { mockItunes.fetchArtworkUrl("Anti-Hero", "Taylor Swift") }

        // Secondary and fallback providers (InnerTube, OkHttp calls for Tidal/Deezer/Spotify) were NEVER invoked!
        coVerify(exactly = 0) { mockInnerTube.searchSongs(any(), any(), any()) }
        coVerify(exactly = 0) { mockHttp.newCall(match { it.url.host.contains("tidal.com") || it.url.host.contains("deezer.com") }) }
    }

    @Test
    fun appleMusicTimeoutFallsBackToSecondaryProvidersWithoutStalling() = runTest {
        val mockDao = mockk<ArtworkCacheDao>(relaxed = true)
        val mockLastFm = mockk<LastFmTrackInfoProvider>(relaxed = true)
        val mockItunes = mockk<ITunesArtworkProvider>()
        val mockInnerTube = mockk<com.neonsong.app.data.music.InnerTubeMusicApi>(relaxed = true)
        val mockHttp = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>(relaxed = true)

        io.mockk.every { mockHttp.newCall(any()) } returns mockCall
        io.mockk.every { mockCall.enqueue(any()) } answers {
            val callback = firstArg<okhttp3.Callback>()
            callback.onFailure(mockCall, java.io.IOException("Simulated mock failure"))
        }

        coEvery { mockDao.getNewest(any()) } returns emptyList()
        coEvery { mockDao.get(any()) } returns null

        // Simulate slow/hanging Apple Music (e.g. 10 seconds)
        coEvery { mockItunes.fetchArtworkUrl("Slow Track", "Unknown Artist") } coAnswers {
            kotlinx.coroutines.delay(10_000L)
            "https://is1-ssl.mzstatic.com/image/thumb/late.jpg"
        }

        // InnerTube fallback resolves
        val ytFallback = com.neonsong.app.data.music.YouTubeMusicTrack(
            videoId = "fallback_yt_123",
            title = "Slow Track",
            artist = "Unknown Artist",
            artworkUrl = "https://lh3.googleusercontent.com/fallback_yt_art",
        )
        coEvery { mockInnerTube.searchSongs(any(), any(), any()) } returns listOf(ytFallback)

        val repository = ArtworkRepository(
            cacheDao = mockDao,
            lastFm = mockLastFm,
            itunes = mockItunes,
            innerTube = mockInnerTube,
            http = mockHttp,
        ).apply {
            appleMusicTimeoutMs = 150L // Bounded timeout for test
        }

        // Must complete promptly via fallback without waiting for 10 seconds
        repository.resolve("Slow Track", "Unknown Artist")

        val key = ArtworkNormalizer.cacheKey("Slow Track", "Unknown Artist")
        val resolvedUrl = repository.resolved.value[key]

        assertThat(resolvedUrl).isEqualTo("https://lh3.googleusercontent.com/fallback_yt_art=s1200")
        coVerify(atLeast = 1) { mockInnerTube.searchSongs(any(), any(), any()) }
    }
}
