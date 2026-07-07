package com.example.autoclear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AutoClearColorScheme =
    darkColorScheme(
        primary = MintGlow,
        onPrimary = Graphite,
        secondary = DeepAqua,
        onSecondary = IceText,
        background = Graphite,
        onBackground = IceText,
        surface = SlatePanel,
        onSurface = IceText,
        surfaceVariant = DeepAqua,
        onSurfaceVariant = DustText,
        error = AlertSoft,
    )

@Composable
fun AutoClearTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AutoClearColorScheme,
        typography = Typography,
        content = content,
    )
}
