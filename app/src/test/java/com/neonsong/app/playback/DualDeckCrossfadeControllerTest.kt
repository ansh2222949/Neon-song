package com.neonsong.app.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class DualDeckCrossfadeControllerTest {

    private lateinit var nativeEngine: NativeAudioEngine
    private lateinit var mockPlayerA: ExoPlayer
    private lateinit var mockPlayerB: ExoPlayer
    private lateinit var dualDeckPlayer: DualDeckAudioPlayer
    private lateinit var controller: DualDeckCrossfadeController

    private var virtualTimeMs: Long = 100_000L

    @Before
    fun setUp() {
        nativeEngine = mockk(relaxed = true)
        every { nativeEngine.isAvailable } returns true
        every { nativeEngine.getCrossfade() } returns 0.0f
        every { nativeEngine.getDeckTimeRatio(any()) } returns 1.0f
        every { nativeEngine.underrunCount } returns 0L

        mockPlayerA = mockk(relaxed = true)
        mockPlayerB = mockk(relaxed = true)

        every { mockPlayerA.playbackState } returns Player.STATE_READY
        every { mockPlayerA.playWhenReady } returns true
        every { mockPlayerA.isPlaying } returns true

        every { mockPlayerB.playbackState } returns Player.STATE_READY
        every { mockPlayerB.playWhenReady } returns false
        every { mockPlayerB.isPlaying } returns false

        val context = RuntimeEnvironment.getApplication()
        dualDeckPlayer = DualDeckAudioPlayer(
            context = context,
            nativeAudioEngine = nativeEngine,
            playerFactory = { deckId, _ ->
                if (deckId == DualDeckAudioPlayer.DECK_A) mockPlayerA else mockPlayerB
            },
        )

        virtualTimeMs = 100_000L
        controller = DualDeckCrossfadeController(
            dualDeckPlayer = dualDeckPlayer,
            crossfadeDurationMs = 8_000L,
            minPrebufferFrames = 4800L,
            clock = { virtualTimeMs },
        )
    }

    @Test
    fun initialSetup_establishesDeckAActiveAndDeckBSilent() {
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_A)
        assertThat(controller.standbyDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
        assertThat(controller.state).isEqualTo(CrossfadeState.IDLE)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_A)).isTrue()
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isFalse()

        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_A, 1.0f) }
        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_B, 0.0f) }
        verify { nativeEngine.setCrossfade(0.0f) }
    }

    @Test
    fun armStandbyTrack_prebuffersInaudibleWithoutNativeConsumption() {
        val nextSong = MediaItem.Builder()
            .setMediaId("song_2")
            .setUri("file:///music/song_2.opus")
            .build()

        controller.armStandbyTrack(nextSong)

        assertThat(controller.state).isEqualTo(CrossfadeState.PREBUFFERING)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isFalse()

        // Player B is prepared with playWhenReady=true so decoder produces PCM
        verify { mockPlayerB.setMediaItem(nextSong) }
        verify { mockPlayerB.prepare() }
        verify { mockPlayerB.playWhenReady = true }

        // Native volume is 0 and native consumption is NOT started yet
        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_B, 0.0f) }
        verify(exactly = 0) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_B, true) }
    }

    @Test
    fun update_whenStandbyNotPrebuffered_doesNotPrematurelyStopDeckA() {
        val nextSong = MediaItem.Builder().setMediaId("song_2").build()
        controller.armStandbyTrack(nextSong)

        // Deck B has only 2000 frames (threshold is 4800)
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 2000L

        // Song 1 reaches 175s of 180s (5s remaining <= 8s crossfade threshold)
        val progress = controller.update(currentPositionMs = 175_000L, durationMs = 180_000L, nowMs = virtualTimeMs)

        // Crossfade should NOT start yet because Deck B is not ready
        assertThat(progress).isEqualTo(0.0f)
        assertThat(controller.state).isEqualTo(CrossfadeState.PREBUFFERING)

        // Deck A must NOT be paused or muted
        verify(exactly = 0) { mockPlayerA.pause() }
        verify(exactly = 0) { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_A, 0.0f) }
        verify(exactly = 0) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_B, true) }
    }

    @Test
    fun seamlessCrossfade_equalPowerOverlapAndCompletionWithoutRestart() {
        val nextSong = MediaItem.Builder().setMediaId("song_2").build()
        controller.armStandbyTrack(nextSong)

        // Deck B is prebuffered with 9600 frames (>= 4800 threshold)
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L

        // Reaching 172s of 180s (8s remaining)
        val p0 = controller.update(currentPositionMs = 172_000L, durationMs = 180_000L, nowMs = virtualTimeMs)
        assertThat(controller.state).isEqualTo(CrossfadeState.CROSSFADING)
        assertThat(p0).isEqualTo(0.0f)

        // Verify native consumption is now enabled for Deck B (both decks consuming together in Oboe callback)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isTrue()

        // Advance 4 seconds (50% through 8-second crossfade)
        virtualTimeMs += 4_000L
        val pHalf = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(pHalf).isEqualTo(0.5f)

        // Verify equal-power gains at midpoint: cos(pi/4) ~ 0.7071 and sin(pi/4) ~ 0.7071
        val expectedMidGain = 0.7071f
        val diagMid = controller.getDiagnostics()
        assertThat(abs(diagMid.deckAGain - expectedMidGain)).isLessThan(0.01f)
        assertThat(abs(diagMid.deckBGain - expectedMidGain)).isLessThan(0.01f)

        // Advance remaining 4 seconds (100% completion)
        virtualTimeMs += 4_000L
        val pEnd = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(pEnd).isEqualTo(1.0f)
        assertThat(controller.state).isEqualTo(CrossfadeState.COMPLETED)

        // Verify outgoing Deck A was cleanly paused and flushed without restarting Deck B
        verify { mockPlayerA.pause() }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_A) }
        verify(exactly = 0) { mockPlayerB.seekTo(any()) } // Deck B was NOT restarted!

        // Roles swapped: Deck B is now the active playing deck at 1.0 volume
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
        assertThat(controller.standbyDeck).isEqualTo(DualDeckAudioPlayer.DECK_A)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isTrue()
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_A)).isFalse()
        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_B, 1.0f) }
    }

    @Test
    fun subsequentCrossfade_fromDeckBBackToDeckA_symmetricTransition() {
        // Complete first crossfade so Deck B is active
        controller.completeCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)

        // Arm song 3 on Deck A (now the standby deck)
        val song3 = MediaItem.Builder().setMediaId("song_3").build()
        controller.armStandbyTrack(song3)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_A)).isFalse()
        verify { mockPlayerA.setMediaItem(song3) }
        verify { mockPlayerA.playWhenReady = true }

        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 6000L

        // Trigger crossfade from Deck B to Deck A
        controller.startCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.state).isEqualTo(CrossfadeState.CROSSFADING)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_A)).isTrue()

        // Midpoint
        virtualTimeMs += 4_000L
        val p = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(p).isEqualTo(0.5f)

        // Complete crossfade
        virtualTimeMs += 4_000L
        controller.stepCrossfade(nowMs = virtualTimeMs)

        // Now Deck A is back as active deck, Deck B is paused
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_A)
        verify { mockPlayerB.pause() }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_B) }
    }

    @Test
    fun pauseAndResume_freezesAndResumesMonotonicClockCleanly() {
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 5000L
        controller.startCrossfade(nowMs = virtualTimeMs)

        // 2 seconds into fade
        virtualTimeMs += 2_000L
        controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.crossfadeProgress).isEqualTo(0.25f)

        // User pauses
        controller.onPause(nowMs = virtualTimeMs)
        verify { mockPlayerA.pause() }
        verify { mockPlayerB.pause() }

        // 10 seconds pass while paused
        virtualTimeMs += 10_000L
        controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.crossfadeProgress).isEqualTo(0.25f) // Should remain frozen

        // User resumes
        controller.onPlay(nowMs = virtualTimeMs)
        verify { mockPlayerA.play() }
        verify { mockPlayerB.play() }

        // Advance 2 more seconds: total elapsed should be 4s -> 0.5f
        virtualTimeMs += 2_000L
        val progress = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(progress).isEqualTo(0.5f)
    }

    @Test
    fun seekOutsideWindow_cancelsInFlightCrossfade() {
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 5000L
        controller.startCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.state).isEqualTo(CrossfadeState.CROSSFADING)

        // User seeks back to 30s of 180s track (150s remaining > 8s window)
        controller.onSeek(targetPositionMs = 30_000L, durationMs = 180_000L)

        assertThat(controller.state).isEqualTo(CrossfadeState.CANCELLED)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isFalse()
        verify { mockPlayerB.pause() }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_B) }
        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_A, 1.0f) }
    }

    @Test
    fun scenarioA_bothTracksFixedToOpus_continuousBufferAndSmoothTransition() {
        val opusTrack1 = MediaItem.Builder().setMediaId("opus_1").build()
        val opusTrack2 = MediaItem.Builder().setMediaId("opus_2").build()

        dualDeckPlayer.loadDeck(DualDeckAudioPlayer.DECK_A, opusTrack1, playWhenReady = true)
        controller.armStandbyTrack(opusTrack2)

        val format48k = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(48000)
            .setChannelCount(2)
            .build()
        dualDeckPlayer.sinkA.configure(format48k, 0, null)
        dualDeckPlayer.sinkB.configure(format48k, 0, null)

        // Both decoders produce 48kHz audio into their native rings
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 48000L
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 24000L

        controller.startCrossfade(nowMs = virtualTimeMs)
        virtualTimeMs += 4_000L
        controller.stepCrossfade(nowMs = virtualTimeMs)
        virtualTimeMs += 4_000L
        controller.stepCrossfade(nowMs = virtualTimeMs)

        assertThat(controller.state).isEqualTo(CrossfadeState.COMPLETED)
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
    }

    @Test
    fun scenarioB_bothTracksFixedToLossless_independentResamplersNoCrossTalk() {
        val flacTrack1 = MediaItem.Builder().setMediaId("flac_1").build()
        val flacTrack2 = MediaItem.Builder().setMediaId("flac_2").build()

        dualDeckPlayer.loadDeck(DualDeckAudioPlayer.DECK_A, flacTrack1, playWhenReady = true)
        controller.armStandbyTrack(flacTrack2)

        val format44k = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelCount(2)
            .build()
        val format96k = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(96000)
            .setChannelCount(2)
            .build()

        // Deck A uses 44.1kHz FLAC, Deck B uses 96kHz Hi-Res FLAC
        dualDeckPlayer.sinkA.configure(format44k, 0, null)
        dualDeckPlayer.sinkB.configure(format96k, 0, null)

        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 44100L
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 96000L

        controller.startCrossfade(nowMs = virtualTimeMs)
        virtualTimeMs += 8_000L
        controller.stepCrossfade(nowMs = virtualTimeMs)

        assertThat(controller.state).isEqualTo(CrossfadeState.COMPLETED)
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
    }

    @Test
    fun scenarioC_opusPlaybackFollowedByLosslessUpgradeHandoff_zeroStutterZeroGap() {
        // Deck A is actively playing Opus at 65,000ms
        val opusItem = MediaItem.Builder().setMediaId("song_1_opus").build()
        dualDeckPlayer.loadDeck(DualDeckAudioPlayer.DECK_A, opusItem, playWhenReady = true)
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 48000L

        // Lossless FLAC stream arrives from provider
        val losslessItem = MediaItem.Builder().setMediaId("song_1_lossless_flac").build()

        // Perform quality upgrade handoff on spare deck (Deck B)
        controller.performQualityUpgradeHandoff(
            losslessMediaItem = losslessItem,
            targetPositionMs = 65_000L,
            handoffDurationMs = 1_000L,
            nowMs = virtualTimeMs,
        )

        // Deck B was preloaded at exact position and prebuffering began with volume=0
        verify { mockPlayerB.setMediaItem(losslessItem) }
        verify { mockPlayerB.seekTo(65_000L) }
        verify { mockPlayerB.prepare() }
        verify { mockPlayerB.playWhenReady = true }
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isFalse()

        // Once Deck B prebuffers PCM, trigger handoff crossfade
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L
        controller.startCrossfade(nowMs = virtualTimeMs)

        // 500ms into handoff
        virtualTimeMs += 500L
        controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_A)).isTrue()
        assertThat(dualDeckPlayer.isDeckConsuming(DualDeckAudioPlayer.DECK_B)).isTrue()

        // Handoff completes at 1000ms
        virtualTimeMs += 500L
        controller.stepCrossfade(nowMs = virtualTimeMs)

        // Active deck is now Deck B (Lossless), Deck A was paused without popping
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
        verify { mockPlayerA.pause() }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_A) }
    }

    @Test
    fun diagnosticsSnapshot_reportsAllRequiredMetrics() {
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 12345L
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 67890L
        every { nativeEngine.isDeckPlaying(DualDeckAudioPlayer.DECK_A) } returns true
        every { nativeEngine.isDeckPlaying(DualDeckAudioPlayer.DECK_B) } returns false
        every { nativeEngine.underrunCount } returns 3L

        val diag = controller.getDiagnostics(gainA = 0.8f, gainB = 0.2f)

        assertThat(diag.deckABufferedFrames).isEqualTo(12345L)
        assertThat(diag.deckBBufferedFrames).isEqualTo(67890L)
        assertThat(diag.playerAPlaybackState).isEqualTo(Player.STATE_READY)
        assertThat(diag.nativeDeckAPlaying).isTrue()
        assertThat(diag.nativeDeckBPlaying).isFalse()
        assertThat(diag.deckAGain).isEqualTo(0.8f)
        assertThat(diag.deckBGain).isEqualTo(0.2f)
        assertThat(diag.nativeUnderrunCount).isEqualTo(3L)
        assertThat(diag.deckA).isNotNull()
        assertThat(diag.deckB).isNotNull()
    }

    @Test
    fun crossfade_withTemporaryNetworkBuffering_holdsDeckAGainAndFreezesProgress() {
        val nextSong = MediaItem.Builder().setMediaId("next_song").build()
        controller.armStandbyTrack(nextSong)

        // Deck B initially prebuffered with 9600 frames
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L

        controller.startCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.state).isEqualTo(CrossfadeState.CROSSFADING)

        // 2 seconds into 8-second crossfade (progress = 0.25)
        virtualTimeMs += 2000L
        val p1 = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(p1).isEqualTo(0.25f)
        val initialDeckAGain = controller.currentDeckAGain
        assertThat(initialDeckAGain).isGreaterThan(0.9f)

        // Network stall occurs on Deck B! Buffer starves to only 500 frames (< safe headroom 1920)
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 500L
        every { mockPlayerB.playbackState } returns Player.STATE_BUFFERING

        // Stall is detected immediately; progress is frozen at 0.25f
        val pStalled = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.isStarved).isTrue()
        assertThat(pStalled).isEqualTo(0.25f)

        // Advance 1 second while Deck B is starved
        virtualTimeMs += 1000L
        val p2 = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.isStarved).isTrue()
        // Progress must be frozen at 0.25f during starvation!
        assertThat(p2).isEqualTo(0.25f)
        // Deck A gain must be held audible (never faded out into silence)!
        assertThat(controller.currentDeckAGain).isAtLeast(0.5f)

        // Advance another 2 seconds while still starved
        virtualTimeMs += 2000L
        val p3 = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.isStarved).isTrue()
        assertThat(p3).isEqualTo(0.25f)
        assertThat(controller.currentDeckAGain).isAtLeast(0.5f)

        // Network recovers! Deck B fills back to 9600 frames and STATE_READY
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L
        every { mockPlayerB.playbackState } returns Player.STATE_READY

        // Step crossfade after network recovery: recovers without skipping forward (resumes at exact freeze point 0.25f)
        virtualTimeMs += 2000L
        val p4 = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.isStarved).isFalse()
        assertThat(p4).isEqualTo(0.25f)

        // Advance 2 seconds post-recovery: progress smoothly advances from 0.25f to 0.50f
        virtualTimeMs += 2000L
        val p5 = controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(p5).isEqualTo(0.5f)

        // Advance remaining 4 seconds to completion without premature cutoff or skipping forward
        virtualTimeMs += 4000L
        controller.stepCrossfade(nowMs = virtualTimeMs)
        assertThat(controller.state).isEqualTo(CrossfadeState.COMPLETED)
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
    }

    @Test
    fun independentSpeedAndPitchSettings_arePreservedAcrossTrackTransitions() {
        // User sets custom speed 1.25x and pitch 1.15x on Deck A
        every { nativeEngine.getDeckSpeed(DualDeckAudioPlayer.DECK_A) } returns 1.25f
        every { nativeEngine.getDeckPitch(DualDeckAudioPlayer.DECK_A) } returns 1.15f
        every { nativeEngine.getDeckSpeed(DualDeckAudioPlayer.DECK_B) } returns 1.0f
        every { nativeEngine.getDeckPitch(DualDeckAudioPlayer.DECK_B) } returns 1.0f

        val song2 = MediaItem.Builder().setMediaId("song_2").build()
        controller.armStandbyTrack(song2)
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L

        controller.startCrossfade(nowMs = virtualTimeMs)
        virtualTimeMs += 8000L
        controller.stepCrossfade(nowMs = virtualTimeMs)

        // After completion, Deck B (new active deck) must have inherited Deck A's active speed and pitch!
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
        verify {
            dualDeckPlayer.sinkFor(DualDeckAudioPlayer.DECK_B)
                .setPlaybackParameters(PlaybackParameters(1.25f, 1.15f))
        }
    }

    @Test
    fun codecCombinations_losslessToOpusAndMixedSampleRatesAndChannels_allCrossfadeSmoothly() {
        // Deck A: Lossless 96kHz Stereo Float
        val formatLossless96k = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(96000)
            .setChannelCount(2)
            .build()
        dualDeckPlayer.sinkA.configure(formatLossless96k, 0, null)

        // Deck B: Opus 48kHz Mono 16-bit
        val formatOpus48kMono = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(48000)
            .setChannelCount(1)
            .build()
        dualDeckPlayer.sinkB.configure(formatOpus48kMono, 0, null)

        val opusItem = MediaItem.Builder().setMediaId("opus_mono").build()
        controller.armStandbyTrack(opusItem)

        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 96000L
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 24000L

        controller.startCrossfade(nowMs = virtualTimeMs)
        virtualTimeMs += 8000L
        controller.stepCrossfade(nowMs = virtualTimeMs)

        assertThat(controller.state).isEqualTo(CrossfadeState.COMPLETED)
        assertThat(controller.activeDeck).isEqualTo(DualDeckAudioPlayer.DECK_B)
        verify { mockPlayerA.pause() }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_A) }
    }

    @Test
    fun dynamicPrebuffering_scalesHeadroomWithSampleRate() {
        // Source 96kHz Hi-Res FLAC should dynamically scale to 200ms = 19,200 frames
        val format96k = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_32BIT)
            .setSampleRate(96000)
            .setChannelCount(2)
            .build()
        dualDeckPlayer.sinkB.configure(format96k, 0, null)

        val effectiveFrames = controller.getEffectivePrebufferFrames(DualDeckAudioPlayer.DECK_B)
        // 96,000 * 0.200 = 19,200 frames
        assertThat(effectiveFrames).isEqualTo(19200L)

        // Source 44.1kHz standard FLAC should scale to 200ms = 8,820 frames
        val format44k = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelCount(2)
            .build()
        dualDeckPlayer.sinkB.configure(format44k, 0, null)
        val effectiveFrames44k = controller.getEffectivePrebufferFrames(DualDeckAudioPlayer.DECK_B)
        assertThat(effectiveFrames44k).isEqualTo(8820L)
    }
}
