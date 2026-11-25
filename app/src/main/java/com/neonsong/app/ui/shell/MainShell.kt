package com.neonsong.app.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.R
import com.neonsong.app.ui.common.ArtworkPaletteCache
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.PredictiveBackScreen
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.feed.FeedScreen
import com.neonsong.app.ui.home.HomeScreen
import com.neonsong.app.ui.player.LocalBottomNavSlot
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.playlist.PlaylistScreen
import com.neonsong.app.ui.theme.LayerBackdrop
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalBottomChromePadding
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.NeonSpacing
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.ui.theme.neonGlassSurface
import com.neonsong.app.ui.theme.neonTactileClick
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import com.neonsong.app.util.LocalHapticManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton

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
    FEED(R.string.nav_feed),
    STATS(R.string.nav_stats),
    PLAYLISTS(R.string.nav_playlists),
}

/** Shared with any screen hosted inside [MainShell] so their scrolling
 *  lists know how much bottom content padding to reserve — the nav
 *  overlays content (it's not a Scaffold bottomBar reserving space), so
 *  each screen leaves this much room for its last item to clear it. */
object FloatingNavDefaults {
    val ContentBottomPadding = 112.dp

    @Composable
    fun contentBottomPadding(): Dp =
        ContentBottomPadding +
            LocalMiniPlayerScrollClearance.current +
            WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
}

private val FloatingDockShape = SquircleShape(31.dp)
private val UpdateCardShape = SquircleShape(22.dp)
private val UpdateButtonShape = SquircleShape(14.dp)

private val PagerSnapSpec = spring<Float>(dampingRatio = 0.90f, stiffness = 420f)
private val NavItemScaleSpring = spring<Float>(
    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
    visibilityThreshold = 0.0005f,
)
private val NavItemColorSpec = tween<Color>(durationMillis = 180, easing = FastOutSlowInEasing)

@Composable
fun MainShell(
    onOpenSettings: () -> Unit,
    onOpenSettingsSection: (String) -> Unit = { onOpenSettings() },
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

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTabIndex != pagerState.settledPage) {
            selectedTabIndex = pagerState.settledPage
        }
    }

    val updateInfo by mainShellViewModel.updateInfo.collectAsStateWithLifecycle()
    val showUpdateBanner = updateInfo.isUpdateAvailable && !updateInfo.isDismissed
    val navigationBackdrop = rememberLayerBackdrop { drawContent() }
    val navGlass = isLiquidGlassBackdropSupported()
    val feedIndex = remember { tabs.indexOf(MainTab.FEED) }

    val musicPlayer = LocalMusicPlayer.current
    val playerState by musicPlayer.state.collectAsStateWithLifecycle()
    val hasActiveTrack = playerState.current != null || LocalMiniPlayerScrollClearance.current > 0.dp

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockHeight = 68.dp
    val miniPlayerHeight = if (hasActiveTrack) 72.dp else 0.dp

    val targetBottomPadding = navBarBottom + dockHeight + miniPlayerHeight + 24.dp
    val animatedBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "bottomChromePadding"
    )

    Box(Modifier.fillMaxSize()) {
        // Capture active tab inside navigationBackdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassSource(if (navGlass) navigationBackdrop else null),
        ) {
            val pagerFling = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = PagerSnapSpec,
            )
            CompositionLocalProvider(LocalBottomChromePadding provides animatedBottomPadding) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    flingBehavior = pagerFling,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val isCurrent by remember(page) {
                        derivedStateOf { pagerState.currentPage == page }
                    }
                    PredictiveBackScreen(
                        enabled = isCurrent && tabs[page] != MainTab.FEED,
                        onBack = {
                            scope.launch {
                                pagerState.animateScrollToPage(feedIndex, animationSpec = PagerSnapSpec)
                            }
                        },
                    ) {
                        when (tabs[page]) {
                            MainTab.FEED -> FeedScreen(
                                onOpenSettings = onOpenSettings,
                                onOpenSettingsSection = onOpenSettingsSection,
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
                            MainTab.PLAYLISTS -> PlaylistScreen(
                                onOpenPlaylist = onOpenPlaylist,
                                onOpenGenerator = onOpenGenerator,
                            )
                        }
                    }
                }
            }
        }

        // App update prompt banner
        AnimatedVisibility(
            visible = showUpdateBanner,
            enter = slideInVertically(
                animationSpec = ExpressiveMotion.appleFluidSpring(),
                initialOffsetY = { -it },
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = ExpressiveMotion.appleFluidSpring(),
                targetOffsetY = { -it },
            ) + fadeOut(tween(140)),
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

        val bottomNavSlot = LocalBottomNavSlot.current
        val currentSelectedIndex by rememberUpdatedState(selectedTabIndex)
        val currentOnSelect by rememberUpdatedState { index: Int ->
            if (index != selectedTabIndex) selectedTabIndex = index
            scope.launch {
                pagerState.animateScrollToPage(index, animationSpec = PagerSnapSpec)
            }
            Unit
        }
        val currentOnOpenSearch by rememberUpdatedState(onOpenSearch)

        DisposableEffect(navigationBackdrop, tabs) {
            bottomNavSlot.value = {
                Box(Modifier.fillMaxSize()) {
                    FloatingNavBar(
                        backdrop = navigationBackdrop,
                        tabs = tabs,
                        selectedIndex = currentSelectedIndex,
                        pageOffset = {
                            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, (tabs.size - 1).toFloat())
                        },
                        onSelect = currentOnSelect,
                        onOpenSearch = currentOnOpenSearch,
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
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        shape = UpdateCardShape,
        color = Color.Transparent,
        border = null,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = UpdateCardShape,
                preset = LiquidGlassPreset.FloatingControls,
                accentTint = primary,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.update_ready_to_install, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
            Surface(
                shape = UpdateButtonShape,
                color = primary,
                modifier = Modifier
                    .clip(UpdateButtonShape)
                    .clickable(onClick = onUpdate),
            ) {
                Text(
                    text = stringResource(R.string.update),
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
                    contentDescription = stringResource(R.string.dismiss_update),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    pageOffset: () -> Float,
    onSelect: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liquidGlass = LocalLiquidGlass.current
    val isDark = LocalIsDarkTheme.current
    val isGlassActive = liquidGlass && backdrop != null && isLiquidGlassBackdropSupported()
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val shadowPx = with(density) { (if (isDark) 18.dp else 12.dp).toPx() }
    val spotColor = Color.Black.copy(alpha = 0.50f)

    val fallbackBg = remember(isDark) {
        (if (isDark) Color(0xFF14161D) else Color(0xFFFDFEFF)).copy(alpha = 0.90f)
    }
    val fallbackBorder = remember(isDark) {
        BorderStroke(
            0.9.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.28f else 0.70f),
                    Color.White.copy(alpha = if (isDark) 0.08f else 0.20f),
                ),
            ),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .widthIn(max = 440.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Primary 3-Tab Liquid Glass Dock with gesture-synced sliding indicator
        Surface(
            shape = FloatingDockShape,
            color = if (isGlassActive) Color.Transparent else fallbackBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isGlassActive) null else fallbackBorder,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 310.dp)
                .height(62.dp)
                .graphicsLayer {
                    shadowElevation = shadowPx
                    shape = FloatingDockShape
                    clip = false
                    val accent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: fallbackPrimary
                    ambientShadowColor = accent.copy(alpha = 0.28f)
                    spotShadowColor = spotColor
                }
                .then(
                    if (isGlassActive && backdrop != null) {
                        Modifier.liquidGlassChrome(
                            shape = FloatingDockShape,
                            enabled = true,
                            preset = LiquidGlassPreset.BottomNavigation,
                            backdrop = backdrop,
                        )
                    } else Modifier.neonGlassSurface(FloatingDockShape),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
            ) {
                // Continuous sliding glass lens indicator (100% draw-phase: zero subcomposition, zero recomposition)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(1f / tabs.size)
                        .graphicsLayer {
                            translationX = pageOffset() * size.width
                        }
                        .drawWithCache {
                            val accent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: fallbackPrimary
                            val fillBrush = Brush.verticalGradient(
                                listOf(
                                    accent.copy(alpha = if (isDark) 0.30f else 0.18f),
                                    (if (isDark) Color.White else accent).copy(alpha = if (isDark) 0.08f else 0.06f),
                                ),
                            )
                            val borderBrush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (isDark) 0.48f else 0.72f),
                                    accent.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = if (isDark) 0.22f else 0.06f),
                                ),
                            )
                            val stroke = Stroke(width = 0.9.dp.toPx())
                            val cornerRadius = CornerRadius(size.height * 0.5f, size.height * 0.5f)
                            onDrawBehind {
                                drawRoundRect(brush = fillBrush, cornerRadius = cornerRadius)
                                drawRoundRect(brush = borderBrush, cornerRadius = cornerRadius, style = stroke)
                            }
                        },
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        LiquidNavTabItem(
                            label = stringResource(tab.labelRes),
                            icon = tab.icon(),
                            selected = selectedIndex == index,
                            onClick = { onSelect(index) },
                        )
                    }
                }
            }
        }

        // Detached Symmetrical Circular Liquid Glass Search Orb
        LiquidSearchOrb(
            backdrop = if (isGlassActive) backdrop else null,
            fallbackBg = fallbackBg,
            fallbackBorder = fallbackBorder,
            fallbackPrimary = fallbackPrimary,
            shadowPx = shadowPx,
            spotColor = spotColor,
            onClick = onOpenSearch,
        )
    }
}

@Composable
private fun RowScope.LiquidNavTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val hapticManager = LocalHapticManager.current
    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (selected) 1.03f else 1.0f,
        animationSpec = NavItemScaleSpring,
        label = "navTabScale",
    )
    val alphaState = animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1.0f,
        animationSpec = NavItemScaleSpring,
        label = "navTabAlpha",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) primary else inactive,
        animationSpec = NavItemColorSpec,
        label = "navTabColor",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .neonTactileClick(pressedScale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiquidSearchOrb(
    backdrop: LayerBackdrop?,
    fallbackBg: Color,
    fallbackBorder: BorderStroke,
    fallbackPrimary: Color,
    shadowPx: Float,
    spotColor: Color,
    onClick: () -> Unit,
) {
    val hapticManager = LocalHapticManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = NavItemScaleSpring,
        label = "searchOrbScale",
    )
    val alphaState = animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1.0f,
        animationSpec = NavItemScaleSpring,
        label = "searchOrbAlpha",
    )

    Surface(
        shape = CircleShape,
        color = if (backdrop != null) Color.Transparent else fallbackBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = if (backdrop != null) null else fallbackBorder,
        modifier = Modifier
            .size(62.dp)
            // Single RenderNode combining shadow + press scale; song palette read deferred to draw phase
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
                alpha = alphaState.value
                shadowElevation = shadowPx
                shape = CircleShape
                clip = false
                val accent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: fallbackPrimary
                ambientShadowColor = accent.copy(alpha = 0.26f)
                spotShadowColor = spotColor
            }
            .then(
                if (backdrop != null) {
                    // BottomNavigation preset matches dock optics without attaching a duplicate press spring
                    Modifier.liquidGlassChrome(
                        shape = CircleShape,
                        enabled = true,
                        preset = LiquidGlassPreset.BottomNavigation,
                        backdrop = backdrop,
                    )
                } else Modifier.neonGlassSurface(CircleShape),
            )
            .neonTactileClick(pressedScale = 0.92f, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.FEED -> Icons.Filled.Home
    MainTab.STATS -> Icons.Filled.Leaderboard
    MainTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
}
