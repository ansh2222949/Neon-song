package com.neonsong.app.ui.player

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.PowerManager
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.neonsong.app.data.artwork.ArtworkNormalizer
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ArtworkViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Language("AGSL")
internal val FLUID_AGSL = """
    uniform shader curArt;
    uniform shader prevArt;
    uniform float2 curArtSize;
    uniform float2 prevArtSize;
    uniform float fade;

    uniform float2 resolution;
    uniform float4 sides;
    uniform float4 angles;
    uniform float2 centre0;
    uniform float2 centre1;
    uniform float2 centre2;
    uniform float2 centre3;

    uniform float2 twistA;
    uniform float2 twistB;
    uniform float twistRadius;
    uniform float twistAngle;

    uniform float saturation;
    uniform float brightness;
    uniform float contrast;

    float2 twist(float2 coord, float2 offset, float radius, float angle) {
        float2 d = coord - offset;
        float dist = length(d);
        float safeRadius = max(radius, 0.001);
        if (dist < safeRadius) {
            float ratio = (safeRadius - dist) / safeRadius;
            float a = ratio * ratio * angle;
            float s = sin(a);
            float c = cos(a);
            d = float2(d.x * c - d.y * s, d.x * s + d.y * c);
        }
        return d + offset;
    }

    float2 localOf(float2 p, float2 centre, float angle, float side) {
        float2 d = p - centre;
        float c = cos(-angle);
        float s = sin(-angle);
        float safeSide = max(side, 0.001);
        return float2(d.x * c - d.y * s, d.x * s + d.y * c) / safeSide + 0.5;
    }

    float feather(float2 uv) {
        float2 d = abs(uv - 0.5) * 2.0;
        float dist = max(d.x, d.y);
        return 1.0 - smoothstep(0.70, 1.0, dist);
    }

    half4 main(float2 fragCoord) {
        float2 p = twist(fragCoord, twistA, twistRadius, twistAngle);
        p = twist(p, twistB, twistRadius, -twistAngle);

        float2 u0 = clamp(localOf(p, centre0, angles.x, sides.x), 0.0, 1.0);
        float2 u1 = localOf(p, centre1, angles.y, sides.y);
        float2 u2 = localOf(p, centre2, angles.z, sides.z);
        float2 u3 = localOf(p, centre3, angles.w, sides.w);

        float w1 = feather(u1);
        float w2 = feather(u2);
        float w3 = feather(u3);

        float2 uv = mix(u0, u1, w1);
        uv = mix(uv, u2, w2 * 0.75);
        uv = mix(uv, u3, w3 * 0.60);
        uv = clamp(uv, 0.0, 1.0);

        float3 cur = float3(curArt.eval(uv * curArtSize).rgb);
        float3 c = cur;
        if (fade < 0.999) {
            float3 prv = float3(prevArt.eval(uv * prevArtSize).rgb);
            c = mix(prv, cur, fade);
        }

        // Perceptual luma saturation boost (Rec. 709)
        float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
        c = float3(luma) + (c - float3(luma)) * saturation;

        // Vertical lighting depth curve: brighter behind album art, smoothly darkens toward controls
        float depthFactor = 1.0 - smoothstep(0.40, 1.0, fragCoord.y / max(resolution.y, 1.0)) * 0.45;
        c *= (brightness * depthFactor);
        c = c * (1.0 + contrast) - float3(0.5 * contrast);

        // 1.5 / 255 subtle dither to eliminate OLED 8-bit banding rings
        float dither = (fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5) * (1.5 / 255.0);
        c += float3(dither);

        return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
    }
""".trimIndent()

internal class MutableFluidCopy(
    var centreX: Float = 0f,
    var centreY: Float = 0f,
    var side: Float = 0f,
    var angle: Float = 0f,
)

internal fun computeFluidCopies(
    width: Float,
    height: Float,
    seconds: Float,
    target: Array<MutableFluidCopy>,
) {
    computeFluidCopies(width, height, seconds, seed = 0, target = target)
}

internal fun computeFluidCopies(
    width: Float,
    height: Float,
    seconds: Float,
    seed: Int,
    target: Array<MutableFluidCopy>,
) {
    require(target.size >= 4) { "target array must have at least 4 elements" }
    val longest = maxOf(width, height)
    val t = seconds * FLOW_SPEED
    val cx = width / 2f
    val cy = height / 2f

    val phase0 = if (seed != 0) startPhase(seed, 0) else 0f
    val phase1 = if (seed != 0) startPhase(seed, 1) else 0f
    val phase2 = if (seed != 0) startPhase(seed, 2) else 0f
    val phase3 = if (seed != 0) startPhase(seed, 3) else 0f

    // Smooth organic breathing for blob sizes with deterministic seed-based phase offsets
    val breathe0 = 1f + 0.08f * sin(t * 0.22f + phase0)
    val breathe1 = 1f + 0.12f * sin(t * 0.31f + 1.2f + phase1)
    val breathe2 = 1f + 0.14f * sin(t * 0.27f + 2.4f + phase2)
    val breathe3 = 1f + 0.15f * sin(t * 0.35f + 3.6f + phase3)

    // Aspect-ratio independent orbits: use respective dimensions for X and Y to prevent compression
    val a3 = ORBIT_RATE_MID * t + phase2
    val rx3 = width * ORBIT_RADIUS_MID
    val ry3 = height * ORBIT_RADIUS_MID
    val x3 = cx + rx3 * cos(a3)
    val y3 = cy + ry3 * sin(a3)

    val a4 = ORBIT_RATE_SMALL * t + phase3
    val rx4 = width * ORBIT_RADIUS_SMALL
    val ry4 = height * ORBIT_RADIUS_SMALL
    val x4 = cx + rx4 * cos(a4)
    val y4 = cy + ry4 * sin(a4)

    target[0].centreX = cx
    target[0].centreY = cy
    target[0].side = longest * SQRT_2 * breathe0
    target[0].angle = ROTATION[0] * t + phase0

    target[1].centreX = width / 2.5f
    target[1].centreY = height / 2.5f
    target[1].side = longest * 0.8f * breathe1
    target[1].angle = ROTATION[1] * t + phase1

    target[2].centreX = x3
    target[2].centreY = y3
    target[2].side = longest * 0.5f * breathe2
    target[2].angle = ROTATION[2] * t + phase2

    target[3].centreX = x4
    target[3].centreY = y4
    target[3].side = longest * 0.25f * breathe3
    target[3].angle = ROTATION[3] * t + phase3
}

private fun startPhase(seed: Int, index: Int): Float =
    (((seed ushr (index * 5)) and 0x1F) / 32f) * TWO_PI

private const val TWO_PI = 6.2831855f
private val SQRT_2 = sqrt(2f)

// Exact period in seconds: 160 * PI ≈ 502.6548246f.
// All rotational, orbital, breathing, and swirl Lissajous frequencies are chosen as integer multiples of (2 / 160):
// - Breathing: 0.55 (44 cycles), 0.775 (62 cycles), 0.675 (54 cycles), 0.875 (70 cycles)
// - Orbits: 0.1125 (9 cycles), 0.225 (18 cycles)
// - Rotations: 0.15 (12 cycles), -0.30 (24 cycles), 0.15 (12 cycles), -0.20 (16 cycles)
// - Swirl twists: 0.15 (12 cycles), 0.1125 (9 cycles), 0.125 (10 cycles), 0.175 (14 cycles)
// Since every frequency completes an exact integer number of cycles in FLUID_PERIOD_S, wrapping produces
// zero visual discontinuity while keeping floating-point time bounded.
private const val FLUID_PERIOD_S = 502.6548246f

private val ROTATION = floatArrayOf(0.06f, -0.12f, 0.06f, -0.08f)

private const val ORBIT_RADIUS_MID = 0.25f
private const val ORBIT_RADIUS_SMALL = 0.10f
private const val ORBIT_RATE_MID = 0.045f
private const val ORBIT_RATE_SMALL = 0.09f
private const val FLOW_SPEED = 2.5f

private const val SATURATION = 1.9f
private const val BRIGHTNESS = 0.52f
private const val CONTRAST = 0.3f

private const val TWIST_ANGLE = 1.6f
private const val TWIST_REACH = 1.0f

private const val BLUR_FRACTION = 0.025f
private const val BLUR_SIGMA_MIN = 12f
private const val BLUR_SIGMA_MAX = 22f

private const val TEXTURE_PX = 256
private const val TEXTURE_CACHE = 4
private const val CROSSFADE_MS = 420
private const val APPEAR_MS = 280
private const val FRAME_SECONDS = 1f / 22f

private class BlurEffectCache(private val maxEntries: Int = 16) {
    private val cache = LruCache<Int, androidx.compose.ui.graphics.RenderEffect>(maxEntries)
    var sigma = Float.NaN
    var effect: androidx.compose.ui.graphics.RenderEffect? = null
    var failed = false
    private var lastKey = -1

    fun getOrCreate(requestedSigma: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (failed || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val key = (kotlin.math.round(requestedSigma / 2.0f) * 2.0f).toInt()
        if (key <= 0) {
            lastKey = key
            sigma = 0f
            effect = null
            return null
        }
        if (key == lastKey && effect != null) return effect

        var cached = synchronized(cache) { cache.get(key) }
        if (cached == null) {
            val created = runCatching {
                RenderEffect
                    .createBlurEffect(key.toFloat(), key.toFloat(), Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
            if (created.isSuccess) {
                cached = created.getOrNull()
                if (cached != null) {
                    synchronized(cache) { cache.put(key, cached) }
                }
            } else {
                failed = true
                effect = null
                return null
            }
        }
        lastKey = key
        sigma = key.toFloat()
        effect = cached
        return cached
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
        lastKey = -1
        sigma = Float.NaN
        effect = null
    }
}

private object FluidTextureCache {
    private val cache = LruCache<String, Bitmap>(TEXTURE_CACHE)
    private var isCallbacksRegistered = false

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }

    fun initMemoryCallbacks(context: Context) {
        if (isCallbacksRegistered) return
        val app = context.applicationContext as? Application ?: return
        @Suppress("DEPRECATION")
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Deprecated in Java", ReplaceWith("onTrimMemory(level)"))
            override fun onLowMemory() {
                clear()
            }
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    clear()
                }
            }
        })
        isCallbacksRegistered = true
    }
}

private val inFlightRequests = ConcurrentHashMap<String, Deferred<Bitmap?>>()

private suspend fun loadTexture(context: Context, url: String, preBlur: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
    FluidTextureCache.initMemoryCallbacks(context)
    val cacheKey = if (preBlur) "$url-preblurred" else url
    FluidTextureCache.get(cacheKey)?.let { return@withContext it }

    coroutineScope {
        val deferred = inFlightRequests.computeIfAbsent(cacheKey) {
            async(Dispatchers.IO) {
                try {
                    loadTextureInternal(context, url, preBlur, cacheKey)
                } finally {
                    inFlightRequests.remove(cacheKey)
                }
            }
        }
        deferred.await()
    }
}

private suspend fun loadTextureInternal(context: Context, url: String, preBlur: Boolean, cacheKey: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(TEXTURE_PX)
        .allowHardware(false)
        .build()

    val result = try {
        context.imageLoader.execute(request)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    val drawable = (result as? SuccessResult)?.drawable ?: return null
    var bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
        drawable.bitmap
    } else {
        val bmp = Bitmap.createBitmap(TEXTURE_PX, TEXTURE_PX, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, TEXTURE_PX, TEXTURE_PX)
        drawable.draw(canvas)
        bmp
    }

    if (preBlur) {
        bitmap = FastBlur.blurArtwork(bitmap, radius = 18, maxDimension = 64)
    }

    FluidTextureCache.put(cacheKey, bitmap)
    return bitmap
}

internal fun fluidBlurSigma(width: Float, height: Float, extraBlurProgress: Float = 0f): Float {
    val shortest = minOf(width, height)
    val base = (shortest * BLUR_FRACTION).coerceIn(BLUR_SIGMA_MIN, BLUR_SIGMA_MAX)
    val expanded = (shortest * 0.05f).coerceIn(24f, 36f)
    return androidx.compose.ui.util.lerp(base, expanded, extraBlurProgress)
}

internal fun fluidBlurSigma(width: Float, height: Float, extraBlur: Boolean): Float =
    fluidBlurSigma(width, height, if (extraBlur) 1f else 0f)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslFluidRenderer {
    private val shader = RuntimeShader(FLUID_AGSL)
    private val paint = Paint()

    private var lastCurrentBitmap: Bitmap? = null
    private var lastCurrentShader: BitmapShader? = null
    private var lastPrevBitmap: Bitmap? = null
    private var lastPrevShader: BitmapShader? = null

    fun reset() {
        lastCurrentBitmap = null
        lastCurrentShader = null
        lastPrevBitmap = null
        lastPrevShader = null
        paint.shader = null
    }

    fun render(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        copies: Array<MutableFluidCopy>,
        current: Bitmap,
        previous: Bitmap,
        fade: Float,
        seconds: Float = 0f,
        extraBlurProgress: Float = 0f,
    ) {
        if (width <= 0f || height <= 0f) return
        if (lastCurrentBitmap !== current || lastCurrentShader == null) {
            lastCurrentBitmap = current
            lastCurrentShader = BitmapShader(current, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        if (lastPrevBitmap !== previous || lastPrevShader == null) {
            lastPrevBitmap = previous
            lastPrevShader = BitmapShader(previous, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        shader.setInputShader("curArt", lastCurrentShader!!)
        shader.setInputShader("prevArt", lastPrevShader!!)
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("curArtSize", current.width.toFloat(), current.height.toFloat())
        shader.setFloatUniform("prevArtSize", previous.width.toFloat(), previous.height.toFloat())
        shader.setFloatUniform("fade", fade)
        shader.setFloatUniform("sides", copies[0].side, copies[1].side, copies[2].side, copies[3].side)
        shader.setFloatUniform("angles", copies[0].angle, copies[1].angle, copies[2].angle, copies[3].angle)
        shader.setFloatUniform("centre0", copies[0].centreX, copies[0].centreY)
        shader.setFloatUniform("centre1", copies[1].centreX, copies[1].centreY)
        shader.setFloatUniform("centre2", copies[2].centreX, copies[2].centreY)
        shader.setFloatUniform("centre3", copies[3].centreX, copies[3].centreY)

        // Dynamic Lissajous convection currents with frequencies completing exact integer cycles over FLUID_PERIOD_S
        // 0.15 = 12 cycles, 0.1125 = 9 cycles, 0.125 = 10 cycles, 0.175 = 14 cycles in 160*PI seconds
        val twistAx = width * (0.25f + 0.10f * sin(seconds * 0.15f))
        val twistAy = height * (0.90f + 0.08f * cos(seconds * 0.1125f))
        val twistBx = width * (0.75f - 0.10f * cos(seconds * 0.125f))
        val twistBy = height * (0.10f + 0.08f * sin(seconds * 0.175f))

        shader.setFloatUniform("twistA", twistAx, twistAy)
        shader.setFloatUniform("twistB", twistBx, twistBy)
        shader.setFloatUniform("twistRadius", (maxOf(width, height) + minOf(width, height)) / 2f * TWIST_REACH)
        shader.setFloatUniform("twistAngle", TWIST_ANGLE)
        shader.setFloatUniform("saturation", SATURATION)
        val targetBrightness = androidx.compose.ui.util.lerp(BRIGHTNESS, 0.32f, extraBlurProgress)
        shader.setFloatUniform("brightness", targetBrightness)
        shader.setFloatUniform("contrast", CONTRAST)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/**
 * Checks whether this device can render the fluid background.
 * Requires:
 *  - Android 12+ (API >= 31) because RenderEffect and AGSL (API 33+) are unsupported on API <= 30
 *    (where Modifier.blur() is an explicit no-op in Jetpack Compose).
 *  - Hardware acceleration enabled on the current View.
 *  - Not a low-RAM device where background blurs are unsupported or disabled by the OS/GPU.
 */
@Composable
fun isFluidSupported(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return false
    }

    val context = LocalContext.current
    val view = LocalView.current

    if (view.isInEditMode) return false
    if (!view.isHardwareAccelerated) return false

    val activityManager = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (activityManager?.isLowRamDevice == true) {
        return false
    }

    return true
}

@Composable
fun FluidArtworkBackground(
    track: PlayableTrack,
    modifier: Modifier = Modifier,
    extraBlur: Boolean = false,
    rotatingBackgroundEnabled: Boolean = true,
    isPlaying: Boolean = true,
    artworkViewModel: ArtworkViewModel = hiltViewModel(),
    fallback: @Composable () -> Unit,
) {
    val fluidSupported = isFluidSupported()
    if (!rotatingBackgroundEnabled || !fluidSupported) {
        Box(modifier) {
            fallback()
        }
        return
    }

    val context = LocalContext.current
    val embeddedUrl = track.artworkUrl
    val isReal = remember(embeddedUrl) { ArtworkNormalizer.isRealImage(embeddedUrl) }

    val key = remember(track.title, track.artist) { ArtworkNormalizer.cacheKey(track.title, track.artist) }
    val resolvedUrl by remember(key) {
        artworkViewModel.resolved.map { it[key] }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = artworkViewModel.resolved.value[key])

    LaunchedEffect(key) {
        if (!isReal && resolvedUrl.isNullOrBlank()) {
            artworkViewModel.resolve(track.title, track.artist)
        }
    }

    val artworkUrl = resolvedUrl?.takeIf { it.isNotBlank() } ?: (if (isReal) embeddedUrl else null)

    var current by remember { mutableStateOf<Bitmap?>(null) }
    var previous by remember { mutableStateOf<Bitmap?>(null) }
    var transitionBuffer by remember { mutableStateOf<Bitmap?>(null) }
    val crossfade = remember { Animatable(1f) }
    val appear = remember { Animatable(0f) }

    val agslRenderer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { AgslFluidRenderer() }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            agslRenderer?.reset()
        }
    }

    LaunchedEffect(artworkUrl, agslRenderer == null) {
        if (artworkUrl.isNullOrBlank()) {
            if (current != null) {
                appear.animateTo(0f, tween(APPEAR_MS, easing = FastOutSlowInEasing))
                current = null
                previous = null
            }
            return@LaunchedEffect
        }

        val bitmap = loadTexture(context, artworkUrl, preBlur = true)
        if (bitmap == null) {
            // Keep the existing artwork rather than blanking out on temporary network/decoding glitch (Bug 23).
            return@LaunchedEffect
        }

        val showing = current
        if (showing != null && showing !== bitmap) {
            val oldPrevious = previous
            val currentCrossfade = crossfade.value

            // If a previous crossfade was in-flight, flatten the visible composite into transitionBuffer
            // to eliminate visual snapping and maintain smooth continuity across rapid track changes (Bug 12).
            val startPrevious = if (oldPrevious != null && currentCrossfade < 0.99f) {
                val buffer = transitionBuffer?.takeIf { !it.isRecycled && it.width == TEXTURE_PX && it.height == TEXTURE_PX }
                    ?: Bitmap.createBitmap(TEXTURE_PX, TEXTURE_PX, Bitmap.Config.ARGB_8888).also { transitionBuffer = it }
                val canvas = android.graphics.Canvas(buffer)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(oldPrevious, null, Rect(0, 0, TEXTURE_PX, TEXTURE_PX), paint)
                paint.alpha = (currentCrossfade.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawBitmap(showing, null, Rect(0, 0, TEXTURE_PX, TEXTURE_PX), paint)
                buffer
            } else {
                showing
            }

            previous = startPrevious
            current = bitmap
            crossfade.snapTo(0f)
            try {
                crossfade.animateTo(1f, tween(CROSSFADE_MS, easing = FastOutSlowInEasing))
            } finally {
                previous = null
            }
        } else {
            current = bitmap
        }
    }

    LaunchedEffect(current != null) {
        if (current != null) {
            appear.animateTo(1f, tween(APPEAR_MS, easing = FastOutSlowInEasing))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()

    var seconds by remember { mutableFloatStateOf(0f) }

    val blurTransition by animateFloatAsState(
        targetValue = if (extraBlur) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "fluidBlurTransition",
    )

    val flowVelocity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "fluidFlowVelocity",
    )

    val currentFlowVelocity by rememberUpdatedState(flowVelocity)
    val isCrossfadeRunning by rememberUpdatedState(crossfade.isRunning)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val hasArtwork = current != null
    val powerManager = remember(context) {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    val isScreenInteractive = powerManager?.isInteractive ?: true
    val isExtraBlurPaused = extraBlur && blurTransition >= 0.99f && !crossfade.isRunning

    // Gate animation loop on isPlaying, deceleration velocity, lifecycle state, screen interactivity, extraBlur, and artwork presence
    LaunchedEffect(isPlaying, flowVelocity > 0.01f, crossfade.isRunning, lifecycleState, hasArtwork, isScreenInteractive, isExtraBlurPaused) {
        if (!hasArtwork || (!isPlaying && flowVelocity <= 0.01f && !crossfade.isRunning) ||
            !lifecycleState.isAtLeast(Lifecycle.State.RESUMED) || !isScreenInteractive || isExtraBlurPaused
        ) {
            return@LaunchedEffect
        }
        var last = 0L
        val frameDelayMs = (FRAME_SECONDS * 1000f).toLong().coerceAtLeast(16L)
        while (isActive) {
            val playing = currentIsPlaying
            val vel = currentFlowVelocity
            val xfRunning = isCrossfadeRunning
            if (!playing && vel <= 0.005f && !xfRunning) {
                break
            }
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    val surge = if (xfRunning) {
                        1f + 2.5f * kotlin.math.sin(crossfade.value * Math.PI.toFloat())
                    } else 1f
                    val effectiveVelocity = (vel * surge).coerceIn(0f, 4f)
                    seconds = (seconds + dt * effectiveVelocity) % FLUID_PERIOD_S
                }
                last = now
            }
            delay(frameDelayMs - 6L)
        }
    }

    val blurEffectCache = remember { BlurEffectCache() }

    // Preallocated data holder to avoid heap allocations in the draw loop
    val fluidCopiesHolder = remember {
        Array(4) { MutableFluidCopy() }
    }

    val trackSeed = remember(track.title, track.artist) {
        (track.title.hashCode() * 31) xor track.artist.hashCode()
    }

    // Color grading filter for non-AGSL fallback matching AGSL saturation, brightness, and contrast (Bug 10, 30)
    val fallbackColorFilter = remember(blurTransition) {
        val b = androidx.compose.ui.util.lerp(BRIGHTNESS, 0.32f, blurTransition)
        val s = SATURATION
        val cm = 1f + CONTRAST
        val co = -0.5f * CONTRAST * 255f
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f
        val invS = 1f - s
        val a = invS * lr
        val bg = invS * lg
        val c = invS * lb
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    (a + s) * b * cm, bg * b * cm, c * b * cm, 0f, co,
                    a * b * cm, (bg + s) * b * cm, c * b * cm, 0f, co,
                    a * b * cm, bg * b * cm, (c + s) * b * cm, 0f, co,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    // ImageBitmap caching for non-AGSL fallback to avoid allocating wrappers every frame
    var cachedCurBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cachedCurImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var cachedPrevBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cachedPrevImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    Box(modifier) {
        if (appear.value < 1f || current == null) {
            fallback()
        }

        if (current != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = appear.value
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val sigma = fluidBlurSigma(size.width, size.height, blurTransition)
                            renderEffect = blurEffectCache.getOrCreate(sigma)
                        }
                    }
                    .drawBehind {
                        if (size.width <= 0f || size.height <= 0f || appear.value <= 0.001f) return@drawBehind
                        val cur = current ?: return@drawBehind
                        val prv = previous ?: cur

                        if (agslRenderer != null) {
                            drawIntoCanvas { canvas ->
                                val scale = 0.25f
                                val invScale = 4f
                                val scaledWidth = size.width * scale
                                val scaledHeight = size.height * scale
                                computeFluidCopies(scaledWidth, scaledHeight, seconds, seed = trackSeed, target = fluidCopiesHolder)

                                val nativeCanvas = canvas.nativeCanvas
                                val saveCount = nativeCanvas.save()
                                try {
                                    nativeCanvas.scale(invScale, invScale)
                                    agslRenderer.render(
                                        canvas = nativeCanvas,
                                        width = scaledWidth,
                                        height = scaledHeight,
                                        copies = fluidCopiesHolder,
                                        current = cur,
                                        previous = prv,
                                        fade = crossfade.value,
                                        seconds = seconds,
                                        extraBlurProgress = blurTransition,
                                    )
                                } finally {
                                    nativeCanvas.restoreToCount(saveCount)
                                }
                            }
                        } else {
                            computeFluidCopies(size.width, size.height, seconds, seed = trackSeed, target = fluidCopiesHolder)
                            if (cur !== cachedCurBitmap) {
                                cachedCurBitmap = cur
                                cachedCurImageBitmap = cur.asImageBitmap()
                            }
                            if (prv !== cachedPrevBitmap) {
                                cachedPrevBitmap = prv
                                cachedPrevImageBitmap = prv.asImageBitmap()
                            }

                            val fade = crossfade.value
                            val drawPrev = prv !== cur && fade < 0.999f && cachedPrevImageBitmap != null

                            if (drawPrev) {
                                val prevImg = cachedPrevImageBitmap!!
                                val prevAlpha = (1f - fade).coerceIn(0f, 1f)
                                for (copy in fluidCopiesHolder) {
                                    if (copy.side <= 0f) continue
                                    val deg = Math.toDegrees(copy.angle.toDouble()).toFloat()
                                    val sideInt = copy.side.toInt().coerceAtLeast(1)
                                    val halfSide = copy.side / 2f
                                    val left = (copy.centreX - halfSide).toInt()
                                    val top = (copy.centreY - halfSide).toInt()
                                    rotate(
                                        degrees = deg,
                                        pivot = Offset(copy.centreX, copy.centreY),
                                    ) {
                                        drawImage(
                                            image = prevImg,
                                            dstOffset = IntOffset(left, top),
                                            dstSize = IntSize(sideInt, sideInt),
                                            alpha = prevAlpha,
                                            colorFilter = fallbackColorFilter,
                                        )
                                    }
                                }
                            }

                            val curImg = cachedCurImageBitmap ?: return@drawBehind
                            val curAlpha = if (drawPrev) fade.coerceIn(0f, 1f) else 1f
                            for (copy in fluidCopiesHolder) {
                                if (copy.side <= 0f) continue
                                val deg = Math.toDegrees(copy.angle.toDouble()).toFloat()
                                val sideInt = copy.side.toInt().coerceAtLeast(1)
                                val halfSide = copy.side / 2f
                                val left = (copy.centreX - halfSide).toInt()
                                val top = (copy.centreY - halfSide).toInt()
                                rotate(
                                    degrees = deg,
                                    pivot = Offset(copy.centreX, copy.centreY),
                                ) {
                                    drawImage(
                                        image = curImg,
                                        dstOffset = IntOffset(left, top),
                                        dstSize = IntSize(sideInt, sideInt),
                                        alpha = curAlpha,
                                        colorFilter = fallbackColorFilter,
                                    )
                                }
                            }
                        }
                    }
            )
        }
    }
}
