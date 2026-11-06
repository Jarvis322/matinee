package com.tcltv.sahne.system

import com.tcltv.sahne.data.MemoryStatus

/** One app task the TV keeps in its recent list, newest first. */
data class RecentTask(val taskId: Int, val packageName: String)

/**
 * The TV's own list of recently used apps, for the app switcher. Android gives that list to the shell only, so it goes
 * through Automatic ADB; without it there is no list.
 */
class RecentApps(private val adb: LocalAdb, private val ownPackage: String) {

    /** Recent app tasks, most recent first, one per app; null when adb can't be reached. */
    fun recent(): List<RecentTask>? {
        val output = adb.output("dumpsys activity recents") ?: return null
        return parse(output)
            .filterNot { it.packageName == ownPackage }
            .distinctBy { it.packageName }
    }

    /**
     * Takes the app out of the recent list and quits it, as swiping it away does on Apple TV. Apps the TV relies on
     * keep running and only leave the list.
     */
    fun close(task: RecentTask): Boolean {
        val remove = "am stack remove ${task.taskId}"
        val command = if (MemoryStatus.isProtected(task.packageName)) remove else "$remove; am force-stop ${task.packageName}"
        return adb.runQuietly(command)
    }

    companion object {
        private val HEADER = Regex("""^\* Recent #\d+: Task\{\S+ #(\d+) type=(\w+)""")
        private val COMPONENT_PREFIXES = listOf("mActivityComponent=", "realActivity=")

        /** Reads `dumpsys activity recents`: ordinary app tasks only, leaving out home screens and other system types. */
        fun parse(dumpsys: String): List<RecentTask> {
            val tasks = mutableListOf<RecentTask>()
            var taskId: Int? = null
            var isApp = false
            for (raw in dumpsys.lineSequence()) {
                val line = raw.trim()
                val header = HEADER.find(line)
                if (header != null) {
                    taskId = header.groupValues[1].toInt()
                    isApp = header.groupValues[2] == "standard"
                    continue
                }
                val id = taskId ?: continue
                val prefix = COMPONENT_PREFIXES.firstOrNull { line.startsWith(it) } ?: continue
                val packageName = line.removePrefix(prefix).substringBefore('/')
                if (isApp && packageName.isNotEmpty()) tasks += RecentTask(id, packageName)
                taskId = null
            }
            return tasks
        }
    }
}
