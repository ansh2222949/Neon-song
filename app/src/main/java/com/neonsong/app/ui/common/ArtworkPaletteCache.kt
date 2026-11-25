package com.neonsong.app.ui.common

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class ArtworkPaletteColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

object ArtworkPaletteCache {
    private val cache = LruCache<String, ArtworkPaletteColors>(48)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ArtworkPaletteColors?>>()

    /** Live song palette read directly in draw phases (zero tree recomposition). */
    val activeSongPalette = mutableStateOf<ArtworkPaletteColors?>(null)

    /** Last valid extracted or peeked palette to prevent flashing fallbacks during in-flight decoding. */
    var lastValidPalette: ArtworkPaletteColors? = null
        private set

    fun setActiveSongPalette(colors: ArtworkPaletteColors?) {
        if (colors != null) {
            lastValidPalette = colors
        }
        if (activeSongPalette.value != colors) {
            activeSongPalette.value = colors
        }
    }

    /** Instant memory lookup for Frame-0 popup & screen rendering. */
    fun peek(url: String?): ArtworkPaletteColors? =
        url?.takeIf { it.isNotBlank() }?.let { cache.get(it) }

    fun peekOrLastValid(url: String?): ArtworkPaletteColors? =
        peek(url) ?: lastValidPalette

    suspend fun getOrExtract(context: Context, url: String): ArtworkPaletteColors? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }

        val existing = inFlight[url]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<ArtworkPaletteColors?>()
        val raced = inFlight.putIfAbsent(url, deferred)
        if (raced != null) return raced.await()

        return try {
            val extracted = withContext(Dispatchers.IO) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
                        .size(96) // Ultra-fast decode for instant color adaptation
                        .build()
                    val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                        ?: return@runCatching null

                    val palette = Palette.from(bitmap)
                        .maximumColorCount(16)
                        .clearFilters()
                        .generate()

                    // Collect candidate swatches prioritized by vibrancy + visual presence
                    val rankedSwatches = buildList {
                        palette.vibrantSwatch?.let(::add)
                        palette.lightVibrantSwatch?.let(::add)
                        palette.darkVibrantSwatch?.let(::add)
                        palette.dominantSwatch?.let(::add)
                        palette.mutedSwatch?.let(::add)
                        addAll(palette.swatches.sortedByDescending { swatch ->
                            val hsl = swatch.hsl
                            val vibrancyBoost = if (hsl[1] > 0.28f && hsl[2] in 0.22f..0.78f) 2.2f else 0.7f
                            swatch.population * vibrancyBoost
                        })
                    }.distinctBy { it.rgb }

                    val first = rankedSwatches.firstOrNull() ?: return@runCatching null
                    val second = rankedSwatches.firstOrNull { hueDistance(it.hsl[0], first.hsl[0]) >= 34f }
                    val third = rankedSwatches.firstOrNull { candidate ->
                        hueDistance(candidate.hsl[0], first.hsl[0]) >= 34f &&
                            (second == null || hueDistance(candidate.hsl[0], second.hsl[0]) >= 34f)
                    }

                    val c1 = boostVibrant(first.rgb, minL = 0.48f, maxL = 0.66f)
                    val c2 = if (second != null) {
                        boostVibrant(second.rgb, minL = 0.46f, maxL = 0.68f)
                    } else {
                        boostVibrant(first.rgb, minL = 0.50f, maxL = 0.68f, hueShiftDeg = 38f)
                    }
                    val c3 = if (third != null) {
                        boostVibrant(third.rgb, minL = 0.42f, maxL = 0.62f)
                    } else {
                        boostVibrant(first.rgb, minL = 0.44f, maxL = 0.62f, hueShiftDeg = -36f)
                    }

                    ArtworkPaletteColors(primary = c1, secondary = c2, tertiary = c3).also {
                        cache.put(url, it)
                        lastValidPalette = it
                    }
                }.getOrNull()
            }
            deferred.complete(extracted)
            extracted
        } catch (t: Throwable) {
            deferred.complete(null)
            null
        } finally {
            inFlight.remove(url, deferred)
        }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    private fun boostVibrant(
        colorInt: Int,
        minL: Float,
        maxL: Float,
        hueShiftDeg: Float = 0f,
    ): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorInt, hsl)
        hsl[0] = (hsl[0] + hueShiftDeg + 360f) % 360f
        hsl[1] = if (hsl[1] < 0.10f) {
            (hsl[1] * 1.4f).coerceIn(0.06f, 0.20f)
        } else {
            if (hsl[0] in 34f..54f && hsl[2] < 0.45f) hsl[0] = 24f // Avoid muddy olive
            (hsl[1] * 1.38f).coerceIn(0.62f, 0.96f)
        }
        hsl[2] = hsl[2].coerceIn(minL, maxL)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
