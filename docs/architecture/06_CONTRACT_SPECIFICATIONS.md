# 06 — CORE CONTRACT SPECIFICATIONS (`core/contracts/`)

**Project:** Neon Song  
**Document:** Public Reactive Interfaces & Domain Contracts  
**Target Environment:** `D:\NEON-SONG-EXPERIMENT`  

---

## 1. Overview & Contract Tenets

All cross-feature communication in Neon Song occurs exclusively through the stable Kotlin contracts defined in `core/contracts/`. 

**Rules:**
1. Contracts expose state via Kotlin Coroutines `StateFlow` and actions via suspended functions or non-blocking calls.
2. Contracts only use shared domain models (`Track`, `AudioFormat`, `LyricsLine`, `EqPreset`).
3. Never expose Android framework internals (`Context`, `ExoPlayer`, `MediaCodec`) through contracts.

---

## 2. Playback & Queue Contracts

```kotlin
package com.neonsong.app.core.contracts

import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val deckAActive: Boolean = true,
    val currentDeckSampleRate: Int = 48000,
    val bitDepth: Int = 16,
    val audioFormat: String = "AAC"
)

interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>
    val currentTrack: StateFlow<TrackDomainModel?>
    
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun playTrack(track: TrackDomainModel, autoPlay: Boolean = true)
    fun setPlaybackSpeed(speed: Float)
    fun setPitchShiftSemitones(semitones: Float)
}

data class QueueState(
    val items: List<TrackDomainModel> = emptyList(),
    val currentIndex: Int = -1,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

enum class RepeatMode { OFF, ONE, ALL }

interface QueueController {
    val queueState: StateFlow<QueueState>
    
    fun setQueue(tracks: List<TrackDomainModel>, startIndex: Int = 0)
    fun addToQueueNext(track: TrackDomainModel)
    fun addToQueueEnd(track: TrackDomainModel)
    fun moveItem(fromIndex: Int, toIndex: Int)
    fun removeItem(index: Int)
    fun clearQueue()
    fun toggleShuffle()
    fun setRepeatMode(mode: RepeatMode)
}
```

---

## 3. Lyrics Contract

```kotlin
package com.neonsong.app.core.contracts

import kotlinx.coroutines.flow.StateFlow

data class LyricsSyncState(
    val lines: List<LyricsLineModel> = emptyList(),
    val currentLineIndex: Int = -1,
    val currentSyllableIndex: Int = -1,
    val isSynchronized: Boolean = false,
    val providerName: String = "LRC-Lib",
    val timeOffsetMs: Long = 0L
)

data class LyricsLineModel(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val syllables: List<SyllableTimingModel> = emptyList()
)

data class SyllableTimingModel(
    val startMs: Long,
    val durationMs: Long,
    val text: String
)

interface LyricsProvider {
    val lyricsSyncState: StateFlow<LyricsSyncState>
    
    suspend fun loadLyricsForTrack(track: TrackDomainModel): Result<LyricsSyncState>
    fun setTimeOffset(offsetMs: Long)
    fun toggleTranslation(enabled: Boolean)
    fun toggleRomanization(enabled: Boolean)
}
```

---

## 4. Audio Settings & Theme Contracts

```kotlin
package com.neonsong.app.core.contracts

import kotlinx.coroutines.flow.StateFlow

data class AudioSettingsState(
    val audioQualityTier: String = "HIGH",
    val dolbyAtmosEnabled: Boolean = false,
    val bitPerfectOutput: Boolean = false,
    val crossfadeDurationSeconds: Float = 3.0f,
    val autoDjEnabled: Boolean = false,
    val autoEqProfile: String? = null,
    val parametricEqGains: FloatArray = FloatArray(10)
)

interface AudioSettingsRepository {
    val audioSettingsState: StateFlow<AudioSettingsState>
    
    suspend fun setAudioQuality(tier: String)
    suspend fun setDolbyAtmos(enabled: Boolean)
    suspend fun setBitPerfect(enabled: Boolean)
    suspend fun setCrossfadeDuration(seconds: Float)
    suspend fun setAutoDj(enabled: Boolean)
    suspend fun setEqBandGain(bandIndex: Int, gainDb: Float)
    suspend fun applyAutoEqProfile(profileName: String)
}

data class ThemeState(
    val isDarkMode: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
    val dynamicColorEnabled: Boolean = true,
    val accentColorHex: String = "#FF1390E8"
)

interface ThemeProvider {
    val themeState: StateFlow<ThemeState>
    
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setLiquidGlass(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAccentColor(hex: String)
}
```

---

## 5. AudioEngine Contract (Native Audio Bridge Abstraction)

```kotlin
package com.neonsong.app.core.contracts

interface AudioEngine {
    fun initializeEngine(sampleRate: Int, framesPerBurst: Int): Boolean
    fun startAudioStream(): Boolean
    fun pauseAudioStream(): Boolean
    fun stopAudioStream(): Boolean
    fun setMasterVolume(volume: Float)
    fun setDeckVolumes(deckAVolume: Float, deckBVolume: Float)
    fun setParametricEqBand(band: Int, frequency: Float, gainDb: Float, q: Float)
    fun setParametricEqEnabled(enabled: Boolean)
    fun setTempoAndPitch(tempo: Float, pitchSemitones: Float)
    fun setDolbyAtmosProcessing(enabled: Boolean, surroundStrength: Float)
    fun releaseEngine()
}
```

---

## 6. Feature Registration Contract

```kotlin
package com.neonsong.app.core.contracts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

interface FeatureRegistration {
    val featureId: String
    val displayName: String
    val icon: ImageVector?
    val route: String
    val entryComposable: @Composable () -> Unit
}
```
