package com.neonsong.app.playback

import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Detailed per-deck diagnostic metrics capturing format, timeline, buffer occupancy, and processing state.
 */
data class DeckDiagnostics(
    val deckId: Int,
    val trackId: String,
    val codec: String,
    val inputSampleRate: Int,
    val inputChannelCount: Int,
    val inputPcmEncoding: Int,
    val outputSampleRate: Int,
    val speed: Float,
    val pitch: Float,
    val exoPositionMs: Long,
    val nativeConsumedFrames: Long,
    val ringBufferOccupancyFrames: Long,
    val acceptedFrames: Long,
    val isPlaying: Boolean,
    val gain: Float,
    val exoPlaybackState: Int,
)

/**
 * Diagnostics snapshot capturing real-time dual-deck state during crossfades and prebuffering.
 */
data class CrossfadeDiagnostics(
    val deckABufferedFrames: Long,
    val deckBBufferedFrames: Long,
    val playerAPlaybackState: Int,
    val playerAPlayWhenReady: Boolean,
    val playerAIsPlaying: Boolean,
    val playerBPlaybackState: Int,
    val playerBPlayWhenReady: Boolean,
    val playerBIsPlaying: Boolean,
    val nativeDeckAPlaying: Boolean,
    val nativeDeckBPlaying: Boolean,
    val deckAGain: Float,
    val deckBGain: Float,
    val crossfadeStartTimeMs: Long,
    val crossfadeCompletionTimeMs: Long,
    val nativeUnderrunCount: Long,
    val isStarved: Boolean = false,
    val starvationTimestampMs: Long? = null,
    val deckA: DeckDiagnostics? = null,
    val deckB: DeckDiagnostics? = null,
)

enum class CrossfadeState {
    IDLE,
    PREBUFFERING,
    CROSSFADING,
    COMPLETED,
    CANCELLED,
}

/**
 * Seamless Dual-Deck Crossfade Controller for Neon Song.
 *
 * Implements genuine simultaneous dual-deck playback with overlapping audio transitions:
 * 1. ExoPlayer decoding state is decoupled from native ring buffer consumption.
 * 2. Standby deck prebuffers decoded PCM in its native ring buffer while remaining inaudible (volume = 0.0)
 *    and unconsumed (isNativeConsuming = false).
 * 3. Crossfade progression uses an equal-power law (cos/sin) driven by a stable monotonic clock.
 * 4. Both decks are consumed simultaneously by the SAME Oboe audio callback during overlap.
 * 5. Outgoing deck stops only after crossfade completes; incoming deck continues without restart or silence gap.
 */
class DualDeckCrossfadeController(
    val dualDeckPlayer: DualDeckAudioPlayer,
    var crossfadeDurationMs: Long = 8_000L,
    var minPrebufferFrames: Long = 4800L,
    private val coroutineScope: CoroutineScope? = null,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    companion object {
        private const val TAG = "DualDeckCrossfade"
        private const val TICK_INTERVAL_MS = 20L
    }

    var activeDeck: Int = DualDeckAudioPlayer.DECK_A
        private set

    val standbyDeck: Int
        get() = if (activeDeck == DualDeckAudioPlayer.DECK_A) {
            DualDeckAudioPlayer.DECK_B
        } else {
            DualDeckAudioPlayer.DECK_A
        }

    val activePlayer: ExoPlayer
        get() = dualDeckPlayer.playerFor(activeDeck)

    val standbyPlayer: ExoPlayer
        get() = dualDeckPlayer.playerFor(standbyDeck)

    val activeSink: NativeDeckAudioSink
        get() = dualDeckPlayer.sinkFor(activeDeck)

    val standbySink: NativeDeckAudioSink
        get() = dualDeckPlayer.sinkFor(standbyDeck)

    var state: CrossfadeState = CrossfadeState.IDLE
        private set

    var crossfadeProgress: Float = 0f
        private set

    var crossfadeStartTimeMs: Long = 0L
        private set

    var crossfadeCompletionTimeMs: Long = 0L
        private set

    var currentDeckAGain: Float = 1.0f
        private set

    var currentDeckBGain: Float = 0.0f
        private set

    var isStarved: Boolean = false
        private set

    var starvationStartTimeMs: Long = 0L
        private set

    var totalStarvationDurationMs: Long = 0L
        private set

    var onCrossfadeCompleted: ((newActiveDeck: Int) -> Unit)? = null
    var onDiagnosticsLogged: ((CrossfadeDiagnostics) -> Unit)? = null

    private var crossfadeJob: Job? = null
    private var pausedElapsedMs: Long = 0L
    private var isCrossfadePaused: Boolean = false

    init {
        // Initially set Deck A as active and consuming at full volume, Deck B silent and paused
        dualDeckPlayer.setDeckConsuming(DualDeckAudioPlayer.DECK_A, true)
        dualDeckPlayer.setDeckConsuming(DualDeckAudioPlayer.DECK_B, false)
        dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_A, 1.0f)
        dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_B, 0.0f)
        dualDeckPlayer.setCrossfade(0.0f)
    }

    /**
     * Calculates the effective required prebuffer frames dynamically based on the deck's input sample rate.
     * Ensures adequate buffer headroom across standard rates (44.1k/48k) and high-res lossless (96k).
     */
    fun getEffectivePrebufferFrames(deckId: Int): Long {
        val sampleRate = dualDeckPlayer.sinkFor(deckId).inputSampleRate
        val targetMs = 200L // 200ms target prebuffer headroom prevents transition underrun
        val framesFromRate = if (sampleRate > 0) (sampleRate.toLong() * targetMs) / 1000L else 4800L
        return maxOf(minPrebufferFrames, framesFromRate)
    }

    /**
     * Returns the minimum safe buffer headroom required during active crossfading (~40ms).
     */
    fun getSafeHeadroomFrames(deckId: Int): Long {
        val sampleRate = dualDeckPlayer.sinkFor(deckId).inputSampleRate
        val targetMs = 40L
        val framesFromRate = if (sampleRate > 0) (sampleRate.toLong() * targetMs) / 1000L else 1920L
        return maxOf(1920L, framesFromRate)
    }

    /**
     * Arms and prebuffers the standby deck with the next track.
     *
     * Prebuffering starts decoding immediately with [playWhenReady = true], feeding PCM
     * into the native ring buffer via [NativeDeckAudioSink.handleBuffer].
     *
     * Native ring buffer consumption is disabled ([isNativeConsuming = false]), and audible
     * volume remains at 0.0f so Deck B sits prebuffered and inaudible until the transition begins.
     */
    fun armStandbyTrack(mediaItem: MediaItem) {
        if (state == CrossfadeState.CROSSFADING) {
            Log.w(TAG, "Cannot arm standby while crossfade is actively running")
            return
        }

        dualDeckPlayer.setDeckConsuming(standbyDeck, false)
        dualDeckPlayer.setDeckVolume(standbyDeck, 0.0f)
        dualDeckPlayer.loadDeck(standbyDeck, mediaItem, playWhenReady = true)
        state = CrossfadeState.PREBUFFERING
        crossfadeProgress = 0f
        isStarved = false
        starvationStartTimeMs = 0L
        totalStarvationDurationMs = 0L

        Log.i(
            TAG,
            "Armed standby deck $standbyDeck for '${mediaItem.mediaMetadata.title}'. Decoding active; native consumption held for prebuffering.",
        )
    }

    /**
     * Verifies whether the standby deck has buffered sufficient PCM frames in its native SPSC ring buffer.
     */
    fun isStandbyPrebuffered(): Boolean {
        val threshold = getEffectivePrebufferFrames(standbyDeck)
        return dualDeckPlayer.isDeckPrebuffered(standbyDeck, threshold)
    }

    /**
     * Evaluates current playback position and starts crossfade if within the transition window.
     *
     * @param currentPositionMs Current playback position of the active deck.
     * @param durationMs Total duration of the active deck's track.
     * @param nowMs Current monotonic time.
     * @return Current crossfade progress (0.0f = active deck full, 1.0f = completed transition).
     */
    fun update(
        currentPositionMs: Long,
        durationMs: Long,
        nowMs: Long = clock(),
    ): Float {
        if (state == CrossfadeState.CROSSFADING) {
            return stepCrossfade(nowMs)
        }

        if (state != CrossfadeState.PREBUFFERING) {
            return 0f
        }

        if (durationMs <= 0L || currentPositionMs < 0L) {
            return 0f
        }

        val remainingMs = durationMs - currentPositionMs
        if (remainingMs <= crossfadeDurationMs) {
            val effectiveThreshold = getEffectivePrebufferFrames(standbyDeck)
            if (!dualDeckPlayer.isDeckPrebuffered(standbyDeck, effectiveThreshold)) {
                val buffered = dualDeckPlayer.getDeckBufferedFrames(standbyDeck)
                Log.i(
                    TAG,
                    "Approaching transition window (${remainingMs}ms remaining), but standby deck $standbyDeck has only $buffered / $effectiveThreshold frames. Active deck continues playing normally without premature stop.",
                )
                return 0f
            }

            // Standby deck is prebuffered and ready: initiate crossfade transition!
            startCrossfade(nowMs)
            return stepCrossfade(nowMs)
        }

        return 0f
    }

    /**
     * Begins the simultaneous dual-deck crossfade transition.
     */
    fun startCrossfade(nowMs: Long = clock()) {
        if (state == CrossfadeState.CROSSFADING) return

        state = CrossfadeState.CROSSFADING
        crossfadeStartTimeMs = nowMs
        crossfadeProgress = 0f
        pausedElapsedMs = 0L
        isCrossfadePaused = false
        isStarved = false
        starvationStartTimeMs = 0L
        totalStarvationDurationMs = 0L

        // Enable native ring buffer consumption on standby deck so the SAME Oboe audio callback consumes both decks
        dualDeckPlayer.setDeckConsuming(standbyDeck, true)

        Log.i(
            TAG,
            "Started seamless dual-deck crossfade: activeDeck=$activeDeck -> standbyDeck=$standbyDeck (duration=${crossfadeDurationMs}ms)",
        )

        // Launch asynchronous ticker if coroutine scope is provided
        coroutineScope?.let { scope ->
            crossfadeJob?.cancel()
            crossfadeJob = scope.launch(dispatcher) {
                while (isActive && state == CrossfadeState.CROSSFADING) {
                    if (!isCrossfadePaused) {
                        val progress = stepCrossfade(clock())
                        if (progress >= 1.0f) break
                    }
                    delay(TICK_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Advances crossfade progression based on the monotonic clock and updates equal-power gains.
     * Incorporates buffer starvation protection: if incoming deck buffer starves, crossfade
     * progress is frozen and outgoing deck gain is held audible to prevent silence interruptions.
     */
    fun stepCrossfade(nowMs: Long = clock()): Float {
        if (state != CrossfadeState.CROSSFADING) return crossfadeProgress
        if (isCrossfadePaused) return crossfadeProgress

        // Starvation Check: verify standby deck has safe headroom
        val standbyBuffered = dualDeckPlayer.getDeckBufferedFrames(standbyDeck)
        val safeHeadroom = getSafeHeadroomFrames(standbyDeck)
        val standbyPlayerState = standbyPlayer.playbackState

        val isBufferStarved = standbyBuffered < safeHeadroom || standbyPlayerState == Player.STATE_BUFFERING
        if (isBufferStarved) {
            if (!isStarved) {
                isStarved = true
                starvationStartTimeMs = nowMs
                Log.w(
                    TAG,
                    "[CROSSFADE STARVATION DETECTED] at ${nowMs}ms: Standby deck $standbyDeck buffer=$standbyBuffered frames (headroom=$safeHeadroom), state=$standbyPlayerState. Freezing crossfade progress and holding active deck gain.",
                )
            }
        } else if (isStarved) {
            val starvedDuration = nowMs - starvationStartTimeMs
            totalStarvationDurationMs += starvedDuration
            isStarved = false
            Log.i(
                TAG,
                "[CROSSFADE STARVATION RECOVERED] at ${nowMs}ms after ${starvedDuration}ms: Standby deck $standbyDeck has $standbyBuffered frames. Resuming crossfade.",
            )
        }

        val elapsed = if (isStarved) {
            val activeElapsedBeforeStarvation = (starvationStartTimeMs - crossfadeStartTimeMs) - totalStarvationDurationMs + pausedElapsedMs
            maxOf(0L, activeElapsedBeforeStarvation)
        } else {
            val totalElapsed = (nowMs - crossfadeStartTimeMs) - totalStarvationDurationMs + pausedElapsedMs
            maxOf(0L, totalElapsed)
        }

        val progress = if (crossfadeDurationMs > 0L) {
            (elapsed.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            1.0f
        }
        crossfadeProgress = progress

        // Calculate equal-power gains:
        // Outgoing deck gain = cos(progress * π / 2)
        // Incoming deck gain = sin(progress * π / 2)
        val outgoingGain = cos(progress * (PI.toFloat() / 2f)).coerceIn(0f, 1f)
        val incomingGain = sin(progress * (PI.toFloat() / 2f)).coerceIn(0f, 1f)

        val gainA: Float
        val gainB: Float
        if (activeDeck == DualDeckAudioPlayer.DECK_A) {
            if (isStarved) {
                gainA = maxOf(outgoingGain, 0.5f) // maintain active deck audibility
                gainB = minOf(incomingGain, (standbyBuffered.toFloat() / safeHeadroom.toFloat()).coerceIn(0f, 1f))
            } else {
                gainA = outgoingGain
                gainB = incomingGain
            }
            dualDeckPlayer.setCrossfade(progress)
            dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_A, gainA)
            dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_B, gainB)
        } else {
            if (isStarved) {
                gainA = minOf(incomingGain, (standbyBuffered.toFloat() / safeHeadroom.toFloat()).coerceIn(0f, 1f))
                gainB = maxOf(outgoingGain, 0.5f) // maintain active deck audibility
            } else {
                gainA = incomingGain
                gainB = outgoingGain
            }
            dualDeckPlayer.setCrossfade(1.0f - progress)
            dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_A, gainA)
            dualDeckPlayer.setDeckVolume(DualDeckAudioPlayer.DECK_B, gainB)
        }

        currentDeckAGain = gainA
        currentDeckBGain = gainB

        logDiagnostics(gainA, gainB, nowMs)

        if (progress >= 1.0f && !isStarved) {
            completeCrossfade(nowMs)
        }

        return progress
    }

    /**
     * Finalizes the crossfade transition, stopping the outgoing deck without restarting Song 2.
     * Preserves independent user speed and pitch settings across deck transitions.
     */
    fun completeCrossfade(nowMs: Long = clock()) {
        crossfadeJob?.cancel()
        crossfadeJob = null

        val previousActiveDeck = activeDeck
        val newActiveDeck = standbyDeck

        // Outgoing deck is silenced and native consumption paused
        dualDeckPlayer.setDeckConsuming(previousActiveDeck, false)
        dualDeckPlayer.setDeckVolume(previousActiveDeck, 0.0f)
        val outgoingPlayer = dualDeckPlayer.playerFor(previousActiveDeck)
        outgoingPlayer.pause()
        dualDeckPlayer.flushDeck(previousActiveDeck)

        // Transfer user playback speed and pitch from outgoing deck to new active deck to preserve user settings
        val activeSpeed = dualDeckPlayer.getDeckSpeed(previousActiveDeck)
        val activePitch = dualDeckPlayer.getDeckPitch(previousActiveDeck)
        dualDeckPlayer.setDeckPlaybackParameters(newActiveDeck, activeSpeed, activePitch)

        // Incoming deck is established as new active deck at full volume
        dualDeckPlayer.setDeckConsuming(newActiveDeck, true)
        dualDeckPlayer.setDeckVolume(newActiveDeck, 1.0f)
        if (newActiveDeck == DualDeckAudioPlayer.DECK_A) {
            dualDeckPlayer.setCrossfade(0.0f)
        } else {
            dualDeckPlayer.setCrossfade(1.0f)
        }

        activeDeck = newActiveDeck
        crossfadeCompletionTimeMs = nowMs
        state = CrossfadeState.COMPLETED
        crossfadeProgress = 1.0f
        isStarved = false
        starvationStartTimeMs = 0L
        totalStarvationDurationMs = 0L

        if (newActiveDeck == DualDeckAudioPlayer.DECK_A) {
            currentDeckAGain = 1.0f
            currentDeckBGain = 0.0f
        } else {
            currentDeckAGain = 0.0f
            currentDeckBGain = 1.0f
        }

        Log.i(
            TAG,
            "Crossfade complete in ${nowMs - crossfadeStartTimeMs}ms. Swapped active deck to $newActiveDeck. Outgoing deck $previousActiveDeck paused.",
        )

        onCrossfadeCompleted?.invoke(newActiveDeck)
    }

    /**
     * Cancels an in-flight crossfade, restoring the active deck to 1.0 volume and silencing the standby deck.
     */
    fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null

        if (state == CrossfadeState.CROSSFADING || state == CrossfadeState.PREBUFFERING) {
            dualDeckPlayer.setDeckConsuming(standbyDeck, false)
            dualDeckPlayer.setDeckVolume(standbyDeck, 0.0f)
            dualDeckPlayer.playerFor(standbyDeck).pause()
            dualDeckPlayer.flushDeck(standbyDeck)

            dualDeckPlayer.setDeckConsuming(activeDeck, true)
            dualDeckPlayer.setDeckVolume(activeDeck, 1.0f)
            if (activeDeck == DualDeckAudioPlayer.DECK_A) {
                dualDeckPlayer.setCrossfade(0.0f)
                currentDeckAGain = 1.0f
                currentDeckBGain = 0.0f
            } else {
                dualDeckPlayer.setCrossfade(1.0f)
                currentDeckAGain = 0.0f
                currentDeckBGain = 1.0f
            }

            state = CrossfadeState.CANCELLED
            crossfadeProgress = 0f
            Log.i(TAG, "Crossfade cancelled. Active deck $activeDeck restored to full gain.")
        }
    }

    /**
     * Handles pause event: pauses monotonic progress while retaining deck buffers.
     */
    fun onPause(nowMs: Long = clock()) {
        if (state == CrossfadeState.CROSSFADING && !isCrossfadePaused) {
            isCrossfadePaused = true
            pausedElapsedMs += (nowMs - crossfadeStartTimeMs)
            standbyPlayer.pause()
        }
        activePlayer.pause()
    }

    /**
     * Handles resume/play event: resumes monotonic progress and playback on both decks.
     */
    fun onPlay(nowMs: Long = clock()) {
        activePlayer.play()
        if (state == CrossfadeState.CROSSFADING && isCrossfadePaused) {
            isCrossfadePaused = false
            crossfadeStartTimeMs = nowMs
            standbyPlayer.play()
        }
    }

    /**
     * Handles seek on active deck. Cancels any in-flight crossfade if seeking outside the transition window.
     */
    fun onSeek(targetPositionMs: Long, durationMs: Long) {
        if (state == CrossfadeState.CROSSFADING) {
            val remainingMs = durationMs - targetPositionMs
            if (remainingMs > crossfadeDurationMs) {
                cancelCrossfade()
            }
        }
    }

    /**
     * Performs a controlled audio handoff to upgrade an active stream (e.g. Opus to Lossless FLAC).
     *
     * Preloads the Lossless version on the spare deck, seeks to [targetPositionMs], prebuffers
     * in the native ring buffer, and performs a micro-crossfade handoff without pausing or flushing
     * the active playback pipeline.
     */
    fun performQualityUpgradeHandoff(
        losslessMediaItem: MediaItem,
        targetPositionMs: Long,
        handoffDurationMs: Long = 1_000L,
        nowMs: Long = clock(),
    ) {
        if (state == CrossfadeState.CROSSFADING) {
            Log.w(TAG, "Deferring quality upgrade handoff: track crossfade already in flight")
            return
        }

        val spareDeck = standbyDeck
        dualDeckPlayer.setDeckConsuming(spareDeck, false)
        dualDeckPlayer.setDeckVolume(spareDeck, 0.0f)

        val sparePlayer = dualDeckPlayer.playerFor(spareDeck)
        sparePlayer.setMediaItem(losslessMediaItem)
        sparePlayer.seekTo(targetPositionMs)
        sparePlayer.prepare()
        sparePlayer.playWhenReady = true

        crossfadeDurationMs = handoffDurationMs
        state = CrossfadeState.PREBUFFERING

        Log.i(
            TAG,
            "Quality upgrade preloaded on spare deck $spareDeck at ${targetPositionMs}ms. Waiting for prebuffer...",
        )
    }

    /**
     * Captures a comprehensive snapshot of the dual-deck engine diagnostics.
     */
    fun getDiagnostics(
        gainA: Float = currentDeckAGain,
        gainB: Float = currentDeckBGain,
        nowMs: Long = clock(),
    ): CrossfadeDiagnostics {
        val sinkA = dualDeckPlayer.sinkFor(DualDeckAudioPlayer.DECK_A)
        val sinkB = dualDeckPlayer.sinkFor(DualDeckAudioPlayer.DECK_B)
        val nativeEngine = dualDeckPlayer.nativeAudioEngine
        val outputRate = nativeEngine.outputSampleRate

        val deckADiag = DeckDiagnostics(
            deckId = DualDeckAudioPlayer.DECK_A,
            trackId = dualDeckPlayer.playerA.currentMediaItem?.mediaId ?: "deck_a",
            codec = sinkA.format?.sampleMimeType ?: "audio/raw",
            inputSampleRate = sinkA.inputSampleRate,
            inputChannelCount = sinkA.inputChannelCount,
            inputPcmEncoding = sinkA.inputPcmEncoding,
            outputSampleRate = outputRate,
            speed = dualDeckPlayer.getDeckSpeed(DualDeckAudioPlayer.DECK_A),
            pitch = dualDeckPlayer.getDeckPitch(DualDeckAudioPlayer.DECK_A),
            exoPositionMs = dualDeckPlayer.playerA.currentPosition,
            nativeConsumedFrames = nativeEngine.getDeckFramesRendered(DualDeckAudioPlayer.DECK_A),
            ringBufferOccupancyFrames = dualDeckPlayer.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_A),
            acceptedFrames = sinkA.framesWritten,
            isPlaying = nativeEngine.isDeckPlaying(DualDeckAudioPlayer.DECK_A),
            gain = gainA,
            exoPlaybackState = dualDeckPlayer.playerA.playbackState,
        )

        val deckBDiag = DeckDiagnostics(
            deckId = DualDeckAudioPlayer.DECK_B,
            trackId = dualDeckPlayer.playerB.currentMediaItem?.mediaId ?: "deck_b",
            codec = sinkB.format?.sampleMimeType ?: "audio/raw",
            inputSampleRate = sinkB.inputSampleRate,
            inputChannelCount = sinkB.inputChannelCount,
            inputPcmEncoding = sinkB.inputPcmEncoding,
            outputSampleRate = outputRate,
            speed = dualDeckPlayer.getDeckSpeed(DualDeckAudioPlayer.DECK_B),
            pitch = dualDeckPlayer.getDeckPitch(DualDeckAudioPlayer.DECK_B),
            exoPositionMs = dualDeckPlayer.playerB.currentPosition,
            nativeConsumedFrames = nativeEngine.getDeckFramesRendered(DualDeckAudioPlayer.DECK_B),
            ringBufferOccupancyFrames = dualDeckPlayer.getDeckBufferedFrames(DualDeckAudioPlayer.DECK_B),
            acceptedFrames = sinkB.framesWritten,
            isPlaying = nativeEngine.isDeckPlaying(DualDeckAudioPlayer.DECK_B),
            gain = gainB,
            exoPlaybackState = dualDeckPlayer.playerB.playbackState,
        )

        return CrossfadeDiagnostics(
            deckABufferedFrames = deckADiag.ringBufferOccupancyFrames,
            deckBBufferedFrames = deckBDiag.ringBufferOccupancyFrames,
            playerAPlaybackState = deckADiag.exoPlaybackState,
            playerAPlayWhenReady = dualDeckPlayer.playerA.playWhenReady,
            playerAIsPlaying = dualDeckPlayer.playerA.isPlaying,
            playerBPlaybackState = deckBDiag.exoPlaybackState,
            playerBPlayWhenReady = dualDeckPlayer.playerB.playWhenReady,
            playerBIsPlaying = dualDeckPlayer.playerB.isPlaying,
            nativeDeckAPlaying = deckADiag.isPlaying,
            nativeDeckBPlaying = deckBDiag.isPlaying,
            deckAGain = gainA,
            deckBGain = gainB,
            crossfadeStartTimeMs = crossfadeStartTimeMs,
            crossfadeCompletionTimeMs = crossfadeCompletionTimeMs,
            nativeUnderrunCount = nativeEngine.underrunCount,
            isStarved = isStarved,
            starvationTimestampMs = if (isStarved) starvationStartTimeMs else null,
            deckA = deckADiag,
            deckB = deckBDiag,
        )
    }

    private fun logDiagnostics(gainA: Float, gainB: Float, nowMs: Long = clock()) {
        val diagnostics = getDiagnostics(gainA, gainB, nowMs)
        onDiagnosticsLogged?.invoke(diagnostics)
        val a = diagnostics.deckA
        val b = diagnostics.deckB
        Log.d(
            TAG,
            "[DIAGNOSTICS @ ${nowMs}ms] state=$state progress=${"%.3f".format(crossfadeProgress)} starved=$isStarved underruns=${diagnostics.nativeUnderrunCount} | " +
                "DeckA[track=${a?.trackId} codec=${a?.codec} inRate=${a?.inputSampleRate} enc=${a?.inputPcmEncoding} speed=${a?.speed} pitch=${a?.pitch} buf=${a?.ringBufferOccupancyFrames} rendered=${a?.nativeConsumedFrames} gain=${"%.3f".format(gainA)} state=${a?.exoPlaybackState}] | " +
                "DeckB[track=${b?.trackId} codec=${b?.codec} inRate=${b?.inputSampleRate} enc=${b?.inputPcmEncoding} speed=${b?.speed} pitch=${b?.pitch} buf=${b?.ringBufferOccupancyFrames} rendered=${b?.nativeConsumedFrames} gain=${"%.3f".format(gainB)} state=${b?.exoPlaybackState}]",
        )
    }
}
