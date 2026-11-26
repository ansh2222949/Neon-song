package com.neonsong.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonsong.app.data.lossless.LosslessMusicApi
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface

/**
 * Target stream or download type when opening the Apple Music audio quality selector.
 */
enum class AudioQualityTarget(val title: String) {
    CELLULAR_STREAMING("Cellular Streaming"),
    WIFI_STREAMING("Wi-Fi Streaming"),
    DOWNLOADS("Downloads"),
}

/**
 * 1:1 Apple Music audio quality tiers mapped to Neon Song backend engine presets.
 */
enum class AppleAudioQualityTier(
    val qualityId: Int,
    val title: String,
    val subtitle: String,
    val technicalDetail: String,
) {
    HIGH_EFFICIENCY(
        qualityId = LosslessMusicApi.QUALITY_DATA_SAVER, // 4
        title = "High Efficiency",
        subtitle = "AAC with low data usage",
        technicalDetail = "96 kbps HE-AAC",
    ),
    HIGH_QUALITY(
        qualityId = LosslessMusicApi.QUALITY_MP3_320, // 5
        title = "High Quality",
        subtitle = "AAC 256 kbps",
        technicalDetail = "256 kbps AAC / 320 kbps MP3",
    ),
    LOSSLESS(
        qualityId = LosslessMusicApi.QUALITY_CD_LOSSLESS, // 6
        title = "Lossless",
        subtitle = "ALAC up to 24-bit/48 kHz",
        technicalDetail = "16-bit/44.1 kHz to 24-bit/48 kHz FLAC/ALAC",
    ),
    HI_RES_LOSSLESS(
        qualityId = LosslessMusicApi.QUALITY_MAX_HI_RES, // 27
        title = "Hi-Res Lossless",
        subtitle = "ALAC up to 24-bit/192 kHz",
        technicalDetail = "Up to 24-bit/192 kHz Studio FLAC/ALAC",
    ),
    DOLBY_ATMOS(
        qualityId = LosslessMusicApi.QUALITY_DOLBY_ATMOS, // 28
        title = "Dolby Atmos",
        subtitle = "Spatial Audio with Dolby Atmos",
        technicalDetail = "Multichannel EAC3-JOC Spatial Audio",
    );

    companion object {
        fun fromQualityId(id: Int): AppleAudioQualityTier = when (id) {
            LosslessMusicApi.QUALITY_DATA_SAVER -> HIGH_EFFICIENCY
            LosslessMusicApi.QUALITY_MP3_320 -> HIGH_QUALITY
            LosslessMusicApi.QUALITY_CD_LOSSLESS, LosslessMusicApi.QUALITY_HI_RES_96 -> LOSSLESS
            LosslessMusicApi.QUALITY_DOLBY_ATMOS -> DOLBY_ATMOS
            LosslessMusicApi.QUALITY_MAX_HI_RES -> HI_RES_LOSSLESS
            else -> HI_RES_LOSSLESS
        }
    }
}

/**
 * 1:1 Apple Music Audio Quality Settings Screen redesign.
 *
 * Implements:
 * 1. Song-Reactive Liquid Glass Scaffold refracting live artwork.
 * 2. Lossless Audio master toggle + informational caption.
 * 3. Spatial Audio / Dolby Atmos selector with sculpted glass options.
 * 4. Inset Grouped cards for Cellular Streaming, Wi-Fi Streaming, and Downloads.
 */
@Composable
fun AppleAudioQualityScreen(
    preferLossless: Boolean,
    cellularQuality: Int,
    wifiQuality: Int,
    downloadQuality: Int,
    dolbyAtmosMode: String = "Automatic",
    onToggleLossless: (Boolean) -> Unit,
    onNavigateToTarget: (AudioQualityTarget) -> Unit,
    onSelectDolbyAtmos: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()

    SettingsGlassScaffold(
        title = "Audio Quality",
        subtitle = "Streaming, downloads & Dolby Atmos",
        onBack = onBack,
        listState = listState,
        modifier = modifier,
    ) { contentPadding, _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: Lossless Audio Switch Card
            item(key = "lossless_master_section") {
                SettingsGlassSection(
                    title = "Lossless Audio",
                    subtitle = "Studio master bit-depth & uncompressed dynamic range",
                ) {
                    val shape = remember { SquircleShape(20.dp) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = shape,
                                accentTint = if (preferLossless) primary.copy(alpha = 0.16f) else Color.Unspecified,
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroupIconBadge(
                                icon = Icons.Filled.GraphicEq,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 44.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Lossless Audio Engine",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (preferLossless) "Bit-perfect studio resolution active" else "Standard compressed audio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = preferLossless,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onToggleLossless(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Lossless files preserve every nuance and harmonic detail of original studio master recordings. Enabling this will consume significantly more cellular data and device storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            // Section 2: Dolby Atmos / Spatial Audio Card
            item(key = "dolby_atmos_section") {
                SettingsGlassSection(
                    title = "Spatial Audio & Dolby Atmos",
                    subtitle = "Multidimensional soundstage with binaural rendering",
                ) {
                    val options = listOf(
                        "Automatic" to "Uses Dolby Atmos when spatial hardware is active",
                        "Always On" to "Always requests Dolby Atmos when available",
                        "Off" to "Standard stereo playback only",
                    )
                    ExpressiveGroup(rowCount = options.size) { index, position ->
                        val (option, optionSubtitle) = options[index]
                        val isSelected = dolbyAtmosMode.equals(option, ignoreCase = true)
                        val shape = groupShape(position)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassSurface(
                                    shape = shape,
                                    accentTint = if (isSelected) primary.copy(alpha = 0.22f) else Color.Unspecified,
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onSelectDolbyAtmos(option)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    GroupIconBadge(
                                        icon = Icons.Filled.SurroundSound,
                                        container = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        size = 38.dp,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = optionSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Plays supported tracks in Dolby Atmos spatial audio with multidimensional acoustic positioning, enhanced soundstage width, and virtual surround clarity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            // Section 3: Audio Quality Destinations (Cellular, Wi-Fi, Downloads)
            item(key = "destinations_section") {
                SettingsGlassSection(
                    title = "Quality Profiles by Connection",
                    subtitle = "Configure independent bitrates for cellular, Wi-Fi, and offline downloads",
                ) {
                    val effectiveCellularId = if (preferLossless) {
                        if (dolbyAtmosMode.equals("Off", ignoreCase = true) && cellularQuality == LosslessMusicApi.QUALITY_DOLBY_ATMOS) {
                            LosslessMusicApi.QUALITY_MAX_HI_RES
                        } else {
                            cellularQuality
                        }
                    } else if (cellularQuality == LosslessMusicApi.QUALITY_DATA_SAVER) {
                        LosslessMusicApi.QUALITY_DATA_SAVER
                    } else {
                        LosslessMusicApi.QUALITY_MP3_320
                    }
                    val effectiveWifiId = if (preferLossless) {
                        if (dolbyAtmosMode.equals("Off", ignoreCase = true) && wifiQuality == LosslessMusicApi.QUALITY_DOLBY_ATMOS) {
                            LosslessMusicApi.QUALITY_MAX_HI_RES
                        } else {
                            wifiQuality
                        }
                    } else {
                        LosslessMusicApi.QUALITY_MP3_320
                    }
                    val effectiveDownloadId = if (dolbyAtmosMode.equals("Off", ignoreCase = true) && downloadQuality == LosslessMusicApi.QUALITY_DOLBY_ATMOS) {
                        LosslessMusicApi.QUALITY_MAX_HI_RES
                    } else {
                        downloadQuality
                    }
                    ExpressiveGroup(rowCount = 3) { index, position ->
                        when (index) {
                            0 -> {
                                val cellularTier = AppleAudioQualityTier.fromQualityId(effectiveCellularId)
                                LiquidQualityActionRow(
                                    title = "Cellular Streaming",
                                    subtitle = cellularTier.title,
                                    technicalDetail = cellularTier.technicalDetail,
                                    position = position,
                                    onClick = { onNavigateToTarget(AudioQualityTarget.CELLULAR_STREAMING) },
                                )
                            }
                            1 -> {
                                val wifiTier = AppleAudioQualityTier.fromQualityId(effectiveWifiId)
                                LiquidQualityActionRow(
                                    title = "Wi-Fi Streaming",
                                    subtitle = wifiTier.title,
                                    technicalDetail = wifiTier.technicalDetail,
                                    position = position,
                                    onClick = { onNavigateToTarget(AudioQualityTarget.WIFI_STREAMING) },
                                )
                            }
                            else -> {
                                val downloadTier = AppleAudioQualityTier.fromQualityId(effectiveDownloadId)
                                LiquidQualityActionRow(
                                    title = "Downloads & Offline",
                                    subtitle = downloadTier.title,
                                    technicalDetail = downloadTier.technicalDetail,
                                    position = position,
                                    onClick = { onNavigateToTarget(AudioQualityTarget.DOWNLOADS) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1:1 Apple Music Audio Quality Selection Sub-Screen.
 *
 * Displays quality tiers (High Efficiency, High Quality, Lossless, Hi-Res Lossless, Dolby Atmos)
 * with theme checkmark, data consumption footnotes, and "About Lossless Audio" sheet.
 */
@Composable
fun AppleAudioQualityPickerScreen(
    target: AudioQualityTarget,
    preferLossless: Boolean,
    selectedQualityId: Int,
    dolbyAtmosMode: String = "Automatic",
    onSelectTier: (AppleAudioQualityTier) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showAboutDialog by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val atmosDisabled = dolbyAtmosMode.equals("Off", ignoreCase = true)
    val effectiveSelectedId = when {
        target == AudioQualityTarget.DOWNLOADS -> if (atmosDisabled && selectedQualityId == LosslessMusicApi.QUALITY_DOLBY_ATMOS) LosslessMusicApi.QUALITY_MAX_HI_RES else selectedQualityId
        !preferLossless && target == AudioQualityTarget.CELLULAR_STREAMING ->
            if (selectedQualityId == LosslessMusicApi.QUALITY_DATA_SAVER) LosslessMusicApi.QUALITY_DATA_SAVER else LosslessMusicApi.QUALITY_MP3_320
        !preferLossless && target == AudioQualityTarget.WIFI_STREAMING -> LosslessMusicApi.QUALITY_MP3_320
        atmosDisabled && selectedQualityId == LosslessMusicApi.QUALITY_DOLBY_ATMOS -> LosslessMusicApi.QUALITY_MAX_HI_RES
        else -> selectedQualityId
    }
    val currentTier = AppleAudioQualityTier.fromQualityId(effectiveSelectedId)
    val listState = rememberLazyListState()

    // Determine available tiers based on target, lossless switch, and Dolby Atmos mode
    val tiers = remember(target, preferLossless, atmosDisabled) {
        when (target) {
            AudioQualityTarget.CELLULAR_STREAMING -> {
                if (preferLossless) {
                    buildList {
                        add(AppleAudioQualityTier.HIGH_EFFICIENCY)
                        add(AppleAudioQualityTier.HIGH_QUALITY)
                        add(AppleAudioQualityTier.LOSSLESS)
                        add(AppleAudioQualityTier.HI_RES_LOSSLESS)
                        if (!atmosDisabled) add(AppleAudioQualityTier.DOLBY_ATMOS)
                    }
                } else {
                    listOf(
                        AppleAudioQualityTier.HIGH_EFFICIENCY,
                        AppleAudioQualityTier.HIGH_QUALITY,
                    )
                }
            }
            AudioQualityTarget.WIFI_STREAMING -> {
                if (preferLossless) {
                    buildList {
                        add(AppleAudioQualityTier.HIGH_QUALITY)
                        add(AppleAudioQualityTier.LOSSLESS)
                        add(AppleAudioQualityTier.HI_RES_LOSSLESS)
                        if (!atmosDisabled) add(AppleAudioQualityTier.DOLBY_ATMOS)
                    }
                } else {
                    listOf(AppleAudioQualityTier.HIGH_QUALITY)
                }
            }
            AudioQualityTarget.DOWNLOADS -> {
                buildList {
                    add(AppleAudioQualityTier.HIGH_QUALITY)
                    add(AppleAudioQualityTier.LOSSLESS)
                    add(AppleAudioQualityTier.HI_RES_LOSSLESS)
                    if (!atmosDisabled) add(AppleAudioQualityTier.DOLBY_ATMOS)
                }
            }
        }
    }

    SettingsGlassScaffold(
        title = target.title,
        subtitle = "Choose your preferred audio quality",
        onBack = onBack,
        listState = listState,
        modifier = modifier,
    ) { contentPadding, _ ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Group of Quality Options
            item(key = "quality_tiers_group") {
                SettingsGlassSection(
                    title = "Available Tiers",
                    subtitle = "Select resolution profile for ${target.title}",
                ) {
                    ExpressiveGroup(rowCount = tiers.size) { index, position ->
                        val tier = tiers[index]
                        val isSelected = currentTier == tier
                        val shape = groupShape(position)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassSurface(
                                    shape = shape,
                                    accentTint = if (isSelected) primary.copy(alpha = 0.22f) else Color.Unspecified,
                                )
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onSelectTier(tier)
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tier.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${tier.subtitle} • ${tier.technicalDetail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Explanatory Footnote Note
            item(key = "data_consumption_footnote") {
                val footnoteText = if (target == AudioQualityTarget.DOWNLOADS) {
                    "Lossless audio files require significantly more disk space.\n\n10 GB of offline storage holds approximately:\n• 3,000 songs at High Quality\n• 1,000 songs with Lossless\n• 200 songs with Hi-Res Lossless"
                } else {
                    "Lossless streaming consumes significantly more cellular and Wi-Fi bandwidth.\n\nA typical 3-minute song is approximately:\n• 1.5 MB with High Efficiency\n• 6 MB with High Quality (256 kbps)\n• 36 MB with Lossless (24-bit/48 kHz)\n• 145 MB with Hi-Res Lossless (24-bit/192 kHz)\n\nPlayback depends on track availability, network conditions, and connected DAC/headphone capabilities."
                }

                SettingsGlassSection(
                    title = "Data & Storage Guidance",
                    trailingBadge = {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text(
                        text = footnoteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            // "About Lossless Audio" Link Card
            item(key = "about_lossless_link") {
                val shape = remember { SquircleShape(20.dp) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(shape = shape)
                        .clickable { showAboutDialog = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupIconBadge(
                            icon = Icons.Filled.Info,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "About Lossless Audio & Hardware DACs",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutLosslessAudioDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun LiquidQualityActionRow(
    title: String,
    subtitle: String,
    technicalDetail: String,
    position: GroupPosition,
    onClick: () -> Unit,
) {
    val shape = groupShape(position)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(shape = shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$subtitle • $technicalDetail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Liquid Glass Inset Modal Dialog explaining Lossless Audio.
 */
@Composable
fun AboutLosslessAudioDialog(
    onDismiss: () -> Unit,
) {
    com.neonsong.app.ui.common.NeonPopupCardDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Text(
                text = "About Lossless Audio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Neon Song streams audio in pristine bit-perfect resolutions ranging from 16-bit/44.1 kHz (CD Quality) up to 24-bit/192 kHz Studio Master FLAC and ALAC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    lineHeight = 20.sp,
                )
                Text(
                    text = "Key Audiophile Considerations:\n• Streaming lossless over cellular consumes substantially more data.\n• Offline downloads require larger device storage.\n• Hi-Res Lossless (above 24-bit/48 kHz) requires a compatible USB DAC or audiophile output hardware.\n• Standard Bluetooth codecs compress audio; wired listening is recommended for true bit-perfect playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
