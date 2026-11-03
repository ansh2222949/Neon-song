# Android APK builds

The app version is maintained in `app/build.gradle.kts` and mirrored in `version.json`.

## Local debug APK

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs are large and use a debug signing key; they are intended for local installation and are not production release packages.

## GitHub downloads

Use [NeonSong.apk](https://github.com/ansh2222949/Neon-song/releases/latest/download/NeonSong.apk) on Android 10 or newer, or browse the [latest GitHub Release](https://github.com/ansh2222949/Neon-song/releases/latest) for all variants. On a phone, open the APK link, download the file, then tap it from Downloads to install. Android may ask you to allow APK installs for the browser or file manager. Android 7–9 users should select the legacy APK in the release assets.

The release workflow publishes the stable `NeonSong.apk` name, versioned universal and legacy packages, and SHA-256 checksums. Before running it, add a persistent Android signing key to GitHub Actions secrets: `SIGNING_KEY` (base64 encoded keystore) and `KEY_STORE_PASSWORD` or `RELEASE_STORE_PASSWORD`. The key alias defaults to `release_key`; if your keystore uses another alias, set `ALIAS` or `RELEASE_KEY_ALIAS`. The key password defaults to the store password; set `KEY_PASSWORD` or `RELEASE_KEY_PASSWORD` if they differ. The workflow stops before publishing if the required signing key or store password is missing.

APK files, generated build outputs, local databases, diagnostic dumps, backup snapshots, and internal audit reports are excluded from Git by the root `.gitignore`. See [the documentation index](../docs/README.md) for public project documentation.
