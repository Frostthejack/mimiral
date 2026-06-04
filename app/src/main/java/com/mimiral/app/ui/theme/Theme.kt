package com.mimiral.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mimiral.app.data.local.settings.ReaderSettingsRepository

// ── Theme enum ─────────────────────────────────────────────
enum class MimiralThemeType(val label: String) {
    DAY("Day"),
    SEPIA("Sepia"),
    DARK("Dark"),
    NIGHT("Night")
}

// ── Color Schemes ──────────────────────────────────────────

private val DayColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = DayOnPrimary,
    secondary = DaySecondary,
    onSecondary = DayOnSecondary,
    background = DayBackground,
    onBackground = DayOnBackground,
    surface = DaySurface,
    onSurface = DayOnSurface
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = SepiaOnPrimary,
    secondary = SepiaSecondary,
    onSecondary = SepiaOnSecondary,
    background = SepiaBackground,
    onBackground = SepiaOnBackground,
    surface = SepiaSurface,
    onSurface = SepiaOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface
)

private val NightColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    secondary = NightSecondary,
    onSecondary = NightOnSecondary,
    background = NightBackground,
    onBackground = NightOnBackground,
    surface = NightSurface,
    onSurface = NightOnSurface
)

// ── Theme State ────────────────────────────────────────────

object ThemeState {
    var currentTheme by mutableStateOf(MimiralThemeType.DAY)
}

/**
 * Remembers and initializes theme state from DataStore.
 * Must be called at the top of the composition tree (e.g. in MainContent).
 */
@Composable
fun rememberMimiralThemeState(): MimiralThemeType {
    val context = LocalContext.current
    val settingsRepository = remember { ReaderSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(
        initial = com.mimiral.app.data.local.settings.ReaderSettings()
    )

    // Sync DataStore value into ThemeState on first composition and when it changes
    LaunchedEffect(settings.themeName) {
        val theme = try {
            MimiralThemeType.valueOf(settings.themeName)
        } catch (_: IllegalArgumentException) {
            MimiralThemeType.DAY
        }
        ThemeState.currentTheme = theme
    }

    return ThemeState.currentTheme
}

// ── Theme Switcher Composable ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MimiralThemeSwitcher(
    currentTheme: MimiralThemeType = ThemeState.currentTheme,
    onThemeSelected: (MimiralThemeType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = currentTheme.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MimiralThemeType.entries.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.label) },
                    onClick = {
                        ThemeState.currentTheme = theme
                        onThemeSelected(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Main Theme Composable ──────────────────────────────────

@Composable
fun MimiralTheme(
    themeType: MimiralThemeType = ThemeState.currentTheme,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDark = themeType == MimiralThemeType.DARK || themeType == MimiralThemeType.NIGHT

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val view = LocalView.current
            if (view.isInEditMode) {
                // Preview: use static scheme
                themeType.toColorScheme()
            } else {
                // On Android 12+, use dynamic colors but only for Day/Sepia (light) or Dark (dark)
                // Night theme should stay pure black even with dynamic colors
                if (themeType == MimiralThemeType.NIGHT) {
                    NightColorScheme
                } else {
                    val context = view.context
                    if (isDark) {
                        dynamicDarkColorScheme(context)
                    } else {
                        dynamicLightColorScheme(
                            context
                        )
                    }
                }
            }
        }
        else -> themeType.toColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ── Helper ─────────────────────────────────────────────────

fun MimiralThemeType.toColorScheme(): ColorScheme = when (this) {
    MimiralThemeType.DAY -> DayColorScheme
    MimiralThemeType.SEPIA -> SepiaColorScheme
    MimiralThemeType.DARK -> DarkColorScheme
    MimiralThemeType.NIGHT -> NightColorScheme
}
