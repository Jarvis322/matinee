package com.tcltv.sahne.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The Play build is updated by Play; there is never an update of its own to offer. */
object SelfUpdater {
    val update: StateFlow<SelfUpdate?> = MutableStateFlow<SelfUpdate?>(null).asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    suspend fun checkIfDue(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    suspend fun install(context: Context) = Unit
}
