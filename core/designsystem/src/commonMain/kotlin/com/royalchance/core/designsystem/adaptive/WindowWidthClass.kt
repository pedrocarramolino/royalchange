package com.royalchance.core.designsystem.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clase de ancho de ventana según los puntos de corte de Material 3:
 * compacto (móvil en vertical), medio (tablet, ventana estrecha) y expandido (escritorio).
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        fun fromWidth(width: Dp): WindowWidthClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.Compact }

/** Calcula la clase de ancho del espacio disponible y la expone a todo el árbol. */
@Composable
fun ProvideWindowWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalWindowWidthClass provides WindowWidthClass.fromWidth(maxWidth),
            content = content,
        )
    }
}
