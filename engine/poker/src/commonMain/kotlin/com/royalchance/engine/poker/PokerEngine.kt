package com.royalchance.engine.poker

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.standardDeck

/**
 * Motor de Texas Hold'em No-Limit: funciones puras `(estado, acción) → estado`.
 *
 * - Ciegas pequeña y grande; en mano a dos, el botón pone la pequeña y habla primero antes del flop.
 * - Subida mínima: la última subida completa (al menos la ciega grande). Un all-in corto no cambia
 *   la subida mínima. Simplificación: tras un all-in corto, quien ya había hablado puede volver a
 *   subir (en reglamento estricto solo podría igualar o retirarse).
 * - Botes laterales por niveles de lo aportado; las fichas sobrantes de un reparto (en unidades de
 *   [PokerRules.chipUnit]) van al primer ganador a la izquierda del botón.
 *
 * Al terminar la mano cada asiento conserva lo que aportó ([Seat.committed]) hasta el reparto
 * siguiente: quien lleva la contabilidad sabe así cuánto puso el jugador aunque la mano acabe con
 * su propia acción. El azar solo entra al barajar ([startHand]).
 */
object PokerEngine {

    /** Reparte una mano nueva: mueve el botón, pone las ciegas y da dos cartas a cada jugador con fichas. */
    fun startHand(state: PokerState, random: RandomGenerator): Outcome<PokerState, PokerError> {
        val active = state.seats.indices.filter { state.seats[it].stack > 0 }
        if (active.size < 2) return failure(PokerError.NotEnoughPlayers)

        val n = state.seats.size
        val button = if (state.handNumber == 0 && state.seats[state.button].stack > 0) {
            state.button
        } else {
            nextIndex(n, state.button) { state.seats[it].stack > 0 }!!
        }
        val order = (1..n).map { (button + it) % n }.filter { state.seats[it].stack > 0 }
        val deck = random.shuffled(standardDeck())
        var cursor = 0
        // Se reparte una carta a cada uno, empezando a la izquierda del botón, y luego otra.
        val holes = HashMap<Int, List<Card>>()
        repeat(2) {
            order.forEach { seat -> holes[seat] = (holes[seat] ?: emptyList()) + deck[cursor++] }
        }
        val seats = state.seats.mapIndexed { index, seat ->
            seat.copy(hole = holes[index] ?: emptyList(), bet = 0, committed = 0, folded = false, acted = false, lastAction = null)
        }

        val headsUp = active.size == 2
        val smallBlindSeat = if (headsUp) button else order[0]
        val bigBlindSeat = if (headsUp) order.first { it != button } else order[1]

        var next = state.copy(
            seats = seats,
            button = button,
            deck = deck.drop(cursor),
            board = emptyList(),
            street = Street.Preflop,
            phase = PokerPhase.Betting,
            toAct = null,
            currentBet = state.rules.bigBlind,
            minRaise = state.rules.bigBlind,
            handNumber = state.handNumber + 1,
            awards = emptyList(),
            showdown = false,
        )
        next = next.pay(smallBlindSeat, state.rules.smallBlind, SeatAction.SmallBlind)
        next = next.pay(bigBlindSeat, state.rules.bigBlind, SeatAction.BigBlind)
        return Outcome.Success(next.continueFrom(bigBlindSeat))
    }

    fun apply(state: PokerState, seatIndex: Int, action: PokerAction): Outcome<PokerState, PokerError> {
        val legal = state.legalActions(seatIndex) ?: return failure(PokerError.NotYourTurn)
        val seat = state.seats[seatIndex]
        val unit = state.rules.chipUnit
        val next = when (action) {
            PokerAction.Fold -> state.update(seatIndex) { it.copy(folded = true, acted = true, lastAction = SeatAction.Fold) }

            PokerAction.Check -> {
                if (!legal.canCheck) return failure(PokerError.Illegal)
                state.update(seatIndex) { it.copy(acted = true, lastAction = SeatAction.Check) }
            }

            PokerAction.Call -> {
                if (legal.callAmount == 0L) {
                    state.update(seatIndex) { it.copy(acted = true, lastAction = SeatAction.Check) }
                } else {
                    state.pay(seatIndex, legal.callAmount, SeatAction.Call).update(seatIndex) { it.copy(acted = true) }
                }
            }

            is PokerAction.RaiseTo -> {
                val to = action.amount
                val allIn = to == legal.maxRaiseTo
                val valid = legal.canRaise && to > state.currentBet && to <= legal.maxRaiseTo &&
                    (allIn || (to >= legal.minRaiseTo && to % unit == 0L))
                if (!valid) return failure(PokerError.Illegal)
                val increase = to - state.currentBet
                val fullRaise = increase >= state.minRaise
                val kind = if (state.currentBet == 0L) SeatAction.Bet else SeatAction.Raise
                state.pay(seatIndex, to - seat.bet, kind)
                    .copy(currentBet = to, minRaise = if (fullRaise) increase else state.minRaise)
                    .let { raised ->
                        // Una subida reabre la acción: los demás tienen que volver a hablar.
                        raised.copy(seats = raised.seats.mapIndexed { i, s -> if (i == seatIndex) s.copy(acted = true) else s.copy(acted = false) })
                    }
            }
        }
        return Outcome.Success(next.afterAction(seatIndex))
    }

    // ── Desarrollo de la mano ────────────────────────────────────────────────────────────────

    private fun PokerState.afterAction(seatIndex: Int): PokerState {
        val inHand = seats.indices.filter { seats[it].inHand }
        if (inHand.size == 1) return awardUncontested(inHand.single())
        return continueFrom(seatIndex)
    }

    /** Pasa el turno al siguiente que deba hablar o, si la ronda está cerrada, a la siguiente calle. */
    private fun PokerState.continueFrom(seatIndex: Int): PokerState {
        val next = nextToAct(seatIndex)
        return if (next != null) copy(toAct = next, phase = PokerPhase.Betting) else endStreet()
    }

    private fun PokerState.nextToAct(after: Int): Int? {
        val canAct = seats.count { it.canAct }
        return nextIndex(seats.size, after) { index ->
            val seat = seats[index]
            // Si solo queda uno con fichas y ya iguala, no hay nadie con quien apostar.
            seat.canAct && (!seat.acted || seat.bet < currentBet) && !(canAct == 1 && seat.bet >= currentBet)
        }
    }

    private fun PokerState.endStreet(): PokerState {
        var state = copy(
            seats = seats.map { it.copy(bet = 0, acted = false, lastAction = if (it.allIn || it.folded) it.lastAction else null) },
            currentBet = 0,
            minRaise = rules.bigBlind,
            toAct = null,
        )
        while (true) {
            if (state.street == Street.River) return state.showdown()
            state = state.dealNextStreet()
            // Con un solo jugador (o ninguno) que pueda apostar, se reparte el resto sin apuestas.
            if (state.seats.count { it.canAct } >= 2) {
                val first = nextIndex(state.seats.size, state.button) { state.seats[it].canAct }
                return state.copy(toAct = first, phase = PokerPhase.Betting)
            }
        }
    }

    private fun PokerState.dealNextStreet(): PokerState {
        val (street, count) = when (street) {
            Street.Preflop -> Street.Flop to 3
            Street.Flop -> Street.Turn to 1
            Street.Turn -> Street.River to 1
            Street.River -> error("No hay calle después del river")
        }
        return copy(street = street, board = board + deck.take(count), deck = deck.drop(count))
    }

    private fun PokerState.awardUncontested(winner: Int): PokerState {
        val amount = pot
        val seats = seats.mapIndexed { index, seat ->
            val updated = seat.copy(bet = 0)
            if (index == winner) updated.copy(stack = seat.stack + amount) else updated
        }
        return copy(seats = seats, phase = PokerPhase.HandOver, toAct = null, awards = listOf(PotAward(amount, listOf(winner), null)), showdown = false)
    }

    private fun PokerState.showdown(): PokerState {
        val values = seats.indices.filter { seats[it].inHand }.associateWith { HandEvaluator.evaluate(seats[it].hole + board) }
        val stacks = seats.map { it.stack }.toMutableList()
        val awards = sidePots().map { (amount, eligible) ->
            val best = eligible.maxOf { values.getValue(it) }
            val winners = clockwiseFromButton(eligible.filter { values.getValue(it) == best })
            split(amount, winners).forEach { (seat, share) -> stacks[seat] += share }
            PotAward(amount, winners, best.category)
        }
        return copy(
            seats = seats.mapIndexed { index, seat -> seat.copy(stack = stacks[index], bet = 0) },
            phase = PokerPhase.HandOver,
            toAct = null,
            awards = awards,
            showdown = true,
        )
    }

    /**
     * Botes por niveles de aportación: cada nivel lo disputan quienes siguen en la mano y aportaron
     * al menos ese nivel. Lo aportado por encima de lo que nadie más puede disputar vuelve a su dueño.
     */
    private fun PokerState.sidePots(): List<Pair<Long, List<Int>>> {
        val levels = seats.map { it.committed }.filter { it > 0 }.distinct().sorted()
        val pots = mutableListOf<Pair<Long, List<Int>>>()
        var previous = 0L
        for (level in levels) {
            val amount = seats.sumOf { minOf(it.committed, level) - minOf(it.committed, previous) }
            val eligible = seats.indices.filter { seats[it].inHand && seats[it].committed >= level }
            previous = level
            if (amount == 0L) continue
            when {
                // Nadie que siga en la mano llega a este nivel: se suma al bote anterior.
                eligible.isEmpty() -> pots[pots.lastIndex] = pots.last().let { (a, e) -> (a + amount) to e }
                pots.isNotEmpty() && pots.last().second == eligible -> pots[pots.lastIndex] = pots.last().let { (a, e) -> (a + amount) to e }
                else -> pots += amount to eligible
            }
        }
        return pots
    }

    private fun PokerState.split(amount: Long, winners: List<Int>): List<Pair<Int, Long>> {
        val unit = rules.chipUnit
        val units = amount / unit
        val base = units / winners.size
        val remainder = (units % winners.size).toInt()
        return winners.mapIndexed { order, seat -> seat to (base + if (order < remainder) 1 else 0) * unit }
    }

    private fun PokerState.clockwiseFromButton(indices: List<Int>): List<Int> =
        indices.sortedBy { (it - button - 1 + seats.size) % seats.size }

    // ── Utilidades ───────────────────────────────────────────────────────────────────────────

    private fun PokerState.pay(seatIndex: Int, amount: Long, kind: SeatAction): PokerState = update(seatIndex) { seat ->
        val paid = minOf(amount, seat.stack)
        val stack = seat.stack - paid
        seat.copy(
            stack = stack,
            bet = seat.bet + paid,
            committed = seat.committed + paid,
            lastAction = if (stack == 0L) SeatAction.AllIn else kind,
        )
    }

    private inline fun PokerState.update(seatIndex: Int, change: (Seat) -> Seat): PokerState =
        copy(seats = seats.mapIndexed { index, seat -> if (index == seatIndex) change(seat) else seat })

    /** Siguiente índice después de [after] (en el sentido de las agujas del reloj) que cumple [predicate]. */
    private inline fun nextIndex(size: Int, after: Int, predicate: (Int) -> Boolean): Int? =
        (1..size).map { (after + it) % size }.firstOrNull(predicate)
}
