package com.neonsong.app.ui.player

import androidx.compose.runtime.Composable

@Composable
fun LyricsSizeDialog(
    currentScale: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    com.neonsong.app.features.lyrics.LyricsSizeDialog(
        currentScale = currentScale,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
