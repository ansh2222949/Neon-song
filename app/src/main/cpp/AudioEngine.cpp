#include "AudioEngine.h"

#include <soxr.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>

namespace neonsong::audio {
namespace {

constexpr double kPi = 3.1415926535897932384626433832795;
constexpr float kHalfPi = 1.57079632679489661923F;
constexpr float kBtAacHeadroomGain = 0.87096359F;  // -1.20 dBFS true-peak margin for AAC encoder
constexpr float kRatioEpsilon = 1e-3F;
constexpr float kGainEpsilon = 1e-6F;
constexpr double kFadeCurveSeconds = 0.002;
constexpr float kVolumeRampSeconds = 0.005F;
constexpr std::size_t kResamplerSafetyMarginFrames = 256;

[[nodiscard]] inline float sanitizeClamp(
    float value,
    float minValue,
    float maxValue,
    float fallback) noexcept {
    if (!std::isfinite(value)) return fallback;
    return std::clamp(value, minValue, maxValue);
}

inline void applyGainInPlace(float* samples, std::size_t sampleCount, float gain) noexcept {
    if (samples == nullptr || std::abs(gain - 1.0F) <= kGainEpsilon) return;
    for (std::size_t i = 0; i < sampleCount; ++i) {
        samples[i] *= gain;
    }
}

inline void applyBtAacHeadroomIfNeeded(
    float* samples,
    std::size_t sampleCount,
    bool btAacOpt,
    bool bitPerfect) noexcept {
    if (!btAacOpt || bitPerfect || samples == nullptr) return;
    applyGainInPlace(samples, sampleCount, kBtAacHeadroomGain);
}

[[nodiscard]] inline soxr_t asSoxr(void* handle) noexcept {
    return static_cast<soxr_t>(handle);
}

[[nodiscard]] void* createSoxrHandle(
    double inputRate,
    double outputRate,
    std::int32_t channelCount) noexcept {
    if (!(inputRate > 0.0) || !(outputRate > 0.0) || channelCount <= 0) {
        return nullptr;
    }
    soxr_error_t error = nullptr;
    const soxr_io_spec_t ioSpec = soxr_io_spec(SOXR_FLOAT32_I, SOXR_FLOAT32_I);
    // HQ is transparent for playback while avoiding VHQ CPU spikes on mobile ARM cores.
    const soxr_quality_spec_t qualitySpec = soxr_quality_spec(SOXR_HQ, 0);
    const soxr_runtime_spec_t runtimeSpec = soxr_runtime_spec(1);
    soxr_t handle = soxr_create(
        inputRate,
        outputRate,
        static_cast<unsigned>(channelCount),
        &error,
        &ioSpec,
        &qualitySpec,
        &runtimeSpec);
    if (error != nullptr || handle == nullptr) {
        if (handle != nullptr) soxr_delete(handle);
        return nullptr;
    }
    return handle;
}

inline void duplicateMonoToStereo(
    const float* mono,
    float* stereo,
    std::size_t frameCount) noexcept {
    for (std::size_t frame = 0; frame < frameCount; ++frame) {
        const float sample = mono[frame];
        const std::size_t dst = frame * 2U;
        stereo[dst] = sample;
        stereo[dst + 1U] = sample;
    }
}

}  // namespace

void AudioEngine::SoxrDeleter::operator()(void* handle) const noexcept {
    if (handle != nullptr) {
        soxr_delete(asSoxr(handle));
    }
}

AudioEngine::~AudioEngine() {
    stop();
    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    mediaResampler_.reset();
}

bool AudioEngine::start(std::int32_t preferredOutputSampleRate) {
    std::lock_guard<std::mutex> controlLock(controlMutex_);
    playbackEnabled_.store(false, std::memory_order_release);
    inputAlreadyProcessed_.store(false, std::memory_order_release);
    renderedFrames_.store(0, std::memory_order_release);
    underrunCount_.store(0, std::memory_order_release);
    restartAllowed_.store(true, std::memory_order_release);
    return startLocked(preferredOutputSampleRate);
}

bool AudioEngine::startLocked(std::int32_t preferredOutputSampleRate) {
    if (stream_ != nullptr) return true;

    // Never assume the device honored the request: all downstream DSP and
    // resampling keys off openedStream->getSampleRate() read back below.
    const std::int32_t requestedRate = std::max(preferredOutputSampleRate, 0);
    requestedOutputSampleRate_.store(requestedRate, std::memory_order_release);

    std::shared_ptr<oboe::AudioStream> openedStream;
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::None)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(kOutputChannels)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    if (requestedRate > 0) {
        builder.setSampleRate(requestedRate);
    }

    const auto openResult = builder.openStream(openedStream);
    if (openResult != oboe::Result::OK || openedStream == nullptr) {
        restartAllowed_.store(false, std::memory_order_release);
        return false;
    }

    const std::int32_t sampleRate = openedStream->getSampleRate();
    if (sampleRate < kMinSampleRate || sampleRate > kMaxSampleRate ||
        openedStream->getChannelCount() != kOutputChannels ||
        openedStream->getFormat() != oboe::AudioFormat::Float ||
        !openedStream->usesAAudio()) {
        openedStream->close();
        restartAllowed_.store(false, std::memory_order_release);
        return false;
    }

    streamOpenCount_.fetch_add(1, std::memory_order_release);
    if (requestedRate > 0 && sampleRate != requestedRate) {
        rateAdaptationCount_.fetch_add(1, std::memory_order_release);
    }

    const std::int32_t framesPerBurst = std::max(openedStream->getFramesPerBurst(), 1);
    const std::int32_t bufferCapacity = std::max(openedStream->getBufferCapacityInFrames(), framesPerBurst);
    const std::int32_t targetDeviceBufferFrames = std::min(
        bufferCapacity,
        std::max(
            framesPerBurst * 4,
            static_cast<std::int32_t>(
                (static_cast<std::int64_t>(sampleRate) * kTargetDeviceBufferMilliseconds) / 1000)));
    if (targetDeviceBufferFrames > 0) {
        (void) openedStream->setBufferSizeInFrames(targetDeviceBufferFrames);
    }

    // Release and allocate all producer/RT buffers under full producer lock hierarchy.
    {
        for (auto& deck : decks_) {
            deck.control.activeRing.store(nullptr, std::memory_order_release);
        }
        releaseProducerStateLocked();

        std::scoped_lock deckLocks(decks_[0].producer.mutex, decks_[1].producer.mutex);

        const std::size_t capacitySamples =
            (static_cast<std::size_t>(sampleRate) * kRingMilliseconds * kOutputChannels) / 1000U;
        maxCallbackFrames_ = std::max<std::size_t>(
            kProducerChunkFrames * 2U,
            static_cast<std::size_t>(std::max(targetDeviceBufferFrames, bufferCapacity)));
        const std::size_t maxStretchedInputFrames =
            static_cast<std::size_t>(std::ceil(static_cast<double>(maxCallbackFrames_) * kMaxTimeRatio)) +
            kWsolaPaddingFrames;

        for (auto& deck : decks_) {
            deck.producer.ring = std::make_unique<LockFreeRingBuffer<float>>(capacitySamples);
            deck.producer.rawScratch.resize(kProducerChunkFrames * kOutputChannels);
            deck.producer.stereoScratch.resize(kProducerChunkFrames * kOutputChannels);
            deck.producer.resampledScratch.resize(kMaxResampledFrames * kOutputChannels);

            deck.rt.ringScratch.assign(maxStretchedInputFrames * kOutputChannels, 0.0F);
            deck.rt.stretchedScratch.assign(maxCallbackFrames_ * kOutputChannels, 0.0F);
            deck.rt.stretcher.configure(static_cast<double>(sampleRate), kOutputChannels);

            const float ratio = deck.control.timeRatioTarget.load(std::memory_order_acquire);
            deck.rt.stretcher.setTimeRatio(ratio);
            deck.rt.appliedRatio = ratio;
            deck.rt.currentMixGain = 0.0F;
            deck.rt.flushAck = deck.control.flushRequests.load(std::memory_order_acquire);

            deck.control.wsolaDrained.store(true, std::memory_order_release);
            deck.control.flushTargetWrite.store(0, std::memory_order_release);
        }
    }

    oboeDsp_.setPeakProtectionEnabled(true);
    oboeDsp_.configure(sampleRate);
    buildFadeCurve(sampleRate);

    prebufferFrames_ = std::min(
        (static_cast<std::size_t>(sampleRate) * kRingMilliseconds) / 2000U,
        std::max(
            static_cast<std::size_t>(framesPerBurst) * 4U,
            (static_cast<std::size_t>(sampleRate) * kPrebufferMilliseconds) / 1000U));
    prebufferBaseFrames_ = prebufferFrames_;
    underrunDecayIntervalFrames_ = std::max<std::size_t>(
        1U,
        static_cast<std::size_t>(sampleRate) * kUnderrunDecaySeconds);
    outputVolumeRampStep_ = 1.0F /
        std::max(1.0F, static_cast<float>(sampleRate) * kVolumeRampSeconds);
    currentOutputVolume_ = sanitizeClamp(
        targetOutputVolume_.load(std::memory_order_acquire), 0.0F, 1.0F, 1.0F);
    cachedCrossfade_ = -1.0F;
    resetCallbackStateLocked();

    outputSampleRate_.store(sampleRate, std::memory_order_release);
    for (auto& deck : decks_) {
        deck.control.activeRing.store(deck.producer.ring.get(), std::memory_order_release);
    }

    stream_ = std::move(openedStream);
    if (stream_->requestStart() != oboe::Result::OK) {
        for (auto& deck : decks_) {
            deck.control.activeRing.store(nullptr, std::memory_order_release);
        }
        stream_->close();
        stream_.reset();
        outputSampleRate_.store(0, std::memory_order_release);
        releaseProducerStateLocked();
        restartAllowed_.store(false, std::memory_order_release);
        return false;
    }
    return true;
}

void AudioEngine::stop() noexcept {
    // Disallow automatic restart before locking controlMutex_ so any concurrent
    // onErrorAfterClose() exits on its fast path without contending.
    restartAllowed_.store(false, std::memory_order_release);
    playbackEnabled_.store(false, std::memory_order_release);

    std::lock_guard<std::mutex> controlLock(controlMutex_);
    for (auto& deck : decks_) {
        deck.control.activeRing.store(nullptr, std::memory_order_release);
    }
    if (stream_ != nullptr) {
        (void) stream_->requestStop();
        (void) stream_->close();
        stream_.reset();
    }
    outputSampleRate_.store(0, std::memory_order_release);
    renderedFrames_.store(0, std::memory_order_release);
    for (auto& deck : decks_) {
        deck.control.playing.store(false, std::memory_order_release);
    }
    releaseProducerStateLocked();
}

bool AudioEngine::isRunning() const noexcept {
    std::lock_guard<std::mutex> controlLock(controlMutex_);
    return stream_ != nullptr &&
        outputSampleRate_.load(std::memory_order_acquire) > 0;
}

void AudioEngine::flushOutput() noexcept {
    std::lock_guard<std::mutex> controlLock(controlMutex_);
    if (stream_ == nullptr) return;
    flushDeck(kDeckA);
    flushDeck(kDeckB);
    if (outputSampleRate_.load(std::memory_order_acquire) <= 0) {
        resetCallbackStateLocked();
    }
}

void AudioEngine::flushDeck(int deckId) noexcept {
    if (!isValidDeck(deckId)) return;
    auto& deck = decks_[static_cast<std::size_t>(deckId)];
    std::lock_guard<std::mutex> lock(deck.producer.mutex);

    auto* ring = deck.control.activeRing.load(std::memory_order_relaxed);
    if (ring == nullptr) {
        ring = deck.producer.ring.get();
    }
    const std::uint64_t target = ring != nullptr ? ring->currentWriteIndex() : 0U;
    deck.control.flushTargetWrite.store(target, std::memory_order_release);

    if (deck.producer.resampler != nullptr) {
        if (soxr_clear(asSoxr(deck.producer.resampler.get())) != nullptr) {
            deck.producer.resampler.reset();
            deck.producer.resamplerInputRate = 0;
        }
    }
    deck.producer.resamplerFlushing = false;
    deck.control.resamplerDrained.store(true, std::memory_order_release);
    deck.control.framesRendered.store(0, std::memory_order_release);

    if (outputSampleRate_.load(std::memory_order_acquire) > 0) {
        // Stream is active: hand off consumer-owned ring/WSOLA reset to RT callback.
        deck.control.flushRequests.fetch_add(1, std::memory_order_release);
    } else {
        // Stream is stopped: consumer thread is not running, clear directly.
        if (ring != nullptr) ring->clearUpTo(target);
        deck.rt.stretcher.reset();
        deck.rt.currentMixGain = 0.0F;
        deck.control.wsolaDrained.store(true, std::memory_order_release);
    }
}

std::size_t AudioEngine::deckBufferedFrames(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return 0;
    const auto& deck = decks_[static_cast<std::size_t>(deckId)];
    // Locking producer mutex prevents a UAF if stop()/startLocked() destroys ring concurrently.
    std::lock_guard<std::mutex> lock(deck.producer.mutex);
    const auto* ring = deck.control.activeRing.load(std::memory_order_acquire);
    return ring == nullptr ? 0 : ring->availableToRead() / kOutputChannels;
}

void AudioEngine::setDeckVolume(int deckId, float volume) noexcept {
    if (!isValidDeck(deckId)) return;
    decks_[static_cast<std::size_t>(deckId)].control.volume.store(
        sanitizeClamp(volume, 0.0F, 1.0F, 1.0F),
        std::memory_order_release);
}

float AudioEngine::deckVolume(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return 1.0F;
    return decks_[static_cast<std::size_t>(deckId)].control.volume.load(std::memory_order_acquire);
}

void AudioEngine::setCrossfadeProgress(float progress) noexcept {
    crossfadeProgress_.store(
        sanitizeClamp(progress, 0.0F, 1.0F, 0.0F),
        std::memory_order_release);
}

float AudioEngine::crossfadeProgress() const noexcept {
    return crossfadeProgress_.load(std::memory_order_acquire);
}

void AudioEngine::setDeckPlaybackParameters(int deckId, float speed, float pitch) noexcept {
    if (!isValidDeck(deckId)) return;
    auto& control = decks_[static_cast<std::size_t>(deckId)].control;

    const float clampedSpeed = sanitizeClamp(speed, kMinTimeRatio, kMaxTimeRatio, 1.0F);
    const float clampedPitch = sanitizeClamp(pitch, kMinTimeRatio, kMaxTimeRatio, 1.0F);
    // Resampler outputs at (outputRate / pitch), making duration (1 / pitch).
    // WSOLA time-scales by (speed / pitch), yielding final speed = speed and pitch = pitch.
    const float wsolaRatio = std::clamp(clampedSpeed / clampedPitch, kMinTimeRatio, kMaxTimeRatio);

    control.speed.store(clampedSpeed, std::memory_order_release);
    control.pitch.store(clampedPitch, std::memory_order_release);
    control.timeRatioTarget.store(wsolaRatio, std::memory_order_release);
}

float AudioEngine::deckSpeed(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return 1.0F;
    return decks_[static_cast<std::size_t>(deckId)].control.speed.load(std::memory_order_acquire);
}

float AudioEngine::deckPitch(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return 1.0F;
    return decks_[static_cast<std::size_t>(deckId)].control.pitch.load(std::memory_order_acquire);
}

void AudioEngine::setDeckTimeRatio(int deckId, float ratio) noexcept {
    if (!isValidDeck(deckId)) return;
    auto& control = decks_[static_cast<std::size_t>(deckId)].control;
    const float clampedRatio = sanitizeClamp(ratio, kMinTimeRatio, kMaxTimeRatio, 1.0F);
    const float currentPitch = control.pitch.load(std::memory_order_acquire);
    control.timeRatioTarget.store(clampedRatio, std::memory_order_release);
    control.speed.store(
        std::clamp(clampedRatio * currentPitch, kMinTimeRatio, kMaxTimeRatio),
        std::memory_order_release);
}

float AudioEngine::deckTimeRatio(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return 1.0F;
    return decks_[static_cast<std::size_t>(deckId)].control.timeRatioTarget.load(
        std::memory_order_acquire);
}

void AudioEngine::setDeckPlaying(int deckId, bool playing) noexcept {
    if (!isValidDeck(deckId)) return;
    decks_[static_cast<std::size_t>(deckId)].control.playing.store(
        playing, std::memory_order_release);
    const bool anyPlaying =
        decks_[kDeckA].control.playing.load(std::memory_order_acquire) ||
        decks_[kDeckB].control.playing.load(std::memory_order_acquire);
    playbackEnabled_.store(anyPlaying, std::memory_order_release);
}

bool AudioEngine::isDeckPlaying(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return false;
    return decks_[static_cast<std::size_t>(deckId)].control.playing.load(
        std::memory_order_acquire);
}

void AudioEngine::drainDeckResampler(int deckId) noexcept {
    if (!isValidDeck(deckId)) return;
    auto& deck = decks_[static_cast<std::size_t>(deckId)];
    std::lock_guard<std::mutex> lock(deck.producer.mutex);

    auto* ring = deck.control.activeRing.load(std::memory_order_acquire);
    if (ring == nullptr || deck.producer.resampler == nullptr) {
        deck.control.resamplerDrained.store(true, std::memory_order_release);
        return;
    }

    deck.producer.resamplerFlushing = true;
    while (true) {
        const std::size_t availableOutputFrames = std::min(
            kMaxResampledFrames,
            ring->availableToWrite() / kOutputChannels);
        if (availableOutputFrames == 0) break;

        std::size_t outputDone = 0;
        const soxr_error_t error = soxr_process(
            asSoxr(deck.producer.resampler.get()),
            nullptr,
            0,
            nullptr,
            deck.producer.resampledScratch.data(),
            availableOutputFrames,
            &outputDone);
        if (error != nullptr || outputDone == 0) {
            deck.control.resamplerDrained.store(true, std::memory_order_release);
            break;
        }
        const std::size_t writtenSamples = ring->write(
            deck.producer.resampledScratch.data(),
            outputDone * kOutputChannels);
        if (writtenSamples != outputDone * kOutputChannels) {
            break;
        }
    }
}

bool AudioEngine::isDeckResamplerDrained(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return true;
    return decks_[static_cast<std::size_t>(deckId)].control.resamplerDrained.load(
        std::memory_order_acquire);
}

bool AudioEngine::isDeckWsolaDrained(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return true;
    return decks_[static_cast<std::size_t>(deckId)].control.wsolaDrained.load(
        std::memory_order_acquire);
}

std::uint64_t AudioEngine::deckFramesRendered(int deckId) const noexcept {
    if (!isValidDeck(deckId)) return 0;
    return decks_[static_cast<std::size_t>(deckId)].control.framesRendered.load(
        std::memory_order_acquire);
}

std::int64_t AudioEngine::presentationLatencyUs() const noexcept {
    std::lock_guard<std::mutex> controlLock(controlMutex_);
    if (stream_ == nullptr) return 0;
    const auto rate = outputSampleRate_.load(std::memory_order_acquire);
    if (rate <= 0) return 0;

    auto latencyResult = stream_->calculateLatencyMillis();
    if (latencyResult && latencyResult.value() > 0.0) {
        return static_cast<std::int64_t>(latencyResult.value() * 1000.0);
    }
    const auto bufferFrames = stream_->getBufferSizeInFrames();
    return (static_cast<std::int64_t>(bufferFrames) * 1000000LL) / rate;
}

std::size_t AudioEngine::writePcm(
    const void* pcm,
    std::size_t frameCount,
    PcmFormat format,
    std::int32_t inputSampleRate,
    std::int32_t inputChannelCount) {
    const auto result = writeDeckPcm(
        kDeckA, pcm, frameCount, format, inputSampleRate, inputChannelCount);
    return result > 0 ? static_cast<std::size_t>(result) : 0U;
}

std::int64_t AudioEngine::writeDeckPcm(
    int deckId,
    const void* pcm,
    std::size_t frameCount,
    PcmFormat format,
    std::int32_t inputSampleRate,
    std::int32_t inputChannelCount) {
    if (!isValidDeck(deckId)) return -4;
    const std::size_t sampleBytes = PcmConverter::bytesPerSample(format);
    if (pcm == nullptr || sampleBytes == 0) return -1;
    if (frameCount == 0) return 0;
    if (inputSampleRate < kMinSampleRate || inputSampleRate > kMaxSampleRate ||
        (inputChannelCount != 1 && inputChannelCount != kOutputChannels)) {
        return -2;
    }

    const std::size_t d = static_cast<std::size_t>(deckId);
    auto& deck = decks_[d];
    std::lock_guard<std::mutex> producerLock(deck.producer.mutex);

    auto* ring = deck.control.activeRing.load(std::memory_order_acquire);
    const std::int32_t destinationRate = outputSampleRate_.load(std::memory_order_acquire);
    if (ring == nullptr || destinationRate <= 0) return -3;

    if (!configureDeckResamplerLocked(d, inputSampleRate)) return -2;

    inputAlreadyProcessed_.store(false, std::memory_order_release);
    deck.control.wsolaDrained.store(false, std::memory_order_release);

    const auto* inputBytes = static_cast<const std::uint8_t*>(pcm);
    const std::size_t bytesPerFrame = sampleBytes * static_cast<std::size_t>(inputChannelCount);
    const double targetOutRate =
        static_cast<double>(destinationRate) / static_cast<double>(deck.producer.resamplerPitch);
    const double ratio = targetOutRate / static_cast<double>(inputSampleRate);
    const std::size_t ratioSafeChunk = std::max<std::size_t>(
        1U,
        static_cast<std::size_t>(
            (kMaxResampledFrames - kResamplerSafetyMarginFrames) / std::max(1.0, ratio)));

    std::size_t consumedFrames = 0;
    while (consumedFrames < frameCount) {
        const std::size_t chunkFrames = std::min({
            frameCount - consumedFrames,
            kProducerChunkFrames,
            ratioSafeChunk,
        });
        const void* chunkInput = inputBytes + consumedFrames * bytesPerFrame;
        const std::size_t inputSamples = chunkFrames * static_cast<std::size_t>(inputChannelCount);

        if (inputChannelCount == kOutputChannels) {
            if (!PcmConverter::toFloat(
                    chunkInput, inputSamples, format, deck.producer.stereoScratch.data())) {
                break;
            }
        } else {
            if (!PcmConverter::toFloat(
                    chunkInput, inputSamples, format, deck.producer.rawScratch.data())) {
                break;
            }
            duplicateMonoToStereo(
                deck.producer.rawScratch.data(),
                deck.producer.stereoScratch.data(),
                chunkFrames);
        }

        if (deck.producer.resampler == nullptr) {
            const std::size_t writtenSamples = ring->write(
                deck.producer.stereoScratch.data(),
                chunkFrames * kOutputChannels);
            const std::size_t writtenFrames = writtenSamples / kOutputChannels;
            consumedFrames += writtenFrames;
            if (writtenFrames != chunkFrames) break;
            continue;
        }

        std::size_t chunkDone = 0;
        while (chunkDone < chunkFrames) {
            const std::size_t availOut = std::min(
                kMaxResampledFrames,
                ring->availableToWrite() / kOutputChannels);
            if (availOut == 0) break;

            std::size_t inDone = 0;
            std::size_t outDone = 0;
            const soxr_error_t error = soxr_process(
                asSoxr(deck.producer.resampler.get()),
                deck.producer.stereoScratch.data() + chunkDone * kOutputChannels,
                chunkFrames - chunkDone,
                &inDone,
                deck.producer.resampledScratch.data(),
                availOut,
                &outDone);
            if (error != nullptr) break;

            if (outDone > 0) {
                const std::size_t samples = outDone * kOutputChannels;
                if (ring->write(deck.producer.resampledScratch.data(), samples) != samples) {
                    break;
                }
            }
            chunkDone += inDone;
            consumedFrames += inDone;
            if (inDone == 0 && outDone == 0) break;
        }
        if (chunkDone < chunkFrames) break;
    }

    if (consumedFrames > 0) {
        deck.control.resamplerDrained.store(false, std::memory_order_release);
        deck.control.framesWritten.fetch_add(consumedFrames, std::memory_order_release);
    }
    return static_cast<std::int64_t>(consumedFrames);
}

std::size_t AudioEngine::writeProcessedPcm(
    const float* pcm,
    std::size_t frameCount,
    std::int32_t inputSampleRate,
    std::int32_t inputChannelCount) {
    if (pcm == nullptr || frameCount == 0 || inputSampleRate <= 0 ||
        (inputChannelCount != 1 && inputChannelCount != kOutputChannels)) {
        return 0;
    }

    auto& deck = decks_[kDeckA];
    std::lock_guard<std::mutex> producerLock(deck.producer.mutex);
    auto* ring = deck.control.activeRing.load(std::memory_order_acquire);
    const std::int32_t destinationRate = outputSampleRate_.load(std::memory_order_acquire);
    if (ring == nullptr || inputSampleRate != destinationRate) return 0;

    inputAlreadyProcessed_.store(true, std::memory_order_release);
    deck.control.wsolaDrained.store(false, std::memory_order_release);

    const std::size_t writableFrames = std::min(
        frameCount,
        ring->availableToWrite() / kOutputChannels);
    if (writableFrames == 0) return 0;

    if (inputChannelCount == kOutputChannels) {
        const std::size_t written =
            ring->write(pcm, writableFrames * kOutputChannels) / kOutputChannels;
        deck.control.framesWritten.fetch_add(written, std::memory_order_release);
        return written;
    }

    std::size_t writtenFrames = 0;
    while (writtenFrames < writableFrames) {
        const std::size_t chunkFrames = std::min(
            writableFrames - writtenFrames,
            kProducerChunkFrames);
        duplicateMonoToStereo(
            pcm + writtenFrames,
            deck.producer.stereoScratch.data(),
            chunkFrames);
        const std::size_t chunkWrittenFrames = ring->write(
            deck.producer.stereoScratch.data(),
            chunkFrames * kOutputChannels) / kOutputChannels;
        writtenFrames += chunkWrittenFrames;
        if (chunkWrittenFrames != chunkFrames) break;
    }
    deck.control.framesWritten.fetch_add(writtenFrames, std::memory_order_release);
    return writtenFrames;
}

void AudioEngine::setPlaying(bool playing) noexcept {
    if (playing &&
        !decks_[kDeckA].control.playing.load(std::memory_order_acquire) &&
        !decks_[kDeckB].control.playing.load(std::memory_order_acquire)) {
        decks_[kDeckA].control.playing.store(true, std::memory_order_release);
    }
    playbackEnabled_.store(playing, std::memory_order_release);
}

void AudioEngine::setOutputVolume(float volume) noexcept {
    targetOutputVolume_.store(
        sanitizeClamp(volume, 0.0F, 1.0F, 1.0F),
        std::memory_order_release);
}

void AudioEngine::flushResampler() {
    drainDeckResampler(kDeckA);
}

void AudioEngine::setStudioMasterClarity(bool enabled) noexcept {
    oboeDsp_.setStudioMasterClarity(enabled);
    mediaDsp_.setStudioMasterClarity(enabled);
}

void AudioEngine::setBauerCrossfeedEnabled(bool enabled) noexcept {
    oboeDsp_.setBauerCrossfeedEnabled(enabled);
    mediaDsp_.setBauerCrossfeedEnabled(enabled);
}

void AudioEngine::setAnalogTubeWarmthEnabled(bool enabled) noexcept {
    oboeDsp_.setAnalogTubeWarmthEnabled(enabled);
    mediaDsp_.setAnalogTubeWarmthEnabled(enabled);
}

void AudioEngine::setMonoBassEnabled(bool enabled) noexcept {
    oboeDsp_.setMonoBassEnabled(enabled);
    mediaDsp_.setMonoBassEnabled(enabled);
}

void AudioEngine::setSpatialImmersionEnabled(bool enabled) noexcept {
    oboeDsp_.setSpatialImmersionEnabled(enabled);
    mediaDsp_.setSpatialImmersionEnabled(enabled);
}

void AudioEngine::setSpatialImmersionStrength(float strength) noexcept {
    const float safe = sanitizeClamp(strength, 0.0F, 1.0F, 0.0F);
    oboeDsp_.setSpatialImmersionStrength(safe);
    mediaDsp_.setSpatialImmersionStrength(safe);
}

void AudioEngine::setHrtfHeadScale(float scale) noexcept {
    const float safe = sanitizeClamp(scale, 0.5F, 1.5F, 1.0F);
    oboeDsp_.setHrtfHeadScale(safe);
    mediaDsp_.setHrtfHeadScale(safe);
}

bool AudioEngine::isSpatialImmersionEnabled() const noexcept {
    return oboeDsp_.isSpatialImmersionEnabled() && mediaDsp_.isSpatialImmersionEnabled();
}

float AudioEngine::getHrtfHeadScale() const noexcept {
    return oboeDsp_.getHrtfHeadScale();
}

void AudioEngine::setBitPerfect(bool enabled) noexcept {
    oboeDsp_.setBitPerfect(enabled);
    mediaDsp_.setBitPerfect(enabled);
}

bool AudioEngine::isBitPerfect() const noexcept {
    return oboeDsp_.isBitPerfectEnabled() && mediaDsp_.isBitPerfectEnabled();
}

void AudioEngine::setBtAacOptimizationEnabled(bool enabled) noexcept {
    btAacOptimizationEnabled_.store(enabled, std::memory_order_release);
}

bool AudioEngine::isBtAacOptimizationEnabled() const noexcept {
    return btAacOptimizationEnabled_.load(std::memory_order_acquire);
}

std::int32_t AudioEngine::consumeHapticTelemetry() noexcept {
    float bassM = 0.0F;
    float transM = 0.0F;
    float bassO = 0.0F;
    float transO = 0.0F;
    mediaDsp_.consumeHapticTelemetry(bassM, transM);
    oboeDsp_.consumeHapticTelemetry(bassO, transO);

    const float bass = std::max(
        sanitizeClamp(bassM, 0.0F, 1.0F, 0.0F),
        sanitizeClamp(bassO, 0.0F, 1.0F, 0.0F));
    const float trans = std::max(
        sanitizeClamp(transM, 0.0F, 1.0F, 0.0F),
        sanitizeClamp(transO, 0.0F, 1.0F, 0.0F));

    const auto b = static_cast<std::int32_t>(bass * 10000.0F);
    const auto t = static_cast<std::int32_t>(trans * 10000.0F);
    return (b << 16) | (t & 0xFFFF);
}

void AudioEngine::setEqualizer(
    bool enabled,
    const float* gainsDb,
    std::size_t gainCount) noexcept {
    oboeDsp_.setEqualizer(enabled, gainsDb, gainCount);
    mediaDsp_.setEqualizer(enabled, gainsDb, gainCount);
}

bool AudioEngine::configureMediaProcessor(
    std::int32_t inputSampleRate,
    std::int32_t outputSampleRate,
    std::int32_t channelCount) {
    if (inputSampleRate < kMinSampleRate || inputSampleRate > kMaxSampleRate ||
        outputSampleRate < kMinSampleRate || outputSampleRate > kMaxSampleRate ||
        channelCount < 1 || channelCount > static_cast<std::int32_t>(DspProcessor::kMaxChannels)) {
        return false;
    }

    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    mediaResampler_.reset();
    mediaInputSampleRate_ = inputSampleRate;
    mediaOutputSampleRate_ = outputSampleRate;
    mediaChannelCount_ = channelCount;
    mediaChannelCountAtomic_.store(channelCount, std::memory_order_release);
    mediaScratch_.resize(kProducerChunkFrames * static_cast<std::size_t>(channelCount));

    // Resample first so sinc overshoot is caught by peak protection at the output rate.
    mediaDsp_.setPeakProtectionEnabled(true);
    mediaDsp_.configure(outputSampleRate);
    return configureMediaResamplerLocked();
}

bool AudioEngine::processMediaPcm(
    const void* pcm,
    std::size_t frameCount,
    PcmFormat format,
    float* output,
    std::size_t outputCapacityFrames,
    std::size_t& outputFrameCount) {
    outputFrameCount = 0;
    if (pcm == nullptr || output == nullptr || frameCount == 0 || outputCapacityFrames == 0) {
        return false;
    }

    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    if (mediaInputSampleRate_ <= 0 || mediaOutputSampleRate_ <= 0 ||
        mediaChannelCount_ <= 0 || mediaScratch_.empty()) {
        return false;
    }

    const std::size_t channels = static_cast<std::size_t>(mediaChannelCount_);
    const std::size_t bytesPerFrame = PcmConverter::bytesPerSample(format) * channels;
    if (bytesPerFrame == 0) return false;

    const bool btAacOpt = btAacOptimizationEnabled_.load(std::memory_order_relaxed);
    const bool bitPerfect = mediaDsp_.isBitPerfectEnabled();

    if (mediaResampler_ == nullptr && mediaInputSampleRate_ == mediaOutputSampleRate_) {
        if (outputCapacityFrames < frameCount ||
            !PcmConverter::toFloat(pcm, frameCount * channels, format, output)) {
            return false;
        }
        mediaDsp_.process(output, static_cast<std::int32_t>(frameCount), mediaChannelCount_);
        applyBtAacHeadroomIfNeeded(output, frameCount * channels, btAacOpt, bitPerfect);
        outputFrameCount = frameCount;
        return true;
    }
    if (mediaResampler_ == nullptr) return false;

    const auto* inputBytes = static_cast<const std::uint8_t*>(pcm);
    std::size_t consumedFrames = 0;
    while (consumedFrames < frameCount) {
        const std::size_t chunkFrames = std::min(
            frameCount - consumedFrames,
            kProducerChunkFrames);
        if (!PcmConverter::toFloat(
                inputBytes + consumedFrames * bytesPerFrame,
                chunkFrames * channels,
                format,
                mediaScratch_.data())) {
            return false;
        }

        std::size_t chunkConsumed = 0;
        while (chunkConsumed < chunkFrames) {
            const std::size_t remainingOutput = outputCapacityFrames - outputFrameCount;
            if (remainingOutput == 0) {
                return false;  // Caller-supplied output buffer too small; do not silently drop input.
            }
            std::size_t inputDone = 0;
            std::size_t outputDone = 0;
            const soxr_error_t error = soxr_process(
                asSoxr(mediaResampler_.get()),
                mediaScratch_.data() + chunkConsumed * channels,
                chunkFrames - chunkConsumed,
                &inputDone,
                output + outputFrameCount * channels,
                remainingOutput,
                &outputDone);
            if (error != nullptr) return false;

            if (outputDone > 0) {
                float* dst = output + outputFrameCount * channels;
                mediaDsp_.process(dst, static_cast<std::int32_t>(outputDone), mediaChannelCount_);
                applyBtAacHeadroomIfNeeded(dst, outputDone * channels, btAacOpt, bitPerfect);
                outputFrameCount += outputDone;
            }
            chunkConsumed += inputDone;
            consumedFrames += inputDone;
            if (inputDone == 0 && outputDone == 0) break;
        }
        if (chunkConsumed < chunkFrames) {
            return false;
        }
    }
    return true;
}

bool AudioEngine::flushMediaProcessor(
    float* output,
    std::size_t outputCapacityFrames,
    std::size_t& outputFrameCount) {
    outputFrameCount = 0;
    if (output == nullptr || outputCapacityFrames == 0) return false;

    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    if (mediaResampler_ == nullptr) return true;

    const std::size_t channels = static_cast<std::size_t>(mediaChannelCount_);
    const bool btAacOpt = btAacOptimizationEnabled_.load(std::memory_order_relaxed);
    const bool bitPerfect = mediaDsp_.isBitPerfectEnabled();

    while (outputFrameCount < outputCapacityFrames) {
        std::size_t outputDone = 0;
        const soxr_error_t error = soxr_process(
            asSoxr(mediaResampler_.get()),
            nullptr,
            0,
            nullptr,
            output + outputFrameCount * channels,
            outputCapacityFrames - outputFrameCount,
            &outputDone);
        if (error != nullptr) return false;
        if (outputDone == 0) break;

        float* dst = output + outputFrameCount * channels;
        mediaDsp_.process(dst, static_cast<std::int32_t>(outputDone), mediaChannelCount_);
        applyBtAacHeadroomIfNeeded(dst, outputDone * channels, btAacOpt, bitPerfect);
        outputFrameCount += outputDone;
    }
    return true;
}

void AudioEngine::resetMediaProcessor() {
    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    mediaDsp_.reset();
    if (mediaResampler_ != nullptr) {
        if (soxr_clear(asSoxr(mediaResampler_.get())) != nullptr) {
            mediaResampler_.reset();
            (void) configureMediaResamplerLocked();
        }
    } else {
        (void) configureMediaResamplerLocked();
    }
}

bool AudioEngine::configureMediaResamplerLocked() {
    if (mediaInputSampleRate_ < kMinSampleRate || mediaOutputSampleRate_ < kMinSampleRate ||
        mediaChannelCount_ < 1 ||
        mediaChannelCount_ > static_cast<std::int32_t>(DspProcessor::kMaxChannels)) {
        return false;
    }
    if (mediaInputSampleRate_ == mediaOutputSampleRate_) return true;

    mediaResampler_.reset(createSoxrHandle(
        static_cast<double>(mediaInputSampleRate_),
        static_cast<double>(mediaOutputSampleRate_),
        mediaChannelCount_));
    return mediaResampler_ != nullptr;
}

std::size_t AudioEngine::bufferedFrames() const noexcept {
    return deckBufferedFrames(kDeckA) + deckBufferedFrames(kDeckB);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* /* audioStream */,
    void* audioData,
    std::int32_t numFrames) {
    auto* output = static_cast<float*>(audioData);
    if (output == nullptr || numFrames <= 0) return oboe::DataCallbackResult::Continue;

    const std::size_t totalFrames = static_cast<std::size_t>(numFrames);
    if (maxCallbackFrames_ == 0 ||
        (decks_[kDeckA].control.activeRing.load(std::memory_order_acquire) == nullptr &&
         decks_[kDeckB].control.activeRing.load(std::memory_order_acquire) == nullptr)) {
        std::memset(output, 0, totalFrames * kOutputChannels * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    // Process in chunks bounded by maxCallbackFrames_ so scratch buffers can never overflow.
    std::size_t processedFrames = 0;
    while (processedFrames < totalFrames) {
        const std::size_t chunkFrames = std::min(totalFrames - processedFrames, maxCallbackFrames_);
        renderChunkRt(output + processedFrames * kOutputChannels, chunkFrames);
        processedFrames += chunkFrames;
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::renderChunkRt(float* output, std::size_t requestedFrames) noexcept {
    bool anyFlushed = false;
    syncDeckControlsRt(anyFlushed);

    const bool playingNow = playbackEnabled_.load(std::memory_order_acquire);
    const bool deckAPlaying = decks_[kDeckA].control.playing.load(std::memory_order_acquire);
    const bool deckBPlaying = decks_[kDeckB].control.playing.load(std::memory_order_acquire);
    const bool deckAActive = deckAPlaying || decks_[kDeckA].rt.currentMixGain > kGainEpsilon;
    const bool deckBActive = deckBPlaying || decks_[kDeckB].rt.currentMixGain > kGainEpsilon;

    auto* ringA = decks_[kDeckA].control.activeRing.load(std::memory_order_acquire);
    auto* ringB = decks_[kDeckB].control.activeRing.load(std::memory_order_acquire);
    const std::size_t availA = (deckAActive && ringA != nullptr)
        ? (ringA->availableToRead() / kOutputChannels) : 0U;
    const std::size_t availB = (deckBActive && ringB != nullptr)
        ? (ringB->availableToRead() / kOutputChannels) : 0U;
    const std::size_t maxAvailFrames = std::max(availA, availB);

    // Only enter global prebuffering after a flush if no active deck has audio ready.
    if (anyFlushed && maxAvailFrames == 0) {
        underrunActive_ = false;
        recoveryFading_ = false;
        underrunFadePosition_ = fadeInCurve_.size();
        recoveryFadePosition_ = 0;
        prebuffering_ = true;
        prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
        lastOutput_.fill(0.0F);
        underrunAnchor_.fill(0.0F);
        cleanFrames_ = 0;
    }

    const bool outputBitPerfect = oboeDsp_.isBitPerfectEnabled();
    const bool drainingPauseTail =
        !playingNow && currentOutputVolume_ > kGainEpsilon && maxAvailFrames > 0;

    if ((!playingNow && !drainingPauseTail) || (!deckAActive && !deckBActive)) {
        std::memset(output, 0, requestedFrames * kOutputChannels * sizeof(float));
        if (!playingNow) {
            currentOutputVolume_ = 0.0F;
        }
        underrunActive_ = false;
        recoveryFading_ = false;
        prebuffering_ = true;
        prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
        lastOutput_.fill(0.0F);
        return;
    }

    if (prebuffering_ && playingNow) {
        const bool anyStillFeeding =
            (deckAActive && !decks_[kDeckA].control.resamplerDrained.load(std::memory_order_acquire)) ||
            (deckBActive && !decks_[kDeckB].control.resamplerDrained.load(std::memory_order_acquire));
        if (maxAvailFrames == 0 ||
            (anyStillFeeding && maxAvailFrames < prebufferFrames_ && prebufferWaitCallbacks_-- > 0)) {
            std::memset(output, 0, requestedFrames * kOutputChannels * sizeof(float));
            return;
        }
        prebuffering_ = false;
        recoveryFading_ = !outputBitPerfect;
        recoveryFadePosition_ = 0;
    }

    const float* samplesA = nullptr;
    const float* samplesB = nullptr;
    const std::size_t activeFramesA = pullDeckFramesRt(kDeckA, deckAActive, requestedFrames, samplesA);
    const std::size_t activeFramesB = pullDeckFramesRt(kDeckB, deckBActive, requestedFrames, samplesB);
    const std::size_t validFrames = std::max(activeFramesA, activeFramesB);
    const bool wasUnderrun = underrunActive_;

    if (validFrames > 0) {
        updateCrossfadeGainsRt(crossfadeProgress_.load(std::memory_order_relaxed));
        const float targetGainA = deckAPlaying
            ? (cachedXfadeGainA_ * decks_[kDeckA].control.volume.load(std::memory_order_relaxed))
            : 0.0F;
        const float targetGainB = deckBPlaying
            ? (cachedXfadeGainB_ * decks_[kDeckB].control.volume.load(std::memory_order_relaxed))
            : 0.0F;

        mixDecksRt(
            output,
            samplesA, activeFramesA, targetGainA,
            samplesB, activeFramesB, targetGainB,
            validFrames,
            outputBitPerfect);

        applyOutputStageRt(output, validFrames, playingNow, wasUnderrun, outputBitPerfect);

        lastOutput_[0] = output[(validFrames - 1U) * kOutputChannels];
        lastOutput_[1] = output[(validFrames - 1U) * kOutputChannels + 1U];
        renderedFrames_.fetch_add(validFrames, std::memory_order_release);
    }

    handleUnderrunOrDecayRt(output, validFrames, requestedFrames, playingNow, wasUnderrun);
}

void AudioEngine::syncDeckControlsRt(bool& anyFlushed) noexcept {
    anyFlushed = false;
    for (auto& deck : decks_) {
        const std::uint64_t req = deck.control.flushRequests.load(std::memory_order_acquire);
        if (req != deck.rt.flushAck) {
            auto* ring = deck.control.activeRing.load(std::memory_order_relaxed);
            if (ring != nullptr) {
                ring->clearUpTo(deck.control.flushTargetWrite.load(std::memory_order_acquire));
            }
            deck.rt.stretcher.reset();
            deck.rt.currentMixGain = 0.0F;
            deck.control.wsolaDrained.store(true, std::memory_order_release);
            deck.rt.flushAck = req;
            anyFlushed = true;
        }

        const float ratioTarget = deck.control.timeRatioTarget.load(std::memory_order_acquire);
        if (std::abs(ratioTarget - deck.rt.appliedRatio) > 1e-4F) {
            deck.rt.stretcher.setTimeRatio(ratioTarget);
            deck.rt.appliedRatio = ratioTarget;
        }
    }
}

void AudioEngine::updateCrossfadeGainsRt(float xfade) noexcept {
    if (std::abs(xfade - cachedCrossfade_) <= kGainEpsilon) return;
    cachedCrossfade_ = xfade;
    if (xfade <= 0.0F) {
        cachedXfadeGainA_ = 1.0F;
        cachedXfadeGainB_ = 0.0F;
    } else if (xfade >= 1.0F) {
        cachedXfadeGainA_ = 0.0F;
        cachedXfadeGainB_ = 1.0F;
    } else {
        const float angle = xfade * kHalfPi;
        cachedXfadeGainA_ = std::cos(angle);
        cachedXfadeGainB_ = std::sin(angle);
    }
}

std::size_t AudioEngine::pullDeckFramesRt(
    std::size_t deckIdx,
    bool deckActive,
    std::size_t requestedFrames,
    const float*& outSamples) noexcept {
    auto& deck = decks_[deckIdx];
    auto* ring = deck.control.activeRing.load(std::memory_order_acquire);
    outSamples = deck.rt.ringScratch.data();

    if (!deckActive || ring == nullptr) {
        return 0;
    }

    const float ratio = deck.rt.appliedRatio;
    const bool stretching = std::abs(ratio - 1.0F) > kRatioEpsilon;
    const std::size_t maxRingScratchFrames = deck.rt.ringScratch.size() / kOutputChannels;
    const std::size_t neededFrames = stretching
        ? std::min(
              maxRingScratchFrames,
              static_cast<std::size_t>(std::ceil(static_cast<float>(requestedFrames) * ratio)) +
                  kWsolaPaddingFrames)
        : std::min(maxRingScratchFrames, requestedFrames);

    const std::size_t framesRead =
        ring->read(deck.rt.ringScratch.data(), neededFrames * kOutputChannels) / kOutputChannels;

    std::size_t activeFrames = framesRead;
    if (stretching) {
        const std::size_t maxOutFrames = std::min(
            requestedFrames,
            deck.rt.stretchedScratch.size() / kOutputChannels);
        if (framesRead > 0) {
            activeFrames = deck.rt.stretcher.process(
                deck.rt.ringScratch.data(),
                framesRead,
                deck.rt.stretchedScratch.data(),
                maxOutFrames);
            outSamples = deck.rt.stretchedScratch.data();
            deck.control.wsolaDrained.store(false, std::memory_order_release);
        } else if (deck.control.resamplerDrained.load(std::memory_order_acquire)) {
            activeFrames = deck.rt.stretcher.flush(
                deck.rt.stretchedScratch.data(),
                maxOutFrames);
            if (activeFrames > 0) {
                outSamples = deck.rt.stretchedScratch.data();
                deck.control.wsolaDrained.store(false, std::memory_order_release);
            } else {
                deck.control.wsolaDrained.store(true, std::memory_order_release);
            }
        }
    } else {
        deck.control.wsolaDrained.store(true, std::memory_order_release);
    }

    if (activeFrames > 0) {
        deck.control.framesRendered.fetch_add(activeFrames, std::memory_order_release);
    }
    return activeFrames;
}

void AudioEngine::mixDecksRt(
    float* output,
    const float* samplesA,
    std::size_t framesA,
    float targetGainA,
    const float* samplesB,
    std::size_t framesB,
    float targetGainB,
    std::size_t validFrames,
    bool outputBitPerfect) noexcept {
    float& curA = decks_[kDeckA].rt.currentMixGain;
    float& curB = decks_[kDeckB].rt.currentMixGain;

    // Preserve bit-exact unity start for single-deck bit-perfect playback.
    if (outputBitPerfect) {
        if (framesA > 0 && framesB == 0 && std::abs(targetGainA - 1.0F) <= kGainEpsilon) {
            curA = 1.0F;
        } else if (framesB > 0 && framesA == 0 && std::abs(targetGainB - 1.0F) <= kGainEpsilon) {
            curB = 1.0F;
        }
    }

    const bool rampA = framesA > 0 && std::abs(targetGainA - curA) > kGainEpsilon;
    const bool rampB = framesB > 0 && std::abs(targetGainB - curB) > kGainEpsilon;

    if (!rampA && !rampB) {
        // Fast auto-vectorizable steady-state paths.
        const float gA = framesA > 0 ? curA : 0.0F;
        const float gB = framesB > 0 ? curB : 0.0F;

        if (framesB == 0) {
            const std::size_t n = framesA * kOutputChannels;
            if (std::abs(gA - 1.0F) <= kGainEpsilon) {
                std::memcpy(output, samplesA, n * sizeof(float));
            } else {
                for (std::size_t i = 0; i < n; ++i) {
                    output[i] = samplesA[i] * gA;
                }
            }
            return;
        }
        if (framesA == 0) {
            const std::size_t n = framesB * kOutputChannels;
            if (std::abs(gB - 1.0F) <= kGainEpsilon) {
                std::memcpy(output, samplesB, n * sizeof(float));
            } else {
                for (std::size_t i = 0; i < n; ++i) {
                    output[i] = samplesB[i] * gB;
                }
            }
            return;
        }

        const std::size_t commonSamples = std::min(framesA, framesB) * kOutputChannels;
        for (std::size_t i = 0; i < commonSamples; ++i) {
            output[i] = samplesA[i] * gA + samplesB[i] * gB;
        }
        if (framesA > framesB) {
            const std::size_t totalA = framesA * kOutputChannels;
            for (std::size_t i = commonSamples; i < totalA; ++i) {
                output[i] = samplesA[i] * gA;
            }
        } else if (framesB > framesA) {
            const std::size_t totalB = framesB * kOutputChannels;
            for (std::size_t i = commonSamples; i < totalB; ++i) {
                output[i] = samplesB[i] * gB;
            }
        }
        return;
    }

    // Per-frame click-free gain ramping path.
    const float step = outputVolumeRampStep_;
    for (std::size_t frame = 0; frame < validFrames; ++frame) {
        const std::size_t off = frame * kOutputChannels;
        float left = 0.0F;
        float right = 0.0F;

        if (frame < framesA) {
            curA += std::clamp(targetGainA - curA, -step, step);
            left += samplesA[off] * curA;
            right += samplesA[off + 1U] * curA;
        }
        if (frame < framesB) {
            curB += std::clamp(targetGainB - curB, -step, step);
            left += samplesB[off] * curB;
            right += samplesB[off + 1U] * curB;
        }
        output[off] = left;
        output[off + 1U] = right;
    }
}

void AudioEngine::applyOutputStageRt(
    float* output,
    std::size_t validFrames,
    bool playingNow,
    bool wasUnderrun,
    bool outputBitPerfect) noexcept {
    const bool alreadyProcessed = inputAlreadyProcessed_.load(std::memory_order_acquire);
    if (!alreadyProcessed) {
        oboeDsp_.process(output, static_cast<std::int32_t>(validFrames));
    }

    if (wasUnderrun && !outputBitPerfect) {
        recoveryFading_ = true;
        recoveryFadePosition_ = 0;
    }

    const bool btAacOpt = btAacOptimizationEnabled_.load(std::memory_order_relaxed);
    const float aacHeadroomGain = (!outputBitPerfect && btAacOpt && !alreadyProcessed)
        ? kBtAacHeadroomGain
        : 1.0F;

    // Even in bit-perfect mode, ramp smoothly to 0 on pause so playback never hangs
    // draining the ring buffer at full volume.
    const float volumeTarget = playingNow
        ? (outputBitPerfect ? 1.0F : targetOutputVolume_.load(std::memory_order_relaxed))
        : 0.0F;

    const bool needsVolumeRamp = std::abs(volumeTarget - currentOutputVolume_) > kGainEpsilon;
    if (recoveryFading_ || needsVolumeRamp) {
        const float step = outputVolumeRampStep_;
        for (std::size_t frame = 0; frame < validFrames; ++frame) {
            float recoveryGain = 1.0F;
            if (recoveryFading_) {
                if (recoveryFadePosition_ < fadeInCurve_.size()) {
                    recoveryGain = fadeInCurve_[recoveryFadePosition_++];
                } else {
                    recoveryFading_ = false;
                }
            }
            currentOutputVolume_ += std::clamp(volumeTarget - currentOutputVolume_, -step, step);
            const float gain = recoveryGain * currentOutputVolume_ * aacHeadroomGain;
            const std::size_t offset = frame * kOutputChannels;
            output[offset] *= gain;
            output[offset + 1U] *= gain;
        }
    } else {
        const float steadyGain = currentOutputVolume_ * aacHeadroomGain;
        applyGainInPlace(output, validFrames * kOutputChannels, steadyGain);
    }
}

void AudioEngine::handleUnderrunOrDecayRt(
    float* output,
    std::size_t validFrames,
    std::size_t requestedFrames,
    bool playingNow,
    bool wasUnderrun) noexcept {
    if (validFrames < requestedFrames) {
        if (playingNow) {
            if (!wasUnderrun || validFrames > 0) {
                underrunCount_.fetch_add(1, std::memory_order_release);
                underrunAnchor_ = lastOutput_;
                underrunFadePosition_ = 0;
            }
            recoveryFading_ = false;
            for (std::size_t frame = validFrames; frame < requestedFrames; ++frame) {
                const float fadeOut = underrunFadePosition_ < fadeInCurve_.size()
                    ? (1.0F - fadeInCurve_[underrunFadePosition_++])
                    : 0.0F;
                const std::size_t offset = frame * kOutputChannels;
                output[offset] = underrunAnchor_[0] * fadeOut;
                output[offset + 1U] = underrunAnchor_[1] * fadeOut;
            }
            underrunActive_ = true;
            prebuffering_ = true;
            prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
            cleanFrames_ = 0;

            auto* ringA = decks_[kDeckA].control.activeRing.load(std::memory_order_relaxed);
            auto* ringB = decks_[kDeckB].control.activeRing.load(std::memory_order_relaxed);
            const std::size_t capacityFrames = (ringA != nullptr)
                ? (ringA->capacity() / kOutputChannels)
                : (ringB != nullptr ? ringB->capacity() / kOutputChannels : 0U);
            if (capacityFrames > 0) {
                prebufferFrames_ = std::max(
                    prebufferBaseFrames_,
                    std::min(prebufferFrames_ * 2U, capacityFrames / 2U));
            }
        } else {
            std::memset(
                output + validFrames * kOutputChannels,
                0,
                (requestedFrames - validFrames) * kOutputChannels * sizeof(float));
            underrunActive_ = false;
            recoveryFading_ = false;
        }
        return;
    }

    underrunActive_ = false;
    cleanFrames_ += requestedFrames;
    while (underrunDecayIntervalFrames_ > 0 && cleanFrames_ >= underrunDecayIntervalFrames_) {
        cleanFrames_ -= underrunDecayIntervalFrames_;
        if (prebufferFrames_ > prebufferBaseFrames_) {
            prebufferFrames_ = std::max(prebufferBaseFrames_, prebufferFrames_ / 2U);
        }
        auto current = underrunCount_.load(std::memory_order_acquire);
        while (current > 0 && !underrunCount_.compare_exchange_weak(
            current, current - 1, std::memory_order_release, std::memory_order_acquire)) {
        }
    }
}

void AudioEngine::onErrorAfterClose(
    oboe::AudioStream* audioStream,
    oboe::Result /* error */) {
    if (!restartAllowed_.load(std::memory_order_acquire)) return;
    std::lock_guard<std::mutex> controlLock(controlMutex_);
    if (!restartAllowed_.load(std::memory_order_acquire) ||
        stream_ == nullptr || stream_.get() != audioStream) {
        return;
    }

    streamRestartCount_.fetch_add(1, std::memory_order_release);
    for (auto& deck : decks_) {
        deck.control.activeRing.store(nullptr, std::memory_order_release);
    }
    stream_.reset();
    outputSampleRate_.store(0, std::memory_order_release);
    releaseProducerStateLocked();

    // Control atomics (playing, speed, pitch, timeRatioTarget) remain intact and are
    // applied inside startLocked() before requestStart(), avoiding any RT data race.
    try {
        (void) startLocked(requestedOutputSampleRate_.load(std::memory_order_acquire));
    } catch (...) {
        restartAllowed_.store(false, std::memory_order_release);
        for (auto& deck : decks_) {
            deck.control.activeRing.store(nullptr, std::memory_order_release);
        }
        outputSampleRate_.store(0, std::memory_order_release);
    }
}

void AudioEngine::releaseProducerStateLocked() noexcept {
    std::scoped_lock lock(decks_[0].producer.mutex, decks_[1].producer.mutex);

    for (auto& deck : decks_) {
        deck.producer.resampler.reset();
        deck.producer.resamplerInputRate = 0;
        deck.producer.resamplerPitch = 1.0F;
        deck.producer.resamplerFlushing = false;
        deck.control.resamplerDrained.store(true, std::memory_order_release);
        deck.producer.ring.reset();
        deck.producer.rawScratch.clear();
        deck.producer.stereoScratch.clear();
        deck.producer.resampledScratch.clear();

        deck.rt.ringScratch.clear();
        deck.rt.stretchedScratch.clear();
        deck.rt.stretcher.reset();
        deck.rt.appliedRatio = 1.0F;
        deck.rt.currentMixGain = 0.0F;
    }
    maxCallbackFrames_ = 0;
}

void AudioEngine::resetCallbackStateLocked() noexcept {
    underrunActive_ = false;
    recoveryFading_ = false;
    underrunFadePosition_ = fadeInCurve_.size();
    recoveryFadePosition_ = fadeInCurve_.size();
    prebuffering_ = true;
    prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
    lastOutput_.fill(0.0F);
    underrunAnchor_.fill(0.0F);
    cleanFrames_ = 0;
}

bool AudioEngine::configureDeckResamplerLocked(std::size_t deckIdx, std::int32_t inputSampleRate) {
    if (deckIdx >= kDeckCount) return false;
    auto& deck = decks_[deckIdx];

    const std::int32_t baseDestinationRate = outputSampleRate_.load(std::memory_order_acquire);
    if (baseDestinationRate <= 0) return false;

    const float pitch = deck.control.pitch.load(std::memory_order_acquire);
    const bool noPitchShift = std::abs(pitch - 1.0F) < kRatioEpsilon;
    const bool pitchChanged = std::abs(pitch - deck.producer.resamplerPitch) >= kRatioEpsilon;
    const bool rateChanged = deck.producer.resamplerInputRate != inputSampleRate;

    if (!deck.producer.resamplerFlushing && !rateChanged && !pitchChanged) {
        if (inputSampleRate == baseDestinationRate && noPitchShift) {
            deck.producer.resampler.reset();
            return true;
        }
        if (deck.producer.resampler != nullptr) return true;
    }

    deck.producer.resampler.reset();
    deck.producer.resamplerFlushing = false;
    deck.producer.resamplerInputRate = inputSampleRate;
    deck.producer.resamplerPitch = pitch;

    if (inputSampleRate == baseDestinationRate && noPitchShift) {
        return true;
    }

    // Divide by pitch so higher pitch produces fewer frames -> higher frequency at playback rate.
    const double targetOutRate =
        static_cast<double>(baseDestinationRate) / static_cast<double>(pitch);
    deck.producer.resampler.reset(createSoxrHandle(
        static_cast<double>(inputSampleRate),
        targetOutRate,
        kOutputChannels));
    if (deck.producer.resampler == nullptr) {
        deck.producer.resamplerInputRate = 0;
        return false;
    }
    return true;
}

void AudioEngine::buildFadeCurve(std::int32_t sampleRate) {
    const std::size_t fadeFrames = std::max<std::size_t>(
        1U,
        static_cast<std::size_t>(std::llround(static_cast<double>(sampleRate) * kFadeCurveSeconds)));
    fadeInCurve_.resize(fadeFrames);
    for (std::size_t frame = 0; frame < fadeFrames; ++frame) {
        const double phase = kPi * static_cast<double>(frame + 1U) /
            static_cast<double>(fadeFrames);
        fadeInCurve_[frame] = static_cast<float>(0.5 * (1.0 - std::cos(phase)));
    }
}

}  // namespace neonsong::audio
