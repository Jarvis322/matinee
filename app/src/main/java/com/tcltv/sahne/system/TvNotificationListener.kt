package com.tcltv.sahne.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayStoreUpdate(
    val id: String,
    val title: String,
    val text: String?,
    val postedAt: Long,
    val openIntent: PendingIntent?,
)

/**
 * Process-wide bridge: the system creates the listener service independently of the activity,
 * so its findings are published here for the view model to observe.
 */
object PlayStoreUpdates {
    private val _updates = MutableStateFlow<List<PlayStoreUpdate>>(emptyList())
    val updates: StateFlow<List<PlayStoreUpdate>> = _updates.asStateFlow()

    internal fun publish(value: List<PlayStoreUpdate>) {
        _updates.value = value
    }
}

/**
 * Exists for media-session access and Play Store update notifications; all other notifications are ignored unread.
 */
class TvNotificationListener : NotificationListenerService() {

    private var recorder: MediaHistoryRecorder? = null

    override fun onListenerConnected() {
        publish()
        recorder = MediaHistoryRecorder(this, ComponentName(this, TvNotificationListener::class.java)).also { it.start() }
    }

    override fun onListenerDisconnected() {
        PlayStoreUpdates.publish(emptyList())
        recorder?.stop()
        recorder = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == PLAY_STORE) publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == PLAY_STORE) publish()
    }

    private fun publish() {
        val active = try {
            activeNotifications.orEmpty()
        } catch (e: SecurityException) {
            emptyArray()
        }
        PlayStoreUpdates.publish(
            active
                .filter { it.packageName == PLAY_STORE && it.notification.channelId in UPDATE_CHANNELS }
                .mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    PlayStoreUpdate(
                        id = "${sbn.key}@${sbn.postTime}",
                        title = title,
                        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() },
                        postedAt = sbn.postTime,
                        openIntent = sbn.notification.contentIntent,
                    )
                },
        )
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"
        // Channel ids observed in the Play Store build on the target TV (dumpsys notification).
        private val UPDATE_CHANNELS = setOf("updates-available", "UPDATES")
    }
}
