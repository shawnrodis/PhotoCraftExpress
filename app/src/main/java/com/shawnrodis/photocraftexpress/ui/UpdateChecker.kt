package com.shawnrodis.photocraftexpress.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates, following the Toolbox pattern: on launch the app checks
 * GitHub Releases at most once per 24 hours; the About dialog also has a
 * manual "Check for updates". A newer release shows its notes with a
 * Download button; the APK is downloaded to cache and handed to the
 * system installer. Nothing ever installs silently — the user always taps
 * Download, then taps Install in the system prompt. (The first true
 * one-tap update requires the app to already be installed from a GitHub
 * release, so the signature matches.)
 */
object UpdateChecker {

    const val REPO = "shawnrodis/PhotoCraftExpress"
    private const val PREFS_LAST_CHECK = "last_update_check_ms"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    data class ReleaseInfo(
        val tag: String,
        val versionName: String,
        val notes: String,
        val apkUrl: String?
    )

    /** "v0.2.0" -> [0, 2, 0]; tolerates missing parts and suffixes. */
    fun parseVersion(tag: String): List<Int> {
        val clean = tag.trim().removePrefix("v").substringBefore("-")
        if (clean.isEmpty()) return emptyList()
        return clean.split(".").map { it.toIntOrNull() ?: 0 }
    }

    /** True when [latest] is strictly newer than [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** Parses a GitHub "latest release" JSON body. Null on bad input. */
    fun parseLatest(json: String): ReleaseInfo? {
        return try {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name", "")
            if (tag.isEmpty()) return null
            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url", null)
                        break
                    }
                }
            }
            ReleaseInfo(
                tag = tag,
                versionName = tag.removePrefix("v"),
                notes = obj.optString("body", ""),
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    fun shouldCheck(prefs: SharedPreferences): Boolean {
        val last = prefs.getLong(PREFS_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS
    }

    fun markChecked(prefs: SharedPreferences) {
        prefs.edit().putLong(PREFS_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** Fetches the latest release from GitHub. Null on any failure. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "PhotoCraftExpress")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseLatest(body)
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the release APK into cache/updates/. Null on failure. */
    suspend fun downloadApk(context: Context, url: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, fileName)
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                if (conn.responseCode != 200) return@withContext null
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                file
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Hands [apkFile] to the system installer. If the app may not install
     * unknown apps yet, opens the system setting for this app instead and
     * returns false (the user grants it, then taps Download again).
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val pm = context.packageManager
            if (!pm.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return false
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
