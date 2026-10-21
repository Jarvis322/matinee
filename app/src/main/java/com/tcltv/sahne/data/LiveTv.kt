package com.tcltv.sahne.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The TV's built-in tuner (antenna, cable or satellite), opened like the stock launcher's Live TV button.
 *
 * Only the shortcut is possible: from Android 12 the system shares a TV's channel list only with privileged apps such
 * as the stock launcher, so a third-party launcher can't list channels even with READ_EPG_DATA.
 */
data class LiveTvInput(val id: String, val label: String)

class LiveTvCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val tvInputManager = appContext.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    val tuners: Flow<List<LiveTvInput>> = callbackFlow {
        val manager = tvInputManager
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputAdded(inputId: String) { trySend(loadTuners(manager)) }
            override fun onInputRemoved(inputId: String) { trySend(loadTuners(manager)) }
            override fun onInputUpdated(inputId: String) { trySend(loadTuners(manager)) }
        }
        trySend(loadTuners(manager))
        manager.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterCallback(callback) }
    }

    /**
     * Built-in tuners are hardware inputs, whose ids end in "/HW<n>" by the framework's own naming. App inputs of the
     * same type, such as preview players from video apps, are left out.
     */
    private fun loadTuners(manager: TvInputManager): List<LiveTvInput> =
        manager.tvInputList
            .filter { it.type == TvInputInfo.TYPE_TUNER && HARDWARE_ID.containsMatchIn(it.id) && it.parentId == null }
            .map { LiveTvInput(it.id, it.loadLabel(appContext)?.toString().orEmpty()) }

    fun tunerIntent(input: LiveTvInput): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        val HARDWARE_ID = Regex("/HW\\d+$")
    }
}
