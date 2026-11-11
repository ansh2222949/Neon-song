package com.neonsong.app.composition

import androidx.compose.runtime.Composable
import com.google.common.truth.Truth.assertThat
import com.neonsong.app.core.contracts.FeatureRegistration
import org.junit.Test

class CompositionWiringTest {

    @Test
    fun testFeatureRegistryRegistrationAndLookup() {
        val registry = FeatureRegistry()
        assertThat(registry.getRegisteredFeatures()).isEmpty()

        val mockFeature = object : FeatureRegistration {
            override val featureId: String = "test_feature"
            override val displayName: String = "Test Feature"
            override val icon = null
            override val route: String = "test_route"
            override val entryComposable: @Composable () -> Unit = {}
        }

        registry.registerFeature(mockFeature)

        val features = registry.getRegisteredFeatures()
        assertThat(features).hasSize(1)
        assertThat(features[0].featureId).isEqualTo("test_feature")
        assertThat(features[0].displayName).isEqualTo("Test Feature")

        val found = registry.findFeatureById("test_feature")
        assertThat(found).isNotNull()
        assertThat(found?.route).isEqualTo("test_route")

        val notFound = registry.findFeatureById("non_existent")
        assertThat(notFound).isNull()
    }
}
