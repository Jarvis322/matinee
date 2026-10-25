package com.tcltv.sahne.airplay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.tcltv.sahne.R
import androidx.core.content.ContextCompat

/** Full-screen surface that shows what the phone mirrors; leaving it (Back) disconnects the phone. */
class MirrorActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surface: SurfaceView
    private var container: FrameLayout? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private lateinit var hint: TextView
    private val prefs by lazy { getSharedPreferences("airplay", MODE_PRIVATE) }

    /**
     * Fills the screen's width with a portrait phone. A video played full screen on a phone held upright sits in a
     * 16:9 band across the middle of the picture, and filling the width makes that band fill the TV.
     */
    private var fillWidth: Boolean
        get() = prefs.getBoolean(KEY_FILL_WIDTH, false)
        set(value) = prefs.edit().putBoolean(KEY_FILL_WIDTH, value).apply()

    private val finishRequest = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The window is black, not the SurfaceView: a background on the view itself paints over the video layer.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        surface = SurfaceView(this).apply { holder.addCallback(this@MirrorActivity) }
        hint = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xCC15182A.toInt())
            }
            visibility = View.GONE
        }
        val root = FrameLayout(this).apply {
            addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(48) })
            // A new screen size means the phone rotated; fit it again.
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }
        }
        container = root
        setContentView(root)
        surface.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        ContextCompat.registerReceiver(this, finishRequest, IntentFilter(ACTION_FINISH), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AirPlayReceiver.get(this).attachSurface(holder.surface) { width, height ->
            runOnUiThread {
                val becamePortrait = height > width && videoHeight <= videoWidth
                videoWidth = width
                videoHeight = height
                fitVideo()
                if (becamePortrait) showHint()
            }
        }
    }

    /**
     * Shows the phone's picture as large as it fits without stretching: a portrait phone gets bars at the sides, or,
     * with [fillWidth], fills the width and lets the top and bottom run off the screen.
     */
    private fun fitVideo() {
        val box = container ?: return
        if (videoWidth <= 0 || videoHeight <= 0 || box.width == 0 || box.height == 0) return
        val portrait = videoHeight > videoWidth
        val scale = if (portrait && fillWidth) {
            box.width.toFloat() / videoWidth
        } else {
            minOf(box.width.toFloat() / videoWidth, box.height.toFloat() / videoHeight)
        }
        val width = (videoWidth * scale).toInt()
        val height = (videoHeight * scale).toInt()
        val params = surface.layoutParams as FrameLayout.LayoutParams
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            params.gravity = Gravity.CENTER
            surface.layoutParams = params
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (ok && event.repeatCount == 0 && videoHeight > videoWidth) {
            fillWidth = !fillWidth
            fitVideo()
            showHint()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Says what OK does now, for a few seconds. */
    private fun showHint() {
        hint.setText(if (fillWidth) R.string.airplay_hint_show_all else R.string.airplay_hint_fill)
        hint.visibility = View.VISIBLE
        hint.removeCallbacks(hideHint)
        hint.postDelayed(hideHint, HINT_MS)
    }

    private val hideHint = Runnable { hint.visibility = View.GONE }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        AirPlayReceiver.get(this).detachSurface()
    }

    override fun onDestroy() {
        unregisterReceiver(finishRequest)
        if (isFinishing) AirPlayReceiver.get(this).disconnect()
        super.onDestroy()
    }

    companion object {
        const val ACTION_FINISH = "com.yigitcan.matinee.airplay.FINISH"
        private const val KEY_FILL_WIDTH = "fill_width"
        private const val HINT_MS = 4_000L
    }
}
