package com.neonsong.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val MIN_CORNER_SCALE = 0.6f
const val MAX_CORNER_SCALE = 1.4f

fun clampCornerScale(scale: Float): Float =
    if (scale.isFinite()) scale.coerceIn(MIN_CORNER_SCALE, MAX_CORNER_SCALE) else 1.0f

val LocalShapeScale = compositionLocalOf { 1.0f }

@Immutable
data class NeonCornerTokens(
    val rawScale: Float = 1.0f,
) {
    val scale: Float = clampCornerScale(rawScale)

    val extraSmallDp: Dp = (10f * scale).dp
    val smallDp: Dp = (14f * scale).dp
    val mediumDp: Dp = (18f * scale).dp
    val largeDp: Dp = (24f * scale).dp
    val extraLargeDp: Dp = (30f * scale).dp

    val studioHero: Shape = SquircleShape((28f * scale).dp)
    val hero: Shape = SquircleShape(extraLargeDp)
    val heroInner: Shape = SquircleShape((22f * scale).dp)
    val listContainer: Shape = SquircleShape(largeDp)
    val card: Shape = SquircleShape((20f * scale).dp)
    val statPill: Shape = SquircleShape((16f * scale).dp)
    val trackRow: Shape = SquircleShape((16f * scale).dp)
    val artwork: Shape = SquircleShape(smallDp)

    fun dp(base: Dp): Dp = (base.value * scale).dp

    fun squircle(base: Dp): SquircleShape = SquircleShape((base.value * scale).dp)

    fun rounded(base: Dp): RoundedCornerShape = RoundedCornerShape((base.value * scale).dp)
}

val LocalNeonCorners = compositionLocalOf { NeonCornerTokens(1.0f) }

@Composable
fun rememberScaledCornerDp(base: Dp): Dp {
    val scale = LocalShapeScale.current
    return remember(base, scale) { (base.value * clampCornerScale(scale)).dp }
}

@Composable
fun rememberScaledSquircle(base: Dp): SquircleShape {
    val scale = LocalShapeScale.current
    return remember(base, scale) { SquircleShape((base.value * clampCornerScale(scale)).dp) }
}

fun neonShapesForScale(scale: Float = 1.0f): Shapes {
    val s = clampCornerScale(scale)
    return Shapes(
        extraSmall = SquircleShape((10f * s).dp),
        small = SquircleShape((14f * s).dp),
        medium = SquircleShape((18f * s).dp),
        large = SquircleShape((24f * s).dp),
        extraLarge = SquircleShape((30f * s).dp),
    )
}

/** app.css: --radius: 20px; --radius-sm: 12px; --radius-xs: 8px */
val NeonSongShapes = neonShapesForScale(1.0f)

val ExpressiveHeroShape = SquircleShape(30.dp)
val ExpressivePillShape = RoundedCornerShape(percent = 50)
val HeroInnerShape = SquircleShape(22.dp)
val StatPillShape = SquircleShape(16.dp)
val ListContainerShape = SquircleShape(24.dp)
val BadgePillShape = RoundedCornerShape(percent = 50)
val NowPlayingCardShape = SquircleShape(20.dp)
val TrackRowShape = SquircleShape(16.dp)
val ArtworkShape = SquircleShape(14.dp)

/**
 * Material 3 Expressive 10-step corner scale primitives.
 */
object M3ExpressiveShape {
    val None = RoundedCornerShape(0.dp)
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val LargeIncreased = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(28.dp)
    val ExtraLargeIncreased = RoundedCornerShape(32.dp)
    val ExtraExtraLarge = RoundedCornerShape(48.dp)
    val Full = RoundedCornerShape(percent = 50)

    // Asymmetric shapes for connected components (e.g. ConnectedButtonGroup)
    val GroupLeft = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val GroupMiddle = RoundedCornerShape(4.dp)
    val GroupRight = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp)
    val GroupTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val GroupBottom = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    val GroupSingle = RoundedCornerShape(20.dp)
}
