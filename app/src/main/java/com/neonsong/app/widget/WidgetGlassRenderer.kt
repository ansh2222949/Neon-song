package com.neonsong.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import java.io.File

data class WidgetBakedAssets(
    val artSquircle: Bitmap?,
    val primaryColor: Int,
    val onPrimaryColor: Int,
    val secondaryColor: Int,
)

object WidgetGlassRenderer {

    private const val TARGET_ART_PX = 256

    fun decodeScaledArt(path: String?): Bitmap? = runCatching {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val largest = maxOf(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (largest / (sampleSize * 2) >= TARGET_ART_PX) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        if (decoded.width <= TARGET_ART_PX && decoded.height <= TARGET_ART_PX) {
            decoded
        } else {
            val scale = TARGET_ART_PX.toFloat() / maxOf(decoded.width, decoded.height)
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== decoded) decoded.recycle()
            scaled
        }
    }.getOrNull()

    fun bake(
        sourceArt: Bitmap?,
        isObsidian: Boolean,
        fallbackPrimary: Int,
    ): WidgetBakedAssets {
        val (primary, onPrimary, secondary) = extractColors(sourceArt, isObsidian, fallbackPrimary)
        val squircle = sourceArt?.let { renderSquircle(it, isObsidian, primary) }
        return WidgetBakedAssets(
            artSquircle = squircle,
            primaryColor = primary,
            onPrimaryColor = onPrimary,
            secondaryColor = secondary,
        )
    }

    private fun extractColors(
        art: Bitmap?,
        isObsidian: Boolean,
        fallbackPrimary: Int,
    ): Triple<Int, Int, Int> {
        if (art == null || art.isRecycled) {
            val onPrimary = if (ColorUtils.calculateLuminance(fallbackPrimary) > 0.5) Color.BLACK else Color.WHITE
            val secondary = ColorUtils.blendARGB(fallbackPrimary, if (isObsidian) Color.WHITE else Color.BLACK, 0.20f)
            return Triple(fallbackPrimary, onPrimary, secondary)
        }
        return runCatching {
            val palette = Palette.from(art).maximumColorCount(16).generate()
            val swatch = palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.mutedSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch

            val rawPrimary = swatch?.rgb ?: fallbackPrimary
            val primary = if (isObsidian) {
                val hsv = FloatArray(3)
                Color.colorToHSV(rawPrimary, hsv)
                if (hsv[2] < 0.65f) hsv[2] = 0.65f
                if (hsv[1] < 0.35f) hsv[1] = 0.35f
                Color.HSVToColor(hsv)
            } else {
                rawPrimary
            }
            val onPrimary = if (ColorUtils.calculateLuminance(primary) > 0.45) Color.BLACK else Color.WHITE
            val secondary = palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: ColorUtils.blendARGB(primary, Color.WHITE, 0.25f)

            Triple(primary, onPrimary, secondary)
        }.getOrDefault(
            Triple(
                fallbackPrimary,
                if (ColorUtils.calculateLuminance(fallbackPrimary) > 0.5) Color.BLACK else Color.WHITE,
                fallbackPrimary,
            )
        )
    }

    private fun renderSquircle(src: Bitmap, isObsidian: Boolean, accentColor: Int): Bitmap = runCatching {
        val size = TARGET_ART_PX
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Superellipse squircle corner radius: ~22% for One UI curvature
        val radius = size * 0.22f
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        // 1. Draw rounded mask
        canvas.drawRoundRect(rect, radius, radius, paint)

        // 2. Draw scaled artwork clipped to squircle
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val srcRect = Rect(0, 0, src.width, src.height)
        val dstRect = Rect(0, 0, size, size)
        canvas.drawBitmap(src, srcRect, dstRect, paint)

        // 3. Specular Fresnel glass rim
        paint.xfermode = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        val rimShader = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            intArrayOf(
                Color.argb(if (isObsidian) 140 else 180, 255, 255, 255),
                Color.argb(30, 255, 255, 255),
                Color.argb(if (isObsidian) 80 else 40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.shader = rimShader
        val strokeRect = RectF(1f, 1f, (size - 1).toFloat(), (size - 1).toFloat())
        canvas.drawRoundRect(strokeRect, radius - 1f, radius - 1f, paint)

        out
    }.getOrDefault(src)
}
