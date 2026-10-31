package com.tcltv.sahne.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbKeyPair
import dadb.AdbTimeoutException
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Matinee's own connection to the TV's adb daemon on localhost, used once to grant itself what Android only grants
 * over adb. The TV asks the user to allow it the first time; the key stays in Matinee's private storage.
 */
class LocalAdb(context: Context) {

    private val appContext = context.applicationContext
    private val keyDir = File(appContext.filesDir, "adb")

    enum class SetupResult { DONE, ADB_OFF, NOT_ALLOWED, FAILED }

    /** Whether the one-time setup has run: the grant survives app updates, but not a reinstall. */
    fun isSetUp(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Connects (the TV shows its "Allow debugging?" prompt the first time) and grants Matinee the permissions that make
     * the Home button service and notification access manage themselves from then on.
     */
    suspend fun setUp(): SetupResult = withContext(Dispatchers.IO) {
        val pkg = appContext.packageName
        val listener = ComponentName(appContext, TvNotificationListener::class.java).flattenToString()
        val commands = buildList {
            add("pm grant $pkg ${Manifest.permission.WRITE_SECURE_SETTINGS}")
            // Android 13+ blocks accessibility for apps installed from an APK file until this is allowed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("appops set $pkg ACCESS_RESTRICTED_SETTINGS allow")
            add("cmd notification allow_listener $listener")
        }
        // dadb reports a refused port and an unanswered prompt alike; only a plain connect tells them apart.
        if (!isListening()) return@withContext SetupResult.ADB_OFF
        try {
            connect(SETUP_TIMEOUT_MS).use { adb ->
                commands.forEach { command ->
                    val response = adb.shell(command)
                    if (response.exitCode != 0) Log.w(TAG, "'$command' exited ${response.exitCode}: ${response.allOutput.trim()}")
                }
            }
            if (isSetUp()) SetupResult.DONE else SetupResult.FAILED
        } catch (e: AdbConnectException) {
            // The daemon is there (checked above) but the handshake never completed: the prompt was declined or
            // left unanswered until the timeout.
            SetupResult.NOT_ALLOWED
        } catch (e: AdbAuthException) {
            SetupResult.NOT_ALLOWED
        } catch (e: AdbTimeoutException) {
            SetupResult.NOT_ALLOWED
        } catch (e: IOException) {
            Log.w(TAG, "adb setup failed: ${e.message}")
            SetupResult.FAILED
        }
    }

    /** Runs one shell command on an already allowed connection; false when adb is off or not allowed. */
    fun runQuietly(command: String): Boolean = try {
        connect(COMMAND_TIMEOUT_MS).use { it.shell(command).exitCode == 0 }
    } catch (e: IOException) {
        Log.w(TAG, "adb '$command' failed: ${e.message}")
        false
    }

    private fun isListening(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS) }
        true
    } catch (e: IOException) {
        false
    }

    private fun connect(timeoutMs: Int): Dadb {
        keyDir.mkdirs()
        val privateKey = File(keyDir, "adbkey")
        val publicKey = File(keyDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) AdbKeyPair.generate(privateKey, publicKey)
        return Dadb.create(HOST, PORT, AdbKeyPair.read(privateKey, publicKey), CONNECT_TIMEOUT_MS, timeoutMs)
    }

    private companion object {
        const val TAG = "LocalAdb"
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val CONNECT_TIMEOUT_MS = 3_000
        // Long enough for the user to find the TV's prompt and allow it.
        const val SETUP_TIMEOUT_MS = 60_000
        const val COMMAND_TIMEOUT_MS = 5_000
    }
}
