package com.neonsong.app.composition

import com.neonsong.app.core.contracts.AudioEngine
import com.neonsong.app.core.contracts.AudioSettingsRepository
import com.neonsong.app.core.contracts.LyricsProvider
import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.core.contracts.QueueController
import com.neonsong.app.core.contracts.ThemeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppContainer @Inject constructor(
    override val playbackController: PlaybackController,
    override val queueController: QueueController,
    override val lyricsProvider: LyricsProvider,
    override val audioSettingsRepository: AudioSettingsRepository,
    override val themeProvider: ThemeProvider,
    override val audioEngine: AudioEngine
) : AppContainer
