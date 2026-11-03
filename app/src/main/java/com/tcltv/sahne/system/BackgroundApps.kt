package com.tcltv.sahne.system

import com.tcltv.sahne.data.MemoryStatus

/**
 * Finds which apps still have a process running. Android shows other apps' processes only to the shell, so this goes
 * through Automatic ADB; without it there is no list.
 */
class BackgroundApps(private val adb: LocalAdb, private val ownPackage: String) {

    /** Packages from [candidates] that have a process, or null when adb can't be reached. */
    fun running(candidates: Set<String>): Set<String>? {
        val output = adb.output("ps -A -o NAME") ?: return null
        return parse(output, candidates)
            .filterNot { it == ownPackage || MemoryStatus.isProtected(it) }
            .toSet()
    }

    /** Ends the app's processes for good, the same as "Force stop" in the TV's app settings. */
    fun close(packageName: String): Boolean = adb.runQuietly("am force-stop $packageName")

    companion object {
        /** Process names look like "com.example.app" or "com.example.app:service"; only the package part counts. */
        fun parse(psOutput: String, candidates: Set<String>): Set<String> =
            psOutput.lineSequence()
                .map { it.trim().substringBefore(':') }
                .filter { it in candidates }
                .toSet()
    }
}
