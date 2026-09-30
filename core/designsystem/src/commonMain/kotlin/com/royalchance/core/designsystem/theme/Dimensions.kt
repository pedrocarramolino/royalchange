package com.royalchance.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Escala de espaciado (múltiplos de 4 dp). */
object RoyalSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object RoyalSizes {
    /** Mínimo táctil recomendado por Material y las guías de accesibilidad. */
    val minTouchTarget = 48.dp

    /** Acciones principales: grandes y fáciles de alcanzar con el pulgar. */
    val primaryActionHeight = 56.dp

    /** Ancho máximo de formularios en pantallas grandes: líneas cómodas de leer. */
    val formMaxWidth = 480.dp

    /** Ancho máximo del contenido en escritorio. */
    val contentMaxWidth = 1200.dp
}

internal val RoyalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
