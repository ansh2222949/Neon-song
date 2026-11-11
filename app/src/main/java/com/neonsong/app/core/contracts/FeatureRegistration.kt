package com.neonsong.app.core.contracts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Compile-time feature declaration contract.
 * Allows independent modules to register navigation routes and entry screens.
 */
interface FeatureRegistration {
    val featureId: String
    val displayName: String
    val icon: ImageVector?
    val route: String
    val entryComposable: @Composable () -> Unit
}
