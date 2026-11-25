package com.neonsong.app.playback.resolve

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStreamCachePerformanceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sampleEntry(
        id: String,
        streamUrl: String = "https://googlevideo.example/$id",
        expiresAt: Long = System.currentTimeMillis() + 6 * 3600 * 1000L,
    ): StreamCache {
        return StreamCache(
            trackId = "track-$id",
            youtubeVideoId = id,
            streamUrl = streamUrl,
            audioCodec = "opus",
            bitrateKbps = 160,
            durationMs = 210_000L,
            expiresAt = expiresAt,
        )
    }

    @Test
    fun storeWithScopeOffloadsDiskIoAndDoesNotBlockCaller() = runTest {
        val dir = tempFolder.newFolder("stream_cache_async")
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cache = PlaybackStreamCache(
            directory = dir,
            scope = this,
            diskDispatcher = testDispatcher,
        )

        val entry = sampleEntry("vid1")
        cache.putFromPlayback(entry)

        // Memory cache hit is immediate (0ms delay)
        val hit = cache.get(TrackIdentity("track-vid1", "vid1"))
        assertNotNull(hit)
        assertEquals("https://googlevideo.example/vid1", hit?.streamUrl)

        // Advance coroutines to flush the dispatcher
        advanceUntilIdle()

        // Verify that disk snapshot was written asynchronously
        val diskFile = File(dir, "streams.json")
        assertTrue("Expected streams.json to be created by background dispatcher", diskFile.exists())
        assertTrue("Expected non-empty streams.json", diskFile.length() > 0)
        cache.close()
    }

    @Test
    fun flushSyncGuaranteesImmediateDiskWrite() {
        val dir = tempFolder.newFolder("stream_cache_sync")
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val cache = PlaybackStreamCache(
            directory = dir,
            scope = testScope,
        )

        cache.putFromPlayback(sampleEntry("sync1"))
        cache.flushSync()

        val diskFile = File(dir, "streams.json")
        assertTrue("flushSync must guarantee streams.json exists immediately", diskFile.exists())
        assertTrue(diskFile.length() > 0)

        // Reload from disk to verify integrity
        val reloaded = PlaybackStreamCache(directory = dir)
        val hit = reloaded.get(TrackIdentity("track-sync1", "sync1"))
        assertNotNull(hit)
        assertEquals("https://googlevideo.example/sync1", hit?.streamUrl)

        testScope.cancel()
    }

    @Test
    fun concurrentPutsAndInvalidationsMaintainStrictDiskConsistency() = runTest {
        val dir = tempFolder.newFolder("stream_cache_concurrency")
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cache = PlaybackStreamCache(
            directory = dir,
            scope = this,
            diskDispatcher = testDispatcher,
        )

        val iterations = 30
        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        // 10 concurrent coroutines doing rapid writes and invalidations
        for (worker in 0 until 10) {
            val job = launch(Dispatchers.Default) {
                for (i in 0 until iterations) {
                    val id = "entry_${worker}_$i"
                    cache.putFromPlayback(sampleEntry(id))
                    if (i % 3 == 0) {
                        cache.invalidate(TrackIdentity("track-$id", id))
                    }
                }
            }
            jobs.add(job)
        }

        // Wait for all concurrent writers to finish memory operations
        jobs.forEach { it.join() }

        // Process background disk worker to completion
        advanceUntilIdle()

        // Verify the background worker itself persisted the latest snapshot.
        val reloaded = PlaybackStreamCache(directory = dir)

        // Verify that every valid non-invalidated item in cache is present on disk
        for (worker in 0 until 10) {
            for (i in 0 until iterations) {
                val id = "entry_${worker}_$i"
                val memoryHit = cache.get(TrackIdentity("track-$id", id))
                val diskHit = reloaded.get(TrackIdentity("track-$id", id))
                if (memoryHit != null) {
                    assertNotNull("Disk must contain non-expired memory entry $id", diskHit)
                    assertEquals(memoryHit.streamUrl, diskHit?.streamUrl)
                } else {
                    assertNull("Disk must not contain invalidated entry $id", diskHit)
                }
            }
        }
        cache.close()
    }

    @Test
    fun invalidateRemovesFromMemoryImmediatelyAndUpdatesDisk() = runTest {
        val dir = tempFolder.newFolder("stream_cache_invalidate")
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val cache = PlaybackStreamCache(
            directory = dir,
            scope = this,
            diskDispatcher = testDispatcher,
        )

        val identity = TrackIdentity("track-inv", "inv1")
        cache.putFromPlayback(sampleEntry("inv1"))
        assertNotNull(cache.get(identity))

        cache.invalidate(identity)
        assertNull(cache.get(identity))

        advanceUntilIdle()

        val reloaded = PlaybackStreamCache(directory = dir)
        assertNull(reloaded.get(identity))
        cache.close()
    }
}
