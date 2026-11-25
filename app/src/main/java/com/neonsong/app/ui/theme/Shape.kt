package com.neonsong.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** app.css: --radius: 20px; --radius-sm: 12px; --radius-xs: 8px */
val NeonSongShapes = Shapes(
    extraSmall = SquircleShape(10.dp),
    small = SquircleShape(14.dp),
    medium = SquircleShape(18.dp),
    large = SquircleShape(24.dp),
    extraLarge = SquircleShape(30.dp),
)

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
