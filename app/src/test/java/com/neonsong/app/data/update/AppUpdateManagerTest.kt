package com.neonsong.app.data.update

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun selectPreferredApkAsset_prefersNeonSongApkOverAlphabeticalAndroid7() {
        val assets = listOf(
            "Neon.Song-v5.0.0-android7.apk" to "https://example.com/android7.apk",
            "Neon.Song-v5.0.0-release.apk" to "https://example.com/release.apk",
            "NeonSong.apk" to "https://example.com/NeonSong.apk",
            "SHA256SUMS.txt" to "https://example.com/SHA256SUMS.txt",
        )

        val selected = AppUpdateManager.selectPreferredApkAsset(assets)
        assertThat(selected).isEqualTo("https://example.com/NeonSong.apk")
    }

    @Test
    fun selectPreferredApkAsset_avoidsAndroid7AndRawWhenNeonSongApkMissing() {
        val assets = listOf(
            "Neon.Song-v5.0.0-android7.apk" to "https://example.com/android7.apk",
            "Neon.Song-v5.0.0-raw.apk" to "https://example.com/raw.apk",
            "Neon.Song-v5.0.0-release.apk" to "https://example.com/release.apk",
        )

        val selected = AppUpdateManager.selectPreferredApkAsset(assets)
        assertThat(selected).isEqualTo("https://example.com/release.apk")
    }

    @Test
    fun selectPreferredApkAsset_ignoresNonApkAndBlankUrls() {
        val assets = listOf(
            "SHA256SUMS.txt" to "https://example.com/SHA256SUMS.txt",
            "NeonSong.apk" to "   ",
            "Neon.Song-v5.0.0-android7.apk" to "https://example.com/android7.apk",
        )

        val selected = AppUpdateManager.selectPreferredApkAsset(assets)
        assertThat(selected).isEqualTo("https://example.com/android7.apk")
    }

    @Test
    fun buildUpdateSourceUrls_putsGithubReleasesAheadOfRawManifest() {
        val defaultUrls = AppUpdateManager.buildUpdateSourceUrls("")
        assertThat(defaultUrls).containsExactly(
            AppUpdateManager.DEFAULT_GITHUB_API_URL,
            AppUpdateManager.DEFAULT_MANIFEST_URL,
        ).inOrder()

        val customUrls = AppUpdateManager.buildUpdateSourceUrls("  https://custom.example/update.json  ")
        assertThat(customUrls).containsExactly(
            "https://custom.example/update.json",
            AppUpdateManager.DEFAULT_GITHUB_API_URL,
            AppUpdateManager.DEFAULT_MANIFEST_URL,
        ).inOrder()
    }

    @Test
    fun evaluateApkInstallability_rejectsSameOrOlderVersionCodeAndPackageMismatch() {
        val sameVersion = AppUpdateManager.evaluateApkInstallability(
            expectedPackageName = "com.neonsong.app",
            installedVersionCode = 25L,
            installedSigningDigests = setOf("abc123"),
            archivePackageName = "com.neonsong.app",
            archiveVersionCode = 25L,
            archiveVersionName = "4.2.5",
            archiveSigningDigests = setOf("abc123"),
        )
        assertThat(sameVersion.canInstall).isFalse()
        assertThat(sameVersion.message).contains("not newer")

        val wrongPackage = AppUpdateManager.evaluateApkInstallability(
            expectedPackageName = "com.neonsong.app",
            installedVersionCode = 25L,
            installedSigningDigests = setOf("abc123"),
            archivePackageName = "com.other.app",
            archiveVersionCode = 26L,
            archiveVersionName = "5.0.0",
            archiveSigningDigests = setOf("abc123"),
        )
        assertThat(wrongPackage.canInstall).isFalse()
        assertThat(wrongPackage.message).contains("com.other.app")
    }

    @Test
    fun evaluateApkInstallability_warnsOnSigningKeyMismatchAndAllowsMatchingUpgrade() {
        val mismatch = AppUpdateManager.evaluateApkInstallability(
            expectedPackageName = "com.neonsong.app",
            installedVersionCode = 25L,
            installedSigningDigests = setOf("oldkey"),
            archivePackageName = "com.neonsong.app",
            archiveVersionCode = 26L,
            archiveVersionName = "5.0.0",
            archiveSigningDigests = setOf("newkey"),
        )
        assertThat(mismatch.canInstall).isTrue()
        assertThat(mismatch.signatureMismatch).isTrue()
        assertThat(mismatch.message).contains("Signing key differs")

        val validUpgrade = AppUpdateManager.evaluateApkInstallability(
            expectedPackageName = "com.neonsong.app",
            installedVersionCode = 25L,
            installedSigningDigests = setOf("samekey"),
            archivePackageName = "com.neonsong.app",
            archiveVersionCode = 26L,
            archiveVersionName = "5.0.0",
            archiveSigningDigests = setOf("samekey"),
        )
        assertThat(validUpgrade.canInstall).isTrue()
        assertThat(validUpgrade.signatureMismatch).isFalse()
        assertThat(validUpgrade.message).isNull()
    }

    @Test
    fun isNewerVersion_comparesSemanticVersionNumbersAccurately() {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns ""
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)

        val manager = AppUpdateManager(context, okHttpClient)

        assertThat(manager.isNewerVersion("5.0.0", "4.2.5")).isTrue()
        assertThat(manager.isNewerVersion("5.0.1", "5.0.0")).isTrue()
        assertThat(manager.isNewerVersion("4.2.5", "4.2.5")).isFalse()
        assertThat(manager.isNewerVersion("4.2.4", "4.2.5")).isFalse()
    }
}
