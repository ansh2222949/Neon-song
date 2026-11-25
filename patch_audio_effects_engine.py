import sys

file_path = r'd:\Neon-song-main\app\src\main\java\com\neonsong\app\playback\AudioEffectsEngine.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.splitlines(keepends=True)

# 1. Imports
target_imports = "".join(lines[2:28])
new_imports = '''import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import com.neonsong.app.data.local.EQ_BAND_FREQS_HZ
import com.neonsong.app.data.local.EQ_MAX_GAIN_DB
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.EqualizerPresets
import com.neonsong.app.data.local.EqualizerSettings
import com.neonsong.app.data.local.SettingsPreferences
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
'''
assert target_imports in content, "target_imports not found"
content = content.replace(target_imports, new_imports, 1)

# 2. Class declaration & fields & init & setReplayGainTags & close
target_class_head = "".join(lines[43:97])
new_class_head = '''@Singleton
class AudioEffectsEngine @Inject constructor(
    equalizerPreferences: EqualizerPreferences,
    settingsPreferences: SettingsPreferences,
    applicationScope: CoroutineScope,
    // Optional so manually constructed standby engines keep compiling without
    // the prefs binding; null simply leaves loudness normalization OFF.
    loudnessPrefs: LoudnessPrefs? = null,
) : Closeable {
    private val effectMutex = Mutex()
    private val applyRequests = Channel<Unit>(Channel.CONFLATED)
    private val jobs = mutableListOf<Job>()

    @Volatile private var requestedSessionId = C.AUDIO_SESSION_ID_UNSET
    @Volatile private var fallbackRequired = false
    @Volatile private var equalizerSettings = EqualizerSettings()
    @Volatile private var studioClarityEnabled = false
    @Volatile private var bitPerfectActive = false
    @Volatile private var usbExclusiveActive = false
    @Volatile private var systemEffectsActive = false
    @Volatile private var loudnessMode: LoudnessMode = LoudnessMode.OFF
    @Volatile private var loudnessPreampDb = 0f
    @Volatile private var replayGainTags: ReplayGainTags? = null

    private var attachedSessionId = C.AUDIO_SESSION_ID_UNSET
    private var toneEffect: ToneEffect? = null
    private var lastAppliedCurve: FloatArray? = null

    init {
        jobs += applicationScope.launch(Dispatchers.Default) {
            for (ignored in applyRequests) {
                effectMutex.withLock { applyInternal() }
            }
        }
        jobs += applicationScope.launch(Dispatchers.Default) {
            equalizerPreferences.settings
                .distinctUntilChanged()
                .collect { settings ->
                    equalizerSettings = settings
                    requestApply()
                }
        }
        jobs += applicationScope.launch(Dispatchers.Default) {
            settingsPreferences.settings
                .map { it.isStudioMasterClarityEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    studioClarityEnabled = enabled
                    requestApply()
                }
        }
        loudnessPrefs?.let { prefs ->
            jobs += applicationScope.launch(Dispatchers.Default) {
                prefs.settings
                    .distinctUntilChanged()
                    .collect { settings ->
                        loudnessMode = settings.mode
                        loudnessPreampDb = settings.preampDb
                        requestApply()
                    }
            }
        }
    }
'''
assert target_class_head in content, "target_class_head not found"
content = content.replace(target_class_head, new_class_head, 1)

# setReplayGainTags
target_replay_tags = "".join(lines[138:144])
new_replay_tags = '''    fun setReplayGainTags(tags: ReplayGainTags?) {
        if (replayGainTags == tags) return
        replayGainTags = tags
        requestApply()
    }
'''
assert target_replay_tags in content, "target_replay_tags not found"
content = content.replace(target_replay_tags, new_replay_tags, 1)

# Add close() after detach()
target_detach = "".join(lines[154:160])
new_detach = target_detach + '''
    override fun close() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        applyRequests.close()
        if (effectMutex.tryLock()) {
            try {
                releaseAllInternal()
                attachedSessionId = C.AUDIO_SESSION_ID_UNSET
            } finally {
                effectMutex.unlock()
            }
        }
    }
'''
assert target_detach in content, "target_detach not found"
content = content.replace(target_detach, new_detach, 1)

# 3. applyInternal, buildCombinedCurve, applyToneEffect, releaseToneInternal
target_apply_block = "".join(lines[164:258])
new_apply_block = '''    private fun applyInternal() {
        val targetSessionId = requestedSessionId
        if (targetSessionId != attachedSessionId) {
            releaseAllInternal()
            attachedSessionId = targetSessionId
        }
        if (bitPerfectActive || usbExclusiveActive || systemEffectsActive ||
            !fallbackRequired || attachedSessionId == C.AUDIO_SESSION_ID_UNSET
        ) {
            releaseAllInternal()
            return
        }

        val loudnessGainDb = LoudnessNormalizer.gainForTags(
            tags = replayGainTags,
            mode = loudnessMode,
            preampDb = loudnessPreampDb,
            limiterEngaged = false,
        )
        val userEqEnabled = equalizerSettings.enabled
        val loudnessActive = loudnessMode != LoudnessMode.OFF && loudnessGainDb != 0f
        val needsTone = userEqEnabled || studioClarityEnabled || loudnessActive
        if (needsTone) {
            applyToneEffect(buildCombinedCurve(userEqEnabled, loudnessActive, loudnessGainDb))
        } else {
            releaseToneInternal()
        }
    }

    private fun buildCombinedCurve(userEqEnabled: Boolean, loudnessActive: Boolean, loudnessGainDb: Float): FloatArray =
        FloatArray(EQ_BAND_FREQS_HZ.size) { index ->
            val studioGain = if (studioClarityEnabled) {
                EqualizerPresets.STUDIO_MASTER.gainsDb.getOrElse(index) { 0f }.takeIf { it.isFinite() } ?: 0f
            } else 0f
            val userGain = if (userEqEnabled) {
                equalizerSettings.gainsDb.getOrElse(index) { 0f }.takeIf { it.isFinite() } ?: 0f
            } else 0f
            val combined = studioGain + userGain
            val base = if (combined.isFinite()) combined.coerceIn(-EQ_MAX_GAIN_DB, EQ_MAX_GAIN_DB) else 0f
            if (!loudnessActive || loudnessGainDb == 0f || !loudnessGainDb.isFinite()) {
                base
            } else {
                val total = base + loudnessGainDb
                if (total.isFinite()) {
                    total.coerceIn(-LOUDNESS_STAGE_MAX_GAIN_DB, LOUDNESS_STAGE_MAX_GAIN_DB)
                } else {
                    base
                }
            }
        }

    private fun applyToneEffect(gainsDb: FloatArray) {
        var effect = toneEffect
        val existingControl = effect != null && effect.hasControl()
        if (existingControl && lastAppliedCurve?.contentEquals(gainsDb) == true) return
        if (!existingControl) {
            releaseToneInternal()
            effect = createToneEffect(attachedSessionId)
            toneEffect = effect
        }
        if (effect?.apply(gainsDb) == true) {
            lastAppliedCurve = gainsDb.copyOf()
            return
        }

        if (effect is ModernDynamicsEffect) {
            Log.w(TAG, "DynamicsProcessing rejected this device; using legacy Equalizer")
            releaseToneInternal()
            val legacy = LegacyEqualizerEffect.create(attachedSessionId)
            toneEffect = legacy
            if (legacy?.apply(gainsDb) == true) {
                lastAppliedCurve = gainsDb.copyOf()
            } else {
                Log.w(TAG, "Platform Equalizer is unavailable on this audio route")
                releaseToneInternal()
            }
        } else {
            Log.w(TAG, "Platform Equalizer failed on this audio route")
            releaseToneInternal()
        }
    }

    private fun createToneEffect(audioSessionId: Int): ToneEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ModernDynamicsEffect.create(audioSessionId)?.let { return it }
        }
        return LegacyEqualizerEffect.create(audioSessionId)
    }

    private fun releaseAllInternal() {
        releaseToneInternal()
    }

    private fun releaseToneInternal() {
        toneEffect?.release()
        toneEffect = null
        lastAppliedCurve = null
    }
'''
assert target_apply_block in content, "target_apply_block not found"
content = content.replace(target_apply_block, new_apply_block, 1)

# 4. ModernDynamicsEffect.companion.create
target_dyn_comp = "".join(lines[303:320])
new_dyn_comp = '''        companion object {
            fun create(audioSessionId: Int): ModernDynamicsEffect? {
                for (channels in intArrayOf(12, 2)) {
                    val created = runCatching {
                        val config = DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                            channels,
                            true,
                            EQ_BAND_FREQS_HZ.size,
                            false, 0,
                            false, 0,
                            true,
                        ).build()
                        ModernDynamicsEffect(DynamicsProcessing(EFFECT_PRIORITY, audioSessionId, config))
                    }.onFailure {
                        Log.w(TAG, "DynamicsProcessing(${channels}ch) creation failed", it)
                    }.getOrNull()
                    if (created != null) return created
                }
                return null
            }
        }
'''
assert target_dyn_comp in content, "target_dyn_comp not found"
content = content.replace(target_dyn_comp, new_dyn_comp, 1)

# 5. companion object: delete unused MIN_BOOST_*, precompute EQ_LOG_FREQS, fast interpolateCurve
target_comp_obj = "".join(lines[354:391])
new_comp_obj = '''    private companion object {
        const val TAG = "FallbackAudioFX"
        const val EFFECT_PRIORITY = 0
        const val MILLIHERTZ_PER_HZ = 1_000
        const val MILLIBELS_PER_DB = 100f
        const val MAX_PRE_LIMITER_BOOST_DB = 1.0f
        // Platform-safe per-band ceiling once the uniform loudness offset is
        // added on top of the EQ curve.
        const val LOUDNESS_STAGE_MAX_GAIN_DB = 15f

        val EQ_LOG_FREQS = FloatArray(EQ_BAND_FREQS_HZ.size) { ln(EQ_BAND_FREQS_HZ[it].toFloat()) }

        fun calculateHeadroomDb(gainsDb: FloatArray): Float {
            val maximumBoost = gainsDb.filter { it.isFinite() }.maxOrNull()?.coerceAtLeast(0f) ?: 0f
            return -(maximumBoost - MAX_PRE_LIMITER_BOOST_DB).coerceAtLeast(0f)
        }

        fun interpolateCurve(hz: Int, gainsDb: FloatArray): Float {
            if (gainsDb.size != EQ_BAND_FREQS_HZ.size) return 0f
            if (hz <= EQ_BAND_FREQS_HZ.first()) return gainsDb.first()
            if (hz >= EQ_BAND_FREQS_HZ.last()) return gainsDb.last()
            val logHz = ln(hz.toFloat())
            for (index in 0 until EQ_BAND_FREQS_HZ.lastIndex) {
                if (hz in EQ_BAND_FREQS_HZ[index]..EQ_BAND_FREQS_HZ[index + 1]) {
                    val position = (logHz - EQ_LOG_FREQS[index]) /
                        (EQ_LOG_FREQS[index + 1] - EQ_LOG_FREQS[index])
                    return gainsDb[index] + (gainsDb[index + 1] - gainsDb[index]) * position
                }
            }
            return 0f
        }
    }
'''
assert target_comp_obj in content, "target_comp_obj not found"
content = content.replace(target_comp_obj, new_comp_obj, 1)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Successfully patched AudioEffectsEngine.kt")
