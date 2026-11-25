package com.neonsong.app.ui.settings

import com.google.common.truth.Truth.assertThat
import com.neonsong.app.data.backup.BackupFile
import com.neonsong.app.data.backup.BackupPlaylistSnapshot
import com.neonsong.app.data.backup.BackupPrefsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class SettingsRestoreValidationTest {

    private val backupJson = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeValidBackup_succeedsWithCorrectMetadata() {
        val original = BackupFile(
            type = "neonsong-backup",
            schemaVersion = 8,
            createdAt = 1700000000000L,
            appVersion = "1.0.0",
            prefs = BackupPrefsSnapshot(
                booleans = mapOf("autodj_enabled" to true),
                strings = mapOf("app_language" to "en"),
            ),
            playlists = listOf(
                BackupPlaylistSnapshot(
                    id = 1L,
                    title = "Favorites",
                    subtitle = "Best tracks",
                    mode = "custom",
                    tracksJson = "[]",
                    createdAtMillis = 1700000000000L,
                )
            ),
        )

        val jsonString = backupJson.encodeToString(original)
        val decoded = backupJson.decodeFromString(BackupFile.serializer(), jsonString)

        assertThat(decoded.type).isEqualTo("neonsong-backup")
        assertThat(decoded.schemaVersion).isEqualTo(8)
        assertThat(decoded.playlists).hasSize(1)
        assertThat(decoded.playlists[0].title).isEqualTo("Favorites")
        assertThat(decoded.prefs.booleans["autodj_enabled"]).isTrue()
    }

    @Test
    fun decodeBackup_withUnknownFutureKeys_ignoresThemSafely() {
        val jsonWithUnknownFields = """
            {
                "type": "neonsong-backup",
                "schemaVersion": 8,
                "createdAt": 1700000000000,
                "appVersion": "1.1.0",
                "futureFeatureEnabled": true,
                "futureConfigMap": {"alpha": 123},
                "prefs": {
                    "booleans": {},
                    "strings": {}
                },
                "playlists": []
            }
        """.trimIndent()

        val decoded = backupJson.decodeFromString(BackupFile.serializer(), jsonWithUnknownFields)
        assertThat(decoded.type).isEqualTo("neonsong-backup")
        assertThat(decoded.appVersion).isEqualTo("1.1.0")
    }

    @Test
    fun decodeCorruptedJson_throwsSafelyWithoutCrash() {
        val corruptedJson = "not a valid json payload { broken"

        val result = runCatching {
            backupJson.decodeFromString(BackupFile.serializer(), corruptedJson)
        }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun concurrentDecoding_isThreadSafe() = runBlocking(Dispatchers.Default) {
        val sample = BackupFile(
            type = "neonsong-backup",
            schemaVersion = 8,
            createdAt = 1700000000000L,
            appVersion = "1.0.0",
            prefs = BackupPrefsSnapshot(),
            playlists = emptyList(),
        )
        val payload = backupJson.encodeToString(sample)

        // Run 50 concurrent decodes simultaneously
        val deferreds = (1..50).map {
            async {
                val decoded = backupJson.decodeFromString(BackupFile.serializer(), payload)
                decoded.type == "neonsong-backup"
            }
        }
        val results = deferreds.awaitAll()
        assertThat(results).hasSize(50)
        assertThat(results.all { it }).isTrue()
    }
}
