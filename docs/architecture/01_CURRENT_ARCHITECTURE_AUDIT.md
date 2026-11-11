# 01 — CURRENT ARCHITECTURE AUDIT

**Project:** Neon Song  
**Inspection Date:** November 4, 2026  
**Status:** Read-Only Discovery Phase Completed  
**Repository Branch:** `experiment/modular-architecture` (isolated experimental clone)  
**Baseline Git Commit:** `38688d15`  

---

## 1. Executive Summary & Codebase Scale

Neon Song is an advanced, audiophile-grade Android music streaming and local playback application built with **Jetpack Compose**, **Media3 Dual-ExoPlayer architecture**, and a high-performance **C++20 / Oboe / SoXR real-time DSP audio engine**.

### 1.1 Volume Metrics
* **Total Kotlin/Java Source Files:** 356
* **Total Kotlin/Java Lines of Code (LOC):** 131,011
* **Total Native C++ Source & Header Files:** 22
* **Total Native C++ Lines of Code (LOC):** 6,170
* **Total JNI Exported Functions:** 91 (86 C++ methods across 4 bridges + 1 Rust vault bridge + 4 companion hooks)
* **Active DataStore Preference Keys:** 89 distinct keys across 5 preference categories
* **Room Database Tables:** 5 Entities (`SavedPlaylistEntity`, `SongPlayStatsEntity`, `DownloadedTrackEntity`, `ArtworkCacheEntity`, `RecommendationExclusionEntity`)
* **Room DAOs:** 5 DAOs
* **Baseline Unit Test Suite:** 538 tests executed (536 Passed, 2 Known Pre-Existing Failures in `SyncedVideoRepositoryTest`)

---

## 2. Monolithic & God Class Inventory

The codebase exhibits significant architectural strengths (such as zero-allocation real-time native audio and liquid-glass rendering pipelines) but also suffers from extreme concentration of concerns in several monolithic "God classes". These files represent the primary targets for feature decomposition.

| File Path | LOC | Functions | Primary Responsibilities Mixed Together |
| :--- | :--- | :--- | :--- |
| `playback/MusicPlayer.kt` | 8,216 | 213 | Dual ExoPlayer lifecycle, queue management, DSP parameters, lossless stream resolution, hot-swapping, AutoDJ, AutoMix, audio focus, sleep timer, ReplayGain, bit-perfect DAC routing, and duration verification. |
| `ui/settings/SettingsScreen.kt` | 6,776 | 55 | Audio Quality UI, Visual Theme, AutoEq, DSP toggle switches, Last.fm scrobbling, YouTube login, Cache management, Backup/Restore, Excluded songs, and Provider modules. |
| `ui/player/PlayerHost.kt` | 6,483 | 48 | Maximized FullPlayer, collapsed MiniPlayer, SleepTimer overlay, lyrics sheet modal, swipe/drag gesture handling, video player dialog, visualizer hooks, and audio output controls. |
| `data/music/InnerTubeMusicApi.kt` | 4,215 | 141 | YouTube Music InnerTube endpoint communication, protobuf/json parsing, search scraping, song radio, artist profiles, cipher deobfuscation, and stream extraction. |
| `ui/feed/FeedScreen.kt` | 4,006 | 36 | Apple Music home feed, genre pills, mood discovery, Last.fm recommendations, top charts, speed dial chips, and profile dialog. |
| `data/download/TrackDownloadManager.kt` | 3,434 | 58 | Parallel chunk downloading, SAF filesystem interaction, ID3/Vorbis tag writing, lossless stream grabbing, and Room download states. |
| `ui/settings/DownloadsScreen.kt` | 2,523 | 20 | Downloaded tracks UI, sorting, filtering, folder browsing, and storage deletion. |
| `ui/home/HomeScreen.kt` | 2,336 | 30 | Secondary home presentation, recent plays, recommendations, and bento grid layout. |
| `playback/NativeProcessingAudioSink.kt` | 1,584 | 84 | Custom Media3 AudioSink routing PCM frames to NativeAudioEngine or hardware AudioTrack. |
| `ui/player/ModernLyricsView.kt` | 1,475 | 11 | Syllable-by-syllable synchronized karaoke text rendering, auto-scrolling, and canvas effects. |
| `playback/MusicPlaybackService.kt` | 1,464 | 52 | Foreground service lifecycle, MediaSession handling, notification construction, and headset event receivers. |
| `data/lossless/LosslessMusicApi.kt` | 1,425 | 48 | Multi-tier lossless waterfall (Dolby Atmos -> Hi-Res -> CD Lossless -> MP3), addon client queries, and decoy detection. |
| `ui/theme/LiquidGlass.kt` | 1,323 | 27 | Shader-based blur, specular sheen, dynamic color sampling, and glass container layouts. |
| `ui/player/LyricsView.kt` | 1,266 | 8 | Traditional line-synced lyrics rendering, offset adjusting, and provider switcher. |
| `ui/playlist/PlaylistDetailScreen.kt` | 1,490 | 6 | Playlist tracks list, reordering, cover picker, search filtering, and playback actions. |

---

## 3. Subsystem-by-Subsystem Function Inventory

### 3.1 Application & Lifecycle Subsystem (`com.neonsong.app`)
* **`NeonSongApplication.kt`**: Application entry point; initializes Hilt dependency injection, StrictMode policies, CrashHandler, Notification channels, and native library loading (`System.loadLibrary("neonsong_audio")` and `System.loadLibrary("neonsong_vault")`).
* **`MainActivity.kt`**: Single-activity host; configures edge-to-edge window insets, predictive back gesture handler, dynamic theme provider, and hosts `NavGraph`.
* **`StartupTrail.kt`**: Measures cold startup latency and records time-to-first-frame (TTFF) benchmarks.

### 3.2 Playback & Media3 Architecture (`com.neonsong.app.playback`)
* **`MusicPlayer.kt`**: Central player coordinator.
  - *Public Methods:* `playTrack()`, `playQueue()`, `pause()`, `resume()`, `togglePlayPause()`, `seekTo()`, `next()`, `previous()`, `setSpeed()`, `setSleepTimerMinutes()`, `applyEqualizerSettings()`, `setBauerCrossfeedEnabled()`, `setAnalogTubeWarmthEnabled()`, `setMonoBassEnabled()`.
  - *Internal State:* Exposes `chromeState: StateFlow<PlayerChromeState>` and `progressState: StateFlow<PlaybackProgressState>`.
  - *ExoPlayer Architecture:* Employs a Dual-ExoPlayer setup with primary player (`player`) and secondary player (`crossfadePlayer`) to achieve zero-gap, overlap crossfading.
* **`NativeProcessingAudioSink.kt`**: Custom Media3 `AudioSink` implementation that intercepts decoded audio frames and feeds them into `NativeAudioEngine` ring buffers.
* **`NativeAudioEngine.kt`**: Kotlin JNI bridge declaring 52 native methods calling into `AudioEngine.cpp`.
* **`NativeAutoMixEngine.kt`**: Kotlin JNI bridge declaring 17 native methods calling into `AutoMixEngine.cpp`.
* **`NativeAutoDJEngine.kt`**: Kotlin JNI bridge declaring 5 native methods calling into `AutoDJProcessor.cpp`.
* **`MusicPlaybackService.kt`**: Android `MediaSessionService` that connects Android Auto, system media controls, Bluetooth AVRCP, and persistent notifications with `MusicPlayer`.

### 3.3 Native C++20 / Oboe / DSP Subsystem (`app/src/main/cpp`)
* **`AudioEngine.cpp` & `AudioEngine.h`**: Manages Oboe output audio stream (`oboe::AudioStreamCallback`), audio routing, SoXR high-quality resampler, and real-time mixing of Deck A and Deck B.
* **`DspProcessor.cpp` & `DspProcessor.h`**: Real-time DSP pipeline:
  - 16-band graphic equalizer with minimum-phase biquad IIR filters.
  - Studio Master Clarity harmonic exciter.
  - Bauer / Meier binaural crossfeed for headphone fatigue reduction.
  - Analog tube warmth / tape saturation soft-clipper using `std::tanh`.
  - Mono bass filter (<120 Hz) using a 4th-order Linkwitz-Riley crossover (`LinkwitzRileyFilter.h`).
* **`AutoMixEngine.cpp` & `AutoMixEngine.h`**: Automatic DJ crossfade engine with multiple transition curves: Equal Power, Club Bass Swap, Smooth S-Curve, and Fast Drop.
* **`WsolaTimeStretcher.cpp` & `WsolaTimeStretcher.h`**: Real-time WSOLA time-stretcher for pitch-preserved BPM alignment.
* **`BpmPhaseDetector.cpp` & `BpmPhaseDetector.h`**: Autocorrelation energy onset beat tracking algorithm.
* **`LockFreeRingBuffer.h`**: Single-Producer Single-Consumer (SPSC) lock-free ring buffer guaranteeing zero mutex contention in the audio callback.
* **`ParametricEq.cpp` & `ParametricEq.h`**: Multi-filter parametric equalizer supporting Peak, Low Shelf, High Shelf, and Band Pass biquads.

### 3.4 Lyrics Subsystem (`com.neonsong.app.data.lyrics` & `ui/player`)
* **`LyricsRepository.kt`**: Aggregates lyrics from local cache and remote providers.
* **`MultiProviderLyricsResolver.kt`**: Waterfall resolver querying LRCLib, K-Pop, NetEase, KuGou, and Genius.
* **`SyncedLyricsParser.kt`**: High-performance parser for LRC, Enhanced LRC, and syllable-by-syllable word timestamps.
* **`ModernLyricsView.kt` & `LyricsView.kt`**: Jetpack Compose UI with kinetic typography, vocal highlighting, and spring auto-scroll.

### 3.5 Audio Quality & DSP Configuration (`com.neonsong.app.ui.settings` & `playback/eq`)
* **`AudioQualityScreen.kt`**: Audiophile tier selector: Dolby Atmos (28), Hi-Res Lossless (27/7), CD Lossless (24), MP3 320k (1), and Data Saver (4).
* **`EqualizerEngine.kt`**: Bridges UI gain sliders to 16-band native DSP filters.
* **`ParametricEqNative.kt`**: JNI wrapper for parametric EQ filter curves.
* **`AutoEqScreen.kt` & `AutoEqEngine.kt`**: Profiles 4,000+ headphone models and applies compensatory parametric target curves.

### 3.6 UI & Liquid Glass Design System (`com.neonsong.app.ui.*`)
* **`PlayerHost.kt`**: Hosts maximized and mini player sheets.
* **`FeedScreen.kt` & `HomeScreen.kt`**: Apple Music-inspired discovery feed and statistics.
* **`PlaylistScreen.kt`**: Apple Music-inspired Library view featuring navigation rows and a 2-column square card grid.
* **`LiquidGlass.kt`**: RenderEffect and canvas-based frosted glass material engine with dynamic ambient color extraction.
* **`ExpressiveHeader.kt`**: Collapsing top header with liquid-glass backdrop and spring animations.

### 3.7 Data & Persistence Layer (`com.neonsong.app.data.*`)
* **`AppDatabase.kt`**: Room database with 5 tables and automatic migrations.
* **`SettingsPreferences.kt`**: Central DataStore repository managing 89 keys across audio, appearance, scrobbling, and system behavior.
* **`TrackDownloadManager.kt`**: Multi-stream chunked downloader with VorbisComment and ID3 tag injection.
* **`LosslessMusicApi.kt`**: Client library querying the lossless audio addon with HMAC verification via `neonsong-vault`.

---

## 4. Public API & State Ownership Inventory

| Subsystem | State Holder | Primary State Flows Exposed | Storage Backing |
| :--- | :--- | :--- | :--- |
| **Playback** | `MusicPlayer` | `chromeState: StateFlow<PlayerChromeState>`<br>`progressState: StateFlow<PlaybackProgressState>` | In-memory + `SessionPreferences` |
| **Queue** | `MusicPlayer` | `queue: StateFlow<List<PlayableTrack>>`<br>`queueIndex: StateFlow<Int>` | In-memory + Room DB |
| **Lyrics** | `LyricsRepository` | `currentLyrics: StateFlow<LyricsUiState>` | Memory cache + LRU disk cache |
| **Settings** | `SettingsPreferences` | `settingsFlow: Flow<UserSettings>` | Jetpack DataStore (`user_settings.preferences_pb`) |
| **Equalizer** | `EqualizerPreferences` | `equalizerFlow: Flow<EqualizerSettings>` | Jetpack DataStore (`equalizer.preferences_pb`) |
| **Theme** | `ThemePreferences` | `themeFlow: Flow<ThemeSettings>` | Jetpack DataStore (`theme.preferences_pb`) |
| **Library** | `PlaylistRepository` | `playlists: StateFlow<List<SavedPlaylist>>` | Room Database (`neonsong.db`) |
| **Downloads**| `TrackDownloadManager`| `activeDownloads: StateFlow<List<DownloadItem>>` | Room Database + SAF File System |

---

## 5. Baseline Test & Build Health Status

* **Build Status (`./gradlew assembleDebug`):** SUCCESSFUL (1m 37s clean build)
* **Total Executed Unit Tests:** 538
* **Passed Tests:** 536
* **Failed Tests:** 2 (Pre-existing in original project)
  1. `SyncedVideoRepositoryTest > resolveVideoWithDirectVideoIdCachesAndReturnsBundle` (Failed at line 46: cache expiration mock expectation)
  2. `SyncedVideoRepositoryTest > resolveVideoWithoutVideoIdFallsBackToInnerTubeSearch` (Failed at line 67: search query fallback parameter mismatch)
* **Test Health Verification:** All audio DSP, WSOLA, SPSC ring buffer, AutoMix, Crossfade, Equalizer, and Player synchronization tests passed cleanly.

---

## 6. Audit Conclusion & Phase Transition Sign-off

The read-only code and function inspection confirms that:
1. All JNI symbols and native functions are strictly bound to fixed packages.
2. The codebase has clear logical boundaries despite physical co-location in massive packages.
3. Decoupling must proceed through **contracts** (`core/contracts/`) while preserving the 5 low-level JNI bridge packages intact.
4. Target architecture blueprinting (Stage C) can now safely begin.
