package com.neonsong.app.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.neonsong.app.data.local.EQ_MAX_GAIN_DB
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Automatically switches Studio Master Clarity presets (SPEAKER / HEADPHONE / DAC)
 * and restores per-device saved 16-Band EQ curves whenever Bluetooth headphones,
 * wired IEMs, or USB DACs connect/disconnect.
 */
class SmartRouteProfileManager(
    context: Context,
    private val settingsPreferences: SettingsPreferences,
    private val equalizerPreferences: EqualizerPreferences,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val routeMutex = Mutex()

    @Volatile var autoSwitchEnabled: Boolean = true
        private set

    @Volatile var activeRouteKey: String = ""
        private set

    fun setAutoSwitchEnabled(enabled: Boolean) {
        autoSwitchEnabled = enabled
    }

    private fun hasWiredHeadphones(): Boolean = runCatching {
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && dev.type == AudioDeviceInfo.TYPE_USB_HEADSET)
        } == true
    }.getOrDefault(false)

    /**
     * Call whenever Bluetooth, Wired, or USB DAC state changes, and save current EQ
     * to the outgoing route before loading the incoming route's profile.
     */
    fun onRouteChanged(
        bluetoothActive: Boolean,
        bluetoothName: String?,
        usbDacName: String?,
        hasUsbDac: Boolean,
        applyClarityPresetToEngines: (ClarityPreset) -> Unit,
    ) {
        if (!autoSwitchEnabled) return

        val wiredActive = !hasUsbDac && !bluetoothActive && hasWiredHeadphones()
        val safeDacName = usbDacName?.trim()?.replace(':', '_')?.lowercase()
        val safeBtName = bluetoothName?.trim()?.replace(':', '_')?.lowercase()
        val newRouteKey = when {
            hasUsbDac && !safeDacName.isNullOrBlank() -> "dac:$safeDacName"
            bluetoothActive && !safeBtName.isNullOrBlank() -> "bt:$safeBtName"
            wiredActive -> ROUTE_WIRED_HEADPHONE
            else -> ROUTE_SPEAKER
        }

        if (newRouteKey == activeRouteKey) return
        val previousRouteKey = activeRouteKey
        activeRouteKey = newRouteKey

        val targetClarityPreset = when {
            hasUsbDac -> ClarityPresets.DAC          // Index 3 in DspProcessor.h
            bluetoothActive || wiredActive -> ClarityPresets.HEADPHONE // Index 2 in DspProcessor.h
            else -> ClarityPresets.SPEAKER           // Index 1 in DspProcessor.h
        }

        // Always keep the acoustic Clarity voicing matched to physical driver type
        applyClarityPresetToEngines(targetClarityPreset)

        scope.launch(Dispatchers.IO) {
            routeMutex.withLock {
                // Persist active route clarity preset so SettingsPreferences, UI, and engine collectors stay unified
                runCatching { settingsPreferences.setClarityPreset(targetClarityPreset.index) }

                // 1. Save current EQ curve under the outgoing device key (if there was a prior route)
                if (previousRouteKey.isNotEmpty()) {
                    runCatching {
                        val currentEq = equalizerPreferences.settings.first()
                        saveEqForRoute(previousRouteKey, currentEq.enabled, currentEq.gainsDb)
                    }
                }

                // Guard: if another route change happened while waiting for lock, don't clobber
                if (activeRouteKey != newRouteKey) return@withLock

                // 2. Restore saved EQ curve for the newly connected device (if previously saved)
                runCatching {
                    val restored = loadEqForRoute(newRouteKey)
                    if (restored != null) {
                        equalizerPreferences.setEnabled(restored.first)
                        equalizerPreferences.setAllBands(restored.second)
                        Log.i(TAG, "Auto-switched EQ & Clarity ($targetClarityPreset) for device '$newRouteKey'")
                    } else {
                        Log.i(TAG, "Auto-switched Clarity ($targetClarityPreset) for new device '$newRouteKey'")
                    }
                }
            }
        }
    }

    private fun saveEqForRoute(routeKey: String, enabled: Boolean, gainsDb: List<Float>) {
        if (gainsDb.isEmpty()) return
        val csv = gainsDb.joinToString(",") { gain ->
            val safeGain = if (gain.isFinite()) gain.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
            "%.2f".format(java.util.Locale.US, safeGain)
        }
        prefs.edit()
            .putBoolean("eq_on_$routeKey", enabled)
            .putString("eq_gains_$routeKey", csv)
            .apply()
    }

    private fun loadEqForRoute(routeKey: String): Pair<Boolean, List<Float>>? {
        val csv = prefs.getString("eq_gains_$routeKey", null) ?: return null
        val gains = csv.split(',').mapNotNull { token ->
            token.trim().toFloatOrNull()?.takeIf { f -> f.isFinite() }?.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB)
        }
        if (gains.size != NativeAudioEngine.EQUALIZER_BAND_COUNT) return null
        val enabled = prefs.getBoolean("eq_on_$routeKey", true)
        return enabled to gains
    }

    companion object {
        private const val TAG = "SmartRouteProfile"
        private const val PREFS_NAME = "neonsong_smart_route_profiles"
        private const val KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
        private const val ROUTE_SPEAKER = "speaker:internal"
        private const val ROUTE_WIRED_HEADPHONE = "wired:headphones"
    }
}
