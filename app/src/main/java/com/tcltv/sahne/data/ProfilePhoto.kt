package com.tcltv.sahne.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Optional profile photo, placed by the user (e.g. via `adb push`) in the app's own external files folder.
 * Google does not expose the signed-in account's photo to third-party apps.
 */
class ProfilePhoto(context: Context) {

    private val folder: File? = context.applicationContext.getExternalFilesDir(null)

    val expectedPath: String = File(folder, "avatar.jpg").path

    suspend fun load(): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = CANDIDATES.map { File(folder, it) }.firstOrNull { it.isFile } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_SIZE_PX) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }

    /** One-shot import of a name pushed from a computer, for names the TV keyboard can't type easily. */
    suspend fun consumePendingName(): String? = withContext(Dispatchers.IO) {
        val file = File(folder, PENDING_NAME_FILE)
        if (!file.isFile) return@withContext null
        val name = runCatching { file.readText(Charsets.UTF_8).trim() }.getOrNull()
        file.delete()
        name?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
    }

    private companion object {
        const val PENDING_NAME_FILE = "profile_name.txt"
        const val MAX_NAME_LENGTH = 40
        val CANDIDATES = listOf("avatar.jpg", "avatar.jpeg", "avatar.png", "avatar.webp")
        const val TARGET_SIZE_PX = 192
    }
}
