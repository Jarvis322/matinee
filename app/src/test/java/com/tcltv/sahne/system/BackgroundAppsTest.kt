package com.tcltv.sahne.system

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundAppsTest {

    private val candidates = setOf("com.google.android.youtube.tv", "com.spotify.tv.android", "com.example.notrunning")

    @Test
    fun processNamesCountForTheirPackage() {
        val output = """
            NAME
            system_server
            com.google.android.youtube.tv
            com.google.android.youtube.tv:player
            com.spotify.tv.android:media
            com.other.app
        """.trimIndent()

        assertEquals(setOf("com.google.android.youtube.tv", "com.spotify.tv.android"), BackgroundApps.parse(output, candidates))
    }

    @Test
    fun anAppWithNoProcessIsNotListed() {
        assertEquals(emptySet<String>(), BackgroundApps.parse("NAME\nsystem_server\n", candidates))
    }
}
