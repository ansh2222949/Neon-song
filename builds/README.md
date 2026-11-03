# Build Neon Song on your laptop

This guide is for building the app from source. The quick debug build works without private credentials. Lossless add-on signing needs a separate server key and a non-debuggable APK.

## 1. Install the build tools

Install:

- Android Studio and Android SDK Platform **37**
- Android NDK **28.2.13676358** and CMake **3.22.1** (install them from Android Studio → SDK Manager → SDK Tools)
- JDK **17 or newer**
- Python **3.9 or newer**
- Rust stable from [rustup.rs](https://rustup.rs/)

The Rust native library is built for three Android CPU types. In PowerShell, Terminal, or a command prompt, install the Android Rust targets and `cargo-ndk` once:

```shell
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
```

## 2. Build a regular debug APK

Clone the repository and enter its folder:

```shell
git clone https://github.com/ansh2222949/Neon-song.git
cd Neon-song
```

### Windows PowerShell

```powershell
$env:ANDROID_NDK_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk\ndk\28.2.13676358'
.\gradlew.bat :app:assembleDebug
```

If the SDK is installed in another folder, point `ANDROID_NDK_HOME` at that folder's `ndk\28.2.13676358` directory.

### macOS or Linux

Set `ANDROID_NDK_HOME` to the NDK folder installed by Android Studio, then run:

```shell
# Replace this example with the NDK path shown in Android Studio's SDK Manager.
export ANDROID_NDK_HOME="/path/to/Android/Sdk/ndk/28.2.13676358"
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a USB-connected phone with `.\gradlew.bat :app:installDebug` on Windows or `./gradlew :app:installDebug` on macOS/Linux.

The debug APK uses a development certificate. You can use it for normal playback and development, but the native add-on signer refuses debuggable builds. Adding the add-on key to a debug APK will not enable add-on sign-in.

## 3. Optional: build a private APK with a lossless add-on

Lossless playback also requires a personal add-on URL from a compatible provider. For the LastWave add-on, follow the [official setup guide](https://lastwave.app/addon/), complete its Telegram or Discord verification, and copy your personal URL. In the app, open **Settings → Provider Modules**, paste the complete URL, and tap **Connect**. Then choose a lossless tier under **Settings → Audio & Playback → Audio Quality**.

The personal URL is different from `ADDON_CLIENT_SECRET`:

- Your personal URL is used to identify your add-on account. Keep it private.
- `ADDON_CLIENT_SECRET` is a shared app-to-service signing key. A public clone cannot include it; request a valid key from the add-on service operator.

To make a private add-on-enabled APK:

1. Copy `.env.example` to `.env` in the repository root.
2. Edit `.env` and add `ADDON_CLIENT_SECRET=` followed by the genuine key provided by the service operator. Do not use the example placeholder.
3. If Python does not already have it, install the certificate reader needed by the strict release build with `python -m pip install cryptography`.
4. Build the non-debuggable release variant:

```powershell
.\gradlew.bat :app:assembleRelease
```

On macOS or Linux, use `./gradlew :app:assembleRelease`. The APK is at `app/build/outputs/apk/release/app-release.apk`.

For a private phone build, Gradle can use the computer's Android debug keystore when no release keystore is configured. That APK is for your own device, not a public release. A public release needs a persistent release keystore so future updates have the same signing identity.

The shared key is included in the APK's native library. Native packaging makes casual inspection harder; it cannot make a shared APK secret from people who receive it. Keep an add-on-enabled APK private. If the key was exposed, the service operator must rotate it.

## 4. GitHub Actions releases

The repository does not contain release credentials. The repository owner must add these under **GitHub → Settings → Secrets and variables → Actions** before a release workflow can publish:

- `ADDON_CLIENT_SECRET`
- `SIGNING_KEY` (base64-encoded release keystore)
- `KEY_STORE_PASSWORD` or `RELEASE_STORE_PASSWORD`
- Optional aliases/passwords: `ALIAS`, `KEY_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`

Never commit `.env`, a keystore, a personal add-on URL, or a built APK. `.env` and APK outputs are ignored by Git.

## Troubleshooting

- **`cargo` or `cargo-ndk` not found:** install Rust, reopen the terminal, then run `cargo install cargo-ndk --locked`.
- **NDK not found:** install NDK `28.2.13676358` in Android Studio SDK Manager and set `ANDROID_NDK_HOME` to its directory.
- **`ADDON_CLIENT_SECRET` is missing:** an add-on-enabled release needs the real key from its service operator. A normal debug build does not need it.
- **“Addon signing key is not configured” in a debug APK:** expected; build `assembleRelease` with a valid local secret.
- **“No address associated with hostname”:** the phone cannot resolve the add-on server's DNS name. Check the full URL, internet connection, and provider status; the app cannot repair the provider's DNS.

See [the project README](../README.md) for app features and the [changelog](../CHANGELOG.md) for version history.
