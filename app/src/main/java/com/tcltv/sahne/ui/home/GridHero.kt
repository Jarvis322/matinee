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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.data.LastPlayed
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.fadeIntoBackdrop
import com.tcltv.sahne.ui.interStyle

/**
 * Area above the grid's shelf, like the top shelf on Apple TV: the focused app's last played artwork across the top
 * of the screen, fading out behind the shelf, with its title at the bottom left just above the shelf. Apps with
 * nothing played yet show only their name over the tinted backdrop.
 */
@Composable
internal fun GridHero(app: LaunchableApp, entry: LastPlayed?, viewModel: LauncherViewModel, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        // Ends a little below the shelf's top edge, so artwork never sits behind the app grid. Its bottom is faded out
        // with an alpha mask rather than a colour, so it melts into the coloured backdrop and the glass shelf.
        Box(
            Modifier
                .fillMaxWidth()
                .height((GRID_HERO_BOTTOM + ARTWORK_OVERLAP).dp)
                // With no artwork there is nothing to fade out, and the steps would only cost fill.
                .then(if (entry != null) Modifier.fadeIntoBackdrop(HERO_FADE_HEIGHT) else Modifier),
        ) {
            Crossfade(
                targetState = entry,
                animationSpec = if (LocalReduceMotion.current) snap() else tween(500),
                label = "gridHero",
            ) { shown ->
                if (shown != null) HeroArtwork(shown, viewModel)
            }
            // Darkens the left side for the title.
            Box(
                Modifier.fillMaxSize().background(
                    // Strong enough to quiet text baked into thumbnails, which would otherwise fight with the title.
                    Brush.horizontalGradient(
                        0f to Palette.Background.copy(alpha = 0.94f),
                        0.38f to Palette.Background.copy(alpha = 0.7f),
                        0.7f to Color.Transparent,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    // The top band keeps the clock and profile readable over bright artwork.
                    Brush.verticalGradient(
                        0f to Palette.Background.copy(alpha = 0.92f),
                        0.3f to Color.Transparent,
                    ),
                ),
            )
        }
        Column(
            Modifier
                .padding(start = 48.dp, top = (GRID_HERO_BOTTOM - TITLE_GAP).dp)
                .width(560.dp)
                .layout { measurable, constraints ->
                    // Grows upward from just above the shelf, whatever the title's line count.
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
                },
        ) {
            BasicText(
                entry?.title ?: app.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = if (entry != null) interStyle(26.sp, 800).copy(lineHeight = 31.sp) else interStyle(40.sp, 800),
            )
            val meta = listOfNotNull(entry?.subtitle, if (entry != null) app.label else null).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
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

private const val ARTWORK_OVERLAP = 60
private val HERO_FADE_HEIGHT = 170.dp
private const val TITLE_GAP = 22

@Composable
private fun HeroArtwork(entry: LastPlayed, viewModel: LauncherViewModel) {
    var artwork by remember(entry.artworkPath, entry.updatedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.artworkPath, entry.updatedAt) { artwork = viewModel.loadArtwork(entry) }
    val image = artwork ?: return
    // Square music covers would be cropped to a sliver across the full width, so they sit on the right at their own shape.
    if (entry.artworkIsSquare) {
        Box(Modifier.fillMaxSize().padding(start = 560.dp, end = 48.dp, top = 90.dp, bottom = (ARTWORK_OVERLAP + 20).dp)) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    } else {
        // Apps share artwork at about 640 px (YouTube sends exactly 640x360), so it is upscaled about 3x across the
        // screen; the better filter keeps that softer rather than blocky.
        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
