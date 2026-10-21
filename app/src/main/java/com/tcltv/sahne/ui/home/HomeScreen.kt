package com.tcltv.sahne.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import com.tcltv.sahne.data.CardTheme
import com.tcltv.sahne.data.HomeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tcltv.sahne.data.AppCatalog
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.WeatherUiState
import com.tcltv.sahne.data.DevicePower
import com.tcltv.sahne.data.HdmiInput
import com.tcltv.sahne.data.LiveTvInput
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.data.TemperatureUnit
import com.tcltv.sahne.ui.AppGlyph
import com.tcltv.sahne.ui.Inter
import com.tcltv.sahne.ui.KeyCap
import com.tcltv.sahne.ui.GlassStyle
import com.tcltv.sahne.ui.LocalReduceMotion
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.TintedIcon
import com.tcltv.sahne.ui.focusSheen
import com.tcltv.sahne.ui.glass
import com.tcltv.sahne.ui.rememberFocusSheen
import com.tcltv.sahne.ui.interStyle
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SIDE = 48
private const val SYSTEM_SETTINGS_PACKAGE = "com.android.tv.settings"
val NeutralAccent = Color(0xFF34408A)

@Composable
fun HomeScreen(viewModel: LauncherViewModel, resumeTick: Int, editRequest: Int, onOpenSettings: () -> Unit) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val allApps by viewModel.appsForSettings.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val liveTv by viewModel.liveTvInputs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastPlayed by viewModel.lastPlayed.collectAsStateWithLifecycle()

    if (settings.homeLayout == HomeLayout.GRID) {
        GridHome(viewModel, apps, allApps, liveTv, inputs, settings, lastPlayed, resumeTick, onOpenSettings)
        return
    }

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var liftedPackage by remember { mutableStateOf<String?>(null) }
    var focusRequest by remember { mutableStateOf(CardFocusRequest(null, 0)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllApps by remember { mutableStateOf(false) }
    var glow by remember { mutableStateOf<FocusGlow?>(null) }

    val current = apps.firstOrNull { it.packageName == selectedPackage } ?: apps.firstOrNull()
    val accent = when {
        !settings.adaptiveBackground -> NeutralAccent
        current != null -> Color(current.accent)
        else -> NeutralAccent
    }
    LaunchedEffect(editRequest) {
        if (editRequest > 0) editMode = true
    }
    fun requestCardFocus(packageName: String?) {
        if (packageName != null) focusRequest = CardFocusRequest(packageName, focusRequest.nonce + 1)
    }
    LaunchedEffect(resumeTick, apps.isNotEmpty()) {
        requestCardFocus(current?.packageName)
    }
    BackHandler(enabled = editMode) {
        liftedPackage = null
        editMode = false
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackground(accent)
        val dim = Modifier.alpha(if (editMode) 0.18f else 1f)
        val currentLast = current?.let { lastPlayed[it.packageName] }
        if (current != null) {
            if (currentLast != null && !editMode) {
                val others = lastPlayed.values
                    .filter { it.packageName != current.packageName }
                    .sortedByDescending { it.updatedAt }
                    .mapNotNull { entry -> allApps.firstOrNull { it.packageName == entry.packageName }?.let { entry to it } }
                    .take(2)
                LastPlayedPanel(currentLast, current, others, viewModel, Modifier.align(Alignment.TopEnd).padding(end = SIDE.dp, top = 80.dp))
            } else {
                Watermark(current, Modifier.align(Alignment.TopEnd).padding(end = SIDE.dp, top = 110.dp).then(dim))
            }
        }

        TopBar(viewModel, onOpenSettings, Modifier.padding(start = SIDE.dp, end = SIDE.dp, top = 30.dp))

        if (current != null) {
            Hero(
                app = current,
                editMode = editMode,
                onOpen = { viewModel.launchApp(current) },
                onInfo = { viewModel.openAppInfo(current) },
                modifier = Modifier.padding(start = SIDE.dp, top = 129.dp).then(dim),
            )
        }

        if (editMode) {
            EditToolbar(Modifier.align(Alignment.TopCenter).padding(top = 235.dp))
        }

        // Drawn outside the row so the glow isn't clipped by the row's bounds.
        Canvas(Modifier.fillMaxSize()) {
            val g = glow ?: return@Canvas
            val radius = g.bounds.maxDimension * 0.85f
            // A card focused before the row scrolls it into view has empty clipped bounds, and a zero-radius
            // RadialGradient throws, which crashed the launcher on slower TVs.
            if (radius <= 0f) return@Canvas
            drawCircle(
                brush = Brush.radialGradient(listOf(g.color.copy(alpha = 0.5f), Color.Transparent), center = g.bounds.center, radius = radius),
                radius = radius,
                center = g.bounds.center,
            )
        }

        AppsRow(
            apps = apps,
            cardTheme = settings.cardTheme,
            // A card clears only its own glow: the newly focused card may report before the old one lets go.
            onGlow = { owner, g -> if (g != null) glow = g else if (glow?.owner == owner) glow = null },
            onShowAll = { showAllApps = true },
            selected = current,
            editMode = editMode,
            liftedPackage = liftedPackage,
            resumablePackages = lastPlayed.keys,
            focusRequest = focusRequest,
            onFocusRequestHandled = { focusRequest = focusRequest.copy(packageName = null) },
            onFocused = { selectedPackage = it.packageName },
            onClick = { app ->
                if (editMode) {
                    liftedPackage = if (liftedPackage == app.packageName) null else app.packageName
                } else {
                    viewModel.launchApp(app)
                }
            },
            // Entering edit mode leaves the card in place so Up reaches its remove button; OK then lifts it.
            onLongPress = { editMode = true },
            onMove = { app, delta ->
                viewModel.moveApp(app.packageName, delta)
                requestCardFocus(app.packageName)
            },
            onRemove = { app ->
                val index = apps.indexOfFirst { it.packageName == app.packageName }
                val neighbor = apps.getOrNull(index + 1) ?: apps.getOrNull(index - 1)
                if (liftedPackage == app.packageName) liftedPackage = null
                viewModel.toggleHidden(app.packageName)
                requestCardFocus(neighbor?.packageName)
            },
            onAdd = { showAddDialog = true },
            modifier = Modifier.padding(top = 338.dp),
        )

        val visibleInputs = if (settings.hideDisconnectedInputs) inputs.filter { it.connected } else inputs
        BottomRow(
            liveTv = if (settings.showInputs) liveTv else emptyList(),
            inputs = if (settings.showInputs) visibleInputs else emptyList(),
            enabled = !editMode,
            onOpenLiveTv = viewModel::openLiveTv,
            onOpenInput = viewModel::openInput,
            modifier = Modifier
                .padding(start = SIDE.dp, end = SIDE.dp, top = if (editMode) 492.dp else 479.dp)
                .alpha(if (editMode) 0.3f else 1f),
        )
    }

    if (showAllApps) {
        AllAppsDialog(
            apps = allApps,
            onOpen = { app ->
                showAllApps = false
                viewModel.launchApp(app)
            },
            onDismiss = { showAllApps = false },
        )
    }

    if (showAddDialog) {
        val hidden = allApps.filter { AppCatalog.isHidden(it, settings) }
        AddAppDialog(
            hiddenApps = hidden,
            onAdd = { app ->
                viewModel.toggleHidden(app.packageName)
                showAddDialog = false
                requestCardFocus(app.packageName)
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun Watermark(app: LaunchableApp, modifier: Modifier) {
    val reduceMotion = LocalReduceMotion.current
    Crossfade(
        targetState = app,
        animationSpec = if (reduceMotion) snap() else tween(450),
        label = "watermark",
        modifier = modifier,
    ) { shown ->
        val banner = shown.banner
        val icon = shown.icon
        val shape = RoundedCornerShape(14.dp)
        when {
            banner != null -> Image(
                banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = WATERMARK_ALPHA,
                modifier = Modifier.width(320.dp).height(180.dp).clip(shape),
            )
            icon != null -> Image(icon, contentDescription = null, alpha = WATERMARK_ALPHA, modifier = Modifier.size(150.dp).clip(shape))
        }
    }
}

private const val WATERMARK_ALPHA = 0.3f

@Composable
private fun Hero(
    app: LaunchableApp,
    editMode: Boolean,
    onOpen: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    Column(modifier) {
        AnimatedContent(
            targetState = app,
            transitionSpec = {
                if (reduceMotion) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 6 }) togetherWith fadeOut(tween(160))
            },
            label = "hero",
        ) { shown ->
            Column {
                BasicText(
                    shown.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = 72.sp,
                        lineHeight = 74.sp,
                        fontWeight = FontWeight(800),
                        letterSpacing = (-2.6).sp,
                        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFBDBDC6))),
                    ),
                    modifier = Modifier.width(520.dp),
                )
                BasicText(
                    when {
                        editMode -> stringResource(R.string.edit_apps_subtitle)
                        else -> stringResource(shown.tagline)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(520.dp),
                    style = interStyle(17.sp, 500, Color.White.copy(alpha = 0.86f)),
                )
            }
        }
        if (!editMode) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OpenButton(onOpen)
                InfoButton(onInfo)
            }
        }
    }
}


@Composable
internal fun focusScale(focused: Boolean, target: Float): State<Float> {
    val reduceMotion = LocalReduceMotion.current
    return animateFloatAsState(
        targetValue = if (focused) target else 1f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "focusScale",
    )
}

/** Reads the animated scale while drawing, so a focus spring redraws the card instead of recomposing it every frame. */
internal fun Modifier.scaleBy(scale: State<Float>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

@Composable
private fun OpenButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .scaleBy(focusScale(focused, 1.06f))
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White)
            .border(2.dp, if (focused) Palette.Amber else Color.Transparent, RoundedCornerShape(17.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 6.dp, end = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).background(Palette.Background, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(stringResource(R.string.key_ok), style = interStyle(9.5.sp, 800))
        }
        Spacer(Modifier.width(9.dp))
        BasicText(stringResource(R.string.open_with_ok_suffix), style = interStyle(14.sp, 600, Palette.Background))
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .scaleBy(focusScale(focused, 1.06f))
            .height(34.dp)
            .glass(CircleShape, if (focused) GlassStyle.Lit else GlassStyle.Pill)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIcon(R.drawable.ic_info_circle, 13.dp, Palette.TextPrimary)
        Spacer(Modifier.width(8.dp))
        BasicText(stringResource(R.string.app_info), style = interStyle(14.sp, 500))
    }
}

@Composable
private fun EditToolbar(modifier: Modifier) {
    Row(
        modifier
            .height(37.dp)
            .glass(CircleShape, GlassStyle.Dense)
            .padding(start = 7.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.height(25.dp).clip(RoundedCornerShape(13.dp)).background(Color.White).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintedIcon(R.drawable.ic_pencil, 12.dp, Palette.Background)
            Spacer(Modifier.width(7.dp))
            BasicText(stringResource(R.string.edit_mode), style = interStyle(12.5.sp, 700, Palette.Background))
        }
        Spacer(Modifier.width(12.dp))
        ToolbarHint("◀▶", stringResource(R.string.edit_hint_move))
        ToolbarDot()
        ToolbarHint(stringResource(R.string.key_ok), stringResource(R.string.edit_hint_drop))
        ToolbarDot()
        ToolbarHint("▲", stringResource(R.string.edit_hint_remove))
        ToolbarDot()
        ToolbarHint(stringResource(R.string.key_back), stringResource(R.string.edit_hint_done))
    }
}

@Composable
internal fun ToolbarHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KeyCap(key)
        Spacer(Modifier.width(6.dp))
        BasicText(label, style = interStyle(11.5.sp, 500, Color.White.copy(alpha = 0.85f)))
    }
}

@Composable
internal fun ToolbarDot() {
    Box(Modifier.padding(horizontal = 11.dp).size(3.dp).background(Color.White.copy(alpha = 0.35f), CircleShape))
}

@Composable
private fun AppsRow(
    apps: List<LaunchableApp>,
    cardTheme: CardTheme,
    onGlow: (String, FocusGlow?) -> Unit,
    onShowAll: () -> Unit,
    selected: LaunchableApp?,
    editMode: Boolean,
    liftedPackage: String?,
    resumablePackages: Set<String>,
    focusRequest: CardFocusRequest,
    onFocusRequestHandled: () -> Unit,
    onFocused: (LaunchableApp) -> Unit,
    onClick: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    onMove: (LaunchableApp, Int) -> Unit,
    onRemove: (LaunchableApp) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(Modifier.padding(start = SIDE.dp), verticalAlignment = Alignment.Bottom) {
            BasicText(stringResource(R.string.section_apps), style = interStyle(12.sp, 600, Color.White.copy(alpha = 0.62f)))
            Spacer(Modifier.width(7.dp))
            if (editMode) {
                BasicText(stringResource(R.string.edit_row_hint), style = interStyle(11.sp, 500, Color.White.copy(alpha = 0.5f)))
            } else {
                BasicText(apps.size.toString(), style = interStyle(11.sp, 600, Color.White.copy(alpha = 0.5f)))
                Spacer(Modifier.width(10.dp))
                AllAppsChip(onShowAll)
            }
        }
        LazyRow(
            // Extra top room in edit mode so a lifted, scaled card clears the row label.
            contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = if (editMode) 18.dp else 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppCard(
                    app = app,
                    index = index,
                    cardTheme = cardTheme,
                    onGlow = onGlow,
                    isSelected = app.packageName == selected?.packageName,
                    editMode = editMode,
                    lifted = app.packageName == liftedPackage,
                    resumable = app.packageName in resumablePackages,
                    removable = app.packageName != SYSTEM_SETTINGS_PACKAGE,
                    // In edit mode the add card comes after the last app and takes Right instead.
                    isLastInRow = !editMode && index == apps.lastIndex,
                    focusRequest = focusRequest,
                    onFocusRequestHandled = onFocusRequestHandled,
                    onFocused = { onFocused(app) },
                    onClick = { onClick(app) },
                    onLongPress = { onLongPress(app) },
                    onMove = { onMove(app, it) },
                    onRemove = { onRemove(app) },
                )
            }
            if (editMode) {
                item(key = "add") { AddCard(onAdd) }
            }
        }
    }
}

internal fun KeyEvent.isCenter() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/** Right on a row's last item would otherwise send focus to the nearest control further right, such as the top bar. */
internal fun Modifier.blockRightExit(block: Boolean) =
    if (block) onPreviewKeyEvent { it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown } else this

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCard(
    app: LaunchableApp,
    index: Int,
    cardTheme: CardTheme,
    onGlow: (String, FocusGlow?) -> Unit,
    isSelected: Boolean,
    editMode: Boolean,
    lifted: Boolean,
    resumable: Boolean,
    removable: Boolean,
    isLastInRow: Boolean,
    focusRequest: CardFocusRequest,
    onFocusRequestHandled: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var swallowKeyUp by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }
    val showRemove = editMode && removable && !lifted
    val bringIntoView = remember { BringIntoViewRequester() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val arrowRoomPx = with(LocalDensity.current) { 56.dp.toPx() }
    // Moving a lifted card doesn't move focus, so the row has to be scrolled explicitly to follow it.
    LaunchedEffect(lifted, index) {
        if (lifted && cardSize != IntSize.Zero) {
            bringIntoView.bringIntoView(Rect(-arrowRoomPx, 0f, cardSize.width + arrowRoomPx, cardSize.height.toFloat()))
        }
    }
    // Requested from inside the card so the focus target is attached when the request runs.
    LaunchedEffect(focusRequest) {
        if (focusRequest.packageName == app.packageName) {
            runCatching { focusRequester.requestFocus() }
            // Consumed once, so a card re-entering composition while scrolling can't steal focus again.
            onFocusRequestHandled()
        }
    }
    val reduceMotion = LocalReduceMotion.current
    val accent = Color(app.accent)
    val shape = RoundedCornerShape(15.dp)
    val captionAlpha = animateFloatAsState(if (focused) 1f else 0f, tween(if (reduceMotion) 0 else 200), label = "caption")
    // Kept as State and read in graphicsLayer, so these springs redraw the card without recomposing it.
    val tilt = animateFloatAsState(
        targetValue = if (editMode && !lifted) (if (index % 2 == 0) -1.4f else 1.1f) else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.55f),
        label = "tilt",
    )
    val lift = animateDpAsState(if (lifted) (-10).dp else 0.dp, if (reduceMotion) snap() else spring(dampingRatio = 0.6f), label = "lift")
    val sheen = rememberFocusSheen(focused || lifted)

    Column(
        Modifier
            .zIndex(if (lifted) 1f else 0f)
            .bringIntoViewRequester(bringIntoView)
            .onSizeChanged { cardSize = it },
    ) {
        Box {
            if (lifted) DashedSlot(Modifier.matchParentSize().padding(4.dp))
            Box(
                Modifier
                    .onGloballyPositioned {
                        coordinates = it
                        if (focused || lifted) onGlow(app.packageName, FocusGlow(app.packageName, it.boundsInRoot(), accent))
                    }
                    .graphicsLayer {
                        rotationZ = tilt.value
                        translationY = lift.value.toPx()
                    }
                    .scaleBy(focusScale(focused || lifted, 1.17f))
                    .padding(4.dp)
                    // Up from a card in edit mode lands on its own remove button, independent of layout geometry.
                    .focusProperties { if (showRemove) up = removeFocus }
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) {
                            onFocused()
                            coordinates?.takeIf { c -> c.isAttached }?.let { c -> onGlow(app.packageName, FocusGlow(app.packageName, c.boundsInRoot(), accent)) }
                        } else if (!lifted) {
                            onGlow(app.packageName, null)
                        }
                    }
                    .blockRightExit(isLastInRow && !lifted)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.isCenter() && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                                if (!editMode && !swallowKeyUp) onLongPress()
                                swallowKeyUp = true
                                true
                            }
                            event.isCenter() && event.type == KeyEventType.KeyUp && swallowKeyUp -> {
                                swallowKeyUp = false
                                true
                            }
                            lifted && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                onMove(-1)
                                true
                            }
                            lifted && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                onMove(1)
                                true
                            }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = app.label }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            ) {
                Box(
                    Modifier
                        .width(123.dp)
                        .height(75.dp)
                        .clip(shape)
                        .background(if (cardTheme == CardTheme.LIGHT) CardLight else CardDark)
                        // Scale alone is too subtle from across the room, so focus also lights a bright rim and a sheen.
                        .focusSheen(sheen, shape)
                        .then(if (focused || lifted) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = if (cardTheme == CardTheme.LIGHT) 0.14f else 0.1f), shape)),
                    contentAlignment = Alignment.Center,
                ) {
                    CardArt(app, cardTheme)
                }
            }
            if (showRemove) {
                RemoveButton(
                    appLabel = app.label,
                    focusRequester = removeFocus,
                    cardFocus = focusRequester,
                    onRemove = onRemove,
                    modifier = Modifier.offset(x = (-7).dp, y = (-7).dp).graphicsLayer { rotationZ = tilt.value },
                )
            }
            if (lifted) {
                MoveArrow(R.drawable.ic_chevron_left, Modifier.align(Alignment.CenterStart).offset { IntOffset((-40).dp.roundToPx(), lift.value.roundToPx()) })
                MoveArrow(R.drawable.ic_chevron_right, Modifier.align(Alignment.CenterEnd).offset { IntOffset(40.dp.roundToPx(), lift.value.roundToPx()) })
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(
            Modifier.overflowWithoutWidth().graphicsLayer { alpha = if (isSelected || lifted) captionAlpha.value else 0f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(app.label, style = interStyle(11.sp, 700, if (editMode) Palette.TextPrimary else lerp(accent, Color.White, 0.45f)))
            BasicText("  ·  ", style = interStyle(11.sp, 500, Palette.TextMuted))
            when {
                lifted -> BasicText(stringResource(R.string.edit_drop_at, index + 1), style = interStyle(11.sp, 500, Palette.TextSecondary))
                editMode -> {
                    KeyCap(stringResource(R.string.key_ok))
                    Spacer(Modifier.width(5.dp))
                    BasicText(stringResource(R.string.edit_pick_up), style = interStyle(11.sp, 500, Palette.TextSecondary))
                }
                else -> {
                    KeyCap(stringResource(R.string.key_ok))
                    Spacer(Modifier.width(5.dp))
                    BasicText(
                        stringResource(if (resumable) R.string.continue_with_ok_suffix else R.string.open_with_ok_suffix),
                        style = interStyle(11.sp, 500, Palette.TextSecondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashedSlot(modifier: Modifier) {
    Canvas(modifier) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            cornerRadius = CornerRadius(13.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

@Composable
private fun RemoveButton(
    appLabel: String,
    focusRequester: FocusRequester,
    cardFocus: FocusRequester,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val description = stringResource(R.string.a11y_remove_app, appLabel)
    Box(
        modifier
            .scaleBy(focusScale(focused, 1.25f))
            .size(22.dp)
            .background(if (focused) Color.White else Color(0xFF2A2A30), CircleShape)
            .border(1.5.dp, if (focused) Color.White else Color.White.copy(alpha = 0.6f), CircleShape)
            .focusProperties { down = cardFocus }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        TintedIcon(R.drawable.ic_x, 12.dp, if (focused) Palette.Background else Color.White)
    }
}

@Composable
private fun MoveArrow(icon: Int, modifier: Modifier) {
    Box(modifier.size(26.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        TintedIcon(icon, 13.dp, Palette.Background)
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Box(
            Modifier
                .scaleBy(focusScale(focused, 1.1f))
                .padding(4.dp)
                .width(123.dp)
                .height(75.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (focused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                .blockRightExit(true)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            DashedSlot(Modifier.matchParentSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(22.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    TintedIcon(R.drawable.ic_plus, 12.dp, Color.White)
                }
                Spacer(Modifier.height(7.dp))
                BasicText(stringResource(R.string.add_app), style = interStyle(11.5.sp, 600, Color.White.copy(alpha = 0.85f)))
            }
        }
    }
}

@Composable
internal fun AddAppDialog(hiddenApps: List<LaunchableApp>, onAdd: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(hiddenApps.isNotEmpty()) { runCatching { first.requestFocus() } }
        Column(
            Modifier
                .width(380.dp)
                .glass(RoundedCornerShape(28.dp), GlassStyle.Dense)
                .padding(22.dp),
        ) {
            BasicText(stringResource(R.string.add_app), style = interStyle(18.sp, 700))
            Spacer(Modifier.height(12.dp))
            if (hiddenApps.isEmpty()) {
                BasicText(stringResource(R.string.no_hidden_apps), style = interStyle(13.sp, 500, Palette.TextSecondary))
            }
            // Lazy and height-capped: many hidden system components would otherwise run off the screen.
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
              itemsIndexed(hiddenApps, key = { _, app -> app.packageName }) { index, app ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) Color.White else Color.Transparent)
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAdd(app) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).background(Color(app.accent), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { AppGlyph(app, 16.dp, Color.White) }
                    Spacer(Modifier.width(12.dp))
                    BasicText(app.label, style = interStyle(14.sp, 600, if (focused) Palette.Background else Palette.TextPrimary))
                }
              }
            }
        }
    }
}

@Composable
private fun BottomRow(
    liveTv: List<LiveTvInput>,
    inputs: List<HdmiInput>,
    enabled: Boolean,
    onOpenLiveTv: (LiveTvInput) -> Unit,
    onOpenInput: (HdmiInput) -> Unit,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth().height(29.dp), verticalAlignment = Alignment.CenterVertically) {
        if (liveTv.isNotEmpty() || inputs.isNotEmpty()) {
            BasicText(stringResource(R.string.section_inputs), style = interStyle(12.sp, 600, Color.White.copy(alpha = 0.55f)))
            Spacer(Modifier.width(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                liveTv.forEachIndexed { index, input ->
                    LiveTvChip(input, showLabel = liveTv.size > 1, enabled, isLast = inputs.isEmpty() && index == liveTv.lastIndex) {
                        onOpenLiveTv(input)
                    }
                }
                inputs.forEachIndexed { index, input ->
                    InputChip(input, enabled, isLast = index == inputs.lastIndex) { onOpenInput(input) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        KeyCap("◀▶")
        Spacer(Modifier.width(5.dp))
        BasicText(stringResource(R.string.hint_navigate), style = interStyle(11.sp, 500, Palette.TextMuted))
        Spacer(Modifier.width(12.dp))
        KeyCap(stringResource(R.string.key_ok))
        Spacer(Modifier.width(5.dp))
        BasicText(stringResource(R.string.hint_open), style = interStyle(11.sp, 500, Palette.TextMuted))
    }
}

@Composable
internal fun InputChip(input: HdmiInput, enabled: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val powerText = input.power?.let { stringResource(if (it == DevicePower.ON) R.string.input_power_on else R.string.input_power_standby) }
    SourceChip(
        icon = input.logo.icon,
        title = input.chipTitle,
        detail = input.chipDevice,
        power = input.power,
        description = listOfNotNull(input.chipTitle, input.chipDevice, input.portLabel, powerText).distinct().joinToString(", "),
        enabled = enabled,
        isLast = isLast,
        onClick = onClick,
    )
}

/** Opens the TV's own live broadcast on its built-in tuner, like the stock launcher's Live TV button. */
@Composable
internal fun LiveTvChip(input: LiveTvInput, showLabel: Boolean, enabled: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val title = stringResource(R.string.live_tv)
    // A TV with several tuners (antenna and satellite, say) needs the TV's own name to tell them apart.
    val detail = input.label.takeIf { showLabel && it.isNotBlank() }
    SourceChip(
        icon = R.drawable.ic_antenna,
        title = title,
        detail = detail,
        power = null,
        description = listOfNotNull(title, detail).joinToString(", "),
        enabled = enabled,
        isLast = isLast,
        onClick = onClick,
    )
}

@Composable
private fun SourceChip(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    power: DevicePower?,
    description: String,
    enabled: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val content = if (focused) Palette.Background else Palette.TextPrimary
    Row(
        Modifier
            .scaleBy(focusScale(focused, 1.08f))
            .height(29.dp)
            .glass(CircleShape, if (focused) GlassStyle.Bright else GlassStyle.Pill)
            .focusProperties { canFocus = enabled }
            .blockRightExit(isLast)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 7.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(17.dp).background(content.copy(alpha = 0.1f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TintedIcon(icon, 11.dp, content)
        }
        Spacer(Modifier.width(8.dp))
        BasicText(title, maxLines = 1, style = interStyle(12.sp, 600, content))
        detail?.let {
            BasicText(" · $it", maxLines = 1, style = interStyle(12.sp, 500, content.copy(alpha = 0.6f)))
        }
        power?.let {
            Spacer(Modifier.width(7.dp))
            Box(Modifier.size(6.dp).background(if (it == DevicePower.ON) Palette.Online else Palette.Warning, CircleShape))
        }
    }
}

/** A one-shot request to move focus to an app card; the nonce makes repeated requests for the same card distinct. */
private data class CardFocusRequest(val packageName: String?, val nonce: Int)

/** Lets a caption extend past its card without widening the row item, which left blank gaps after long names. */
private fun Modifier.overflowWithoutWidth() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    layout(0, placeable.height) { placeable.placeRelative(0, 0) }
}

internal val CardDark = Color(0xFF1B1B20)
internal val CardLight = Color(0xFFF5F5F7)

/** Where the focused card is, so its glow can be drawn behind the row instead of inside it. */
private data class FocusGlow(val owner: String, val bounds: Rect, val color: Color)

/** The app's own logo and name on a plain tile, so every card reads the same in either theme. */
@Composable
internal fun BoxScope.CardArt(app: LaunchableApp, theme: CardTheme) {
    val banner = app.banner
    // Light cards show the app's own TV banner edge to edge; dark cards put its logo and name on a dark tile.
    if (theme == CardTheme.LIGHT && banner != null) {
        Image(banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        return
    }
    val text = if (theme == CardTheme.LIGHT) Color(0xFF16161A) else Color.White
    // A white logo vanishes on a light tile and a black one on a dark tile; fall back to the app's full icon then.
    val lightness = app.glyphLightness
    val blends = lightness != null && if (theme == CardTheme.LIGHT) lightness > 0.85f else lightness < 0.15f
    val image = if (blends) app.icon else app.glyph
    Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))) }
        Spacer(Modifier.width(7.dp))
        FittedLabel(app.label, text, Modifier.weight(1f, fill = false))
    }
}

/** Shrinks the name until it fits in two lines, so long app names are never cut off with an ellipsis. */
@Composable
private fun FittedLabel(text: String, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val heightPx = with(LocalDensity.current) { 34.dp.roundToPx() }
        val style = remember(text, widthPx, color) {
            LABEL_SIZES.asSequence()
                .map { size -> interStyle(size.sp, 800, color).copy(lineHeight = (size + 1.5f).sp) }
                .firstOrNull { candidate ->
                    val result = measurer.measure(text, candidate, maxLines = 2, constraints = Constraints(maxWidth = widthPx, maxHeight = heightPx))
                    // Each word must fit on its own line too, or the layout breaks it mid-word ("CloudStre / am").
                    val wordsFit = text.split(' ').all { word -> measurer.measure(word, candidate, softWrap = false, maxLines = 1).size.width <= widthPx }
                    !result.hasVisualOverflow && wordsFit
                } ?: interStyle(LABEL_SIZES.last().sp, 800, color).copy(lineHeight = (LABEL_SIZES.last() + 1.5f).sp)
        }
        BasicText(text, maxLines = 2, style = style)
    }
}

private val LABEL_SIZES = listOf(13.5f, 12.5f, 11.5f, 10.5f, 9.5f)

@Composable
private fun AllAppsChip(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.08f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (focused) Palette.Background else Palette.TextPrimary
        BasicText(stringResource(R.string.all_apps), style = interStyle(11.sp, 600, color))
        TintedIcon(R.drawable.ic_chevron_right, 11.dp, color)
    }
}

@Composable
private fun AllAppsDialog(apps: List<LaunchableApp>, onOpen: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(apps.isNotEmpty()) { runCatching { first.requestFocus() } }
        Column(
            Modifier
                .width(640.dp)
                .glass(RoundedCornerShape(28.dp), GlassStyle.Dense)
                .padding(22.dp),
        ) {
            BasicText(stringResource(R.string.all_apps_title, apps.size), style = interStyle(18.sp, 700))
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 380.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(app) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.glyph?.let { Image(it, contentDescription = null, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(8.dp))
                        BasicText(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(12.sp, 600, if (focused) Palette.Background else Palette.TextPrimary))
                    }
                }
            }
        }
    }
}
