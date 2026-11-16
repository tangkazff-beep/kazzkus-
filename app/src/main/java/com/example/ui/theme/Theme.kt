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

private val KarsaDarkColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = ObsidianBg,
    primaryContainer = CyanDeepContainer,
    onPrimaryContainer = CyanSoftText,
    secondary = AmberWarm,
    onSecondary = ObsidianBg,
    secondaryContainer = AmberDeepContainer,
    onSecondaryContainer = AmberSoftText,
    tertiary = EmeraldAccent,
    onTertiary = ObsidianBg,
    tertiaryContainer = EmeraldDeepContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B),
    error = ErrorCoral,
    onError = ObsidianBg,
    errorContainer = ErrorDeepContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val KarsaLightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = CyanOnContainerLight,
    secondary = AmberPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = EmeraldLight,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF064E3B),
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = AlabasterSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = AlabasterSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = AlabasterSurface,
    surfaceContainer = AlabasterSurfaceElevated,
    surfaceContainerHigh = AlabasterSurfaceHighest,
    outline = AlabasterBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = ErrorRedLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

val KarsaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KarsaAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KarsaDarkColorScheme else KarsaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KarsaTypography,
        shapes = KarsaShapes,
        content = content
    )
}
