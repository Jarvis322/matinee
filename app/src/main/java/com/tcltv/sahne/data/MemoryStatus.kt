package com.tcltv.sahne.data

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MemorySnapshot(val totalBytes: Long, val availableBytes: Long) {
    val usedPercent: Int
        get() = if (totalBytes <= 0) 0 else (((totalBytes - availableBytes) * 100) / totalBytes).toInt()
}

data class CleanResult(val freedBytes: Long, val closedApps: Int)

class MemoryStatus(context: Context) {

    private val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val ownPackage = context.packageName

    fun snapshot(): MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return MemorySnapshot(info.totalMem, info.availMem)
    }

    suspend fun clean(candidatePackages: Collection<String>, excluded: Set<String>): CleanResult = withContext(Dispatchers.Default) {
        val before = snapshot().availableBytes
        val targets = candidatePackages.filter { it != ownPackage && it !in excluded && !isProtected(it) }
        targets.forEach { activityManager.killBackgroundProcesses(it) }
        val after = snapshot().availableBytes
        CleanResult(freedBytes = (after - before).coerceAtLeast(0), closedApps = targets.size)
    }

    private companion object {
        // TCL lists system services (input switching, HDMI, quick panel) as launchable apps; never close them.
        val PROTECTED_PREFIXES = listOf("com.tcl.", "com.realtek.", "com.android.", "com.google.android.gms", "com.google.android.gsf")
        val PROTECTED_PACKAGES = setOf(
            "com.google.android.tv.remote.service",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.apps.mediashell",
            "com.android.vending",
        )

        fun isProtected(pkg: String): Boolean =
            pkg in PROTECTED_PACKAGES || PROTECTED_PREFIXES.any { pkg.startsWith(it) } || ".inputmethod." in pkg
    }
}
