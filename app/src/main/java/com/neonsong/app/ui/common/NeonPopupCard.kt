package com.neonsong.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.SquircleShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Floating Card-Style Dialog backed by the app's multi-color NeonLivingCanvas.
 * Zero live backdrop blur, zero extra animations — instant & crisp.
 */
@Composable
fun NeonPopupCardDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 440.dp,
    maxHeight: Dp = 660.dp,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val musicPlayer = LocalMusicPlayer.current
    val currentTrack by remember(musicPlayer) {
        musicPlayer.chromeState.map { it.current }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = musicPlayer.chromeState.value.current)
    val cardShape = remember { SquircleShape(26.dp) }
    val songAccent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: MaterialTheme.colorScheme.primary
    val rimBrush = remember(isDark, songAccent) {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = if (isDark) 0.36f else 0.80f),
            0.35f to songAccent.copy(alpha = if (isDark) 0.26f else 0.22f),
            0.75f to Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
            1.0f to songAccent.copy(alpha = if (isDark) 0.22f else 0.18f),
        )
    }

    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .widthIn(max = maxWidth)
                .heightIn(max = maxHeight)
                .shadow(
                    elevation = if (isDark) 20.dp else 14.dp,
                    shape = cardShape,
                    ambientColor = songAccent.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                    clip = false,
                )
                .clip(cardShape)
                .border(1.dp, rimBrush, cardShape),
        ) {
            NeonLivingCanvas(
                currentTrack = currentTrack,
                subtleMotion = false,
                modifier = Modifier.matchParentSize(),
            ) {}

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                content = content,
            )
        }
    }
}

/**
 * Floating Card-Style Modal Sheet (detached inset card, same multi-color NeonLivingCanvas).
 * Replaces stock sheets & heavy live-blur modals across Player, AutoEQ, Lyrics, and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonPopupCardSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 600.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalIsDarkTheme.current
    val musicPlayer = LocalMusicPlayer.current
    val currentTrack by remember(musicPlayer) {
        musicPlayer.chromeState.map { it.current }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = musicPlayer.chromeState.value.current)
    val cardShape = remember { SquircleShape(28.dp) }
    val songAccent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: MaterialTheme.colorScheme.primary
    val rimBrush = remember(isDark, songAccent) {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = if (isDark) 0.36f else 0.80f),
            0.35f to songAccent.copy(alpha = if (isDark) 0.26f else 0.22f),
            0.75f to Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
            1.0f to songAccent.copy(alpha = if (isDark) 0.22f else 0.18f),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = cardShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.50f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = null,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = maxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp + safeDrawingBottomPadding())
                .shadow(
                    elevation = if (isDark) 20.dp else 14.dp,
                    shape = cardShape,
                    ambientColor = songAccent.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                    clip = false,
                )
                .clip(cardShape)
                .border(1.dp, rimBrush, cardShape),
        ) {
            NeonLivingCanvas(
                currentTrack = currentTrack,
                subtleMotion = false,
                modifier = Modifier.matchParentSize(),
            ) {}

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                // Minimal drag indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
                    )
                }

                content()
            }
        }
    }
}
