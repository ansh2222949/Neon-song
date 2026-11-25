@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.neonsong.app.features.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.features.player.components.PlayerArtwork
import com.neonsong.app.playback.PlaybackChromeState
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.theme.Backdrop
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.util.LocalHapticManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import com.neonsong.app.ui.theme.neonGlassSurface
import com.neonsong.app.ui.theme.neonTactileClick
import kotlin.math.abs

private val MiniPlayerShape = SquircleShape(24.dp)
private val MiniArtShape = SquircleShape(12.dp)
private val MiniDragSettleSpec = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = 440f,
    visibilityThreshold = 0.5f,
)

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(ExpressiveMotion.effectsFast()) +
                scaleIn(ExpressiveMotion.appleBouncySpring(), initialScale = 0.78f)) togetherWith
                (fadeOut(ExpressiveMotion.effectsFast()) +
                    scaleOut(ExpressiveMotion.effectsFast(), targetScale = 0.78f))
        },
        label = "playPauseIcon",
        modifier = modifier,
    ) { playing ->
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = "Play or pause",
            modifier = Modifier.fillMaxSize(),
            tint = tint,
        )
    }
}

@Composable
fun PlayingWaveBars(
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = CircleShape,
    isPlaying: Boolean = true,
    animated: Boolean = true,
) {
    // Single lightweight 12fps phase ticker instead of 3x 120Hz infiniteTransitions
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, animated) {
        if (!isPlaying || !animated) return@LaunchedEffect
        while (true) {
            phase = (phase + 0.55f) % 6.2831855f
            delay(84L) // ~12 FPS: crisp wave feel, 10x lower LTPO/CPU impact
        }
    }

    val bar1Provider: () -> Float = {
        when {
            !isPlaying -> 0.22f
            !animated -> 0.52f
            else -> 0.28f + 0.68f * (0.5f + 0.5f * kotlin.math.sin(phase))
        }
    }
    val bar2Provider: () -> Float = {
        when {
            !isPlaying -> 0.22f
            !animated -> 0.92f
            else -> 0.28f + 0.68f * (0.5f + 0.5f * kotlin.math.sin(phase * 1.3f + 1.8f))
        }
    }
    val bar3Provider: () -> Float = {
        when {
            !isPlaying -> 0.22f
            !animated -> 0.68f
            else -> 0.28f + 0.68f * (0.5f + 0.5f * kotlin.math.sin(phase * 0.9f + 3.6f))
        }
    }

    if (containerColor == Color.Transparent) {
        Box(
            modifier = modifier.defaultMinSize(minWidth = 24.dp, minHeight = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            WaveBarsCanvas(
                waveColor = waveColor,
                bar1Provider = bar1Provider,
                bar2Provider = bar2Provider,
                bar3Provider = bar3Provider,
            )
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            modifier = modifier.defaultMinSize(minWidth = 26.dp, minHeight = 22.dp),
        ) {
            WaveBarsCanvas(
                waveColor = waveColor,
                bar1Provider = bar1Provider,
                bar2Provider = bar2Provider,
                bar3Provider = bar3Provider,
            )
        }
    }
}

@Composable
private fun WaveBarsCanvas(
    waveColor: Color,
    bar1Provider: () -> Float,
    bar2Provider: () -> Float,
    bar3Provider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 5.dp, vertical = 5.dp)
            .size(16.dp, 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val barCount = 3
            val barWidth = (size.width / 5.2f).coerceAtLeast(1.5f)
            val barGap = barWidth * 0.9f
            val totalContentWidth = barCount * barWidth + (barCount - 1) * barGap
            val startX = ((size.width - totalContentWidth) / 2f).coerceAtLeast(0f)

            repeat(barCount) { index ->
                val fraction = when (index) {
                    0 -> bar1Provider()
                    1 -> bar2Provider()
                    else -> bar3Provider()
                }
                val barHeight = (size.height * fraction).coerceIn(minOf(barWidth, size.height), size.height)
                drawRoundRect(
                    color = waveColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = startX + index * (barWidth + barGap),
                        y = size.height - barHeight,
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
    }
}

@Composable
fun MiniPlayerGlassProgress(
    progressProvider: () -> Float, // Recomposition nahi hoga!
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .drawBehind {
                val p = progressProvider().coerceIn(0f, 1f)
                // Track base
                drawRect(color = Color.White.copy(alpha = 0.12f))
                // Active glass progress bar
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.7f), accentColor)
                    ),
                    size = size.copy(width = size.width * p)
                )
            }
    )
}

@Composable
fun MiniPlayerProgress(progressState: StateFlow<PlaybackProgressState>) {
    // Quantize to ~240 visual steps (~1px on most phones / ~1s on a 4m song) so LTPO can sleep!
    val quantizedFraction = remember(progressState) {
        progressState
            .map { s ->
                if (s.durationMs > 0L) ((s.positionMs.toFloat() / s.durationMs) * 240f).toInt().coerceIn(0, 240)
                else 0
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0)

    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val fallbackSecondary = MaterialTheme.colorScheme.tertiary

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 3.dp)
            .height(2.5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = quantizedFraction.value / 240f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .drawWithCache {
                    val palette = com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value
                    val brush = Brush.horizontalGradient(
                        listOf(palette?.primary ?: fallbackPrimary, palette?.secondary ?: fallbackSecondary),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        )
    }
}

@Composable
fun MiniPlayer(
    state: PlaybackChromeState,
    progressState: StateFlow<PlaybackProgressState>,
    isLossless: Boolean = false,
    audioCodec: String? = null,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    bottomPadding: Dp,
    edgeToEdge: Boolean,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onVerticalDrag: ((Float) -> Unit)? = null,
    onVerticalDragEnd: ((Float) -> Unit)? = null,
    hideArtworkForMorph: Boolean = false,
    onArtworkBoundsChanged: (Rect) -> Unit = {},
) {
    val hapticManager = LocalHapticManager.current
    val haptics = LocalHapticFeedback.current
    val track = state.current ?: return

    val liquidGlass = LocalLiquidGlass.current
    val isGlass = liquidGlass && isLiquidGlassBackdropSupported() && backdrop != null
    val luminance = 0.5f

    val barInteraction = remember { MutableInteractionSource() }
    val isBarPressed by barInteraction.collectIsPressedAsState()

    var dragX by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }
    var dragY by remember(track.videoId, track.title) { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        dragX = 0f
        dragY = 0f
    }

    val shownXState = animateFloatAsState(dragX, MiniDragSettleSpec, label = "miniPlayerX")
    val shownYState = animateFloatAsState(dragY, MiniDragSettleSpec, label = "miniPlayerY")
    val barPressScaleState = animateFloatAsState(
        targetValue = if (isBarPressed) 0.975f else 1f,
        animationSpec = if (isBarPressed) ExpressiveMotion.FloatPressSpring else ExpressiveMotion.FloatReleaseSpring,
        label = "miniBarPressScale",
    )

    val density = LocalDensity.current
    val horizontalThreshold = with(density) { 48.dp.toPx() }
    val expandThreshold = with(density) { 30.dp.toPx() }
    val dismissThreshold = with(density) { 52.dp.toPx() }
    val isDark = LocalIsDarkTheme.current

    val positionedModifier = modifier
        .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        )
        .padding(horizontal = 20.dp)
        .padding(bottom = bottomPadding)
        .widthIn(max = 440.dp)
        .fillMaxWidth()

    Box(
        modifier = positionedModifier
            .pointerInput(track.videoId, track.title, horizontalThreshold, expandThreshold, dismissThreshold) {
                val flingXPerSec = 900f
                val flingUpPerSec = 520f
                val flingDownPerSec = 950f
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var lockedX: Boolean? = null
                    var didThresholdHaptic = false
                    var accX = 0f
                    var accY = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                if (lockedX != null) {
                                    change.consume()
                                }
                                break
                            }
                            val delta = change.positionChange()
                            accX += delta.x
                            accY += delta.y
                            tracker.addPosition(change.uptimeMillis, change.position)

                            val isFirstLock = lockedX == null && (abs(accX) > touchSlopPx || abs(accY) > touchSlopPx)
                            if (isFirstLock) {
                                lockedX = abs(accX) > abs(accY)
                            }
                            if (lockedX != null) {
                                change.consume()
                                if (lockedX == true) {
                                    // Rubber-band resistance on horizontal track scrub
                                    dragX += delta.x * 0.72f
                                } else {
                                    if (onVerticalDrag != null) {
                                        val dy = if (isFirstLock) accY else delta.y
                                        onVerticalDrag(dy)
                                    } else {
                                        dragY += delta.y * 0.85f
                                    }
                                }
                                val crossed = if (lockedX == true) {
                                    abs(dragX) > horizontalThreshold
                                } else {
                                    val effectiveY = if (onVerticalDrag != null) accY else dragY
                                    effectiveY < -expandThreshold || effectiveY > dismissThreshold
                                }
                                if (crossed && !didThresholdHaptic) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    didThresholdHaptic = true
                                }
                            }
                        }
                        if (lockedX != null) {
                            val velocity = tracker.calculateVelocity()
                            val finalX = dragX
                            val finalY = dragY
                            dragX = 0f
                            dragY = 0f
                            if (lockedX == false) {
                                if (onVerticalDragEnd != null) {
                                    onVerticalDragEnd(velocity.y)
                                } else {
                                    when {
                                        finalY < -expandThreshold || (velocity.y < -flingUpPerSec && finalY < -6f) -> {
                                            hapticManager.click()
                                            onExpand()
                                        }
                                        finalY > dismissThreshold || (velocity.y > flingDownPerSec && finalY > 12f) -> {
                                            hapticManager.click()
                                            onClose()
                                        }
                                    }
                                }
                            } else {
                                when {
                                    finalX < -horizontalThreshold || (velocity.x < -flingXPerSec && finalX < -10f) -> {
                                        hapticManager.click()
                                        onNext()
                                    }
                                    finalX > horizontalThreshold || (velocity.x > flingXPerSec && finalX > 10f) -> {
                                        hapticManager.click()
                                        onPrevious()
                                    }
                                }
                            }
                        } else {
                            dragX = 0f
                            dragY = 0f
                        }
                    } catch (_: CancellationException) {
                        dragX = 0f
                        dragY = 0f
                        onVerticalDragEnd?.invoke(0f)
                    }
                }
            }
            .graphicsLayer {
                val sy = shownYState.value
                val upwardPull = (-sy / 120f).coerceIn(0f, 1f)
                val scale = if (upwardPull > 0.05f) 1f + (upwardPull * 0.025f) else barPressScaleState.value
                translationY = if (sy < 0f) sy * 0.55f else sy
                scaleX = scale
                scaleY = scale
                alpha = (1f - (sy.coerceAtLeast(0f) / (dismissThreshold * 2.5f))).coerceIn(0.45f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        val artworkSharedKey = "player_artwork_${track.videoId ?: track.title}"

        val sharedArtworkModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = artworkSharedKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ ->
                        spring(
                            dampingRatio = 0.84f,
                            stiffness = 380f,
                        )
                    },
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(14.dp)),
                )
            }
        } else Modifier

        val fallbackPrimary = MaterialTheme.colorScheme.primary
        val shadowPx = with(density) { 16.dp.toPx() }
        val spotShadowColor = Color.Black.copy(alpha = 0.45f)

        val fallbackBg = remember(isDark) {
            (if (isDark) Color(0xFF14161D) else Color(0xFFFDFEFF)).copy(alpha = 0.92f)
        }
        val fallbackBorder = remember(isDark) {
            BorderStroke(
                0.9.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDark) 0.28f else 0.72f),
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.22f),
                    ),
                ),
            )
        }

        Surface(
            shape = MiniPlayerShape,
            color = if (isGlass) Color.Transparent else fallbackBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isGlass) null else fallbackBorder,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    shadowElevation = shadowPx
                    shape = MiniPlayerShape
                    clip = false
                    val accent = com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value?.primary
                        ?: fallbackPrimary
                    ambientShadowColor = accent.copy(alpha = 0.24f)
                    this.spotShadowColor = spotShadowColor
                }
                .then(
                    if (isGlass) {
                        // Use BottomNavigation (non-interactive glass) so MiniPlayer's own press scale & swipe gestures never conflict
                        Modifier.liquidGlassChrome(
                            shape = MiniPlayerShape,
                            enabled = true,
                            preset = LiquidGlassPreset.BottomNavigation,
                            backdrop = backdrop,
                            contentBrightness = luminance,
                        )
                    } else Modifier.neonGlassSurface(MiniPlayerShape),
                )
                .clickable(
                    interactionSource = barInteraction,
                    indication = null,
                    onClick = {
                        hapticManager.click()
                        onExpand()
                    },
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(
                                (if (isGlass && !isDark) Color.Black else Color.White).copy(alpha = 0.22f),
                            ),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 11.dp, end = 10.dp, top = 4.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Inner track content slides horizontally during Next/Previous swipe
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                val sx = shownXState.value
                                translationX = sx
                                alpha = (1f - (abs(sx) / (horizontalThreshold * 2.2f))).coerceIn(0.35f, 1f)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .then(sharedArtworkModifier)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    val sz = coords.size
                                    if (sz.width > 0 && sz.height > 0) {
                                        onArtworkBoundsChanged(
                                            Rect(
                                                left = pos.x,
                                                top = pos.y,
                                                right = pos.x + sz.width,
                                                bottom = pos.y + sz.height,
                                            )
                                        )
                                    }
                                }
                                .graphicsLayer {
                                    alpha = if (hideArtworkForMorph) 0f else 1f
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = MiniArtShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 2.dp,
                                shadowElevation = 6.dp,
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f)),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                PlayerArtwork(track, Modifier.fillMaxSize(), 12.dp)
                            }
                        }

                        val miniTitleColor = MaterialTheme.colorScheme.onSurface
                        val miniArtistColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)

                        AnimatedContent(
                            targetState = track,
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )) togetherWith fadeOut(tween(140))
                            },
                            contentKey = { "${it.videoId}_${it.title}_${it.artist}" },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            label = "miniPlayerTrackText",
                        ) { currentTrack ->
                            Column(verticalArrangement = Arrangement.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        currentTrack.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 15.sp,
                                            letterSpacing = (-0.2).sp,
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = miniTitleColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (state.isPlaying) {
                                        PlayingWaveBars(
                                            waveColor = MaterialTheme.colorScheme.primary,
                                            containerColor = Color.Transparent,
                                            isPlaying = true,
                                            animated = false,
                                            modifier = Modifier.size(16.dp, 12.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        currentTrack.artist,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.Normal,
                                        color = miniArtistColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (isLossless) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
                                        ) {
                                            Text(
                                                text = if (audioCodec?.uppercase() == "FLAC") "FLAC" else "LOSSLESS",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.5.sp,
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val controlColor = MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .neonTactileClick(pressedScale = 0.88f, onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isBuffering) {
                            ExpressiveInlineLoadingIndicator(
                                size = 22.dp,
                                color = controlColor,
                                strokeWidth = 2.4.dp,
                            )
                        } else {
                            AnimatedPlayPauseIcon(
                                isPlaying = state.isPlaying,
                                modifier = Modifier.size(26.dp),
                                tint = controlColor,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(42.dp)
                            .then(
                                if (state.queueSize > 1) {
                                    Modifier.neonTactileClick(pressedScale = 0.88f, onClick = onNext)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(26.dp),
                            tint = if (state.queueSize > 1) {
                                controlColor
                            } else controlColor.copy(alpha = 0.30f),
                        )
                    }
                }
                val progressProvider = remember(progressState) {
                    {
                        val s = progressState.value
                        if (s.durationMs > 0L) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
                    }
                }
                val palette = com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value
                val accentColor = palette?.primary ?: fallbackPrimary

                MiniPlayerGlassProgress(
                    progressProvider = progressProvider,
                    accentColor = accentColor,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 3.dp)
                        .clip(CircleShape),
                )
            }
        }
    }
}
