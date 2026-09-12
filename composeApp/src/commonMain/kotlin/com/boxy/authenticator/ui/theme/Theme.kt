package com.boxy.authenticator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.boxy.authenticator.domain.models.enums.AppTheme

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF285C55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EDE5),
    onPrimaryContainer = Color(0xFF063731),
    secondary = Color(0xFF725C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF8B3A55),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3A0718),
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFF8FAF8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4ECE8),
    outline = Color(0xFF72817C),
)
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8ED8CB),
    onPrimary = Color(0xFF003A33),
    primaryContainer = Color(0xFF155048),
    onPrimaryContainer = Color(0xFFB6F5EA),
    secondary = Color(0xFFE7C458),
    onSecondary = Color(0xFF3C2F00),
    secondaryContainer = Color(0xFF574600),
    onSecondaryContainer = Color(0xFFFFE38B),
    tertiary = Color(0xFFFFB0C5),
    onTertiary = Color(0xFF541D31),
    tertiaryContainer = Color(0xFF703348),
    onTertiaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF0D1110),
    surface = Color(0xFF0D1110),
    surfaceContainer = Color(0xFF171D1B),
    surfaceVariant = Color(0xFF26302D),
    outline = Color(0xFF899792),
)

@Composable
fun BoxyTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val lightColorScheme = if (dynamicColor) {
        buildPlatformLightColorScheme(true).applyLightThemeAdjustments()
    } else {
        LightColorScheme.applyLightThemeAdjustments()
    }
    val darkColorScheme = if (dynamicColor) {
        buildPlatformDarkColorScheme(true).applyDarkThemeAdjustments()
    } else {
        DarkColorScheme.applyDarkThemeAdjustments()
    }

    val isDarkTheme = when (theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDarkTheme) darkColorScheme else lightColorScheme

    UpdatePlatformTheme(colorScheme, isDarkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = TokkyShapes,
        content = content
    )
}

@Composable
expect fun UpdatePlatformTheme(colorScheme: ColorScheme, isDarkTheme: Boolean)

@Composable
expect fun buildPlatformLightColorScheme(dynamicColor: Boolean): ColorScheme

@Composable
expect fun buildPlatformDarkColorScheme(dynamicColor: Boolean): ColorScheme

private fun ColorScheme.applyLightThemeAdjustments(): ColorScheme = copy(
    surfaceVariant = surface.mixWith(primary, .04f),
    outlineVariant = outlineVariant.copy(0.7f),
)

private fun ColorScheme.applyDarkThemeAdjustments(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = surface.mixWith(primary, .005f),
    outlineVariant = outlineVariant.copy(0.5f),
)

fun Color.mixWith(other: Color, ratio: Float): Color {
    return lerp(this, other, ratio.coerceIn(0f, 1f))
}
