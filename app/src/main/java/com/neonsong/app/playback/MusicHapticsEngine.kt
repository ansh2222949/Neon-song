package com.neonsong.app.playback

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.max
import kotlin.math.min

enum class HapticMode(val id: String, val label: String, val description: String) {
    BEATS_ONLY("beats_only", "Beats Only", "Punchy kicks & crisp snares"),
    FULL_SONG("full_song", "All Music", "Beats, basslines & continuous groove"),
    VOCALS_MELODY("vocals_melody", "Vocals & Melody", "Vocal articulation & melodic plucks"),
    BASS_HEAVY("bass_heavy", "Deep Bass", "Strictly <110 Hz 808s & sub-bass drops");

    companion object {
        fun fromId(id: String?): HapticMode = entries.find { it.id == id } ?: BEATS_ONLY
    }
}

enum class HapticIntensity(val id: String, val label: String, val scale: Float) {
    LOW("low", "Low", 0.64f),
    NORMAL("normal", "Normal", 1.0f),
    STRONG("strong", "Strong", 1.36f);

    companion object {
        fun fromId(id: String?): HapticIntensity = entries.find { it.id == id } ?: NORMAL
    }
}

/**
 * Apple Music Haptics-inspired Tactile Beat & Groove Engine:
 * Uses dual-envelope onset detection (fast attack / slow baseline), first-order
 * spectral flux derivatives, and adaptive crest-factor thresholding so haptics
 * lock tightly to musical transients across both quiet intros and heavily mastered drops.
 */
class MusicHapticsEngine(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile var enabled: Boolean = false
        private set

    @Volatile var mode: HapticMode = HapticMode.BEATS_ONLY
        private set

    @Volatile var intensityLevel: HapticIntensity = HapticIntensity.NORMAL
        private set

    private var hasLoadedInitialConfig = false
    private var lastPollElapsedMs = 0L
    private var lastPulseElapsedMs = 0L
    private var lastPulseStrength = 0f

    // Dual-envelope followers & previous-frame state for true rising-edge onset detection
    private var fastBassEnv = 0.18f
    private var slowBassEnv = 0.20f
    private var bassPeakCrest = 0.35f
    private var prevBassPeak = 0f

    private var fastTransEnv = 0.08f
    private var slowTransEnv = 0.09f
    private var transPeakCrest = 0.20f
    private var prevTransSnap = 0f

    private var supportsThud = false
    private var supportsTick = false
    private var supportsLowTick = false
    private var supportsQuickRise = false

    init {
        val vib = vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vib != null) {
            val check = runCatching {
                vib.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_THUD,
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    } else {
                        VibrationEffect.Composition.PRIMITIVE_TICK
                    },
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                )
            }.getOrDefault(booleanArrayOf(false, false, false, false))
            supportsThud = check.getOrElse(0) { false }
            supportsTick = check.getOrElse(1) { false }
            supportsLowTick = check.getOrElse(2) { false }
            supportsQuickRise = check.getOrElse(3) { false }
            Log.i(
                TAG,
                "Vibrator capabilities: hasVibrator=${vib.hasVibrator()}, thud=$supportsThud, " +
                    "tick=$supportsTick, lowTick=$supportsLowTick, quickRise=$supportsQuickRise",
            )
        }
    }

    val isSupported: Boolean
        get() = vibrator?.hasVibrator() == true

    /**
     * Synchronizes DataStore preferences and fires a tactile confirmation pulse
     * only when the user actively changes a toggle/chip after initial startup.
     */
    fun syncConfig(enable: Boolean, newMode: HapticMode, newIntensity: HapticIntensity) {
        val wasInitialized = hasLoadedInitialConfig
        hasLoadedInitialConfig = true

        val prevEnabled = enabled
        val prevMode = mode
        val prevIntensity = intensityLevel

        enabled = enable
        mode = newMode
        intensityLevel = newIntensity

        if (!enable) {
            if (prevEnabled) runCatching { vibrator?.cancel() }
            resetEnvelopes()
            return
        }

        if (wasInitialized) {
            when {
                !prevEnabled -> vibratePulse(
                    pulseType = PulseType.PUNCH_KICK,
                    intensity = (0.68f * newIntensity.scale).coerceIn(0.25f, 1.0f),
                )
                prevMode != newMode -> {
                    val previewType = when (newMode) {
                        HapticMode.VOCALS_MELODY -> PulseType.CRISP_TICK
                        HapticMode.FULL_SONG -> PulseType.SOFT_TEXTURE
                        HapticMode.BASS_HEAVY -> PulseType.SUB_DROP
                        HapticMode.BEATS_ONLY -> PulseType.PUNCH_KICK
                    }
                    vibratePulse(
                        pulseType = previewType,
                        intensity = (0.65f * newIntensity.scale).coerceIn(0.25f, 1.0f),
                    )
                }
                prevIntensity != newIntensity -> vibratePulse(
                    pulseType = PulseType.PUNCH_KICK,
                    intensity = (0.68f * newIntensity.scale).coerceIn(0.25f, 1.0f),
                )
            }
        }
    }

    fun setEnabled(enable: Boolean) {
        syncConfig(enable, mode, intensityLevel)
    }

    fun setMode(newMode: HapticMode) {
        syncConfig(enabled, newMode, intensityLevel)
    }

    fun setIntensity(newIntensity: HapticIntensity) {
        syncConfig(enabled, mode, newIntensity)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAudioSessionIdChanged(sessionId: Int) {
        // Deliberately do NOT attach HapticGenerator: on Samsung One UI & Pixel devices,
        // enabling HapticGenerator locks the vibrator HAL and blocks vibrator.vibrate()!
        resetEnvelopes()
    }

    private fun resetEnvelopes() {
        fastBassEnv = 0.16f
        slowBassEnv = 0.18f
        bassPeakCrest = 0.32f
        prevBassPeak = 0f
        fastTransEnv = 0.06f
        slowTransEnv = 0.07f
        transPeakCrest = 0.18f
        prevTransSnap = 0f
        lastPulseStrength = 0f
    }

    /**
     * Consumes the packed 32-bit telemetry from NativeAudioEngine.consumeHapticTelemetry()
     * on the playback ticker and maps musical onsets & envelopes to LRA primitives.
     */
    fun pollAndFire(packedTelemetry: Int, isPlaying: Boolean) {
        val vib = vibrator
        if (!enabled || !isPlaying || packedTelemetry == 0 || vib == null || !vib.hasVibrator()) return
        val now = SystemClock.elapsedRealtime()

        // Reset adaptive baselines after pauses, seeks, or silent breakdowns
        if (now - lastPollElapsedMs > 350L) {
            resetEnvelopes()
        }
        lastPollElapsedMs = now

        val bassPeak = (((packedTelemetry ushr 16) and 0xFFFF) / 10000f).coerceIn(0f, 1.5f)
        val transientSnap = ((packedTelemetry and 0xFFFF) / 10000f).coerceIn(0f, 1.5f)

        // First-order instantaneous onset derivatives (positive only = rising attack edge)
        val bassSlope = max(0f, bassPeak - prevBassPeak)
        val transSlope = max(0f, transientSnap - prevTransSnap)
        prevBassPeak = bassPeak
        prevTransSnap = transientSnap

        // Asymmetric dual-envelope tracking: fast attack, smooth release
        val bassFastAlpha = if (bassPeak > fastBassEnv) 0.55f else 0.18f
        fastBassEnv += bassFastAlpha * (bassPeak - fastBassEnv)
        slowBassEnv += 0.065f * (bassPeak - slowBassEnv)
        bassPeakCrest = max(bassPeak, bassPeakCrest * 0.988f).coerceAtLeast(0.22f)

        val transFastAlpha = if (transientSnap > fastTransEnv) 0.60f else 0.22f
        fastTransEnv += transFastAlpha * (transientSnap - fastTransEnv)
        slowTransEnv += 0.085f * (transientSnap - slowTransEnv)
        transPeakCrest = max(transientSnap, transPeakCrest * 0.985f).coerceAtLeast(0.10f)

        // Dynamic flux above rolling baseline & normalized crest ratio
        val bassFlux = max(0f, fastBassEnv - slowBassEnv)
        val transFlux = max(0f, fastTransEnv - slowTransEnv)
        val normBass = (bassPeak / bassPeakCrest).coerceIn(0f, 1.25f)
        val normTrans = (transientSnap / transPeakCrest).coerceIn(0f, 1.25f)

        val currentMode = mode
        val currentIntensity = intensityLevel

        val baseCooldownMs = when (currentMode) {
            HapticMode.FULL_SONG -> 42L
            HapticMode.VOCALS_MELODY -> 44L
            HapticMode.BEATS_ONLY -> 54L
            HapticMode.BASS_HEAVY -> 64L
        }
        // Adaptive refractory period: heavy kicks add slight physical settling time,
        // while rapid sharp transients can break through slightly earlier in STRONG mode.
        val dynamicPadMs = (lastPulseStrength * 14f).toLong()
        val intensityOffsetMs = when (currentIntensity) {
            HapticIntensity.STRONG -> -6L
            HapticIntensity.LOW -> 6L
            HapticIntensity.NORMAL -> 0L
        }
        val elapsedSincePulse = now - lastPulseElapsedMs
        val effectiveCooldownMs = (baseCooldownMs + dynamicPadMs + intensityOffsetMs).coerceAtLeast(36L)

        // Allow an ultra-sharp snare/transient accent to break through late in the cooldown window
        val isRapidTransientOverride = elapsedSincePulse >= (effectiveCooldownMs * 0.72f) &&
            transSlope >= 0.065f && normTrans >= 0.88f
        if (elapsedSincePulse < effectiveCooldownMs && !isRapidTransientOverride) return

        var pulseType: PulseType? = null
        var rawStrength = 0f

        when (currentMode) {
            HapticMode.BEATS_ONLY -> {
                val isSubKick = bassPeak >= 0.28f && bassSlope >= 0.045f && bassFlux >= 0.038f
                val isPunchKick = !isSubKick && (
                    (bassPeak >= 0.20f && bassSlope >= 0.028f && normBass >= 0.68f) ||
                        (bassPeak >= 0.26f && bassFlux >= 0.032f && transientSnap >= 0.065f)
                    )
                val isSnareOrClap = !isSubKick && !isPunchKick && (
                    (transientSnap >= 0.11f && transSlope >= 0.025f) ||
                        (transFlux >= 0.032f && normTrans >= 0.74f)
                    )

                when {
                    isSubKick -> {
                        pulseType = PulseType.SUB_DROP
                        rawStrength = 0.38f + normBass * 0.42f + bassSlope * 2.2f
                    }
                    isPunchKick -> {
                        pulseType = PulseType.PUNCH_KICK
                        rawStrength = 0.30f + normBass * 0.38f + bassFlux * 2.6f
                    }
                    isSnareOrClap -> {
                        pulseType = PulseType.CRISP_TICK
                        rawStrength = 0.26f + normTrans * 0.40f + transSlope * 2.4f
                    }
                }
            }

            HapticMode.FULL_SONG -> {
                val isHeavyKick = bassPeak >= 0.24f && bassSlope >= 0.035f && bassFlux >= 0.030f
                val isPunchKick = !isHeavyKick && bassPeak >= 0.18f &&
                    (bassSlope >= 0.022f || (bassFlux >= 0.024f && normBass >= 0.64f))
                val isSnareSnap = !isHeavyKick && !isPunchKick &&
                    ((transientSnap >= 0.085f && transSlope >= 0.018f) || (transFlux >= 0.024f && normTrans >= 0.70f))
                val isGrooveSwell = !isHeavyKick && !isPunchKick && !isSnareSnap &&
                    elapsedSincePulse >= 68L &&
                    ((bassPeak >= 0.21f && normBass >= 0.58f) || (transientSnap >= 0.060f && transFlux >= 0.014f))

                when {
                    isHeavyKick -> {
                        pulseType = PulseType.SUB_DROP
                        rawStrength = 0.36f + normBass * 0.42f + bassSlope * 2.1f
                    }
                    isPunchKick -> {
                        pulseType = PulseType.PUNCH_KICK
                        rawStrength = 0.28f + normBass * 0.36f + bassFlux * 2.4f
                    }
                    isSnareSnap -> {
                        pulseType = PulseType.CRISP_TICK
                        rawStrength = 0.25f + normTrans * 0.38f + transSlope * 2.3f
                    }
                    isGrooveSwell -> {
                        pulseType = PulseType.SOFT_TEXTURE
                        rawStrength = (0.18f + normBass * 0.24f + normTrans * 0.18f).coerceAtMost(0.58f)
                    }
                }
            }

            HapticMode.VOCALS_MELODY -> {
                // Prioritize consonant/pluck articulation (transSlope + transFlux) while filtering out pure sub-bass rumble
                val isSyllableOrPluck = (transientSnap >= 0.062f && transSlope >= 0.016f) ||
                    (transFlux >= 0.020f && normTrans >= 0.65f)
                val isMelodicContour = !isSyllableOrPluck && elapsedSincePulse >= 64L &&
                    transientSnap >= 0.040f && (transFlux >= 0.010f || (bassFlux >= 0.018f && bassPeak in 0.12f..0.34f))

                if (isSyllableOrPluck) {
                    pulseType = PulseType.CRISP_TICK
                    rawStrength = (0.24f + normTrans * 0.42f + transSlope * 2.8f).coerceIn(0.24f, 0.92f)
                } else if (isMelodicContour) {
                    pulseType = PulseType.SOFT_TEXTURE
                    rawStrength = (0.20f + normTrans * 0.28f + transFlux * 1.8f).coerceIn(0.20f, 0.54f)
                }
            }

            HapticMode.BASS_HEAVY -> {
                val isSub808Attack = bassPeak >= 0.22f && (bassSlope >= 0.026f || bassFlux >= 0.028f) && normBass >= 0.64f
                val isSubSustainRumble = !isSub808Attack && elapsedSincePulse >= 82L &&
                    bassPeak >= 0.30f && normBass >= 0.72f

                if (isSub808Attack) {
                    pulseType = PulseType.SUB_DROP
                    rawStrength = 0.38f + normBass * 0.46f + bassSlope * 2.5f
                } else if (isSubSustainRumble) {
                    pulseType = PulseType.PUNCH_KICK
                    rawStrength = (0.28f + normBass * 0.36f).coerceAtMost(0.76f)
                }
            }
        }

        val chosenPulse = pulseType ?: return
        val scaledIntensity = (rawStrength * currentIntensity.scale).coerceIn(0.20f, 1.0f)

        lastPulseElapsedMs = now
        lastPulseStrength = scaledIntensity
        vibratePulse(chosenPulse, scaledIntensity)
    }

    private fun vibratePulse(pulseType: PulseType, intensity: Float) {
        val vib = vibrator ?: return
        var effect: VibrationEffect? = null
        val clamped = intensity.coerceIn(0.18f, 1.0f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (supportsThud || supportsTick || supportsLowTick)) {
            val comp = VibrationEffect.startComposition()
            var added = false

            when (pulseType) {
                PulseType.SUB_DROP -> {
                    // Two-stage sub-bass bloom: crisp initial transient + deep resonant body
                    if (supportsLowTick && supportsThud && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && clamped >= 0.52f) {
                        comp.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                            (clamped * 0.65f).coerceIn(0.20f, 0.85f),
                        )
                        comp.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_THUD,
                            clamped,
                            12,
                        )
                        added = true
                    } else {
                        val prim = when {
                            supportsThud -> VibrationEffect.Composition.PRIMITIVE_THUD
                            supportsLowTick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                            supportsTick -> VibrationEffect.Composition.PRIMITIVE_TICK
                            else -> -1
                        }
                        if (prim != -1) {
                            comp.addPrimitive(prim, clamped)
                            added = true
                        }
                    }
                }

                PulseType.PUNCH_KICK -> {
                    val prim = when {
                        supportsThud -> VibrationEffect.Composition.PRIMITIVE_THUD
                        supportsLowTick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                        supportsTick -> VibrationEffect.Composition.PRIMITIVE_TICK
                        else -> -1
                    }
                    if (prim != -1) {
                        comp.addPrimitive(prim, clamped)
                        added = true
                    }
                }

                PulseType.SOFT_TEXTURE -> {
                    val prim = when {
                        supportsLowTick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                        supportsTick -> VibrationEffect.Composition.PRIMITIVE_TICK
                        supportsThud -> VibrationEffect.Composition.PRIMITIVE_THUD
                        else -> -1
                    }
                    if (prim != -1) {
                        comp.addPrimitive(prim, (clamped * 0.82f).coerceIn(0.18f, 0.72f))
                        added = true
                    }
                }

                PulseType.CRISP_TICK -> {
                    val prim = when {
                        supportsTick -> VibrationEffect.Composition.PRIMITIVE_TICK
                        supportsLowTick && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                        else -> -1
                    }
                    if (prim != -1) {
                        comp.addPrimitive(prim, clamped)
                        added = true
                    }
                }
            }

            if (added) {
                effect = runCatching { comp.compose() }.getOrNull()
            }
        }

        // Shaped waveform fallback for devices where LRA primitives are unsupported
        if (effect == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val peakAmp = (clamped * 255f).toInt().coerceIn(38, 255)
            effect = when (pulseType) {
                PulseType.SUB_DROP -> {
                    val attackAmp = min(255, (peakAmp * 0.75f).toInt().coerceAtLeast(45))
                    val tailAmp = (peakAmp * 0.42f).toInt().coerceAtLeast(24)
                    VibrationEffect.createWaveform(
                        longArrayOf(8L, 16L, 12L),
                        intArrayOf(attackAmp, peakAmp, tailAmp),
                        -1,
                    )
                }
                PulseType.PUNCH_KICK -> {
                    val tailAmp = (peakAmp * 0.35f).toInt().coerceAtLeast(20)
                    VibrationEffect.createWaveform(
                        longArrayOf(14L, 8L),
                        intArrayOf(peakAmp, tailAmp),
                        -1,
                    )
                }
                PulseType.SOFT_TEXTURE -> {
                    VibrationEffect.createOneShot(13L, (peakAmp * 0.72f).toInt().coerceIn(30, 185))
                }
                PulseType.CRISP_TICK -> {
                    VibrationEffect.createOneShot(9L, peakAmp)
                }
            }
        }

        if (effect != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // USAGE_TOUCH is never blocked by Samsung One UI's media-vibration filter
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .build()
                    vib.vibrate(effect, attrs)
                } else {
                    vib.vibrate(effect)
                }
            }.onFailure { error ->
                Log.w(TAG, "Vibrator call failed (check VIBRATE permission in AndroidManifest.xml)", error)
            }
        }
    }

    fun release() {
        resetEnvelopes()
        runCatching { vibrator?.cancel() }
    }

    private enum class PulseType {
        SUB_DROP,
        PUNCH_KICK,
        SOFT_TEXTURE,
        CRISP_TICK,
    }

    private companion object {
        const val TAG = "MusicHapticsEngine"
    }
}
