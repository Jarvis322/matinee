package com.tcltv.sahne.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.data.LaunchableApp

/** Lays the UI out on a fixed 960x540dp design canvas scaled to the real screen, matching the reference at any resolution. */
@Composable
fun DesignCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val current = LocalDensity.current
        val scale = minOf(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT)
        CompositionLocalProvider(LocalDensity provides Density(current.density * scale, fontScale = 1f)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

private const val DESIGN_WIDTH = 960f
private const val DESIGN_HEIGHT = 540f

@Composable
fun TintedIcon(res: Int, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

@Composable
fun AppGlyph(app: LaunchableApp, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val icon = app.icon
    when {
        icon != null -> Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size), alpha = tint.alpha)
        else -> Box(modifier.size(size))
    }
}

@Composable
fun KeyCap(label: String, modifier: Modifier = Modifier, light: Boolean = false) {
    Box(
        modifier
            .background(if (light) Color.White else Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(alpha = if (light) 0f else 0.22f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = interStyle(10.sp, 700, if (light) Color.Black else Palette.TextPrimary))
    }
}

/** A single-line text field for the remote: the keyboard's check mark closes it and moves focus on, and Down leaves it. */
@Composable
fun TextInput(value: String, placeholder: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = interStyle(14.sp, 500),
        cursorBrush = SolidColor(Palette.Amber),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // The keyboard's check mark closes it and hands focus to whatever sits below, such as the city results.
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            focusManager.moveFocus(FocusDirection.Down)
        }),
        modifier = modifier
            .fillMaxWidth()
            // A text field swallows the remote's Down key, which left focus stuck here with results below it.
            .onPreviewKeyEvent { event ->
                event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown && focusManager.moveFocus(FocusDirection.Down)
            }
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.5.dp, if (focused) Color.White.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) BasicText(placeholder, style = interStyle(14.sp, 500, Palette.TextMuted))
                inner()
            }
        },
    )
}

/**
 * Ignores OK until a fresh press begins. A long press that opens a screen is still held when that screen appears, so
 * its repeats and its release would otherwise land on the new screen and activate whatever is focused first.
 */
@Composable
fun Modifier.ignoreHeldOk(resetKey: Any? = Unit): Modifier {
    var armed by remember(resetKey) { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        val center = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            !center || armed -> false
            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
                armed = true
                false
            }
            else -> true
        }
    }
}

/** A click on OK, or a long press on it; the key-up that ends a long press is swallowed so it doesn't also click. */
@Composable
fun Modifier.longPressClickable(onClick: () -> Unit, onLongPress: () -> Unit): Modifier {
    var swallowKeyUp by remember { mutableStateOf(false) }
    return this
        .onPreviewKeyEvent { event ->
            val center = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
            when {
                center && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                    if (!swallowKeyUp) onLongPress()
                    swallowKeyUp = true
                    true
                }
                center && event.type == KeyEventType.KeyUp && swallowKeyUp -> {
                    swallowKeyUp = false
                    true
                }
                else -> false
            }
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
