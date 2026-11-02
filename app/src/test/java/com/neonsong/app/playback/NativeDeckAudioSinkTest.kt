package com.neonsong.app.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class NativeDeckAudioSinkTest {

    private lateinit var nativeEngine: NativeAudioEngine
    private lateinit var deckASink: NativeDeckAudioSink
    private lateinit var deckBSink: NativeDeckAudioSink

    private val testStereoFloatFormat = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_FLOAT)
        .setSampleRate(48000)
        .setChannelCount(2)
        .build()

    private val testStereoPcm16Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setSampleRate(44100)
        .setChannelCount(2)
        .build()

    private val testMonoPcm24Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_24BIT)
        .setSampleRate(96000)
        .setChannelCount(1)
        .build()

    @Before
    fun setUp() {
        nativeEngine = mockk(relaxed = true)
        every { nativeEngine.isAvailable } returns true
        every { nativeEngine.isDeckResamplerDrained(any()) } returns true
        every { nativeEngine.isDeckWsolaDrained(any()) } returns true
        every { nativeEngine.getPresentationLatencyUs() } returns 0L
        every { nativeEngine.getDeckTimeRatio(any()) } returns 1.0f
        every { nativeEngine.outputSampleRate } returns 48000

        deckASink = NativeDeckAudioSink(deckId = 0, nativeEngine = nativeEngine)
        deckBSink = NativeDeckAudioSink(deckId = 1, nativeEngine = nativeEngine)
    }

    @Test
    fun supportsFormat_validPcmFormats_returnsDirectlySupported() {
        assertThat(deckASink.supportsFormat(testStereoFloatFormat)).isTrue()
        assertThat(deckASink.getFormatSupport(testStereoFloatFormat))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)

        assertThat(deckASink.supportsFormat(testStereoPcm16Format)).isTrue()
        assertThat(deckASink.getFormatSupport(testStereoPcm16Format))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)

        assertThat(deckASink.supportsFormat(testMonoPcm24Format)).isTrue()
        assertThat(deckASink.getFormatSupport(testMonoPcm24Format))
            .isEqualTo(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY)
    }

    @Test
    fun supportsFormat_invalidFormats_returnsUnsupported() {
        val nonRawFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEG)
            .setSampleRate(48000)
            .setChannelCount(2)
            .build()
        assertThat(deckASink.supportsFormat(nonRawFormat)).isFalse()
        assertThat(deckASink.getFormatSupport(nonRawFormat))
            .isEqualTo(AudioSink.SINK_FORMAT_UNSUPPORTED)

        val zeroRateFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setSampleRate(0)
            .setChannelCount(2)
            .build()
        assertThat(deckASink.supportsFormat(zeroRateFormat)).isFalse()
    }

    @Test
    fun configure_validFormat_configuresAndInvokesCallback() {
        var callbackRate = 0
        var callbackEncoding = 0
        var callbackChannels = 0
        deckASink.onConfiguredFormat = { rate, encoding, channels ->
            callbackRate = rate
            callbackEncoding = encoding
            callbackChannels = channels
        }

        deckASink.configure(testStereoFloatFormat, 0, null)

        assertThat(callbackRate).isEqualTo(48000)
        assertThat(callbackEncoding).isEqualTo(C.ENCODING_PCM_FLOAT)
        assertThat(callbackChannels).isEqualTo(2)
    }

    @Test
    fun configure_unsupportedFormat_throwsConfigurationException() {
        val invalidFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .build()

        assertThrows(AudioSink.ConfigurationException::class.java) {
            deckASink.configure(invalidFormat, 0, null)
        }
    }

    @Test
    fun handleBuffer_whenNativeAcceptsAllDirectFrames_advancesPositionAndReturnsTrue() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        val frameCount = 128
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val buffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(frameCount * bytesPerFrame))
        buffer.flip()

        every {
            nativeEngine.writeDeckPcm(
                deckId = 0,
                buffer = any(),
                frameCount = frameCount,
                encoding = NativePcmEncoding.PCM_FLOAT,
                inputSampleRate = 48000,
                inputChannelCount = 2,
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + frameCount * bytesPerFrame)
            frameCount
        }

        val accepted = deckASink.handleBuffer(buffer, 0L, 1)

        assertThat(accepted).isTrue()
        assertThat(buffer.remaining()).isEqualTo(0)
        assertThat(buffer.position()).isEqualTo(frameCount * bytesPerFrame)
    }

    @Test
    fun handleBuffer_whenNativeBackpressuresPartialWrite_advancesOnlyAcceptedFramesAndReturnsFalse() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        val frameCount = 100
        val acceptedFrames = 40
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val buffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(frameCount * bytesPerFrame))
        buffer.flip()

        every {
            nativeEngine.writeDeckPcm(
                deckId = 0,
                buffer = any(),
                frameCount = frameCount,
                encoding = NativePcmEncoding.PCM_FLOAT,
                inputSampleRate = 48000,
                inputChannelCount = 2,
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + acceptedFrames * bytesPerFrame)
            acceptedFrames
        }

        val accepted = deckASink.handleBuffer(buffer, 0L, 1)

        assertThat(accepted).isFalse()
        assertThat(buffer.position()).isEqualTo(acceptedFrames * bytesPerFrame)
        assertThat(buffer.remaining()).isEqualTo((frameCount - acceptedFrames) * bytesPerFrame)

        // Retry with remainder
        val remainingFrames = frameCount - acceptedFrames
        every {
            nativeEngine.writeDeckPcm(
                deckId = 0,
                buffer = any(),
                frameCount = remainingFrames,
                encoding = NativePcmEncoding.PCM_FLOAT,
                inputSampleRate = 48000,
                inputChannelCount = 2,
            )
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + remainingFrames * bytesPerFrame)
            remainingFrames
        }

        val retryAccepted = deckASink.handleBuffer(buffer, 0L, 1)
        assertThat(retryAccepted).isTrue()
        assertThat(buffer.remaining()).isEqualTo(0)
    }

    @Test
    fun handleBuffer_whenHeapBufferProvided_copiesThroughDirectScratchWithoutLoss() {
        deckASink.configure(testStereoPcm16Format, 0, null)

        val frameCount = 64
        val bytesPerFrame = 2 * 2 // PCM16 stereo
        val heapBuffer = ByteBuffer.allocate(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        heapBuffer.put(ByteArray(frameCount * bytesPerFrame) { 0x42.toByte() })
        heapBuffer.flip()

        every {
            nativeEngine.writeDeckPcm(
                deckId = 0,
                buffer = any(),
                frameCount = frameCount,
                encoding = NativePcmEncoding.PCM_I16,
                inputSampleRate = 44100,
                inputChannelCount = 2,
            )
        } returns frameCount

        val accepted = deckASink.handleBuffer(heapBuffer, 0L, 1)

        assertThat(accepted).isTrue()
        assertThat(heapBuffer.position()).isEqualTo(frameCount * bytesPerFrame)
        assertThat(heapBuffer.remaining()).isEqualTo(0)
    }

    @Test
    fun handleBuffer_whenNativeEngineUnavailable_throwsWriteException() {
        deckASink.configure(testStereoFloatFormat, 0, null)
        every { nativeEngine.isAvailable } returns false

        val buffer = ByteBuffer.allocateDirect(16) // 2 stereo float frames
        buffer.put(ByteArray(16))
        buffer.flip()

        val ex = assertThrows(AudioSink.WriteException::class.java) {
            deckASink.handleBuffer(buffer, 0L, 1)
        }
        assertThat(ex.errorCode).isEqualTo(-3)
        assertThat(buffer.position()).isEqualTo(0)
        verify(exactly = 0) { nativeEngine.writeDeckPcm(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun handleBuffer_whenFrameMisaligned_throwsWriteException() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        // 8 bytes per stereo float frame. 13 bytes is misaligned.
        val buffer = ByteBuffer.allocateDirect(13)
        buffer.put(ByteArray(13))
        buffer.flip()

        val ex = assertThrows(AudioSink.WriteException::class.java) {
            deckASink.handleBuffer(buffer, 0L, 1)
        }
        assertThat(ex.errorCode).isEqualTo(-1)
        assertThat(buffer.position()).isEqualTo(0)
    }

    @Test
    fun handleBuffer_whenNativeReturnsNegativeErrorCode_throwsWriteException() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        val bytesPerFrame = Float.SIZE_BYTES * 2
        val buffer = ByteBuffer.allocateDirect(10 * bytesPerFrame)
        buffer.put(ByteArray(10 * bytesPerFrame))
        buffer.flip()

        every {
            nativeEngine.writeDeckPcm(any(), any(), any(), any(), any(), any())
        } returns -2 // Format error

        val ex = assertThrows(AudioSink.WriteException::class.java) {
            deckASink.handleBuffer(buffer, 0L, 1)
        }
        assertThat(ex.errorCode).isEqualTo(-2)
    }

    @Test
    fun playAndPause_updatesNativeDeckPlayingStateIndependently() {
        deckASink.play()
        verify(exactly = 1) { nativeEngine.setDeckPlaying(0, true) }

        deckBSink.pause()
        verify(exactly = 1) { nativeEngine.setDeckPlaying(1, false) }

        deckASink.pause()
        verify(exactly = 1) { nativeEngine.setDeckPlaying(0, false) }
    }

    @Test
    fun getCurrentPositionUs_tracksFramesConsumedByNativeRing() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        val frameCount = 48000
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val buffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(frameCount * bytesPerFrame))
        buffer.flip()

        every {
            nativeEngine.writeDeckPcm(any(), any(), any(), any(), any(), any())
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + frameCount * bytesPerFrame)
            frameCount
        }

        deckASink.handleBuffer(buffer, 0L, 1)

        // Native ring holds 24,000 frames (half a second)
        every { nativeEngine.getDeckBufferedFrames(0) } returns 24000L

        // Played frames = 48,000 - 24,000 = 24,000 frames.
        // At 48 kHz, 24,000 frames = 500,000 us (0.5 seconds).
        val positionUs = deckASink.getCurrentPositionUs(false)
        assertThat(positionUs).isEqualTo(500000L)
    }

    @Test
    fun getCurrentPositionUs_withResamplingAndWsola_calculatesCorrectMediaTime() {
        // Source is 44.1 kHz, output is 48 kHz, Deck B WSOLA is 1.25x speed
        deckBSink.configure(testStereoPcm16Format, 0, null) // 44100 Hz

        every { nativeEngine.outputSampleRate } returns 48000
        every { nativeEngine.getDeckTimeRatio(1) } returns 1.25f
        every { nativeEngine.getPresentationLatencyUs() } returns 20000L // 20 ms

        val inputFrames = 44100 // exactly 1 second of media
        val bytesPerFrame = 2 * 2
        val buffer = ByteBuffer.allocateDirect(inputFrames * bytesPerFrame).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(ByteArray(inputFrames * bytesPerFrame))
        buffer.flip()

        every {
            nativeEngine.writeDeckPcm(1, any(), any(), any(), any(), any())
        } answers {
            val buf = secondArg<ByteBuffer>()
            buf.position(buf.position() + inputFrames * bytesPerFrame)
            inputFrames
        }

        deckBSink.handleBuffer(buffer, 0L, 1)

        // Output ring holds 24,000 frames at 48 kHz (0.5 s of output)
        every { nativeEngine.getDeckBufferedFrames(1) } returns 24000L

        // writtenMediaUs = 1,000,000 us (1.0 s)
        // bufferedMediaUs = (24000 / 48000) * 1.25 * 1,000,000 = 625,000 us
        // mediaLatencyUs = 20,000 * 1.25 = 25,000 us
        // expected media position = 1,000,000 - 625,000 - 25,000 = 350,000 us
        val posUs = deckBSink.getCurrentPositionUs(false)
        assertThat(posUs).isEqualTo(350000L)
    }

    @Test
    fun flush_resetsCountersAndCallsNativeFlushDeck() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        deckASink.flush()

        verify(exactly = 1) { nativeEngine.flushDeck(0) }
        assertThat(deckASink.getCurrentPositionUs(false)).isEqualTo(0L)
    }

    @Test
    fun endOfStream_lifecycleStates_behaveCorrectly() {
        deckASink.configure(testStereoFloatFormat, 0, null)

        every { nativeEngine.getDeckBufferedFrames(0) } returns 1024L
        assertThat(deckASink.isEnded()).isFalse()
        assertThat(deckASink.hasPendingData()).isTrue()

        deckASink.playToEndOfStream()
        verify(exactly = 1) { nativeEngine.drainDeckResampler(0) }
        assertThat(deckASink.isEnded()).isFalse() // still has 1024 frames buffered
        assertThat(deckASink.hasPendingData()).isTrue()

        // Ring drains to 0, resampler and WSOLA drained
        every { nativeEngine.getDeckBufferedFrames(0) } returns 0L
        every { nativeEngine.isDeckResamplerDrained(0) } returns true
        every { nativeEngine.isDeckWsolaDrained(0) } returns true

        assertThat(deckASink.isEnded()).isTrue()
        assertThat(deckASink.hasPendingData()).isFalse()
    }

    @Test
    fun endOfStream_gatesOnResamplerAndWsolaDrain() {
        deckBSink.configure(testStereoFloatFormat, 0, null)
        deckBSink.playToEndOfStream()

        every { nativeEngine.getDeckBufferedFrames(1) } returns 0L
        every { nativeEngine.isDeckResamplerDrained(1) } returns false
        every { nativeEngine.isDeckWsolaDrained(1) } returns true

        // Even though ring buffer has 0 frames, resampler is not yet drained
        assertThat(deckBSink.isEnded()).isFalse()
        verify(atLeast = 1) { nativeEngine.drainDeckResampler(1) }

        // Once resampler drains but WSOLA is not drained
        every { nativeEngine.isDeckResamplerDrained(1) } returns true
        every { nativeEngine.isDeckWsolaDrained(1) } returns false
        assertThat(deckBSink.isEnded()).isFalse()

        // When both resampler and WSOLA are drained
        every { nativeEngine.isDeckWsolaDrained(1) } returns true
        assertThat(deckBSink.isEnded()).isTrue()
    }

    @Test
    fun setVolume_routesToNativeEngineDeckVolume() {
        deckASink.setVolume(0.75f)
        verify(exactly = 1) { nativeEngine.setDeckVolume(0, 0.75f) }

        deckBSink.setVolume(0.5f)
        verify(exactly = 1) { nativeEngine.setDeckVolume(1, 0.5f) }
    }
}
