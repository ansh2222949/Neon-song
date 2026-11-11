package com.neonsong.app.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile var autoSwitchEnabled: Boolean = prefs.getBoolean(KEY_AUTO_SWITCH_ENABLED, true)
        private set

    @Volatile var activeRouteKey: String = ROUTE_SPEAKER
        private set

    fun setAutoSwitchEnabled(enabled: Boolean) {
        autoSwitchEnabled = enabled
        prefs.edit().putBoolean(KEY_AUTO_SWITCH_ENABLED, enabled).apply()
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
        val newRouteKey = when {
            hasUsbDac && !usbDacName.isNullOrBlank() -> "dac:${usbDacName.trim().lowercase()}"
            bluetoothActive && !bluetoothName.isNullOrBlank() -> "bt:${bluetoothName.trim().lowercase()}"
            wiredActive -> ROUTE_WIRED_HEADPHONE
            else -> ROUTE_SPEAKER
        }

        val targetClarityPreset = when {
            hasUsbDac -> ClarityPresets.DAC          // Index 3 in DspProcessor.h
            bluetoothActive || wiredActive -> ClarityPresets.HEADPHONE // Index 2 in DspProcessor.h
            else -> ClarityPresets.SPEAKER           // Index 1 in DspProcessor.h
        }

        // Always keep the acoustic Clarity voicing matched to physical driver type
        applyClarityPresetToEngines(targetClarityPreset)

        if (newRouteKey == activeRouteKey) return
        val previousRouteKey = activeRouteKey
        activeRouteKey = newRouteKey

        scope.launch(Dispatchers.IO) {
            // 1. Save current EQ curve under the outgoing device key
            runCatching {
                val currentEq = equalizerPreferences.settings.first()
                saveEqForRoute(previousRouteKey, currentEq.enabled, currentEq.gainsDb)
            }

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

    private fun saveEqForRoute(routeKey: String, enabled: Boolean, gainsDb: List<Float>) {
        if (gainsDb.isEmpty()) return
        val csv = gainsDb.joinToString(",") { "%.2f".format(java.util.Locale.US, it) }
        prefs.edit()
            .putBoolean("eq_on_$routeKey", enabled)
            .putString("eq_gains_$routeKey", csv)
            .apply()
    }

    private fun loadEqForRoute(routeKey: String): Pair<Boolean, List<Float>>? {
        val csv = prefs.getString("eq_gains_$routeKey", null) ?: return null
        val gains = csv.split(',').mapNotNull { it.toFloatOrNull() }
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
