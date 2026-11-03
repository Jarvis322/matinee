package com.tcltv.sahne.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.tcltv.sahne.R

/**
 * Switches the TV to an input. Most TVs open one through the standard Android TV "passthrough" intent, but some, such
 * as projectors with their own source menu, have nothing that handles it. Then the TV's own input picker is opened
 * instead (which needs Automatic ADB to press the key), and if that isn't possible the user is told.
 */
class InputSwitcher(context: Context, private val adb: LocalAdb) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /** Blocks on adb when it falls back, so call it off the main thread. */
    fun open(intent: Intent) {
        if (start(intent)) return
        if (adb.isSetUp() && adb.runQuietly("input keyevent ${KeyEvent.KEYCODE_TV_INPUT}")) return
        handler.post { Toast.makeText(appContext, R.string.input_switch_failed, Toast.LENGTH_LONG).show() }
    }

    private fun start(intent: Intent): Boolean = try {
        appContext.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "nothing handles ${intent.data}")
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "not allowed to open ${intent.data}: ${e.message}")
        false
    }

    private companion object {
        const val TAG = "InputSwitcher"
    }
}
