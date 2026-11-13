package com.tcltv.sahne.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.RecentApp
import com.tcltv.sahne.SwitcherState
import com.tcltv.sahne.data.CardTheme
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.appCardFocus
import com.tcltv.sahne.ui.interStyle
import com.tcltv.sahne.ui.rememberFocusSheen
import com.tcltv.sahne.ui.settings.DialogButton
import kotlinx.coroutines.launch

/**
 * The app switcher, as on Apple TV: the apps used last, newest first. OK opens one, Up quits it, Back closes the
 * switcher. It sits in its own window so focus can't wander to the home screen underneath.
 */
@Composable
fun AppSwitcherHost(viewModel: LauncherViewModel) {
    val state by viewModel.switcher.collectAsStateWithLifecycle()
    val current = state ?: return
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = viewModel::dismissSwitcher,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        AppSwitcher(viewModel, current, settings.cardTheme)
    }
}

@Composable
private fun AppSwitcher(viewModel: LauncherViewModel, state: SwitcherState, cardTheme: CardTheme) {
    val launch = rememberAppLauncher(viewModel)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA))) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center)) {
            BasicText(
                stringResource(R.string.switcher_title),
                style = interStyle(22.sp, 700),
                modifier = Modifier.padding(start = SWITCHER_SIDE.dp),
            )
            Spacer(Modifier.height(26.dp))
            when (state) {
                SwitcherState.Loading -> Message(R.string.switcher_loading, null)
                SwitcherState.NeedsAdb -> Message(R.string.background_apps_needs_adb, R.string.switcher_needs_adb_sub)
                is SwitcherState.Ready -> if (state.apps.isEmpty()) {
                    Message(R.string.switcher_empty, null)
                } else {
                    SwitcherRow(
                        apps = state.apps,
                        cardTheme = cardTheme,
                        onOpen = { item ->
                            launch(item.app)
                            viewModel.dismissSwitcher()
                        },
                        onClose = viewModel::closeRecentApp,
                    )
                    Spacer(Modifier.height(26.dp))
                    // Below the row, so Down reaches it; Up on a card already quits that one app.
                    Row(Modifier.padding(start = SWITCHER_SIDE.dp), verticalAlignment = Alignment.CenterVertically) {
                        DialogButton(stringResource(R.string.switcher_close_all), Modifier, viewModel::closeAllRecentApps)
                        Spacer(Modifier.width(18.dp))
                        BasicText(stringResource(R.string.switcher_hint), style = interStyle(13.sp, 500, Palette.TextSecondary))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitcherRow(
    apps: List<RecentApp>,
    cardTheme: CardTheme,
    onOpen: (RecentApp) -> Unit,
    onClose: (RecentApp) -> Unit,
) {
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // Where focus goes next: the first card on opening, and after an app is quit the card that takes its place. Focus
    // jumps to a neighbour on its own as the quit card leaves, so the place is noted before it does.
    var focusTarget by remember { mutableIntStateOf(0) }
    LaunchedEffect(apps) {
        val target = apps.getOrNull(focusTarget.coerceAtMost(apps.lastIndex)) ?: return@LaunchedEffect
        // The row lays the card out a frame later.
        withFrameNanos { }
        runCatching { requesters[target.app.packageName]?.requestFocus() }
    }
    SmoothScrolling {
        LazyRow(
            contentPadding = PaddingValues(horizontal = SWITCHER_SIDE.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            itemsIndexed(apps, key = { _, item -> item.app.packageName }) { index, item ->
                SwitcherCard(
                    item = item,
                    cardTheme = cardTheme,
                    requester = requesters.getOrPut(item.app.packageName) { FocusRequester() },
                    onOpen = { onOpen(item) },
                    onClose = {
                        focusTarget = index
                        onClose(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitcherCard(
    item: RecentApp,
    cardTheme: CardTheme,
    requester: FocusRequester,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val app = item.app
    var focused by remember { mutableStateOf(false) }
    val origin = LocalLaunchOrigin.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    // 0 at rest, 1 once the card has flown up and away as the app quits.
    val leaving = remember { Animatable(0f) }
    val shape = RoundedCornerShape(18.dp)
    val sheen = rememberFocusSheen(focused)
    val lightCard = cardTheme == CardTheme.LIGHT
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            translationY = -leaving.value * LEAVE_DISTANCE.dp.toPx()
            alpha = 1f - leaving.value
        },
    ) {
        Row(Modifier.width(CARD_WIDTH.dp), verticalAlignment = Alignment.CenterVertically) {
            app.glyph?.let { Image(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(8.dp))
            BasicText(
                app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = interStyle(14.sp, 600, if (focused) Palette.TextPrimary else Palette.TextSecondary),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
                .onGloballyPositioned { if (focused) origin.bounds = it.boundsInRoot() }
                .scaleBy(focusScale(focused, 1.08f))
                .shadow(if (focused) 26.dp else 0.dp, shape)
                .clip(shape)
                .background(if (lightCard) CardLight else CardDark)
                .appCardFocus(sheen, shape, lightCard)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                    if (leaving.value == 0f && !leaving.isRunning) {
                        scope.launch {
                            if (!reduceMotion) leaving.animateTo(1f, tween(LEAVE_MILLIS))
                            onClose()
                        }
                    }
                    true
                }
                .semantics { contentDescription = app.label }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            // The home row's card art, drawn at its own size and scaled up, so every app looks as it does on the row.
            Box(
                Modifier
                    .requiredSize(ROW_CARD_WIDTH.dp, ROW_CARD_HEIGHT.dp)
                    .graphicsLayer {
                        scaleX = CARD_WIDTH / ROW_CARD_WIDTH
                        scaleY = CARD_HEIGHT / ROW_CARD_HEIGHT
                    },
                contentAlignment = Alignment.CenterStart,
            ) { CardArt(app, cardTheme) }
        }
    }
}

@Composable
private fun Message(@StringRes title: Int, @StringRes subtitle: Int?) {
    Column(Modifier.padding(horizontal = SWITCHER_SIDE.dp)) {
        BasicText(stringResource(title), style = interStyle(17.sp, 600))
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            BasicText(stringResource(subtitle), style = interStyle(13.sp, 500, Palette.TextSecondary))
        }
    }
}

private const val SWITCHER_SIDE = 64
private const val CARD_WIDTH = 236f
private const val CARD_HEIGHT = 144f
private const val ROW_CARD_WIDTH = 123f
private const val ROW_CARD_HEIGHT = 75f
private const val LEAVE_DISTANCE = 240
private const val LEAVE_MILLIS = 240
private const val SCRIM_ALPHA = 0.84f
