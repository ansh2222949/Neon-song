package com.neonsong.app.ui.settings

import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.lossless.LosslessMusicApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualitySettingsTest {

    @Test
    fun testAppleAudioQualityTierMapping() {
        assertEquals(
            AppleAudioQualityTier.HI_RES_LOSSLESS,
            AppleAudioQualityTier.fromQualityId(LosslessMusicApi.QUALITY_MAX_HI_RES),
        )
        assertEquals(
            AppleAudioQualityTier.LOSSLESS,
            AppleAudioQualityTier.fromQualityId(LosslessMusicApi.QUALITY_CD_LOSSLESS),
        )
        assertEquals(
            AppleAudioQualityTier.LOSSLESS,
            AppleAudioQualityTier.fromQualityId(LosslessMusicApi.QUALITY_HI_RES_96),
        )
        assertEquals(
            AppleAudioQualityTier.HIGH_QUALITY,
            AppleAudioQualityTier.fromQualityId(LosslessMusicApi.QUALITY_MP3_320),
        )
        assertEquals(
            AppleAudioQualityTier.HIGH_EFFICIENCY,
            AppleAudioQualityTier.fromQualityId(LosslessMusicApi.QUALITY_DATA_SAVER),
        )
    }

    @Test
    fun testAudioQualityTargetTitles() {
        assertEquals("Cellular Streaming", AudioQualityTarget.CELLULAR_STREAMING.title)
        assertEquals("Wi-Fi Streaming", AudioQualityTarget.WIFI_STREAMING.title)
        assertEquals("Downloads", AudioQualityTarget.DOWNLOADS.title)
    }

    @Test
    fun testDefaultMiscSettingsAudioQuality() {
        val settings = MiscSettings()
        assertTrue(settings.preferLosslessStreaming)
        assertEquals(27, settings.cellularStreamingQuality)
        assertEquals(27, settings.wifiStreamingQuality)
        assertEquals(27, settings.downloadQuality)
        assertEquals(
            AppleAudioQualityTier.HI_RES_LOSSLESS,
            AppleAudioQualityTier.fromQualityId(settings.wifiStreamingQuality),
        )
    }

    @Test
    fun testTogglingLosslessStreaming() {
        var settings = MiscSettings()
        assertTrue(settings.preferLosslessStreaming)

        settings = settings.copy(preferLosslessStreaming = false)
        assertFalse(settings.preferLosslessStreaming)

        settings = settings.copy(
            cellularStreamingQuality = LosslessMusicApi.QUALITY_MP3_320,
            wifiStreamingQuality = LosslessMusicApi.QUALITY_CD_LOSSLESS,
        )
        assertEquals(LosslessMusicApi.QUALITY_MP3_320, settings.cellularStreamingQuality)
        assertEquals(
            AppleAudioQualityTier.HIGH_QUALITY,
            AppleAudioQualityTier.fromQualityId(settings.cellularStreamingQuality),
        )
        assertEquals(LosslessMusicApi.QUALITY_CD_LOSSLESS, settings.wifiStreamingQuality)
        assertEquals(
            AppleAudioQualityTier.LOSSLESS,
            AppleAudioQualityTier.fromQualityId(settings.wifiStreamingQuality),
        )
    }
}
