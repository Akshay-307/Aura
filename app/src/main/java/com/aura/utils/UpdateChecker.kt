package com.aura.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/Akshay-307/Aura-Releases/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String?,
        val releaseNotes: String?,
        val error: String? = null
    )

    /** Fetches the latest release from GitHub and compares with installed version. */
    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Aura-Android-App")
                .build()

            Log.d(TAG, "Checking for updates at: $GITHUB_API_URL")

            client.newCall(request).execute().use { response ->
                val code = response.code
                Log.d(TAG, "GitHub API response code: $code")

                if (code == 404) {
                    // No releases published yet
                    Log.d(TAG, "No releases found on GitHub (404)")
                    return@withContext UpdateInfo(false, "", null, null)
                }

                if (!response.isSuccessful) {
                    Log.w(TAG, "Unsuccessful response: $code")
                    return@withContext UpdateInfo(
                        false, "", null, null,
                        error = "Server returned HTTP $code"
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "Empty response body")
                    return@withContext UpdateInfo(false, "", null, null, error = "Empty response")
                }

                val json = JSONObject(body)

                // tag_name is like "v1.0.1" — strip leading 'v' only
                val rawTag = json.optString("tag_name", "")
                val latestVersion = rawTag
                    .trimStart()
                    .removePrefix("v")
                    .removeSuffix("-release")
                    .trim()

                if (latestVersion.isBlank()) {
                    Log.w(TAG, "Could not parse tag_name: '$rawTag'")
                    return@withContext UpdateInfo(false, "", null, null, error = "Invalid tag")
                }

                val currentVersion = getAppVersionName(context)
                Log.d(TAG, "Current version: $currentVersion | Latest version: $latestVersion")

                val isNewer = isVersionNewer(currentVersion, latestVersion)
                Log.d(TAG, "Is update available: $isNewer")

                // Find APK download URL from assets
                var downloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url")
                            Log.d(TAG, "Found APK asset: $name -> $downloadUrl")
                            break
                        }
                    }
                }
                // Fall back to release HTML page
                if (downloadUrl.isNullOrBlank()) {
                    downloadUrl = json.optString("html_url")
                    Log.d(TAG, "No APK asset found; using release page: $downloadUrl")
                }

                val releaseNotes = json.optString("body", "").ifBlank { null }

                UpdateInfo(isNewer, latestVersion, downloadUrl, releaseNotes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while checking for updates", e)
            UpdateInfo(false, "", null, null, error = e.localizedMessage ?: "Unknown error")
        }
    }

    /** Returns the installed versionName from PackageManager. */
    fun getAppVersionName(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            (info.versionName ?: "1.0").trim()
        } catch (e: Exception) {
            Log.e(TAG, "Could not read versionName", e)
            "1.0"
        }
    }

    /**
     * Compares two semantic version strings (e.g. "1.0" vs "1.0.1").
     * Returns true if [latest] is strictly newer than [current].
     */
    private fun isVersionNewer(current: String, latest: String): Boolean {
        val cur = current.split(".").mapNotNull { it.trim().toIntOrNull() }
        val lat = latest.split(".").mapNotNull { it.trim().toIntOrNull() }

        if (cur.isEmpty() || lat.isEmpty()) {
            Log.w(TAG, "Could not parse versions: current='$current' latest='$latest'")
            return false
        }

        val len = maxOf(cur.size, lat.size)
        for (i in 0 until len) {
            val c = cur.getOrElse(i) { 0 }
            val l = lat.getOrElse(i) { 0 }
            if (l > c) return true
            if (c > l) return false
        }
        return false // equal
    }

    /** Shows an update dialog with download & install action. */
    fun showUpdateDialog(context: Context, updateInfo: UpdateInfo) {
        val notes = updateInfo.releaseNotes?.takeIf { it.isNotBlank() }
            ?: "No release notes provided."
        AlertDialog.Builder(context)
            .setTitle("Update Available — v${updateInfo.latestVersion}")
            .setMessage("A new version is ready.\n\nRelease Notes:\n$notes")
            .setPositiveButton("Download & Install") { _, _ ->
                val url = updateInfo.downloadUrl ?: return@setPositiveButton
                val scope = (context as? AppCompatActivity)?.lifecycleScope
                    ?: @Suppress("OPT_IN_USAGE") GlobalScope
                scope.launch { downloadAndInstallApk(context, url) }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    /** Downloads the APK into cache and triggers the system installer. */
    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String) =
        withContext(Dispatchers.Main) {
            val progressDialog = AlertDialog.Builder(context)
                .setTitle("Downloading Update")
                .setMessage("Preparing download...")
                .setCancelable(false)
                .create()
            progressDialog.show()

            withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(downloadUrl)
                        .header("User-Agent", "Aura-Android-App").build()
                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful)
                            throw Exception("Server returned ${response.code}")
                        val body = response.body
                            ?: throw Exception("Empty response body")
                        val contentLen = body.contentLength()

                        val apkFile = File(context.cacheDir, "aura_update.apk")
                        if (apkFile.exists()) apkFile.delete()

                        val buf = ByteArray(8192)
                        var read: Int
                        var total = 0L

                        body.byteStream().use { input ->
                            apkFile.outputStream().use { out ->
                                while (input.read(buf).also { read = it } != -1) {
                                    out.write(buf, 0, read)
                                    total += read
                                    if (contentLen > 0) {
                                        val pct = ((total * 100) / contentLen).toInt()
                                        withContext(Dispatchers.Main) {
                                            progressDialog.setMessage("Downloaded $pct%")
                                        }
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            triggerInstall(context, apkFile)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        AlertDialog.Builder(context)
                            .setTitle("Download Failed")
                            .setMessage(e.localizedMessage ?: "Unknown error")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

    private fun triggerInstall(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install trigger failed", e)
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }
}
