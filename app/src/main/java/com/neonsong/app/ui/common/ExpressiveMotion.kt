package com.neonsong.app.ui.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/** Shared, restrained Material 3 Expressive motion language for the app. */
object ExpressiveMotion {
    const val Quick = 160
    const val Standard = 300
    const val Emphasized = 440

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

    // Calm settle: zero overshoot
    fun <T> appleBouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
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

    fun forwardEnter(): EnterTransition =
        fadeIn(effectsDefault()) +
            slideInHorizontally(appleFluidSpring()) { it / 6 } +
            scaleIn(appleFluidSpring(), initialScale = 0.95f)

    fun forwardExit(): ExitTransition =
        fadeOut(effectsFast()) +
            slideOutHorizontally(appleFluidSpring()) { -it / 8 } +
            scaleOut(appleFluidSpring(), targetScale = 0.97f)

    fun backEnter(): EnterTransition =
        fadeIn(effectsDefault()) +
            slideInHorizontally(appleFluidSpring()) { -it / 8 } +
            scaleIn(appleFluidSpring(), initialScale = 0.97f)

    fun backExit(): ExitTransition =
        fadeOut(effectsFast()) +
            slideOutHorizontally(appleFluidSpring()) { it / 6 } +
            scaleOut(appleFluidSpring(), targetScale = 0.95f)
}

/**
 * Tactile spring press feedback modifier conforming to Apple-like tactile responsiveness.
 * Subtly squishes the component when held and springs back dynamically on release.
 */
@Composable
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    targetScale: Float = 0.96f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = ExpressiveMotion.appleTactileSpring(),
        label = "expressivePressScale",
    )
    return this.scale(scale)
}

/**
 * Clickable surface with expressive spring-based bounce feedback.
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
