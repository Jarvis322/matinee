package com.tcltv.sahne.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class LastPlayed(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val artworkPath: String?,
    val artworkIsSquare: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float?
        get() = if (durationMs > 0 && positionMs >= 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
}

/**
 * The last item each app reported through its media session, kept only on this TV (app-private storage).
 * Shared by the notification listener (which records) and the launcher UI (which reads) in the same process.
 */
class MediaHistory private constructor(context: Context) {

    private val dir = File(context.filesDir, "media_history")
    private val indexFile = File(dir, "index.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _entries = MutableStateFlow<Map<String, LastPlayed>>(emptyMap())
    val entries: StateFlow<Map<String, LastPlayed>> = _entries.asStateFlow()

    init {
        scope.launch { lock.withLock { _entries.value = readIndex() } }
    }

    fun record(packageName: String, title: String, subtitle: String?, artwork: Bitmap?, positionMs: Long, durationMs: Long) {
        scope.launch {
            lock.withLock {
                val previous = _entries.value[packageName]
                val sameItem = previous?.title == title && previous.subtitle == subtitle
                // Players report position often; only a real change is worth a disk write and an artwork reload.
                if (sameItem && kotlin.math.abs(positionMs - previous.positionMs) < MIN_POSITION_CHANGE_MS &&
                    (artwork == null || previous.artworkPath != null)
                ) {
                    return@withLock
                }
                val artworkFile = when {
                    artwork != null && (!sameItem || previous?.artworkPath == null) -> saveArtwork(packageName, artwork)
                    sameItem -> previous?.artworkPath?.let(::File)
                    else -> {
                        // A new item without artwork must not keep showing the previous item's cover.
                        File(dir, "$packageName.jpg").delete()
                        null
                    }
                }
                val square = when {
                    artwork != null -> artwork.width > 0 && kotlin.math.abs(artwork.width - artwork.height) <= artwork.width / 10
                    sameItem -> previous?.artworkIsSquare ?: false
                    else -> false
                }
                val entry = LastPlayed(
                    packageName = packageName,
                    title = title,
                    subtitle = subtitle,
                    artworkPath = artworkFile?.path,
                    artworkIsSquare = square,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                )
                _entries.value = _entries.value + (packageName to entry)
                writeIndex(_entries.value)
            }
        }
    }

    fun clear() {
        scope.launch {
            lock.withLock {
                dir.listFiles()?.forEach { it.delete() }
                _entries.value = emptyMap()
            }
        }
    }

    private fun saveArtwork(packageName: String, source: Bitmap): File? = try {
        dir.mkdirs()
        val scale = minOf(1f, MAX_ARTWORK_WIDTH.toFloat() / source.width)
        val scaled = if (scale < 1f) {
            // coerceAtLeast: a very wide strip would scale to 0 px high, and createScaledBitmap throws on that.
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            source
        }
        val file = File(dir, "$packageName.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't save artwork", e)
        null
    } catch (e: IllegalArgumentException) {
        // An app-provided bitmap that can't be scaled or compressed must not take the launcher process down.
        Log.w(TAG, "Couldn't save artwork", e)
        null
    }

    private fun readIndex(): Map<String, LastPlayed> {
        if (!indexFile.isFile) return emptyMap()
        val array = runCatching { JSONArray(indexFile.readText()) }.getOrNull() ?: return emptyMap()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val pkg = o.optString("package").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = o.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            pkg to LastPlayed(
                packageName = pkg,
                title = title,
                subtitle = o.optString("subtitle").takeIf { it.isNotBlank() },
                artworkPath = o.optString("artwork").takeIf { it.isNotBlank() && File(it).isFile },
                artworkIsSquare = o.optBoolean("square"),
                positionMs = o.optLong("position", -1),
                durationMs = o.optLong("duration", -1),
                updatedAt = o.optLong("updatedAt"),
            )
        }.toMap()
    }

    private fun writeIndex(entries: Map<String, LastPlayed>) {
        val array = JSONArray()
        entries.values.forEach { e ->
            array.put(
                JSONObject()
                    .put("package", e.packageName)
                    .put("title", e.title)
                    .put("subtitle", e.subtitle ?: "")
                    .put("artwork", e.artworkPath ?: "")
                    .put("square", e.artworkIsSquare)
                    .put("position", e.positionMs)
                    .put("duration", e.durationMs)
                    .put("updatedAt", e.updatedAt),
            )
        }
        try {
            dir.mkdirs()
            val tmp = File(dir, "index.json.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(indexFile)) Log.w(TAG, "Couldn't replace media history index")
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't write media history index", e)
        }
    }

    companion object {
        private const val MAX_ARTWORK_WIDTH = 640
        private const val MIN_POSITION_CHANGE_MS = 30_000L
        private const val TAG = "MediaHistory"

        @Volatile
        private var instance: MediaHistory? = null

        fun get(context: Context): MediaHistory =
            instance ?: synchronized(this) {
                instance ?: MediaHistory(context.applicationContext).also { instance = it }
            }
    }
}
