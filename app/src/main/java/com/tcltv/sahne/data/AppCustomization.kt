package com.tcltv.sahne.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tcltv.sahne.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** What the user changed about one app: its name, its picture, and whether it needs the parental PIN to open. */
data class AppOverride(val label: String? = null, val icon: String? = null, val locked: Boolean = false) {
    val isDefault: Boolean get() = label == null && icon == null && !locked
}

/** A group of apps shown as one card; [apps] are package names in the order they appear inside. */
data class Folder(val id: String, val name: String, val apps: List<String>) {
    val key: String get() = FOLDER_PREFIX + id

    companion object {
        const val FOLDER_PREFIX = "folder:"

        fun newId(): String = UUID.randomUUID().toString().take(8)

        fun isFolderKey(packageName: String) = packageName.startsWith(FOLDER_PREFIX)
    }
}

internal object CustomizationJson {
    fun encodeOverrides(map: Map<String, AppOverride>): String {
        val root = JSONObject()
        map.forEach { (pkg, o) ->
            if (o.isDefault) return@forEach
            root.put(
                pkg,
                JSONObject().apply {
                    o.label?.let { put("label", it) }
                    o.icon?.let { put("icon", it) }
                    if (o.locked) put("locked", true)
                },
            )
        }
        return root.toString()
    }

    fun decodeOverrides(text: String?): Map<String, AppOverride> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(text)
            buildMap {
                root.keys().forEach { pkg ->
                    val o = root.optJSONObject(pkg) ?: return@forEach
                    put(pkg, AppOverride(o.optString("label").takeIf { it.isNotBlank() }, o.optString("icon").takeIf { it.isNotBlank() }, o.optBoolean("locked")))
                }
            }
        } catch (e: JSONException) {
            emptyMap()
        }
    }

    fun encodeFolders(folders: List<Folder>): String = JSONArray().apply {
        folders.forEach { f ->
            put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
        }
    }.toString()

    fun decodeFolders(text: String?): List<Folder> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val apps = o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                Folder(id, o.optString("name"), apps)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

/**
 * Pictures the user chose for apps and wallpapers, kept in the app's private storage. Bitmaps are decoded no larger
 * than a card needs, so a 20-megapixel photo costs no more memory than a banner.
 */
class UserImages(context: Context) {

    private val dir = File(context.filesDir, "user_images").apply { mkdirs() }

    fun file(name: String) = File(dir, name)

    fun delete(name: String) {
        file(name).delete()
    }

    /** Decodes [name] scaled so its width is about [maxWidth], or null when it is missing or not an image. */
    fun load(name: String, maxWidth: Int, mutable: Boolean = false): Bitmap? {
        val file = file(name)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = mutable })
    }
}

/** Draws the card for a folder: its first apps' icons in a grid, and its name, on a dark glassy tile. */
internal object FolderArt {

    fun banner(icons: List<ImageBitmap?>): ImageBitmap {
        val w = 320
        val h = 180
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF39406A.toInt(), 0xFF1E2238.toInt(), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        drawIcons(canvas, paint, icons, RectF(70f, 8f, 250f, h - 8f))
        return bitmap.asImageBitmap()
    }

    fun glyph(icons: List<ImageBitmap?>): ImageBitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 40f, 40f, paint)
        drawIcons(canvas, paint, icons, RectF(14f, 14f, size - 14f, size - 14f))
        return bitmap.asImageBitmap()
    }

    /** Always a 2x2 grid of small tiles, so even a folder with one app reads as a folder; up to four icons fill them. */
    private fun drawIcons(canvas: Canvas, paint: Paint, icons: List<ImageBitmap?>, area: RectF) {
        val cell = minOf(area.width(), area.height()) / 2f
        val gap = cell * 0.07f
        val left = area.centerX() - cell
        val top = area.centerY() - cell
        for (i in 0 until 4) {
            val cx = left + (i % 2) * cell
            val cy = top + (i / 2) * cell
            val tile = RectF(cx + gap, cy + gap, cx + cell - gap, cy + cell - gap)
            paint.color = 0x40FFFFFF
            canvas.drawRoundRect(tile, cell * 0.2f, cell * 0.2f, paint)
            icons.getOrNull(i)?.asAndroidBitmap()?.let { icon ->
                val inset = cell * 0.08f
                canvas.drawBitmap(icon, null, RectF(tile.left + inset, tile.top + inset, tile.right - inset, tile.bottom - inset), paint)
            }
        }
    }
}

/** Builds the stand-in [LaunchableApp] that represents a folder, so the home screen can treat it like any other card. */
internal fun folderApp(context: Context, folder: Folder, members: List<LaunchableApp>): LaunchableApp {
    val icons = members.map { it.icon }
    val glyph = FolderArt.glyph(icons)
    return LaunchableApp(
        packageName = folder.key,
        component = ComponentName(context.packageName, folder.key),
        label = folder.name.ifBlank { context.getString(R.string.folder_default_name) },
        accent = 0xFF5B6C9E.toInt(),
        icon = glyph,
        glyph = glyph,
        glyphLightness = 1f,
        banner = FolderArt.banner(icons),
        tagline = R.string.tagline_folder,
        isSystemComponent = false,
        isTvApp = true,
        folderId = folder.id,
    )
}
