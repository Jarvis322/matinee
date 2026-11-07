package com.tcltv.sahne.system

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {

    @Test
    fun appTasksAreListedNewestFirstWithoutHomeScreens() {
        val dumpsys = """
            ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)
              * Recent #0: Task{49df638 #97 type=standard A=10104:com.yigitcan.matinee U=0 visible=true sz=1}
                affinity=10104:com.yigitcan.matinee
                mActivityComponent=com.yigitcan.matinee/com.tcltv.sahne.MainActivity
              * Recent #1: Task{d8749e0 #96 type=standard A=10070:com.google.android.youtube.tv.main U=0 sz=1}
                intent={act=android.intent.action.MAIN cmp=com.google.android.youtube.tv/.MainActivity}
                mActivityComponent=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.MainActivity
              * Recent #2: Task{9f8d97c #94 type=home A=10055:.TvLauncher U=0 rootTaskId=1 sz=1}
                mActivityComponent=com.google.android.tvlauncher/.MainActivity
              * Recent #3: Task{87b1010 #99 type=standard A=10059:com.android.vending U=0 sz=1}
                realActivity=com.android.vending/com.google.android.finsky.MainActivity
        """.trimIndent()

        assertEquals(
            listOf(
                RecentTask(97, "com.yigitcan.matinee"),
                RecentTask(96, "com.google.android.youtube.tv"),
                RecentTask(99, "com.android.vending"),
            ),
            RecentApps.parse(dumpsys),
        )
    }

    @Test
    fun tasksRestoredAfterARestartCountAsApps() {
        val dumpsys = """
              * Recent #0: Task{c265bab #3195 type=standard A=1000:com.tcl.tv}
                mActivityComponent=com.tcl.tv/com.tcl.player.TVActivity
              * Recent #1: Task{c155788 #3193 type=home A=10052:com.google.android.apps.tv.launcherx}
                mActivityComponent=com.google.android.apps.tv.launcherx/.home.HomeActivity
              * Recent #2: Task{302a383 #3190 type=undefined A=10077:com.netflix.ninja}
                intent={act=com.netflix.action.NETFLIX_KEY_START flg=0x10020000 cmp=com.netflix.ninja/.MainActivity}
                mActivityComponent=com.netflix.ninja/.MainActivity
                Activities=[]
        """.trimIndent()

        assertEquals(
            listOf(RecentTask(3195, "com.tcl.tv"), RecentTask(3190, "com.netflix.ninja")),
            RecentApps.parse(dumpsys),
        )
    }

    @Test
    fun aTaskWithoutAnActivityIsSkipped() {
        val dumpsys = """
              * Recent #0: Task{40a92d2 #71 type=standard I=com.example/.Main U=0 sz=0}
                askedCompatMode=false
              * Recent #1: Task{1a8afa3 #38 type=standard A=10097:com.tcltv.hiz U=0 sz=1}
                mActivityComponent=com.tcltv.hiz/.MainActivity
        """.trimIndent()

        assertEquals(listOf(RecentTask(38, "com.tcltv.hiz")), RecentApps.parse(dumpsys))
    }
}
