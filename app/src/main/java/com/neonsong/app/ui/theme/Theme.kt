@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.neonsong.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.repository.ThemeUiState
import com.neonsong.app.util.HapticManager
import com.neonsong.app.util.LocalHapticManager

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.neonsong.app.data.local.ThemeMode

/** Shared composition local indicating whether the active theme is dark mode. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

val LocalIsAmoled = staticCompositionLocalOf { false }

@Composable
fun ColorScheme.animateSmoothly(durationMillis: Int = 450): ColorScheme {
    val spec = tween<Color>(durationMillis = durationMillis, easing = FastOutSlowInEasing)
    return copy(
        primary = animateColorAsState(primary, spec, label = "cPrimary").value,
        onPrimary = animateColorAsState(onPrimary, spec, label = "cOnPrimary").value,
        primaryContainer = animateColorAsState(primaryContainer, spec, label = "cPrimaryContainer").value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, spec, label = "cOnPrimaryContainer").value,
        inversePrimary = animateColorAsState(inversePrimary, spec, label = "cInversePrimary").value,
        secondary = animateColorAsState(secondary, spec, label = "cSecondary").value,
        onSecondary = animateColorAsState(onSecondary, spec, label = "cOnSecondary").value,
        secondaryContainer = animateColorAsState(secondaryContainer, spec, label = "cSecondaryContainer").value,
        onSecondaryContainer = animateColorAsState(onSecondaryContainer, spec, label = "cOnSecondaryContainer").value,
        tertiary = animateColorAsState(tertiary, spec, label = "cTertiary").value,
        onTertiary = animateColorAsState(onTertiary, spec, label = "cOnTertiary").value,
        tertiaryContainer = animateColorAsState(tertiaryContainer, spec, label = "cTertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(onTertiaryContainer, spec, label = "cOnTertiaryContainer").value,
        background = animateColorAsState(background, spec, label = "cBackground").value,
        onBackground = animateColorAsState(onBackground, spec, label = "cOnBackground").value,
        surface = animateColorAsState(surface, spec, label = "cSurface").value,
        onSurface = animateColorAsState(onSurface, spec, label = "cOnSurface").value,
        surfaceVariant = animateColorAsState(surfaceVariant, spec, label = "cSurfaceVariant").value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, spec, label = "cOnSurfaceVariant").value,
        surfaceContainerLowest = animateColorAsState(surfaceContainerLowest, spec, label = "cSurfContLowest").value,
        surfaceContainerLow = animateColorAsState(surfaceContainerLow, spec, label = "cSurfContLow").value,
        surfaceContainer = animateColorAsState(surfaceContainer, spec, label = "cSurfCont").value,
        surfaceContainerHigh = animateColorAsState(surfaceContainerHigh, spec, label = "cSurfContHigh").value,
        surfaceContainerHighest = animateColorAsState(surfaceContainerHighest, spec, label = "cSurfContHighest").value,
        outline = animateColorAsState(outline, spec, label = "cOutline").value,
        outlineVariant = animateColorAsState(outlineVariant, spec, label = "cOutlineVariant").value,
    )
}

/**
 * Wraps the whole app. Supports System Default, Light, and Dark modes.
 * Kyant0 Backdrop captures underlying content into a hardware-accelerated layer
 * shared app-wide; screens add their own sibling sources for local content
 * (nav bar, player, headers).
 */
@Composable
fun NeonSongTheme(
    themeState: ThemeUiState,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeState.themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
    // Dynamic Color ON (S+): use the live wallpaper Monet scheme instead of the
    // default manual accent or monochrome. System dynamic auto-tracks wallpaper
    // changes, so no manual refresh is needed. Now-playing artwork wins when it
    // is active; pre-S falls back to the repository's wallpaper-seeded scheme.
    val context = LocalContext.current
    val useSystemDynamic = themeState.mode == AccentMode.DYNAMIC &&
        !themeState.isNowPlayingThemed &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val systemDark = if (useSystemDynamic) {
        val base = Md3SchemeBuilder.enrichDynamicColors(dynamicDarkColorScheme(context))
        if (themeState.amoled || themeState.themeMode == ThemeMode.AMOLED) {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainer = Color(0xFF030303),
                surfaceContainerHigh = Color(0xFF080808),
                surfaceContainerHighest = Color(0xFF0D0D0D),
                surfaceVariant = Color(0xFF141414),
                outlineVariant = Color(0xFF1E1E1E),
            )
        } else base
    } else null
    val systemLight = if (useSystemDynamic) {
        Md3SchemeBuilder.enrichDynamicColors(dynamicLightColorScheme(context))
    } else null
    val activeColorScheme = when {
        systemDark != null && isDark -> systemDark
        systemLight != null && !isDark -> systemLight
        isDark -> themeState.darkColorScheme
        else -> themeState.lightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            runCatching {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    val isAmoled = isDark && (themeState.amoled || themeState.themeMode == ThemeMode.AMOLED)
    val shapeScale = clampCornerScale(themeState.cornerScale)
    val scaledShapes = remember(shapeScale) { neonShapesForScale(shapeScale) }
    val cornerTokens = remember(shapeScale) { NeonCornerTokens(shapeScale) }
    val animatedColorScheme = activeColorScheme.animateSmoothly()

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = if (themeState.useCustomFont) NeonSongTypography else SystemTypography,
        shapes = scaledShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CompositionLocalProvider(
                LocalLiquidGlass provides themeState.liquidGlass,
                LocalIsDarkTheme provides isDark,
                LocalIsAmoled provides isAmoled,
                LocalShapeScale provides shapeScale,
                LocalNeonCorners provides cornerTokens,
                LocalHapticManager provides HapticManager.getInstance(LocalContext.current).also {
                    it.attachView(LocalView.current)
                },
            ) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalRippleConfiguration provides null, // Stock M3 grey ripple permanently off
                    content = content,
                )
            }
        }
    }
}
