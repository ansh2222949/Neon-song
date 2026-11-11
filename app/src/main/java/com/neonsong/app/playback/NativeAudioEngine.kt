package com.neonsong.app.playback

import android.util.Log
import com.neonsong.app.data.local.EQ_MAX_GAIN_DB
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.data.local.SpatialImmersionPreset
import java.io.Closeable
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class NativePcmEncoding(internal val nativeValue: Int, internal val bytesPerSample: Int) {
    PCM_I16(0, 2),
    PCM_I24_PACKED(1, 3),
    PCM_I32(2, 4),
    PCM_FLOAT(3, 4),
}

/**
 * Process-wide owner of the C++17 DSP/Oboe engine. Normal app playback enters
 * through [NativeProcessingAudioSink] as decoded Float32 PCM. When Oboe cannot
 * open, the same native DSP feeds the platform AudioTrack compatibility path.
 */
@Singleton
class NativeAudioEngine @Inject constructor(
    settingsPreferences: SettingsPreferences,
    equalizerPreferences: EqualizerPreferences,
    applicationScope: CoroutineScope,
) : Closeable {
    private val handleLock = Any()
    @Volatile
    private var nativeHandle = if (libraryLoaded) {
        try {
            nativeCreate()
        } catch (error: LinkageError) {
            Log.e(TAG, "Native audio engine is unavailable; using Android audio", error)
            0L
        }
    } else {
        0L
    }

    val isAvailable: Boolean
        get() = nativeHandle != 0L

    /** System audio-effects mode: while true the engine holds a flat feed
     *  and ignores pref-driven DSP pushes (they resume on restore). Set by
     *  the player, which re-pushes user prefs when the mode ends. */
    @Volatile var systemFlattened = false

    init {
        if (isAvailable) {
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.isStudioMasterClarityEnabled }
                    .distinctUntilChanged()
                    .collect { if (!systemFlattened) setStudioMasterClarity(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.clarityPreset }
                    .distinctUntilChanged()
                    .collect { if (!systemFlattened) setClarityPreset(ClarityPresets.fromIndex(it)) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.clarityAtmosBypass }
                    .distinctUntilChanged()
                    .collect { if (!systemFlattened) setClarityAtmosBypass(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.bauerCrossfeedEnabled }
                    .distinctUntilChanged()
                    .collect { setBauerCrossfeedEnabled(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.analogTubeWarmthEnabled }
                    .distinctUntilChanged()
                    .collect { setAnalogTubeWarmthEnabled(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.monoBassEnabled }
                    .distinctUntilChanged()
                    .collect { setMonoBassEnabled(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { it.spatialImmersionEnabled }
                    .distinctUntilChanged()
                    .collect { setSpatialImmersionEnabled(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                settingsPreferences.settings
                    .map { SpatialImmersionPreset.fromId(it.spatialImmersionPreset.id).strength }
                    .distinctUntilChanged()
                    .collect { setSpatialImmersionStrength(it) }
            }
            applicationScope.launch(Dispatchers.Default) {
                equalizerPreferences.settings.collect { settings ->
                    if (!systemFlattened) setEqualizer(settings.enabled, settings.gainsDb.toFloatArray())
                }
            }
        }
    }

    /** Opens a stereo Float32 Oboe output stream. Zero asks Android for its native rate. */
    fun start(preferredOutputSampleRate: Int = 0): Boolean =
        withHandle(false) { nativeStart(it, preferredOutputSampleRate.coerceAtLeast(0)) }

    fun stop() {
        withHandle(Unit) { nativeStop(it) }
    }

    /** True while an Oboe stream is open; callers can reuse it without a reopen pop. */
    val isRunning: Boolean
        get() = withHandle(false, ::nativeIsRunning)

    /**
     * Drops queued output and resets fade/prebuffer state while keeping the
     * stream open. Seeks and track transitions reuse this instead of a full
     * stop/start cycle, which is audible as a click on several OEM stacks.
     */
    fun flushOutput() {
        withHandle(Unit) { nativeFlushOutput(it) }
    }

    internal fun setPlaying(playing: Boolean) {
        withHandle(Unit) { nativeSetPlaying(it, playing) }
    }

    internal fun setOutputVolume(volume: Float) {
        withHandle(Unit) { nativeSetOutputVolume(it, volume.coerceIn(0f, 1f)) }
    }

    /** Thread-safe; native DSP crossfades wet/dry over exactly 50 ms.
     *  Dropped while [systemFlattened] — external effects own the feed. */
    fun setStudioMasterClarity(enabled: Boolean) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetStudioMasterClarity(it, enabled) }
    }

    fun setBauerCrossfeedEnabled(enabled: Boolean) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetBauerCrossfeedEnabled(it, enabled) }
    }

    fun setAnalogTubeWarmthEnabled(enabled: Boolean) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetAnalogTubeWarmthEnabled(it, enabled) }
    }

    fun setMonoBassEnabled(enabled: Boolean) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetMonoBassEnabled(it, enabled) }
    }

    fun setSpatialImmersionEnabled(enabled: Boolean) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetSpatialImmersionEnabled(it, enabled) }
    }

    fun setSpatialImmersionStrength(strength: Float) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetSpatialImmersionStrength(it, strength) }
    }

    fun setHrtfHeadScale(scale: Float) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetHrtfHeadScale(it, scale.coerceIn(0.85f, 1.18f)) }
    }

    fun getHrtfHeadScale(): Float {
        return withHandle(1.0f) { nativeGetHrtfHeadScale(it) }
    }

    fun isSpatialImmersionEnabled(): Boolean {
        return withHandle(false) { nativeIsSpatialImmersionEnabled(it) }
    }

    /** Thread-safe; completely bypasses all native DSP for bit-exact output. */
    fun setBitPerfect(enabled: Boolean) {
        withHandle(Unit) { nativeSetBitPerfect(it, enabled) }
    }

    /**
     * Enables -1.2 dBFS true-peak headroom when streaming over Bluetooth A2DP/AAC
     * to prevent psychoacoustic encoder inter-sample clipping on loud vocals/transients.
     */
    fun setBluetoothAacOptimization(enabled: Boolean) {
        withHandle(Unit) { nativeSetBtAacOptimization(it, enabled) }
    }

    /**
     * Read-back of the actual native bypass state (both DSP instances).
     * Lets callers separate bitPerfectRequested from bitPerfectActuallyActive:
     * false when the engine is unavailable or the flag never landed.
     */
    fun isBitPerfectActive(): Boolean =
        withHandle(false, ::nativeIsBitPerfect)

    /** Updates the native 16-band EQ; its gains are smoothed in C++.
     *  Dropped while [systemFlattened] — external effects own the feed. */
    fun setEqualizer(enabled: Boolean, gainsDb: FloatArray) {
        if (systemFlattened) {
            Log.i(TAG, "16-band EQ update ignored while system effects mode owns output")
            return
        }
        require(gainsDb.size == EQUALIZER_BAND_COUNT) { "Expected 16 equalizer bands" }
        val safeGains = FloatArray(gainsDb.size) { index ->
            val gain = gainsDb[index]
            if (gain.isFinite()) gain.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
        }
        withHandle(Unit) { nativeSetEqualizer(it, enabled, safeGains) }
        Log.i(
            TAG,
            "16-band EQ updated enabled=$enabled activeBands=${safeGains.count { it != 0f }}",
        )
    }

    /**
     * Clarity wet/dry mix in 0..1. Defaults to 1.0, which reproduces the
     * shipping curve sample-exactly; lower values blend toward dry.
     * Thread-safe; smoothed on the native 50 ms ramp.
     */
    fun setClarityWet(wet: Float) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetClarityWet(it, wet.coerceIn(0f, 1f)) }
    }

    /** Per-stage clarity trims in dB ([ClarityPresets.TRIM_COUNT] stages, +-12 dB). */
    fun setClarityTrims(trimsDb: FloatArray) {
        if (systemFlattened) return
        require(trimsDb.size == ClarityPresets.TRIM_COUNT) { "Expected 8 clarity trims" }
        val safeTrims = FloatArray(trimsDb.size) { index ->
            val trim = trimsDb[index]
            if (trim.isFinite()) trim.coerceIn(-CLARITY_TRIM_MAX_DB, CLARITY_TRIM_MAX_DB) else 0f
        }
        withHandle(Unit) { nativeSetClarityTrims(it, safeTrims) }
    }

    /** Applies a [ClarityPreset] by index; see [ClarityPresets] for the trim data. */
    fun setClarityPreset(preset: ClarityPreset) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetClarityPreset(it, preset.index) }
    }

    /**
     * Atmos-aware bypass: while true the native clarity chain fully bypasses
     * (multichannel-safe), independent of the on/off toggle.
     */
    fun setClarityAtmosBypass(bypass: Boolean) {
        if (systemFlattened) return
        withHandle(Unit) { nativeSetClarityAtmosBypass(it, bypass) }
    }

    internal fun configureMediaProcessor(
        inputSampleRate: Int,
        outputSampleRate: Int,
        channelCount: Int,
    ): Boolean {
        require(inputSampleRate > 0 && outputSampleRate > 0)
        require(channelCount in 1..12)
        return withHandle(false) {
            nativeConfigureMediaProcessor(it, inputSampleRate, outputSampleRate, channelCount)
        }
    }

    /** Processes decoded Media3 PCM into an interleaved direct Float32 buffer. */
    internal fun processMediaPcm(
        input: ByteBuffer,
        inputByteOffset: Int,
        output: ByteBuffer,
        outputByteOffset: Int,
        frameCount: Int,
        encoding: NativePcmEncoding,
        channelCount: Int,
    ): Int {
        require(input.isDirect && output.isDirect) { "Native PCM buffers must be direct" }
        return withHandle(-1) { handle ->
            nativeProcessMediaPcm(
                handle,
                input,
                inputByteOffset,
                output,
                outputByteOffset,
                frameCount,
                encoding.nativeValue,
                channelCount,
            )
        }
    }

    internal fun flushMediaProcessor(output: ByteBuffer, channelCount: Int): Int {
        require(output.isDirect)
        return withHandle(-1) {
            nativeFlushMediaProcessor(it, output, output.position(), channelCount)
        }
    }

    internal fun resetMediaProcessor() {
        withHandle(Unit) { nativeResetMediaProcessor(it) }
    }

    /**
     * Enqueues little-endian mono/stereo PCM from a direct [ByteBuffer]. Returns
     * input frames accepted. A short return means the producer must retry the
     * unconsumed frames after Oboe frees ring-buffer space.
     */
    fun writePcm(
        buffer: ByteBuffer,
        frameCount: Int,
        encoding: NativePcmEncoding,
        inputSampleRate: Int,
        inputChannelCount: Int,
    ): Int {
        require(buffer.isDirect) { "NativeAudioEngine requires a direct ByteBuffer" }
        require(inputChannelCount == 1 || inputChannelCount == 2) {
            "Only mono or stereo PCM is supported"
        }
        require(inputSampleRate > 0) { "inputSampleRate must be positive" }
        require(frameCount >= 0) { "frameCount must not be negative" }
        val requiredBytes = frameCount.toLong() * inputChannelCount * encoding.bytesPerSample
        require(requiredBytes <= buffer.remaining().toLong()) { "PCM buffer is too small" }
        if (frameCount == 0) return 0

        val accepted = withHandle(0) { handle ->
            nativeWritePcm(
                handle,
                buffer,
                buffer.position(),
                frameCount,
                encoding.nativeValue,
                inputSampleRate,
                inputChannelCount,
            )
        }
        if (accepted > 0) {
            buffer.position(buffer.position() + accepted * inputChannelCount * encoding.bytesPerSample)
        }
        return accepted
    }

    /**
     * Enqueues PCM into a specific deck (0 = Deck A, 1 = Deck B).
     * Returns accepted frames. A partial write indicates backpressure;
     * buffer position is advanced by accepted frames only.
     */
    fun writeDeckPcm(
        deckId: Int,
        buffer: ByteBuffer,
        frameCount: Int,
        encoding: NativePcmEncoding,
        inputSampleRate: Int,
        inputChannelCount: Int,
    ): Int {
        require(buffer.isDirect) { "NativeAudioEngine requires a direct ByteBuffer" }
        require(deckId in 0..1) { "deckId must be 0 or 1" }
        require(inputChannelCount == 1 || inputChannelCount == 2) {
            "Only mono or stereo PCM is supported"
        }
        require(inputSampleRate > 0) { "inputSampleRate must be positive" }
        require(frameCount >= 0) { "frameCount must not be negative" }
        val requiredBytes = frameCount.toLong() * inputChannelCount * encoding.bytesPerSample
        require(requiredBytes <= buffer.remaining().toLong()) { "PCM buffer is too small" }
        if (frameCount == 0) return 0

        val accepted = withHandle(-3) { handle ->
            nativeWriteDeckPcm(
                handle,
                deckId,
                buffer,
                buffer.position(),
                frameCount,
                encoding.nativeValue,
                inputSampleRate,
                inputChannelCount,
            )
        }
        if (accepted > 0) {
            buffer.position(buffer.position() + accepted * inputChannelCount * encoding.bytesPerSample)
        }
        return accepted
    }

    fun flushDeck(deckId: Int) {
        if (deckId in 0..1) {
            withHandle(Unit) { nativeFlushDeck(it, deckId) }
        }
    }

    fun getDeckBufferedFrames(deckId: Int): Long {
        return if (deckId in 0..1) {
            withHandle(0L) { nativeGetDeckBufferedFrames(it, deckId) }
        } else {
            0L
        }
    }

    fun setDeckVolume(deckId: Int, volume: Float) {
        if (deckId in 0..1) {
            withHandle(Unit) { nativeSetDeckVolume(it, deckId, volume.coerceIn(0f, 1f)) }
        }
    }

    fun setCrossfade(progress: Float) {
        withHandle(Unit) { nativeSetCrossfade(it, progress.coerceIn(0f, 1f)) }
    }

    fun getCrossfade(): Float {
        return withHandle(0f) { nativeGetCrossfade(it) }
    }

    fun setDeckPlaybackParameters(deckId: Int, speed: Float, pitch: Float) {
        if (deckId in 0..1) {
            val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
            val clampedPitch = pitch.coerceIn(0.25f, 3.0f)
            withHandle(Unit) { nativeSetDeckPlaybackParameters(it, deckId, clampedSpeed, clampedPitch) }
        }
    }

    fun getDeckSpeed(deckId: Int): Float {
        return if (deckId in 0..1) {
            withHandle(1.0f) { nativeGetDeckSpeed(it, deckId) }
        } else {
            1.0f
        }
    }

    fun getDeckPitch(deckId: Int): Float {
        return if (deckId in 0..1) {
            withHandle(1.0f) { nativeGetDeckPitch(it, deckId) }
        } else {
            1.0f
        }
    }

    fun setDeckTimeRatio(deckId: Int, ratio: Float) {
        if (deckId in 0..1) {
            withHandle(Unit) { nativeSetDeckTimeRatio(it, deckId, ratio.coerceIn(0.25f, 3.0f)) }
        }
    }

    fun getDeckTimeRatio(deckId: Int): Float {
        return if (deckId in 0..1) {
            withHandle(1.0f) { nativeGetDeckTimeRatio(it, deckId) }
        } else {
            1.0f
        }
    }

    fun setDeckPlaying(deckId: Int, playing: Boolean) {
        if (deckId in 0..1) {
            withHandle(Unit) { nativeSetDeckPlaying(it, deckId, playing) }
        }
    }

    fun isDeckPlaying(deckId: Int): Boolean {
        return if (deckId in 0..1) {
            withHandle(false) { nativeIsDeckPlaying(it, deckId) }
        } else {
            false
        }
    }

    fun drainDeckResampler(deckId: Int) {
        if (deckId in 0..1) {
            withHandle(Unit) { nativeDrainDeckResampler(it, deckId) }
        }
    }

    fun isDeckResamplerDrained(deckId: Int): Boolean {
        return if (deckId in 0..1) {
            withHandle(true) { nativeIsDeckResamplerDrained(it, deckId) }
        } else {
            true
        }
    }

    fun isDeckWsolaDrained(deckId: Int): Boolean {
        return if (deckId in 0..1) {
            withHandle(true) { nativeIsDeckWsolaDrained(it, deckId) }
        } else {
            true
        }
    }

    fun getDeckFramesRendered(deckId: Int): Long {
        return if (deckId in 0..1) {
            withHandle(0L) { nativeGetDeckFramesRendered(it, deckId) }
        } else {
            0L
        }
    }

    fun getPresentationLatencyUs(): Long {
        return withHandle(0L, ::nativeGetPresentationLatencyUs)
    }

    /** Enqueues Float32 PCM already processed at the active Oboe sample rate. */
    internal fun writeProcessedPcm(
        buffer: ByteBuffer,
        frameCount: Int,
        inputSampleRate: Int,
        inputChannelCount: Int,
    ): Int {
        require(buffer.isDirect) { "Processed PCM must use a direct ByteBuffer" }
        require(inputChannelCount in 1..2)
        require(inputSampleRate > 0)
        require(frameCount >= 0)
        val requiredBytes = frameCount.toLong() * inputChannelCount * Float.SIZE_BYTES
        require(requiredBytes <= buffer.remaining().toLong()) { "Processed PCM buffer is too small" }
        if (frameCount == 0) return 0

        val accepted = withHandle(0) { handle ->
            nativeWriteProcessedPcm(
                handle,
                buffer,
                buffer.position(),
                frameCount,
                inputSampleRate,
                inputChannelCount,
            )
        }
        if (accepted > 0) {
            buffer.position(
                buffer.position() + accepted * inputChannelCount * Float.SIZE_BYTES,
            )
        }
        return accepted
    }

    /** Drains libsoxr's delayed sinc tail at end-of-stream. */
    fun flushResampler() {
        withHandle(Unit) { nativeFlushResampler(it) }
    }

    val outputSampleRate: Int
        get() = withHandle(0, ::nativeOutputSampleRate)

    /** The rate requested when opening the current stream (0 = let Android choose). */
    val requestedSampleRate: Int
        get() = withHandle(0, ::nativeRequestedSampleRate)

    val bufferedFrames: Long
        get() = withHandle(0L, ::nativeBufferedFrames)

    val renderedFrames: Long
        get() = withHandle(0L, ::nativeRenderedFrames)

    val underrunCount: Long
        get() = withHandle(0L, ::nativeUnderrunCount)

    /** Streams opened since process start — cumulative across stop/start cycles. */
    val streamOpenCount: Long
        get() = withHandle(0L, ::nativeStreamOpenCount)

    /** Fatal stream errors that triggered an automatic in-place rebuild. */
    val streamRestartCount: Long
        get() = withHandle(0L, ::nativeStreamRestartCount)

    /** Times the device substituted a different rate than requested. */
    val rateAdaptationCount: Long
        get() = withHandle(0L, ::nativeRateAdaptationCount)

    /** Atomically reads and resets the peak-held telemetry for Music Haptics. */
    fun consumeHapticTelemetry(): Int = withHandle(0, ::nativeConsumeHapticTelemetry)

    override fun close() {
        synchronized(handleLock) {
            val handle = nativeHandle
            nativeHandle = 0L
            if (handle != 0L) {
                try {
                    nativeDestroy(handle)
                } catch (error: LinkageError) {
                    Log.e(TAG, "Could not release native audio engine", error)
                }
            }
        }
    }

    private inline fun <T> withHandle(fallback: T, block: (Long) -> T): T {
        return synchronized(handleLock) {
            val handle = nativeHandle
            if (handle == 0L) {
                fallback
            } else {
                try {
                    block(handle)
                } catch (error: LinkageError) {
                    // A stale/split APK can load the library yet still miss an
                    // individual JNI symbol. Disable native processing for the
                    // rest of this process instead of crashing playback/UI.
                    nativeHandle = 0L
                    Log.e(TAG, "Native audio call failed; falling back to Android audio", error)
                    fallback
                }
            }
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long, preferredOutputSampleRate: Int): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeIsRunning(handle: Long): Boolean
    private external fun nativeFlushOutput(handle: Long)
    private external fun nativeSetPlaying(handle: Long, playing: Boolean)
    private external fun nativeSetOutputVolume(handle: Long, volume: Float)
    private external fun nativeSetStudioMasterClarity(handle: Long, enabled: Boolean)
    private external fun nativeSetBauerCrossfeedEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetAnalogTubeWarmthEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetMonoBassEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetSpatialImmersionEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetSpatialImmersionStrength(handle: Long, strength: Float)
    private external fun nativeSetHrtfHeadScale(handle: Long, scale: Float)
    private external fun nativeGetHrtfHeadScale(handle: Long): Float
    private external fun nativeIsSpatialImmersionEnabled(handle: Long): Boolean
    private external fun nativeSetBitPerfect(handle: Long, enabled: Boolean)
    private external fun nativeSetBtAacOptimization(handle: Long, enabled: Boolean)
    private external fun nativeIsBitPerfect(handle: Long): Boolean
    private external fun nativeSetEqualizer(handle: Long, enabled: Boolean, gainsDb: FloatArray)
    private external fun nativeSetClarityWet(handle: Long, wet: Float)
    private external fun nativeSetClarityTrims(handle: Long, trimsDb: FloatArray)
    private external fun nativeSetClarityPreset(handle: Long, preset: Int)
    private external fun nativeSetClarityAtmosBypass(handle: Long, bypass: Boolean)
    private external fun nativeConfigureMediaProcessor(
        handle: Long,
        inputSampleRate: Int,
        outputSampleRate: Int,
        channelCount: Int,
    ): Boolean
    private external fun nativeProcessMediaPcm(
        handle: Long,
        input: ByteBuffer,
        inputByteOffset: Int,
        output: ByteBuffer,
        outputByteOffset: Int,
        frameCount: Int,
        encoding: Int,
        channelCount: Int,
    ): Int
    private external fun nativeFlushMediaProcessor(
        handle: Long,
        output: ByteBuffer,
        outputByteOffset: Int,
        channelCount: Int,
    ): Int
    private external fun nativeResetMediaProcessor(handle: Long)
    private external fun nativeWritePcm(
        handle: Long,
        buffer: ByteBuffer,
        byteOffset: Int,
        frameCount: Int,
        encoding: Int,
        inputSampleRate: Int,
        inputChannelCount: Int,
    ): Int
    private external fun nativeWriteDeckPcm(
        handle: Long,
        deckId: Int,
        buffer: ByteBuffer,
        byteOffset: Int,
        frameCount: Int,
        encoding: Int,
        inputSampleRate: Int,
        inputChannelCount: Int,
    ): Int
    private external fun nativeFlushDeck(handle: Long, deckId: Int)
    private external fun nativeGetDeckBufferedFrames(handle: Long, deckId: Int): Long
    private external fun nativeSetDeckVolume(handle: Long, deckId: Int, volume: Float)
    private external fun nativeSetCrossfade(handle: Long, progress: Float)
    private external fun nativeGetCrossfade(handle: Long): Float
    private external fun nativeSetDeckPlaybackParameters(handle: Long, deckId: Int, speed: Float, pitch: Float)
    private external fun nativeGetDeckSpeed(handle: Long, deckId: Int): Float
    private external fun nativeGetDeckPitch(handle: Long, deckId: Int): Float
    private external fun nativeSetDeckTimeRatio(handle: Long, deckId: Int, ratio: Float)
    private external fun nativeGetDeckTimeRatio(handle: Long, deckId: Int): Float
    private external fun nativeSetDeckPlaying(handle: Long, deckId: Int, playing: Boolean)
    private external fun nativeIsDeckPlaying(handle: Long, deckId: Int): Boolean
    private external fun nativeDrainDeckResampler(handle: Long, deckId: Int)
    private external fun nativeIsDeckResamplerDrained(handle: Long, deckId: Int): Boolean
    private external fun nativeIsDeckWsolaDrained(handle: Long, deckId: Int): Boolean
    private external fun nativeGetDeckFramesRendered(handle: Long, deckId: Int): Long
    private external fun nativeGetPresentationLatencyUs(handle: Long): Long
    private external fun nativeWriteProcessedPcm(
        handle: Long,
        buffer: ByteBuffer,
        byteOffset: Int,
        frameCount: Int,
        inputSampleRate: Int,
        inputChannelCount: Int,
    ): Int
    private external fun nativeFlushResampler(handle: Long)
    private external fun nativeOutputSampleRate(handle: Long): Int
    private external fun nativeRequestedSampleRate(handle: Long): Int
    private external fun nativeBufferedFrames(handle: Long): Long
    private external fun nativeUnderrunCount(handle: Long): Long
    private external fun nativeRenderedFrames(handle: Long): Long
    private external fun nativeStreamOpenCount(handle: Long): Long
    private external fun nativeStreamRestartCount(handle: Long): Long
    private external fun nativeRateAdaptationCount(handle: Long): Long
    private external fun nativeConsumeHapticTelemetry(handle: Long): Int

    companion object {
        const val TAG = "NativeAudioEngine"
        const val EQUALIZER_BAND_COUNT = 16
        const val CLARITY_TRIM_MAX_DB = 12f
        const val HRTF_HEAD_SCALE_MIN = 0.85f
        const val HRTF_HEAD_SCALE_DEFAULT = 1.00f
        const val HRTF_HEAD_SCALE_MAX = 1.18f
        const val HRTF_HEAD_PRESET_SMALL = 0.88f
        const val HRTF_HEAD_PRESET_STANDARD = 1.00f
        const val HRTF_HEAD_PRESET_WIDE = 1.12f

        val libraryLoaded = try {
            System.loadLibrary("neonsong_audio")
            true
        } catch (error: LinkageError) {
            Log.e(TAG, "Could not load native audio library", error)
            false
        } catch (error: SecurityException) {
            Log.e(TAG, "Native audio loading was blocked", error)
            false
        }
    }
}
