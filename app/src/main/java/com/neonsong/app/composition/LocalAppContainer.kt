package com.neonsong.app.composition

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal key providing access to the current [AppContainer] instance
 * throughout the Compose UI hierarchy.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided in CompositionLocal hierarchy")
}
