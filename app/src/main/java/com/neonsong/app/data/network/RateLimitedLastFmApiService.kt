package com.neonsong.app.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject

/**
 * Applies the shared coroutine-level rate-limit policy to every Last.fm API call.
 */
class RateLimitedLastFmApiService @Inject constructor(
    private val delegate: LastFmApiService,
    private val rateGuard: LastFmRateGuard,
) : LastFmApiService {
    override suspend fun get(params: Map<String, String>): Response<ResponseBody> =
        rateGuard.executeWithRetry { delegate.get(params) }

    override suspend fun post(params: Map<String, String>): Response<ResponseBody> =
        rateGuard.executeWithRetry(retryableStatusCodes = setOf(429)) { delegate.post(params) }
}
