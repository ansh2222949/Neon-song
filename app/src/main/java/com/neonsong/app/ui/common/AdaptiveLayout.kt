package com.neonsong.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Material 3 window size classes based on viewport width breakpoints.
 * - COMPACT: Standard phones in portrait (< 600dp)
 * - MEDIUM: Foldables unfolded, 7-8" tablets, portrait 10" tablets (600dp..839dp)
 * - EXPANDED: Large tablets (10"+), landscape tablets, desktop/Chromebook (>= 840dp)
 */
enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 600 -> WindowSizeClass.COMPACT
        screenWidth < 840 -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
}

/**
 * Granular device form factor classification targeting mobile variety:
 * - COMPACT_SMALL: Narrow or small mobile devices (< 360dp, e.g. Fold outer display, compact budget phones)
 * - COMPACT_NORMAL: Standard mobile phones (360dp .. 411dp, e.g. Pixel, Galaxy S standard)
 * - COMPACT_LARGE: Large phablet / Plus / Ultra devices (412dp .. 599dp)
 * - MEDIUM: Foldables unfolded, 7-8" mini-tablets (600dp .. 839dp)
 * - EXPANDED: Large tablets, Chromebooks, desktop (>= 840dp)
 */
enum class ScreenFormFactor {
    COMPACT_SMALL,
    COMPACT_NORMAL,
    COMPACT_LARGE,
    MEDIUM,
    EXPANDED,
}

@Composable
fun rememberScreenFormFactor(): ScreenFormFactor {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 360 -> ScreenFormFactor.COMPACT_SMALL
        screenWidth < 412 -> ScreenFormFactor.COMPACT_NORMAL
        screenWidth < 600 -> ScreenFormFactor.COMPACT_LARGE
        screenWidth < 840 -> ScreenFormFactor.MEDIUM
        else -> ScreenFormFactor.EXPANDED
    }
}

/** Returns true if screen width is narrow/compact mobile (< 360dp). */
@Composable
fun isSmallMobileScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp < 360

/** Returns true if screen width is a large phone or phablet (412dp .. 599dp). */
@Composable
fun isLargeMobileScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp in 412..599

/** Returns true if the current display is a tablet, foldable, or wide viewport (>= 600dp). */
@Composable
fun isTabletOrWideScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 600

/** Returns true if the current display is an expanded display (>= 840dp). */
@Composable
fun isExpandedScreen(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 840

/**
 * Dynamically resolves hero artwork size based on phone screen width.
 */
@Composable
fun rememberAdaptiveHeroSize(
    smallPhone: Dp = 210.dp,
    standardPhone: Dp = 250.dp,
    largePhone: Dp = 270.dp,
    tablet: Dp = 280.dp,
): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 360 -> smallPhone
        screenWidth < 412 -> standardPhone
        screenWidth < 600 -> largePhone
        else -> tablet
    }
}

/**
 * Dynamically resolves horizontal screen margin/padding based on screen size.
 */
@Composable
fun rememberAdaptivePadding(
    smallPhone: Dp = 10.dp,
    standardPhone: Dp = 16.dp,
    largePhone: Dp = 20.dp,
    tablet: Dp = 24.dp,
): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 360 -> smallPhone
        screenWidth < 412 -> standardPhone
        screenWidth < 600 -> largePhone
        else -> tablet
    }
}

/**
 * Dynamically resolves carousel card width for optimal proportions across mobile screens.
 */
@Composable
fun rememberAdaptiveCardWidth(
    smallPhone: Dp = 145.dp,
    standardPhone: Dp = 160.dp,
    largePhone: Dp = 175.dp,
    tablet: Dp = 190.dp,
): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 360 -> smallPhone
        screenWidth < 412 -> standardPhone
        screenWidth < 600 -> largePhone
        else -> tablet
    }
}

/**
 * Dynamically resolves header background height for full-bleed covers.
 */
@Composable
fun rememberAdaptiveHeaderHeight(
    smallPhone: Dp = 380.dp,
    standardPhone: Dp = 440.dp,
    largePhone: Dp = 480.dp,
    tablet: Dp = 520.dp,
): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 360 || screenHeight < 680 -> smallPhone
        screenWidth < 412 -> standardPhone
        screenWidth < 600 -> largePhone
        else -> tablet
    }
}

@Composable
fun Modifier.adaptiveContentWidth(maxWidth: Dp = 840.dp): Modifier {
    return if (isTabletOrWideScreen()) {
        this.widthIn(max = maxWidth).fillMaxWidth()
    } else {
        this
    }
}
