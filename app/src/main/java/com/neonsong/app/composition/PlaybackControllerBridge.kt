package com.neonsong.app.composition

import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.core.contracts.QueueController
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.PlayableTrack
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composition bridge binding [MusicPlayer] implementation to
 * [PlaybackController] and [QueueController] contracts.
 */
@Singleton
class PlaybackControllerBridge @Inject constructor(
    private val player: MusicPlayer
) : PlaybackController, QueueController {

    override val state: StateFlow<MusicPlayerState>
        get() = player.state

    override fun resume() {
        player.resume()
    }

    override fun playTrack(track: PlayableTrack, sourceLabel: String) {
        player.play(track = track, sourceLabel = sourceLabel)
    }

    override fun pause() {
        player.pause()
    }

    override fun togglePlayPause() {
        player.togglePlayPause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun next() {
        player.next()
    }

    override fun previous() {
        player.previous()
    }

    override fun seekToQueueItem(index: Int) {
        player.seekToQueueItem(index)
    }

    override fun setPlaybackParameters(speed: Float, pitch: Float) {
        player.setPlaybackParameters(speed, pitch)
    }

    override fun resetPlaybackParameters() {
        player.resetPlaybackParameters()
    }

    override fun setSleepTimerMinutes(minutes: Int) {
        player.setSleepTimerMinutes(minutes)
    }

    override fun cycleSleepTimer() {
        player.cycleSleepTimer()
    }

    override fun stopAndClear() {
        player.stopAndClear()
    }

    override fun playQueue(tracks: List<PlayableTrack>, startIndex: Int, sourceLabel: String) {
        player.playQueue(tracks, startIndex, sourceLabel)
    }

    override fun playDiscoverQueue(tracks: List<PlayableTrack>, startIndex: Int) {
        player.playDiscoverQueue(tracks, startIndex)
    }

    override fun playNext(track: PlayableTrack) {
        player.playNext(track)
    }

    override fun addToQueue(track: PlayableTrack) {
        player.addToQueue(track)
    }

    override fun removeQueueItem(index: Int) {
        player.removeQueueItem(index)
    }

    override fun moveQueueItem(from: Int, to: Int) {
        player.moveQueueItem(from, to)
    }

    override fun clearUpcoming() {
        player.clearUpcoming()
    }

    override fun toggleShuffle() {
        player.toggleShuffle()
    }

    override fun cycleRepeatMode() {
        player.cycleRepeatMode()
    }

    override fun peekUpcomingIndices(count: Int): List<Int> {
        return player.peekUpcomingIndices(count)
    }
}
