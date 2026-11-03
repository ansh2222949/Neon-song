import java.io.File
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.neonsong.app"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    val localProps = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { load(it) }
        }
        val envFile = rootProject.file(".env")
        if (envFile.exists()) {
            envFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    setProperty(parts[0].trim(), parts[1].trim())
                }
            }
        }
    }

    fun resolveSecret(vararg keys: String): String {
        for (key in keys) {
            val fromEnv = System.getenv(key)
            if (!fromEnv.isNullOrBlank()) return fromEnv.trim().replace("\r", "").replace("\n", "").replace("\"", "").replace("\\", "")
            val fromGradle = project.findProperty(key) as? String
            if (!fromGradle.isNullOrBlank()) return fromGradle.trim().replace("\r", "").replace("\n", "").replace("\"", "").replace("\\", "")
            val fromLocal = localProps.getProperty(key)
            if (!fromLocal.isNullOrBlank()) return fromLocal.trim().replace("\r", "").replace("\n", "").replace("\"", "").replace("\\", "")
            val dotEnv = rootProject.file(".env")
            if (dotEnv.isFile) {
                dotEnv.useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("$key=")) {
                            val v = trimmed.substringAfter("=").trim().replace("\"", "").replace("\\", "")
                            if (v.isNotBlank()) return v
                        }
                    }
                }
            }
        }
        return ""
    }

    defaultConfig {
        // A distinct ID lets Neon Song install beside the upstream app.
        applicationId = "com.neonsong.app"
        minSdk = (project.findProperty("minSdk") as? String)?.toIntOrNull() ?: 29
        targetSdk = 35
        versionCode = 25
        versionName = "4.2.5"

        val neonSongLyricsToken = resolveSecret("NEONSONG_LYRICS_TOKEN")
        val neonSongLyricsUrl = resolveSecret("NEONSONG_LYRICS_URL")
        buildConfigField("String", "NEONSONG_LYRICS_TOKEN", "\"$neonSongLyricsToken\"")
        buildConfigField("String", "NEONSONG_LYRICS_URL", "\"$neonSongLyricsUrl\"")

        // Native secrets (addon client lock) live strictly in native .so via
        // Rust vault secrets_generated.rs (tools/generate_native_secrets.py).
        // No secret fields are exposed in DEX / BuildConfig.

        externalNativeBuild {
            cmake {
                targets += listOf("neonsong_audio", "soxr")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    // Android 15+ can boot with 16 KB memory pages; all native
                    // libraries must be built/aligned accordingly. Ignored
                    // harmlessly by NDK toolchains that predate the flag.
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DCMAKE_POLICY_VERSION_MINIMUM=3.5",
                )
            }
        }
    }

    signingConfigs {
        create("release_config") {
            val base64Key = resolveSecret("SIGNING_KEY")
            val storeFilePath = resolveSecret("RELEASE_STORE_FILE")
            val storePasswordProp = resolveSecret("RELEASE_STORE_PASSWORD", "KEY_STORE_PASSWORD")
            val keyAliasProp = resolveSecret("RELEASE_KEY_ALIAS", "ALIAS").ifBlank { "release_key" }
            val keyPasswordProp = resolveSecret("RELEASE_KEY_PASSWORD", "KEY_PASSWORD").ifBlank { storePasswordProp }

            val keystoreFile: File? = when {
                base64Key.isNotBlank() -> {
                    try {
                        val decodedBytes = Base64.getDecoder().decode(base64Key.trim())
                        val tempKeystore = layout.buildDirectory.file("signing/release.keystore").get().asFile
                        tempKeystore.parentFile.mkdirs()
                        tempKeystore.writeBytes(decodedBytes)
                        tempKeystore
                    } catch (_: Exception) {
                        null
                    }
                }
                storeFilePath.isNotBlank() -> file(storeFilePath)
                else -> null
            }

            if (keystoreFile != null && keystoreFile.exists() && storePasswordProp.isNotBlank()) {
                storeFile = keystoreFile
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            } else {
                val debugConfig = getByName("debug")
                val debugFile = debugConfig.storeFile ?: File(System.getProperty("user.home"), ".android/debug.keystore")
                if (!debugFile.exists() || debugFile.length() == 0L) {
                    try {
                        debugFile.parentFile?.mkdirs()
                        val javaHome = System.getProperty("java.home")
                        val isWin = org.gradle.internal.os.OperatingSystem.current().isWindows
                        val keytoolPath = File(javaHome, if (isWin) "bin/keytool.exe" else "bin/keytool")
                        val keytoolExec = if (keytoolPath.exists()) keytoolPath.absolutePath else "keytool"
                        ProcessBuilder(
                            keytoolExec,
                            "-genkeypair",
                            "-noprompt",
                            "-keystore", debugFile.absolutePath,
                            "-storepass", "android",
                            "-alias", "androiddebugkey",
                            "-keypass", "android",
                            "-keyalg", "RSA",
                            "-keysize", "2048",
                            "-validity", "10000",
                            "-dname", "CN=Android Debug,O=Android,C=US"
                        ).start().waitFor()
                    } catch (_: Exception) {}
                }
                initWith(debugConfig)
                if (debugFile.exists()) {
                    storeFile = debugFile
                }
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release_config")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("rawRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            // Raw variant — no code/resource shrinking, no ProGuard/R8
            signingConfig = signingConfigs.getByName("release_config")
            // proguardFiles from initWith are ignored when minify is off
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by org.jellyfin.media3:media3-ffmpeg-decoder AAR metadata.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        ignoreWarnings = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.palette)

    // Home-screen "Now Playing" widget (Glance — Compose-style APIs over
    // RemoteViews), driven by the same MediaController access the local
    // scrobbler (MediaScrobbleListenerService) already holds.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Required even in a Compose-only app: Theme.Material3.DayNight.NoActionBar
    // (used as the AndroidManifest/splash theme parent in themes.xml) is an XML
    // style resource shipped by this artifact. androidx.compose.material3 is
    // Compose-only Kotlin and contributes no AAPT-resolvable style/ resources,
    // so without this dependency that parent can never be found by the linker.
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    // Lyrics sync uses the in-house reference implementation
    // (mirrors Metrolist/SimpMusic start-based active-index + per-frame
    // position, no external karaoke canvas). No gradle lyrics library.
    // Installs the baseline profiles bundled inside Compose (and other
    // androidx) AARs so hot UI paths are AOT-compiled on device instead of
    // running through JIT on first use — a large, zero-code smoothness win
    // for scrolling and animations in release builds.
    implementation(libs.androidx.profileinstaller)

    // Native in-app audio playback, background service, system media
    // controls, Bluetooth/headset controls and a MediaController-backed UI.
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    // Segmented provider-module path: DASH chunk source + CDM decryption.
    // Pinned to the same 1.2.1 line as exoplayer/hls to avoid binary mismatch.
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    // MediaBrowserServiceCompat/MediaSessionCompat bridge used by Android
    // Auto to browse the Neon Song library and control the same player.
    implementation("androidx.media:media:1.7.0")

    // GPLv3 Media3-matched FFmpeg software decoder (distribution must comply).
    // The renderer factory prefers FFmpeg for every codec it supports so all
    // devices decode through one deterministic, OEM-bug-free path; platform
    // decoders remain as automatic fallbacks.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.2.1+1")

    // Core library desugaring required by the FFmpeg decoder AAR metadata.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Low-latency native output. Version 1.10 remains API-compatible with the
    // requested Oboe 1.8+ baseline and exposes its CMake target through Prefab.
    implementation("com.google.oboe:oboe:1.10.0")

    // Metadata/search remains local InnerTube/NewPipe functionality; playback
    // resolves through InnerTubeX first and retains NewPipe as a fallback.
    implementation(libs.newpipe.extractor)

    // InnerTubeX is invoked through the compatibility adapter because its
    // current release is built with a newer Kotlin metadata version than the
    // app. Runtime-only keeps the app compiler on its existing Kotlin line.
    runtimeOnly(libs.innertubex)
    runtimeOnly("io.ktor:ktor-client-cio:3.5.2")

    // Unit Testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Bit-perfect USB exclusive output: audio_engine UsbAudioDriver.
    implementation(project(":audio:decent-usb-audio-driver"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.kotlin.get())
        }
    }
}

tasks.withType<Test> {
    maxHeapSize = "2048m"
}

// ============================================================================
// STAGE 7.1.3: RUST VAULT NATIVE RELEASE PIPELINE (Kotlin + Rust + Python)
// ============================================================================

val isReleaseTarget = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}
val isStrictSecrets = isReleaseTarget || project.hasProperty("strictSecrets") || (System.getenv("STRICT_SECRETS") == "true")

val pyExec = org.gradle.internal.os.OperatingSystem.current().let {
    if (it.isWindows) "python" else "python3"
}

// 1. Generate native secrets via Python (preBuild)
val generateNativeSecrets = tasks.register<Exec>("generateNativeSecrets") {
    workingDir = rootProject.projectDir
    val scriptArgs = mutableListOf(pyExec, "tools/generate_native_secrets.py")
    if (isStrictSecrets) {
        scriptArgs.add("--strict")
    }
    commandLine(scriptArgs)
    isIgnoreExitValue = false
}
tasks.matching { it.name.startsWith("preBuild") || it.name.startsWith("configureCMake") }.configureEach {
    dependsOn(generateNativeSecrets)
}

// Resolve NDK directory path outside task execution
val resolvedNdkPath: String? = System.getenv("ANDROID_NDK_HOME")
    ?: System.getenv("ANDROID_NDK_ROOT")
    ?: run {
        val sdkDir = File(System.getenv("ANDROID_HOME") ?: "C:/Users/anshs/AppData/Local/Android/Sdk")
        val ndkParent = File(sdkDir, "ndk")
        if (ndkParent.exists() && ndkParent.isDirectory) {
            ndkParent.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }?.absolutePath
        } else null
    }

// 2. Compile Rust native vault (security/neonsong-vault) using cargo-ndk
val buildRustVault = tasks.register<Exec>("buildRustVault") {
    dependsOn(generateNativeSecrets)
    workingDir = File(rootProject.projectDir, "security/neonsong-vault")

    inputs.dir(File(rootProject.projectDir, "security/neonsong-vault/src"))
    inputs.file(File(rootProject.projectDir, "security/neonsong-vault/Cargo.toml"))
    outputs.dir(File(project.projectDir, "src/main/jniLibs"))

    if (resolvedNdkPath != null) {
        environment("ANDROID_NDK_HOME", resolvedNdkPath)
    }

    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", "${project.projectDir}/src/main/jniLibs",
        "build", "--release"
    )
    isIgnoreExitValue = false
}

// Ensure Rust vault is built before AGP gathers/merges JNI folders and native libraries
tasks.matching {
    it.name.contains("JniLibFolders", ignoreCase = true) ||
    it.name.contains("NativeLibs", ignoreCase = true)
}.configureEach {
    dependsOn(buildRustVault)
}
