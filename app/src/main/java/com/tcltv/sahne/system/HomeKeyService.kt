package com.tcltv.sahne.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.media.tv.TvContract
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.tcltv.sahne.MainActivity
import com.tcltv.sahne.data.KeyAction
import com.tcltv.sahne.data.KeyBinding
import com.tcltv.sahne.data.KeyBindingStore
import com.tcltv.sahne.data.PressType
import java.util.concurrent.Executors

/**
 * Keeps Matinee as the home screen on TVs whose stock launcher holds on to Home: it takes the Home key, and when the
 * TV shows the stock home screen anyway (waking up, restarting, backing out of an app) it opens Matinee over it.
 * It also runs the actions the user gave remote keys in Settings → Remote; keys without one pass through untouched.
 * Only key events and the stock launcher's own window changes are received; window content is never requested.
 */
class HomeKeyService : AccessibilityService() {

    private var stockHomes: Set<ComponentName> = emptySet()
    private var lastOpenedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val bindings by lazy { KeyBindingStore(this) }
    private val adb by lazy { LocalAdb(this) }
    // adb calls block on a socket; they must never hold up the key events behind them.
    private val adbExecutor = Executors.newSingleThreadExecutor()
    private val classifier by lazy {
        PressClassifier(
            scheduler = { delayMs, block ->
                val task = Runnable(block)
                handler.postDelayed(task, delayMs)
                PressClassifier.Cancellable { handler.removeCallbacks(task) }
            },
            longPressMs = ViewConfiguration.getLongPressTimeout().toLong(),
            doublePressMs = DOUBLE_PRESS_MS,
            onPress = ::onPress,
        )
    }

    private var learnUntil = 0L
    private var learnedKey = NO_KEY

    private val commands = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_LEARN_KEY -> learnUntil = SystemClock.uptimeMillis() + LEARN_WINDOW_MS
                ACTION_BINDINGS_CHANGED -> bindings.invalidate()
            }
        }
    }

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
        val filter = IntentFilter().apply {
            addAction(ACTION_LEARN_KEY)
            addAction(ACTION_BINDINGS_CHANGED)
        }
        ContextCompat.registerReceiver(this, commands, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // After a restart the stock home screen is often up before this service is bound, so its window event is
        // never seen; take over once. Later reconnects (an app update, say) leave the screen alone.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openMatinee()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(commands) }
        handler.removeCallbacksAndMessages(null)
        adbExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (learn(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openMatinee()
            return true
        }
        // Keys typed back in through adb ("normal" short presses) come from the virtual keyboard; let them through.
        if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false
        val binding = bindings.current()[event.keyCode] ?: return false
        val presses = binding.actions.keys + PressType.SHORT
        when {
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> classifier.down(event.keyCode, presses)
            event.action == KeyEvent.ACTION_UP && event.isCanceled -> classifier.cancel(event.keyCode)
            event.action == KeyEvent.ACTION_UP -> classifier.up(event.keyCode, presses)
        }
        return true
    }

    /** While the settings screen waits for a key to assign, the next key pressed is reported to it and goes no further. */
    private fun learn(event: KeyEvent): Boolean {
        if (learnedKey == event.keyCode && event.action == KeyEvent.ACTION_UP) {
            learnedKey = NO_KEY
            return true
        }
        if (SystemClock.uptimeMillis() > learnUntil || event.action != KeyEvent.ACTION_DOWN) return false
        learnUntil = 0L
        learnedKey = event.keyCode
        sendBroadcast(Intent(ACTION_KEY_LEARNED).setPackage(packageName).putExtra(EXTRA_KEY_CODE, event.keyCode))
        return true
    }

    private fun onPress(keyCode: Int, press: PressType) {
        val binding: KeyBinding = bindings.current()[keyCode] ?: return
        when (val action = binding.actions[press]) {
            null -> passThrough(keyCode)
            is KeyAction.OpenApp -> launch(appIntent(action.packageName))
            is KeyAction.OpenInput -> launch(
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(action.inputId))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            KeyAction.MatineeHome -> launch(matineeIntent())
            KeyAction.MatineeSettings -> launch(matineeIntent().putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true))
            KeyAction.ScreenOff -> screenOff()
            KeyAction.DoNothing -> Unit
        }
    }

    /** Gives a key whose short press has no action of its own its usual effect back, typed again through adb. */
    private fun passThrough(keyCode: Int) {
        // Without adb there is no way to hand the key back; the settings screen tells the user so.
        if (adb.isSetUp()) adbExecutor.execute { adb.runQuietly("input keyevent $keyCode") }
    }

    private fun screenOff() {
        if (adb.isSetUp()) {
            adbExecutor.execute {
                if (!adb.runQuietly("input keyevent ${KeyEvent.KEYCODE_SLEEP}")) handler.post { lockScreen() }
            }
        } else {
            lockScreen()
        }
    }

    private fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    private fun appIntent(packageName: String): Intent? =
        (packageManager.getLeanbackLaunchIntentForPackage(packageName) ?: packageManager.getLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun matineeIntent() = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun launch(intent: Intent?) {
        if (intent == null) return
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            // An uninstalled app or a removed input: the key simply does nothing.
            Log.w(TAG, "key action could not start ${intent.component ?: intent.data}: ${e.message}")
        }
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
        startActivity(matineeIntent())
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

    companion object {
        /** Sent by the settings screen: report the next key pressed instead of acting on it. */
        const val ACTION_LEARN_KEY = "com.tcltv.sahne.action.LEARN_KEY"
        const val ACTION_KEY_LEARNED = "com.tcltv.sahne.action.KEY_LEARNED"
        const val ACTION_BINDINGS_CHANGED = "com.tcltv.sahne.action.KEY_BINDINGS_CHANGED"
        const val EXTRA_KEY_CODE = "keyCode"

        private const val TAG = "HomeKeyService"
        private const val NO_KEY = -1
        private const val REOPEN_GUARD_MS = 1_000L
        private const val BOOT_WINDOW_MS = 2 * 60_000L
        private const val DOUBLE_PRESS_MS = 300L
        private const val LEARN_WINDOW_MS = 15_000L
    }
}
