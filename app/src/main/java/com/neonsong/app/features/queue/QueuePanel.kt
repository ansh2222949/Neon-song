package com.neonsong.app.features.queue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.neonsong.app.ui.theme.LiquidGlassPreset
import com.neonsong.app.features.player.ui.PlayingWaveBars


import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.filled.ClearAll
import com.neonsong.app.ui.theme.LiquidGlassSurface
import com.neonsong.app.ui.theme.liquidGlassChrome
import com.neonsong.app.ui.theme.LocalLiquidGlassBackdrop
import com.neonsong.app.ui.theme.LocalLiquidGlass


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neonsong.app.R
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.ui.common.TrackContextMenuSheet
import com.neonsong.app.ui.common.TrackMenuCapabilities
import com.neonsong.app.ui.common.TrackMenuTarget
import com.neonsong.app.ui.player.PlayerArtwork
import com.neonsong.app.ui.player.isQueueLockedState
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import kotlinx.coroutines.launch

@Composable
fun QueuePanel(
    state: MusicPlayerState,
    player: MusicPlayer,
    ambientColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val isQueueLocked = isQueueLockedState
    val currentTrackColor = Color(0xFF64B5F6)
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var menuIndex by remember { mutableStateOf<Int?>(null) }

    // Stable keys so animateItem() can animate moves instead of treating
    // every shifted row as a new item. Duplicates get occurrence suffixes.
    val queueKeys = remember(state.queue) {
        val counts = mutableMapOf<String, Int>()
        state.queue.map { item ->
            val base = item.videoId ?: item.playbackUrl
                ?: "${item.artist}|${item.title}|${item.album}"
            val n = counts.getOrDefault(base, 0)
            counts[base] = n + 1
            "$base#$n"
        }
    }
    // Abort a stale drag if the queue itself changes underneath us
    // (track ended, clear-upcoming, fresh playQueue, …).
    LaunchedEffect(state.queue.size) {
        if (draggingIndex >= state.queue.size) {
            draggingIndex = -1
            dragOffsetY = 0f
        }
    }
    // Follow the now-playing song in long queues: auto-scroll on every track
    // change (and on first open), unless the user is dragging or has manually
    // scrolled within the last few seconds. The eye button still jumps on demand.
    var userScrollHoldUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) userScrollHoldUntil = System.currentTimeMillis() + 3_000L
    }
    LaunchedEffect(state.currentIndex, state.queue.size) {
        if (draggingIndex != -1 || state.queue.isEmpty()) return@LaunchedEffect
        if (System.currentTimeMillis() < userScrollHoldUntil) return@LaunchedEffect
        val target = state.currentIndex.coerceIn(0, state.queue.size - 1)
        runCatching { listState.animateScrollToItem(target) }
    }
    // True upcoming play order (shuffle/repeat aware). Under shuffle the list
    // below stays in playlist order, so without this strip you can never tell
    // what plays next — and Play-next adds look "lost".
    val upcomingIndices = remember(state.queue, state.currentIndex, state.shuffleEnabled, state.repeatMode) {
        runCatching { player.peekUpcomingIndices(4) }.getOrDefault(emptyList())
    }.filter { it in state.queue.indices && it != state.currentIndex }
    val nextQueueIndex = upcomingIndices.firstOrNull()
    val shuffleUpcoming = if (state.shuffleEnabled) upcomingIndices else emptyList()

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                val playingPosition = state.currentIndex
                    .takeIf { it in state.queue.indices }
                    ?.plus(1)
                    ?.let { " · $it playing" }
                    .orEmpty()
                Text(
                    when {
                        state.queue.isEmpty() -> "No songs queued"
                        state.isEndlessQueue -> "Unlimited songs$playingPosition"
                        else -> "${state.queue.size} songs$playingPosition"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.70f),
                )
                if (state.queue.size > 1 && !isQueueLocked) {
                    Text(
                        stringResource(com.neonsong.app.R.string.queue_rearrange_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statsText = remember(state.queue) {
                    QueueStatsFormatter.formatQueueStats(state.queue)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = statsText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.90f),
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = {
                        isQueueLockedState = !isQueueLockedState
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isQueueLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .border(
                            0.8.dp,
                            if (isQueueLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else Color.White.copy(alpha = 0.16f),
                            RoundedCornerShape(16.dp)
                        ),
                ) {
                    Icon(
                        imageVector = if (isQueueLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = if (isQueueLocked) "Unlock queue" else "Lock queue",
                        tint = if (isQueueLocked) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.90f),
                    )
                }
                if (state.queue.size > state.currentIndex + 1 && !isQueueLocked) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            player.clearUpcoming()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                    ) {
                        Icon(
                            Icons.Filled.ClearAll,
                            contentDescription = "Clear upcoming songs",
                            tint = Color.White.copy(alpha = 0.90f),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        userScrollHoldUntil = 0L
                        scope.launch { listState.animateScrollToItem(state.currentIndex.coerceAtLeast(0)) }
                    },
                    enabled = state.currentIndex >= 0 && state.currentIndex < state.queue.size,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = "Scroll to current song",
                        tint = Color.White.copy(alpha = 0.90f),
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.queue, key = { index, _ -> queueKeys.getOrNull(index) ?: index.toString() }) { index, item ->
                val isCurrent = index == state.currentIndex
                val isDragging = index == draggingIndex
                val stableKey = queueKeys.getOrNull(index) ?: index.toString()
                val currentIndex by rememberUpdatedState(index)
                @Suppress("DEPRECATION")
                val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        if (dismissValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                            player.removeQueueItem(currentIndex)
                            true
                        } else {
                            false
                        }
                    }
                )
                androidx.compose.material3.SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = !isQueueLocked,
                    enableDismissFromEndToStart = !isQueueLocked,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        // The delete background must only exist while a swipe
                        // is actually in progress: the queue card is
                        // translucent (liquid glass), so a permanently
                        // composed trash icon ghosts through the card and
                        // visually merges with the drag handle at the same
                        // CenterEnd spot.
                        if (dismissState.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                            val alignment = if (dismissState.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = alignment
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    },
                    content = {
                LiquidGlassSurface(
                    glassModifier = Modifier.liquidGlassChrome(RoundedCornerShape(18.dp), LocalLiquidGlass.current),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        player.seekToQueueItem(index)
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isCurrent) ambientColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                    contentColor = Color.White,
                    border = BorderStroke(
                        if (isCurrent) 1.2.dp else 0.8.dp,
                        if (isCurrent) ambientColor.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            shadowElevation = if (isDragging) 18f else 0f
                            val s = if (isDragging) 1.025f else 1f
                            scaleX = s
                            scaleY = s
                            alpha = if (isDragging) 0.96f else 1f
                        },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier.width(30.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PlayingWaveBars(
                                    waveColor = ambientColor,
                                    containerColor = Color.Transparent,
                                    isPlaying = state.isPlaying,
                                    modifier = Modifier.size(16.dp, 14.dp),
                                )
                            }
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.55f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(30.dp),
                            )
                        }
                        PlayerArtwork(item, Modifier.size(48.dp), 12.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isCurrent) ambientColor else Color.White,
                            )
                            Text(
                                item.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) ambientColor.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.60f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { menuIndex = index },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White.copy(alpha = 0.75f),
                            )
                        }
                        if (!isQueueLocked) {
                            Icon(
                                Icons.Filled.DragHandle,
                                stringResource(com.neonsong.app.R.string.queue_drag_hint),
                                tint = if (isDragging) ambientColor else Color.White.copy(alpha = 0.60f),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isDragging) ambientColor.copy(alpha = 0.28f)
                                        else Color.White.copy(alpha = 0.08f),
                                    )
                                    .padding(8.dp)
                                    .pointerInput(stableKey) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragOffsetY = 0f
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                draggingIndex = -1
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = -1
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val source = draggingIndex
                                                if (source < 0) return@detectDragGesturesAfterLongPress
                                                dragOffsetY += dragAmount.y
                                                val layoutInfo = listState.layoutInfo
                                                val draggedInfo = layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.index == source }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val draggedCenter = draggedInfo.offset + draggedInfo.size / 2 + dragOffsetY.toInt()
                                                val target = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                    info.index != source &&
                                                        info.index in state.queue.indices &&
                                                        draggedCenter in info.offset..(info.offset + info.size)
                                                }?.index
                                                if (target != null && target != source) {
                                                    player.moveQueueItem(source, target)
                                                    // Keep the row glued under the finger across
                                                    // the layout shift caused by the move.
                                                    val targetInfo = layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.index == target }
                                                    if (targetInfo != null) {
                                                        dragOffsetY += (draggedInfo.offset - targetInfo.offset).toFloat()
                                                    }
                                                    draggingIndex = target
                                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                                }
                                                // Edge auto-scroll while dragging.
                                                val viewportStart = layoutInfo.viewportStartOffset
                                                val viewportEnd = layoutInfo.viewportEndOffset
                                                val edgeZone = 180
                                                when {
                                                    draggedCenter < viewportStart + edgeZone ->
                                                        scope.launch { listState.scrollBy(-28f) }
                                                    draggedCenter > viewportEnd - edgeZone ->
                                                        scope.launch { listState.scrollBy(28f) }
                                                }
                                            },
                                        )
                                    },
                            )
                        }
                    }
                }
                }
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    menuIndex?.let { index ->
        val itemTrack = state.queue.getOrNull(index)
        if (itemTrack != null) {
            com.neonsong.app.ui.common.TrackContextMenuSheet(
                target = com.neonsong.app.ui.common.TrackMenuTarget.Track(itemTrack.title, itemTrack.artist, itemTrack.album ?: ""),
                capabilities = com.neonsong.app.ui.common.TrackMenuCapabilities(showCopyActions = true),
                playableTrack = itemTrack,
                onDismiss = { menuIndex = null },
                onRemoveFromQueue = if (isQueueLocked) null else ({ player.removeQueueItem(index) })
            )
        } else {
            menuIndex = null
        }
    }
}
