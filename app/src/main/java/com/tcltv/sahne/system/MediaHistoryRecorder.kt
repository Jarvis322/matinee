package com.tcltv.sahne.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.tcltv.sahne.data.MediaHistory
import com.tcltv.sahne.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/** Remembers the last item each app plays, using the metadata apps publish on their media sessions. */
class MediaHistoryRecorder(context: Context, private val listener: ComponentName) {

    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val history = MediaHistory.get(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableMapOf<MediaController, MediaController.Callback>()
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var recordingEnabled = true

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sync(it.orEmpty()) }

    fun start() {
        if (started) return
        // "Show last played" off means nothing is written, not just hidden.
        scope.launch { SettingsStore(appContext).settings.collect { recordingEnabled = it.showLastPlayed } }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            started = true
            sync(sessionManager.getActiveSessions(listener))
        } catch (e: SecurityException) {
            started = false
        }
    }

    fun stop() {
        scope.coroutineContext.cancelChildren()
        if (started) sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        started = false
        watched.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        watched.clear()
    }

    private fun sync(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watched.keys.filter { it.sessionToken !in tokens }.forEach { gone ->
            watched.remove(gone)?.let { gone.unregisterCallback(it) }
        }
        controllers
            .filter { it.packageName != appContext.packageName && watched.keys.none { w -> w.sessionToken == it.sessionToken } }
            .forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = capture(controller)
                    override fun onPlaybackStateChanged(state: PlaybackState?) = capture(controller)
                }
                controller.registerCallback(callback, handler)
                watched[controller] = callback
                capture(controller)
            }
    }

    private fun capture(controller: MediaController) {
        if (!recordingEnabled) return
        val state = controller.playbackState ?: return
        if (state.state != PlaybackState.STATE_PLAYING && state.state != PlaybackState.STATE_PAUSED) return
        val metadata = controller.metadata ?: return
        val title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val subtitle = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM)
        val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        history.record(controller.packageName, title, subtitle, artwork, state.position, duration)
    }

    private fun MediaMetadata.text(key: String): String? = getString(key)?.trim()?.takeIf { it.isNotEmpty() }
}
