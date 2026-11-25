package com.neonsong.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/**
 * A small rubber-band "squeeze" for scrollable content dragged past its
 * bounds — or that has nothing to scroll at all, like Generate's list when
 * it fits on screen without overflowing.
 *
 * v3 — the two previous versions (both since removed after real-device
 * reports of a phantom "extra movement" after a normal scroll settled)
 * shared a suspect neither actually addressed: Android's OWN default
 * stretch/glow overscroll (on by default for every scrollable Compose
 * component on API 31+) was never disabled, so it kept running
 * SIMULTANEOUSLY with this custom one. Two independent overscroll
 * systems reacting to the same boundary — the platform's own spring-back
 * timing and this one's — landing at slightly different moments reads
 * exactly like "it already stopped, then moved again". This version's
 * actual fix is disabling the platform effect for the wrapped content via
 * [withoutPlatformOverscroll] (LocalOverscrollConfiguration = null) and
 * only ever running ONE overscroll system at a time — this one.
 */
@Composable
fun Modifier.wobbleOverscroll(): Modifier {
    val maxOffsetPx = with(LocalDensity.current) { 28.dp.toPx() }
    var raw by remember { mutableFloatStateOf(0f) }
    val settleAnim = remember { Animatable(0f, visibilityThreshold = 0.25f) }
    val settleSpec = remember {
        spring(
            dampingRatio = 0.82f,
            stiffness = 440f,
            visibilityThreshold = 0.25f,
        )
    }

    val connection = remember(maxOffsetPx) {
        object : NestedScrollConnection {
            // Smoothly unwind overscroll if user reverses drag direction before lifting finger
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (raw != 0f && available.y != 0f && sign(raw) != sign(available.y)) {
                    val prev = raw
                    val next = prev + available.y * 0.45f
                    raw = if (sign(next) != sign(prev)) 0f else next
                    val consumedY = (raw - prev) / 0.45f
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f && source == NestedScrollSource.UserInput) {
                    val base = if (raw == 0f && abs(settleAnim.value) > 0.5f) settleAnim.value else raw
                    // Progressive rubber-band resistance instead of hitting a hard wall
                    val resistance = (1f - (abs(base) / maxOffsetPx).coerceIn(0f, 1f)) * 0.36f
                    raw = (base + available.y * resistance).coerceIn(-maxOffsetPx, maxOffsetPx)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (raw != 0f) {
                    val start = raw
                    settleAnim.snapTo(start)
                    raw = 0f
                    settleAnim.animateTo(
                        targetValue = 0f,
                        initialVelocity = (available.y * 0.12f).coerceIn(-600f, 600f),
                        animationSpec = settleSpec,
                    )
                }
                return Velocity.Zero
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            val offset = if (raw != 0f) raw else settleAnim.value
            translationY = offset
            clip = abs(offset) > 0.25f
        }
}

/** Disables Android's own default stretch/glow overscroll for whatever
 *  scrollable content is composed inside [content] — see
 *  [wobbleOverscroll]'s doc comment for why this matters: without it, the
 *  platform's own overscroll effect keeps running underneath a custom one
 *  and the two visibly conflict at the scroll boundary. */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WithoutPlatformOverscroll(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.LocalOverscrollConfiguration provides null,
        content = content,
    )
}
