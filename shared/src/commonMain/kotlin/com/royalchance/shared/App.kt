package com.royalchance.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalchance.shared.resources.Res
import com.royalchance.shared.resources.app_name
import com.royalchance.shared.resources.tagline
import org.jetbrains.compose.resources.stringResource

/**
 * Punto de entrada de la UI en todas las plataformas.
 *
 * En la Fase 2 solo valida que Android, escritorio y web renderizan el mismo código y los mismos
 * recursos. El sistema de diseño y la navegación reales llegan en la Fase 3.
 */
@Composable
fun App() {
    MaterialTheme(colorScheme = BootstrapColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.app_name).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Paleta mínima provisional; la identidad visual "Noir & Oro" completa se define en la Fase 3.
private val BootstrapColorScheme = darkColorScheme(
    primary = Color(0xFFD4AF6A),
    background = Color(0xFF0E0F13),
    surface = Color(0xFF0E0F13),
    onBackground = Color(0xFFF3EDE0),
    onSurface = Color(0xFFF3EDE0),
    onSurfaceVariant = Color(0xFFB9B2A4),
)
