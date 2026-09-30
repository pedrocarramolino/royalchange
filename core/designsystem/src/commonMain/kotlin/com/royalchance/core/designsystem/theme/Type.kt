package com.royalchance.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.resources.Res
import com.royalchance.core.designsystem.resources.cinzel
import com.royalchance.core.designsystem.resources.manrope
import org.jetbrains.compose.resources.Font

/**
 * Cinzel (capitales de inspiración romana) para títulos y marca; Manrope para interfaz y lectura.
 * Ambas son fuentes variables con licencia SIL OFL 1.1, incluidas en la app.
 */
@Composable
internal fun royalTypography(): Typography {
    val displayFamily = FontFamily(
        Font(Res.font.cinzel, FontWeight.Normal),
        Font(Res.font.cinzel, FontWeight.SemiBold),
        Font(Res.font.cinzel, FontWeight.Bold),
    )
    val textFamily = FontFamily(
        Font(Res.font.manrope, FontWeight.Normal),
        Font(Res.font.manrope, FontWeight.Medium),
        Font(Res.font.manrope, FontWeight.SemiBold),
        Font(Res.font.manrope, FontWeight.Bold),
    )
    return remember(displayFamily, textFamily) {
        val base = Typography()
        fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold, spacing: Float = 1f) =
            copy(fontFamily = displayFamily, fontWeight = weight, letterSpacing = spacing.sp)
        fun TextStyle.text(weight: FontWeight) = copy(fontFamily = textFamily, fontWeight = weight)

        Typography(
            displayLarge = base.displayLarge.display(spacing = 2f),
            displayMedium = base.displayMedium.display(spacing = 2f),
            displaySmall = base.displaySmall.display(spacing = 1.5f),
            headlineLarge = base.headlineLarge.display(),
            headlineMedium = base.headlineMedium.display(),
            headlineSmall = base.headlineSmall.display(),
            titleLarge = base.titleLarge.text(FontWeight.Bold),
            titleMedium = base.titleMedium.text(FontWeight.SemiBold),
            titleSmall = base.titleSmall.text(FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.text(FontWeight.Normal),
            bodyMedium = base.bodyMedium.text(FontWeight.Normal),
            bodySmall = base.bodySmall.text(FontWeight.Normal),
            labelLarge = base.labelLarge.text(FontWeight.SemiBold),
            labelMedium = base.labelMedium.text(FontWeight.SemiBold),
            labelSmall = base.labelSmall.text(FontWeight.Medium),
        )
    }
}

/** Cifras de ancho fijo: los saldos y contadores no "bailan" al animarse. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
