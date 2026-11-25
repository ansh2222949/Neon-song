package com.neonsong.app.features.lyrics

import com.neonsong.app.ui.player.AnimatedPlayPauseIcon
import com.neonsong.app.ui.player.PlayerProgressSlider
import com.neonsong.app.ui.player.formatTime
import com.neonsong.app.ui.player.WavySeekBar


import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.Slider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LiquidGlassSurface
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.local.LyricsAnimation
import com.neonsong.app.data.lyrics.LyricLine
import com.neonsong.app.data.lyrics.isRtlText
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.flow.StateFlow

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Success(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val isWordSynced: Boolean = false,
        val plainLyrics: String? = null,
        val isInstrumental: Boolean = false,
        val source: String? = null,
    ) : LyricsUiState {
        val isRtl: Boolean get() = lines.any { it.isRtl } || isRtlText(plainLyrics)
    }
    data object Empty : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

@Composable
fun LyricsPanel(
    state: MusicPlayerState,
    player: MusicPlayer,
    lyricsState: LyricsUiState,
    progressState: StateFlow<PlaybackProgressState>? = null,
    lyricsAnimation: LyricsAnimation = LyricsAnimation.APPLE_FLUID,
    wavySeekbarEnabled: Boolean = true,
    onRetry: () -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    onOpenLyricsOffset: (() -> Unit)? = null,
    lyricsFontScale: Float = 1.0f,
    onLyricsFontScaleChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Manual sync correction (ms, + = lyrics earlier). Applies to lyric
     *  focus/highlight only — the seekbar below keeps true position. */
    lyricsOffsetMs: Long = 0L,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val track = state.current ?: return
    val liquidGlass = LocalLiquidGlass.current
    val effectiveProgressFlow = progressState ?: player.progressState

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Weighted layout only: deliberately no card, background, border, or shadow.
        AnimatedContent(
            targetState = lyricsState,
            transitionSpec = {
                (fadeIn(tween(ExpressiveMotion.Quick)) +
                    androidx.compose.animation.scaleIn(ExpressiveMotion.spatialSpring(), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(ExpressiveMotion.Quick)) +
                        androidx.compose.animation.scaleOut(tween(ExpressiveMotion.Quick), targetScale = 0.96f))
            },
            label = "lyricsStateContent",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { targetState ->
            when (targetState) {
                    is LyricsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                ExpressiveInlineLoadingIndicator(
                                    size = 42.dp,
                                    strokeWidth = 3.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Finding lyrics…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is LyricsUiState.Empty, is LyricsUiState.Error -> {
                        EmptyLyricsView(
                            isInstrumental = false,
                            onRetry = onRetry,
                        )
                    }

                    is LyricsUiState.Success -> {
                        if (targetState.isInstrumental) {
                            EmptyLyricsView(
                                isInstrumental = true,
                                onRetry = onRetry,
                            )
                        } else if (targetState.isSynced && targetState.lines.isNotEmpty()) {
                            SyncedLyricsList(
                                lines = targetState.lines,
                                currentPositionMs = { effectiveProgressFlow.value.positionMs + lyricsOffsetMs },
                                lyricsOffsetMs = lyricsOffsetMs,
                                lyricsFontScale = lyricsFontScale,
                                isPlaying = state.isPlaying,
                                onSeek = player::seekTo,
                                animationStyle = lyricsAnimation,
                                liquidGlass = liquidGlass,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (!targetState.plainLyrics.isNullOrBlank()) {
                            PlainLyricsView(
                                plainLyrics = targetState.plainLyrics,
                                lyricsFontScale = lyricsFontScale,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            EmptyLyricsView(
                                isInstrumental = false,
                                onRetry = onRetry,
                            )
                        }
                    }

                    is LyricsUiState.Idle -> {
                        Box(Modifier.fillMaxSize())
                    }
            }
        }

        // Transparent playback controls; isolated so playback ticks don't recompose LyricsPanel
        LyricsPlaybackControlsHost(
            progressFlow = effectiveProgressFlow,
            state = state,
            player = player,
            wavySeekbarEnabled = wavySeekbarEnabled,
            onToggleFullscreen = onToggleFullscreen,
            isFullscreen = isFullscreen,
            lyricsOffsetMs = lyricsOffsetMs,
            onOpenLyricsOffset = onOpenLyricsOffset,
            lyricsFontScale = lyricsFontScale,
            onLyricsFontScaleChange = onLyricsFontScaleChange,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp, top = 6.dp),
        )
    }
}

@Composable
private fun LyricsPlaybackControlsHost(
    progressFlow: StateFlow<PlaybackProgressState>,
    state: MusicPlayerState,
    player: MusicPlayer,
    wavySeekbarEnabled: Boolean,
    onToggleFullscreen: (() -> Unit)?,
    isFullscreen: Boolean,
    lyricsOffsetMs: Long,
    onOpenLyricsOffset: (() -> Unit)?,
    lyricsFontScale: Float,
    onLyricsFontScaleChange: (Float) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val progress by progressFlow.collectAsStateWithLifecycle(
        initialValue = PlaybackProgressState(positionMs = state.positionMs, durationMs = state.durationMs),
    )
    LyricsPlaybackControls(
        state = state,
        currentPositionMs = progress.positionMs,
        totalDurationMs = if (progress.durationMs > 0) progress.durationMs else state.durationMs,
        player = player,
        wavySeekbarEnabled = wavySeekbarEnabled,
        onToggleFullscreen = onToggleFullscreen,
        isFullscreen = isFullscreen,
        lyricsOffsetMs = lyricsOffsetMs,
        onOpenLyricsOffset = onOpenLyricsOffset,
        lyricsFontScale = lyricsFontScale,
        onLyricsFontScaleChange = onLyricsFontScaleChange,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        tertiaryColor = tertiaryColor,
        modifier = modifier,
    )
}

internal fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var low = 0
    var high = lines.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (lines[middle].timeMs <= positionMs) low = middle + 1 else high = middle
    }
    return low - 1
}

/**
 * Guarantees proper spacing between words across all providers.
 * If provider syllable tokens stripped whitespace, restores spaces using line.text as ground truth.
 */
internal fun resolveSyllableDisplayTexts(line: LyricLine): List<String> {
    val syllables = line.syllables
    if (syllables.isEmpty()) return emptyList()

    val fullText = line.text
    if (fullText.contains(' ') || fullText.contains('\u00A0')) {
        val result = mutableListOf<String>()
        var cursor = 0

        for (i in syllables.indices) {
            val syl = syllables[i]
            val cleanSyl = syl.text.trim()
            if (cleanSyl.isEmpty()) {
                result.add(syl.text)
                continue
            }

            val idx = fullText.indexOf(cleanSyl, startIndex = cursor, ignoreCase = true)
            if (idx >= 0) {
                val endIdx = idx + cleanSyl.length
                val hasTrailingSpace = endIdx < fullText.length && (fullText[endIdx] == ' ' || fullText[endIdx] == '\u00A0')
                if (hasTrailingSpace && i < syllables.size - 1) {
                    result.add("$cleanSyl ")
                    cursor = endIdx + 1
                } else {
                    result.add(cleanSyl)
                    cursor = endIdx
                }
            } else {
                if (i < syllables.size - 1) {
                    result.add("$cleanSyl ")
                } else {
                    result.add(cleanSyl)
                }
            }
        }
        return result
    }

    return syllables.map { it.text }
}

private fun Modifier.lyricsVerticalFade(
    topFadeHeight: Dp = 48.dp,
    bottomFadeHeight: Dp = 64.dp,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val topPx = topFadeHeight.toPx().coerceAtMost(size.height / 3f)
        val bottomPx = bottomFadeHeight.toPx().coerceAtMost(size.height / 3f)
        val brush = Brush.verticalGradient(
            0.00f to Color.Transparent,
            (topPx / size.height).coerceIn(0f, 0.4f) to Color.Black,
            (1f - (bottomPx / size.height)).coerceIn(0.6f, 1f) to Color.Black,
            1.00f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }
    }

@Composable
private fun SyncedLyricsList(
    lines: List<LyricLine>,
    currentPositionMs: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    animationStyle: LyricsAnimation,
    liquidGlass: Boolean,
    modifier: Modifier = Modifier,
    lyricsOffsetMs: Long = 0L,
    lyricsFontScale: Float = 1.0f,
) {
    val listState = rememberLazyListState()
    var userScrolledTime by remember { mutableLongStateOf(0L) }
    val currentPositionMsState by rememberUpdatedState(currentPositionMs)

    val isOverallRtl = remember(lines) {
        val meaningfulLines = lines.filter { it.text.isNotBlank() && it.text != "♪" }
        if (meaningfulLines.isEmpty()) false
        else meaningfulLines.count { it.isRtl } > meaningfulLines.size / 2
    }

    // Cheap binary search per progress tick matching desktop client.
    // Row composables below isolate recomposition so only the active line re-reads positionMs.
    val activeIndex by remember(lines) {
        derivedStateOf {
            activeLineIndex(lines, currentPositionMsState())
        }
    }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    if (isDragged) {
        userScrolledTime = System.currentTimeMillis()
    }

    LaunchedEffect(activeIndex, isPlaying) {
        val timeSinceUserScroll = System.currentTimeMillis() - userScrolledTime
        if (timeSinceUserScroll > 2200L && activeIndex in lines.indices) {
            runCatching {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportSize.height
                val targetItem = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
                if (targetItem != null && viewportHeight > 0) {
                    val targetY = viewportHeight / 3
                    val delta = targetItem.offset - targetY
                    listState.animateScrollBy(delta.toFloat())
                } else {
                    val targetIndex = (activeIndex - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(index = targetIndex, scrollOffset = 0)
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.lyricsVerticalFade(),
        contentPadding = PaddingValues(
            top = 40.dp,
            bottom = 130.dp,
            // Wider gutters: at the large type scale the focus zoom
            // (up to 1.18x) would otherwise push scaled rows past the
            // clipped list edges.
            start = 16.dp,
            end = 16.dp,
        ),
        // Small base gap; each row appends its own trailing gap below so
        // a lead row followed by backing vocals groups tight instead of
        // sitting at the same constant distance as full phrases.
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val fullRowGap = when (animationStyle) {
            LyricsAnimation.APPLE_ZOOM -> 30.dp
            LyricsAnimation.CARD_POP -> 24.dp
            else -> 26.dp
        }
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == activeIndex
            val isPast = activeIndex >= 0 && index < activeIndex
            val distance = kotlin.math.abs(index - activeIndex)
            // Backing-vocal rows (every syllable flagged background) render
            // dim and slightly smaller, grouped tight under their lead row.
            val isBgRow = line.syllables.isNotEmpty() && line.syllables.all { it.isBackground }
            val tightAfter = lines.getOrNull(index + 1)?.let { next ->
                next.syllables.isNotEmpty() && next.syllables.all { it.isBackground }
            } == true
            val isLineRtl = remember(line, isOverallRtl) {
                line.isRtl || (isOverallRtl && (line.text.isBlank() || line.text == "♪"))
            }

            // Each profile gets a distinct motion signature. These targets only
            // change when focus changes (except the short onset pulse below), so
            // off-screen/inactive rows never run a permanent animation loop.
            val scaleTarget = when (animationStyle) {
                LyricsAnimation.APPLE_FLUID -> if (isActive) 1.085f else if (distance == 1) 0.99f else 0.975f
                LyricsAnimation.KARAOKE_PULSE -> if (isActive) 1.10f else if (distance == 1) 0.99f else 0.97f
                LyricsAnimation.KINETIC_SLIDE -> if (isActive) 1.045f else if (isPast) 0.99f else 0.975f
                LyricsAnimation.CINEMATIC_BLUR -> if (isActive) 1.065f else if (distance == 1) 0.96f else 0.93f
                LyricsAnimation.LOSSLESS_GLOW -> if (isActive) 1.075f else if (distance == 1) 0.99f else 0.97f
                LyricsAnimation.CARD_POP -> if (isActive) 1.065f else 0.985f
                LyricsAnimation.APPLE_ZOOM -> when {
                    isActive -> 1.18f
                    distance == 1 -> 0.94f
                    else -> 0.88f
                }
                LyricsAnimation.MINIMAL_WAVE -> 1f
            }

            val scaleSpec: AnimationSpec<Float> = when (animationStyle) {
                LyricsAnimation.MINIMAL_WAVE -> tween(100)
                else -> tween(200, easing = FastOutSlowInEasing)
            }

            val scale = animateFloatAsState(
                targetValue = scaleTarget,
                animationSpec = scaleSpec,
                label = "lyricScale_$index",
            )

            // Direct non-bouncy scale (clean scroll without wobbling or bouncy pops)
            val pulseScale = 1f

            // Horizontal focus tracking / directional entry and exit.
            // Inverted for RTL so lyrics smoothly glide along reading orientation.
            val rawTranslationXTarget = when (animationStyle) {
                LyricsAnimation.APPLE_FLUID -> when {
                    isActive -> 4f
                    isPast -> 0f
                    else -> -5f
                }
                LyricsAnimation.KARAOKE_PULSE -> if (isActive) 3f else 0f
                LyricsAnimation.KINETIC_SLIDE -> when {
                    isActive -> 0f
                    isPast -> 12f
                    else -> -24f
                }
                LyricsAnimation.LOSSLESS_GLOW -> if (isActive) 2f else 0f
                LyricsAnimation.MINIMAL_WAVE -> when {
                    isActive -> 2f
                    isPast -> 0f
                    else -> -2f
                }
                else -> 0f
            }
            val translationXTarget = if (isLineRtl) -rawTranslationXTarget else rawTranslationXTarget
            val translationX = animateFloatAsState(
                targetValue = translationXTarget,
                animationSpec = when (animationStyle) {
                    LyricsAnimation.MINIMAL_WAVE -> tween(110)
                    else -> tween(200, easing = FastOutSlowInEasing)
                },
                label = "lyricTransX_$index",
            )

            // Vertical depth drift gives past/future lines a readable direction.
            val translationYTarget = when (animationStyle) {
                LyricsAnimation.APPLE_FLUID -> when {
                    isActive -> -2f
                    isPast -> -1f
                    else -> 3f
                }
                LyricsAnimation.KARAOKE_PULSE -> if (isActive) -2f else 1f
                LyricsAnimation.KINETIC_SLIDE -> if (isActive) -1f else 1f
                LyricsAnimation.CINEMATIC_BLUR -> when {
                    isActive -> 0f
                    isPast -> -10f
                    else -> 10f
                }
                LyricsAnimation.LOSSLESS_GLOW -> if (isActive) -2f else 1f
                LyricsAnimation.CARD_POP -> if (isActive) -4f else 2f
                LyricsAnimation.APPLE_ZOOM -> when {
                    isActive -> -3f
                    isPast -> -1f
                    else -> 2f
                }
                LyricsAnimation.MINIMAL_WAVE -> if (isPast) -1f else if (isActive) 0f else 1f
            }
            val translationY = animateFloatAsState(
                targetValue = translationYTarget,
                animationSpec = when (animationStyle) {
                    LyricsAnimation.MINIMAL_WAVE -> tween(100)
                    else -> tween(200, easing = FastOutSlowInEasing)
                },
                label = "lyricTransY_$index",
            )

            val rawRotationTarget = when (animationStyle) {
                LyricsAnimation.KINETIC_SLIDE -> when {
                    isActive -> 0f
                    isPast -> 0.35f
                    else -> -0.65f
                }
                LyricsAnimation.CARD_POP -> when {
                    isActive -> 0f
                    isPast -> -0.35f
                    else -> 0.55f
                }
                else -> 0f
            }
            val rotationTarget = if (isLineRtl) -rawRotationTarget else rawRotationTarget
            val rotation = animateFloatAsState(
                targetValue = rotationTarget,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "lyricRotation_$index",
            )
            val depthRotationTarget = when (animationStyle) {
                LyricsAnimation.CINEMATIC_BLUR -> when {
                    isActive -> 0f
                    isPast -> -1.25f
                    else -> 1.25f
                }
                LyricsAnimation.CARD_POP -> when {
                    isActive -> 0f
                    isPast -> -0.6f
                    else -> 0.8f
                }
                else -> 0f
            }
            val depthRotation = animateFloatAsState(
                targetValue = depthRotationTarget,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "lyricDepth_$index",
            )

            // Alpha Floor
            val alphaTarget = when (animationStyle) {
                LyricsAnimation.APPLE_FLUID -> if (isActive) 1f else if (distance == 1) 0.64f else if (isPast) 0.50f else 0.43f
                LyricsAnimation.KARAOKE_PULSE -> if (isActive) 1f else if (isPast) 0.62f else 0.49f
                LyricsAnimation.KINETIC_SLIDE -> if (isActive) 1f else if (isPast) 0.54f else 0.42f
                LyricsAnimation.CINEMATIC_BLUR -> if (isActive) 1f else if (distance <= 1) 0.58f else 0.28f
                LyricsAnimation.LOSSLESS_GLOW -> if (isActive) 1f else if (distance == 1) 0.66f else 0.46f
                LyricsAnimation.CARD_POP -> if (isActive) 1f else if (isPast) 0.62f else 0.48f
                LyricsAnimation.APPLE_ZOOM -> if (isActive) 1f else if (distance == 1) 0.55f else 0.32f
                LyricsAnimation.MINIMAL_WAVE -> if (isActive) 1f else if (distance == 1) 0.58f else 0.38f
            }
            val alpha = animateFloatAsState(
                targetValue = alphaTarget,
                animationSpec = tween(if (animationStyle == LyricsAnimation.MINIMAL_WAVE) 90 else 160),
                label = "lyricAlpha_$index",
            )

            val interactiveColor = Color.White
            val textColor by animateColorAsState(
                targetValue = Color.White,
                animationSpec = tween(140),
                label = "lyricColor_$index",
            )

            val lineLayoutDirection = if (isLineRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale.value * pulseScale
                            scaleY = scale.value * pulseScale
                            transformOrigin = TransformOrigin(if (isLineRtl) 1f else 0f, 0.5f)
                            this.alpha = alpha.value * if (isBgRow) (if (isActive) 0.85f else 0.55f) else 1f
                            this.translationX = translationX.value * density
                            this.translationY = translationY.value * density
                            rotationZ = rotation.value
                            rotationX = depthRotation.value
                            if (depthRotation.value != 0f) cameraDistance = 24f * density
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // Inverse of the highlight shift: tap targets audio time.
                            onSeek((line.timeMs - lyricsOffsetMs).coerceAtLeast(0L))
                        }
                        .padding(
                            horizontal = if (animationStyle == LyricsAnimation.CARD_POP) 16.dp else 12.dp,
                            vertical = if (isActive) 8.dp else 6.dp,
                        ),
                ) {
                    // Compact type: lineHeight leaves room for the focus zoom
                    // (up to 1.18x) so scaled rows neither overlap neighbours
                    // nor clip at the list edges — without the old oversized
                    // leading that stretched the gaps between rows. Backing
                    // rows stay smaller and never take the lead weight.
                    val fontStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = ((if (isBgRow) 21f else 28f) * lyricsFontScale).sp,
                        fontWeight = if (isBgRow) {
                            FontWeight.Medium
                        } else if (isActive) {
                            if (animationStyle == LyricsAnimation.APPLE_ZOOM) FontWeight.Black else FontWeight.ExtraBold
                        } else FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        lineHeight = ((if (isBgRow) 32f else 42f) * lyricsFontScale).sp,
                    )

                    WordByWordLyricLine(
                        line = line,
                        currentPositionMs = currentPositionMs,
                        isActive = isActive,
                        activeColor = textColor,
                        inactiveColor = Color.White.copy(alpha = 0.55f),
                        liquidGlass = liquidGlass,
                        accentColor = interactiveColor,
                        animationStyle = animationStyle,
                        fontStyle = fontStyle,
                        isRtl = isLineRtl,
                        modifier = Modifier.padding(start = if (isBgRow) 14.dp else 0.dp),
                    )
                }
            }
            if (!tightAfter) {
                Spacer(modifier = Modifier.height(fullRowGap - 4.dp))
            }
        }
    }
}

@Composable
private fun WordByWordLyricLine(
    line: LyricLine,
    currentPositionMs: () -> Long,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    liquidGlass: Boolean,
    accentColor: Color,
    animationStyle: LyricsAnimation,
    fontStyle: TextStyle,
    isRtl: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lineLayoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val lineColor by animateColorAsState(
        targetValue = if (isActive) activeColor else inactiveColor,
        animationSpec = tween(220),
        label = "lineColor",
    )
    CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
        if (!line.hasSyllables || !isActive) {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = fontStyle,
                    color = lineColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!line.transliteration.isNullOrBlank() && isActive) {
                    val transliterationRtl = isRtlText(line.transliteration)
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (transliterationRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        Text(
                            text = line.transliteration,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp,
                            ),
                            color = activeColor.copy(alpha = 0.72f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp),
                        )
                    }
                }
            }
            return@CompositionLocalProvider
        }

        val positionMs = currentPositionMs()
        val displayTexts = remember(line) { resolveSyllableDisplayTexts(line) }
        val annotatedText = buildAnnotatedString {
            line.syllables.forEachIndexed { sIndex, syllable ->
                val elapsed = positionMs - syllable.timeMs
                val duration = syllable.durationMs.coerceAtLeast(1L)
                val wordProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val completed = elapsed >= duration
                val activeWord = elapsed in 0 until duration
                val nextWordTime = line.syllables.getOrNull(sIndex + 1)?.timeMs
                val reachedWithoutDuration = syllable.durationMs <= 0L &&
                    positionMs >= syllable.timeMs && (nextWordTime == null || positionMs < nextWordTime)

                val wordColor = when {
                    completed || reachedWithoutDuration -> activeColor
                    activeWord -> {
                        val activeHighlight = lerp(accentColor, Color.White, if (liquidGlass) 0.72f else 0.45f)
                        activeHighlight.copy(alpha = 0.72f + wordProgress * 0.28f)
                    }
                    else -> inactiveColor.copy(alpha = 0.44f)
                }

                pushStyle(SpanStyle(color = wordColor))
                append(displayTexts.getOrElse(sIndex) { syllable.text })
                pop()
            }
        }

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = annotatedText,
                style = fontStyle,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!line.transliteration.isNullOrBlank()) {
                val transliterationRtl = isRtlText(line.transliteration)
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (transliterationRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Text(
                        text = line.transliteration,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                        ),
                        color = activeColor.copy(alpha = 0.76f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsView(
    plainLyrics: String,
    modifier: Modifier = Modifier,
    lyricsFontScale: Float = 1.0f,
) {
    val isRtl = remember(plainLyrics) { isRtlText(plainLyrics) }
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .padding(top = 24.dp, bottom = 90.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.SyncDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.90f),
                )
                Text(
                    "Lyrics not time-synced",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.70f),
                )
            }

            Text(
                text = plainLyrics,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (22f * lyricsFontScale).sp,
                    lineHeight = (36f * lyricsFontScale).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                ),
                textAlign = TextAlign.Start,
                color = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyLyricsView(
    isInstrumental: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = if (isInstrumental) Icons.Filled.MusicOff else Icons.Filled.Lyrics,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = Color.White.copy(alpha = 0.90f),
            )

            Text(
                text = if (isInstrumental) "Instrumental" else "No lyrics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Text(
                text = if (isInstrumental) {
                    "This track has no vocal lyrics."
                } else {
                    "No synced lyrics found for this track."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.70f),
                textAlign = TextAlign.Center,
            )

            if (!isInstrumental) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onRetry,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun LyricsPlaybackControls(
    state: MusicPlayerState,
    currentPositionMs: Long,
    totalDurationMs: Long,
    player: MusicPlayer,
    wavySeekbarEnabled: Boolean = true,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    lyricsOffsetMs: Long = 0L,
    onOpenLyricsOffset: (() -> Unit)? = null,
    lyricsFontScale: Float = 1.0f,
    onLyricsFontScaleChange: (Float) -> Unit = {},
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    var showFontSlider by rememberSaveable { mutableStateOf(false) }

    // This Column performs layout only. It intentionally draws no container.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = showFontSlider,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
        ) {
            val fontSliderShape = remember { SquircleShape(20.dp) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .liquidGlassSurface(
                        shape = fontSliderShape,
                        borderWidth = 0.85.dp,
                        accentTint = primaryColor,
                        alphaScale = 1.15f,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Lyrics size: ${(lyricsFontScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.90f),
                        )
                        if (lyricsFontScale != 1.0f) {
                            TextButton(
                                onClick = { onLyricsFontScaleChange(1.0f) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp),
                            ) {
                                Text(
                                    "Reset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.70f),
                        )
                        Slider(
                            value = lyricsFontScale,
                            onValueChange = onLyricsFontScaleChange,
                            valueRange = 0.7f..1.5f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "A",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.95f),
                        )
                    }
                }
            }
        }

        if (onToggleFullscreen != null || onOpenLyricsOffset != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOpenLyricsOffset != null) {
                    val isOffsetActive = lyricsOffsetMs != 0L
                    LiquidGlassIconButton(
                        onClick = onOpenLyricsOffset,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        accentTint = if (isOffsetActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        contentDescription = "Sync offset",
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isOffsetActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.90f),
                        )
                    }

                    Spacer(Modifier.width(10.dp))
                }

                // Lyrics Font Scale toggle button placed immediately to the LEFT of Full Screen
                val isCustomFont = lyricsFontScale != 1.0f
                LiquidGlassIconButton(
                    onClick = { showFontSlider = !showFontSlider },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    accentTint = if (showFontSlider || isCustomFont) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    contentDescription = "Adjust lyrics text size",
                ) {
                    Icon(
                        Icons.Filled.FormatSize,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (showFontSlider || isCustomFont) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.90f),
                    )
                }

                Spacer(Modifier.width(10.dp))

                if (onToggleFullscreen != null) {
                    LiquidGlassIconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        accentTint = if (isFullscreen) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        contentDescription = if (isFullscreen) "Exit fullscreen lyrics" else "Fullscreen lyrics",
                    ) {
                        Icon(
                            if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (isFullscreen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.90f),
                        )
                    }
                }
            }
        }

        if (isFullscreen) return@Column

        // Current-gesture value only; null = finger off, show live position.
        // Keyed by track so a previous song's drag can never leak into this
        // one, and nullable so a press without movement seeks nowhere while a
        // gesture that ends without onValueChangeFinished can't pin the bar.
        val lyricsTrackKey = state.current?.let { it.videoId ?: "${it.artist}|${it.title}" }
        val seekInteraction = remember(lyricsTrackKey) { MutableInteractionSource() }
        var isInteracting by remember(lyricsTrackKey) { mutableStateOf(false) }
        LaunchedEffect(seekInteraction, lyricsTrackKey) {
            var dragCount = 0
            var pressCount = 0
            seekInteraction.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> dragCount++
                    is DragInteraction.Stop, is DragInteraction.Cancel -> dragCount = maxOf(0, dragCount - 1)
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = maxOf(0, pressCount - 1)
                }
                isInteracting = dragCount > 0 || pressCount > 0
            }
        }
        var dragValue by remember(lyricsTrackKey) { mutableStateOf<Float?>(null) }
        var lastSeekValue by remember(lyricsTrackKey) { mutableStateOf<Float?>(null) }
        LaunchedEffect(isInteracting, lyricsTrackKey) {
            if (!isInteracting) {
                delay(120L)
                dragValue = null
                lastSeekValue = null
            }
        }
        LaunchedEffect(dragValue, isInteracting, lyricsTrackKey) {
            if (dragValue != null && !isInteracting) {
                delay(250L)
                dragValue = null
                lastSeekValue = null
            }
        }
        val end = totalDurationMs.coerceAtLeast(1).toFloat()
        val shown = (dragValue ?: currentPositionMs.coerceIn(0, totalDurationMs.coerceAtLeast(0)).toFloat())
            .coerceIn(0f, end)

        if (wavySeekbarEnabled) {
            WavySeekBar(
                positionMs = currentPositionMs,
                durationMs = totalDurationMs,
                isPlaying = state.isPlaying,
                onSeek = player::seekTo,
                isTranslucent = true,
                trackKey = state.current?.let { it.videoId ?: "${it.artist}|${it.title}" },
                showTimeLabels = false,
                modifier = Modifier.fillMaxWidth(),
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                tertiaryColor = tertiaryColor,
            )
        } else {
            PlayerProgressSlider(
                value = shown,
                onValueChange = {
                    dragValue = it
                    lastSeekValue = it
                },
                onValueChangeFinished = {
                    // Commit only this gesture's value; no value = no seek.
                    val target = (lastSeekValue ?: dragValue)?.toLong()
                    dragValue = null
                    lastSeekValue = null
                    if (target != null) player.seekTo(target)
                },
                valueRange = 0f..end,
                enabled = totalDurationMs > 0,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = seekInteraction,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatTime(shown.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                "−${formatTime((totalDurationMs - shown.toLong()).coerceAtLeast(0))}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassIconButton(
                onClick = player::previous,
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                accentTint = primaryColor,
                contentDescription = "Previous",
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = 0.94f),
                )
            }

            Spacer(Modifier.width(28.dp))

            LiquidGlassIconButton(
                onClick = player::togglePlayPause,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                accentTint = primaryColor,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
            ) {
                if (state.isBuffering) {
                    ExpressiveInlineLoadingIndicator(
                        size = 24.dp,
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    AnimatedPlayPauseIcon(state.isPlaying, Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.width(28.dp))

            LiquidGlassIconButton(
                onClick = player::next,
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                accentTint = primaryColor,
                contentDescription = "Next",
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = 0.94f),
                )
            }
        }
    }
}

/**
 * Animated pixel scroll for [LazyListState], which only ships instant
 * [LazyListState.scrollBy] and indexed [LazyListState.animateScrollToItem].
 * Ease-out-cubic frame loop so the active-line follow stays smooth instead
 * of jumping. Callers already guard with runCatching.
 */
private suspend fun LazyListState.animateScrollBy(pixels: Float) {
    if (pixels == 0f) return
    var consumed = 0f
    var startNanos = -1L
    var done = false
    while (!done) {
        val target = withFrameNanos { now ->
            if (startNanos < 0L) startNanos = now
            val t = ((now - startNanos) / 350_000_000f).coerceIn(0f, 1f)
            done = t >= 1f
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            pixels * eased
        }
        val delta = target - consumed
        val scrolled = scrollBy(delta)
        consumed += scrolled
        if (scrolled == 0f && delta != 0f) {
            break
        }
    }
}
