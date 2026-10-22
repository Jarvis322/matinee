package com.tcltv.sahne

import android.os.SystemClock
import androidx.annotation.StringRes
import com.tcltv.sahne.data.LauncherSettings
import com.tcltv.sahne.data.PinHasher
import com.tcltv.sahne.data.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A question the PIN screen is asking: why, and what to do once the right PIN is entered. */
class PinRequest(@StringRes val reason: Int, val action: () -> Unit)

sealed interface PinResult {
    data object Accepted : PinResult
    data class Wrong(val failures: Int) : PinResult
    data class Locked(val remainingMillis: Long) : PinResult
}

/**
 * Decides when the parental PIN is needed and checks it. Locked apps ask every time. Opening settings and editing the
 * home screen ask once, then stay open for a few minutes, so a parent isn't asked again for every change.
 */
class ParentalGate(
    private val settings: StateFlow<LauncherSettings>,
    private val store: SettingsStore,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _request = MutableStateFlow<PinRequest?>(null)
    val request: StateFlow<PinRequest?> = _request.asStateFlow()

    private var adminUntil = 0L

    val hasPin: Boolean get() = settings.value.parental.hasPin

    /** Runs [action] now, or after the PIN when the app is locked. */
    fun requireForApp(@StringRes reason: Int, locked: Boolean, action: () -> Unit) {
        if (locked && hasPin) _request.value = PinRequest(reason, action) else action()
    }

    /** Runs [action] now, or after the PIN when settings are protected and no recent PIN covers it. */
    fun requireForAdmin(@StringRes reason: Int, action: () -> Unit) {
        val parental = settings.value.parental
        if (!parental.hasPin || !parental.protectSettings || SystemClock.elapsedRealtime() < adminUntil) {
            action()
        } else {
            _request.value = PinRequest(reason) {
                adminUntil = SystemClock.elapsedRealtime() + ADMIN_WINDOW_MILLIS
                action()
            }
        }
    }

    fun endAdminSession() {
        adminUntil = 0L
    }

    fun cancel() {
        _request.value = null
    }

    suspend fun submit(pin: String): PinResult {
        val request = _request.value ?: return PinResult.Wrong(0)
        val parental = settings.value.parental
        val remaining = parental.lockoutRemainingMillis(System.currentTimeMillis())
        if (remaining > 0) return PinResult.Locked(remaining)
        val ok = withContext(worker) { PinHasher.matches(pin, parental) }
        if (!ok) {
            store.recordPinFailure(System.currentTimeMillis())
            val failures = parental.failures + 1
            val lock = PinHasher.lockoutMillisAfter(failures)
            return if (lock > 0) PinResult.Locked(lock) else PinResult.Wrong(failures)
        }
        store.clearPinFailures()
        _request.value = null
        request.action()
        return PinResult.Accepted
    }

    /** Sets or changes the PIN. Changing it needs the old one first, which the caller asks for with [requireForAdmin]. */
    suspend fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = withContext(worker) { PinHasher.hash(pin, salt) }
        store.setPin(hash, salt)
        adminUntil = SystemClock.elapsedRealtime() + ADMIN_WINDOW_MILLIS
    }

    suspend fun removePin() {
        store.clearPin()
    }

    private companion object {
        const val ADMIN_WINDOW_MILLIS = 5 * 60_000L
    }
}
