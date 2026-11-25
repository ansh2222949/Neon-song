#pragma once

#include <cstddef>
#include <vector>

namespace neonsong::audio {

class WsolaTimeStretcher final {
public:
    static constexpr std::size_t kMaxChannels = 12;

    WsolaTimeStretcher() noexcept;
    void configure(double sampleRate, std::size_t channels = 2) noexcept;
    void reset() noexcept;

    void setTimeRatio(float ratio) noexcept; // e.g. 0.25 to 3.0 (1.0 = normal)
    void startRecoveryRamp(float targetRatio = 1.0F, float durationSeconds = 5.0F) noexcept;

    std::size_t process(
        const float* inputInterleaved,
        std::size_t inputFrames,
        float* outputInterleaved,
        std::size_t outputCapacityFrames) noexcept;

    std::size_t flush(float* outputInterleaved, std::size_t outputCapacityFrames) noexcept;

    [[nodiscard]] float currentRatio() const noexcept {
        return currentRatio_;
    }

private:
    double sampleRate_{48000.0};
    std::size_t channels_{2};

    float targetRatio_{1.0F};
    float currentRatio_{1.0F};
    bool rampActive_{false};
    float rampTarget_{1.0F};
    float rampStepPerHop_{0.0F};

    std::size_t windowSize_{1024};
    std::size_t synthesisHop_{512};
    std::size_t searchWindow_{256};
    std::size_t inputCapacityFrames_{1024 * 16};
    std::size_t outputCapacityFrames_{1024 * 16};

    std::vector<float> hannWindow_;
    std::vector<float> inputBuffer_;
    std::vector<float> prevTemplateMono_;
    std::vector<float> monoSearchScratch_;
    std::vector<float> outputBuffer_;

    std::size_t inputReadPos_{0};
    std::size_t inputWritePos_{0};
    std::size_t inputFramesAvailable_{0};
    std::size_t outputReadPos_{0};
    std::size_t outputFramesAvailable_{0};

    double nominalAnalysisPos_{0.0};
    float prevTemplateEnergy_{0.0F};
    float prevTemplateFullEnergy_{0.0F};
    bool hasPreviousTemplate_{false};
    bool tailPadded_{false};

    void updateRamp() noexcept;
    std::size_t findBestOffset(std::size_t nominalPos) noexcept;
    std::size_t appendInput(const float* inputInterleaved, std::size_t inputFrames) noexcept;
    void appendSilence(std::size_t frames) noexcept;
    void consumeInput(std::size_t frames) noexcept;
    float inputMonoMix(std::size_t frame) const noexcept;
    void synthesizePending(
        float* outputInterleaved,
        std::size_t& framesWritten,
        std::size_t outputCapacityFrames) noexcept;
    void overlapAddSegment(std::size_t bestPos, std::size_t synthesisStart) noexcept;
    void drainOutput(
        float* outputInterleaved,
        std::size_t& framesWritten,
        std::size_t outputCapacityFrames,
        std::size_t keepFrames = 0) noexcept;
};

} // namespace neonsong::audio
