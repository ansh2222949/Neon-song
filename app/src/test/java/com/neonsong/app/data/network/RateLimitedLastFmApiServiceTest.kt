package com.neonsong.app.data.network

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimitedLastFmApiServiceTest {
    private val mediaType = "application/json".toMediaType()

    @Test
    fun getRetriesRateLimitThroughProductionService() = runTest {
        val delegate = mockk<LastFmApiService>()
        var calls = 0
        coEvery { delegate.get(any()) } coAnswers {
            calls++
            if (calls == 1) {
                Response.error(429, "limited".toResponseBody(mediaType))
            } else {
                Response.success("ok".toResponseBody(mediaType))
            }
        }

        val service = RateLimitedLastFmApiService(delegate, LastFmRateGuard())
        val response = service.get(mapOf("method" to "track.getInfo"))

        assertThat(calls).isEqualTo(2)
        assertThat(response.isSuccessful).isTrue()
        coVerify(exactly = 2) { delegate.get(any()) }
    }

    @Test
    fun postDoesNotRetryServiceUnavailableAndRiskDuplicateWrites() = runTest {
        val delegate = mockk<LastFmApiService>()
        var calls = 0
        coEvery { delegate.post(any()) } coAnswers {
            calls++
            Response.error<ResponseBody>(503, "unavailable".toResponseBody(mediaType))
        }

        val service = RateLimitedLastFmApiService(delegate, LastFmRateGuard())
        val response = service.post(mapOf("method" to "track.scrobble"))

        assertThat(calls).isEqualTo(1)
        assertThat(response.code()).isEqualTo(503)
        coVerify(exactly = 1) { delegate.post(any()) }
    }
}
