package com.neonsong.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import com.neonsong.app.ui.theme.GlassActionButton as Button
import androidx.compose.material3.ButtonDefaults
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.data.playlist.ExternalTrackRow
import com.neonsong.app.data.playlist.SavedPlaylist
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.groupPositionFor
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.theme.LocalIsDarkTheme

/** How many preview rows are listed before a "+N more tracks" footer. */
private const val PREVIEW_ROW_LIMIT = 50

/**
 * Paste-a-link importer for public Spotify and Apple Music playlists redesign.
 *
 * Implements:
 * 1. Song-Reactive Liquid Glass Scaffold.
 * 2. 3D Glass Hero Card for link pasting and clipboard integration.
 * 3. Continuous sculpted `ExpressiveGroup` list for track previews.
 * 4. Floating bottom Import CTA with liquid glass sheen.
 */
@Composable
fun ExternalPlaylistImportScreen(
    onBack: () -> Unit,
    onImportSuccess: (SavedPlaylist) -> Unit,
    viewModel: ExternalPlaylistImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()

    val preview = state.preview
    val rows = preview?.rows.orEmpty()
    val visibleRows = rows.take(PREVIEW_ROW_LIMIT)

    SettingsGlassScaffold(
        title = "Import Playlist",
        subtitle = "Spotify & Apple Music links",
        onBack = onBack,
        listState = listState,
    ) { contentPadding, _ ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .adaptiveContentWidth(maxWidth = 860.dp),
            ) {
                // Link input Hero Card
                item(key = "paste_link_hero_card") {
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

                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GroupIconBadge(
                                        icon = Icons.Filled.Link,
                                        container = MaterialTheme.colorScheme.primaryContainer,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        size = 46.dp,
                                        iconSize = 22.dp,
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "Paste Playlist Link",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "Paste a public Spotify or Apple Music link to import tracks into Neon Song",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = state.link,
                                    onValueChange = viewModel::onLinkChange,
                                    placeholder = { Text("https://open.spotify.com/playlist/...") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingIcon = {
                                        if (state.link.isNotBlank()) {
                                            IconButton(onClick = { viewModel.onLinkChange("") }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                            }
                                        } else {
                                            IconButton(onClick = {
                                                val text = clipboard.getText()?.text.orEmpty()
                                                if (text.isNotBlank()) {
                                                    viewModel.onLinkChange(text)
                                                }
                                            }) {
                                                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = {
                                        keyboard?.hide()
                                        viewModel.loadPreview()
                                    }),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.50f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        keyboard?.hide()
                                        viewModel.loadPreview()
                                    },
                                    enabled = state.link.isNotBlank() && !state.isLoading && !state.isImporting,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Reading Playlist...")
                                    } else {
                                        Icon(Icons.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Preview Playlist", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Preview summary
                if (preview != null) {
                    item(key = "playlist_preview_summary") {
                        SettingsGlassSection(
                            title = preview.title,
                            subtitle = preview.author?.takeIf { it.isNotBlank() } ?: "External Playlist",
                            trailingBadge = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Text(
                                        text = preview.source.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (rows.size == 1) "1 track available" else "${rows.size} tracks available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                state.lastResult?.let { result ->
                                    val skipped = (result.totalRows - result.matchedCount).coerceAtLeast(0)
                                    Text(
                                        text = "${result.matchedCount} imported, $skipped skipped",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                val summary = state.progress
                                if (summary != null && !state.isImporting) {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Bounded track list
                    item(key = "track_preview_group") {
                        SettingsGlassSection(
                            title = "Tracks Preview",
                            subtitle = "Songs identified for matching and import",
                        ) {
                            ExpressiveGroup(rowCount = visibleRows.size) { index, position ->
                                val row = visibleRows[index]
                                ExternalTrackRowCard(
                                    row = row,
                                    position = position,
                                )
                            }
                        }
                    }

                    if (rows.size > visibleRows.size) {
                        item(key = "more_tracks_footer") {
                            Text(
                                text = "+ ${rows.size - visibleRows.size} more tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }

                // Error Message Card
                state.errorMessage?.let { error ->
                    item(key = "error_banner") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(80.dp))
                }
            }

            // Floating bottom Import CTA
            AnimatedVisibility(
                visible = rows.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .adaptiveContentWidth(maxWidth = 600.dp)
                    .padding(bottom = 20.dp + LocalMiniPlayerScrollClearance.current)
                    .padding(horizontal = 20.dp),
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.import { saved -> onImportSuccess(saved) }
                    },
                    enabled = !state.isImporting,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = state.progress ?: "Importing Playlist...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (rows.size == 1) "Import 1 track" else "Import ${rows.size} tracks",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalTrackRowCard(
    row: ExternalTrackRow,
    position: GroupPosition,
) {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
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
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
        border = BorderStroke(0.8.dp, rowBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconBadge(
                icon = Icons.Filled.MusicNote,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 38.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.artist.isNotBlank()) {
                    Text(
                        text = row.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
