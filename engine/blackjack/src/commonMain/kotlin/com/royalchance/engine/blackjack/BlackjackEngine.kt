package com.royalchance.engine.blackjack

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Shoe

/**
 * Motor de Blackjack: función pura `(estado, acción) → (nuevo estado, eventos)`.
 *
 * El azar solo entra al barajar el zapato ([RandomGenerator]); después todo es determinista, así
 * que los tests usan zapatos preparados. Una acción inválida devuelve un error tipado.
 */
object BlackjackEngine {

    fun apply(
        state: BlackjackState,
        action: BlackjackAction,
        random: RandomGenerator,
    ): Outcome<BlackjackStep, BlackjackError> = when (action) {
        is BlackjackAction.Deal -> deal(state, action.bet, random)
        BlackjackAction.Hit -> move(state, BlackjackMove.Hit) { hit(it) }
        BlackjackAction.Stand -> move(state, BlackjackMove.Stand) { finishActive(it) }
        BlackjackAction.Double -> move(state, BlackjackMove.Double) { double(it) }
        BlackjackAction.Split -> move(state, BlackjackMove.Split) { split(it) }
    }

    /** Comprueba una apuesta sin repartir: la UI la valida antes de cobrarla. */
    fun validateBet(rules: BlackjackRules, bet: Long): Boolean =
        bet in rules.minimumBet..rules.maximumBet && bet % rules.betStep == 0L

    private fun deal(state: BlackjackState, bet: Long, random: RandomGenerator): Outcome<BlackjackStep, BlackjackError> {
        if (state.phase == BlackjackPhase.PlayerTurn) return failure(BlackjackError.NotAllowed)
        if (!validateBet(state.rules, bet)) return failure(BlackjackError.InvalidBet)

        val table = Table(
            BlackjackState(rules = state.rules, shoe = state.shoe, phase = BlackjackPhase.PlayerTurn),
        )
        val shoe = state.shoe
        if (shoe == null || shoe.needsShuffle) {
            table.state = table.state.copy(shoe = Shoe.shuffled(state.rules.decks, state.rules.penetration, random))
            table.events += BlackjackEvent.ShoeShuffled
        }
        // Orden real: jugador, crupier (vista), jugador, crupier (boca abajo).
        val first = table.draw()
        table.events += BlackjackEvent.CardToPlayer(0, first)
        val up = table.draw()
        table.events += BlackjackEvent.CardToDealer(up, faceDown = false)
        val second = table.draw()
        table.events += BlackjackEvent.CardToPlayer(0, second)
        val hole = table.draw()
        table.events += BlackjackEvent.CardToDealer(hole, faceDown = true)
        table.state = table.state.copy(hands = listOf(PlayerHand(listOf(first, second), stake = bet)), dealer = listOf(up, hole))

        // El crupier mira si tiene blackjack: con él, o con el del jugador, la ronda termina ya.
        val dealerBlackjack = handValue(table.state.dealer).total == 21
        val playerBlackjack = table.state.hands.single().isBlackjack
        if (dealerBlackjack || playerBlackjack) {
            table.reveal()
            table.settle(dealerBlackjack = dealerBlackjack)
        }
        return Outcome.Success(table.step())
    }

    private fun move(
        state: BlackjackState,
        move: BlackjackMove,
        block: (Table) -> Unit,
    ): Outcome<BlackjackStep, BlackjackError> {
        if (move !in state.availableMoves) return failure(BlackjackError.NotAllowed)
        val table = Table(state)
        block(table)
        return Outcome.Success(table.step())
    }

    private fun hit(table: Table) {
        val index = table.state.activeHand
        table.giveCard(index)
        val hand = table.state.hands[index]
        when {
            hand.isBust -> {
                table.events += BlackjackEvent.HandBusted(index)
                finishActive(table)
            }
            // Con 21 no hay nada que mejorar: se planta sola.
            hand.value.total == 21 -> finishActive(table)
        }
    }

    private fun double(table: Table) {
        val index = table.state.activeHand
        table.updateHand(index) { it.copy(stake = it.stake * 2, doubled = true) }
        table.events += BlackjackEvent.HandDoubled(index)
        table.giveCard(index)
        if (table.state.hands[index].isBust) table.events += BlackjackEvent.HandBusted(index)
        finishActive(table)
    }

    private fun split(table: Table) {
        val index = table.state.activeHand
        val hand = table.state.hands[index]
        val (first, second) = hand.cards
        val hands = table.state.hands.toMutableList()
        hands[index] = PlayerHand(listOf(first), hand.stake, fromSplit = true)
        hands.add(index + 1, PlayerHand(listOf(second), hand.stake, fromSplit = true))
        table.state = table.state.copy(hands = hands)
        table.events += BlackjackEvent.HandSplit(index)
        table.giveCard(index)
        table.giveCard(index + 1)

        val splitAces = first.rank == Rank.Ace
        if (splitAces) {
            // Ases separados: una sola carta cada uno y se plantan.
            table.updateHand(index) { it.copy(finished = true) }
            table.updateHand(index + 1) { it.copy(finished = true) }
            advance(table)
        } else {
            if (table.state.hands[index + 1].value.total == 21) table.updateHand(index + 1) { it.copy(finished = true) }
            if (table.state.hands[index].value.total == 21) finishActive(table)
        }
    }

    /** Termina la mano activa y pasa a la siguiente, o al crupier si no quedan. */
    private fun finishActive(table: Table) {
        table.updateHand(table.state.activeHand) { it.copy(finished = true) }
        advance(table)
    }

    private fun advance(table: Table) {
        val next = table.state.hands.indices.firstOrNull { it > table.state.activeHand && !table.state.hands[it].finished }
            ?: table.state.hands.indices.firstOrNull { !table.state.hands[it].finished }
        if (next != null) {
            table.state = table.state.copy(activeHand = next)
        } else {
            dealerPlays(table)
        }
    }

    private fun dealerPlays(table: Table) {
        table.reveal()
        // Si todas las manos se han pasado, el crupier no necesita robar.
        if (table.state.hands.any { !it.isBust }) {
            // Se planta en cualquier 17, también blando.
            while (handValue(table.state.dealer).total < 17) {
                val card = table.draw()
                table.state = table.state.copy(dealer = table.state.dealer + card)
                table.events += BlackjackEvent.CardToDealer(card, faceDown = false)
            }
            if (handValue(table.state.dealer).total > 21) table.events += BlackjackEvent.DealerBusted
        }
        table.settle(dealerBlackjack = false)
    }

    /** Estado en construcción durante una acción, con sus eventos en orden. */
    private class Table(var state: BlackjackState) {
        val events = mutableListOf<BlackjackEvent>()

        fun draw(): Card {
            val (card, shoe) = checkNotNull(state.shoe) { "Mesa sin zapato" }.draw()
            state = state.copy(shoe = shoe)
            return card
        }

        fun giveCard(index: Int) {
            val card = draw()
            updateHand(index) { it.copy(cards = it.cards + card) }
            events += BlackjackEvent.CardToPlayer(index, card)
        }

        fun updateHand(index: Int, transform: (PlayerHand) -> PlayerHand) {
            state = state.copy(hands = state.hands.toMutableList().also { it[index] = transform(it[index]) })
        }

        fun reveal() {
            if (!state.holeCardRevealed) {
                state = state.copy(holeCardRevealed = true)
                events += BlackjackEvent.HoleCardRevealed
            }
        }

        fun settle(dealerBlackjack: Boolean) {
            val dealerValue = handValue(state.dealer).total
            val results = state.hands.map { hand -> resultOf(hand, dealerValue, dealerBlackjack) }
            state = state.copy(
                phase = BlackjackPhase.RoundOver,
                hands = state.hands.map { it.copy(finished = true) },
                results = results,
            )
            results.forEachIndexed { index, result -> events += BlackjackEvent.HandSettled(index, result) }
        }

        fun step() = BlackjackStep(state, events.toList())
    }

    private fun resultOf(hand: PlayerHand, dealerValue: Int, dealerBlackjack: Boolean): HandResult {
        val stake = hand.stake
        return when {
            hand.isBust -> HandResult(HandOutcome.Loss, 0)
            hand.isBlackjack && dealerBlackjack -> HandResult(HandOutcome.Push, stake)
            // 3:2: la apuesta es múltiplo de 10, así que el pago es entero.
            hand.isBlackjack -> HandResult(HandOutcome.Blackjack, stake + stake * 3 / 2)
            dealerBlackjack -> HandResult(HandOutcome.Loss, 0)
            dealerValue > 21 || hand.value.total > dealerValue -> HandResult(HandOutcome.Win, stake * 2)
            hand.value.total == dealerValue -> HandResult(HandOutcome.Push, stake)
            else -> HandResult(HandOutcome.Loss, 0)
        }
    }
}
