package com.neonsong.app.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTonalActionButton as FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neonsong.app.data.generate.GenerateRepository
import com.neonsong.app.data.local.db.RecommendationExclusionEntity
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExcludedSongsViewModel @Inject constructor(
    private val generateRepository: GenerateRepository,
) : ViewModel() {
    val exclusions = generateRepository.observeRecommendationExclusions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun allowAgain(trackKey: String) {
        viewModelScope.launch {
            generateRepository.removeRecommendationExclusion(trackKey)
        }
    }
}

/**
 * Excluded Songs Screen redesign.
 *
 * Implements:
 * 1. Song-Reactive Liquid Glass Scaffold.
 * 2. Visual empty state with glowing tonal badge.
 * 3. Continuous sculpted `ExpressiveGroup` list of excluded items with 1-tap restore action.
 */
@Composable
fun ExcludedSongsScreen(
    onBack: () -> Unit,
    viewModel: ExcludedSongsViewModel = hiltViewModel(),
) {
    val exclusions by viewModel.exclusions.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()

    SettingsGlassScaffold(
        title = "Excluded Songs",
        subtitle = "${exclusions.size} song${if (exclusions.size == 1) "" else "s"} hidden from recommendations",
        onBack = onBack,
        listState = listState,
    ) { contentPadding, _ ->
        if (exclusions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
                    border = BorderStroke(
                        0.9.dp,
                        Brush.verticalGradient(
                            listOf(
                                (if (isDark) Color.White else primary).copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(76.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                        Text(
                            text = "No Excluded Songs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Tracks marked with “Don't recommend again” will appear here. You can restore them to your recommendations at any time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "excluded_songs_section") {
                    SettingsGlassSection(
                        title = "Recommendation Exclusions",
                        subtitle = "Tracks excluded from Home mixes & Auto DJ generation",
                    ) {
                        ExpressiveGroup(rowCount = exclusions.size) { index, position ->
                            val exclusion = exclusions[index]
                            ExcludedSongRow(
                                exclusion = exclusion,
                                position = position,
                                onAllowAgain = { viewModel.allowAgain(exclusion.trackKey) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcludedSongRow(
    exclusion: RecommendationExclusionEntity,
    position: GroupPosition,
    onAllowAgain: () -> Unit,
) {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = groupShape(position)

    val legacyParts = exclusion.trackKey.split('|', limit = 2)
    val title = exclusion.trackName.ifBlank {
        legacyParts.firstOrNull().orEmpty().legacyDisplayName("Unknown song")
    }
    val artist = exclusion.artistName.ifBlank {
        legacyParts.getOrNull(1).orEmpty().legacyDisplayName("Unknown artist")
    }
    val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .format(Date(exclusion.excludedAtMillis))

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
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
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
                icon = Icons.Filled.Block,
                container = MaterialTheme.colorScheme.errorContainer,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                size = 42.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$artist • Hidden $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(
                onClick = onAllowAgain,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Filled.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Restore",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun String.legacyDisplayName(fallback: String): String =
    trim().takeIf(String::isNotEmpty)
        ?.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }
        ?: fallback
