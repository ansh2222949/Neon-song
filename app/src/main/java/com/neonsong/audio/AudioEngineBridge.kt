package com.neonsong.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10

enum class HrtfHeadPreset(
    val title: String,
    val scale: Float,
    val radiusCm: Float,
    val description: String
) {
    SMALL(
        title = "Small",
        scale = HrtfHeadScaleConfig.PRESET_SMALL,
        radiusCm = 7.70f,
        description = "Tighter ITD (577 µs) & higher 8.2 kHz pinna notch"
    ),
    STANDARD(
        title = "Standard (KEMAR)",
        scale = HrtfHeadScaleConfig.PRESET_STANDARD,
        radiusCm = 8.75f,
        description = "Reference 8.75 cm anthropomorphic head (656 µs ITD, 7.2 kHz notch)"
    ),
    WIDE(
        title = "Wide",
        scale = HrtfHeadScaleConfig.PRESET_WIDE,
        radiusCm = 9.80f,
        description = "Deeper ITD (734 µs) & lower 6.4 kHz pinna contour for expansive stage"
    );

    companion object {
        private const val SNAP_TOLERANCE = 0.005f

        fun fromScale(scale: Float): HrtfHeadPreset? =
            entries.firstOrNull { abs(it.scale - scale) <= SNAP_TOLERANCE }
    }
}

object HrtfHeadScaleConfig {
    const val MIN_SCALE = 0.85f
    const val DEFAULT_SCALE = 1.00f
    const val MAX_SCALE = 1.18f

    const val PRESET_SMALL = 0.88f
    const val PRESET_STANDARD = 1.00f
    const val PRESET_WIDE = 1.12f

    // Reference KEMAR acoustic constants from DspProcessor.h
    private const val KEMAR_RADIUS_CM = 8.75f
    private const val KEMAR_SIDE_ITD_US = 655.7f
    private const val KEMAR_PINNA_NOTCH_KHZ = 7.20f

    fun clamp(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else DEFAULT_SCALE

    fun radiusCm(scale: Float): Float = clamp(scale) * KEMAR_RADIUS_CM

    fun sideItdMicros(scale: Float): Float = clamp(scale) * KEMAR_SIDE_ITD_US

    fun pinnaNotchKhz(scale: Float): Float = KEMAR_PINNA_NOTCH_KHZ / clamp(scale)
}

enum class VocalSeparationPreset(
    val title: String,
    val separation: Float,
    val description: String
) {
    NATURAL(
        title = "Natural",
        separation = VocalSeparationConfig.PRESET_NATURAL,
        description = "Cohesive studio stage with gentle center vocal focus"
    ),
    STUDIO_3D(
        title = "Studio 3D",
        separation = VocalSeparationConfig.PRESET_STUDIO,
        description = "Isolated 310 Hz–3.5 kHz lead vocals in front & 12ch instrument bed"
    ),
    HOLOGRAPHIC(
        title = "Holographic",
        separation = VocalSeparationConfig.PRESET_HOLOGRAPHIC,
        description = "Maximum vocal-vs-instrument split & deep 7.1.4 surround unmasking"
    );

    companion object {
        private const val SNAP_TOLERANCE = 0.015f

        fun fromValue(value: Float): VocalSeparationPreset? =
            entries.firstOrNull { abs(it.separation - value) <= SNAP_TOLERANCE }
    }
}

object VocalSeparationConfig {
    const val MIN_SEPARATION = 0.00f
    const val DEFAULT_SEPARATION = 0.75f
    const val MAX_SEPARATION = 1.00f

    const val PRESET_NATURAL = 0.40f
    const val PRESET_STUDIO = 0.75f
    const val PRESET_HOLOGRAPHIC = 1.00f

    fun clamp(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_SEPARATION, MAX_SEPARATION) else DEFAULT_SEPARATION

    // Center vocal formant (310 Hz - 3.5 kHz) + 2.2 kHz articulation focus lift in dB
    fun vocalFocusDb(value: Float): Float {
        val sep = clamp(value)
        val linearGain = 1.0f + (0.08f + 0.22f * sep) + 0.5f * (0.12f + 0.32f * sep)
        return 20.0f * log10(linearGain)
    }

    // Dynamic 2.2 kHz vocal-pocket unmasking depth carved from side/surround beds in dB
    fun surroundUnmaskDb(value: Float): Float {
        val sep = clamp(value)
        val remaining = 1.0f - (0.10f + 0.34f * sep)
        return 20.0f * log10(remaining.coerceAtLeast(0.1f))
    }

    // Percentage of mid-channel instrument energy steered to 12ch (SL/SR, BL/BR, TFL/TFR)
    fun instrumentSpreadPercent(value: Float): Int =
        ((0.65f + 0.55f * clamp(value)) * 100f).toInt()
}

object AudioEngineBridge {

    init {
        runCatching { System.loadLibrary("neonsong_audio") }
    }

    private val _spatialImmersionEnabled = MutableStateFlow(false)
    val spatialImmersionEnabled: StateFlow<Boolean> = _spatialImmersionEnabled.asStateFlow()

    private val _spatialImmersionStrength = MutableStateFlow(0.85f)
    val spatialImmersionStrength: StateFlow<Float> = _spatialImmersionStrength.asStateFlow()

    private val _hrtfHeadScale = MutableStateFlow(HrtfHeadScaleConfig.DEFAULT_SCALE)
    val hrtfHeadScale: StateFlow<Float> = _hrtfHeadScale.asStateFlow()

    private val _vocalInstrumentSeparation = MutableStateFlow(VocalSeparationConfig.DEFAULT_SEPARATION)
    val vocalInstrumentSeparation: StateFlow<Float> = _vocalInstrumentSeparation.asStateFlow()

    fun setSpatialImmersionEnabled(enabled: Boolean) {
        _spatialImmersionEnabled.value = enabled
        runCatching { nativeSetSpatialImmersionEnabled(enabled) }
    }

    fun setSpatialImmersionStrength(strength: Float) {
        val clamped = if (strength.isFinite()) strength.coerceIn(0f, 1f) else 0.85f
        _spatialImmersionStrength.value = clamped
        runCatching { nativeSetSpatialImmersionStrength(clamped) }
    }

    fun setHrtfHeadScale(scale: Float) {
        val clamped = HrtfHeadScaleConfig.clamp(scale)
        _hrtfHeadScale.value = clamped
        runCatching { nativeSetHrtfHeadScale(clamped) }
    }

    fun setHrtfHeadPreset(preset: HrtfHeadPreset) {
        setHrtfHeadScale(preset.scale)
    }

    fun setVocalInstrumentSeparation(separation: Float) {
        val clamped = VocalSeparationConfig.clamp(separation)
        _vocalInstrumentSeparation.value = clamped
        runCatching { nativeSetVocalInstrumentSeparation(clamped) }
    }

    fun setVocalSeparationPreset(preset: VocalSeparationPreset) {
        setVocalInstrumentSeparation(preset.separation)
    }

    @JvmStatic
    private external fun nativeSetSpatialImmersionEnabled(enabled: Boolean)

    @JvmStatic
    private external fun nativeSetSpatialImmersionStrength(strength: Float)

    @JvmStatic
    private external fun nativeSetHrtfHeadScale(scale: Float)

    @JvmStatic
    private external fun nativeSetVocalInstrumentSeparation(separation: Float)
}
