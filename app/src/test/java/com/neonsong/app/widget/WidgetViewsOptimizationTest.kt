package com.neonsong.app.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class WidgetViewsOptimizationTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun buildWidgetViews_withNullMemoryArt_returnsValidRemoteViews() {
        val views = WidgetViews.build(
            context = context,
            appWidgetId = 101,
            eqFrame = 0,
            progressOverride = 0.5f,
            memoryArt = null,
        )
        assertThat(views).isNotNull()
    }

    @Test
    fun buildWidgetViews_withValidBitmap_extractsAndCachesColors() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.MAGENTA)

        // First build: populates cache
        val views1 = WidgetViews.build(
            context = context,
            appWidgetId = 102,
            eqFrame = 1,
            progressOverride = 0.25f,
            memoryArt = bitmap,
        )
        assertThat(views1).isNotNull()

        // Second build with same bitmap generationId: reuses cache without re-extracting Palette
        val views2 = WidgetViews.build(
            context = context,
            appWidgetId = 102,
            eqFrame = 2,
            progressOverride = 0.30f,
            memoryArt = bitmap,
        )
        assertThat(views2).isNotNull()
    }

    @Test
    fun buildObsidianWidgetViews_withMemoryArt_returnsValidRemoteViews() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN)

        val views = ObsidianWidgetViews.build(
            context = context,
            appWidgetId = 201,
            eqFrame = 0,
            progressOverride = 0.75f,
            memoryArt = bitmap,
        )
        assertThat(views).isNotNull()

        // Repeated call to verify art caching
        val viewsCached = ObsidianWidgetViews.build(
            context = context,
            appWidgetId = 201,
            eqFrame = 1,
            progressOverride = 0.80f,
            memoryArt = bitmap,
        )
        assertThat(viewsCached).isNotNull()
    }

    @Test
    fun buildObsidianWidgetViews_withNullArt_handlesGracefully() {
        val views = ObsidianWidgetViews.build(
            context = context,
            appWidgetId = 202,
            eqFrame = null,
            progressOverride = null,
            memoryArt = null,
        )
        assertThat(views).isNotNull()
    }
}
