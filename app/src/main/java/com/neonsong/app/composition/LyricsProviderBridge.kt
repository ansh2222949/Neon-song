package com.neonsong.app.composition

import com.neonsong.app.core.contracts.LyricsProvider
import com.neonsong.app.data.lyrics.LyricsRepository
import com.neonsong.app.data.lyrics.LyricsResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composition bridge binding [LyricsRepository] to [LyricsProvider] contract.
 */
@Singleton
class LyricsProviderBridge @Inject constructor(
    private val lyricsRepository: LyricsRepository
) : LyricsProvider {

    override suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?,
        forceRefresh: Boolean,
        wordByWord: Boolean,
        videoId: String?,
        isrc: String?,
        onPartialResult: suspend (LyricsResult) -> Unit
    ): LyricsResult {
        return lyricsRepository.getLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds,
            forceRefresh = forceRefresh,
            wordByWord = wordByWord,
            videoId = videoId,
            isrc = isrc,
            onPartialResult = onPartialResult
        )
    }
}
