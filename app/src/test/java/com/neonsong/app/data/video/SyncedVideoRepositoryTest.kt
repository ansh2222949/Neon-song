package com.neonsong.app.data.video

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.music.InnerTubeMusicApi
import com.neonsong.app.data.music.SyncedVideoStreamBundle
import com.neonsong.app.data.music.YouTubeMusicTrack
import com.neonsong.app.data.music.YouTubeStreamExtractor
import com.neonsong.app.data.music.YouTubeVideoStream
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncedVideoRepositoryTest {

    private val mockInnerTube = mockk<InnerTubeMusicApi>()
    private val mockExtractor = mockk<YouTubeStreamExtractor>()
    private val repository = SyncedVideoRepository(mockInnerTube, mockExtractor)

    private fun sampleBundle(videoId: String): SyncedVideoStreamBundle {
        val stream720 = YouTubeVideoStream(
            videoId = videoId,
            url = "https://googlevideo.example/video_720p",
            width = 1280,
            height = 720,
            resolution = "720p",
            bitrate = 1_500_000,
        )
        val stream1080 = YouTubeVideoStream(
            videoId = videoId,
            url = "https://googlevideo.example/video_1080p",
            width = 1920,
            height = 1080,
            resolution = "1080p",
            bitrate = 3_000_000,
        )
        return SyncedVideoStreamBundle(
            videoId = videoId,
            defaultStream = stream720,
            availableStreams = listOf(stream720, stream1080),
        )
    }

    @Test
    fun resolveVideoWithDirectVideoIdCachesAndReturnsBundle() = runTest {
        val bundle = sampleBundle("vid_abc")
        coEvery { mockExtractor.resolveVideoStreamBundle("vid_abc") } returns bundle
        coEvery { mockInnerTube.searchVideos(any(), any()) } returns emptyList()

        // First resolve: queries extractor (and hits cache on second call)
        val result1 = repository.resolveVideo("Midnight City", "M83", "vid_abc")
        assertThat(result1.isSuccess).isTrue()
        assertThat(result1.getOrNull()).isEqualTo(bundle)
        coVerify(exactly = 1) { mockExtractor.resolveVideoStreamBundle("vid_abc") }

        // Second resolve: hits in-memory cache, extractor is NOT called again
        val result2 = repository.resolveVideo("Midnight City", "M83", "vid_abc")
        assertThat(result2.isSuccess).isTrue()
        assertThat(result2.getOrNull()).isEqualTo(bundle)
        coVerify(exactly = 1) { mockExtractor.resolveVideoStreamBundle("vid_abc") }
    }

    @Test
    fun resolveVideoWithoutVideoIdFallsBackToInnerTubeSearch() = runTest {
        val searchTrack = YouTubeMusicTrack(
            videoId = "searched_vid_123",
            title = "Starboy (Official Music Video)",
            artist = "The Weeknd",
            artworkUrl = "https://example.com/art.jpg",
        )
        coEvery { mockInnerTube.searchVideos(any(), limit = 15) } returns listOf(searchTrack)

        val bundle = sampleBundle("searched_vid_123")
        coEvery { mockExtractor.resolveVideoStreamBundle("searched_vid_123") } returns bundle

        val result = repository.resolveVideo("Starboy", "The Weeknd", null)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.videoId).isEqualTo("searched_vid_123")

        coVerify(atLeast = 1) { mockInnerTube.searchVideos("Starboy The Weeknd official music video", limit = 15) }
        coVerify(exactly = 1) { mockExtractor.resolveVideoStreamBundle("searched_vid_123") }
    }

    @Test
    fun trackRequestKeyDistinguishesUpdatedVideoIds() {
        val viewModel = com.neonsong.app.ui.player.SyncedVideoViewModel(repository)
        val initialKey = viewModel.trackRequestKey("Blue Moon", "9Lana", null)
        val resolvedKey = viewModel.trackRequestKey("Blue Moon", "9Lana", "iGRxE549f7I")

        assertThat(initialKey).isNotEqualTo(resolvedKey)
    }

    @Test
    fun resolveVideoRejectsJapaneseRawVocalsCoverAndPicksOfficialMv() = runTest {
        val fanClip = YouTubeMusicTrack(
            videoId = "v3L5G7FP2u8",
            title = "【Ado生歌】クローゼットの中から「ギラギラ」を歌うAdo様",
            artist = "切り抜きチャンネル",
            artworkUrl = null,
            durationSeconds = 276,
        )
        val officialMv = YouTubeMusicTrack(
            videoId = "sOiMD45QGLs",
            title = "【Ado】ギラギラ (Gira Gira)",
            artist = "Ado",
            artworkUrl = null,
            durationSeconds = 277,
        )
        coEvery { mockInnerTube.searchVideos(any(), limit = 15) } returns listOf(fanClip, officialMv)
        coEvery { mockExtractor.resolveVideoStreamBundle("sOiMD45QGLs") } returns sampleBundle("sOiMD45QGLs")

        val result = repository.resolveVideo(
            trackTitle = "ギラギラ",
            trackArtist = "Ado",
            videoId = null,
            targetDurationMs = 276_000L,
        )
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.videoId).isEqualTo("sOiMD45QGLs")
        coVerify(exactly = 0) { mockExtractor.resolveVideoStreamBundle("v3L5G7FP2u8") }
    }
}
