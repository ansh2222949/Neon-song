package com.neonsong.app.playback.autodj

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.playback.NativeAutoDJEngine
import com.neonsong.app.playback.PlayableTrack
import com.neonsong.app.playback.TrackLifecycleStage
import com.neonsong.app.playback.resolve.StreamResolveCoordinator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class AutoDJControllerLifecycleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val settingsFlow = MutableStateFlow(MiscSettings(autoDJEnabled = true))
    private val settingsPreferences = mockk<SettingsPreferences>(relaxed = true) {
        every { settings } returns settingsFlow
    }
    private val nativeEngine = mockk<NativeAutoDJEngine>(relaxed = true)
    private val host = mockk<AutoDJPlaybackHost>(relaxed = true)
    private val resolver = mockk<StreamResolveCoordinator>(relaxed = true)
    private lateinit var scope: CoroutineScope
    private lateinit var controller: AutoDJController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(SupervisorJob() + testDispatcher)
        controller = AutoDJController(nativeEngine, settingsPreferences)
    }

    @After
    fun tearDown() {
        controller.detachHost()
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun attachHost_startsInIdleStatus_andCanBeDisabled() {
        controller.attachHost(host, scope, resolver)
        assertThat(controller.status.value).isEqualTo(AutoDJStatus.Idle)
        assertThat(controller.isEnabled).isTrue()

        // Disable via preferences
        settingsFlow.value = MiscSettings(autoDJEnabled = false)
        assertThat(controller.status.value).isEqualTo(AutoDJStatus.Disabled)
        assertThat(controller.isEnabled).isFalse()

        // Re-enable
        settingsFlow.value = MiscSettings(autoDJEnabled = true)
        assertThat(controller.status.value).isEqualTo(AutoDJStatus.Idle)
        assertThat(controller.isEnabled).isTrue()
    }

    @Test
    fun repeatedAttachHost_doesNotLeakCollector_andDetachesCleanly() {
        // Attach first time
        controller.attachHost(host, scope, resolver)
        assertThat(controller.isEnabled).isTrue()

        // Attach second time with new scope and host (simulating player recreation)
        val newScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val newHost = mockk<AutoDJPlaybackHost>(relaxed = true)
        controller.attachHost(newHost, newScope, resolver)

        // Preferences update must still work correctly
        settingsFlow.value = MiscSettings(autoDJEnabled = false)
        assertThat(controller.status.value).isEqualTo(AutoDJStatus.Disabled)
        assertThat(controller.isEnabled).isFalse()

        // Detach host should clean up everything
        controller.detachHost()
        newScope.cancel()
    }

    @Test
    fun setEnabledDirect_cancelsPrefetchesAndUpdatesStatus() {
        controller.attachHost(host, scope, resolver)
        controller.setTrackStage("track_1", TrackLifecycleStage.RESOLVING)
        assertThat(controller.getTrackStage("track_1")).isEqualTo(TrackLifecycleStage.RESOLVING)

        controller.setEnabledDirect(false)
        assertThat(controller.isEnabled).isFalse()
        assertThat(controller.status.value).isEqualTo(AutoDJStatus.Disabled)

        controller.setEnabledDirect(true)
        assertThat(controller.isEnabled).isTrue()
        assertThat(controller.status.value).isEqualTo(AutoDJStatus.Idle)
    }

    @Test
    fun onQueueChanged_prunesStaleTracksFromCache() {
        val trackInQueue = PlayableTrack(title = "Keep Me", artist = "Artist 1", videoId = "vid_keep")
        val trackRemoved = PlayableTrack(title = "Drop Me", artist = "Artist 2", videoId = "vid_drop")

        every { host.getQueueSize() } returns 1
        every { host.getTrackAt(0) } returns trackInQueue

        controller.attachHost(host, scope, resolver)
        controller.setTrackStage("vid_keep", TrackLifecycleStage.READY)
        controller.setTrackStage("vid_drop", TrackLifecycleStage.RESOLVING)

        controller.onQueueChanged()

        // vid_keep is in queue and preserved; vid_drop was removed from queue and pruned to QUEUED fallback
        assertThat(controller.getTrackStage("vid_keep")).isEqualTo(TrackLifecycleStage.READY)
        assertThat(controller.getTrackStage("vid_drop")).isEqualTo(TrackLifecycleStage.QUEUED)
    }
}
