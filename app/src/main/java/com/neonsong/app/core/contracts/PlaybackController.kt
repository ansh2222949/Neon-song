package com.neonsong.app.core.contracts

import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlayableTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level playback control contract.
 * Exposes reactive player state and playback control commands to UI features.
 */
interface PlaybackController {
    /**
     * Observable stream of current player state (track, position, buffering, formats).
     */
    val state: StateFlow<MusicPlayerState>

    /** Resume playback of current paused track. */
    fun resume()

    /** Play a specific track. */
    fun playTrack(track: PlayableTrack, sourceLabel: String = "Neon Song")

    /** Pause playback. */
    fun pause()

    /** Toggle between play and pause. */
    fun togglePlayPause()

    /** Seek to a specific track position in milliseconds. */
    fun seekTo(positionMs: Long)

    /** Skip to the next track in queue. */
    fun next()

    /** Skip to the previous track or restart current track. */
    fun previous()

    /** Jump directly to a specific item in the queue. */
    fun seekToQueueItem(index: Int)

    /** Set playback speed and pitch scaling. */
    fun setPlaybackParameters(speed: Float, pitch: Float)

    /** Reset playback speed and pitch to default (1.0f). */
    fun resetPlaybackParameters()

    /** Set sleep timer duration in minutes (0 to cancel). */
    fun setSleepTimerMinutes(minutes: Int)

    /** Cycle sleep timer preset (off -> 15m -> 30m -> 45m -> 60m -> end of track). */
    fun cycleSleepTimer()

    /** Stop playback and release temporary resources. */
    fun stopAndClear()
}
