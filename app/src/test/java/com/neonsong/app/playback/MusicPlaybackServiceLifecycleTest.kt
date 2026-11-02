package com.neonsong.app.playback

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.ScrobblerPreferences
import com.neonsong.app.data.local.ScrobblerSettings
import com.neonsong.app.data.repository.ScrobbleRepository
import com.neonsong.app.data.repository.ThemeRepository
import com.neonsong.app.data.repository.ThemeUiState
import com.neonsong.app.data.artwork.ArtworkRepository
import com.neonsong.app.widget.ActiveMediaSessionHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class MusicPlaybackServiceLifecycleTest {

    private lateinit var context: Context
    private lateinit var controller: ServiceController<MusicPlaybackService>
    private lateinit var service: MusicPlaybackService
    private lateinit var mockPlayer: MusicPlayer
    private lateinit var playerStateFlow: MutableStateFlow<MusicPlayerState>
    private lateinit var mockScrobbleRepo: ScrobbleRepository
    private lateinit var mockScrobblerPrefs: ScrobblerPreferences
    private lateinit var mockThemeRepo: ThemeRepository
    private lateinit var mockArtworkRepo: ArtworkRepository
    private lateinit var mockAutoLibrary: AndroidAutoMediaLibrary

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mockPlayer = mockk(relaxed = true)
        playerStateFlow = MutableStateFlow(MusicPlayerState())
        every { mockPlayer.state } returns playerStateFlow

        mockScrobbleRepo = mockk(relaxed = true)
        mockScrobblerPrefs = mockk(relaxed = true)
        every { mockScrobblerPrefs.settings } returns MutableStateFlow(ScrobblerSettings())

        mockThemeRepo = mockk(relaxed = true)
        val testColorScheme = androidx.compose.material3.darkColorScheme()
        val mockThemeUiState = mockk<ThemeUiState>(relaxed = true) {
            every { colorScheme } returns testColorScheme
        }
        every { mockThemeRepo.uiState } returns MutableStateFlow(mockThemeUiState)

        mockArtworkRepo = mockk(relaxed = true)
        every { mockArtworkRepo.resolved } returns MutableStateFlow(emptyMap())

        mockAutoLibrary = mockk(relaxed = true)
        every { mockAutoLibrary.playlistChanges } returns MutableStateFlow(Unit)
        every { mockAutoLibrary.downloadChanges } returns MutableStateFlow(emptyList())

        controller = Robolectric.buildService(MusicPlaybackService::class.java)
        service = controller.get()
        service.musicPlayer = mockPlayer
        service.scrobbleRepository = mockScrobbleRepo
        service.scrobblerPreferences = mockScrobblerPrefs
        service.themeRepository = mockThemeRepo
        service.artworkRepository = mockArtworkRepo
        service.androidAutoLibrary = mockAutoLibrary

        // Mark injected = true on Hilt_MusicPlaybackService to bypass Hilt dependency resolution in unit test
        val injectedField = Hilt_MusicPlaybackService::class.java.getDeclaredField("injected")
        injectedField.isAccessible = true
        injectedField.set(service, true)

        controller.create()
    }

    @After
    fun tearDown() {
        runCatching { controller.destroy() }
        ActiveMediaSessionHolder.ownToken = null
        ActiveMediaSessionHolder.controller = null
    }

    @Test
    fun onStartCommand_withNullIntentAndEmptyPlayer_stopsSelfAndReturnsStartNotSticky() {
        val result = service.onStartCommand(null, 0, 1)
        assertThat(result).isEqualTo(android.app.Service.START_NOT_STICKY)
    }

    @Test
    fun onStartCommand_withActionToggle_invokesTogglePlayPause() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = "com.neonsong.app.playback.TOGGLE"
        }
        val result = service.onStartCommand(intent, 0, 2)
        assertThat(result).isEqualTo(android.app.Service.START_NOT_STICKY)
        verify { mockPlayer.togglePlayPause() }
    }

    @Test
    fun onStartCommand_withActionNext_invokesPlayerNext() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = "com.neonsong.app.playback.NEXT"
        }
        service.onStartCommand(intent, 0, 3)
        verify { mockPlayer.next() }
    }

    @Test
    fun onStartCommand_withActionPrevious_invokesPlayerPrevious() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = "com.neonsong.app.playback.PREVIOUS"
        }
        service.onStartCommand(intent, 0, 4)
        verify { mockPlayer.previous() }
    }

    @Test
    fun onStartCommand_withActionStop_invokesPlayerStopAndClear() {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            action = "com.neonsong.app.playback.STOP"
        }
        service.onStartCommand(intent, 0, 5)
        verify { mockPlayer.stopAndClear() }
    }

    @Test
    fun onTaskRemoved_pausesIfPlaying_stopsAndClears_andStopsForeground() {
        playerStateFlow.value = MusicPlayerState(isPlaying = true)
        val shadowService = Shadows.shadowOf(service)

        service.onTaskRemoved(Intent())

        verify { mockPlayer.pause() }
        verify { mockPlayer.stopAndClear(clearSession = false) }
        assertThat(shadowService.isStoppedBySelf).isTrue()
    }

    @Test
    fun notificationChannel_isCreatedWithDefaultImportanceAndNoVibration() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("neonsong_playback_v3")
        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(channel.shouldVibrate()).isFalse()
    }

    @Test
    fun onDestroy_releasesMediaSessionAndClearsActiveSession() {
        assertThat(ActiveMediaSessionHolder.ownToken).isNotNull()
        controller.destroy()
        assertThat(ActiveMediaSessionHolder.ownToken).isNull()
    }

    @Test
    fun mediaSession_fastForwardAndRewind_seekAppropriately() {
        playerStateFlow.value = MusicPlayerState(positionMs = 30_000L, isPlaying = true)

        service.sessionCallback.onFastForward()
        verify { mockPlayer.seekTo(40_000L) }

        service.sessionCallback.onRewind()
        verify { mockPlayer.seekTo(20_000L) }
    }

    @Test
    fun mediaSession_transportCallbacks_routeToPlayer() {
        service.sessionCallback.onPlay()
        verify { mockPlayer.resume() }

        service.sessionCallback.onPause()
        verify { mockPlayer.pause() }

        service.sessionCallback.onSeekTo(15_000L)
        verify { mockPlayer.seekTo(15_000L) }

        service.sessionCallback.onSkipToQueueItem(3)
        verify { mockPlayer.seekToQueueItem(3) }

        service.sessionCallback.onSetShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)
        verify { mockPlayer.setShuffleEnabled(true) }
    }

    @Test
    fun serviceActionPendingIntent_isConfiguredWithImmutableAndTargetComponent() {
        val serviceActionMethod = MusicPlaybackService::class.java.getDeclaredMethod(
            "serviceAction",
            String::class.java,
            Int::class.javaPrimitiveType,
        )
        serviceActionMethod.isAccessible = true

        val pendingIntent = serviceActionMethod.invoke(
            service,
            "com.neonsong.app.playback.TOGGLE",
            2,
        ) as android.app.PendingIntent

        assertThat(pendingIntent).isNotNull()
        val shadow = Shadows.shadowOf(pendingIntent)
        assertThat(shadow.isService).isTrue()
        assertThat(shadow.savedIntent.action).isEqualTo("com.neonsong.app.playback.TOGGLE")
        assertThat(shadow.savedIntent.component?.className).isEqualTo(MusicPlaybackService::class.java.name)
    }

    @Test
    fun onStartCommand_withNullIntentAndLoadedTrack_stopsSelfToPreventFgsCrash() {
        val dummyTrack = PlayableTrack(title = "Synthwave", artist = "Neon Artist")
        playerStateFlow.value = MusicPlayerState(current = dummyTrack, isPlaying = false)
        val shadowService = Shadows.shadowOf(service)

        val result = service.onStartCommand(null, 0, 1)

        assertThat(result).isEqualTo(android.app.Service.START_NOT_STICKY)
        assertThat(shadowService.isStoppedBySelf).isTrue()
    }

    @Test
    fun updateWakeLocks_holdsWakeLockWhilePlayingOrBuffering_andReleasesOnPause() {
        val wakeLockField = MusicPlaybackService::class.java.getDeclaredField("playbackWakeLock")
        wakeLockField.isAccessible = true
        val wakeLock = wakeLockField.get(service) as? PowerManager.WakeLock
        assertThat(wakeLock).isNotNull()

        val updateWakeLocksMethod = MusicPlaybackService::class.java.getDeclaredMethod(
            "updateWakeLocks",
            MusicPlayerState::class.java,
        )
        updateWakeLocksMethod.isAccessible = true

        // 1. When playing -> WakeLock held
        updateWakeLocksMethod.invoke(service, MusicPlayerState(isPlaying = true))
        assertThat(wakeLock!!.isHeld).isTrue()

        // 2. When buffering -> WakeLock remains held
        updateWakeLocksMethod.invoke(service, MusicPlayerState(isBuffering = true))
        assertThat(wakeLock.isHeld).isTrue()

        // 3. When paused -> WakeLock released
        updateWakeLocksMethod.invoke(service, MusicPlayerState(isPlaying = false, isBuffering = false))
        assertThat(wakeLock.isHeld).isFalse()
    }
}
