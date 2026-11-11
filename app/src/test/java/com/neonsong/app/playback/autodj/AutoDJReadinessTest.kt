package com.neonsong.app.playback.autodj

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.NativeAutoDJEngine
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.TrackLifecycleStage
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class AutoDJReadinessTest {

    private class TestPlaybackHost : AutoDJPlaybackHost {
        var standbyReady = false
        val queue = listOf(
            PlayableTrack(title = "Track 1", artist = "Artist 1", videoId = "v1"),
            PlayableTrack(title = "Track 2", artist = "Artist 2", videoId = "v2"),
        )
        override fun getTrackAt(index: Int): PlayableTrack? = queue.getOrNull(index)
        override fun getQueueSize(): Int = queue.size
        override fun getCurrentIndex(): Int = 0
        override fun isCurrentItem(index: Int): Boolean = index == 0
        override fun registerResolvedStream(index: Int, track: PlayableTrack, stream: MusicPlayer.ResolvedStream) {}
        override fun armStandbyForNext(index: Int, track: PlayableTrack) {}
        override fun triggerAutoTransition(overlapMs: Long) {}
        override fun setLateTrackBuffering(isWaiting: Boolean) {}
        override fun skipToNextPlayableItem(failedIndex: Int) {}
        override fun requestQueueRefill() {}
        override fun isStandbyReady(index: Int): Boolean = standbyReady
    }

    private lateinit var controller: AutoDJController
    private lateinit var mockHost: TestPlaybackHost

    @Before
    fun setUp() {
        val mockNative = mockk<NativeAutoDJEngine>(relaxed = true)
        val mockSettings = mockk<SettingsPreferences>(relaxed = true)
        controller = AutoDJController(
            nativeEngine = mockNative,
            settingsPreferences = mockSettings,
            autoDJEngine = AutoDJEngine(),
        )

        mockHost = TestPlaybackHost()
    }

    @Test
    fun getTrackStage_transitionsFromPreparingToReadyWhenHostStandbyIsReady() {
        val nextTrackKey = "yt:v2"
        controller.setTrackStage(nextTrackKey, TrackLifecycleStage.PREPARING)

        // When host is not attached
        val initialStage = controller.getTrackStage(nextTrackKey, nextIndex = 1)
        assertThat(initialStage).isEqualTo(TrackLifecycleStage.PREPARING)

        // Attach host with standby NOT ready
        val field = AutoDJController::class.java.getDeclaredField("host")
        field.isAccessible = true
        field.set(controller, mockHost)

        val stillPreparing = controller.getTrackStage(nextTrackKey, nextIndex = 1)
        assertThat(stillPreparing).isEqualTo(TrackLifecycleStage.PREPARING)

        // Standby buffers and reaches Player.STATE_READY
        mockHost.standbyReady = true

        val readyStage = controller.getTrackStage(nextTrackKey, nextIndex = 1)
        assertThat(readyStage).isEqualTo(TrackLifecycleStage.READY)
    }
}
