package io.xstarrevival.app.gs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun GsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
