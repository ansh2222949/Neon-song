package com.neonsong.app.features.settings.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import com.neonsong.app.ui.theme.LiquidGlassChip
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.neonsong.app.ui.common.ExpressiveMotion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.data.local.EQ_BAND_FREQS_HZ
import com.neonsong.app.data.local.EQ_MAX_GAIN_DB
import com.neonsong.app.data.local.EqualizerPresets
import com.neonsong.app.data.local.EqualizerSettings
import com.neonsong.app.data.local.eqBandLabel
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.settings.SettingsGlassScaffold
import com.neonsong.app.ui.settings.SettingsGlassSection
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EQ_MAX_DB = EQ_MAX_GAIN_DB

private val EqBadgeShape = SquircleShape(9.dp)
private val EqTrackShape = SquircleShape(22.dp)
private val EqThumbShape = SquircleShape(11.dp)
private val EqFillShape = RoundedCornerShape(6.dp)

@Composable
fun EqualizerSheet(
    eq: EqualizerSettings,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onPickPreset: (String) -> Unit,
    onBandPreview: (Int, Float) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onOpenAutoEq: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    val gains = remember {
        mutableStateListOf<Float>().apply {
            EQ_BAND_FREQS_HZ.indices.forEach { index ->
                add(
                    eq.gainsDb.getOrNull(index)
                        ?.takeIf { it.isFinite() }
                        ?.coerceIn(-EQ_MAX_DB, EQ_MAX_DB)
                        ?: 0f,
                )
            }
        }
    }
    LaunchedEffect(eq.gainsDb) {
        EQ_BAND_FREQS_HZ.indices.forEach { index ->
            val v = eq.gainsDb.getOrNull(index)
                ?.takeIf { it.isFinite() }
                ?.coerceIn(-EQ_MAX_DB, EQ_MAX_DB)
                ?: 0f
            if (gains[index] != v) gains[index] = v
        }
    }

    val listState = rememberLazyListState()

    SettingsGlassScaffold(
        title = "Equalizer",
        subtitle = "16-band studio tuning",
        onBack = onDismiss,
        listState = listState,
        actions = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPickPreset(EqualizerPresets.FLAT.name)
                },
                enabled = eq.enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Reset", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { contentPadding, _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsGlassSection(
                title = "Equalizer engine",
                subtitle = "Apply the 16-band profile to active playback",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (eq.enabled) "Equalizer on" else "Equalizer off",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = eq.enabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSetEnabled(enabled)
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

            SettingsGlassSection(title = "Frequency response", subtitle = "Live preview of the current EQ curve") {
                EqualizerCurveGraph(
                    bandCount = EQ_BAND_FREQS_HZ.size,
                    gainAt = { gains[it] },
                    enabled = eq.enabled,
                )
            }

            SettingsGlassSection(title = "Presets", subtitle = "Choose a starting point, then fine-tune any band") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    EqualizerPresets.GENRE_PRESETS.forEach { preset ->
                        val isSelected = eq.presetName.equals(preset.name, ignoreCase = true)
                        LiquidGlassChip(
                            selected = isSelected && eq.enabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPickPreset(preset.name)
                            },
                            shape = CircleShape,
                            accentTint = MaterialTheme.colorScheme.primary,
                            label = {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected && eq.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                        )
                    }
                    if (eq.presetName == EqualizerPresets.CUSTOM_NAME) {
                        LiquidGlassChip(
                            selected = eq.enabled,
                            onClick = {},
                            shape = CircleShape,
                            accentTint = MaterialTheme.colorScheme.primary,
                            label = {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (eq.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                        )
                    }
                }
            }

            SettingsGlassSection(title = "Headphone correction", subtitle = "Load a measured compensation profile for your headphones") {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenAutoEq()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Auto EQ")
                }
            }

            val boardAlpha = if (eq.enabled) 1f else 0.40f
            SettingsGlassSection(title = "Band controls", subtitle = "Drag a slider or double-tap it to reset that band") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(boardAlpha),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "+8 dB (Boost)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "-8 dB (Cut)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EQ_BAND_FREQS_HZ.forEachIndexed { index, hz ->
                            EqBandSlot(
                                index = index,
                                hz = hz,
                                enabled = eq.enabled,
                                gains = gains,
                                onBandPreview = onBandPreview,
                                onBandChange = onBandChange,
                            )
                        }
                    }
                }
            }

            Text(
                text = when {
                    !eq.enabled -> "Turn on to apply equalization live"
                    eq.presetName == EqualizerPresets.CUSTOM_NAME -> "Custom profile \u2022 tap or drag any bar"
                    else -> "${eq.presetName} preset active \u2022 tap or drag any bar to customize"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EqBandSlot(
    index: Int,
    hz: Int,
    enabled: Boolean,
    gains: SnapshotStateList<Float>,
    onBandPreview: (Int, Float) -> Unit,
    onBandChange: (Int, Float) -> Unit,
) {
    EqNativeSlider(
        gainDb = gains[index],
        hz = hz,
        enabled = enabled,
        onGainChange = { value ->
            gains[index] = value
            onBandPreview(index, value)
        },
        onChangeFinished = {
            onBandChange(index, gains[index])
        },
    )
}

@Composable
fun EqNativeSlider(
    gainDb: Float,
    hz: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onGainChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.outline
    val normalized = ((gainDb + EQ_MAX_DB) / (EQ_MAX_DB * 2f)).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    val thumbScaleState = animateFloatAsState(
        targetValue = if (isDragging) 1.08f else 1.0f,
        animationSpec = if (isDragging) ExpressiveMotion.FloatPressSpring else ExpressiveMotion.FloatReleaseSpring,
        label = "eqThumbScale",
    )
    val boostBrush = remember(primary) { Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.60f))) }
    val cutBrush = remember(secondary) { Brush.verticalGradient(listOf(secondary.copy(alpha = 0.60f), secondary)) }
    val density = LocalDensity.current
    val draggingShadowPx = with(density) { 6.dp.toPx() }
    val idleShadowPx = with(density) { 2.dp.toPx() }
    val currentGain by rememberUpdatedState(gainDb)
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val currentOnChangeFinished by rememberUpdatedState(onChangeFinished)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = EqBadgeShape,
            color = when {
                !enabled -> surfaceContainer.copy(alpha = 0.55f)
                gainDb > 0f -> primary.copy(alpha = 0.22f)
                gainDb < 0f -> secondary.copy(alpha = 0.22f)
                else -> surfaceContainer.copy(alpha = 0.72f)
            },
        ) {
            Text(
                text = if (gainDb > 0f) "+${"%.1f".format(gainDb)}" else "${"%.1f".format(gainDb)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (gainDb != 0f) FontWeight.Bold else FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = when {
                    !enabled -> onSurfaceVariant.copy(alpha = 0.55f)
                    gainDb > 0f -> primary
                    gainDb < 0f -> secondary
                    else -> onSurface
                },
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        val trackHeight = 152.dp
        val thumbHeight = 22.dp
        val thumbWidth = 36.dp

        Box(
            modifier = Modifier
                .width(44.dp)
                .height(trackHeight)
                .liquidGlassSurface(
                    shape = EqTrackShape,
                    borderWidth = 0.8.dp,
                    accentTint = if (gainDb != 0f && enabled) primary else Color.Unspecified,
                    alphaScale = 0.88f,
                )
                .clip(EqTrackShape)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnGainChange(0f)
                            currentOnChangeFinished()
                        },
                        onTap = { offset ->
                            val tappedFraction = (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                            val rawGain = tappedFraction * EQ_MAX_DB * 2f - EQ_MAX_DB
                            val snapped = if (rawGain in -0.35f..0.35f) 0f else (rawGain * 2f).roundToInt() / 2f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnGainChange(snapped)
                            currentOnChangeFinished()
                        },
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            currentOnChangeFinished()
                        },
                        onDragCancel = {
                            isDragging = false
                            currentOnChangeFinished()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaFraction = -dragAmount / size.height.toFloat()
                        val currentFraction = ((currentGain + EQ_MAX_DB) / (EQ_MAX_DB * 2f))
                        val newFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
                        val newGain = (newFraction * EQ_MAX_DB * 2f - EQ_MAX_DB).let {
                            if (it in -0.3f..0.3f) 0f else (it * 2f).roundToInt() / 2f
                        }
                        if (newGain != currentGain) {
                            if (newGain == 0f && currentGain != 0f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            currentOnGainChange(newGain)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(outline.copy(alpha = 0.55f)),
            )

            val baselineFraction = 0.5f
            val topFraction = if (normalized >= baselineFraction) 1f - normalized else 1f - baselineFraction
            val heightFraction = abs(normalized - baselineFraction)

            if (heightFraction > 0.01f && enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.50f)
                        .fillMaxHeight(heightFraction)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            translationY = size.height * topFraction
                        }
                        .clip(EqFillShape)
                        .background(if (gainDb > 0f) boostBrush else cutBrush),
                )
            }

            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val maxTravel = (trackHeight - thumbHeight).toPx()
                        val s = thumbScaleState.value
                        translationY = maxTravel * (1f - normalized)
                        scaleX = s
                        scaleY = s
                        shadowElevation = if (isDragging) draggingShadowPx else idleShadowPx
                        shape = EqThumbShape
                        clip = true
                    }
                    .background(if (enabled) onSurface else outline),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (enabled) primary else onSurfaceVariant.copy(alpha = 0.45f)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = eqBandLabel(hz),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (gainDb != 0f && enabled) primary else onSurface,
            maxLines = 1,
        )
        Text(
            text = eqBandCategory(hz),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurfaceVariant,
            maxLines = 1,
        )
    }
}
