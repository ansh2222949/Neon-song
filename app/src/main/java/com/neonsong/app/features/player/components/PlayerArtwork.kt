package com.neonsong.app.features.player.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.canvas.CanvasArtwork
import com.neonsong.app.ui.player.CanvasContentMode
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.player.CanvasArtworkPlayer
import kotlin.math.abs

@Composable
fun PlayerArtwork(
    track: PlayableTrack,
    modifier: Modifier,
    corner: Dp,
    decodeSizePx: Int? = null,
    canvas: CanvasArtwork? = null,
    isPlaying: Boolean = false,
    pausedForTransition: Boolean = false,
    transformations: List<coil.transform.Transformation> = emptyList(),
    onAspectRatioChanged: (Float) -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        // Base Static Artwork Layer (Always present so the card never disappears)
        ArtworkImage(
            name = track.title,
            artist = track.artist,
            embeddedUrl = track.artworkUrl,
            fallbackIcon = Icons.Filled.MusicNote,
            modifier = Modifier.fillMaxSize(),
            decodeSizePx = decodeSizePx,
            transformations = transformations,
        )

        // Animated Canvas Overlay Layer (Runs smoothly on top when active)
        if (canvas != null && !canvas.url.isNullOrBlank()) {
            CanvasArtworkPlayer(
                canvas = canvas,
                isPlaying = isPlaying,
                pausedForTransition = pausedForTransition,
                onAspectRatioChanged = onAspectRatioChanged,
                contentMode = CanvasContentMode.CROP,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Captures 4D spatial device tilt (pitch and roll) via ROTATION_VECTOR or GRAVITY sensors,
 * normalized to [-1f, 1f] for buttery-smooth perspective parallax.
 */
@Composable
fun rememberSpatialTilt(enabled: Boolean = true): State<Offset> {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val isResumed = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val tiltState = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled, view, isResumed) {
        if (!enabled || !isResumed) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val remappedMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            private var smoothedX = 0f
            private var smoothedY = 0f
            private var initialized = false

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val displayRotation = view.display?.rotation ?: android.view.Surface.ROTATION_0

                val (targetX, targetY) = if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val (axisX, axisY) = when (displayRotation) {
                        android.view.Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        android.view.Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        android.view.Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                    SensorManager.getOrientation(remappedMatrix, orientation)
                    val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    (roll / 32f).coerceIn(-1f, 1f) to (pitch / 32f).coerceIn(-1f, 1f)
                } else if (event.sensor.type == Sensor.TYPE_GRAVITY) {
                    val rawX = event.values[0]
                    val rawY = event.values[1]
                    val (gx, gy) = when (displayRotation) {
                        android.view.Surface.ROTATION_90 -> -rawY to rawX
                        android.view.Surface.ROTATION_180 -> -rawX to -rawY
                        android.view.Surface.ROTATION_270 -> rawY to -rawX
                        else -> rawX to rawY
                    }
                    (-gx / 9.8f).coerceIn(-1f, 1f) to (gy / 9.8f).coerceIn(-1f, 1f)
                } else return

                if (!initialized) {
                    smoothedX = targetX
                    smoothedY = targetY
                    initialized = true
                } else {
                    smoothedX = smoothedX * 0.70f + targetX * 0.30f
                    smoothedY = smoothedY * 0.70f + targetY * 0.30f
                }

                val prev = tiltState.value
                if (abs(smoothedX - prev.x) > 0.065f || abs(smoothedY - prev.y) > 0.065f) {
                    tiltState.value = Offset(smoothedX, smoothedY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return tiltState
}
