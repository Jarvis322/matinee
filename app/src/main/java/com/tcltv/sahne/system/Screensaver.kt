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

    /** Makes Matinee the screensaver and turns screensavers on; false without the permission. */
    fun setMatinee(): Boolean {
        if (!canSetDirectly()) return false
        return try {
            Settings.Secure.putString(appContext.contentResolver, COMPONENTS, component)
            Settings.Secure.putInt(appContext.contentResolver, ENABLED, 1)
            true
        } catch (e: SecurityException) {
            // The grant was taken back (a reinstall, or by hand over adb).
            Log.w(TAG, "cannot set the screensaver: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "Screensaver"
        // Not in the public SDK, but the same keys Android's own screensaver settings write.
        const val COMPONENTS = "screensaver_components"
        const val ENABLED = "screensaver_enabled"
    }
}
