package com.neonsong.app.composition

import com.neonsong.app.core.contracts.AudioEngine
import com.neonsong.app.core.contracts.AudioSettingsRepository
import com.neonsong.app.core.contracts.LyricsProvider
import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.core.contracts.QueueController
import com.neonsong.app.core.contracts.ThemeProvider

/**
 * Central application composition root interface.
 * Exposes core contract interfaces without exposing concrete internal implementations.
 */
interface AppContainer {
    val playbackController: PlaybackController
    val queueController: QueueController
    val lyricsProvider: LyricsProvider
    val audioSettingsRepository: AudioSettingsRepository
    val themeProvider: ThemeProvider
    val audioEngine: AudioEngine
}
