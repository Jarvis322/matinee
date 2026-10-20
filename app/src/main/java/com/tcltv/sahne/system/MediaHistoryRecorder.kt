package com.tcltv.sahne.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private val watched = mutableMapOf<MediaController, Watched>()

    /** Last metadata and state a session reported, so captures don't unparcel artwork over binder each time. */
    private class Watched(val callback: MediaController.Callback, var metadata: MediaMetadata?, var state: PlaybackState?)
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
            // The listener may already be registered when getActiveSessions throws.
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
            started = false
        }
    }

    fun stop() {
        scope.coroutineContext.cancelChildren()
        if (started) sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        started = false
        watched.forEach { (controller, w) -> controller.unregisterCallback(w.callback) }
        watched.clear()
    }

    private fun sync(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        watched.keys.filter { it.sessionToken !in tokens }.forEach { gone ->
            watched.remove(gone)?.let { w ->
                gone.unregisterCallback(w.callback)
                // The session ended (app closed or switched away): save where the user stopped.
                capture(gone.packageName, w)
            }
        }
        controllers
            .filter { it.packageName != appContext.packageName && watched.keys.none { w -> w.sessionToken == it.sessionToken } }
            .forEach { controller ->
                lateinit var w: Watched
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        w.metadata = metadata
                        capture(controller.packageName, w)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        // Stopping keeps the last useful state, so the position where the user stopped is saved.
                        val previous = w.state
                        if (state != null && state.state in RECORDABLE_STATES) w.state = state
                        if (state?.state == PlaybackState.STATE_STOPPED || state?.state == PlaybackState.STATE_NONE) {
                            if (previous != null) capture(controller.packageName, w)
                            // Saved; a later session end must not count the stopped time as watched.
                            w.state = null
                        } else {
                            capture(controller.packageName, w)
                        }
                    }

                    override fun onSessionDestroyed() = capture(controller.packageName, w)
                }
                w = Watched(callback, controller.metadata, controller.playbackState?.takeIf { it.state in RECORDABLE_STATES })
                controller.registerCallback(callback, handler)
                watched[controller] = w
                capture(controller.packageName, w)
            }
    }

    private fun capture(packageName: String, w: Watched) {
        if (!recordingEnabled) return
        val state = w.state ?: return
        val metadata = w.metadata ?: return
        val title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val subtitle = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM)
        val artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val position = currentPosition(state).let { if (duration > 0) it.coerceAtMost(duration) else it }
        history.record(packageName, title, subtitle, artwork, position, duration)
    }

    /** The reported position is from the last state update; while playing, add the time that has passed since. */
    private fun currentPosition(state: PlaybackState): Long {
        if (state.position < 0) return state.position
        if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0) return state.position
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (elapsed * state.playbackSpeed).toLong()
    }

    private companion object {
        val RECORDABLE_STATES = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED)
    }

    private fun MediaMetadata.text(key: String): String? = getString(key)?.trim()?.takeIf { it.isNotEmpty() }
}
