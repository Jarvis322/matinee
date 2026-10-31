package com.tcltv.sahne.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.tcltv.sahne.HomeKeyProblem
import kotlinx.coroutines.delay

/**
 * Knows whether the Home button service is switched on and actually running, and, once Automatic ADB has granted
 * Matinee the right to change secure settings, turns it back on when the TV has switched it off or let it die.
 */
class HomeKeyGuard(context: Context) {

    private val appContext = context.applicationContext
    private val component = ComponentName(appContext, HomeKeyService::class.java)
    private val prefs = appContext.getSharedPreferences("home_key", Context.MODE_PRIVATE)
    private val adb = LocalAdb(appContext)

    fun isEnabled(): Boolean = enabledServices().any { it.equals(component.flattenToString(), ignoreCase = true) }

    /**
     * Whether Android has the service running. It can be switched on and still stopped: Android doesn't restart an
     * accessibility service whose process died until it is switched off and on again.
     */
    fun isRunning(): Boolean =
        appContext.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.let { info -> ComponentName(info.packageName, info.name) } == component }

    /**
     * What keeps the Home button from opening Matinee after the user turned it on, or null when nothing does. A TV that
     * force-stops Matinee (some clean up apps in standby) also takes it off Android's list of enabled services, so
     * having seen it run once is remembered.
     */
    fun problem(): HomeKeyProblem? {
        val enabled = isEnabled()
        if (enabled && isRunning()) {
            if (!prefs.getBoolean(KEY_WANTED, false)) prefs.edit().putBoolean(KEY_WANTED, true).apply()
            return null
        }
        return when {
            enabled -> HomeKeyProblem.STOPPED
            prefs.getBoolean(KEY_WANTED, false) -> HomeKeyProblem.SWITCHED_OFF
            else -> null
        }
    }

    /** The user doesn't want the Home button; stop asking for it and stop turning it back on. */
    fun forget() = prefs.edit().putBoolean(KEY_WANTED, false).apply()

    /** Whether Matinee may switch the service itself, which only Automatic ADB makes possible. */
    fun canSwitch(): Boolean = adb.isSetUp()

    /** Switches the service on directly, after the user agreed to it in Matinee; false without the permission. */
    suspend fun switchOn(): Boolean {
        if (!canSwitch()) return false
        val others = enabledServices().filterNot { it.equals(component.flattenToString(), ignoreCase = true) }
        return try {
            if (isEnabled()) {
                // Writing the list without it first makes Android unbind the stopped service, so adding it back binds
                // it anew; both writes at once would be read as no change at all.
                write(others)
                delay(REBIND_DELAY_MS)
            }
            write(others + component.flattenToString())
            Settings.Secure.putInt(appContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            prefs.edit().putBoolean(KEY_WANTED, true).apply()
            true
        } catch (e: SecurityException) {
            // The grant was taken back (a reinstall, or by hand over adb).
            Log.w(TAG, "cannot switch the Home button service: ${e.message}")
            false
        }
    }

    /** Switches the service off for good, so neither the watchdog nor the home screen brings it back. */
    fun switchOff(): Boolean {
        if (!canSwitch()) return false
        return try {
            write(enabledServices().filterNot { it.equals(component.flattenToString(), ignoreCase = true) })
            forget()
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot switch the Home button service: ${e.message}")
            false
        }
    }

    /** Turns the service back on if the user wants it and something stopped it; true when it was repaired. */
    suspend fun repairIfNeeded(): Boolean {
        // Android binds accessibility services again at every start and may still be doing so right after boot.
        if (SystemClock.elapsedRealtime() < BOOT_SETTLE_MS) return false
        if (problem() == null || !canSwitch()) return false
        return switchOn()
    }

    private fun enabledServices(): List<String> =
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .filter { it.isNotBlank() }

    private fun write(services: List<String>) {
        Settings.Secure.putString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":"))
    }

    companion object {
        /** How long after boot the service's state is trusted. */
        const val BOOT_SETTLE_MS = 2 * 60_000L

        private const val TAG = "HomeKeyGuard"
        private const val KEY_WANTED = "wanted"
        private const val REBIND_DELAY_MS = 800L
    }
}
