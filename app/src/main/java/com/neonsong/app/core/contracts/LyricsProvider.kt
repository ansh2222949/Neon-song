package com.neonsong.app.core.contracts

import com.neonsong.app.data.lyrics.LyricsResult

/**
 * Lyrics retrieval and synchronization contract.
 * Exposes multi-tier lyrics fetching (syllable, word-by-word, and line-synced LRC).
 */
interface LyricsProvider {
    /**
     * Fetch synchronized or plain lyrics for a track across providers.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
        forceRefresh: Boolean = false,
        wordByWord: Boolean = true,
        videoId: String? = null,
        isrc: String? = null,
        onPartialResult: suspend (LyricsResult) -> Unit = {}
    ): LyricsResult
}
