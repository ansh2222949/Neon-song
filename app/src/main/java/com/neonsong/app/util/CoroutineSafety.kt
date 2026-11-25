package com.neonsong.app.util

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Like [runCatching], but always rethrows [CancellationException] so cooperative
 * cancellation and structured concurrency are never silently suppressed.
 */
inline fun <T> cancellableRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Result.failure(t)
}

/**
 * Safely normalizes Last.fm fields that may arrive as a [JsonArray], a single
 * [JsonObject], or an empty primitive string `""`.
 */
fun JsonElement?.asJsonObjectList(): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> listOf(this)
    else -> emptyList()
}
