#include "AutoDJProcessor.h"

namespace neonsong {

AutoDJProcessor::AutoDJProcessor(int32_t maxRetries, int32_t refillThreshold)
    : m_latencyEstimator(2500.0, 1000.0, 0.2),
      m_maxRetries(std::max(0, maxRetries)),
      m_refillThreshold(std::max(0, refillThreshold)) {}

void AutoDJProcessor::recordResolutionLatency(int64_t latencyMs, bool wasSuccess) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_latencyEstimator.recordSample(static_cast<double>(latencyMs), wasSuccess);
}

AutoDJDecision AutoDJProcessor::evaluate(
    int64_t currentPositionMs,
    int64_t durationMs,
    bool isPlaying,
    int64_t transitionDurationMs,
    TrackLifecycleStage nextStage,
    int32_t retryCount,
    int32_t unplayedCount
) {
    std::lock_guard<std::mutex> lock(m_mutex);

    // 1. Terminal failure handling
    if (nextStage == TrackLifecycleStage::FAILED) {
        if (retryCount >= m_maxRetries) {
            return AutoDJDecision::SKIP_FAILED_TRACK;
        }
        return AutoDJDecision::PREFETCH_NEXT;
    }

    const int64_t safePosMs = std::max<int64_t>(0, currentPositionMs);
    const int64_t safeTransitionMs = std::max<int64_t>(0, transitionDurationMs);
    const int64_t remainingMs = (durationMs > 0) ? std::max<int64_t>(0, durationMs - safePosMs) : 0;

    // 2. Unknown duration or stream without fixed duration: prefetch if queued
    if (durationMs <= 0) {
        if (nextStage == TrackLifecycleStage::QUEUED) {
            return AutoDJDecision::PREFETCH_NEXT;
        }
        if (nextStage == TrackLifecycleStage::RESOLVED) {
            return AutoDJDecision::PREPARE_STANDBY;
        }
        if (unplayedCount <= m_refillThreshold) {
            return AutoDJDecision::REFILL_QUEUE;
        }
        return AutoDJDecision::DO_NOTHING;
    }

    // 3. Track has reached or exceeded its duration
    if (remainingMs <= 0) {
        if (nextStage == TrackLifecycleStage::READY) return AutoDJDecision::TRIGGER_TRANSITION;
        if (nextStage == TrackLifecycleStage::QUEUED) return AutoDJDecision::PREFETCH_NEXT;
        if (nextStage == TrackLifecycleStage::RESOLVED) return AutoDJDecision::PREPARE_STANDBY;
        if (nextStage == TrackLifecycleStage::RESOLVING ||
            nextStage == TrackLifecycleStage::PREPARING ||
            nextStage == TrackLifecycleStage::WAITING_LATE) {
            return AutoDJDecision::WAIT_FOR_LATE_TRACK;
        }
        if (unplayedCount <= m_refillThreshold) {
            return AutoDJDecision::REFILL_QUEUE;
        }
        return AutoDJDecision::DO_NOTHING;
    }

    // 4. Normal active playback progression
    if (isPlaying) {
        if (remainingMs <= safeTransitionMs) {
            if (nextStage == TrackLifecycleStage::READY) return AutoDJDecision::TRIGGER_TRANSITION;
            if (nextStage == TrackLifecycleStage::RESOLVING ||
                nextStage == TrackLifecycleStage::PREPARING ||
                nextStage == TrackLifecycleStage::WAITING_LATE) {
                return AutoDJDecision::WAIT_FOR_LATE_TRACK;
            }
        }
        if (nextStage == TrackLifecycleStage::QUEUED || nextStage == TrackLifecycleStage::RESOLVED) {
            const int64_t leadWindowMs = m_latencyEstimator.calculateDynamicLeadWindowMs(safeTransitionMs);
            if (remainingMs <= leadWindowMs) {
                return (nextStage == TrackLifecycleStage::QUEUED)
                    ? AutoDJDecision::PREFETCH_NEXT
                    : AutoDJDecision::PREPARE_STANDBY;
            }
        }
    }

    // 5. Refill check: if queue is running out of unplayed tracks in second half or last 60s
    if (unplayedCount <= m_refillThreshold &&
        (safePosMs >= (durationMs / 2) || remainingMs <= 60000)) {
        return AutoDJDecision::REFILL_QUEUE;
    }

    return AutoDJDecision::DO_NOTHING;
}

int64_t AutoDJProcessor::getDynamicLeadWindowMs(int64_t transitionDurationMs) const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_latencyEstimator.calculateDynamicLeadWindowMs(transitionDurationMs);
}

void AutoDJProcessor::getLatencyStats(
    float& outMeanMs,
    float& outStdDevMs,
    float& outLeadWindowMs,
    int64_t transitionDurationMs
) const {
    std::lock_guard<std::mutex> lock(m_mutex);
    outMeanMs = static_cast<float>(m_latencyEstimator.getMeanMs());
    outStdDevMs = static_cast<float>(m_latencyEstimator.getStdDevMs());
    outLeadWindowMs = static_cast<float>(m_latencyEstimator.calculateDynamicLeadWindowMs(transitionDurationMs));
}

} // namespace neonsong
