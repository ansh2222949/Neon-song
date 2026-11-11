# 03 — CODEBASE DEPENDENCY MAP & JNI BRIDGES

**Project:** Neon Song  
**Document:** Call Graph, Coupling Matrix & JNI Symbol Registry  
**Status:** Read-Only Discovery Phase Completed  
**Source Directory:** `D:\NEON-SONG-EXPERIMENT`  

---

## 1. High-Level Subsystem Coupling Matrix

The following table details direct code references between top-level functional areas. The highest coupling exists between the presentation layer (`ui`) and the monolithic playback controller (`playback`).

| Calling Subsystem | Target Subsystem | Coupling Volume | Key Coupling Reason |
| :--- | :--- | :--- | :--- |
| `com` | `data` | 591 references |
| `com` | `ui` | 586 references |
| `com` | `playback` | 118 references |
| `com` | `util` | 21 references |
| `com` | `R` | 16 references |
| `com` | `diagnostics` | 14 references |
| `com` | `widget` | 4 references |
| `com` | `MainActivity` | 3 references |
| `com` | `BuildConfig` | 2 references |

---

## 2. Identified Circular Dependencies & Cross-Feature State Mutations

### 2.1 Critical Monolithic State Leaks
In the current architecture, `MusicPlayer.kt` acts as a global mutable nexus:
1. **Direct UI Mutability:** `PlayerHost.kt`, `HomeScreen.kt`, and `PlaylistDetailScreen.kt` directly invoke state-mutating methods on `MusicPlayer` (e.g. `setEqBandGain`, `setPitch`, `setTempo`, `skipToNext`, `clearQueue`) without an intermediary command bus or contract interface.
2. **Settings Direct Mutation:** `SettingsScreen.kt` directly accesses both `SettingsPreferences` and internal playback hooks (`MusicPlayer.setDolbyAtmosEnabled`, `NativeAudioEngine.setBitPerfectMode`), bypassing a centralized Audio Settings repository.
3. **Lyrics Polling vs. Push:** `ModernLyricsView.kt` queries `MusicPlayer.currentPositionMs` via high-frequency UI polling (60 Hz recomposition loop) rather than receiving synchronized `LyricsSyncState` events.

---

## 3. Complete JNI Kotlin-to-C++ Symbol Mapping Table

Every native function exported by C++ and Rust relies on strict JNI name mangling (`Java_<package>_<class>_<method>`). If the corresponding Kotlin class is moved or renamed, Android will fail at runtime with `java.lang.UnsatisfiedLinkError`.

**Total Registered JNI Bridge Symbols: 83**

| C++ / Native Symbol | Source Native File | Target Kotlin Class (PACKAGE-LOCKED) | Target Kotlin Method | Return Type |
| :--- | :--- | :--- | :--- | :--- |
| `Java_com_neonsong_app_playback_NativeAutoDJEngine_nativeCreate` | `AutoDJBridge.cpp` | `com.neonsong.app.playback.NativeAutoDJEngine` | `nativeCreate` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAutoDJEngine_nativeDestroy` | `AutoDJBridge.cpp` | `com.neonsong.app.playback.NativeAutoDJEngine` | `nativeDestroy` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoDJEngine_nativeRecordLatency` | `AutoDJBridge.cpp` | `com.neonsong.app.playback.NativeAutoDJEngine` | `nativeRecordLatency` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoDJEngine_nativeEvaluate` | `AutoDJBridge.cpp` | `com.neonsong.app.playback.NativeAutoDJEngine` | `nativeEvaluate` | `jint` |
| `Java_com_neonsong_app_playback_NativeAutoDJEngine_nativeGetLatencyStats` | `AutoDJBridge.cpp` | `com.neonsong.app.playback.NativeAutoDJEngine` | `nativeGetLatencyStats` | `jfloatArray` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeCreate` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeCreate` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeDestroy` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeDestroy` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeConfigure` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeConfigure` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeSetEnabled` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeSetEnabled` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeSetBarMode` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeSetBarMode` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeSetCurveType` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeSetCurveType` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeTriggerFadeNow` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeTriggerFadeNow` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeTriggerFadeBars` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeTriggerFadeBars` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeTriggerFadeDurationMs` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeTriggerFadeDurationMs` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeReset` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeReset` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeWriteDeckBPcm` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeWriteDeckBPcm` | `jint` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeProcess` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeProcess` | `void` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeGetState` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeGetState` | `jint` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeGetTransitionProgress` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeGetTransitionProgress` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeGetDeckBLeadingTrimFrames` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeGetDeckBLeadingTrimFrames` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeGetDeckABpm` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeGetDeckABpm` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAutoMixEngine_nativeGetDeckBBpm` | `AutoMixBridge.cpp` | `com.neonsong.app.playback.NativeAutoMixEngine` | `nativeGetDeckBBpm` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeCreate` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeCreate` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeDestroy` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeDestroy` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeStart` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeStart` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeStop` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeStop` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsRunning` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeIsRunning` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushOutput` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeFlushOutput` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetPlaying` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetPlaying` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetOutputVolume` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetOutputVolume` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetStudioMasterClarity` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetStudioMasterClarity` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetBauerCrossfeedEnabled` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetBauerCrossfeedEnabled` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetAnalogTubeWarmthEnabled` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetAnalogTubeWarmthEnabled` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetMonoBassEnabled` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetMonoBassEnabled` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityWet` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetClarityWet` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityTrims` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetClarityTrims` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityPreset` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetClarityPreset` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetClarityAtmosBypass` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetClarityAtmosBypass` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetBitPerfect` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetBitPerfect` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsBitPerfect` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeIsBitPerfect` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetEqualizer` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetEqualizer` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeConfigureMediaProcessor` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeConfigureMediaProcessor` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeProcessMediaPcm` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeProcessMediaPcm` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushMediaProcessor` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeFlushMediaProcessor` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeResetMediaProcessor` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeResetMediaProcessor` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeWritePcm` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeWritePcm` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeWriteDeckPcm` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeWriteDeckPcm` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushDeck` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeFlushDeck` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckBufferedFrames` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetDeckBufferedFrames` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckVolume` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetDeckVolume` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetCrossfade` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetCrossfade` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetCrossfade` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetCrossfade` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckPlaybackParameters` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetDeckPlaybackParameters` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckSpeed` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetDeckSpeed` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckPitch` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetDeckPitch` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckTimeRatio` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetDeckTimeRatio` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckTimeRatio` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetDeckTimeRatio` | `jfloat` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeSetDeckPlaying` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeSetDeckPlaying` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsDeckPlaying` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeIsDeckPlaying` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeDrainDeckResampler` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeDrainDeckResampler` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsDeckResamplerDrained` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeIsDeckResamplerDrained` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetDeckFramesRendered` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetDeckFramesRendered` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeIsDeckWsolaDrained` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeIsDeckWsolaDrained` | `jboolean` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeGetPresentationLatencyUs` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeGetPresentationLatencyUs` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeWriteProcessedPcm` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeWriteProcessedPcm` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeFlushResampler` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeFlushResampler` | `void` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeOutputSampleRate` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeOutputSampleRate` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeBufferedFrames` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeBufferedFrames` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeUnderrunCount` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeUnderrunCount` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeRenderedFrames` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeRenderedFrames` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeRequestedSampleRate` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeRequestedSampleRate` | `jint` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeStreamOpenCount` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeStreamOpenCount` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeStreamRestartCount` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeStreamRestartCount` | `jlong` |
| `Java_com_neonsong_app_playback_NativeAudioEngine_nativeRateAdaptationCount` | `NativeBridge.cpp` | `com.neonsong.app.playback.NativeAudioEngine` | `nativeRateAdaptationCount` | `jlong` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativePublish` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativePublish` | `void` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeIsEngaged` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeIsEngaged` | `jboolean` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeCreate` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeCreate` | `jlong` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeDestroy` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeDestroy` | `void` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeConfigure` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeConfigure` | `void` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeReset` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeReset` | `void` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeProcessDirect` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeProcessDirect` | `jboolean` |
| `Java_com_neonsong_app_playback_eq_ParametricEqNative_nativeProcessArray` | `ParametricEqBridge.cpp` | `com.neonsong.app.playback.eq.ParametricEqNative` | `nativeProcessArray` | `jboolean` |
| `Java_com_neonsong_app_data_lossless_NativeSecrets_getDecryptionKeyNative` | `security/neonsong-vault/src/lib.rs (Rust)` | `com.neonsong.app.data.lossless.NativeSecrets` | `getDecryptionKeyNative` | `jbyteArray` |

---

## 4. Architectural Call Chain Verification

The target call chain strictly separates UI presentation from real-time audio:

```text
[Compose UI (PlayerHost / SettingsScreen)]
       │
       ▼ (Contract Calls)
[PlaybackController / AudioSettingsRepository]
       │
       ▼ (Domain Implementation)
[MusicPlayer / DefaultAudioSettingsRepository]
       │
       ▼ (AudioEngine Contract)
[NativeAudioEngineAdapter]
       │
       ▼ (Package-Locked JNI Bridge)
[com.neonsong.app.playback.NativeAudioEngine]
       │
       ▼ (JNI C-ABI: Java_com_neonsong_app_playback_NativeAudioEngine_*)
[NativeBridge.cpp / AutoMixBridge.cpp / AutoDJBridge.cpp / ParametricEqBridge.cpp]
       │
       ▼ (C++20 Audio Engine)
[Oboe Audio Stream & DSP Pipeline (DspProcessor / WSOLA / SPSC RingBuffer)]
```
