package com.tcltv.sahne.airplay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** Full-screen surface that shows what the phone mirrors; leaving it (Back) disconnects the phone. */
class MirrorActivity : Activity(), SurfaceHolder.Callback {

    private val finishRequest = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val surface = SurfaceView(this).apply {
            setBackgroundColor(Color.BLACK)
            holder.addCallback(this@MirrorActivity)
        }
        setContentView(surface)
        surface.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        ContextCompat.registerReceiver(this, finishRequest, IntentFilter(ACTION_FINISH), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        AirPlayReceiver.get(this).attachSurface(holder.surface)
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
