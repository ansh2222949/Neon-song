package com.neonsong.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import com.neonsong.app.ui.theme.GlassTonalActionButton as FilledTonalButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neonsong.app.R
import com.neonsong.app.data.local.HomeSection
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun HomeSection.icon(): ImageVector = when (this) {
    HomeSection.HERO -> Icons.Filled.Home
    HomeSection.QUICK_TILES -> Icons.Filled.Dashboard
    HomeSection.TASTE_STRIP -> Icons.Filled.Sell
    HomeSection.QUICK_PICKS -> Icons.Filled.Bolt
    HomeSection.BECAUSE_YOU_LISTEN_TO -> Icons.Filled.History
    HomeSection.FRESH_FINDS -> Icons.Filled.Whatshot
    HomeSection.JUMP_BACK_IN -> Icons.Filled.Replay
    HomeSection.MIXES -> Icons.Filled.Shuffle
    HomeSection.SPOTLIGHT -> Icons.Filled.Star
    HomeSection.TOP_ARTISTS -> Icons.Filled.People
    HomeSection.HEAVY_ROTATION -> Icons.Filled.TrendingUp
    HomeSection.ALBUMS -> Icons.Filled.LibraryMusic
    HomeSection.CHARTS -> Icons.Filled.TrendingUp
    HomeSection.NEW_RELEASES -> Icons.Filled.NewReleases
    HomeSection.FRIENDS -> Icons.Filled.People
}

internal val DEFAULT_YT_MUSIC_SECTIONS = listOf(
    "COMMUNITY PLAYLISTS",
    "Trending",
    "NEW RELEASES",
    "Energy boosters",
    "Feel good",
    "Workout",
    "Relax",
    "Focus",
    "Commute",
    "Party",
    "Romance",
    "Sleep",
)

@HiltViewModel
class HomeSectionsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {
    val hiddenSections: StateFlow<Set<String>> = settingsPreferences.settings
        .map { it.hiddenHomeSections }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val ytMusicHiddenSections: StateFlow<Set<String>> = settingsPreferences.settings
        .map { it.ytMusicHiddenSections }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val ytMusicSectionsOrder: StateFlow<List<String>> = settingsPreferences.settings
        .map { it.ytMusicSectionsOrder }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSectionVisible(section: HomeSection, visible: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setHomeSectionVisible(section.id, visible)
        }
    }

    fun showAll() {
        viewModelScope.launch {
            settingsPreferences.showAllHomeSections()
        }
    }

    fun saveYtMusicSections(order: List<String>, hidden: Set<String>) {
        viewModelScope.launch {
            settingsPreferences.saveYtMusicSections(order, hidden)
        }
    }

    fun resetYtMusicSections() {
        viewModelScope.launch {
            settingsPreferences.resetYtMusicSections()
        }
    }
}

/**
 * Redesigned Home Feed Layout & Sections Customization Screen.
 *
 * Implements:
 * 1. Song-Reactive Liquid Glass Scaffold.
 * 2. YouTube Music Shelves trigger Bento Card with top specular rim.
 * 3. Continuous sculpted `ExpressiveGroup` list for main Home sections.
 * 4. Liquid Glass Modal Bottom Sheet with Up/Down section reordering.
 */
@Composable
fun HomeSectionsScreen(
    onBack: () -> Unit,
    viewModel: HomeSectionsViewModel = hiltViewModel(),
) {
    val hidden by viewModel.hiddenSections.collectAsStateWithLifecycle()
    val ytHidden by viewModel.ytMusicHiddenSections.collectAsStateWithLifecycle()
    val ytOrder by viewModel.ytMusicSectionsOrder.collectAsStateWithLifecycle()
    val visibleCount = HomeSection.entries.size - hidden.size
    var showYtMusicSheet by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()

    SettingsGlassScaffold(
        title = stringResource(R.string.settings_home_sections),
        subtitle = stringResource(
            R.string.home_sections_visible,
            visibleCount,
            HomeSection.entries.size,
        ),
        onBack = onBack,
        listState = listState,
        actions = {
            TextButton(
                onClick = viewModel::showAll,
                enabled = hidden.isNotEmpty(),
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.home_sections_show_all))
            }
        },
    ) { contentPadding, _ ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 1. YouTube Music Selection Bento Hero Card
            item(key = "yt_music_selection_hero") {
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
                    onClick = { showYtMusicSheet = true },
                    shape = RoundedCornerShape(26.dp),
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
                    border = BorderStroke(1.2.dp, heroBorder),
                    modifier = Modifier.fillMaxWidth(),
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
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroupIconBadge(
                                icon = Icons.Filled.Subscriptions,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 48.dp,
                                iconSize = 24.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "YouTube Music Shelves",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    ) {
                                        Text(
                                            text = "Interactive",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Reorder feed shelves, toggle categories & curate your Home feed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // 2. Main Native Home Sections List
            item(key = "home_sections_list") {
                SettingsGlassSection(
                    title = "Home Screen Feed Modules",
                    subtitle = stringResource(R.string.home_sections_desc),
                ) {
                    ExpressiveGroup(rowCount = HomeSection.entries.size) { index, position ->
                        val section = HomeSection.entries[index]
                        val visible = section.id !in hidden
                        val shape = groupShape(position)
                        val rowBorder = remember(isDark, primary) {
                            Brush.verticalGradient(
                                listOf(
                                    (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                                    Color.Transparent,
                                ),
                            )
                        }

                        Surface(
                            onClick = { viewModel.setSectionVisible(section, !visible) },
                            shape = shape,
                            color = if (visible) {
                                if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isDark) 0.35f else 0.50f)
                            },
                            border = BorderStroke(0.8.dp, rowBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GroupIconBadge(
                                    icon = section.icon(),
                                    container = if (visible) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    tint = if (visible) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    size = 42.dp,
                                    iconSize = 20.dp,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(section.titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(section.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (visible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = visible,
                                    onCheckedChange = { viewModel.setSectionVisible(section, it) },
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
                }
            }
        }

        if (showYtMusicSheet) {
            YouTubeMusicSectionsSheet(
                initialOrder = ytOrder,
                initialHidden = ytHidden,
                onSave = viewModel::saveYtMusicSections,
                onReset = viewModel::resetYtMusicSections,
                onDismiss = { showYtMusicSheet = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeMusicSectionsSheet(
    initialOrder: List<String>,
    initialHidden: Set<String>,
    onSave: (List<String>, Set<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val initialList = remember(initialOrder) {
        (initialOrder + DEFAULT_YT_MUSIC_SECTIONS).distinctBy { it.lowercase() }
    }
    var currentSections by remember(initialList) { mutableStateOf(initialList) }
    var currentHidden by remember(initialHidden) { mutableStateOf(initialHidden) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "YouTube Music Sections",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${currentSections.size - currentHidden.size} of ${currentSections.size} sections enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            currentSections = DEFAULT_YT_MUSIC_SECTIONS
                            currentHidden = emptySet()
                            onReset()
                        },
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                    FilledTonalButton(
                        onClick = {
                            onSave(currentSections, currentHidden)
                            onDismiss()
                        },
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(currentSections, key = { _, s -> s }) { index, sectionName ->
                    val isVisible = sectionName !in currentHidden
                    val position = when {
                        currentSections.size == 1 -> GroupPosition.SINGLE
                        index == 0 -> GroupPosition.TOP
                        index == currentSections.size - 1 -> GroupPosition.BOTTOM
                        else -> GroupPosition.MIDDLE
                    }
                    val shape = groupShape(position)
                    val rowBorder = remember(isDark, primary) {
                        Brush.verticalGradient(
                            listOf(
                                (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                                Color.Transparent,
                            ),
                        )
                    }

                    Surface(
                        shape = shape,
                        color = if (isVisible) {
                            if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isDark) 0.35f else 0.50f)
                        },
                        border = BorderStroke(0.7.dp, rowBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val list = currentSections.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index - 1, item)
                                            currentSections = list
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < currentSections.size - 1) {
                                            val list = currentSections.toMutableList()
                                            val item = list.removeAt(index)
                                            list.add(index + 1, item)
                                            currentSections = list
                                        }
                                    },
                                    enabled = index < currentSections.size - 1,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = if (index < currentSections.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sectionName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Switch(
                                checked = isVisible,
                                onCheckedChange = { checked ->
                                    currentHidden = if (checked) currentHidden - sectionName else currentHidden + sectionName
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
            }
        }
    }
}
