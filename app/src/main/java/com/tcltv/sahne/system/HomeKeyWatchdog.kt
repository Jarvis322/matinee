package com.tcltv.sahne.system

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Checks the Home button service every so often and turns it back on when it stopped, so the remote's Home button
 * keeps working while Matinee itself isn't opened. Only scheduled once Automatic ADB is set up; without it Matinee
 * can do nothing about a stopped service but tell the user next time it is opened.
 */
class HomeKeyWatchdog : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            HomeKeyGuard(this@HomeKeyWatchdog).repairIfNeeded()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4_101
        // The shortest period Android allows for periodic jobs.
        private const val PERIOD_MS = 15 * 60_000L

        /** Idempotent; the job is persisted, so it survives restarts until Matinee is uninstalled. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, HomeKeyWatchdog::class.java))
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
