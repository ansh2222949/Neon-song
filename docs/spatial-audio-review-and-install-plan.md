# Spatial Audio Review and Phone Install Plan

## Request

Review the supplied DSP notes against the current engine, add the supported improvements and three spatial room presets, expose the presets in Settings and the player controls, then build, install, and inspect the app on the connected phone.

## Notes reviewed

- The first and fourth attachments propose replacing `DspProcessor.h/.cpp`, changing pinna filtering, spatial channel treatment, reflection polarity, haptic bass tracking, and per-block filter coefficients.
- The second and fifth attachments propose Studio Nearfield, Concert Hall, and Cinema Dome presets, persistence, native strength synchronization, and a Settings selector.
- The third attachment proposes always-on LFE recovery, center-channel vocal ducking, fixed DialNorm makeup gain, and stronger Atmos height cues.

## Current implementation findings

1. `DspProcessor` already accepts up to 12 PCM channels and has stereo and multichannel spatial paths, a low-pass LFE-to-front feed, and center-envelope surround ducking. The LFE feed currently runs only while Spatial Immersion is active.
2. The pinna stage uses a Chamberlin SVF recurrence for its 7.2 kHz cue. A TPT/ZDF SVF can replace that recurrence while keeping the existing stereo polarity and output shaping intact.
3. `dcBlockerR_` and `tubeDcBlockerR_` are prepared in `configure()`, but `process()` recalculates both coefficients per audio block. The haptic bass envelope also shares the 200 Hz tube low-pass coefficient instead of having its own 95 Hz coefficient.
4. Spatial Immersion already has a native strength setter, but no named presets, persisted preset choice, or preset controls.
5. The PCM processing API receives a channel count, not Atmos object metadata or an explicit speaker-layout description. It cannot establish E-AC-3 JOC decoding, a discrete 7.1.4 output route, or Dolby DialNorm values from the supplied code. Fixed +2 dB gain and claims of a true 12-speaker output are not supported by the current data path.
6. The initial `adb devices` check could not create ADB's `.android` directory in the restricted user profile. Recheck ADB with a workspace-local profile, then use the authorized install path if the sandbox still blocks device access.

## Implementation sequence

### 0. Protect current work

- Preserve existing workspace edits, especially the three already-modified `libneonsong_vault.so` files.
- Make targeted changes in the current implementation; do not replace entire DSP source files with the pasted versions.

### 1. Fix the verified DSP hot-path issues

- Add a dedicated 95 Hz haptic low-pass coefficient during `configure()` and use it in both haptic tracking paths.
- Set the configured DC-blocker coefficients to the same 5 Hz and 7 Hz behavior currently used by `process()`, then use the stored values in the audio callback.
- Replace the pinna Chamberlin recurrence with the supplied TPT/ZDF equations and configure its coefficients once per sample-rate change.
- Keep the current stereo polarity and channel summing behavior unless source-level analysis proves a signal-path defect; the pasted claims alone do not establish a polarity or comb-filter bug.

### 2. Add persisted spatial presets

- Add a typed `SpatialImmersionPreset` with IDs and strengths: Studio Nearfield `0.52`, Concert Hall `0.74`, and Cinema Dome `0.94`.
- Persist the selected ID in `SettingsPreferences`, defaulting existing installs to Concert Hall.
- Sync preset strength to each native engine and update it immediately when the user changes the selection.

### 3. Add preset controls

- Add the three-choice selector below the existing Spatial Immersion toggle in Audio & Playback settings.
- Add the same selector to `AudioControlsSheet` so the active profile can be changed from the player.
- Use the shared Liquid Glass surface treatment for the selector card and choices.
- Describe presets as spatial mix profiles; do not label stereo output as discrete 7.1.4 speaker playback.

### 4. Review unsupported Atmos claims

- Keep LFE recovery gated by Spatial Immersion until the PCM layout and route are explicit.
- Do not add a fixed DialNorm makeup gain without decoded loudness metadata and peak measurements.
- Do not alter surround polarity or add more synthesized channels based only on the pasted line references; preserve channel-aware behavior and document the layout boundary.

### 5. Build and install

- Compile and assemble a debug APK, preserving the pre-existing native library files in the working tree.
- Resolve the connected device with ADB, install package `com.neonsong.app`, launch it, and check that it opens and the preset selector is reachable.
- Inspect on-device playback controls and settings. Audio quality and spatial perception still require listening with the user's intended output device.

## Acceptance checklist

- [ ] Haptic tracking uses a dedicated 95 Hz coefficient; DC-blocker coefficients are prepared off the audio callback.
- [ ] Pinna filtering uses the TPT/ZDF recurrence without changing the existing stereo polarity path.
- [ ] Three presets persist, load, and update native strength on the active engine(s).
- [ ] Presets appear in Audio & Playback settings and AudioControlsSheet using Liquid Glass.
- [ ] Debug APK assembles and installs on the connected phone.
- [ ] App launches; preset controls are reachable; user can perform a listening check.
- [ ] Unsupported JOC decoding and DialNorm claims are not represented as implemented capabilities.
