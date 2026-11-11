package com.neonsong.app.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.neonsong.app.data.local.LyricsAnimation
import com.neonsong.app.data.lyrics.LyricLine
import com.neonsong.app.features.lyrics.LyricsUiState
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlaybackProgressState
import kotlinx.coroutines.flow.StateFlow


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
    lyricsOffsetMs: Long = 0L,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    com.neonsong.app.features.lyrics.LyricsPanel(
        state = state,
        player = player,
        lyricsState = lyricsState,
        progressState = progressState,
        lyricsAnimation = lyricsAnimation,
        wavySeekbarEnabled = wavySeekbarEnabled,
        onRetry = onRetry,
        onToggleFullscreen = onToggleFullscreen,
        isFullscreen = isFullscreen,
        onOpenLyricsOffset = onOpenLyricsOffset,
        lyricsFontScale = lyricsFontScale,
        onLyricsFontScaleChange = onLyricsFontScaleChange,
        modifier = modifier,
        lyricsOffsetMs = lyricsOffsetMs,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        tertiaryColor = tertiaryColor,
    )
}

internal fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
    com.neonsong.app.features.lyrics.activeLineIndex(lines, positionMs)

internal fun resolveSyllableDisplayTexts(line: LyricLine): List<String> =
    com.neonsong.app.features.lyrics.resolveSyllableDisplayTexts(line)
