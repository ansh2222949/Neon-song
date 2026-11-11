#pragma once

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace neonsong::audio {

enum class PcmFormat : std::int32_t {
    Int16 = 0,
    PackedInt24 = 1,
    Int32 = 2,
    Float32 = 3,
};

class PcmConverter final {
public:
    [[nodiscard]] static constexpr std::size_t bytesPerSample(PcmFormat format) noexcept {
        switch (format) {
            case PcmFormat::Int16: return 2;
            case PcmFormat::PackedInt24: return 3;
            case PcmFormat::Int32:
            case PcmFormat::Float32: return 4;
        }
        return 0;
    }

    // Converts little-endian interleaved PCM to normalized Float32.
    // Branchless arithmetic allows Clang/GCC to auto-vectorize via ARM NEON.
    [[nodiscard]] static bool toFloat(
        const void* input,
        std::size_t sampleCount,
        PcmFormat format,
        float* output) noexcept {
        if (input == nullptr || output == nullptr) return false;

        switch (format) {
            case PcmFormat::Int16: {
                constexpr float kScale16 = 1.0F / 32768.0F;
                if ((reinterpret_cast<std::uintptr_t>(input) & 1U) == 0U) {
                    const auto* __restrict samples16 = static_cast<const std::int16_t*>(input);
                    for (std::size_t i = 0; i < sampleCount; ++i) {
                        output[i] = static_cast<float>(samples16[i]) * kScale16;
                    }
                } else {
                    const auto* __restrict bytes = static_cast<const std::uint8_t*>(input);
                    for (std::size_t i = 0; i < sampleCount; ++i) {
                        const std::uint16_t raw = static_cast<std::uint16_t>(
                            static_cast<std::uint16_t>(bytes[0]) |
                            (static_cast<std::uint16_t>(bytes[1]) << 8U));
                        const auto value = static_cast<std::int16_t>(raw);
                        output[i] = static_cast<float>(value) * kScale16;
                        bytes += 2;
                    }
                }
                return true;
            }

            case PcmFormat::PackedInt24: {
                constexpr float kScale24 = 1.0F / 8388608.0F;
                const auto* __restrict bytes = static_cast<const std::uint8_t*>(input);
                for (std::size_t i = 0; i < sampleCount; ++i) {
                    const std::uint32_t u24 = static_cast<std::uint32_t>(bytes[0]) |
                        (static_cast<std::uint32_t>(bytes[1]) << 8U) |
                        (static_cast<std::uint32_t>(bytes[2]) << 16U);
                    // Branchless 24->32-bit sign extension (compiles to single ARM SBFX instruction)
                    const std::int32_t signed24 = static_cast<std::int32_t>(u24 << 8U) >> 8;
                    output[i] = static_cast<float>(signed24) * kScale24;
                    bytes += 3;
                }
                return true;
            }

            case PcmFormat::Int32: {
                // Convert via double to preserve all 24+ mantissa bits of 32-bit PCM before float rounding
                constexpr double kScale32 = 1.0 / 2147483648.0;
                if ((reinterpret_cast<std::uintptr_t>(input) & 3U) == 0U) {
                    const auto* __restrict samples32 = static_cast<const std::int32_t*>(input);
                    for (std::size_t i = 0; i < sampleCount; ++i) {
                        output[i] = static_cast<float>(static_cast<double>(samples32[i]) * kScale32);
                    }
                } else {
                    const auto* __restrict bytes = static_cast<const std::uint8_t*>(input);
                    for (std::size_t i = 0; i < sampleCount; ++i) {
                        const std::uint32_t raw = static_cast<std::uint32_t>(bytes[0]) |
                            (static_cast<std::uint32_t>(bytes[1]) << 8U) |
                            (static_cast<std::uint32_t>(bytes[2]) << 16U) |
                            (static_cast<std::uint32_t>(bytes[3]) << 24U);
                        const auto value = static_cast<std::int32_t>(raw);
                        output[i] = static_cast<float>(static_cast<double>(value) * kScale32);
                        bytes += 4;
                    }
                }
                return true;
            }

            case PcmFormat::Float32: {
                if ((reinterpret_cast<std::uintptr_t>(input) & 3U) == 0U) {
                    const auto* inFloat = static_cast<const float*>(input);
                    if (inFloat == output) {
                        for (std::size_t i = 0; i < sampleCount; ++i) {
                            const float v = output[i];
                            output[i] = std::isfinite(v) ? std::clamp(v, -1.0F, 1.0F) : 0.0F;
                        }
                    } else {
                        for (std::size_t i = 0; i < sampleCount; ++i) {
                            const float v = inFloat[i];
                            output[i] = std::isfinite(v) ? std::clamp(v, -1.0F, 1.0F) : 0.0F;
                        }
                    }
                } else {
                    const auto* __restrict bytes = static_cast<const std::uint8_t*>(input);
                    for (std::size_t i = 0; i < sampleCount; ++i) {
                        const std::uint32_t raw = static_cast<std::uint32_t>(bytes[0]) |
                            (static_cast<std::uint32_t>(bytes[1]) << 8U) |
                            (static_cast<std::uint32_t>(bytes[2]) << 16U) |
                            (static_cast<std::uint32_t>(bytes[3]) << 24U);
                        float value;
                        std::memcpy(&value, &raw, sizeof(value));
                        output[i] = std::isfinite(value) ? std::clamp(value, -1.0F, 1.0F) : 0.0F;
                        bytes += 4;
                    }
                }
                return true;
            }
        }
        return false;
    }
};

}  // namespace neonsong::audio
