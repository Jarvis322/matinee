package com.tcltv.sahne.system

import com.tcltv.sahne.data.PressType
import org.junit.Assert.assertEquals
import org.junit.Test

class PressClassifierTest {

    private class FakeScheduler : PressClassifier.Scheduler {
        private class Task(val at: Long, val block: () -> Unit, var cancelled: Boolean = false)

        private val tasks = mutableListOf<Task>()
        var now = 0L

        override fun after(delayMs: Long, block: () -> Unit): PressClassifier.Cancellable {
            val task = Task(now + delayMs, block)
            tasks += task
            return PressClassifier.Cancellable { task.cancelled = true }
        }

        fun advance(ms: Long) {
            val until = now + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= until }.minByOrNull { it.at } ?: break
                tasks -= next
                now = next.at
                next.block()
            }
            now = until
        }
    }

    private val scheduler = FakeScheduler()
    private val presses = mutableListOf<Pair<Int, PressType>>()
    private val classifier = PressClassifier(scheduler, longPressMs = 500, doublePressMs = 300) { key, press -> presses += key to press }

    private val all = setOf(PressType.SHORT, PressType.LONG, PressType.DOUBLE)

    @Test
    fun shortPressWithoutDoubleActionIsReportedOnRelease() {
        classifier.down(KEY, setOf(PressType.SHORT, PressType.LONG))
        scheduler.advance(100)
        classifier.up(KEY, setOf(PressType.SHORT, PressType.LONG))

        assertEquals(listOf(KEY to PressType.SHORT), presses)
    }

    @Test
    fun shortPressWaitsForPossibleSecondTapWhenDoubleIsAssigned() {
        classifier.down(KEY, all)
        classifier.up(KEY, all)
        assertEquals(emptyList<Pair<Int, PressType>>(), presses)

        scheduler.advance(300)
        assertEquals(listOf(KEY to PressType.SHORT), presses)
    }

    @Test
    fun twoQuickTapsMakeOneDoublePress() {
        classifier.down(KEY, all)
        classifier.up(KEY, all)
        scheduler.advance(150)
        classifier.down(KEY, all)
        classifier.up(KEY, all)
        scheduler.advance(1_000)

        assertEquals(listOf(KEY to PressType.DOUBLE), presses)
    }

    @Test
    fun holdingFiresLongPressOnceAndReleaseAddsNothing() {
        classifier.down(KEY, all)
        scheduler.advance(600)
        classifier.up(KEY, all)
        scheduler.advance(1_000)

        assertEquals(listOf(KEY to PressType.LONG), presses)
    }

    @Test
    fun holdingWithoutLongActionIsStillAShortPress() {
        classifier.down(KEY, setOf(PressType.SHORT))
        scheduler.advance(2_000)
        classifier.up(KEY, setOf(PressType.SHORT))

        assertEquals(listOf(KEY to PressType.SHORT), presses)
    }

    @Test
    fun anotherKeyEndsTheWaitForASecondTap() {
        classifier.down(KEY, all)
        classifier.up(KEY, all)
        scheduler.advance(100)
        classifier.down(OTHER, setOf(PressType.SHORT))
        classifier.up(OTHER, setOf(PressType.SHORT))

        assertEquals(listOf(KEY to PressType.SHORT, OTHER to PressType.SHORT), presses)
    }

    @Test
    fun cancelledPressRunsNothing() {
        classifier.down(KEY, all)
        classifier.cancel(KEY)
        classifier.up(KEY, all)
        scheduler.advance(1_000)

        assertEquals(emptyList<Pair<Int, PressType>>(), presses)
    }

    private companion object {
        const val KEY = 183
        const val OTHER = 184
    }
}
