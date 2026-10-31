package com.tcltv.sahne.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.AdbSetupState
import com.tcltv.sahne.LauncherViewModel
import com.tcltv.sahne.R
import com.tcltv.sahne.data.KeyAction
import com.tcltv.sahne.data.KeyBinding
import com.tcltv.sahne.data.PressType
import com.tcltv.sahne.system.HomeKeyService
import com.tcltv.sahne.system.LocalAdb
import com.tcltv.sahne.ui.GlassStyle
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.glass
import com.tcltv.sahne.ui.interStyle

/** The remote's rows under Bluetooth and remote: the Home button service the keys depend on, Automatic ADB, the keys. */
@Composable
internal fun RemoteRows(
    viewModel: LauncherViewModel,
    keyRowModifier: @Composable (Int) -> Modifier,
    onEditKey: (Int) -> Unit,
) {
    val bindings by viewModel.keyBindings.collectAsStateWithLifecycle()
    val adb by viewModel.adbSetup.collectAsStateWithLifecycle()
    var settingUpAdb by remember { mutableStateOf(false) }
    var learning by remember { mutableStateOf(false) }
    HomeKeyRow(viewModel)
    ValueRow(
        R.string.setting_auto_adb,
        R.string.setting_auto_adb_sub,
        stringResource(if (adb == AdbSetupState.Ready) R.string.switch_on else R.string.switch_off),
    ) { settingUpAdb = true }
    ValueRow(R.string.setting_key_add, R.string.setting_key_add_sub, null) { learning = true }
    bindings.sortedBy { it.keyCode }.forEach { binding ->
        ValueRow(
            keyName(binding.keyCode),
            bindingSummary(viewModel, binding),
            null,
            keyRowModifier(binding.keyCode),
        ) { onEditKey(binding.keyCode) }
    }
    Note(R.string.note_remote_keys)
    if (settingUpAdb) AdbSetupDialog(viewModel) { settingUpAdb = false }
    if (learning) {
        LearnKeyDialog(
            viewModel = viewModel,
            onKey = { keyCode ->
                learning = false
                onEditKey(keyCode)
            },
            onDismiss = { learning = false },
        )
    }
}

/** One assigned key: what each kind of press does, and removing it. */
@Composable
internal fun KeyEditorPage(viewModel: LauncherViewModel, keyCode: Int, firstRow: FocusRequester, onDone: () -> Unit) {
    val bindings by viewModel.keyBindings.collectAsStateWithLifecycle()
    val actions = bindings.firstOrNull { it.keyCode == keyCode }?.actions.orEmpty()
    var choosing by remember { mutableStateOf<PressType?>(null) }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }
    PanelList {
        PressType.entries.forEachIndexed { index, press ->
            ValueRow(
                stringResource(pressLabel(press)),
                null,
                actionLabel(viewModel, actions[press]),
                if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
            ) { choosing = press }
        }
        if (actions.isNotEmpty()) {
            ValueRow(R.string.key_remove, null, null) {
                viewModel.removeKeyBinding(keyCode)
                onDone()
            }
        }
        Note(R.string.note_key_normal)
    }
    choosing?.let { press ->
        ActionPickerDialog(
            viewModel = viewModel,
            title = stringResource(pressLabel(press)),
            current = actions[press],
            onChoose = { action ->
                viewModel.setKeyAction(keyCode, press, action)
                choosing = null
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun AdbSetupDialog(viewModel: LauncherViewModel, onDismiss: () -> Unit) {
    val state by viewModel.adbSetup.collectAsStateWithLifecycle()
    val connecting = state == AdbSetupState.Connecting
    SettingsDialog(stringResource(R.string.setting_auto_adb), onDismiss) {
        BasicText(
            stringResource(if (state == AdbSetupState.Ready) R.string.adb_ready_body else R.string.adb_body),
            style = interStyle(12.5.sp, 500, Palette.TextSecondary).copy(lineHeight = 18.sp),
        )
        val failure = (state as? AdbSetupState.Failed)?.reason
        if (connecting || failure != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(
                    when (failure) {
                        null -> R.string.adb_connecting
                        LocalAdb.SetupResult.ADB_OFF -> R.string.adb_failed_off
                        LocalAdb.SetupResult.NOT_ALLOWED -> R.string.adb_failed_denied
                        else -> R.string.adb_failed_other
                    },
                ),
                style = interStyle(12.5.sp, 600, if (failure != null) Palette.Amber else Palette.TextPrimary).copy(lineHeight = 18.sp),
            )
        }
        Spacer(Modifier.height(18.dp))
        val close = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
        Row {
            DialogButton(stringResource(R.string.close), Modifier.focusRequester(close), onDismiss)
            if (state != AdbSetupState.Ready) {
                Spacer(Modifier.width(10.dp))
                DialogButton(stringResource(if (connecting) R.string.adb_connecting_short else R.string.adb_connect), Modifier) {
                    if (!connecting) viewModel.runAdbSetup()
                }
            }
        }
    }
}

/**
 * Waits for the key to assign. The Home button service reports it when running, which also catches keys the TV would
 * otherwise act on itself; without the service, keys that reach Matinee are caught here.
 */
@Composable
private fun LearnKeyDialog(viewModel: LauncherViewModel, onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var rejected by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun offer(keyCode: Int) {
        // Back leaves, as everywhere else; with the service catching every key it is the only way out.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onDismiss()
            return
        }
        if (keyCode in UNASSIGNABLE_KEYS) {
            rejected = true
            viewModel.startKeyLearning()
        } else {
            onKey(keyCode)
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                offer(intent.getIntExtra(HomeKeyService.EXTRA_KEY_CODE, KeyEvent.KEYCODE_UNKNOWN))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(HomeKeyService.ACTION_KEY_LEARNED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        viewModel.startKeyLearning()
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SettingsDialog(stringResource(R.string.key_learn_title), onDismiss) {
        BasicText(
            stringResource(if (rejected) R.string.key_learn_rejected else R.string.key_learn_body),
            style = interStyle(12.5.sp, 500, if (rejected) Palette.Amber else Palette.TextSecondary).copy(lineHeight = 18.sp),
            modifier = Modifier
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) offer(event.nativeKeyEvent.keyCode)
                    true
                }
                .focusable(),
        )
    }
}

@Composable
private fun ActionPickerDialog(
    viewModel: LauncherViewModel,
    title: String,
    current: KeyAction?,
    onChoose: (KeyAction?) -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    val apps by viewModel.appsForSettings.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    when (picking) {
        Picking.APP -> SettingsDialog(stringResource(R.string.action_open_app), onDismiss = { picking = null }) {
            ChoiceList(apps.map { it.label to KeyAction.OpenApp(it.packageName) }, current, onChoose)
        }
        Picking.INPUT -> SettingsDialog(stringResource(R.string.action_open_input), onDismiss = { picking = null }) {
            ChoiceList(inputs.map { it.name to KeyAction.OpenInput(it.id) }, current, onChoose)
        }
        null -> SettingsDialog(title, onDismiss) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ChoiceRow(stringResource(R.string.action_normal), current == null, Modifier.focusRequester(first)) { onChoose(null) }
                }
                SIMPLE_ACTIONS.forEach { (label, action) ->
                    item { ChoiceRow(stringResource(label), current == action) { onChoose(action) } }
                }
                item {
                    ValueRow(stringResource(R.string.action_open_app), null, (current as? KeyAction.OpenApp)?.let { actionLabel(viewModel, it) }) {
                        picking = Picking.APP
                    }
                }
                if (inputs.isNotEmpty()) {
                    item {
                        ValueRow(stringResource(R.string.action_open_input), null, (current as? KeyAction.OpenInput)?.let { actionLabel(viewModel, it) }) {
                            picking = Picking.INPUT
                        }
                    }
                }
            }
        }
    }
}

private enum class Picking { APP, INPUT }

@Composable
private fun ChoiceList(options: List<Pair<String, KeyAction>>, current: KeyAction?, onChoose: (KeyAction) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(options.isNotEmpty()) { runCatching { first.requestFocus() } }
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        options.forEachIndexed { index, (label, action) ->
            item {
                ChoiceRow(label, current == action, if (index == 0) Modifier.focusRequester(first) else Modifier) { onChoose(action) }
            }
        }
    }
}

@Composable
private fun SettingsDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(440.dp)
                .glass(RoundedCornerShape(28.dp), GlassStyle.Dense)
                .padding(22.dp),
        ) {
            BasicText(title, style = interStyle(17.sp, 700))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private val SIMPLE_ACTIONS = listOf(
    R.string.action_nothing to KeyAction.DoNothing,
    R.string.action_matinee_home to KeyAction.MatineeHome,
    R.string.action_matinee_settings to KeyAction.MatineeSettings,
    R.string.action_screen_off to KeyAction.ScreenOff,
)

/** Keys Matinee needs for itself or the TV can't do without: Home, Back and navigation, and power. */
private val UNASSIGNABLE_KEYS = setOf(
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_POWER,
    KeyEvent.KEYCODE_SLEEP,
    KeyEvent.KEYCODE_WAKEUP,
    KeyEvent.KEYCODE_UNKNOWN,
)

@StringRes
private fun pressLabel(press: PressType): Int = when (press) {
    PressType.SHORT -> R.string.press_short
    PressType.LONG -> R.string.press_long
    PressType.DOUBLE -> R.string.press_double
}

@Composable
private fun bindingSummary(viewModel: LauncherViewModel, binding: KeyBinding): String =
    PressType.entries.mapNotNull { press ->
        binding.actions[press]?.let { stringResource(pressLabel(press)) + ": " + actionLabel(viewModel, it) }
    }.joinToString(" · ")

@Composable
private fun actionLabel(viewModel: LauncherViewModel, action: KeyAction?): String {
    val apps by viewModel.appsForSettings.collectAsStateWithLifecycle()
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    return when (action) {
        null -> stringResource(R.string.action_normal)
        KeyAction.DoNothing -> stringResource(R.string.action_nothing)
        KeyAction.MatineeHome -> stringResource(R.string.action_matinee_home)
        KeyAction.MatineeSettings -> stringResource(R.string.action_matinee_settings)
        KeyAction.ScreenOff -> stringResource(R.string.action_screen_off)
        is KeyAction.OpenApp -> apps.firstOrNull { it.packageName == action.packageName }?.label ?: action.packageName
        is KeyAction.OpenInput -> inputs.firstOrNull { it.id == action.inputId }?.name ?: stringResource(R.string.action_open_input)
    }
}

/** The remote's own name for a key where it has a well-known one, else Android's name for it. */
@Composable
internal fun keyName(keyCode: Int): String {
    val known = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> R.string.key_back
        KeyEvent.KEYCODE_MENU -> R.string.key_menu
        KeyEvent.KEYCODE_SETTINGS -> R.string.key_settings
        KeyEvent.KEYCODE_TV_INPUT -> R.string.key_input
        KeyEvent.KEYCODE_GUIDE -> R.string.key_guide
        KeyEvent.KEYCODE_INFO -> R.string.key_info
        KeyEvent.KEYCODE_PROG_RED -> R.string.key_red
        KeyEvent.KEYCODE_PROG_GREEN -> R.string.key_green
        KeyEvent.KEYCODE_PROG_YELLOW -> R.string.key_yellow
        KeyEvent.KEYCODE_PROG_BLUE -> R.string.key_blue
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> R.string.key_play_pause
        KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> R.string.key_mute
        KeyEvent.KEYCODE_VOLUME_UP -> R.string.key_volume_up
        KeyEvent.KEYCODE_VOLUME_DOWN -> R.string.key_volume_down
        KeyEvent.KEYCODE_CHANNEL_UP -> R.string.key_channel_up
        KeyEvent.KEYCODE_CHANNEL_DOWN -> R.string.key_channel_down
        else -> null
    }
    if (known != null) return stringResource(known)
    if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return (keyCode - KeyEvent.KEYCODE_0).toString()
    return KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
}
