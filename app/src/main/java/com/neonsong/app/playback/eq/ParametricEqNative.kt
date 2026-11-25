package com.neonsong.app.playback.eq

import android.util.Log
import androidx.media3.common.C
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * JNI surface of the native parametric EQ (ParametricEq.cpp).
 *
 * [publish] sets the single process-wide configuration that both the native
 * DSP chain and every [DirectPcmEq] pick up on their next block.
 */
object ParametricEqNative {
    private const val TAG = "ParametricEqNative"

    const val MAX_FILTERS = 20
    const val MAX_CHANNELS = 12

    @Volatile
    private var nativeDisabled = false

    private val isLibraryLoaded: Boolean = runCatching {
        System.loadLibrary("neonsong_audio")
        true
    }.getOrElse { error ->
        Log.w(TAG, "Native parametric EQ unavailable", error)
        false
    }

    val isAvailable: Boolean
        get() = isLibraryLoaded && !nativeDisabled

    fun publish(enabled: Boolean, preampDb: Float, filters: List<NativeFilter>) {
        if (!isAvailable) return
        val safePreamp = if (preampDb.isFinite()) preampDb.coerceIn(-30f, 30f) else 0f
        val safeFilters = filters.take(MAX_FILTERS)
        val size = safeFilters.size

        val types = IntArray(size) { safeFilters[it].type.coerceIn(0, 7) }
        val frequencies = FloatArray(size) {
            val f = safeFilters[it].frequencyHz
            if (f.isFinite()) f.coerceIn(10f, 40000f) else 1000f
        }
        val gains = FloatArray(size) {
            val g = safeFilters[it].gainDb
            if (g.isFinite()) g.coerceIn(-30f, 30f) else 0f
        }
        val qs = FloatArray(size) {
            val q = safeFilters[it].q
            if (q.isFinite()) q.coerceIn(0.05f, 30f) else 0.707f
        }
        val filterEnabled = BooleanArray(size) { safeFilters[it].enabled }

        try {
            nativePublish(
                enabled,
                safePreamp,
                types,
                frequencies,
                gains,
                qs,
                filterEnabled,
            )
        } catch (error: Exception) {
            Log.w(TAG, "publish failed", error)
        } catch (error: LinkageError) {
            Log.e(TAG, "publish native linkage failure, disabling native EQ", error)
            nativeDisabled = true
        }
    }

    /** True when an enabled configuration actually changes the signal. */
    fun isEngaged(): Boolean {
        if (!isAvailable) return false
        return try {
            nativeIsEngaged()
        } catch (error: Exception) {
            false
        } catch (error: LinkageError) {
            nativeDisabled = true
            false
        }
    }

    data class NativeFilter(
        val type: Int,
        val frequencyHz: Float,
        val gainDb: Float,
        val q: Float,
        val enabled: Boolean,
    )

    /** Media3 PCM encoding -> native encoding id (see ParametricEq.h), or null when unsupported. */
    fun nativeEncodingOf(media3Encoding: Int): Int? = when (media3Encoding) {
        C.ENCODING_PCM_16BIT -> 0
        C.ENCODING_PCM_24BIT -> 1
        C.ENCODING_PCM_32BIT -> 2
        C.ENCODING_PCM_FLOAT -> 3
        else -> null
    }

    @JvmStatic external fun nativePublish(
        enabled: Boolean,
        preampDb: Float,
        types: IntArray,
        frequencies: FloatArray,
        gains: FloatArray,
        qs: FloatArray,
        filterEnabled: BooleanArray,
    )
    @JvmStatic external fun nativeIsEngaged(): Boolean
    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeConfigure(handle: Long, sampleRate: Int, channels: Int)
    @JvmStatic external fun nativeReset(handle: Long)
    @JvmStatic external fun nativeProcessDirect(
        handle: Long,
        buffer: ByteBuffer,
        offset: Int,
        length: Int,
        encoding: Int,
    ): Boolean
    @JvmStatic external fun nativeProcessArray(
        handle: Long,
        array: ByteArray,
        offset: Int,
        length: Int,
        encoding: Int,
    ): Boolean
}

/**
 * In-place parametric EQ for the direct routes (USB exclusive, internal
 * direct output, Bit-Perfect AudioTrack), where PCM never enters the native
 * Float32 DSP chain. Works in the source packing so the route keeps its format.
 * Renderer thread only.
 */
class DirectPcmEq : Closeable {
    @Volatile
    private var handle = 0L
    @Volatile
    private var isClosed = false
    private var encoding: Int? = null
    private var frameBytes = 0

    fun configure(sampleRate: Int, channels: Int, media3Encoding: Int) {
        if (isClosed || !ParametricEqNative.isAvailable || sampleRate <= 0 || channels !in 1..ParametricEqNative.MAX_CHANNELS) {
            encoding = null
            frameBytes = 0
            return
        }

        if (handle == 0L) {
            handle = try {
                ParametricEqNative.nativeCreate()
            } catch (error: Exception) {
                0L
            } catch (error: LinkageError) {
                0L
            }
        }
        val currentHandle = handle
        if (currentHandle == 0L) {
            encoding = null
            frameBytes = 0
            return
        }

        val enc = ParametricEqNative.nativeEncodingOf(media3Encoding)
        val bytesPerSample = when (enc) {
            0 -> 2
            1 -> 3
            2, 3 -> 4
            else -> 0
        }
        if (enc == null || bytesPerSample <= 0) {
            encoding = null
            frameBytes = 0
            return
        }

        encoding = enc
        frameBytes = bytesPerSample * channels

        try {
            ParametricEqNative.nativeConfigure(currentHandle, sampleRate, channels)
        } catch (error: Exception) {
            encoding = null
            frameBytes = 0
        } catch (error: LinkageError) {
            encoding = null
            frameBytes = 0
        }
    }

    fun reset() {
        val currentHandle = handle
        if (!isClosed && currentHandle != 0L) {
            try {
                ParametricEqNative.nativeReset(currentHandle)
            } catch (_: Exception) {
            } catch (_: LinkageError) {
            }
        }
    }

    /** Applies the EQ to buffer[position, limit). Returns true when samples changed. */
    fun process(buffer: ByteBuffer): Boolean {
        if (isClosed || buffer.isReadOnly || !buffer.hasRemaining()) return false
        val enc = encoding ?: return false
        val currentHandle = handle
        if (currentHandle == 0L || frameBytes <= 0) return false

        val start = buffer.position()
        val length = (buffer.remaining() / frameBytes) * frameBytes
        if (length <= 0) return false

        return try {
            when {
                buffer.isDirect -> ParametricEqNative.nativeProcessDirect(currentHandle, buffer, start, length, enc)
                buffer.hasArray() && !buffer.isReadOnly -> ParametricEqNative.nativeProcessArray(
                    currentHandle,
                    buffer.array(),
                    buffer.arrayOffset() + start,
                    length,
                    enc,
                )
                else -> false
            }
        } catch (error: Exception) {
            false
        } catch (error: LinkageError) {
            false
        }
    }

    fun release() {
        isClosed = true
        val currentHandle = handle
        if (currentHandle != 0L) {
            handle = 0L
            encoding = null
            frameBytes = 0
            try {
                ParametricEqNative.nativeDestroy(currentHandle)
            } catch (_: Exception) {
            } catch (_: LinkageError) {
            }
        }
    }

    override fun close() {
        release()
    }
}
