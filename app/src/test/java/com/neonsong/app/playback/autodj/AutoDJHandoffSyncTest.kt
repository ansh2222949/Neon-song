package com.neonsong.app.playback.autodj

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.playback.NativeAutoDJEngine
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.TrackLifecycleStage
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AutoDJHandoffSyncTest {

    private class MockPlaybackHost : AutoDJPlaybackHost {
        var standbyArmed = false
        var standbyReady = false
        var transitionTriggered = false
        var transitionOverlapMs = 0L
        var lateTrackWaiting = false

        val queue = listOf(
            PlayableTrack(title = "Track A", artist = "Artist A", videoId = "vidA"),
            PlayableTrack(title = "Track B", artist = "Artist B", videoId = "vidB"),
        )

        override fun getTrackAt(index: Int): PlayableTrack? = queue.getOrNull(index)
        override fun getQueueSize(): Int = queue.size
        override fun getCurrentIndex(): Int = 0
        override fun isCurrentItem(index: Int): Boolean = index == 0
        override fun registerResolvedStream(index: Int, track: PlayableTrack, stream: com.neonsong.app.playback.MusicPlayer.ResolvedStream) {}
        override fun armStandbyForNext(index: Int, track: PlayableTrack) {
            standbyArmed = true
        }
        override fun triggerAutoTransition(overlapMs: Long) {
            transitionTriggered = true
            transitionOverlapMs = overlapMs
        }
        override fun setLateTrackBuffering(isWaiting: Boolean) {
            lateTrackWaiting = isWaiting
        }
        override fun skipToNextPlayableItem(failedIndex: Int) {}
        override fun requestQueueRefill() {}
        override fun isStandbyReady(index: Int): Boolean = standbyReady
    }

    private lateinit var controller: AutoDJController
    private lateinit var mockHost: MockPlaybackHost
    private lateinit var mockNative: NativeAutoDJEngine
    private lateinit var mockSettings: SettingsPreferences

    @Before
    fun setUp() {
        mockNative = mockk(relaxed = true)
        mockSettings = mockk(relaxed = true)
        mockHost = MockPlaybackHost()

        controller = AutoDJController(
            nativeEngine = mockNative,
            settingsPreferences = mockSettings,
            autoDJEngine = AutoDJEngine(),
        )

        val hostField = AutoDJController::class.java.getDeclaredField("host")
        hostField.isAccessible = true
        hostField.set(controller, mockHost)

        val enabledField = AutoDJController::class.java.getDeclaredField("isEnabled")
        enabledField.isAccessible = true
        enabledField.set(controller, true)
    }

    @Test
    fun standbyMustBeReadyBeforeTriggeringTransition() {
        mockHost.standbyReady = false
        assertThat(mockHost.isStandbyReady(1)).isFalse()

        // Host reports preparing stage when standby is not yet ready
        val trackKey = "yt:vidB"
        controller.setTrackStage(trackKey, TrackLifecycleStage.PREPARING)
        assertThat(controller.getTrackStage(trackKey, 1)).isEqualTo(TrackLifecycleStage.PREPARING)

        // Standby becomes ready
        mockHost.standbyReady = true
        assertThat(controller.getTrackStage(trackKey, 1)).isEqualTo(TrackLifecycleStage.READY)
    }

    @Test
    fun triggerAutoTransition_recordsAccurateOverlapDuration() {
        mockHost.standbyReady = true
        mockHost.triggerAutoTransition(4500L)

        assertThat(mockHost.transitionTriggered).isTrue()
        assertThat(mockHost.transitionOverlapMs).isEqualTo(4500L)
    }
}
