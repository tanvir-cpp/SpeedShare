package com.example.speedshareandroid.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionTag: String,
    val cleanVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val apkSize: Long
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/tanvir-cpp/SpeedShare/releases/latest"
    const val RELEASES_WEB_URL = "https://github.com/tanvir-cpp/SpeedShare/releases/latest"

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "SpeedShare-Android/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (conn.responseCode != 200) {
                Log.d(TAG, "GitHub API returned response code: ${conn.responseCode}")
                return@withContext null
            }

            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            val tagName = json.optString("tag_name", "")
            if (tagName.isEmpty()) return@withContext null

            val cleanTag = tagName.trimStart('v', 'V')
            if (!isNewerVersion(cleanTag, currentVersion)) {
                return@withContext null
            }

            var apkUrl: String? = null
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            return@withContext UpdateInfo(
                versionTag = tagName,
                cleanVersion = cleanTag,
                releaseTitle = json.optString("name", "SpeedShare $tagName"),
                changelog = json.optString("body", "New version available with enhancements and bug fixes."),
                apkDownloadUrl = apkUrl,
                htmlUrl = json.optString("html_url", RELEASES_WEB_URL),
                apkSize = apkSize
            )
        } catch (e: Exception) {
            Log.d(TAG, "Update check failed: ${e.message}")
            return@withContext null
        }
    }

    fun isNewerVersion(remoteVer: String, localVer: String): Boolean {
        try {
            val remoteParts = remoteVer.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = localVer.split(".").map { it.toIntOrNull() ?: 0 }

            val length = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until length) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {
            return remoteVer.compareTo(localVer, ignoreCase = true) > 0
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(context.cacheDir, "SpeedShare-Update.apk")
            if (targetFile.exists()) targetFile.delete()

            // Follow redirects manually (HttpURLConnection doesn't always follow them
            // for binary downloads, and we want to be explicit).
            var url = downloadUrl
            var conn: HttpURLConnection? = null
            var redirects = 0
            while (redirects < 5) {
                val connCandidate = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "SpeedShare-Android-Updater")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val code = connCandidate.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == 307 || code == 308) {
                    connCandidate.disconnect()
                    val next = connCandidate.getHeaderField("Location") ?: break
                    url = next
                    redirects++
                    continue
                }
                conn = connCandidate
                break
            }

            if (conn == null) return@withContext null

            val totalBytes = conn.contentLengthLong
            conn.inputStream.use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(percent)
                        }
                    }
                    outStream.flush()
                }
            }
            conn.disconnect()

            return@withContext targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK: ${e.message}")
            return@withContext null
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) return false
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer intent: ${e.message}")
            false
        }
    }
}
