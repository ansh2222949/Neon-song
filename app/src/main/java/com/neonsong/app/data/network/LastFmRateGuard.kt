package com.neonsong.app.data.network

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class LastFmRateGuard @Inject constructor() {

    private val lock = Any()
    private var cooldownUntilElapsed: Long = 0L

    val cooldownRemainingMs: Long
        get() = synchronized(lock) {
            (cooldownUntilElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }

    fun onRequestLimited() {
        synchronized(lock) {
            cooldownUntilElapsed = SystemClock.elapsedRealtime() + 2_000L
        }
    }

    fun onRequestSucceeded() {
        synchronized(lock) {
            cooldownUntilElapsed = 0L
        }
    }

    suspend fun suspendAwaitClearance(maxWaitMs: Long = 2_000L): Boolean {
        val remaining = cooldownRemainingMs
        if (remaining > maxWaitMs) {
            if (maxWaitMs > 0) delay(maxWaitMs)
            return false
        }
        if (remaining > 0) {
            delay(minOf(remaining, maxWaitMs))
        }
        return true
    }

    /**
     * Executes a Last.fm API call with non-blocking coroutine-level cooldown and single retry.
     * Unlike OkHttp interceptors which block carrier threads with Thread.sleep, this suspends
     * the coroutine without holding any thread in the OkHttp dispatcher pool.
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = 1,
        retryableStatusCodes: Set<Int> = setOf(429, 503),
        call: suspend () -> retrofit2.Response<T>,
    ): retrofit2.Response<T> {
        suspendAwaitClearance()
        var response = call()
        var retriesLeft = maxRetries
        while (response.code() in retryableStatusCodes && retriesLeft > 0) {
            retriesLeft--
            onRequestLimited()
            response.errorBody()?.close()
            suspendAwaitClearance()
            response = call()
        }
        if (response.isSuccessful) {
            onRequestSucceeded()
        }
        return response
    }
}
