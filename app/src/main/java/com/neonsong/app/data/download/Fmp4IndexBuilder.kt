package com.neonsong.app.data.download

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Builds Segment Index (sidx) and Movie Fragment Random Access (mfra/tfra/mfro)
 * indexing, and patches duration headers for fragmented MP4 (fMP4) audio files
 * (such as Dolby Atmos E-AC-3 and AAC DASH downloads).
 *
 * DASH segments concatenate `0.mp4` (init chunk with duration=0) and media chunks
 * (`moof` + `mdat`) without an inline seek index. ExoPlayer's FragmentedMp4Extractor
 * marks unindexed fMP4 as unseekable (SeekMap.Unseekable) and external players
 * display 0:00 duration or fail to seek.
 *
 * This builder:
 * 1. Scans `moof` + `mdat` fragments and reads `tfdt` decode times and subsegment sizes.
 * 2. Patches `duration` in `mvhd`, `tkhd`, and `mdhd` headers so players immediately
 *    know the true duration.
 * 3. Inserts a standard `sidx` box (ISO/IEC 14496-12) between `moov` and the first `moof`
 *    so Media3 / ExoPlayer generates a ChunkIndex seek map for instant seeking.
 * 4. Appends a terminal `mfra` box (`tfra` + `mfro`) so all external players and
 *    tools can seek with sample-accurate random access.
 */
object Fmp4IndexBuilder {
    private const val TAG = "Fmp4IndexBuilder"
    private const val MFRO_BOX_SIZE = 16
    private const val COPY_BUFFER_SIZE = 64 * 1024

    data class FragmentInfo(
        val time: Long,
        val duration: Long,
        val moofOffset: Long,
        val totalSize: Long, // moof size + mdat size
    )

    data class MoovInfo(
        var movieTimescale: Long = 1000L,
        var movieDuration: Long = 0L,
        var movieDurationOffset: Long = -1L,
        var movieDurationIs64: Boolean = false,
        var trackId: Long = 1L,
        var trackDuration: Long = 0L,
        var trackDurationOffset: Long = -1L,
        var trackDurationIs64: Boolean = false,
        var mediaTimescale: Long = 48000L,
        var mediaDuration: Long = 0L,
        var mediaDurationOffset: Long = -1L,
        var mediaDurationIs64: Boolean = false,
    )

    /**
     * Checks if the file is a fragmented MP4 (contains at least one top-level `moof` box).
     */
    fun isFragmentedMp4(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < 32) return false
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var pos = 0L
            val header = ByteArray(16)
            while (pos + 8 <= length) {
                raf.seek(pos)
                if (raf.read(header, 0, 8) < 8) break
                val size32 = be32(header, 0)
                val type = String(header, 4, 4, Charsets.US_ASCII)
                var boxSize = size32.toLong() and 0xFFFF_FFFFL
                var headerSize = 8L
                if (size32 == 1) {
                    if (raf.read(header, 8, 8) < 8) break
                    boxSize = be64(header, 8)
                    headerSize = 16L
                } else if (size32 == 0) {
                    boxSize = length - pos
                }
                if (boxSize < headerSize) break
                if (type == "moof") return true
                pos += boxSize
            }
            false
        }
    }.getOrDefault(false)

    /**
     * Checks if the file already contains an `mfra` box at the end.
     */
    fun hasMfraBox(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < MFRO_BOX_SIZE) return false
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            raf.seek(length - MFRO_BOX_SIZE)
            val footer = ByteArray(MFRO_BOX_SIZE)
            raf.readFully(footer)
            val mfroType = String(footer, 4, 4, Charsets.US_ASCII)
            if (mfroType == "mfro") {
                val mfraSize = be32(footer, 12).toLong() and 0xFFFF_FFFFL
                if (mfraSize >= MFRO_BOX_SIZE && mfraSize <= length) {
                    raf.seek(length - mfraSize + 4)
                    val mfraTypeBytes = ByteArray(4)
                    raf.readFully(mfraTypeBytes)
                    return String(mfraTypeBytes, Charsets.US_ASCII) == "mfra"
                }
            }
            false
        }
    }.getOrDefault(false)

    /**
     * Finalizes a fragmented MP4 file:
     * - Patches `mvhd`, `tkhd`, `mdhd` duration headers if zero.
     * - Inserts `sidx` box between `moov` and the first `moof` if missing.
     * - Appends `mfra` box (`tfra` + `mfro`) if missing.
     *
     * Returns true if indexing/patching succeeded or file was already indexed.
     */
    private val activeFileLocks = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock>()

    /**
     * Finalizes a fragmented MP4 file:
     * - Patches `mvhd`, `tkhd`, `mdhd` duration headers if zero.
     * - Inserts `sidx` box between `moov` and the first `moof` if missing.
     * - Appends `mfra` box (`tfra` + `mfro`) if missing.
     *
     * Returns true if indexing/patching succeeded or file was already indexed.
     */
    fun finalizeFmp4(file: File, knownDurationMs: Long = 0L): Boolean {
        val canonicalKey = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val lock = activeFileLocks.computeIfAbsent(canonicalKey) { java.util.concurrent.locks.ReentrantLock() }
        if (!lock.tryLock()) {
            Log.w(TAG, "Indexing already in progress for ${file.name}; skipping concurrent attempt")
            return false
        }
        try {
            return finalizeFmp4Internal(file, knownDurationMs)
        } finally {
            lock.unlock()
            activeFileLocks.remove(canonicalKey, lock)
        }
    }

    private fun finalizeFmp4Internal(file: File, knownDurationMs: Long = 0L): Boolean = runCatching {
        recoverFileBackupIfNeeded(file)
        file.parentFile?.let { runCatching { recoverOrphanedBackups(it) } }
        if (!file.isFile || file.length() < 32) return false

        // Production BMFF structural gate: reject malformed fMP4 media before any processing
        if (!isValidFmp4(file, requireSidx = false)) {
            Log.w(TAG, "Source file ${file.name} failed fMP4 structural validation; aborting finalization")
            return false
        }

        val moovInfo = MoovInfo()
        val fragments = mutableListOf<FragmentInfo>()
        var firstMoofOffset = -1L
        var hasSidx = false
        var currentMoofOffset = -1L
        var currentMoofSize = 0L
        var currentFragTime = 0L
        var currentFragDuration = 0L
        var lastFragEnd = 0L

        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var pos = 0L
            val header = ByteArray(16)

            while (pos + 8 <= length) {
                raf.seek(pos)
                if (raf.read(header, 0, 8) < 8) break
                val size32 = be32(header, 0)
                val type = String(header, 4, 4, Charsets.US_ASCII)
                var boxSize = size32.toLong() and 0xFFFF_FFFFL
                var headerSize = 8L
                if (size32 == 1) {
                    if (raf.read(header, 8, 8) < 8) break
                    boxSize = be64(header, 8)
                    headerSize = 16L
                } else if (size32 == 0) {
                    boxSize = length - pos
                }
                if (boxSize < headerSize) break

                val payloadStart = pos + headerSize
                val payloadSize = boxSize - headerSize

                when (type) {
                    "moov" -> {
                        if (payloadSize in 1..(32 * 1024 * 1024)) {
                            parseMoovBox(raf, payloadStart, payloadSize, moovInfo)
                        }
                    }
                    "sidx" -> {
                        hasSidx = true
                    }
                    "moof" -> {
                        if (firstMoofOffset < 0L) {
                            firstMoofOffset = pos
                        }
                        currentMoofOffset = pos
                        currentMoofSize = boxSize
                        val (fragTime, fragDuration) = parseMoofBox(
                            raf = raf,
                            payloadStart = payloadStart,
                            payloadSize = payloadSize,
                            expectedTrackId = moovInfo.trackId,
                            fallbackTime = lastFragEnd,
                        )
                        currentFragTime = fragTime
                        currentFragDuration = fragDuration
                        lastFragEnd = fragTime + fragDuration
                    }
                    "mdat" -> {
                        if (currentMoofOffset >= 0L) {
                            val totalSubsegmentSize = currentMoofSize + boxSize
                            fragments.add(
                                FragmentInfo(
                                    time = currentFragTime,
                                    duration = currentFragDuration,
                                    moofOffset = currentMoofOffset,
                                    totalSize = totalSubsegmentSize,
                                )
                            )
                            currentMoofOffset = -1L
                        }
                    }
                }

                pos += boxSize
            }
        }

        if (firstMoofOffset < 0L || fragments.isEmpty()) {
            // Progressive MP4: patch duration if zero and known
            if (knownDurationMs > 0L) {
                RandomAccessFile(file, "rw").use { raf ->
                    patchDurationsIfZero(raf, moovInfo, knownDurationMs)
                }
            }
            return true
        }

        // Calculate total duration in media timescale
        val totalMediaDuration = if (lastFragEnd > 0L) {
            lastFragEnd
        } else if (knownDurationMs > 0L && moovInfo.mediaTimescale > 0L) {
            (knownDurationMs * moovInfo.mediaTimescale) / 1000L
        } else {
            0L
        }

        // 1. Insert sidx box before the first moof if not already present
        var sidxInsertedSize = 0L

        if (!hasSidx && fragments.size <= 65535) {
            val sidxBytes = buildSidxBox(
                trackId = moovInfo.trackId,
                timescale = moovInfo.mediaTimescale,
                earliestPresentationTime = fragments.first().time,
                fragments = fragments,
            )
            val parentDir = file.parentFile ?: file.canonicalFile.parentFile
            val txId = "${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
            val tempFile = File(parentDir, "${file.name}.sidx_tmp_$txId")
            val backupFile = File(parentDir, "${file.name}.bak")
            var insertSuccess = false

            try {
                val fos = FileOutputStream(tempFile)
                val bos = java.io.BufferedOutputStream(fos, COPY_BUFFER_SIZE)
                try {
                    FileInputStream(file).use { input ->
                        copyExactly(input, bos, firstMoofOffset)
                        bos.write(sidxBytes)
                        copyStream(input, bos)
                    }
                    bos.flush()
                    try {
                        fos.fd.sync()
                    } catch (syncErr: Exception) {
                        Log.e(TAG, "Hardware sync failed for ${tempFile.name}; aborting transaction", syncErr)
                        throw syncErr
                    }
                } finally {
                    bos.close()
                }

                val expectedSize = file.length() + sidxBytes.size
                if (tempFile.length() == expectedSize && tempFile.length() > 0L && isValidIndexedFmp4(tempFile)) {
                    insertSuccess = replaceFileAtomically(tempFile, file, backupFile)
                    if (insertSuccess) {
                        sidxInsertedSize = sidxBytes.size.toLong()
                        Log.i(TAG, "Inserted sidx box (${sidxBytes.size} bytes) before first moof in ${file.name}")
                    }
                } else {
                    Log.w(TAG, "sidx temp file validation failed for ${file.name}: expected $expectedSize, got ${tempFile.length()}")
                }
            } catch (err: Exception) {
                Log.w(TAG, "Could not insert sidx box for ${file.name}: ${err.message}", err)
            } finally {
                if (tempFile.exists()) {
                    runCatching { tempFile.delete() }
                }
            }
        }

        // 2. Patch duration in mvhd, tkhd, mdhd and append mfra
        RandomAccessFile(file, "rw").use { raf ->
            patchDurations(raf, moovInfo, totalMediaDuration, knownDurationMs)

            val currentLength = raf.length()
            val alreadyHasMfra = if (currentLength >= MFRO_BOX_SIZE) {
                raf.seek(currentLength - MFRO_BOX_SIZE)
                val footer = ByteArray(MFRO_BOX_SIZE)
                raf.readFully(footer)
                String(footer, 4, 4, Charsets.US_ASCII) == "mfro"
            } else false

            if (!alreadyHasMfra) {
                val shiftedFragments = if (sidxInsertedSize > 0L) {
                    fragments.map { it.copy(moofOffset = it.moofOffset + sidxInsertedSize) }
                } else {
                    fragments
                }
                val mfraBytes = buildMfraBox(moovInfo.trackId, shiftedFragments)
                raf.seek(currentLength)
                raf.write(mfraBytes)
                Log.i(TAG, "Appended mfra index with ${fragments.size} fragments (${mfraBytes.size} bytes) to ${file.name}")
            }
        }

        true
    }.getOrElse { error ->
        Log.w(TAG, "Failed to finalize fMP4 for ${file.name}: ${error.message}", error)
        false
    }

    private fun parseMoovBox(
        raf: RandomAccessFile,
        moovStart: Long,
        moovSize: Long,
        moovInfo: MoovInfo,
    ) {
        val moovBytes = ByteArray(moovSize.toInt())
        raf.seek(moovStart)
        raf.readFully(moovBytes)

        var pos = 0
        while (pos + 8 <= moovBytes.size) {
            val size = be32(moovBytes, pos)
            val type = String(moovBytes, pos + 4, 4, Charsets.US_ASCII)
            val boxSize = size.toLong() and 0xFFFF_FFFFL
            if (boxSize < 8 || pos + boxSize > moovBytes.size) break

            val payloadStart = pos + 8
            val payloadSize = (boxSize - 8).toInt()

            when (type) {
                "mvhd" -> {
                    val version = moovBytes[payloadStart].toInt() and 0xFF
                    if (version == 1 && payloadSize >= 32) {
                        moovInfo.movieTimescale = be32(moovBytes, payloadStart + 20).toLong() and 0xFFFF_FFFFL
                        moovInfo.movieDuration = be64(moovBytes, payloadStart + 24)
                        moovInfo.movieDurationOffset = moovStart + payloadStart + 24
                        moovInfo.movieDurationIs64 = true
                    } else if (version == 0 && payloadSize >= 20) {
                        moovInfo.movieTimescale = be32(moovBytes, payloadStart + 12).toLong() and 0xFFFF_FFFFL
                        moovInfo.movieDuration = be32(moovBytes, payloadStart + 16).toLong() and 0xFFFF_FFFFL
                        moovInfo.movieDurationOffset = moovStart + payloadStart + 16
                        moovInfo.movieDurationIs64 = false
                    }
                }
                "trak" -> {
                    parseTrakBox(moovBytes, payloadStart, payloadSize, moovStart, moovInfo)
                }
            }

            pos += boxSize.toInt()
        }
    }

    private fun parseTrakBox(
        bytes: ByteArray,
        trakStart: Int,
        trakSize: Int,
        fileMoovStart: Long,
        moovInfo: MoovInfo,
    ) {
        var pos = trakStart
        val end = trakStart + trakSize
        while (pos + 8 <= end) {
            val size = be32(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val boxSize = size.toLong() and 0xFFFF_FFFFL
            if (boxSize < 8 || pos + boxSize > end) break

            val payloadStart = pos + 8
            val payloadSize = (boxSize - 8).toInt()

            when (type) {
                "tkhd" -> {
                    val version = bytes[payloadStart].toInt() and 0xFF
                    if (version == 1 && payloadSize >= 36) {
                        moovInfo.trackId = be32(bytes, payloadStart + 20).toLong() and 0xFFFF_FFFFL
                        moovInfo.trackDuration = be64(bytes, payloadStart + 28)
                        moovInfo.trackDurationOffset = fileMoovStart + payloadStart + 28
                        moovInfo.trackDurationIs64 = true
                    } else if (version == 0 && payloadSize >= 24) {
                        moovInfo.trackId = be32(bytes, payloadStart + 12).toLong() and 0xFFFF_FFFFL
                        moovInfo.trackDuration = be32(bytes, payloadStart + 20).toLong() and 0xFFFF_FFFFL
                        moovInfo.trackDurationOffset = fileMoovStart + payloadStart + 20
                        moovInfo.trackDurationIs64 = false
                    }
                }
                "mdia" -> {
                    parseMdiaBox(bytes, payloadStart, payloadSize, fileMoovStart, moovInfo)
                }
            }

            pos += boxSize.toInt()
        }
    }

    private fun parseMdiaBox(
        bytes: ByteArray,
        mdiaStart: Int,
        mdiaSize: Int,
        fileMoovStart: Long,
        moovInfo: MoovInfo,
    ) {
        var pos = mdiaStart
        val end = mdiaStart + mdiaSize
        while (pos + 8 <= end) {
            val size = be32(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val boxSize = size.toLong() and 0xFFFF_FFFFL
            if (boxSize < 8 || pos + boxSize > end) break

            val payloadStart = pos + 8
            val payloadSize = (boxSize - 8).toInt()

            if (type == "mdhd") {
                val version = bytes[payloadStart].toInt() and 0xFF
                if (version == 1 && payloadSize >= 32) {
                    moovInfo.mediaTimescale = be32(bytes, payloadStart + 20).toLong() and 0xFFFF_FFFFL
                    moovInfo.mediaDuration = be64(bytes, payloadStart + 24)
                    moovInfo.mediaDurationOffset = fileMoovStart + payloadStart + 24
                    moovInfo.mediaDurationIs64 = true
                } else if (version == 0 && payloadSize >= 20) {
                    moovInfo.mediaTimescale = be32(bytes, payloadStart + 12).toLong() and 0xFFFF_FFFFL
                    moovInfo.mediaDuration = be32(bytes, payloadStart + 16).toLong() and 0xFFFF_FFFFL
                    moovInfo.mediaDurationOffset = fileMoovStart + payloadStart + 16
                    moovInfo.mediaDurationIs64 = false
                }
            }

            pos += boxSize.toInt()
        }
    }

    private fun parseMoofBox(
        raf: RandomAccessFile,
        payloadStart: Long,
        payloadSize: Long,
        expectedTrackId: Long,
        fallbackTime: Long,
    ): Pair<Long, Long> {
        val moofBytes = ByteArray(minOf(payloadSize.toInt(), 512 * 1024))
        raf.seek(payloadStart)
        raf.readFully(moofBytes)

        var fragTime = fallbackTime
        var fragDuration = 0L

        var pos = 0
        while (pos + 8 <= moofBytes.size) {
            val size = be32(moofBytes, pos)
            val type = String(moofBytes, pos + 4, 4, Charsets.US_ASCII)
            val boxSize = size.toLong() and 0xFFFF_FFFFL
            if (boxSize < 8 || pos + boxSize > moofBytes.size) break

            if (type == "traf") {
                var trafPos = pos + 8
                val trafEnd = pos + boxSize.toInt()
                var defaultSampleDuration = 0L

                while (trafPos + 8 <= trafEnd) {
                    val subSize = be32(moofBytes, trafPos)
                    val subType = String(moofBytes, trafPos + 4, 4, Charsets.US_ASCII)
                    val subBoxSize = subSize.toLong() and 0xFFFF_FFFFL
                    if (subBoxSize < 8 || trafPos + subBoxSize > trafEnd) break

                    val subPayload = trafPos + 8
                    val subPayloadSize = (subBoxSize - 8).toInt()

                    when (subType) {
                        "tfhd" -> {
                            if (subPayloadSize >= 8) {
                                val flags = (moofBytes[subPayload + 1].toInt() and 0xFF shl 16) or
                                    (moofBytes[subPayload + 2].toInt() and 0xFF shl 8) or
                                    (moofBytes[subPayload + 3].toInt() and 0xFF)
                                var cursor = subPayload + 8
                                if ((flags and 0x000001) != 0 && cursor + 8 <= trafEnd) cursor += 8
                                if ((flags and 0x000002) != 0 && cursor + 4 <= trafEnd) cursor += 4
                                if ((flags and 0x000008) != 0 && cursor + 4 <= trafEnd) {
                                    defaultSampleDuration = be32(moofBytes, cursor).toLong() and 0xFFFF_FFFFL
                                }
                            }
                        }
                        "tfdt" -> {
                            if (subPayloadSize >= 4) {
                                val version = moofBytes[subPayload].toInt() and 0xFF
                                if (version == 1 && subPayloadSize >= 12) {
                                    fragTime = be64(moofBytes, subPayload + 4)
                                } else if (version == 0 && subPayloadSize >= 8) {
                                    fragTime = be32(moofBytes, subPayload + 4).toLong() and 0xFFFF_FFFFL
                                }
                            }
                        }
                        "trun" -> {
                            if (subPayloadSize >= 8) {
                                val flags = (moofBytes[subPayload + 1].toInt() and 0xFF shl 16) or
                                    (moofBytes[subPayload + 2].toInt() and 0xFF shl 8) or
                                    (moofBytes[subPayload + 3].toInt() and 0xFF)
                                val sampleCount = be32(moofBytes, subPayload + 4)
                                if ((flags and 0x000100) != 0) {
                                    var cursor = subPayload + 8
                                    if ((flags and 0x000001) != 0) cursor += 4
                                    if ((flags and 0x000004) != 0) cursor += 4
                                    var totalDuration = 0L
                                    val entrySize = 4 +
                                        (if ((flags and 0x000200) != 0) 4 else 0) +
                                        (if ((flags and 0x000400) != 0) 4 else 0) +
                                        (if ((flags and 0x000800) != 0) 4 else 0)
                                    for (i in 0 until minOf(sampleCount, 4096)) {
                                        if (cursor + 4 <= trafEnd) {
                                            totalDuration += be32(moofBytes, cursor).toLong() and 0xFFFF_FFFFL
                                            cursor += entrySize
                                        } else break
                                    }
                                    if (totalDuration > 0L) fragDuration = totalDuration
                                } else if (defaultSampleDuration > 0L && sampleCount > 0) {
                                    fragDuration = defaultSampleDuration * sampleCount
                                }
                            }
                        }
                    }

                    trafPos += subBoxSize.toInt()
                }
            }

            pos += boxSize.toInt()
        }

        return Pair(fragTime, fragDuration)
    }

    private fun patchDurationsIfZero(raf: RandomAccessFile, info: MoovInfo, knownDurationMs: Long) {
        if (knownDurationMs <= 0L) return
        val movieScale = info.movieTimescale.takeIf { it > 0 } ?: 1000L
        val movieDuration = (knownDurationMs * movieScale) / 1000L
        val mediaScale = info.mediaTimescale.takeIf { it > 0 } ?: 48000L
        val mediaDuration = (knownDurationMs * mediaScale) / 1000L

        if (info.movieDuration == 0L && info.movieDurationOffset > 0) {
            raf.seek(info.movieDurationOffset)
            if (info.movieDurationIs64) writeBe64(raf, movieDuration)
            else writeBe32(raf, movieDuration.toInt())
        }
        if (info.trackDuration == 0L && info.trackDurationOffset > 0) {
            raf.seek(info.trackDurationOffset)
            if (info.trackDurationIs64) writeBe64(raf, movieDuration)
            else writeBe32(raf, movieDuration.toInt())
        }
        if (info.mediaDuration == 0L && info.mediaDurationOffset > 0) {
            raf.seek(info.mediaDurationOffset)
            if (info.mediaDurationIs64) writeBe64(raf, mediaDuration)
            else writeBe32(raf, mediaDuration.toInt())
        }
    }

    private fun patchDurations(
        raf: RandomAccessFile,
        info: MoovInfo,
        totalMediaDuration: Long,
        knownDurationMs: Long,
    ) {
        val mediaScale = info.mediaTimescale.takeIf { it > 0 } ?: 48000L
        val movieScale = info.movieTimescale.takeIf { it > 0 } ?: 1000L

        val effectiveMediaDur = if (totalMediaDuration > 0L) {
            totalMediaDuration
        } else if (knownDurationMs > 0L) {
            (knownDurationMs * mediaScale) / 1000L
        } else 0L

        if (effectiveMediaDur <= 0L) return

        val movieDuration = (effectiveMediaDur * movieScale) / mediaScale

        if (info.movieDurationOffset > 0) {
            raf.seek(info.movieDurationOffset)
            if (info.movieDurationIs64) writeBe64(raf, movieDuration)
            else writeBe32(raf, movieDuration.toInt())
        }
        if (info.trackDurationOffset > 0) {
            raf.seek(info.trackDurationOffset)
            if (info.trackDurationIs64) writeBe64(raf, movieDuration)
            else writeBe32(raf, movieDuration.toInt())
        }
        if (info.mediaDurationOffset > 0) {
            raf.seek(info.mediaDurationOffset)
            if (info.mediaDurationIs64) writeBe64(raf, effectiveMediaDur)
            else writeBe32(raf, effectiveMediaDur.toInt())
        }
    }

    /**
     * Builds an ISO/IEC 14496-12 compliant `sidx` (Segment Index) box.
     * When parsed by Media3's FragmentedMp4Extractor.parseSidx(), this produces
     * a sample-accurate ChunkIndex seek map.
     */
    fun buildSidxBox(
        trackId: Long,
        timescale: Long,
        earliestPresentationTime: Long,
        fragments: List<FragmentInfo>,
    ): ByteArray {
        val referenceCount = fragments.size
        val totalBoxSize = 32 + referenceCount * 12
        val out = ByteArrayOutputStream(totalBoxSize)

        // Box header
        writeBe32(out, totalBoxSize)
        out.write("sidx".toByteArray(Charsets.US_ASCII))

        // FullBox: version=0 (1 byte), flags=0 (3 bytes)
        out.write(0)
        out.write(0)
        out.write(0)
        out.write(0)

        // reference_ID (4 bytes)
        writeBe32(out, trackId.toInt())

        // timescale (4 bytes)
        writeBe32(out, (timescale.takeIf { it > 0 } ?: 48000L).toInt())

        // earliest_presentation_time (4 bytes, v0)
        writeBe32(out, (earliestPresentationTime and 0xFFFF_FFFFL).toInt())

        // first_offset (4 bytes, v0): distance from the end of this sidx box to the first moof = 0
        writeBe32(out, 0)

        // reserved (2 bytes: 0)
        out.write(0)
        out.write(0)

        // reference_count (2 bytes)
        out.write((referenceCount ushr 8) and 0xFF)
        out.write(referenceCount and 0xFF)

        // References: 12 bytes each
        for (frag in fragments) {
            // reference_type (1 bit = 0) + referenced_size (31 bits)
            val refSize = (frag.totalSize and 0x7FFFFFFF).toInt()
            writeBe32(out, refSize)

            // subsegment_duration (4 bytes)
            val dur = (frag.duration and 0xFFFF_FFFFL).toInt()
            writeBe32(out, dur)

            // starts_with_SAP(1 bit = 1) | SAP_type(3 bits = 1) | SAP_delta_time(28 bits = 0) = 0x90000000
            writeBe32(out, 0x90000000.toInt())
        }

        return out.toByteArray()
    }

    /**
     * Builds an ISO/IEC 14496-12 compliant `mfra` container box consisting of
     * a version 1 `tfra` box (with 64-bit time and 64-bit moof byte offsets)
     * and a terminal `mfro` box.
     */
    fun buildMfraBox(trackId: Long, entries: List<FragmentInfo>): ByteArray {
        val tfraStream = ByteArrayOutputStream()
        tfraStream.write(1) // version 1
        tfraStream.write(0)
        tfraStream.write(0)
        tfraStream.write(0)
        writeBe32(tfraStream, trackId.toInt())
        writeBe32(tfraStream, 0)
        writeBe32(tfraStream, entries.size)
        for (entry in entries) {
            writeBe64(tfraStream, entry.time)
            writeBe64(tfraStream, entry.moofOffset)
            tfraStream.write(1)
            tfraStream.write(1)
            tfraStream.write(1)
        }
        val tfraPayload = tfraStream.toByteArray()
        val tfraBoxSize = 8 + tfraPayload.size

        val mfroBoxSize = MFRO_BOX_SIZE
        val totalMfraSize = 8 + tfraBoxSize + mfroBoxSize

        val out = ByteArrayOutputStream(totalMfraSize)
        writeBe32(out, totalMfraSize)
        out.write("mfra".toByteArray(Charsets.US_ASCII))

        writeBe32(out, tfraBoxSize)
        out.write("tfra".toByteArray(Charsets.US_ASCII))
        out.write(tfraPayload)

        writeBe32(out, mfroBoxSize)
        out.write("mfro".toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write(0)
        out.write(0)
        out.write(0)
        writeBe32(out, totalMfraSize)

        return out.toByteArray()
    }

    private fun copyExactly(input: InputStream, output: OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private fun be32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun be64(bytes: ByteArray, offset: Int): Long {
        if (offset + 8 > bytes.size) return 0L
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun writeBe32(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeBe64(out: ByteArrayOutputStream, value: Long) {
        for (i in 7 downTo 0) {
            out.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }

    private fun writeBe32(raf: RandomAccessFile, value: Int) {
        raf.writeByte((value ushr 24) and 0xFF)
        raf.writeByte((value ushr 16) and 0xFF)
        raf.writeByte((value ushr 8) and 0xFF)
        raf.writeByte(value and 0xFF)
    }

    private fun writeBe64(raf: RandomAccessFile, value: Long) {
        for (i in 7 downTo 0) {
            raf.writeByte(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }

    /**
     * Atomically replaces targetFile with tempFile while guaranteeing zero data loss.
     * 1. Attempts atomic move via java.nio.file.Files.move (StandardCopyOption.ATOMIC_MOVE).
     * 2. If atomic move fails (e.g. cross-filesystem or older storage HAL), executes a
     *    crash-safe 3-phase journaling replacement:
     *    a. targetFile is renamed to backupFile (preserving original data).
     *    b. tempFile is renamed to targetFile.
     *    c. If tempFile rename fails, backupFile is immediately restored to targetFile.
     *    d. Only after targetFile is confirmed intact and non-empty, backupFile is removed.
     */
    fun replaceFileAtomically(tempFile: File, targetFile: File, backupFile: File): Boolean {
        // Step 0: Ensure pre-existing backup integrity before attempting any replacement
        if (backupFile.exists()) {
            if (!targetFile.exists() || targetFile.length() == 0L) {
                Log.w(TAG, "Target missing or empty but backup exists; restoring original from ${backupFile.name}")
                runCatching { targetFile.delete() }
                if (!backupFile.renameTo(targetFile)) {
                    Log.e(TAG, "Failed to restore backup to target; aborting replacement to preserve data")
                    return false
                }
                return false
            } else {
                backupFile.delete()
            }
        }

        // Step 0.5: Pre-flight validation gate - tempFile must exist, be non-empty, and be a valid indexed fMP4
        if (!tempFile.exists() || tempFile.length() == 0L || !isValidIndexedFmp4(tempFile)) {
            Log.w(TAG, "tempFile failed validation before atomic promotion: ${tempFile.name}")
            return false
        }

        // Step 1: Try atomic move if running on supported platform
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            if (backupFile.exists()) {
                runCatching { backupFile.delete() }
            }
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Files.move ATOMIC_MOVE not supported or failed (${e.message}), using journaling fallback")
        }

        // Step 2: 3-Phase Journaling Fallback
        // Phase 2a: Preserve original as backup
        if (!targetFile.renameTo(backupFile)) {
            Log.w(TAG, "Failed to rename original file to backup: ${backupFile.name}")
            return false
        }

        // Phase 2b: Move new file into position
        if (!tempFile.renameTo(targetFile)) {
            Log.w(TAG, "Failed to rename temp file to target; attempting immediate rollback")
            // Phase 2c: Emergency rollback to restore original
            if (!backupFile.renameTo(targetFile)) {
                Log.e(TAG, "CRITICAL: Rollback failed! Backup file preserved at ${backupFile.absolutePath}")
            }
            return false
        }

        // Phase 2d: Verify target exists and is non-empty before deleting backup
        if (targetFile.exists() && targetFile.length() > 0L) {
            runCatching { backupFile.delete() }
            return true
        } else {
            Log.e(TAG, "Target file missing or empty after rename! Restoring from backup")
            runCatching { targetFile.delete() }
            backupFile.renameTo(targetFile)
            return false
        }
    }

    /**
     * Checks if a surviving .bak file exists for the specified file and restores it if the
     * primary file is missing or zero bytes.
     */
    fun recoverFileBackupIfNeeded(file: File): Boolean {
        val parentDir = file.parentFile ?: file.canonicalFile.parentFile ?: return false
        val backupFile = File(parentDir, "${file.name}.bak")
        if (backupFile.exists() && backupFile.length() > 0L) {
            if (!file.exists() || file.length() == 0L) {
                Log.i(TAG, "Recovering surviving backup for ${file.name} prior to processing")
                runCatching { file.delete() }
                return backupFile.renameTo(file)
            }
        }
        return false
    }

    /**
     * Validates that the file contains a structurally valid fragmented MP4
     * with valid box boundaries, sample data references, and container hierarchies.
     *
     * @param file The file to validate.
     * @param requireSidx If true, requires an ISO/IEC 14496-12 `sidx` box preceding the first `moof`.
     */
    fun isValidFmp4(file: File, requireSidx: Boolean = false): Boolean = runCatching {
        if (!file.isFile || file.length() < 32) return false
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var pos = 0L
            val header = ByteArray(16)
            var hasFtyp = false
            var hasMoov = false
            var hasSidx = false
            var hasMoof = false
            var hasMdat = false
            var moovOffset = -1L
            var moovSize = 0L
            var firstMoofOffset = -1L
            var currentMoofOffset = -1L
            var currentMoofSize = 0L
            var sidxOffset = -1L
            var sidxSize = 0L

            data class FragmentPair(
                val moofOffset: Long,
                val moofSize: Long,
                val mdatOffset: Long,
                val mdatSize: Long,
                val mdatHeaderSize: Long
            )
            val fragments = mutableListOf<FragmentPair>()

            while (pos + 8 <= length) {
                raf.seek(pos)
                if (raf.read(header, 0, 8) < 8) return false
                val size32 = be32(header, 0)
                val type = String(header, 4, 4, Charsets.US_ASCII)
                var boxSize = size32.toLong() and 0xFFFF_FFFFL
                var headerSize = 8L
                if (size32 == 1) {
                    if (raf.read(header, 8, 8) < 8) return false
                    boxSize = be64(header, 8)
                    headerSize = 16L
                    if (boxSize < 16L || boxSize > length - pos) return false
                } else if (size32 == 0) {
                    boxSize = length - pos
                }
                if (boxSize < headerSize || pos + boxSize > length || pos + boxSize < pos) return false

                when (type) {
                    "ftyp" -> {
                        if (pos != 0L) return false // ftyp must be the initial box
                        hasFtyp = true
                    }
                    "moov" -> {
                        hasMoov = true
                        moovOffset = pos
                        moovSize = boxSize
                    }
                    "sidx" -> {
                        hasSidx = true
                        if (sidxOffset == -1L) {
                            sidxOffset = pos
                            sidxSize = boxSize
                        }
                    }
                    "moof" -> {
                        hasMoof = true
                        if (firstMoofOffset == -1L) firstMoofOffset = pos
                        currentMoofOffset = pos
                        currentMoofSize = boxSize
                    }
                    "mdat" -> {
                        hasMdat = true
                        if (currentMoofOffset >= 0L) {
                            fragments.add(
                                FragmentPair(
                                    moofOffset = currentMoofOffset,
                                    moofSize = currentMoofSize,
                                    mdatOffset = pos,
                                    mdatSize = boxSize,
                                    mdatHeaderSize = headerSize
                                )
                            )
                            currentMoofOffset = -1L
                        }
                    }
                }
                pos += boxSize
            }

            // 1. Top-level integrity checks
            if (pos != length) return false // trailing unparsed garbage detected
            if (!hasFtyp || !hasMoov || !hasMoof || !hasMdat) return false
            if (requireSidx && (!hasSidx || sidxOffset > firstMoofOffset)) return false
            if (fragments.isEmpty()) return false

            // 2. Validate deep moov hierarchy (mvhd, trak, mdia, mdhd, mvex, trex)
            if (moovSize !in 32..(32 * 1024 * 1024)) return false
            val moovBytes = ByteArray(moovSize.toInt())
            raf.seek(moovOffset)
            raf.readFully(moovBytes)

            var mvhdFound = false
            var trakFound = false
            var mdhdFound = false
            var mvexFound = false
            var trexFound = false
            var parsedTrackId = -1L

            var moovPos = 8
            while (moovPos + 8 <= moovBytes.size) {
                val subSize = be32(moovBytes, moovPos).toLong() and 0xFFFF_FFFFL
                val subType = String(moovBytes, moovPos + 4, 4, Charsets.US_ASCII)
                if (subSize < 8 || moovPos + subSize > moovBytes.size) return false
                val subPayload = moovPos + 8
                val subPayloadSize = (subSize - 8).toInt()

                when (subType) {
                    "mvhd" -> {
                        mvhdFound = true
                        val version = moovBytes[subPayload].toInt() and 0xFF
                        val timescale = if (version == 1 && subPayloadSize >= 32) {
                            be32(moovBytes, subPayload + 20).toLong() and 0xFFFF_FFFFL
                        } else if (version == 0 && subPayloadSize >= 20) {
                            be32(moovBytes, subPayload + 12).toLong() and 0xFFFF_FFFFL
                        } else 0L
                        if (timescale <= 0L) return false
                    }
                    "trak" -> {
                        trakFound = true
                        var trakPos = subPayload
                        val trakEnd = moovPos + subSize.toInt()
                        while (trakPos + 8 <= trakEnd) {
                            val cSize = be32(moovBytes, trakPos).toLong() and 0xFFFF_FFFFL
                            val cType = String(moovBytes, trakPos + 4, 4, Charsets.US_ASCII)
                            if (cSize < 8 || trakPos + cSize > trakEnd) return false
                            val cPayload = trakPos + 8
                            val cPayloadSize = (cSize - 8).toInt()

                            when (cType) {
                                "tkhd" -> {
                                    val version = moovBytes[cPayload].toInt() and 0xFF
                                    if (version == 1 && cPayloadSize >= 36) {
                                        parsedTrackId = be32(moovBytes, cPayload + 20).toLong() and 0xFFFF_FFFFL
                                    } else if (version == 0 && cPayloadSize >= 24) {
                                        parsedTrackId = be32(moovBytes, cPayload + 12).toLong() and 0xFFFF_FFFFL
                                    }
                                }
                                "mdia" -> {
                                    var mdiaPos = cPayload
                                    val mdiaEnd = trakPos + cSize.toInt()
                                    while (mdiaPos + 8 <= mdiaEnd) {
                                        val mSize = be32(moovBytes, mdiaPos).toLong() and 0xFFFF_FFFFL
                                        val mType = String(moovBytes, mdiaPos + 4, 4, Charsets.US_ASCII)
                                        if (mSize < 8 || mdiaPos + mSize > mdiaEnd) return false
                                        if (mType == "mdhd") {
                                            mdhdFound = true
                                            val mPayload = mdiaPos + 8
                                            val mPayloadSize = (mSize - 8).toInt()
                                            val version = moovBytes[mPayload].toInt() and 0xFF
                                            val mediaTimescale = if (version == 1 && mPayloadSize >= 32) {
                                                be32(moovBytes, mPayload + 20).toLong() and 0xFFFF_FFFFL
                                            } else if (version == 0 && mPayloadSize >= 20) {
                                                be32(moovBytes, mPayload + 12).toLong() and 0xFFFF_FFFFL
                                            } else 0L
                                            if (mediaTimescale <= 0L) return false
                                        }
                                        mdiaPos += mSize.toInt()
                                    }
                                }
                            }
                            trakPos += cSize.toInt()
                        }
                    }
                    "mvex" -> {
                        mvexFound = true
                        var mvexPos = subPayload
                        val mvexEnd = moovPos + subSize.toInt()
                        while (mvexPos + 8 <= mvexEnd) {
                            val cSize = be32(moovBytes, mvexPos).toLong() and 0xFFFF_FFFFL
                            val cType = String(moovBytes, mvexPos + 4, 4, Charsets.US_ASCII)
                            if (cSize < 8 || mvexPos + cSize > mvexEnd) return false
                            if (cType == "trex") {
                                trexFound = true
                                val cPayload = mvexPos + 8
                                val cPayloadSize = (cSize - 8).toInt()
                                if (cPayloadSize >= 20) {
                                    val trexTrackId = be32(moovBytes, cPayload + 4).toLong() and 0xFFFF_FFFFL
                                    if (parsedTrackId > 0 && trexTrackId != parsedTrackId) return false
                                }
                            }
                            mvexPos += cSize.toInt()
                        }
                    }
                }
                moovPos += subSize.toInt()
            }

            if (!mvhdFound || !trakFound || !mdhdFound || !mvexFound || !trexFound) return false

            // 3. Deep fragment validation (moof -> traf -> tfhd, tfdt, trun & mdat payload checks)
            for (frag in fragments) {
                if (frag.moofSize !in 16..(16 * 1024 * 1024)) return false
                val moofBytes = ByteArray(frag.moofSize.toInt())
                raf.seek(frag.moofOffset)
                raf.readFully(moofBytes)

                var mfhdFound = false
                var trafFound = false
                var tfhdFound = false
                var tfdtFound = false
                var trunFound = false
                var baseDataOffset = frag.moofOffset
                var sampleDataOffset = -1L
                var totalSampleSize = 0L

                var moofPos = 8
                while (moofPos + 8 <= moofBytes.size) {
                    val subSize = be32(moofBytes, moofPos).toLong() and 0xFFFF_FFFFL
                    val subType = String(moofBytes, moofPos + 4, 4, Charsets.US_ASCII)
                    if (subSize < 8 || moofPos + subSize > moofBytes.size) return false
                    val subPayload = moofPos + 8
                    val subPayloadSize = (subSize - 8).toInt()

                    when (subType) {
                        "mfhd" -> {
                            mfhdFound = true
                            if (subPayloadSize >= 8) {
                                val seq = be32(moofBytes, subPayload + 4)
                                if (seq <= 0) return false
                            } else return false
                        }
                        "traf" -> {
                            trafFound = true
                            var trafPos = subPayload
                            val trafEnd = moofPos + subSize.toInt()
                            var defaultSampleSize = 0L

                            while (trafPos + 8 <= trafEnd) {
                                val cSize = be32(moofBytes, trafPos).toLong() and 0xFFFF_FFFFL
                                val cType = String(moofBytes, trafPos + 4, 4, Charsets.US_ASCII)
                                if (cSize < 8 || trafPos + cSize > trafEnd) return false
                                val cPayload = trafPos + 8
                                val cPayloadSize = (cSize - 8).toInt()

                                when (cType) {
                                    "tfhd" -> {
                                        tfhdFound = true
                                        if (cPayloadSize >= 8) {
                                            val flags = (moofBytes[cPayload + 1].toInt() and 0xFF shl 16) or
                                                (moofBytes[cPayload + 2].toInt() and 0xFF shl 8) or
                                                (moofBytes[cPayload + 3].toInt() and 0xFF)
                                            var cursor = cPayload + 8
                                            if ((flags and 0x000001) != 0 && cursor + 8 <= trafEnd) {
                                                baseDataOffset = be64(moofBytes, cursor)
                                                cursor += 8
                                                if (baseDataOffset < 0 || baseDataOffset > length) return false
                                            }
                                            if ((flags and 0x000002) != 0) cursor += 4
                                            if ((flags and 0x000008) != 0) cursor += 4
                                            if ((flags and 0x000010) != 0 && cursor + 4 <= trafEnd) {
                                                defaultSampleSize = be32(moofBytes, cursor).toLong() and 0xFFFF_FFFFL
                                            }
                                        } else return false
                                    }
                                    "tfdt" -> {
                                        tfdtFound = true
                                        if (cPayloadSize < 8) return false
                                    }
                                    "trun" -> {
                                        trunFound = true
                                        if (cPayloadSize >= 8) {
                                            val flags = (moofBytes[cPayload + 1].toInt() and 0xFF shl 16) or
                                                (moofBytes[cPayload + 2].toInt() and 0xFF shl 8) or
                                                (moofBytes[cPayload + 3].toInt() and 0xFF)
                                            val sampleCount = be32(moofBytes, cPayload + 4)
                                            if (sampleCount <= 0 || sampleCount > 1_000_000) return false
                                            var cursor = cPayload + 8
                                            if ((flags and 0x000001) != 0 && cursor + 4 <= trafEnd) {
                                                val dataOffsetVal = be32(moofBytes, cursor)
                                                sampleDataOffset = baseDataOffset + dataOffsetVal
                                                cursor += 4
                                            } else {
                                                sampleDataOffset = baseDataOffset
                                            }
                                            if ((flags and 0x000004) != 0) cursor += 4

                                            if ((flags and 0x000200) != 0) {
                                                val hasDur = (flags and 0x000100) != 0
                                                val hasFlags = (flags and 0x000400) != 0
                                                val hasCt = (flags and 0x000800) != 0
                                                val entrySize = (if (hasDur) 4 else 0) + 4 + (if (hasFlags) 4 else 0) + (if (hasCt) 4 else 0)
                                                for (s in 0 until sampleCount) {
                                                    val sSizeOffset = cursor + (if (hasDur) 4 else 0)
                                                    if (sSizeOffset + 4 <= trafEnd) {
                                                        totalSampleSize += be32(moofBytes, sSizeOffset).toLong() and 0xFFFF_FFFFL
                                                    } else return false
                                                    cursor += entrySize
                                                }
                                            } else if (defaultSampleSize > 0) {
                                                totalSampleSize = defaultSampleSize * sampleCount
                                            }
                                        } else return false
                                    }
                                }
                                trafPos += cSize.toInt()
                            }
                        }
                    }
                    moofPos += subSize.toInt()
                }

                if (!mfhdFound || !trafFound || !tfhdFound || !tfdtFound || !trunFound) return false

                // Validate that sample data lies strictly inside the corresponding mdat box payload
                val mdatPayloadStart = frag.mdatOffset + frag.mdatHeaderSize
                val mdatPayloadEnd = frag.mdatOffset + frag.mdatSize
                if (sampleDataOffset < mdatPayloadStart || sampleDataOffset > mdatPayloadEnd) return false
                if (sampleDataOffset + totalSampleSize > mdatPayloadEnd || totalSampleSize <= 0) return false
            }

            // 4. Validate sidx structure if present
            if (hasSidx) {
                if (sidxSize < 32) return false
                val sidxBytes = ByteArray(sidxSize.toInt())
                raf.seek(sidxOffset)
                raf.readFully(sidxBytes)
                val version = sidxBytes[8].toInt() and 0xFF
                val refCountOffset = if (version == 0) 30 else 38
                val firstRefOffset = if (version == 0) 32 else 40
                if (sidxSize < firstRefOffset + 12) return false
                val refCount = ((sidxBytes[refCountOffset].toInt() and 0xFF) shl 8) or
                    (sidxBytes[refCountOffset + 1].toInt() and 0xFF)
                if (refCount <= 0 || refCount > 65535) return false

                val refSize = be32(sidxBytes, firstRefOffset).toLong() and 0x7FFFFFFF
                if (refCount == 1 && fragments.size == 1) {
                    val expectedSubsegmentSize = fragments[0].moofSize + fragments[0].mdatSize
                    if (refSize != expectedSubsegmentSize) return false
                }
            }

            true
        }
    }.getOrDefault(false)

    /**
     * Validates that the file contains a structurally valid indexed fragmented MP4
     * with valid box boundaries, sample data references, and an inline `sidx` box.
     */
    fun isValidIndexedFmp4(file: File): Boolean = isValidFmp4(file, requireSidx = true)

    /**
     * Scans for orphaned .bak files from interrupted transactions and restores them.
     */
    fun recoverOrphanedBackups(directory: File) {
        if (!directory.isDirectory) return
        val backups = directory.listFiles { _, name -> name.endsWith(".bak") } ?: return
        for (backup in backups) {
            val originalName = backup.name.removeSuffix(".bak")
            val originalFile = File(directory, originalName)
            if (!originalFile.exists() || originalFile.length() == 0L) {
                Log.i(TAG, "Recovering orphaned backup: ${backup.name} -> $originalName")
                runCatching { originalFile.delete() }
                backup.renameTo(originalFile)
            } else {
                Log.d(TAG, "Cleaning up resolved backup: ${backup.name}")
                runCatching { backup.delete() }
            }
        }
    }
}
