package com.neonsong.app.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class DualDeckAudioPlayerTest {

    private lateinit var nativeEngine: NativeAudioEngine
    private lateinit var mockPlayerA: ExoPlayer
    private lateinit var mockPlayerB: ExoPlayer
    private lateinit var dualDeckPlayer: DualDeckAudioPlayer

    private val testFormat = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_FLOAT)
        .setSampleRate(48000)
        .setChannelCount(2)
        .build()

    @Before
    fun setUp() {
        nativeEngine = mockk(relaxed = true)
        every { nativeEngine.isAvailable } returns true
        every { nativeEngine.getCrossfade() } returns 0.0f
        every { nativeEngine.getDeckTimeRatio(any()) } returns 1.0f

        mockPlayerA = mockk(relaxed = true)
        mockPlayerB = mockk(relaxed = true)

        val context = RuntimeEnvironment.getApplication()
        dualDeckPlayer = DualDeckAudioPlayer(
            context = context,
            nativeAudioEngine = nativeEngine,
            playerFactory = { deckId, _ ->
                if (deckId == DualDeckAudioPlayer.DECK_A) mockPlayerA else mockPlayerB
            },
        )
    }

    @Test
    fun initialization_assignsIndependentDeckIdsToSinks() {
        assertThat(dualDeckPlayer.sinkA.deckId).isEqualTo(DualDeckAudioPlayer.DECK_A)
        assertThat(dualDeckPlayer.sinkB.deckId).isEqualTo(DualDeckAudioPlayer.DECK_B)
        assertThat(dualDeckPlayer.playerFor(DualDeckAudioPlayer.DECK_A)).isSameInstanceAs(mockPlayerA)
        assertThat(dualDeckPlayer.playerFor(DualDeckAudioPlayer.DECK_B)).isSameInstanceAs(mockPlayerB)
    }

    @Test
    fun loadDeck_preparesAndSetsMediaItemOnTargetDeck() {
        val mediaItemA = MediaItem.fromUri("file:///path/to/song_a.flac")
        val mediaItemB = MediaItem.fromUri("file:///path/to/song_b.flac")

        dualDeckPlayer.loadDeck(DualDeckAudioPlayer.DECK_A, mediaItemA, playWhenReady = true)
        verify { mockPlayerA.setMediaItem(mediaItemA) }
        verify { mockPlayerA.prepare() }
        verify { mockPlayerA.playWhenReady = true }

        dualDeckPlayer.loadDeck(DualDeckAudioPlayer.DECK_B, mediaItemB, playWhenReady = false)
        verify { mockPlayerB.setMediaItem(mediaItemB) }
        verify { mockPlayerB.prepare() }
        verify { mockPlayerB.playWhenReady = false }
    }

    @Test
    fun setCrossfade_delegatesToNativeAudioEngineWithCoercion() {
        dualDeckPlayer.setCrossfade(0.65f)
        verify { nativeEngine.setCrossfade(0.65f) }

        dualDeckPlayer.setCrossfade(1.5f)
        verify { nativeEngine.setCrossfade(1.0f) }

        dualDeckPlayer.setCrossfade(-0.2f)
        verify { nativeEngine.setCrossfade(0.0f) }
    }

    @Test
    fun setDeckVolume_updatesBothNativeEngineAndSink() {
        dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_A, 0.8f)
        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_A, 0.8f) }

        dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_B, 0.4f)
        verify { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_B, 0.4f) }
    }

    @Test
    fun setDeckTimeRatio_updatesNativeWsolaAndPlayerPlaybackParameters() {
        dualDeckPlayer.setDeckTimeRatio(DualDeckAudioPlayer.DECK_B, 1.25f)

        verify { nativeEngine.setDeckTimeRatio(DualDeckAudioPlayer.DECK_B, 1.25f) }
        verify { mockPlayerB.playbackParameters = PlaybackParameters(1.25f) }
    }

    @Test
    fun flushDeck_flushesTargetSinkAndNativeDeckBuffer() {
        dualDeckPlayer.flushDeck(DualDeckAudioPlayer.DECK_A)
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_A) }

        dualDeckPlayer.flushDeck(DualDeckAudioPlayer.DECK_B)
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_B) }
    }

    @Test
    fun getDeckBufferedFrames_queriesTargetNativeDeckBuffer() {
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A) } returns 4800L
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L

        assertThat(dualDeckPlayer.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A)).isEqualTo(4800L)
        assertThat(dualDeckPlayer.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B)).isEqualTo(9600L)
    }

    @Test
    fun release_cleansUpBothPlayersSinksAndFlushesBuffers() {
        dualDeckPlayer.release()

        verify { mockPlayerA.release() }
        verify { mockPlayerB.release() }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_A) }
        verify { nativeEngine.flushDeck(DualDeckAudioPlayer.DECK_B) }
    }

    @Test
    fun concurrentPcmTransfer_routesToSeparateNativeDeckRings() {
        dualDeckPlayer.sinkA.configure(testFormat, 0, null)
        dualDeckPlayer.sinkB.configure(testFormat, 0, null)

        val frameCount = 256
        val bytes = frameCount * 2 * Float.SIZE_BYTES

        val bufferA = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        val bufferB = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        for (i in 0 until (frameCount * 2)) {
            bufferA.putFloat(0.5f)
            bufferB.putFloat(-0.5f)
        }
        bufferA.flip()
        bufferB.flip()

        every {
            nativeEngine.writeDeckPcm(
                deckId = DualDeckAudioPlayer.DECK_A,
                buffer = any(),
                frameCount = frameCount,
                encoding = NativePcmEncoding.PCM_FLOAT,
                inputSampleRate = 48000,
                inputChannelCount = 2,
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + bytes)
            frameCount
        }

        every {
            nativeEngine.writeDeckPcm(
                deckId = DualDeckAudioPlayer.DECK_B,
                buffer = any(),
                frameCount = frameCount,
                encoding = NativePcmEncoding.PCM_FLOAT,
                inputSampleRate = 48000,
                inputChannelCount = 2,
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + bytes)
            frameCount
        }

        val handledA = dualDeckPlayer.sinkA.handleBuffer(bufferA, 0L, 1)
        val handledB = dualDeckPlayer.sinkB.handleBuffer(bufferB, 0L, 1)

        assertThat(handledA).isTrue()
        assertThat(handledB).isTrue()
        assertThat(bufferA.remaining()).isEqualTo(0)
        assertThat(bufferB.remaining()).isEqualTo(0)

        verify(exactly = 1) {
            nativeEngine.writeDeckPcm(DualDeckAudioPlayer.DECK_A, any(), frameCount, any(), 48000, 2)
        }
        verify(exactly = 1) {
            nativeEngine.writeDeckPcm(DualDeckAudioPlayer.DECK_B, any(), frameCount, any(), 48000, 2)
        }
    }

    @Test
    fun backpressureOnDeckA_doesNotImpedeDeckB() {
        dualDeckPlayer.sinkA.configure(testFormat, 0, null)
        dualDeckPlayer.sinkB.configure(testFormat, 0, null)

        val frameCount = 100
        val bytes = frameCount * 2 * Float.SIZE_BYTES

        val bufferA = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        val bufferB = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        for (i in 0 until (frameCount * 2)) {
            bufferA.putFloat(0.1f)
            bufferB.putFloat(0.2f)
        }
        bufferA.flip()
        bufferB.flip()

        // Deck A has backpressure: accepts only 40 frames
        every {
            nativeEngine.writeDeckPcm(
                deckId = DualDeckAudioPlayer.DECK_A,
                buffer = any(),
                frameCount = frameCount,
                encoding = any(),
                inputSampleRate = any(),
                inputChannelCount = any(),
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + 40 * 2 * Float.SIZE_BYTES)
            40
        }

        // Deck B has capacity: accepts all 100 frames
        every {
            nativeEngine.writeDeckPcm(
                deckId = DualDeckAudioPlayer.DECK_B,
                buffer = any(),
                frameCount = frameCount,
                encoding = any(),
                inputSampleRate = any(),
                inputChannelCount = any(),
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + bytes)
            100
        }

        val handledA = dualDeckPlayer.sinkA.handleBuffer(bufferA, 0L, 1)
        val handledB = dualDeckPlayer.sinkB.handleBuffer(bufferB, 0L, 1)

        // Deck A returns false for retry
        assertThat(handledA).isFalse()
        assertThat(bufferA.remaining()).isEqualTo(60 * 2 * Float.SIZE_BYTES)

        // Deck B returns true
        assertThat(handledB).isTrue()
        assertThat(bufferB.remaining()).isEqualTo(0)
    }

    @Test
    fun isDeckPrebuffered_verifiesBufferThresholdBeforeCrossfade() {
        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 2400L
        assertThat(dualDeckPlayer.isDeckPrebuffered(DualDeckAudioPlayer.DECK_B, minFrames = 4800L)).isFalse()

        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 4800L
        assertThat(dualDeckPlayer.isDeckPrebuffered(DualDeckAudioPlayer.DECK_B, minFrames = 4800L)).isTrue()

        every { nativeEngine.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B) } returns 9600L
        assertThat(dualDeckPlayer.isDeckPrebuffered(DualDeckAudioPlayer.DECK_B, minFrames = 4800L)).isTrue()
    }

    @Test
    fun pauseDeckBWhileDeckAContinues_independentPlaybackLifecycle() {
        dualDeckPlayer.sinkA.play()
        dualDeckPlayer.sinkB.play()

        verify(exactly = 1) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_A, true) }
        verify(exactly = 1) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_B, true) }

        // Pausing Deck B must not pause Deck A
        dualDeckPlayer.sinkB.pause()
        verify(exactly = 1) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_B, false) }
        verify(exactly = 0) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_A, false) }
    }

    @Test
    fun pauseDeckAWhileDeckBContinues_independentPlaybackLifecycle() {
        dualDeckPlayer.sinkA.play()
        dualDeckPlayer.sinkB.play()

        // Pausing Deck A must not pause Deck B
        dualDeckPlayer.sinkA.pause()
        verify(exactly = 1) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_A, false) }
        verify(exactly = 0) { nativeEngine.setDeckPlaying(DualDeckAudioPlayer.DECK_B, false) }
    }

    @Test
    fun rapidCrossfadeAndDeckSwitching_preservesEngineParameters() {
        for (i in 0..10) {
            val progress = i / 10.0f
            dualDeckPlayer.setCrossfade(progress)
            dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_A, 1.0f - progress)
            dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_B, progress)
        }

        verify(exactly = 11) { nativeEngine.setCrossfade(any()) }
        verify(exactly = 11) { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_A, any()) }
        verify(exactly = 11) { nativeEngine.setDeckVolume(DualDeckAudioPlayer.DECK_B, any()) }
    }

    @Test
    fun differentSourceSampleRates_handlesIndependentDeckIngress() {
        val format44100 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelCount(2)
            .build()

        val format96000 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(96000)
            .setChannelCount(2)
            .build()

        dualDeckPlayer.sinkA.configure(format44100, 0, null)
        dualDeckPlayer.sinkB.configure(format96000, 0, null)

        val frameCount = 128
        val bufA = ByteBuffer.allocateDirect(frameCount * 2 * 2).order(ByteOrder.nativeOrder())
        bufA.put(ByteArray(frameCount * 2 * 2))
        bufA.flip()

        val bufB = ByteBuffer.allocateDirect(frameCount * 2 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        bufB.put(ByteArray(frameCount * 2 * Float.SIZE_BYTES))
        bufB.flip()

        every {
            nativeEngine.writeDeckPcm(DualDeckAudioPlayer.DECK_A, any(), frameCount, any(), 44100, 2)
        } returns frameCount

        every {
            nativeEngine.writeDeckPcm(DualDeckAudioPlayer.DECK_B, any(), frameCount, any(), 96000, 2)
        } returns frameCount

        val handledA = dualDeckPlayer.sinkA.handleBuffer(bufA, 0L, 1)
        val handledB = dualDeckPlayer.sinkB.handleBuffer(bufB, 0L, 1)

        assertThat(handledA).isTrue()
        assertThat(handledB).isTrue()

        verify(exactly = 1) {
            nativeEngine.writeDeckPcm(DualDeckAudioPlayer.DECK_A, any(), frameCount, NativePcmEncoding.PCM_I16, 44100, 2)
        }
        verify(exactly = 1) {
            nativeEngine.writeDeckPcm(DualDeckAudioPlayer.DECK_B, any(), frameCount, NativePcmEncoding.PCM_FLOAT, 96000, 2)
        }
    }
}
