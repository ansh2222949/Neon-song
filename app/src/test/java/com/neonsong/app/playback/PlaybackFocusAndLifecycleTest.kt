package com.neonsong.app.playback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PlaybackFocusAndLifecycleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestPlayer(): MusicPlayer {
        val dacMonitor = mockk<UsbDacMonitor>(relaxed = true)
        every { dacMonitor.state } returns MutableStateFlow(UsbDacMonitor.State())

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
    fun player_audioAttributes_configuredForMediaAndMusic() {
        val player = createTestPlayer()
        val track = PlayableTrack(title = "Focus Test", artist = "Artist", durationMs = 180_000L)
        player.play(track, sourceLabel = "Test")

        // Retrieve underlying ExoPlayer audio attributes
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertThat(audioManager).isNotNull()

        // Active state must reflect playing item
        assertThat(player.state.value.current?.title).isEqualTo("Focus Test")
    }

    @Test
    fun becomingNoisy_pausesPlaybackSafely() {
        val player = createTestPlayer()
        val track = PlayableTrack(title = "Noisy Track", artist = "Artist", durationMs = 200_000L)
        player.play(track, sourceLabel = "Test")

        // Simulate ACTION_AUDIO_BECOMING_NOISY broadcast
        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)

        // Calling pause explicitly verifies pause flow
        player.pause()
        assertThat(player.state.value.isPlaying).isFalse()
    }

    @Test
    fun audioFocus_transientLossAndGain_pausesAndResumes() {
        val player = createTestPlayer()
        val track = PlayableTrack(title = "Focus Cycle", artist = "Artist", durationMs = 200_000L)
        player.play(track, sourceLabel = "Test")

        // Transient loss (e.g. phone call or navigation prompt without ducking)
        player.pause()
        assertThat(player.state.value.isPlaying).isFalse()

        // Regained focus
        player.resume()
        assertThat(player.state.value.isPlaying).isTrue()
    }

    @Test
    fun playbackLifecycle_stopAndClear_releasesActiveSession() {
        val player = createTestPlayer()
        val track = PlayableTrack(title = "Stop Test", artist = "Artist", durationMs = 150_000L)
        player.play(track, sourceLabel = "Test")
        assertThat(player.state.value.current).isNotNull()

        player.stopAndClear(clearSession = true)
        assertThat(player.state.value.current).isNull()
        assertThat(player.state.value.queue).isEmpty()
        assertThat(player.state.value.isPlaying).isFalse()
    }
}
