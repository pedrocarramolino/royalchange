package com.royalchance.feature.roulette

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royalchance.core.designsystem.component.StakeMarker
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.Chips
import com.royalchance.engine.roulette.PocketColor
import com.royalchance.engine.roulette.RouletteBet
import com.royalchance.engine.roulette.RouletteWheel
import com.royalchance.feature.roulette.resources.Res
import com.royalchance.feature.roulette.resources.roulette_bet_black
import com.royalchance.feature.roulette.resources.roulette_bet_column
import com.royalchance.feature.roulette.resources.roulette_bet_dozen
import com.royalchance.feature.roulette.resources.roulette_bet_even
import com.royalchance.feature.roulette.resources.roulette_bet_high
import com.royalchance.feature.roulette.resources.roulette_bet_low
import com.royalchance.feature.roulette.resources.roulette_bet_odd
import com.royalchance.feature.roulette.resources.roulette_bet_red
import com.royalchance.feature.roulette.resources.roulette_bet_straight
import com.royalchance.feature.roulette.resources.roulette_cell_description
import com.royalchance.feature.roulette.resources.roulette_cell_lost
import com.royalchance.feature.roulette.resources.roulette_cell_staked
import com.royalchance.feature.roulette.resources.roulette_cell_won
import com.royalchance.feature.roulette.resources.roulette_color_black
import com.royalchance.feature.roulette.resources.roulette_color_green
import com.royalchance.feature.roulette.resources.roulette_color_red
import com.royalchance.feature.roulette.resources.roulette_dozen_label
import com.royalchance.feature.roulette.resources.roulette_label_even
import com.royalchance.feature.roulette.resources.roulette_label_high
import com.royalchance.feature.roulette.resources.roulette_label_low
import com.royalchance.feature.roulette.resources.roulette_label_odd
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal val PocketRed = Color(0xFFB3263A)
internal val PocketBlack = Color(0xFF15171D)
internal val PocketGreen = Color(0xFF0E7A4F)

internal fun pocketColor(number: Int): Color = when (RouletteWheel.colorOf(number)) {
    PocketColor.Green -> PocketGreen
    PocketColor.Red -> PocketRed
    PocketColor.Black -> PocketBlack
}

/** Proporciones de las columnas del tapete: docenas, tres columnas de números y apuestas sencillas. */
private const val DOZEN_WEIGHT = 0.75f
private const val OUTSIDE_WEIGHT = 1.1f

/**
 * Tapete europeo en vertical: el 0 arriba, doce filas de tres números, las docenas a la izquierda,
 * las apuestas sencillas a la derecha y las columnas (2:1) abajo. Cada toque apuesta la ficha elegida.
 */
@Composable
internal fun RouletteBoard(state: RouletteUiState, onPlace: (RouletteBet) -> Unit, rowHeight: Dp, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 520.dp)) {
        Row(Modifier.fillMaxWidth().height(rowHeight)) {
            Spacer(Modifier.weight(DOZEN_WEIGHT))
            Cell(RouletteBet.Straight(0), "0", PocketGreen, state, onPlace, Modifier.weight(3f))
            Spacer(Modifier.weight(OUTSIDE_WEIGHT))
        }
        Row(Modifier.fillMaxWidth().height(rowHeight * 12)) {
            Column(Modifier.weight(DOZEN_WEIGHT)) {
                (1..3).forEach { dozen ->
                    Cell(RouletteBet.Dozen(dozen), stringResource(Res.string.roulette_dozen_label, dozen * 12 - 11, dozen * 12), null, state, onPlace, Modifier.weight(1f), vertical = true)
                }
            }
            Column(Modifier.weight(3f)) {
                (1..12).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        (3 * row - 2..3 * row).forEach { number ->
                            Cell(RouletteBet.Straight(number), number.toString(), pocketColor(number), state, onPlace, Modifier.weight(1f))
                        }
                    }
                }
            }
            Column(Modifier.weight(OUTSIDE_WEIGHT)) {
                OUTSIDE_BETS.forEach { (bet, label) ->
                    val color = when (bet) {
                        RouletteBet.Red -> PocketRed
                        RouletteBet.Black -> PocketBlack
                        else -> null
                    }
                    Cell(bet, label?.let { stringResource(it) } ?: "", color, state, onPlace, Modifier.weight(1f), diamond = color != null)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(rowHeight)) {
            Spacer(Modifier.weight(DOZEN_WEIGHT))
            (1..3).forEach { column -> Cell(RouletteBet.Column(column), "2:1", null, state, onPlace, Modifier.weight(1f)) }
            Spacer(Modifier.weight(OUTSIDE_WEIGHT))
        }
    }
}

/** Apuestas sencillas, de arriba abajo, con su texto (rojo y negro se dibujan con un rombo). */
private val OUTSIDE_BETS: List<Pair<RouletteBet, StringResource?>> = listOf(
    RouletteBet.Low to Res.string.roulette_label_low,
    RouletteBet.Even to Res.string.roulette_label_even,
    RouletteBet.Red to null,
    RouletteBet.Black to null,
    RouletteBet.Odd to Res.string.roulette_label_odd,
    RouletteBet.High to Res.string.roulette_label_high,
)

@Composable
private fun Cell(
    bet: RouletteBet,
    label: String,
    background: Color?,
    state: RouletteUiState,
    onPlace: (RouletteBet) -> Unit,
    modifier: Modifier,
    vertical: Boolean = false,
    diamond: Boolean = false,
) {
    val casino = RoyalTheme.casinoColors
    val result = if (state.showResult) state.lastSpin?.spin?.results?.firstOrNull { it.bet == bet } else null
    val stake = if (state.showResult) result?.stake ?: 0 else state.stakeOn(bet)
    val winningNumber = state.showResult && bet is RouletteBet.Straight && bet.number == state.lastSpin?.spin?.number

    val name = betName(bet)
    val description = buildString {
        append(stringResource(Res.string.roulette_cell_description, name))
        if (stake > 0) {
            append(". ")
            append(
                when {
                    result == null -> stringResource(Res.string.roulette_cell_staked, chipsText(Chips(stake)))
                    result.won -> stringResource(Res.string.roulette_cell_won, chipsText(Chips(result.payout)))
                    else -> stringResource(Res.string.roulette_cell_lost, chipsText(Chips(stake)))
                },
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .let { if (background != null && !diamond) it.background(background, MaterialTheme.shapes.extraSmall) else it }
            .border(
                if (winningNumber) BorderStroke(3.dp, casino.gold) else BorderStroke(1.dp, casino.onFelt.copy(alpha = 0.35f)),
                MaterialTheme.shapes.extraSmall,
            )
            .clickable(enabled = state.canBet) { onPlace(bet) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (state.canBet) onClick { onPlace(bet); true }
            },
    ) {
        if (diamond && background != null) {
            Diamond(background, Modifier.padding(vertical = 6.dp, horizontal = 10.dp))
        } else {
            Text(
                text = label,
                style = if (vertical) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (winningNumber) casino.gold else casino.onFelt,
                textAlign = TextAlign.Center,
                maxLines = if (vertical) 3 else 1,
            )
        }
        if (stake > 0) {
            StakeMarker(
                amount = if (result?.won == true) result.payout else stake,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .alpha(if (result != null && !result.won) 0.35f else 1f),
                size = 22.dp,
            )
        }
    }
}

@Composable
private fun Diamond(color: Color, modifier: Modifier) {
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val path = Path().apply {
            moveTo(center.x, 0f)
            lineTo(center.x + size.height * 0.9f, center.y)
            lineTo(center.x, size.height)
            lineTo(center.x - size.height * 0.9f, center.y)
            close()
        }
        drawPath(path, color)
        drawPath(path, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5f))
    }
}

/** Nombre de una apuesta para lectores de pantalla y resúmenes ("pleno al 17, rojo"). */
@Composable
internal fun betName(bet: RouletteBet): String = when (bet) {
    is RouletteBet.Straight -> stringResource(Res.string.roulette_bet_straight, bet.number, colorName(bet.number))
    is RouletteBet.Dozen -> stringResource(Res.string.roulette_bet_dozen, bet.index)
    is RouletteBet.Column -> stringResource(Res.string.roulette_bet_column, bet.index)
    RouletteBet.Red -> stringResource(Res.string.roulette_bet_red)
    RouletteBet.Black -> stringResource(Res.string.roulette_bet_black)
    RouletteBet.Even -> stringResource(Res.string.roulette_bet_even)
    RouletteBet.Odd -> stringResource(Res.string.roulette_bet_odd)
    RouletteBet.Low -> stringResource(Res.string.roulette_bet_low)
    RouletteBet.High -> stringResource(Res.string.roulette_bet_high)
    // El tapete aún no ofrece apuestas entre números; el motor sí las admite.
    else -> bet.numbers.sorted().joinToString("-")
}

@Composable
internal fun colorName(number: Int): String = stringResource(
    when (RouletteWheel.colorOf(number)) {
        PocketColor.Green -> Res.string.roulette_color_green
        PocketColor.Red -> Res.string.roulette_color_red
        PocketColor.Black -> Res.string.roulette_color_black
    },
)
