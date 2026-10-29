package com.tcltv.sahne.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

private const val TAG = "SelfUpdater"

/**
 * Looks for a newer Matinee among the GitHub releases and installs it when the user asks. Android shows its own
 * install screen for every update; the APK is checked to be a newer Matinee first, and Android itself refuses one
 * signed with a different key.
 */
object SelfUpdater {

    private const val LATEST_RELEASE = "https://api.github.com/repos/Jarvis322/matinee/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/Jarvis322/matinee/releases/download/"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60_000L
    private const val TIMEOUT_MS = 15_000
    private const val APK_NAME = "update.apk"

    private val _update = MutableStateFlow<SelfUpdate?>(null)
    val update: StateFlow<SelfUpdate?> = _update.asStateFlow()

    private val lock = Mutex()
    private var lastCheckAt = 0L
    private var downloadUrl: String? = null

    /** Asks GitHub at most every few hours; the home screen calls this each time it comes back. */
    suspend fun checkIfDue(context: Context) {
        if (lock.isLocked) return
        lock.withLock {
            val now = SystemClock.elapsedRealtime()
            if (lastCheckAt != 0L && now - lastCheckAt < CHECK_INTERVAL_MS) return
            val stage = _update.value?.stage
            if (stage == SelfUpdate.Stage.DOWNLOADING || stage == SelfUpdate.Stage.CONFIRMING) return
            try {
                val release = latestRelease()
                lastCheckAt = now
                if (release == null || !isNewer(release.version, installedVersion(context))) {
                    _update.value = null
                    return
                }
                downloadUrl = release.apkUrl
                if (_update.value?.version != release.version) {
                    _update.value = SelfUpdate(release.version, release.publishedAt, SelfUpdate.Stage.AVAILABLE)
                }
            } catch (e: IOException) {
                // Offline or GitHub unreachable: the next return to the home screen after the interval tries again.
                Log.w(TAG, "update check failed: ${e.message}")
            }
        }
    }

    /** Downloads the update and hands it to Android's installer, which asks the user to confirm. */
    suspend fun install(context: Context) {
        val current = _update.value ?: return
        val url = downloadUrl ?: return
        if (current.stage == SelfUpdate.Stage.DOWNLOADING || current.stage == SelfUpdate.Stage.CONFIRMING) return
        _update.value = current.copy(stage = SelfUpdate.Stage.DOWNLOADING, percent = 0)
        val apk = File(context.cacheDir, APK_NAME)
        try {
            download(url, apk) { percent -> _update.value = _update.value?.copy(percent = percent) }
            verify(context, apk)
            commit(context, apk)
            _update.value = _update.value?.copy(stage = SelfUpdate.Stage.CONFIRMING)
        } catch (e: IOException) {
            Log.w(TAG, "update install failed: ${e.message}")
            _update.value = current.copy(stage = SelfUpdate.Stage.FAILED)
        } finally {
            apk.delete()
        }
    }

    /** Called by [UpdateInstallReceiver] when Android's installer gives up or the user cancels. */
    internal fun installFailed(message: String?) {
        Log.w(TAG, "installer reported failure: $message")
        _update.value = _update.value?.copy(stage = SelfUpdate.Stage.FAILED)
    }

    private class Release(val version: String, val publishedAt: Long, val apkUrl: String)

    private suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                // No published release yet.
                404 -> return@withContext null
                else -> throw IOException("HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = runCatching { JSONObject(body) }.getOrElse { throw IOException("Malformed release") }
            val version = json.optString("tag_name").removePrefix("v").takeIf { versionParts(it) != null } ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            val apkUrl = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith(DOWNLOAD_PREFIX) }
                ?: return@withContext null
            val publishedAt = runCatching { Instant.parse(json.optString("published_at")).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            Release(version, publishedAt, apkUrl)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(url: String, target: File, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = connection.contentLengthLong
            var received = 0L
            var reported = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        val percent = if (total > 0) (received * 100 / total).toInt() else 0
                        if (percent != reported) {
                            reported = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            if (total > 0 && received != total) throw IOException("Download incomplete")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Refuses anything but a newer Matinee before the user is asked to install it. The signing key is left to Android,
     * which rejects a mismatched update; reading signers from the downloaded archive returned none on Android 13.
     */
    private fun verify(context: Context, apk: File) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: throw IOException("Not an APK")
        val installed = installedInfo(context)
        if (archive.packageName != context.packageName) throw IOException("Different app: ${archive.packageName}")
        if (versionCode(archive) <= versionCode(installed)) throw IOException("Not newer")
    }

    private suspend fun commit(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            // Mutable: the installer adds the status and the confirmation screen to it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                flags,
            )
            session.commit(status.intentSender)
        }
    }

    private fun installedVersion(context: Context): String = installedInfo(context).versionName.orEmpty()

    private fun installedInfo(context: Context): PackageInfo {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(context.packageName, 0)
        }
    }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageArchiveInfo(apk.path, 0)
        }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun isNewer(candidate: String, installed: String): Boolean {
        val a = versionParts(candidate) ?: return false
        val b = versionParts(installed) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(version: String): List<Int>? =
        version.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}
