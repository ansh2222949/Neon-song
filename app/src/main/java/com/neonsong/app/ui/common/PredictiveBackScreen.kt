package com.neonsong.app.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import com.neonsong.app.ui.theme.SquircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Wraps a pushed screen (Settings / Search / Discover / Genres — any
 * NavHost destination reached by navigating on top of MainShell) with
 * Android's Predictive Back visuals: as the user drags the system back
 * gesture in from the edge, this screen scales down and its corners round
 * off — the same "lifting off the stack" treatment modern Google apps use.
 * Releasing past the gesture's commit point finishes the animation and
 * calls [onBack] (pop the stack); releasing early cancels and the screen
 * springs back to full size.
 *
 * Anchored to the edge the gesture actually started from, not the screen's
 * center: BackEventCompat reports which edge (LEFT/RIGHT) and how far down
 * the screen (touchY) the drag began, and the shrink's pivot is set to
 * that exact point via TransformOrigin. That's what makes it read as
 * "shrinking away from the corner/edge you dragged from" instead of
 * uniformly shrinking toward the middle of the screen — the same
 * touch-anchored feel the system's own predictive back uses, and it also
 * nudges the screen a few dp further toward that edge as it shrinks, on
 * top of the scale, for the same reason.
 *
 * This animates the CURRENT (exiting) screen only — since NavHost here
 * only keeps the active back-stack entry composed, there's no destination
 * screen underneath to reveal/blur during the gesture. A blurred preview
 * of the previous screen would need it kept composed alongside this one
 * (e.g. via a two-pane Crossfade or navigation-compose 2.8+'s built-in
 * predictive-back transitions) — noted as a follow-up if that fuller
 * effect is wanted; this still gives real, system-driven progress-based
 * animation rather than a canned pop transition.
 *
 * Falls back gracefully pre-Android 13 / wherever predictive back isn't
 * available: PredictiveBackHandler's Flow simply completes without
 * meaningful per-frame progress, so [onBack] still fires correctly — just
 * without the scale/round animation playing first.
 */
private class BackGestureMeta {
    var edge: Int = BackEventCompat.EDGE_LEFT
    var touchYFraction: Float = 0.5f
    var containerHeightPx: Float = 1f
}

@Composable
fun PredictiveBackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val meta = remember { BackGestureMeta() }
    val backClipShape = remember { SquircleShape(30.dp) }

    PredictiveBackHandler(enabled = enabled) { backEvents ->
        resetJob?.cancelAndJoin()
        resetJob = null
        try {
            backEvents.collect { event ->
                meta.edge = event.swipeEdge
                meta.touchYFraction = (event.touchY / meta.containerHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                progress.snapTo(event.progress)
            }
            progress.snapTo(0f)
            onBack()
        } finally {
            if (progress.value > 0f) {
                resetJob = gestureScope.launch {
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.86f,
                            stiffness = 460f,
                            visibilityThreshold = 0.001f,
                        ),
                    )
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .then(if (backgroundColor != Color.Transparent) Modifier.background(backgroundColor) else Modifier)
            .onSizeChanged { size -> meta.containerHeightPx = size.height.toFloat().coerceAtLeast(1f) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    if (p <= 0.001f) {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        alpha = 1f
                        clip = false
                        shadowElevation = 0f
                    } else {
                        val isRight = meta.edge == BackEventCompat.EDGE_RIGHT
                        val scale = 1f - 0.11f * p
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(if (isRight) 1f else 0f, meta.touchYFraction)
                        translationX = (if (isRight) 1f else -1f) * 26.dp.toPx() * p
                        alpha = 1f - 0.22f * p
                        shape = backClipShape
                        clip = true
                        shadowElevation = 20f * p
                    }
                },
        ) {
            content()
        }
    }
}
