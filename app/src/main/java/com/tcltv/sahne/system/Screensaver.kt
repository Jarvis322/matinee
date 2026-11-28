package com.tcltv.sahne.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Whether Matinee is the TV's screensaver, and making it one. Choosing it in the TV's settings is the normal way;
 * once Automatic ADB has granted the right to change secure settings, Matinee can set itself directly, which matters on
 * Google TV, whose settings only offer its own Ambient mode.
 */
class Screensaver(context: Context) {

    private val appContext = context.applicationContext
    private val component = ComponentName(appContext, ScreensaverService::class.java).flattenToString()
    private val adb = LocalAdb(appContext)
    private val prefs = appContext.getSharedPreferences("screensaver", Context.MODE_PRIVATE)

    /** Null when the TV doesn't let apps read which screensaver is chosen. */
    fun isMatinee(): Boolean? = try {
        Settings.Secure.getString(appContext.contentResolver, COMPONENTS)
            ?.split(',')
            ?.any { it.equals(component, ignoreCase = true) }
            ?: false
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun canSetDirectly(): Boolean = adb.isSetUp()

    /**
     * Makes Matinee the screensaver and turns screensavers on; false without the permission. The TV's own choice is
     * kept, since Google TV's settings can't bring it back once something else is set.
     */
    fun setMatinee(): Boolean {
        if (!canSetDirectly()) return false
        return try {
            val current = read(COMPONENTS)
            if (!current.isNullOrBlank() && !current.contains(component, ignoreCase = true)) {
                prefs.edit().putString(KEY_PREVIOUS, current).apply()
            }
            Settings.Secure.putString(appContext.contentResolver, COMPONENTS, component)
            Settings.Secure.putInt(appContext.contentResolver, ENABLED, 1)
            true
        } catch (e: SecurityException) {
            // The grant was taken back (a reinstall, or by hand over adb).
            Log.w(TAG, "cannot set the screensaver: ${e.message}")
            false
        }
    }

    /**
     * Puts back the screensaver chosen before Matinee, or the TV's default when Matinee was set by a version that didn't
     * keep it; false without the permission.
     */
    fun restorePrevious(): Boolean {
        if (!canSetDirectly()) return false
        val previous = prefs.getString(KEY_PREVIOUS, null) ?: read(DEFAULT_COMPONENT) ?: return false
        return try {
            Settings.Secure.putString(appContext.contentResolver, COMPONENTS, previous)
            prefs.edit().remove(KEY_PREVIOUS).apply()
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot restore the screensaver: ${e.message}")
            false
        }
    }

    private fun read(key: String): String? = try {
        Settings.Secure.getString(appContext.contentResolver, key)?.takeIf { it.isNotBlank() }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TAG = "Screensaver"
        const val KEY_PREVIOUS = "previous"
        // Set by Android at startup from the device's configuration: the TV's own screensaver, such as Ambient mode.
        const val DEFAULT_COMPONENT = "screensaver_default_component"
        // Not in the public SDK, but the same keys Android's own screensaver settings write.
        const val COMPONENTS = "screensaver_components"
        const val ENABLED = "screensaver_enabled"
    }
}
