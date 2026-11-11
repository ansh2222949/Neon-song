package com.neonsong.app.data.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LastFmRateGuardTest {

    private val rateGuard = LastFmRateGuard()
    private val jsonMedia = "application/json".toMediaType()

    @Test
    fun rateLimitedSetsCooldownAndSucceededClearsIt() {
        assertThat(rateGuard.cooldownRemainingMs).isEqualTo(0L)

        rateGuard.onRequestLimited()
        assertThat(rateGuard.cooldownRemainingMs).isGreaterThan(0L)

        rateGuard.onRequestSucceeded()
        assertThat(rateGuard.cooldownRemainingMs).isEqualTo(0L)
    }

    @Test
    fun executeWithRetryRetriesOn429AndSucceeds() = runTest {
        var callCount = 0

        val response = rateGuard.executeWithRetry(maxRetries = 1) {
            callCount++
            if (callCount == 1) {
                Response.error(429, "Too Many Requests".toResponseBody(jsonMedia))
            } else {
                Response.success("OK")
            }
        }

        assertThat(callCount).isEqualTo(2)
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.body()).isEqualTo("OK")
        assertThat(rateGuard.cooldownRemainingMs).isEqualTo(0L)
    }

    @Test
    fun executeWithRetryRetriesOn503AndReturnsFailureIfStillFailing() = runTest {
        var callCount = 0

        val response = rateGuard.executeWithRetry<String>(maxRetries = 1) {
            callCount++
            Response.error<String>(503, "Service Unavailable".toResponseBody(jsonMedia))
        }

        assertThat(callCount).isEqualTo(2)
        assertThat(response.isSuccessful).isFalse()
        assertThat(response.code()).isEqualTo(503)
        assertThat(rateGuard.cooldownRemainingMs).isGreaterThan(0L)
    }
}
