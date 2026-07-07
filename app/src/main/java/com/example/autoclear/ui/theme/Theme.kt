package com.example.autoclear.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is intentionally dark-only because the overlay and launcher icon are
// built around a black-and-neon visual language.
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
        // Keep theming centralized here so future branding changes do not require
        // touching every screen composable.
        colorScheme = AutoClearColorScheme,
        typography = Typography,
        content = content,
    )
}
