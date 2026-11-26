package com.neonsong.app.features.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.core.designsystem.components.StatusPill
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.local.EqualizerSettings
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.repository.ThemeUiState
import com.neonsong.app.features.settings.audio.EqualizerCurveGraph
import com.neonsong.app.playback.ClarityPresets
import com.neonsong.app.playback.LoudnessMode
import com.neonsong.app.playback.LoudnessSettings
import com.neonsong.app.ui.common.ArtworkPaletteCache
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.NeonAppLogo
import com.neonsong.app.ui.settings.AppleAudioQualityTier
import com.neonsong.app.ui.settings.SettingsTab
import com.neonsong.app.ui.theme.Backdrop
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalIsAmoled
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalNeonCorners
import com.neonsong.app.ui.theme.LocalShapeScale
import com.neonsong.app.ui.theme.Md3SchemeBuilder
import com.neonsong.app.ui.theme.NeonCornerTokens
import com.neonsong.app.ui.theme.animateSmoothly
import com.neonsong.app.ui.theme.clampCornerScale
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.neonShapesForScale
import com.neonsong.app.util.LocalHapticManager
import kotlin.math.roundToInt

@Immutable
data class AudioCodecSnapshot(
    val audioCodec: String? = null,
    val bitDepth: Int? = null,
    val samplingRateKHz: Double? = null,
    val isLossless: Boolean = false,
    val bitrateKbps: Int? = null,
)

/**
 * Shared Liquid Glass studio card shell used both by the sticky Live Preview
 * Studio Card (Appearance & Audio tabs) and standard Settings sub-page heroes,
 * deduplicating [SubPageGlassHeroCard], [AppearanceStudioHero], and [AudioStudioHero].
 */
@Composable
fun SettingsStudioHeroShell(
    shape: Shape,
    accentTint: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    shadowAccent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val shadowPx = with(LocalDensity.current) { (if (isDark) 16.dp else 10.dp).toPx() }
    val spotShadow = Color.Black.copy(alpha = 0.45f)

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = null,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = shadowPx
                this.shape = shape
                clip = false
                ambientShadowColor = shadowAccent.copy(alpha = 0.28f)
                spotShadowColor = spotShadow
            }
            .liquidGlassChrome(
                shape = shape,
                preset = LiquidGlassPreset.Card,
                backdrop = backdrop,
                accentTint = accentTint,
            ),
    ) {
        content()
    }
}

/**
 * Sticky Live Preview Studio Card mounted inside SettingsScreen's floating top
 * chrome (`zIndex(2f)`), dynamically previewing Appearance or Audio settings.
 */
@Composable
fun SettingsLivePreviewCard(
    tab: SettingsTab,
    themeUiState: ThemeUiState?,
    misc: MiscSettings,
    eq: EqualizerSettings,
    liveEqGains: List<Float>,
    usbExclusiveEnabled: Boolean,
    loudness: LoudnessSettings,
    codecSnapshot: AudioCodecSnapshot,
    isolateSongPalette: Boolean,
    onToggleIsolateSongPalette: () -> Unit,
    onOpenEqualizer: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    when (tab) {
        SettingsTab.APPEARANCE -> AppearanceLivePreviewCard(
            themeUiState = themeUiState,
            misc = misc,
            isolateSongPalette = isolateSongPalette,
            onToggleIsolateSongPalette = onToggleIsolateSongPalette,
            backdrop = backdrop,
            modifier = modifier,
        )
        SettingsTab.AUDIO -> AudioLivePreviewCard(
            eq = eq,
            liveEqGains = liveEqGains,
            misc = misc,
            usbExclusiveEnabled = usbExclusiveEnabled,
            loudness = loudness,
            codecSnapshot = codecSnapshot,
            onOpenEqualizer = onOpenEqualizer,
            backdrop = backdrop,
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
private fun AppearanceLivePreviewCard(
    themeUiState: ThemeUiState?,
    misc: MiscSettings,
    isolateSongPalette: Boolean,
    onToggleIsolateSongPalette: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticManager.current
    val isSystemDark = isSystemInDarkTheme()
    val themeMode = themeUiState?.themeMode ?: ThemeMode.SYSTEM
    val previewIsDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
    val previewIsAmoled = previewIsDark &&
        ((themeUiState?.amoled ?: false) || themeMode == ThemeMode.AMOLED)
    val previewLiquidGlass = themeUiState?.liquidGlass ?: true
    val previewScale = clampCornerScale(themeUiState?.cornerScale ?: 1.0f)
    val previewCorners = remember(previewScale) { NeonCornerTokens(previewScale) }
    val previewShapes = remember(previewScale) { neonShapesForScale(previewScale) }

    val activeSongPalette = ArtworkPaletteCache.activeSongPalette.value
    val baseAccentHex = themeUiState?.accentColorHex ?: "#00E5FF"
    val accentMode = themeUiState?.mode ?: AccentMode.MANUAL

    val baseScheme: ColorScheme = remember(
        themeUiState,
        previewIsDark,
        previewIsAmoled,
        previewLiquidGlass,
        accentMode,
        baseAccentHex,
        isolateSongPalette,
    ) {
        val canUseSongScheme = !isolateSongPalette && themeUiState != null
        if (canUseSongScheme && themeUiState?.isNowPlayingThemed == true) {
            if (previewIsDark) themeUiState.darkColorScheme else themeUiState.lightColorScheme
        } else {
            when (accentMode) {
                AccentMode.MONOCHROME -> if (previewIsDark) {
                    Md3SchemeBuilder.buildMonochromeDarkScheme(previewIsAmoled, previewLiquidGlass)
                } else {
                    Md3SchemeBuilder.buildMonochromeLightScheme(previewLiquidGlass)
                }
                AccentMode.DYNAMIC -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (previewIsDark) {
                            val base = Md3SchemeBuilder.enrichDynamicColors(dynamicDarkColorScheme(context))
                            if (previewIsAmoled) {
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
                        } else {
                            Md3SchemeBuilder.enrichDynamicColors(dynamicLightColorScheme(context))
                        }
                    } else if (previewIsDark) {
                        Md3SchemeBuilder.enrichDynamicColors(
                            Md3SchemeBuilder.buildDarkScheme(baseAccentHex, previewIsAmoled, previewLiquidGlass),
                        )
                    } else {
                        Md3SchemeBuilder.enrichDynamicColors(
                            Md3SchemeBuilder.buildLightScheme(baseAccentHex, previewLiquidGlass),
                        )
                    }
                }
                AccentMode.MANUAL -> if (previewIsDark) {
                    Md3SchemeBuilder.buildDarkScheme(baseAccentHex, previewIsAmoled, previewLiquidGlass)
                } else {
                    Md3SchemeBuilder.buildLightScheme(baseAccentHex, previewLiquidGlass)
                }
            }
        }
    }

    val animatedPreviewScheme = baseScheme.animateSmoothly(durationMillis = 380)

    CompositionLocalProvider(
        LocalLiquidGlass provides previewLiquidGlass,
        LocalIsDarkTheme provides previewIsDark,
        LocalIsAmoled provides previewIsAmoled,
        LocalShapeScale provides previewScale,
        LocalNeonCorners provides previewCorners,
    ) {
        MaterialTheme(
            colorScheme = animatedPreviewScheme,
            shapes = previewShapes,
        ) {
            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val surfaceTint = if (previewIsAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
            val songAccent = if (!isolateSongPalette) activeSongPalette?.primary else null
            val effectiveAccent = songAccent ?: primary

            val themeModeLabel = when {
                previewIsAmoled -> "AMOLED"
                themeMode == ThemeMode.SYSTEM -> if (previewIsDark) "System Dark" else "System Light"
                themeMode == ThemeMode.LIGHT -> "Light"
                else -> "Dark"
            }
            val accentLabel = when {
                !isolateSongPalette && (activeSongPalette != null || misc.dynamicNowPlayingEnabled) -> "Song Art"
                accentMode == AccentMode.MONOCHROME -> "Mono"
                accentMode == AccentMode.DYNAMIC -> "Material You"
                else -> "Custom"
            }

            SettingsStudioHeroShell(
                shape = previewCorners.studioHero,
                accentTint = effectiveAccent.copy(alpha = 0.22f),
                shadowAccent = effectiveAccent,
                backdrop = backdrop,
                modifier = modifier,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    effectiveAccent.copy(alpha = if (previewIsAmoled) 0.14f else 0.18f),
                                    tertiary.copy(alpha = if (previewIsAmoled) 0.08f else 0.12f),
                                    surfaceTint.copy(alpha = if (previewLiquidGlass) 0.18f else 0.92f),
                                ),
                            ),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Header + Song Palette Isolation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(previewCorners.squircle(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Appearance Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$themeModeLabel • $accentLabel • ${(previewScale * 100).roundToInt()}% corner",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = previewCorners.squircle(12.dp),
                            color = if (isolateSongPalette) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isolateSongPalette) primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .clip(previewCorners.squircle(12.dp))
                                .clickable {
                                    haptic.click()
                                    onToggleIsolateSongPalette()
                                },
                        ) {
                            Text(
                                text = if (isolateSongPalette) "Isolated" else "Song Art",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isolateSongPalette) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }

                    // Mini Interactive Theme / Corner / Glass Surface Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = previewCorners.card,
                                accentTint = effectiveAccent.copy(alpha = 0.14f),
                            )
                            .background(
                                if (previewLiquidGlass) Color.Transparent
                                else surfaceTint.copy(alpha = 0.96f),
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            NeonAppLogo(
                                size = 34.dp,
                                shape = previewCorners.artwork,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Neon Song Studio",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (previewLiquidGlass) "Liquid Glass Refraction" else "Solid Material Surface",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(
                                shape = previewCorners.statPill,
                                color = effectiveAccent,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "Accent",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }

                    // Live Status Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusPill(label = themeModeLabel, tint = effectiveAccent)
                        StatusPill(
                            label = if (previewLiquidGlass) "Glass On" else "Glass Off",
                            tint = if (previewLiquidGlass) tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusPill(
                            label = "Corner ${(previewScale * 100).roundToInt()}%",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        if (previewIsAmoled) {
                            StatusPill(label = "True Black", tint = effectiveAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLivePreviewCard(
    eq: EqualizerSettings,
    liveEqGains: List<Float>,
    misc: MiscSettings,
    usbExclusiveEnabled: Boolean,
    loudness: LoudnessSettings,
    codecSnapshot: AudioCodecSnapshot,
    onOpenEqualizer: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val corners = LocalNeonCorners.current
    val haptic = LocalHapticManager.current

    val dspBypassed = misc.isBitPerfectEnabled || misc.systemEffectsMode
    val eqActive = eq.enabled && !dspBypassed
    val clarityActive = misc.isStudioMasterClarityEnabled && !dspBypassed
    val clarityPreset = remember(misc.clarityPreset) { ClarityPresets.fromIndex(misc.clarityPreset) }

    val codecBadge = remember(codecSnapshot, misc) {
        val activeCodec = codecSnapshot.audioCodec?.takeIf { it.isNotBlank() }
        when {
            activeCodec != null && codecSnapshot.bitDepth != null && codecSnapshot.samplingRateKHz != null ->
                "$activeCodec • ${codecSnapshot.bitDepth}/${codecSnapshot.samplingRateKHz}kHz"
            activeCodec != null -> activeCodec
            misc.preferLosslessStreaming -> AppleAudioQualityTier.fromQualityId(misc.wifiStreamingQuality).title
            else -> "High Quality"
        }
    }

    val dspBadges = remember(
        eqActive,
        eq.presetName,
        misc,
        usbExclusiveEnabled,
        loudness.mode,
        clarityActive,
        clarityPreset,
    ) {
        buildList<Pair<String, Color>> {
            if (misc.isBitPerfectEnabled) add("Bit-Perfect" to primary)
            if (usbExclusiveEnabled) add("USB DAC" to primary)
            if (misc.systemEffectsMode) add("Ext Effects" to tertiary)
            if (clarityActive) add("Clarity • ${clarityPreset.displayName}" to secondary)
            if (eqActive) add("EQ • ${eq.presetName}" to primary)
            if (!dspBypassed && misc.spatialImmersionEnabled) add("3D Spatial" to tertiary)
            if (!dspBypassed && misc.bauerCrossfeedEnabled) add("Crossfeed" to secondary)
            if (!dspBypassed && misc.analogTubeWarmthEnabled) add("Tube" to tertiary)
            if (!dspBypassed && misc.monoBassEnabled) add("Mono Bass" to secondary)
            if (misc.automixEnabled) add("AutoMix" to primary)
            else if (misc.autoDJEnabled) add("Auto DJ" to secondary)
            if (misc.musicHapticsEnabled) add("Haptics" to tertiary)
            if (!misc.isBitPerfectEnabled && !usbExclusiveEnabled && loudness.mode != LoudnessMode.OFF) {
                add("ReplayGain" to secondary)
            }
        }
    }

    SettingsStudioHeroShell(
        shape = corners.studioHero,
        accentTint = primary.copy(alpha = 0.22f),
        shadowAccent = primary,
        backdrop = backdrop,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            primary.copy(alpha = 0.16f),
                            secondary.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GroupIconBadge(
                    icon = Icons.Filled.GraphicEq,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 38.dp,
                    iconSize = 20.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio & DSP Studio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (dspBypassed) {
                            if (misc.isBitPerfectEnabled) "Bit-Perfect bypass active" else "External system effects active"
                        } else if (eqActive) {
                            "16-Band EQ (${eq.presetName}) • Atmos ${misc.dolbyAtmosMode}"
                        } else {
                            "DSP chain ready • Atmos ${misc.dolbyAtmosMode}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(label = codecBadge, tint = primary)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(corners.squircle(22.dp))
                    .clickable {
                        haptic.click()
                        onOpenEqualizer()
                    },
            ) {
                EqualizerCurveGraph(
                    bandCount = liveEqGains.size,
                    gainAt = { liveEqGains[it] },
                    enabled = eqActive,
                    graphHeight = 82.dp,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (dspBadges.isEmpty()) {
                    StatusPill(label = "Flat / Standard Output", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dspBadges.forEach { (label, tint) ->
                        StatusPill(label = label, tint = tint)
                    }
                }
            }
        }
    }
}
