package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val title: String, val subtitle: String) {
    OBSIDIAN_DARK("Obsidian Dark Studio", "OLED High-Contrast • Accent: 90%"),
    NORDIC_LIGHT("Nordic Minimalist", "Modern Light • Precision Clean")
}

// 6px Corner Radius specification
val AppCornerRadius = 6.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppCornerRadius),
    small = RoundedCornerShape(AppCornerRadius),
    medium = RoundedCornerShape(AppCornerRadius),
    large = RoundedCornerShape(AppCornerRadius),
    extraLarge = RoundedCornerShape(AppCornerRadius)
)

// Obsidian Dark Studio (OLED High-Contrast, 90% Accent Weight)
val ObsidianDarkColorScheme: ColorScheme = darkColorScheme(
    primary = AlpineCyanDark,
    onPrimary = Color.Black,
    primaryContainer = AlpineCyanDarkContainer,
    onPrimaryContainer = AlpineCyanDark,
    secondary = AlpineCyanDarkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = AlpineCyanDarkSecondary.copy(alpha = 0.2f),
    onSecondaryContainer = AlpineCyanDarkSecondary,
    tertiary = AlpineCyanDark,
    onTertiary = Color.Black,
    background = ObsidianCanvas,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder.copy(alpha = 0.6f),
    error = CoralError,
    onError = Color.White
)

// Nordic Minimalist (Modern Light, 90% Accent Weight)
val NordicLightColorScheme: ColorScheme = lightColorScheme(
    primary = AlpineCyanLight,
    onPrimary = Color.White,
    primaryContainer = AlpineCyanLightContainer,
    onPrimaryContainer = AlpineCyanLight,
    secondary = AlpineCyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = AlpineCyanLightSecondary.copy(alpha = 0.15f),
    onSecondaryContainer = AlpineCyanLightSecondary,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = NordicCanvas,
    onBackground = NordicTextPrimary,
    surface = NordicSurface,
    onSurface = NordicTextPrimary,
    surfaceVariant = NordicSurfaceElevated,
    onSurfaceVariant = NordicTextSecondary,
    outline = NordicBorder,
    outlineVariant = NordicSurfaceHighlight,
    error = CoralError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.OBSIDIAN_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.OBSIDIAN_DARK -> ObsidianDarkColorScheme
        AppThemeMode.NORDIC_LIGHT -> NordicLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = Typography,
        content = content
    )
}
