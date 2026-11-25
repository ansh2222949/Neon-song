package com.neonsong.app.playback

import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * UAC Feature Unit volume (SET_CUR on the AudioControl interface).
 *
 * Analog/digital gain after the PCM payload stays bit-identical at any
 * listening level. [attach] only returns true when SET_CUR actually changes
 * GET_CUR — a successful control transfer alone is not proof the DAC
 * attenuates. Dongles without a real volume control fail closed so the
 * exclusive path can software-scale instead of claiming hardware volume.
 */
class UacFeatureVolume(
    private val connection: UsbDeviceConnection,
    private val controlInterfaceId: Int,
) {
    @Volatile
    var available: Boolean = false
        private set

    private var featureUnitId: Int = 0
    private var channels: IntArray = intArrayOf(0)
    private var minRaw: Int = DEFAULT_MIN_RAW
    private var maxRaw: Int = DEFAULT_MAX_RAW
    private var resRaw: Int = DEFAULT_RES_RAW
    private var getCurRequest: Int = 0
    private var lastMuted: Boolean? = null

    fun attach(): Boolean {
        available = false
        getCurRequest = 0
        lastMuted = null
        val ids = parseFeatureUnitIds().ifEmpty { FALLBACK_UNIT_IDS.toList() }
        for (id in ids.distinct()) {
            val found = discoverChannels(id)
            if (found.isEmpty()) continue
            val range = queryVolumeRange(id, found)
            minRaw = range.first
            maxRaw = range.second
            resRaw = range.third
            val verifiedChannels = verifyWritableChannels(id, found)
            if (verifiedChannels.isEmpty()) continue

            // Avoid stacking Master (ch 0) + L/R (ch 1, 2) on DACs with series stages.
            val activeChannels = when {
                1 in verifiedChannels && 2 in verifiedChannels -> {
                    if (0 in verifiedChannels) writeVolume(id, 0, maxRaw)
                    intArrayOf(1, 2)
                }
                0 in verifiedChannels -> {
                    if (1 in verifiedChannels) writeVolume(id, 1, maxRaw)
                    if (2 in verifiedChannels) writeVolume(id, 2, maxRaw)
                    intArrayOf(0)
                }
                else -> verifiedChannels
            }

            featureUnitId = id
            channels = activeChannels
            available = true
            Log.i(
                TAG,
                "Feature Unit volume verified id=0x${id.toString(16)} " +
                    "iface=$controlInterfaceId ch=${activeChannels.joinToString()} " +
                    "req=0x${getCurRequest.toString(16)} " +
                    "range=[${minRaw / 256.0}dB..${maxRaw / 256.0}dB res=${resRaw / 256.0}dB]",
            )
            return true
        }
        Log.i(TAG, "No writable UAC Feature Unit volume control")
        return false
    }

    fun setNormalized(volume: Float): Boolean {
        if (!available || featureUnitId == 0) return false
        val clamped = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 1f
        val shouldMute = clamped <= 0f
        val coded = encodedVolume(clamped, minRaw, maxRaw, resRaw)

        // Mute before setting volume when silencing; set volume before unmuting when audible.
        if (shouldMute && lastMuted != true) {
            writeMute(featureUnitId, true)
            lastMuted = true
        }
        var ok = false
        for (channel in channels) {
            if (writeVolume(featureUnitId, channel, coded)) ok = true
        }
        if (!shouldMute && lastMuted != false) {
            writeMute(featureUnitId, false)
            lastMuted = false
        }

        if (ok && clamped in 0.001f..0.85f && coded < maxRaw - 512) {
            val checkChannel = channels.first()
            val readback = readVolume(featureUnitId, checkChannel)
            if (readback != null && readback >= maxRaw && maxRaw > coded + 512) {
                Log.w(
                    TAG,
                    "DAC ignored SET_CUR coded=$coded on ch=$checkChannel (readback=$readback); failing closed",
                )
                available = false
                return false
            }
        }
        return ok
    }

    private fun discoverChannels(unitId: Int): IntArray {
        val found = CHANNELS_TO_TRY.filter { readVolume(unitId, it) != null }
        return found.toIntArray()
    }

    private fun queryVolumeRange(unitId: Int, found: IntArray): Triple<Int, Int, Int> {
        for (ch in found) {
            readUac2Range(unitId, ch)?.let { return it }
            readUac1Range(unitId, ch)?.let { return it }
        }
        return Triple(DEFAULT_MIN_RAW, DEFAULT_MAX_RAW, DEFAULT_RES_RAW)
    }

    /**
     * UAC 2.0 GET_RANGE (bRequest = 0x02) on Feature Unit Volume control.
     * Layout: wNumSubRanges (2B) + N * [wMin (2B), wMax (2B), wRes (2B)].
     */
    private fun readUac2Range(unitId: Int, channel: Int): Triple<Int, Int, Int>? {
        val wIndex = ((unitId and 0xFF) shl 8) or (controlInterfaceId and 0xFF)
        val wValue = VOLUME_WVALUE or (channel and 0xFF)
        val buf = ByteArray(64)
        val ret = runCatching {
            connection.controlTransfer(0xA1, 0x02, wValue, wIndex, buf, buf.size, TIMEOUT_MS)
        }.getOrDefault(-1)
        if (ret < 8) return null
        val count = (buf[0].toInt() and 0xFF) or ((buf[1].toInt() and 0xFF) shl 8)
        if (count <= 0) return null
        var overallMin = Int.MAX_VALUE
        var overallMax = Int.MIN_VALUE
        var bestRes = Int.MAX_VALUE
        val maxEntries = ((ret - 2) / 6).coerceAtMost(count)
        for (idx in 0 until maxEntries) {
            val base = 2 + idx * 6
            val subMin = ((buf[base].toInt() and 0xFF) or ((buf[base + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
            val subMax = ((buf[base + 2].toInt() and 0xFF) or ((buf[base + 3].toInt() and 0xFF) shl 8)).toShort().toInt()
            val subRes = ((buf[base + 4].toInt() and 0xFF) or ((buf[base + 5].toInt() and 0xFF) shl 8)).toShort().toInt()
            if (subMin < overallMin) overallMin = subMin
            if (subMax > overallMax) overallMax = subMax
            if (subRes > 0 && subRes < bestRes) bestRes = subRes
        }
        val safeMin = overallMin.coerceIn(-32767, -256)
        val safeMax = overallMax.coerceAtMost(0)
        if (safeMax <= safeMin) return null
        val safeRes = (if (bestRes == Int.MAX_VALUE) DEFAULT_RES_RAW else bestRes).coerceIn(1, 2048)
        return Triple(safeMin, safeMax, safeRes)
    }

    /**
     * UAC 1.0 GET_MIN (0x82), GET_MAX (0x83), GET_RES (0x84).
     */
    private fun readUac1Range(unitId: Int, channel: Int): Triple<Int, Int, Int>? {
        val minVal = readControlShort(unitId, channel, 0x82) ?: return null
        val maxVal = readControlShort(unitId, channel, 0x83) ?: return null
        val safeMin = minVal.coerceIn(-32767, -256)
        val safeMax = maxVal.coerceAtMost(0)
        if (safeMax <= safeMin) return null
        val resVal = readControlShort(unitId, channel, 0x84)?.takeIf { it > 0 } ?: DEFAULT_RES_RAW
        return Triple(safeMin, safeMax, resVal.coerceIn(1, 2048))
    }

    private fun readControlShort(unitId: Int, channel: Int, request: Int): Int? {
        val data = ByteArray(2)
        val wIndex = ((unitId and 0xFF) shl 8) or (controlInterfaceId and 0xFF)
        val wValue = VOLUME_WVALUE or (channel and 0xFF)
        val ret = runCatching {
            connection.controlTransfer(
                0xA1,
                request,
                wValue,
                wIndex,
                data,
                data.size,
                TIMEOUT_MS,
            )
        }.getOrDefault(-1)
        if (ret < 2) return null
        val raw = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        return raw.toShort().toInt()
    }

    private fun verifyWritableChannels(unitId: Int, found: IntArray): IntArray {
        val writable = mutableListOf<Int>()
        val span = (maxRaw - minRaw).coerceAtLeast(1)
        val step = resRaw.coerceIn(1, span)
        val upper = (maxRaw - step).coerceAtLeast(minRaw)
        val testCandidate = (maxRaw - 12 * step).coerceIn(minRaw, upper)
        for (channel in found) {
            val original = readVolume(unitId, channel) ?: continue
            var test = if (kotlin.math.abs(original - testCandidate) >= step) {
                testCandidate
            } else {
                (minRaw + 2 * step).coerceIn(minRaw, maxRaw)
            }
            if (test == original) {
                test = if (original != minRaw) minRaw else maxRaw
            }
            if (test == original || !writeVolume(unitId, channel, test)) continue
            runCatching { Thread.sleep(VERIFY_SLEEP_MS) }
            val updated = readVolume(unitId, channel)
            writeVolume(unitId, channel, original)
            val changed = updated != null && updated != original
            Log.i(
                TAG,
                "verify FU 0x${unitId.toString(16)} ch=$channel original=$original " +
                    "test=$test readback=$updated writable=$changed",
            )
            if (changed) writable += channel
        }
        return writable.toIntArray()
    }

    private fun readVolume(unitId: Int, channel: Int): Int? {
        val data = ByteArray(2)
        val wIndex = ((unitId and 0xFF) shl 8) or (controlInterfaceId and 0xFF)
        val wValue = VOLUME_WVALUE or (channel and 0xFF)
        val requests = if (getCurRequest != 0) intArrayOf(getCurRequest) else intArrayOf(0x01, 0x81)
        for (request in requests) {
            val ret = runCatching {
                connection.controlTransfer(0xA1, request, wValue, wIndex, data, data.size, TIMEOUT_MS)
            }.getOrDefault(-1)
            if (ret >= 2) {
                if (getCurRequest == 0) getCurRequest = request
                val raw = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                return raw.toShort().toInt()
            }
        }
        return null
    }

    private fun writeVolume(unitId: Int, channel: Int, coded: Int): Boolean {
        val data = ByteArray(2)
        data[0] = (coded and 0xFF).toByte()
        data[1] = ((coded shr 8) and 0xFF).toByte()
        val wIndex = ((unitId and 0xFF) shl 8) or (controlInterfaceId and 0xFF)
        val wValue = VOLUME_WVALUE or (channel and 0xFF)
        val ret = runCatching {
            connection.controlTransfer(
                0x21,
                0x01,
                wValue,
                wIndex,
                data,
                data.size,
                TIMEOUT_MS,
            )
        }.getOrDefault(-1)
        return ret >= 0
    }

    private fun writeMute(unitId: Int, mute: Boolean): Boolean {
        val data = byteArrayOf(if (mute) 1 else 0)
        val wIndex = ((unitId and 0xFF) shl 8) or (controlInterfaceId and 0xFF)
        var ok = false
        for (channel in channels) {
            val wValue = MUTE_WVALUE or (channel and 0xFF)
            val ret = runCatching {
                connection.controlTransfer(
                    0x21,
                    0x01,
                    wValue,
                    wIndex,
                    data,
                    data.size,
                    TIMEOUT_MS,
                )
            }.getOrDefault(-1)
            if (ret >= 0) ok = true
        }
        return ok
    }

    private fun parseFeatureUnitIds(): List<Int> {
        val raw = connection.rawDescriptors ?: return emptyList()
        val allFeatureUnits = mutableListOf<Int>()
        val unitSourceMap = HashMap<Int, Int>()
        val playbackOutputSources = mutableListOf<Int>()
        val otherOutputSources = mutableListOf<Int>()

        var i = 0
        var inTargetAudioControl = false
        while (i + 1 < raw.size) {
            val length = raw[i].toInt() and 0xFF
            if (length < 2 || i + length > raw.size) break
            val type = raw[i + 1].toInt() and 0xFF
            if (type == 0x04 && length >= 9) {
                val ifaceNum = raw[i + 2].toInt() and 0xFF
                val ifaceClass = raw[i + 5].toInt() and 0xFF
                val ifaceSub = raw[i + 6].toInt() and 0xFF
                inTargetAudioControl = ifaceNum == (controlInterfaceId and 0xFF) &&
                    ifaceClass == 1 && ifaceSub == 1
            } else if (inTargetAudioControl && type == 0x24 && length >= 4) {
                val subtype = raw[i + 2].toInt() and 0xFF
                val id = raw[i + 3].toInt() and 0xFF
                when (subtype) {
                    OUTPUT_TERMINAL_SUBTYPE -> if (length >= 8) {
                        val termType = (raw[i + 4].toInt() and 0xFF) or
                            ((raw[i + 5].toInt() and 0xFF) shl 8)
                        val sourceId = raw[i + 7].toInt() and 0xFF
                        if ((termType and 0xFF00) == 0x0300) {
                            playbackOutputSources += sourceId
                        } else if (termType != USB_STREAMING_TERMINAL_TYPE) {
                            otherOutputSources += sourceId
                        }
                    }
                    FEATURE_UNIT_SUBTYPE -> {
                        allFeatureUnits += id
                        if (length >= 5) unitSourceMap[id] = raw[i + 4].toInt() and 0xFF
                    }
                    MIXER_UNIT_SUBTYPE, SELECTOR_UNIT_SUBTYPE, PROCESSING_UNIT_SUBTYPE -> {
                        if (length >= 6) unitSourceMap[id] = raw[i + 5].toInt() and 0xFF
                    }
                }
            }
            i += length
        }

        val fuSet = allFeatureUnits.toSet()
        val prioritized = LinkedHashSet<Int>()
        fun traceBack(startSource: Int) {
            var cur = startSource
            var hops = 0
            while (cur > 0 && hops++ < 6) {
                if (cur in fuSet) {
                    prioritized += cur
                    return
                }
                cur = unitSourceMap[cur] ?: return
            }
        }
        playbackOutputSources.forEach(::traceBack)
        otherOutputSources.forEach(::traceBack)
        prioritized.addAll(allFeatureUnits)
        return prioritized.toList()
    }

    companion object {
        const val TAG = "UacFeatureVolume"
        const val FEATURE_UNIT_SUBTYPE = 0x06
        const val VOLUME_WVALUE = 0x0200
        const val MUTE_WVALUE = 0x0100
        const val TIMEOUT_MS = 80
        const val VERIFY_SLEEP_MS = 8L
        const val MUTE = 0x8000
        const val DEFAULT_MIN_RAW = -15872 // -62.0 dB in 1/256 dB units
        const val DEFAULT_MAX_RAW = 0      //   0.0 dB
        const val DEFAULT_RES_RAW = 256    //   1.0 dB step alignment
        private const val PERCEPTUAL_FLOOR_DB = -62.0
        val CHANNELS_TO_TRY = intArrayOf(0, 1, 2)
        val FALLBACK_UNIT_IDS = intArrayOf(0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A)

        private const val OUTPUT_TERMINAL_SUBTYPE = 0x03
        private const val MIXER_UNIT_SUBTYPE = 0x04
        private const val SELECTOR_UNIT_SUBTYPE = 0x05
        private const val PROCESSING_UNIT_SUBTYPE = 0x07
        private const val USB_STREAMING_TERMINAL_TYPE = 0x0101

        /**
         * Maps normalized slider position [volume] in (0, 1] to a perceptual
         * dB attenuation (-62 dB at 0+ .. 0 dB at 1.0), so step 1/15 (~6.7%)
         * is ~ -53.8 dB instead of -23.5 dB, then aligns to [resRaw] within
         * the DAC's hardware [minRaw]..[maxRaw] range.
         */
        fun perceptualDb(volume: Float, minDb: Double = PERCEPTUAL_FLOOR_DB, maxDb: Double = 0.0): Double {
            if (!volume.isFinite()) return maxDb
            val v = volume.coerceIn(0f, 1f).toDouble()
            if (v <= 0.0) return -127.0
            val safeMax = if (maxDb.isFinite()) maxDb else 0.0
            val safeMin = if (minDb.isFinite()) minDb.coerceAtMost(safeMax - 1.0) else PERCEPTUAL_FLOOR_DB
            // Respect DACs with shallower floors (e.g. -30 dB) so the lower slider stays responsive.
            val floor = safeMin.coerceAtLeast(-72.0)
            val curved = v.pow(0.75)
            return (floor + (safeMax - floor) * curved).coerceIn(safeMin, safeMax)
        }

        fun perceptualLinearGain(volume: Float): Float {
            if (!volume.isFinite()) return 1f
            val v = volume.coerceIn(0f, 1f)
            if (v <= 0f) return 0f
            if (v >= 0.999f) return 1f
            val db = perceptualDb(v, PERCEPTUAL_FLOOR_DB, 0.0)
            return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
        }

        fun encodedVolume(
            volume: Float,
            minRaw: Int = DEFAULT_MIN_RAW,
            maxRaw: Int = DEFAULT_MAX_RAW,
            resRaw: Int = DEFAULT_RES_RAW,
        ): Int {
            if (!volume.isFinite()) return maxRaw
            if (volume <= 0f) return MUTE
            val safeMaxRaw = maxRaw.coerceAtMost(0)
            val safeMinRaw = minRaw.coerceAtMost(safeMaxRaw)
            if (safeMinRaw == safeMaxRaw) return safeMaxRaw

            val db = perceptualDb(volume, safeMinRaw / 256.0, safeMaxRaw / 256.0)
            val rawTarget = (db * 256.0).roundToInt().coerceIn(safeMinRaw, safeMaxRaw)
            val step = resRaw.coerceAtLeast(1)
            val aligned = if (step > 1) {
                safeMinRaw + ((rawTarget - safeMinRaw + step / 2) / step) * step
            } else {
                rawTarget
            }
            return aligned.coerceIn(safeMinRaw, safeMaxRaw)
        }
    }
}
