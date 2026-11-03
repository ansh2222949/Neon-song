# Neon Song ⚡🎵

<p align="center">
  <img src="app/src/main/res/drawable/beru_icon_3d.png" width="128" height="128" alt="Neon Song Logo" style="border-radius: 28px; box-shadow: 0 10px 30px rgba(0,0,0,0.5);" />
</p>

<p align="center">
  <b>Next-Gen Native Android Music Player</b><br>
  <i>Powered by 4D Liquid Glass UI, YouTube Music Stream Engine, and Audiophile Soundstage.</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Audio-Media3%20ExoPlayer-FF6F00?style=for-the-badge" alt="Media3" />
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPLv3" />
</p>

> **Origin and license:** Neon Song is a modified derivative of [LastWave Native](https://github.com/Clash-Projects/LastWave-Native). This project is released under GPL-3.0. See [NOTICE.md](NOTICE.md) for the upstream credit and modification notice.

---

## 📥 Download

**Android 10 and newer:** [Download NeonSong.apk](https://github.com/ansh2222949/Neon-song/releases/latest/download/NeonSong.apk) · [Browse all releases](https://github.com/ansh2222949/Neon-song/releases/latest)

On your phone, open the download link, download the APK, then tap it in the Downloads app to install. Android may ask you to allow APK installs for the browser or file manager you used. For Android 7–9, choose the legacy APK from the release assets.

For development, run `./gradlew :app:assembleDebug`; the debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use a development signing key and are not suitable for updating a separately signed release installation. APKs and local build outputs are intentionally excluded from Git.

Project documentation is indexed in [docs/README.md](docs/README.md).

---

## 📱 Visual Showcase

<div align="center">

| 🏠 Home & Discovery | 🔎 Search |
|:---:|:---:|
| <img src="docs/screenshots/home_screen.png" width="260" alt="Neon Song home screen captured on Android" /> | <img src="docs/screenshots/search_screen.png" width="260" alt="Search screen captured on Android" /> |

<br>

| 🎵 Player | 💎 Audio & Theme Settings |
|:---:|:---:|
| <img src="docs/screenshots/player_screen.png" width="260" alt="Neon Song player captured on Android" /> | <img src="docs/screenshots/settings_screen.png" width="260" alt="Audio and visual theme settings captured on Android" /> |

Screenshots were captured from a Samsung Galaxy S24 Ultra running Android 17.

</div>

---

## ✨ Features

### 🎧 4D Audiophile Player Experience
- **Depth-Elevated Artwork Card**: 3D perspective artwork cards with specular border highlights and soft shadow depth.
- **Dynamic Breathing Backdrops**: Ambient gradient mesh that subtly breathes and reacts with the track's dominant palette.
- **Ultra-Slim Wave Seekbar**: Ultra-sleek `3.0dp` waveform track with a floating jewel-bead scrubber and specular neon halo.
- **Real-Time Stream Format Detection**: Dynamic badge identifying active playback stream (`OPUS`, `HI-RES`, `LOSSLESS`, `ATMOS`).
- **Enhanced Motion Glow**: Atmospheric multi-layer breathing glow beneath the playback dock.

### ⚡ Liquid Glass UI & Theming
- **Translucent Glass Tiles**: Frosted acrylic materials (`18dp` backdrop blur, subtle inner stroke, elevated press feedback).
- **Apple-inspired Liquid Glass**: Translucent controls, responsive glass surfaces, and fluid touch feedback across player, search, and loading states.
- **Haptic Feedback**: Multi-tier tactile vibrations for button presses, scrubbing, and gesture triggers.

### 🌐 YouTube Music Streaming & Sync
- **Ad-Free Music Streaming**: High-efficiency Opus / AAC streaming directly from YouTube Music.
- **Background & Lockscreen Playback**: System notification controls with synced progress and media session integration.
- **In-App Music Video Mode**: One-tap toggle to watch the official YouTube music video synchronized with current audio.

### 🎤 Real-Time Synced Karaoke Lyrics
- Millisecond-accurate word & line synchronized lyrics.
- Smooth spring-based auto-scrolling with tactile swipe seeking.

### 💾 Offline Downloads Vault
- One-tap download to local storage.
- Embedded high-res artwork, artist metadata, and clean folder structures.

---

## 🛠️ Architecture & Tech Stack

| Component | Technology |
|---|---|
| **Architecture** | Clean Architecture + MVVM + Unidirectional Data Flow |
| **Language & UI** | Kotlin + Jetpack Compose with app-owned Liquid Glass controls |
| **Audio Engine** | AndroidX Media3 ExoPlayer with native audio pipelines |
| **Local Database** | Room Database + Jetpack DataStore |
| **Dependency Injection** | Dagger Hilt |
| **Asynchronous Engine** | Kotlin Coroutines & Reactive Flows |
| **Network & Parsing** | Ktor, OkHttp, Retrofit |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17 or higher
- Android SDK 37 and NDK 28.2.13676358 (Target API 35, Min API 29)

### Build & Run
```bash
# Clone the repository
git clone https://github.com/ansh2222949/Neon-song.git
cd Neon-song

# Assemble debug APK
./gradlew assembleDebug

# Install directly to a connected device
./gradlew installDebug
```

See [Builds and releases](builds/README.md) for local APK builds, phone downloads, and the signing requirements for GitHub releases.

---

## 📄 License
This modified project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Bundled libraries, media, and other third-party materials may carry separate license terms; retain their notices and check their licenses before redistributing them.
