package com.tcltv.sahne.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tcltv.sahne.R
import kotlin.math.cos
import kotlin.math.sin

/** A soft pool of light in a built-in wallpaper. Positions and radii are fractions of the screen's width and height. */
class Blob(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Int,
    /** How far the light drifts, as a fraction of the screen, and how fast and from where in its cycle, when animated. */
    val drift: Float = 0.06f,
    val speed: Float = 0.2f,
    val phase: Float = 0f,
)

/**
 * Wallpapers that ship with Matinee. They are drawn from a few pools of light rather than stored as pictures, so they
 * are sharp at any screen size, add nothing to the download, and can move.
 */
enum class BuiltinWallpaper(val id: String, @StringRes val label: Int, val base: Int, val blobs: List<Blob>) {
    AURORA(
        "aurora", R.string.wallpaper_aurora, 0xFF050912.toInt(),
        listOf(
            Blob(0.78f, 0.12f, 0.62f, 0xFF1FD6A8.toInt(), 0.07f, 0.17f, 0f),
            Blob(0.22f, 0.74f, 0.58f, 0xFF2B63E8.toInt(), 0.06f, 0.13f, 2f),
            Blob(0.62f, 0.9f, 0.5f, 0xFF8A3FD6.toInt(), 0.05f, 0.21f, 4f),
        ),
    ),
    DUSK(
        "dusk", R.string.wallpaper_dusk, 0xFF0B0813.toInt(),
        listOf(
            Blob(0.82f, 0.1f, 0.6f, 0xFFFF7A59.toInt(), 0.06f, 0.15f, 1f),
            Blob(0.3f, 0.2f, 0.5f, 0xFFC23B8E.toInt(), 0.07f, 0.19f, 3f),
            Blob(0.5f, 0.95f, 0.6f, 0xFF4B2DB8.toInt(), 0.05f, 0.12f, 5f),
        ),
    ),
    OCEAN(
        "ocean", R.string.wallpaper_ocean, 0xFF040B14.toInt(),
        listOf(
            Blob(0.7f, 0.08f, 0.66f, 0xFF1E8FE8.toInt(), 0.06f, 0.14f, 0.5f),
            Blob(0.15f, 0.8f, 0.6f, 0xFF0AA3A0.toInt(), 0.07f, 0.18f, 2.5f),
            Blob(0.9f, 0.85f, 0.45f, 0xFF2742B8.toInt(), 0.05f, 0.2f, 4.5f),
        ),
    ),
    EMBER(
        "ember", R.string.wallpaper_ember, 0xFF0D0707.toInt(),
        listOf(
            Blob(0.8f, 0.14f, 0.62f, 0xFFFF6A2A.toInt(), 0.07f, 0.16f, 0f),
            Blob(0.2f, 0.85f, 0.55f, 0xFFD42E5B.toInt(), 0.06f, 0.2f, 3.5f),
            Blob(0.55f, 0.55f, 0.35f, 0xFF7A2230.toInt(), 0.05f, 0.1f, 5.5f),
        ),
    ),
    FOREST(
        "forest", R.string.wallpaper_forest, 0xFF050B08.toInt(),
        listOf(
            Blob(0.75f, 0.1f, 0.6f, 0xFF3AC46B.toInt(), 0.06f, 0.15f, 1.5f),
            Blob(0.2f, 0.7f, 0.58f, 0xFF127A5B.toInt(), 0.07f, 0.17f, 3f),
            Blob(0.7f, 0.95f, 0.45f, 0xFF9BC93B.toInt(), 0.04f, 0.22f, 5f),
        ),
    ),
    ORCHID(
        "orchid", R.string.wallpaper_orchid, 0xFF0A0610.toInt(),
        listOf(
            Blob(0.8f, 0.12f, 0.62f, 0xFFB44FE0.toInt(), 0.07f, 0.14f, 2f),
            Blob(0.25f, 0.8f, 0.55f, 0xFFE0509B.toInt(), 0.06f, 0.18f, 0f),
            Blob(0.6f, 0.5f, 0.35f, 0xFF4A3DC4.toInt(), 0.05f, 0.1f, 4f),
        ),
    ),
    GRAPHITE(
        "graphite", R.string.wallpaper_graphite, 0xFF08090B.toInt(),
        listOf(
            Blob(0.78f, 0.1f, 0.65f, 0xFF7B8497.toInt(), 0.06f, 0.13f, 0f),
            Blob(0.2f, 0.85f, 0.55f, 0xFF3C4457.toInt(), 0.06f, 0.16f, 3f),
        ),
    ),
    SAND(
        "sand", R.string.wallpaper_sand, 0xFF0C0A07.toInt(),
        listOf(
            Blob(0.8f, 0.1f, 0.62f, 0xFFE0A857.toInt(), 0.06f, 0.15f, 1f),
            Blob(0.22f, 0.8f, 0.55f, 0xFFB86B3A.toInt(), 0.07f, 0.18f, 3.5f),
            Blob(0.6f, 0.95f, 0.4f, 0xFF7A4E2A.toInt(), 0.05f, 0.2f, 5f),
        ),
    );

    companion object {
        fun byId(id: String): BuiltinWallpaper? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Photographs that ship in the app's assets, in `assets/wallpapers`. Only the files actually present are offered, so a
 * build without them simply has no photo section.
 */
enum class PhotoWallpaper(val id: String, @StringRes val label: Int, val asset: String) {
    MOUNTAIN_DUSK("mountain_dusk", R.string.photo_mountain_dusk, "photo_01.jpg"),
    LAKE_TRAIL("lake_trail", R.string.photo_lake_trail, "photo_02.jpg"),
    ROCKY_COAST("rocky_coast", R.string.photo_rocky_coast, "photo_03.jpg"),
    CLIFF_BEACH("cliff_beach", R.string.photo_cliff_beach, "photo_04.jpg"),
    PARIS("paris", R.string.photo_paris, "photo_05.jpg"),
    SALT_MARSH("salt_marsh", R.string.photo_salt_marsh, "photo_06.jpg"),
    HILL_TOWN("hill_town", R.string.photo_hill_town, "photo_07.jpg");

    companion object {
        fun byId(id: String): PhotoWallpaper? = entries.firstOrNull { it.id == id }

        /** The photos this build actually contains. */
        fun available(context: Context): List<PhotoWallpaper> {
            val present = runCatching { context.assets.list("wallpapers")?.toSet() }.getOrNull().orEmpty()
            return entries.filter { it.asset in present }
        }
    }
}

/** What the home screen draws: the wallpaper at screen size, and a small soft copy the glass surfaces show through. */
class ResolvedWallpaper(
    val key: String,
    val display: ImageBitmap,
    val soft: ImageBitmap,
    val builtin: BuiltinWallpaper?,
    /** How much darker the glass should be over this picture, 0 to 1, so white text stays readable on it. */
    val glassDim: Float = 0f,
)

/**
 * How much to darken glass over [soft]: bright pictures and busy ones (high contrast, such as clouds or a city) need
 * more, the dark built-in wallpapers none. Judged from luminance alone, which is what decides whether text reads.
 */
internal fun glassDimFor(soft: Bitmap): Float {
    val pixels = IntArray(soft.width * soft.height)
    soft.getPixels(pixels, 0, soft.width, 0, 0, soft.width, soft.height)
    var sum = 0.0
    var sumSquares = 0.0
    for (p in pixels) {
        val y = (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
        sum += y
        sumSquares += y * y
    }
    val mean = sum / pixels.size
    val spread = kotlin.math.sqrt((sumSquares / pixels.size - mean * mean).coerceAtLeast(0.0))
    return ((mean - 0.28) * 0.9 + spread * 0.7).toFloat().coerceIn(0f, 0.42f)
}

/** Where a wallpaper choice is stored: nothing, a built-in's id, or the file name of a picture the user added. */
object WallpaperKey {
    const val BUILTIN_PREFIX = "builtin:"
    const val USER_PREFIX = "user:"
    const val PHOTO_PREFIX = "photo:"

    fun builtin(wallpaper: BuiltinWallpaper) = BUILTIN_PREFIX + wallpaper.id

    fun user(file: String) = USER_PREFIX + file

    fun photo(wallpaper: PhotoWallpaper) = PHOTO_PREFIX + wallpaper.id
}

object WallpaperRenderer {

    /** Draws [wallpaper] at [w] x [h]; [time] in seconds moves the light, and 0 is the resting picture. */
    fun render(wallpaper: BuiltinWallpaper, w: Int, h: Int, time: Float, into: Bitmap? = null, scrims: Boolean = true): Bitmap {
        val bitmap = into ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(wallpaper.base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        for (blob in wallpaper.blobs) {
            // Measured from the resting picture, so time 0 (previews, the still wallpaper) looks exactly as before.
            val t = time * MOTION_SPEED
            val angle = t * blob.speed + blob.phase
            val reach = blob.drift * MOTION_REACH
            val cx = (blob.x + reach * (sin(angle) - sin(blob.phase))) * w
            val cy = (blob.y + reach * 0.7f * (cos(angle * 0.8f) - cos(blob.phase * 0.8f))) * h
            // The light also swells and settles, which reads as movement even where a blob barely travels.
            val radius = blob.radius * w * (1f + 0.14f * sin(t * blob.speed * 0.7f))
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(withAlpha(blob.color, 0.9f), withAlpha(blob.color, 0.4f), withAlpha(blob.color, 0.12f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        paint.shader = null
        if (scrims) drawScrims(canvas, w, h)
        return bitmap
    }

    /**
     * Darkens the left, for the title, and the bottom, for the shelf. It is part of the picture rather than a layer on
     * top, so the glass that shows the wallpaper through itself, and the artwork that fades into it, see the same thing
     * the screen does.
     */
    fun drawScrims(canvas: Canvas, w: Int, h: Int) {
        val base = SCRIM_BASE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(withAlpha(base, 0.8f), withAlpha(base, 0.4f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.34f, 0.6f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.TRANSPARENT, withAlpha(base, 0.5f), withAlpha(base, 0.72f)),
            floatArrayOf(0.55f, 0.85f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private const val SCRIM_BASE = 0xFF0B0B0E.toInt()

    /** How much faster and farther the light moves than each blob's own drift and speed; at 1 it was too slow to see. */
    private const val MOTION_SPEED = 2.4f
    private const val MOTION_REACH = 2.2f

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)
}

/** Turns the stored wallpaper choice into pictures ready to draw. */
class WallpaperResolver(private val context: Context, private val images: UserImages) {

    fun resolve(key: String): ResolvedWallpaper? = when {
        key.startsWith(WallpaperKey.BUILTIN_PREFIX) -> BuiltinWallpaper.byId(key.removePrefix(WallpaperKey.BUILTIN_PREFIX))?.let { builtin(key, it) }
        key.startsWith(WallpaperKey.PHOTO_PREFIX) -> PhotoWallpaper.byId(key.removePrefix(WallpaperKey.PHOTO_PREFIX))?.let { photo(key, it) }
        key.startsWith(WallpaperKey.USER_PREFIX) -> user(key, key.removePrefix(WallpaperKey.USER_PREFIX))
        else -> null
    }

    /** A small preview of a built-in, for the picker. */
    fun preview(wallpaper: BuiltinWallpaper, width: Int): ImageBitmap =
        WallpaperRenderer.render(wallpaper, width, width * 9 / 16, 0f, scrims = false).asImageBitmap()

    fun userPreview(file: String, width: Int): ImageBitmap? = images.load(file, width)?.asImageBitmap()

    fun photoPreview(wallpaper: PhotoWallpaper, width: Int): ImageBitmap? = decodeAsset(wallpaper.asset, width, mutable = false)?.asImageBitmap()

    /** Bakes the scrims into the picture and into the soft copy the glass shows through. */
    private fun finish(key: String, full: Bitmap): ResolvedWallpaper {
        // Scaled down, the picture is a light blur of itself: soft enough to read as glass, sharp enough that the
        // bent edges show what they bend. Halving step by step averages every pixel instead of skipping most of them.
        var soft = full
        while (soft.width / 2 >= SOFT_WIDTH) {
            val half = Bitmap.createScaledBitmap(soft, soft.width / 2, (soft.height / 2).coerceAtLeast(1), true)
            if (soft !== full) soft.recycle()
            soft = half
        }
        val softHeight = (SOFT_WIDTH.toFloat() * full.height / full.width).toInt().coerceAtLeast(1)
        soft = Bitmap.createScaledBitmap(soft, SOFT_WIDTH, softHeight, true).let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
        WallpaperRenderer.drawScrims(Canvas(full), full.width, full.height)
        WallpaperRenderer.drawScrims(Canvas(soft), soft.width, soft.height)
        return ResolvedWallpaper(key, full.asImageBitmap(), soft.asImageBitmap(), null, glassDimFor(soft))
    }

    private fun builtin(key: String, wallpaper: BuiltinWallpaper): ResolvedWallpaper {
        val (w, h) = displaySize()
        val soft = WallpaperRenderer.render(wallpaper, SOFT_WIDTH, SOFT_WIDTH * 9 / 16, 0f)
        return ResolvedWallpaper(
            key,
            WallpaperRenderer.render(wallpaper, w, h, 0f).asImageBitmap(),
            soft.asImageBitmap(),
            wallpaper,
            glassDimFor(soft),
        )
    }

    private fun user(key: String, file: String): ResolvedWallpaper? {
        val (w, _) = displaySize()
        val full = images.load(file, w, mutable = true) ?: return null
        return finish(key, full)
    }

    private fun photo(key: String, wallpaper: PhotoWallpaper): ResolvedWallpaper? {
        val (w, _) = displaySize()
        val full = decodeAsset(wallpaper.asset, w, mutable = true) ?: return null
        return finish(key, full)
    }

    private fun decodeAsset(name: String, maxWidth: Int, mutable: Boolean): Bitmap? = runCatching {
        val path = "wallpapers/$name"
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        context.assets.open(path).use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample; inMutable = mutable })
        }
    }.getOrNull()

    /** The screen's own size, capped at full HD: 4K would cost 32 MB for a picture that sits behind blurred glass. */
    private fun displaySize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val width = minOf(metrics.widthPixels, MAX_DISPLAY_WIDTH).coerceAtLeast(960)
        return width to (width * 9 / 16)
    }

    private companion object {
        const val SOFT_WIDTH = 384
        const val MAX_DISPLAY_WIDTH = 1920
    }
}
