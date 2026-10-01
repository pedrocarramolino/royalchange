package com.royalchance.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royalchance.core.designsystem.graphics.SuitGlyph
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.theme.RoyalTheme

/** Proporción de una carta de póker real (63 × 88 mm). */
private const val CARD_ASPECT = 88f / 63f

// La cara de la carta tiene colores fijos: es un objeto físico, igual en tema claro y oscuro.
private val CardFace = Color(0xFFFBF8F1)
private val CardRed = Color(0xFFB3263B)
private val CardBlack = Color(0xFF15171D)

/**
 * Carta de la baraja francesa. Boca abajo muestra el reverso de la casa; al voltearse
 * ([faceDown] pasa a `false`) cambia con una transición corta.
 *
 * @param rank símbolo del valor ("A", "10", "K"…).
 * @param description texto para lectores de pantalla ("As de picas"); boca abajo, "carta oculta".
 */
@Composable
fun PlayingCard(
    rank: String,
    suit: SuitShape,
    description: String,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    width: Dp = 64.dp,
) {
    val shape = RoundedCornerShape(width * 0.12f)
    Box(modifier.size(width, width * CARD_ASPECT).semantics { contentDescription = description }) {
        AnimatedContent(
            targetState = faceDown,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f)) togetherWith fadeOut(tween(120)) },
            label = "card",
        ) { down ->
            Surface(shape = shape, shadowElevation = 3.dp, color = CardFace, modifier = Modifier.size(width, width * CARD_ASPECT)) {
                if (down) CardBack(width) else CardFront(rank, suit, width)
            }
        }
    }
}

@Composable
private fun CardFront(rank: String, suit: SuitShape, width: Dp) {
    val color = if (suit == SuitShape.Hearts || suit == SuitShape.Diamonds) CardRed else CardBlack
    Box(Modifier.padding(width * 0.08f)) {
        Text(
            text = rank,
            style = TextStyle(fontSize = (width.value * 0.3f).sp, fontWeight = FontWeight.Bold, color = color),
            modifier = Modifier.align(Alignment.TopStart),
        )
        SuitGlyph(suit, brush = SolidColor(color), modifier = Modifier.align(Alignment.Center).size(width * 0.46f))
        SuitGlyph(suit, brush = SolidColor(color), modifier = Modifier.align(Alignment.BottomEnd).size(width * 0.2f))
    }
}

@Composable
private fun CardBack(width: Dp) {
    val casino = RoyalTheme.casinoColors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(width * 0.06f)
            .border(1.dp, casino.gold.copy(alpha = 0.8f), RoundedCornerShape(width * 0.08f))
            .drawBehind { drawRect(casino.ruby) },
    ) {
        SuitGlyph(SuitShape.Spades, brush = casino.goldBrush, modifier = Modifier.size(width * 0.4f))
    }
}
