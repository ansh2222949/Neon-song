package com.neonsong.app.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.neonsong.app.R
import com.neonsong.app.data.backup.BackupRepository
import com.neonsong.app.data.discover.DiscoverRepository
import com.neonsong.app.data.download.TrackDownloadManager
import com.neonsong.app.data.generate.GenerateRepository
import com.neonsong.app.data.local.EqualizerPreferences
import com.neonsong.app.data.local.EqualizerPresets
import com.neonsong.app.data.local.EqualizerSettings
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.ScrobblerPreferences
import com.neonsong.app.data.local.ScrobblerSettings
import com.neonsong.app.data.local.SessionData
import com.neonsong.app.data.local.SessionPreferences
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.data.local.ThemeMode
import com.neonsong.app.data.local.ThemePreferences
import com.neonsong.app.data.local.db.DownloadedTrackDao
import com.neonsong.app.data.local.db.RecommendationExclusionEntity
import com.neonsong.app.data.model.AuthState
import com.neonsong.app.data.music.InnerTubeMusicApi
import com.neonsong.app.data.playlist.PlaylistImportManager
import com.neonsong.app.data.playlist.PlaylistRepository
import com.neonsong.app.data.repository.AuthRepository
import com.neonsong.app.data.repository.HomeRepository
import com.neonsong.app.data.repository.LastFmAuthCallbackCoordinator
import com.neonsong.app.data.repository.ThemeRepository
import com.neonsong.app.data.repository.ThemeUiState
import com.neonsong.app.data.spotify.SpotifyAuthManager
import com.neonsong.app.data.spotify.SpotifyIsrcMatcher
import com.neonsong.app.data.spotify.SpotifyLibraryManager
import com.neonsong.app.data.update.AppUpdateManager
import com.neonsong.app.data.update.UpdateInfo
import com.neonsong.app.data.ytmusic.YtConnection
import com.neonsong.app.data.ytmusic.YtMusicAuthManager
import com.neonsong.app.data.ytmusic.YtMusicLibraryManager
import com.neonsong.app.data.ytmusic.YtMusicPreferences
import com.neonsong.app.data.ytmusic.YtMusicSyncManager
import com.neonsong.app.data.ytmusic.YtSyncState
import com.neonsong.app.playback.LoudnessPrefs
import com.neonsong.app.playback.LoudnessSettings
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.MusicPlayerState
import com.neonsong.app.playback.NativeAudioEngine
import com.neonsong.app.presence.DiscordPresencePreferences
import com.neonsong.app.ui.common.NeonBrand
import com.neonsong.app.ui.theme.MAX_CORNER_SCALE
import com.neonsong.app.ui.theme.MIN_CORNER_SCALE
import com.neonsong.app.ui.theme.NeonCornerTokens
import com.neonsong.app.ui.theme.clampCornerScale
import com.neonsong.app.util.AppLocaleManager
import com.neonsong.app.util.FileExportHelper
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class SettingsReactiveAndPreviewTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(
        themeRepository: ThemeRepository = mockk(relaxed = true),
        settingsPreferences: SettingsPreferences = mockk(relaxed = true),
        musicPlayer: MusicPlayer = mockk(relaxed = true),
    ): SettingsViewModel {
        val authRepository = mockk<AuthRepository>(relaxed = true).apply {
            every { authState } returns MutableStateFlow(AuthState.SignedOut)
        }
        val authCallback = mockk<LastFmAuthCallbackCoordinator>(relaxed = true).apply {
            every { pendingToken } returns MutableStateFlow(null)
        }
        val sessionPreferences = mockk<SessionPreferences>(relaxed = true).apply {
            every { session } returns MutableStateFlow(SessionData())
        }
        every { themeRepository.uiState } returns MutableStateFlow(mockk<ThemeUiState>(relaxed = true))
        every { settingsPreferences.settings } returns MutableStateFlow(MiscSettings())
        val playlistRepository = mockk<PlaylistRepository>(relaxed = true).apply {
            every { playlists } returns MutableStateFlow(emptyList())
        }
        val scrobblerPreferences = mockk<ScrobblerPreferences>(relaxed = true).apply {
            every { settings } returns MutableStateFlow(ScrobblerSettings())
        }
        val discordPresencePreferences = mockk<DiscordPresencePreferences>(relaxed = true).apply {
            every { enabled } returns MutableStateFlow(true)
        }
        val equalizerPreferences = mockk<EqualizerPreferences>(relaxed = true).apply {
            every { settings } returns MutableStateFlow(EqualizerSettings())
        }
        val loudnessPrefs = mockk<LoudnessPrefs>(relaxed = true).apply {
            every { settings } returns MutableStateFlow(LoudnessSettings())
        }
        val ytAuthManager = mockk<YtMusicAuthManager>(relaxed = true).apply {
            every { connection } returns MutableStateFlow(YtConnection.DISCONNECTED)
        }
        val ytMusicSyncManager = mockk<YtMusicSyncManager>(relaxed = true).apply {
            every { state } returns MutableStateFlow(YtSyncState.Idle)
        }
        val ytMusicPreferences = mockk<YtMusicPreferences>(relaxed = true).apply {
            every { syncEnabled } returns MutableStateFlow(false)
            every { historySyncEnabled } returns MutableStateFlow(true)
            every { lastSyncAt } returns MutableStateFlow(0L)
            every { syncedPlaylistIds } returns MutableStateFlow(null)
            every { hiddenLibraryPlaylistIds } returns MutableStateFlow(emptySet())
        }
        val ytMusicLibraryManager = mockk<YtMusicLibraryManager>(relaxed = true).apply {
            every { accountPlaylists } returns MutableStateFlow(emptyList())
        }
        val downloadedTrackDao = mockk<DownloadedTrackDao>(relaxed = true).apply {
            every { count() } returns MutableStateFlow(0)
            every { totalBytes() } returns MutableStateFlow(0L)
        }
        val generateRepository = mockk<GenerateRepository>(relaxed = true).apply {
            every { observeRecommendationExclusions() } returns MutableStateFlow(emptyList<RecommendationExclusionEntity>())
        }
        val appUpdateManager = mockk<AppUpdateManager>(relaxed = true).apply {
            every { updateInfo } returns MutableStateFlow(UpdateInfo())
        }
        every { musicPlayer.state } returns MutableStateFlow(MusicPlayerState())

        return SettingsViewModel(
            authRepository = authRepository,
            authCallback = authCallback,
            homeRepository = mockk<HomeRepository>(relaxed = true),
            sessionPreferences = sessionPreferences,
            themeRepository = themeRepository,
            settingsPreferences = settingsPreferences,
            audioEngine = mockk<dagger.Lazy<NativeAudioEngine>>(relaxed = true),
            generateRepository = generateRepository,
            discoverRepository = mockk<DiscoverRepository>(relaxed = true),
            backupRepository = mockk<BackupRepository>(relaxed = true),
            playlistRepository = playlistRepository,
            musicPlayer = musicPlayer,
            downloadManager = mockk<TrackDownloadManager>(relaxed = true),
            fileExportHelper = mockk<FileExportHelper>(relaxed = true),
            scrobblerPreferences = scrobblerPreferences,
            discordPresencePreferences = discordPresencePreferences,
            equalizerPreferences = equalizerPreferences,
            loudnessPrefs = loudnessPrefs,
            ytAuthManager = ytAuthManager,
            ytMusicSyncManager = ytMusicSyncManager,
            ytMusicPreferences = ytMusicPreferences,
            ytMusicLibraryManager = ytMusicLibraryManager,
            downloadedTrackDao = downloadedTrackDao,
            appLocaleManager = mockk<AppLocaleManager>(relaxed = true),
            playlistImportManager = mockk<PlaylistImportManager>(relaxed = true),
            innerTube = mockk<InnerTubeMusicApi>(relaxed = true),
            appUpdateManager = appUpdateManager,
            spotifyAuthManager = mockk<SpotifyAuthManager>(relaxed = true),
            spotifyLibraryManager = mockk<SpotifyLibraryManager>(relaxed = true),
            spotifyIsrcMatcher = mockk<SpotifyIsrcMatcher>(relaxed = true),
            context = RuntimeEnvironment.getApplication(),
        )
    }

    @Test
    fun amoledToggle_preservesSystemThemeMode() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFile("theme_amoled_test.preferences_pb")
        }
        val prefs = ThemePreferences(dataStore)

        prefs.setThemeMode(ThemeMode.SYSTEM)
        prefs.setAmoled(true)
        val afterEnable = prefs.prefs.first()
        assertThat(afterEnable.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(afterEnable.amoled).isTrue()

        prefs.setAmoled(false)
        val afterDisable = prefs.prefs.first()
        assertThat(afterDisable.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(afterDisable.amoled).isFalse()
    }

    @Test
    fun liquidGlassAndCornerScale_persistAndClampCorrectly() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFile("theme_glass_corner_test.preferences_pb")
        }
        val prefs = ThemePreferences(dataStore)

        prefs.setLiquidGlass(false)
        prefs.setCornerScale(2.5f)
        val high = prefs.prefs.first()
        assertThat(high.liquidGlass).isFalse()
        assertThat(high.cornerScale).isEqualTo(MAX_CORNER_SCALE)

        prefs.setCornerScale(0.1f)
        val low = prefs.prefs.first()
        assertThat(low.cornerScale).isEqualTo(MIN_CORNER_SCALE)
    }

    @Test
    fun neonCornerTokens_scaleProportionallyAndClampInvalidInputs() {
        val scaled = NeonCornerTokens(1.25f)
        assertThat(scaled.scale).isEqualTo(1.25f)
        assertThat(scaled.mediumDp.value).isWithin(0.01f).of(22.5f)
        assertThat(clampCornerScale(Float.NaN)).isEqualTo(1.0f)
    }

    @Test
    fun neonBrand_keyRoundTripAppliesDrawableRes() {
        NeonBrand.applyKey(NeonBrand.KEY_MONOCHROME_GLASS)
        assertThat(NeonBrand.AppIconRes).isEqualTo(R.drawable.ic_launcher_beru_foreground)
        assertThat(NeonBrand.keyForRes(NeonBrand.AppIconRes)).isEqualTo(NeonBrand.KEY_MONOCHROME_GLASS)

        NeonBrand.applyKey(NeonBrand.KEY_BERU_3D)
        assertThat(NeonBrand.AppIconRes).isEqualTo(R.drawable.beru_icon_3d)
        assertThat(NeonBrand.keyForRes(NeonBrand.AppIconRes)).isEqualTo(NeonBrand.KEY_BERU_3D)
    }

    @Test
    fun settingsViewModel_exposesLiveEqGainsOnDragAndPreset() {
        val musicPlayer = mockk<MusicPlayer>(relaxed = true)
        val vm = createViewModel(musicPlayer = musicPlayer)
        try {
            vm.previewEqBandGain(3, 5.5f)
            assertThat(vm.liveEqGains.value[3]).isEqualTo(5.5f)

            vm.applyEqPreset(EqualizerPresets.STUDIO_MASTER.name)
            assertThat(vm.liveEqGains.value).isEqualTo(EqualizerPresets.STUDIO_MASTER.gainsDb)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun settingsViewModel_persistsAppIconLiquidGlassAndCornerScale() {
        val themeRepository = mockk<ThemeRepository>(relaxed = true)
        val settingsPreferences = mockk<SettingsPreferences>(relaxed = true)
        val vm = createViewModel(
            themeRepository = themeRepository,
            settingsPreferences = settingsPreferences,
        )
        try {
            vm.setLiquidGlass(false)
            vm.setCornerScale(1.2f)
            vm.setAppIcon(R.drawable.ic_launcher_beru_foreground)

            coVerify { themeRepository.setLiquidGlass(false) }
            coVerify { themeRepository.setCornerScale(1.2f) }
            coVerify { settingsPreferences.setAppIconKey(NeonBrand.KEY_MONOCHROME_GLASS) }
            assertThat(NeonBrand.AppIconRes).isEqualTo(R.drawable.ic_launcher_beru_foreground)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun inlinePlayerLyricsSetting_persistsAndUpdatesViaViewModel() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFile("settings_inline_lyrics_test.preferences_pb")
        }
        val prefs = SettingsPreferences(dataStore, RuntimeEnvironment.getApplication())
        assertThat(prefs.settings.first().inlinePlayerLyricsEnabled).isFalse()

        prefs.setInlinePlayerLyricsEnabled(true)
        assertThat(prefs.settings.first().inlinePlayerLyricsEnabled).isTrue()

        val settingsMock = mockk<SettingsPreferences>(relaxed = true)
        val vm = createViewModel(settingsPreferences = settingsMock)
        try {
            vm.setInlinePlayerLyricsEnabled(true)
            coVerify { settingsMock.setInlinePlayerLyricsEnabled(true) }
        } finally {
            vm.viewModelScope.cancel()
        }
    }
}

