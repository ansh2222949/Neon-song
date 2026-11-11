package com.neonsong.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.LiquidGlassIconButton


/** Circular liquid-glass overflow button shared by the app's song lists. */
@Composable
fun OverflowMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassIconButton(
        onClick = onClick,
        modifier = modifier
            .padding(start = 10.dp)
            .size(40.dp),
        accentTint = MaterialTheme.colorScheme.secondary,
        contentDescription = "More options",
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}
