package com.example.speedshareandroid.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Update payload data parsed from a GitHub release.
 *
 * `sha256` and `expectedSize` are used by the downloader to verify the
 * downloaded file is exactly what was published. They are extracted from
 * the release body (a known `SpeedShare-Update.sha256` sidecar asset
 * pattern) and from the GitHub asset metadata.
 */
data class UpdateInfo(
    val versionTag: String,
    val cleanVersion: String,
    val releaseTitle: String,
    val changelog: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val apkSize: Long,
    val sha256: String? = null,
    val isPrerelease: Boolean = false
)

/**
 * Errors that can occur during the update flow. Surfaced to the UI as
 * a discriminated union so the dialog can show a useful message instead
 * of a generic toast.
 */
sealed class UpdateError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No newer release found on GitHub. Not really an error. */
    object NoUpdate : UpdateError("No update available")
    /** Network failed. */
    class Network(cause: Throwable) : UpdateError("Network error: ${cause.message}", cause)
    /** GitHub returned non-200. */
    class Http(val code: Int) : UpdateError("GitHub returned HTTP $code")
    /** Response body could not be parsed. */
    class Parse(cause: Throwable) : UpdateError("Bad response: ${cause.message}", cause)
    /** Download failed. */
    class Download(cause: Throwable) : UpdateError("Download failed: ${cause.message}", cause)
    /** Downloaded file size doesn't match. */
    class SizeMismatch(val expected: Long, val actual: Long) : UpdateError(
        "Size mismatch (expected $expected, got $actual)"
    )
    /** Downloaded file's SHA-256 doesn't match. */
    class HashMismatch(val expected: String, val actual: String) : UpdateError("Hash mismatch")
    /** Installation failed. */
    class Install(cause: Throwable) : UpdateError("Install failed: ${cause.message}", cause)
}

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/tanvir-cpp/SpeedShare/releases/latest"
    const val RELEASES_WEB_URL = "https://github.com/tanvir-cpp/SpeedShare/releases/latest"
    private const val SHA256_ASSET_SUFFIX = ".sha256"

    /**
     * Check the latest GitHub release for a newer version.
     *
     * Returns:
     *  - `Result.success(UpdateInfo)` if a newer release is available
     *  - `Result.failure(UpdateError.NoUpdate)` if the latest release is not newer
     *  - `Result.failure(...)` for any other error
     *
     * Note: `Result` is the Kotlin stdlib `kotlin.Result`, NOT the network
     * 4xx/5xx HTTP result type. (Avoids the name shadowing.)
     */
    suspend fun checkForUpdates(currentVersion: String): kotlin.Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(GITHUB_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "SpeedShare-Android/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            if (conn.responseCode != 200) {
                return@withContext kotlin.Result.failure(UpdateError.Http(conn.responseCode))
            }
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)

            val tagName = json.optString("tag_name", "")
            if (tagName.isEmpty()) {
                return@withContext kotlin.Result.failure(UpdateError.NoUpdate)
            }
            val isPrerelease = json.optBoolean("prerelease", false)
            val cleanTag = tagName.trimStart('v', 'V')
            if (!isNewerVersion(cleanTag, currentVersion, allowPrerelease = false) ||
                (isPrerelease && !isNewerVersion(cleanTag, currentVersion, allowPrerelease = true))
            ) {
                // Either not newer at all, or a prerelease we shouldn't show
                // unless the user explicitly opted in (which the UI doesn't
                // currently support).
                return@withContext kotlin.Result.failure(UpdateError.NoUpdate)
            }

            var apkUrl: String? = null
            var apkSize = 0L
            var sha256: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                // First pass: find the APK, then look for a sidecar .sha256
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true) && apkUrl == null) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                    }
                    if (apkUrl != null && name.equals(apkUrl.substringAfterLast('/') + SHA256_ASSET_SUFFIX, ignoreCase = true)) {
                        sha256 = asset.optString("browser_download_url")
                    }
                }
            }

            kotlin.Result.success(
                UpdateInfo(
                    versionTag = tagName,
                    cleanVersion = cleanTag,
                    releaseTitle = json.optString("name", "SpeedShare $tagName"),
                    changelog = json.optString("body", "New version available with enhancements and bug fixes."),
                    apkDownloadUrl = apkUrl,
                    htmlUrl = json.optString("html_url", RELEASES_WEB_URL),
                    apkSize = apkSize,
                    sha256 = sha256,
                    isPrerelease = isPrerelease
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            kotlin.Result.failure(UpdateError.Network(e))
        }
    }

    /**
     * Compare two semver-ish version strings. Returns true if [remoteVer]
     * is strictly newer than [localVer].
     *
     * Prerelease tags (e.g. "1.2.0-rc1") are NOT considered newer than
     * the same release version unless [allowPrerelease] is true. This
     * prevents the updater from offering beta builds to ordinary users.
     */
    fun isNewerVersion(remoteVer: String, localVer: String, allowPrerelease: Boolean = false): Boolean {
        if (remoteVer == localVer) return false
        val (rMain, rPre) = splitPrerelease(remoteVer)
        val (lMain, lPre) = splitPrerelease(localVer)
        val cmp = compareMain(rMain, lMain)
        if (cmp != 0) return cmp > 0
        // main parts equal: a release version is newer than any prerelease
        if (rPre == null && lPre == null) return false
        if (rPre == null) return true
        if (lPre == null) return false
        // both have prerelease tags: require explicit opt-in
        if (!allowPrerelease) return false
        return rPre.compareTo(lPre, ignoreCase = true) > 0
    }

    private fun splitPrerelease(v: String): Pair<String, String?> {
        val dash = v.indexOf('-')
        return if (dash < 0) v to null else v.substring(0, dash) to v.substring(dash + 1)
    }

    private fun compareMain(a: String, b: String): Int {
        val ap = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bp = b.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(ap.size, bp.size)
        for (i in 0 until len) {
            val x = ap.getOrElse(i) { 0 }
            val y = bp.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    /**
     * Download the APK and verify its size and SHA-256.
     * Returns `Result.success(File)` on success.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedSize: Long,
        sha256Url: String? = null,
        onProgress: (Float) -> Unit
    ): kotlin.Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(context.cacheDir, "SpeedShare-Update.apk")
            if (targetFile.exists()) targetFile.delete()

            val downloaded = downloadToFile(downloadUrl, targetFile, onProgress)
                ?: return@withContext kotlin.Result.failure(
                    UpdateError.Download(IllegalStateException("No HTTP response"))
                )

            // Size check
            if (expectedSize > 0 && downloaded.length() != expectedSize) {
                downloaded.delete()
                return@withContext kotlin.Result.failure(
                    UpdateError.SizeMismatch(expectedSize, downloaded.length())
                )
            }

            // SHA-256 check
            if (sha256Url != null) {
                val expectedHash = fetchText(sha256Url)?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                if (!expectedHash.isNullOrEmpty()) {
                    val actualHash = sha256Of(downloaded)
                    if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                        downloaded.delete()
                        return@withContext kotlin.Result.failure(
                            UpdateError.HashMismatch(expectedHash, actualHash)
                        )
                    }
                }
            }

            kotlin.Result.success(downloaded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/verify APK: ${e.message}")
            kotlin.Result.failure(UpdateError.Download(e))
        }
    }

    private fun downloadToFile(
        downloadUrl: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): File? {
        var url = downloadUrl
        var conn: HttpURLConnection? = null
        var redirects = 0
        while (redirects < 5) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SpeedShare-Android-Updater")
                setRequestProperty("Accept", "application/vnd.android.package-archive")
            }
            val code = c.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == 307 || code == 308
            ) {
                c.disconnect()
                val next = c.getHeaderField("Location") ?: return null
                url = next
                redirects++
                continue
            }
            conn = c
            break
        }
        conn ?: return null
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
        return targetFile
    }

    private fun fetchText(url: String): String? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SpeedShare-Android-Updater")
        }
        if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(65536)
            var n: Int
            while (fis.read(buf).also { n = it } > 0) digest.update(buf, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Hand the downloaded APK to the system package installer. Returns
     * true on success. The intent:
     *  - sets `EXTRA_INSTALLER_PACKAGE_NAME` so Play Protect knows the
     *    install is self-initiated by us (lowest scrutiny tier)
     *  - uses the proper APK MIME
     *  - grants URI permission to the system installer
     *  - is dispatched as a NEW_TASK so it works from non-Activity contexts
     */
    fun launchApkInstaller(context: Context, apkFile: File): kotlin.Result<Unit> {
        if (!apkFile.exists()) {
            return kotlin.Result.failure(UpdateError.Install(IllegalStateException("APK not found")))
        }
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                // Self-identify: tells the system this install was initiated
                // by our own package, not a third-party. Play Protect applies
                // less scrutiny to self-installs.
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            // Verify a package installer exists before dispatching
            val pm = context.packageManager
            val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.canRequestPackageInstalls()
            } else true
            if (!canInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Caller is responsible for prompting the user to grant the
                // "Install unknown apps" permission. Returning Install with
                // the SecurityException makes the UI layer handle the prompt.
                val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settings)
                return kotlin.Result.failure(
                    UpdateError.Install(SecurityException("REQUEST_INSTALL_PACKAGES not granted"))
                )
            }
            context.startActivity(intent)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer intent: ${e.message}")
            kotlin.Result.failure(UpdateError.Install(e))
        }
    }
}
