package com.royalchance.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Tema de Royal Chance. El modo oscuro es el predeterminado; el claro es una variante de "salón de día".
 */
@Composable
fun RoyalChanceTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCasinoColors provides if (darkTheme) DarkCasinoColors else LightCasinoColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = royalTypography(),
            shapes = RoyalShapes,
            content = content,
        )
    }
}

/** Acceso a los tokens propios del tema, complementarios a `MaterialTheme`. */
object RoyalTheme {
    val casinoColors: CasinoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCasinoColors.current
}
