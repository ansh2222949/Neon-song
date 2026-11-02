@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.neonsong.app.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dedicated Media3 [AudioSink] for a single deck (e.g. Deck A = 0, Deck B = 1).
 *
 * Captures decoded PCM directly from ExoPlayer's audio renderer pipeline and transfers
 * it through JNI into the native C++ engine's independent SPSC ring buffer for that deck.
 *
 * Guarantees:
 * 1. Zero platform AudioTrack output: audio flows exclusively through the native C++/Oboe pipeline.
 * 2. Backpressure preservation: advances [ByteBuffer.position] only by accepted frames; returns false
 *    on partial write so ExoPlayer retries without frame drop or duplication.
 * 3. Exact format negotiation for PCM16, PCM24, PCM32, and Float32.
 * 4. Smooth monotonic presentation position tracking calculated from native consumed frames.
 */
class NativeDeckAudioSink(
    val deckId: Int,
    private val nativeEngine: NativeAudioEngine,
) : AudioSink {

    init {
        require(deckId in 0..1) { "deckId must be 0 (Deck A) or 1 (Deck B)" }
    }

    private var listener: AudioSink.Listener? = null
    private var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    private var audioAttributes: AudioAttributes = AudioAttributes.DEFAULT

    @Volatile
    private var isPlaying = false

    @Volatile
    private var isConfigured = false

    private var currentFormat: Format? = null
    private var sampleRate: Int = 0
    private var channelCount: Int = 0
    private var pcmEncoding: Int = C.ENCODING_INVALID
    private var bytesPerFrame: Int = 0
    private var nativeEncoding: NativePcmEncoding = NativePcmEncoding.PCM_FLOAT
    private var totalFramesWritten: Long = 0L

    val inputSampleRate: Int get() = sampleRate
    val inputChannelCount: Int get() = channelCount
    val inputPcmEncoding: Int get() = pcmEncoding
    val format: Format? get() = currentFormat
    val framesWritten: Long get() = totalFramesWritten

    @Volatile
    private var eosQueued: Boolean = false

    @Volatile
    private var emptyBufferTimestampMs: Long = 0L

    private var heapScratchDirectBuffer: ByteBuffer? = null

    var onConfiguredFormat: ((sampleRateHz: Int, pcmEncoding: Int, channelCount: Int) -> Unit)? = null

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun setClock(clock: Clock) {
        // No-op: timing is tied to Oboe's native clock domain
    }

    override fun supportsFormat(format: Format): Boolean {
        if (!nativeEngine.isAvailable) return false
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return false
        val encodingSupported = when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> true
            else -> false
        }
        val channelsSupported = format.channelCount in 1..2
        val sampleRateSupported = format.sampleRate > 0
        return encodingSupported && channelsSupported && sampleRateSupported
    }

    override fun getFormatSupport(format: Format): Int {
        return if (supportsFormat(format)) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        }
    }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport {
        return AudioOffloadSupport.DEFAULT_UNSUPPORTED
    }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        if (!supportsFormat(inputFormat)) {
            throw AudioSink.ConfigurationException("Unsupported format for NativeDeckAudioSink: $inputFormat", inputFormat)
        }

        currentFormat = inputFormat
        sampleRate = inputFormat.sampleRate
        channelCount = inputFormat.channelCount
        pcmEncoding = inputFormat.pcmEncoding

        nativeEncoding = when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> NativePcmEncoding.PCM_I16
            C.ENCODING_PCM_24BIT -> NativePcmEncoding.PCM_I24_PACKED
            C.ENCODING_PCM_32BIT -> NativePcmEncoding.PCM_I32
            C.ENCODING_PCM_FLOAT -> NativePcmEncoding.PCM_FLOAT
            else -> NativePcmEncoding.PCM_FLOAT
        }

        bytesPerFrame = nativeEncoding.bytesPerSample * channelCount
        totalFramesWritten = 0L
        eosQueued = false
        emptyBufferTimestampMs = 0L
        isConfigured = true

        nativeEngine.setDeckPlaybackParameters(deckId, playbackParameters.speed, playbackParameters.pitch)
        onConfiguredFormat?.invoke(sampleRate, pcmEncoding, channelCount)
    }

    @Volatile
    var isNativeConsuming: Boolean = true
        set(value) {
            field = value
            nativeEngine.setDeckPlaying(deckId, value && isPlaying)
        }

    override fun play() {
        isPlaying = true
        nativeEngine.setDeckPlaying(deckId, isNativeConsuming)
    }

    override fun pause() {
        isPlaying = false
        nativeEngine.setDeckPlaying(deckId, false)
    }

    override fun handleDiscontinuity() {
        // Discontinuity in stream, position sync
        listener?.onPositionDiscontinuity()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!isConfigured || bytesPerFrame == 0) return false
        if (!buffer.hasRemaining()) return true

        val remainingBytes = buffer.remaining()
        // Priority 7: Validate PCM frame alignment
        if (remainingBytes % bytesPerFrame != 0) {
            throw AudioSink.WriteException(
                /* errorCode = */ -1, // Invalid buffer / misaligned PCM frame
                currentFormat ?: Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).build(),
                /* isRecoverable = */ false,
            )
        }

        val requestedFrames = remainingBytes / bytesPerFrame
        if (requestedFrames == 0) {
            buffer.position(buffer.limit())
            return true
        }

        if (!nativeEngine.isAvailable) {
            // Priority 5: Fatal error when native engine is unavailable
            throw AudioSink.WriteException(
                /* errorCode = */ -3,
                currentFormat ?: Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).build(),
                /* isRecoverable = */ true,
            )
        }

        val directPosition = buffer.position()
        val isDirectAligned = buffer.isDirect && (directPosition % nativeEncoding.bytesPerSample == 0)

        val acceptedFrames: Int
        if (isDirectAligned) {
            acceptedFrames = nativeEngine.writeDeckPcm(
                deckId = deckId,
                buffer = buffer,
                frameCount = requestedFrames,
                encoding = nativeEncoding,
                inputSampleRate = sampleRate,
                inputChannelCount = channelCount,
            )
        } else {
            val scratch = getOrCreateHeapDirectScratch(remainingBytes)
            scratch.clear()
            val originalPosition = buffer.position()
            val framesToCopy = minOf(requestedFrames, scratch.capacity() / bytesPerFrame)
            val bytesToCopy = framesToCopy * bytesPerFrame

            val limit = buffer.limit()
            buffer.limit(originalPosition + bytesToCopy)
            scratch.put(buffer)
            buffer.limit(limit)
            scratch.flip()

            acceptedFrames = nativeEngine.writeDeckPcm(
                deckId = deckId,
                buffer = scratch,
                frameCount = framesToCopy,
                encoding = nativeEncoding,
                inputSampleRate = sampleRate,
                inputChannelCount = channelCount,
            )
            // Advance original buffer only by accepted frames
            if (acceptedFrames > 0) {
                buffer.position(originalPosition + acceptedFrames * bytesPerFrame)
            } else {
                buffer.position(originalPosition)
            }
        }

        // Priority 5: Distinguish negative error codes from backpressure (0)
        if (acceptedFrames < 0) {
            throw AudioSink.WriteException(
                /* errorCode = */ acceptedFrames,
                currentFormat ?: Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).build(),
                /* isRecoverable = */ acceptedFrames == -3,
            )
        }

        if (acceptedFrames > 0) {
            totalFramesWritten += acceptedFrames
            emptyBufferTimestampMs = 0L
        }

        return acceptedFrames == requestedFrames
    }

    private fun getOrCreateHeapDirectScratch(requiredBytes: Int): ByteBuffer {
        val existing = heapScratchDirectBuffer
        if (existing == null || existing.capacity() < requiredBytes) {
            val capacity = maxOf(requiredBytes, 16384)
            val newBuf = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
            heapScratchDirectBuffer = newBuf
            return newBuf
        }
        return existing
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!isConfigured || sampleRate <= 0) return AudioSink.CURRENT_POSITION_NOT_SET
        if (totalFramesWritten == 0L) return 0L

        val writtenMediaUs = (totalFramesWritten * 1_000_000L) / sampleRate
        if (sourceEnded && isEnded()) {
            return writtenMediaUs
        }

        val speed = if (playbackParameters.speed != 1.0f) {
            playbackParameters.speed.toDouble().coerceIn(0.25, 3.0)
        } else {
            val ratio = nativeEngine.getDeckTimeRatio(deckId)
            if (ratio > 0f) ratio.toDouble().coerceIn(0.25, 3.0) else 1.0
        }

        val renderedFrames = nativeEngine.getDeckFramesRendered(deckId)
        val outputRate = nativeEngine.outputSampleRate.takeIf { it > 0 } ?: sampleRate
        val latencyUs = nativeEngine.getPresentationLatencyUs()

        if (renderedFrames > 0L) {
            val renderedMediaUs = ((renderedFrames.toDouble() / outputRate.toDouble()) * speed * 1_000_000.0).toLong()
            val mediaLatencyUs = (latencyUs.toDouble() * speed).toLong()
            val playedUs = maxOf(0L, renderedMediaUs - mediaLatencyUs)
            return minOf(writtenMediaUs, playedUs)
        }

        val bufferedOutputFrames = nativeEngine.getDeckBufferedFrames(deckId)
        val bufferedMediaUs = ((bufferedOutputFrames.toDouble() / outputRate.toDouble()) * speed * 1_000_000.0).toLong()
        val mediaLatencyUs = (latencyUs.toDouble() * speed).toLong()
        val playedUs = writtenMediaUs - bufferedMediaUs - mediaLatencyUs
        return maxOf(0L, minOf(writtenMediaUs, playedUs))
    }

    override fun flush() {
        nativeEngine.flushDeck(deckId)
        totalFramesWritten = 0L
        eosQueued = false
        emptyBufferTimestampMs = 0L
    }

    override fun playToEndOfStream() {
        if (!eosQueued) {
            eosQueued = true
            if (isConfigured) {
                nativeEngine.drainDeckResampler(deckId)
            }
        }
    }

    override fun isEnded(): Boolean {
        if (!eosQueued || !isConfigured) return false
        if (nativeEngine.getDeckBufferedFrames(deckId) > 0L) return false

        if (!nativeEngine.isDeckResamplerDrained(deckId)) {
            nativeEngine.drainDeckResampler(deckId)
            if (!nativeEngine.isDeckResamplerDrained(deckId)) return false
            if (nativeEngine.getDeckBufferedFrames(deckId) > 0L) return false
        }

        if (!nativeEngine.isDeckWsolaDrained(deckId)) return false

        val latencyUs = nativeEngine.getPresentationLatencyUs()
        if (latencyUs > 0) {
            val nowMs = System.currentTimeMillis()
            if (emptyBufferTimestampMs == 0L) {
                emptyBufferTimestampMs = nowMs
                return false
            }
            val elapsedUs = (nowMs - emptyBufferTimestampMs) * 1000L
            if (elapsedUs < latencyUs) {
                return false
            }
        }

        return true
    }

    override fun hasPendingData(): Boolean {
        if (!isConfigured) return false
        return nativeEngine.getDeckBufferedFrames(deckId) > 0L ||
            !nativeEngine.isDeckResamplerDrained(deckId) ||
            !nativeEngine.isDeckWsolaDrained(deckId)
    }

    override fun reset() {
        flush()
        isConfigured = false
        currentFormat = null
        sampleRate = 0
        channelCount = 0
        pcmEncoding = C.ENCODING_INVALID
        bytesPerFrame = 0
        heapScratchDirectBuffer = null
    }

    override fun release() {
        reset()
    }

    override fun setVolume(volume: Float) {
        nativeEngine.setDeckVolume(deckId, volume)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
        nativeEngine.setDeckPlaybackParameters(deckId, playbackParameters.speed, playbackParameters.pitch)
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        // WSOLA / DSP handles stretch and transitions natively
    }

    override fun getSkipSilenceEnabled(): Boolean = false

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {
        // Output device routing is handled by Oboe
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        // DSP is handled natively
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        // Output device is handled by Oboe
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        // Timing handled natively
    }

    override fun enableTunnelingV21() {
        // Tunneling not used with custom Oboe sink
    }

    override fun disableTunneling() {
        // Tunneling not used with custom Oboe sink
    }

    override fun setOffloadMode(offloadMode: Int) {
        // Offload not used with custom Oboe sink
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) {
        // Padding not applicable for direct PCM stream
    }
}
