package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VolleyColorScheme = darkColorScheme(
    primary = VolleyYellow,
    onPrimary = Color.Black,
    primaryContainer = VolleyYellowDark,
    onPrimaryContainer = Color.Black,
    secondary = VolleyYellowDark,
    onSecondary = Color.Black,
    secondaryContainer = VolleyDarkSurfaceVariant,
    onSecondaryContainer = VolleyWhite,
    background = VolleyBlack,
    onBackground = VolleyWhite,
    surface = VolleyDarkSurface,
    onSurface = VolleyWhite,
    surfaceVariant = VolleyDarkSurfaceVariant,
    onSurfaceVariant = VolleyTextSecondary,
    outline = VolleyGrayBorder,
    error = VolleyRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Sunlight-ready high contrast dark scheme by default
    dynamicColor: Boolean = false, // Keep high-contrast custom yellow & dark theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VolleyColorScheme,
        typography = Typography,
        content = content
    )
}
