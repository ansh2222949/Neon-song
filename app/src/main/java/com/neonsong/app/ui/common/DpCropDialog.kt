package com.neonsong.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neonsong.app.util.LocalHapticManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive Profile Picture (DP) Cropper dialog.
 * Enables pan, pinch-to-zoom, 90° rotation, and real-time circular framing preview
 * before saving a high-resolution cropped avatar.
 */
@Composable
fun DpCropDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onCropSuccess: (File) -> Unit,
) {
    val context = LocalContext.current
    val hapticManager = LocalHapticManager.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    // Load and downsample image safely from content resolver
    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                val maxDimension = max(options.outWidth, options.outHeight)
                var sampleSize = 1
                while (maxDimension / sampleSize > 2048) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val loaded = context.contentResolver.openInputStream(sourceUri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
                sourceBitmap = loaded
            } catch (_: Exception) {
                sourceBitmap = null
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0A0C),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Top Bar ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            hapticManager.click()
                            onDismiss()
                        },
                        enabled = !isSaving,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Text(
                        text = "Crop Profile Picture",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    Button(
                        onClick = {
                            val bitmap = sourceBitmap ?: return@Button
                            if (isSaving) return@Button
                            isSaving = true
                            hapticManager.click()
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val cropped = performCrop(
                                        src = bitmap,
                                        scale = scale,
                                        pan = panOffset,
                                        rotation = rotationDegrees,
                                    )
                                    val destFile = File(context.filesDir, "custom_profile_dp.jpg")
                                    FileOutputStream(destFile).use { out ->
                                        cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    withContext(Dispatchers.Main) {
                                        onCropSuccess(destFile)
                                    }
                                } catch (_: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && sourceBitmap != null,
                        shape = RoundedCornerShape(100),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (isSaving) {
                            Text("Saving...")
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save DP", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Interactive Crop Canvas ──
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val density = LocalDensity.current
                    val viewportSizePx = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) * 0.78f }
                    val cropCircleRadius = viewportSizePx / 2f

                    val currentBitmap = sourceBitmap
                    if (currentBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                                        panOffset = Offset(
                                            x = panOffset.x + pan.x,
                                            y = panOffset.y + pan.y,
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val imageBitmap = remember(currentBitmap, rotationDegrees) {
                                if (rotationDegrees == 0) {
                                    currentBitmap.asImageBitmap()
                                } else {
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    Bitmap.createBitmap(
                                        currentBitmap, 0, 0,
                                        currentBitmap.width, currentBitmap.height,
                                        matrix, true
                                    ).asImageBitmap()
                                }
                            }

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasCenter = center

                                // 1. Draw Image with user pan & zoom
                                val baseScale = max(
                                    viewportSizePx / imageBitmap.width.toFloat(),
                                    viewportSizePx / imageBitmap.height.toFloat(),
                                )
                                val effectiveScale = baseScale * scale
                                val dstWidth = (imageBitmap.width * effectiveScale).roundToInt()
                                val dstHeight = (imageBitmap.height * effectiveScale).roundToInt()

                                val dstLeft = (canvasCenter.x - dstWidth / 2f + panOffset.x).roundToInt()
                                val dstTop = (canvasCenter.y - dstHeight / 2f + panOffset.y).roundToInt()

                                drawImage(
                                    image = imageBitmap,
                                    dstOffset = IntOffset(dstLeft, dstTop),
                                    dstSize = IntSize(dstWidth, dstHeight),
                                )

                                // 2. Darkened Mask around circular viewport
                                val circlePath = Path().apply {
                                    addOval(
                                        Rect(
                                            center = canvasCenter,
                                            radius = cropCircleRadius,
                                        )
                                    )
                                }

                                clipPath(circlePath, clipOp = ClipOp.Difference) {
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.72f),
                                        size = size,
                                    )
                                }

                                // 3. Guide outline circle
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    radius = cropCircleRadius,
                                    center = canvasCenter,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                    } else if (isLoading) {
                        ExpressiveLoadingIndicator(message = "Preparing photo for crop...")
                    } else {
                        Text("Could not load selected image", color = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Controls (Zoom slider, Rotate, Reset) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.ZoomOut,
                            contentDescription = "Zoom out",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                        Slider(
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.8f..3.5f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                        )
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = "Zoom in",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                hapticManager.click()
                                rotationDegrees = (rotationDegrees + 90) % 360
                            },
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
                        }

                        Spacer(Modifier.width(20.dp))

                        FilledTonalIconButton(
                            onClick = {
                                hapticManager.click()
                                scale = 1f
                                panOffset = Offset.Zero
                                rotationDegrees = 0
                            },
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset positioning")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extracts a high-res square Bitmap matching the circular crop preview.
 */
private fun performCrop(
    src: Bitmap,
    scale: Float,
    pan: Offset,
    rotation: Int,
): Bitmap {
    val rotatedSrc = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    } else {
        src
    }

    val targetSize = 640
    val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)

    val baseScale = max(
        targetSize / rotatedSrc.width.toFloat(),
        targetSize / rotatedSrc.height.toFloat(),
    )
    val finalScale = baseScale * scale

    val matrix = Matrix().apply {
        postScale(finalScale, finalScale)
        // Adjust for pan relative to target size scale
        val panRatio = targetSize / 300f // Normalized viewport ratio
        val dx = (targetSize - rotatedSrc.width * finalScale) / 2f + pan.x * panRatio
        val dy = (targetSize - rotatedSrc.height * finalScale) / 2f + pan.y * panRatio
        postTranslate(dx, dy)
    }

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    canvas.drawBitmap(rotatedSrc, matrix, paint)

    return output
}
