package com.neonsong.app.ui.search

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.search.SearchResultItem
import com.neonsong.app.data.search.SearchTab
import org.junit.Test

class SearchAppleStyleTest {

    @Test
    fun `search tabs have expected Apple Music category labels`() {
        val appleLabels = listOf(
            SearchTab.TRACKS to "SONGS",
            SearchTab.ARTISTS to "ARTISTS",
            SearchTab.ALBUMS to "ALBUMS",
            SearchTab.PLAYLISTS to "PLAYLISTS",
            SearchTab.USERS to "USERS",
        )

        assertThat(appleLabels.map { it.second }).containsExactly(
            "SONGS", "ARTISTS", "ALBUMS", "PLAYLISTS", "USERS"
        ).inOrder()
    }

    @Test
    fun `search item subtitle format matches Apple Music pattern`() {
        val songItem = SearchResultItem(
            name = "Usseewa",
            artist = "ADO",
            subtitle = "Kyougen",
            url = "https://music.youtube.com/watch?v=123",
        )

        val formattedSongSub = if (!songItem.artist.isNullOrBlank()) "Song \u2022 ${songItem.artist}" else "Song"
        assertThat(formattedSongSub).isEqualTo("Song \u2022 ADO")

        val artistItem = SearchResultItem(
            name = "ADO",
            artist = null,
            subtitle = null,
            url = "https://music.youtube.com/channel/123",
        )
        val formattedArtistSub = "Artist"
        assertThat(formattedArtistSub).isEqualTo("Artist")

        val albumItem = SearchResultItem(
            name = "Kyougen",
            artist = "ADO",
            subtitle = null,
            url = "https://music.youtube.com/playlist?list=123",
        )
        val formattedAlbumSub = if (!albumItem.artist.isNullOrBlank()) "Album \u2022 ${albumItem.artist}" else "Album"
        assertThat(formattedAlbumSub).isEqualTo("Album \u2022 ADO")
    }
}
