package com.neonsong.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.ui.common.ExpressiveHeader
import com.neonsong.app.ui.common.NeonLivingCanvas
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.theme.Backdrop
import com.neonsong.app.ui.theme.LocalBottomChromePadding
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.NeonSpacing
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import kotlin.math.abs

@Composable
fun SettingsGlassScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    listState: LazyListState? = null,
    rotatingBackgroundEnabled: Boolean = true,
    reuseParentBackdrop: Boolean = true,
    content: @Composable (contentPadding: PaddingValues, backdrop: Backdrop?) -> Unit,
) {
    val musicPlayer = LocalMusicPlayer.current
    val playbackState by musicPlayer.chromeState.collectAsStateWithLifecycle()
    val currentPlayingTrack = playbackState.current
    val isTrackPlaying = playbackState.isPlaying
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current

    val isScrolling by remember(listState) {
        derivedStateOf { listState?.isScrollInProgress == true }
    }

    val isGlassSupported = isLiquidGlassBackdropSupported()
    val settingsBackdrop = rememberLayerBackdrop { drawContent() }
    val backdrop = if (isGlassSupported) settingsBackdrop else null

    val statusBarTopDp = with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    val headerTopPadding = remember(headerHeightPx, density, statusBarTopDp) {
        if (headerHeightPx > 0f) with(density) { headerHeightPx.toDp() } else statusBarTopDp + 64.dp
    }

    val bottomChromePadding = LocalBottomChromePadding.current
    val contentPadding = remember(headerTopPadding, bottomChromePadding) {
        PaddingValues(
            start = NeonSpacing.ScreenHorizontal,
            end = NeonSpacing.ScreenHorizontal,
            top = headerTopPadding + 12.dp,
            bottom = bottomChromePadding,
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Capture BOTH living canvas + scrolling content inside settingsBackdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassSource(backdrop),
            contentAlignment = Alignment.TopCenter,
        ) {
            NeonLivingCanvas(
                currentTrack = currentPlayingTrack,
                isPlaying = isTrackPlaying,
                isScrolling = isScrolling,
                rotatingBackgroundEnabled = rotatingBackgroundEnabled,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    // Pass null inside source to prevent recursive backdrop self-sampling
                    content(contentPadding, null)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f)
                .onGloballyPositioned { coords ->
                    val newHeight = coords.size.height.toFloat()
                    if (abs(headerHeightPx - newHeight) > 1f) {
                        headerHeightPx = newHeight
                    }
                },
        ) {
            ExpressiveHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                backdrop = backdrop,
                actions = actions ?: {},
            )
        }
    }
}
