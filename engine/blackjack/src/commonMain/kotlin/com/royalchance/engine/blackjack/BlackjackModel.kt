package com.royalchance.engine.blackjack

import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Shoe
import kotlinx.serialization.Serializable

/**
 * Reglas de la mesa (acordadas en la Fase 1): zapato de 6 barajas con penetración del 75 %, el
 * crupier se planta en todos los 17 (también blandos) y mira si tiene blackjack, el blackjack paga
 * 3:2, se dobla con las dos primeras cartas (también tras separar) y se separa hasta 4 manos; los
 * ases separados reciben una sola carta. Sin seguro ni rendición.
 *
 * Con la apuesta máxima, doblando las 4 manos se arriesgan 80.000 fichas: dentro del máximo por
 * ronda de la economía (100.000).
 */
@Serializable
data class BlackjackRules(
    val decks: Int = 6,
    val penetration: Double = 0.75,
    val minimumBet: Long = 10,
    val maximumBet: Long = 10_000,
    /** Las apuestas van de 10 en 10: así el pago 3:2 siempre es un número entero. */
    val betStep: Long = 10,
    val maxHands: Int = 4,
)

/** Valor de una mano: [soft] si un as cuenta como 11 sin pasarse. */
data class HandValue(val total: Int, val soft: Boolean)

/** Valor de la carta en Blackjack: figuras 10, as 1 (el 11 se decide al sumar la mano). */
val Rank.points: Int
    get() = when (this) {
        Rank.Ace -> 1
        Rank.Jack, Rank.Queen, Rank.King -> 10
        else -> ordinal + 2
    }

fun handValue(cards: List<Card>): HandValue {
    val hard = cards.sumOf { it.rank.points }
    val hasAce = cards.any { it.rank == Rank.Ace }
    return if (hasAce && hard + 10 <= 21) HandValue(hard + 10, soft = true) else HandValue(hard, soft = false)
}

/**
 * Mano del jugador.
 *
 * @property stake fichas en juego en esta mano (el doble si se ha doblado).
 * @property fromSplit viene de separar: un 21 con dos cartas no es blackjack.
 * @property finished el jugador ya no puede actuar sobre ella.
 */
@Serializable
data class PlayerHand(
    val cards: List<Card>,
    val stake: Long,
    val doubled: Boolean = false,
    val fromSplit: Boolean = false,
    val finished: Boolean = false,
) {
    val value: HandValue get() = handValue(cards)
    val isBust: Boolean get() = value.total > 21
    val isBlackjack: Boolean get() = !fromSplit && cards.size == 2 && value.total == 21
}

@Serializable
enum class HandOutcome { Blackjack, Win, Push, Loss }

/** Resultado de una mano. [payout] incluye la apuesta devuelta (0 si se pierde). */
@Serializable
data class HandResult(val outcome: HandOutcome, val payout: Long)

@Serializable
enum class BlackjackPhase {
    /** Esperando la apuesta de la primera ronda. */
    Betting,

    /** El jugador decide sobre la mano activa. */
    PlayerTurn,

    /** Ronda liquidada; la siguiente empieza con una nueva apuesta. */
    RoundOver,
}

enum class BlackjackMove { Hit, Stand, Double, Split }

/**
 * Estado completo de la mesa: inmutable y serializable, para guardarlo tras cada acción y
 * reanudar la mano al volver.
 */
@Serializable
data class BlackjackState(
    val rules: BlackjackRules = BlackjackRules(),
    val shoe: Shoe? = null,
    val phase: BlackjackPhase = BlackjackPhase.Betting,
    val hands: List<PlayerHand> = emptyList(),
    val activeHand: Int = 0,
    val dealer: List<Card> = emptyList(),
    val holeCardRevealed: Boolean = false,
    /** Un resultado por mano, en el mismo orden, cuando la ronda termina. */
    val results: List<HandResult> = emptyList(),
) {
    val totalStake: Long get() = hands.sumOf { it.stake }
    val totalPayout: Long get() = results.sumOf { it.payout }

    /** Cartas del crupier visibles para el jugador (la segunda, boca abajo hasta revelarla). */
    val visibleDealerCards: List<Card> get() = if (holeCardRevealed) dealer else dealer.take(1)

    /** Movimientos permitidos ahora sobre la mano activa. */
    val availableMoves: Set<BlackjackMove>
        get() {
            if (phase != BlackjackPhase.PlayerTurn) return emptySet()
            val hand = hands.getOrNull(activeHand)?.takeUnless { it.finished } ?: return emptySet()
            return buildSet {
                add(BlackjackMove.Hit)
                add(BlackjackMove.Stand)
                if (hand.cards.size == 2) add(BlackjackMove.Double)
                if (canSplit(hand)) add(BlackjackMove.Split)
            }
        }

    private fun canSplit(hand: PlayerHand): Boolean {
        val (first, second) = hand.cards.takeIf { it.size == 2 } ?: return false
        return hands.size < rules.maxHands &&
            first.rank.points == second.rank.points &&
            // No se vuelven a separar ases.
            !(hand.fromSplit && first.rank == Rank.Ace)
    }

    /** Fichas que hay que apostar antes de aplicar [action] (0 si no requiere apuesta). */
    fun stakeRequiredFor(action: BlackjackAction): Long = when (action) {
        is BlackjackAction.Deal -> action.bet
        BlackjackAction.Double, BlackjackAction.Split -> hands.getOrNull(activeHand)?.stake ?: 0
        BlackjackAction.Hit, BlackjackAction.Stand -> 0
    }
}

sealed interface BlackjackAction {
    data class Deal(val bet: Long) : BlackjackAction

    data object Hit : BlackjackAction

    data object Stand : BlackjackAction

    data object Double : BlackjackAction

    data object Split : BlackjackAction
}

/** Lo que ha pasado, en orden: dirige las animaciones y los sonidos. La UI no deduce nada. */
sealed interface BlackjackEvent {
    data object ShoeShuffled : BlackjackEvent

    data class CardToPlayer(val hand: Int, val card: Card) : BlackjackEvent

    data class CardToDealer(val card: Card, val faceDown: Boolean) : BlackjackEvent

    data object HoleCardRevealed : BlackjackEvent

    data class HandSplit(val hand: Int) : BlackjackEvent

    data class HandDoubled(val hand: Int) : BlackjackEvent

    data class HandBusted(val hand: Int) : BlackjackEvent

    data object DealerBusted : BlackjackEvent

    data class HandSettled(val hand: Int, val result: HandResult) : BlackjackEvent
}

sealed interface BlackjackError {
    /** Fuera de los límites de la mesa o no múltiplo de la ficha mínima. */
    data object InvalidBet : BlackjackError

    /** La acción no está permitida ahora (fase o mano equivocada, doblar con 3 cartas…). */
    data object NotAllowed : BlackjackError
}

data class BlackjackStep(val state: BlackjackState, val events: List<BlackjackEvent>)
