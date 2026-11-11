package com.neonsong.app.features.lyrics

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.lyrics.LyricLine
import com.neonsong.app.data.lyrics.isRtlText
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlaybackProgressState
import com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.LiquidGlassCard
import com.neonsong.app.ui.player.AnimatedPlayPauseIcon
import com.neonsong.app.ui.player.PlayerProgressSlider
import com.neonsong.app.ui.player.WavySeekBar
import com.neonsong.app.ui.player.formatTime
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ModernLyricsPanel(
    state: MusicPlayerState,
    player: MusicPlayer,
    lyricsState: LyricsUiState,
    progressState: StateFlow<PlaybackProgressState>? = null,
    wavySeekbarEnabled: Boolean = true,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    onRetry: () -> Unit = {},
    onOpenLyricsOffset: (() -> Unit)? = null,
    lyricsFontScale: Float = 1.0f,
    onLyricsFontScaleChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    lyricsOffsetMs: Long = 0L,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    state.current ?: return

    val progressFlow = progressState ?: player.progressState
    val progressInitial = PlaybackProgressState(positionMs = state.positionMs, durationMs = state.durationMs)

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = lyricsState,
            transitionSpec = {
                (fadeIn(tween(ExpressiveMotion.Quick)) +
                    scaleIn(ExpressiveMotion.spatialSpring(), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(ExpressiveMotion.Quick)) +
                        scaleOut(tween(ExpressiveMotion.Quick), targetScale = 0.96f))
            },
            label = "modernLyricsStateContent",
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
                                color = Color.White,
                            )
                            Text(
                                "Finding lyrics…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.70f),
                            )
                        }
                    }
                }

                is LyricsUiState.Empty, is LyricsUiState.Error -> {
                    ModernEmptyLyricsView(
                        isInstrumental = false,
                        onRetry = onRetry,
                    )
                }

                is LyricsUiState.Success -> {
                    if (targetState.isInstrumental) {
                        ModernEmptyLyricsView(
                            isInstrumental = true,
                            onRetry = onRetry,
                        )
                    } else if (targetState.isSynced && targetState.lines.isNotEmpty()) {
                        val isWordSynced = targetState.isWordSynced ||
                            remember(targetState.lines) { targetState.lines.any { it.hasSyllables } }
                        val isOverallRtl = remember(targetState.lines) {
                            val meaningful = targetState.lines.filter { it.text.isNotBlank() && it.text != "♪" }
                            if (meaningful.isEmpty()) false
                            else meaningful.count { it.isRtl } > meaningful.size / 2
                        }
                        val isAppleMusic = remember(targetState.source) {
                            targetState.source?.contains("Apple Music", ignoreCase = true) == true
                        }
                        val currentTextStyle = LocalTextStyle.current
                        val karaokeNormalStyle = remember(currentTextStyle, isAppleMusic, isWordSynced, lyricsFontScale) {
                            val baseSize = (if (isAppleMusic) 28f else if (isWordSynced) 31f else 29f) * lyricsFontScale
                            currentTextStyle.copy(
                                fontSize = baseSize.sp,
                                lineHeight = (baseSize * 1.36f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                textMotion = TextMotion.Animated,
                            )
                        }
                        val karaokeAccompanimentStyle = remember(currentTextStyle, isAppleMusic, isWordSynced, lyricsFontScale) {
                            val baseSize = (if (isAppleMusic) 21f else if (isWordSynced) 23f else 21f) * lyricsFontScale
                            currentTextStyle.copy(
                                fontSize = baseSize.sp,
                                lineHeight = (baseSize * 1.34f).sp,
                                fontWeight = FontWeight.Bold,
                                textMotion = TextMotion.Animated,
                            )
                        }

                        val layoutDirection = if (isOverallRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        val syncLabel = remember(targetState.source, isWordSynced) {
                            val provider = targetState.source
                                ?.substringBefore(" (")
                                ?.takeIf { it.isNotBlank() } ?: "Lyrics"
                            "${if (isWordSynced) "WORD SYNC" else "LINE SYNC"} • $provider"
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 6.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = liquidGlassContainerColor(
                                            lerp(Color.White.copy(alpha = 0.14f), primaryColor.copy(alpha = 0.28f), 0.45f),
                                        ),
                                        border = BorderStroke(
                                            0.8.dp,
                                            Brush.linearGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.36f),
                                                    primaryColor.copy(alpha = 0.45f),
                                                ),
                                            ),
                                        ),
                                        contentColor = Color.White.copy(alpha = 0.95f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(lerp(primaryColor, Color.White, 0.35f)),
                                            )
                                            Text(
                                                text = syncLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    letterSpacing = 0.6.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            )
                                        }
                                    }
                                }
                                SyncedLyricsProgressHost(
                                    lines = targetState.lines,
                                    isOverallRtl = isOverallRtl,
                                    normalStyle = karaokeNormalStyle,
                                    accompanimentStyle = karaokeAccompanimentStyle,
                                    progressFlow = progressFlow,
                                    progressInitial = progressInitial,
                                    state = state,
                                    player = player,
                                    lyricsOffsetMs = lyricsOffsetMs,
                                    primaryColor = primaryColor,
                                    secondaryColor = secondaryColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                    } else if (!targetState.plainLyrics.isNullOrBlank()) {
                        ModernPlainLyricsView(
                            plainLyrics = targetState.plainLyrics,
                            lyricsFontScale = lyricsFontScale,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ModernEmptyLyricsView(
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

        ModernLyricsControlsHost(
            state = state,
            progressFlow = progressFlow,
            progressInitial = progressInitial,
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
                .padding(bottom = 14.dp, top = 4.dp),
        )
    }
}

private val blurEffectPool = android.util.LruCache<Int, BlurEffect>(32)

private fun getPooledBlurEffect(radius: Float): BlurEffect? {
    if (radius <= 0.3f) return null
    val key = (radius * 2f).roundToInt()
    if (key <= 0) return null
    return blurEffectPool.get(key) ?: run {
        val qRadius = key / 2f
        val effect = BlurEffect(qRadius, qRadius)
        blurEffectPool.put(key, effect)
        effect
    }
}

/**
 * Isolated leaf composable for vocal intro/interlude countdown dots.
 * Only this tiny leaf reads [currentPositionMs] when a >= 4.5s instrumental gap precedes the next line,
 * preventing the parent LazyColumn rows from recomposing at 120Hz!
 */
@Composable
private fun VocalCountdownSlot(
    lineTimeMs: Long,
    currentPositionMs: () -> Long,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val remainingMs = lineTimeMs - currentPositionMs()
    if (remainingMs !in 1L..3500L) return

    val dotStep = (remainingMs.toFloat() / 1166f).coerceIn(0f, 3f)
    val accentDotColor = remember(primaryColor) { lerp(Color.White, primaryColor, 0.35f) }

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 3) {
            val dotThreshold = (3 - i).toFloat()
            val dotActive = dotStep <= dotThreshold
            val alpha = if (dotActive) 0.90f else 0.22f
            val scale = if (dotActive && dotStep in (dotThreshold - 1f)..dotThreshold) {
                0.85f + 0.35f * sin((dotThreshold - dotStep) * Math.PI.toFloat())
            } else 1.0f

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(accentDotColor, CircleShape),
            )
        }
    }
}

@Composable
private fun ModernSyncedLyricsList(
    lines: List<LyricLine>,
    isOverallRtl: Boolean,
    normalStyle: TextStyle,
    accompanimentStyle: TextStyle,
    currentPositionMs: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var userScrolledTime by remember { mutableLongStateOf(0L) }
    val currentPositionMsState by rememberUpdatedState(currentPositionMs)

    val activeIndex by remember(lines) {
        derivedStateOf { activeLineIndex(lines, currentPositionMsState()) }
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
                    val targetY = (viewportHeight * 0.30f).toInt()
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
        modifier = modifier.clipToBounds(),
        contentPadding = PaddingValues(top = 36.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == activeIndex
            val isPast = activeIndex >= 0 && index < activeIndex
            val distance = abs(index - activeIndex)
            val isBgRow = line.syllables.isNotEmpty() && line.syllables.all { it.isBackground }
            val isLineRtl = remember(line, isOverallRtl) {
                line.isRtl || (isOverallRtl && (line.text.isBlank() || line.text == "♪"))
            }

            val scaleTarget = if (isActive) 1.045f else if (distance == 1) 0.99f else 0.97f
            val scale by animateFloatAsState(
                targetValue = scaleTarget,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "modernLyricScale_$index",
            )
            val alphaTarget = if (isActive) 1f else if (distance == 1) 0.58f else if (isPast) 0.36f else 0.28f
            val alpha by animateFloatAsState(
                targetValue = alphaTarget,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                label = "modernLyricAlpha_$index",
            )
            // Only blur distant lines; keep immediate neighbor crisp to cut GPU RenderEffect work in half
            val blurTarget = if (isActive || isDragged || activeIndex < 0 || distance <= 1) 0f
            else ((distance - 1).coerceAtMost(3) * 1.4f)
            val blurRadius by animateFloatAsState(
                targetValue = blurTarget,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "modernLyricBlur_$index",
            )

            // Check gap statically (without reading currentPositionMs() here!)
            val isUpcoming = (activeIndex < 0 && index == 0) || (index == activeIndex + 1)
            val prevEndMs = if (index > 0) {
                val prev = lines[index - 1]
                prev.timeMs + prev.durationMs.coerceAtLeast(0L)
            } else 0L
            val gapMs = line.timeMs - prevEndMs

            if (isUpcoming && isPlaying && gapMs >= 4500L) {
                VocalCountdownSlot(
                    lineTimeMs = line.timeMs,
                    currentPositionMs = currentPositionMs,
                    primaryColor = primaryColor,
                    modifier = Modifier.padding(start = if (isBgRow) 20.dp else 8.dp, bottom = 4.dp),
                )
            }

            val activeGlassAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "modernLyricGlass_$index",
            )

            val lineLayoutDirection = if (isLineRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(if (isLineRtl) 1f else 0f, 0.5f)
                            scaleX = scale
                            scaleY = scale
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.35f) {
                                val r = (blurRadius * density * 2f).roundToInt() / 2f
                                renderEffect = getPooledBlurEffect(r)
                            }
                            this.alpha = alpha * if (isBgRow) (if (isActive) 0.88f else 0.55f) else 1f
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSeek(line.timeMs)
                        }
                        .padding(horizontal = 6.dp, vertical = if (isActive) 6.dp else 3.dp),
                    contentAlignment = if (isBgRow) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    if (activeGlassAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { this.alpha = activeGlassAlpha }
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            lerp(Color.White, primaryColor, 0.35f).copy(alpha = 0.16f * activeGlassAlpha),
                                            lerp(Color.White, secondaryColor, 0.45f).copy(alpha = 0.05f * activeGlassAlpha),
                                        ),
                                    ),
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.34f * activeGlassAlpha),
                                            primaryColor.copy(alpha = 0.30f * activeGlassAlpha),
                                            Color.White.copy(alpha = 0.06f * activeGlassAlpha),
                                        ),
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                ),
                        )
                    }
                    val fontStyle = if (isBgRow) accompanimentStyle else normalStyle
                    ModernWordByWordLine(
                        line = line,
                        currentPositionMs = currentPositionMs,
                        isActive = isActive,
                        activeColor = Color.White,
                        inactiveColor = Color.White.copy(alpha = 0.52f),
                        accentGlowColor = primaryColor,
                        fontStyle = fontStyle,
                        isRtl = isLineRtl,
                        isBackingVocal = isBgRow,
                        modifier = Modifier.padding(
                            horizontal = if (isActive) 14.dp else 6.dp,
                            vertical = if (isActive) 8.dp else 2.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernWordByWordLine(
    line: LyricLine,
    currentPositionMs: () -> Long,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    accentGlowColor: Color,
    fontStyle: TextStyle,
    isRtl: Boolean = false,
    isBackingVocal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lineLayoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
        val activeShadow = remember(accentGlowColor) {
            Shadow(
                color = lerp(Color.White, accentGlowColor, 0.45f).copy(alpha = 0.55f),
                blurRadius = 20f,
                offset = Offset.Zero,
            )
        }
        val activeStyle = remember(fontStyle, isActive, activeShadow) {
            if (isActive) fontStyle.copy(shadow = activeShadow) else fontStyle
        }

        if (!line.hasSyllables || !isActive) {
            val animatedColor by animateColorAsState(
                targetValue = if (isActive) activeColor else inactiveColor.copy(alpha = 0.46f),
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "lineColor",
            )
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = if (isBackingVocal) Alignment.End else Alignment.Start,
            ) {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = activeStyle,
                    color = animatedColor,
                    textAlign = if (isBackingVocal) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!line.transliteration.isNullOrBlank() && isActive) {
                    Text(
                        text = line.transliteration,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp,
                        ),
                        color = activeColor.copy(alpha = 0.74f),
                        textAlign = if (isBackingVocal) TextAlign.End else TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
                    )
                }
            }
            return@CompositionLocalProvider
        }

        val displayTexts = remember(line) { resolveSyllableDisplayTexts(line) }
        val fullText = remember(line, displayTexts) {
            line.syllables.indices.joinToString("") { displayTexts.getOrElse(it) { line.syllables[it].text } }
        }
        val charRanges = remember(line, displayTexts) {
            var offset = 0
            line.syllables.indices.map { i ->
                val start = offset
                offset += displayTexts.getOrElse(i) { line.syllables[i].text }.length
                start to offset
            }
        }
        var layout by remember(fullText) { mutableStateOf<TextLayoutResult?>(null) }

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isBackingVocal) Alignment.End else Alignment.Start,
        ) {
            // Fast Hardware-Accelerated Single-Pass Clip Wipe (Zero saveLayer allocations!)
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fullText,
                    style = fontStyle,
                    color = inactiveColor.copy(alpha = 0.38f),
                    textAlign = if (isBackingVocal) TextAlign.End else TextAlign.Start,
                    onTextLayout = { layout = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = fullText,
                    style = activeStyle,
                    color = activeColor,
                    textAlign = if (isBackingVocal) TextAlign.End else TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            val l = layout ?: return@drawWithContent
                            val textLength = fullText.length
                            if (textLength == 0) return@drawWithContent
                            val pos = currentPositionMs()
                            var fill = 0f
                            var sylIdx = -1
                            var sylP = 0f
                            for (i in line.syllables.indices) {
                                val syl = line.syllables[i]
                                if (pos < syl.timeMs) break
                                val (cs, ce) = charRanges[i]
                                val linear = if (syl.durationMs <= 0L) 1f
                                else ((pos - syl.timeMs).toFloat() / syl.durationMs).coerceIn(0f, 1f)
                                val p = 1f - (1f - linear).toDouble().pow(1.35).toFloat()
                                fill = cs + (ce - cs) * p
                                sylIdx = i
                                sylP = p
                                if (p < 1f) break
                            }
                            if (fill <= 0f) return@drawWithContent
                            if (fill >= textLength) {
                                drawContent()
                                return@drawWithContent
                            }

                            val (sCs, sCe) = charRanges[sylIdx]
                            val startIdx = sCs.coerceIn(0, textLength - 1)
                            val lastIdx = (sCe - 1).coerceIn(startIdx, textLength - 1)
                            val sameLine = l.getLineForOffset(startIdx) == l.getLineForOffset(lastIdx)
                            val activeLineIdx: Int
                            val wipeX: Float
                            if (sameLine) {
                                activeLineIdx = l.getLineForOffset(startIdx)
                                val xs = l.getHorizontalPosition(startIdx, true)
                                val xe = if (sCe < textLength && l.getLineForOffset(sCe) == activeLineIdx) {
                                    l.getHorizontalPosition(sCe, true)
                                } else if (isRtl) l.getLineLeft(activeLineIdx) else l.getLineRight(activeLineIdx)
                                wipeX = xs + (xe - xs) * sylP
                            } else {
                                val charIdx = fill.toInt().coerceIn(0, textLength - 1)
                                val frac = fill - charIdx
                                activeLineIdx = l.getLineForOffset(charIdx)
                                val x0 = l.getHorizontalPosition(charIdx, true)
                                val x1 = if (charIdx + 1 < textLength && l.getLineForOffset(charIdx + 1) == activeLineIdx) {
                                    l.getHorizontalPosition(charIdx + 1, true)
                                } else if (isRtl) l.getLineLeft(activeLineIdx) else l.getLineRight(activeLineIdx)
                                wipeX = x0 + (x1 - x0) * frac
                            }

                            val lineTop = l.getLineTop(activeLineIdx)
                            val lineBottom = l.getLineBottom(activeLineIdx)

                            // 1. Fully sung wrapped lines above the current visual line
                            if (lineTop > 0f) {
                                clipRect(left = 0f, top = 0f, right = size.width, bottom = lineTop) {
                                    this@drawWithContent.drawContent()
                                }
                            }

                            // 2. Current visual line clipped cleanly to wipeX
                            if (isRtl) {
                                clipRect(left = wipeX.coerceIn(0f, size.width), top = lineTop, right = size.width, bottom = lineBottom) {
                                    this@drawWithContent.drawContent()
                                }
                            } else {
                                clipRect(left = 0f, top = lineTop, right = wipeX.coerceIn(0f, size.width), bottom = lineBottom) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        },
                )
            }

            if (!line.transliteration.isNullOrBlank()) {
                Text(
                    text = line.transliteration,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    ),
                    color = activeColor.copy(alpha = 0.76f),
                    textAlign = if (isBackingVocal) TextAlign.End else TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ModernPlainLyricsView(
    plainLyrics: String,
    lyricsFontScale: Float = 1f,
    modifier: Modifier = Modifier,
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
                    fontSize = (26f * lyricsFontScale).sp,
                    lineHeight = (42f * lyricsFontScale).sp,
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
private fun ModernEmptyLyricsView(
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
                TextButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsProgressHost(
    lines: List<LyricLine>,
    isOverallRtl: Boolean,
    normalStyle: TextStyle,
    accompanimentStyle: TextStyle,
    progressFlow: StateFlow<PlaybackProgressState>,
    progressInitial: PlaybackProgressState,
    state: MusicPlayerState,
    player: MusicPlayer,
    lyricsOffsetMs: Long = 0L,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    val progress by progressFlow.collectAsStateWithLifecycle(initialValue = progressInitial)
    val latestSampleMs by rememberUpdatedState(progress.positionMs)
    val offsetState by rememberUpdatedState(lyricsOffsetMs)
    val framePositionMs = remember { mutableLongStateOf(progress.positionMs) }
    val hasAnySyllables = remember(lines) { lines.any { it.hasSyllables } }

    LaunchedEffect(state.isPlaying, hasAnySyllables) {
        // Only run the high-frequency vsync interpolator when the song actually has word-by-word syllables!
        // For line-synced songs, the ~60ms progressFlow updates are already sub-perceptual and use 0% extra CPU.
        if (!state.isPlaying || !hasAnySyllables) {
            snapshotFlow { latestSampleMs }.collect { framePositionMs.longValue = it }
            return@LaunchedEffect
        }
        var lastSample = latestSampleMs
        var sampleFrameNanos = -1L
        var lastFrameNanos = -1L
        var display = latestSampleMs.toDouble()
        while (true) {
            withFrameNanos { now ->
                val sample = latestSampleMs
                if (sample != lastSample || sampleFrameNanos < 0L) {
                    lastSample = sample
                    sampleFrameNanos = now
                }
                val target = sample + ((now - sampleFrameNanos) / 1_000_000.0).coerceAtMost(250.0)
                val frameDt = if (lastFrameNanos < 0L) 0.0 else (now - lastFrameNanos) / 1_000_000.0
                lastFrameNanos = now
                val predicted = display + frameDt
                val err = target - predicted
                display = if (abs(err) > 300.0) {
                    target
                } else {
                    maxOf(display, predicted + err * 0.2)
                }
                framePositionMs.longValue = display.toLong()
            }
        }
    }

    val currentPosition: () -> Long = remember {
        { framePositionMs.longValue + offsetState }
    }

    ModernSyncedLyricsList(
        lines = lines,
        isOverallRtl = isOverallRtl,
        normalStyle = normalStyle,
        accompanimentStyle = karaokeAccompanimentStyleOr(accompanimentStyle),
        currentPositionMs = currentPosition,
        isPlaying = state.isPlaying,
        onSeek = { lineTimeMs ->
            player.seekTo((lineTimeMs - lyricsOffsetMs).coerceAtLeast(0L))
        },
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        modifier = modifier,
    )
}

private fun karaokeAccompanimentStyleOr(style: TextStyle): TextStyle = style

@Composable
private fun ModernLyricsControlsHost(
    state: MusicPlayerState,
    progressFlow: StateFlow<PlaybackProgressState>,
    progressInitial: PlaybackProgressState,
    player: MusicPlayer,
    wavySeekbarEnabled: Boolean = true,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    lyricsOffsetMs: Long = 0L,
    onOpenLyricsOffset: (() -> Unit)? = null,
    lyricsFontScale: Float = 1f,
    onLyricsFontScaleChange: (Float) -> Unit = {},
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    val progress by progressFlow.collectAsStateWithLifecycle(initialValue = progressInitial)
    ModernLyricsControls(
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

@Composable
private fun ModernLyricsControls(
    state: MusicPlayerState,
    currentPositionMs: Long,
    totalDurationMs: Long,
    player: MusicPlayer,
    wavySeekbarEnabled: Boolean = true,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    lyricsOffsetMs: Long = 0L,
    onOpenLyricsOffset: (() -> Unit)? = null,
    lyricsFontScale: Float = 1f,
    onLyricsFontScaleChange: (Float) -> Unit = {},
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    var showFontSlider by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnimatedVisibility(
            visible = showFontSlider,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
        ) {
            LiquidGlassCard(
                shape = RoundedCornerShape(18.dp),
                accentTint = primaryColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                            style = MaterialTheme.typography.labelMedium,
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
                                    color = primaryColor,
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

        if (isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onToggleFullscreen != null) {
                    LiquidGlassIconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        accentTint = primaryColor,
                        contentDescription = "Exit fullscreen lyrics",
                    ) {
                        Icon(
                            Icons.Filled.FullscreenExit,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
            }
            return@Column
        }

        val lyricsTrackKey = state.current?.let { it.videoId ?: "${it.artist}|${it.title}" }

        if (wavySeekbarEnabled) {
            // WavySeekBar renders its own dynamic-tinted time labels that track scrubbing in real time!
            WavySeekBar(
                positionMs = currentPositionMs,
                durationMs = totalDurationMs,
                isPlaying = state.isPlaying,
                onSeek = player::seekTo,
                isTranslucent = true,
                trackKey = lyricsTrackKey,
                showTimeLabels = true,
                modifier = Modifier.fillMaxWidth(),
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                tertiaryColor = tertiaryColor,
            )
        } else {
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
            val end = totalDurationMs.coerceAtLeast(1).toFloat()
            val shown = (dragValue ?: currentPositionMs.coerceIn(0, totalDurationMs.coerceAtLeast(0)).toFloat())
                .coerceIn(0f, end)

            PlayerProgressSlider(
                value = shown,
                onValueChange = {
                    dragValue = it
                    lastSeekValue = it
                },
                onValueChangeFinished = {
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
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(
                    "−${formatTime((totalDurationMs - shown.toLong()).coerceAtLeast(0))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }

        // Unified Bottom Transport & Lyrics Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left utility: Font size toggle
            val isCustomFont = lyricsFontScale != 1.0f
            LiquidGlassIconButton(
                onClick = { showFontSlider = !showFontSlider },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                accentTint = if (showFontSlider || isCustomFont) primaryColor else Color.Unspecified,
                contentDescription = "Adjust lyrics text size",
            ) {
                Icon(
                    Icons.Filled.FormatSize,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (showFontSlider || isCustomFont) primaryColor else Color.White.copy(alpha = 0.90f),
                )
            }

            // Center transport: Previous / Play-Pause / Next
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
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
                        null,
                        Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.94f),
                    )
                }

                LiquidGlassIconButton(
                    onClick = player::togglePlayPause,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    accentTint = primaryColor,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                ) {
                    if (state.isBuffering) {
                        ExpressiveInlineLoadingIndicator(
                            size = 22.dp,
                            color = Color.White,
                            strokeWidth = 2.4.dp,
                        )
                    } else {
                        AnimatedPlayPauseIcon(state.isPlaying, Modifier.size(28.dp))
                    }
                }

                LiquidGlassIconButton(
                    onClick = player::next,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    accentTint = primaryColor,
                    contentDescription = "Next",
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        null,
                        Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.94f),
                    )
                }
            }

            // Right utility: Fullscreen lyrics toggle (or Sync Offset if fullscreen callback absent)
            if (onToggleFullscreen != null) {
                LiquidGlassIconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    contentDescription = "Fullscreen lyrics",
                ) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.90f),
                    )
                }
            } else if (onOpenLyricsOffset != null) {
                val isOffsetActive = lyricsOffsetMs != 0L
                LiquidGlassIconButton(
                    onClick = onOpenLyricsOffset,
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    accentTint = if (isOffsetActive) primaryColor else Color.Unspecified,
                    contentDescription = "Sync offset",
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isOffsetActive) primaryColor else Color.White.copy(alpha = 0.90f),
                    )
                }
            } else {
                Spacer(Modifier.size(42.dp))
            }
        }
    }
}

private suspend fun LazyListState.animateScrollBy(pixels: Float) {
    if (pixels == 0f) return
    var consumed = 0f
    var startNanos = -1L
    var done = false
    while (!done) {
        if (isScrollInProgress) {
            break
        }
        val target = withFrameNanos { now ->
            if (startNanos < 0L) startNanos = now
            val t = ((now - startNanos) / 480_000_000f).coerceIn(0f, 1f)
            done = t >= 1f
            val inv = 1f - t
            val eased = 1f - inv * inv * inv * inv
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
