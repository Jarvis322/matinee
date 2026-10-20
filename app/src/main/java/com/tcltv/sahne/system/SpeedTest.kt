package com.tcltv.sahne.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Measures download throughput by reading from Cloudflare's public speed-test endpoint for a few seconds.
 * Nothing is uploaded; the request carries no identifiers beyond what any HTTPS request does.
 */
class SpeedTest {

    /** Megabits per second, or throws [IOException] when the server can't be reached. */
    suspend fun measureDownloadMbps(): Double = withContext(Dispatchers.IO) {
        val connection = URL(URL_DOWNLOAD).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            val start = System.nanoTime()
            connection.inputStream.use { input ->
                while (System.nanoTime() - start < MEASURE_NANOS) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
            }
            val seconds = (System.nanoTime() - start) / 1e9
            if (seconds <= 0.0 || total == 0L) throw IOException("No data received")
            total * 8 / seconds / 1_000_000
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        // The endpoint refuses 100 MB or more; on lines fast enough to finish early, elapsed time is used instead.
        const val URL_DOWNLOAD = "https://speed.cloudflare.com/__down?bytes=90000000"
        const val MEASURE_NANOS = 3_000_000_000L
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val BUFFER_BYTES = 64 * 1024
    }
}
