#include "AudioEngine.h"

#include <soxr.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>

namespace neonsong::audio {
namespace {

constexpr double kPi = 3.1415926535897932384626433832795;

soxr_t asSoxr(void* handle) noexcept {
    return static_cast<soxr_t>(handle);
}

}  // namespace

AudioEngine::~AudioEngine() {
    stop();
    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    if (mediaResampler_ != nullptr) soxr_delete(asSoxr(mediaResampler_));
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

    // The engine never assumes a device accepted a configuration: everything
    // downstream keys off openedStream->getSampleRate(), read back below.
    const std::int32_t requestedRate = std::max(preferredOutputSampleRate, 0);
    requestedOutputSampleRate_.store(requestedRate, std::memory_order_release);
    // Streaming music values route stability over minimum round-trip latency.
    // Shared mode coexists reliably with Bluetooth, calls, accessibility and
    // OEM spatializers; exclusive mode is intentionally avoided. The default
    // performance mode (not LowLatency) matches Poweramp/VLC-class players:
    // music must survive aggressive OEM CPU throttling, and One UI's Dolby
    // Atmos/Sound Assistant effect chains behave best with regular buffers
    // instead of MMAP fast-track deadlines.
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
    if (sampleRate <= 0 || sampleRate > 384000 ||
        openedStream->getChannelCount() != kOutputChannels ||
        openedStream->getFormat() != oboe::AudioFormat::Float) {
        // A device reporting an unusable configuration must never be trusted
        // with audio: fail cleanly and let the caller pick its fallback path.
        openedStream->close();
        restartAllowed_.store(false, std::memory_order_release);
        return false;
    }
    // Oboe's OpenSL ES compatibility backend varies widely across older OEM
    // devices. Media3 AudioTrack is the safer music path there; retain Oboe for
    // its native AAudio backend and fall back cleanly otherwise.
    if (!openedStream->usesAAudio()) {
        openedStream->close();
        restartAllowed_.store(false, std::memory_order_release);
        return false;
    }
    streamOpenCount_.fetch_add(1, std::memory_order_release);
    if (requestedRate > 0 && sampleRate != requestedRate) {
        // The device silently substituted its own rate (common on Samsung
        // paths). Count it so diagnostics can distinguish "we asked for X and
        // got Y" from a genuine native-rate selection.
        rateAdaptationCount_.fetch_add(1, std::memory_order_release);
    }

    const auto framesPerBurst = std::max(openedStream->getFramesPerBurst(), 1);
    // A medium device buffer (~60 ms when capacity allows) absorbs OEM CPU
    // governor spikes â€” the primary source of One UI crackle. The ring
    // buffer in front of the device already provides gapless continuity, so
    // this costs no perceptible latency for a streaming music player.
    const auto targetDeviceBufferFrames = std::min(
        openedStream->getBufferCapacityInFrames(),
        std::max(
            framesPerBurst * 4,
            static_cast<std::int32_t>(
                static_cast<std::int64_t>(sampleRate) *
                kTargetDeviceBufferMilliseconds / 1000)));
    if (targetDeviceBufferFrames > 0) {
        (void) openedStream->setBufferSizeInFrames(targetDeviceBufferFrames);
    }

    {
        std::lock_guard<std::mutex> producerLock(producerMutex_);
        for (std::size_t d = 0; d < kDeckCount; ++d) {
            std::lock_guard<std::mutex> deckLock(deckProducerMutexes_[d]);
            activeDeckRings_[d].store(nullptr, std::memory_order_release);
        }
        activeRingBuffer_.store(nullptr, std::memory_order_release);
        releaseProducerStateLocked();
        const auto capacitySamples = static_cast<std::size_t>(sampleRate) *
            kRingMilliseconds * kOutputChannels / 1000U;
        ringBuffer_ = std::make_unique<LockFreeRingBuffer<float>>(capacitySamples);
        rawScratch_.resize(kProducerChunkFrames * kOutputChannels);
        stereoScratch_.resize(kProducerChunkFrames * kOutputChannels);
        resampledScratch_.resize(kMaxResampledFrames * kOutputChannels);

        for (std::size_t d = 0; d < kDeckCount; ++d) {
            deckRings_[d] = std::make_unique<LockFreeRingBuffer<float>>(capacitySamples);
            deckRawScratch_[d].resize(kProducerChunkFrames * kOutputChannels);
            deckStereoScratch_[d].resize(kProducerChunkFrames * kOutputChannels);
            deckResampledScratch_[d].resize(kMaxResampledFrames * kOutputChannels);
        }
        realtimeScratchA_.assign(kProducerChunkFrames * 4, 0.0F);
        realtimeScratchB_.assign(kProducerChunkFrames * 4, 0.0F);
        realtimeStretchedA_.assign(kProducerChunkFrames * 4, 0.0F);
        realtimeStretchedB_.assign(kProducerChunkFrames * 4, 0.0F);
        for (std::size_t d = 0; d < kDeckCount; ++d) {
            deckStretchers_[d].configure(static_cast<double>(sampleRate), kOutputChannels);
            deckStretchers_[d].setTimeRatio(1.0F);
            deckSpeed_[d].store(1.0F, std::memory_order_release);
            deckPitch_[d].store(1.0F, std::memory_order_release);
            deckTimeRatioTarget_[d].store(1.0F, std::memory_order_release);
            deckStretcherAppliedRatio_[d] = 1.0F;
            deckWsolaDrained_[d].store(true, std::memory_order_release);
            deckFlushTargetWrite_[d].store(0, std::memory_order_release);
        }
    }

    oboeDsp_.setPeakProtectionEnabled(true);
    oboeDsp_.configure(sampleRate);
    buildFadeCurve(sampleRate);
    underrunFadePosition_ = fadeInCurve_.size();
    recoveryFadePosition_ = fadeInCurve_.size();
    underrunActive_ = false;
    recoveryFading_ = false;
    prebuffering_ = true;
    prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
    prebufferFrames_ = std::min(
        static_cast<std::size_t>(sampleRate) * kRingMilliseconds / 2000U,
        std::max(
            static_cast<std::size_t>(framesPerBurst * 4),
            static_cast<std::size_t>(sampleRate) * kPrebufferMilliseconds / 1000U));
    prebufferBaseFrames_ = prebufferFrames_;
    underrunDecayIntervalFrames_ =
        static_cast<std::size_t>(sampleRate) * kUnderrunDecaySeconds;
    cleanFrames_ = 0;
    underrunAnchor_.fill(0.0F);
    lastOutput_.fill(0.0F);
    currentOutputVolume_ = std::clamp(
        targetOutputVolume_.load(std::memory_order_acquire), 0.0F, 1.0F);
    outputVolumeRampStep_ = 1.0F /
        std::max(1.0F, static_cast<float>(sampleRate) * 0.005F);

    outputSampleRate_.store(sampleRate, std::memory_order_release);
    activeRingBuffer_.store(ringBuffer_.get(), std::memory_order_release);
    activeDeckRings_[0].store(deckRings_[0].get(), std::memory_order_release);
    activeDeckRings_[1].store(deckRings_[1].get(), std::memory_order_release);
    stream_ = std::move(openedStream);
    if (stream_->requestStart() != oboe::Result::OK) {
        activeRingBuffer_.store(nullptr, std::memory_order_release);
        activeDeckRings_[0].store(nullptr, std::memory_order_release);
        activeDeckRings_[1].store(nullptr, std::memory_order_release);
        stream_->close();
        stream_.reset();
        outputSampleRate_.store(0, std::memory_order_release);
        std::lock_guard<std::mutex> producerLock(producerMutex_);
        releaseProducerStateLocked();
        restartAllowed_.store(false, std::memory_order_release);
        return false;
    }
    return true;
}

void AudioEngine::stop() noexcept {
    std::lock_guard<std::mutex> controlLock(controlMutex_);
    playbackEnabled_.store(false, std::memory_order_release);
    restartAllowed_.store(false, std::memory_order_release);
    activeRingBuffer_.store(nullptr, std::memory_order_release);
    activeDeckRings_[0].store(nullptr, std::memory_order_release);
    activeDeckRings_[1].store(nullptr, std::memory_order_release);
    if (stream_ != nullptr) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
    outputSampleRate_.store(0, std::memory_order_release);
    renderedFrames_.store(0, std::memory_order_release);
    std::lock_guard<std::mutex> producerLock(producerMutex_);
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
    flushDeck(0);
    flushDeck(1);
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

void AudioEngine::flushDeck(int deckId) noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return;
    const std::size_t idx = static_cast<std::size_t>(deckId);
    std::lock_guard<std::mutex> lock(deckProducerMutexes_[idx]);

    auto* ring = activeDeckRings_[idx].load(std::memory_order_relaxed);
    if (ring == nullptr) {
        ring = deckRings_[idx].get();
    }
    const std::uint64_t target = ring != nullptr ? ring->currentWriteIndex() : 0;
    deckFlushTargetWrite_[idx].store(target, std::memory_order_release);

    // Request consumer-owned SPSC ring buffer reset up to target
    deckFlushRequests_[idx].fetch_add(1, std::memory_order_release);

    if (deckResamplers_[idx] != nullptr) {
        soxr_clear(asSoxr(deckResamplers_[idx]));
    }
    deckResamplersFlushing_[idx] = false;
    deckResamplersDrained_[idx].store(true, std::memory_order_release);
    deckStretchers_[idx].reset();
    deckWsolaDrained_[idx].store(true, std::memory_order_release);
    deckFramesRendered_[idx].store(0, std::memory_order_release);

    // If stream is stopped or idle, consumer thread is not running, so clear directly
    if (!isRunning() && ring != nullptr) {
        ring->clearUpTo(target);
    }
}

std::size_t AudioEngine::deckBufferedFrames(int deckId) const noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return 0;
    const auto* ring = activeDeckRings_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
    return ring == nullptr ? 0 : ring->availableToRead() / kOutputChannels;
}

void AudioEngine::setDeckVolume(int deckId, float volume) noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        deckVolumes_[static_cast<std::size_t>(deckId)].store(
            std::clamp(volume, 0.0F, 1.0F), std::memory_order_release);
    }
}

float AudioEngine::deckVolume(int deckId) const noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        return deckVolumes_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
    }
    return 1.0F;
}

void AudioEngine::setCrossfadeProgress(float progress) noexcept {
    crossfadeProgress_.store(std::clamp(progress, 0.0F, 1.0F), std::memory_order_release);
}

float AudioEngine::crossfadeProgress() const noexcept {
    return crossfadeProgress_.load(std::memory_order_acquire);
}

void AudioEngine::setDeckPlaybackParameters(int deckId, float speed, float pitch) noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return;
    const std::size_t d = static_cast<std::size_t>(deckId);
    const float clampedSpeed = std::clamp(speed, 0.25F, 3.0F);
    const float clampedPitch = std::clamp(pitch, 0.25F, 3.0F);
    const float oldPitch = deckPitch_[d].load(std::memory_order_relaxed);

    deckSpeed_[d].store(clampedSpeed, std::memory_order_release);
    deckPitch_[d].store(clampedPitch, std::memory_order_release);

    // WSOLA stretch ratio = speed / pitch
    // If pitch increases by 1.25x, resampler makes duration 0.8x.
    // WSOLA time-stretches by (speed / pitch). When speed=1.0, WSOLA ratio is 0.8x,
    // which stretches duration back to 1.0x, keeping duration unchanged while pitch is 1.25x!
    const float wsolaRatio = std::clamp(clampedSpeed / clampedPitch, 0.25F, 3.0F);
    deckTimeRatioTarget_[d].store(wsolaRatio, std::memory_order_release);

    // If pitch changed, reconfigure deck resampler on next write
    if (std::abs(clampedPitch - oldPitch) > 0.001F) {
        std::lock_guard<std::mutex> lock(deckProducerMutexes_[d]);
        const auto inRate = deckResamplerInputRates_[d];
        if (inRate > 0) {
            deckResamplerInputRates_[d] = 0; // Force re-creation with new pitch target
            (void) configureDeckResamplerLocked(deckId, inRate);
        }
    }
}

float AudioEngine::deckSpeed(int deckId) const noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        return deckSpeed_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
    }
    return 1.0F;
}

float AudioEngine::deckPitch(int deckId) const noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        return deckPitch_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
    }
    return 1.0F;
}

void AudioEngine::setDeckTimeRatio(int deckId, float ratio) noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        const float clamped = std::clamp(ratio, 0.25F, 3.0F);
        deckTimeRatioTarget_[static_cast<std::size_t>(deckId)].store(clamped, std::memory_order_release);
        deckSpeed_[static_cast<std::size_t>(deckId)].store(clamped, std::memory_order_release);
    }
}

float AudioEngine::deckTimeRatio(int deckId) const noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        return deckTimeRatioTarget_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
    }
    return 1.0F;
}

void AudioEngine::setDeckPlaying(int deckId, bool playing) noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return;
    deckPlaying_[static_cast<std::size_t>(deckId)].store(playing, std::memory_order_release);
    const bool anyPlaying = deckPlaying_[0].load(std::memory_order_acquire) ||
                            deckPlaying_[1].load(std::memory_order_acquire);
    playbackEnabled_.store(anyPlaying, std::memory_order_release);
}

bool AudioEngine::isDeckPlaying(int deckId) const noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return false;
    return deckPlaying_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
}

void AudioEngine::drainDeckResampler(int deckId) noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return;
    const std::size_t d = static_cast<std::size_t>(deckId);
    std::lock_guard<std::mutex> lock(deckProducerMutexes_[d]);
    auto* ring = activeDeckRings_[d].load(std::memory_order_acquire);
    if (ring == nullptr || deckResamplers_[d] == nullptr) {
        deckResamplersDrained_[d].store(true, std::memory_order_release);
        return;
    }
    deckResamplersFlushing_[d] = true;
    while (true) {
        const std::size_t availableOutputFrames = std::min(
            kMaxResampledFrames,
            ring->availableToWrite() / kOutputChannels);
        if (availableOutputFrames == 0) break;

        std::size_t outputDone = 0;
        const soxr_error_t error = soxr_process(
            asSoxr(deckResamplers_[d]),
            nullptr,
            0,
            nullptr,
            deckResampledScratch_[d].data(),
            availableOutputFrames,
            &outputDone);
        if (error != nullptr || outputDone == 0) {
            deckResamplersDrained_[d].store(true, std::memory_order_release);
            break;
        }
        const std::size_t writtenSamples = ring->write(
            deckResampledScratch_[d].data(),
            outputDone * kOutputChannels);
        if (writtenSamples != outputDone * kOutputChannels) {
            break;
        }
    }
}

bool AudioEngine::isDeckResamplerDrained(int deckId) const noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return true;
    return deckResamplersDrained_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
}

bool AudioEngine::isDeckWsolaDrained(int deckId) const noexcept {
    if (deckId >= 0 && deckId < static_cast<int>(kDeckCount)) {
        return deckWsolaDrained_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
    }
    return true;
}

std::uint64_t AudioEngine::deckFramesRendered(int deckId) const noexcept {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return 0;
    return deckFramesRendered_[static_cast<std::size_t>(deckId)].load(std::memory_order_acquire);
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
    const auto result = writeDeckPcm(0, pcm, frameCount, format, inputSampleRate, inputChannelCount);
    return result > 0 ? static_cast<std::size_t>(result) : 0U;
}

std::int64_t AudioEngine::writeDeckPcm(
    int deckId,
    const void* pcm,
    std::size_t frameCount,
    PcmFormat format,
    std::int32_t inputSampleRate,
    std::int32_t inputChannelCount) {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) {
        return -4; // Invalid deck
    }
    if (pcm == nullptr || PcmConverter::bytesPerSample(format) == 0) {
        return -1; // Invalid buffer
    }
    if (frameCount == 0) {
        return 0;
    }
    if (inputSampleRate <= 0 || (inputChannelCount != 1 && inputChannelCount != kOutputChannels)) {
        return -2; // Invalid format
    }

    const std::size_t d = static_cast<std::size_t>(deckId);
    std::lock_guard<std::mutex> producerLock(deckProducerMutexes_[d]);
    auto* ring = activeDeckRings_[d].load(std::memory_order_acquire);
    const std::int32_t destinationRate = outputSampleRate_.load(std::memory_order_acquire);
    if (ring == nullptr || destinationRate <= 0) {
        return -3; // Engine not started / unavailable
    }
    if (!configureDeckResamplerLocked(deckId, inputSampleRate)) {
        return -2; // Resampler configuration failed
    }
    inputAlreadyProcessed_.store(false, std::memory_order_release);
    deckWsolaDrained_[d].store(false, std::memory_order_release);

    const auto* inputBytes = static_cast<const std::uint8_t*>(pcm);
    const std::size_t bytesPerFrame = PcmConverter::bytesPerSample(format) *
        static_cast<std::size_t>(inputChannelCount);
    const float pitch = deckPitch_[d].load(std::memory_order_relaxed);
    const double ratio = (static_cast<double>(destinationRate) * static_cast<double>(pitch)) / inputSampleRate;
    const std::size_t ratioSafeChunk = std::max<std::size_t>(
        1,
        static_cast<std::size_t>((kMaxResampledFrames - 256) /
            std::max(1.0, ratio)));

    std::size_t consumedFrames = 0;
    while (consumedFrames < frameCount) {
        const std::size_t chunkFrames = std::min({
            frameCount - consumedFrames,
            kProducerChunkFrames,
            ratioSafeChunk,
        });
        const void* chunkInput = inputBytes + consumedFrames * bytesPerFrame;
        const std::size_t inputSamples = chunkFrames *
            static_cast<std::size_t>(inputChannelCount);
        if (!PcmConverter::toFloat(chunkInput, inputSamples, format, deckRawScratch_[d].data())) {
            break;
        }

        if (inputChannelCount == 1) {
            for (std::size_t frame = 0; frame < chunkFrames; ++frame) {
                deckStereoScratch_[d][frame * 2U] = deckRawScratch_[d][frame];
                deckStereoScratch_[d][frame * 2U + 1U] = deckRawScratch_[d][frame];
            }
        } else {
            std::memcpy(
                deckStereoScratch_[d].data(),
                deckRawScratch_[d].data(),
                chunkFrames * kOutputChannels * sizeof(float));
        }

        if (deckResamplers_[d] == nullptr) {
            const std::size_t writtenSamples = ring->write(
                deckStereoScratch_[d].data(),
                chunkFrames * kOutputChannels);
            const std::size_t writtenFrames = writtenSamples / kOutputChannels;
            consumedFrames += writtenFrames;
            if (writtenFrames != chunkFrames) break;
            continue;
        }

        const std::size_t availableOutputFrames = std::min(
            kMaxResampledFrames,
            ring->availableToWrite() / kOutputChannels);
        if (availableOutputFrames == 0) break;

        std::size_t inputDone = 0;
        std::size_t outputDone = 0;
        const soxr_error_t error = soxr_process(
            asSoxr(deckResamplers_[d]),
            deckStereoScratch_[d].data(),
            chunkFrames,
            &inputDone,
            deckResampledScratch_[d].data(),
            availableOutputFrames,
            &outputDone);
        if (error != nullptr) break;

        const std::size_t writtenSamples = ring->write(
            deckResampledScratch_[d].data(),
            outputDone * kOutputChannels);
        if (writtenSamples != outputDone * kOutputChannels) break;
        consumedFrames += inputDone;
        if (inputDone == 0) break;
    }
    if (consumedFrames > 0) {
        deckResamplersDrained_[d].store(false, std::memory_order_release);
    }
    deckFramesWritten_[d].fetch_add(consumedFrames, std::memory_order_release);
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

    std::lock_guard<std::mutex> producerLock(producerMutex_);
    auto* ring = activeRingBuffer_.load(std::memory_order_acquire);
    const auto destinationRate = outputSampleRate_.load(std::memory_order_acquire);
    if (ring == nullptr || inputSampleRate != destinationRate) return 0;
    inputAlreadyProcessed_.store(true, std::memory_order_release);

    const std::size_t writableFrames = std::min(
        frameCount,
        ring->availableToWrite() / kOutputChannels);
    if (writableFrames == 0) return 0;
    if (inputChannelCount == kOutputChannels) {
        return ring->write(pcm, writableFrames * kOutputChannels) / kOutputChannels;
    }

    std::size_t writtenFrames = 0;
    while (writtenFrames < writableFrames) {
        const std::size_t chunkFrames = std::min(
            writableFrames - writtenFrames,
            kProducerChunkFrames);
        for (std::size_t frame = 0; frame < chunkFrames; ++frame) {
            const float sample = pcm[writtenFrames + frame];
            stereoScratch_[frame * kOutputChannels] = sample;
            stereoScratch_[frame * kOutputChannels + 1U] = sample;
        }
        const std::size_t writtenSamples = ring->write(
            stereoScratch_.data(),
            chunkFrames * kOutputChannels);
        const std::size_t chunkWrittenFrames = writtenSamples / kOutputChannels;
        writtenFrames += chunkWrittenFrames;
        if (chunkWrittenFrames != chunkFrames) break;
    }
    return writtenFrames;
}

void AudioEngine::setPlaying(bool playing) noexcept {
    playbackEnabled_.store(playing, std::memory_order_release);
}

void AudioEngine::setOutputVolume(float volume) noexcept {
    if (!std::isfinite(volume)) volume = 1.0F;
    targetOutputVolume_.store(std::clamp(volume, 0.0F, 1.0F), std::memory_order_release);
}

void AudioEngine::flushResampler() {
    std::lock_guard<std::mutex> producerLock(producerMutex_);
    auto* ring = activeRingBuffer_.load(std::memory_order_acquire);
    if (ring == nullptr || resampler_ == nullptr) return;
    resamplerFlushing_ = true;

    while (true) {
        const std::size_t outputCapacity = std::min(
            kMaxResampledFrames,
            ring->availableToWrite() / kOutputChannels);
        if (outputCapacity == 0) return;
        std::size_t outputDone = 0;
        const soxr_error_t error = soxr_process(
            asSoxr(resampler_),
            nullptr,
            0,
            nullptr,
            resampledScratch_.data(),
            outputCapacity,
            &outputDone);
        if (error != nullptr || outputDone == 0) {
            soxr_delete(asSoxr(resampler_));
            resampler_ = nullptr;
            resamplerInputRate_ = 0;
            resamplerFlushing_ = false;
            return;
        }
        if (ring->write(resampledScratch_.data(), outputDone * kOutputChannels) !=
            outputDone * kOutputChannels) {
            return;
        }
    }
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

void AudioEngine::setBitPerfect(bool enabled) noexcept {
    oboeDsp_.setBitPerfect(enabled);
    mediaDsp_.setBitPerfect(enabled);
}

bool AudioEngine::isBitPerfect() const noexcept {
    return oboeDsp_.isBitPerfectEnabled() && mediaDsp_.isBitPerfectEnabled();
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
    if (inputSampleRate <= 0 || outputSampleRate <= 0 ||
        (channelCount != 1 && channelCount != 2)) {
        return false;
    }
    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    if (mediaResampler_ != nullptr) {
        soxr_delete(asSoxr(mediaResampler_));
        mediaResampler_ = nullptr;
    }
    mediaInputSampleRate_ = inputSampleRate;
    mediaOutputSampleRate_ = outputSampleRate;
    mediaChannelCount_ = channelCount;
    mediaScratch_.resize(kProducerChunkFrames * static_cast<std::size_t>(channelCount));
    // Resample first, then run tone/peak processing at the actual output rate
    // so sinc overshoot is included in final peak protection.
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
    if (pcm == nullptr || output == nullptr || frameCount == 0 ||
        outputCapacityFrames == 0) {
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

    if (mediaResampler_ == nullptr && mediaInputSampleRate_ == mediaOutputSampleRate_) {
        if (outputCapacityFrames < frameCount ||
            !PcmConverter::toFloat(pcm, frameCount * channels, format, output)) {
            return false;
        }
        mediaDsp_.process(output, static_cast<std::int32_t>(frameCount), mediaChannelCount_);
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
            std::size_t inputDone = 0;
            std::size_t outputDone = 0;
            const std::size_t remainingOutput = outputCapacityFrames - outputFrameCount;
            if (remainingOutput == 0) break;
            const soxr_error_t error = soxr_process(
                asSoxr(mediaResampler_),
                mediaScratch_.data() + chunkConsumed * channels,
                chunkFrames - chunkConsumed,
                &inputDone,
                output + outputFrameCount * channels,
                remainingOutput,
                &outputDone);
            if (error != nullptr) return false;
            if (outputDone > 0) {
                mediaDsp_.process(
                    output + outputFrameCount * channels,
                    static_cast<std::int32_t>(outputDone),
                    mediaChannelCount_);
                outputFrameCount += outputDone;
            }
            if (inputDone == 0 && outputDone == 0) {
                // Soxr completed buffering for this pass
                break;
            }
            chunkConsumed += inputDone;
            consumedFrames += inputDone;
        }
        if (chunkConsumed < chunkFrames && outputCapacityFrames == outputFrameCount) {
            break;
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
    while (outputFrameCount < outputCapacityFrames) {
        std::size_t outputDone = 0;
        const soxr_error_t error = soxr_process(
            asSoxr(mediaResampler_),
            nullptr,
            0,
            nullptr,
            output + outputFrameCount * channels,
            outputCapacityFrames - outputFrameCount,
            &outputDone);
        if (error != nullptr) return false;
        mediaDsp_.process(
            output + outputFrameCount * channels,
            static_cast<std::int32_t>(outputDone),
            mediaChannelCount_);
        outputFrameCount += outputDone;
        if (outputDone == 0) break;
    }
    return true;
}

void AudioEngine::resetMediaProcessor() {
    std::lock_guard<std::mutex> processorLock(mediaProcessorMutex_);
    mediaDsp_.reset();
    if (mediaResampler_ != nullptr) {
        soxr_delete(asSoxr(mediaResampler_));
        mediaResampler_ = nullptr;
    }
    (void) configureMediaResamplerLocked();
}

bool AudioEngine::configureMediaResamplerLocked() {
    if (mediaInputSampleRate_ <= 0 || mediaOutputSampleRate_ <= 0 ||
        (mediaChannelCount_ != 1 && mediaChannelCount_ != 2)) {
        return false;
    }
    if (mediaInputSampleRate_ == mediaOutputSampleRate_) return true;
    soxr_error_t error = nullptr;
    const soxr_io_spec_t ioSpec = soxr_io_spec(SOXR_FLOAT32_I, SOXR_FLOAT32_I);
    // HQ is transparent for playback while avoiding the large CPU spikes of
    // VHQ on low-end ARM cores. VHQ's extra offline-grade precision is not
    // worth real-time underruns on mobile hardware.
    const soxr_quality_spec_t qualitySpec = soxr_quality_spec(SOXR_HQ, 0);
    const soxr_runtime_spec_t runtimeSpec = soxr_runtime_spec(1);
    mediaResampler_ = soxr_create(
        static_cast<double>(mediaInputSampleRate_),
        static_cast<double>(mediaOutputSampleRate_),
        static_cast<unsigned>(mediaChannelCount_),
        &error,
        &ioSpec,
        &qualitySpec,
        &runtimeSpec);
    if (error != nullptr || mediaResampler_ == nullptr) {
        if (mediaResampler_ != nullptr) soxr_delete(asSoxr(mediaResampler_));
        mediaResampler_ = nullptr;
        return false;
    }
    return true;
}

std::size_t AudioEngine::bufferedFrames() const noexcept {
    return deckBufferedFrames(0) + deckBufferedFrames(1);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* /* audioStream */,
    void* audioData,
    std::int32_t numFrames) {
    auto* output = static_cast<float*>(audioData);
    auto* ringA = activeDeckRings_[0].load(std::memory_order_acquire);
    auto* ringB = activeDeckRings_[1].load(std::memory_order_acquire);
    if (output == nullptr || numFrames <= 0) return oboe::DataCallbackResult::Continue;

    const std::size_t requestedFrames = static_cast<std::size_t>(numFrames);
    if (ringA == nullptr && ringB == nullptr) {
        std::memset(output, 0, requestedFrames * kOutputChannels * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }

    // Process consumer-owned SPSC ring buffer flush requests
    for (std::size_t d = 0; d < kDeckCount; ++d) {
        const std::uint64_t req = deckFlushRequests_[d].load(std::memory_order_acquire);
        if (req != deckFlushAcks_[d]) {
            auto* ring = activeDeckRings_[d].load(std::memory_order_relaxed);
            if (ring != nullptr) {
                ring->clearUpTo(deckFlushTargetWrite_[d].load(std::memory_order_acquire));
            }
            deckStretchers_[d].reset();
            deckWsolaDrained_[d].store(true, std::memory_order_release);
            deckFlushAcks_[d] = req;
        }

        const float ratioTarget = deckTimeRatioTarget_[d].load(std::memory_order_acquire);
        if (std::abs(ratioTarget - deckStretcherAppliedRatio_[d]) > 0.0001F) {
            deckStretchers_[d].setTimeRatio(ratioTarget);
            deckStretcherAppliedRatio_[d] = ratioTarget;
        }
    }

    const bool playingNow = playbackEnabled_.load(std::memory_order_acquire);
    const bool deckAPlaying = deckPlaying_[0].load(std::memory_order_acquire);
    const bool deckBPlaying = deckPlaying_[1].load(std::memory_order_acquire);

    // Smooth pause-tail draining per deck
    const float pauseStep = outputVolumeRampStep_ * 2.0F;
    if (deckAPlaying) {
        deckPauseGain_[0] = std::min(1.0F, deckPauseGain_[0] + pauseStep);
    } else {
        deckPauseGain_[0] = std::max(0.0F, deckPauseGain_[0] - pauseStep);
    }

    if (deckBPlaying) {
        deckPauseGain_[1] = std::min(1.0F, deckPauseGain_[1] + pauseStep);
    } else {
        deckPauseGain_[1] = std::max(0.0F, deckPauseGain_[1] - pauseStep);
    }

    const bool anyDeckActive = deckAPlaying || deckBPlaying ||
        deckPauseGain_[0] > 0.0F || deckPauseGain_[1] > 0.0F;

    const std::size_t availA = (ringA != nullptr) ? (ringA->availableToRead() / kOutputChannels) : 0;
    const std::size_t availB = (ringB != nullptr) ? (ringB->availableToRead() / kOutputChannels) : 0;
    const std::size_t availableFrames = availA + availB;

    const bool drainingPauseTail =
        !playingNow && currentOutputVolume_ > 0.0F && availableFrames > 0;
    if ((!playingNow && !drainingPauseTail) || !anyDeckActive) {
        std::memset(output, 0, requestedFrames * kOutputChannels * sizeof(float));
        underrunActive_ = false;
        recoveryFading_ = false;
        prebuffering_ = true;
        prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
        lastOutput_.fill(0.0F);
        return oboe::DataCallbackResult::Continue;
    }

    if (prebuffering_ && playingNow &&
        (availableFrames == 0 ||
            (availableFrames < prebufferFrames_ && prebufferWaitCallbacks_-- > 0))) {
        std::memset(output, 0, requestedFrames * kOutputChannels * sizeof(float));
        return oboe::DataCallbackResult::Continue;
    }
    if (prebuffering_) {
        prebuffering_ = false;
        recoveryFading_ = true;
        recoveryFadePosition_ = 0;
    }

    std::size_t framesA = 0;
    if ((deckAPlaying || deckPauseGain_[0] > 0.0F) && ringA != nullptr) {
        const std::size_t readA = ringA->read(realtimeScratchA_.data(), requestedFrames * kOutputChannels);
        framesA = readA / kOutputChannels;
        deckFramesRendered_[0].fetch_add(framesA, std::memory_order_release);
    }

    std::size_t framesB = 0;
    if ((deckBPlaying || deckPauseGain_[1] > 0.0F) && ringB != nullptr) {
        const std::size_t readB = ringB->read(realtimeScratchB_.data(), requestedFrames * kOutputChannels);
        framesB = readB / kOutputChannels;
        deckFramesRendered_[1].fetch_add(framesB, std::memory_order_release);
    }

    // Apply WSOLA time stretching to Deck A if ratio != 1.0
    const float* aSamples = realtimeScratchA_.data();
    std::size_t activeFramesA = framesA;
    if (std::abs(deckStretcherAppliedRatio_[0] - 1.0F) > 0.001F) {
        if (framesA > 0) {
            const std::size_t stretched = deckStretchers_[0].process(
                realtimeScratchA_.data(),
                framesA,
                realtimeStretchedA_.data(),
                requestedFrames);
            aSamples = realtimeStretchedA_.data();
            activeFramesA = stretched;
            deckWsolaDrained_[0].store(false, std::memory_order_release);
        } else if (deckResamplersDrained_[0].load(std::memory_order_relaxed) &&
                   (deckAPlaying || deckPauseGain_[0] > 0.0F)) {
            const std::size_t flushed = deckStretchers_[0].flush(
                realtimeStretchedA_.data(),
                requestedFrames);
            if (flushed > 0) {
                aSamples = realtimeStretchedA_.data();
                activeFramesA = flushed;
                deckFramesRendered_[0].fetch_add(activeFramesA, std::memory_order_release);
                deckWsolaDrained_[0].store(false, std::memory_order_release);
            } else {
                deckWsolaDrained_[0].store(true, std::memory_order_release);
            }
        }
    } else {
        deckWsolaDrained_[0].store(true, std::memory_order_release);
    }

    // Apply WSOLA time stretching to Deck B if ratio != 1.0
    const float* bSamples = realtimeScratchB_.data();
    std::size_t activeFramesB = framesB;
    if (std::abs(deckStretcherAppliedRatio_[1] - 1.0F) > 0.001F) {
        if (framesB > 0) {
            const std::size_t stretched = deckStretchers_[1].process(
                realtimeScratchB_.data(),
                framesB,
                realtimeStretchedB_.data(),
                requestedFrames);
            bSamples = realtimeStretchedB_.data();
            activeFramesB = stretched;
            deckWsolaDrained_[1].store(false, std::memory_order_release);
        } else if (deckResamplersDrained_[1].load(std::memory_order_relaxed) &&
                   (deckBPlaying || deckPauseGain_[1] > 0.0F)) {
            // Resampler is drained and ring is empty: flush WSOLA tail
            const std::size_t flushed = deckStretchers_[1].flush(
                realtimeStretchedB_.data(),
                requestedFrames);
            if (flushed > 0) {
                bSamples = realtimeStretchedB_.data();
                activeFramesB = flushed;
                deckFramesRendered_[1].fetch_add(activeFramesB, std::memory_order_release);
                deckWsolaDrained_[1].store(false, std::memory_order_release);
            } else {
                deckWsolaDrained_[1].store(true, std::memory_order_release);
            }
        }
    } else {
        deckWsolaDrained_[1].store(true, std::memory_order_release);
    }

    const std::size_t validFrames = std::max(activeFramesA, activeFramesB);
    const bool wasUnderrun = underrunActive_;

    if (validFrames > 0) {
        const float xfade = crossfadeProgress_.load(std::memory_order_relaxed);
        // Equal-power crossfade curve with per-deck volume and pause gain
        const float gainA = std::cos(xfade * 1.57079632679F) *
            deckVolumes_[0].load(std::memory_order_relaxed) * deckPauseGain_[0];
        const float gainB = std::sin(xfade * 1.57079632679F) *
            deckVolumes_[1].load(std::memory_order_relaxed) * deckPauseGain_[1];

        for (std::size_t f = 0; f < validFrames; ++f) {
            const std::size_t off = f * kOutputChannels;
            const float sA_0 = (f < activeFramesA) ? aSamples[off] : 0.0F;
            const float sA_1 = (f < activeFramesA) ? aSamples[off + 1U] : 0.0F;
            const float sB_0 = (f < activeFramesB) ? bSamples[off] : 0.0F;
            const float sB_1 = (f < activeFramesB) ? bSamples[off + 1U] : 0.0F;

            output[off]      = sA_0 * gainA + sB_0 * gainB;
            output[off + 1U] = sA_1 * gainA + sB_1 * gainB;
        }

        if (!inputAlreadyProcessed_.load(std::memory_order_acquire)) {
            oboeDsp_.process(output, static_cast<std::int32_t>(validFrames));
        }
        if (wasUnderrun) {
            recoveryFading_ = true;
            recoveryFadePosition_ = 0;
        }

        const bool outputBitPerfect = oboeDsp_.isBitPerfectEnabled();
        for (std::size_t frame = 0; frame < validFrames; ++frame) {
            float recoveryGain = 1.0F;
            if (!outputBitPerfect && recoveryFading_) {
                if (recoveryFadePosition_ < fadeInCurve_.size()) {
                    recoveryGain = fadeInCurve_[recoveryFadePosition_++];
                } else {
                    recoveryFading_ = false;
                }
            }
            const std::size_t offset = frame * kOutputChannels;
            const float volumeTarget = playingNow
                ? targetOutputVolume_.load(std::memory_order_relaxed)
                : 0.0F;
            const float volumeDelta = volumeTarget - currentOutputVolume_;
            currentOutputVolume_ += std::clamp(
                volumeDelta,
                -outputVolumeRampStep_,
                outputVolumeRampStep_);
            const float gain = outputBitPerfect
                ? 1.0F
                : recoveryGain * currentOutputVolume_;
            output[offset] *= gain;
            output[offset + 1U] *= gain;
        }
        lastOutput_[0] = output[(validFrames - 1U) * kOutputChannels];
        lastOutput_[1] = output[(validFrames - 1U) * kOutputChannels + 1U];
        renderedFrames_.fetch_add(validFrames, std::memory_order_release);
    }

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
                    ? 1.0F - fadeInCurve_[underrunFadePosition_++]
                    : 0.0F;
                const std::size_t offset = frame * kOutputChannels;
                output[offset] = underrunAnchor_[0] * fadeOut;
                output[offset + 1U] = underrunAnchor_[1] * fadeOut;
            }
            underrunActive_ = true;
            prebuffering_ = true;
            prebufferWaitCallbacks_ = kPrebufferMaxCallbacks;
            cleanFrames_ = 0;
            const auto capacityFrames = (ringA != nullptr)
                ? (ringA->capacity() / kOutputChannels)
                : (ringB != nullptr ? ringB->capacity() / kOutputChannels : 0);
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
    } else {
        underrunActive_ = false;
        cleanFrames_ += requestedFrames;
        while (cleanFrames_ >= underrunDecayIntervalFrames_) {
            cleanFrames_ -= underrunDecayIntervalFrames_;
            if (prebufferFrames_ > prebufferBaseFrames_) {
                prebufferFrames_ = std::max(
                    prebufferBaseFrames_, prebufferFrames_ / 2U);
            }
            auto current = underrunCount_.load(std::memory_order_acquire);
            if (current == 0) break;
            while (current > 0 && !underrunCount_.compare_exchange_weak(
                current, current - 1, std::memory_order_release)) {
            }
        }
    }

    return oboe::DataCallbackResult::Continue;
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
    activeRingBuffer_.store(nullptr, std::memory_order_release);
    activeDeckRings_[0].store(nullptr, std::memory_order_release);
    activeDeckRings_[1].store(nullptr, std::memory_order_release);
    stream_.reset();
    outputSampleRate_.store(0, std::memory_order_release);
    {
        std::lock_guard<std::mutex> producerLock(producerMutex_);
        releaseProducerStateLocked();
    }
    try {
        (void) startLocked(requestedOutputSampleRate_.load(std::memory_order_acquire));
    } catch (...) {
        restartAllowed_.store(false, std::memory_order_release);
        activeRingBuffer_.store(nullptr, std::memory_order_release);
        activeDeckRings_[0].store(nullptr, std::memory_order_release);
        activeDeckRings_[1].store(nullptr, std::memory_order_release);
        outputSampleRate_.store(0, std::memory_order_release);
    }
}

void AudioEngine::releaseProducerStateLocked() noexcept {
    // Synchronize against any active deck producer operations
    std::scoped_lock lock(deckProducerMutexes_[0], deckProducerMutexes_[1]);

    if (resampler_ != nullptr) {
        soxr_delete(asSoxr(resampler_));
        resampler_ = nullptr;
    }
    resamplerInputRate_ = 0;
    resamplerFlushing_ = false;
    ringBuffer_.reset();
    rawScratch_.clear();
    stereoScratch_.clear();
    resampledScratch_.clear();

    for (std::size_t d = 0; d < kDeckCount; ++d) {
        if (deckResamplers_[d] != nullptr) {
            soxr_delete(asSoxr(deckResamplers_[d]));
            deckResamplers_[d] = nullptr;
        }
        deckResamplerInputRates_[d] = 0;
        deckResamplersFlushing_[d] = false;
        deckResamplersDrained_[d].store(true, std::memory_order_release);
        deckRings_[d].reset();
        deckRawScratch_[d].clear();
        deckStereoScratch_[d].clear();
        deckResampledScratch_[d].clear();
        deckPlaying_[d].store(false, std::memory_order_release);
        deckPauseGain_[d] = 1.0F;
    }
    realtimeScratchA_.clear();
    realtimeScratchB_.clear();
    realtimeStretchedA_.clear();
    realtimeStretchedB_.clear();
    for (std::size_t d = 0; d < kDeckCount; ++d) {
        deckStretchers_[d].reset();
        deckStretcherAppliedRatio_[d] = 1.0F;
        deckTimeRatioTarget_[d].store(1.0F, std::memory_order_release);
        deckSpeed_[d].store(1.0F, std::memory_order_release);
        deckPitch_[d].store(1.0F, std::memory_order_release);
    }
}

bool AudioEngine::configureResamplerLocked(std::int32_t inputSampleRate) {
    return configureDeckResamplerLocked(0, inputSampleRate);
}

bool AudioEngine::configureDeckResamplerLocked(int deckId, std::int32_t inputSampleRate) {
    if (deckId < 0 || deckId >= static_cast<int>(kDeckCount)) return false;
    const std::size_t d = static_cast<std::size_t>(deckId);
    const std::int32_t baseDestinationRate = outputSampleRate_.load(std::memory_order_acquire);
    if (baseDestinationRate <= 0) return false;

    const float pitch = deckPitch_[d].load(std::memory_order_relaxed);
    const double targetOutRate = static_cast<double>(baseDestinationRate) * static_cast<double>(pitch);

    if (deckResamplersFlushing_[d]) {
        if (deckResamplers_[d] != nullptr) soxr_delete(asSoxr(deckResamplers_[d]));
        deckResamplers_[d] = nullptr;
        deckResamplerInputRates_[d] = 0;
        deckResamplersFlushing_[d] = false;
    }
    const bool noPitchShift = std::abs(pitch - 1.0F) < 0.001F;
    if (deckResamplerInputRates_[d] == inputSampleRate) {
        if (inputSampleRate == baseDestinationRate && noPitchShift) {
            if (deckResamplers_[d] != nullptr) {
                soxr_delete(asSoxr(deckResamplers_[d]));
                deckResamplers_[d] = nullptr;
            }
            return true;
        }
        if (deckResamplers_[d] != nullptr) return true;
    }

    if (deckResamplers_[d] != nullptr) {
        soxr_delete(asSoxr(deckResamplers_[d]));
        deckResamplers_[d] = nullptr;
    }
    deckResamplerInputRates_[d] = inputSampleRate;
    if (inputSampleRate == baseDestinationRate && noPitchShift) return true;

    soxr_error_t error = nullptr;
    const soxr_io_spec_t ioSpec = soxr_io_spec(SOXR_FLOAT32_I, SOXR_FLOAT32_I);
    const soxr_quality_spec_t qualitySpec = soxr_quality_spec(SOXR_HQ, 0);
    const soxr_runtime_spec_t runtimeSpec = soxr_runtime_spec(1);
    deckResamplers_[d] = soxr_create(
        static_cast<double>(inputSampleRate),
        targetOutRate,
        kOutputChannels,
        &error,
        &ioSpec,
        &qualitySpec,
        &runtimeSpec);
    if (error != nullptr || deckResamplers_[d] == nullptr) {
        if (deckResamplers_[d] != nullptr) soxr_delete(asSoxr(deckResamplers_[d]));
        deckResamplers_[d] = nullptr;
        deckResamplerInputRates_[d] = 0;
        return false;
    }
    return true;
}

void AudioEngine::buildFadeCurve(std::int32_t sampleRate) {
    const std::size_t fadeFrames = std::max<std::size_t>(
        1,
        static_cast<std::size_t>(std::llround(sampleRate * 0.002)));
    fadeInCurve_.resize(fadeFrames);
    for (std::size_t frame = 0; frame < fadeFrames; ++frame) {
        const double phase = kPi * static_cast<double>(frame + 1U) /
            static_cast<double>(fadeFrames);
        fadeInCurve_[frame] = static_cast<float>(0.5 * (1.0 - std::cos(phase)));
    }
}

}  // namespace neonsong::audio
