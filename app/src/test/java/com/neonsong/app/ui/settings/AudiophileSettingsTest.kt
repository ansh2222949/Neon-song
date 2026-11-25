package com.neonsong.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.local.MiscSettings
import com.neonsong.app.data.local.SettingsPreferences
import com.neonsong.app.playback.AutoMixCurveType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AudiophileSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `default audiophile dsp settings are transparent and safe`() {
        val settings = MiscSettings()
        assertThat(settings.bauerCrossfeedEnabled).isFalse()
        assertThat(settings.analogTubeWarmthEnabled).isFalse()
        assertThat(settings.monoBassEnabled).isFalse()
        assertThat(settings.automixCurveType).isEqualTo(0)
    }

    @Test
    fun `settings preferences persists audiophile toggles and automix curve`() = runTest {
        val testFile = File(context.filesDir, "test_audiophile_prefs.preferences_pb")
        testFile.delete()

        val dsJob = SupervisorJob()
        val dataStoreScope = CoroutineScope(Dispatchers.IO + dsJob)

        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { testFile },
            )

            val prefs = SettingsPreferences(dataStore, context)

            // Initial default state
            val initial = prefs.settings.first()
            assertThat(initial.bauerCrossfeedEnabled).isFalse()
            assertThat(initial.analogTubeWarmthEnabled).isFalse()
            assertThat(initial.monoBassEnabled).isFalse()
            assertThat(initial.automixCurveType).isEqualTo(0)

            // Enable Bauer Crossfeed
            prefs.setBauerCrossfeedEnabled(true)
            assertThat(prefs.settings.first().bauerCrossfeedEnabled).isTrue()

            // Enable Analog Tube Warmth
            prefs.setAnalogTubeWarmthEnabled(true)
            assertThat(prefs.settings.first().analogTubeWarmthEnabled).isTrue()

            // Enable Mono Bass
            prefs.setMonoBassEnabled(true)
            assertThat(prefs.settings.first().monoBassEnabled).isTrue()

            // Set AutoMix Curve to Club Bass Swap (3 in AutoMixCurveType)
            prefs.setAutomixCurveType(3)
            assertThat(prefs.settings.first().automixCurveType).isEqualTo(3)

            // Verify AutoMix curve enum mapping
            val curveEnum = AutoMixCurveType.entries[prefs.settings.first().automixCurveType]
            assertThat(curveEnum).isEqualTo(AutoMixCurveType.BASS_SWAP_CLUB)

            // Clamping check
            prefs.setAutomixCurveType(10)
            assertThat(prefs.settings.first().automixCurveType).isEqualTo(3)
        } finally {
            dataStoreScope.cancel()
            testFile.delete()
        }
    }
}
