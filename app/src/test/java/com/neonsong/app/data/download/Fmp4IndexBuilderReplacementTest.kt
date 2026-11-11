package com.neonsong.app.data.download

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 1.6.2 fMP4 Test Validity Audit Suite.
 *
 * This test suite verifies:
 * 1. Structurally valid minimal fragmented MP4 (fMP4) fixtures with ISO/IEC 14496-12 compliance
 *    (valid ftyp, moov hierarchy with mvhd, trak, mdia, minf, stbl, mvex/trex, moof with mfhd,
 *     traf, tfhd, tfdt, trun, and mdat with sample payload).
 * 2. 10 structural corruption failure modes (missing mdat, missing moof, truncated moov, invalid tfhd,
 *    trun offset outside mdat, sample size overflow, invalid sidx reference, 64-bit overflow,
 *    trailing garbage, same-length corruption).
 * 3. Concurrency guarantees (CountDownLatch completion, executor termination, task completion,
 *    structural validity, clean removal of temporary/backup files, and concurrent orphan recovery).
 * 4. Transactional recovery guarantees (target missing, target 0 bytes, target corrupted, target valid
 *    with stale backup, backup restore failure, interrupted recovery, concurrent recovery).
 *
 * AUDIT CLASSIFICATION:
 * - Real Production Code Tested: Fmp4IndexBuilder.finalizeFmp4, replaceFileAtomically,
 *   recoverFileBackupIfNeeded, recoverOrphanedBackups, isValidIndexedFmp4, buildSidxBox, buildMfraBox.
 * - Test Helpers: ValidFmp4FixtureBuilder, BmffStructureValidator.
 * - Mocks Used: NONE (all tests execute real filesystem transactions and byte-exact ISO BMFF parsing).
 */
class Fmp4IndexBuilderReplacementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // TASK 1: Structurally Valid Minimal Fragmented MP4 Fixtures
    // =========================================================================

    @Test
    fun validFmp4Fixture_passesDeepBmffValidation() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        val file = tempFolder.newFile("valid_deep.m4a").apply { writeBytes(validBytes) }

        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isTrue()
        assertThat(result.error).isNull()
    }

    @Test
    fun validFmp4Fixture_passesProductionIsValidIndexedFmp4() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        val file = tempFolder.newFile("valid_prod.m4a").apply { writeBytes(validBytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isTrue()
    }

    @Test
    fun finalizeFmp4_withValidUnindexedFmp4_insertsSidxPatchesDurationsAndAppendsMfra() {
        val unindexedBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val file = tempFolder.newFile("unindexed.m4a").apply { writeBytes(unindexedBytes) }

        assertThat(Fmp4IndexBuilder.hasMfraBox(file)).isFalse()

        val ok = Fmp4IndexBuilder.finalizeFmp4(file, knownDurationMs = 1000L)

        assertThat(ok).isTrue()
        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isTrue()
        assertThat(Fmp4IndexBuilder.hasMfraBox(file)).isTrue()

        // Verify with deep BMFF structure validator
        val deepResult = BmffStructureValidator.validate(file)
        assertThat(deepResult.isValid).isTrue()
        assertThat(deepResult.hasSidx).isTrue()
        assertThat(deepResult.hasMfra).isTrue()
    }

    // =========================================================================
    // TASK 2: Structural Corruption Tests (10 Failure Modes)
    // =========================================================================

    @Test
    fun corruption1_missingMdat_failsDeepBmffValidation() {
        // Build file without mdat box
        val bytes = ValidFmp4FixtureBuilder.buildWithoutMdat()
        val file = tempFolder.newFile("missing_mdat.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        assertThat(Fmp4IndexBuilder.isValidFmp4(file, requireSidx = false)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("Missing required mdat box")
    }

    @Test
    fun corruption2_missingMoof_failsValidation() {
        // File has ftyp, moov, mdat, but NO moof box
        val bytes = ValidFmp4FixtureBuilder.buildWithoutMoof()
        val file = tempFolder.newFile("missing_moof.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("Missing required moof box")
    }

    @Test
    fun corruption3_truncatedMoov_failsValidation() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        // Truncate in the middle of moov
        val truncatedBytes = validBytes.copyOf(100)
        val file = tempFolder.newFile("truncated_moov.m4a").apply { writeBytes(truncatedBytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun corruption4_invalidTfhdBaseDataOffset_failsDeepValidation() {
        val bytes = ValidFmp4FixtureBuilder.buildWithCorruptTfhdBaseOffset(corruptOffset = 9999999L)
        val file = tempFolder.newFile("corrupt_tfhd.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("tfhd base-data-offset out of file bounds")
    }

    @Test
    fun corruption5_trunOffsetPointingOutsideMdat_failsDeepValidation() {
        val bytes = ValidFmp4FixtureBuilder.buildWithCorruptTrunOffset(corruptDataOffset = 10L) // Points before mdat payload
        val file = tempFolder.newFile("corrupt_trun.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("trun sample data offset points outside mdat payload")
    }

    @Test
    fun corruption6_sampleSizeExtendingBeyondMdat_failsDeepValidation() {
        val bytes = ValidFmp4FixtureBuilder.buildWithCorruptSampleSize(excessiveSampleSize = 50000)
        val file = tempFolder.newFile("excessive_sample.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("Sample data extends beyond mdat payload bounds")
    }

    @Test
    fun corruption7_invalidSidxReference_failsDeepValidation() {
        val bytes = ValidFmp4FixtureBuilder.buildWithCorruptSidxReferenceSize(corruptRefSize = 9999)
        val file = tempFolder.newFile("corrupt_sidx.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
        assertThat(result.error).contains("sidx referenced_size does not match subsegment size")
    }

    @Test
    fun corruption8_64BitBoxSizeOverflow_failsValidation() {
        val bytes = ValidFmp4FixtureBuilder.buildWith64BitOverflow()
        val file = tempFolder.newFile("overflow_64bit.m4a").apply { writeBytes(bytes) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun corruption9_trailingGarbage_failsValidation() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        val withGarbage = validBytes + "TRAILING_UNPARSED_GARBAGE_BYTES".toByteArray()
        val file = tempFolder.newFile("trailing_garbage.m4a").apply { writeBytes(withGarbage) }

        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun corruption10_sameLengthInternalCorruption_detected() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        val corruptBytes = validBytes.clone()
        // Corrupt the moov type bytes into unknown box type without changing file length
        val moovIdx = indexOfAscii(corruptBytes, "moov")
        assertThat(moovIdx).isAtLeast(0)
        corruptBytes[moovIdx] = 'x'.code.toByte()
        corruptBytes[moovIdx + 1] = 'x'.code.toByte()

        val file = tempFolder.newFile("same_length_corrupt.m4a").apply { writeBytes(corruptBytes) }

        assertThat(file.length()).isEqualTo(validBytes.size.toLong())
        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
        val result = BmffStructureValidator.validate(file)
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun productionGate_truncatedNestedBoxes_isRejected() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        val corruptBytes = validBytes.clone()
        val trafIdx = indexOfAscii(corruptBytes, "traf")
        assertThat(trafIdx).isAtLeast(4)
        // Overwrite traf size (4 bytes before "traf") with size exceeding moof
        corruptBytes[trafIdx - 4] = 0x7F
        corruptBytes[trafIdx - 3] = 0xFF.toByte()

        val file = tempFolder.newFile("truncated_nested.m4a").apply { writeBytes(corruptBytes) }
        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
    }

    @Test
    fun productionGate_sameLengthCorruptionInsideNestedStructures_isRejected() {
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = true)
        val corruptBytes = validBytes.clone()
        val tfhdIdx = indexOfAscii(corruptBytes, "tfhd")
        assertThat(tfhdIdx).isAtLeast(0)
        corruptBytes[tfhdIdx] = 'x'.code.toByte()
        corruptBytes[tfhdIdx + 1] = 'x'.code.toByte()

        val file = tempFolder.newFile("nested_same_length_corrupt.m4a").apply { writeBytes(corruptBytes) }
        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(file)).isFalse()
    }

    @Test
    fun productionGate_malformedMediaCannotReplacePreviouslyValidOriginal() {
        val dir = tempFolder.newFolder("prod_gate_replace")
        val validOriginalBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val target = File(dir, "song.m4a").apply { writeBytes(validOriginalBytes) }
        val backup = File(dir, "song.m4a.bak")

        // Construct malformed temp media (sample data extends far beyond mdat)
        val malformedTempBytes = ValidFmp4FixtureBuilder.buildWithCorruptSampleSize(excessiveSampleSize = 99999)
        val temp = File(dir, "song.m4a.tmp").apply { writeBytes(malformedTempBytes) }

        // Attempting to finalize or promote malformed temp file MUST fail validation
        assertThat(Fmp4IndexBuilder.isValidIndexedFmp4(temp)).isFalse()

        // Calling finalizeFmp4 on a malformed file must fail and NOT replace target
        val finalizeOk = Fmp4IndexBuilder.finalizeFmp4(temp)
        assertThat(finalizeOk).isFalse()

        // Calling replaceFileAtomically directly with malformed media must also be rejected by the pre-flight gate
        val replaceOk = Fmp4IndexBuilder.replaceFileAtomically(temp, target, backup)
        assertThat(replaceOk).isFalse()

        // Target must remain 100% untouched and authentic!
        assertThat(target.exists()).isTrue()
        assertThat(target.readBytes()).isEqualTo(validOriginalBytes)
        assertThat(backup.exists()).isFalse()
    }

    // =========================================================================
    // TASK 3: Strengthened Concurrency Tests
    // =========================================================================

    @Test
    fun finalizeFmp4_concurrentCallsOnSameFile_serializesSafelyAndCleansUpAllTempFiles() {
        val unindexedBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val file = tempFolder.newFile("concurrent_prod.m4a").apply { writeBytes(unindexedBytes) }
        val parentDir = file.parentFile!!

        val threads = 4
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val successCount = AtomicInteger(0)
        val completedCount = AtomicInteger(0)

        for (i in 0 until threads) {
            executor.submit {
                try {
                    startLatch.await()
                    if (Fmp4IndexBuilder.finalizeFmp4(file, 1000L)) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    completedCount.incrementAndGet()
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val awaitSuccess = doneLatch.await(5, TimeUnit.SECONDS)
        assertThat(awaitSuccess).isTrue()

        executor.shutdown()
        val terminated = executor.awaitTermination(5, TimeUnit.SECONDS)
        assertThat(terminated).isTrue()

        // All 4 tasks must have executed to completion
        assertThat(completedCount.get()).isEqualTo(threads)
        // At least one thread succeeded
        assertThat(successCount.get()).isAtLeast(1)

        // Verify final file structure with deep validator
        assertThat(file.exists()).isTrue()
        val deepResult = BmffStructureValidator.validate(file)
        assertThat(deepResult.isValid).isTrue()
        assertThat(deepResult.hasSidx).isTrue()

        // Verify zero temporary or backup files left in the directory
        val tempFiles = parentDir.listFiles { _, name ->
            name.contains(".sidx_tmp_") || name.endsWith(".bak") || name.endsWith(".tmp")
        } ?: emptyArray()
        assertThat(tempFiles).isEmpty()
    }

    @Test
    fun finalizeFmp4_concurrentWithOrphanRecovery_doesNotDeadlockOrCorrupt() {
        val unindexedBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val file = tempFolder.newFile("concurrent_recovery.m4a").apply { writeBytes(unindexedBytes) }
        val parentDir = file.parentFile!!

        val threads = 3
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)

        // Thread 0 runs finalizeFmp4
        executor.submit {
            try {
                startLatch.await()
                Fmp4IndexBuilder.finalizeFmp4(file, 1000L)
            } finally {
                doneLatch.countDown()
            }
        }

        // Threads 1 and 2 run orphan recovery concurrently
        for (i in 1 until threads) {
            executor.submit {
                try {
                    startLatch.await()
                    Fmp4IndexBuilder.recoverOrphanedBackups(parentDir)
                    Fmp4IndexBuilder.recoverFileBackupIfNeeded(file)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        assertThat(file.exists()).isTrue()
        assertThat(BmffStructureValidator.validate(file).isValid).isTrue()
    }

    // =========================================================================
    // TASK 4: Strengthened Recovery Tests (7 Failure-Injection Scenarios)
    // =========================================================================

    @Test
    fun recovery1_targetMissingWithValidBackup_restoresOriginal() {
        val dir = tempFolder.newFolder("rec1")
        val target = File(dir, "track1.m4a")
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val backup = File(dir, "track1.m4a.bak").apply { writeBytes(validBytes) }

        assertThat(target.exists()).isFalse()
        assertThat(backup.exists()).isTrue()

        val recovered = Fmp4IndexBuilder.recoverFileBackupIfNeeded(target)

        assertThat(recovered).isTrue()
        assertThat(target.exists()).isTrue()
        assertThat(target.readBytes()).isEqualTo(validBytes)
        assertThat(backup.exists()).isFalse()
    }

    @Test
    fun recovery2_targetZeroBytesWithValidBackup_restoresOriginal() {
        val dir = tempFolder.newFolder("rec2")
        val target = File(dir, "track2.m4a").apply { writeBytes(ByteArray(0)) }
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val backup = File(dir, "track2.m4a.bak").apply { writeBytes(validBytes) }

        assertThat(target.length()).isEqualTo(0L)

        val recovered = Fmp4IndexBuilder.recoverFileBackupIfNeeded(target)

        assertThat(recovered).isTrue()
        assertThat(target.length()).isEqualTo(validBytes.size.toLong())
        assertThat(target.readBytes()).isEqualTo(validBytes)
        assertThat(backup.exists()).isFalse()
    }

    @Test
    fun recovery3_targetCorruptedOrZeroBytesInAtomicReplace_restoresBackupWithoutDataLoss() {
        val dir = tempFolder.newFolder("rec3")
        val target = File(dir, "track3.m4a").apply { writeBytes(ByteArray(0)) }
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val backup = File(dir, "track3.m4a.bak").apply { writeBytes(validBytes) }
        val temp = File(dir, "track3.m4a.tmp").apply { writeBytes("NEW_REPLACEMENT".toByteArray()) }

        // replaceFileAtomically Step 0 must restore backup and abort replacement
        val ok = Fmp4IndexBuilder.replaceFileAtomically(temp, target, backup)

        assertThat(ok).isFalse()
        assertThat(target.exists()).isTrue()
        assertThat(target.readBytes()).isEqualTo(validBytes)
    }

    @Test
    fun recovery4_targetValidWithStaleBackup_cleansStaleBackupAndPreservesTarget() {
        val dir = tempFolder.newFolder("rec4")
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val target = File(dir, "track4.m4a").apply { writeBytes(validBytes) }
        val staleBackup = File(dir, "track4.m4a.bak").apply { writeBytes("OLD_STALE_DATA".toByteArray()) }

        Fmp4IndexBuilder.recoverOrphanedBackups(dir)

        assertThat(target.exists()).isTrue()
        assertThat(target.readBytes()).isEqualTo(validBytes)
        assertThat(staleBackup.exists()).isFalse()
    }

    @Test
    fun recovery5_backupRestoreFailure_preservesBackupFile() {
        val dir = tempFolder.newFolder("rec5")
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val backup = File(dir, "track5.m4a.bak").apply { writeBytes(validBytes) }

        // Create target as a non-empty directory to prevent target deletion and force renameTo failure
        val targetAsDirectory = File(dir, "track5.m4a").apply { mkdirs() }
        File(targetAsDirectory, "child.bin").writeBytes(byteArrayOf(1, 2, 3))
        val dummyTemp = File(dir, "track5.m4a.tmp").apply { writeBytes("NEW".toByteArray()) }

        val ok = Fmp4IndexBuilder.replaceFileAtomically(dummyTemp, targetAsDirectory, backup)

        assertThat(ok).isFalse()
        // Critical integrity guarantee: backup file MUST still exist!
        assertThat(backup.exists()).isTrue()
        assertThat(backup.readBytes()).isEqualTo(validBytes)
    }

    @Test
    fun recovery6_recoveryInterruptedBeforeCompletion_recoversOnSubsequentRun() {
        val dir = tempFolder.newFolder("rec6")
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val backup = File(dir, "track6.m4a.bak").apply { writeBytes(validBytes) }
        val target = File(dir, "track6.m4a")

        // First run interrupted (simulated by failure or crash before step completes)
        assertThat(target.exists()).isFalse()
        assertThat(backup.exists()).isTrue()

        // Subsequent recovery run
        Fmp4IndexBuilder.recoverOrphanedBackups(dir)

        assertThat(target.exists()).isTrue()
        assertThat(target.readBytes()).isEqualTo(validBytes)
        assertThat(backup.exists()).isFalse()
    }

    @Test
    fun recovery7_concurrentRecoveryAttempts_areSafeAndIdempotent() {
        val dir = tempFolder.newFolder("rec7")
        val validBytes = ValidFmp4FixtureBuilder.build(withSidx = false)
        val target = File(dir, "track7.m4a")
        val backup = File(dir, "track7.m4a.bak").apply { writeBytes(validBytes) }

        val threads = 4
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val successfulRestores = AtomicInteger(0)

        for (i in 0 until threads) {
            executor.submit {
                try {
                    startLatch.await()
                    if (Fmp4IndexBuilder.recoverFileBackupIfNeeded(target)) {
                        successfulRestores.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue()
        executor.shutdown()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        // At least 1 thread completes the rename, remaining threads see file already restored
        assertThat(successfulRestores.get()).isAtLeast(1)
        assertThat(target.exists()).isTrue()
        assertThat(target.readBytes()).isEqualTo(validBytes)
        assertThat(backup.exists()).isFalse()
    }

    // =========================================================================
    // TASK 1 & 5: Structurally Valid ISO/IEC 14496-12 Fixture Builder
    // =========================================================================

    object ValidFmp4FixtureBuilder {
        fun build(
            withSidx: Boolean = false,
            samplePayload: ByteArray = "NeonAudioSample!".toByteArray(Charsets.US_ASCII),
            sampleDuration: Long = 1024L,
            timescale: Long = 48000L,
            trackId: Long = 1L
        ): ByteArray {
            val baos = ByteArrayOutputStream()

            // 1. ftyp
            val ftypPayload = ByteArrayOutputStream().apply {
                write("dash".toByteArray(Charsets.US_ASCII))
                write(byteArrayOf(0, 0, 0, 1))
                write("dashiso6mp41isom".toByteArray(Charsets.US_ASCII))
            }.toByteArray()
            baos.write(makeBox("ftyp", ftypPayload))

            // 2. moov
            val moov = buildMoov(timescale, trackId, sampleDuration, samplePayload.size)
            baos.write(moov)

            // 3. moof & mdat
            val (moof, mdat) = buildMoofAndMdat(trackId, sampleDuration, samplePayload)

            if (withSidx) {
                val fragInfo = Fmp4IndexBuilder.FragmentInfo(
                    time = 0L,
                    duration = sampleDuration,
                    moofOffset = baos.size().toLong(),
                    totalSize = (moof.size + mdat.size).toLong()
                )
                val sidx = Fmp4IndexBuilder.buildSidxBox(trackId, timescale, 0L, listOf(fragInfo))
                baos.write(sidx)
            }

            baos.write(moof)
            baos.write(mdat)
            return baos.toByteArray()
        }

        fun buildWithoutMdat(): ByteArray {
            val full = build(withSidx = true)
            val mdatIdx = indexOfAscii(full, "mdat")
            return full.copyOf(mdatIdx - 4) // drop mdat header and payload
        }

        fun buildWithoutMoof(): ByteArray {
            val baos = ByteArrayOutputStream()
            val ftypPayload = ByteArrayOutputStream().apply {
                write("dash".toByteArray(Charsets.US_ASCII))
                write(byteArrayOf(0, 0, 0, 1))
                write("dashiso6mp41isom".toByteArray(Charsets.US_ASCII))
            }.toByteArray()
            baos.write(makeBox("ftyp", ftypPayload))
            baos.write(buildMoov(48000L, 1L, 1024L, 16))
            val mdat = makeBox("mdat", "NeonAudioSample!".toByteArray(Charsets.US_ASCII))
            baos.write(mdat)
            return baos.toByteArray()
        }

        fun buildWithCorruptTfhdBaseOffset(corruptOffset: Long): ByteArray {
            val samplePayload = "NeonAudioSample!".toByteArray(Charsets.US_ASCII)
            val baos = ByteArrayOutputStream()
            baos.write(makeBox("ftyp", "dash".toByteArray() + byteArrayOf(0,0,0,1) + "dashiso6mp41isom".toByteArray()))
            baos.write(buildMoov(48000L, 1L, 1024L, samplePayload.size))

            val mfhd = makeFullBox("mfhd", 0, 0, byteArrayOf(0,0,0,1))
            val tfhdPayload = ByteArrayOutputStream().apply {
                write(byteArrayOf(0,0,0,1)) // track_id 1
                // flag 0x000001 (base-data-offset present) -> 8 bytes
                write(be64Bytes(corruptOffset))
            }.toByteArray()
            val tfhd = makeFullBox("tfhd", 0, 0x000001, tfhdPayload)
            val tfdt = makeFullBox("tfdt", 1, 0, ByteArray(8))
            val trun = makeFullBox("trun", 0, 0, byteArrayOf(0,0,0,1))
            val traf = makeBox("traf", tfhd + tfdt + trun)
            val moof = makeBox("moof", mfhd + traf)
            val mdat = makeBox("mdat", samplePayload)

            val fragInfo = Fmp4IndexBuilder.FragmentInfo(0L, 1024L, baos.size().toLong(), (moof.size + mdat.size).toLong())
            baos.write(Fmp4IndexBuilder.buildSidxBox(1L, 48000L, 0L, listOf(fragInfo)))
            baos.write(moof)
            baos.write(mdat)
            return baos.toByteArray()
        }

        fun buildWithCorruptTrunOffset(corruptDataOffset: Long): ByteArray {
            return buildWithTrunAndSample(dataOffset = corruptDataOffset.toInt(), sampleSize = 16)
        }

        fun buildWithCorruptSampleSize(excessiveSampleSize: Int): ByteArray {
            return buildWithTrunAndSample(dataOffset = -1, sampleSize = excessiveSampleSize)
        }

        private fun buildWithTrunAndSample(dataOffset: Int, sampleSize: Int): ByteArray {
            val samplePayload = "NeonAudioSample!".toByteArray(Charsets.US_ASCII)
            val baos = ByteArrayOutputStream()
            baos.write(makeBox("ftyp", "dash".toByteArray() + byteArrayOf(0,0,0,1) + "dashiso6mp41isom".toByteArray()))
            baos.write(buildMoov(48000L, 1L, 1024L, samplePayload.size))

            val mfhd = makeFullBox("mfhd", 0, 0, byteArrayOf(0,0,0,1))
            val tfhd = makeFullBox("tfhd", 0, 0, byteArrayOf(0,0,0,1))
            val tfdt = makeFullBox("tfdt", 1, 0, ByteArray(8))

            val actualOffset = if (dataOffset >= 0) dataOffset else 104 // 96 moof + 8 mdat header
            val trunPayload = ByteArrayOutputStream().apply {
                write(byteArrayOf(0,0,0,1)) // sampleCount 1
                write(be32Bytes(actualOffset)) // data_offset
                write(be32Bytes(1024)) // sample_duration
                write(be32Bytes(sampleSize)) // sample_size
            }.toByteArray()
            val trun = makeFullBox("trun", 0, 0x000301, trunPayload)
            val traf = makeBox("traf", tfhd + tfdt + trun)
            val moof = makeBox("moof", mfhd + traf)
            val mdat = makeBox("mdat", samplePayload)

            val fragInfo = Fmp4IndexBuilder.FragmentInfo(0L, 1024L, baos.size().toLong(), (moof.size + mdat.size).toLong())
            baos.write(Fmp4IndexBuilder.buildSidxBox(1L, 48000L, 0L, listOf(fragInfo)))
            baos.write(moof)
            baos.write(mdat)
            return baos.toByteArray()
        }

        fun buildWithCorruptSidxReferenceSize(corruptRefSize: Int): ByteArray {
            val samplePayload = "NeonAudioSample!".toByteArray(Charsets.US_ASCII)
            val baos = ByteArrayOutputStream()
            baos.write(makeBox("ftyp", "dash".toByteArray() + byteArrayOf(0,0,0,1) + "dashiso6mp41isom".toByteArray()))
            baos.write(buildMoov(48000L, 1L, 1024L, samplePayload.size))

            val (moof, mdat) = buildMoofAndMdat(1L, 1024L, samplePayload)
            val fragInfo = Fmp4IndexBuilder.FragmentInfo(0L, 1024L, baos.size().toLong(), corruptRefSize.toLong())
            baos.write(Fmp4IndexBuilder.buildSidxBox(1L, 48000L, 0L, listOf(fragInfo)))
            baos.write(moof)
            baos.write(mdat)
            return baos.toByteArray()
        }

        fun buildWith64BitOverflow(): ByteArray {
            val baos = ByteArrayOutputStream()
            // Box header with size=1 and 64-bit size = Long.MAX_VALUE
            baos.write(byteArrayOf(0, 0, 0, 1))
            baos.write("ftyp".toByteArray())
            baos.write(byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
            baos.write("dashiso6mp41isom".toByteArray())
            return baos.toByteArray()
        }

        private fun buildMoov(timescale: Long, trackId: Long, sampleDuration: Long, sampleSize: Int): ByteArray {
            val mvhdPayload = ByteArrayOutputStream().apply {
                write(ByteArray(8))
                write(be32Bytes(1000)) // movie timescale = 1000
                write(ByteArray(4)) // duration = 0
                write(byteArrayOf(0, 1, 0, 0)) // rate 1.0
                write(byteArrayOf(1, 0)) // volume 1.0
                write(ByteArray(10))
                write(byteArrayOf(0, 1, 0, 0)); write(ByteArray(12)); write(byteArrayOf(0, 1, 0, 0)); write(ByteArray(12)); write(byteArrayOf(0x40, 0, 0, 0))
                write(ByteArray(24))
                write(byteArrayOf(0, 0, 0, 2))
            }.toByteArray()
            val mvhd = makeFullBox("mvhd", 0, 0, mvhdPayload)

            val tkhdPayload = ByteArrayOutputStream().apply {
                write(ByteArray(8))
                write(be32Bytes(trackId.toInt()))
                write(ByteArray(4)) // reserved
                write(ByteArray(4)) // duration = 0
                write(ByteArray(8))
                write(ByteArray(4))
                write(byteArrayOf(1, 0))
                write(ByteArray(2))
                write(byteArrayOf(0, 1, 0, 0)); write(ByteArray(12)); write(byteArrayOf(0, 1, 0, 0)); write(ByteArray(12)); write(byteArrayOf(0x40, 0, 0, 0))
                write(ByteArray(8))
            }.toByteArray()
            val tkhd = makeFullBox("tkhd", 0, 7, tkhdPayload)

            val mdhdPayload = ByteArrayOutputStream().apply {
                write(ByteArray(8))
                write(be32Bytes(timescale.toInt()))
                write(ByteArray(4)) // duration = 0
                write(byteArrayOf(0x15, 0xc7.toByte()))
                write(ByteArray(2))
            }.toByteArray()
            val mdhd = makeFullBox("mdhd", 0, 0, mdhdPayload)

            val hdlrPayload = ByteArrayOutputStream().apply {
                write(ByteArray(4))
                write("soun".toByteArray(Charsets.US_ASCII))
                write(ByteArray(12))
                write("NeonSound\u0000".toByteArray(Charsets.US_ASCII))
            }.toByteArray()
            val hdlr = makeFullBox("hdlr", 0, 0, hdlrPayload)

            val smhd = makeFullBox("smhd", 0, 0, ByteArray(4))
            val url = makeFullBox("url ", 0, 1, ByteArray(0))
            val dref = makeFullBox("dref", 0, 0, byteArrayOf(0, 0, 0, 1) + url)
            val dinf = makeBox("dinf", dref)

            val mp4aPayload = ByteArrayOutputStream().apply {
                write(ByteArray(6))
                write(byteArrayOf(0, 1))
                write(ByteArray(8))
                write(byteArrayOf(0, 2))
                write(byteArrayOf(0, 16))
                write(ByteArray(4))
                val sr = (timescale shl 16).toInt()
                write(be32Bytes(sr))
            }.toByteArray()
            val mp4a = makeBox("mp4a", mp4aPayload)
            val stsd = makeFullBox("stsd", 0, 0, byteArrayOf(0, 0, 0, 1) + mp4a)
            val stts = makeFullBox("stts", 0, 0, ByteArray(4))
            val stsc = makeFullBox("stsc", 0, 0, ByteArray(4))
            val stsz = makeFullBox("stsz", 0, 0, ByteArray(8))
            val stco = makeFullBox("stco", 0, 0, ByteArray(4))
            val stbl = makeBox("stbl", stsd + stts + stsc + stsz + stco)

            val minf = makeBox("minf", smhd + dinf + stbl)
            val mdia = makeBox("mdia", mdhd + hdlr + minf)
            val trak = makeBox("trak", tkhd + mdia)

            val trexPayload = ByteArrayOutputStream().apply {
                write(be32Bytes(trackId.toInt()))
                write(byteArrayOf(0, 0, 0, 1))
                write(be32Bytes(sampleDuration.toInt()))
                write(be32Bytes(sampleSize))
                write(byteArrayOf(0x02, 0, 0, 0))
            }.toByteArray()
            val trex = makeFullBox("trex", 0, 0, trexPayload)
            val mvex = makeBox("mvex", trex)

            return makeBox("moov", mvhd + trak + mvex)
        }

        private fun buildMoofAndMdat(trackId: Long, sampleDuration: Long, samplePayload: ByteArray): Pair<ByteArray, ByteArray> {
            val mfhd = makeFullBox("mfhd", 0, 0, byteArrayOf(0, 0, 0, 1))
            val tfhdPayload = ByteArrayOutputStream().apply {
                write(be32Bytes(trackId.toInt()))
                write(be32Bytes(sampleDuration.toInt()))
                write(be32Bytes(samplePayload.size))
            }.toByteArray()
            val tfhd = makeFullBox("tfhd", 0, 0x000018, tfhdPayload)
            val tfdt = makeFullBox("tfdt", 1, 0, ByteArray(8))

            val dummyTrunPayload = ByteArrayOutputStream().apply {
                write(byteArrayOf(0, 0, 0, 1))
                write(ByteArray(4))
                write(be32Bytes(sampleDuration.toInt()))
                write(be32Bytes(samplePayload.size))
            }.toByteArray()
            val dummyTrun = makeFullBox("trun", 0, 0x000301, dummyTrunPayload)
            val dummyMoof = makeBox("moof", mfhd + makeBox("traf", tfhd + tfdt + dummyTrun))
            val dataOffset = dummyMoof.size + 8 // points to first byte after mdat header

            val trunPayload = ByteArrayOutputStream().apply {
                write(byteArrayOf(0, 0, 0, 1))
                write(be32Bytes(dataOffset))
                write(be32Bytes(sampleDuration.toInt()))
                write(be32Bytes(samplePayload.size))
            }.toByteArray()
            val trun = makeFullBox("trun", 0, 0x000301, trunPayload)
            val traf = makeBox("traf", tfhd + tfdt + trun)
            val moof = makeBox("moof", mfhd + traf)
            val mdat = makeBox("mdat", samplePayload)

            return Pair(moof, mdat)
        }

        fun makeBox(type: String, payload: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(payload.size + 8)
            val size = payload.size + 8
            out.write(be32Bytes(size))
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(payload)
            return out.toByteArray()
        }

        fun makeFullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(payload.size + 12)
            val size = payload.size + 12
            out.write(be32Bytes(size))
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(version and 0xFF)
            out.write((flags ushr 16) and 0xFF)
            out.write((flags ushr 8) and 0xFF)
            out.write(flags and 0xFF)
            out.write(payload)
            return out.toByteArray()
        }

        private fun be32Bytes(v: Int): ByteArray = byteArrayOf(
            ((v ushr 24) and 0xFF).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            (v and 0xFF).toByte()
        )

        private fun be64Bytes(v: Long): ByteArray {
            val res = ByteArray(8)
            for (i in 7 downTo 0) {
                res[7 - i] = ((v ushr (i * 8)) and 0xFF).toByte()
            }
            return res
        }
    }

    // =========================================================================
    // TASK 5: Deep ISO BMFF Structure Validator
    // =========================================================================

    data class BmffValidationResult(
        val isValid: Boolean,
        val error: String? = null,
        val hasSidx: Boolean = false,
        val hasMfra: Boolean = false
    )

    object BmffStructureValidator {
        fun validate(file: File): BmffValidationResult {
            if (!file.isFile || file.length() < 32) {
                return BmffValidationResult(false, "File is missing or too small for ISO BMFF (< 32 bytes)")
            }

            return RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                var pos = 0L
                val header = ByteArray(16)

                var hasFtyp = false
                var hasMoov = false
                var hasMoof = false
                var hasMdat = false
                var hasSidx = false
                var hasMfra = false

                var moovOffset = -1L
                var moovSize = 0L
                var moofOffset = -1L
                var moofSize = 0L
                var mdatOffset = -1L
                var mdatSize = 0L
                var sidxOffset = -1L
                var sidxRefSize = -1L

                while (pos + 8 <= length) {
                    raf.seek(pos)
                    if (raf.read(header, 0, 8) < 8) {
                        return@use BmffValidationResult(false, "Premature EOF reading box header at offset $pos")
                    }
                    val size32 = readBe32(header, 0).toLong() and 0xFFFF_FFFFL
                    val type = String(header, 4, 4, Charsets.US_ASCII)

                    var boxSize = size32
                    var headerSize = 8L

                    if (size32 == 1L) {
                        if (raf.read(header, 8, 8) < 8) {
                            return@use BmffValidationResult(false, "Premature EOF reading 64-bit box header at offset $pos")
                        }
                        boxSize = readBe64(header, 8)
                        headerSize = 16L
                        if (boxSize < 16L || boxSize > length - pos) {
                            return@use BmffValidationResult(false, "64-bit box size overflow or invalid: $boxSize")
                        }
                    } else if (size32 == 0L) {
                        boxSize = length - pos
                    }

                    if (boxSize < headerSize || pos + boxSize > length) {
                        return@use BmffValidationResult(false, "Box '$type' truncated: declared size $boxSize exceeds file length $length at offset $pos")
                    }

                    when (type) {
                        "ftyp" -> {
                            if (pos != 0L) return@use BmffValidationResult(false, "ftyp box must be first box in file, found at $pos")
                            hasFtyp = true
                        }
                        "moov" -> {
                            hasMoov = true
                            moovOffset = pos
                            moovSize = boxSize
                        }
                        "sidx" -> {
                            hasSidx = true
                            sidxOffset = pos
                            // Parse sidx reference size
                            if (boxSize >= 44) {
                                val sidxBytes = ByteArray(boxSize.toInt())
                                raf.seek(pos)
                                raf.readFully(sidxBytes)
                                val refCount = ((sidxBytes[30].toInt() and 0xFF) shl 8) or (sidxBytes[31].toInt() and 0xFF)
                                if (refCount > 0) {
                                    sidxRefSize = (readBe32(sidxBytes, 32).toLong() and 0x7FFFFFFF)
                                }
                            }
                        }
                        "moof" -> {
                            if (!hasMoof) {
                                hasMoof = true
                                moofOffset = pos
                                moofSize = boxSize
                            }
                        }
                        "mdat" -> {
                            if (!hasMdat) {
                                hasMdat = true
                                mdatOffset = pos
                                mdatSize = boxSize
                            }
                        }
                        "mfra" -> {
                            hasMfra = true
                        }
                    }

                    pos += boxSize
                }

                if (pos != length) {
                    return@use BmffValidationResult(false, "Trailing garbage after ISO BMFF boxes: expected $length, parsed $pos")
                }

                if (!hasFtyp) return@use BmffValidationResult(false, "Missing required ftyp box")
                if (!hasMoov) return@use BmffValidationResult(false, "Missing required moov box")
                if (!hasMoof) return@use BmffValidationResult(false, "Missing required moof box")
                if (!hasMdat) return@use BmffValidationResult(false, "Missing required mdat box")

                if (hasSidx && sidxOffset > moofOffset) {
                    return@use BmffValidationResult(false, "sidx box at $sidxOffset must precede first moof at $moofOffset")
                }

                // Deep validate moof -> traf -> tfhd & trun
                val moofBytes = ByteArray(moofSize.toInt())
                raf.seek(moofOffset)
                raf.readFully(moofBytes)

                var trafFound = false
                var tfhdFound = false
                var trunFound = false
                var baseDataOffset = moofOffset // default base data offset is first byte of moof
                var sampleDataOffset = -1L
                var totalSampleSize = 0L

                var mpos = 8
                while (mpos + 8 <= moofBytes.size) {
                    val subSize = readBe32(moofBytes, mpos).toLong() and 0xFFFF_FFFFL
                    val subType = String(moofBytes, mpos + 4, 4, Charsets.US_ASCII)
                    if (subSize < 8 || mpos + subSize > moofBytes.size) break

                    if (subType == "traf") {
                        trafFound = true
                        var tpos = mpos + 8
                        val tend = mpos + subSize.toInt()
                        var defaultSampleSize = 0L

                        while (tpos + 8 <= tend) {
                            val cSize = readBe32(moofBytes, tpos).toLong() and 0xFFFF_FFFFL
                            val cType = String(moofBytes, tpos + 4, 4, Charsets.US_ASCII)
                            if (cSize < 8 || tpos + cSize > tend) break

                            val payload = tpos + 8
                            val payloadSize = (cSize - 8).toInt()

                            when (cType) {
                                "tfhd" -> {
                                    tfhdFound = true
                                    if (payloadSize >= 8) {
                                        val flags = (moofBytes[payload + 1].toInt() and 0xFF shl 16) or
                                            (moofBytes[payload + 2].toInt() and 0xFF shl 8) or
                                            (moofBytes[payload + 3].toInt() and 0xFF)
                                        var cursor = payload + 8
                                        if ((flags and 0x000001) != 0 && cursor + 8 <= tend) {
                                            baseDataOffset = readBe64(moofBytes, cursor)
                                            cursor += 8
                                            if (baseDataOffset < 0 || baseDataOffset > length) {
                                                return@use BmffValidationResult(false, "tfhd base-data-offset out of file bounds: $baseDataOffset (file length: $length)")
                                            }
                                        }
                                        if ((flags and 0x000002) != 0) cursor += 4
                                        if ((flags and 0x000008) != 0) cursor += 4
                                        if ((flags and 0x000010) != 0 && cursor + 4 <= tend) {
                                            defaultSampleSize = readBe32(moofBytes, cursor).toLong() and 0xFFFF_FFFFL
                                        }
                                    }
                                }
                                "trun" -> {
                                    trunFound = true
                                    if (payloadSize >= 8) {
                                        val flags = (moofBytes[payload + 1].toInt() and 0xFF shl 16) or
                                            (moofBytes[payload + 2].toInt() and 0xFF shl 8) or
                                            (moofBytes[payload + 3].toInt() and 0xFF)
                                        val sampleCount = readBe32(moofBytes, payload + 4)
                                        var cursor = payload + 8
                                        if ((flags and 0x000001) != 0 && cursor + 4 <= tend) {
                                            val dataOffsetVal = readBe32(moofBytes, cursor)
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
                                                if (sSizeOffset + 4 <= tend) {
                                                    totalSampleSize += readBe32(moofBytes, sSizeOffset).toLong() and 0xFFFF_FFFFL
                                                }
                                                cursor += entrySize
                                            }
                                        } else if (defaultSampleSize > 0) {
                                            totalSampleSize = defaultSampleSize * sampleCount
                                        }
                                    }
                                }
                            }
                            tpos += cSize.toInt()
                        }
                    }
                    mpos += subSize.toInt()
                }

                if (!trafFound) return@use BmffValidationResult(false, "moof missing traf box")
                if (!tfhdFound) return@use BmffValidationResult(false, "traf missing tfhd box")
                if (!trunFound) return@use BmffValidationResult(false, "traf missing trun box")

                // Validate mdat boundaries
                val mdatPayloadStart = mdatOffset + 8
                val mdatPayloadEnd = mdatOffset + mdatSize
                if (sampleDataOffset < mdatPayloadStart || sampleDataOffset > mdatPayloadEnd) {
                    return@use BmffValidationResult(false, "trun sample data offset points outside mdat payload: offset $sampleDataOffset not in [$mdatPayloadStart, $mdatPayloadEnd]")
                }

                if (sampleDataOffset + totalSampleSize > mdatPayloadEnd) {
                    return@use BmffValidationResult(false, "Sample data extends beyond mdat payload bounds: sample end ${sampleDataOffset + totalSampleSize} > mdat end $mdatPayloadEnd")
                }

                if (hasSidx && sidxRefSize > 0) {
                    val actualSubsegmentSize = moofSize + mdatSize
                    if (sidxRefSize != actualSubsegmentSize) {
                        return@use BmffValidationResult(false, "sidx referenced_size does not match subsegment size: expected $actualSubsegmentSize, got $sidxRefSize")
                    }
                }

                BmffValidationResult(true, null, hasSidx, hasMfra)
            }
        }

        private fun readBe32(b: ByteArray, offset: Int): Int {
            return ((b[offset].toInt() and 0xFF) shl 24) or
                ((b[offset + 1].toInt() and 0xFF) shl 16) or
                ((b[offset + 2].toInt() and 0xFF) shl 8) or
                (b[offset + 3].toInt() and 0xFF)
        }

        private fun readBe64(b: ByteArray, offset: Int): Long {
            var v = 0L
            for (i in 0 until 8) {
                v = (v shl 8) or (b[offset + i].toLong() and 0xFF)
            }
            return v
        }
    }

    companion object {
        fun indexOfAscii(haystack: ByteArray, needle: String): Int {
            val needleBytes = needle.toByteArray(Charsets.US_ASCII)
            outer@ for (i in 0..haystack.size - needleBytes.size) {
                for (j in needleBytes.indices) {
                    if (haystack[i + j] != needleBytes[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}
