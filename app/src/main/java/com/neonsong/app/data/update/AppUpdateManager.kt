package com.neonsong.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val isChecking: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = "",
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val downloadUrl: String? = null,
    val isDismissed: Boolean = false,
    val message: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloaded: Boolean = false,
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        const val PREFS_NAME = "neon_song_updates"
        const val KEY_CUSTOM_UPDATE_URL = "custom_update_manifest_url"
        const val KEY_DISMISSED_VERSION = "dismissed_version"

        // Default GitHub releases endpoint and raw manifest fallback
        const val DEFAULT_GITHUB_API_URL = "https://api.github.com/repos/Clash-Projects/Neon Song-native/releases/latest"
        const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/Clash-Projects/Neon Song-native/main/version.json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updateInfo = MutableStateFlow(
        UpdateInfo(
            currentVersion = getCurrentVersion(),
        )
    )
    val updateInfo: StateFlow<UpdateInfo> = _updateInfo.asStateFlow()

    init {
        checkForUpdate(isSilent = true)
    }

    fun getCurrentVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.2.4"
    } catch (_: Exception) {
        "4.2.4"
    }

    fun getCustomUpdateUrl(): String {
        return prefs.getString(KEY_CUSTOM_UPDATE_URL, "") ?: ""
    }

    fun setCustomUpdateUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_UPDATE_URL, url.trim()).apply()
        checkForUpdate(isSilent = false)
    }

    fun checkForUpdate(isSilent: Boolean = false) {
        scope.launch {
            _updateInfo.update {
                it.copy(
                    isChecking = true,
                    message = if (!isSilent) "Checking for updates..." else it.message,
                )
            }

            val currentVer = getCurrentVersion()
            val customUrl = getCustomUpdateUrl().takeIf { it.isNotBlank() }
            val urlsToTry = listOfNotNull(
                customUrl,
                DEFAULT_MANIFEST_URL,
                DEFAULT_GITHUB_API_URL,
            )

            var foundUpdate = false
            for (targetUrl in urlsToTry) {
                try {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "NeonSong-App/${currentVer}")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        response.close()
                        continue
                    }

                    val body = response.body?.string().orEmpty()
                    response.close()
                    if (body.isBlank()) continue

                    val json = JSONObject(body)
                    var remoteVersion = ""
                    var releaseNotes = ""
                    var releaseUrl = ""
                    var downloadUrl: String? = null

                    if (json.has("tag_name")) {
                        // GitHub Release JSON structure
                        remoteVersion = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
                        releaseNotes = json.optString("body", "")
                        releaseUrl = json.optString("html_url", "")

                        val assets = json.optJSONArray("assets") ?: JSONArray()
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    } else if (json.has("version")) {
                        // Custom version.json manifest structure
                        remoteVersion = json.optString("version", "").removePrefix("v").removePrefix("V")
                        releaseNotes = json.optString("changelog", json.optString("releaseNotes", ""))
                        releaseUrl = json.optString("releaseUrl", "")
                        downloadUrl = json.optString("downloadUrl", null)
                    }

                    if (remoteVersion.isNotBlank()) {
                        val isNewer = isNewerVersion(remoteVersion, currentVer)
                        val dismissed = prefs.getString(KEY_DISMISSED_VERSION, "") ?: ""
                        val isDismissed = (dismissed == remoteVersion)

                        _updateInfo.update {
                            it.copy(
                                isChecking = false,
                                isUpdateAvailable = isNewer,
                                latestVersion = remoteVersion,
                                releaseNotes = releaseNotes,
                                releaseUrl = releaseUrl,
                                downloadUrl = downloadUrl,
                                isDismissed = isDismissed,
                                message = if (isNewer) "Version $remoteVersion is available!" else if (!isSilent) "Neon Song is up to date (v$currentVer)" else null,
                            )
                        }
                        foundUpdate = true
                        break
                    }
                } catch (_: Exception) {
                    // Try next fallback endpoint
                }
            }

            if (!foundUpdate) {
                _updateInfo.update {
                    it.copy(
                        isChecking = false,
                        isUpdateAvailable = false,
                        message = if (!isSilent) "Neon Song is up to date (v$currentVer)" else null,
                    )
                }
            }
        }
    }

    fun dismissUpdate(version: String) {
        val clean = version.removePrefix("v").removePrefix("V")
        prefs.edit().putString(KEY_DISMISSED_VERSION, clean).apply()
        _updateInfo.update { it.copy(isDismissed = true) }
    }

    fun openUpdate(context: Context) {
        val targetUrl = _updateInfo.value.downloadUrl ?: _updateInfo.value.releaseUrl
        if (targetUrl.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun downloadAndInstall(activityContext: Context) {
        val downloadUrl = _updateInfo.value.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            openUpdate(activityContext)
            return
        }

        scope.launch {
            _updateInfo.update { it.copy(isDownloading = true, downloadProgress = 0.05f) }
            try {
                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()
                val apkFile = File(updatesDir, "NeonSong-update.apk")
                if (apkFile.exists()) apkFile.delete()

                val request = Request.Builder().url(downloadUrl).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    withContext(Dispatchers.Main) { openUpdate(activityContext) }
                    _updateInfo.update { it.copy(isDownloading = false) }
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    response.close()
                    withContext(Dispatchers.Main) { openUpdate(activityContext) }
                    _updateInfo.update { it.copy(isDownloading = false) }
                    return@launch
                }

                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0.05f, 0.99f)
                        _updateInfo.update { it.copy(downloadProgress = progress) }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                response.close()

                _updateInfo.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1.0f,
                        isDownloaded = true,
                    )
                }

                withContext(Dispatchers.Main) {
                    installApk(activityContext, apkFile)
                }
            } catch (e: Exception) {
                _updateInfo.update { it.copy(isDownloading = false) }
                withContext(Dispatchers.Main) {
                    openUpdate(activityContext)
                }
            }
        }
    }

    private fun installApk(activityContext: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.provider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(installIntent)
        } catch (_: Exception) {
            openUpdate(activityContext)
        }
    }

    fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val cleanRemote = remote.removePrefix("v").removePrefix("V").substringBefore("-")
        val cleanLocal = local.removePrefix("v").removePrefix("V").substringBefore("-")
        if (cleanRemote == cleanLocal) return false

        val rParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
