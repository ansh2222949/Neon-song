package com.neonsong.app.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class NativeProcessingAudioSinkBackpressureTest {

    private lateinit var enhancedDelegate: DefaultAudioSink
    private lateinit var fallbackDelegate: DefaultAudioSink
    private lateinit var processor: NativePcmAudioProcessor
    private lateinit var autoMixEngine: NativeAutoMixEngine
    private lateinit var sink: NativeProcessingAudioSink

    private val testStereoFormat = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_FLOAT)
        .setSampleRate(48000)
        .setChannelCount(2)
        .build()

    private val testMonoFormat = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_FLOAT)
        .setSampleRate(48000)
        .setChannelCount(1)
        .build()

    @Before
    fun setUp() {
        enhancedDelegate = mockk(relaxed = true)
        fallbackDelegate = mockk(relaxed = true)
        processor = mockk(relaxed = true)
        autoMixEngine = mockk(relaxed = true)

        every { autoMixEngine.isAvailable } returns true
        every { processor.isAvailable } returns true
        every { processor.nativeOutputSampleRate } returns 48000
        every { processor.outputSampleRateFor(any()) } returns 48000
        every { processor.configure(any()) } answers {
            val arg = firstArg<AudioProcessor.AudioFormat>()
            AudioProcessor.AudioFormat(48000, arg.channelCount, C.ENCODING_PCM_FLOAT)
        }
        every { enhancedDelegate.getFormatSupport(any()) } returns AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

        sink = NativeProcessingAudioSink(
            enhancedDelegate = enhancedDelegate,
            fallbackDelegate = fallbackDelegate,
            processor = processor,
            autoMixEngine = autoMixEngine,
            standbyAutoMixRole = true, // Standby Deck B
        )
        sink.setAutoMixEnabled(true)
    }

    @Test
    fun handleBuffer_whenStandbyRingBufferFull_returnsFalseAndDoesNotAdvanceInput() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput
        every { autoMixEngine.writeDeckBPcm(any(), any()) } returns 0

        val accepted = sink.handleBuffer(inputBuffer, 0L, 1)

        assertThat(accepted).isFalse()
        assertThat(inputBuffer.position()).isEqualTo(0)
    }

    @Test
    fun handleBuffer_whenStandbyRingBufferAcceptsAll_returnsTrueAndAdvancesInput() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput
        every { autoMixEngine.writeDeckBPcm(any(), any()) } returns frameCount

        val accepted = sink.handleBuffer(inputBuffer, 0L, 1)

        assertThat(accepted).isTrue()
        assertThat(inputBuffer.position()).isEqualTo(frameCount * bytesPerFrame)
    }

    @Test
    fun handleBuffer_partialAcceptanceAndRetry_advancesCorrectlyWithoutDataLoss() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput

        every { autoMixEngine.writeDeckBPcm(any(), 100) } returns 40

        val firstCall = sink.handleBuffer(inputBuffer, 0L, 1)
        assertThat(firstCall).isFalse()
        assertThat(inputBuffer.position()).isEqualTo(0)

        every { autoMixEngine.writeDeckBPcm(any(), 60) } returns 60

        val secondCall = sink.handleBuffer(inputBuffer, 0L, 1)
        assertThat(secondCall).isTrue()
        assertThat(inputBuffer.position()).isEqualTo(frameCount * bytesPerFrame)
    }

    @Test
    fun handleBuffer_multipleRetries_eventuallyConsumesAllFramesWithoutDuplication() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput

        every { autoMixEngine.writeDeckBPcm(any(), 100) } returns 30
        assertThat(sink.handleBuffer(inputBuffer, 0L, 1)).isFalse()
        assertThat(inputBuffer.position()).isEqualTo(0)

        every { autoMixEngine.writeDeckBPcm(any(), 70) } returns 30
        assertThat(sink.handleBuffer(inputBuffer, 0L, 1)).isFalse()
        assertThat(inputBuffer.position()).isEqualTo(0)

        every { autoMixEngine.writeDeckBPcm(any(), 40) } returns 40
        assertThat(sink.handleBuffer(inputBuffer, 0L, 1)).isTrue()
        assertThat(inputBuffer.position()).isEqualTo(frameCount * bytesPerFrame)

        verify(exactly = 3) { autoMixEngine.writeDeckBPcm(any(), any()) }
    }

    @Test
    fun handleBuffer_noDuplicatePcm_preservesSampleContinuity() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 10
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frameCount) {
            inputBuffer.putFloat(i.toFloat())
            inputBuffer.putFloat(i.toFloat())
        }
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            processedOutput.putFloat(i.toFloat())
            processedOutput.putFloat(i.toFloat())
        }
        processedOutput.flip()
        every { processor.getOutput() } returns processedOutput

        val capturedSamples = mutableListOf<Float>()
        every { autoMixEngine.writeDeckBPcm(any(), any()) } answers {
            val buf = firstArg<ByteBuffer>()
            val framesToRead = secondArg<Int>()
            val startPos = buf.position()
            val firstSample = buf.getFloat(startPos)
            capturedSamples.add(firstSample)
            if (framesToRead == 10) 4 else framesToRead
        }

        assertThat(sink.handleBuffer(inputBuffer, 0L, 1)).isFalse()
        assertThat(sink.handleBuffer(inputBuffer, 0L, 1)).isTrue()

        assertThat(capturedSamples).containsExactly(0.0f, 4.0f).inOrder()
    }

    @Test
    fun handleBuffer_whenAutoMixEngineMissing_doesNotSilentlyDiscardAudio() {
        val noEngineSink = NativeProcessingAudioSink(
            enhancedDelegate = enhancedDelegate,
            fallbackDelegate = fallbackDelegate,
            processor = processor,
            autoMixEngine = null,
            standbyAutoMixRole = true,
        )
        noEngineSink.setAutoMixEnabled(true)
        noEngineSink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput

        val accepted = noEngineSink.handleBuffer(inputBuffer, 0L, 1)

        // Must NOT consume buffer silently!
        assertThat(accepted).isFalse()
        assertThat(inputBuffer.position()).isEqualTo(0)
    }

    @Test
    fun handleBuffer_monoPcm_upmixesToStereoAndIngestsCorrectly() {
        sink.configure(testMonoFormat, 0, null)

        val frameCount = 50
        val bytesPerFrame = Float.SIZE_BYTES // 4 bytes for mono
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            inputBuffer.putFloat(i.toFloat())
        }
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            processedOutput.putFloat(i.toFloat())
        }
        processedOutput.flip()
        every { processor.getOutput() } returns processedOutput

        var receivedDirect = false
        var verifiedStereoSamples = false

        every { autoMixEngine.writeDeckBPcm(any(), any()) } answers {
            val buf = firstArg<ByteBuffer>()
            val frames = secondArg<Int>()
            receivedDirect = buf.isDirect
            // Verify stereo upmix: sample 0 has L == 0.0f, R == 0.0f; sample 1 has L == 1.0f, R == 1.0f
            val l0 = buf.getFloat(0)
            val r0 = buf.getFloat(4)
            val l1 = buf.getFloat(8)
            val r1 = buf.getFloat(12)
            verifiedStereoSamples = (l0 == 0.0f && r0 == 0.0f && l1 == 1.0f && r1 == 1.0f)
            frames
        }

        val accepted = sink.handleBuffer(inputBuffer, 0L, 1)

        assertThat(accepted).isTrue()
        assertThat(receivedDirect).isTrue()
        assertThat(verifiedStereoSamples).isTrue()
        assertThat(inputBuffer.position()).isEqualTo(frameCount * bytesPerFrame)
    }

    @Test
    fun handleBuffer_nonDirectByteBuffer_copiesToDirectAndIngestsWithoutDiscard() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 50
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        // Heap non-direct buffer returned by processor
        val heapOutput = ByteBuffer.allocate(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        heapOutput.put(FloatArray(frameCount * 2) { 0.7f }.let { fa ->
            val bb = ByteBuffer.allocate(fa.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            fa.forEach { bb.putFloat(it) }
            bb.array()
        })
        heapOutput.flip()
        assertThat(heapOutput.isDirect).isFalse()

        every { processor.getOutput() } returns heapOutput

        var receivedDirect = false
        every { autoMixEngine.writeDeckBPcm(any(), any()) } answers {
            val buf = firstArg<ByteBuffer>()
            val frames = secondArg<Int>()
            receivedDirect = buf.isDirect
            frames
        }

        val accepted = sink.handleBuffer(inputBuffer, 0L, 1)

        assertThat(accepted).isTrue()
        assertThat(receivedDirect).isTrue()
        assertThat(inputBuffer.position()).isEqualTo(frameCount * bytesPerFrame)
    }

    @Test
    fun handleBuffer_invalidNativeWrittenFrames_isCoercedAndDoesNotCorruptBuffer() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput

        // Native engine returns negative error code (-1)
        every { autoMixEngine.writeDeckBPcm(any(), 100) } returns -1

        val firstCall = sink.handleBuffer(inputBuffer, 0L, 1)
        // Must not advance position when negative
        assertThat(firstCall).isFalse()
        assertThat(inputBuffer.position()).isEqualTo(0)

        // Native engine returns impossible large frame count (999 > 100)
        every { autoMixEngine.writeDeckBPcm(any(), 100) } returns 999

        val secondCall = sink.handleBuffer(inputBuffer, 0L, 1)
        // Must be coerced to frameCount and not throw buffer overflow
        assertThat(secondCall).isTrue()
        assertThat(inputBuffer.position()).isEqualTo(frameCount * bytesPerFrame)
    }

    @Test
    fun flush_standbyRole_resetsNativeAutoMixEngineRingBuffer() {
        sink.configure(testStereoFormat, 0, null)

        val frameCount = 100
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.put(ByteArray(frameCount * bytesPerFrame))
        inputBuffer.flip()

        val processedOutput = ByteBuffer.allocateDirect(frameCount * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        every { processor.getOutput() } returns processedOutput
        every { autoMixEngine.writeDeckBPcm(any(), any()) } returns 0

        sink.handleBuffer(inputBuffer, 0L, 1)
        assertThat(sink.hasPendingData()).isTrue()

        // Flush clears pending data AND resets native AutoMixEngine
        sink.flush()

        assertThat(sink.hasPendingData()).isFalse()
        verify(atLeast = 1) { autoMixEngine.reset() }
    }

    @Test
    fun playToEndOfStream_tailPartialAcceptance_retriesCorrectly() {
        sink.configure(testStereoFormat, 0, null)

        val tailFrames = 60
        val bytesPerFrame = Float.SIZE_BYTES * 2
        val tailBuffer = ByteBuffer.allocateDirect(tailFrames * bytesPerFrame)
            .order(ByteOrder.LITTLE_ENDIAN)
        tailBuffer.put(ByteArray(tailFrames * bytesPerFrame))
        tailBuffer.flip()

        every { processor.getOutput() } returns tailBuffer

        // Call 1: accepts 25 of 60 frames
        every { autoMixEngine.writeDeckBPcm(any(), 60) } returns 25
        sink.playToEndOfStream()
        assertThat(sink.isEnded()).isFalse()

        // Call 2: accepts remaining 35 frames
        every { autoMixEngine.writeDeckBPcm(any(), 35) } returns 35
        every { enhancedDelegate.isEnded() } returns true
        sink.playToEndOfStream()
        assertThat(sink.isEnded()).isTrue()
    }
}
