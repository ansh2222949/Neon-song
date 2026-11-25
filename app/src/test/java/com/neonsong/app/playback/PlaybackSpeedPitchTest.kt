package com.neonsong.app.playback

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class PlaybackSpeedPitchTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestPlayer(): MusicPlayer {
        val context = RuntimeEnvironment.getApplication()
        val dacMonitor = mockk<UsbDacMonitor>(relaxed = true)
        io.mockk.every { dacMonitor.state } returns kotlinx.coroutines.flow.MutableStateFlow(UsbDacMonitor.State())

        return MusicPlayer(
            context = context,
            innerTube = mockk(relaxed = true),
            losslessMusicApi = mockk(relaxed = true),
            moduleDrmFactory = mockk(relaxed = true),
            moduleManager = mockk(relaxed = true),
            offlineLicense = mockk(relaxed = true),
            segBridge = mockk(relaxed = true),
            settingsPreferences = mockk(relaxed = true),
            equalizerPreferences = mockk(relaxed = true),
            discoverRepository = mockk(relaxed = true),
            nativeAudioEngine = dagger.Lazy { mockk(relaxed = true) },
            audioEffectsEngine = mockk(relaxed = true),
            applicationScope = CoroutineScope(SupervisorJob() + testDispatcher),
            downloadedTrackDao = dagger.Lazy { mockk(relaxed = true) },
            usbDacMonitor = dacMonitor,
            exclusiveUsbOutput = mockk(relaxed = true),
            songPlayStatsRepository = dagger.Lazy { mockk(relaxed = true) },
            nativeAutoMixEngine = dagger.Lazy { mockk(relaxed = true) },
            autoDJController = dagger.Lazy { mockk(relaxed = true) },
            autoEqController = mockk(relaxed = true),
        )
    }

    @Test
    fun playbackParameters_clampedToSupportedRange() {
        val player = createTestPlayer()

        // Test clamping below minimum (0.25f)
        player.setPlaybackParameters(0.1f, 0.05f)
        assertThat(player.state.value.speed).isEqualTo(0.25f)
        assertThat(player.state.value.pitch).isEqualTo(0.25f)

        // Test clamping above maximum (3.0f)
        player.setPlaybackParameters(5.0f, 10.0f)
        assertThat(player.state.value.speed).isEqualTo(3.0f)
        assertThat(player.state.value.pitch).isEqualTo(3.0f)

        // Test nominal values
        player.setPlaybackParameters(0.85f, 0.90f)
        assertThat(player.state.value.speed).isEqualTo(0.85f)
        assertThat(player.state.value.pitch).isEqualTo(0.90f)
    }

    @Test
    fun playbackParameters_independentControlMaintainsOppositeValue() {
        val player = createTestPlayer()
        player.resetPlaybackParameters()

        // Speed change preserves pitch
        player.setSpeed(0.80f)
        assertThat(player.state.value.speed).isEqualTo(0.80f)
        assertThat(player.state.value.pitch).isEqualTo(1.0f)

        // Pitch change preserves speed
        player.setPitch(0.92f)
        assertThat(player.state.value.speed).isEqualTo(0.80f)
        assertThat(player.state.value.pitch).isEqualTo(0.92f)

        // Reset resets both to 1.0f
        player.resetPlaybackParameters()
        assertThat(player.state.value.speed).isEqualTo(1.0f)
        assertThat(player.state.value.pitch).isEqualTo(1.0f)
    }

    @Test
    fun playbackParameters_linkedModeSnapsPitchToSpeed() {
        val player = createTestPlayer()

        // In linked varispeed mode: speed and pitch are set to the same value
        val varispeedRatio = 0.82f
        player.setPlaybackParameters(varispeedRatio, varispeedRatio)

        assertThat(player.state.value.speed).isEqualTo(varispeedRatio)
        assertThat(player.state.value.pitch).isEqualTo(varispeedRatio)
    }

    @Test
    fun playbackParameters_persistenceAcrossTrackUpdates() {
        val player = createTestPlayer()
        player.setPlaybackParameters(0.75f, 0.85f)

        // Update current track in state
        val track = PlayableTrack(title = "Test Song", artist = "Test Artist", durationMs = 180_000L)
        player.setCurrentTrackForTesting(track)

        // Speed and pitch must remain stable across track updates
        assertThat(player.state.value.speed).isEqualTo(0.75f)
        assertThat(player.state.value.pitch).isEqualTo(0.85f)
    }
}
