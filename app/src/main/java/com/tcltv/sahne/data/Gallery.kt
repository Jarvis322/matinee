package com.tcltv.sahne.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** One picture on the TV or on a USB drive, as MediaStore reports it. */
data class GalleryImage(val uri: Uri, val name: String, val folder: String, val width: Int, val height: Int)

/** A folder of pictures, shown as one entry before the pictures themselves. */
data class GalleryFolder(val name: String, val images: List<GalleryImage>)

/**
 * The pictures a user can choose from. Reads MediaStore, so it covers internal storage and USB drives alike, and
 * needs no system picker, which most TVs don't have.
 */
class Gallery(private val context: Context) {

    private val thumbs = object : LruCache<Uri, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: Uri, value: Bitmap) = value.byteCount
    }

    suspend fun folders(): List<GalleryFolder> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val images = mutableListOf<GalleryImage>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && images.size < MAX_IMAGES) {
                    val width = c.getInt(3)
                    val height = c.getInt(4)
                    // Tiny pictures (icons, thumbnails the system keeps) would only look blurry as a wallpaper.
                    if (width in 1 until MIN_SIDE && height in 1 until MIN_SIDE) continue
                    images += GalleryImage(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                        c.getString(1).orEmpty(),
                        c.getString(2).orEmpty().ifBlank { "Pictures" },
                        width,
                        height,
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext emptyList()
        }
        images.groupBy { it.folder }.map { (name, list) -> GalleryFolder(name, list) }.sortedByDescending { it.images.size }
    }

    suspend fun thumbnail(uri: Uri, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        thumbs.get(uri) ?: decode(uri, width)?.also { thumbs.put(uri, it) }
    }

    /** Decodes [uri] scaled so its longest side is about [maxSide], honouring nothing it can't open. */
    fun decode(uri: Uri, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (e: Exception) {
        // A file that vanished (a USB drive pulled out) or isn't a picture simply isn't offered.
        null
    }

    /** Copies a chosen picture into [target] scaled to at most [maxSide], as JPEG, or as PNG when [keepAlpha]. */
    suspend fun importTo(uri: Uri, target: File, maxSide: Int, keepAlpha: Boolean): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, maxSide) ?: return@withContext false
        val scaled = scaleDown(bitmap, maxSide)
        try {
            FileOutputStream(target).use { out ->
                if (keepAlpha) scaled.compress(Bitmap.CompressFormat.PNG, 100, out) else scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            true
        } catch (e: java.io.IOException) {
            target.delete()
            false
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private companion object {
        const val MAX_IMAGES = 1500
        const val MIN_SIDE = 256
        const val THUMB_CACHE_BYTES = 6 * 1024 * 1024
    }
}
