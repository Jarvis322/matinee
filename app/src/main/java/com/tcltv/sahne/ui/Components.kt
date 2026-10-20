package com.tcltv.sahne.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
