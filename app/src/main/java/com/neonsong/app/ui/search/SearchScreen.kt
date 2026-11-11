package com.neonsong.app.ui.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.search.SearchResultItem
import com.neonsong.app.data.search.SearchTab
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.common.HeaderActionIcon
import com.neonsong.app.ui.common.OverflowMenuButton
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.common.TrackMiniTrayData
import com.neonsong.app.ui.common.TrackMiniTraySheet
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.common.safeHorizontalContentPadding
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.player.PlayingWaveBars
import com.neonsong.app.ui.theme.GlassFilledIconActionButton as FilledIconButton
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import com.neonsong.app.ui.theme.LiquidGlassChip
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.ui.theme.rememberLiquidTouchInteraction

private val EXPLORE_GENRES = listOf(
    "Pop", "Rock", "Hip-Hop", "Lo-Fi", "Electronic", "Indie",
    "R&B", "Bollywood", "Jazz", "Metal", "Acoustic", "Chill",
    "Anime", "Classical", "Synthwave",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
    onOpenArtist: (name: String, browseId: String?) -> Unit = { _, _ -> },
    onOpenAlbum: (title: String, artist: String, browseId: String?) -> Unit = { _, _, _ -> },
    onOpenPlaylist: (playlistId: String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val musicPlayer = LocalMusicPlayer.current
    val playbackState by musicPlayer.chromeState.collectAsStateWithLifecycle()
    var menuTarget by remember { mutableStateOf<TrackMenuTarget?>(null) }
    var miniTrayItem by remember { mutableStateOf<SearchResultItem?>(null) }
    val focusManager = LocalFocusManager.current

    val bottomClearance = 84.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 860.dp),
        ) {
            // Top Header: Page Title "Search", Back Button, and Apple Music Style Filter Pills
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Spacer(Modifier.height(10.dp))

                SearchFilterPills(
                    selectedTab = state.tab,
                    onTabSelected = viewModel::setTab,
                )
            }

            // Middle Content Area
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .safeHorizontalContentPadding(),
            ) {
                val matchingRecent = remember(state.query, state.recentSearches) {
                    val q = state.query.trim().lowercase()
                    if (q.isEmpty()) emptyList()
                    else state.recentSearches.filter { it.lowercase().contains(q) }.take(3)
                }

                when {
                    // 1. Empty Query State: Show Recent Search History & Explore Tags
                    state.query.isBlank() -> {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 10.dp, bottom = bottomClearance),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (state.recentSearches.isNotEmpty()) {
                                item(key = "history_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "Recent Searches",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = viewModel::clearRecentSearches) {
                                            Text(
                                                "Clear",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                items(state.recentSearches, key = { "hist_$it" }) { recentQuery ->
                                    RecentSearchRow(
                                        text = recentQuery,
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.executeSearch(recentQuery)
                                        },
                                        onDelete = { viewModel.removeRecentSearch(recentQuery) },
                                    )
                                }
                                item(key = "history_spacer") {
                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                            item(key = "explore_section") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Explore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Browse by Genre & Mood",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        EXPLORE_GENRES.forEach { genre ->
                                            LiquidGlassChip(
                                                selected = false,
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    viewModel.executeSearch(genre)
                                                },
                                                label = {
                                                    Text(
                                                        genre,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Live Auto-Complete Suggestions (while actively typing and before results arrive)
                    state.isShowingSuggestions && (state.suggestions.isNotEmpty() || matchingRecent.isNotEmpty()) && state.results.isEmpty() -> {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomClearance),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (matchingRecent.isNotEmpty()) {
                                items(matchingRecent, key = { "recent_match_$it" }) { recentText ->
                                    SuggestionRow(
                                        fullText = recentText,
                                        query = state.query,
                                        isHistory = true,
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.executeSearch(recentText)
                                        },
                                        onInsert = { viewModel.setQuery(recentText) },
                                    )
                                }
                            }

                            items(
                                state.suggestions.filter { !matchingRecent.contains(it) },
                                key = { "sugg_$it" },
                            ) { suggestion ->
                                SuggestionRow(
                                    fullText = suggestion,
                                    query = state.query,
                                    isHistory = false,
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.executeSearch(suggestion)
                                    },
                                    onInsert = { viewModel.setQuery(suggestion) },
                                )
                            }
                        }
                    }

                    // 3. Search Results State: Loading, Empty, Results
                    else -> {
                        Crossfade(
                            targetState = state.status,
                            animationSpec = tween(ExpressiveMotion.Standard, easing = FastOutSlowInEasing),
                            label = "searchState",
                        ) { status ->
                            when (status) {
                                SearchStatus.IDLE, SearchStatus.LOADING -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ExpressiveLoadingIndicator()
                                }
                                SearchStatus.EMPTY -> Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(44.dp),
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            "No results found for \"${state.query}\"",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                SearchStatus.RESULTS -> {
                                    val topResult = state.results.firstOrNull()
                                    val otherResults = if (state.results.size > 1) state.results.drop(1) else emptyList()

                                    LazyColumn(
                                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomClearance),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        // Compact horizontal suggestion chips above results when typing
                                        if (state.isShowingSuggestions && state.suggestions.isNotEmpty()) {
                                            item(key = "inline_suggestions") {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    state.suggestions.take(5).forEach { sugg ->
                                                        LiquidGlassChip(
                                                            selected = false,
                                                            onClick = {
                                                                focusManager.clearFocus()
                                                                viewModel.executeSearch(sugg)
                                                            },
                                                            shape = CircleShape,
                                                            label = {
                                                                Text(
                                                                    sugg,
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                )
                                                            },
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                            }
                                        }

                                        if (topResult != null && state.tab != SearchTab.USERS) {
                                            val isTopPlaying = state.tab == SearchTab.TRACKS && playbackState.isPlaying &&
                                                playbackState.current?.title.equals(topResult.name, ignoreCase = true) &&
                                                (topResult.artist.isNullOrBlank() || playbackState.current?.artist.equals(topResult.artist, ignoreCase = true))

                                            item(key = "top_result_card", contentType = "top_card") {
                                                TopResultCard(
                                                    item = topResult,
                                                    tab = state.tab,
                                                    isPlaying = isTopPlaying,
                                                    onPlay = {
                                                        focusManager.clearFocus()
                                                        when (state.tab) {
                                                            SearchTab.ARTISTS -> onOpenArtist(topResult.name, topResult.entityId)
                                                            SearchTab.ALBUMS -> onOpenAlbum(topResult.name, topResult.artist.orEmpty(), topResult.entityId)
                                                            SearchTab.PLAYLISTS -> topResult.entityId?.let(onOpenPlaylist)
                                                            else -> viewModel.playResult(topResult)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (state.tab == SearchTab.TRACKS) miniTrayItem = topResult
                                                    },
                                                    onMenu = {
                                                        menuTarget = when (state.tab) {
                                                            SearchTab.TRACKS -> TrackMenuTarget.Track(topResult.name, topResult.artist.orEmpty(), topResult.url)
                                                            SearchTab.ARTISTS -> TrackMenuTarget.Artist(topResult.name, topResult.url)
                                                            SearchTab.ALBUMS -> TrackMenuTarget.Album(topResult.name, topResult.artist.orEmpty(), topResult.url)
                                                            SearchTab.PLAYLISTS, SearchTab.USERS -> null
                                                        }
                                                    },
                                                )
                                                Spacer(Modifier.height(8.dp))
                                            }
                                        }

                                        items(
                                            if (state.tab == SearchTab.USERS) state.results else otherResults,
                                            key = { it.entityId ?: it.url.ifBlank { it.name + it.artist.orEmpty() } },
                                            contentType = { "search_result" },
                                        ) { item ->
                                            val isItemPlaying = state.tab == SearchTab.TRACKS && playbackState.isPlaying &&
                                                playbackState.current?.title.equals(item.name, ignoreCase = true) &&
                                                (item.artist.isNullOrBlank() || playbackState.current?.artist.equals(item.artist, ignoreCase = true))

                                            SearchResultRow(
                                                item = item,
                                                tab = state.tab,
                                                isPlaying = isItemPlaying,
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    when (state.tab) {
                                                        SearchTab.ARTISTS -> onOpenArtist(item.name, item.entityId)
                                                        SearchTab.ALBUMS -> onOpenAlbum(item.name, item.artist.orEmpty(), item.entityId)
                                                        SearchTab.PLAYLISTS -> item.entityId?.let(onOpenPlaylist)
                                                        else -> viewModel.playResult(item)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (state.tab == SearchTab.TRACKS) {
                                                        miniTrayItem = item
                                                    }
                                                },
                                                onMenu = {
                                                    menuTarget = when (state.tab) {
                                                        SearchTab.TRACKS -> TrackMenuTarget.Track(item.name, item.artist.orEmpty(), item.url)
                                                        SearchTab.ARTISTS -> TrackMenuTarget.Artist(item.name, item.url)
                                                        SearchTab.ALBUMS -> TrackMenuTarget.Album(item.name, item.artist.orEmpty(), item.url)
                                                        SearchTab.PLAYLISTS, SearchTab.USERS -> null
                                                    }
                                                },
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

        // True Floating Bottom Search Bar (iOS 18/26 Safari & Music style)
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val isImeOpen = WindowInsets.isImeVisible || imeBottom > 0
        val targetBottomPadding = if (isImeOpen) 8.dp else (12.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding())
        val bottomPadding by animateDpAsState(
            targetValue = targetBottomPadding,
            animationSpec = tween(durationMillis = 160),
            label = "searchBarImePadding",
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 860.dp)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPadding),
        ) {
            BottomSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.searchNow()
                },
                onClear = viewModel::clearQuery,
                tab = state.tab,
            )
        }
    }

    menuTarget?.let { target ->
        TrackContextMenuSheet(
            target = target,
            capabilities = TrackMenuCapabilities(showCopyActions = true, showDeleteScrobble = true),
            playbackSourceLabel = "Search",
            onDismiss = { menuTarget = null },
        )
    }

    miniTrayItem?.let { item ->
        TrackMiniTraySheet(
            data = TrackMiniTrayData(
                title = item.name,
                artist = item.artist.orEmpty(),
                album = item.subtitle,
                artworkUrl = item.artworkUrl,
                videoId = item.videoId,
                sourceLabel = "Search",
                onPlay = { viewModel.playResult(item) },
            ),
            onDismiss = { miniTrayItem = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(
    item: SearchResultItem,
    tab: SearchTab,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val haptics = LocalHapticFeedback.current
    val glassTouch = rememberLiquidTouchInteraction()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .liquidGlassSurface(
                shape = shape,
                borderWidth = 0.8.dp,
                accentTint = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified,
                interactive = true,
                interaction = glassTouch,
            )
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fallback = when (tab) {
                SearchTab.TRACKS -> if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote
                SearchTab.ARTISTS -> Icons.Filled.Person
                SearchTab.ALBUMS -> Icons.Filled.Album
                SearchTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                SearchTab.USERS -> Icons.Filled.Person
            }
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(if (tab == SearchTab.ARTISTS) CircleShape else RoundedCornerShape(14.dp)),
            ) {
                ArtworkImage(
                    name = item.name,
                    artist = item.artist.orEmpty(),
                    embeddedUrl = item.artworkUrl,
                    fallbackIcon = fallback,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isPlaying) {
                    PlayingWaveBars(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(24.dp, 18.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                ) {
                    Text(
                        when (tab) {
                            SearchTab.TRACKS -> "TOP SONG"
                            SearchTab.ARTISTS -> "TOP ARTIST"
                            SearchTab.ALBUMS -> "TOP ALBUM"
                            SearchTab.PLAYLISTS -> "TOP PLAYLIST"
                            SearchTab.USERS -> "USER"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (tab) {
                    SearchTab.TRACKS -> item.artist.orEmpty()
                    SearchTab.ALBUMS -> item.artist.orEmpty()
                    SearchTab.PLAYLISTS -> item.subtitle.orEmpty()
                    SearchTab.ARTISTS -> item.subtitle.orEmpty().ifBlank { "Artist" }
                    SearchTab.USERS -> item.artist.orEmpty()
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onPlay,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    if (tab == SearchTab.PLAYLISTS) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.PlayArrow,
                    contentDescription = if (tab == SearchTab.PLAYLISTS) "Open playlist" else "Play top result",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (tab != SearchTab.PLAYLISTS && tab != SearchTab.USERS) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onMenu,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    fullText: String,
    query: String,
    isHistory: Boolean,
    onClick: () -> Unit,
    onInsert: () -> Unit,
) {
    val baseColor = MaterialTheme.colorScheme.onSurface
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = remember(fullText, query, baseColor, highlightColor) {
        buildHighlightedSuggestion(fullText, query, baseColor, highlightColor)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isHistory) Icons.Filled.History else Icons.Filled.Search,
            contentDescription = null,
            tint = if (isHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        )
        IconButton(
            onClick = onInsert,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Insert into search bar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = 45f },
            )
        }
    }
}

private fun buildHighlightedSuggestion(
    suggestion: String,
    query: String,
    baseColor: Color,
    highlightColor: Color,
): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(suggestion)
    val lowerSug = suggestion.lowercase()
    val lowerQ = q.lowercase()
    val idx = lowerSug.indexOf(lowerQ)
    if (idx < 0) return AnnotatedString(suggestion)

    return buildAnnotatedString {
        if (idx > 0) {
            withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Normal)) {
                append(suggestion.substring(0, idx))
            }
        }
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(suggestion.substring(idx, idx + q.length))
        }
        if (idx + q.length < suggestion.length) {
            withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.SemiBold)) {
                append(suggestion.substring(idx + q.length))
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove from history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SearchResultRow(
    item: SearchResultItem,
    tab: SearchTab,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun openLastFm(path: String) {
        val base = item.url.ifBlank { "https://www.last.fm/user/${item.name}" }.trimEnd('/')
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("$base$path"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) { }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                enabled = tab != SearchTab.USERS,
                onClick = onClick,
                onLongClick = if (tab == SearchTab.TRACKS) onLongClick else onMenu,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fallback = when (tab) {
            SearchTab.TRACKS -> if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote
            SearchTab.ARTISTS -> Icons.Filled.Person
            SearchTab.ALBUMS -> Icons.Filled.Album
            SearchTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
            SearchTab.USERS -> Icons.Filled.Person
        }
        val avatarSize = if (tab == SearchTab.ARTISTS || tab == SearchTab.USERS) 54.dp else 48.dp
        Box(modifier = Modifier.size(avatarSize)) {
            ArtworkImage(
                name = item.name,
                artist = item.artist.orEmpty(),
                embeddedUrl = item.artworkUrl,
                fallbackIcon = fallback,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(if (tab == SearchTab.ARTISTS || tab == SearchTab.USERS) CircleShape else RoundedCornerShape(10.dp)),
            )
            if (isPlaying) {
                PlayingWaveBars(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(22.dp, 16.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when (tab) {
                SearchTab.TRACKS -> if (!item.artist.isNullOrBlank()) "Song \u2022 ${item.artist}" else "Song"
                SearchTab.ALBUMS -> if (!item.artist.isNullOrBlank()) "Album \u2022 ${item.artist}" else "Album"
                SearchTab.PLAYLISTS -> item.subtitle.orEmpty().ifBlank { "Playlist" }
                SearchTab.ARTISTS -> "Artist"
                SearchTab.USERS -> listOfNotNull(item.artist, item.listeners?.let { "$it scrobbles" }).joinToString(" \u2022 ")
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tab == SearchTab.USERS) {
            IconButton(onClick = { openLastFm("/+addfriend") }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Follow ${item.name} on Last.fm")
            }
            IconButton(onClick = { openLastFm("/+removefriend") }) {
                Icon(Icons.Filled.PersonRemove, contentDescription = "Unfollow ${item.name} on Last.fm")
            }
        } else if (tab != SearchTab.PLAYLISTS) {
            OverflowMenuButton(onClick = onMenu)
        }
    }
}

/**
 * FIXED: High-contrast Apple Music filter pills (selected pill has vibrant fill + crisp text).
 */
@Composable
private fun SearchFilterPills(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        SearchTab.TRACKS to "Songs",
        SearchTab.ARTISTS to "Artists",
        SearchTab.ALBUMS to "Albums",
        SearchTab.PLAYLISTS to "Playlists",
        SearchTab.USERS to "Users",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (tab, label) ->
            val selected = selectedTab == tab
            val bgColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(180),
                label = "pillBg",
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(180),
                label = "pillText",
            )

            Surface(
                onClick = { onTabSelected(tab) },
                shape = CircleShape,
                color = bgColor,
                modifier = if (selected) {
                    Modifier
                } else {
                    Modifier.liquidGlassSurface(shape = CircleShape, borderWidth = 0.8.dp)
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    tab: SearchTab,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(26.dp),
                borderWidth = 0.8.dp,
                accentTint = MaterialTheme.colorScheme.primary,
                interactive = false,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = if (query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = if (tab == SearchTab.USERS) "Search Last.fm users\u2026" else "Songs, artists, albums\u2026",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSearch()
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
