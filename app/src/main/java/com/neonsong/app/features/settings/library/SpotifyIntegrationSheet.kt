package com.neonsong.app.features.settings.library

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.data.playlist.SavedPlaylist
import com.neonsong.app.data.spotify.SpotifyAuthState
import com.neonsong.app.data.spotify.SpotifyBatchProgress
import com.neonsong.app.ui.common.EdgeToEdgeDialogWindow
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.theme.GlassActionButton as Button
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import com.neonsong.app.ui.theme.GlassTonalActionButton as FilledTonalButton
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.bounceClick
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.liquidGlassSurface

import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily

private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDarkSurface = Color(0xFF121212)

/**
 * Editorial Liquid Glass bottom sheet for Spotify OAuth 2.0 PKCE Login,
 * Liked Songs synchronization, and playlist resolution via YouTube audio streams.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyIntegrationSheet(
    authState: SpotifyAuthState,
    clientId: String,
    redirectUri: String,
    playlists: List<SavedPlaylist>,
    isLoading: Boolean,
    errorMessage: String? = null,
    importProgress: SpotifyBatchProgress?,
    onSetClientId: (String) -> Unit,
    onSetRedirectUri: (String) -> Unit,
    onLogin: (Context) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshPlaylists: () -> Unit,
    onImportLikedSongs: () -> Unit,
    onImportPlaylist: (SavedPlaylist) -> Unit,
    onOpenExternalImport: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    var showClientIdDialog by remember { mutableStateOf(false) }
    var copiedUri by remember { mutableStateOf(false) }

    val isAuthenticated = authState is SpotifyAuthState.Authenticated

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && playlists.isEmpty()) {
            onRefreshPlaylists()
        }
    }

    val isDark = LocalIsDarkTheme.current
    val sheetShape = remember {
        SquircleShape(
            topStart = CornerSize(28.dp),
            topEnd = CornerSize(28.dp),
            bottomEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.97f),
        tonalElevation = 6.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                (if (isDark) Color.White else SpotifyGreen).copy(alpha = 0.45f),
                                (if (isDark) Color.White else SpotifyGreen).copy(alpha = 0.20f),
                            ),
                        ),
                    ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveContentWidth(maxWidth = 640.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp + safeDrawingBottomPadding()),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SquircleShape(14.dp))
                        .background(SpotifyGreen.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "Spotify",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Spotify Account & Library",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                    )
                    Text(
                        text = if (isAuthenticated) "Connected • OAuth 2.0 PKCE Session Active" else "Sync Liked Songs & personal playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAuthenticated) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Real-time batch import progress card
            AnimatedVisibility(visible = importProgress != null) {
                if (importProgress != null) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = importProgress.progressFraction,
                        label = "spotifyImportProgress",
                    )
                    val cardShape = remember { SquircleShape(18.dp) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .liquidGlassSurface(
                                shape = cardShape,
                                borderWidth = 1.2.dp,
                                accentTint = SpotifyGreen,
                            )
                            .clip(cardShape)
                            .padding(16.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (importProgress.isFinished) "Import Finished!" else "Resolving Tracks via YouTube…",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyGreen,
                                )
                                Text(
                                    text = "${importProgress.completed}/${importProgress.total}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = SpotifyGreen,
                                trackColor = SpotifyGreen.copy(alpha = 0.2f),
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = importProgress.currentTitle?.let { "Currently matching: $it" }
                                    ?: "${importProgress.resolvedTracks.size} resolved • ${importProgress.failureCount} skipped",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (!isAuthenticated) {
                // Unauthenticated State
                val infoShape = remember { SquircleShape(18.dp) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(
                            shape = infoShape,
                            borderWidth = 0.75.dp,
                        )
                        .clip(infoShape)
                        .padding(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Connect Your Spotify Library",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Sign in securely via official Spotify OAuth 2.0 PKCE. Your saved Liked Songs and playlists will be resolved to high-fidelity audio streams for seamless in-app playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )

                        // Client ID Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { showClientIdDialog = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Client ID Configuration",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (clientId.isBlank() || clientId == "YOUR_SPOTIFY_CLIENT_ID") "Default Client ID (Tap to configure)" else "Client ID: ${clientId.take(8)}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Configuration",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        // Redirect URI Row with 1-Tap Copy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Redirect URI",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = redirectUri,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = SpotifyGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(SquircleShape(8.dp))
                                    .background(SpotifyGreen.copy(alpha = 0.15f))
                                    .bounceClick {
                                        clipboardManager.setText(AnnotatedString(redirectUri))
                                        copiedUri = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (copiedUri) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                        contentDescription = "Copy URI",
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (copiedUri) "Copied!" else "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SpotifyGreen,
                                    )
                                }
                            }
                        }

                        // Step-by-step Setup Helper Card for Dashboard
                        val guideShape = remember { SquircleShape(12.dp) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(guideShape)
                                .background(SpotifyGreen.copy(alpha = 0.08f))
                                .padding(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Fix 'Not matching configuration':",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SpotifyGreen,
                                        ),
                                    )
                                }
                                Text(
                                    text = "1. Open developer.spotify.com/dashboard & select your app\n2. Click 'Settings' (top-right)\n3. Under 'Redirect URIs', click 'Add' & paste the URI above\n4. Click 'Save' at bottom of page, then tap Log In below!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Primary Login Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bounceClick {
                            onLogin(context)
                        }
                        .clip(SquircleShape(16.dp))
                        .background(SpotifyGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Log In with Spotify",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            ),
                        )
                    }
                }
            } else {
                // Authenticated State: Library Actions & Playlists
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Error Notice / Spotify Developer Premium Policy Alert
                    if (!errorMessage.isNullOrBlank()) {
                        item(key = "spotify_error_notice") {
                            val isPremiumError = errorMessage.contains("premium", ignoreCase = true) || errorMessage.contains("403", ignoreCase = true)
                            val alertShape = remember { SquircleShape(16.dp) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassSurface(
                                        shape = alertShape,
                                        borderWidth = 1.dp,
                                        accentTint = if (isPremiumError) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                    )
                                    .clip(alertShape)
                                    .padding(16.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WarningAmber,
                                            contentDescription = null,
                                            tint = if (isPremiumError) SpotifyGreen else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = if (isPremiumError) "Spotify Web API Policy (HTTP 403)" else "Sync Notice",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPremiumError) SpotifyGreen else MaterialTheme.colorScheme.error,
                                        )
                                    }

                                    Text(
                                        text = if (isPremiumError) {
                                            "Spotify requires the developer account that created this Client ID to have an Active Spotify Premium subscription.\n\nDon't have Spotify Premium? You can still import ANY Spotify playlist for 100% free using our built-in URL Importer below!"
                                        } else {
                                            errorMessage
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp,
                                    )

                                    if (isPremiumError) {
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(SquircleShape(12.dp))
                                                .background(SpotifyGreen)
                                                .bounceClick {
                                                    onOpenExternalImport()
                                                }
                                                .padding(vertical = 11.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "Import via Playlist Link (Free • No Premium)",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Liked Songs Card
                    item(key = "spotify_liked_songs") {
                        val likedShape = remember { SquircleShape(18.dp) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassSurface(
                                    shape = likedShape,
                                    borderWidth = 0.8.dp,
                                    accentTint = SpotifyGreen,
                                )
                                .clip(likedShape)
                                .clickable {
                                    onImportLikedSongs()
                                }
                                .padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(SquircleShape(12.dp))
                                        .background(SpotifyGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Sync Liked Songs",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Import all your saved Spotify tracks into Neon Song",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                FilledTonalButton(
                                    onClick = onImportLikedSongs,
                                    shape = SquircleShape(12.dp),
                                ) {
                                    Text("Sync", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Playlists Header
                    item(key = "spotify_playlists_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Your Spotify Playlists (${playlists.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = onRefreshPlaylists,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Refresh", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (isLoading && playlists.isEmpty()) {
                        item(key = "spotify_playlists_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (playlists.isEmpty()) {
                        item(key = "spotify_playlists_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (errorMessage?.contains("premium", ignoreCase = true) == true) "Playlists locked by Spotify Developer Premium policy" else "No Spotify playlists found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(playlists.size, key = { playlists[it].id }) { index ->
                            val playlist = playlists[index]
                            val itemShape = remember { SquircleShape(16.dp) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassSurface(
                                        shape = itemShape,
                                        borderWidth = 0.75.dp,
                                    )
                                    .clip(itemShape)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(SquircleShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    Spacer(Modifier.width(14.dp))

                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = playlist.subtitle.ifBlank { "${playlist.remoteTrackCount ?: playlist.tracks.size} tracks" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    FilledTonalButton(
                                        onClick = { onImportPlaylist(playlist) },
                                        shape = SquircleShape(10.dp),
                                    ) {
                                        Text("Import", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Disconnect Section
                    item(key = "spotify_disconnect_section") {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .bounceClick {
                                    onDisconnect()
                                }
                                .clip(SquircleShape(14.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Disconnect Spotify Account",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // Spotify OAuth Customization Dialog
    if (showClientIdDialog) {
        var inputId by remember { mutableStateOf(if (clientId == "YOUR_SPOTIFY_CLIENT_ID") "" else clientId) }
        var inputUri by remember { mutableStateOf(redirectUri) }
        AlertDialog(
            onDismissRequest = { showClientIdDialog = false },
            title = {
                Text("Spotify OAuth Configuration", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure your Spotify Developer App credentials. The Redirect URI MUST exactly match what you entered in developer.spotify.com/dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = inputId,
                        onValueChange = { inputId = it },
                        label = { Text("Client ID") },
                        placeholder = { Text("Enter Spotify Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = inputUri,
                        onValueChange = { inputUri = it },
                        label = { Text("Redirect URI") },
                        placeholder = { Text("e.g. neonsong://spotify-auth-callback") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val presets = listOf(
                            "neonsong://spotify-auth-callback",
                            "neonsong://callback",
                        )
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(SquircleShape(8.dp))
                                    .background(
                                        if (inputUri == preset) SpotifyGreen.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .bounceClick { inputUri = preset }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = preset.substringAfter("://"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (inputUri == preset) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (inputUri == preset) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputId.isNotBlank()) onSetClientId(inputId)
                    if (inputUri.isNotBlank()) onSetRedirectUri(inputUri)
                    showClientIdDialog = false
                }) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClientIdDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
