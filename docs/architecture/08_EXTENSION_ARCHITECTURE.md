# 08 — EXTENSION & ADD-ON ARCHITECTURE

**Project:** Neon Song  
**Document:** Compile-Time Extension Points & Modular Add-On Framework  
**Target Environment:** `D:\NEON-SONG-EXPERIMENT`  

---

## 1. Extension Strategy Overview

Neon Song supports modular feature additions through **compile-time extension points**. We intentionally avoid complex runtime reflection, dynamic DEX loading, or fragile plugin class loaders in favor of **type-safe, compiler-verified contract registration**.

---

## 2. Identified Extension Points

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        CORE EXTENSION CONTRACTS                        │
├─────────────────────────┬──────────────────────────────────────────────┤
│ Extension Target        │ Base Contract Interface                     │
├─────────────────────────┼──────────────────────────────────────────────┤
│ 1. Audio Visualizers    │ core/contracts/AudioVisualizerPlugin.kt      │
│ 2. Lyrics Providers     │ core/contracts/LyricsSourcePlugin.kt         │
│ 3. DSP Audio Effects    │ core/contracts/DspEffectPlugin.kt            │
│ 4. Equalizer Algorithms │ core/contracts/EqualizerEnginePlugin.kt      │
│ 5. Visual Themes        │ core/contracts/ThemeDefinitionPlugin.kt      │
│ 6. Music Info Providers │ core/contracts/MetadataEnricherPlugin.kt     │
└─────────────────────────┴──────────────────────────────────────────────┘
```

---

## 3. Extension Registration Pattern

Extensions implement the relevant plugin contract and register themselves in `app/composition/FeatureRegistry.kt`:

```kotlin
// In core/contracts/AudioVisualizerPlugin.kt
interface AudioVisualizerPlugin {
    val id: String
    val displayName: String
    @Composable
    fun RenderVisualizer(
        fftFrequencies: FloatArray,
        waveformData: ByteArray,
        modifier: Modifier
    )
}

// In app/feature_registration/FeatureRegistry.kt
object FeatureRegistry {
    private val visualizers = mutableListOf<AudioVisualizerPlugin>()
    private val lyricsSources = mutableListOf<LyricsSourcePlugin>()
    
    fun registerVisualizer(plugin: AudioVisualizerPlugin) {
        visualizers.add(plugin)
    }
    
    fun getAvailableVisualizers(): List<AudioVisualizerPlugin> = visualizers.toList()
}
```

---

## 4. Compile-Time Registration Flow

1. **Step 1:** Developer creates a new folder in `features/` (e.g. `features/visualizer/waves/`).
2. **Step 2:** Developer implements `AudioVisualizerPlugin`.
3. **Step 3:** In `app/composition/VisualizerModule.kt`, the new plugin is added to the compile-time list.
4. **Step 4:** The visualizer automatically appears in the visualizer selection dropdown on the player screen with zero changes to `PlayerHost.kt` or `MusicPlayer.kt`.
