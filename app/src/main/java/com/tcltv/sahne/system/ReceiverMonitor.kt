package com.tcltv.sahne.system

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** NOT_SUPPORTED: the TV has no such receiver at all. UNAVAILABLE: it has one, but it is turned off. */
enum class ReceiverState { NOT_SUPPORTED, UNAVAILABLE, READY, PLAYING }

data class ReceiverStatus(val state: ReceiverState, val canDetectPlayback: Boolean)

/** Tracks a built-in casting receiver (AirPlay, Google Cast): installed and enabled, and whether it is playing. */
class ReceiverMonitor(context: Context, private val packages: List<String>) {

    private val appContext = context.applicationContext
    private val listenerComponent = ComponentName(appContext, TvNotificationListener::class.java)
    private val sessionManager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    val status: Flow<ReceiverStatus> = callbackFlow {
        if (!isReceiverEnabled()) {
            val state = if (isReceiverInstalled()) ReceiverState.UNAVAILABLE else ReceiverState.NOT_SUPPORTED
            trySend(ReceiverStatus(state, canDetectPlayback = false))
            awaitClose { }
            return@callbackFlow
        }

        val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
        val handler = Handler(Looper.getMainLooper())

        fun publish(controllers: List<MediaController>?) {
            val playing = controllers.orEmpty().any { controller ->
                controller.packageName in packages &&
                    controller.playbackState?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
            }
            trySend(ReceiverStatus(if (playing) ReceiverState.PLAYING else ReceiverState.READY, canDetectPlayback = controllers != null))
        }

        fun watch(controllers: List<MediaController>?) {
            controllerCallbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            controllerCallbacks.clear()
            controllers.orEmpty().filter { it.packageName in packages }.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish(activeSessionsOrNull())
                }
                controller.registerCallback(callback, handler)
                controllerCallbacks[controller] = callback
            }
            publish(controllers)
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        val registered = try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            true
        } catch (e: SecurityException) {
            false
        }
        watch(if (registered) activeSessionsOrNull() else null)

        awaitClose {
            controllerCallbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            if (registered) sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        }
    }.distinctUntilChanged()

    private fun activeSessionsOrNull(): List<MediaController>? =
        try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            null
        }

    private fun isReceiverInstalled(): Boolean = packages.any { pkg ->
        runCatching { appContext.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
    }

    private fun isReceiverEnabled(): Boolean = packages.any { pkg ->
        runCatching { appContext.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
    }

    companion object {
        val AIRPLAY_PACKAGES = listOf("com.tcl.airplay2", "com.realtek.airplay2")
        val GOOGLE_CAST_PACKAGES = listOf("com.google.android.apps.mediashell")
    }
}
