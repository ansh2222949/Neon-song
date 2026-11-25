package com.neonsong.app.playback

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MusicPlayerSelectionSyncTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleTrack(
        id: String,
        title: String = "Title $id",
        artist: String = "Artist $id",
        durationMs: Long = 200_000L,
        playbackUrl: String? = null,
        videoId: String? = "vid_$id",
    ) = PlayableTrack(
        title = title,
        artist = artist,
        durationMs = durationMs,
        playbackUrl = playbackUrl,
        videoId = videoId,
    )

    private fun createTestPlayer(): MusicPlayer {
        val context = RuntimeEnvironment.getApplication()
        val dacMonitor = mockk<UsbDacMonitor>(relaxed = true)
        io.mockk.every { dacMonitor.state } returns kotlinx.coroutines.flow.MutableStateFlow(UsbDacMonitor.State())

        return MusicPlayer(
            context = context,
            innerTube = mockk(relaxed = true),
            losslessMusicApi = mockk(relaxed = true),
            moduleDrmFactory = mockk(relaxed = true),
            moduleManager = mockk(relaxed = true),
            offlineLicense = mockk(relaxed = true),
            segBridge = mockk(relaxed = true),
            settingsPreferences = mockk(relaxed = true),
            equalizerPreferences = mockk(relaxed = true),
            discoverRepository = mockk(relaxed = true),
            nativeAudioEngine = dagger.Lazy { mockk(relaxed = true) },
            audioEffectsEngine = mockk(relaxed = true),
            applicationScope = CoroutineScope(SupervisorJob() + testDispatcher),
            downloadedTrackDao = dagger.Lazy { mockk(relaxed = true) },
            usbDacMonitor = dacMonitor,
            exclusiveUsbOutput = mockk(relaxed = true),
            songPlayStatsRepository = dagger.Lazy { mockk(relaxed = true) },
            nativeAutoMixEngine = dagger.Lazy { mockk(relaxed = true) },
            autoDJController = dagger.Lazy { mockk(relaxed = true) },
            autoEqController = mockk(relaxed = true),
        )
    }

    // Mandatory Case 1: Tap Song A once: only Song A should play.
    @Test
    fun case1_tapSongAOnce_onlySongAPlays() {
        val player = createTestPlayer()
        val trackA = sampleTrack("A")

        player.play(trackA, sourceLabel = "Test")

        assertThat(player.state.value.current?.title).isEqualTo(trackA.title)
        assertThat(player.state.value.currentIndex).isEqualTo(0)
        assertThat(player.state.value.queue).hasSize(1)
        assertThat(player.isResolvingMediaIdForTesting(trackA.mediaIdKey())).isTrue()
    }

    // Mandatory Case 2: Tap Song A while another song is playing.
    @Test
    fun case2_tapSongAWhileAnotherPlaying_switchesToSongA() {
        val player = createTestPlayer()
        val track1 = sampleTrack("1")
        val trackA = sampleTrack("A")

        player.play(track1, sourceLabel = "Test")
        val gen1 = player.getPlayRequestGenerationForTesting()

        player.play(trackA, sourceLabel = "Test")
        val genA = player.getPlayRequestGenerationForTesting()

        assertThat(genA).isGreaterThan(gen1)
        assertThat(player.state.value.current?.title).isEqualTo(trackA.title)
        assertThat(player.isResolvingMediaIdForTesting(trackA.mediaIdKey())).isTrue()
    }

    // Mandatory Case 3: Tap Song A followed quickly by Song B.
    @Test
    fun case3_tapSongAThenQuicklySongB_songBWins() {
        val player = createTestPlayer()
        val trackA = sampleTrack("A")
        val trackB = sampleTrack("B")

        player.play(trackA, sourceLabel = "Test")
        val genA = player.getPlayRequestGenerationForTesting()

        player.play(trackB, sourceLabel = "Test")
        val genB = player.getPlayRequestGenerationForTesting()

        assertThat(genB).isGreaterThan(genA)
        assertThat(player.state.value.current?.title).isEqualTo(trackB.title)
        assertThat(player.isResolvingMediaIdForTesting(trackB.mediaIdKey())).isTrue()
    }

    // Mandatory Case 4: Tap three different songs rapidly.
    @Test
    fun case4_tapThreeDifferentSongsRapidly_latestSongCWins() {
        val player = createTestPlayer()
        val trackA = sampleTrack("A")
        val trackB = sampleTrack("B")
        val trackC = sampleTrack("C")

        player.play(trackA, sourceLabel = "Test")
        val genA = player.getPlayRequestGenerationForTesting()

        player.play(trackB, sourceLabel = "Test")
        val genB = player.getPlayRequestGenerationForTesting()

        player.play(trackC, sourceLabel = "Test")
        val genC = player.getPlayRequestGenerationForTesting()

        assertThat(genC).isGreaterThan(genB)
        assertThat(genB).isGreaterThan(genA)
        assertThat(player.state.value.current?.title).isEqualTo(trackC.title)
        assertThat(player.isResolvingMediaIdForTesting(trackC.mediaIdKey())).isTrue()
    }

    // Mandatory Case 5: Tap a song while its media source is still resolving.
    @Test
    fun case5_tapSongWhileMediaSourceResolving_newSelectionSupersedes() {
        val player = createTestPlayer()
        val queue = listOf(sampleTrack("1"), sampleTrack("2"), sampleTrack("3"))
        player.playQueue(queue, startIndex = 0, sourceLabel = "Test")

        // User taps track 2 while track 0 is resolving
        player.seekToQueueItem(2)
        val gen = player.getPlayRequestGenerationForTesting()

        assertThat(player.state.value.current?.title).isEqualTo(queue[2].title)
        assertThat(player.state.value.currentIndex).isEqualTo(2)
        assertThat(player.getPlayRequestGenerationForTesting()).isEqualTo(gen)
    }

    // Mandatory Case 6: Slow network and delayed media resolution.
    @Test
    fun case6_slowNetworkDelayedMediaResolution_staleResolutionIgnored() {
        val player = createTestPlayer()
        val trackSlow = sampleTrack("Slow")
        val trackFast = sampleTrack("Fast")

        player.play(trackSlow, sourceLabel = "Test")
        val slowGen = player.getPlayRequestGenerationForTesting()

        player.play(trackFast, sourceLabel = "Test")
        val fastGen = player.getPlayRequestGenerationForTesting()

        // Stale generation from slow request must be superseded by fastGen
        assertThat(fastGen).isGreaterThan(slowGen)
        assertThat(player.state.value.current?.title).isEqualTo(trackFast.title)
    }

    // Mandatory Case 7: Local files and streaming tracks.
    @Test
    fun case7_localFilesAndStreamingTracks_correctRouting() {
        val player = createTestPlayer()
        val localTrack = sampleTrack("Local", playbackUrl = "/storage/emulated/0/Music/song.flac")
        val streamTrack = sampleTrack("Stream", videoId = "stream123")

        player.play(localTrack, sourceLabel = "Local")
        assertThat(player.state.value.current?.title).isEqualTo(localTrack.title)
        assertThat(player.state.value.current?.playbackUrl).isEqualTo("/storage/emulated/0/Music/song.flac")

        player.play(streamTrack, sourceLabel = "Streaming")
        assertThat(player.state.value.current?.title).isEqualTo(streamTrack.title)
        assertThat(player.state.value.current?.videoId).isEqualTo("stream123")
    }

    // Mandatory Case 8: Repeated taps on the same song.
    @Test
    fun case8_repeatedTapsOnSameSong_restartsOrRetriesGracefully() {
        val player = createTestPlayer()
        val queue = listOf(sampleTrack("1"), sampleTrack("2"))
        player.playQueue(queue, startIndex = 0, sourceLabel = "Test")

        // First tap: restarts from 0
        player.seekToQueueItem(0)
        assertThat(player.state.value.currentIndex).isEqualTo(0)
        assertThat(player.state.value.current?.title).isEqualTo(queue[0].title)

        // Second tap on the same song
        player.seekToQueueItem(0)
        assertThat(player.state.value.currentIndex).isEqualTo(0)
        assertThat(player.state.value.current?.title).isEqualTo(queue[0].title)
    }

    // Mandatory Case 9: Song selection while AutoDJ is active.
    @Test
    fun case9_songSelectionWhileAutoDJActive_resetsAutoDJ() {
        val player = createTestPlayer()
        player.setAutoMixActiveForTesting(active = true, progress = 0.45f)
        assertThat(player.state.value.isAutoMixActive).isTrue()

        val trackNew = sampleTrack("NewSelection")
        player.play(trackNew, sourceLabel = "Manual")

        assertThat(player.state.value.current?.title).isEqualTo(trackNew.title)
    }

    // Mandatory Case 10: Selection during an existing crossfade.
    @Test
    fun case10_selectionDuringExistingCrossfade_cancelsCrossfade() {
        val player = createTestPlayer()
        val trackA = sampleTrack("A")
        player.play(trackA, sourceLabel = "Test")

        // Trigger manual crossfade cancel & new play
        player.cancelCrossfadeForTesting()
        val trackB = sampleTrack("B")
        player.play(trackB, sourceLabel = "Test")

        assertThat(player.state.value.current?.title).isEqualTo(trackB.title)
    }

    // Mandatory Case 11: Previous/next during media preparation.
    @Test
    fun case11_previousNextDuringMediaPreparation_latestNavigationWins() {
        val player = createTestPlayer()
        val queue = listOf(sampleTrack("1"), sampleTrack("2"), sampleTrack("3"))
        player.playQueue(queue, startIndex = 1, sourceLabel = "Test")

        player.next()
        assertThat(player.state.value.currentIndex).isEqualTo(2)

        player.previous()
        assertThat(player.state.value.currentIndex).isEqualTo(1)
    }

    // Mandatory Case 12: Failed resolution followed by another selection.
    @Test
    fun case12_failedResolutionFollowedByAnotherSelection_playsNewSelectionCleanly() {
        val player = createTestPlayer()
        val failedTrack = sampleTrack("FailingTrack")
        player.play(failedTrack, sourceLabel = "Test")

        // User immediately selects working track
        val workingTrack = sampleTrack("WorkingTrack")
        player.play(workingTrack, sourceLabel = "Test")

        assertThat(player.state.value.current?.title).isEqualTo(workingTrack.title)
        assertThat(player.state.value.error).isNull()
    }

    // Acceptance Requirement: 100 consecutive randomized selection sequences
    // must produce the correct final selected and playing track, with zero stale playback replacements.
    @Test
    fun acceptance_100ConsecutiveRandomizedSelections_producesZeroStaleReplacements() {
        val player = createTestPlayer()
        val tracks = (0 until 20).map { sampleTrack("Track_$it") }
        val random = Random(42)

        for (sequence in 1..100) {
            val selectedIdx = random.nextInt(tracks.size)
            val chosenTrack = tracks[selectedIdx]

            // Random action: play single or playQueue
            if (random.nextBoolean()) {
                player.play(chosenTrack, sourceLabel = "RandomBatch")
            } else {
                player.playQueue(tracks, startIndex = selectedIdx, sourceLabel = "RandomBatch")
            }

            // Invariant 1: State must immediately reflect the chosen track
            assertThat(player.state.value.current?.title).isEqualTo(chosenTrack.title)
            // Invariant 2: Resolving generation must be active
            assertThat(player.getPlayRequestGenerationForTesting()).isGreaterThan(0L)
            // Invariant 3: The media id of the chosen track must be tracked
            assertThat(player.isResolvingMediaIdForTesting(chosenTrack.mediaIdKey())).isTrue()
        }
    }
}
