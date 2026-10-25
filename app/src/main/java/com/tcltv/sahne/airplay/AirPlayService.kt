package com.tcltv.sahne.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.tcltv.sahne.R

/** Keeps the AirPlay receiver running while Matinee is not in front, so phones can still find the TV. */
class AirPlayService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.airplay_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_cast)
            .setContentTitle(getString(R.string.airplay_notification_title))
            .setContentText(getString(R.string.airplay_notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AirPlayReceiver.get(this).start(intent?.getStringExtra(EXTRA_NAME) ?: DEFAULT_NAME)
        return START_STICKY
    }

    override fun onDestroy() {
        AirPlayReceiver.get(this).stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "airplay"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_NAME = "name"
        private const val DEFAULT_NAME = "Matinee"

        /**
         * The name phones list this TV under: the TV's own device name, so two TVs in one home can be told apart, with
         * Matinee after it so it isn't confused with a receiver built into the TV. DNS-SD names stop at 63 bytes.
         */
        fun receiverName(context: Context): String {
            val device = runCatching { android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?: Build.MODEL.trim().takeIf { it.isNotEmpty() }
                ?: return DEFAULT_NAME
            var name = "$device ($DEFAULT_NAME)"
            while (name.toByteArray(Charsets.UTF_8).size > 63 && name.length > DEFAULT_NAME.length + 4) {
                name = name.substring(0, name.length - DEFAULT_NAME.length - 4).trimEnd() + " ($DEFAULT_NAME)"
            }
            return name
        }

        fun setRunning(context: Context, running: Boolean, name: String) {
            val intent = Intent(context, AirPlayService::class.java).putExtra(EXTRA_NAME, name)
            if (running) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}
