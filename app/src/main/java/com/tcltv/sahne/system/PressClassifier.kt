package com.tcltv.sahne.system

import com.tcltv.sahne.data.PressType

/**
 * Turns a key's downs and ups into one short, long or double press. It waits only for what the key has an action for:
 * a key without a double-press action reports its short press as soon as it is released.
 */
class PressClassifier(
    private val scheduler: Scheduler,
    private val longPressMs: Long,
    private val doublePressMs: Long,
    private val onPress: (keyCode: Int, press: PressType) -> Unit,
) {
    fun interface Cancellable {
        fun cancel()
    }

    fun interface Scheduler {
        fun after(delayMs: Long, block: () -> Unit): Cancellable
    }

    private var heldKey = NONE
    private var longFired = false
    private var longTimer: Cancellable? = null
    private var tappedKey = NONE
    private var tapTimer: Cancellable? = null

    /** The first down of a press; the key's auto-repeats must not be passed in. */
    fun down(keyCode: Int, presses: Set<PressType>) {
        // Another key ends the wait for a second tap of the previous one.
        if (tappedKey != NONE && tappedKey != keyCode) flushTap()
        longTimer?.cancel()
        heldKey = keyCode
        longFired = false
        longTimer = if (PressType.LONG in presses) {
            scheduler.after(longPressMs) {
                longTimer = null
                longFired = true
                // Holding the second tap down makes it a long press, not a double one.
                cancelTap()
                onPress(keyCode, PressType.LONG)
            }
        } else {
            null
        }
    }

    fun up(keyCode: Int, presses: Set<PressType>) {
        if (keyCode != heldKey) return
        heldKey = NONE
        longTimer?.cancel()
        longTimer = null
        if (longFired) return
        when {
            PressType.DOUBLE !in presses -> onPress(keyCode, PressType.SHORT)
            tappedKey == keyCode -> {
                cancelTap()
                onPress(keyCode, PressType.DOUBLE)
            }
            else -> {
                tappedKey = keyCode
                tapTimer = scheduler.after(doublePressMs) {
                    tapTimer = null
                    tappedKey = NONE
                    onPress(keyCode, PressType.SHORT)
                }
            }
        }
    }

    /** The system cancelled the press (the key was taken over mid-way); nothing runs for it. */
    fun cancel(keyCode: Int) {
        if (keyCode != heldKey) return
        heldKey = NONE
        longTimer?.cancel()
        longTimer = null
    }

    private fun flushTap() {
        val key = tappedKey
        cancelTap()
        onPress(key, PressType.SHORT)
    }

    private fun cancelTap() {
        tapTimer?.cancel()
        tapTimer = null
        tappedKey = NONE
    }

    private companion object {
        const val NONE = -1
    }
}
