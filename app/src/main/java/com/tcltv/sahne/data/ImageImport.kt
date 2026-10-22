package com.tcltv.sahne.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** A picture the user chose, as a content or file URI. Reading it needs no storage permission. */
data class PickedImage(val uri: Uri)

/** Copies pictures the user chose through a file picker into the app's own storage. */
class ImageImport(private val context: Context) {

    /** Decodes [uri] scaled so its longest side is about [maxSide]; null when it is missing or not a picture. */
    private fun decode(uri: Uri, maxSide: Int): Bitmap? = try {
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
        // A file that vanished (a USB drive pulled out) or isn't a picture simply fails to import.
        null
    }

    /** Copies a chosen picture into [target] scaled to at most [maxSide], as JPEG, or as PNG when [keepAlpha]. */
    suspend fun importTo(image: PickedImage, target: File, maxSide: Int, keepAlpha: Boolean): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decode(image.uri, maxSide) ?: return@withContext false
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
}
