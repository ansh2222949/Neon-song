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

> **Personal project:** Neon Song is my personal, AI-assisted modification of [LastWave Native](https://github.com/Clash-Projects/LastWave-Native). It is an independent project, not an official LastWave release. The upstream project and this derivative are covered by GPL-3.0; see [NOTICE.md](NOTICE.md) for credits and change notes.

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

## What I changed for Neon Song

I personally adapted this project with help from AI coding tools. The original LastWave Native project and its contributors remain credited; the features below describe the main Neon Song changes, not original authorship of the upstream code.

- **Neon branding and Liquid Glass UI:** Apple-inspired translucent surfaces, animated player controls and loading states, updated search and queue panels, responsive artwork, and haptic feedback.
- **More reliable playback handoff:** Resolve the next track at the selected quality before it is needed, warm up to three upcoming AutoDJ tracks, show the upcoming track in the player, detect the active stream format, and retry/fall back when a source fails.
- **AutoDJ and AutoMix:** Dual-deck transitions, BPM/key-aware matching, crossfade curves, DJ controls, and a native audio mixing engine.
- **Audio tools:** Parametric EQ, studio clarity controls, output and loudness settings, and device-aware spatial/Atmos and bit-perfect handling.
- **Lossless providers:** Connect a compatible personal streaming add-on for FLAC, Hi-Res, or Dolby Atmos when that provider has a matching source. The app reports the source format and falls back when the requested source is unavailable.
- **Video and offline listening:** Synchronized music-video playback, screen-off audio handoff, and downloads with artwork and track metadata.
- **Build and release work:** Separate Android package branding, native Rust secret handling, and automated APK packaging/checksums.

The inherited LastWave features—such as YouTube Music search and playback, background controls, synced lyrics, library tools, and downloads—remain credited to the upstream project. See [CHANGELOG.md](CHANGELOG.md) for release-by-release changes.

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

### 🎚️ AutoDJ, AutoMix & Audio Tools
- BPM/key-aware AutoDJ suggestions and dual-deck AutoMix transitions.
- Choose transition curves and adjust DJ controls while a mix is playing.
- Tune playback with the parametric equalizer, Studio Clarity, output settings, and device-aware spatial audio handling.

### 🎼 Lossless & Hi-Res Add-ons
- Connect a personal provider URL to request CD-quality FLAC, Hi-Res FLAC, or Dolby Atmos when available.
- Choose a preferred maximum quality and see the format reported by the active stream.
- If the provider or a matching lossless source is unavailable, playback can fall back to YouTube Music quality.

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

## 🎧 Enable Lossless, Hi-Res, or Dolby Atmos

Lossless streaming needs a compatible external add-on; it is not included as a server in this repository.

1. Follow the [official LastWave add-on guide](https://lastwave.app/addon/) to verify with Telegram or Discord and copy your personal add-on URL.
2. In Neon Song, open **Settings → Provider Modules → Add Streaming Addon**, paste the complete URL (including `https://`), and tap **Connect**.
3. Open **Settings → Audio & Playback → Audio Quality**, turn on Lossless Audio, and choose a preferred quality tier.
4. Play a track. Available quality depends on the provider, track, network, and device; the app may use YouTube Music quality when no matching lossless source is available.

The app does not ask for the add-on website password. Website verification gives you a private URL; keep that URL private and do not post it in screenshots or issues. The add-on is a separate service, so a DNS message such as “No address associated with hostname” means the service hostname could not be resolved. Check the URL and service availability; changing app settings cannot repair the provider's DNS.

## 🚀 Build from source

### Quick build on Windows

Install Android Studio, then use SDK Manager to install **Android SDK Platform 37**, **NDK 28.2.13676358**, and **CMake 3.22.1**. Also install **JDK 17 or newer**, **Python 3.9 or newer**, and the stable Rust toolchain.

Open PowerShell in the cloned project folder and run:

```powershell
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
$env:ANDROID_NDK_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk\ndk\28.2.13676358'
.\gradlew.bat :app:assembleDebug
```

If Android Studio installed the SDK somewhere else, change the `ANDROID_NDK_HOME` path to that SDK's `ndk\28.2.13676358` folder. The debug APK appears at `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected phone, run `.\gradlew.bat :app:installDebug`.

For macOS or Linux, install the same Android SDK, NDK, CMake, Python, and Rust components, set `ANDROID_NDK_HOME` to the installed NDK folder, then run `cargo install cargo-ndk --locked`, `rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android`, and `./gradlew :app:assembleDebug`.

A normal debug build needs no private key and is suitable for trying the app's standard features. Add-on signing is intentionally unavailable in debuggable builds. For a private add-on-enabled release build, see [the local build and signing guide](builds/README.md); never commit a real secret or share an APK that contains one. GitHub release builds need maintainer-provided Actions secrets.

---

## 📄 License
This modified project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Bundled libraries, media, and other third-party materials may carry separate license terms; retain their notices and check their licenses before redistributing them.
