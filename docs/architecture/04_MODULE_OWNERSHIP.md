# 04 — MODULE & FEATURE OWNERSHIP MATRIX

**Project:** Neon Song  
**Document:** Domain Boundaries, State Ownership & Data Flow Architecture  
**Target Environment:** `D:\NEON-SONG-EXPERIMENT`  

---

## 1. Feature Responsibility & Ownership Matrix

Each feature module is completely self-contained. It owns its internal ViewModels, UI Composables, local state, and domain logic. Features communicate with each other exclusively through the interfaces declared in `core/contracts/`.

| Feature Module | Primary State Owned | Internal UI Components | Contracts Implemented | Contracts Consumed |
| :--- | :--- | :--- | :--- | :--- |
| **`features/player`** | Playback status, current track, duration, deck A/B volume, audio format details. | `PlayerHost`, `FullPlayer`, `MiniPlayer`, `WaveformSeeker`, `AudioOutputDialog`. | `PlaybackController` | `QueueController`, `LyricsProvider`, `AudioSettingsRepository`, `ThemeProvider`. |
| **`features/queue`** | Current queue list, original un-shuffled queue, shuffle mode, repeat mode, history. | `QueueBottomSheet`, `QueueTrackItem`, `ReorderableQueueList`. | `QueueController` | `PlaybackController`. |
| **`features/lyrics`** | Synced lyrics lines, syllable timings, translation state, romanization state. | `ModernLyricsView`, `LyricsView`, `LyricsSyncCanvas`, `LyricsOffsetDialog`. | `LyricsProvider` | `PlaybackController` (receives sync position). |
| **`features/settings`** | Audio quality tier, EQ band gains, AutoEq profile, theme selection, Last.fm auth. | `SettingsScreen`, `AudioQualitySheet`, `EqualizerSheet`, `AutoEqDialog`, `ThemeSheet`. | `AudioSettingsRepository`, `ThemeProvider` | `PlaybackController` (re-sync audio params). |
| **`features/library`** | Search query, search history, saved playlists, artist detail, album detail. | `SearchScreen`, `ArtistDetailScreen`, `AlbumDetailScreen`, `PlaylistDetailScreen`. | `LibraryRepository` | `PlaybackController`, `QueueController`. |
| **`features/feed`** | Apple-style home shelves, mood pills, curated recommendations, speed dials. | `FeedScreen`, `AppleMusicShelf`, `MoodPillRow`, `ProfileMenu`. | `FeedProvider` | `PlaybackController`, `QueueController`. |
| **`native/`** | C++ audio engine state, Oboe stream, DSP filter taps, WSOLA ring buffer. | None (Headless native C++20). | `AudioEngine` (via adapter) | Oboe Hardware AudioTrack Sink. |

---

## 2. Forbidden Cross-Feature Dependencies

To guarantee that a developer working on **Lyrics** never has to inspect the rest of the application, the following import rules are enforced:

```text
❌ FORBIDDEN: features/lyrics  ──> features/player/PlayerViewModel
❌ FORBIDDEN: features/settings──> playback/MusicPlayer
❌ FORBIDDEN: features/queue   ──> features/player/FullPlayer
❌ FORBIDDEN: features/player  ──> features/settings/SettingsScreen
❌ FORBIDDEN: ui/*             ──> native/jni/NativeAudioEngine (Direct JNI call)

✅ ALLOWED:   features/lyrics  ──> core/contracts/PlaybackController (StateFlow only)
✅ ALLOWED:   features/settings──> core/contracts/AudioSettingsRepository
✅ ALLOWED:   features/player  ──> core/contracts/QueueController
✅ ALLOWED:   features/*       ──> core/designsystem/* (Theme tokens & LiquidGlass)
✅ ALLOWED:   app/composition ──> Wires all implementations to contracts
```

---

## 3. Data & Persistence Boundaries

1. **Audio Preferences:** Owned strictly by `features/settings/audio/` and persisted through `core/datastore/SettingsPreferences`.
2. **Appearance Preferences:** Owned strictly by `features/settings/appearance/` and persisted through `core/datastore/ThemePreferences`.
3. **Playback History & Room Entities:** Owned by `data/local/` and accessed via repository interfaces in `core/contracts/`.
4. **Downloaded Tracks:** Owned by `data/download/` and presented via `features/library/downloads/`.
