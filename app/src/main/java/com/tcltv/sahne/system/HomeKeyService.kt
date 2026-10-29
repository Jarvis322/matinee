package com.tcltv.sahne.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.tcltv.sahne.MainActivity

/**
 * Keeps Matinee as the home screen on TVs whose stock launcher holds on to Home: it takes the Home key, and when the
 * TV shows the stock home screen anyway (waking up, restarting, backing out of an app) it opens Matinee over it.
 * Only key events and the stock launcher's own window changes are received; window content is never requested.
 */
class HomeKeyService : AccessibilityService() {

    private var stockHomes: Set<ComponentName> = emptySet()
    private var lastOpenedAt = 0L

    override fun onServiceConnected() {
        stockHomes = stockHomeActivities()
        // Window events are limited to the stock launcher's packages, so no other app's activity is ever seen.
        serviceInfo = serviceInfo.apply {
            if (stockHomes.isEmpty()) {
                eventTypes = 0
            } else {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                packageNames = stockHomes.map { it.packageName }.distinct().toTypedArray()
            }
        }
        // After a restart the stock home screen is often up before this service is bound, so its window event is
        // never seen; take over once. Later reconnects (an app update, say) leave the screen alone.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openMatinee()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openMatinee()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val shown = ComponentName(event.packageName?.toString() ?: return, event.className?.toString() ?: return)
        // Only the home screen itself; the stock launcher's other screens (its settings, say) stay reachable.
        if (shown in stockHomes) openMatinee()
    }

    override fun onInterrupt() = Unit

    private fun openMatinee() {
        val now = SystemClock.elapsedRealtime()
        // The Home key and the home window it brings up arrive together; one launch is enough.
        if (now - lastOpenedAt < REOPEN_GUARD_MS) return
        lastOpenedAt = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    private fun stockHomeActivities(): Set<ComponentName> {
        // Some apps also tag their app-list entry with HOME (beIN Connect's splash screen); a stock home screen is
        // never in the app list, so those are apps being opened, not the home screen coming back.
        val appListEntries = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
            .flatMap { activities(it) }
            .map { it.component }
            .toSet()
        return activities(Intent.CATEGORY_HOME)
            // Negative priority marks fallbacks (the boot-time FallbackHome, setup recovery), which must stay alone.
            .filter { it.priority >= 0 && it.activityInfo.packageName != packageName }
            .map { it.component }
            .filterNot { it in appListEntries }
            .toSet()
    }

    private fun activities(category: String): List<ResolveInfo> =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL)

    private val ResolveInfo.component get() = ComponentName(activityInfo.packageName, activityInfo.name)

    private companion object {
        const val REOPEN_GUARD_MS = 1_000L
        const val BOOT_WINDOW_MS = 2 * 60_000L
    }
}
