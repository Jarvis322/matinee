package com.tcltv.sahne.data

import android.content.Context
import android.util.Log
import com.tcltv.sahne.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Wide backdrops from TMDB (themoviedb.org) for Continue watching, looked up by the title an app reported. Only the
 * title is sent. A backdrop is used only when TMDB has a show or film of exactly that name, so a YouTube video or a song
 * never gets some unrelated film's picture. Results, found or not, are kept on the TV so a title is asked about once.
 */
class TmdbArtwork(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "tmdb")
    private val lock = Mutex()

    /** Turned off for the rest of the session when TMDB refuses the token, so a bad key isn't retried on every focus. */
    @Volatile
    private var rejected = false

    val isAvailable: Boolean get() = BuildConfig.TMDB_TOKEN.isNotEmpty() && !rejected

    /** The cached backdrop file for [entry], fetching it the first time; null when there is none. */
    suspend fun backdrop(entry: LastPlayed): File? = withContext(Dispatchers.IO) {
        if (!isAvailable || entry.artworkIsSquare) return@withContext null
        val key = cacheKey(entry)
        val image = File(dir, "$key.jpg")
        val miss = File(dir, "$key.miss")
        // One lookup per title at a time: several cards ask for the same entry as focus moves along the row.
        lock.withLock {
            if (image.isFile) return@withContext image
            if (miss.isFile && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS) return@withContext null
            try {
                val path = findBackdropPath(entry)
                dir.mkdirs()
                if (path == null) {
                    miss.writeText("")
                    null
                } else {
                    download("$IMAGE_BASE$path", image)
                    image
                }
            } catch (e: IOException) {
                // Offline or TMDB unreachable: no picture this time, asked again on a later look.
                Log.w(TAG, "backdrop lookup failed: ${e.message}")
                null
            }
        }
    }

    /** Forgets every looked-up picture, with the history they belong to. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun findBackdropPath(entry: LastPlayed): String? {
        // Episodes come either as "show / episode" or "episode / show"; the show's own name is the one TMDB knows.
        val names = listOfNotNull(entry.subtitle, entry.title).map(String::trim).filter { it.length >= MIN_TITLE_LENGTH }.distinct()
        for (name in names) {
            searchExact(name)?.let { return it }
        }
        return null
    }

    private fun searchExact(name: String): String? {
        val query = URLEncoder.encode(name, "UTF-8")
        val json = getJson("$API_BASE/search/multi?query=$query&include_adult=false") ?: return null
        val results = json.optJSONArray("results") ?: return null
        val wanted = normalize(name)
        return (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .filter { it.optString("media_type") == "tv" || it.optString("media_type") == "movie" }
            .firstOrNull { result ->
                val title = result.optString("name").ifEmpty { result.optString("title") }
                val original = result.optString("original_name").ifEmpty { result.optString("original_title") }
                (normalize(title) == wanted || normalize(original) == wanted) && result.optString("backdrop_path").startsWith("/")
            }
            ?.optString("backdrop_path")
    }

    private fun getJson(url: String): JSONObject? {
        val connection = open(url)
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return try {
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } catch (e: JSONException) {
                    throw IOException("malformed TMDB response", e)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> {
                    rejected = true
                    throw IOException("TMDB refused the token ($code)")
                }
                // Rate limited or a server fault: try again on a later look rather than now.
                else -> throw IOException("TMDB answered $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("image answered ${connection.responseCode}")
            val partial = File(target.path + ".part")
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(target)) throw IOException("couldn't keep the image")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            if (url.startsWith(API_BASE)) setRequestProperty("Authorization", "Bearer ${BuildConfig.TMDB_TOKEN}")
        }

    companion object {
        private const val TAG = "TmdbArtwork"
        private const val API_BASE = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w780"
        private const val TIMEOUT_MS = 8_000
        private const val MIN_TITLE_LENGTH = 2
        private const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Case, accents' spacing and punctuation differ between apps and TMDB ("Dexter - Resurrection"). */
        fun normalize(name: String): String =
            name.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        private fun cacheKey(entry: LastPlayed): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("${entry.title}\u0000${entry.subtitle.orEmpty()}".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
