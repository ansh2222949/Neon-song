package com.neonsong.app.data.canvas

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.music.SyncedVideoStreamBundle
import com.neonsong.app.data.music.YouTubeVideoStream
import com.neonsong.app.data.network.NetworkMonitor
import com.neonsong.app.data.video.SyncedVideoRepository
import com.neonsong.app.playback.PlayableTrack
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class CanvasMatchingTest {

    @Test
    fun testNormalizeForMatch() {
        val raw = "Beyoncé - CRAZY IN LOVE (feat. JAY-Z)"
        val normalized = raw.normalizeForMatch()
        assertThat(normalized).isEqualTo("beyonce crazy in love feat jay z")
    }

    @Test
    fun testSplitArtists() {
        val artists1 = splitArtists("Taylor Swift feat. Post Malone")
        assertThat(artists1).containsExactly("taylor swift", "post malone")

        val artists2 = splitArtists("Daft Punk & Pharrell Williams")
        assertThat(artists2).containsExactly("daft punk", "pharrell williams")

        val artists3 = splitArtists("Marshmello x Khalid")
        assertThat(artists3).containsExactly("marshmello", "khalid")
    }

    @Test
    fun testCanvasArtworkMatches() {
        val canvas = CanvasArtwork(
            url = "https://example.com/loop.mp4",
            title = "Crazy In Love",
            artist = "Beyoncé ft. Jay-Z",
            album = "Dangerously in Love",
            source = CanvasSource.APPLE,
        )

        // Matching with minor punctuation/case variations
        val isMatch = canvas.matches(
            wantTitle = "Crazy in Love",
            wantArtist = "Beyonce",
            wantAlbum = "Dangerously In Love",
        )
        assertThat(isMatch).isTrue()

        // Mismatched title should be rejected
        val wrongTitle = canvas.matches(
            wantTitle = "Halo",
            wantArtist = "Beyonce",
            wantAlbum = "Dangerously In Love",
        )
        assertThat(wrongTitle).isFalse()
    }

    @Test
    fun testTidalCoverUrlFormatting() {
        val uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        val expected = "https://resources.tidal.com/videos/a1b2c3d4/e5f6/7890/abcd/ef1234567890/1280x1280.mp4"
        val url = TidalCanvas.coverUrl(uuid)
        assertThat(url).isEqualTo(expected)

        // Malformed UUID returns null
        assertThat(TidalCanvas.coverUrl("invalid-uuid")).isNull()
    }

    @Test
    fun testNonLatinCanvasMatchingPreservesJapaneseAndHindiScripts() {
        assertThat("ギラギラ".normalizeForMatch()).isEqualTo("ギラギラ")
        assertThat("तुम ही हो".normalizeForMatch()).isEqualTo("तुम ही हो")

        val giraGiraCanvas = CanvasArtwork(
            url = "https://example.com/gira.mp4",
            title = "ギラギラ",
            artist = "Ado",
            album = "狂言",
            source = CanvasSource.APPLE,
        )
        assertThat(
            giraGiraCanvas.matches(
                wantTitle = "ギラギラ",
                wantArtist = "Ado",
                wantAlbum = null,
            ),
        ).isTrue()
        assertThat(
            giraGiraCanvas.matches(
                wantTitle = "うっせぇわ",
                wantArtist = "Ado",
                wantAlbum = null,
            ),
        ).isFalse()
    }

    @Test
    fun testSpotifyCanvasFallsBackToClippedYoutubeMvWhenProvidersEmpty() = runTest {
        val offlineClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val mockNetwork = mockk<NetworkMonitor> {
            every { isOnCellular() } returns false
        }
        val mockVideoRepo = mockk<SyncedVideoRepository>()
        val mvStream = YouTubeVideoStream(
            videoId = "whTcBozYrOo",
            url = "https://googlevideo.example/let_me_battle_480p.mp4",
            width = 854,
            height = 480,
            codec = "avc1.4d401f",
            durationMs = 181_000L,
            requestHeaders = mapOf("User-Agent" to "TestUA"),
        )
        coEvery {
            mockVideoRepo.resolveVideo(
                trackTitle = "Let me battle",
                trackArtist = "9Lana",
                videoId = "whTcBozYrOo",
                targetDurationMs = null,
                trackAlbum = null,
            )
        } returns Result.success(
            SyncedVideoStreamBundle(
                videoId = "whTcBozYrOo",
                defaultStream = mvStream,
                availableStreams = listOf(mvStream),
            ),
        )

        val repo = CanvasRepository(offlineClient, mockNetwork, mockVideoRepo)
        val canvas = repo.canvasFor(
            track = PlayableTrack(
                title = "Let me battle",
                artist = "9Lana",
                videoId = "whTcBozYrOo",
                playbackUrl = "https://music.youtube.com/watch?v=whTcBozYrOo",
            ),
            preferSpotify = true,
        )

        assertThat(canvas).isNotNull()
        assertThat(canvas?.source).isEqualTo(CanvasSource.YOUTUBE_MV)
        assertThat(canvas?.url).isEqualTo("https://googlevideo.example/let_me_battle_480p.mp4")
        assertThat(canvas?.clipStartMs).isEqualTo(25_000L)
        assertThat(canvas?.clipEndMs).isEqualTo(43_000L)
        assertThat(canvas?.requestHeaders).containsEntry("User-Agent", "TestUA")
    }

    @Test
    fun testSpotifyCanvasMvFallbackPrefersHw1080pOverLowResAndAv1OnWifi() = runTest {
        val offlineClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val mockNetwork = mockk<NetworkMonitor> {
            every { isOnCellular() } returns false
        }
        val mockVideoRepo = mockk<SyncedVideoRepository>()
        val s360 = YouTubeVideoStream(
            videoId = "sOiMD45QGLs",
            url = "https://googlevideo.example/gira_360.mp4",
            width = 640,
            height = 360,
            codec = "avc1.4d401e",
            durationMs = 277_000L,
        )
        val s1080Hw = YouTubeVideoStream(
            videoId = "sOiMD45QGLs",
            url = "https://googlevideo.example/gira_1080_h264.mp4",
            width = 1920,
            height = 1080,
            codec = "avc1.640028",
            durationMs = 277_000L,
        )
        val s2160Av1 = YouTubeVideoStream(
            videoId = "sOiMD45QGLs",
            url = "https://googlevideo.example/gira_4k_av1.webm",
            width = 3840,
            height = 2160,
            codec = "av01.0.12M.08",
            durationMs = 277_000L,
        )
        coEvery {
            mockVideoRepo.resolveVideo(
                trackTitle = "ギラギラ",
                trackArtist = "Ado",
                videoId = "sOiMD45QGLs",
                targetDurationMs = null,
                trackAlbum = null,
            )
        } returns Result.success(
            SyncedVideoStreamBundle(
                videoId = "sOiMD45QGLs",
                defaultStream = s1080Hw,
                availableStreams = listOf(s360, s1080Hw, s2160Av1),
            ),
        )

        val repo = CanvasRepository(offlineClient, mockNetwork, mockVideoRepo)
        val canvas = repo.canvasFor(
            track = PlayableTrack(
                title = "ギラギラ",
                artist = "Ado",
                videoId = "sOiMD45QGLs",
                playbackUrl = "https://music.youtube.com/watch?v=sOiMD45QGLs",
            ),
            preferSpotify = true,
        )

        assertThat(canvas?.url).isEqualTo("https://googlevideo.example/gira_1080_h264.mp4")
    }
}
