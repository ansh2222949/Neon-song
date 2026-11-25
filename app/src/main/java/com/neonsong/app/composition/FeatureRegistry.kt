package com.neonsong.app.composition

import com.neonsong.app.core.contracts.FeatureRegistration
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compile-time feature registry.
 * Registers and exposes available modular features and future add-on screens.
 */
@Singleton
class FeatureRegistry @Inject constructor() {

    private val features = Collections.synchronizedList(mutableListOf<FeatureRegistration>())

    fun registerFeature(feature: FeatureRegistration) {
        features.add(feature)
    }

    fun getRegisteredFeatures(): List<FeatureRegistration> {
        return features.toList()
    }

    fun findFeatureById(id: String): FeatureRegistration? {
        return features.firstOrNull { it.featureId == id }
    }
}
