package com.royalchance.engine.poker

import com.royalchance.engine.cards.Card
import kotlinx.serialization.Serializable

/**
 * Límites de una mesa. Todas las cantidades son múltiplos de [chipUnit]: así cada movimiento de
 * fichas del jugador cumple la apuesta mínima de la economía.
 */
@Serializable
data class PokerRules(
    val smallBlind: Long = 10,
    val bigBlind: Long = 20,
    val minBuyIn: Long = 400,
    val maxBuyIn: Long = 2_000,
    val chipUnit: Long = 10,
) {
    companion object {
        /** Mesas disponibles. */
        val TABLES: List<PokerRules> = listOf(
            PokerRules(),
            PokerRules(smallBlind = 50, bigBlind = 100, minBuyIn = 2_000, maxBuyIn = 10_000),
        )
    }
}

enum class Street { Preflop, Flop, Turn, River }

enum class PokerPhase {
    /** Entre manos: se puede repartir. */
    Waiting,

    /** Ronda de apuestas en curso: le toca a [PokerState.toAct]. */
    Betting,

    /** La mano terminó y se repartieron los botes ([PokerState.awards]). */
    HandOver,
}

/** Lo último que hizo un asiento en la mano, para enseñarlo en la mesa. */
enum class SeatAction { SmallBlind, BigBlind, Fold, Check, Call, Bet, Raise, AllIn }

@Serializable
data class Seat(
    val name: String,
    val isHuman: Boolean,
    val stack: Long,
    /** Estilo de juego de un bot (sin efecto en el jugador). */
    val style: BotStyle = BotStyle.Balanced,
    val hole: List<Card> = emptyList(),
    /** Apostado en la calle actual. */
    val bet: Long = 0,
    /** Apostado en toda la mano (para los botes). */
    val committed: Long = 0,
    val folded: Boolean = false,
    /** Ya habló en esta ronda de apuestas. */
    val acted: Boolean = false,
    val lastAction: SeatAction? = null,
) {
    /** Juega esta mano: recibió cartas y no se ha retirado. */
    val inHand: Boolean get() = hole.isNotEmpty() && !folded

    val allIn: Boolean get() = inHand && stack == 0L

    /** Puede apostar todavía. */
    val canAct: Boolean get() = inHand && stack > 0
}

/** Bote repartido: cuánto, a quién y con qué mano (sin mano si todos los demás se retiraron). */
@Serializable
data class PotAward(val amount: Long, val winners: List<Int>, val category: HandCategory?)

@Serializable
data class PokerState(
    val rules: PokerRules = PokerRules(),
    val seats: List<Seat> = emptyList(),
    val button: Int = 0,
    val deck: List<Card> = emptyList(),
    val board: List<Card> = emptyList(),
    val street: Street = Street.Preflop,
    val phase: PokerPhase = PokerPhase.Waiting,
    val toAct: Int? = null,
    /** Apuesta más alta de la calle. */
    val currentBet: Long = 0,
    /** Subida mínima: la última subida completa (al menos la ciega grande). */
    val minRaise: Long = 0,
    val handNumber: Int = 0,
    val awards: List<PotAward> = emptyList(),
    /** Hubo enfrentamiento: se enseñan las cartas de quienes llegaron al final. */
    val showdown: Boolean = false,
) {
    /** Fichas en el bote de la mano (al terminar, lo que se repartió). */
    val pot: Long get() = seats.sumOf { it.committed }

    fun legalActions(seatIndex: Int): LegalActions? {
        if (phase != PokerPhase.Betting || toAct != seatIndex) return null
        val seat = seats[seatIndex]
        val toCall = minOf(currentBet - seat.bet, seat.stack)
        val maxTo = seat.bet + seat.stack
        val minTo = minOf(currentBet + maxOf(minRaise, rules.bigBlind), maxTo)
        return LegalActions(
            canCheck = toCall == 0L,
            callAmount = toCall,
            canRaise = maxTo > currentBet,
            minRaiseTo = minTo,
            maxRaiseTo = maxTo,
        )
    }
}

/** Lo que puede hacer quien tiene el turno. Las subidas se expresan como "subir hasta X". */
data class LegalActions(
    val canCheck: Boolean,
    val callAmount: Long,
    val canRaise: Boolean,
    val minRaiseTo: Long,
    val maxRaiseTo: Long,
)

sealed interface PokerAction {
    data object Fold : PokerAction

    data object Check : PokerAction

    data object Call : PokerAction

    /** Apostar o subir hasta [amount] en esta calle (todo lo que queda = all-in). */
    data class RaiseTo(val amount: Long) : PokerAction
}

enum class PokerError { NotYourTurn, Illegal, NotEnoughPlayers }

/** Estilo de los bots: cuánto juegan (laxitud) y cuánto apuestan y farolean (agresividad). */
@Serializable
enum class BotStyle(val looseness: Double, val aggression: Double) {
    Tight(0.0, 0.35),
    Balanced(0.06, 0.5),
    Loose(0.12, 0.45),
    Aggressive(0.08, 0.8),
}
