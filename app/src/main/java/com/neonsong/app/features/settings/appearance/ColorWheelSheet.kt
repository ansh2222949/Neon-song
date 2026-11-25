package com.neonsong.app.features.settings.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.neonsong.app.ui.theme.GlassActionButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.neonsong.app.ui.theme.GlassOutlinedActionButton as OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.common.EdgeToEdgeDialogWindow
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.theme.ExpressivePillShape
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalLiquidGlassOverlayBackdrop
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassChrome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorWheelSheet(
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var hue by remember { mutableFloatStateOf(4f) }
    var saturation by remember { mutableFloatStateOf(0.75f) }
    var lightness by remember { mutableFloatStateOf(0.5f) }
    val previewColor = Color.hsl(hue, saturation, lightness)

    val sheetShape = remember {
        SquircleShape(
            topStart = CornerSize(28.dp),
            topEnd = CornerSize(28.dp),
            bottomEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.liquidGlassChrome(
            shape = sheetShape,
            enabled = true,
            preset = LiquidGlassPreset.ModalSheet,
            backdrop = LocalLiquidGlassOverlayBackdrop.current,
        ),
        shape = sheetShape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.42f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
    ) {
        EdgeToEdgeDialogWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 560.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 20.dp + safeDrawingBottomPadding()),
        ) {
            Text("Custom Color", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            val previewShape = remember { SquircleShape(24.dp) }
            Surface(
                shape = previewShape,
                color = previewColor,
                shadowElevation = 12.dp,
                border = BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.20f))
                    ),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(120f, 30f),
                                radius = 260f,
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "#%02X%02X%02X".format(
                            (previewColor.red * 255).toInt(),
                            (previewColor.green * 255).toInt(),
                            (previewColor.blue * 255).toInt(),
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (lightness > 0.62f) Color.Black.copy(alpha = 0.85f) else Color.White,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Hue", style = MaterialTheme.typography.labelLarge)
            Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
            Text("Saturation", style = MaterialTheme.typography.labelLarge)
            Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
            Text("Lightness", style = MaterialTheme.typography.labelLarge)
            Slider(value = lightness, onValueChange = { lightness = it }, valueRange = 0.15f..0.85f)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = ExpressivePillShape,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApply(previewColor) },
                    shape = ExpressivePillShape,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Apply")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

