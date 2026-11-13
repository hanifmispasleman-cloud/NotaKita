package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = Color(0xFF003824),
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = MintLight,
    secondary = AmberCoffeeLight,
    onSecondary = Color(0xFF442600),
    secondaryContainer = Color(0xFF5E3805),
    onSecondaryContainer = AmberCoffeeContainer,
    background = DarkBackground,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = LightMutedText,
    error = WarningRedLight,
    onError = Color(0xFF680003)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = MintLight,
    onPrimaryContainer = ForestGreenDark,
    secondary = AmberCoffee,
    onSecondary = Color.White,
    secondaryContainer = AmberCoffeeContainer,
    onSecondaryContainer = Color(0xFF4E2600),
    background = CreamBackground,
    onBackground = CharcoalText,
    surface = WhiteSurface,
    onSurface = CharcoalText,
    surfaceVariant = Color(0xFFE8EBE6),
    onSurfaceVariant = MutedGreyText,
    error = WarningRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
