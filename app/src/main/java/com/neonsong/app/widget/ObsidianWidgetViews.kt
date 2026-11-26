package com.neonsong.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.neonsong.app.R

internal object ObsidianWidgetViews {

    private const val PROGRESS_MAX = 1000

    private val eqFrames = intArrayOf(
        R.drawable.widget_eq_frame_0,
        R.drawable.widget_eq_frame_1,
        R.drawable.widget_eq_frame_2,
    )

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

    private fun resolveAssets(memoryArt: Bitmap?, artPath: String?): WidgetBakedAssets {
        val fallbackPrimary = 0xFF7C4DFF.toInt()
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
            isObsidian = true,
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
        val resolved = WidgetViews.resolve(context)
        val assets = resolveAssets(memoryArt, resolved.snapshot.artPath)
        val brand = WidgetGlassRenderer.brandArtwork(context, isObsidian = true, fallbackPrimary = 0xFF7C4DFF.toInt())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Samsung One UI responsive breakpoints (2x1, 3x1/4x1/5x1, 2x2, 4x2/4x3+)
            val sizeMap = mapOf(
                SizeF(110f, 48f) to buildLayout(context, R.layout.widget_obsidian_compact, resolved, eqFrame, progressOverride, assets, brand),
                SizeF(215f, 48f) to buildLayout(context, R.layout.widget_obsidian_horizontal, resolved, eqFrame, progressOverride, assets, brand),
                SizeF(115f, 115f) to buildLayout(context, R.layout.widget_obsidian_square, resolved, eqFrame, progressOverride, assets, brand),
                SizeF(240f, 115f) to buildLayout(context, R.layout.widget_obsidian_expanded, resolved, eqFrame, progressOverride, assets, brand),
            )
            return RemoteViews(sizeMap)
        }

        return buildLayout(context, resolveLayoutId(context, appWidgetId), resolved, eqFrame, progressOverride, assets, brand)
    }

    private const val OBSIDIAN_BASE_SURFACE = 0xEB101218.toInt()

    private fun resolveLayoutId(context: Context, appWidgetId: Int): Int {
        val width = minWidthDp(context, appWidgetId)
        val height = minHeightDp(context, appWidgetId)
        return when {
            height >= 115 && width < 240 -> R.layout.widget_obsidian_square
            height >= 115 && width >= 240 -> R.layout.widget_obsidian_expanded
            width in 1 until 215 -> R.layout.widget_obsidian_compact
            else -> R.layout.widget_obsidian_horizontal
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
                    SizeF(110f, 48f) to partial(R.layout.widget_obsidian_compact),
                    SizeF(215f, 48f) to partial(R.layout.widget_obsidian_horizontal),
                    SizeF(115f, 115f) to partial(R.layout.widget_obsidian_square),
                    SizeF(240f, 115f) to partial(R.layout.widget_obsidian_expanded),
                ),
            )
        }
        return partial(resolveLayoutId(context, appWidgetId))
    }

    private fun buildLayout(
        context: Context,
        layoutId: Int,
        resolved: WidgetViews.Resolved,
        eqFrame: Int?,
        progressOverride: Float?,
        assets: WidgetBakedAssets,
        brand: WidgetBakedAssets,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutId)
        bind(
            context = context,
            views = views,
            resolved = resolved,
            eqFrame = eqFrame,
            progressOverride = progressOverride,
            assets = assets,
            brand = brand,
            isCompact = (layoutId == R.layout.widget_obsidian_compact),
        )
        return views
    }

    private fun bind(
        context: Context,
        views: RemoteViews,
        resolved: WidgetViews.Resolved,
        eqFrame: Int?,
        progressOverride: Float?,
        assets: WidgetBakedAssets,
        brand: WidgetBakedAssets,
        isCompact: Boolean,
    ) {
        val snapshot = resolved.snapshot
        if (!resolved.usableSession) {
            views.setViewVisibility(R.id.widget_empty_group, View.VISIBLE)
            views.setViewVisibility(R.id.widget_content_group, View.GONE)
            val brandSquircle = brand.artSquircle
            if (brandSquircle != null && !brandSquircle.isRecycled) {
                views.setImageViewBitmap(R.id.widget_empty_icon, brandSquircle)
            } else {
                views.setImageViewResource(R.id.widget_empty_icon, R.drawable.widget_art_placeholder)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    val idleSurface = ColorUtils.blendARGB(OBSIDIAN_BASE_SURFACE, brand.primaryColor, 0.14f)
                    views.setColorStateList(
                        R.id.widget_root,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(idleSurface),
                    )
                }
            }
            if (resolved.hasAccess) {
                views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_empty_sub, context.getString(R.string.widget_aura_idle))
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
        views.setTextViewText(R.id.widget_state, if (playing) "PLAYING" else "PAUSED")
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

        // Subtle obsidian song-reactive tint (keeps dark smoked glass classy, never loud)
        runCatching {
            views.setInt(R.id.widget_prev, "setColorFilter", Color.WHITE)
            views.setInt(R.id.widget_next, "setColorFilter", Color.WHITE)
            views.setInt(R.id.widget_play_pause, "setColorFilter", assets.onPrimaryColor)
            views.setTextColor(R.id.widget_state, ColorUtils.blendARGB(Color.WHITE, assets.primaryColor, 0.35f))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_progress,
                    "setProgressTintList",
                    ColorStateList.valueOf(assets.primaryColor),
                )
                views.setColorStateList(
                    R.id.widget_play_pause_container,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(assets.primaryColor),
                )
                val tintedSurface = ColorUtils.blendARGB(OBSIDIAN_BASE_SURFACE, assets.primaryColor, 0.10f)
                views.setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(tintedSurface),
                )
            }
        }

        // Cached 256px superellipse squircle artwork with specular Fresnel rim
        val art = assets.artSquircle?.takeUnless { it.isRecycled } ?: brand.artSquircle?.takeUnless { it.isRecycled }
        if (art != null) {
            views.setImageViewBitmap(R.id.widget_art, art)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        }

        views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAppPending(context))
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            WidgetActions.togglePending(context, ObsidianGlassWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause_container,
            WidgetActions.togglePending(context, ObsidianGlassWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            WidgetActions.prevPending(context, ObsidianGlassWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            WidgetActions.nextPending(context, ObsidianGlassWidgetReceiver::class.java),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next_container,
            WidgetActions.nextPending(context, ObsidianGlassWidgetReceiver::class.java),
        )
    }
}
