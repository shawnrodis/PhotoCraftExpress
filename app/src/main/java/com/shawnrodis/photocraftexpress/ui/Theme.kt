package com.shawnrodis.photocraftexpress.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Steel-blue accent used across the editor chrome. */
val Accent = Color(0xFF31A8FF)
val ChromeBar = Color(0xFF1B1B1B)
val PanelBg = Color(0xFF1E1E1E)
val CanvasBg = Color(0xFF141414)
val AppBg = Color(0xFF121212)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = AppBg,
    onBackground = Color(0xFFF2F2F2),
    surface = ChromeBar,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = PanelBg,
    onSurfaceVariant = Color(0xFFBDBDBD)
)

@Composable
fun PhotoCraftExpressTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
