package com.neonsong.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.neonsong.app.R

/**
 * Samsung One UI responsive Aura Glass widget.
 * Uses crisp resolution-independent vector/shape surfaces (zero stretched backdrop bitmaps)
 * with song-reactive glass surface tinting and flexible weighted layouts across every grid size.
 */
internal object AuraWidgetViews {

    private const val PROGRESS_MAX = 1000
    private const val FALLBACK_PRIMARY = 0xFF00E5FF.toInt()
    private const val BASE_SMOKED_SURFACE = 0xEB151722.toInt()
    private const val ICON_LIGHT = 0xFFF7F8FA.toInt()
    private const val ICON_DARK = 0xFF0B0E14.toInt()

    private val eqFrames = intArrayOf(
        R.drawable.widget_eq_frame_0,
        R.drawable.widget_eq_frame_1,
        R.drawable.widget_eq_frame_2,
    )

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedAssets: WidgetBakedAssets? = null

    private fun resolveAssets(memoryArt: Bitmap?, artPath: String?): WidgetBakedAssets {
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
            fallbackPrimary = FALLBACK_PRIMARY,
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
        val brand = WidgetGlassRenderer.brandArtwork(context, isObsidian = true, fallbackPrimary = FALLBACK_PRIMARY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizeMap = mapOf(
                SizeF(110f, 48f) to buildLayout(context, R.layout.widget_aura_compact, resolved, eqFrame, progressOverride, assets, brand),
                SizeF(200f, 48f) to buildLayout(context, R.layout.widget_aura_horizontal, resolved, eqFrame, progressOverride, assets, brand),
                SizeF(115f, 105f) to buildLayout(context, R.layout.widget_aura_square, resolved, eqFrame, progressOverride, assets, brand),
                SizeF(230f, 105f) to buildLayout(context, R.layout.widget_aura_expanded, resolved, eqFrame, progressOverride, assets, brand),
            )
            return RemoteViews(sizeMap)
        }

        val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull()
        val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 240
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 105
        val layoutId = when {
            minH < 96 && minW < 185 -> R.layout.widget_aura_compact
            minH < 96 -> R.layout.widget_aura_horizontal
            minW < 210 -> R.layout.widget_aura_square
            else -> R.layout.widget_aura_expanded
        }
        return buildLayout(context, layoutId, resolved, eqFrame, progressOverride, assets, brand)
    }

    fun buildProgressOnly(
        context: Context,
        appWidgetId: Int,
        progress: Float,
    ): RemoteViews {
        val step = (progress.coerceIn(0f, 1f) * PROGRESS_MAX).toInt()
        fun progressViews(layoutId: Int): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
            if (layoutId != R.layout.widget_aura_compact) {
                setProgressBar(R.id.widget_progress, PROGRESS_MAX, step, false)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(110f, 48f) to progressViews(R.layout.widget_aura_compact),
                    SizeF(200f, 48f) to progressViews(R.layout.widget_aura_horizontal),
                    SizeF(115f, 105f) to progressViews(R.layout.widget_aura_square),
                    SizeF(230f, 105f) to progressViews(R.layout.widget_aura_expanded),
                ),
            )
        }
        val options = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }.getOrNull()
        val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 240
        val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 105
        val layoutId = when {
            minH < 96 && minW < 185 -> R.layout.widget_aura_compact
            minH < 96 -> R.layout.widget_aura_horizontal
            minW < 210 -> R.layout.widget_aura_square
            else -> R.layout.widget_aura_expanded
        }
        return progressViews(layoutId)
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
        val snap = resolved.snapshot
        val hasTrackArt = assets.artSquircle != null && !assets.artSquircle.isRecycled
        val activePalette = if (hasTrackArt) assets else brand
        val accentTint = ColorUtils.blendARGB(activePalette.primaryColor, 0xFFFFFFFF.toInt(), 0.22f)
        val playContentColor = activePalette.onPrimaryColor
        val surfaceTint = ColorUtils.blendARGB(BASE_SMOKED_SURFACE, activePalette.primaryColor, 0.22f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(surfaceTint))
        }

        if (!resolved.usableSession) {
            views.setViewVisibility(R.id.widget_empty_group, View.VISIBLE)
            views.setViewVisibility(R.id.widget_content_group, View.GONE)
            val brandSquircle = brand.artSquircle
            if (brandSquircle != null && !brandSquircle.isRecycled) {
                views.setImageViewBitmap(R.id.widget_empty_icon, brandSquircle)
            } else {
                views.setImageViewResource(R.id.widget_empty_icon, R.drawable.widget_art_placeholder)
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
            return views
        }

        views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.openAppPending(context))
        views.setViewVisibility(R.id.widget_empty_group, View.GONE)
        views.setViewVisibility(R.id.widget_content_group, View.VISIBLE)

        val squircle = assets.artSquircle?.takeUnless { it.isRecycled } ?: brand.artSquircle?.takeUnless { it.isRecycled }
        if (squircle != null) {
            views.setImageViewBitmap(R.id.widget_art, squircle)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        }

        views.setTextViewText(R.id.widget_title, snap.title.ifBlank { "Neon Song" })
        views.setTextViewText(R.id.widget_artist, snap.artist.ifBlank { "Unknown artist" })

        val playIcon = if (snap.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playIcon)
        views.setInt(R.id.widget_btn_play_pause, "setColorFilter", playContentColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.widget_play_pause_container, "setBackgroundTintList", ColorStateList.valueOf(activePalette.primaryColor))
        }
        val togglePi = WidgetActions.togglePending(context, AuraWidgetReceiver::class.java)
        views.setOnClickPendingIntent(R.id.widget_play_pause_container, togglePi)
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, togglePi)

        if (layoutId != R.layout.widget_aura_compact) {
            views.setInt(R.id.widget_btn_prev, "setColorFilter", ICON_LIGHT)
            views.setInt(R.id.widget_btn_next, "setColorFilter", ICON_LIGHT)
            views.setOnClickPendingIntent(R.id.widget_btn_prev, WidgetActions.prevPending(context, AuraWidgetReceiver::class.java))
            views.setOnClickPendingIntent(R.id.widget_btn_next, WidgetActions.nextPending(context, AuraWidgetReceiver::class.java))

            val progressFraction = (progressOverride ?: snap.progress).coerceIn(0f, 1f)
            views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, (progressFraction * PROGRESS_MAX).toInt(), false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_progress, "setProgressTintList", ColorStateList.valueOf(accentTint))
            }
        }

        if (layoutId == R.layout.widget_aura_square || layoutId == R.layout.widget_aura_expanded) {
            val frameIdx = if (snap.isPlaying) ((eqFrame ?: WidgetUpdater.animationFrame) % eqFrames.size).coerceAtLeast(0) else 0
            views.setImageViewResource(R.id.widget_eq_icon, eqFrames[frameIdx])
            views.setInt(R.id.widget_eq_icon, "setColorFilter", accentTint)
            val badgeText = when {
                layoutId == R.layout.widget_aura_square -> "Neon"
                snap.sourceApp.isNotBlank() -> snap.sourceApp
                else -> "Neon Song"
            }
            views.setTextViewText(R.id.widget_source_label, badgeText)
        }

        return views
    }
}
