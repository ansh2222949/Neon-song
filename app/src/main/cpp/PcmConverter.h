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
private:
    [[nodiscard]] static inline float sanitizeUnitFloat(float v) noexcept {
        std::uint32_t bits = 0;
        std::memcpy(&bits, &v, sizeof(bits));
        if ((bits & 0x7F800000U) == 0x7F800000U) return 0.0F;  // NaN / Inf (safe under -ffast-math)
        return std::clamp(v, -1.0F, 1.0F);
    }

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
        if (sampleCount == 0U) return true;

        const auto* bytes = static_cast<const std::uint8_t*>(input);
        switch (format) {
            case PcmFormat::Int16: {
                constexpr float kScale16 = 1.0F / 32768.0F;
                for (std::size_t i = 0; i < sampleCount; ++i) {
                    std::int16_t raw;
                    std::memcpy(&raw, bytes + i * 2U, sizeof(raw));
                    output[i] = static_cast<float>(raw) * kScale16;
                }
                return true;
            }

            case PcmFormat::PackedInt24: {
                constexpr float kScale24 = 1.0F / 8388608.0F;
                float* __restrict out = output;
                std::size_t i = 0;
                // 4x unrolled loop (reads 4-byte chunks safely up to sampleCount - 1).
                const std::size_t vecEnd = (sampleCount > 4U) ? ((sampleCount - 1U) & ~std::size_t{3U}) : 0U;
                for (; i < vecEnd; i += 4U) {
                    std::uint32_t u0, u1, u2, u3;
                    std::memcpy(&u0, bytes, 4U);
                    std::memcpy(&u1, bytes + 3U, 4U);
                    std::memcpy(&u2, bytes + 6U, 4U);
                    std::memcpy(&u3, bytes + 9U, 4U);
                    out[i]      = static_cast<float>(static_cast<std::int32_t>(u0 << 8U) >> 8) * kScale24;
                    out[i + 1U] = static_cast<float>(static_cast<std::int32_t>(u1 << 8U) >> 8) * kScale24;
                    out[i + 2U] = static_cast<float>(static_cast<std::int32_t>(u2 << 8U) >> 8) * kScale24;
                    out[i + 3U] = static_cast<float>(static_cast<std::int32_t>(u3 << 8U) >> 8) * kScale24;
                    bytes += 12U;
                }
                const std::size_t fastEnd = sampleCount - 1U;
                for (; i < fastEnd; ++i) {
                    std::uint32_t u32;
                    std::memcpy(&u32, bytes, 4U);
                    out[i] = static_cast<float>(static_cast<std::int32_t>(u32 << 8U) >> 8) * kScale24;
                    bytes += 3U;
                }
                const std::uint32_t u24 = static_cast<std::uint32_t>(bytes[0]) |
                    (static_cast<std::uint32_t>(bytes[1]) << 8U) |
                    (static_cast<std::uint32_t>(bytes[2]) << 16U);
                out[sampleCount - 1U] =
                    static_cast<float>(static_cast<std::int32_t>(u24 << 8U) >> 8) * kScale24;
                return true;
            }

            case PcmFormat::Int32: {
                // 2^-31 only shifts the float exponent; direct float is bit-exact and 2x faster on NEON.
                constexpr float kScale32 = 1.0F / 2147483648.0F;
                for (std::size_t i = 0; i < sampleCount; ++i) {
                    std::int32_t raw;
                    std::memcpy(&raw, bytes + i * 4U, sizeof(raw));
                    output[i] = static_cast<float>(raw) * kScale32;
                }
                return true;
            }

            case PcmFormat::Float32: {
                for (std::size_t i = 0; i < sampleCount; ++i) {
                    float value;
                    std::memcpy(&value, bytes + i * 4U, sizeof(value));
                    output[i] = sanitizeUnitFloat(value);
                }
                return true;
            }
        }
        return false;
    }
};

}  // namespace neonsong::audio
