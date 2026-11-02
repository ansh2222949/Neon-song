package com.neonsong.app.ui.shell

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import android.view.HapticFeedbackConstants
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalView
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.graphics.scale
import com.neonsong.app.ui.theme.drawInteractiveGlass
import com.neonsong.app.ui.theme.liquidGlass
import com.neonsong.app.ui.theme.rememberGlassInteraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.IntBuffer
import kotlin.time.Duration.Companion.seconds
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.PredictiveBackScreen
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.feed.FeedScreen
import com.neonsong.app.ui.home.HomeScreen
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.playlist.PlaylistScreen
import androidx.compose.foundation.shape.CornerBasedShape
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.sign
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassContainerColor
import com.neonsong.app.ui.theme.LayerBackdrop
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.util.LocalHapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Thin bridge exposing AppUpdateManager's live update state to MainShell */
@HiltViewModel
class MainShellViewModel @Inject constructor(
    val appUpdateManager: com.neonsong.app.data.update.AppUpdateManager,
) : ViewModel() {
    val updateInfo = appUpdateManager.updateInfo

    fun dismissUpdate(version: String) {
        appUpdateManager.dismissUpdate(version)
    }

    fun openUpdate(context: android.content.Context) {
        appUpdateManager.openUpdate(context)
    }

    fun downloadAndInstall(context: android.content.Context) {
        appUpdateManager.downloadAndInstall(context)
    }
}

private enum class MainTab(val labelRes: Int) {
    FEED(com.neonsong.app.R.string.nav_feed),
    STATS(com.neonsong.app.R.string.nav_stats),
    PLAYLISTS(com.neonsong.app.R.string.nav_playlists),
}

/** Shared with any screen hosted inside [MainShell] so their scrolling
 *  lists know how much bottom content padding to reserve — the nav
 *  overlays content (it's not a Scaffold bottomBar reserving space), so
 *  each screen leaves this much room for its last item to clear it. */
object FloatingNavDefaults {
    val ContentBottomPadding = 112.dp

    /**
     * Full bottom clearance for edge-to-edge scrolling content: the floating
     * dock's visual height + margins ([ContentBottomPadding]) PLUS the live
     * navigation-bar (gesture area) inset. Screens that let their list draw
     * beneath the transparent gesture area must use this instead of the raw
     * constant, otherwise the last row hides behind the dock/gesture bar.
     */
    @Composable
    fun contentBottomPadding(): Dp =
        ContentBottomPadding +
            LocalMiniPlayerScrollClearance.current +
            WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
}

private val DockShape: CornerBasedShape = RoundedCornerShape(32.dp)
private val PillShape: Shape = CircleShape

private fun <T> navSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)

@Composable
fun MainShell(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenGenres: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFriendProfile: (username: String, displayName: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
    onOpenFeedPlaylist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenGenerator: () -> Unit = {},
    onOpenNewReleases: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    mainShellViewModel: MainShellViewModel = hiltViewModel(),
) {
    val tabs = MainTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Drive the dock selection from an explicit tab index instead of
    // pagerState.currentPage. currentPage flips mid-scroll (halfway through
    // animateScrollToPage), which re-triggers the pill width animation + the
    // generator FAB enter/exit while the pager is still moving — the two
    // competing size animations clip the dock (rectangular, limited) and let
    // the FAB draw over the pill. Updating immediately on tap and syncing from
    // settledPage on swipe keeps one clean transition.
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTabIndex != pagerState.settledPage) {
            selectedTabIndex = pagerState.settledPage
        }
    }
    val updateInfo by mainShellViewModel.updateInfo.collectAsStateWithLifecycle()
    val showUpdateBanner = updateInfo.isUpdateAvailable && !updateInfo.isDismissed
    val backgroundColor = MaterialTheme.colorScheme.background
    // Unconditional remember keeps composition stable; usage gated below.
    val navigationBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val navGlass = isLiquidGlassBackdropSupported()

    Box(Modifier.fillMaxSize()) {
        val feedIndex = tabs.indexOf(MainTab.FEED)
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            modifier = Modifier.fillMaxSize().liquidGlassSource(if (navGlass) navigationBackdrop else null),
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            PredictiveBackScreen(
                enabled = isCurrent && tabs[page] != MainTab.FEED,
                onBack = { scope.launch { pagerState.animateScrollToPage(feedIndex) } },
            ) {
                when (tabs[page]) {
                    MainTab.FEED -> FeedScreen(
                        onOpenSettings = onOpenSettings,
                        onOpenSearch = onOpenSearch,
                        onOpenDiscover = onOpenDiscover,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenFeedPlaylist = onOpenFeedPlaylist,
                        onOpenGenerator = onOpenGenerator,
                        onOpenFriends = onOpenFriends,
                        onOpenFriendProfile = onOpenFriendProfile,
                        onOpenNewReleases = onOpenNewReleases,
                        onOpenDownloads = onOpenDownloads,
                    )
                    MainTab.STATS -> HomeScreen(
                        onOpenSettings = onOpenSettings,
                        onOpenSearch = onOpenSearch,
                        onOpenDiscover = onOpenDiscover,
                        onOpenGenres = onOpenGenres,
                        onOpenFriends = onOpenFriends,
                        onOpenDownloads = onOpenDownloads,
                    )
                    MainTab.PLAYLISTS -> PlaylistScreen(onOpenPlaylist = onOpenPlaylist)
                }
            }
        }

        // App update prompt banner (only shown on app open when an update is available and not dismissed)
        AnimatedVisibility(
            visible = showUpdateBanner,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .adaptiveContentWidth(maxWidth = 600.dp)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(10f),
        ) {
            UpdatePromptCard(
                version = updateInfo.latestVersion,
                onUpdate = { mainShellViewModel.downloadAndInstall(context) },
                onDismiss = { mainShellViewModel.dismissUpdate(updateInfo.latestVersion) },
            )
        }

        val bottomNavSlot = com.neonsong.app.ui.player.LocalBottomNavSlot.current
        val currentSelectedIndex by androidx.compose.runtime.rememberUpdatedState(selectedTabIndex)
        val currentOnSelect by androidx.compose.runtime.rememberUpdatedState { index: Int ->
            if (index != selectedTabIndex) selectedTabIndex = index
            scope.launch { pagerState.animateScrollToPage(index) }
            Unit
        }
        val currentOnOpenGenerator by androidx.compose.runtime.rememberUpdatedState(onOpenGenerator)

        androidx.compose.runtime.DisposableEffect(navigationBackdrop, tabs) {
            bottomNavSlot.value = {
                Box(Modifier.fillMaxSize()) {
                    FloatingNavBar(
                        backdrop = navigationBackdrop,
                        tabs = tabs,
                        selectedIndex = currentSelectedIndex,
                        onSelect = currentOnSelect,
                        onOpenGenerator = currentOnOpenGenerator,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            onDispose { bottomNavSlot.value = {} }
        }
    }
}

@Composable
private fun UpdatePromptCard(
    version: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.neonsong.app.R.string.update_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.neonsong.app.R.string.update_ready_to_install, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onUpdate),
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.neonsong.app.R.string.update),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(com.neonsong.app.R.string.dismiss_update),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FloatingNavBar(
    backdrop: LayerBackdrop?,
    tabs: List<MainTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpenGenerator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquidGlass = LocalLiquidGlass.current
    val fabInteraction = remember { MutableInteractionSource() }
    val isDark = LocalIsDarkTheme.current
    val isGlassActive = liquidGlass && backdrop != null && isLiquidGlassBackdropSupported()
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            tabs.forEachIndexed { index, tab ->
                val onClick = remember(index) { { onSelect(index) } }
                CircularNavItem(
                    label = androidx.compose.ui.res.stringResource(tab.labelRes),
                    icon = tab.icon(),
                    selected = selectedIndex == index,
                    onClick = onClick,
                    backdrop = backdrop,
                )
            }

            // Satellite Companion Generator Button (only visible on Playlists tab).
            AnimatedVisibility(
                visible = selectedIndex == tabs.indexOf(MainTab.PLAYLISTS),
                enter = fadeIn(animationSpec = tween(180)) +
                    scaleIn(initialScale = 0.6f, animationSpec = navSpring()) +
                    expandHorizontally(
                        animationSpec = navSpring(),
                        expandFrom = Alignment.End,
                        clip = false,
                    ),
                exit = fadeOut(animationSpec = tween(120)) +
                    scaleOut(targetScale = 0.6f, animationSpec = navSpring()) +
                    shrinkHorizontally(
                        animationSpec = navSpring(),
                        shrinkTowards = Alignment.End,
                        clip = false,
                    ),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isGlassActive) {
                        liquidGlassContainerColor(MaterialTheme.colorScheme.primaryContainer, backdrop = backdrop)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shadowElevation = if (liquidGlass) 0.dp else 8.dp,
                    tonalElevation = if (liquidGlass) 0.dp else 4.dp,
                    border = BorderStroke(
                        1.5.dp,
                        Brush.verticalGradient(
                            0.0f to Color.White.copy(alpha = if (isDark) 0.40f else 0.55f),
                            1.0f to primaryColor.copy(alpha = 0.35f),
                        ),
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .then(
                            if (isGlassActive) {
                                Modifier.liquidGlassChrome(
                                    CircleShape,
                                    liquidGlass,
                                    LiquidGlassPreset.FloatingControls,
                                    backdrop,
                                    interactionSource = fabInteraction,
                                )
                            } else Modifier
                        )
                        .clickable(interactionSource = fabInteraction, indication = null, onClick = onOpenGenerator),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = androidx.compose.ui.res.stringResource(com.neonsong.app.R.string.nav_create_playlist),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val liquidGlass = LocalLiquidGlass.current
    val isDark = LocalIsDarkTheme.current
    val isGlassActive = liquidGlass && backdrop != null && isLiquidGlassBackdropSupported()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth spring animations for scale and selection transition
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else (if (selected) 1.06f else 1.0f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
        label = "circularNavPressScale",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 380f),
        label = "circularNavIconScale",
    )

    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) {
            if (isGlassActive) 0.38f else 0.95f
        } else {
            if (isGlassActive) 0.12f else 0.40f
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "circularNavFillAlpha",
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) {
            if (isDark) 0.65f else 0.75f
        } else {
            if (isDark) 0.16f else 0.26f
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "circularNavBorderAlpha",
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 0.45f else 0.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "circularNavGlowAlpha",
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            if (isGlassActive) primaryColor else onPrimaryContainerColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "circularNavIconTint",
    )

    val baseFillColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            // Elegant subtle glow for active circular base
            .drawBehind {
                if (glowAlpha > 0f) {
                    val radius = size.minDimension * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = glowAlpha * 0.45f),
                                primaryColor.copy(alpha = glowAlpha * 0.12f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isGlassActive) {
                liquidGlassContainerColor(baseFillColor.copy(alpha = fillAlpha), enabled = true, backdrop = backdrop)
            } else {
                baseFillColor.copy(alpha = fillAlpha)
            },
            tonalElevation = if (isGlassActive) 0.dp else (if (selected) 4.dp else 1.dp),
            shadowElevation = if (isGlassActive) 0.dp else (if (selected) 6.dp else 2.dp),
            border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                brush = if (selected) {
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = (borderAlpha + 0.2f).coerceAtMost(1f)),
                        0.45f to primaryColor.copy(alpha = borderAlpha),
                        1.0f to primaryColor.copy(alpha = borderAlpha * 0.5f),
                    )
                } else {
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = borderAlpha),
                        1.0f to Color.White.copy(alpha = borderAlpha * 0.35f),
                    )
                },
            ),
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isGlassActive && backdrop != null) {
                        Modifier.liquidGlassChrome(
                            CircleShape,
                            enabled = true,
                            preset = LiquidGlassPreset.BottomNavigation,
                            backdrop = backdrop,
                            interactionSource = interactionSource,
                        )
                    } else Modifier
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        hapticManager.click()
                        onClick()
                    },
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Subtle top specular highlight arc inside circle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawCircle(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = if (selected) 0.24f else 0.10f),
                                    0.45f to Color.White.copy(alpha = 0.02f),
                                    1.0f to Color.Transparent,
                                ),
                                radius = size.minDimension / 2f,
                            )
                        }
                )

                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.FEED -> Icons.Filled.Home
    MainTab.STATS -> Icons.Filled.Leaderboard
    MainTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
}
