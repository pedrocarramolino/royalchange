package com.royalchance.engine.poker

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.standardDeck

/**
 * Bots de la mesa. Deciden con la equidad de su mano (probabilidad de ganar el bote), estimada
 * simulando el resto del reparto contra cartas desconocidas de sus rivales, y la comparan con lo
 * que cuesta seguir (pot odds). El estilo ajusta cuánto juegan, cuánto suben y cuánto farolean.
 *
 * No hacen trampa: solo usan sus cartas y las comunitarias. Todo el azar sale del [RandomGenerator].
 */
object PokerBot {

    const val SIMULATIONS: Int = 160

    fun decide(state: PokerState, seatIndex: Int, random: RandomGenerator): PokerAction {
        val legal = state.legalActions(seatIndex) ?: return PokerAction.Fold
        val seat = state.seats[seatIndex]
        val style = seat.style
        val opponents = state.seats.indices.count { it != seatIndex && state.seats[it].inHand }
        val equity = equity(seat.hole, state.board, opponents, random)

        // Reparto justo con N rivales: 1/(N+1). Los umbrales se escalan sobre él.
        val fair = 1.0 / (opponents + 1)
        val strength = equity + style.looseness
        val valueThreshold = fair + (1 - fair) * 0.22
        val raiseThreshold = fair + (1 - fair) * 0.38
        val roll = random.nextInt(1_000) / 1_000.0
        val pot = state.pot

        if (legal.callAmount == 0L) {
            return when {
                legal.canRaise && strength >= valueThreshold && roll < 0.35 + style.aggression * 0.6 ->
                    raise(state, legal, pot, if (strength >= raiseThreshold) 0.75 else 0.5, random)
                // Farol de vez en cuando, más en los bots agresivos.
                legal.canRaise && roll < style.aggression * 0.1 -> raise(state, legal, pot, 0.5, random)
                else -> PokerAction.Check
            }
        }

        val potOdds = legal.callAmount.toDouble() / (pot + legal.callAmount)
        return when {
            legal.canRaise && strength >= raiseThreshold && roll < style.aggression ->
                raise(state, legal, pot, 0.8, random)
            strength >= potOdds + 0.04 -> PokerAction.Call
            // Pagar algo barato de vez en cuando para no ser previsible.
            legal.callAmount <= state.rules.bigBlind * 2 && roll < 0.15 + style.looseness -> PokerAction.Call
            else -> PokerAction.Fold
        }
    }

    /** Sube una fracción del bote (redondeada a la unidad de ficha); si es casi todo, va all-in. */
    private fun raise(state: PokerState, legal: LegalActions, pot: Long, fraction: Double, random: RandomGenerator): PokerAction {
        val unit = state.rules.chipUnit
        val jitter = 0.85 + random.nextInt(31) / 100.0
        val wanted = state.currentBet + (pot * fraction * jitter).toLong()
        val rounded = (wanted / unit) * unit
        val to = rounded.coerceIn(legal.minRaiseTo, legal.maxRaiseTo)
        return if (to >= legal.maxRaiseTo * 0.7) PokerAction.RaiseTo(legal.maxRaiseTo) else PokerAction.RaiseTo(to)
    }

    /**
     * Equidad estimada por simulación: reparte al azar las comunitarias que faltan y dos cartas a
     * cada rival, y cuenta qué parte del bote se llevaría ([SIMULATIONS] repeticiones).
     */
    fun equity(hole: List<Card>, board: List<Card>, opponents: Int, random: RandomGenerator, simulations: Int = SIMULATIONS): Double {
        if (opponents == 0) return 1.0
        val known = hole + board
        val pool = standardDeck().filter { it !in known }.toMutableList()
        val needed = (5 - board.size) + opponents * 2
        var share = 0.0
        repeat(simulations) {
            // Fisher–Yates parcial: solo se barajan las cartas que hacen falta.
            for (i in 0 until needed) {
                val j = i + random.nextInt(pool.size - i)
                val tmp = pool[i]
                pool[i] = pool[j]
                pool[j] = tmp
            }
            val fullBoard = board + pool.subList(0, 5 - board.size)
            val mine = HandEvaluator.evaluate(hole + fullBoard)
            var best = true
            var ties = 0
            var offset = 5 - board.size
            for (o in 0 until opponents) {
                val theirs = HandEvaluator.evaluate(listOf(pool[offset], pool[offset + 1]) + fullBoard)
                offset += 2
                if (theirs > mine) {
                    best = false
                    break
                }
                if (theirs == mine) ties++
            }
            if (best) share += 1.0 / (ties + 1)
        }
        return share / simulations
    }
}

/** Mesa con el jugador y cinco bots; los bots sin fichas se sustituyen por otros entre manos. */
object PokerTable {

    const val SEATS: Int = 6

    private val NAMES = listOf("Valentina", "Bruno", "Carmen", "Hugo", "Lucía", "Mateo", "Elena", "Diego", "Sofía", "Álvaro", "Irene", "Marcos")

    fun create(rules: PokerRules, playerName: String, buyIn: Long, random: RandomGenerator): PokerState {
        val names = random.shuffled(NAMES)
        val seats = List(SEATS) { index ->
            if (index == 0) {
                Seat(name = playerName, isHuman = true, stack = buyIn)
            } else {
                newBot(rules, names[index - 1], random)
            }
        }
        return PokerState(rules = rules, seats = seats, button = random.nextInt(SEATS))
    }

    /** Sustituye a los bots que se han quedado sin fichas para la ciega grande. */
    fun refill(state: PokerState, random: RandomGenerator): PokerState {
        val used = state.seats.map { it.name }.toSet()
        val available = random.shuffled(NAMES.filter { it !in used }).toMutableList()
        return state.copy(
            seats = state.seats.map { seat ->
                if (!seat.isHuman && seat.stack < state.rules.bigBlind) newBot(state.rules, available.removeFirstOrNull() ?: seat.name, random) else seat
            },
        )
    }

    private fun newBot(rules: PokerRules, name: String, random: RandomGenerator): Seat {
        val steps = ((rules.maxBuyIn - rules.minBuyIn) / rules.chipUnit).toInt()
        val stack = rules.minBuyIn + random.nextInt(steps + 1) * rules.chipUnit
        val style = BotStyle.entries[random.nextInt(BotStyle.entries.size)]
        return Seat(name = name, isHuman = false, stack = stack, style = style)
    }
}
