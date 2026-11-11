package com.neonsong.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.neonsong.app.R
import com.neonsong.app.core.designsystem.components.SectionLabel
import com.neonsong.app.core.designsystem.components.SettingBadgeChip
import com.neonsong.app.core.designsystem.components.StatusChip
import com.neonsong.app.core.designsystem.components.settingHighlightGlow
import com.neonsong.app.data.local.AccentMode
import com.neonsong.app.data.local.AppLanguage
import com.neonsong.app.data.local.HomeSection
import com.neonsong.app.data.local.LyricsAnimation
import com.neonsong.app.data.local.LyricsProvider
import com.neonsong.app.data.local.LyricsUiVersion
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.local.nativeDisplayName
import com.neonsong.app.features.settings.about.AboutCard
import com.neonsong.app.features.settings.appearance.AccentPresetGrid
import com.neonsong.app.features.settings.appearance.AppearanceContent
import com.neonsong.app.features.settings.appearance.ColorWheelSheet
import com.neonsong.app.features.settings.audio.AudioStudioHero
import com.neonsong.app.features.settings.audio.AutomixBarsRow
import com.neonsong.app.features.settings.audio.ClarityPresetDialog
import com.neonsong.app.features.settings.audio.CrossfadeDurationRow
import com.neonsong.app.features.settings.audio.EqualizerSheet
import com.neonsong.app.features.settings.audio.LoudnessModeDialog
import com.neonsong.app.features.settings.library.LyricsAnimationSheet
import com.neonsong.app.features.settings.library.LyricsProviderDialog
import com.neonsong.app.features.settings.library.SyncPlaylistsSheet
import com.neonsong.app.features.settings.library.YouTubeChannelSheet
import com.neonsong.app.features.settings.library.YouTubeLibraryVisibilitySheet
import com.neonsong.app.features.settings.playback.LAST_FM_CREATE_KEY_URL
import com.neonsong.app.features.settings.playback.LastFmIntegrationCard
import com.neonsong.app.features.settings.playback.YouTubeAccountRow
import com.neonsong.app.features.settings.playback.lastSyncSuffix
import com.neonsong.app.features.settings.playback.relativeTime
import com.neonsong.app.playback.ClarityPresets
import com.neonsong.app.playback.LoudnessMode
import com.neonsong.app.ui.common.EdgeToEdgeDialogWindow
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.ExpressiveGroupRow
import com.neonsong.app.ui.common.ExpressiveGroupSelectRow
import com.neonsong.app.ui.common.ExpressiveGroupToggleRow
import com.neonsong.app.ui.common.ExpressiveHeader
import com.neonsong.app.ui.common.GroupGap
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.GroupInnerRadius
import com.neonsong.app.ui.common.GroupOuterRadius
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.HeaderActionIcon
import com.neonsong.app.ui.common.LiquidGlassCard
import com.neonsong.app.ui.common.NeonAppLogo
import com.neonsong.app.ui.common.NeonBrand
import com.neonsong.app.ui.common.SpatialImmersionPresetSelector
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.groupPositionFor
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.common.rememberExpressiveHeaderBackdrop
import com.neonsong.app.ui.common.rememberGroupPressScaleState
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.common.safeHorizontalContentPadding
import com.neonsong.app.ui.player.FluidArtworkBackground
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.LyricsOffsetDialog
import com.neonsong.app.ui.player.LyricsSizeDialog
import com.neonsong.app.ui.player.PlayingWaveBars
import com.neonsong.app.ui.shell.FloatingNavDefaults
import com.neonsong.app.ui.theme.ExpressivePillShape
import com.neonsong.app.ui.theme.GlassActionButton as Button
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import com.neonsong.app.ui.theme.GlassOutlinedActionButton as OutlinedButton
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import com.neonsong.app.ui.theme.GlassTonalActionButton as FilledTonalButton
import com.neonsong.app.ui.theme.GlassTonalIconActionButton as FilledTonalIconButton
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.LocalLiquidGlass
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.isLiquidGlassBackdropSupported
import com.neonsong.app.ui.theme.liquidGlassSource
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.rememberLayerBackdrop
import com.neonsong.app.util.BatteryOptimizationHelper
import com.neonsong.app.util.LocalHapticManager
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// Bridge extension so GroupPosition cleanly maps to core components
private fun GroupPosition.toCore(): com.neonsong.app.core.designsystem.components.GroupPosition =
    com.neonsong.app.core.designsystem.components.GroupPosition.valueOf(this.name)

internal fun com.neonsong.app.core.designsystem.components.GroupPosition.toCommon(): GroupPosition =
    GroupPosition.valueOf(this.name)

/**
 * Settings Navigation Tabs for the Sub-Page ("Page inside page") architecture.
 */
enum class SettingsTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val eyebrow: String,
    val heroSummary: String,
) {
    AUDIO(
        title = "Audio & Playback",
        subtitle = "Streaming quality, Audio engine, Equalizer, Output & Loudness",
        icon = Icons.Filled.GraphicEq,
        eyebrow = "AUDIO & DSP ENGINE",
        heroSummary = "Studio Master Clarity • 16-Band Mastering EQ • Lossless Audio Engine • 192kHz",
    ),
    APPEARANCE(
        title = "Appearance & Visuals",
        subtitle = "Themes, Colors, Artwork, Lyrics",
        icon = Icons.Filled.Palette,
        eyebrow = "VISUAL STYLES & GLASS",
        heroSummary = "Dynamic Liquid Glass • Material 3 Theming • Animated Video Canvas • Typography",
    ),
    YOUTUBE(
        title = "YouTube & Sync",
        subtitle = "Account connection, 24/7 Library sync, Channels, History",
        icon = Icons.Filled.CloudSync,
        eyebrow = "YOUTUBE MUSIC SUITE",
        heroSummary = "Account Connection • 24/7 Library Mirroring • Custom Channel Feeds & Shelves",
    ),
    LAST_FM(
        title = "Last.fm",
        subtitle = "Account connection, Scrobbling sync & API credentials",
        icon = Icons.Filled.Album,
        eyebrow = "LAST.FM SCROBBLER",
        heroSummary = "Global Scrobbles • Listening Statistics • Real-Time Scrobble Synchronization",
    ),
    LIBRARY(
        title = "Library & Content",
        subtitle = "Home layout, Playlist imports, Downloads, Exclusions",
        icon = Icons.Filled.QueueMusic,
        eyebrow = "LIBRARY ARCHITECTURE",
        heroSummary = "Feed Shelves Layout • Playlist Importers • Offline Cache • Exclusions",
    ),
    HOME_SECTIONS(
        title = "Home Feed Layout",
        subtitle = "Feed sections visibility, order and YouTube Music shelves",
        icon = Icons.Filled.Dashboard,
        eyebrow = "FEED ARCHITECTURE",
        heroSummary = "Configure which sections appear on your Home feed and customize YouTube Music shelves",
    ),
    DATA_BACKUP(
        title = "Data & Storage",
        subtitle = "Backup & Restore, Cache clearing, CSV history, Reset data",
        icon = Icons.Filled.Backup,
        eyebrow = "STORAGE & DATA MANAGEMENT",
        heroSummary = "Device Space Breakdown • Disk Cache Cleaner • SAF JSON Mirroring & Reset",
    ),
    ABOUT(
        title = "About & System",
        subtitle = "App language, Updates, Community, Diagnostics, Source code",
        icon = Icons.Filled.AutoAwesome,
        eyebrow = "NEON SONG AUDIO",
        heroSummary = "Audiophile Streaming Player • Architecture, Language & Community Diagnostics",
    ),
}

/**
 * Determines target LazyColumn item index (section) for a searchable setting.
 */
private fun getTargetSectionIndex(settingId: String?): Int {
    if (settingId == null) return 0
    val entry = SettingsSearchIndex.allEntries.firstOrNull { it.id == settingId } ?: return 0
    return when (entry.parentTab) {
        SettingsTab.AUDIO -> when (entry.section) {
            "Output & Loudness" -> 2
            "Audiophile DSP & Mastering" -> 3
            else -> 1
        }
        SettingsTab.APPEARANCE -> when (entry.section) {
            "Accent Color" -> 2
            "App Icon & Brand Emblem" -> 3
            "Player & Lyrics Experience", "Experimental & Features", "Experimental & Player" -> 4
            "Canvas" -> 5
            else -> 1
        }
        SettingsTab.LIBRARY -> when (entry.section) {
            "Playlist Importers", "Imports" -> 2
            "Provider Modules & Addons", "Modules & Addons" -> 3
            else -> 1
        }
        SettingsTab.HOME_SECTIONS -> if (entry.section.contains("YouTube")) 2 else 1
        SettingsTab.DATA_BACKUP -> when {
            entry.id == "backup.clear_all" -> 3
            entry.section == "Backup & Restore" -> 2
            else -> 1
        }
        SettingsTab.ABOUT -> if (entry.section == "About & Community" || entry.section.contains("About")) 2 else 1
        else -> 0
    }
}

/**
 * Main Settings Destination of Neon Song.
 * Fluid Song-Reactive Artwork Background + Liquid Glass Optical Refraction System.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenModules: () -> Unit = {},
    onOpenHomeSections: () -> Unit = {},
    onOpenExcludedSongs: () -> Unit = {},
    onOpenYouTubeImport: () -> Unit = {},
    onOpenYouTubeLogin: () -> Unit = {},
    onOpenExternalImport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val avatarUrl by viewModel.avatarUrl.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val misc by viewModel.misc.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle()
    val downloadTotalBytes by viewModel.downloadTotalBytes.collectAsStateWithLifecycle()
    val ytConnection by viewModel.ytConnection.collectAsStateWithLifecycle()
    val ytSyncEnabled by viewModel.ytSyncEnabled.collectAsStateWithLifecycle()
    val ytHistorySyncEnabled by viewModel.ytHistorySyncEnabled.collectAsStateWithLifecycle()
    val ytSyncState by viewModel.ytSyncState.collectAsStateWithLifecycle()
    val ytLastSyncAt by viewModel.ytLastSyncAt.collectAsStateWithLifecycle()
    val syncedPlaylistIds by viewModel.syncedPlaylistIds.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val ytAccountPlaylists by viewModel.ytAccountPlaylists.collectAsStateWithLifecycle()
    val hiddenYtLibraryPlaylistIds by viewModel.hiddenYtLibraryPlaylistIds.collectAsStateWithLifecycle()
    val ytChannels by viewModel.ytChannels.collectAsStateWithLifecycle()
    val ytChannelsLoading by viewModel.ytChannelsLoading.collectAsStateWithLifecycle()
    val eq by viewModel.equalizer.collectAsStateWithLifecycle()
    val usbExclusiveEnabled by viewModel.usbExclusiveEnabled.collectAsStateWithLifecycle()
    val loudness by viewModel.loudness.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isLastFmConnected by viewModel.isLastFmConnected.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val lastFmAuthUrl by viewModel.lastFmAuthUrl.collectAsStateWithLifecycle()
    val lastFmConnecting by viewModel.lastFmConnecting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Live music player & song reactivity (replicated 1:1 from FeedScreen.kt)
    val musicPlayer = LocalMusicPlayer.current
    val playbackState by musicPlayer.chromeState.collectAsStateWithLifecycle()
    val currentPlayingTrack = playbackState.current
    val isTrackPlaying = playbackState.isPlaying
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = LocalIsDarkTheme.current
    val rotatingBackgroundEnabled = misc.rotatingBackgroundEnabled

    // Backdrops & glass refraction
    val settingsBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val isGlassSupported = isLiquidGlassBackdropSupported()

    // Last.fm web auth launch
    LaunchedEffect(lastFmAuthUrl) {
        lastFmAuthUrl?.let { url ->
            runCatching {
                androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                    .launchUrl(context, Uri.parse(url))
            }.onFailure {
                viewModel.showToast("No browser available to connect Last.fm")
                viewModel.cancelLastFmConnect()
            }
        }
    }

    var showAudioQualityScreen by rememberSaveable { mutableStateOf(false) }
    var qualityPickerTarget by rememberSaveable { mutableStateOf<AudioQualityTarget?>(null) }
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showDownloadQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showEqSheet by rememberSaveable { mutableStateOf(false) }
    var showAutoEq by rememberSaveable { mutableStateOf(false) }
    var showLyricsAnimationSheet by rememberSaveable { mutableStateOf(false) }
    var showLyricsProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSizeDialog by rememberSaveable { mutableStateOf(false) }
    var showLoudnessDialog by rememberSaveable { mutableStateOf(false) }
    var showClarityPresetDialog by rememberSaveable { mutableStateOf(false) }
    var showSyncPlaylistsSheet by rememberSaveable { mutableStateOf(false) }
    var showYtLibraryVisibilitySheet by rememberSaveable { mutableStateOf(false) }
    var showYtChannelSheet by rememberSaveable { mutableStateOf(false) }
    var showYtDisconnectConfirm by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showYouTubeImportSheet by remember { mutableStateOf(false) }
    val currentLanguage = remember(misc.appLanguageTag) { AppLanguage.fromTag(misc.appLanguageTag) }

    // SAF Launchers
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.handleRestorePicked(uri)
    }
    val csvPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.handleCsvPicked(uri)
    }
    val settingsPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setSettingsPhoto(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri, appVersionName(context))
    }

    var activeTab by rememberSaveable { mutableStateOf<SettingsTab?>(null) }
    var isSearchBarVisible by rememberSaveable { mutableStateOf(false) }
    var highlightedSettingId by remember { mutableStateOf<String?>(null) }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val headerTopPadding = remember(headerHeightPx, density) {
        if (headerHeightPx > 0f) with(density) { headerHeightPx.toDp() } else 145.dp
    }
    val focusManager = LocalFocusManager.current

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val mainSettingsListState = rememberLazyListState()
    val tabListStates = remember { mutableMapOf<SettingsTab, LazyListState>() }
    val currentActiveListState = if (activeTab == null) mainSettingsListState else tabListStates.getOrPut(activeTab!!) { LazyListState() }
    val isScrolling by remember(currentActiveListState) { derivedStateOf { currentActiveListState.isScrollInProgress } }

    LaunchedEffect(highlightedSettingId) {
        if (highlightedSettingId != null) {
            delay(3200L)
            highlightedSettingId = null
        }
    }

    BackHandler(
        enabled = showEqSheet ||
            qualityPickerTarget != null ||
            showAudioQualityScreen ||
            searchQuery.isNotBlank() ||
            isSearchBarVisible ||
            activeTab != null
    ) {
        when {
            showEqSheet -> showEqSheet = false
            qualityPickerTarget != null -> qualityPickerTarget = null
            showAudioQualityScreen -> showAudioQualityScreen = false
            searchQuery.isNotBlank() || isSearchBarVisible -> {
                viewModel.clearSearch()
                isSearchBarVisible = false
            }
            activeTab != null -> activeTab = null
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    CompositionLocalProvider(
        androidx.compose.foundation.LocalOverscrollConfiguration provides null,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Live Song-Reactive Background (Identical to FeedScreen.kt)
            if (currentPlayingTrack != null) {
                FluidArtworkBackground(
                    track = currentPlayingTrack,
                    modifier = Modifier.fillMaxSize(),
                    extraBlur = true,
                    rotatingBackgroundEnabled = rotatingBackgroundEnabled,
                    isPlaying = isTrackPlaying &&
                        !isScrolling &&
                        !showAudioQualityScreen &&
                        qualityPickerTarget == null &&
                        !showEqSheet &&
                        !showAutoEq,
                    fallback = {
                        Box(Modifier.fillMaxSize().background(backgroundColor))
                    },
                )
                val baseScrimAlpha = if (isDark) 0.58f else 0.84f
                val baseScrimColor = if (isDark) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = baseScrimAlpha }
                        .background(baseScrimColor),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                )
            }

            // Adaptive Container with Real-Time Backdrop Glass Refraction (Matches FeedScreen.kt lines 351-356)
            // Placing liquidGlassSource around the scrollable content lets settingsBackdrop capture
            // the scrolling text/cards so they blur smoothly beneath the floating header!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .adaptiveContentWidth(maxWidth = 780.dp)
                    .liquidGlassSource(if (isGlassSupported) settingsBackdrop else null),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (qualityPickerTarget != null) {
                    val target = qualityPickerTarget!!
                    AppleAudioQualityPickerScreen(
                        target = target,
                        preferLossless = misc.preferLosslessStreaming,
                        selectedQualityId = when (target) {
                            AudioQualityTarget.CELLULAR_STREAMING -> misc.cellularStreamingQuality
                            AudioQualityTarget.WIFI_STREAMING -> misc.wifiStreamingQuality
                            AudioQualityTarget.DOWNLOADS -> misc.downloadQuality
                        },
                        onSelectTier = { tier ->
                            when (target) {
                                AudioQualityTarget.CELLULAR_STREAMING -> viewModel.setCellularStreamingQuality(tier.qualityId)
                                AudioQualityTarget.WIFI_STREAMING -> viewModel.setWifiStreamingQuality(tier.qualityId)
                                AudioQualityTarget.DOWNLOADS -> viewModel.setDownloadQuality(tier.qualityId)
                            }
                        },
                        onBack = { qualityPickerTarget = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showAudioQualityScreen) {
                    AppleAudioQualityScreen(
                        preferLossless = misc.preferLosslessStreaming,
                        cellularQuality = misc.cellularStreamingQuality,
                        wifiQuality = misc.wifiStreamingQuality,
                        downloadQuality = misc.downloadQuality,
                        dolbyAtmosMode = misc.dolbyAtmosMode,
                        onToggleLossless = { viewModel.setPreferLosslessStreaming(it) },
                        onNavigateToTarget = { target -> qualityPickerTarget = target },
                        onSelectDolbyAtmos = { viewModel.setDolbyAtmosMode(it) },
                        onBack = { showAudioQualityScreen = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (searchQuery.isNotBlank()) {
                    SettingsSearchResultsList(
                        query = searchQuery,
                        topPadding = headerTopPadding + if (isSearchBarVisible) 68.dp else 0.dp,
                        results = searchResults,
                        onResultClick = { entry ->
                            focusManager.clearFocus()
                            viewModel.clearSearch()
                            isSearchBarVisible = false
                            activeTab = entry.parentTab
                            highlightedSettingId = entry.id
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            if (targetState != null && initialState == null) {
                                (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> width / 4 } + fadeIn()).togetherWith(
                                    slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width / 4 } + fadeOut(),
                                )
                            } else if (targetState == null && initialState != null) {
                                (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width / 4 } + fadeIn()).togetherWith(
                                    slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> width / 4 } + fadeOut(),
                                )
                            } else {
                                (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> width / 4 } + fadeIn()).togetherWith(
                                    slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width -> -width / 4 } + fadeOut(),
                                )
                            }
                        },
                        label = "SettingsSubPageTransition",
                        modifier = Modifier.fillMaxSize(),
                    ) { tab ->
                        val listState = if (tab == null) mainSettingsListState else tabListStates.getOrPut(tab) { LazyListState() }

                        LaunchedEffect(highlightedSettingId, tab) {
                            if (highlightedSettingId != null && tab != null) {
                                val targetIndex = getTargetSectionIndex(highlightedSettingId)
                                if (targetIndex > 0) {
                                    delay(80L)
                                    listState.animateScrollToItem(targetIndex)
                                } else if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                                    delay(80L)
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = headerTopPadding + 12.dp + if (isSearchBarVisible) 68.dp else 0.dp,
                                bottom = FloatingNavDefaults.contentBottomPadding() + 28.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .safeHorizontalContentPadding(),
                        ) {
                            when (tab) {
                                null -> {
                                    // 1. 3D Profile & Now-Playing Suite Hero Card
                                    item(key = "root_profile_hero", contentType = "hero") {
                                        val displayName = when {
                                            session.username.isNotBlank() -> session.username
                                            ytConnection.isConnected && ytConnection.accountName.isNotBlank() -> ytConnection.accountName
                                            else -> "Neon Song User"
                                        }
                                        val effectiveDp = misc.settingsPhotoUri.takeIf { it.isNotBlank() }
                                            ?: avatarUrl?.takeIf { it.isNotBlank() }

                                        SettingsProfileHero(
                                            displayName = displayName,
                                            effectiveDp = effectiveDp,
                                            isYtConnected = ytConnection.isConnected,
                                            isLastFmConnected = isLastFmConnected,
                                            downloadCount = downloadCount,
                                            appVersion = appVersionName(context),
                                            currentTrackTitle = currentPlayingTrack?.title,
                                            currentTrackArtist = currentPlayingTrack?.artist,
                                            isPlaying = isTrackPlaying,
                                            onAvatarClick = {
                                                settingsPhotoLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            onOpenDownloads = onOpenDownloads,
                                            onOpenYouTube = { activeTab = SettingsTab.YOUTUBE },
                                            onOpenLastFm = { activeTab = SettingsTab.LAST_FM },
                                        )
                                    }

                                    // 2. 4-Card Quick Access Bento Row
                                    item(key = "root_bento_row", contentType = "bento") {
                                        val audioLabel = if (misc.preferLosslessStreaming) {
                                            AppleAudioQualityTier.fromQualityId(misc.wifiStreamingQuality).title
                                        } else {
                                            "Standard High"
                                        }
                                        val homeSectionsActive = "${HomeSection.entries.size - misc.hiddenHomeSections.size} / ${HomeSection.entries.size} Active"
                                        val appearanceLabel = when (theme.themeMode) {
                                            ThemeMode.AMOLED -> "AMOLED Pure"
                                            ThemeMode.DARK -> "Dark Theme"
                                            ThemeMode.LIGHT -> "Light Theme"
                                            ThemeMode.SYSTEM -> "System Auto"
                                        }
                                        val storageLabel = if (downloadCount > 0) "$downloadCount Offline" else "Storage & Cache"

                                        SettingsQuickBentoRow(
                                            audioLabel = audioLabel,
                                            homeSectionsLabel = homeSectionsActive,
                                            appearanceLabel = appearanceLabel,
                                            storageLabel = storageLabel,
                                            onOpenAudio = { activeTab = SettingsTab.AUDIO },
                                            onOpenHomeLayout = { activeTab = SettingsTab.HOME_SECTIONS },
                                            onOpenAppearance = { activeTab = SettingsTab.APPEARANCE },
                                            onOpenStorage = { activeTab = SettingsTab.DATA_BACKUP },
                                        )
                                    }

                                    // 3. Services & Integrations Section
                                    item(key = "root_services_section", contentType = "section") {
                                        SettingsGlassSection(
                                            title = "Services & Add-ons",
                                            subtitle = "Connected accounts, streaming extensions & scrobbling",
                                        ) {
                                            ExpressiveGroup(rowCount = 3) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.Extension,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_modules_title),
                                                        subtitle = stringResource(R.string.settings_modules_sub),
                                                        onClick = onOpenModules,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "services.modules" || highlightedSettingId == "library.modules"),
                                                        trailingBadge = {
                                                            SettingBadgeChip(text = "Extensions", color = MaterialTheme.colorScheme.tertiary)
                                                        },
                                                    )
                                                    1 -> SettingsActionCard(
                                                        icon = Icons.Filled.Album,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Last.fm",
                                                        subtitle = if (isLastFmConnected) "Connected as ${session.username} • Live sync active" else "Connect account, scrobbles & statistics sync",
                                                        onClick = { activeTab = SettingsTab.LAST_FM },
                                                        position = position,
                                                        trailingBadge = {
                                                            if (isLastFmConnected) {
                                                                SettingBadgeChip(text = "Active", color = MaterialTheme.colorScheme.primary)
                                                            } else {
                                                                SettingBadgeChip(text = "Setup", color = MaterialTheme.colorScheme.secondary)
                                                            }
                                                        },
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.CloudSync,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "YouTube & Sync",
                                                        subtitle = if (ytConnection.isConnected) "Connected as ${ytConnection.accountName} • 24/7 mirroring" else "Connect account, 24/7 playlist sync, channels",
                                                        onClick = { activeTab = SettingsTab.YOUTUBE },
                                                        position = position,
                                                        trailingBadge = {
                                                            if (ytConnection.isConnected) {
                                                                SettingBadgeChip(text = "Synced", color = MaterialTheme.colorScheme.primary)
                                                            } else {
                                                                SettingBadgeChip(text = "Connect", color = MaterialTheme.colorScheme.secondary)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 4. Playback & Experience Section
                                    item(key = "root_playback_section", contentType = "section") {
                                        SettingsGlassSection(
                                            title = "Playback & UI",
                                            subtitle = "Audio processing, visuals, home layout & imports",
                                        ) {
                                            ExpressiveGroup(rowCount = 3) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.GraphicEq,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Audio & Playback",
                                                        subtitle = "Streaming quality, Audio engine, Equalizer, Output & Loudness",
                                                        onClick = { activeTab = SettingsTab.AUDIO },
                                                        position = position,
                                                        trailingBadge = {
                                                            SettingBadgeChip(
                                                                text = if (misc.losslessQuality in listOf(6, 7, 27, 28)) "Lossless" else "Standard",
                                                                color = MaterialTheme.colorScheme.primary,
                                                            )
                                                        },
                                                    )
                                                    1 -> SettingsActionCard(
                                                        icon = Icons.Filled.Palette,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Appearance & Visuals",
                                                        subtitle = "Themes, Colors, Artwork, Lyrics & Video Canvas",
                                                        onClick = { activeTab = SettingsTab.APPEARANCE },
                                                        position = position,
                                                        trailingBadge = {
                                                            SettingBadgeChip(
                                                                text = theme.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                                                                color = MaterialTheme.colorScheme.tertiary,
                                                            )
                                                        },
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.QueueMusic,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Library & Content",
                                                        subtitle = if (downloadCount > 0) "$downloadCount offline songs • Imports, Home layout" else "Home layout, Playlist imports, Downloads, Exclusions",
                                                        onClick = { activeTab = SettingsTab.LIBRARY },
                                                        position = position,
                                                        trailingBadge = {
                                                            if (downloadCount > 0) {
                                                                SettingBadgeChip(text = "$downloadCount dl", color = MaterialTheme.colorScheme.secondary)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 5. System & Storage Section
                                    item(key = "root_system_section", contentType = "section") {
                                        SettingsGlassSection(
                                            title = "System & Storage",
                                            subtitle = "Storage metrics, JSON backups, Language & Updates",
                                        ) {
                                            ExpressiveGroup(rowCount = 2) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.Backup,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Data & Storage",
                                                        subtitle = "Backup & Restore, Cache clearing, CSV history, Reset data",
                                                        onClick = { activeTab = SettingsTab.DATA_BACKUP },
                                                        position = position,
                                                        trailingBadge = {
                                                            SettingBadgeChip(text = "Storage", color = MaterialTheme.colorScheme.tertiary)
                                                        },
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.AutoAwesome,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "About & System",
                                                        subtitle = if (updateInfo.isUpdateAvailable) "Update Available (${updateInfo.latestVersion}) • v${appVersionName(context)}" else "v${appVersionName(context)} • Language, Diagnostics, Community",
                                                        onClick = { activeTab = SettingsTab.ABOUT },
                                                        position = position,
                                                        trailingBadge = {
                                                            if (updateInfo.isUpdateAvailable) {
                                                                SettingBadgeChip(text = "Update", color = MaterialTheme.colorScheme.error)
                                                            } else {
                                                                SettingBadgeChip(text = "v${appVersionName(context)}", color = MaterialTheme.colorScheme.outline)
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                SettingsTab.AUDIO -> {
                                    item(key = "audio_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.AUDIO)
                                    }
                                    item(key = "audio_streaming_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_audio), subtitle = "Streaming bit-depth, Auto DJ & playback transitions") {
                                            val isIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                                            val audioRows = buildList<@Composable (GroupPosition) -> Unit> {
                                                add { position ->
                                                    val currentTier = AppleAudioQualityTier.fromQualityId(misc.wifiStreamingQuality)
                                                    val subtitleText = if (misc.preferLosslessStreaming) {
                                                        "${currentTier.title} • Lossless Audio On"
                                                    } else {
                                                        "High Quality • Lossless Audio Off"
                                                    }
                                                    SettingsActionCard(
                                                        icon = Icons.Filled.HighQuality,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Audio Quality",
                                                        subtitle = subtitleText,
                                                        onClick = { showAudioQualityScreen = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.streaming_quality" || highlightedSettingId == "audio.quality"),
                                                        trailingBadge = {
                                                            Text(
                                                                text = if (misc.preferLosslessStreaming) currentTier.title else "Off",
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                            )
                                                        },
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Tune,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.settings_bit_perfect),
                                                        subtitle = if (misc.isBitPerfectEnabled) stringResource(R.string.settings_bit_perfect_on_detail) else stringResource(R.string.settings_bit_perfect_off_detail),
                                                        checked = misc.isBitPerfectEnabled,
                                                        onCheckedChange = viewModel::setBitPerfectEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.bit_perfect"),
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.AutoAwesome,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Auto DJ",
                                                        subtitle = if (misc.autoDJEnabled) "Autonomous queue replenishment & predictive prefetch" else "Automatic queue management disabled",
                                                        checked = misc.autoDJEnabled,
                                                        onCheckedChange = viewModel::setAutoDJEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.autodj"),
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.AutoAwesome,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Automix (AutoDJ)",
                                                        subtitle = if (misc.automixEnabled) "Beat-synced transitions with bass crossover • ${misc.automixBars} bars" else "Seamless tempo-synced DJ blends between queued tracks",
                                                        checked = misc.automixEnabled,
                                                        onCheckedChange = viewModel::setAutomixEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.automix"),
                                                    )
                                                }
                                                if (misc.automixEnabled) {
                                                    add { position ->
                                                        AutomixBarsRow(
                                                            bars = misc.automixBars,
                                                            onBarsChange = viewModel::setAutomixBars,
                                                            position = position.toCore(),
                                                            isHighlighted = (highlightedSettingId == "audio.automix_bars"),
                                                        )
                                                    }
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.GraphicEq,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_crossfade),
                                                        subtitle = if (misc.automixEnabled) "Disabled while Automix is active" else if (misc.crossfadeEnabled && misc.isBitPerfectEnabled) "Paused while Bit-Perfect is enabled" else if (misc.crossfadeEnabled) "Smooth transition between tracks • ${misc.crossfadeSeconds} sec" else "Blend the end of a track into the next one",
                                                        checked = misc.crossfadeEnabled,
                                                        onCheckedChange = viewModel::setCrossfadeEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.crossfade"),
                                                    )
                                                }
                                                if (misc.crossfadeEnabled) {
                                                    add { position ->
                                                        CrossfadeDurationRow(
                                                            seconds = misc.crossfadeSeconds,
                                                            onSecondsChange = viewModel::setCrossfadeSeconds,
                                                            position = position.toCore(),
                                                            isHighlighted = (highlightedSettingId == "audio.crossfade_duration"),
                                                        )
                                                    }
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Lyrics,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_download_lyrics),
                                                        subtitle = if (misc.downloadLyrics) "Save .lrc companion files & embed lyrics in downloads" else "Do not fetch or save lyrics when downloading",
                                                        checked = misc.downloadLyrics,
                                                        onCheckedChange = viewModel::setDownloadLyrics,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.download_lyrics"),
                                                    )
                                                }
                                                if (!isIgnored) {
                                                    add { position ->
                                                        SettingsActionCard(
                                                            icon = Icons.Filled.Bolt,
                                                            iconContainer = MaterialTheme.colorScheme.errorContainer,
                                                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                                            title = stringResource(R.string.settings_battery_title),
                                                            subtitle = "Restricted • Tap to exempt from Samsung Device Care / sleeping apps",
                                                            onClick = { BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context) },
                                                            position = position,
                                                            isHighlighted = (highlightedSettingId == "audio.battery_optimization"),
                                                        )
                                                    }
                                                }
                                            }
                                            ExpressiveGroup(rowCount = audioRows.size) { index, position ->
                                                audioRows[index](position)
                                            }
                                        }
                                    }

                                    item(key = "audio_output_section") {
                                        SettingsGlassSection(title = "Output & Loudness", subtitle = "DAC routing, mastering equalizer & clarity curve") {
                                            val clarityPreset = remember(misc.clarityPreset) { ClarityPresets.fromIndex(misc.clarityPreset) }
                                            val loudnessSubtitle = when (loudness.mode) {
                                                LoudnessMode.TRACK -> "Track • Match every track to -14 LUFS"
                                                LoudnessMode.ALBUM -> "Album • Keep intentional album dynamics"
                                                else -> "Off • Play tagged tracks at original level"
                                            }
                                            ExpressiveGroup(rowCount = 8) { index, position ->
                                                when (index) {
                                                    0 -> SettingsToggleCard(
                                                        icon = Icons.Filled.Usb,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "USB Exclusive Output",
                                                        subtitle = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) "Requires Android 10 or newer" else if (usbExclusiveEnabled || misc.isBitPerfectEnabled) "Direct usbdevfs to the DAC • Bit-Perfect engages automatically" else "Off • Needs a USB DAC and Bit-Perfect (or this toggle)",
                                                        checked = usbExclusiveEnabled,
                                                        onCheckedChange = viewModel::setUsbExclusiveEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.usb_exclusive"),
                                                    )
                                                    1 -> SettingsActionCard(
                                                        icon = Icons.Filled.VolumeUp,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Loudness Normalization",
                                                        subtitle = loudnessSubtitle,
                                                        onClick = { showLoudnessDialog = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.loudness_normalization"),
                                                    )
                                                    2 -> SettingsActionCard(
                                                        icon = Icons.Filled.GraphicEq,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_equalizer),
                                                        subtitle = if (misc.isBitPerfectEnabled && eq.enabled) "On • ${eq.presetName} (Bypassed by Bit-Perfect Mode)" else if (eq.enabled) "On • ${eq.presetName} • 16-band Studio EQ" else "16-Band Studio Mastering Equalizer (20 Hz - 20 kHz) & AutoEq",
                                                        onClick = { showEqSheet = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.equalizer"),
                                                    )
                                                    3 -> SettingsToggleCard(
                                                        icon = Icons.Filled.VolumeUp,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "System Audio Effects",
                                                        subtitle = if (misc.systemEffectsMode) "External effects active • in-app EQ bypassed" else "Let Dolby / equalizer apps process playback",
                                                        checked = misc.systemEffectsMode,
                                                        onCheckedChange = viewModel::setSystemEffectsMode,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.system_audio_effects"),
                                                    )
                                                    4 -> SettingsToggleCard(
                                                        icon = Icons.Filled.Waves,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_studio_clarity),
                                                        subtitle = if (misc.isStudioMasterClarityEnabled) "Crystal-clear open sound • airy detail • deep separation" else "Original unshaped output",
                                                        checked = misc.isStudioMasterClarityEnabled,
                                                        onCheckedChange = viewModel::setStudioMasterClarity,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.studio_clarity"),
                                                    )
                                                    5 -> SettingsActionCard(
                                                        icon = Icons.Filled.Tune,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Clarity Output Preset",
                                                        subtitle = "${clarityPreset.displayName} • ${clarityPreset.description}",
                                                        onClick = { showClarityPresetDialog = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.clarity_preset"),
                                                    )
                                                    6 -> SettingsToggleCard(
                                                        icon = Icons.Filled.GraphicEq,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Clarity Spatial Bypass",
                                                        subtitle = if (misc.clarityAtmosBypass) "Clarity bypasses on multichannel/spatial sources" else "Off • Clarity processes every source",
                                                        checked = misc.clarityAtmosBypass,
                                                        onCheckedChange = viewModel::setClarityAtmosBypass,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.clarity_spatial_bypass"),
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.Headphones,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.autoeq_title),
                                                        subtitle = com.neonsong.app.ui.settings.autoeq.autoEqSummary(),
                                                        onClick = { showAutoEq = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.autoeq"),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    item(key = "audio_dsp_section") {
                                        SettingsGlassSection(title = "Audiophile DSP & Mastering", subtitle = "Headroom, acoustic crossfeed, tube warmth & haptics") {
                                            val curveLabel = when (misc.automixCurveType) {
                                                1 -> "Smoothstep S-Curve • Gentle fade"
                                                2 -> "Club Bass Swap • Midpoint crossover"
                                                3 -> "Fast Drop • High-energy drop"
                                                else -> "Equal Power (L² + R² = 1.0) • Constant energy"
                                            }
                                            val activeHapticMode = com.neonsong.app.playback.HapticMode.fromId(misc.musicHapticsMode)
                                            val activeHapticIntensity = com.neonsong.app.playback.HapticIntensity.fromId(misc.musicHapticsIntensity)

                                            val dspRows = buildList<@Composable (GroupPosition) -> Unit> {
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Headphones,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Bluetooth True-Peak Headroom",
                                                        subtitle = if (misc.bluetoothHeadroomEnabled) "-1.2 dBFS True-Peak margin • auto-engages on Bluetooth" else "Off • 0.0 dBFS unity output",
                                                        checked = misc.bluetoothHeadroomEnabled,
                                                        onCheckedChange = viewModel::setBluetoothHeadroomEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "audio.bluetooth_headroom"),
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Headphones,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Bauer Crossfeed",
                                                        subtitle = if (misc.bauerCrossfeedEnabled) "Headphone crossfeed • softer hard-panned mixes" else "Off • original stereo image",
                                                        checked = misc.bauerCrossfeedEnabled,
                                                        onCheckedChange = viewModel::setBauerCrossfeedEnabled,
                                                        position = position,
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Bolt,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Analog Tube Warmth",
                                                        subtitle = if (misc.analogTubeWarmthEnabled) "Gentle asymmetric harmonics • subtle warmth" else "Off • transparent playback",
                                                        checked = misc.analogTubeWarmthEnabled,
                                                        onCheckedChange = viewModel::setAnalogTubeWarmthEnabled,
                                                        position = position,
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Waves,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Mono Bass (<130 Hz)",
                                                        subtitle = if (misc.monoBassEnabled) "Centers low-frequency side energy below 130 Hz" else "Off • full stereo bass",
                                                        checked = misc.monoBassEnabled,
                                                        onCheckedChange = viewModel::setMonoBassEnabled,
                                                        position = position,
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.GraphicEq,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "3D Spatial & Studio Immersion",
                                                        subtitle = if (misc.spatialImmersionEnabled) "Active • Phase-pure vocal lock & Haas 3D soundstage" else "Off • standard stereo",
                                                        checked = misc.spatialImmersionEnabled,
                                                        onCheckedChange = viewModel::setSpatialImmersionEnabled,
                                                        position = position,
                                                    )
                                                }
                                                if (misc.spatialImmersionEnabled) {
                                                    add {
                                                        SpatialImmersionPresetSelector(
                                                            selectedPreset = misc.spatialImmersionPreset,
                                                            onPresetSelected = viewModel::setSpatialImmersionPreset,
                                                        )
                                                    }
                                                }
                                                add { position ->
                                                    SettingsActionCard(
                                                        icon = Icons.Filled.AutoAwesome,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "AutoMix DJ Curve",
                                                        subtitle = curveLabel,
                                                        onClick = {
                                                            val next = (misc.automixCurveType + 1) % 4
                                                            viewModel.setAutomixCurveType(next)
                                                        },
                                                        position = position,
                                                    )
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.NotificationsActive,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Music Haptics (Tactile Beats)",
                                                        subtitle = if (misc.musicHapticsEnabled) "Active • ${activeHapticMode.label} (${activeHapticIntensity.label})" else "Off • Tactile beats disabled",
                                                        checked = misc.musicHapticsEnabled,
                                                        onCheckedChange = viewModel::setMusicHapticsEnabled,
                                                        position = position,
                                                    )
                                                }
                                                if (misc.musicHapticsEnabled) {
                                                    add { position ->
                                                        MusicHapticsConfigRow(
                                                            selectedMode = activeHapticMode,
                                                            selectedIntensity = activeHapticIntensity,
                                                            onSelectMode = { viewModel.setMusicHapticsMode(it.id) },
                                                            onSelectIntensity = { viewModel.setMusicHapticsIntensity(it.id) },
                                                            position = position,
                                                        )
                                                    }
                                                }
                                                add { position ->
                                                    SettingsToggleCard(
                                                        icon = Icons.Filled.Tune,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Smart Device Audio Profile",
                                                        subtitle = if (misc.smartRouteAutoSwitchEnabled) "Active • Auto-switches Clarity & restores device EQ" else "Off • Manual Clarity & EQ selection",
                                                        checked = misc.smartRouteAutoSwitchEnabled,
                                                        onCheckedChange = viewModel::setSmartRouteAutoSwitchEnabled,
                                                        position = position,
                                                    )
                                                }
                                            }
                                            ExpressiveGroup(rowCount = dspRows.size) { index, position ->
                                                dspRows[index](position)
                                            }
                                        }
                                    }
                                }

                                SettingsTab.APPEARANCE -> {
                                    item(key = "appearance_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.APPEARANCE)
                                    }
                                    item(key = "appearance_modes_section") {
                                        AppearanceContent(
                                            themeUiState = theme,
                                            miscSettings = misc,
                                            highlightedSettingId = highlightedSettingId,
                                            onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                                            onSetAmoled = { enabled ->
                                                viewModel.setThemeMode(if (enabled) ThemeMode.AMOLED else ThemeMode.DARK)
                                            },
                                            onSetDynamicColor = { enabled ->
                                                viewModel.setAccentMode(if (enabled) AccentMode.DYNAMIC else AccentMode.MANUAL)
                                            },
                                            onSetDynamicNowPlaying = viewModel::setDynamicNowPlaying,
                                            onSetUseCustomFont = viewModel::setUseCustomFont,
                                            onOpenHomeSections = { activeTab = SettingsTab.HOME_SECTIONS },
                                            onPickSettingsPhoto = {
                                                runCatching {
                                                    settingsPhotoLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }.onFailure { viewModel.showToast("No image picker is available") }
                                            },
                                            onRemoveSettingsPhoto = { viewModel.setSettingsPhoto(null) },
                                        )
                                    }
                                    item(key = "appearance_accent_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_accent), subtitle = "Signature tones & custom color wheel") {
                                            AccentPresetGrid(
                                                currentMode = theme?.mode ?: AccentMode.MANUAL,
                                                selectedHex = theme?.accentColorHex,
                                                onPickPreset = { hex -> viewModel.setManualAccent(Color(android.graphics.Color.parseColor(hex))) },
                                                onPickMono = { viewModel.setAccentMode(AccentMode.MONOCHROME) },
                                                onPickCustom = viewModel::openColorWheel,
                                            )
                                        }
                                    }
                                    item(key = "appearance_brand_icon_section") {
                                        SettingsGlassSection(title = "App Icon & Brand Emblem", subtitle = "Choose your preferred home launcher mark") {
                                            AppIconShowcaseSelector()
                                        }
                                    }
                                    item(key = "appearance_player_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_experimental), subtitle = "Lyrics renderer, fluid rotating background & player buttons") {
                                            ExpressiveGroup(rowCount = 7) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.Lyrics,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_lyrics_animation),
                                                        subtitle = if (misc.lyricsUiVersion == LyricsUiVersion.MODERN) "New UI (Modern)" else "${misc.lyricsAnimation.title} • ${misc.lyricsAnimation.description}",
                                                        onClick = { showLyricsAnimationSheet = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.lyrics_animation"),
                                                    )
                                                    1 -> SettingsToggleCard(
                                                        icon = Icons.Filled.Waves,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_wavy_seekbar),
                                                        subtitle = if (misc.wavySeekbarEnabled) stringResource(R.string.settings_wavy_seekbar_on) else stringResource(R.string.settings_wavy_seekbar_off),
                                                        checked = misc.wavySeekbarEnabled,
                                                        onCheckedChange = viewModel::setWavySeekbarEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.wavy_seekbar"),
                                                    )
                                                    2 -> SettingsToggleCard(
                                                        icon = Icons.Filled.RestartAlt,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.settings_rotating_background),
                                                        subtitle = if (misc.rotatingBackgroundEnabled) stringResource(R.string.settings_rotating_background_on) else stringResource(R.string.settings_rotating_background_off),
                                                        checked = misc.rotatingBackgroundEnabled,
                                                        onCheckedChange = viewModel::setRotatingBackgroundEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.rotating_background"),
                                                    )
                                                    3 -> SettingsActionCard(
                                                        icon = Icons.Filled.Lyrics,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_lyrics_provider),
                                                        subtitle = "${misc.lyricsProvider.title} • ${misc.lyricsProvider.subtitle}",
                                                        onClick = { showLyricsProviderDialog = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.lyrics_provider"),
                                                    )
                                                    4 -> SettingsActionCard(
                                                        icon = Icons.Filled.Timer,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Lyrics sync offset",
                                                        subtitle = if (misc.lyricsOffsetMs == 0L) "Off • highlight follows audio exactly" else "${if (misc.lyricsOffsetMs > 0) "+" else ""}${misc.lyricsOffsetMs} ms • sync adjustment",
                                                        onClick = { showLyricsOffsetDialog = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.lyrics_offset"),
                                                    )
                                                    5 -> SettingsActionCard(
                                                        icon = Icons.Filled.FormatSize,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Lyrics text size",
                                                        subtitle = "${(misc.lyricsFontScale * 100).roundToInt()}% • ${when { misc.lyricsFontScale < 0.85f -> "Small"; misc.lyricsFontScale in 0.85f..1.15f -> "Standard"; else -> "Large" }}",
                                                        onClick = { showLyricsSizeDialog = true },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.lyrics_size"),
                                                    )
                                                    else -> SettingsToggleCard(
                                                        icon = Icons.Filled.PlayCircle,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Button Now Playing",
                                                        subtitle = if (misc.buttonNowPlayingEnabled) "Floating button mode active • Card hidden" else "Classic bottom now playing card",
                                                        checked = misc.buttonNowPlayingEnabled,
                                                        onCheckedChange = viewModel::setButtonNowPlayingEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.button_now_playing"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "appearance_canvas_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_canvas_enabled), subtitle = "Spotify-style background video loops") {
                                            val canvasRowCount = if (misc.canvasEnabled) 3 else 1
                                            ExpressiveGroup(rowCount = canvasRowCount) { index, position ->
                                                when (index) {
                                                    0 -> SettingsToggleCard(
                                                        icon = Icons.Filled.SmartDisplay,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.settings_canvas_enabled),
                                                        subtitle = stringResource(R.string.settings_canvas_enabled_sub),
                                                        checked = misc.canvasEnabled,
                                                        onCheckedChange = viewModel::setCanvasEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.canvas_enabled"),
                                                    )
                                                    1 -> SettingsToggleCard(
                                                        icon = Icons.Filled.Visibility,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_canvas_full_bleed),
                                                        subtitle = stringResource(R.string.settings_canvas_full_bleed_sub),
                                                        checked = misc.canvasFullBleed,
                                                        onCheckedChange = viewModel::setCanvasFullBleed,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.canvas_full_bleed"),
                                                    )
                                                    else -> SettingsToggleCard(
                                                        icon = Icons.Filled.CloudDownload,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_canvas_cellular),
                                                        subtitle = stringResource(R.string.settings_canvas_cellular_sub),
                                                        checked = misc.canvasOverCellular,
                                                        onCheckedChange = viewModel::setCanvasOverCellular,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "appearance.canvas_cellular"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                SettingsTab.YOUTUBE -> {
                                    item(key = "youtube_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.YOUTUBE)
                                    }
                                    item(key = "youtube_account_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_youtube), subtitle = "Seamless YouTube Music mirroring & channel feeds") {
                                            val ytConnected = ytConnection.isConnected
                                            val syncSubtitle = when (val sync = ytSyncState) {
                                                is com.neonsong.app.data.ytmusic.YtSyncState.Running -> "Syncing ${sync.current}/${sync.total} • ${sync.label}"
                                                is com.neonsong.app.data.ytmusic.YtSyncState.Completed -> "Playlists mirrored to your account • synced ${relativeTime(sync.atMillis)}"
                                                is com.neonsong.app.data.ytmusic.YtSyncState.Failed -> "Last pass failed — will retry automatically"
                                                else -> if (!ytConnected) "Connect an account first" else if (ytSyncEnabled) "Playlists mirror 24/7" + lastSyncSuffix(ytLastSyncAt) else "Keep your YT Music library in sync with Neon Song"
                                            }
                                            val currentChannelName = ytChannels.firstOrNull {
                                                it.channelId == ytConnection.onBehalfOfUser &&
                                                    it.authUserIndex == ytConnection.authUserIndex &&
                                                    it.pageId == ytConnection.pageId
                                            }?.accountName ?: ytConnection.accountName
                                            val ytRowCount = if (ytConnected) 7 else 3

                                            ExpressiveGroup(rowCount = ytRowCount) { index, position ->
                                                when (index) {
                                                    0 -> if (ytConnected) {
                                                        YouTubeAccountRow(
                                                            accountName = ytConnection.accountName,
                                                            channelHandle = ytConnection.channelHandle,
                                                            onDisconnect = { showYtDisconnectConfirm = true },
                                                            position = position.toCore(),
                                                            isHighlighted = (highlightedSettingId == "youtube.account"),
                                                        )
                                                    } else {
                                                        SettingsActionCard(
                                                            icon = Icons.Filled.CloudSync,
                                                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            title = stringResource(R.string.settings_connect_yt),
                                                            subtitle = stringResource(R.string.settings_connect_yt_sub),
                                                            onClick = onOpenYouTubeLogin,
                                                            position = position,
                                                            isHighlighted = (highlightedSettingId == "youtube.account"),
                                                        )
                                                    }
                                                    1 -> SettingsToggleCard(
                                                        icon = Icons.Filled.CloudSync,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_yt_sync),
                                                        subtitle = syncSubtitle,
                                                        checked = ytConnected && ytSyncEnabled,
                                                        enabled = ytConnected,
                                                        onCheckedChange = viewModel::setYtSyncEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "youtube.sync"),
                                                    )
                                                    2 -> if (ytConnected) {
                                                        SettingsActionCard(
                                                            icon = Icons.Filled.SwitchAccount,
                                                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            title = "YouTube channel",
                                                            subtitle = currentChannelName.ifBlank { "Default channel" },
                                                            onClick = {
                                                                viewModel.loadYtChannels()
                                                                showYtChannelSheet = true
                                                            },
                                                            position = position,
                                                            isHighlighted = (highlightedSettingId == "youtube.channel"),
                                                        )
                                                    } else {
                                                        SettingsActionCard(
                                                            icon = Icons.Filled.QueueMusic,
                                                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            title = "Import from YouTube Music",
                                                            subtitle = "Search, browse, or paste playlist links & IDs",
                                                            onClick = onOpenYouTubeImport,
                                                            position = position,
                                                            isHighlighted = (highlightedSettingId == "youtube.import"),
                                                        )
                                                    }
                                                    3 -> {
                                                        val selectedCount = syncedPlaylistIds?.size ?: allPlaylists.size
                                                        val syncCountText = if (syncedPlaylistIds == null || selectedCount == allPlaylists.size) {
                                                            "All (${allPlaylists.size}) playlists syncing"
                                                        } else {
                                                            "$selectedCount of ${allPlaylists.size} playlists selected"
                                                        }
                                                        SettingsActionCard(
                                                            icon = Icons.Filled.FormatListBulleted,
                                                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            title = stringResource(R.string.settings_select_playlists),
                                                            subtitle = syncCountText,
                                                            onClick = { showSyncPlaylistsSheet = true },
                                                            position = position,
                                                            isHighlighted = (highlightedSettingId == "youtube.select_playlists"),
                                                        )
                                                    }
                                                    4 -> {
                                                        val shownCount = ytAccountPlaylists.count { it.id !in hiddenYtLibraryPlaylistIds }
                                                        val visibilitySubtitle = if (shownCount == ytAccountPlaylists.size) {
                                                            "All (${ytAccountPlaylists.size}) account playlists shown"
                                                         } else {
                                                            "$shownCount of ${ytAccountPlaylists.size} account playlists shown"
                                                        }
                                                        SettingsActionCard(
                                                            icon = Icons.Filled.Visibility,
                                                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            title = stringResource(R.string.settings_yt_shown),
                                                            subtitle = visibilitySubtitle,
                                                            onClick = { showYtLibraryVisibilitySheet = true },
                                                            position = position,
                                                            isHighlighted = (highlightedSettingId == "youtube.shown_playlists"),
                                                        )
                                                    }
                                                    5 -> SettingsActionCard(
                                                        icon = Icons.Filled.QueueMusic,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.settings_make_local),
                                                        subtitle = stringResource(R.string.settings_make_local_sub),
                                                        onClick = onOpenYouTubeImport,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "youtube.import"),
                                                    )
                                                    else -> SettingsToggleCard(
                                                        icon = Icons.Filled.History,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_yt_history),
                                                        subtitle = if (ytHistorySyncEnabled) "On • listening in Neon Song updates your YouTube history" else "Off • listening stays out of your YouTube history",
                                                        checked = ytHistorySyncEnabled,
                                                        onCheckedChange = viewModel::setYtHistorySyncEnabled,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "youtube.history"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "youtube_shelves_section") {
                                        SettingsGlassSection(title = "YouTube Music Shelves Layout", subtitle = "Reorder or hide shelves displayed on your Home feed") {
                                            YouTubeMusicShelvesEditor(
                                                order = misc.ytMusicSectionsOrder,
                                                hidden = misc.ytMusicHiddenSections,
                                                onSave = viewModel::saveYtMusicSections,
                                                onReset = viewModel::resetYtMusicSections,
                                            )
                                        }
                                    }
                                }

                                SettingsTab.LAST_FM -> {
                                    item(key = "lastfm_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.LAST_FM)
                                    }
                                    item(key = "lastfm_card_section") {
                                        SettingsGlassSection(title = "Last.fm Scrobbler", subtitle = "Global scrobbles, charts & profile synchronization") {
                                            LastFmIntegrationCard(
                                                isConnected = isLastFmConnected,
                                                username = session.username,
                                                avatarUrl = avatarUrl,
                                                connecting = lastFmConnecting,
                                                awaitingApproval = lastFmAuthUrl != null,
                                                hasApiKey = hasApiKey,
                                                isHighlighted = (highlightedSettingId in listOf("lastfm.connect", "lastfm.api_credentials")),
                                                onConnect = { viewModel.beginLastFmConnect() },
                                                onCancel = viewModel::cancelLastFmConnect,
                                                onDisconnect = viewModel::disconnectLastFm,
                                                onSaveKeys = viewModel::saveApiCredentials,
                                                onRemoveKey = viewModel::clearApiKey,
                                                onOpenCreateKeyPage = {
                                                    runCatching {
                                                        androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                                                            .launchUrl(context, Uri.parse(LAST_FM_CREATE_KEY_URL))
                                                    }.onFailure {
                                                        viewModel.showToast("No browser available to open Last.fm")
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }

                                SettingsTab.LIBRARY -> {
                                    item(key = "library_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.LIBRARY)
                                    }
                                    item(key = "library_home_section") {
                                        SettingsGlassSection(title = "Home & Organization", subtitle = "Feed layout customization and exclusion filters") {
                                            ExpressiveGroup(rowCount = 2) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.Dashboard,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.settings_home_sections),
                                                        subtitle = run {
                                                            val total = HomeSection.entries.size
                                                            val visible = total - misc.hiddenHomeSections.size
                                                            stringResource(R.string.home_sections_visible, visible, total)
                                                        },
                                                        onClick = { activeTab = SettingsTab.HOME_SECTIONS },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "library.home_sections"),
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.RestartAlt,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_excluded_songs),
                                                        subtitle = "${state.recommendationExclusionCount} songs excluded",
                                                        onClick = onOpenExcludedSongs,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "library.excluded_songs"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "library_imports_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_imports), subtitle = "Bring your music from YouTube, Spotify, Apple Music & local files") {
                                            ExpressiveGroup(rowCount = 3) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.QueueMusic,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Import from YouTube Music",
                                                        subtitle = "Search, browse, or paste playlist links & IDs",
                                                        onClick = onOpenYouTubeImport,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "library.import_yt"),
                                                    )
                                                    1 -> SettingsActionCard(
                                                        icon = Icons.Filled.QueueMusic,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = "Import from Spotify / Apple Music",
                                                        subtitle = "Paste a public playlist link to resolve tracks",
                                                        onClick = onOpenExternalImport,
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "library.import_external"),
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.FileDownload,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = stringResource(R.string.settings_import_file),
                                                        subtitle = stringResource(R.string.settings_import_file_sub),
                                                        onClick = {
                                                            runCatching {
                                                                csvPickerLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "audio/x-mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                                                            }.onFailure { viewModel.showToast("No file picker is available") }
                                                        },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "library.import_file"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "library_modules_section") {
                                        SettingsGlassSection(title = "Provider Modules & Addons", subtitle = "Third-party audio providers & metadata resolution") {
                                            ExpressiveGroup(rowCount = 2) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.Extension,
                                                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        title = "Manage Modules",
                                                        subtitle = "Install, configure or disable providers",
                                                        onClick = onOpenModules,
                                                        position = position,
                                                    )
                                                    else -> SettingsToggleCard(
                                                        icon = Icons.Filled.Bolt,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = "Prefer Provider Modules",
                                                        subtitle = if (misc.preferProviderModules) "Module hits play immediately on cache miss" else "Standard provider resolution sequence",
                                                        checked = misc.preferProviderModules,
                                                        onCheckedChange = viewModel::setPreferProviderModules,
                                                        position = position,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                SettingsTab.HOME_SECTIONS -> {
                                    item(key = "home_sections_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.HOME_SECTIONS)
                                    }
                                    item(key = "home_sections_toggles") {
                                        val totalSections = HomeSection.entries.size
                                        val visibleCount = totalSections - misc.hiddenHomeSections.size
                                        SettingsGlassSection(
                                            title = "Home Feed Sections",
                                            subtitle = "Toggle visibility for main screen categories",
                                            trailingBadge = {
                                                SettingBadgeChip(text = "$visibleCount of $totalSections Active", color = MaterialTheme.colorScheme.primary)
                                            },
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    TextButton(onClick = viewModel::showAllHomeSections) {
                                                        Text("Show All Sections", fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                                ExpressiveGroup(rowCount = HomeSection.entries.size) { index, position ->
                                                    val section = HomeSection.entries[index]
                                                    val isHidden = section.id in misc.hiddenHomeSections
                                                    val sectionIcon = when (section) {
                                                        HomeSection.HERO -> Icons.Filled.Home
                                                        HomeSection.QUICK_TILES -> Icons.Filled.Dashboard
                                                        HomeSection.TASTE_STRIP -> Icons.Filled.Sell
                                                        HomeSection.QUICK_PICKS -> Icons.Filled.Bolt
                                                        HomeSection.BECAUSE_YOU_LISTEN_TO -> Icons.Filled.History
                                                        HomeSection.FRESH_FINDS -> Icons.Filled.Whatshot
                                                        HomeSection.JUMP_BACK_IN -> Icons.Filled.Replay
                                                        HomeSection.MIXES -> Icons.Filled.Shuffle
                                                        HomeSection.SPOTLIGHT -> Icons.Filled.Star
                                                        HomeSection.TOP_ARTISTS -> Icons.Filled.Group
                                                        HomeSection.HEAVY_ROTATION -> Icons.Filled.Favorite
                                                        HomeSection.ALBUMS -> Icons.Filled.Album
                                                        HomeSection.CHARTS -> Icons.Filled.TrendingUp
                                                        HomeSection.NEW_RELEASES -> Icons.Filled.NewReleases
                                                        HomeSection.FRIENDS -> Icons.Filled.Group
                                                    }
                                                    ExpressiveGroupToggleRow(
                                                        icon = sectionIcon,
                                                        title = stringResource(section.titleRes),
                                                        subtitle = stringResource(section.subtitleRes),
                                                        checked = !isHidden,
                                                        onCheckedChange = { isVisible ->
                                                            viewModel.setHomeSectionVisible(section.id, isVisible)
                                                        },
                                                        position = position,
                                                        iconContainer = if (!isHidden) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        iconTint = if (!isHidden) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "home_yt_shelves") {
                                        SettingsGlassSection(title = "YouTube Music Shelves", subtitle = "Custom feed shelves from YouTube Music") {
                                            YouTubeMusicShelvesEditor(
                                                order = misc.ytMusicSectionsOrder,
                                                hidden = misc.ytMusicHiddenSections,
                                                onSave = viewModel::saveYtMusicSections,
                                                onReset = viewModel::resetYtMusicSections,
                                            )
                                        }
                                    }
                                }

                                SettingsTab.DATA_BACKUP -> {
                                    item(key = "backup_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.DATA_BACKUP)
                                    }
                                    item(key = "storage_breakdown_section") {
                                        SettingsGlassSection(title = "Storage Metrics & Cleanup", subtitle = "Detailed disk breakdown and 1-tap cache purges") {
                                            SettingsStorageCard(
                                                downloadTotalBytes = downloadTotalBytes ?: 0L,
                                                downloadCount = downloadCount,
                                                exclusionCount = state.recommendationExclusionCount,
                                                onClearArtworkCache = {
                                                    runCatching {
                                                        coil.Coil.imageLoader(context).diskCache?.clear()
                                                        coil.Coil.imageLoader(context).memoryCache?.clear()
                                                        viewModel.showToast("Artwork cache cleared successfully")
                                                    }.onFailure {
                                                        viewModel.showToast("Could not clear artwork cache")
                                                    }
                                                },
                                                onClearExclusions = {
                                                    viewModel.clearRecommendationExclusions()
                                                    viewModel.showToast("Exclusions reset")
                                                },
                                            )
                                        }
                                    }
                                    item(key = "backup_restore_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_backup), subtitle = "SAF JSON snapshot mirroring") {
                                            ExpressiveGroup(rowCount = 2) { index, position ->
                                                when (index) {
                                                    0 -> SettingsActionCard(
                                                        icon = Icons.Filled.Backup,
                                                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        title = stringResource(R.string.settings_backup),
                                                        subtitle = stringResource(R.string.settings_backup_sub),
                                                        onClick = {
                                                            runCatching { backupLauncher.launch("neonsong-backup-${System.currentTimeMillis()}.json") }
                                                                .onFailure { viewModel.showToast("No file picker is available") }
                                                        },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "backup.create"),
                                                    )
                                                    else -> SettingsActionCard(
                                                        icon = Icons.Filled.CloudDownload,
                                                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        title = stringResource(R.string.settings_restore),
                                                        subtitle = stringResource(R.string.settings_restore_sub),
                                                        onClick = {
                                                            runCatching { restoreLauncher.launch(arrayOf("*/*")) }
                                                                .onFailure { viewModel.showToast("No file picker is available") }
                                                        },
                                                        position = position,
                                                        isHighlighted = (highlightedSettingId == "backup.restore"),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    item(key = "backup_danger_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_data), subtitle = "Irreversible data erasure") {
                                            SettingsActionCard(
                                                icon = Icons.Filled.Delete,
                                                iconContainer = MaterialTheme.colorScheme.errorContainer,
                                                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                                title = stringResource(R.string.settings_clear_all),
                                                subtitle = stringResource(R.string.settings_clear_all_sub),
                                                danger = true,
                                                onClick = viewModel::requestClearAllData,
                                                position = GroupPosition.SINGLE,
                                                isHighlighted = (highlightedSettingId == "backup.clear_all"),
                                            )
                                        }
                                    }
                                }

                                SettingsTab.ABOUT -> {
                                    item(key = "about_hero") {
                                        SubPageGlassHeroCard(tab = SettingsTab.ABOUT)
                                    }
                                    item(key = "about_language_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_language), subtitle = "In-app language override") {
                                            SettingsActionCard(
                                                icon = Icons.Filled.Language,
                                                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                title = stringResource(R.string.settings_language_title),
                                                subtitle = currentLanguage.nativeDisplayName(),
                                                onClick = { showLanguageDialog = true },
                                                position = GroupPosition.SINGLE,
                                                isHighlighted = (highlightedSettingId == "about.language"),
                                            )
                                        }
                                    }
                                    item(key = "about_diagnostics_section") {
                                        SettingsGlassSection(title = stringResource(R.string.settings_section_about), subtitle = "System diagnostics, update manifest & release notes") {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                SettingsActionCard(
                                                    icon = Icons.Filled.Code,
                                                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    title = stringResource(R.string.settings_diagnostics),
                                                    subtitle = stringResource(R.string.settings_diagnostics_sub),
                                                    onClick = { viewModel.exportDiagnostics() },
                                                    position = GroupPosition.SINGLE,
                                                    isHighlighted = (highlightedSettingId == "about.diagnostics"),
                                                )
                                                AboutCard(
                                                    versionName = appVersionName(context),
                                                    updateInfo = updateInfo,
                                                    onCheckForUpdates = viewModel::checkForUpdates,
                                                    onDownloadAndInstall = { viewModel.downloadAndInstallUpdate(context) },
                                                    onOpenReleaseUrl = { viewModel.openUpdate(context) },
                                                    onConfigureSource = { showUpdateUrlDialog = true },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Glass Search Bar & Floating Liquid Glass Header (OUTSIDE liquidGlassSource, sampling settingsBackdrop)
            if (qualityPickerTarget == null && !showAudioQualityScreen) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .adaptiveContentWidth(maxWidth = 780.dp)
                        .align(Alignment.TopCenter)
                        .zIndex(2f),
                ) {
                    AnimatedVisibility(
                        visible = isSearchBarVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = headerTopPadding),
                    ) {
                        SettingsSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onClear = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.clearSearch()
                                } else {
                                    isSearchBarVisible = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }

                    // Floating Liquid Glass Header (Exact ExpressiveHeader from FeedScreen.kt)
                    val headerTitle = if (searchQuery.isNotBlank()) {
                        stringResource(R.string.settings_search_title)
                    } else {
                        when (activeTab) {
                            SettingsTab.AUDIO -> "Audio & DSP"
                            SettingsTab.APPEARANCE -> "Appearance"
                            SettingsTab.YOUTUBE -> "YouTube & Sync"
                            SettingsTab.LAST_FM -> "Last.fm"
                            SettingsTab.LIBRARY -> "Library & Content"
                            SettingsTab.HOME_SECTIONS -> "Feed Layout"
                            SettingsTab.DATA_BACKUP -> "Storage & Data"
                            SettingsTab.ABOUT -> "About & System"
                            null -> stringResource(R.string.settings)
                        }
                    }

                    val headerSubtitle = if (searchQuery.isNotBlank()) {
                        "${searchResults.size} setting${if (searchResults.size == 1) "" else "s"} found"
                    } else {
                        when (activeTab) {
                            SettingsTab.AUDIO -> "Hi-Res, Equalizer & DSP"
                            SettingsTab.APPEARANCE -> "Themes, Glass & Lyrics"
                            SettingsTab.YOUTUBE -> if (ytConnection.isConnected) "Connected as ${ytConnection.accountName}" else "Connect & Mirror Library"
                            SettingsTab.LAST_FM -> if (isLastFmConnected) "Connected as ${session.username}" else "Connect Scrobbler"
                            SettingsTab.LIBRARY -> "Home Sections & Imports"
                            SettingsTab.HOME_SECTIONS -> "Feed Sections & Shelves"
                            SettingsTab.DATA_BACKUP -> "Backup, Cache & Reset"
                            SettingsTab.ABOUT -> "v${appVersionName(context)} • Neon Song"
                            null -> if (currentPlayingTrack != null) "${currentPlayingTrack.title} • ${currentPlayingTrack.artist}" else "Liquid Glass Audio Suite"
                        }
                    }

                    ExpressiveHeader(
                        title = headerTitle,
                        subtitle = headerSubtitle,
                        backdrop = if (isGlassSupported) settingsBackdrop else null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .onGloballyPositioned { coordinates ->
                                val newHeight = coordinates.size.height.toFloat()
                                if (abs(headerHeightPx - newHeight) > 1f) {
                                    headerHeightPx = newHeight
                                }
                            },
                        onBack = {
                            if (searchQuery.isNotBlank() || isSearchBarVisible) {
                                viewModel.clearSearch()
                                isSearchBarVisible = false
                            } else if (activeTab != null) {
                                activeTab = null
                            } else {
                                onBack()
                            }
                        },
                        actions = {
                            HeaderActionIcon(
                                icon = if (isSearchBarVisible || searchQuery.isNotBlank()) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (isSearchBarVisible || searchQuery.isNotBlank()) stringResource(R.string.settings_search_clear) else stringResource(R.string.settings_search_title),
                                onClick = {
                                    if (isSearchBarVisible || searchQuery.isNotBlank()) {
                                        viewModel.clearSearch()
                                        isSearchBarVisible = false
                                    } else {
                                        isSearchBarVisible = true
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    // Sheet: Equalizer
    if (showEqSheet) {
        EqualizerSheet(
            eq = eq,
            onDismiss = { showEqSheet = false },
            onSetEnabled = viewModel::setEqualizerEnabled,
            onPickPreset = viewModel::applyEqPreset,
            onBandPreview = viewModel::previewEqBandGain,
            onBandChange = viewModel::setEqBandGain,
            onOpenAutoEq = {
                showEqSheet = false
                showAutoEq = true
            },
        )
    }

    // Sheet: Color Wheel
    if (state.showColorWheel) {
        ColorWheelSheet(onDismiss = viewModel::dismissColorWheel, onApply = viewModel::applyCustomColor)
    }

    // Dialog: Update Source URL
    if (showUpdateUrlDialog) {
        var tempUrl by remember { mutableStateOf(viewModel.getCustomUpdateUrl()) }
        AlertDialog(
            onDismissRequest = { showUpdateUrlDialog = false },
            title = { Text("Update Source URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set a custom update manifest JSON or GitHub API URL to push in-app updates to your users in the future:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Update URL") },
                        placeholder = { Text("https://raw.githubusercontent.com/.../version.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setCustomUpdateUrl(tempUrl)
                    showUpdateUrlDialog = false
                }) {
                    Text("Save & Check")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setCustomUpdateUrl("")
                    showUpdateUrlDialog = false
                }) {
                    Text("Reset Default")
                }
            },
        )
    }

    // Dialog: Clear All Data Confirm
    if (state.showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAllConfirm,
            title = { Text(stringResource(R.string.dialog_clear_all_title)) },
            text = { Text(stringResource(R.string.dialog_clear_all_text)) },
            confirmButton = { TextButton(onClick = { viewModel.confirmClearAllData(onLoggedOut) }) { Text(stringResource(R.string.dialog_clear_all_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAllConfirm) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // Dialog: YouTube Disconnect Confirm
    if (showYtDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showYtDisconnectConfirm = false },
            title = { Text(stringResource(R.string.dialog_disconnect_yt_title)) },
            text = { Text(stringResource(R.string.dialog_disconnect_yt_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showYtDisconnectConfirm = false
                    viewModel.disconnectYouTube()
                }) { Text(stringResource(R.string.common_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { showYtDisconnectConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // Dialog: Restore Backup Confirm
    if (state.showRestoreConfirm) {
        val isPlaylistMirror = state.pendingRestoreKind == PendingRestoreKind.PLAYLIST_MIRROR
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreConfirm,
            title = { Text(if (isPlaylistMirror) "Sync playlist JSON?" else "Restore backup?") },
            text = {
                Text(
                    if (isPlaylistMirror) {
                        "This will merge ${state.pendingRestorePlaylistCount ?: 0} playlist(s) from the local JSON file and reconnect automatic syncing."
                    } else {
                        "This will replace your current data with ${state.pendingRestorePlaylistCount ?: 0} playlist(s) and all settings from the backup file."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmRestore(onBack) }) { Text(if (isPlaylistMirror) stringResource(R.string.common_sync) else stringResource(R.string.common_restore)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRestoreConfirm) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // Dialog: Language Picker
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    AppLanguage.SELECTABLE.forEach { language ->
                        val selected = language == currentLanguage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.setAppLanguage(language)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    language.nativeDisplayName(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                                if (language == AppLanguage.SYSTEM) {
                                    Text(
                                        stringResource(R.string.settings_language_system_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.common_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.common_done))
                }
            },
        )
    }

    // Dialog: Last.fm Session Key Password Dialog
    if (state.showSessionKeyDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissSessionKeyDialog,
            title = { Text("Enable scrobbling") },
            text = {
                Column {
                    Text(
                        "Last.fm only allows scrobbling through a signed session. It is sent directly to Last.fm over HTTPS and never stored.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Last.fm password") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = state.sessionKeyError != null,
                        supportingText = state.sessionKeyError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submitPassword(password) },
                    enabled = !state.sessionKeyLoading,
                ) {
                    if (state.sessionKeyLoading) {
                        com.neonsong.app.ui.common.ExpressiveInlineLoadingIndicator(size = 18.dp)
                    } else {
                        Text("Enable")
                    }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissSessionKeyDialog) { Text("Cancel") } },
        )
    }

    if (showAutoEq) {
        com.neonsong.app.ui.settings.autoeq.AutoEqDialog(onDismiss = { showAutoEq = false })
    }

    if (showLyricsAnimationSheet) {
        LyricsAnimationSheet(
            version = misc.lyricsUiVersion,
            wordByWord = misc.wordByWordLyrics,
            onWordByWordChange = viewModel::setWordByWordLyrics,
            onSelectVersion = viewModel::setLyricsUiVersion,
            current = misc.lyricsAnimation,
            onSelect = {
                viewModel.setLyricsAnimation(it)
                showLyricsAnimationSheet = false
            },
            onDismiss = { showLyricsAnimationSheet = false },
        )
    }

    if (showLyricsProviderDialog) {
        LyricsProviderDialog(
            current = misc.lyricsProvider,
            onSelect = {
                viewModel.setLyricsProvider(it)
                showLyricsProviderDialog = false
            },
            onDismiss = { showLyricsProviderDialog = false },
        )
    }

    if (showLyricsOffsetDialog) {
        LyricsOffsetDialog(
            currentMs = misc.lyricsOffsetMs,
            onSelect = { viewModel.setLyricsOffsetMs(it) },
            onDismiss = { showLyricsOffsetDialog = false },
        )
    }

    if (showLyricsSizeDialog) {
        LyricsSizeDialog(
            currentScale = misc.lyricsFontScale,
            onSelect = { viewModel.setLyricsFontScale(it) },
            onDismiss = { showLyricsSizeDialog = false },
        )
    }

    if (showLoudnessDialog) {
        LoudnessModeDialog(
            current = loudness.mode,
            onSelect = {
                viewModel.setLoudnessMode(it)
                showLoudnessDialog = false
            },
            onDismiss = { showLoudnessDialog = false },
        )
    }

    if (showClarityPresetDialog) {
        ClarityPresetDialog(
            currentIndex = misc.clarityPreset,
            onSelect = {
                viewModel.setClarityPreset(it)
                showClarityPresetDialog = false
            },
            onDismiss = { showClarityPresetDialog = false },
        )
    }

    if (showSyncPlaylistsSheet) {
        SyncPlaylistsSheet(
            playlists = allPlaylists,
            syncedIds = syncedPlaylistIds,
            onToggleSync = viewModel::togglePlaylistSync,
            onSelectAll = viewModel::selectAllPlaylistsForSync,
            onDismiss = { showSyncPlaylistsSheet = false },
        )
    }

    if (showYtLibraryVisibilitySheet) {
        YouTubeLibraryVisibilitySheet(
            playlists = ytAccountPlaylists,
            hiddenIds = hiddenYtLibraryPlaylistIds,
            onSetVisible = viewModel::setYtLibraryPlaylistVisible,
            onSetAllVisible = viewModel::setAllYtLibraryPlaylistsVisible,
            onDismiss = { showYtLibraryVisibilitySheet = false },
        )
    }

    if (showYtChannelSheet) {
        YouTubeChannelSheet(
            channels = ytChannels,
            isLoading = ytChannelsLoading,
            selectedChannelId = ytConnection.onBehalfOfUser,
            selectedAuthUser = ytConnection.authUserIndex,
            selectedPageId = ytConnection.pageId,
            onReload = viewModel::loadYtChannels,
            onSelect = {
                viewModel.selectYtChannel(it)
                showYtChannelSheet = false
            },
            onDismiss = { showYtChannelSheet = false },
        )
    }

    if (showYouTubeImportSheet) {
        YouTubeImportSheet(
            onDismiss = { showYouTubeImportSheet = false },
            innerTube = viewModel.innerTube,
            importManager = viewModel.playlistImportManager,
            onImportSuccess = { saved ->
                viewModel.showToast("Imported \"${saved.title}\" (${saved.tracks.size} tracks)")
            },
        )
    }

    state.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000)
            viewModel.dismissToast()
        }
        Box(
            Modifier
                .fillMaxSize()
                .safeHorizontalContentPadding()
                .padding(
                    bottom = 24.dp +
                        LocalMiniPlayerScrollClearance.current +
                        safeDrawingBottomPadding(),
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(shape = ExpressivePillShape, color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 6.dp) {
                Text(msg, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}

// =========================================================================
// 3-LAYER LIQUID GLASS COMPONENTS & HERO SUITES
// =========================================================================

/**
 * Directional Outer Glass Section matching FeedGlassSection in FeedScreen.kt.
 */
@Composable
internal fun SettingsGlassSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)
    }
    val borderBrush = remember(isDark, primary) {
        Brush.verticalGradient(
            listOf(
                (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.16f else 0.14f),
                (if (isDark) Color.White else primary).copy(alpha = 0.04f),
            ),
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        border = BorderStroke(0.9.dp, borderBrush),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (trailingBadge != null) {
                        Spacer(Modifier.width(8.dp))
                        trailingBadge()
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/**
 * Hero Card shown at the top of every nested Sub-Page ("Page inside page").
 */
@Composable
private fun SubPageGlassHeroCard(
    tab: SettingsTab,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val heroBorder = remember(isDark, primary, tertiary) {
        Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.25f),
                tertiary.copy(alpha = 0.45f),
            ),
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, heroBorder),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = RoundedCornerShape(28.dp),
                preset = LiquidGlassPreset.Card,
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Top specular rim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.55f else 0.75f),
                                primary.copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupIconBadge(
                    icon = tab.icon,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 52.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = tab.eyebrow,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.heroSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 3D Profile & Now-Playing Suite Hero Card on the Root Settings Hub.
 */
@Composable
private fun SettingsProfileHero(
    displayName: String,
    effectiveDp: String?,
    isYtConnected: Boolean,
    isLastFmConnected: Boolean,
    downloadCount: Int,
    appVersion: String,
    currentTrackTitle: String?,
    currentTrackArtist: String?,
    isPlaying: Boolean,
    onAvatarClick: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenLastFm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hapticManager = LocalHapticManager.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current.density

    var touchX by remember { mutableFloatStateOf(0f) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var isTouched by remember { mutableStateOf(false) }

    val animatedTiltXState = animateFloatAsState(
        targetValue = if (isTouched) touchX else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "profileTiltX",
    )
    val animatedTiltYState = animateFloatAsState(
        targetValue = if (isTouched) touchY else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "profileTiltY",
    )
    val cardScaleState = animateFloatAsState(
        targetValue = if (isTouched) 0.98f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "profileScale",
    )

    val heroBorder = remember(isDark, primary, tertiary) {
        Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.25f),
                tertiary.copy(alpha = 0.45f),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed && !change.isConsumed) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                touchX = ((change.position.x - w / 2f) / (w / 2f)).coerceIn(-1f, 1f)
                                touchY = ((change.position.y - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)
                                isTouched = true
                            }
                        } else if (isTouched) {
                            isTouched = false
                            touchX = 0f
                            touchY = 0f
                        }
                    }
                }
            }
            .graphicsLayer {
                val tx = animatedTiltXState.value
                val ty = animatedTiltYState.value
                val s = cardScaleState.value
                rotationY = tx * 7f
                rotationX = -ty * 5f
                cameraDistance = 16f * density
                scaleX = s
                scaleY = s
            },
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            border = BorderStroke(1.2.dp, heroBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassChrome(
                    shape = RoundedCornerShape(30.dp),
                    preset = LiquidGlassPreset.Card,
                ),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // Top optical specular rim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = if (isDark) 0.55f else 0.75f),
                                    primary.copy(alpha = 0.45f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable {
                                    hapticManager.click()
                                    onAvatarClick()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 2.5.dp,
                                        brush = Brush.sweepGradient(listOf(primary, tertiary, MaterialTheme.colorScheme.secondary, primary)),
                                        shape = CircleShape,
                                    )
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!effectiveDp.isNullOrBlank()) {
                                    val cleanPath = effectiveDp.substringBefore('?')
                                    val model = remember(effectiveDp) {
                                        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://") || cleanPath.startsWith("content://")) {
                                            effectiveDp
                                        } else {
                                            java.io.File(cleanPath)
                                        }
                                    }
                                    AsyncImage(
                                        model = remember(context, model, effectiveDp) {
                                            coil.request.ImageRequest.Builder(context)
                                                .data(model)
                                                .memoryCacheKey(effectiveDp)
                                                .diskCacheKey(effectiveDp)
                                                .crossfade(true)
                                                .build()
                                        },
                                        contentDescription = "Profile picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(42.dp),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(primary)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Neon Song Audio Suite",
                                style = MaterialTheme.typography.labelMedium,
                                color = primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                hapticManager.click()
                                onAvatarClick()
                            },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("Edit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Live Song-Reactive Mini-Pill (When Track is Active)
                    if (!currentTrackTitle.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PlayingWaveBars(
                                    waveColor = primary,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.size(width = 16.dp, height = 12.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Now Playing: $currentTrackTitle • ${currentTrackArtist ?: "Unknown"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // Status Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isYtConnected) {
                            StatusChip(
                                label = "YouTube Synced",
                                dotColor = primary,
                                onClick = onOpenYouTube,
                            )
                        }
                        if (isLastFmConnected) {
                            StatusChip(
                                label = "Last.fm Active",
                                dotColor = tertiary,
                                onClick = onOpenLastFm,
                            )
                        }
                        if (downloadCount > 0) {
                            StatusChip(
                                label = "$downloadCount Offline",
                                dotColor = MaterialTheme.colorScheme.secondary,
                                onClick = onOpenDownloads,
                            )
                        }
                        StatusChip(
                            label = "v$appVersion",
                            dotColor = MaterialTheme.colorScheme.outline,
                            onClick = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4-Tile Quick-Access Bento Row for 1-Tap Sub-Page Navigation.
 */
@Composable
private fun SettingsQuickBentoRow(
    audioLabel: String,
    homeSectionsLabel: String,
    appearanceLabel: String,
    storageLabel: String,
    onOpenAudio: () -> Unit,
    onOpenHomeLayout: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "bento_audio") {
            QuickBentoTile(
                title = "Audio & EQ",
                subtitle = audioLabel,
                icon = Icons.Filled.GraphicEq,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = onOpenAudio,
            )
        }
        item(key = "bento_home") {
            QuickBentoTile(
                title = "Feed Layout",
                subtitle = homeSectionsLabel,
                icon = Icons.Filled.Dashboard,
                accentColor = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onOpenHomeLayout,
            )
        }
        item(key = "bento_appearance") {
            QuickBentoTile(
                title = "Appearance",
                subtitle = appearanceLabel,
                icon = Icons.Filled.Palette,
                accentColor = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onOpenAppearance,
            )
        }
        item(key = "bento_storage") {
            QuickBentoTile(
                title = "Data & Storage",
                subtitle = storageLabel,
                icon = Icons.Filled.Backup,
                accentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = onOpenStorage,
            )
        }
    }
}

@Composable
private fun QuickBentoTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current.density

    var touchTileX by remember { mutableFloatStateOf(0f) }
    var touchTileY by remember { mutableFloatStateOf(0f) }
    var isTileTouched by remember { mutableStateOf(false) }

    val animatedTileXState = animateFloatAsState(
        targetValue = if (isTileTouched) touchTileX else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "tileTiltX",
    )
    val animatedTileYState = animateFloatAsState(
        targetValue = if (isTileTouched) touchTileY else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "tileTiltY",
    )
    val scaleState = animateFloatAsState(
        targetValue = if (isTileTouched) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale",
    )

    val borderBrush = remember(isDark, accentColor) {
        Brush.linearGradient(
            listOf(
                accentColor.copy(alpha = 0.55f),
                (if (isDark) Color.White else accentColor).copy(alpha = 0.15f),
            ),
        )
    }

    Surface(
        onClick = {
            hapticManager.click()
            onClick()
        },
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, borderBrush),
        tonalElevation = 0.dp,
        modifier = modifier
            .width(148.dp)
            .height(128.dp)
            .liquidGlassChrome(
                shape = RoundedCornerShape(22.dp),
                preset = LiquidGlassPreset.Card,
                accentTint = accentColor.copy(alpha = 0.22f),
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed && !change.isConsumed) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                touchTileX = ((change.position.x - w / 2f) / (w / 2f)).coerceIn(-1f, 1f)
                                touchTileY = ((change.position.y - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)
                                isTileTouched = true
                            }
                        } else if (isTileTouched) {
                            isTileTouched = false
                            touchTileX = 0f
                            touchTileY = 0f
                        }
                    }
                }
            }
            .graphicsLayer {
                val tx = animatedTileXState.value
                val ty = animatedTileYState.value
                val s = scaleState.value
                rotationY = tx * 8f
                rotationX = -ty * 6f
                cameraDistance = 16f * density
                scaleX = s
                scaleY = s
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Top specular rim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.50f else 0.70f),
                                accentColor.copy(alpha = 0.40f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupIconBadge(
                        icon = icon,
                        container = containerColor,
                        tint = accentColor,
                        size = 40.dp,
                        iconSize = 20.dp,
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// =========================================================================
// CONTINUOUS SCULPTED SETTING ROWS & CONTROLS (EXPRESSIVEGROUP)
// =========================================================================

@Composable
internal fun SettingsActionCard(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
    trailingBadge: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberGroupPressScaleState(interactionSource)
    val titleColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val shape = remember(position) { groupShape(position) }
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    val rowBorder = remember(isDark, primary) {
        Brush.verticalGradient(
            listOf(
                (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                Color.Transparent,
            ),
        )
    }

    Surface(
        onClick = {
            hapticManager.click()
            onClick()
        },
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.7.dp, rowBorder),
        tonalElevation = 0.dp,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = shape,
                preset = LiquidGlassPreset.Card,
                accentTint = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Unspecified,
            )
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconBadge(icon = icon, container = iconContainer, tint = iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingBadge != null) {
                Spacer(Modifier.width(8.dp))
                trailingBadge()
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SettingsToggleCard(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    position: GroupPosition = GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val shape = remember(position) { groupShape(position) }
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    val rowBorder = remember(isDark, primary) {
        Brush.verticalGradient(
            listOf(
                (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                Color.Transparent,
            ),
        )
    }

    Surface(
        onClick = {
            if (enabled) {
                hapticManager.toggle(!checked)
                onCheckedChange(!checked)
            }
        },
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.7.dp, rowBorder),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = shape,
                preset = LiquidGlassPreset.Card,
                accentTint = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Unspecified,
            )
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconBadge(icon = icon, container = iconContainer, tint = iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { nextVal ->
                    hapticManager.toggle(nextVal)
                    onCheckedChange(nextVal)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
    }
}

@Composable
internal fun SettingsActionCard(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: com.neonsong.app.core.designsystem.components.GroupPosition,
    danger: Boolean = false,
    isHighlighted: Boolean = false,
    trailingBadge: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    SettingsActionCard(
        icon = icon,
        iconContainer = iconContainer,
        iconTint = iconTint,
        title = title,
        subtitle = subtitle,
        danger = danger,
        onClick = onClick,
        position = position.toCommon(),
        isHighlighted = isHighlighted,
        trailingBadge = trailingBadge,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsToggleCard(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: com.neonsong.app.core.designsystem.components.GroupPosition,
    enabled: Boolean = true,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    SettingsToggleCard(
        icon = icon,
        iconContainer = iconContainer,
        iconTint = iconTint,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        position = position.toCommon(),
        isHighlighted = isHighlighted,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsPhotoCard(
    photoUri: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    position: com.neonsong.app.core.designsystem.components.GroupPosition = com.neonsong.app.core.designsystem.components.GroupPosition.SINGLE,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val commonPos = position.toCommon()
    val shape = groupShape(commonPos)
    val hasPhoto = photoUri.isNotBlank()
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    val rowBorder = remember(isDark, primary) {
        Brush.verticalGradient(
            listOf(
                (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                Color.Transparent,
            ),
        )
    }

    Surface(
        onClick = {
            hapticManager.click()
            onClick()
        },
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.7.dp, rowBorder),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = shape,
                preset = LiquidGlassPreset.Card,
                accentTint = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Unspecified,
            )
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPhoto) {
                val context = LocalContext.current
                val cleanPath = photoUri.substringBefore('?')
                val model = remember(photoUri) {
                    if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://") || cleanPath.startsWith("content://")) {
                        photoUri
                    } else {
                        java.io.File(cleanPath)
                    }
                }
                AsyncImage(
                    model = remember(context, model, photoUri) {
                        coil.request.ImageRequest.Builder(context)
                            .data(model)
                            .memoryCacheKey(photoUri)
                            .diskCacheKey(photoUri)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = "Selected settings photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            } else {
                GroupIconBadge(
                    icon = Icons.Filled.PhotoCamera,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 44.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Settings photo",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (hasPhoto) "Photo selected • Tap to change" else "Add a personal photo to Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasPhoto) {
                IconButton(onClick = {
                    hapticManager.click()
                    onRemove()
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove settings photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsGroup(
    rowCount: Int,
    content: @Composable (index: Int, position: com.neonsong.app.core.designsystem.components.GroupPosition) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (i in 0 until rowCount) {
            val position = when {
                rowCount == 1 -> com.neonsong.app.core.designsystem.components.GroupPosition.SINGLE
                i == 0 -> com.neonsong.app.core.designsystem.components.GroupPosition.TOP
                i == rowCount - 1 -> com.neonsong.app.core.designsystem.components.GroupPosition.BOTTOM
                else -> com.neonsong.app.core.designsystem.components.GroupPosition.MIDDLE
            }
            content(i, position)
        }
    }
}

// =========================================================================
// SUB-PAGE SPECIFIC CONTROLS & EDITORS
// =========================================================================

/**
 * Storage Breakdown Card with Multi-Segment Progress Bar and 1-Tap Purge Actions.
 */
@Composable
private fun SettingsStorageCard(
    downloadTotalBytes: Long,
    downloadCount: Int,
    exclusionCount: Int,
    onClearArtworkCache: () -> Unit,
    onClearExclusions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadedMb = remember(downloadTotalBytes) {
        String.format(java.util.Locale.US, "%.1f MB", downloadTotalBytes / (1024f * 1024f))
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LiquidGlassCard(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Storage Allocation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$downloadCount offline songs • $downloadedMb used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = downloadedMb,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Multi-Segment Storage Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    val audioWeight = if (downloadTotalBytes > 0) 0.45f else 0.15f
                    Box(Modifier.fillMaxHeight().weight(audioWeight).background(primary))
                    Box(Modifier.fillMaxHeight().weight(0.25f).background(secondary))
                    Box(Modifier.fillMaxHeight().weight(0.15f).background(tertiary))
                    Box(Modifier.fillMaxHeight().weight(1f - audioWeight - 0.25f - 0.15f).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                }

                // Storage Bar Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StorageLegendItem(color = primary, label = "Offline Audio")
                    StorageLegendItem(color = secondary, label = "Artwork Cache")
                    StorageLegendItem(color = tertiary, label = "Database")
                    StorageLegendItem(color = MaterialTheme.colorScheme.outlineVariant, label = "Available")
                }
            }
        }

        // 1-Tap Cache Actions
        ExpressiveGroup(rowCount = 2) { index, position ->
            when (index) {
                0 -> SettingsActionCard(
                    icon = Icons.Filled.Delete,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = "Clear Artwork & Image Cache",
                    subtitle = "Purges cached album art and artist portraits from disk",
                    onClick = onClearArtworkCache,
                    position = position,
                )
                else -> SettingsActionCard(
                    icon = Icons.Filled.RestartAlt,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    title = "Reset Excluded Recommendations",
                    subtitle = "$exclusionCount excluded songs • Clears recommendation filters",
                    onClick = onClearExclusions,
                    position = position,
                )
            }
        }
    }
}

@Composable
private fun StorageLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Interactive App Icon & Brand Showcase Selector.
 */
@Composable
private fun AppIconShowcaseSelector(modifier: Modifier = Modifier) {
    val hapticManager = LocalHapticManager.current
    val primary = MaterialTheme.colorScheme.primary
    val currentIconRes = NeonBrand.AppIconRes

    val icons = remember {
        listOf(
            Triple("Beru 3D Dynamic", "Signature 3D sculpted glass emblem", R.drawable.beru_icon_3d),
            Triple("Monochrome Glass", "Minimal high-contrast obsidian mark", R.drawable.ic_launcher_beru_foreground),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icons.forEach { (name, description, resId) ->
            val isSelected = currentIconRes == resId
            Surface(
                onClick = {
                    hapticManager.click()
                    NeonBrand.AppIconRes = resId
                },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 0.8.dp,
                    color = if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(resId),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if (isSelected) primary else Color.Transparent, CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * YouTube Music Shelves Reordering & Visibility Editor.
 */
@Composable
private fun YouTubeMusicShelvesEditor(
    order: List<String>,
    hidden: Set<String>,
    onSave: (order: List<String>, hidden: Set<String>) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticManager = LocalHapticManager.current
    val effectiveOrder = remember(order) { if (order.isEmpty()) DEFAULT_YT_MUSIC_SECTIONS else order }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${effectiveOrder.size - hidden.size} of ${effectiveOrder.size} Active",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onReset) {
                Text("Reset Shelves", fontWeight = FontWeight.SemiBold)
            }
        }

        ExpressiveGroup(rowCount = effectiveOrder.size) { index, position ->
            val shelfName = effectiveOrder[index]
            val isHidden = shelfName in hidden

            Surface(
                shape = groupShape(position),
                color = if (isHidden) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupIconBadge(
                        icon = if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        container = if (isHidden) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
                        tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 38.dp,
                        iconSize = 18.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = shelfName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isHidden) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Up/Down reorder actions
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                val mutable = effectiveOrder.toMutableList()
                                val item = mutable.removeAt(index)
                                mutable.add(index - 1, item)
                                onSave(mutable, hidden)
                            }
                        },
                        enabled = index > 0,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up")
                    }
                    IconButton(
                        onClick = {
                            if (index < effectiveOrder.size - 1) {
                                val mutable = effectiveOrder.toMutableList()
                                val item = mutable.removeAt(index)
                                mutable.add(index + 1, item)
                                onSave(mutable, hidden)
                            }
                        },
                        enabled = index < effectiveOrder.size - 1,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down")
                    }
                    // Visibility toggle
                    IconButton(
                        onClick = {
                            hapticManager.click()
                            val nextHidden = if (isHidden) hidden - shelfName else hidden + shelfName
                            onSave(effectiveOrder, nextHidden)
                        },
                    ) {
                        Icon(
                            if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Visibility",
                            tint = if (isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// SEARCH & FUZZY MATCHING UI
// =========================================================================

private fun buildHighlightedText(
    text: String,
    ranges: List<IntRange>,
    highlightColor: Color,
): AnnotatedString {
    if (ranges.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var lastIndex = 0
        for (range in ranges) {
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(0, text.length)
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            if (end > start) {
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(text.substring(start, end))
                }
                lastIndex = end
            }
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .liquidGlassChrome(
                shape = RoundedCornerShape(22.dp),
                preset = LiquidGlassPreset.Card,
            )
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(22.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        ),
        placeholder = {
            Text(
                stringResource(R.string.settings_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = primary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

@Composable
private fun SettingsSearchResultsList(
    query: String,
    results: List<MatchResult>,
    onResultClick: (SettingsEntry) -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding + 8.dp,
            bottom = FloatingNavDefaults.contentBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .safeHorizontalContentPadding(),
    ) {
        if (results.isEmpty()) {
            item(key = "search_empty") {
                SettingsSearchEmptyState(query = query)
            }
        } else {
            item(key = "search_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${results.size} setting${if (results.size == 1) "" else "s"} found",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(results, key = { it.entry.id }) { result ->
                SearchResultCard(
                    result = result,
                    onClick = { onResultClick(result.entry) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: MatchResult,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val isDark = LocalIsDarkTheme.current

    val annotatedTitle = remember(result.entry.title, result.titleMatchedRanges, highlightColor) {
        buildHighlightedText(result.entry.title, result.titleMatchedRanges, highlightColor)
    }
    val annotatedSubtitle = remember(result.entry.subtitle, result.subtitleMatchedRanges, highlightColor) {
        buildHighlightedText(result.entry.subtitle, result.subtitleMatchedRanges, highlightColor)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(0.8.dp, (if (isDark) Color.White else highlightColor).copy(alpha = 0.15f)),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = RoundedCornerShape(22.dp),
                preset = LiquidGlassPreset.Card,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Text(
                    text = "${result.entry.parentTab.title} › ${result.entry.section}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupIconBadge(
                    icon = result.entry.icon,
                    container = result.entry.iconContainer(),
                    tint = result.entry.iconTint(),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = annotatedTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = annotatedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchEmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_search_no_results, query),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_search_no_results_sub),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicHapticsConfigRow(
    selectedMode: com.neonsong.app.playback.HapticMode,
    selectedIntensity: com.neonsong.app.playback.HapticIntensity,
    onSelectMode: (com.neonsong.app.playback.HapticMode) -> Unit,
    onSelectIntensity: (com.neonsong.app.playback.HapticIntensity) -> Unit,
    @Suppress("UNUSED_PARAMETER") position: GroupPosition = GroupPosition.MIDDLE,
) {
    val primary = MaterialTheme.colorScheme.primary
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Haptic Mode • ${selectedMode.description}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            com.neonsong.app.playback.HapticMode.entries.forEach { modeOption ->
                val isSelected = modeOption == selectedMode
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectMode(modeOption) },
                    label = {
                        Text(
                            text = modeOption.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primary.copy(alpha = 0.22f),
                        selectedLabelColor = primary,
                        containerColor = (if (isDark) Color.White else primary).copy(alpha = 0.06f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = (if (isDark) Color.White else primary).copy(alpha = 0.12f),
                        selectedBorderColor = primary.copy(alpha = 0.65f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = "Vibration Intensity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.neonsong.app.playback.HapticIntensity.entries.forEach { intensityOption ->
                val isSelected = intensityOption == selectedIntensity
                Surface(
                    onClick = { onSelectIntensity(intensityOption) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) primary.copy(alpha = 0.22f) else (if (isDark) Color.White else primary).copy(alpha = 0.06f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) primary.copy(alpha = 0.65f) else (if (isDark) Color.White else primary).copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = intensityOption.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun appVersionName(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.2.4"
} catch (error: Exception) {
    "4.2.4"
}
