package com.neonsong.app.ui.feed

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonsong.app.R
import com.neonsong.app.data.discover.DiscoverRepository
import com.neonsong.app.data.feed.FeedArtist
import com.neonsong.app.data.feed.FeedData
import com.neonsong.app.data.feed.FeedMix
import com.neonsong.app.data.feed.FeedQuickTile
import com.neonsong.app.data.feed.FeedRepository
import com.neonsong.app.data.generate.GeneratedTrack
import com.neonsong.app.data.generate.youtubeVideoIdOrNull
import com.neonsong.app.data.local.SessionPreferences
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.data.model.RecentTrack
import com.neonsong.app.data.music.HomeChip
import com.neonsong.app.data.music.HomeShelf
import com.neonsong.app.data.music.HomeShelfItem
import com.neonsong.app.data.music.InnerTubeMusicApi
import com.neonsong.app.data.music.YouTubeMusicTrack
import com.neonsong.app.data.music.YouTubePlaylistSummary
import com.neonsong.app.data.repository.HomeRepository
import com.neonsong.app.data.ytmusic.YtMusicAuthManager
import com.neonsong.app.playback.MusicPlayer
import com.neonsong.app.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val launchingRadio: String? = null,
    val error: String? = null,
    val avatarUrl: String? = null,
    val customPhotoUri: String = "",
    val feedData: FeedData = FeedData(),
    val selectedChip: HomeChip? = null,
    val isChipLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val pagedShelves: List<HomeShelf> = emptyList(),
    val chipShelves: List<HomeShelf> = emptyList(),
    val chipContinuationToken: String? = null,
    val defaultContinuationToken: String? = null,
) {
    val isChipActive: Boolean get() = selectedChip != null
    val homeChips: List<HomeChip> get() = feedData.homeChips
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: FeedRepository,
    private val sessionPreferences: SessionPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val musicPlayer: MusicPlayer,
    private val innerTube: InnerTubeMusicApi,
    private val ytAuth: YtMusicAuthManager,
    private val homeRepository: HomeRepository,
    private val discoverRepository: DiscoverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _discoveryTracks = MutableStateFlow<List<GeneratedTrack>>(emptyList())
    val discoveryTracks: StateFlow<List<GeneratedTrack>> = _discoveryTracks.asStateFlow()

    /** Ids of Home sections the user hid in Settings → Home sections. */
    val hiddenHomeSections: StateFlow<Set<String>> = settingsPreferences.settings
        .map { it.hiddenHomeSections }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val ytMusicHiddenSections: StateFlow<Set<String>> = settingsPreferences.settings
        .map { it.ytMusicHiddenSections }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val ytMusicSectionsOrder: StateFlow<List<String>> = settingsPreferences.settings
        .map { it.ytMusicSectionsOrder }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rotatingBackgroundEnabled: StateFlow<Boolean> = settingsPreferences.settings
        .map { it.rotatingBackgroundEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Podcast surfaces (default enabled). */
    val podcastsEnabled: StateFlow<Boolean> = MutableStateFlow(true)

    private var feedJob: Job? = null
    private var chipJob: Job? = null
    private var loadMoreJob: Job? = null
    private var playbackLaunchJob: Job? = null
    private var discoveryJob: Job? = null

    private var requestGeneration: Long = 0L
    private var lastLoadedMillis: Long = 0L
    private val seenContinuationTokens = HashSet<String>()

    init {
        loadDiscovery()
        viewModelScope.launch {
            ytAuth.awaitLoadedConnection()
            combine(ytAuth.connection, sessionPreferences.session) { connection, session ->
                connection to session.username
            }.distinctUntilChanged().collect { (connection, _) ->
                feedJob?.cancel()
                val cached = repository.getCachedFeed()
                _uiState.update { current ->
                    FeedUiState(
                        isLoading = cached == null || !cached.hasAnyContent(),
                        feedData = cached ?: FeedData(
                            isYtConnected = connection.isConnected,
                            ytAccountName = connection.accountName.takeIf { connection.isConnected },
                        ),
                        avatarUrl = current.avatarUrl,
                        customPhotoUri = current.customPhotoUri,
                    )
                }
                loadFeed()
            }
        }
        viewModelScope.launch {
            settingsPreferences.settings
                .map { it.settingsPhotoUri }
                .distinctUntilChanged()
                .collect { photoUri ->
                    _uiState.update { it.copy(customPhotoUri = photoUri) }
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val stats = homeRepository.fetchStats().getOrNull()
            if (stats?.avatarUrl != null) {
                _uiState.update { it.copy(avatarUrl = stats.avatarUrl) }
            }
        }
    }

    fun loadDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = discoverRepository.allowedCachedFeed()
                val tracks = if (cached.isNotEmpty()) cached.take(8) else discoverRepository.nextBatch(8)
                ensureActive()
                _discoveryTracks.value = tracks
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }

    fun loadFeed() = fetchFeed(refreshing = false)

    fun refresh() {
        loadDiscovery()
        fetchFeed(refreshing = true)
    }

    fun onVisible() {
        if (feedJob?.isActive == true) return
        val hasContent = _uiState.value.feedData.hasAnyContent()
        val stale = System.currentTimeMillis() - lastLoadedMillis > STALE_AFTER_MILLIS
        if (!hasContent || stale) refresh()
    }

    fun playInfiniteRadio() {
        val feed = _uiState.value.feedData
        val shelfTracks = feed.nativeShelves
            .asSequence()
            .flatMap { it.items.asSequence() }
            .mapNotNull { (it as? HomeShelfItem.Track)?.track }
            .toList()
        val pool = (feed.quickPicks + feed.ytLikedSongs + feed.freshFinds + feed.ytRecentSongs + feed.charts + shelfTracks)
            .filter { it.videoId.isNotBlank() }
            .distinctBy { it.videoId }
        val seed = pool.randomOrNull() ?: return

        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch(Dispatchers.IO) {
            val related = runCatching {
                innerTube.fetchRelatedSongs(seed.videoId, limit = 30, prefetchStreams = false)
            }.getOrDefault(emptyList())
            ensureActive()
            val queue = (listOf(seed) + related).distinctBy(YouTubeMusicTrack::videoId)
            if (queue.isNotEmpty()) {
                playTracksQueue(queue, startIndex = 0, sourceLabel = "Neon Infinite Radio")
            } else {
                playTrack(seed, "Neon Infinite Radio")
            }
        }
    }

    fun playMix(mix: FeedMix) = playTrack(mix.seed, mix.title)

    private fun fetchFeed(refreshing: Boolean) {
        feedJob?.cancel()
        chipJob?.cancel()
        loadMoreJob?.cancel()
        requestGeneration++
        seenContinuationTokens.clear()

        feedJob = viewModelScope.launch {
            val self = coroutineContext[Job]
            _uiState.update {
                it.copy(
                    isLoading = if (refreshing) false else (!it.feedData.hasAnyContent() || it.isLoading),
                    isRefreshing = refreshing,
                    error = null,
                    selectedChip = if (refreshing) null else it.selectedChip,
                    isChipLoading = if (refreshing) false else it.isChipLoading,
                    isLoadingMore = if (refreshing) false else it.isLoadingMore,
                    pagedShelves = if (refreshing) emptyList() else it.pagedShelves,
                    chipShelves = if (refreshing) emptyList() else it.chipShelves,
                    chipContinuationToken = if (refreshing) null else it.chipContinuationToken,
                    defaultContinuationToken = if (refreshing) null else it.defaultContinuationToken,
                )
            }
            try {
                val connection = ytAuth.awaitLoadedConnection()
                val username = sessionPreferences.session.value.username.takeIf(String::isNotBlank)
                val data = repository.loadFeed(username) { update ->
                    ensureActive()
                    if (ytAuth.connection.value == connection &&
                        sessionPreferences.session.value.username.takeIf(String::isNotBlank) == username
                    ) {
                        _uiState.update { current ->
                            val hasContentNow = update.hasAnyContent() || current.feedData.hasAnyContent()
                            current.copy(
                                feedData = update,
                                defaultContinuationToken = update.homeContinuationToken ?: current.defaultContinuationToken,
                                isLoading = if (hasContentNow) false else current.isLoading,
                            )
                        }
                    }
                }
                ensureActive()
                if (ytAuth.connection.value != connection ||
                    sessionPreferences.session.value.username.takeIf(String::isNotBlank) != username
                ) return@launch

                val stats = runCatching { homeRepository.fetchStats().getOrNull() }.getOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        feedData = data,
                        defaultContinuationToken = data.homeContinuationToken,
                        error = null,
                        avatarUrl = stats?.avatarUrl ?: it.avatarUrl,
                    )
                }
                lastLoadedMillis = System.currentTimeMillis()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ensureActive()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = appContext.getString(R.string.feed_update_error),
                    )
                }
            } finally {
                if (feedJob === self) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun onHomeChipSelected(chip: HomeChip) = selectChip(chip)

    fun selectChip(chip: HomeChip?) {
        val currentSelected = _uiState.value.selectedChip
        if (chip == null || chip == currentSelected) {
            chipJob?.cancel()
            loadMoreJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedChip = null,
                    isChipLoading = false,
                    isLoadingMore = false,
                    chipShelves = emptyList(),
                    chipContinuationToken = null,
                )
            }
            return
        }

        chipJob?.cancel()
        loadMoreJob?.cancel()
        seenContinuationTokens.clear()
        val currentGen = ++requestGeneration
        _uiState.update {
            it.copy(
                selectedChip = chip,
                isChipLoading = true,
                isLoadingMore = false,
                chipShelves = emptyList(),
                chipContinuationToken = null,
                error = null,
            )
        }

        chipJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val page = innerTube.fetchHomeChip(chip)
                ensureActive()
                if (requestGeneration != currentGen || _uiState.value.selectedChip != chip) return@launch
                _uiState.update {
                    it.copy(
                        isChipLoading = false,
                        chipShelves = page.shelves,
                        chipContinuationToken = page.continuationToken,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ensureActive()
                if (requestGeneration == currentGen) {
                    _uiState.update {
                        it.copy(
                            selectedChip = null,
                            isChipLoading = false,
                            error = appContext.getString(R.string.feed_update_error),
                        )
                    }
                }
            }
        }
    }

    fun loadMoreHomeShelves() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isChipLoading || state.isLoading || state.isRefreshing) return

        val isChipActive = state.selectedChip != null
        val token = if (isChipActive) {
            state.chipContinuationToken
        } else {
            state.defaultContinuationToken ?: state.feedData.homeContinuationToken
        }
        if (token.isNullOrBlank() || !seenContinuationTokens.add(token)) return

        val currentGen = requestGeneration
        val activeChip = state.selectedChip
        _uiState.update { it.copy(isLoadingMore = true) }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextPage = innerTube.fetchHomeContinuation(token)
                ensureActive()
                if (requestGeneration != currentGen || _uiState.value.selectedChip != activeChip) return@launch

                val existingShelves = if (isChipActive) {
                    _uiState.value.chipShelves
                } else {
                    _uiState.value.feedData.nativeShelves + _uiState.value.pagedShelves
                }
                val existingKeys = existingShelves.map(::shelfFingerprint).toSet()

                val newShelves = nextPage.shelves.filter { shelf ->
                    shelf.items.isNotEmpty() && shelfFingerprint(shelf) !in existingKeys
                }

                // Stop pagination if backend returns no new unique shelves or repeats the same token
                val nextToken = nextPage.continuationToken?.takeIf {
                    it.isNotBlank() && it !in seenContinuationTokens && newShelves.isNotEmpty()
                }

                _uiState.update { current ->
                    if (isChipActive) {
                        current.copy(
                            isLoadingMore = false,
                            chipShelves = current.chipShelves + newShelves,
                            chipContinuationToken = nextToken,
                        )
                    } else {
                        current.copy(
                            isLoadingMore = false,
                            pagedShelves = current.pagedShelves + newShelves,
                            defaultContinuationToken = nextToken,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                seenContinuationTokens.remove(token)
                throw cancellation
            } catch (_: Exception) {
                seenContinuationTokens.remove(token)
                ensureActive()
                if (requestGeneration == currentGen) {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = appContext.getString(R.string.feed_update_error),
                        )
                    }
                }
            }
        }
    }

    private fun shelfFingerprint(shelf: HomeShelf): String {
        val firstId = when (val item = shelf.items.firstOrNull()) {
            is HomeShelfItem.Track -> item.track.videoId
            is HomeShelfItem.Playlist -> item.playlist.id
            null -> ""
        }
        return "${shelf.title.trim().lowercase()}_$firstId"
    }

    fun playTrack(track: YouTubeMusicTrack, sourceLabel: String = "Feed") {
        musicPlayer.play(track.toPlayableTrack(), sourceLabel = sourceLabel, startRadio = true)
    }

    fun shuffleTracksQueue(tracks: List<YouTubeMusicTrack>, sourceLabel: String = "Feed") {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        musicPlayer.playQueue(
            shuffled.map { it.toPlayableTrack() },
            startIndex = 0,
            sourceLabel = "$sourceLabel Shuffle",
            enableRadioContinuation = true,
        )
    }

    fun playTracksQueue(tracks: List<YouTubeMusicTrack>, startIndex: Int = 0, sourceLabel: String = "Feed") {
        if (tracks.isEmpty()) return
        val playable = tracks.map { it.toPlayableTrack() }
        musicPlayer.playQueue(
            playable,
            startIndex = startIndex.coerceIn(0, playable.lastIndex.coerceAtLeast(0)),
            sourceLabel = sourceLabel,
            enableRadioContinuation = true,
        )
    }

    fun playRecentQueue(tracks: List<RecentTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val playable = tracks.map {
            PlayableTrack(
                title = it.name,
                artist = it.artist.displayName,
                album = it.album.displayName,
                artworkUrl = it.artworkUrl,
                videoId = GeneratedTrack(it.name, it.artist.displayName, it.artworkUrl, it.url).youtubeVideoIdOrNull(),
            )
        }
        musicPlayer.playQueue(
            playable,
            startIndex = startIndex.coerceIn(0, playable.lastIndex.coerceAtLeast(0)),
            sourceLabel = "Jump Back In",
            enableRadioContinuation = true,
        )
    }

    fun playGeneratedQueue(tracks: List<GeneratedTrack>, startIndex: Int = 0, sourceLabel: String = "Heavy Rotation") {
        if (tracks.isEmpty()) return
        val playable = tracks.map {
            PlayableTrack(
                title = it.name,
                artist = it.artist,
                album = it.album,
                artworkUrl = it.artworkUrl,
                videoId = it.youtubeVideoIdOrNull(),
            )
        }
        musicPlayer.playQueue(
            playable,
            startIndex = startIndex.coerceIn(0, playable.lastIndex.coerceAtLeast(0)),
            sourceLabel = sourceLabel,
            enableRadioContinuation = true,
        )
    }

    fun playArtistRadio(artist: FeedArtist) {
        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch(Dispatchers.IO) {
            val seeds = try {
                innerTube.searchSongs("${artist.name} songs", limit = 5, prefetchStreams = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emptyList()
            }
            val seed = seeds.firstOrNull {
                it.artist.contains(artist.name, ignoreCase = true) || artist.name.contains(it.artist, ignoreCase = true)
            } ?: seeds.firstOrNull()
            val related = seed?.let {
                try {
                    innerTube.fetchRelatedSongs(it.videoId, limit = 25, prefetchStreams = false)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    emptyList()
                }
            }.orEmpty()
            ensureActive()
            val songs = (listOfNotNull(seed) + related).distinctBy(YouTubeMusicTrack::videoId)
            if (songs.isNotEmpty()) {
                playTracksQueue(songs, startIndex = 0, sourceLabel = "${artist.name} Radio")
            }
        }
    }

    fun playDiscoveryQuery(title: String, query: String) {
        if (_uiState.value.launchingRadio != null) return
        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(launchingRadio = title) }
            try {
                val seeds = try {
                    innerTube.searchSongs(query, limit = 8, prefetchStreams = false)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    emptyList()
                }
                val seed = seeds.take(5).randomOrNull()
                val related = seed?.let {
                    try {
                        innerTube.fetchRelatedSongs(it.videoId, limit = 24, prefetchStreams = false)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        emptyList()
                    }
                }.orEmpty()
                ensureActive()
                val songs = (listOfNotNull(seed) + related).distinctBy(YouTubeMusicTrack::videoId)
                if (songs.isNotEmpty()) playTracksQueue(songs, sourceLabel = title)
            } finally {
                _uiState.update { it.copy(launchingRadio = null) }
            }
        }
    }

    fun playPlaylistSummary(summary: YouTubePlaylistSummary) {
        if (summary.id.isBlank()) return
        playPlaylistById(summary.id, summary.title)
    }

    fun playPlaylistById(playlistId: String, title: String) {
        if (playlistId.isBlank()) return
        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { innerTube.fetchPlaylist(playlistId, maxTracks = 100) }.getOrNull()
            ensureActive()
            val tracks = result?.tracks.orEmpty()
            if (tracks.isNotEmpty()) {
                val playable = tracks.map { it.toPlayableTrack() }
                musicPlayer.playQueue(playable, startIndex = 0, sourceLabel = title)
            }
        }
    }

    private suspend fun resolveDiscoverMixPlaylistId(tile: FeedQuickTile?): String? {
        var playlistId = tile?.playlistId?.takeIf(String::isNotBlank)
        if (playlistId == null) {
            val feed = _uiState.value.feedData
            val found = feed.ytSuggestedPlaylists.firstOrNull { it.title.contains("Discover Mix", ignoreCase = true) }
                ?: feed.nativeShelves.flatMap { it.items }.filterIsInstance<HomeShelfItem.Playlist>()
                    .map { it.playlist }.firstOrNull { it.title.contains("Discover Mix", ignoreCase = true) }
            playlistId = found?.id?.takeIf(String::isNotBlank)
        }
        if (playlistId == null) {
            val mixes = runCatching { innerTube.fetchHomeMixes() }.getOrDefault(emptyList())
            playlistId = mixes.firstOrNull { it.title.contains("Discover Mix", ignoreCase = true) }?.id?.takeIf(String::isNotBlank)
        }
        if (playlistId == null) {
            val search = runCatching { innerTube.searchPlaylists("Discover Mix") }.getOrDefault(emptyList())
            playlistId = (search.firstOrNull { it.title.contains("Discover Mix", ignoreCase = true) }?.id
                ?: search.firstOrNull()?.id)?.takeIf(String::isNotBlank)
        }
        return playlistId
    }

    fun openDiscoverMixPlaylist(tile: FeedQuickTile? = null, onOpen: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = resolveDiscoverMixPlaylistId(tile) ?: "RDMPO"
            ensureActive()
            launch(Dispatchers.Main) {
                onOpen(playlistId)
            }
        }
    }

    fun playDiscoverMix(tile: FeedQuickTile? = null) {
        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch(Dispatchers.IO) {
            val playlistId = resolveDiscoverMixPlaylistId(tile)
            ensureActive()
            if (!playlistId.isNullOrBlank()) {
                val result = runCatching { innerTube.fetchPlaylist(playlistId, maxTracks = 100) }.getOrNull()
                ensureActive()
                val tracks = result?.tracks.orEmpty()
                if (tracks.isNotEmpty()) {
                    musicPlayer.playQueue(
                        tracks.map { it.toPlayableTrack() },
                        startIndex = 0,
                        sourceLabel = tile?.title?.ifBlank { "Discover Mix" } ?: "Discover Mix",
                    )
                    return@launch
                }
            }
            playInfiniteRadio()
        }
    }

    fun playMyMix(tile: FeedQuickTile? = null) {
        playbackLaunchJob?.cancel()
        playbackLaunchJob = viewModelScope.launch(Dispatchers.IO) {
            val playlistId = tile?.playlistId?.takeIf(String::isNotBlank)
            val tracks = if (playlistId != null) {
                runCatching { innerTube.fetchPlaylist(playlistId, maxTracks = 100) }.getOrNull()?.tracks.orEmpty()
            } else {
                emptyList()
            }
            ensureActive()
            val queue = tracks.ifEmpty {
                val feed = _uiState.value.feedData
                (feed.ytRecentSongs + feed.ytLikedSongs + feed.quickPicks)
                    .filter { it.videoId.isNotBlank() }
                    .distinctBy { it.videoId }
                    .shuffled()
            }
            if (queue.isNotEmpty()) {
                val playable = queue.map { it.toPlayableTrack() }
                musicPlayer.playQueue(
                    playable,
                    startIndex = 0,
                    sourceLabel = tile?.title?.ifBlank { "My Mix" } ?: "My Mix",
                )
            } else {
                playInfiniteRadio()
            }
        }
    }

    fun handleQuickTileClick(tile: FeedQuickTile) {
        val videoId = tile.actionVideoId?.takeIf(String::isNotBlank) ?: return
        musicPlayer.play(
            PlayableTrack(
                title = tile.title,
                artist = tile.subtitle ?: "",
                artworkUrl = tile.artworkUrl,
                videoId = videoId,
            ),
            sourceLabel = "Quick Picks",
        )
    }

    private fun FeedData.hasAnyContent(): Boolean =
        quickTiles.isNotEmpty() || mixes.isNotEmpty() || topArtists.isNotEmpty() ||
            quickPicks.isNotEmpty() || jumpBackIn.isNotEmpty() || recentAlbums.isNotEmpty() ||
            heavyRotation.isNotEmpty() || ytLikedSongs.isNotEmpty() || ytRecentSongs.isNotEmpty() ||
            (becauseYouListenTo?.items?.isNotEmpty() == true) || freshFinds.isNotEmpty() ||
            spotlight != null || charts.isNotEmpty() || newReleases.isNotEmpty() ||
            friends.isNotEmpty() || nativeShelves.isNotEmpty()

    private fun YouTubeMusicTrack.toPlayableTrack(): PlayableTrack = PlayableTrack(
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        videoId = videoId.takeIf(String::isNotBlank),
        durationMs = durationSeconds?.takeIf { it > 0 }?.times(1_000L),
    )

    private companion object {
        const val STALE_AFTER_MILLIS = 90_000L
    }
}
