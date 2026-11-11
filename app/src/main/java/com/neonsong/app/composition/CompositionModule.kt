package com.neonsong.app.composition

import com.neonsong.app.core.audio_api.NativeAudioEngineAdapter
import com.neonsong.app.core.contracts.AudioEngine
import com.neonsong.app.core.contracts.AudioSettingsRepository
import com.neonsong.app.core.contracts.LyricsProvider
import com.neonsong.app.core.contracts.PlaybackController
import com.neonsong.app.core.contracts.QueueController
import com.neonsong.app.core.contracts.ThemeProvider
import com.neonsong.app.data.repository.DefaultAudioSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CompositionModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackController(
        impl: PlaybackControllerBridge
    ): PlaybackController

    @Binds
    @Singleton
    abstract fun bindQueueController(
        impl: PlaybackControllerBridge
    ): QueueController

    @Binds
    @Singleton
    abstract fun bindLyricsProvider(
        impl: LyricsProviderBridge
    ): LyricsProvider

    @Binds
    @Singleton
    abstract fun bindAudioSettingsRepository(
        impl: DefaultAudioSettingsRepository
    ): AudioSettingsRepository

    @Binds
    @Singleton
    abstract fun bindThemeProvider(
        impl: ThemeProviderBridge
    ): ThemeProvider

    @Binds
    @Singleton
    abstract fun bindAudioEngine(
        impl: NativeAudioEngineAdapter
    ): AudioEngine

    @Binds
    @Singleton
    abstract fun bindAppContainer(
        impl: DefaultAppContainer
    ): AppContainer
}
