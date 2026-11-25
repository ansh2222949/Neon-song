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
    static constexpr std::size_t kDeckCount = 2;
    static constexpr int kDeckA = 0;
    static constexpr int kDeckB = 1;

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
    void flushOutput() noexcept;

    // Producer-side PCM ingress (Deck A). Mono is duplicated to stereo.
    std::size_t writePcm(
        const void* pcm,
        std::size_t frameCount,
        PcmFormat format,
        std::int32_t inputSampleRate,
        std::int32_t inputChannelCount);

    // Per-deck PCM ingress (0 = Deck A, 1 = Deck B).
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

    // Enqueues Float32 PCM that has already passed through mediaDsp_.
    // Sample rate must match the active Oboe stream.
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
    void setSpatialImmersionEnabled(bool enabled) noexcept;
    void setSpatialImmersionStrength(float strength) noexcept;
    void setHrtfHeadScale(float scale) noexcept;
    [[nodiscard]] bool isSpatialImmersionEnabled() const noexcept;
    [[nodiscard]] float getHrtfHeadScale() const noexcept;

    // True only when both DSP instances actually bypass.
    [[nodiscard]] bool isBitPerfect() const noexcept;
    void setEqualizer(bool enabled, const float* gainsDb, std::size_t gainCount) noexcept;
    void setBtAacOptimizationEnabled(bool enabled) noexcept;
    [[nodiscard]] bool isBtAacOptimizationEnabled() const noexcept;

    // Packed (bassPeak in high 16 bits, transientPeak in low 16 bits, scaled 0..10000).
    [[nodiscard]] std::int32_t consumeHapticTelemetry() noexcept;

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

    [[nodiscard]] std::int32_t mediaChannelCount() const noexcept {
        return mediaChannelCountAtomic_.load(std::memory_order_acquire);
    }

    [[nodiscard]] std::int32_t outputSampleRate() const noexcept {
        return outputSampleRate_.load(std::memory_order_acquire);
    }
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
    static constexpr std::int32_t kOutputChannels = 2;
    static constexpr std::int32_t kMinSampleRate = 4000;
    static constexpr std::int32_t kMaxSampleRate = 384000;
    static constexpr std::int32_t kRingMilliseconds = 750;
    static constexpr std::int32_t kTargetDeviceBufferMilliseconds = 60;
    static constexpr std::int32_t kPrebufferMilliseconds = 120;
    static constexpr std::int32_t kPrebufferMaxCallbacks = 24;
    static constexpr std::int32_t kUnderrunDecaySeconds = 5;
    static constexpr std::size_t kProducerChunkFrames = 2048;
    static constexpr std::size_t kMaxResampledFrames = 32768;
    static constexpr std::size_t kWsolaPaddingFrames = 64;
    static constexpr float kMinTimeRatio = 0.25F;
    static constexpr float kMaxTimeRatio = 3.0F;
    static constexpr std::size_t kCacheLineBytes = 64;

    struct SoxrDeleter {
        void operator()(void* handle) const noexcept;
    };
    using SoxrHandle = std::unique_ptr<void, SoxrDeleter>;

    // Lock-free cross-thread control & telemetry state for one deck.
    struct alignas(kCacheLineBytes) DeckControlState {
        std::atomic<LockFreeRingBuffer<float>*> activeRing{nullptr};
        std::atomic<float> volume{1.0F};
        std::atomic<bool> playing{false};
        std::atomic<float> speed{1.0F};
        std::atomic<float> pitch{1.0F};
        std::atomic<float> timeRatioTarget{1.0F};
        std::atomic<std::uint64_t> flushRequests{0};
        std::atomic<std::uint64_t> flushTargetWrite{0};
        std::atomic<bool> resamplerDrained{true};
        std::atomic<bool> wsolaDrained{true};
        std::atomic<std::uint64_t> framesWritten{0};
        std::atomic<std::uint64_t> framesRendered{0};
    };

    // Producer-owned state protected by mutex (never locked on RT audio thread).
    struct alignas(kCacheLineBytes) DeckProducerState {
        mutable std::mutex mutex;
        std::unique_ptr<LockFreeRingBuffer<float>> ring;
        SoxrHandle resampler;
        std::int32_t resamplerInputRate{0};
        float resamplerPitch{1.0F};
        bool resamplerFlushing{false};
        std::vector<float> rawScratch;
        std::vector<float> stereoScratch;
        std::vector<float> resampledScratch;
    };

    // Real-time audio-callback-owned state (allocated before requestStart()).
    struct alignas(kCacheLineBytes) DeckRealtimeState {
        WsolaTimeStretcher stretcher;
        std::vector<float> ringScratch;
        std::vector<float> stretchedScratch;
        float appliedRatio{1.0F};
        float currentMixGain{0.0F};
        std::uint64_t flushAck{0};
    };

    struct Deck {
        DeckControlState control;
        DeckProducerState producer;
        DeckRealtimeState rt;
    };

    [[nodiscard]] static constexpr bool isValidDeck(int deckId) noexcept {
        return deckId >= 0 && deckId < static_cast<int>(kDeckCount);
    }

    // Control-plane helpers (must hold controlMutex_).
    [[nodiscard]] bool startLocked(std::int32_t preferredOutputSampleRate);
    void releaseProducerStateLocked() noexcept;
    void resetCallbackStateLocked() noexcept;
    void buildFadeCurve(std::int32_t sampleRate);

    // Producer-plane helpers (must hold decks_[d].producer.mutex).
    [[nodiscard]] bool configureDeckResamplerLocked(std::size_t deckIdx, std::int32_t inputSampleRate);
    [[nodiscard]] bool configureMediaResamplerLocked();

    // Real-time callback helpers (called strictly on Oboe callback thread).
    void renderChunkRt(float* output, std::size_t requestedFrames) noexcept;
    void syncDeckControlsRt(bool& anyFlushed) noexcept;
    void updateCrossfadeGainsRt(float xfade) noexcept;
    [[nodiscard]] std::size_t pullDeckFramesRt(
        std::size_t deckIdx,
        bool deckActive,
        std::size_t requestedFrames,
        const float*& outSamples) noexcept;
    void mixDecksRt(
        float* output,
        const float* samplesA,
        std::size_t framesA,
        float targetGainA,
        const float* samplesB,
        std::size_t framesB,
        float targetGainB,
        std::size_t validFrames,
        bool outputBitPerfect) noexcept;
    void applyOutputStageRt(
        float* output,
        std::size_t validFrames,
        bool playingNow,
        bool wasUnderrun,
        bool outputBitPerfect) noexcept;
    void handleUnderrunOrDecayRt(
        float* output,
        std::size_t validFrames,
        std::size_t requestedFrames,
        bool playingNow,
        bool wasUnderrun) noexcept;

    // Lock hierarchy: controlMutex_ -> decks_[0].producer.mutex -> decks_[1].producer.mutex.
    // mediaProcessorMutex_ is independent.
    mutable std::mutex controlMutex_;
    std::mutex mediaProcessorMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;

    std::array<Deck, kDeckCount> decks_;

    // Cross-thread engine control & diagnostics.
    alignas(kCacheLineBytes) std::atomic<float> crossfadeProgress_{0.0F};
    std::atomic<std::int32_t> outputSampleRate_{0};
    std::atomic<bool> restartAllowed_{false};
    std::atomic<bool> playbackEnabled_{false};
    std::atomic<bool> inputAlreadyProcessed_{false};
    std::atomic<bool> btAacOptimizationEnabled_{false};
    std::atomic<float> targetOutputVolume_{1.0F};
    std::atomic<std::uint64_t> renderedFrames_{0};
    std::atomic<std::uint64_t> underrunCount_{0};
    std::atomic<std::uint64_t> streamOpenCount_{0};
    std::atomic<std::uint64_t> streamRestartCount_{0};
    std::atomic<std::uint64_t> rateAdaptationCount_{0};
    std::atomic<std::int32_t> requestedOutputSampleRate_{0};

    DspProcessor oboeDsp_;
    DspProcessor mediaDsp_;

    // Media processor state (protected by mediaProcessorMutex_).
    SoxrHandle mediaResampler_;
    std::int32_t mediaInputSampleRate_{0};
    std::int32_t mediaOutputSampleRate_{0};
    std::int32_t mediaChannelCount_{0};
    std::atomic<std::int32_t> mediaChannelCountAtomic_{0};
    std::vector<float> mediaScratch_;

    // Callback-only state (initialized before requestStart(); no locks or allocations in RT).
    alignas(kCacheLineBytes) std::vector<float> fadeInCurve_;
    std::size_t maxCallbackFrames_{0};
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
    float cachedCrossfade_{-1.0F};
    float cachedXfadeGainA_{1.0F};
    float cachedXfadeGainB_{0.0F};
    std::size_t cleanFrames_{0};
    std::size_t underrunDecayIntervalFrames_{1};
};

}  // namespace neonsong::audio
