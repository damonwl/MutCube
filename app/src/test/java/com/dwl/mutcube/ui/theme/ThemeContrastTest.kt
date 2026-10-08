package com.dwl.mutcube.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun standardForegroundPairsRemainReadableInBothThemes() {
        for (scheme in listOf(LightColors, DarkColors)) {
            val pairs = listOf(
                scheme.background to scheme.onBackground,
                scheme.surface to scheme.onSurface,
                scheme.surfaceVariant to scheme.onSurfaceVariant,
                scheme.primary to scheme.onPrimary,
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondary to scheme.onSecondary,
                scheme.secondaryContainer to scheme.onSecondaryContainer,
                scheme.tertiary to scheme.onTertiary,
                scheme.tertiaryContainer to scheme.onTertiaryContainer,
                scheme.inverseSurface to scheme.inverseOnSurface,
                scheme.surfaceContainerLowest to scheme.onSurface,
                scheme.surfaceContainerLow to scheme.onSurface,
                scheme.surfaceContainer to scheme.onSurface,
                scheme.surfaceContainerHigh to scheme.onSurface,
                scheme.surfaceContainerHighest to scheme.onSurface,
            )
            for ((background, foreground) in pairs) {
                assertTrue("Unreadable theme pair: $background / $foreground", contrast(background, foreground) >= 4.5f)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (maxOf(firstLuminance, secondLuminance) + 0.05f) /
            (minOf(firstLuminance, secondLuminance) + 0.05f)
    }
}
