package com.neonsong.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonsong.app.R
import com.neonsong.app.playback.PathCheck
import com.neonsong.app.playback.SignalPathReport
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.liquidGlassChrome
import java.util.Locale

/**
 * Detailed audiophile signal-path popup opened from the quality badge (FLAC 24/96…).
 * Visual vertical connected node graph: Source -> Engine/DSP -> Hardware Output.
 * High-precision live telemetry with tabular monospace metrics.
 */
@Composable
fun SignalPathDialog(
    report: SignalPathReport,
    needsUsbPermission: Boolean,
    onRequestUsbAccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val glass = LocalLiquidGlass.current
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = if (glass) 0.dp else 6.dp,
            modifier = Modifier.liquidGlassChrome(
                RoundedCornerShape(24.dp),
                glass,
                LiquidGlassPreset.ModalSheet,
                LocalLiquidGlassOverlayBackdrop.current,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // Header Row: Title & Verdict Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.signal_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    VerdictPill(report = report)
                }

                Text(
                    verdictText(report),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        report.bitPerfect -> MaterialTheme.colorScheme.primary
                        report.clockFallbackResampled -> Color(0xFFFFB74D)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(16.dp))

                // Partition checks into 3 logical pipeline stages
                val sourceChecks = report.checks.filter { it.labelRes == R.string.signal_label_source }
                val engineChecks = report.checks.filter {
                    it.labelRes in listOf(
                        R.string.signal_label_resampler,
                        R.string.signal_label_dsp,
                        R.string.signal_label_tempo,
                        R.string.signal_label_appvol,
                        R.string.signal_label_sysvol,
                    )
                }
                val outputChecks = report.checks.filter {
                    it !in sourceChecks && it !in engineChecks
                }

                val sourcePassed = sourceChecks.all { it.passed }
                val enginePassed = engineChecks.all { it.passed }
                val outputPassed = outputChecks.all { it.passed }

                // Stage 1: Source Node
                val sourceSub = buildString {
                    append(report.sourceLabel)
                    report.sourceRateHz?.let { append(" • $it Hz") }
                }
                PipelineNodeCard(
                    nodeNumber = 1,
                    title = "SOURCE DECODER",
                    subtitle = sourceSub,
                    icon = Icons.Filled.MusicNote,
                    checks = sourceChecks,
                    isPassed = sourcePassed,
                )

                // Connector 1 -> 2
                PipelineConnector(
                    upstreamPassed = sourcePassed,
                    downstreamPassed = enginePassed,
                )

                // Stage 2: Audio Engine & DSP
                val engineSub = when {
                    enginePassed -> "Native Pipeline Direct • Bit-Accurate"
                    report.clockFallbackResampled -> "SOXR Resampling • Clock Fallback"
                    else -> "DSP / Gain Staged"
                }
                PipelineNodeCard(
                    nodeNumber = 2,
                    title = "AUDIO ENGINE & DSP",
                    subtitle = engineSub,
                    icon = Icons.Filled.Tune,
                    checks = engineChecks,
                    isPassed = enginePassed,
                    isWarning = !enginePassed && report.clockFallbackResampled,
                )

                // Connector 2 -> 3
                PipelineConnector(
                    upstreamPassed = enginePassed,
                    downstreamPassed = outputPassed,
                )

                // Stage 3: Hardware Output
                val outputSub = buildString {
                    append(report.dacName ?: stringResource(R.string.signal_shared_track))
                    if (report.platformRateHz > 0) append(" • ${report.platformRateHz} Hz")
                }
                PipelineNodeCard(
                    nodeNumber = 3,
                    title = "HARDWARE OUTPUT",
                    subtitle = outputSub,
                    icon = if (report.dacName != null || report.usbExclusiveActive) Icons.Filled.Usb else Icons.Filled.GraphicEq,
                    checks = outputChecks,
                    isPassed = outputPassed,
                )

                Spacer(Modifier.height(18.dp))

                // Telemetry Section
                Text(
                    stringResource(R.string.signal_health),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))

                // Side-by-side Telemetry Cards
                val measuring = stringResource(R.string.signal_measuring)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val driftVal = report.driftPpm?.let { String.format(Locale.ROOT, "%+.1f PPM", it) }
                        ?: if (report.isPlaying) measuring else "—"
                    val driftColor = when {
                        report.driftPpm == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        kotlin.math.abs(report.driftPpm) < 15.0 -> MaterialTheme.colorScheme.primary
                        kotlin.math.abs(report.driftPpm) < 50.0 -> Color(0xFFFFB74D)
                        else -> MaterialTheme.colorScheme.error
                    }
                    TelemetryCard(
                        title = stringResource(R.string.signal_drift),
                        value = driftVal,
                        statusColor = driftColor,
                        modifier = Modifier.weight(1f),
                    )

                    val glitchColor = if (report.glitchCount == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    TelemetryCard(
                        title = stringResource(R.string.signal_glitches),
                        value = report.glitchCount.toString(),
                        statusColor = glitchColor,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Stream details card
                val streamState = if (report.isPlaying) {
                    stringResource(R.string.signal_playing)
                } else {
                    stringResource(R.string.signal_idle)
                }
                val routeTrack = if (report.usbExclusiveActive || report.bitPerfect) {
                    stringResource(R.string.signal_direct_track)
                } else {
                    stringResource(R.string.signal_shared_track)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.signal_stream),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = buildString {
                                append(streamState)
                                if (report.appRateHz > 0) append(" • ${report.appRateHz} Hz")
                                append(" • $routeTrack")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    }
                }

                if (needsUsbPermission) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onRequestUsbAccess,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.signal_grant_usb))
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineNodeCard(
    nodeNumber: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    checks: List<PathCheck>,
    isPassed: Boolean,
    isWarning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val statusColor = when {
        isPassed -> MaterialTheme.colorScheme.primary
        isWarning -> Color(0xFFFFB74D)
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.50f),
        border = BorderStroke(
            1.dp,
            statusColor.copy(alpha = if (isPassed) 0.30f else 0.45f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(statusColor.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = statusColor,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$nodeNumber. $title",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // LED Status Ring / Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape),
                )
            }

            if (checks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    checks.forEach { check ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(6.dp)
                                    .background(
                                        if (check.passed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        CircleShape,
                                    ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(check.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(check.detailRes, *check.detailArgs.toTypedArray()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineConnector(
    upstreamPassed: Boolean,
    downstreamPassed: Boolean,
    modifier: Modifier = Modifier,
) {
    val topColor = if (upstreamPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val bottomColor = if (downstreamPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(18.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(topColor, bottomColor)),
                    shape = RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    value: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                color = statusColor,
                maxLines = 1,
            )
        }
    }
}

/** Verdict line: confirmation when perfect, otherwise the first failing check. */
@Composable
private fun verdictText(report: SignalPathReport): String {
    if (report.bitPerfect) return stringResource(R.string.signal_verdict_ok)
    val failing = report.checks.firstOrNull { !it.passed } ?: return ""
    return stringResource(failing.labelRes) +
        ": " +
        stringResource(failing.detailRes, *failing.detailArgs.toTypedArray())
}

@Composable
private fun VerdictPill(report: SignalPathReport) {
    val bg = when {
        report.bitPerfect -> MaterialTheme.colorScheme.primary
        report.clockFallbackResampled -> Color(0xFFE65100).copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = when {
        report.bitPerfect -> MaterialTheme.colorScheme.onPrimary
        report.clockFallbackResampled -> Color(0xFFFFB74D)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when {
        report.bitPerfect -> stringResource(R.string.signal_bit_perfect)
        report.clockFallbackResampled -> stringResource(R.string.signal_resampled_dac)
        else -> stringResource(R.string.signal_check_path)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
        )
    }
}
