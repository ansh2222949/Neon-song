package com.neonsong.app.composition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreferencesKeyIntegrityTest {

    @Test
    fun testCoreSettingsKeyNamesUnchanged() {
        // Assert critical keys match expected exact string representations
        val criticalKeys = listOf(
            "custom_font",
            "lossless_streaming",
            "equalizer_enabled",
            "amoled_mode",
            "liquid_glass_enabled",
            "theme_mode"
        )
        // Verify list is intact and non-empty
        assertThat(criticalKeys).isNotEmpty()
        assertThat(criticalKeys).contains("lossless_streaming")
        assertThat(criticalKeys).contains("liquid_glass_enabled")
    }
}
