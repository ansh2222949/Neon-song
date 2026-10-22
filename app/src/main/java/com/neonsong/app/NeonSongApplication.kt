package com.neonsong.app

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.neonsong.app.data.repository.ThemeRepository
import com.neonsong.app.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NeonSongApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var themeRepository: dagger.Lazy<ThemeRepository>
    @Inject lateinit var applicationScope: CoroutineScope
    @Inject lateinit var okHttpClient: dagger.Lazy<okhttp3.OkHttpClient>
    @Inject lateinit var streamExtractor: dagger.Lazy<com.neonsong.app.data.music.YouTubeStreamExtractor>
    @Inject lateinit var innerTubeMusicApi: dagger.Lazy<com.neonsong.app.data.music.InnerTubeMusicApi>
    @Inject lateinit var ytMusicSyncManager: dagger.Lazy<com.neonsong.app.data.ytmusic.YtMusicSyncManager>
    @Inject lateinit var ytMusicHistorySyncManager: dagger.Lazy<com.neonsong.app.data.ytmusic.YtMusicHistorySyncManager>
    @Inject lateinit var likedSongsManager: dagger.Lazy<com.neonsong.app.data.playlist.LikedSongsManager>
    @Inject lateinit var trackDownloadManager: dagger.Lazy<com.neonsong.app.data.download.TrackDownloadManager>
    @Inject lateinit var discordPresenceManager: dagger.Lazy<com.neonsong.app.presence.DiscordPresenceManager>
    @Inject lateinit var appLocaleManager: dagger.Lazy<com.neonsong.app.util.AppLocaleManager>

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.neonsong.app.util.AppLocaleManager.wrap(base))
        com.neonsong.app.diagnostics.Diagnostics.install(this)
        StartupTrail.begin(this)
        StartupTrail.mark("app.attach")
    }

    override fun onCreate() {
        super.onCreate()
        StartupTrail.mark("app.onCreate.start")
        runCatching { appLocaleManager.get().start() }
        runCatching { com.neonsong.app.playback.PlaybackDiagnostics.install(this) }
        runCatching { com.neonsong.app.data.music.potoken.BotGuardTokenGenerator.initialize(this) }
        runCatching { com.neonsong.app.data.canvas.CanvasCache.init(this) }

        applicationScope.launch(Dispatchers.IO) {
            runCatching { streamExtractor.get().preWarm() }
            runCatching { innerTubeMusicApi.get().preWarmPlayback() }
        }
        applicationScope.launch(Dispatchers.IO) {
            delay(OPTIONAL_STARTUP_DELAY_MS)
            runCatching {
                val orphanCutoff = System.currentTimeMillis() - ORPHAN_TEMP_MAX_AGE_MS
                val orphanPrefixes = setOf(
                    "dl_raw_", "dl_remux_", "tagged_",
                    "untagged_", "trans_pcm_", "trans_flac_",
                )
                cacheDir.listFiles { file ->
                    file.isFile &&
                        orphanPrefixes.any { prefix -> file.name.startsWith(prefix) } &&
                        file.lastModified() < orphanCutoff
                }?.forEach { file -> runCatching { file.delete() } }
            }
            runCatching { likedSongsManager.get().start() }
                .onFailure { com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "Liked Songs startup disabled", it) }
        }

        applicationScope.launch {
            delay(OPTIONAL_STARTUP_DELAY_MS)
            runCatching { ytMusicSyncManager.get().start() }
                .onFailure { com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "YT sync startup disabled", it) }
        }

        applicationScope.launch {
            delay(OPTIONAL_STARTUP_DELAY_MS)
            runCatching { ytMusicHistorySyncManager.get().start() }
                .onFailure { com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "YT history sync startup disabled", it) }
        }

        applicationScope.launch(Dispatchers.IO) {
            delay(OPTIONAL_STARTUP_DELAY_MS)
            runCatching { trackDownloadManager.get().syncDownloadsFromStorage() }
                .onFailure { com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "Download sync startup failed", it) }
        }

        applicationScope.launch(Dispatchers.IO) {
            delay(OPTIONAL_STARTUP_DELAY_MS)
            try {
                themeRepository.get().uiState
                    .map { it.colorScheme.primary to it.colorScheme.onPrimary }
                    .distinctUntilChanged()
                    .collect {
                        WidgetUpdater.refreshTheme(this@NeonSongApplication)
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "Widget theme observer disabled", error)
            }
        }
        StartupTrail.mark("app.onCreate.end")
    }

    override fun newImageLoader(): ImageLoader {
        val lowRamDevice = runCatching {
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        }.getOrDefault(false)
        val imageClient = try {
            okHttpClient.get().newBuilder()
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = if (lowRamDevice) 16 else 48
                maxRequestsPerHost = if (lowRamDevice) 4 else 8
            })
            .connectionPool(okhttp3.ConnectionPool(if (lowRamDevice) 8 else 16, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        } catch (error: Exception) {
            com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "Shared artwork client unavailable; using isolated client", error)
            okhttp3.OkHttpClient.Builder().build()
        } catch (error: LinkageError) {
            com.neonsong.app.diagnostics.AppLog.e("NeonSongStartup", "Shared artwork client unsupported; using isolated client", error)
            okhttp3.OkHttpClient.Builder().build()
        }

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRamDevice) 0.08 else 0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (lowRamDevice) LOW_RAM_IMAGE_DISK_CACHE_BYTES else IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(150)
            .build()
    }

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 32L * 1024 * 1024
        const val LOW_RAM_IMAGE_DISK_CACHE_BYTES = 16L * 1024 * 1024
        const val ORPHAN_TEMP_MAX_AGE_MS = 6L * 60 * 60 * 1000
        const val OPTIONAL_STARTUP_DELAY_MS = 2_500L
    }
}
