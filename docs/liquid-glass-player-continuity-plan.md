# Liquid Glass and Player Continuity Plan

## Request

Use one consistent Liquid Glass visual language across reusable components, settings pages, and player controls; make settings entries open their intended page; expose manual and automatic YouTube video quality in the player; keep video and animated artwork continuous while the player changes size; and keep the artwork card visible while animation is running.

## Repository findings

1. The workspace already has local edits in the Liquid Glass theme, MiniPlayer, PlayerHost, settings scaffolding, and native library binaries. Preserve and build on these edits; do not reset or rewrite the native binaries.
2. `liquidGlassChrome`, `LiquidGlassCard`, and glass button controls already exist. Several repeated settings cards and search results still draw opaque Material surfaces, so the shared settings primitives are the highest coverage point for visual consistency.
3. `SyncedVideoPlayer.kt` already has a quality picker and an Auto option. The main `PlayerHost` video controls do not expose that picker. Auto currently selects the extractor's default stream; the player needs to make that mode reachable and clearly selected.
4. `SyncedVideoSurface` creates and releases an ExoPlayer per composable instance. `PlayerHost` mounts it for the mini player, full player, morph overlay, and fullscreen layer, so resizing can replace the renderer and show loading again.
5. `PlayerArtwork` deliberately draws static cover art under the animated Canvas layer, preserving the card while the motion clip starts. MiniPlayer currently calls it without Canvas artwork, and transition/full-player instances can independently start Canvas players.
6. Settings search results and category cards set an `activeTab` and preserve a target setting ID. Verify the profile/settings entry and those direct links before changing navigation behavior.

## Implementation sequence

### 0. Protect current work

- Re-read the edited player/theme/settings sections before touching them.
- Keep existing workspace changes intact and limit changes to source files required by this request.
- Use the checklist below to review each item before proceeding to the next.

### 1. Establish the shared Liquid Glass surface

- Make the reusable card/panel primitive use the same optical profile as MiniPlayer's `liquidGlassChrome`.
- Convert repeated settings sections, action/toggle/photo cards, search results, and related dialogs to that shared surface.
- Replace visible Material container treatment in player controls and settings where the Liquid Glass backdrop is available. Keep Material color and typography tokens as semantic palette inputs.
- Confirm each nested settings page inherits the parent backdrop once and does not capture its own glass controls recursively.

### 2. Verify settings destination behavior

- Trace the Home profile/settings affordance into the settings hub.
- Trace each category, quick card, and search result to its `SettingsTab` and target row.
- Correct only routes that fail to open the requested destination or lose the target highlight.

### 3. Put video quality in the player

- Reuse the existing available-stream list and quality selection state.
- Add an obvious quality control in the in-app video player and its fullscreen controls.
- Keep manual resolution choices and the Auto option, show the current choice, and return to the same playback position when a stream changes.
- Make Auto a clear operating mode rather than a display-only badge.

### 4. Keep one video renderer through size changes

- Keep a single `SyncedVideoSurface` mounted in the player host while video mode is active.
- Let its visible bounds follow the existing mini/full morph and fullscreen state.
- Remove competing video surfaces from the MiniPlayer, full-player body, and fullscreen body so those layers cannot stop/reload playback.
- Retain one audio-master synchronization loop and one owner for stream resolution/player release.

### 5. Keep animated artwork in the same card

- Route active Canvas artwork to the mini-player and the morph layer.
- Use one Canvas renderer while it moves between mini and full-player bounds.
- Keep static artwork under the animation as a stable fallback, including loading, error, and transition frames.
- Preserve the artwork card shape and shared morph throughout the animation.

### 6. Review and verify

- Re-check each checklist item against the final diff and inspect Compose state/lifecycle ownership for duplicate players.
- Build the Android app to catch Kotlin/Compose integration errors.
- Report completed changes and any device-only behavior that still needs a hands-on playback check.

## Acceptance checklist

- [x] Settings and player panels use the MiniPlayer-aligned Liquid Glass treatment wherever a backdrop is available.
- [x] Profile/settings access reaches the settings hub, and each settings destination opens its own category/row. Route review found the existing targets were already wired correctly.
- [x] Video controls expose manual quality and Auto, and update the selected quality state visibly.
- [x] Video keeps one mounted renderer and retains its active player position while collapsing, expanding, and entering/exiting fullscreen.
- [x] Animated Canvas art is routed through the mini-player and the same card layer through the artwork morph.
- [x] Static artwork stays beneath Canvas animation as its loading/error fallback.
- [x] Android app Kotlin compilation succeeds (`:app:compileDebugKotlin`).

Device playback/animation was not launched here; the in-app morph and visual result still need a hands-on device check.
