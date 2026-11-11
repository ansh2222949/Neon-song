# 02 — TARGET ARCHITECTURE BLUEPRINT

**Project:** Neon Song  
**Document:** Target Modular Architecture & Migration Blueprint  
**Status:** Approved for Implementation  
**Isolated Environment:** `D:\NEON-SONG-EXPERIMENT` (Branch: `experiment/modular-architecture`)  

---

## 1. Architectural Vision & Core Principles

The target architecture transforms Neon Song from a single monolithic application layer into a **contract-driven, highly modular, feature-oriented structure**. The core tenets are:

1. **Feature Independence:** Feature teams (Player, Queue, Lyrics, Settings, Library) can modify UI, ViewModels, and internal data structures without affecting unrelated subsystems.
2. **Contract-Based Boundaries (`core/contracts/`):** No feature may directly import another feature's internal ViewModel, Repository, or Composable. Cross-feature communication occurs strictly via reactive `StateFlow` and structured interfaces.
3. **Application Composition Root (`app/composition/`):** A lightweight DI/composition layer wires contracts to implementations at compile-time. There is no runtime service locator or global singleton God object.
4. **Native Audio Engine Isolation (`native/`):** The C++20 / Oboe / DSP / WSOLA / SoXR engine remains strictly isolated. The 5 low-level Kotlin JNI bridge classes remain package-locked (`com.neonsong.app.playback.NativeAudioEngine`, `NativeAutoMixEngine`, `NativeAutoDJEngine`, `ParametricEqNative`, `NativeSecrets`) to guarantee binary linkage without `UnsatisfiedLinkError`.
5. **Real-Time Thread Guarantees:** Zero heap allocations, zero blocking mutexes, zero JNI callbacks, and zero coroutine dispatches inside the Oboe real-time audio thread (`onAudioReady`).

---

## 2. Logical Target Directory Structure

```text
app/
  composition/            # Application composition root: DI bindings, feature & nav registration
  navigation/             # Top-level app navigation coordinator & destination routes
  startup/                # App initialization, crash handling & lifecycle orchestration
  feature_registration/   # Compile-time feature & extension wiring

core/
  common/                 # Coroutine dispatchers, Result wrappers, flow extensions, pure utils
  contracts/              # Stable cross-feature interfaces & shared domain models ONLY
    PlaybackController.kt # Playback actions, state stream, play/pause/seek contracts
    QueueController.kt    # Queue mutation, shuffle, repeat, reorder contracts
    LyricsProvider.kt     # Synchronized lyrics stream, syllable timing contracts
    AudioSettingsRepository.kt # Audio quality, bit-perfect DAC, AutoEq contracts
    AudioEngine.kt        # High-level Kotlin contract over native audio engine
    ThemeProvider.kt      # Dynamic colors, theme mode, liquid-glass contracts
    FeatureRegistration.kt# Extension point contract
  designsystem/           # Shared typography, color tokens, shapes, liquid-glass shaders, buttons
  database/               # Room database configuration, Room entities & migrations
  datastore/              # DataStore preferences infrastructure (preserving exact 97 keys)
  media/                  # Core Media3 / ExoPlayer abstractions & NativeDeckAudioSink bridge
  audio_api/              # High-level Kotlin interface to the isolated Native Audio Engine
  diagnostics/            # Audio latency benchmarks, glitch counters, logger
  testing/                # Shared test doubles & coroutine test rules

features/
  player/                 # FullPlayer, MiniPlayer, gesture handlers, PlayerViewModel
  lyrics/                 # ModernLyricsView, Karaoke text engine, lyrics fetchers
  queue/                  # QueueBottomSheet, QueueViewModel, drag-and-drop reordering
  settings/
    audio/                # QualitySelector, EqualizerSheet, AutoEq, DolbyAtmos
    appearance/           # ThemeSelector, LiquidGlass settings, color accents
    playback/             # Crossfade duration, AutoDJ preferences, ReplayGain
    library/              # Cache management, backup/restore, exclude lists
  library/                # SearchScreen, ArtistScreen, AlbumDetail, PlaylistDetail
  feed/                   # Apple-style Home Feed, curated pills, mood radios

data/
  repositories/           # Concrete repository implementations (MusicRepository, LyricsRepository)
  remote/                 # InnerTube API, Lossless waterfall, GitHub AutoEq sync
  local/                  # Local audio scanner, SAF file manager, Room DAOs
  cache/                  # Artwork disk cache, LRU stream chunk cache

native/                   # Strictly isolated JNI + C++20 Audio Engine
  jni/                    # Low-level JNI bridges (com.neonsong.app.playback.NativeAudioEngine, etc.)
  oboe/                   # Oboe stream lifecycle & real-time audio callback
  dsp/                    # Parametric EQ, SoXR resampler, limiter, Dolby Atmos DSP
  wsola/                  # WSOLA time-stretching & pitch preservation
  crossfade/              # Equal-power crossfade mixer
  audio_buffer/           # Lock-free SPSC ring buffers
```

---

## 3. Package Migration & Ownership Mapping

| Current Package / File | Target Logical Package | Migration Strategy | Boundary Classification |
| :--- | :--- | :--- | :--- |
| `playback/MusicPlayer.kt` | `features/player/` + `core/media/` | Split: AudioSink coordination moves to `core/media/`; queue moves to `features/queue/`; controller logic implements `PlaybackController`. | Internal + Contract |
| `playback/NativeAudioEngine.kt` | `native/jni/` (Package-Locked) | Retain exact package `com.neonsong.app.playback.NativeAudioEngine` to prevent JNI mangled symbol breakage. | Native Bridge |
| `playback/NativeAutoMixEngine.kt`| `native/jni/` (Package-Locked) | Retain exact package `com.neonsong.app.playback.NativeAutoMixEngine`. | Native Bridge |
| `playback/NativeAutoDJEngine.kt` | `native/jni/` (Package-Locked) | Retain exact package `com.neonsong.app.playback.NativeAutoDJEngine`. | Native Bridge |
| `playback/eq/ParametricEqNative.kt`| `native/jni/` (Package-Locked) | Retain exact package `com.neonsong.app.playback.eq.ParametricEqNative`. | Native Bridge |
| `data/lossless/NativeSecrets.kt` | `native/jni/` (Package-Locked) | Retain exact package `com.neonsong.app.data.lossless.NativeSecrets`. | Native Bridge |
| `ui/player/PlayerHost.kt` | `features/player/ui/` | Encapsulate full player & mini player views; access playback via `PlaybackController`. | Internal Composable |
| `ui/player/ModernLyricsView.kt` | `features/lyrics/ui/` | Encapsulate syllable-by-syllable karaoke canvas; listen to `LyricsSyncState`. | Internal Composable |
| `ui/player/LyricsView.kt` | `features/lyrics/ui/` | Traditional line lyrics; fallback provider. | Internal Composable |
| `ui/settings/SettingsScreen.kt` | `features/settings/` | Decompose into sub-screens (`audio/`, `appearance/`, `playback/`, `library/`). | UI Feature |
| `ui/theme/LiquidGlass.kt` | `core/designsystem/` | Shared liquid-glass shader modifier and glass containers. | Core Shared UI |
| `ui/theme/Theme.kt`, `Color.kt` | `core/designsystem/` | Shared design tokens and dynamic color sampling. | Core Design Tokens |
| `data/music/InnerTubeMusicApi.kt`| `data/remote/` | Remote API client; exposed to features only via `MusicRepository` contract. | Internal Data Source |
| `data/lossless/LosslessMusicApi.kt`| `data/remote/` | Lossless waterfall stream resolution. | Internal Data Source |
| `data/download/TrackDownloadManager.kt`| `data/local/` | Background track downloader and SAF manager. | Internal Service |
| `data/db/*` (5 Room Entities & DAOs)| `core/database/` | Exact schema preserved; DAOs exposed to repositories. | Core Persistence |
| `data/preferences/*` (89 DataStore keys)| `core/datastore/` | Exact preference keys preserved; exposed via settings contracts. | Core Persistence |

---

## 4. Visibility & Access Enforcement Rules

1. **`core/contracts/`**: All interfaces, data classes, and sealed states are `public`.
2. **`features/*`**: All internal ViewModels, internal UI components, and state holders are marked `internal`. Only the entry composable (e.g. `FullPlayer()`, `SettingsHost()`) and the feature contract implementation are exposed to `app/composition/`.
3. **`data/*`**: Repositories expose only domain models and contract interfaces. Network models (Protobuf, JSON DTOs) and Room entities remain `internal` to the data layer.
4. **`native/jni/`**: Kotlin JNI bridge classes are wrapped by `core/audio_api/NativeAudioEngineAdapter` which implements the `AudioEngine` contract. Features never call JNI external methods directly.
