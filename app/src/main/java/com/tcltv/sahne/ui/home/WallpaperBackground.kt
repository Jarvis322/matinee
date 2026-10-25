package com.tcltv.sahne.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tcltv.sahne.data.BuiltinWallpaper
import com.tcltv.sahne.data.ResolvedWallpaper
import com.tcltv.sahne.data.WallpaperRenderer
import com.tcltv.sahne.ui.LocalBackdrop
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.LocalWallpaperAnimated
import com.tcltv.sahne.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Draws the chosen wallpaper behind everything and hands its soft copy to the glass surfaces, which show it through
 * themselves. A scrim keeps text readable over a bright picture. Built-in wallpapers can also drift, redrawn small a
 * few times a second, only while Matinee is on screen and motion is allowed.
 */
@Composable
fun WallpaperBackground(wallpaper: ResolvedWallpaper, modifier: Modifier = Modifier) {
    val shared = LocalBackdrop.current
    val animated = LocalWallpaperAnimated.current && !LocalReduceMotion.current && wallpaper.builtin != null
    var frame by remember(wallpaper.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallpaper.key) {
        shared.from = null
        shared.to = wallpaper.soft
        shared.progress = 1f
    }
    if (animated) DriftingLight(wallpaper.builtin!!) { image ->
        frame = image
        shared.to = image
    }
    Box(modifier.fillMaxSize().background(Palette.Background)) {
        Image(
            bitmap = if (animated) frame ?: wallpaper.display else wallpaper.display,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Redraws the wallpaper's light about fifteen times a second into two small bitmaps that take turns, so nothing is allocated. */
@Composable
private fun DriftingLight(wallpaper: BuiltinWallpaper, onFrame: (ImageBitmap) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(wallpaper, lifecycle) {
        val buffers = Array(2) { Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888) }
        val started = System.nanoTime()
        var turn = 0
        // Only while the screen is showing: a launcher sits behind other apps for hours.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val seconds = (System.nanoTime() - started) / 1_000_000_000f
                val buffer = buffers[turn and 1]
                withContext(Dispatchers.Default) { WallpaperRenderer.render(wallpaper, FRAME_WIDTH, FRAME_HEIGHT, seconds, buffer) }
                onFrame(buffer.asImageBitmap())
                turn++
                delay(FRAME_INTERVAL_MS)
            }
        }
    }
}

private const val FRAME_WIDTH = 384
private const val FRAME_HEIGHT = 216
private const val FRAME_INTERVAL_MS = 66L
