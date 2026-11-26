package com.neonsong.app.data.lossless

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LosslessMatchingTest {

    @Test
    fun normalizeTextPreservesJapaneseVoicedKanaAndDistinguishesSongs() {
        val giraGira = LosslessMusicApi.normalizeText("ギラギラ")
        val usseewa = LosslessMusicApi.normalizeText("うっせぇわ")
        val blueMoon = LosslessMusicApi.normalizeText("BLUE MOON")
        val tumHiHo = LosslessMusicApi.normalizeText("तुम ही हो")
        val beyonce = LosslessMusicApi.normalizeText("Beyoncé - Crazy in Love")

        assertThat(giraGira).isEqualTo("ギラギラ")
        assertThat(giraGira).isNotEqualTo(usseewa)
        assertThat(LosslessMusicApi.normalizeText("キラキラ")).isNotEqualTo(giraGira)
        assertThat(blueMoon).isEqualTo("blue moon")
        assertThat(tumHiHo).isEqualTo("तुम ही हो")
        assertThat(beyonce).isEqualTo("beyonce crazy in love")
    }

    @Test
    fun rejectsFirstTakeCoverWithUnrequestedCoArtistForUsseewa() {
        val score = LosslessMusicApi.verifiedMatchScoreForTesting(
            candidateTitle = "うっせぇわ",
            candidateArtist = "Clavick & Ado THE FIRST TAKE",
            candidateAlbum = "THE FIRST TAKE",
            candidateDurationSec = 207,
            targetTitle = "うっせぇわ - Usseewa",
            targetArtist = "Ado",
            expectedDurationSeconds = 207,
            expectedAlbum = "Kyougen",
        )
        assertThat(score).isNull()
    }

    @Test
    fun rejectsPianoVersionForUsseewaEvenBySameArtist() {
        val score = LosslessMusicApi.verifiedMatchScoreForTesting(
            candidateTitle = "うっせぇわ (Piano Ver.)",
            candidateArtist = "Ado",
            candidateAlbum = "レディメイド - Single",
            candidateDurationSec = 223,
            targetTitle = "うっせぇわ - Usseewa",
            targetArtist = "Ado",
            expectedDurationSeconds = 207,
            expectedAlbum = "Kyougen",
        )
        assertThat(score).isNull()
    }

    @Test
    fun rejectsOneCharDifferentShortLatinTitlesCallingVsFalling() {
        val score = LosslessMusicApi.verifiedMatchScoreForTesting(
            candidateTitle = "Falling",
            candidateArtist = "Ado",
            candidateDurationSec = 200,
            targetTitle = "Calling",
            targetArtist = "Ado",
            expectedDurationSeconds = 200,
        )
        assertThat(score).isNull()
    }

    @Test
    fun matchesBilingualJapaneseEnglishTitleWithGenuineArtist() {
        val score = LosslessMusicApi.verifiedMatchScoreForTesting(
            candidateTitle = "Usseewa",
            candidateArtist = "Ado",
            candidateAlbum = "狂言",
            candidateDurationSec = 207,
            targetTitle = "うっせぇわ - Usseewa",
            targetArtist = "Ado",
            expectedDurationSeconds = 207,
            expectedAlbum = "Kyougen",
        )
        assertThat(score).isNotNull()
    }
}
