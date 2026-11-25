# 07 — NATIVE AUDIO ENGINE BOUNDARIES & REAL-TIME AUDIO SAFETY

**Project:** Neon Song  
**Document:** Native Audio Architecture, C++ Subsystems & Oboe RT Guarantees  
**Target Environment:** `D:\NEON-SONG-EXPERIMENT`  

---

## 1. Native Audio Subsystem Call Chain

```text
       ┌─────────────────────────────────────────────────────────┐
       │                Audio UI & Player Screens                │
       └────────────────────────────┬────────────────────────────┘
                                    │ Compose StateFlow
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │         PlayerViewModel / SettingsViewModel             │
       └────────────────────────────┬────────────────────────────┘
                                    │ Interface Calls
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │     PlaybackController / AudioSettingsRepository        │
       └────────────────────────────┬────────────────────────────┘
                                    │ API Invocations
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │        AudioEngine Contract (core/audio_api/)           │
       └────────────────────────────┬────────────────────────────┘
                                    │ Wrapped Delegation
                                    ▼
       ┌─────────────────────────────────────────────────────────┐
       │  Package-Locked JNI Bridge (native/jni/NativeAudio...)  │
       └────────────────────────────┬────────────────────────────┘
                                    │ C-ABI Dynamic JNI Calls
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        NATIVE C++20 AUDIO ENGINE                       │
├───────────────────────┬───────────────────────┬────────────────────────┤
│ native/oboe/          │ native/dsp/           │ native/audio_buffer/   │
│ - Oboe Stream Manager │ - 10-Band ParametricEQ│ - Lock-Free SPSC Ring  │
│ - onAudioReady() [RT] │ - SoXR Resampler      │ - Memory Order Acq/Rel │
├───────────────────────┼───────────────────────┼────────────────────────┤
│ native/wsola/         │ native/crossfade/     │ native/security/       │
│ - Time-Stretch Engine │ - Equal-Power Curve   │ - Rust Decryption Key  │
│ - Pitch Preservation  │ - Dual Deck Mixer     │   Vault (neonsong)     │
└───────────────────────┴───────────────────────┴────────────────────────┘
```

---

## 2. Non-Negotiable Real-Time Audio Constraints

The C++ audio callback (`OboeAudioEngine::onAudioReady`) runs on a high-priority Linux real-time thread scheduled by Android's audio server (`audioserver`). To maintain audiophile bit-depth and prevent buffer under-runs, the following rules are non-negotiable:

### 2.1 Zero Real-Time Heap Allocations
* **Forbidden in RT path:** `new`, `malloc`, `realloc`, `free`, `std::vector::push_back`, `std::string` manipulation, or smart pointer instantiations (`std::make_shared`).
* **Enforcement:** All internal buffers (FFT window arrays, delay lines, biquad coefficients, SoXR working buffers) are pre-allocated during `initAudioEngine(sampleRate, burstSize)` and reused indefinitely.

### 2.2 Zero Blocking Synchronization
* **Forbidden in RT path:** `std::mutex`, `std::unique_lock`, `pthread_mutex_lock`, semaphores, or condition variables.
* **Enforcement:** Audio samples are transferred from ExoPlayer's `NativeDeckAudioSink` to the native engine via single-producer single-consumer lock-free ring buffers (`AudioRingBuffer.h`) using `std::atomic<uint32_t>` with `std::memory_order_release` (producer) and `std::memory_order_acquire` (consumer).

### 2.3 Zero JNI Upcalls from RT Thread
* **Forbidden in RT path:** Calling `env->CallVoidMethod(...)`, acquiring JVM handles, or triggering Kotlin callbacks from `onAudioReady`.
* **Enforcement:** Telemetry, RMS levels, and end-of-track signals are written to atomic flags and polled by the Java playback service on a background thread.

---

## 3. C++ Component Breakdown

1. **`NativeAudioEngine.cpp` & `NativeBridge.cpp`**: Core bridge interfacing with 52 exported JNI methods. Manages Oboe stream lifecycle, device routing, and DAC bit-perfect settings.
2. **`DspProcessor.cpp`**: Applies 10-band biquad parametric filtering, loudness normalization, peak limiting, and stereo widening.
3. **`WsolaProcessor.cpp`**: Implements Waveform Similarity Overlap-Add algorithm for tempo manipulation without pitch alteration.
4. **`CrossfadeMixer.cpp`**: Blends Deck A and Deck B PCM streams using equal-power sine/cosine fading curves:
   $$V_A = \cos\left(rac{\pi}{2} \cdot tight), \quad V_B = \sin\left(rac{\pi}{2} \cdot tight)$$
5. **`ParametricEqBridge.cpp`**: 16 native JNI functions controlling frequency, Q-factor, and gain per band in real time.
6. **`security/neonsong-vault/src/lib.rs`**: Rust crate compiling into the native library to decrypt proprietary lossless streaming tokens.
