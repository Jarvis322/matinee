package com.tcltv.sahne.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tcltv.sahne.R
import com.tcltv.sahne.data.PinHasher
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcltv.sahne.ParentalGate
import com.tcltv.sahne.PinResult
import com.tcltv.sahne.data.Parental
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the PIN screen shows under its title: nothing, or a message that the last entry was refused. */
sealed interface PinNotice {
    data object None : PinNotice
    data object Wrong : PinNotice
    data object Mismatch : PinNotice
    data class Locked(val seconds: Int) : PinNotice
}

/**
 * A PIN entry screen for the remote: a keypad that moves with the D-pad, and the remote's number keys work too.
 * [onComplete] gets the digits once all [PinHasher.LENGTH] are in; [attempt] changes after each refused entry so the
 * dots clear again.
 */
@Composable
fun PinPad(
    title: String,
    notice: PinNotice,
    attempt: Int,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var digits by remember(attempt, title) { mutableStateOf("") }
    val locked = notice is PinNotice.Locked
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }

    fun press(digit: Char) {
        if (locked || digits.length >= PinHasher.LENGTH) return
        digits += digit
        if (digits.length == PinHasher.LENGTH) onComplete(digits)
    }

    fun backspace() {
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(300.dp)
                .glass(RoundedCornerShape(30.dp), GlassStyle.Dense)
                .padding(horizontal = 24.dp, vertical = 22.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val code = event.key.nativeKeyCode
                    when (code) {
                        in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> { press('0' + (code - AndroidKeyEvent.KEYCODE_0)); true }
                        in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> { press('0' + (code - AndroidKeyEvent.KEYCODE_NUMPAD_0)); true }
                        AndroidKeyEvent.KEYCODE_DEL, AndroidKeyEvent.KEYCODE_FORWARD_DEL -> { backspace(); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(title, style = interStyle(17.sp, 700), modifier = Modifier.fillMaxWidth(), maxLines = 2)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(16.dp)) {
                repeat(PinHasher.LENGTH) { index ->
                    Box(Modifier.size(14.dp).background(if (index < digits.length) Color.White else Color.White.copy(alpha = 0.25f), CircleShape))
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicText(
                when (notice) {
                    PinNotice.None -> " "
                    PinNotice.Wrong -> stringResource(R.string.pin_wrong)
                    PinNotice.Mismatch -> stringResource(R.string.pin_mismatch)
                    is PinNotice.Locked -> stringResource(R.string.pin_locked, notice.seconds)
                },
                style = interStyle(11.5.sp, 600, Palette.Warning).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().height(30.dp),
                maxLines = 2,
            )
            val rows = listOf("123", "456", "789")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d -> KeyButton(d.toString(), if (d == '5') Modifier.focusRequester(start) else Modifier, onClick = { press(d) }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton("⌫", Modifier, description = stringResource(R.string.pin_delete), onClick = { backspace() })
                    KeyButton("0", Modifier, onClick = { press('0') })
                    KeyButton("✕", Modifier, description = stringResource(R.string.pin_cancel), onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, description: String = label, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(width = 76.dp, height = 44.dp)
            .glass(CircleShape, if (focused) GlassStyle.Bright else GlassStyle.Inset)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = interStyle(18.sp, 600, if (focused) Palette.Background else Palette.TextPrimary))
    }
}

/** Asks for a new PIN twice, so a slip of the remote doesn't lock the parent out. */
@Composable
fun PinSetup(title: String, onSet: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<PinNotice>(PinNotice.None) }
    var attempt by remember { mutableStateOf(0) }
    PinPad(
        title = if (first == null) title else stringResource(R.string.pin_confirm),
        notice = notice,
        attempt = attempt,
        onComplete = { entered ->
            val firstEntry = first
            when {
                firstEntry == null -> {
                    first = entered
                    notice = PinNotice.None
                }
                firstEntry == entered -> onSet(entered)
                else -> {
                    first = null
                    notice = PinNotice.Mismatch
                    attempt++
                }
            }
        },
        onCancel = onCancel,
    )
}

/** Shows the PIN screen whenever something is waiting for the PIN, and passes each entry to the gate. */
@Composable
fun PinGate(gate: ParentalGate, parental: Parental) {
    val request by gate.request.collectAsStateWithLifecycle()
    val pending = request ?: return
    val scope = rememberCoroutineScope()
    var notice by remember(pending) { mutableStateOf<PinNotice>(PinNotice.None) }
    var attempt by remember(pending) { mutableIntStateOf(0) }
    var lockedUntil by remember(pending) { mutableLongStateOf(System.currentTimeMillis() + parental.lockoutRemainingMillis(System.currentTimeMillis())) }
    // While locked out the screen counts the seconds down, then lets entry resume.
    LaunchedEffect(lockedUntil) {
        while (true) {
            val left = lockedUntil - System.currentTimeMillis()
            if (left <= 0) break
            notice = PinNotice.Locked(((left + 999) / 1000).toInt())
            delay(1000)
        }
        if (notice is PinNotice.Locked) notice = PinNotice.None
    }
    PinPad(
        title = stringResource(pending.reason),
        notice = notice,
        attempt = attempt,
        onComplete = { entered ->
            scope.launch {
                when (val result = gate.submit(entered)) {
                    PinResult.Accepted -> Unit
                    is PinResult.Wrong -> {
                        notice = PinNotice.Wrong
                        attempt++
                    }
                    is PinResult.Locked -> {
                        lockedUntil = System.currentTimeMillis() + result.remainingMillis
                        attempt++
                    }
                }
            }
        },
        onCancel = gate::cancel,
    )
}
