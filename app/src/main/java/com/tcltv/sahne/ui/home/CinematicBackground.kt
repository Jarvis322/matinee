package com.tcltv.sahne.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Rendered once per accent into a bitmap off the main thread: drawing it live cost ~100 ms of GPU time per frame on the TV.
 */
@Composable
fun CinematicBackground(accent: Color, modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val cache = remember { BackdropCache() }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(accent, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val cached = cache.get(accent.toArgb())
        if (cached != null) {
            backdrop = cached
            return@LaunchedEffect
        }
        // Holding the D-pad changes the accent every few frames; render only once focus settles.
        delay(RENDER_DEBOUNCE_MS)
        backdrop = withContext(Dispatchers.Default) {
            renderBackdrop(size, accent.toArgb()).asImageBitmap()
        }.also { cache.put(accent.toArgb(), it) }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background)
            .onSizeChanged { size = it },
    ) {
        Crossfade(
            targetState = backdrop,
            animationSpec = if (LocalReduceMotion.current) snap() else tween(420),
            label = "backdrop",
        ) { image ->
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
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
        w * 0.86f, h * 0.06f, w * 0.62f,
        intArrayOf(withAlpha(accent, 0.78f), withAlpha(accent, 0.32f), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    paint.shader = LinearGradient(
        0f, 0f, w.toFloat(), 0f,
        intArrayOf(withAlpha(base, 0.92f), withAlpha(base, 0.55f), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 0.38f, 0.62f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

    paint.shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(android.graphics.Color.TRANSPARENT, withAlpha(base, 0.82f), base),
        floatArrayOf(0.5f, 0.78f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    return bitmap
}

private fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((alpha * 255).toInt().coerceIn(0, 255) shl 24)
