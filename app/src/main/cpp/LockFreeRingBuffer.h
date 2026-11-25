#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <type_traits>

namespace neonsong::audio {

// Single-producer/single-consumer ring. The producer publishes samples with a
// release store; the Oboe consumer observes them with an acquire load. Indices
// are monotonic so all capacity slots remain usable.
template <typename T>
class LockFreeRingBuffer final {
    static_assert(std::is_trivially_copyable_v<T>, "LockFreeRingBuffer<T> requires trivially copyable T");

public:
    explicit LockFreeRingBuffer(std::size_t capacity)
        : capacity_(roundUpPow2(capacity)),
          mask_(capacity_ - 1U),
          data_(std::make_unique<T[]>(capacity_)) {}

    LockFreeRingBuffer(const LockFreeRingBuffer&) = delete;
    LockFreeRingBuffer& operator=(const LockFreeRingBuffer&) = delete;

    [[nodiscard]] std::size_t capacity() const noexcept { return capacity_; }

    [[nodiscard]] std::size_t availableToRead() const noexcept {
        // Acquire on read before write prevents ARM64 load reordering (read > write underflow).
        const auto read = readIndex_.load(std::memory_order_acquire);
        const auto write = writeIndex_.load(std::memory_order_acquire);
        return (write > read)
            ? std::min<std::size_t>(static_cast<std::size_t>(write - read), capacity_)
            : 0U;
    }

    [[nodiscard]] std::size_t availableToWrite() const noexcept {
        const auto read = readIndex_.load(std::memory_order_acquire);
        const auto write = writeIndex_.load(std::memory_order_acquire);
        const std::size_t used = (write > read)
            ? std::min<std::size_t>(static_cast<std::size_t>(write - read), capacity_)
            : 0U;
        return capacity_ - used;
    }

    std::size_t write(const T* source, std::size_t count) noexcept {
        if (source == nullptr || count == 0) return 0;
        const auto write = writeIndex_.load(std::memory_order_relaxed);
        const auto read = readIndex_.load(std::memory_order_acquire);
        const std::size_t used = (write > read) ? static_cast<std::size_t>(write - read) : 0U;
        const std::size_t writable = std::min(count, capacity_ - std::min(used, capacity_));
        if (writable == 0) return 0;
        copyIntoRing(source, writable, static_cast<std::size_t>(write) & mask_);
        writeIndex_.store(write + writable, std::memory_order_release);
        return writable;
    }

    std::size_t read(T* destination, std::size_t count) noexcept {
        if (destination == nullptr || count == 0) return 0;
        const auto read = readIndex_.load(std::memory_order_relaxed);
        const auto write = writeIndex_.load(std::memory_order_acquire);
        const std::size_t readable = (write > read)
            ? std::min(count, static_cast<std::size_t>(write - read))
            : 0U;
        if (readable == 0) return 0;
        copyFromRing(destination, readable, static_cast<std::size_t>(read) & mask_);
        readIndex_.store(read + readable, std::memory_order_release);
        return readable;
    }

    // Drops all buffered samples. The producer must be quiesced and the
    // consumer parked on another buffer before calling this. Advancing the
    // read index (instead of zeroing both) keeps any already-in-flight read
    // copying from untouched memory.
    void clear() noexcept {
        const auto write = writeIndex_.load(std::memory_order_acquire);
        readIndex_.store(write, std::memory_order_release);
    }

    // SPSC-safe flush invoked strictly from the consumer thread.
    // Advancing readIndex_ to writeIndex_ drops all pending readable samples
    // without racing against any concurrent writeIndex_ update from the producer.
    void clearFromConsumer() noexcept {
        const auto write = writeIndex_.load(std::memory_order_acquire);
        readIndex_.store(write, std::memory_order_release);
    }

    [[nodiscard]] std::uint64_t currentWriteIndex() const noexcept {
        return writeIndex_.load(std::memory_order_acquire);
    }

    void clearUpTo(std::uint64_t targetWrite) noexcept {
        const auto currentRead = readIndex_.load(std::memory_order_acquire);
        const auto currentWrite = writeIndex_.load(std::memory_order_acquire);
        const auto safeTarget = std::min(targetWrite, currentWrite);
        if (safeTarget > currentRead) {
            readIndex_.store(safeTarget, std::memory_order_release);
        }
    }

private:
    void copyIntoRing(const T* source, std::size_t count, std::size_t offset) noexcept {
        const auto first = std::min(count, capacity_ - offset);
        std::memcpy(data_.get() + offset, source, first * sizeof(T));
        if (first < count) {
            std::memcpy(data_.get(), source + first, (count - first) * sizeof(T));
        }
    }

    void copyFromRing(T* destination, std::size_t count, std::size_t offset) noexcept {
        const auto first = std::min(count, capacity_ - offset);
        std::memcpy(destination, data_.get() + offset, first * sizeof(T));
        if (first < count) {
            std::memcpy(destination + first, data_.get(), (count - first) * sizeof(T));
        }
    }

    static constexpr std::size_t roundUpPow2(std::size_t v) noexcept {
        if (v <= 2U) return 2U;
        --v;
        for (std::size_t shift = 1U; shift < sizeof(std::size_t) * 8U; shift <<= 1U) {
            v |= (v >> shift);
        }
        return v + 1U;
    }

    const std::size_t capacity_;
    const std::size_t mask_;
    std::unique_ptr<T[]> data_;
    alignas(64) std::atomic<std::uint64_t> writeIndex_{0};
    alignas(64) std::atomic<std::uint64_t> readIndex_{0};
};

}  // namespace neonsong::audio
