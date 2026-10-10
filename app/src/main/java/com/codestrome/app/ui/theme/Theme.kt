package com.codestrome.app.ui.theme   // <- change to your own package name

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Code Strome is a dark app: dark background + brand red.
// Dynamic colour (wallpaper colours) is NOT used, so the brand always looks the same.
private val CodeStromeColors = darkColorScheme(
    primary = CsRed,
    onPrimary = CsDark,                 // dark text on red = 4.95:1 contrast (white would only be 3.76:1)
    primaryContainer = CsRedDeep,
    onPrimaryContainer = Color.White,
    secondary = CsCream,
    onSecondary = CsDark,
    secondaryContainer = CsCardHigh,
    onSecondaryContainer = CsCream,
    tertiary = CsMuted,
    onTertiary = CsDark,
    background = CsDark,
    onBackground = CsCream,
    surface = CsCard,
    onSurface = CsCream,
    surfaceVariant = CsCardHigh,
    onSurfaceVariant = CsMuted,
    outline = CsBorder,
    outlineVariant = CsBorder,
    error = Color(0xFFFF8A80),
    onError = CsDark,
)

@Composable
fun CodeStromeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CodeStromeColors,
        typography = Typography,        // the Type.kt file Android Studio generated
        content = content
    )
}
