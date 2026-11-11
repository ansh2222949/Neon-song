package com.neonsong.app.core.contracts

import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlayableTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Queue management contract.
 * Exposes queue mutation and playlist scheduling actions.
 */
interface QueueController {
    /**
     * Observable stream containing current queue items and active index.
     */
    val state: StateFlow<MusicPlayerState>

    /** Play a full queue of tracks starting from the specified index. */
    fun playQueue(
        tracks: List<PlayableTrack>,
        startIndex: Int = 0,
        sourceLabel: String = "Neon Song"
    )

    /** Start playing discover/radio queue without disrupting current manual items. */
    fun playDiscoverQueue(
        tracks: List<PlayableTrack>,
        startIndex: Int = 0
    )

    /** Insert a track to play immediately after the current track. */
    fun playNext(track: PlayableTrack)

    /** Append a track to the end of the current queue. */
    fun addToQueue(track: PlayableTrack)

    /** Remove an item from the queue by its index. */
    fun removeQueueItem(index: Int)

    /** Reorder an item within the queue. */
    fun moveQueueItem(from: Int, to: Int)

    /** Clear all upcoming tracks after the current item. */
    fun clearUpcoming()

    /** Toggle shuffle mode on/off. */
    fun toggleShuffle()

    /** Cycle repeat mode (OFF -> ONE -> ALL). */
    fun cycleRepeatMode()

    /** Jump directly to playback of a queue item at [index]. */
    fun seekToQueueItem(index: Int)

    /** Look ahead at upcoming track indices according to shuffle/repeat rules. */
    fun peekUpcomingIndices(count: Int = 4): List<Int> = emptyList()
}
