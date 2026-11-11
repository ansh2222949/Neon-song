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

    static constexpr std::size_t kWindowSize = 1024;
    static constexpr std::size_t kSynthesisHop = 512;
    static constexpr std::size_t kSearchWindow = 192; // +/- 192 frames symmetric search range
    static constexpr std::size_t kInputCapacityFrames = kWindowSize * 16;
    static constexpr std::size_t kOutputCapacityFrames = kWindowSize * 16;

    std::vector<float> hannWindow_;
    std::vector<float> inputBuffer_;
    std::vector<float> prevTemplateMono_; // Mono/Mid template for fast NCC matching
    std::vector<float> monoSearchScratch_; // Linearized mono search slice for O(1) sliding energy
    std::vector<float> outputBuffer_;

    std::size_t inputReadPos_{0};
    std::size_t inputWritePos_{0};
    std::size_t inputFramesAvailable_{0};
    std::size_t outputReadPos_{0};
    std::size_t outputWritePos_{0};
    std::size_t outputFramesAvailable_{0};

    double nominalAnalysisPos_{static_cast<double>(kSearchWindow)};
    float prevTemplateEnergy_{0.0F};
    bool hasPreviousTemplate_{false};

    void updateRamp() noexcept;
    std::size_t findBestOffset(std::size_t nominalPos) noexcept;
    void appendInput(const float* inputInterleaved, std::size_t inputFrames) noexcept;
    void consumeInput(std::size_t frames) noexcept;
    float inputSample(std::size_t frame, std::size_t channel) const noexcept;
    float inputMonoMix(std::size_t frame) const noexcept;
    void drainOutput(float* outputInterleaved, std::size_t& framesWritten,
                     std::size_t outputCapacityFrames,
                     std::size_t keepFrames = 0) noexcept;
};

} // namespace neonsong::audio
