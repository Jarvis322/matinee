package com.tcltv.sahne.airplay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/** Full-screen surface that shows what the phone mirrors; leaving it (Back) disconnects the phone. */
class MirrorActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surface: SurfaceView
    private var container: FrameLayout? = null
    private var videoWidth = 0
    private var videoHeight = 0

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
        val root = FrameLayout(this).apply {
            addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
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
                videoWidth = width
                videoHeight = height
                fitVideo()
            }
        }
    }

    /** Shows the phone's picture as large as it fits without stretching: a portrait phone gets bars at the sides. */
    private fun fitVideo() {
        val box = container ?: return
        if (videoWidth <= 0 || videoHeight <= 0 || box.width == 0 || box.height == 0) return
        val scale = minOf(box.width.toFloat() / videoWidth, box.height.toFloat() / videoHeight)
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
    }
}
