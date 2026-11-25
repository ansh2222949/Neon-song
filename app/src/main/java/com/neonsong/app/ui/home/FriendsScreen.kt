package com.neonsong.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.ExpressiveGroupTrackRow
import com.neonsong.app.ui.common.ExpressiveHeader
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.common.NeonGlassSection
import com.neonsong.app.ui.common.NeonLivingCanvas
import com.neonsong.app.ui.common.adaptiveContentWidth
import com.neonsong.app.ui.common.rememberExpressiveHeaderBackdrop
import com.neonsong.app.ui.common.safeDrawingBottomPadding
import com.neonsong.app.ui.common.safeHorizontalContentPadding
import com.neonsong.app.ui.player.LocalMiniPlayerScrollClearance
import com.neonsong.app.ui.player.LocalMusicPlayer
import com.neonsong.app.ui.theme.liquidGlassSource

/**
 * A real pushed screen now, not a Dialog/ModalBottomSheet — see the long
 * comment trail this replaced in HomeScreen.kt's old FriendsSheet for the
 * full history. Every other pushed screen in this app (Settings, Search,
 * Discover, Genres) already renders correctly edge-to-edge
 * using this exact Scaffold-less ExpressiveHeader + Column pattern, so
 * Friends now just follows the same proven structure instead of being a
 * one-off overlay fighting Compose's Dialog/BottomSheet sizing quirks.
 *
 * Shares HomeViewModel with the Home tab (see NavGraph.kt — scoped to
 * MainShell's own back stack entry) rather than owning its own state, so
 * switching to a friend's profile here is immediately reflected on Home
 * once you navigate back.
 */
@Composable
fun FriendsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onOpenFriendProfile: (username: String, displayName: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val headerBackdrop = rememberExpressiveHeaderBackdrop()
    val musicPlayer = LocalMusicPlayer.current
    val chromeState by musicPlayer.chromeState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val isScrolling by remember(scrollState) { derivedStateOf { scrollState.isScrollInProgress } }

    // Loads the friends list the first time this screen is reached, same
    // trigger openFriendsSheet() used to provide via the old sheet's open
    // action — now driven by navigation arriving here instead.
    LaunchedEffect(Unit) {
        viewModel.openFriendsSheet()
    }

    val friends = uiState.sortedFriends
    val pinnedFriends = uiState.pinnedFriends
    val isLoading = uiState.isLoadingFriends
    val isViewingFriend = uiState.isViewingFriend

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .adaptiveContentWidth(maxWidth = 760.dp),
        ) {
                ExpressiveHeader(
                    title = "Friends",
                    subtitle = if (friends.isNotEmpty()) "Long-press a friend to pin them to the top" else null,
                    onBack = onBack,
                    backdrop = headerBackdrop,
                )

                Spacer(Modifier.height(10.dp))

                Column(
                    Modifier
                        .fillMaxSize()
                        .safeHorizontalContentPadding()
                        .verticalScroll(scrollState)
                        .liquidGlassSource(headerBackdrop)
                        .padding(
                            top = 4.dp,
                            bottom = 24.dp + LocalMiniPlayerScrollClearance.current + safeDrawingBottomPadding(),
                        ),
                ) {
                    NeonGlassSection(horizontalMargin = 0.dp) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            when {
                                isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    ExpressiveLoadingIndicator(message = "Loading friends")
                                }
                                friends.isEmpty() -> Text(
                                    "No friends found on this Last.fm account.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                                else -> {
                                    val rowCount = friends.size + if (isViewingFriend) 1 else 0
                                    ExpressiveGroup(rowCount = rowCount) { index, position ->
                                        if (isViewingFriend && index == 0) {
                                            ExpressiveGroupTrackRow(
                                                title = "Back to my profile",
                                                subtitle = "View your own data again",
                                                position = position,
                                                onClick = {
                                                    viewModel.returnToOwnProfile()
                                                    onBack()
                                                },
                                                leading = {
                                                    Box(
                                                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    }
                                                },
                                            )
                                        } else {
                                            val friend = friends[index - if (isViewingFriend) 1 else 0]
                                            val isPinned = friend.name in pinnedFriends
                                            ExpressiveGroupTrackRow(
                                                title = friend.displayName,
                                                subtitle = "@${friend.name}",
                                                position = position,
                                                onClick = {
                                                    onOpenFriendProfile(friend.name, friend.displayName, friend.avatarUrl)
                                                },
                                                onLongClick = { viewModel.toggleFriendPinned(friend.name) },
                                                leading = {
                                                    if (!friend.avatarUrl.isNullOrBlank()) {
                                                        ArtworkImage(
                                                            name = "friend",
                                                            artist = friend.name,
                                                            embeddedUrl = friend.avatarUrl,
                                                            fallbackIcon = Icons.Filled.AccountCircle,
                                                            modifier = Modifier.size(44.dp).clip(CircleShape),
                                                        )
                                                    } else {
                                                        Box(
                                                            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                },
                                                trailing = if (isPinned) {
                                                    { Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                                } else null,
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
    }
