package com.tcltv.sahne.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.data.LastPlayed
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.interStyle

/**
 * Full-bleed area above the grid's shelf: the focused app's last played artwork across the top of the screen,
 * with its title on the left. Apps with nothing played yet show only their name over the tinted backdrop.
 */
@Composable
internal fun GridHero(app: LaunchableApp, entry: LastPlayed?, viewModel: LauncherViewModel, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        Crossfade(
            targetState = entry,
            animationSpec = if (LocalReduceMotion.current) snap() else tween(500),
            label = "gridHero",
        ) { shown ->
            if (shown != null) HeroArtwork(shown, viewModel)
        }
        // Darkens the left side for the title and fades into the page background behind the shelf.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Palette.Background.copy(alpha = 0.78f), 0.55f to Color.Transparent),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                // The top band keeps the clock and profile readable over bright artwork.
                Brush.verticalGradient(
                    0f to Palette.Background.copy(alpha = 0.7f),
                    0.18f to Color.Transparent,
                    0.4f to Color.Transparent,
                    0.72f to Palette.Background.copy(alpha = 0.85f),
                    1f to Palette.Background,
                ),
            ),
        )
        Column(Modifier.padding(start = 48.dp, top = 104.dp).width(520.dp)) {
            BasicText(
                entry?.title ?: app.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = interStyle(38.sp, 800).copy(lineHeight = 41.sp),
            )
            val meta = listOfNotNull(entry?.subtitle, if (entry != null) app.label else null).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                BasicText(meta, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(13.sp, 500, Palette.TextSecondary))
            }
            entry?.progress?.let { progress ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(200.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(Color.White))
                }
            }
        }
    }
}

@Composable
private fun HeroArtwork(entry: LastPlayed, viewModel: LauncherViewModel) {
    var artwork by remember(entry.artworkPath, entry.updatedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.artworkPath, entry.updatedAt) { artwork = viewModel.loadArtwork(entry) }
    val image = artwork ?: return
    // Square music covers would be cropped to a sliver across the full width, so they sit on the right at their own shape.
    if (entry.artworkIsSquare) {
        Box(Modifier.fillMaxSize().padding(start = 560.dp, end = 48.dp, top = 90.dp, bottom = 230.dp)) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    } else {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
