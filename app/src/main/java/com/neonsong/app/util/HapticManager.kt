package com.neonsong.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Unified tactile haptic engine for Neon Song.
 *
 * On API 29+ uses [VibrationEffect.createPredefined] with cached effects
 * (EFFECT_CLICK, EFFECT_TICK, EFFECT_HEAVY_CLICK, EFFECT_DOUBLE_CLICK) —
 * zero allocations after init. On older APIs falls back to
 * [View.performHapticFeedback] which requires a [View] reference.
 *
 * Usage from Compose: wire via [LocalHapticManager] composition local
 * or call [rememberHapticManager] and invoke presets directly.
 */
class HapticManager private constructor(
    private val vibrator: Vibrator?,
) {
    /** True when the device vibrator is available and API 29+ effects work. */
    private val hasApi29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // ── Pre-cached VibrationEffect instances (API 29+, zero runtime alloc) ──
    private val effectClick: VibrationEffect? = if (hasApi29) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else null

    private val effectTick: VibrationEffect? = if (hasApi29) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    } else null

    private val effectHeavyClick: VibrationEffect? = if (hasApi29) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    } else null

    private val effectDoubleClick: VibrationEffect? = if (hasApi29) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    } else null

    // ── Fallback View ref (only used on API < 29) ──
    @Volatile
    private var fallbackView: View? = null

    /** Attach a View for fallback haptics on API < 29. Call from Composable. */
    fun attachView(view: View) {
        if (!hasApi29) {
            fallbackView = view
        }
    }

    // ── Private dispatch helpers ──

    private fun vibrateEffect(effect: VibrationEffect?) {
        if (effect == null || vibrator == null) return
        try {
            vibrator.vibrate(effect)
        } catch (_: Exception) {
            // Silently ignore — some OEMs throw on vibrate.
        }
    }

    private fun fallback(constant: Int) {
        fallbackView?.performHapticFeedback(
            constant,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    // ── Public expressive presets (zero-alloc in callbacks) ──

    /** Crisp single tap — buttons, play/pause, navigation tab switches. */
    fun click() {
        if (hasApi29) {
            vibrateEffect(effectClick)
        } else {
            fallback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Subtle micro-tick — slider detents while scrubbing, progress steps. */
    fun tick() {
        if (hasApi29) {
            vibrateEffect(effectTick)
        } else {
            fallback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Toggle feedback — heavier for active (on), lighter for inactive (off).
     * Maps to EFFECT_HEAVY_CLICK / EFFECT_TICK for distinct on/off feel.
     */
    fun toggle(active: Boolean) {
        if (hasApi29) {
            vibrateEffect(if (active) effectHeavyClick else effectTick)
        } else {
            fallback(
                if (active) HapticFeedbackConstants.LONG_PRESS
                else HapticFeedbackConstants.CLOCK_TICK,
            )
        }
    }

    /** Slider detent / scrubbing step — same as tick, kept as a semantic alias. */
    fun sliderDetent() {
        tick()
    }

    /** Error / rejection — double-click for a pronounced "nope" feel. */
    fun error() {
        if (hasApi29) {
            vibrateEffect(effectDoubleClick)
        } else {
            fallback(HapticFeedbackConstants.REJECT)
        }
    }

    companion object {
        @Volatile
        private var instance: HapticManager? = null

        /** Retrieve or create the singleton. Thread-safe, idempotent. */
        fun getInstance(context: Context): HapticManager {
            return instance ?: synchronized(this) {
                instance ?: createInstance(context.applicationContext).also { instance = it }
            }
        }

        private fun createInstance(appContext: Context): HapticManager {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return HapticManager(vibrator)
        }
    }
}

/** Composition local for injecting [HapticManager] into the Compose tree. */
val LocalHapticManager = staticCompositionLocalOf<HapticManager> {
    error("HapticManager not provided — wrap with provideHapticManager()")
}

/**
 * Remember a [HapticManager] bound to the current View (for API < 29 fallback).
 * Prefer using [LocalHapticManager] for tree-wide access instead.
 */
@Composable
fun rememberHapticManager(): HapticManager {
    val view = LocalView.current
    return remember(view) {
        HapticManager.getInstance(view.context).also { it.attachView(view) }
    }
}
