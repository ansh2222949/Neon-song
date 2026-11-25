package com.neonsong.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Md3SchemeBuilderTest {

    @Test
    fun enrichDynamicColors_increasesAccentChromaAndPreservesSurfaceRoles() {
        val base = darkColorScheme(
            primary = Color(0xFF778899),
            primaryContainer = Color(0xFF334455),
            secondary = Color(0xFF778899),
            secondaryContainer = Color(0xFF334455),
            tertiary = Color(0xFF778899),
            tertiaryContainer = Color(0xFF334455),
            inversePrimary = Color(0xFF778899),
        )

        val enriched = Md3SchemeBuilder.enrichDynamicColors(base)

        assertTrue(saturation(enriched.primary) > saturation(base.primary))
        assertTrue(saturation(enriched.primaryContainer) > saturation(base.primaryContainer))
        assertEquals(base.surface, enriched.surface)
        assertEquals(base.background, enriched.background)
    }

    @Test
    fun buildDarkScheme_usesVividAccentsAndKeepsDarkSurfaces() {
        val scheme = Md3SchemeBuilder.buildDarkScheme("#00E5FF", amoled = true)

        assertTrue(saturation(scheme.primary) > 0.40f)
        assertTrue(saturation(scheme.secondary) > 0.25f)
        assertTrue(saturation(scheme.tertiary) > 0.32f)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
    }

    private fun saturation(color: Color): Float {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val delta = max - min
        val lightness = (max + min) / 2f
        return if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    }
}
