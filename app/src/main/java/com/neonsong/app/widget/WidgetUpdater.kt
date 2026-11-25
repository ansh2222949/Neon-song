package com.neonsong.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "WidgetUpdater"
private const val ART_FILE_NAME = "widget_now_playing_art.png"
// Partial progress updates send only an Int (0 bitmaps) -> 3s cadence is silky smooth with zero launcher lag
private const val PROGRESS_TICK_MS = 3_000L
private const val PROGRESS_STEPS = 200

object WidgetUpdater {

    @Volatile
    var animationFrame: Int = 0
        private set

    @Volatile
    var memoryArtBitmap: Bitmap? = null
        internal set

    /** Shared scope reused by widget receivers (prevents per-broadcast scope leaks). */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var tickerJob: Job? = null

    @Volatile
    private var lastPushedStep: Int = -1

    /** Starts a lightweight, screen-aware progress ticker while music is playing (idempotent). */
    fun startWaveAnimation(context: Context) {
        synchronized(this) {
            if (tickerJob?.isActive == true) return
            val app = context.applicationContext
            val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            tickerJob = scope.launch {
                while (isActive) {
                    delay(PROGRESS_TICK_MS)
                    val snapshot = WidgetSnapshot.read(app)
                    if (!snapshot.hasSession || !snapshot.isPlaying || !hasPlacedWidgets(app)) {
                        stopWaveAnimation()
                        return@launch
                    }
                    // Never wake Binder / Launcher while screen is off
                    if (powerManager?.isInteractive == false) continue

                    val fraction = readLiveFraction(app, snapshot.progress)
                    val step = (fraction * PROGRESS_STEPS).toInt().coerceIn(0, PROGRESS_STEPS)
                    if (step == lastPushedStep) continue
                    lastPushedStep = step

                    if (!pushProgressOnly(app, fraction)) {
                        stopWaveAnimation()
                        return@launch
                    }
                }
            }
        }
    }

    /** Stops the widget ticker. */
    fun stopWaveAnimation() {
        synchronized(this) {
            tickerJob?.cancel()
            tickerJob = null
            lastPushedStep = -1
        }
    }

    /** Stops the ticker if neither Standard nor Obsidian widgets are placed on the home screen. */
    fun stopIfNoWidgets(context: Context) {
        if (!hasPlacedWidgets(context.applicationContext)) {
            stopWaveAnimation()
        }
    }

    private fun hasPlacedWidgets(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        val standard = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidgetReceiver::class.java))
        if (standard.isNotEmpty()) return@runCatching true
        val obsidian = manager.getAppWidgetIds(ComponentName(context, ObsidianGlassWidgetReceiver::class.java))
        obsidian.isNotEmpty()
    }.getOrDefault(false)

    private val publishMutex = Mutex()

    suspend fun publish(
        context: Context,
        title: String,
        artist: String,
        album: String?,
        sourceApp: String,
        sourcePackage: String,
        art: Bitmap?,
        isPlaying: Boolean,
    ) = publishMutex.withLock {
        val app = context.applicationContext
        memoryArtBitmap = art
        val fraction = readLiveFraction(app, 0f)
        lastPushedStep = (fraction * PROGRESS_STEPS).toInt().coerceIn(0, PROGRESS_STEPS)

        val baseSnapshot = WidgetSnapshot(
            title = title,
            artist = artist,
            album = album.orEmpty(),
            sourceApp = sourceApp,
            sourcePackage = sourcePackage,
            artPath = null,
            isPlaying = isPlaying,
            hasSession = true,
            progress = fraction,
        )
        // Write snapshot BEFORE pushing so title/artist always match the new in-memory art (zero mismatch flash)
        WidgetSnapshot.write(app, baseSnapshot)
        val hasWidgets = pushAll(app, eqFrame = 0, progressOverride = fraction)

        // Persist scaled 256px artwork only when widgets are actually placed (memoryArtBitmap covers instant placement)
        if (hasWidgets) {
            val artPath = art?.let { writeArt(app, it) }
            if (artPath != null) {
                WidgetSnapshot.write(app, baseSnapshot.copy(artPath = artPath))
            }
        }

        if (isPlaying && hasWidgets) startWaveAnimation(app) else stopWaveAnimation()
    }

    suspend fun clear(context: Context) = publishMutex.withLock {
        val app = context.applicationContext
        memoryArtBitmap = null
        stopWaveAnimation()
        val current = WidgetSnapshot.read(app)
        WidgetSnapshot.write(
            app,
            current.copy(artPath = null, isPlaying = false, hasSession = false, progress = 0f),
        )
        pushAll(app)
    }

    suspend fun setPlaying(context: Context, isPlaying: Boolean) = publishMutex.withLock {
        val app = context.applicationContext
        val current = WidgetSnapshot.read(app)
        if (!current.hasSession) return@withLock
        val fraction = readLiveFraction(app, current.progress)
        lastPushedStep = (fraction * PROGRESS_STEPS).toInt().coerceIn(0, PROGRESS_STEPS)
        WidgetSnapshot.write(
            app,
            current.copy(isPlaying = isPlaying, progress = fraction),
        )
        val hasWidgets = pushAll(app, eqFrame = 0, progressOverride = fraction)
        if (isPlaying && hasWidgets) startWaveAnimation(app) else stopWaveAnimation()
    }

    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val hasWidgets = pushAll(app)
        val snapshot = WidgetSnapshot.read(app)
        if (hasWidgets && snapshot.hasSession && snapshot.isPlaying) {
            startWaveAnimation(app)
        }
    }

    suspend fun refreshTheme(context: Context) {
        pushAll(context.applicationContext)
    }

    internal fun updateWidgetIds(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        isObsidian: Boolean,
    ) {
        val app = context.applicationContext
        val art = memoryArtBitmap
        for (appWidgetId in ids) {
            runCatching {
                val views = if (isObsidian) {
                    ObsidianWidgetViews.build(app, appWidgetId, eqFrame = 0, progressOverride = null, memoryArt = art)
                } else {
                    WidgetViews.build(app, appWidgetId, eqFrame = 0, progressOverride = null, memoryArt = art)
                }
                manager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun pushAll(context: Context, eqFrame: Int? = null, progressOverride: Float? = null): Boolean =
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidgetReceiver::class.java))
            for (appWidgetId in ids) {
                val views = WidgetViews.build(context, appWidgetId, eqFrame, progressOverride, memoryArtBitmap)
                runCatching { manager.updateAppWidget(appWidgetId, views) }
            }
            val obsidianIds = manager.getAppWidgetIds(ComponentName(context, ObsidianGlassWidgetReceiver::class.java))
            for (appWidgetId in obsidianIds) {
                val views = ObsidianWidgetViews.build(context, appWidgetId, eqFrame, progressOverride, memoryArtBitmap)
                runCatching { manager.updateAppWidget(appWidgetId, views) }
            }
            ids.isNotEmpty() || obsidianIds.isNotEmpty()
        }.onFailure { Log.w(TAG, "widget push failed", it) }.getOrDefault(false)

    private fun pushProgressOnly(context: Context, progress: Float): Boolean =
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidgetReceiver::class.java))
            for (appWidgetId in ids) {
                val partial = WidgetViews.buildProgressOnly(context, appWidgetId, progress)
                runCatching { manager.partiallyUpdateAppWidget(appWidgetId, partial) }
            }
            val obsidianIds = manager.getAppWidgetIds(ComponentName(context, ObsidianGlassWidgetReceiver::class.java))
            for (appWidgetId in obsidianIds) {
                val partial = ObsidianWidgetViews.buildProgressOnly(context, appWidgetId, progress)
                runCatching { manager.partiallyUpdateAppWidget(appWidgetId, partial) }
            }
            ids.isNotEmpty() || obsidianIds.isNotEmpty()
        }.onFailure { Log.w(TAG, "widget partial progress push failed", it) }.getOrDefault(false)

    private fun readLiveFraction(context: Context, fallback: Float): Float = runCatching {
        val controller = WidgetActions.resolveController(context) ?: return fallback
        val state = controller.playbackState ?: return fallback
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (duration <= 0L) return fallback
        val position = if (state.state == PlaybackState.STATE_PLAYING) {
            state.position + (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime)
        } else {
            state.position
        }
        (position.toFloat() / duration).coerceIn(0f, 1f)
    }.getOrDefault(fallback)

    @Synchronized
    private fun writeArt(context: Context, bitmap: Bitmap): String? = runCatching {
        val file = File(context.filesDir, ART_FILE_NAME)
        val pending = File(context.filesDir, "$ART_FILE_NAME.pending")
        val largest = maxOf(bitmap.width, bitmap.height)
        val cached = if (largest <= 256) bitmap else {
            val scale = 256f / largest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
        FileOutputStream(pending).use { out -> cached.compress(Bitmap.CompressFormat.PNG, 90, out) }
        if (cached !== bitmap) cached.recycle()
        if (!pending.renameTo(file)) {
            pending.copyTo(file, overwrite = true)
            pending.delete()
        }
        file.absolutePath
    }.onFailure { Log.w(TAG, "failed to cache widget art", it) }.getOrNull()
}
