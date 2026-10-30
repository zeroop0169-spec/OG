package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoxyDarkColorScheme = darkColorScheme(
    primary = CyberPink,
    onPrimary = Color.White,
    primaryContainer = CyberPinkSubtle,
    onPrimaryContainer = CyberPink,
    secondary = CyberCyan,
    onSecondary = Color.Black,
    secondaryContainer = CyberCyanSubtle,
    onSecondaryContainer = CyberCyan,
    tertiary = CyberPurple,
    onTertiary = Color.White,
    background = CyberDarkBg,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder,
    error = CyberRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoxyDarkColorScheme,
        typography = Typography,
        content = content
    )
}
