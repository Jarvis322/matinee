package com.tcltv.sahne.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.tcltv.sahne.data.Folder
import com.tcltv.sahne.data.LaunchableApp
import com.tcltv.sahne.ui.GlassStyle
import com.tcltv.sahne.ui.ImagePicker
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.PinSetup
import com.tcltv.sahne.ui.TextInput
import com.tcltv.sahne.ui.focusSheen
import com.tcltv.sahne.ui.glass
import com.tcltv.sahne.ui.interStyle
import com.tcltv.sahne.ui.ignoreHeldOk
import com.tcltv.sahne.ui.longPressClickable
import com.tcltv.sahne.ui.rememberFocusSheen

/** The card the long-press menu is open for, and whether it was reached from inside a folder. */
data class MenuTarget(val app: LaunchableApp, val inFolder: Boolean)

/** Shared by both home layouts: which card has its menu open, and a card the menu asked them to start moving. */
class AppMenuState {
    var target by mutableStateOf<MenuTarget?>(null)
    var moveRequest by mutableStateOf<String?>(null)
}

@Composable
fun rememberAppMenuState() = remember { AppMenuState() }

private sealed interface Prompt {
    data class Rename(val app: LaunchableApp) : Prompt
    data class NewFolder(val app: LaunchableApp) : Prompt
}

/**
 * Everything that hangs off a long press on a card: the menu itself, the name prompts, setting a PIN when an app is
 * locked for the first time, and the open folder's own view.
 */
@Composable
fun AppMenuHost(viewModel: LauncherViewModel, state: AppMenuState, cardTheme: com.tcltv.sahne.data.CardTheme) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val folderApps by viewModel.openFolderApps.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf<Prompt?>(null) }
    var pickFolder by remember { mutableStateOf(false) }
    var lockAfterPin by remember { mutableStateOf<LaunchableApp?>(null) }
    var pickIconFor by remember { mutableStateOf<LaunchableApp?>(null) }

    openFolderId?.let { id ->
        val folder = settings.folders.firstOrNull { it.id == id }
        FolderView(
            title = folder?.name?.ifBlank { null } ?: stringResource(R.string.folder_default_name),
            apps = folderApps,
            cardTheme = cardTheme,
            onClose = viewModel::closeFolder,
            onLaunch = { app ->
                viewModel.closeFolder()
                viewModel.launchApp(app)
            },
            onMenu = { app -> viewModel.gate.requireForAdmin(R.string.pin_reason_admin) { state.target = MenuTarget(app, inFolder = true) } },
        )
    }

    state.target?.let { target ->
        val app = target.app
        val dismiss = {
            state.target = null
            pickFolder = false
        }
        if (pickFolder) {
            FolderPicker(
                folders = settings.folders,
                onPick = { folder ->
                    viewModel.addToFolder(folder.id, app.packageName)
                    dismiss()
                },
                onNew = {
                    prompt = Prompt.NewFolder(app)
                    dismiss()
                },
                onBack = { pickFolder = false },
            )
        } else {
            AppMenu(
                app = app,
                inFolder = target.inFolder,
                hasFolders = settings.folders.isNotEmpty(),
                onDismiss = dismiss,
                onOpen = {
                    dismiss()
                    viewModel.launchApp(app)
                },
                onMove = {
                    dismiss()
                    viewModel.closeFolder()
                    state.moveRequest = app.packageName
                },
                onRename = {
                    prompt = Prompt.Rename(app)
                    dismiss()
                },
                hasCustomName = settings.appOverrides[app.packageName]?.label != null,
                onResetName = {
                    viewModel.renameApp(app.packageName, "")
                    dismiss()
                },
                hasCustomIcon = settings.appOverrides[app.packageName]?.icon != null,
                onChangeIcon = {
                    pickIconFor = app
                    dismiss()
                },
                onResetIcon = {
                    viewModel.resetAppIcon(app.packageName)
                    dismiss()
                },
                onAddToFolder = {
                    if (settings.folders.isEmpty()) {
                        prompt = Prompt.NewFolder(app)
                        dismiss()
                    } else {
                        pickFolder = true
                    }
                },
                onRemoveFromFolder = {
                    viewModel.removeFromFolder(app.packageName)
                    dismiss()
                },
                onToggleLock = {
                    dismiss()
                    when {
                        app.isLocked -> viewModel.setAppLocked(app.packageName, false)
                        viewModel.gate.hasPin -> viewModel.setAppLocked(app.packageName, true)
                        else -> lockAfterPin = app
                    }
                },
                onHide = {
                    viewModel.toggleHidden(app.packageName)
                    dismiss()
                },
                onInfo = {
                    viewModel.openAppInfo(app)
                    dismiss()
                },
                onDeleteFolder = {
                    app.folderId?.let(viewModel::deleteFolder)
                    dismiss()
                },
            )
        }
    }

    prompt?.let { current ->
        when (current) {
            is Prompt.Rename -> TextPrompt(
                title = stringResource(R.string.prompt_rename_title, current.app.label),
                initial = current.app.label,
                onConfirm = {
                    viewModel.renameApp(current.app.packageName, it)
                    prompt = null
                },
                onDismiss = { prompt = null },
            )
            is Prompt.NewFolder -> TextPrompt(
                title = stringResource(R.string.prompt_new_folder_title),
                initial = "",
                onConfirm = {
                    viewModel.createFolder(current.app.packageName, it.trim().ifBlank { "" })
                    prompt = null
                },
                onDismiss = { prompt = null },
            )
        }
    }

    pickIconFor?.let { app ->
        ImagePicker(
            onPicked = { image ->
                viewModel.importAppIcon(app.packageName, image)
                pickIconFor = null
            },
            onDismiss = { pickIconFor = null },
        )
    }

    // Locking needs a PIN to mean anything, so the first lock asks the parent to choose one.
    lockAfterPin?.let { app ->
        PinSetup(
            title = stringResource(R.string.pin_new_title),
            onSet = { pin, kind ->
                viewModel.setPin(pin, kind)
                viewModel.setAppLocked(app.packageName, true)
                lockAfterPin = null
            },
            onCancel = { lockAfterPin = null },
        )
    }
}

@Composable
private fun AppMenu(
    app: LaunchableApp,
    inFolder: Boolean,
    hasFolders: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    hasCustomName: Boolean,
    onResetName: () -> Unit,
    hasCustomIcon: Boolean,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onAddToFolder: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onToggleLock: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onDeleteFolder: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val isFolder = app.folderId != null
    MenuDialog(title = app.label, onDismiss = onDismiss) {
        MenuRow(stringResource(R.string.menu_open), Modifier.focusRequester(first), onOpen)
        if (!inFolder) MenuRow(stringResource(R.string.menu_move), onClick = onMove)
        MenuRow(stringResource(if (isFolder) R.string.menu_rename_folder else R.string.menu_rename), onClick = onRename)
        // A renamed app shows its icon and the new name instead of its own banner; this brings both back.
        if (!isFolder && hasCustomName) MenuRow(stringResource(R.string.menu_reset_name), onClick = onResetName)
        if (isFolder) {
            MenuRow(stringResource(R.string.menu_delete_folder), onClick = onDeleteFolder)
        } else {
            MenuRow(stringResource(R.string.menu_change_picture), onClick = onChangeIcon)
            if (hasCustomIcon) MenuRow(stringResource(R.string.menu_reset_picture), onClick = onResetIcon)
            if (inFolder) {
                MenuRow(stringResource(R.string.menu_remove_from_folder), onClick = onRemoveFromFolder)
            } else {
                MenuRow(stringResource(if (hasFolders) R.string.menu_add_to_folder else R.string.menu_new_folder), onClick = onAddToFolder)
            }
            MenuRow(stringResource(if (app.isLocked) R.string.menu_unlock else R.string.menu_lock), onClick = onToggleLock)
            if (!inFolder) MenuRow(stringResource(R.string.menu_hide), onClick = onHide)
            MenuRow(stringResource(R.string.app_info), onClick = onInfo)
        }
    }
}

@Composable
private fun FolderPicker(folders: List<Folder>, onPick: (Folder) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    MenuDialog(title = stringResource(R.string.menu_add_to_folder), onDismiss = onBack) {
        folders.forEachIndexed { index, folder ->
            MenuRow(
                folder.name.ifBlank { stringResource(R.string.folder_default_name) },
                if (index == 0) Modifier.focusRequester(first) else Modifier,
            ) { onPick(folder) }
        }
        MenuRow(stringResource(R.string.menu_new_folder), onClick = onNew)
    }
}

@Composable
private fun MenuDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(300.dp)
                .ignoreHeldOk()
                .glass(RoundedCornerShape(28.dp), GlassStyle.Dense)
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            BasicText(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = interStyle(16.sp, 700),
                modifier = Modifier.padding(start = 10.dp, bottom = 10.dp, end = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun MenuRow(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(if (focused) Modifier.glass(CircleShape, GlassStyle.Bright) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = interStyle(14.sp, 600, if (focused) Palette.Background else Palette.TextPrimary),
        )
    }
}

/** A name entry on a panel of its own: the keyboard's check mark moves on to OK. */
@Composable
private fun TextPrompt(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(380.dp)
                .ignoreHeldOk()
                .glass(RoundedCornerShape(28.dp), GlassStyle.Dense)
                .padding(22.dp),
        ) {
            BasicText(title, maxLines = 2, style = interStyle(17.sp, 700))
            Spacer(Modifier.padding(top = 14.dp))
            // Down, and the keyboard's check mark, land on OK rather than on whichever button the layout favours.
            TextInput(value = text, placeholder = "", modifier = Modifier.focusRequester(field).focusProperties { down = ok }) { text = it }
            Spacer(Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PromptButton(stringResource(R.string.key_ok), Modifier.focusRequester(ok)) { onConfirm(text) }
                PromptButton(stringResource(R.string.cancel), Modifier, onDismiss)
            }
        }
    }
}

@Composable
private fun PromptButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .heightIn(min = 38.dp)
            .glass(CircleShape, if (focused) GlassStyle.Bright else GlassStyle.Inset)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = interStyle(14.sp, 600, if (focused) Palette.Background else Palette.TextPrimary))
    }
}

/** The apps inside a folder as a grid of cards; OK opens one, a long press opens its menu. */
@Composable
private fun FolderView(
    title: String,
    apps: List<LaunchableApp>,
    cardTheme: com.tcltv.sahne.data.CardTheme,
    onClose: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(620.dp)
                .ignoreHeldOk()
                .glass(RoundedCornerShape(32.dp), GlassStyle.Dense)
                .padding(24.dp),
        ) {
            BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(20.sp, 800))
            Spacer(Modifier.padding(top = 16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    FolderTile(app, cardTheme, if (index == 0) Modifier.focusRequester(first) else Modifier, onClick = { onLaunch(app) }, onLongPress = { onMenu(app) })
                }
            }
        }
    }
}

@Composable
private fun FolderTile(app: LaunchableApp, cardTheme: com.tcltv.sahne.data.CardTheme, modifier: Modifier, onClick: () -> Unit, onLongPress: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val sheen = rememberFocusSheen(focused)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .scaleBy(focusScale(focused, 1.08f))
                .shadow(if (focused) 14.dp else 0.dp, shape)
                .clip(shape)
                .background(if (cardTheme == com.tcltv.sahne.data.CardTheme.LIGHT) CardLight else CardDark)
                .focusSheen(sheen, shape)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = app.label }
                .longPressClickable(onClick = onClick, onLongPress = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            CardArt(app, cardTheme)
        }
        Box(Modifier.heightIn(min = 22.dp).padding(top = 6.dp), contentAlignment = Alignment.Center) {
            if (focused) BasicText(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = interStyle(11.sp, 600))
        }
    }
}
