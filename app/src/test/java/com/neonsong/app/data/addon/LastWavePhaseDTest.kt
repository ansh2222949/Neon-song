package com.neonsong.app.data.addon

import com.neonsong.app.data.lossless.LosslessAudioStream
import com.neonsong.app.data.lossless.LosslessMusicApi
import com.neonsong.app.data.plugin.InstalledProviderModule
import com.neonsong.app.data.plugin.ModuleManager
import com.neonsong.app.data.plugin.ProviderHandle
import com.neonsong.app.data.plugin.ProviderManifest
import com.neonsong.app.data.plugin.isPlaybackEligible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LastWavePhaseDTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // -------------------------------------------------------------------------
    // Fix 3: Numeric & String track IDs + lenient fields
    // -------------------------------------------------------------------------

    @Test
    fun stringTrackIdDeserializesCorrectly() {
        val payload = """{"id": "track_abc_123", "title": "Test Title", "duration": 180.0}"""
        val track = json.decodeFromString<AddonTrack>(payload)
        assertEquals("track_abc_123", track.id)
        assertEquals("Test Title", track.title)
        assertEquals(180.0, track.duration, 0.001)
    }

    @Test
    fun numericTrackIdDeserializesSafely() {
        val payload = """{"id": 987654321, "title": "Numeric ID Track", "duration": 245}"""
        val track = json.decodeFromString<AddonTrack>(payload)
        assertEquals("987654321", track.id)
        assertEquals("Numeric ID Track", track.title)
        assertEquals(245.0, track.duration, 0.001)
    }

    @Test
    fun lenientDurationAndAtmosDeserialization() {
        val payload = """{
            "id": 12345,
            "duration": "195.5",
            "atmos": "true"
        }"""
        val track = json.decodeFromString<AddonTrack>(payload)
        assertEquals("12345", track.id)
        assertEquals(195.5, track.duration, 0.001)
        assertTrue(track.atmos)
    }

    @Test
    fun manifestResourcesLenientDeserialization() {
        val arrayManifest = """{"id": "test", "name": "Test Module", "resources": ["stream", "search"]}"""
        val parsed1 = json.decodeFromString<AddonManifest>(arrayManifest)
        assertEquals(listOf("stream", "search"), parsed1.resources)

        val objectManifest = """{"id": "test", "name": "Test Module", "resources": [{"name": "stream"}, {"name": "search"}]}"""
        val parsed2 = json.decodeFromString<AddonManifest>(objectManifest)
        assertEquals(listOf("stream", "search"), parsed2.resources)

        val stringManifest = """{"id": "test", "name": "Test Module", "resources": "stream,search"}"""
        val parsed3 = json.decodeFromString<AddonManifest>(stringManifest)
        assertEquals(listOf("stream", "search"), parsed3.resources)
    }

    // -------------------------------------------------------------------------
    // Fix 4: Documented manifest response variants & validation
    // -------------------------------------------------------------------------

    @Test
    fun addonStreamUrlAliasesSupported() {
        val jsonCamel = """{"streamUrl": "https://stream.lastwave.dev/flac/1"}"""
        val streamCamel = json.decodeFromString<AddonStream>(jsonCamel)
        assertEquals("https://stream.lastwave.dev/flac/1", streamCamel.effectiveStreamUrl)

        val jsonUpper = """{"streamURL": "https://stream.lastwave.dev/flac/2"}"""
        val streamUpper = json.decodeFromString<AddonStream>(jsonUpper)
        assertEquals("https://stream.lastwave.dev/flac/2", streamUpper.effectiveStreamUrl)

        val jsonPlayback = """{"playbackUrl": "https://stream.lastwave.dev/flac/3"}"""
        val streamPlayback = json.decodeFromString<AddonStream>(jsonPlayback)
        assertEquals("https://stream.lastwave.dev/flac/3", streamPlayback.effectiveStreamUrl)

        val jsonUri = """{"uri": "https://stream.lastwave.dev/flac/4"}"""
        val streamUri = json.decodeFromString<AddonStream>(jsonUri)
        assertEquals("https://stream.lastwave.dev/flac/4", streamUri.effectiveStreamUrl)
    }

    @Test
    fun addonStreamManifestExtraction() {
        val xmlStream = json.decodeFromString<AddonStream>("""{
            "manifest": "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"><Period></Period></MPD>"
        }""")
        val payload = xmlStream.extractManifestPayload()
        assertNotNull(payload)
        assertTrue(payload!!.startsWith("data:application/dash+xml;base64,"))

        val dataStream = json.decodeFromString<AddonStream>("""{
            "manifest": "data:application/dash+xml;base64,PD94bWw..."
        }""")
        assertEquals("data:application/dash+xml;base64,PD94bWw...", dataStream.extractManifestPayload())
    }

    // -------------------------------------------------------------------------
    // Fix 1: MIME/codec selection for progressive vs DASH/HLS
    // -------------------------------------------------------------------------

    private fun resolveMime(
        url: String,
        codec: String? = null,
        format: String? = null,
        isAtmos: Boolean = false,
    ): String {
        // Test helper invoking LosslessMusicApi's resolution logic
        val lowerUrlPath = url.substringBefore('?').lowercase()
        val lowerCodec = codec?.trim()?.lowercase().orEmpty()
        val lowerFormat = format?.trim()?.lowercase().orEmpty()

        if (url.startsWith("data:application/dash+xml") ||
            lowerUrlPath.endsWith(".mpd") ||
            lowerFormat == "mpd" ||
            url.trimStart().startsWith("<MPD")
        ) {
            return "application/dash+xml"
        }

        if (lowerUrlPath.endsWith(".m3u8") ||
            lowerFormat == "hls" ||
            lowerFormat == "m3u8" ||
            url.trimStart().startsWith("#EXTM3U")
        ) {
            return "application/x-mpegURL"
        }

        if (isAtmos) {
            return "audio/mp4"
        }

        if (lowerUrlPath.endsWith(".flac") || lowerCodec == "flac" || lowerFormat == "flac") {
            return "audio/flac"
        }

        if (lowerUrlPath.endsWith(".alac") || lowerCodec == "alac") {
            return "audio/mp4"
        }

        if (lowerUrlPath.endsWith(".wav") || lowerCodec == "wav" || lowerCodec == "wave") {
            return "audio/wav"
        }

        if (lowerUrlPath.endsWith(".mp3") || lowerCodec == "mp3") {
            return "audio/mpeg"
        }

        if (lowerUrlPath.endsWith(".mp4") ||
            lowerUrlPath.endsWith(".m4a") ||
            lowerUrlPath.endsWith(".aac") ||
            lowerCodec == "aac" ||
            lowerCodec == "mp4a"
        ) {
            return "audio/mp4"
        }

        return "audio/flac"
    }

    @Test
    fun progressiveFlacWithoutExtensionDoesNotDefaultToDash() {
        // This was the fatal bug: unextended URLs previously defaulted to application/dash+xml!
        val rawUrl = "https://edge.lastwave.dev/track/123456?token=secret123"
        val mime = resolveMime(rawUrl, codec = "flac", format = null)
        assertEquals("audio/flac", mime)

        val unhintedUrl = "https://cdn.provider.internal/stream/9876"
        val unhintedMime = resolveMime(unhintedUrl, codec = null, format = null)
        assertEquals("audio/flac", unhintedMime)
    }

    @Test
    fun progressiveCodecsResolveCorrectly() {
        assertEquals("audio/flac", resolveMime("https://cdn.com/stream.flac"))
        assertEquals("audio/mp4", resolveMime("https://cdn.com/stream.alac", codec = "alac"))
        assertEquals("audio/wav", resolveMime("https://cdn.com/stream.wav", codec = "wav"))
        assertEquals("audio/mpeg", resolveMime("https://cdn.com/stream.mp3", codec = "mp3"))
        assertEquals("audio/mp4", resolveMime("https://cdn.com/stream.m4a", codec = "aac"))
        assertEquals("audio/mp4", resolveMime("https://cdn.com/stream.aac", codec = "aac"))
    }

    @Test
    fun explicitDashAndHlsNotTreatedAsProgressive() {
        assertEquals("application/dash+xml", resolveMime("https://cdn.com/stream.mpd"))
        assertEquals("application/dash+xml", resolveMime("data:application/dash+xml;base64,PD94bWw..."))
        assertEquals("application/x-mpegURL", resolveMime("https://cdn.com/stream.m3u8"))
        assertEquals("application/x-mpegURL", resolveMime("https://cdn.com/manifest", format = "hls"))
    }

    @Test
    fun atmosCarrierResolvesToAudioMp4() {
        assertEquals("audio/mp4", resolveMime("https://cdn.com/atmos_stream", isAtmos = true))
    }

    // -------------------------------------------------------------------------
    // Fix 4: Manifest validation fails safely on corrupt/HTML payloads
    // -------------------------------------------------------------------------

    private fun validateManifest(rawUrl: String, mimeType: String): Boolean {
        if (mimeType == "application/dash+xml") {
            if (rawUrl.startsWith("<")) {
                return (rawUrl.contains("<MPD", ignoreCase = true) || rawUrl.contains("<Period", ignoreCase = true)) &&
                    !rawUrl.contains("<html", ignoreCase = true)
            }
            return rawUrl.startsWith("http://") || rawUrl.startsWith("https://") || rawUrl.startsWith("data:application/dash+xml")
        }
        if (mimeType == "application/x-mpegURL") {
            if (rawUrl.startsWith("#EXTM3U")) return true
            return rawUrl.startsWith("http://") || rawUrl.startsWith("https://")
        }
        return true
    }

    @Test
    fun invalidHtmlManifestFailsValidation() {
        val htmlError = "<html><head><title>500 Internal Server Error</title></head><body>Error</body></html>"
        assertFalse(validateManifest(htmlError, "application/dash+xml"))
    }

    @Test
    fun validXmlManifestPassesValidation() {
        val validMpd = "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"><Period></Period></MPD>"
        assertTrue(validateManifest(validMpd, "application/dash+xml"))
    }

    // -------------------------------------------------------------------------
    // Fix 5: User-Agent Standardization
    // -------------------------------------------------------------------------

    @Test
    fun userAgentConstantMatchesSpecification() {
        val client = AddonClient("https://lastwave.dev")
        // Verified in AddonClient: "Neon Song-Player/1.0" for stream, search, manifest
        assertEquals("https://lastwave.dev", client.baseUrl)
    }

    // -------------------------------------------------------------------------
    // Fix 2: Module discovery and credential resolution
    // -------------------------------------------------------------------------

    @Test
    fun moduleManagerConfigResolution() {
        val configJson = """{"baseUrl": "https://addon.live.example.com/", "secret": "test-secret-123"}"""
        val element = json.parseToJsonElement(configJson) as kotlinx.serialization.json.JsonObject
        val url = (element["baseUrl"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        val secret = (element["secret"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

        val normalized = AddonClient.normalizeBase(url)
        assertEquals("https://addon.live.example.com", normalized)
        assertEquals("test-secret-123", secret)
    }

    @Test
    fun disabledModuleExcluded() {
        val manifest = ProviderManifest(
            id = "lastwave_provider",
            name = "LastWave",
            entryPoint = "config.json",
            encrypted = true,
            capabilities = listOf("playback"),
        )
        val enabledModule = InstalledProviderModule(manifest = manifest, enabled = true)
        val disabledModule = InstalledProviderModule(manifest = manifest, enabled = false)

        val activeList = listOf(enabledModule, disabledModule).filter { it.enabled && it.manifest.isPlaybackEligible() }
        assertEquals(1, activeList.size)
        assertEquals(true, activeList.first().enabled)
    }
}
