package com.neonsong.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
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
import com.neonsong.app.data.lossless.LosslessMusicApi

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

private val AppleOledCanvas = Color(0xFF000000)
private val AppleGroupBackground = Color(0xFF1C1C1E)
private val AppleGroupRadius = RoundedCornerShape(12.dp)
private val AppleDividerColor = Color.White.copy(alpha = 0.08f)
private val AppleSecondaryText = Color(0xFF8E8E93)
private val AppleSystemRed = Color(0xFFFA2D48)
private val AppleSystemGreen = Color(0xFF34C759)

/**
 * 1:1 Apple Music Audio Quality Settings Screen.
 *
 * Recreates the exact Apple Music "Audio Quality" view:
 * 1. Lossless Audio master toggle + informational caption
 * 2. Inset Grouped card for Cellular Streaming, Wi-Fi Streaming, and Downloads
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledCanvas)
            .statusBarsPadding(),
    ) {
        // Apple Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = "Audio Quality",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: Lossless Audio Switch Card
            item {
                Column {
                    Surface(
                        shape = AppleGroupRadius,
                        color = AppleGroupBackground,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Lossless Audio",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = preferLossless,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onToggleLossless(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleSystemGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF39393D),
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Lossless files preserve every detail of the original audio. Turning this on will consume significantly more data.",
                        color = AppleSecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // Section 2: Dolby Atmos / Spatial Audio Card
            item {
                Column {
                    Text(
                        text = "DOLBY ATMOS",
                        color = AppleSecondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                    )

                    Surface(
                        shape = AppleGroupRadius,
                        color = AppleGroupBackground,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            val options = listOf("Automatic", "Always On", "Off")
                            options.forEachIndexed { idx, option ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = AppleDividerColor,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                }
                                val isSelected = dolbyAtmosMode.equals(option, ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            onSelectDolbyAtmos(option)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = option,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = AppleSystemRed,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Plays supported tracks in Dolby Atmos spatial audio with multidimensional sound clarity and enhanced soundstage.",
                        color = AppleSecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // Section 3: Audio Quality Destinations (Cellular, Wi-Fi, Downloads)
            item {
                Surface(
                    shape = AppleGroupRadius,
                    color = AppleGroupBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // Cellular Streaming Row
                        val cellularTier = AppleAudioQualityTier.fromQualityId(cellularQuality)
                        AppleQualityActionRow(
                            title = "Cellular Streaming",
                            subtitle = cellularTier.title,
                            onClick = { onNavigateToTarget(AudioQualityTarget.CELLULAR_STREAMING) },
                        )

                        // Wi-Fi and Downloads appear when Lossless is enabled (identical to Apple Music)
                        if (preferLossless) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppleDividerColor,
                                modifier = Modifier.padding(start = 16.dp),
                            )

                            val wifiTier = AppleAudioQualityTier.fromQualityId(wifiQuality)
                            AppleQualityActionRow(
                                title = "Wi-Fi Streaming",
                                subtitle = wifiTier.title,
                                onClick = { onNavigateToTarget(AudioQualityTarget.WIFI_STREAMING) },
                            )

                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = AppleDividerColor,
                                modifier = Modifier.padding(start = 16.dp),
                            )

                            val downloadTier = AppleAudioQualityTier.fromQualityId(downloadQuality)
                            AppleQualityActionRow(
                                title = "Downloads",
                                subtitle = downloadTier.title,
                                onClick = { onNavigateToTarget(AudioQualityTarget.DOWNLOADS) },
                            )
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
 * Displays quality tiers (High Efficiency, High Quality, Lossless, Hi-Res Lossless)
 * with red checkmark, data consumption footnotes, and "About Lossless Audio" sheet.
 */
@Composable
fun AppleAudioQualityPickerScreen(
    target: AudioQualityTarget,
    preferLossless: Boolean,
    selectedQualityId: Int,
    onSelectTier: (AppleAudioQualityTier) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showAboutDialog by remember { mutableStateOf(false) }

    val currentTier = AppleAudioQualityTier.fromQualityId(selectedQualityId)

    // Determine available tiers based on target and lossless switch
    val tiers = remember(target, preferLossless) {
        when (target) {
            AudioQualityTarget.CELLULAR_STREAMING -> {
                if (preferLossless) {
                    listOf(
                        AppleAudioQualityTier.HIGH_EFFICIENCY,
                        AppleAudioQualityTier.HIGH_QUALITY,
                        AppleAudioQualityTier.LOSSLESS,
                        AppleAudioQualityTier.HI_RES_LOSSLESS,
                        AppleAudioQualityTier.DOLBY_ATMOS,
                    )
                } else {
                    listOf(
                        AppleAudioQualityTier.HIGH_EFFICIENCY,
                        AppleAudioQualityTier.HIGH_QUALITY,
                    )
                }
            }
            AudioQualityTarget.WIFI_STREAMING -> {
                if (preferLossless) {
                    listOf(
                        AppleAudioQualityTier.HIGH_QUALITY,
                        AppleAudioQualityTier.LOSSLESS,
                        AppleAudioQualityTier.HI_RES_LOSSLESS,
                        AppleAudioQualityTier.DOLBY_ATMOS,
                    )
                } else {
                    listOf(AppleAudioQualityTier.HIGH_QUALITY)
                }
            }
            AudioQualityTarget.DOWNLOADS -> {
                if (preferLossless) {
                    listOf(
                        AppleAudioQualityTier.HIGH_QUALITY,
                        AppleAudioQualityTier.LOSSLESS,
                        AppleAudioQualityTier.HI_RES_LOSSLESS,
                        AppleAudioQualityTier.DOLBY_ATMOS,
                    )
                } else {
                    listOf(AppleAudioQualityTier.HIGH_QUALITY)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledCanvas)
            .statusBarsPadding(),
    ) {
        // Apple Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = target.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Group of Quality Options
            item {
                Surface(
                    shape = AppleGroupRadius,
                    color = AppleGroupBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        tiers.forEachIndexed { index, tier ->
                            val isSelected = currentTier == tier
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onSelectTier(tier)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tier.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tier.subtitle,
                                        color = AppleSecondaryText,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = AppleSystemRed,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            if (index < tiers.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = AppleDividerColor,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Explanatory Footnote Note
            item {
                val footnoteText = if (target == AudioQualityTarget.DOWNLOADS) {
                    "Lossless files will use significantly more space on your device.\n\n10 GB of space could store approximately:\n– 3000 songs at High Quality\n– 1000 songs with Lossless\n– 200 songs with Hi-Res Lossless"
                } else {
                    "Lossless streaming will consume significantly more data.\n\nA 3-minute song will be approximately:\n• 1.5 MB with High Efficiency\n• 6 MB with High Quality at 256 kbps\n• 36 MB with Lossless at 24-bit/48 kHz\n• 145 MB with Hi-Res Lossless at 24-bit/192 kHz\n\nSupport varies and depends on song availability, network conditions, and connected speaker or headphone capability."
                }

                Text(
                    text = footnoteText,
                    color = AppleSecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // "About Lossless Audio" Link Card
            item {
                Surface(
                    onClick = { showAboutDialog = true },
                    shape = AppleGroupRadius,
                    color = AppleGroupBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "About Lossless Audio",
                            color = AppleSystemRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = AppleSecondaryText,
                            modifier = Modifier.size(16.dp),
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
private fun AppleQualityActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppleSecondaryText,
                fontSize = 13.sp,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppleSecondaryText,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Apple Music Inset Modal Dialog explaining Lossless Audio.
 */
@Composable
fun AboutLosslessAudioDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppleGroupBackground,
        shape = AppleGroupRadius,
        title = {
            Text(
                text = "About Lossless Audio",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Apple developed its own lossless audio compression technology called Apple Lossless Audio Codec (ALAC). In addition to AAC, the catalog is encoded using ALAC and studio FLAC in resolutions ranging from 16-bit/44.1 kHz (CD Quality) up to 24-bit/192 kHz.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Text(
                    text = "What you need to know about Lossless:\n• Streaming lossless audio over cellular or Wi-Fi consumes significantly more data.\n• Downloading lossless audio uses significantly more storage on your device.\n• Hi-Res Lossless (above 24-bit/48 kHz) requires external equipment such as a USB digital-to-analog converter (DAC).\n• Bluetooth connections do not support lossless audio.",
                    color = AppleSecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Done",
                    color = AppleSystemRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        },
    )
}
