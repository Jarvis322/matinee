package com.tcltv.sahne.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.ContinueItem
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.data.LastPlayed
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.appCardFocus
import com.tcltv.sahne.ui.interStyle
import com.tcltv.sahne.ui.rememberFocusSheen

/**
 * What each app last played, newest first: a wide picture, the title, how much is left. Selecting a card opens the app,
 * which in most cases picks up where it stopped.
 */
@Composable
internal fun ContinueRow(
    items: List<ContinueItem>,
    viewModel: LauncherViewModel,
    onOpen: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier,
    side: Dp = ROW_SIDE.dp,
) {
    Column(modifier) {
        BasicText(
            stringResource(R.string.section_continue_watching),
            style = interStyle(12.sp, 600, Color.White.copy(alpha = 0.62f)),
            modifier = Modifier.padding(start = side),
        )
        SmoothScrolling {
            LazyRow(
                contentPadding = PaddingValues(start = side, end = side, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.entry.packageName }) { item ->
                    ContinueCard(item, viewModel) { onOpen(item.app) }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, viewModel: LauncherViewModel, onOpen: () -> Unit) {
    val entry = item.entry
    val app = item.app
    var focused by remember { mutableStateOf(false) }
    val origin = LocalLaunchOrigin.current
    val shape = RoundedCornerShape(14.dp)
    val sheen = rememberFocusSheen(focused)
    val accent = Color(app.accent)
    Box(
        Modifier
            .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
            .onGloballyPositioned { if (focused) origin.bounds = it.boundsInRoot() }
            .scaleBy(focusScale(focused, 1.08f))
            .shadow(if (focused) 20.dp else 0.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(accent, Color.Black, 0.35f), lerp(accent, Color.Black, 0.8f))))
            .appCardFocus(sheen, shape, lightCard = false)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = listOfNotNull(entry.title, entry.subtitle, app.label).joinToString(", ") }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
    ) {
        CardPicture(entry, app, viewModel)
        // Keeps the text readable on any picture.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        remainingMinutes(entry)?.let { minutes ->
            BasicText(
                stringResource(R.string.minutes_left, minutes),
                style = interStyle(9.5.sp, 600, Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 9.dp)) {
            BasicText(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(12.5.sp, 700, Palette.TextPrimary))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                app.glyph?.let { Image(it, contentDescription = null, modifier = Modifier.size(11.dp)) }
                Spacer(Modifier.width(5.dp))
                BasicText(
                    listOfNotNull(entry.subtitle, app.label).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = interStyle(9.5.sp, 500, Palette.TextSecondary),
                )
            }
            entry.progress?.let { progress ->
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(lerp(accent, Color.White, 0.25f)))
                }
            }
        }
    }
}

/** TMDB's wide picture first, then the app's own cover, then the app's banner on its accent colour. */
@Composable
private fun CardPicture(entry: LastPlayed, app: LaunchableApp, viewModel: LauncherViewModel) {
    var backdrop by remember(entry.title, entry.subtitle) { mutableStateOf<ImageBitmap?>(null) }
    var cover by remember(entry.artworkPath, entry.updatedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.title, entry.subtitle) { backdrop = viewModel.loadBackdrop(entry) }
    LaunchedEffect(entry.artworkPath, entry.updatedAt) { cover = viewModel.loadArtwork(entry) }
    val wide = backdrop
    val own = cover
    when {
        wide != null -> Image(wide, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        own != null -> Image(
            own,
            contentDescription = null,
            // Square covers (music) are shown whole instead of cropped into the wide frame.
            contentScale = if (entry.artworkIsSquare) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        else -> app.banner?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(24.dp), alpha = 0.5f)
        }
    }
}

private fun remainingMinutes(entry: LastPlayed): Int? {
    if (entry.durationMs <= 0 || entry.positionMs < 0) return null
    return ((entry.durationMs - entry.positionMs) / 60_000).toInt().takeIf { it >= 1 }
}

private const val ROW_SIDE = 48
private const val CARD_WIDTH = 214
private const val CARD_HEIGHT = 120
