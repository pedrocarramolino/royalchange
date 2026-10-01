package com.royalchance.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.designsystem.theme.tabularNumbers

/**
 * Ficha de casino dibujada: disco dorado con seis marcas claras en el canto y un aro interior.
 * Decorativa: quien la use describe la cantidad para los lectores de pantalla.
 */
@Composable
fun ChipGlyph(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val casino = RoyalTheme.casinoColors
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(brush = casino.goldBrush, radius = radius)

        val rim = radius * 0.26f
        val rimRadius = radius - rim / 2f
        repeat(MARKS) { index ->
            drawArc(
                color = casino.ivory,
                startAngle = index * (360f / MARKS) - MARK_SWEEP / 2f,
                sweepAngle = MARK_SWEEP,
                useCenter = false,
                topLeft = Offset(center.x - rimRadius, center.y - rimRadius),
                size = Size(rimRadius * 2f, rimRadius * 2f),
                style = Stroke(width = rim),
            )
        }
        drawCircle(color = casino.goldDeep, radius = radius * 0.6f, style = Stroke(width = radius * 0.1f))
        // Contorno fino: la ficha se distingue también sobre fondos claros.
        drawCircle(color = casino.goldDeep, radius = radius - 0.5.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * Cantidad de fichas con su icono, con cifras de ancho fijo para que no "bailen" al cambiar.
 *
 * @param amount cantidad ya formateada ("25.450").
 * @param description texto completo para lectores de pantalla ("25.450 fichas").
 */
@Composable
fun ChipAmount(
    amount: String,
    description: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.primary,
    glyphSize: Dp = 20.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        ChipGlyph(size = glyphSize)
        Spacer(Modifier.width(glyphSize * 0.4f))
        Text(text = amount, style = style.tabularNumbers(), color = color, maxLines = 1)
    }
}

private const val MARKS = 6
private const val MARK_SWEEP = 24f
