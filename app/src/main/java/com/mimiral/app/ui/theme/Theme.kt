package com.mimiral.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Warm color palette
val WarmBrown = Color(0xFF8B5E3C)
val Tan = Color(0xFFD4A574)
val WarmWhite = Color(0xFFFFF8F0)
val SoftBlack = Color(0xFF2C2C2C)

// Sepia
val Parchment = Color(0xFFF4ECD8)
val DarkBrown = Color(0xFF5B4636)
val Golden = Color(0xFF8B6914)
val Sand = Color(0xFFC4A77D)

// Dark
val DeepNavy = Color(0xFF1A1A2E)
val LightGray = Color(0xFFE0E0E0)
val Coral = Color(0xFFE94560)
val Purple = Color(0xFF533483)

// Night (OLED)
val PureBlack = Color(0xFF000000)
val Gold = Color(0xFFFFD700)
val DarkGray = Color(0xFF404040)

private val DayColorScheme = lightColorScheme(
    primary = WarmBrown,
    onPrimary = WarmWhite,
    secondary = Tan,
    onSecondary = SoftBlack,
    background = WarmWhite,
    onBackground = SoftBlack,
    surface = WarmWhite,
    onSurface = SoftBlack,
)

private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = LightGray,
    secondary = Purple,
    onSecondary = LightGray,
    background = DeepNavy,
    onBackground = LightGray,
    surface = DeepNavy,
    onSurface = LightGray,
)

@Composable
fun MimiralTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Use dynamic color on Android 12+
            if (darkTheme) DarkColorScheme else DayColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> DayColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
