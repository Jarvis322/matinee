package com.tcltv.sahne.ui.screensaver

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tcltv.sahne.data.BuiltinWallpaper
import com.tcltv.sahne.data.LauncherSettings
import com.tcltv.sahne.data.ResolvedWallpaper
import com.tcltv.sahne.data.SettingsStore
import com.tcltv.sahne.data.UserImages
import com.tcltv.sahne.data.WallpaperRenderer
import com.tcltv.sahne.data.WallpaperResolver
import com.tcltv.sahne.ui.DesignCanvas
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.home.rememberNow
import com.tcltv.sahne.ui.interStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The screensaver: the home screen's wallpaper, its light still drifting, with a large clock that changes corner
 * every minute so nothing stays on the same pixels for hours.
 */
@Composable
fun ScreensaverScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsState(initial = null)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val s = settings ?: return@Box
        var chosen by remember { mutableStateOf<ResolvedWallpaper?>(null) }
        LaunchedEffect(s.wallpaper) {
            chosen = if (s.wallpaper.isEmpty()) null else withContext(Dispatchers.Default) {
                WallpaperResolver(context, UserImages(context)).resolve(s.wallpaper)
            }
        }
        // Without a wallpaper the home screen follows the focused app's colour, which means nothing here.
        val picture = chosen
        val builtin = if (s.wallpaper.isEmpty()) BuiltinWallpaper.AURORA else picture?.builtin
        when {
            builtin != null && !s.reduceMotion -> DriftingWallpaper(builtin)
            picture != null -> Image(picture.display, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            builtin != null -> StillWallpaper(builtin)
        }
        DesignCanvas { Clock(s) }
    }
}

@Composable
private fun Clock(settings: LauncherSettings) {
    val now = rememberNow(withSeconds = false)
    val locale = Locale.getDefault()
    val datePattern = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }
    val time = now.format(DateTimeFormatter.ofPattern(if (settings.use24Hour) "HH:mm" else "h:mm", locale))
    val corner = CORNERS[now.minute % CORNERS.size]
    val shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 18f)
    Crossfade(corner, animationSpec = if (settings.reduceMotion) snap() else tween(CORNER_FADE_MS), label = "clockCorner") { alignment ->
        Box(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 44.dp)) {
            Column(Modifier.align(alignment), horizontalAlignment = if (alignment.isEnd()) Alignment.End else Alignment.Start) {
                BasicText(time, style = interStyle(96.sp, 600, letterSpacing = (-3).sp).copy(shadow = shadow))
                BasicText(now.format(datePattern), style = interStyle(20.sp, 500, Palette.TextSecondary).copy(shadow = shadow))
            }
        }
    }
}

private fun Alignment.isEnd() = this == Alignment.BottomEnd || this == Alignment.TopEnd

private val CORNERS = listOf(Alignment.BottomStart, Alignment.TopEnd, Alignment.BottomEnd, Alignment.TopStart)

/** The built-in wallpaper's light moving, redrawn small into two bitmaps that take turns, as on the home screen. */
@Composable
private fun DriftingWallpaper(wallpaper: BuiltinWallpaper) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(wallpaper, lifecycle) {
        val buffers = Array(2) { Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888) }
        val started = System.nanoTime()
        var turn = 0
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val seconds = (System.nanoTime() - started) / 1_000_000_000f
                val buffer = buffers[turn and 1]
                withContext(Dispatchers.Default) { WallpaperRenderer.render(wallpaper, FRAME_WIDTH, FRAME_HEIGHT, seconds, buffer) }
                frame = buffer.asImageBitmap()
                turn++
                delay(FRAME_INTERVAL_MS)
            }
        }
    }
    frame?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

@Composable
private fun StillWallpaper(wallpaper: BuiltinWallpaper) {
    val image = remember(wallpaper) { WallpaperRenderer.render(wallpaper, FRAME_WIDTH, FRAME_HEIGHT, 0f).asImageBitmap() }
    Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

private const val FRAME_WIDTH = 384
private const val FRAME_HEIGHT = 216
private const val FRAME_INTERVAL_MS = 66L
private const val CORNER_FADE_MS = 1200
