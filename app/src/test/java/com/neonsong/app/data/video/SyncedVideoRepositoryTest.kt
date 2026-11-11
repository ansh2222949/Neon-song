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
            height = 720,
            resolution = "720p",
            bitrate = 1_500_000,
        )
        val stream1080 = YouTubeVideoStream(
            videoId = videoId,
            url = "https://googlevideo.example/video_1080p",
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

        // First resolve: queries extractor
        val result1 = repository.resolveVideo("Midnight City", "M83", "vid_abc")
        assertThat(result1.isSuccess).isTrue()
        assertThat(result1.getOrNull()).isEqualTo(bundle)
        coVerify(exactly = 1) { mockExtractor.resolveVideoStreamBundle("vid_abc") }

        // Second resolve: hits in-memory cache, extractor is NOT called again
        val result2 = repository.resolveVideo("Midnight City", "M83", "vid_abc")
        assertThat(result2.isSuccess).isTrue()
        assertThat(result2.getOrNull()).isEqualTo(bundle)
        coVerify(exactly = 1) { mockExtractor.resolveVideoStreamBundle("vid_abc") }

        // InnerTube search was not required because direct videoId was provided
        coVerify(exactly = 0) { mockInnerTube.searchSongs(any(), any(), any()) }
    }

    @Test
    fun resolveVideoWithoutVideoIdFallsBackToInnerTubeSearch() = runTest {
        val searchTrack = YouTubeMusicTrack(
            videoId = "searched_vid_123",
            title = "Starboy",
            artist = "The Weeknd",
            artworkUrl = "https://example.com/art.jpg",
        )
        coEvery { mockInnerTube.searchSongs("Starboy The Weeknd", limit = 5) } returns listOf(searchTrack)

        val bundle = sampleBundle("searched_vid_123")
        coEvery { mockExtractor.resolveVideoStreamBundle("searched_vid_123") } returns bundle

        val result = repository.resolveVideo("Starboy", "The Weeknd", null)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.videoId).isEqualTo("searched_vid_123")

        coVerify(exactly = 1) { mockInnerTube.searchSongs("Starboy The Weeknd", limit = 5) }
        coVerify(exactly = 1) { mockExtractor.resolveVideoStreamBundle("searched_vid_123") }
    }
}
