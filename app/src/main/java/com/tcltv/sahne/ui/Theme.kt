@file:OptIn(ExperimentalTextApi::class)

package com.tcltv.sahne.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.tcltv.sahne.R

object Palette {
    val Background = Color(0xFF0B0B0E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFFFFFFF).copy(alpha = 0.72f)
    val TextMuted = Color(0xFFFFFFFF).copy(alpha = 0.5f)
    val Glass = Color(0xFFFFFFFF).copy(alpha = 0.06f)
    val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.11f)
    val Amber = Color(0xFFF2B134)
    val OnAmber = Color(0xFF1E1606)
    val Warning = Color(0xFFFF5A4E)
    val Online = Color(0xFF3DDC84)
    val ToggleOn = Color(0xFF34C759)
}

private fun inter(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(
    inter(400),
    inter(500),
    inter(600),
    inter(700),
    inter(800),
)

fun interStyle(size: TextUnit, weight: Int, color: Color = Palette.TextPrimary, letterSpacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Inter, fontSize = size, fontWeight = FontWeight(weight), color = color, letterSpacing = letterSpacing)

val LocalReduceMotion = staticCompositionLocalOf { false }
