package com.neonsong.app.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Samsung One UI ships vendor FLAC decoders (c2.sec.flac.decoder,
 * OMX.SEC.FLAC.Decoder, OMX.Exynos.FLAC.Decoder) that decode 24-bit hi-res
 * FLAC to packed 24-bit PCM while failing to advertise
 * KEY_PCM_ENCODING = ENCODING_PCM_24BIT_PACKED in the output MediaFormat.
 * The 3-byte samples are then consumed as 2-byte: buffers drain exactly
 * 3/2 faster — chipmunk pitch at ~1.5x speed — and broken Left/Right byte
 * boundaries surface as harsh digital noise. Only FLAC is affected;
 * Opus/AAC/MP3 play normally, which is why the fault isolated to Samsung
 * hardware playing lossless files.
 *
 * Demotes Samsung vendor decoders to the end of the FLAC codec list so the
 * reliable reference AOSP software decoder (c2.android.flac.decoder) wins.
 * Every other mime type keeps Android's default codec order untouched.
 */
@OptIn(UnstableApi::class)
internal val accurateAudioMediaCodecSelector: MediaCodecSelector =
    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoderInfos = runCatching {
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        }.getOrDefault(emptyList())
        if (decoderInfos.isEmpty()) {
            emptyList()
        } else {
            // Deprioritize buggy vendor decoders (Samsung One UI / Exynos hardware decoders)
            // across audio MIME types to avoid misreported sample rates, 1.5x fast pitch shifts,
            // or digital boundary distortion. Standard AOSP/Google reference decoders take priority.
            decoderInfos.sortedBy { info -> audioDecoderPriority(info.name) }
        }
    }

/** 0 = trusted reference decoder, 1 = proprietary vendor decoder (demoted to avoid clock skew). */
internal fun audioDecoderPriority(name: String): Int {
    val lower = name.lowercase()
    return if (lower.contains("sec.") || lower.contains("exynos")) 1 else 0
}

