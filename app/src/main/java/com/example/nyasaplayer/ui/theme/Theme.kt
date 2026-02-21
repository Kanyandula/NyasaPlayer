package com.example.nyasaplayer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NyasaColorScheme = darkColorScheme(
    primary = NyasaPrimary,
    onPrimary = Color.White,
    primaryContainer = NyasaPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = NyasaSurface3,
    onSecondary = Color.White,
    secondaryContainer = NyasaSurface4,
    onSecondaryContainer = Color.White,
    background = NyasaBackground,
    onBackground = Color.White,
    surface = NyasaSurface1,
    onSurface = Color.White,
    surfaceVariant = NyasaSurface2,
    onSurfaceVariant = NyasaTextSecondary,
    outline = NyasaSurface4,
    outlineVariant = NyasaSurface3,
    error = NyasaError,
    onError = Color.White,
    surfaceTint = Color.Transparent,
    inverseSurface = Color.White,
    inverseOnSurface = NyasaBackground,
    inversePrimary = NyasaPrimaryDark,
    scrim = Color.Black,
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NyasaBackground.toArgb()
            window.navigationBarColor = NyasaBackground.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = NyasaColorScheme,
        typography = NyasaTypography,
        content = content,
    )
}
