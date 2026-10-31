package com.tcltv.sahne.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.tcltv.sahne.R
import com.tcltv.sahne.data.MemoryStatus
import com.tcltv.sahne.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Closes background apps for a remote key, the same way as Settings → Device → Clear memory and with the same
 * exclusions. It runs in Matinee's main process, which owns the settings the Home button service can't read.
 */
class CloseAppsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val excluded = SettingsStore(appContext).settings.first().memoryCleanExclusions
                val result = MemoryStatus(appContext).clean(launchablePackages(appContext), excluded)
                val freedMb = (result.freedBytes / (1024 * 1024)).toInt()
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, appContext.getString(R.string.memory_freed_mb, freedMb), Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun launchablePackages(context: Context): Set<String> =
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { category ->
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
        }.toSet()
}
