#pragma once

#include "DspProcessor.h"
#include "LockFreeRingBuffer.h"
#include "PcmConverter.h"
#include "WsolaTimeStretcher.h"

#include <oboe/Oboe.h>

#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

namespace neonsong::audio {

class AudioEngine final : public oboe::AudioStreamDataCallback,
                          public oboe::AudioStreamErrorCallback {
public:
    AudioEngine() = default;
    ~AudioEngine() override;

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    [[nodiscard]] bool start(std::int32_t preferredOutputSampleRate = 0);
    void stop() noexcept;
    // True while an Oboe stream is open and rendering. Allows callers to
    // reuse a healthy stream instead of reopening AAudio per track.
    [[nodiscard]] bool isRunning() const noexcept;
    // Drops all queued audio and resets callback-side fade/prebuffer state
    // without closing the stream. Used for seeks and track transitions so
    // OEM audio stacks never hear a teardown/reopen pop.
    static constexpr std::size_t kDeckCount = 2;
    static constexpr int kDeckA = 0;
    static constexpr int kDeckB = 1;

    // Drops all queued audio and resets callback-side fade/prebuffer state
    // without closing the stream. Used for seeks and track transitions so
    // OEM audio stacks never hear a teardown/reopen pop.
    void flushOutput() noexcept;

    // Producer-side PCM ingress. One producer thread may call this method.
    // Mono input is duplicated to stereo; stereo remains interleaved.
    std::size_t writePcm(
        const void* pcm,
        std::size_t frameCount,
        PcmFormat format,
        std::int32_t inputSampleRate,
        std::int32_t inputChannelCount);

    // Per-deck PCM ingress. Each deckId (0 = Deck A, 1 = Deck B) has its own
    // SPSC ring buffer, producer mutex, and resampler.
    // Returns accepted frame count (>= 0), or negative error code:
    // -1: invalid buffer, -2: invalid format, -3: engine not started, -4: invalid deck.
    std::int64_t writeDeckPcm(
        int deckId,
        const void* pcm,
        std::size_t frameCount,
        PcmFormat format,
        std::int32_t inputSampleRate,
        std::int32_t inputChannelCount);

    void flushDeck(int deckId) noexcept;
    [[nodiscard]] std::size_t deckBufferedFrames(int deckId) const noexcept;
    void setDeckVolume(int deckId, float volume) noexcept;
    [[nodiscard]] float deckVolume(int deckId) const noexcept;
    void setCrossfadeProgress(float progress) noexcept;
    [[nodiscard]] float crossfadeProgress() const noexcept;
    void setDeckPlaybackParameters(int deckId, float speed, float pitch) noexcept;
    [[nodiscard]] float deckSpeed(int deckId) const noexcept;
    [[nodiscard]] float deckPitch(int deckId) const noexcept;
    void setDeckTimeRatio(int deckId, float ratio) noexcept;
    [[nodiscard]] float deckTimeRatio(int deckId) const noexcept;
    void setDeckPlaying(int deckId, bool playing) noexcept;
    [[nodiscard]] bool isDeckPlaying(int deckId) const noexcept;
    void drainDeckResampler(int deckId) noexcept;
    [[nodiscard]] bool isDeckResamplerDrained(int deckId) const noexcept;
    [[nodiscard]] bool isDeckWsolaDrained(int deckId) const noexcept;
    [[nodiscard]] std::uint64_t deckFramesRendered(int deckId) const noexcept;
    [[nodiscard]] std::int64_t presentationLatencyUs() const noexcept;
    // Enqueues Float32 PCM that has already passed through mediaDsp_. The
    // sample rate must match the active Oboe stream, minimizing any additional
    // rate conversion after libsoxr.
    std::size_t writeProcessedPcm(
        const float* pcm,
        std::size_t frameCount,
        std::int32_t inputSampleRate,
        std::int32_t inputChannelCount);
    void flushResampler();
    void setPlaying(bool playing) noexcept;
    void setOutputVolume(float volume) noexcept;

    void setStudioMasterClarity(bool enabled) noexcept;
    void setBitPerfect(bool enabled) noexcept;
    void setBauerCrossfeedEnabled(bool enabled) noexcept;
    void setAnalogTubeWarmthEnabled(bool enabled) noexcept;
    void setMonoBassEnabled(bool enabled) noexcept;
    // Read-back for UI truthfulness: true only when both DSP instances
    // actually bypass. Lets the signal-path verdict distinguish
    // bitPerfectRequested from bitPerfectActuallyActive.
    [[nodiscard]] bool isBitPerfect() const noexcept;
    void setEqualizer(bool enabled, const float* gainsDb, std::size_t gainCount) noexcept;

    [[nodiscard]] bool configureMediaProcessor(
        std::int32_t inputSampleRate,
        std::int32_t outputSampleRate,
        std::int32_t channelCount);
    [[nodiscard]] bool processMediaPcm(
        const void* pcm,
        std::size_t frameCount,
        PcmFormat format,
        float* output,
        std::size_t outputCapacityFrames,
        std::size_t& outputFrameCount);
    [[nodiscard]] bool flushMediaProcessor(
        float* output,
        std::size_t outputCapacityFrames,
        std::size_t& outputFrameCount);
    void resetMediaProcessor();

    [[nodiscard]] std::int32_t outputSampleRate() const noexcept {
        return outputSampleRate_.load(std::memory_order_acquire);
    }
    // The sample rate that was actually requested for the current stream.
    // Zero means "let Android choose its native rate".
    [[nodiscard]] std::int32_t requestedSampleRate() const noexcept {
        return requestedOutputSampleRate_.load(std::memory_order_acquire);
    }
    [[nodiscard]] std::size_t bufferedFrames() const noexcept;
    [[nodiscard]] std::uint64_t underrunCount() const noexcept {
        return underrunCount_.load(std::memory_order_acquire);
    }
    [[nodiscard]] std::uint64_t renderedFrames() const noexcept {
        return renderedFrames_.load(std::memory_order_acquire);
    }
    // Diagnostics: how many streams were opened, how many were reopened after
    // a fatal stream error, and how often the device's accepted rate differed
    // from what the app asked for.
    [[nodiscard]] std::uint64_t streamOpenCount() const noexcept {
        return streamOpenCount_.load(std::memory_order_acquire);
    }
    [[nodiscard]] std::uint64_t streamRestartCount() const noexcept {
        return streamRestartCount_.load(std::memory_order_acquire);
    }
    [[nodiscard]] std::uint64_t rateAdaptationCount() const noexcept {
        return rateAdaptationCount_.load(std::memory_order_acquire);
    }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        std::int32_t numFrames) override;
    void onErrorAfterClose(
        oboe::AudioStream* audioStream,
        oboe::Result error) override;

private:
    [[nodiscard]] bool startLocked(std::int32_t preferredOutputSampleRate);
    void releaseProducerStateLocked() noexcept;
    [[nodiscard]] bool configureResamplerLocked(std::int32_t inputSampleRate);
    [[nodiscard]] bool configureDeckResamplerLocked(int deckId, std::int32_t inputSampleRate);
    [[nodiscard]] bool configureMediaResamplerLocked();
    void buildFadeCurve(std::int32_t sampleRate);

    static constexpr std::int32_t kOutputChannels = 2;
    static constexpr std::int32_t kRingMilliseconds = 750;
    // Device buffer target and prebuffer are deliberately medium-sized: music
    // stability under OEM throttling beats minimal latency.
    static constexpr std::int32_t kTargetDeviceBufferMilliseconds = 60;
    static constexpr std::int32_t kPrebufferMilliseconds = 120;
    static constexpr std::int32_t kPrebufferMaxCallbacks = 24;
    // Glitch-free playback time that removes one counted underrun episode.
    static constexpr std::int32_t kUnderrunDecaySeconds = 5;
    static constexpr std::size_t kProducerChunkFrames = 2048;
    static constexpr std::size_t kMaxResampledFrames = 32768;

    // Mutable so const probes (isRunning) can serialize against control
    // paths; locking a mutex never mutates its logical state.
    mutable std::mutex controlMutex_;
    mutable std::mutex producerMutex_;
    std::mutex mediaProcessorMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<LockFreeRingBuffer<float>> ringBuffer_;
    std::atomic<LockFreeRingBuffer<float>*> activeRingBuffer_{nullptr};

    // Dual-deck SPSC ring buffers and per-deck producer states
    std::array<std::unique_ptr<LockFreeRingBuffer<float>>, kDeckCount> deckRings_;
    std::array<std::atomic<LockFreeRingBuffer<float>*>, kDeckCount> activeDeckRings_{};
    mutable std::array<std::mutex, kDeckCount> deckProducerMutexes_;
    std::array<void*, kDeckCount> deckResamplers_{};
    std::array<std::int32_t, kDeckCount> deckResamplerInputRates_{};
    std::array<bool, kDeckCount> deckResamplersFlushing_{};
    std::array<std::vector<float>, kDeckCount> deckRawScratch_;
    std::array<std::vector<float>, kDeckCount> deckStereoScratch_;
    std::array<std::vector<float>, kDeckCount> deckResampledScratch_;
    std::array<std::atomic<float>, kDeckCount> deckVolumes_{{1.0F, 1.0F}};
    std::atomic<float> crossfadeProgress_{0.0F};
    std::array<std::atomic<std::uint64_t>, kDeckCount> deckFramesWritten_{};
    std::array<std::atomic<bool>, kDeckCount> deckPlaying_{{false, false}};
    std::array<std::atomic<bool>, kDeckCount> deckResamplersDrained_{{true, true}};
    std::array<std::atomic<std::uint64_t>, kDeckCount> deckFramesRendered_{};
    std::array<std::atomic<std::uint64_t>, kDeckCount> deckFlushRequests_{{0, 0}};
    std::array<std::atomic<std::uint64_t>, kDeckCount> deckFlushTargetWrite_{{0, 0}};
    std::array<std::atomic<bool>, kDeckCount> deckWsolaDrained_{{true, true}};

    // Pre-allocated mixing scratch buffers for real-time audio thread
    std::vector<float> realtimeScratchA_;
    std::vector<float> realtimeScratchB_;
    std::vector<float> realtimeStretchedA_;
    std::vector<float> realtimeStretchedB_;

    // WSOLA time-stretchers and speed/pitch parameters for each deck
    std::array<lastwave::audio::WsolaTimeStretcher, kDeckCount> deckStretchers_;
    std::array<std::atomic<float>, kDeckCount> deckSpeed_{{1.0F, 1.0F}};
    std::array<std::atomic<float>, kDeckCount> deckPitch_{{1.0F, 1.0F}};
    std::array<std::atomic<float>, kDeckCount> deckTimeRatioTarget_{{1.0F, 1.0F}};
    std::array<float, kDeckCount> deckStretcherAppliedRatio_{{1.0F, 1.0F}};

    // Real-time audio thread state
    std::array<std::uint64_t, kDeckCount> deckFlushAcks_{{0, 0}};
    std::array<float, kDeckCount> deckPauseGain_{{1.0F, 1.0F}};

    std::atomic<std::int32_t> outputSampleRate_{0};
    std::atomic<bool> restartAllowed_{false};
    std::atomic<bool> playbackEnabled_{false};
    std::atomic<bool> inputAlreadyProcessed_{false};
    std::atomic<float> targetOutputVolume_{1.0F};
    std::atomic<std::uint64_t> renderedFrames_{0};
    std::atomic<std::uint64_t> underrunCount_{0};
    std::atomic<std::uint64_t> streamOpenCount_{0};
    std::atomic<std::uint64_t> streamRestartCount_{0};
    std::atomic<std::uint64_t> rateAdaptationCount_{0};
    std::atomic<std::int32_t> requestedOutputSampleRate_{0};

    // libsoxr is kept opaque in the public header.
    void* resampler_{nullptr};
    std::int32_t resamplerInputRate_{0};
    bool resamplerFlushing_{false};
    std::vector<float> rawScratch_;
    std::vector<float> stereoScratch_;
    std::vector<float> resampledScratch_;

    DspProcessor oboeDsp_;
    DspProcessor mediaDsp_;
    void* mediaResampler_{nullptr};
    std::int32_t mediaInputSampleRate_{0};
    std::int32_t mediaOutputSampleRate_{0};
    std::int32_t mediaChannelCount_{0};
    std::vector<float> mediaScratch_;

    // Callback-only state. Storage is built before requestStart(), so the
    // real-time callback performs no allocation or locking.
    std::vector<float> fadeInCurve_;
    std::size_t underrunFadePosition_{0};
    std::size_t recoveryFadePosition_{0};
    bool underrunActive_{false};
    bool recoveryFading_{false};
    bool prebuffering_{true};
    std::size_t prebufferFrames_{0};
    std::size_t prebufferBaseFrames_{0};
    std::int32_t prebufferWaitCallbacks_{kPrebufferMaxCallbacks};
    std::array<float, kOutputChannels> underrunAnchor_{};
    std::array<float, kOutputChannels> lastOutput_{};
    float currentOutputVolume_{1.0F};
    float outputVolumeRampStep_{1.0F};
    std::size_t cleanFrames_{0};
    std::size_t underrunDecayIntervalFrames_{0};
};

}  // namespace neonsong::audio
