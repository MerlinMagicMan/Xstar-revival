package io.xstarrevival.app.gs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object GsColors {
    val Ink = Color(0xFF07090B)
    val Panel = Color(0xFF101419)
    val Panel2 = Color(0xFF171C22)
    val Panel3 = Color(0xFF1E252C)
    val White = Color(0xFFF4F7FA)
    val Muted = Color(0xFF9AA6B2)
    val Faint = Color(0xFF5E6974)
    val Orange = Color(0xFFFF6A00)
    val Green = Color(0xFF45E18A)
    val Amber = Color(0xFFFFC857)
    val Red = Color(0xFFFF5D61)
    val Blue = Color(0xFF4EA1FF)
}

private val scheme = darkColorScheme(
    primary = GsColors.Orange,
    onPrimary = Color.Black,
    secondary = GsColors.Green,
    background = GsColors.Ink,
    surface = GsColors.Panel,
    surfaceVariant = GsColors.Panel2,
    onBackground = GsColors.White,
    onSurface = GsColors.White,
    error = GsColors.Red
)

@Composable
fun GsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
