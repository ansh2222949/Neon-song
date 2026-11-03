# Android APK builds

The app version is maintained in `app/build.gradle.kts` and mirrored in `version.json`.

## Local debug APK

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs are large and use a debug signing key; they are intended for local installation and are not production release packages.

## GitHub downloads

Use [NeonSong.apk](https://github.com/ansh2222949/Neon-song/releases/latest/download/NeonSong.apk) on Android 10 or newer, or browse the [latest GitHub Release](https://github.com/ansh2222949/Neon-song/releases/latest) for all variants. On a phone, open the APK link, download the file, then tap it from Downloads to install. Android may ask you to allow APK installs for the browser or file manager. Android 7–9 users should select the legacy APK in the release assets.

The release workflow publishes the stable `NeonSong.apk` name, versioned universal and legacy packages, and SHA-256 checksums. Release builds require the genuine `ADDON_CLIENT_SECRET` plus a persistent Android signing key. In GitHub Actions, configure `ADDON_CLIENT_SECRET`, `SIGNING_KEY` (base64 encoded keystore), and `KEY_STORE_PASSWORD` or `RELEASE_STORE_PASSWORD`. The key alias defaults to `release_key`; if your keystore uses another alias, set `ALIAS` or `RELEASE_KEY_ALIAS`. The key password defaults to the store password; set `KEY_PASSWORD` or `RELEASE_KEY_PASSWORD` if they differ. The workflow stops before publishing if the addon secret or signing credentials are missing.

For local release builds, set `ADDON_CLIENT_SECRET` in the ignored `.env` or `local.properties` file. The native vault generator reads both files as well as environment variables. It intentionally refuses to make a release APK without the real secret; use `assembleDebug` for a local development build when the secret is unavailable.

APK files, generated build outputs, local databases, diagnostic dumps, backup snapshots, and internal audit reports are excluded from Git by the root `.gitignore`. See [the documentation index](../docs/README.md) for public project documentation.
