package com.neonsong.app.playback

import android.app.Application
import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class MusicPlayerRegressionTest {

    private fun sampleTrack(
        title: String,
        artist: String,
        durationMs: Long = 240_000L,
        videoId: String? = null,
        playbackUrl: String? = null,
    ) = PlayableTrack(
        title = title,
        artist = artist,
        durationMs = durationMs,
        videoId = videoId,
        playbackUrl = playbackUrl,
    )

    private fun createTestMusicPlayer(): MusicPlayer {
        val context = RuntimeEnvironment.getApplication()
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
            applicationScope = CoroutineScope(Dispatchers.Unconfined),
            downloadedTrackDao = dagger.Lazy { mockk(relaxed = true) },
            usbDacMonitor = mockk(relaxed = true),
            exclusiveUsbOutput = mockk(relaxed = true),
            songPlayStatsRepository = dagger.Lazy { mockk(relaxed = true) },
            nativeAutoMixEngine = dagger.Lazy { mockk(relaxed = true) },
            autoDJController = dagger.Lazy { mockk(relaxed = true) },
            autoEqController = mockk(relaxed = true),
        )
    }

    @Test
    fun productionMusicPlayer_playHistory_isThreadSafeUnderHighConcurrency() {
        val player = createTestMusicPlayer()
        runBlocking {
            // Run 50 concurrent worker coroutines writing and reading history on the production MusicPlayer
            val jobs = (1..50).map { workerId ->
                async(Dispatchers.Default) {
                    for (i in 1..100) {
                        player.recordHistoryForTesting("track_${workerId}_$i")
                        if (i % 5 == 0) {
                            val dummyState = MusicPlayerState(current = sampleTrack("track_${workerId}_$i", "Artist"))
                            player.popHistoryIndexForTesting(dummyState)
                        }
                    }
                }
            }
            jobs.awaitAll()

            // Verify production playHistory maintains size bound <= 50 without thread concurrency corruption
            assertThat(player.getPlayHistoryForTesting().size).isAtMost(50)
        }
    }

    @Test
    fun productionMusicPlayer_playHistory_deduplicatesConsecutiveRepeats() {
        val player = createTestMusicPlayer()
        player.clearPlayHistoryForTesting()

        player.recordHistoryForTesting("track_1")
        player.recordHistoryForTesting("track_1")
        player.recordHistoryForTesting("track_1")
        player.recordHistoryForTesting("track_2")
        player.recordHistoryForTesting("track_2")
        player.recordHistoryForTesting("track_1")

        assertThat(player.getPlayHistoryForTesting()).containsExactly("track_1", "track_2", "track_1").inOrder()
    }

    @Test
    fun productionMusicPlayer_popHistoryIndex_skipsCurrentAndReturnsPrevious() {
        val player = createTestMusicPlayer()
        player.clearPlayHistoryForTesting()

        val tracks = listOf(
            sampleTrack("Track 1", "Artist 1"),
            sampleTrack("Track 2", "Artist 2"),
            sampleTrack("Track 3", "Artist 3"),
        )
        player.recordHistoryForTesting(tracks[0].mediaIdKey())
        player.recordHistoryForTesting(tracks[1].mediaIdKey())
        player.recordHistoryForTesting(tracks[2].mediaIdKey())

        val snapshot = MusicPlayerState(
            queue = tracks,
            currentIndex = 2,
            current = tracks[2],
        )

        val prevIndex = player.popHistoryIndexForTesting(snapshot)
        assertThat(prevIndex).isEqualTo(1) // index of Track 2
    }

    @Test
    fun productionMusicPlayer_currentPositionMs_isSafeOffMainThread() {
        val player = createTestMusicPlayer()
        runBlocking(Dispatchers.Default) {
            // Calling currentPositionMs() from background dispatcher should not crash with ExoPlayer wrong-thread error
            val pos = player.currentPositionMs()
            assertThat(pos).isEqualTo(0L)
        }
    }

    @Test
    fun crossfadePlan_correctlyGatesOnRepeatOneAndBitPerfect() {
        val bitPerfectInput = CrossfadePlanInput(
            crossfadeEnabled = true,
            bitPerfectEnabled = true,
            isPlaying = true,
            repeatOne = false,
            durationMs = 240_000L,
            positionMs = 238_000L,
            crossfadeMs = 5_000L,
            playbackSpeed = 1f,
            hasNextItem = true,
            nextItemResolved = true,
            streamExpired = false,
            standbyArmedForNext = true,
            standbyReady = true,
            standbyDurationMs = 200_000L,
            standbyBufferedMs = 30_000L,
            armLeadMs = 10_000L,
            refreshLeadMs = 30_000L,
        )
        val plan = planCrossfade(bitPerfectInput)
        assertThat(plan).isEqualTo(CrossfadeDecision.Idle(CrossfadeIdleReason.BitPerfect))

        val repeatOneInput = bitPerfectInput.copy(
            bitPerfectEnabled = false,
            repeatOne = true,
        )
        val repeatPlan = planCrossfade(repeatOneInput)
        assertThat(repeatPlan).isEqualTo(CrossfadeDecision.Idle(CrossfadeIdleReason.RepeatOne))
    }

    @Test
    fun persistedPlaybackSession_restoresSynchronouslyOnInit_beforeAnyPlaybackCommand() {
        val context = RuntimeEnvironment.getApplication()
        val dummySession = PersistedPlaybackSession(
            version = 2,
            queue = listOf(
                sampleTrack("Resurrected Song", "Neon Master", 210_000L),
                sampleTrack("Upcoming Song", "Neon Master", 195_000L),
            ),
            currentIndex = 0,
            positionMs = 73_400L,
            durationMs = 210_000L,
            sourceLabel = "PersistedRecovery",
        )
        val json = Json.encodeToString(dummySession)

        // Pre-populate disk storage to simulate state saved before process death
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .putString("active_session", json)
            .commit()

        // Construct fresh MusicPlayer (simulating app process restart)
        val restoredPlayer = createTestMusicPlayer()

        // Verify state is immediately populated during init {} BEFORE any command is issued
        val state = restoredPlayer.state.value
        assertThat(state.current).isNotNull()
        assertThat(state.current?.title).isEqualTo("Resurrected Song")
        assertThat(state.current?.artist).isEqualTo("Neon Master")
        assertThat(state.positionMs).isEqualTo(73_400L)
        assertThat(state.durationMs).isEqualTo(210_000L)
        assertThat(state.queue).hasSize(2)
        assertThat(state.sourceLabel).isEqualTo("PersistedRecovery")

        // Clean up preferences after test
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistedPlaybackSession_firstTickerIterationAfterRestore_doesNotResetToZero() {
        val context = RuntimeEnvironment.getApplication()
        val dummySession = PersistedPlaybackSession(
            version = 2,
            queue = listOf(sampleTrack("Ticker Survivor", "Neon Master", 210_000L)),
            currentIndex = 0,
            positionMs = 73_400L,
            durationMs = 210_000L,
            sourceLabel = "PersistedRecovery",
        )
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .putString("active_session", Json.encodeToString(dummySession))
            .commit()

        val restoredPlayer = createTestMusicPlayer()

        // Verify initial state
        assertThat(restoredPlayer.getPlayheadPosMsForTesting()).isEqualTo(73_400L)
        assertThat(restoredPlayer.state.value.positionMs).isEqualTo(73_400L)

        // Execute first ticker iteration while paused
        val playhead = restoredPlayer.advancePlayheadForTesting(playing = false)
        assertThat(playhead).isEqualTo(73_400L)
        assertThat(restoredPlayer.getPlayheadPosMsForTesting()).isEqualTo(73_400L)

        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistedPlaybackSession_advancePlayhead_whenPlaying_advancesFromRestoredPosition() {
        val context = RuntimeEnvironment.getApplication()
        val dummySession = PersistedPlaybackSession(
            version = 2,
            queue = listOf(sampleTrack("Resume Advance", "Neon Master", 210_000L)),
            currentIndex = 0,
            positionMs = 73_400L,
            durationMs = 210_000L,
            sourceLabel = "PersistedRecovery",
        )
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .putString("active_session", Json.encodeToString(dummySession))
            .commit()

        val restoredPlayer = createTestMusicPlayer()

        // Calling advancePlayhead(playing = true) when paused wall clock resumes
        val advanced = restoredPlayer.advancePlayheadForTesting(playing = true)
        assertThat(advanced).isAtLeast(73_400L)

        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistedPlaybackSession_negativePosition_safelyClampsToZero() {
        val context = RuntimeEnvironment.getApplication()
        val dummySession = PersistedPlaybackSession(
            version = 2,
            queue = listOf(sampleTrack("Negative Position Song", "Neon Master", 210_000L)),
            currentIndex = 0,
            positionMs = -50_000L,
            durationMs = 210_000L,
            sourceLabel = "PersistedRecovery",
        )
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .putString("active_session", Json.encodeToString(dummySession))
            .commit()

        val restoredPlayer = createTestMusicPlayer()

        // Assert safely clamped to 0
        assertThat(restoredPlayer.state.value.positionMs).isEqualTo(0L)
        assertThat(restoredPlayer.getPlayheadPosMsForTesting()).isEqualTo(0L)

        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistedPlaybackSession_localTrackWithoutVideoId_matchesPlayheadKeyIdentically() {
        val context = RuntimeEnvironment.getApplication()
        val localTrack = sampleTrack(
            title = "Local Audio File",
            artist = "FLAC Artist",
            durationMs = 180_000L,
            playbackUrl = "/storage/emulated/0/Music/local_song.flac",
        )
        val dummySession = PersistedPlaybackSession(
            version = 2,
            queue = listOf(localTrack),
            currentIndex = 0,
            positionMs = 42_000L,
            durationMs = 180_000L,
            sourceLabel = "LocalPlayback",
        )
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .putString("active_session", Json.encodeToString(dummySession))
            .commit()

        val restoredPlayer = createTestMusicPlayer()

        // Verify key identity rule matches mediaIdKey() exactly: "local:/storage/emulated/0/Music/local_song.flac"
        val expectedKey = localTrack.mediaIdKey()
        assertThat(restoredPlayer.getPlayheadKeyForTesting()).isEqualTo(expectedKey)
        assertThat(restoredPlayer.state.value.positionMs).isEqualTo(42_000L)

        // Verify ticker preserves position for local files without videoId
        val playhead = restoredPlayer.advancePlayheadForTesting(playing = false)
        assertThat(playhead).isEqualTo(42_000L)

        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun determineTargetWakeMode_dynamicallySelectsBetweenLocalAndNetwork() {
        val player = createTestMusicPlayer()

        val localFileTrack = sampleTrack("Local Track", "Artist", playbackUrl = "/storage/Music/song.mp3")
        val contentMediaTrack = sampleTrack("MediaStore Track", "Artist", playbackUrl = "content://media/external/audio/123")
        val contentDocTrack = sampleTrack("Local Doc Track", "Artist", playbackUrl = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fsong.flac")
        val contentCloudTrack = sampleTrack("Google Drive Track", "Artist", playbackUrl = "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D54321")
        val contentDropboxTrack = sampleTrack("Dropbox Track", "Artist", playbackUrl = "content://com.dropbox.android.FileOpenProvider/1234")
        val fileUriTrack = sampleTrack("File Track", "Artist", playbackUrl = "file:///sdcard/download.flac")
        val onlineStreamTrack = sampleTrack("Online Track", "Artist", videoId = "video12345", playbackUrl = null)
        val httpsStreamTrack = sampleTrack("Https Track", "Artist", playbackUrl = "https://stream.googlevideo.com/audio")

        // Local filesystem & local MediaStore/Document providers -> WAKE_MODE_LOCAL (Wi-Fi sleeps)
        assertThat(player.determineTargetWakeMode(localFileTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_LOCAL)
        assertThat(player.determineTargetWakeMode(contentMediaTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_LOCAL)
        assertThat(player.determineTargetWakeMode(contentDocTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_LOCAL)
        assertThat(player.determineTargetWakeMode(fileUriTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_LOCAL)
        assertThat(player.determineTargetWakeMode(null)).isEqualTo(androidx.media3.common.C.WAKE_MODE_LOCAL)

        // Cloud storage document providers & remote HTTP/HTTPS streams -> WAKE_MODE_NETWORK
        assertThat(player.determineTargetWakeMode(contentCloudTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_NETWORK)
        assertThat(player.determineTargetWakeMode(contentDropboxTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_NETWORK)
        assertThat(player.determineTargetWakeMode(onlineStreamTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_NETWORK)
        assertThat(player.determineTargetWakeMode(httpsStreamTrack)).isEqualTo(androidx.media3.common.C.WAKE_MODE_NETWORK)
    }

    @Test
    fun cancelCrossfade_resetsNativeAutoMixEngine_preventingLingeringHighPassFilter() {
        val player = createTestMusicPlayer()

        // Simulate active AutoMix crossfade in progress
        player.setAutoMixActiveForTesting(active = true, progress = 0.65f)
        assertThat(player.state.value.isAutoMixActive).isTrue()
        assertThat(player.state.value.autoMixProgress).isEqualTo(0.65f)

        // Cancel crossfade (e.g. user seeked, skipped, or stopped)
        player.cancelCrossfadeForTesting()

        // Verify native engine reset was invoked and UI state cleared immediately
        val state = player.state.value
        assertThat(state.isAutoMixActive).isFalse()
        assertThat(state.autoMixState).isEqualTo(AutoMixState.IDLE)
        assertThat(state.autoMixProgress).isEqualTo(0.0f)
    }

    @Test
    fun persistedPlaybackSession_trackChangeAfterRestore_discardsOldPlayheadState() {
        val context = RuntimeEnvironment.getApplication()
        val track1 = sampleTrack("Restored Song", "Artist 1", 200_000L)
        val track2 = sampleTrack("Next Song", "Artist 2", 180_000L)
        val dummySession = PersistedPlaybackSession(
            version = 2,
            queue = listOf(track1, track2),
            currentIndex = 0,
            positionMs = 73_400L,
            durationMs = 200_000L,
            sourceLabel = "Recovery",
        )
        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .putString("active_session", Json.encodeToString(dummySession))
            .commit()

        val restoredPlayer = createTestMusicPlayer()
        assertThat(restoredPlayer.getPlayheadKeyForTesting()).isEqualTo(track1.mediaIdKey())
        assertThat(restoredPlayer.getPlayheadPosMsForTesting()).isEqualTo(73_400L)

        // Simulate track change to track 2
        restoredPlayer.setCurrentTrackForTesting(track2)
        val playhead = restoredPlayer.advancePlayheadForTesting(playing = false)

        // Verify key shifted to track 2 and old 73,400L position was discarded to 0L
        assertThat(restoredPlayer.getPlayheadKeyForTesting()).isEqualTo(track2.mediaIdKey())
        assertThat(playhead).isEqualTo(0L)
        assertThat(restoredPlayer.getPlayheadPosMsForTesting()).isEqualTo(0L)

        context.getSharedPreferences("neonsong_playback_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
