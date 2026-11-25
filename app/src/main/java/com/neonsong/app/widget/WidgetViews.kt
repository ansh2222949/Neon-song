package com.neonsong.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.neonsong.app.R

internal object WidgetViews {

    private const val PROGRESS_MAX = 1000

    private val eqFrames = intArrayOf(
        R.drawable.widget_eq_frame_0,
        R.drawable.widget_eq_frame_1,
        R.drawable.widget_eq_frame_2,
    )

    internal data class Resolved(
        val snapshot: WidgetSnapshot,
        val hasAccess: Boolean,
        val usableSession: Boolean,
    )

    internal fun resolve(context: Context): Resolved {
        val snapshot = WidgetSnapshot.read(context)
        val hasAccess = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }.getOrDefault(false)
        val usable = snapshot.hasSession &&
            (hasAccess || snapshot.sourcePackage == context.packageName)
        return Resolved(snapshot, hasAccess, usable)
    }

    private fun minWidthDp(context: Context, appWidgetId: Int): Int = runCatching {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    }.getOrDefault(0)

    private fun minHeightDp(context: Context, appWidgetId: Int): Int = runCatching {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    }.getOrDefault(0)

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedAssets: WidgetBakedAssets? = null

    private fun resolveAssets(context: Context, memoryArt: Bitmap?, artPath: String?): WidgetBakedAssets {
        val fallbackPrimary = 0xFF00E5FF.toInt()
        val key = when {
            memoryArt != null && !memoryArt.isRecycled -> "mem:${memoryArt.generationId}:${memoryArt.width}x${memoryArt.height}"
            !artPath.isNullOrBlank() -> "disk:$artPath"
            else -> "empty"
        }
        val existing = cachedAssets
        if (key == cachedKey && existing != null && existing.artSquircle?.isRecycled != true) {
            return existing
        }
        val source = when {
            memoryArt != null && !memoryArt.isRecycled -> memoryArt
            !artPath.isNullOrBlank() -> WidgetGlassRenderer.decodeScaledArt(artPath)
            else -> null
        }
        val baked = WidgetGlassRenderer.bake(
            sourceArt = source,
            isObsidian = false,
            fallbackPrimary = fallbackPrimary,
        )
        cachedKey = key
        cachedAssets = baked
        return baked
    }

    fun build(
        context: Context,
        appWidgetId: Int,
        eqFrame: Int? = null,
        progressOverride: Float? = null,
        memoryArt: Bitmap? = null,
    ): RemoteViews {
        val resolved = resolve(context)
        val assets = resolveAssets(context, memoryArt, resolved.snapshot.artPath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Samsung One UI responsive breakpoints (2x1, 3x1/4x1/5x1, 2x2, 4x2/4x3+)
            val sizeMap = mapOf(
                SizeF(110f, 48f) to buildLayout(context, R.layout.widget_now_playing_compact, resolved, eqFrame, progressOverride, assets),
                SizeF(215f, 48f) to buildLayout(context, R.layout.widget_now_playing, resolved, eqFrame, progressOverride, assets),
                SizeF(115f, 115f) to buildLayout(context, R.layout.widget_now_playing_square, resolved, eqFrame, progressOverride, assets),
                SizeF(240f, 115f) to buildLayout(context, R.layout.widget_now_playing_expanded, resolved, eqFrame, progressOverride, assets),
            )
            return RemoteViews(sizeMap)
        }

        return buildLayout(context, resolveLayoutId(context, appWidgetId), resolved, eqFrame, progressOverride, assets)
    }

    private fun resolveLayoutId(context: Context, appWidgetId: Int): Int {
        val width = minWidthDp(context, appWidgetId)
        val height = minHeightDp(context, appWidgetId)
        return when {
            height >= 115 && width < 240 -> R.layout.widget_now_playing_square
            height >= 115 && width >= 240 -> R.layout.widget_now_playing_expanded
            width in 1 until 215 -> R.layout.widget_now_playing_compact
            else -> R.layout.widget_now_playing
        }
    }

    fun buildProgressOnly(
        context: Context,
        appWidgetId: Int,
        progress: Float,
    ): RemoteViews {
        val step = (progress.coerceIn(0f, 1f) * PROGRESS_MAX).toInt()
        fun partial(layoutId: Int) = RemoteViews(context.packageName, layoutId).apply {
            setProgressBar(R.id.widget_progress, PROGRESS_MAX, step, false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(110f, 48f) to partial(R.layout.widget_now_playing_compact),
                    SizeF(215f, 48f) to partial(R.layout.widget_now_playing),
                    SizeF(115f, 115f) to partial(R.layout.widget_now_playing_square),
                    SizeF(240f, 115f) to partial(R.layout.widget_now_playing_expanded),
                ),
            )
        }
        return partial(resolveLayoutId(context, appWidgetId))
    }

    private fun buildLayout(
        context: Context,
        layoutId: Int,
        resolved: Resolved,
        eqFrame: Int?,
        progressOverride: Float?,
        assets: WidgetBakedAssets,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        bind(
            context = context,
            views = views,
            resolved = resolved,
            eqFrame = eqFrame,
            progressOverride = progressOverride,
            assets = assets,
            isCompact = (layoutId == R.layout.widget_now_playing_compact),
        )
        return views
    }

    private fun bind(
        context: Context,
        views: RemoteViews,
        resolved: Resolved,
        eqFrame: Int?,
        progressOverride: Float?,
        assets: WidgetBakedAssets,
        isCompact: Boolean,
    ) {
        val snapshot = resolved.snapshot
        if (!resolved.usableSession) {
            views.setViewVisibility(R.id.widget_empty_group, View.VISIBLE)
            views.setViewVisibility(R.id.widget_content_group, View.GONE)
            views.setImageViewResource(R.id.widget_empty_icon, R.drawable.widget_art_placeholder)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    val neutralSurface = ContextCompat.getColor(context, R.color.widget_surface)
                    views.setColorStateList(
                        R.id.widget_root,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(neutralSurface),
                    )
                }
            }
            if (resolved.hasAccess) {
                views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_name))
                views.setTextViewText(R.id.widget_empty_sub, "Start a song in any media app")
                views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAppPending(context))
            } else {
                views.setTextViewText(R.id.widget_empty_title, "Allow music access")
                views.setTextViewText(R.id.widget_empty_sub, "Tap to detect every media app")
                views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAccessPending(context))
            }
            return
        }

        views.setViewVisibility(R.id.widget_empty_group, View.GONE)
        views.setViewVisibility(R.id.widget_content_group, View.VISIBLE)

        val secondaryVis = if (isCompact) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_prev, secondaryVis)
        views.setViewVisibility(R.id.widget_next, secondaryVis)
        views.setViewVisibility(R.id.widget_eq_group, secondaryVis)
        views.setViewVisibility(R.id.widget_progress_row, secondaryVis)

        views.setTextViewText(R.id.widget_title, snapshot.title.ifBlank { "Unknown track" })
        views.setTextViewText(R.id.widget_subtitle, snapshot.artist.ifBlank { "Unknown artist" })

        val playing = snapshot.isPlaying
        views.setTextViewText(R.id.widget_state, if (playing) "Pause" else "Play")
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        runCatching {
            val frame = if (playing) eqFrames[(eqFrame ?: 0).mod(eqFrames.size)] else eqFrames[0]
            views.setImageViewResource(R.id.widget_eq_icon, frame)
        }

        runCatching {
            val fraction = (progressOverride ?: snapshot.progress).coerceIn(0f, 1f)
            views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, (fraction * PROGRESS_MAX).toInt(), false)
        }

        // Subtle song-reactive Liquid Glass tinting with guaranteed contrast
        val baseSurface = ContextCompat.getColor(context, R.color.widget_surface)
        val tintedSurface = ColorUtils.blendARGB(baseSurface, assets.primaryColor, 0.12f)
        val iconTint = ContextCompat.getColor(context, R.color.widget_icon_tint)

        runCatching {
            views.setTextColor(R.id.widget_state, assets.onPrimaryColor)
            views.setInt(R.id.widget_play_pause, "setColorFilter", assets.onPrimaryColor)
            views.setInt(R.id.widget_next, "setColorFilter", iconTint)
            views.setInt(R.id.widget_prev, "setColorFilter", iconTint)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_play_pause_container,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(assets.primaryColor),
                )
                views.setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(tintedSurface),
                )
                views.setColorStateList(
                    R.id.widget_progress,
                    "setProgressTintList",
                    ColorStateList.valueOf(assets.primaryColor),
                )
            }
        }

        // Cached 256px superellipse squircle artwork with specular Fresnel rim
        val art = assets.artSquircle
        if (art != null && !art.isRecycled) {
            views.setImageViewBitmap(R.id.widget_art, art)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        }

        views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAppPending(context))
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            WidgetActions.togglePending(context, NowPlayingWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause_container,
            WidgetActions.togglePending(context, NowPlayingWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            WidgetActions.prevPending(context, NowPlayingWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            WidgetActions.nextPending(context, NowPlayingWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next_container,
            WidgetActions.nextPending(context, NowPlayingWidgetReceiver::class.java),
        )
    }
}
