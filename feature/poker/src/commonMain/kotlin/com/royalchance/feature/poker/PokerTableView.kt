package com.royalchance.feature.poker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.component.PlayingCard
import com.royalchance.core.designsystem.component.StakeMarker
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.Chips
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Suit
import com.royalchance.engine.poker.PokerPhase
import com.royalchance.engine.poker.PokerState
import com.royalchance.engine.poker.Seat
import com.royalchance.feature.poker.resources.Res
import com.royalchance.feature.poker.resources.poker_dealer
import com.royalchance.feature.poker.resources.poker_hidden_cards
import com.royalchance.feature.poker.resources.poker_pot
import com.royalchance.feature.poker.resources.poker_seat_description
import com.royalchance.feature.poker.resources.poker_thinking
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mesa ovalada con seis asientos. El jugador se sienta abajo y el juego avanza en el sentido de las
 * agujas del reloj (hacia su izquierda, que en pantalla es arriba a la izquierda).
 */
@Composable
internal fun PokerTableView(table: PokerState, modifier: Modifier = Modifier) {
    val casino = RoyalTheme.casinoColors
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 600.dp
        // Móvil girado: mesa ancha y baja. Las cartas de cada asiento van al lado del nombre (no
        // encima) y todo es algo más pequeño, para que los asientos no tapen el centro.
        val low = maxWidth > maxHeight && maxHeight < 400.dp
        val cardWidth = when {
            low -> 22.dp
            compact -> 26.dp
            else -> 36.dp
        }
        val boardCardWidth = when {
            low -> 30.dp
            compact -> 38.dp
            else -> 56.dp
        }
        Canvas(Modifier.fillMaxSize()) {
            val inset = Offset(size.width * 0.08f, size.height * 0.12f)
            val ovalSize = Size(size.width - inset.x * 2, size.height - inset.y * 2)
            drawOval(Brush.radialGradient(listOf(casino.felt, casino.feltShadow)), inset, ovalSize)
            drawOval(casino.goldBrush, inset, ovalSize, style = Stroke(width = 6.dp.toPx()))
            drawOval(Color.Black.copy(alpha = 0.25f), inset + Offset(10.dp.toPx(), 10.dp.toPx()), Size(ovalSize.width - 20.dp.toPx(), ovalSize.height - 20.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        }

        val n = table.seats.size
        val angles = List(n) { (90.0 + 360.0 / n * it) * PI / 180.0 }
        // Centro de cada elemento en fracciones del tamaño de la mesa.
        val positions = buildList {
            add(0.5f to 0.5f)
            angles.forEach { add((0.5 + 0.40 * cos(it)).toFloat() to (0.5 + 0.40 * sin(it)).toFloat()) }
            val betRadius = if (low) 0.30 to 0.27 else 0.25 to 0.22
            angles.forEach { add((0.5 + betRadius.first * cos(it)).toFloat() to (0.5 + betRadius.second * sin(it)).toFloat()) }
        }

        Layout(
            content = {
                CenterView(table, boardCardWidth)
                table.seats.forEachIndexed { index, seat -> SeatView(table, index, seat, cardWidth, compact, sideCards = low) }
                // En la mesa baja la apuesta va dentro de cada asiento: fuera se montaba encima de ellos.
                table.seats.forEach { seat -> if (low) Box(Modifier.size(1.dp)) else BetView(seat) }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val (fx, fy) = positions[index]
                    val x = (constraints.maxWidth * fx - placeable.width / 2f).toInt().coerceIn(0, maxOf(0, constraints.maxWidth - placeable.width))
                    val y = (constraints.maxHeight * fy - placeable.height / 2f).toInt().coerceIn(0, maxOf(0, constraints.maxHeight - placeable.height))
                    placeable.place(x, y)
                }
            }
        }
    }
}

@Composable
private fun CenterView(table: PokerState, cardWidth: Dp) {
    val casino = RoyalTheme.casinoColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                val card = table.board.getOrNull(index)
                if (card != null) {
                    PokerCard(card, faceDown = false, width = cardWidth)
                } else {
                    Box(
                        Modifier
                            .size(cardWidth, cardWidth * 1.4f)
                            .alpha(0.35f)
                            .padding(1.dp),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(casino.onFelt, style = Stroke(width = 1.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()))
                        }
                    }
                }
            }
        }
        if (table.pot > 0) {
            Text(
                stringResource(Res.string.poker_pot, chipsText(Chips(table.pot))),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = casino.gold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SeatView(table: PokerState, index: Int, seat: Seat, cardWidth: Dp, compact: Boolean, sideCards: Boolean) {
    val active = table.toAct == index && table.phase == PokerPhase.Betting
    val won = table.phase == PokerPhase.HandOver && table.awards.any { index in it.winners }
    val out = seat.hole.isEmpty() && seat.stack == 0L
    val showCards = seat.isHuman || (table.showdown && seat.inHand)
    val status = when {
        active && !seat.isHuman -> stringResource(Res.string.poker_thinking)
        else -> seat.lastAction?.let { actionLabel(it) }
    }
    val cardsDescription = if (seat.hole.isEmpty()) {
        ""
    } else if (showCards) {
        seat.hole.map { cardName(it) }.joinToString(", ")
    } else {
        stringResource(Res.string.poker_hidden_cards)
    }
    val description = stringResource(Res.string.poker_seat_description, seat.name, chipsText(Chips(seat.stack)), status.orEmpty(), cardsDescription)

    val seatModifier = Modifier
        .alpha(if (seat.folded || out) 0.45f else 1f)
        .clearAndSetSemantics { contentDescription = description }
    val cards = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(if (sideCards) (-4).dp else (-6).dp)) {
            // Las del jugador, algo más grandes: son las que más se miran.
            val width = if (sideCards && seat.isHuman) cardWidth * 1.3f else cardWidth
            seat.hole.forEach { card -> PokerCard(card, faceDown = !showCards, width = width) }
        }
    }
    if (sideCards) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = seatModifier) {
            cards()
            SeatInfo(table, index, seat, status, active, won, compact = true, showBet = true)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = seatModifier) {
            cards()
            SeatInfo(table, index, seat, status, active, won, compact)
        }
    }
}

@Composable
private fun SeatInfo(table: PokerState, index: Int, seat: Seat, status: String?, active: Boolean, won: Boolean, compact: Boolean, showBet: Boolean = false) {
    val casino = RoyalTheme.casinoColors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Black.copy(alpha = 0.65f),
        border = when {
            won -> BorderStroke(2.dp, casino.gold)
            active -> BorderStroke(2.dp, casino.goldLight)
            else -> BorderStroke(1.dp, casino.onFelt.copy(alpha = 0.25f))
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(min = if (compact) 64.dp else 88.dp, max = if (compact) 84.dp else 120.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (table.button == index) DealerButton()
                Text(
                    seat.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (seat.isHuman) casino.gold else casino.onFelt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(chipsText(Chips(seat.stack)), style = MaterialTheme.typography.labelSmall, color = casino.onFelt.copy(alpha = 0.9f), maxLines = 1)
            if (status != null) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (active) casino.goldLight else casino.gold.copy(alpha = 0.85f), maxLines = 1)
            }
            if (showBet && seat.bet > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    StakeMarker(seat.bet, size = 14.dp)
                    Text(chipsText(Chips(seat.bet)), style = MaterialTheme.typography.labelSmall, color = casino.onFelt, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BetView(seat: Seat) {
    // Durante la mano, lo apostado en la calle; nada fuera de ella.
    if (seat.bet <= 0) {
        Box(Modifier.size(1.dp))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        StakeMarker(seat.bet, size = 20.dp)
        Text(chipsText(Chips(seat.bet)), style = MaterialTheme.typography.labelSmall, color = RoyalTheme.casinoColors.onFelt)
    }
}

@Composable
private fun DealerButton() {
    val description = stringResource(Res.string.poker_dealer)
    Surface(
        shape = CircleShape,
        color = Color.White,
        modifier = Modifier.size(14.dp).clearAndSetSemantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("D", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Black)
        }
    }
}

@Composable
internal fun PokerCard(card: Card, faceDown: Boolean, width: Dp) {
    PlayingCard(rank = card.rank.symbol, suit = card.suit.shape, description = if (faceDown) "" else cardName(card), faceDown = faceDown, width = width)
}

internal val Suit.shape: SuitShape
    get() = when (this) {
        Suit.Spades -> SuitShape.Spades
        Suit.Hearts -> SuitShape.Hearts
        Suit.Diamonds -> SuitShape.Diamonds
        Suit.Clubs -> SuitShape.Clubs
    }
