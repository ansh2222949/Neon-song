package com.neonsong.app.ui.theme

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiquidGlassDesignSystemTest {

    private val directFrameClock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            return onFrame(System.nanoTime())
        }
    }

    @Test
    fun testLiquidGlassThemeColors_DarkTheme() {
        val accent = Color(0xFF6C9EFF)
        val bg = LiquidGlassTheme.backgroundColor(isDark = true, accent = accent, isAmoled = false)

        // Must be translucent (alpha ~ 0.25)
        assertEquals(0.25f, bg.alpha, 0.01f)
        // Red, green, blue should incorporate subtle accent depth
        assertTrue(bg.red > 0f)
        assertTrue(bg.blue > bg.red) // Accent has higher blue
    }

    @Test
    fun testLiquidGlassThemeColors_LightTheme() {
        val accent = Color(0xFF2463EB)
        val bg = LiquidGlassTheme.backgroundColor(isDark = false, accent = accent, isAmoled = false)

        // Must be luminous frosted glass (alpha ~ 0.25)
        assertEquals(0.25f, bg.alpha, 0.01f)
        // Light mode components are near 1.0
        assertTrue(bg.red > 0.85f)
        assertTrue(bg.green > 0.85f)
        assertTrue(bg.blue > 0.85f)
    }

    @Test
    fun testLiquidGlassThemeColors_AmoledMode() {
        val accent = Color(0xFFFF5722)
        val bg = LiquidGlassTheme.backgroundColor(isDark = true, accent = accent, isAmoled = true)

        // AMOLED glass retains high contrast with subtle dark tint (alpha ~ 0.25)
        assertEquals(0.25f, bg.alpha, 0.01f)
        assertTrue(bg.red > 0f)
    }

    @Test
    fun testLiquidGlassBrushes_NonNull() {
        val accent = Color(0xFF00E5FF)
        val size = Size(400f, 200f)

        val borderDark = LiquidGlassTheme.borderBrush(isDark = true, accent = accent, size = size)
        assertNotNull(borderDark)

        val borderLight = LiquidGlassTheme.borderBrush(isDark = false, accent = accent, size = size)
        assertNotNull(borderLight)

        val sheenDark = LiquidGlassTheme.specularSheenBrush(isDark = true)
        assertNotNull(sheenDark)

        val sheenLight = LiquidGlassTheme.specularSheenBrush(isDark = false)
        assertNotNull(sheenLight)
    }

    @Test
    fun testSquircleShape_OutlineGeneration() {
        val shape = SquircleShape(radius = 24.dp)
        val size = Size(300f, 150f)
        val outline = shape.createOutline(size, 24f, 24f, 24f, 24f, LayoutDirection.Ltr)

        assertTrue(outline is Outline.Generic)
        val genericOutline = outline as Outline.Generic
        assertNotNull(genericOutline.path)

        // Zero size handling
        val zeroOutline = shape.createOutline(Size.Zero, 0f, 0f, 0f, 0f, LayoutDirection.Ltr)
        assertTrue(zeroOutline is Outline.Rectangle)
    }

    @Test
    fun testLiquidTouchInteraction_StateLifecycle() = runTest(directFrameClock) {
        val interaction = LiquidTouchInteraction(this)

        assertEquals(1f, interaction.pressScale.value, 0.001f)
        assertEquals(0f, interaction.glowAlpha.value, 0.001f)

        // Down at (100, 50)
        val touchPoint = Offset(100f, 50f)
        interaction.onTouchDown(touchPoint)

        assertEquals(touchPoint, interaction.touchPosition)

        // Advance coroutines
        testScheduler.advanceUntilIdle()

        // When animated to target, pressScale compresses to 0.96f and glowAlpha reaches 1f
        assertEquals(0.96f, interaction.pressScale.value, 0.01f)
        assertEquals(1f, interaction.glowAlpha.value, 0.01f)

        // Release
        interaction.onTouchRelease()
        testScheduler.advanceUntilIdle()
        assertEquals(1f, interaction.pressScale.value, 0.001f)
        assertEquals(0f, interaction.glowAlpha.value, 0.001f)
    }

    @Test
    fun testLiquidTouchInteraction_CancelRecovery() = runTest(directFrameClock) {
        val interaction = LiquidTouchInteraction(this)

        interaction.onTouchDown(Offset(50f, 50f))
        testScheduler.advanceUntilIdle()
        assertEquals(0.96f, interaction.pressScale.value, 0.01f)

        // Cancel event (e.g. user scrolled)
        interaction.onTouchCancel()
        testScheduler.advanceUntilIdle()
        assertEquals(1f, interaction.pressScale.value, 0.001f)
        assertEquals(0f, interaction.glowAlpha.value, 0.01f)
    }
}
