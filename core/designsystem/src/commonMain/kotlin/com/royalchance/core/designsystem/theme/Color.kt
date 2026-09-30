package com.royalchance.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta base de la identidad "Noir & Oro".
private val Obsidian = Color(0xFF0E0F13)
private val Ivory = Color(0xFFF3EDE0)
private val GoldLight = Color(0xFFF3DFA2)
private val Gold = Color(0xFFD4AF6A)
private val GoldDeep = Color(0xFF9C7A3C)
private val Felt = Color(0xFF0F5B45)
private val FeltShadow = Color(0xFF07100D)
private val Ruby = Color(0xFFB3263B)

internal val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231905),
    primaryContainer = Color(0xFF3B2E12),
    onPrimaryContainer = GoldLight,
    secondary = Color(0xFF5CC79E),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF0F4A38),
    onSecondaryContainer = Color(0xFFB9F0D6),
    tertiary = Color(0xFFF2939E),
    onTertiary = Color(0xFF5E0F1D),
    tertiaryContainer = Color(0xFF7D1A2B),
    onTertiaryContainer = Color(0xFFFFD9DD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Obsidian,
    onBackground = Ivory,
    surface = Obsidian,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF2A2C33),
    onSurfaceVariant = Color(0xFFBDB6A8),
    surfaceContainerLowest = Color(0xFF090A0D),
    surfaceContainerLow = Color(0xFF15161B),
    surfaceContainer = Color(0xFF1A1C22),
    surfaceContainerHigh = Color(0xFF22252C),
    surfaceContainerHighest = Color(0xFF2B2E36),
    outline = Color(0xFF8C8678),
    outlineVariant = Color(0xFF3A3830),
    inverseSurface = Ivory,
    inverseOnSurface = Color(0xFF1C1B17),
    inversePrimary = Color(0xFF7A5A1E),
    scrim = Color.Black,
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7A5A1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3B4),
    onPrimaryContainer = Color(0xFF281A00),
    secondary = Color(0xFF0F6B51),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBDEFD8),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFFA11F33),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDADD),
    onTertiaryContainer = Color(0xFF40000D),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFAF7F0),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF4D483F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F1E8),
    surfaceContainer = Color(0xFFEFEADF),
    surfaceContainerHigh = Color(0xFFE9E3D7),
    surfaceContainerHighest = Color(0xFFE3DDD0),
    outline = Color(0xFF7F786B),
    outlineVariant = Color(0xFFD3CBBB),
    inverseSurface = Color(0xFF31302B),
    inverseOnSurface = Color(0xFFF4F0E7),
    inversePrimary = Gold,
    scrim = Color.Black,
)

/**
 * Colores propios del casino que Material 3 no cubre: tapete, oro, palos de la baraja…
 * Se leen con `RoyalTheme.casinoColors`.
 */
@Immutable
data class CasinoColors(
    val isDark: Boolean,
    val felt: Color,
    val feltShadow: Color,
    val onFelt: Color,
    val goldLight: Color,
    val gold: Color,
    val goldDeep: Color,
    val ruby: Color,
    val ivory: Color,
    val suitRed: Color,
    val suitBlack: Color,
    val success: Color,
    val warning: Color,
) {
    /** Degradado metálico para acciones principales y detalles dorados. */
    val goldBrush: Brush = Brush.verticalGradient(listOf(goldLight, gold, goldDeep))

    /** Tapete: verde esmeralda que se oscurece hacia los bordes. */
    val feltBrush: Brush = Brush.radialGradient(listOf(felt, feltShadow))
}

internal val DarkCasinoColors = CasinoColors(
    isDark = true,
    felt = Felt,
    feltShadow = FeltShadow,
    onFelt = Ivory,
    goldLight = GoldLight,
    gold = Gold,
    goldDeep = GoldDeep,
    ruby = Ruby,
    ivory = Ivory,
    suitRed = Color(0xFFE0485E),
    suitBlack = Ivory,
    success = Color(0xFF5CC79E),
    warning = Color(0xFFF2C45A),
)

internal val LightCasinoColors = CasinoColors(
    isDark = false,
    felt = Color(0xFF1E7A5E),
    feltShadow = Color(0xFF0B3A2C),
    onFelt = Ivory,
    goldLight = GoldLight,
    gold = Gold,
    goldDeep = GoldDeep,
    ruby = Ruby,
    ivory = Ivory,
    suitRed = Ruby,
    suitBlack = Color(0xFF15171D),
    success = Color(0xFF0F6B51),
    warning = Color(0xFF8A5A00),
)

internal val LocalCasinoColors = staticCompositionLocalOf { DarkCasinoColors }
