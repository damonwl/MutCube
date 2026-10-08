package com.dwl.mutcube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.dwl.mutcube.storage.ThemeMode

internal val LightColors = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E3DF),
    onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF555551),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8E4),
    onSecondaryContainer = Color(0xFF202020),
    tertiary = Color(0xFF555551),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8E8E4),
    onTertiaryContainer = Color(0xFF202020),
    inversePrimary = Color(0xFFE3E3DF),
    surfaceTint = Color(0xFF171717),
    background = Color(0xFFF4F4F2),
    onBackground = Color(0xFF161616),
    surface = Color.White,
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFF0F0ED),
    onSurfaceVariant = Color(0xFF666662),
    surfaceDim = Color(0xFFD8D8D4),
    surfaceBright = Color(0xFFFBFBF9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F5),
    surfaceContainer = Color(0xFFEFEFEC),
    surfaceContainerHigh = Color(0xFFE8E8E4),
    surfaceContainerHighest = Color(0xFFE1E1DD),
    inverseSurface = Color(0xFF2D2D2B),
    inverseOnSurface = Color(0xFFF4F4F1),
    outline = Color(0xFF777772),
    outlineVariant = Color(0xFFE3E3DF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F4),
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF373737),
    onPrimaryContainer = Color(0xFFF4F4F4),
    secondary = Color(0xFFC6C6C2),
    onSecondary = Color(0xFF202020),
    secondaryContainer = Color(0xFF323232),
    onSecondaryContainer = Color(0xFFEAEAEA),
    tertiary = Color(0xFFC6C6C2),
    onTertiary = Color(0xFF202020),
    tertiaryContainer = Color(0xFF323232),
    onTertiaryContainer = Color(0xFFEAEAEA),
    inversePrimary = Color(0xFF373737),
    surfaceTint = Color(0xFFF4F4F4),
    background = Color(0xFF151515),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF222222),
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFA9A9A5),
    surfaceDim = Color(0xFF151515),
    surfaceBright = Color(0xFF3A3A3A),
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF373737),
    inverseSurface = Color(0xFFE6E6E2),
    inverseOnSurface = Color(0xFF252523),
    outline = Color(0xFF91918C),
    outlineVariant = Color(0xFF393936),
)

@Composable
fun MutCubeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * textScale.coerceIn(0.85f, 1.3f)),
    ) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors) {
            // MaterialTheme alone does not supply a foreground color for bare Text/Icon nodes.
            // Surface/Card can still override this default with their own matching content colors.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                content()
            }
        }
    }
}
