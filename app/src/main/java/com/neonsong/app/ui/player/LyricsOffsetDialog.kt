package com.neonsong.app.ui.player

import androidx.compose.runtime.Composable

@Composable
fun LyricsOffsetDialog(
    currentMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    com.neonsong.app.features.lyrics.LyricsOffsetDialog(
        currentMs = currentMs,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
