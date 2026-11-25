package com.neonsong.app.playback.resolve

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Memory plus disk cache of signed stream URLs.
 * The map key is [StreamCache.youtubeVideoId]. Track id is stored on the
 * entry and is not required to reuse the URL.
 */
class PlaybackStreamCache(
    private val directory: File,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = MAX_ENTRIES,
    private val scope: CoroutineScope? = null,
    private val diskDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    private val memory = ConcurrentHashMap<String, StreamCache>()
    private val order = ArrayDeque<String>()
    private val lock = Any()
    private val persistenceLock = Any()
    private val file = File(directory, "streams.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private data class VersionedSnapshot(val version: Long, val data: List<StreamCache>)
    private val versionCounter = AtomicLong(0)
    private val pendingSnapshot = AtomicReference<VersionedSnapshot?>()
    private val persistChannel = Channel<Unit>(Channel.CONFLATED)
    private var lastWrittenVersion: Long = 0
    private var workerScope: CoroutineScope? = null
    private val safeMaxEntries = maxEntries.coerceAtLeast(1)
    @Volatile private var closed = false

    init {
        load()
        if (scope != null) {
            val backgroundScope = CoroutineScope(SupervisorJob() + diskDispatcher)
            workerScope = backgroundScope
            scope.coroutineContext[Job]?.invokeOnCompletion {
                backgroundScope.cancel()
            }
            backgroundScope.launch {
                for (signal in persistChannel) {
                    while (true) {
                        val toWrite = pendingSnapshot.getAndSet(null) ?: break
                        persistVersioned(toWrite)
                    }
                }
            }
        }
    }

    fun close() {
        closed = true
        flushSync()
        persistChannel.close()
        workerScope?.cancel()
    }

    fun get(identity: TrackIdentity): StreamCache? {
        val videoId = identity.youtubeVideoId
        if (videoId.isBlank()) return null
        val stored = memory[videoId]
        if (stored == null) {
            log("CACHE_MISS", videoId, "trackId=${identity.trackId}")
            return null
        }
        if (stored.youtubeVideoId != videoId || stored.streamUrl.isBlank()) {
            log("CACHE_MISS", videoId, "trackId=${identity.trackId} emptyUrl=true")
            invalidateIfUnchanged(videoId, stored, reason = "corrupt")
            return null
        }
        val now = nowMs()
        if (stored.isExpired(now)) {
            val remaining = stored.expiresAt - now
            log(
                "CACHE_EXPIRY",
                stored.youtubeVideoId,
                "trackId=${stored.trackId} expiresAt=${stored.expiresAt} remainingMs=$remaining",
            )
            invalidateIfUnchanged(videoId, stored, reason = "expiry")
            return null
        }
        log(
            "CACHE_HIT",
            stored.youtubeVideoId,
            "trackId=${identity.trackId} expiresAt=${stored.expiresAt} remainingMs=${stored.expiresAt - now}",
        )
        return stored.copy(trackId = identity.trackId)
    }

    private fun invalidateIfUnchanged(youtubeVideoId: String, expected: StreamCache, reason: String) {
        val versioned = synchronized(lock) {
            if (memory[youtubeVideoId] !== expected) return
            memory.remove(youtubeVideoId)
            order.remove(youtubeVideoId)
            val ver = versionCounter.incrementAndGet()
            VersionedSnapshot(ver, order.mapNotNull { memory[it] })
        }
        log("CACHE_EVICT", youtubeVideoId, "reason=$reason trackId=${expected.trackId}")
        persist(versioned)
    }

    /** Playback owns the video id and may replace a prefetch entry. */
    fun putFromPlayback(entry: StreamCache) {
        store(entry, replace = true, source = "playback")
    }

    /**
     * Prefetch or a detached extract. Returns false when a fresh URL is
     * already stored so a background resolve cannot replace the playing URL.
     */
    fun putIfAbsent(entry: StreamCache): Boolean = store(entry, replace = false, source = "prefetch")

    fun invalidate(identity: TrackIdentity) {
        val (removed, versioned) = synchronized(lock) {
            val removed = memory.remove(identity.youtubeVideoId)
            if (removed == null && !order.contains(identity.youtubeVideoId)) return
            order.remove(identity.youtubeVideoId)
            val ver = versionCounter.incrementAndGet()
            removed to VersionedSnapshot(ver, order.mapNotNull { memory[it] })
        }
        if (removed != null) {
            log("CACHE_EVICT", identity.youtubeVideoId, "reason=invalidate trackId=${removed.trackId}")
        }
        persist(versioned)
    }

    fun clear() {
        val versioned = synchronized(lock) {
            memory.clear()
            order.clear()
            val ver = versionCounter.incrementAndGet()
            VersionedSnapshot(ver, emptyList())
        }
        persist(versioned)
    }

    fun flushSync() {
        val versioned = synchronized(lock) {
            val ver = versionCounter.incrementAndGet()
            VersionedSnapshot(ver, order.mapNotNull { memory[it] })
        }
        synchronized(persistenceLock) {
            discardPendingThrough(versioned.version)
            writeIfNewer(versioned)
        }
    }

    private fun store(entry: StreamCache, replace: Boolean, source: String): Boolean {
        require(entry.trackId.isNotBlank() && entry.youtubeVideoId.isNotBlank())
        require(entry.streamUrl.isNotBlank()) { "streamUrl is required" }
        val versioned = synchronized(lock) {
            val key = entry.youtubeVideoId
            val existing = memory[key]
            if (!replace && existing != null && existing.streamUrl.isNotBlank() && !existing.isExpired(nowMs())) {
                return@synchronized null
            }
            memory[key] = entry
            order.remove(key)
            order.addLast(key)
            trimLocked()
            val ver = versionCounter.incrementAndGet()
            VersionedSnapshot(ver, order.mapNotNull { memory[it] })
        }
        if (versioned == null) {
            log(
                "CACHE_HIT",
                entry.youtubeVideoId,
                "source=$source keptExisting=true",
            )
            return false
        }
        log(
            "CACHE_INSERT",
            entry.youtubeVideoId,
            "source=$source trackId=${entry.trackId} expiresAt=${entry.expiresAt} " +
                "url=${entry.streamUrl.substringBefore('?').take(80)}",
        )
        persist(versioned)
        return true
    }

    private fun persist(versioned: VersionedSnapshot) {
        if (scope != null) {
            queueSnapshot(versioned)
        } else {
            persistVersioned(versioned)
        }
    }

    private fun queueSnapshot(versioned: VersionedSnapshot) {
        if (closed) return
        while (true) {
            val pending = pendingSnapshot.get()
            if (pending != null && pending.version >= versioned.version) break
            if (pendingSnapshot.compareAndSet(pending, versioned)) break
        }
        if (closed) return
        val result = persistChannel.trySend(Unit)
        if (result.isFailure && !closed) {
            Log.e(TAG, "Unable to queue stream-cache persistence", result.exceptionOrNull())
        }
    }

    private fun persistVersioned(versioned: VersionedSnapshot) {
        synchronized(persistenceLock) {
            writeIfNewer(versioned)
        }
    }

    private fun writeIfNewer(versioned: VersionedSnapshot) {
        if (versioned.version <= lastWrittenVersion) return
        if (persistSnapshot(versioned.data)) {
            lastWrittenVersion = versioned.version
        }
    }

    private fun discardPendingThrough(version: Long) {
        while (true) {
            val pending = pendingSnapshot.get() ?: return
            if (pending.version > version) return
            if (pendingSnapshot.compareAndSet(pending, null)) return
        }
    }

    private fun load() {
        val encoded = runCatching { file.takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val entries = runCatching {
            json.decodeFromString(ListSerializer(StreamCache.serializer()), encoded)
        }.getOrNull() ?: return
        val now = nowMs()
        synchronized(lock) {
            memory.clear()
            order.clear()
            val deduped = LinkedHashMap<String, StreamCache>(entries.size)
            for (entry in entries) {
                if (entry.trackId.isBlank() || entry.youtubeVideoId.isBlank() || entry.streamUrl.isBlank()) continue
                if (entry.isExpired(now)) continue
                val previous = deduped[entry.youtubeVideoId]
                if (previous != null && previous.expiresAt >= entry.expiresAt) continue
                deduped.remove(entry.youtubeVideoId)
                deduped[entry.youtubeVideoId] = entry
            }
            for ((key, entry) in deduped) {
                memory[key] = entry
                order.addLast(key)
            }
            trimLocked()
        }
    }

    private fun trimLocked() {
        val now = nowMs()
        val it = order.iterator()
        while (it.hasNext()) {
            val key = it.next()
            val entry = memory[key]
            if (entry == null || entry.isExpired(now)) {
                it.remove()
                memory.remove(key)
            }
        }
        while (memory.size > safeMaxEntries && order.isNotEmpty()) {
            val oldest = order.removeFirst()
            memory.remove(oldest)
        }
    }

    private fun persistSnapshot(snapshot: List<StreamCache>): Boolean {
        val tmp = File(directory, "streams.json.tmp")
        return try {
            directory.mkdirs()
            val payload = json.encodeToString(
                ListSerializer(StreamCache.serializer()),
                snapshot,
            )
            tmp.writeText(payload, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(payload, Charsets.UTF_8)
                tmp.delete()
            }
            true
        } catch (error: Exception) {
            runCatching { if (tmp.exists()) tmp.delete() }
            Log.e(TAG, "Failed to persist stream cache to ${file.absolutePath}", error)
            false
        }
    }

    private fun log(event: String, youtubeVideoId: String, detail: String) {
        Log.i(TAG, "[$event] youtubeVideoId=$youtubeVideoId $detail")
    }

    private companion object {
        const val MAX_ENTRIES = 128
        const val TAG = "NeonSongResolver"
    }
}
