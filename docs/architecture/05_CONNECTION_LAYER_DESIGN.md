# 05 — APPLICATION COMPOSITION LAYER DESIGN

**Project:** Neon Song  
**Document:** Composition Root, DI Strategy & Anti-Bloat Architecture  
**Target Environment:** `D:\NEON-SONG-EXPERIMENT`  

---

## 1. Composition Root Purpose & Anti-Bloat Philosophy

The role of `app/composition/` is **strictly wiring and assembly**, not execution. It acts as the dependency injection root of the application, connecting concrete implementations to the clean interfaces defined in `core/contracts/`.

### Strict Anti-Bloat Rules
1. **NO Business Logic in Composition:** `app/composition/` must never contain audio calculation logic, queue reordering logic, or UI presentation code.
2. **NO Monolithic ConnectionManager:** We do NOT introduce a giant `ConnectionManager.kt` God object that aggregates all repositories and controllers into a single god class.
3. **NO Static Service Locator:** We do NOT provide a `ServiceLocator.getInstance()` pattern that allows feature components to pull arbitrary dependencies from global static memory.
4. **Compile-Time Contract Binding:** Every feature receives its required contracts via constructor injection in its ViewModel factory or Composable parameters.

---

## 2. Target Composition Structure

```text
app/
  composition/
    AppContainer.kt               # Central interface defining core providers
    DefaultAppContainer.kt        # Lazy instantiation of singletons (DataSources, DB, AudioEngine)
    PlaybackModule.kt             # Binds MusicPlayer to PlaybackController & QueueController
    LyricsModule.kt               # Binds LyricsRepository to LyricsProvider
    AudioSettingsModule.kt        # Binds SettingsPreferences to AudioSettingsRepository
    ThemeModule.kt                # Binds ThemePreferences to ThemeProvider
    ViewModelFactory.kt           # Provides Compose ViewModelProvider.Factory for features
  feature_registration/
    FeatureRegistry.kt            # Compile-time registry for features & future extensions
    FeatureDeclaration.kt         # Metadata contract (route, icon, entry composable)
  navigation/
    AppNavGraph.kt                # Jetpack Compose Navigation host wiring feature routes
    NavDestination.kt             # Sealed classes defining valid navigation routes
```

---

## 3. Dependency Injection & Instantiation Pattern

```kotlin
// Example: app/composition/DefaultAppContainer.kt
class DefaultAppContainer(private val context: Context) : AppContainer {
    
    // Core Infrastructure (Lazy singletons)
    override val database: NeonDatabase by lazy {
        Room.databaseBuilder(context, NeonDatabase::class.java, "neon_song.db").build()
    }
    
    override val settingsDataStore: DataStore<Preferences> by lazy {
        context.dataStore
    }
    
    // Isolated Native Audio Engine Adapter
    override val audioEngine: AudioEngine by lazy {
        NativeAudioEngineAdapter()
    }
    
    // Playback Controller Implementation
    override val playbackController: PlaybackController by lazy {
        MusicPlayer(
            context = context,
            audioEngine = audioEngine,
            settingsRepo = audioSettingsRepository,
            database = database
        )
    }
    
    override val queueController: QueueController get() = playbackController as QueueController
    
    override val lyricsProvider: LyricsProvider by lazy {
        LyricsRepository(context = context)
    }
    
    override val audioSettingsRepository: AudioSettingsRepository by lazy {
        DefaultAudioSettingsRepository(settingsDataStore)
    }
    
    override val themeProvider: ThemeProvider by lazy {
        DefaultThemeProvider(settingsDataStore)
    }
}
```

---

## 4. ViewModel Factory Integration in Features

When a feature composable needs its ViewModel, it requests it using standard Jetpack Compose DI patterns without coupling to concrete classes:

```kotlin
// In features/player/ui/PlayerHost.kt
@Composable
fun PlayerHost(
    playbackController: PlaybackController = LocalAppContainer.current.playbackController,
    queueController: QueueController = LocalAppContainer.current.queueController,
    lyricsProvider: LyricsProvider = LocalAppContainer.current.lyricsProvider,
    themeProvider: ThemeProvider = LocalAppContainer.current.themeProvider
) {
    // PlayerHost interacts ONLY with contracts, guaranteeing 100% decoupling
    val playbackState by playbackController.playbackState.collectAsState()
    val currentTrack by playbackController.currentTrack.collectAsState()
    ...
}
```
