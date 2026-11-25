# ARCHITECTURE & CODEBASE RULES

**Project:** Neon Song  
**Document:** Mandatory Architectural Standards & Review Checklist  

---

## 1. Non-Negotiable Dependency Rules

1. **Strict Upward Flow:** Lower layers must NEVER import higher layers:
   * `native/` cannot import Java/Kotlin.
   * `core/` cannot import `features/` or `app/`.
   * `data/` cannot import `ui/` or `features/`.
   * `features/` cannot import other `features/` directly.
2. **Contract-Based Cross-Feature Calls:** If Feature A needs functionality from Feature B, it must interact through an interface in `core/contracts/`.
3. **No Global Singletons / Service Locators:** Do not create `object GlobalState` or `ServiceLocator.getInstance()` to share mutable state across screens.
4. **Composition Root Ownership:** Only `app/composition/` may instantiate feature implementations and bind them to contracts.

---

## 2. JNI & Native Audio Engine Preservation

1. **Package Locking:** NEVER change the package name of the 5 JNI bridge classes:
   * `com.neonsong.app.playback.NativeAudioEngine`
   * `com.neonsong.app.playback.NativeAutoMixEngine`
   * `com.neonsong.app.playback.NativeAutoDJEngine`
   * `com.neonsong.app.playback.eq.ParametricEqNative`
   * `com.neonsong.app.data.lossless.NativeSecrets`
2. **Real-Time Thread Guarantees:**
   * NO `new`, `malloc`, or vector resizing inside Oboe `onAudioReady`.
   * NO blocking locks or mutexes in the audio path.
   * NO JNI upcalls from the real-time audio thread.

---

## 3. Data & Settings Integrity

1. **DataStore Key Stability:** Never modify existing preference key string literals in DataStore.
2. **Room Database Integrity:** Keep Room table names, column annotations, and migrations 100% backward-compatible.
