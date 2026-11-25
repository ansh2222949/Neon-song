# NEW FEATURE IMPLEMENTATION GUIDE

**Project:** Neon Song  
**Document:** Contributor Workflow for Adding Features & Extensions  

---

## 1. Step-by-Step Feature Addition Recipe

Follow this exact recipe when adding any new feature (e.g. `SleepTimerV2`, `LyricsTranslation`, `SpatialAudioSettings`):

### Step 1: Create the Feature Directory
Create a dedicated package under `features/`:
```text
features/
  my_new_feature/
    ui/                   # Composables, dialogs, sheets
    domain/               # Use cases, domain models, business logic
    data/                 # Local data sources, caching, network calls
    components/           # Feature-specific visual components
```

### Step 2: Define or Identify Contracts
* Check `core/contracts/` for existing interfaces (`PlaybackController`, `QueueController`, `AudioSettingsRepository`).
* If your feature provides a new cross-feature capability, define a minimal interface in `core/contracts/`.
* **RULE:** Do NOT expose internal ViewModels or repositories across packages.

### Step 3: Implement Internal Logic
* Mark all internal helper classes, ViewModels, and state models `internal`.
* Implement the UI using Jetpack Compose, consuming `core/designsystem/` for colors, typography, and `LiquidGlass` modifiers.

### Step 4: Register in `app/composition/`
* Open `app/composition/DefaultAppContainer.kt` or the relevant DI module.
* Wire the feature's contract to its concrete implementation.
* Add navigation routes in `app/navigation/AppNavGraph.kt`.

### Step 5: Verification & Safety
1. Run `./gradlew assembleDebug` to verify compilation.
2. Run `./gradlew testDebugUnitTest` to ensure zero regressions.
3. Verify that zero JNI bridge packages or DataStore keys were touched.
