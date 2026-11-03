package com.neonsong.app.data.lossless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * True-Dolby guards: Dolby ON must only ever badge genuine E-AC-3 spatial
 * bytes, and stereo requests must never accept an Atmos mix (devices
 * without an EC-3 decoder fail on it outright).
 *
 * AC-3 and E-AC-3 are Dolby codecs, but neither proves Atmos/JOC by itself.
 * The tests require an explicit Atmos/JOC signal before labeling a stream
 * spatial, so ordinary Dolby Digital/Plus renditions remain valid fallbacks.
 */
class LosslessAtmosManifestTest {

    private val atmosMpd = """
        <MPD>
          <Period>
            <AdaptationSet mimeType="audio/mp4">
              <Representation codecs="ec+3" bandwidth="768000">
                <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="6"/>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private val nonAtmosDolbyMpd = """
        <MPD>
          <Period>
            <AdaptationSet mimeType="audio/mp4">
              <Representation codecs="ec-3" bandwidth="768000">
                <AudioChannelConfiguration value="6"/>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun trueAtmosManifestAccepted() {
        assertTrue(LosslessMusicApi.isAtmosManifest(atmosMpd))
    }

    @Test
    fun plainEac3IsNotAtmosWithoutExplicitJocSignal() {
        assertFalse(LosslessMusicApi.isAtmosManifest(nonAtmosDolbyMpd))
    }

    @Test
    fun explicitJocMetadataIsAtmos() {
        assertTrue(
            LosslessMusicApi.isAtmosManifest(
                """<MPD><Representation codecs="ec-3"><SupplementalProperty value="JOC"/></Representation></MPD>""",
            ),
        )
    }

    @Test
    fun blankManifestRejected() {
        assertFalse(LosslessMusicApi.isAtmosManifest(""))
    }

    @Test
    fun atmosCodecLabels() {
        assertTrue(LosslessMusicApi.isAtmosCodec("ec+3"))
        assertTrue(LosslessMusicApi.isAtmosCodec("audio/eac3-joc"))
        assertTrue(LosslessMusicApi.isAtmosCodec("Dolby Atmos"))
        assertFalse(LosslessMusicApi.isAtmosCodec("ec-3"))
        assertFalse(LosslessMusicApi.isAtmosCodec("eac3"))
        assertFalse(LosslessMusicApi.isAtmosCodec("ac-3"))
        assertFalse(LosslessMusicApi.isAtmosCodec("ac3"))
        assertFalse(LosslessMusicApi.isAtmosCodec("Dolby Digital Plus"))
        assertFalse(LosslessMusicApi.isAtmosCodec("Dolby Digital"))
        assertFalse(LosslessMusicApi.isAtmosCodec("flac"))
        assertFalse(LosslessMusicApi.isAtmosCodec("mp4a.40.2"))
        assertFalse(LosslessMusicApi.isAtmosCodec(null))
        assertFalse(LosslessMusicApi.isAtmosCodec(""))
    }

    @Test
    fun ordinaryDolbyCodecFamiliesStayDistinct() {
        assertTrue(LosslessMusicApi.isEac3Codec("audio/eac3"))
        assertTrue(LosslessMusicApi.isAc3Codec("audio/ac3"))
        assertTrue(LosslessMusicApi.isLossyDolbyCodec("ec-3"))
        assertEquals("DOLBY DIGITAL PLUS", LosslessMusicApi.dolbyCodecLabel("eac3"))
        assertEquals("DOLBY DIGITAL", LosslessMusicApi.dolbyCodecLabel("ac-3"))
        assertEquals("DOLBY ATMOS", LosslessMusicApi.dolbyCodecLabel("ec+3"))
    }

    @Test
    fun nonAtmosDolbyUrlsAreNotAtmos() {
        assertFalse(LosslessMusicApi.isAtmosStreamUrl("https://cdn.example/track.flac"))
        assertFalse(LosslessMusicApi.isAtmosStreamUrl("https://cdn.example/track.eac3"))
        assertTrue(LosslessMusicApi.isAtmosStreamUrl("https://cdn.example/track.eac3-joc"))
        assertFalse(LosslessMusicApi.isAtmosStreamUrl(""))
    }
}
