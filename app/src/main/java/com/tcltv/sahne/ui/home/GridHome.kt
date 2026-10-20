package com.tcltv.sahne.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.data.AppCatalog
import com.tcltv.sahne.data.CardTheme
import com.tcltv.sahne.data.HdmiInput
import com.tcltv.sahne.data.LastPlayed
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.data.LauncherSettings
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.TintedIcon
import com.tcltv.sahne.ui.interStyle

private const val COLUMNS = 6
private const val GRID_SIDE = 48
// Shared by the shelf's inner padding and the grid rows, so shelf tiles line up with the grid columns below.
private const val ROW_INSET = 12
// Pushes the shelf to the lower part of the screen so the hero has room above it and the first grid row peeks below.
private const val HERO_SPACE = 236

private data class GridFocusRequest(val packageName: String?, val nonce: Int)

/**
 * Favorites shelf above a grid of the home apps. The shelf holds the first [COLUMNS] apps in the user's order,
 * so moving an app into the first places puts it on the shelf.
 */
@Composable
internal fun GridHome(
    viewModel: LauncherViewModel,
    apps: List<LaunchableApp>,
    allApps: List<LaunchableApp>,
    inputs: List<HdmiInput>,
    settings: LauncherSettings,
    lastPlayed: Map<String, LastPlayed>,
    resumeTick: Int,
    onOpenSettings: () -> Unit,
) {
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var liftedPackage by remember { mutableStateOf<String?>(null) }
    var focusRequest by remember { mutableStateOf(GridFocusRequest(null, 0)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var shelfFocused by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    // Read only in graphicsLayer and a derived flag, so the fade redraws the hero instead of recomposing the grid.
    val heroAlpha = animateFloatAsState(
        targetValue = if (shelfFocused) 1f else 0f,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(350),
        label = "heroAlpha",
    )

    val current = apps.firstOrNull { it.packageName == selectedPackage } ?: apps.firstOrNull()
    val accent = if (settings.adaptiveBackground && current != null) Color(current.accent) else NeutralAccent
    val heroVisible by remember { derivedStateOf { heroAlpha.value > 0f } }
    val hiddenApps = remember(allApps, settings) { allApps.filter { AppCatalog.isHidden(it, settings) } }

    fun requestFocus(packageName: String?) {
        if (packageName != null) focusRequest = GridFocusRequest(packageName, focusRequest.nonce + 1)
    }
    LaunchedEffect(resumeTick, apps.isNotEmpty()) { requestFocus(current?.packageName) }
    // A moved card is recomposed at its new place (often in another row), so focus is handed to it once the order lands.
    LaunchedEffect(apps) { liftedPackage?.let(::requestFocus) }
    BackHandler(enabled = liftedPackage != null) { liftedPackage = null }
    // Coming back up to the shelf restores the full hero instead of leaving the shelf pinned under the top bar.
    LaunchedEffect(shelfFocused) { if (shelfFocused) listState.animateScrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        CinematicBackground(accent)
        if (current != null && heroVisible) {
            GridHero(current, lastPlayed[current.packageName], viewModel, Modifier.graphicsLayer { alpha = heroAlpha.value })
        }
        TopBar(viewModel, onOpenSettings, Modifier.padding(start = GRID_SIDE.dp, end = GRID_SIDE.dp, top = 30.dp))

        val shelf = apps.take(COLUMNS)
        // Null marks the add-app tile, shown only when there are apps left to add.
        val cells: List<LaunchableApp?> = apps.drop(COLUMNS) + if (hiddenApps.isNotEmpty()) listOf(null) else emptyList()
        val visibleInputs = (if (settings.hideDisconnectedInputs) inputs.filter { it.connected } else inputs)
            .takeIf { settings.showInputs }.orEmpty()

        @Composable
        fun Cell(app: LaunchableApp?, isRowEnd: Boolean) {
            if (app == null) {
                AddTile(enabled = liftedPackage == null, isRowEnd = isRowEnd, onClick = { showAddDialog = true })
            } else {
                GridTile(
                    app = app,
                    cardTheme = settings.cardTheme,
                    lifted = app.packageName == liftedPackage,
                    isRowEnd = isRowEnd,
                    dimmed = liftedPackage != null && app.packageName != liftedPackage,
                    focusRequest = focusRequest,
                    onFocusRequestHandled = { focusRequest = focusRequest.copy(packageName = null) },
                    onFocused = { selectedPackage = app.packageName },
                    onClick = {
                        when (liftedPackage) {
                            null -> viewModel.launchApp(app)
                            app.packageName -> liftedPackage = null
                        }
                    },
                    onLongPress = { if (liftedPackage == null) liftedPackage = app.packageName },
                    onMove = { delta -> viewModel.moveApp(app.packageName, delta) },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = 80.dp),
            contentPadding = PaddingValues(start = GRID_SIDE.dp, end = GRID_SIDE.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "heroSpace") { Spacer(Modifier.height(HERO_SPACE.dp)) }
            item(key = "shelf") {
                Column(Modifier.onFocusChanged { shelfFocused = it.hasFocus }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
                            .padding(start = ROW_INSET.dp, end = ROW_INSET.dp, top = 14.dp, bottom = 6.dp),
                    ) {
                        CellRow(shelf) { app, isRowEnd -> Cell(app, isRowEnd) }
                    }
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        stringResource(R.string.grid_favorites_hint),
                        style = interStyle(11.sp, 500, Palette.TextMuted),
                        modifier = Modifier.padding(start = ROW_INSET.dp),
                    )
                }
            }
            itemsIndexed(cells.chunked(COLUMNS), key = { index, _ -> "row$index" }) { _, row ->
                CellRow(row, Modifier.padding(horizontal = ROW_INSET.dp)) { app, isRowEnd -> Cell(app, isRowEnd) }
            }
            if (visibleInputs.isNotEmpty()) {
                item(key = "inputs") {
                    Row(Modifier.padding(start = ROW_INSET.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(stringResource(R.string.section_inputs), style = interStyle(12.sp, 600, Color.White.copy(alpha = 0.55f)))
                        Spacer(Modifier.width(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleInputs.forEachIndexed { index, input ->
                                InputChip(input, enabled = liftedPackage == null, isLast = index == visibleInputs.lastIndex) {
                                    viewModel.openInput(input)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (liftedPackage != null) {
            MoveToolbar(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
        }
    }

    if (showAddDialog) {
        AddAppDialog(
            hiddenApps = hiddenApps,
            onAdd = { app ->
                viewModel.toggleHidden(app.packageName)
                showAddDialog = false
                requestFocus(app.packageName)
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** Always [COLUMNS] equal slots, so a short last row keeps the same tile size as full rows. */
@Composable
private fun CellRow(cells: List<LaunchableApp?>, modifier: Modifier = Modifier, cell: @Composable (LaunchableApp?, Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (index in 0 until COLUMNS) {
            Box(Modifier.weight(1f)) {
                if (index < cells.size) key(cells[index]?.packageName ?: "add") { cell(cells[index], index == cells.lastIndex) }
            }
        }
    }
}

@Composable
private fun GridTile(
    app: LaunchableApp,
    cardTheme: CardTheme,
    lifted: Boolean,
    isRowEnd: Boolean,
    dimmed: Boolean,
    focusRequest: GridFocusRequest,
    onFocusRequestHandled: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var swallowKeyUp by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest.packageName == app.packageName) {
            runCatching { focusRequester.requestFocus() }
            onFocusRequestHandled()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val active = focused || lifted
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scaleBy(focusScale(active, if (lifted) 1.14f else 1.1f))
                .shadow(if (active) 14.dp else 0.dp, shape)
                .clip(shape)
                .background(if (cardTheme == CardTheme.LIGHT) CardLight else CardDark)
                // Scale alone is too subtle from across the room, so focus also gets a solid outline.
                .border(if (active) 2.dp else 1.dp, if (active) Color.White else Color.White.copy(alpha = if (dimmed) 0.04f else 0.08f), shape)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                // A lifted tile still moves right into the next row; only plain focus is kept from escaping to the top bar.
                .blockRightExit(isRowEnd && !lifted)
                .onPreviewKeyEvent { event ->
                    when {
                        event.isCenter() && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                            if (!swallowKeyUp) onLongPress()
                            swallowKeyUp = true
                            true
                        }
                        event.isCenter() && event.type == KeyEventType.KeyUp && swallowKeyUp -> {
                            swallowKeyUp = false
                            true
                        }
                        lifted && event.type == KeyEventType.KeyDown -> when (event.key) {
                            Key.DirectionLeft -> { onMove(-1); true }
                            Key.DirectionRight -> { onMove(1); true }
                            Key.DirectionUp -> { onMove(-COLUMNS); true }
                            Key.DirectionDown -> { onMove(COLUMNS); true }
                            else -> false
                        }
                        else -> false
                    }
                }
                .semantics { contentDescription = app.label }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            CardArt(app, cardTheme)
        }
        Box(Modifier.height(22.dp).padding(top = 7.dp), contentAlignment = Alignment.Center) {
            if (active) {
                BasicText(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(11.sp, 600))
            }
        }
    }
}

@Composable
private fun AddTile(enabled: Boolean, isRowEnd: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scaleBy(focusScale(focused, 1.1f))
                .clip(shape)
                .background(Color.White.copy(alpha = if (focused) 0.14f else 0.05f))
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.16f), shape)
                .blockRightExit(isRowEnd)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(24.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                TintedIcon(R.drawable.ic_plus, 12.dp, Color.White)
            }
        }
        Box(Modifier.height(22.dp).padding(top = 7.dp), contentAlignment = Alignment.Center) {
            if (focused) BasicText(stringResource(R.string.add_app), maxLines = 1, style = interStyle(11.sp, 600))
        }
    }
}

@Composable
private fun MoveToolbar(modifier: Modifier) {
    Row(
        modifier
            .height(37.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xE6131317))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(19.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarHint("◀▶▲▼", stringResource(R.string.edit_hint_move))
        ToolbarDot()
        ToolbarHint(stringResource(R.string.key_ok), stringResource(R.string.edit_hint_drop))
        ToolbarDot()
        ToolbarHint(stringResource(R.string.key_back), stringResource(R.string.edit_hint_done))
    }
}
