package com.tcltv.sahne.data

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Turns the apps the catalog found into what the home screen shows: the user's names, pictures and locks applied,
 * and folders standing in for the apps inside them. Everything the user changed is applied here, in one place.
 */
class HomeApps(private val context: Context, private val images: UserImages) {

    private val customIcons = HashMap<String, ImageBitmap?>()
    private val customCards = HashMap<String, ImageBitmap?>()
    private val folderCards = HashMap<String, LaunchableApp>()

    fun decorate(apps: List<LaunchableApp>, overrides: Map<String, AppOverride>): List<LaunchableApp> =
        apps.map { app ->
            val override = overrides[app.packageName] ?: return@map app
            val picture = override.icon?.let(::customIcon)
            val card = override.icon?.let(::customCard)
            app.copy(
                label = override.label ?: app.label,
                icon = picture ?: app.icon,
                glyph = picture ?: app.glyph,
                glyphLightness = if (picture != null) null else app.glyphLightness,
                // A banner carries the app's own name, so a renamed app shows its icon and the new name instead.
                banner = card ?: app.banner.takeIf { override.label == null },
                accent = picture?.let { AppCatalog.dominantColor(it.asAndroidBitmap()) } ?: app.accent,
                isLocked = override.locked,
            )
        }

    /** The home list: apps that sit in a folder are replaced by one card per folder. */
    fun withFolders(decorated: List<LaunchableApp>, folders: List<Folder>): List<LaunchableApp> {
        if (folders.isEmpty()) return decorated
        val byPackage = decorated.associateBy { it.packageName }
        val inFolder = folders.flatMap { it.apps }.toSet()
        val cards = folders.mapNotNull { folder ->
            val members = folder.apps.mapNotNull(byPackage::get)
            if (members.isEmpty()) null else folderCard(folder, members)
        }
        return decorated.filter { it.packageName !in inFolder } + cards
    }

    fun membersOf(folderId: String, decorated: List<LaunchableApp>, folders: List<Folder>): List<LaunchableApp> {
        val byPackage = decorated.associateBy { it.packageName }
        return folders.firstOrNull { it.id == folderId }?.apps?.mapNotNull(byPackage::get).orEmpty()
    }

    private fun customIcon(file: String): ImageBitmap? = customIcons.getOrPut(file) { images.load(file, ICON_WIDTH)?.asImageBitmap() }

    /** The picture as a 16:9 card: used as is when it already is one, else centred whole over its own dominant colour. */
    private fun customCard(file: String): ImageBitmap? = customCards.getOrPut(file) {
        customIcon(file)?.let { CardPicture.compose(it.asAndroidBitmap()).asImageBitmap() }
    }

    private fun folderCard(folder: Folder, members: List<LaunchableApp>): LaunchableApp {
        // The card is redrawn only when the folder's name or what it shows changes.
        val key = folder.id + "|" + folder.name + "|" + members.joinToString { it.packageName + System.identityHashCode(it.icon) }
        return folderCards.getOrPut(key) { folderApp(context, folder, members) }
    }

    private companion object {
        const val ICON_WIDTH = 480
    }
}

/** Edits to folders and the order of the home list. Each returns the new folders and the new order together. */
object FolderEdits {

    data class Result(val folders: List<Folder>, val order: List<String>)

    fun create(order: List<String>, folders: List<Folder>, packageName: String, name: String): Result {
        val folder = Folder(Folder.newId(), name, listOf(packageName))
        val newOrder = if (packageName in order) order.map { if (it == packageName) folder.key else it } else order + folder.key
        return Result(folders.withoutApp(packageName) + folder, newOrder)
    }

    fun add(order: List<String>, folders: List<Folder>, folderId: String, packageName: String): Result {
        val moved = folders.withoutApp(packageName).map { if (it.id == folderId) it.copy(apps = it.apps + packageName) else it }
        return Result(moved, order.filterNot { it == packageName })
    }

    /** The app goes back to the home list, right after the folder it left; an emptied folder disappears. */
    fun remove(order: List<String>, folders: List<Folder>, packageName: String): Result {
        val folder = folders.firstOrNull { packageName in it.apps } ?: return Result(folders, order)
        val remaining = folder.apps - packageName
        if (remaining.isEmpty()) return Result(folders - folder, order.map { if (it == folder.key) packageName else it })
        val newOrder = buildList { order.forEach { add(it); if (it == folder.key) add(packageName) } }
        return Result(folders.map { if (it.id == folder.id) it.copy(apps = remaining) else it }, newOrder)
    }

    fun delete(order: List<String>, folders: List<Folder>, folderId: String): Result {
        val folder = folders.firstOrNull { it.id == folderId } ?: return Result(folders, order)
        val newOrder = buildList { order.forEach { if (it == folder.key) addAll(folder.apps) else add(it) } }
        return Result(folders - folder, newOrder)
    }

    fun rename(folders: List<Folder>, folderId: String, name: String): List<Folder> =
        folders.map { if (it.id == folderId) it.copy(name = name.trim()) else it }

    private fun List<Folder>.withoutApp(packageName: String): List<Folder> =
        mapNotNull { f -> (f.apps - packageName).let { rest -> if (rest.isEmpty()) null else f.copy(apps = rest) } }
}

internal object CardPicture {
    private const val W = 640
    private const val H = 360

    fun compose(source: android.graphics.Bitmap): android.graphics.Bitmap {
        val ratio = source.width.toFloat() / source.height
        val out = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        if (ratio in 1.55f..2.0f) {
            canvas.drawBitmap(source, null, android.graphics.RectF(0f, 0f, W.toFloat(), H.toFloat()), paint)
            return out
        }
        val dominant = AppCatalog.dominantColor(source)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            blend(dominant, android.graphics.Color.WHITE, 0.12f),
            blend(dominant, android.graphics.Color.BLACK, 0.35f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null
        val fitH = H * 0.8f
        val fitW = (fitH * ratio).coerceAtMost(W * 0.9f)
        val h = fitW / ratio
        val left = (W - fitW) / 2f
        val top = (H - h) / 2f
        canvas.drawBitmap(source, null, android.graphics.RectF(left, top, left + fitW, top + h), paint)
        return out
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(
            mix(android.graphics.Color.red(a), android.graphics.Color.red(b)),
            mix(android.graphics.Color.green(a), android.graphics.Color.green(b)),
            mix(android.graphics.Color.blue(a), android.graphics.Color.blue(b)),
        )
    }
}
