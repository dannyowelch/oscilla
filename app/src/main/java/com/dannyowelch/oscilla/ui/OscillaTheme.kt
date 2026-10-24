package com.dannyowelch.oscilla.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OscillaColors = darkColorScheme(
    primary = Color(0xFF80DEEA),
    onPrimary = Color(0xFF00363A),
    secondary = Color(0xFFB388FF),
    background = Color(0xFF0E0F12),
    surface = Color(0xFF17181C),
    surfaceVariant = Color(0xFF2A2C33),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun OscillaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OscillaColors,
        content = content,
    )
}
