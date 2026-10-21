package com.tcltv.sahne.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.data.LastPlayed
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.ui.GlassStyle
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.TintedIcon
import com.tcltv.sahne.ui.glass
import com.tcltv.sahne.ui.interStyle

private val PanelWidth = 320.dp
private val ArtHeight = 108.dp

/** The focused app's last played item, with the two most recent items from other apps underneath. */
@Composable
internal fun LastPlayedPanel(
    entry: LastPlayed,
    app: LaunchableApp,
    others: List<Pair<LastPlayed, LaunchableApp>>,
    viewModel: LauncherViewModel,
    modifier: Modifier,
) {
    Column(
        modifier
            .width(PanelWidth)
            .glass(RoundedCornerShape(24.dp), GlassStyle.Panel)
            .padding(12.dp),
    ) {
        Crossfade(targetState = entry, animationSpec = if (LocalReduceMotion.current) snap() else tween(350), label = "lastPlayed") { shown ->
            MainItem(shown, app, viewModel)
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BasicText(stringResource(R.string.last_played_other_apps), style = interStyle(10.sp, 600, Palette.TextMuted))
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth()) {
                others.forEachIndexed { index, (other, otherApp) ->
                    if (index > 0) Spacer(Modifier.width(10.dp))
                    MiniItem(other, otherApp, viewModel, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MainItem(entry: LastPlayed, app: LaunchableApp, viewModel: LauncherViewModel) {
    val accent = Color(app.accent)
    Column {
        Box(Modifier.fillMaxWidth().height(ArtHeight).clip(RoundedCornerShape(11.dp)).background(artFallback(accent))) {
            Artwork(entry, viewModel, Modifier.fillMaxSize())
            entry.subtitle?.let {
                Box(Modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    BasicText(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(10.sp, 700))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                TintedIcon(R.drawable.ic_player_play_filled, 12.dp, Palette.Background)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
            BasicText(stringResource(R.string.last_played_badge, relativeTime(entry.updatedAt)), style = interStyle(10.sp, 600, Palette.TextSecondary))
        }
        Spacer(Modifier.height(5.dp))
        BasicText(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = interStyle(14.sp, 700).copy(lineHeight = 17.sp))
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                listOfNotNull(entry.subtitle, app.label).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = interStyle(11.sp, 500, Palette.TextSecondary),
                modifier = Modifier.weight(1f),
            )
            remainingText(entry)?.let { BasicText(it, style = interStyle(11.sp, 500, Palette.TextSecondary)) }
        }
        entry.progress?.let { ProgressBar(it, accent, 3.dp, Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun MiniItem(entry: LastPlayed, app: LaunchableApp, viewModel: LauncherViewModel, modifier: Modifier) {
    val accent = Color(app.accent)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(6.dp)).background(artFallback(accent))) {
            Artwork(entry, viewModel, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            BasicText(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(11.sp, 700))
            BasicText(
                listOfNotNull(app.label, remainingText(entry)).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = interStyle(10.sp, 500, Palette.TextMuted),
            )
            entry.progress?.let { ProgressBar(it, accent, 2.dp, Modifier.padding(top = 3.dp)) }
        }
    }
}

@Composable
private fun Artwork(entry: LastPlayed, viewModel: LauncherViewModel, modifier: Modifier) {
    var artwork by remember(entry.artworkPath, entry.updatedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.artworkPath, entry.updatedAt) { artwork = viewModel.loadArtwork(entry) }
    val image = artwork ?: return
    // Square covers (music) are shown whole instead of cropped into the 16:9 frame.
    Image(image, contentDescription = null, contentScale = if (entry.artworkIsSquare) ContentScale.Fit else ContentScale.Crop, modifier = modifier)
}

@Composable
private fun ProgressBar(progress: Float, accent: Color, height: Dp, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(progress).height(height).background(lerp(accent, Color.White, 0.1f)))
    }
}

private fun artFallback(accent: Color) = Brush.linearGradient(listOf(lerp(accent, Color.Black, 0.3f), lerp(accent, Color.Black, 0.75f)))

@Composable
private fun remainingText(entry: LastPlayed): String? {
    if (entry.durationMs <= 0 || entry.positionMs < 0) return null
    val minutes = ((entry.durationMs - entry.positionMs) / 60_000).toInt()
    return if (minutes >= 1) stringResource(R.string.minutes_left, minutes) else null
}

@Composable
private fun relativeTime(time: Long): String {
    // App strings rather than DateUtils, which would follow the system language instead of the in-app one.
    val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.hours_ago, (minutes / 60).toInt())
        else -> stringResource(R.string.days_ago, (minutes / (24 * 60)).toInt())
    }
}
