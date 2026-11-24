package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PhosphorCyan,
    onPrimary = Color(0xFF00272E),
    primaryContainer = CyanContainer,
    onPrimaryContainer = Color(0xFFA6F4FF),
    secondary = RecoveryAmber,
    onSecondary = Color(0xFF2B1C00),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Color(0xFFFFDF9B),
    tertiary = TerminalEmerald,
    onTertiary = Color(0xFF002914),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFF9DFBC4),
    error = CriticalCrimson,
    onError = Color(0xFF330005),
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFFB4B4),
    background = ObsidianBg,
    onBackground = InkPrimary,
    surface = ConsoleSlate,
    onSurface = InkPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ConsoleSlate,
    surfaceContainer = ElevatedCard,
    surfaceContainerHigh = SurfaceHighlight,
    outline = HairlineBorder,
    outlineVariant = Color(0xFF1E293B),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF002026),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF2B1900),
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Color(0xFF002210),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFDCE4EE),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun RecoveryAdbTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
