package com.neonsong.app.ui.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Shared, restrained Material 3 Expressive motion language for the app. */
object ExpressiveMotion {
    const val Quick = 160
    const val Standard = 300
    const val Emphasized = 440

    // Tight threshold so small-delta Float animations (scale/alpha) settle smoothly without end-snap.
    private const val FloatVisibilityThreshold = 0.0005f

    val FloatPressSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 620f,
        visibilityThreshold = FloatVisibilityThreshold,
    )

    val FloatReleaseSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = 440f,
        visibilityThreshold = FloatVisibilityThreshold,
    )

    val FloatFluidSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
        visibilityThreshold = FloatVisibilityThreshold,
    )

    // Apple-inspired fluid motion curves:
    // Fluid: iOS standard fluid physics (silky glide, clean deceleration, no overshoot)
    fun <T> appleFluidSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
    )

    // Sheet / Modal: smooth Apple sheet expansion (non-bouncy, pure slide)
    fun <T> appleSheetSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 340f,
    )

    // Tactile: responsive control touch & snap
    fun <T> appleTactileSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f,
    )

    // Calm settle: subtle organic spring without wild bounce
    fun <T> appleBouncySpring() = spring<T>(
        dampingRatio = 0.82f,
        stiffness = 420f,
    )

    // Spatial springs: fluid deceleration with natural settle, zero bounce
    fun <T> spatialFast() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> spatialDefault() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
    )

    fun <T> spatialSlow() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow,
    )

    // Legacy alias
    fun <T> spatialSpring() = spatialDefault<T>()

    // Effects springs: no overshoot (for alpha, color)
    fun <T> effectsFast() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> effectsDefault() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> effectsSlow() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessVeryLow,
    )

    // Legacy alias
    fun <T> smoothSpring() = appleFluidSpring<T>()

    private val EnterEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    private val ExitEase = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Pre-allocated transitions so NavHost never rebuilds transition specs on recomposition.
    private val CachedForwardEnter: EnterTransition =
        fadeIn(tween(durationMillis = 220, delayMillis = 25, easing = EnterEase)) +
            slideInHorizontally(tween(durationMillis = 260, easing = EnterEase)) { (it * 0.06f).toInt() } +
            scaleIn(tween(durationMillis = 260, easing = EnterEase), initialScale = 0.98f)

    private val CachedForwardExit: ExitTransition =
        fadeOut(tween(durationMillis = 130, easing = ExitEase)) +
            slideOutHorizontally(tween(durationMillis = 180, easing = ExitEase)) { -(it * 0.04f).toInt() }

    private val CachedBackEnter: EnterTransition =
        fadeIn(tween(durationMillis = 220, delayMillis = 20, easing = EnterEase)) +
            slideInHorizontally(tween(durationMillis = 260, easing = EnterEase)) { -(it * 0.05f).toInt() } +
            scaleIn(tween(durationMillis = 260, easing = EnterEase), initialScale = 0.985f)

    private val CachedBackExit: ExitTransition =
        fadeOut(tween(durationMillis = 130, easing = ExitEase)) +
            slideOutHorizontally(tween(durationMillis = 180, easing = ExitEase)) { (it * 0.06f).toInt() }

    fun forwardEnter(): EnterTransition = CachedForwardEnter
    fun forwardExit(): ExitTransition = CachedForwardExit
    fun backEnter(): EnterTransition = CachedBackEnter
    fun backExit(): ExitTransition = CachedBackExit
}

/**
 * Tactile spring press feedback modifier conforming to Apple-like tactile responsiveness.
 * Runs entirely in the draw phase via graphicsLayer (zero layout/recomposition on press).
 */
@Composable
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = if (isPressed) {
            ExpressiveMotion.FloatPressSpring
        } else {
            ExpressiveMotion.FloatReleaseSpring
        },
        label = "expressivePressScale",
    )
    return this.graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
    }
}

/**
 * Clickable surface with expressive spring-based press feedback.
 */
@Composable
fun Modifier.expressiveBounceClickable(
    enabled: Boolean = true,
    targetScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .expressivePressScale(interactionSource, targetScale)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick,
        )
}
