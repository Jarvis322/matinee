package com.tcltv.sahne.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.tcltv.sahne.ui.LocalBackdrop
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.LocalWallpaper
import com.tcltv.sahne.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Rendered once per accent into a bitmap off the main thread: drawing it live cost ~100 ms of GPU time per frame on the TV.
 */
@Composable
fun CinematicBackground(accent: Color, modifier: Modifier = Modifier) {
    val wallpaper = LocalWallpaper.current
    if (wallpaper != null) {
        WallpaperBackground(wallpaper, modifier)
        return
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val cache = remember { BackdropCache() }
    // Shared with the glass surfaces, which show their own slice of whatever is drawn here.
    val shared = LocalBackdrop.current
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(accent, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val cached = cache.get(accent.toArgb())
        val image = cached ?: run {
            // Holding the D-pad changes the accent every few frames; render only once focus settles.
            delay(RENDER_DEBOUNCE_MS)
            withContext(Dispatchers.Default) { renderBackdrop(size, accent.toArgb()).asImageBitmap() }
                .also { cache.put(accent.toArgb(), it) }
        }
        if (shared.to === image) return@LaunchedEffect
        shared.from = shared.to
        shared.to = image
        if (shared.from == null || reduceMotion) {
            shared.from = null
            shared.progress = 1f
            return@LaunchedEffect
        }
        shared.progress = 0f
        animate(0f, 1f, animationSpec = tween(420)) { value, _ -> shared.progress = value }
        shared.from = null
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background)
            .onSizeChanged { size = it },
    ) {
        shared.from?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        shared.to?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (shared.from != null) shared.progress else 1f },
            )
        }
    }
}

private class BackdropCache {
    private val entries = LinkedHashMap<Int, ImageBitmap>()

    fun get(accent: Int): ImageBitmap? = entries[accent]

    fun put(accent: Int, image: ImageBitmap) {
        entries.remove(accent)
        entries[accent] = image
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    private companion object {
        // Each entry is ~230 KB at 320x180, so a few more accents can stay warm.
        const val MAX_ENTRIES = 8
    }
}

// The backdrop is only soft gradients, so a small bitmap scaled to full screen looks the same (~230 KB instead of 3.7 MB).
private const val MAX_RENDER_WIDTH = 320
private const val RENDER_DEBOUNCE_MS = 90L

private fun renderBackdrop(screen: IntSize, accent: Int): Bitmap {
    val scale = minOf(1f, MAX_RENDER_WIDTH.toFloat() / screen.width)
    val w = (screen.width * scale).toInt().coerceAtLeast(1)
    val h = (screen.height * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val base = Palette.Background.toArgb()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    canvas.drawColor(base)

    paint.shader = RadialGradient(
        w * 0.84f, h * 0.04f, w * 0.85f,
        intArrayOf(withAlpha(accent, 0.86f), withAlpha(accent, 0.42f), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    // A second, softer glow low on the left: the glass shelf and panels pick up colour from it, which is what makes
    // them read as glass instead of grey plastic.
    paint.shader = RadialGradient(
        w * 0.16f, h * 0.98f, w * 0.85f,
        intArrayOf(withAlpha(accent, 0.58f), withAlpha(accent, 0.22f), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    paint.shader = LinearGradient(
        0f, 0f, w.toFloat(), 0f,
        intArrayOf(withAlpha(base, 0.8f), withAlpha(base, 0.4f), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 0.34f, 0.6f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    paint.shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(android.graphics.Color.TRANSPARENT, withAlpha(base, 0.5f), withAlpha(base, 0.72f)),
        floatArrayOf(0.55f, 0.85f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    return bitmap
}

private fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)
