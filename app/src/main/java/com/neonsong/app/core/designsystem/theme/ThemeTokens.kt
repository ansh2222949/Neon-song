package com.neonsong.app.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard design tokens for Neon Song.
 * Enforces unified spacing, corner radius scales, elevation, and glass alpha values.
 */
object ThemeTokens {
    // Corner Radii
    val RadiusHero: Dp = 28.dp
    val RadiusCard: Dp = 24.dp
    val RadiusTile: Dp = 18.dp
    val RadiusPill: Dp = 100.dp
    val RadiusSmall: Dp = 12.dp

    // Spacing
    val SpacingScreenPadding: Dp = 16.dp
    val SpacingSection: Dp = 14.dp
    val SpacingCardVertical: Dp = 10.dp
    val SpacingItem: Dp = 8.dp
    val SpacingCompact: Dp = 4.dp

    // Glass Alpha Multipliers
    const val AlphaGlassHighlight: Float = 0.52f
    const val AlphaGlassPrimaryTint: Float = 0.42f
    const val AlphaGlassTertiaryTint: Float = 0.28f
    const val AlphaGlassSurfaceHigh: Float = 0.62f
    const val AlphaGlassSurfaceNormal: Float = 0.70f
    const val AlphaGlassBorderMuted: Float = 0.10f

    // Elevation
    val ElevationNone: Dp = 0.dp
    val ElevationSubtle: Dp = 2.dp
    val ElevationModal: Dp = 8.dp
}

