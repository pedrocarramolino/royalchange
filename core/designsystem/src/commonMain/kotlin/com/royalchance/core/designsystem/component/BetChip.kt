package com.royalchance.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.theme.RoyalTheme

/**
 * Ficha de apuesta: círculo con el valor, en el color de su denominación.
 *
 * @param description texto para lectores de pantalla ("Añadir 100 fichas a la apuesta").
 * @param selected ficha elegida para apostar (aro dorado).
 */
@Composable
fun BetChip(
    value: Long,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 52.dp,
) {
    val casino = RoyalTheme.casinoColors
    val ring = when {
        selected -> casino.gold
        enabled -> Color.White.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.3f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = chipColor(value).copy(alpha = if (enabled) 1f else 0.4f),
        border = BorderStroke(if (selected) 4.dp else 3.dp, ring),
        shadowElevation = if (selected) 6.dp else 0.dp,
        modifier = modifier.size(size).semantics {
            contentDescription = description
            role = Role.Button
            if (selected) this.selected = true
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(chipLabel(value), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Ficha pequeña sobre el tapete con el importe apostado. Decorativa: la casilla lo describe. */
@Composable
fun StakeMarker(amount: Long, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Surface(
        shape = CircleShape,
        color = chipColor(amount),
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)),
        shadowElevation = 3.dp,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = chipLabel(amount),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (size.value * 0.36f).sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** Importe abreviado para una ficha: "10", "500", "1K", "2,5K", "1260", "25K" (desde 10.000, redondeado). */
fun chipLabel(value: Long): String = when {
    value < 1_000 -> value.toString()
    value % 1_000 == 0L -> "${value / 1_000}K"
    value < 10_000 && value % 100 == 0L -> "${value / 1_000},${(value % 1_000) / 100}K"
    value < 10_000 -> value.toString()
    else -> "${value / 1_000}K"
}

/** Color de la denominación más alta que cabe en [value]. */
@Composable
private fun chipColor(value: Long): Color {
    val casino = RoyalTheme.casinoColors
    return when {
        value >= 25_000 -> Color(0xFF8A6D1F)
        value >= 5_000 -> Color(0xFF0F5B45)
        value >= 1_000 -> casino.goldDeep
        value >= 500 -> Color(0xFF6B3FA0)
        value >= 100 -> Color(0xFF1F1F23)
        value >= 50 -> casino.ruby
        else -> Color(0xFF3E6FB0)
    }
}
