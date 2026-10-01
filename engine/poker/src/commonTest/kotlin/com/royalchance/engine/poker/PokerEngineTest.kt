package com.royalchance.engine.poker

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.random.TestRandomGenerator
import com.royalchance.engine.cards.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PokerEngineTest {

    private val rules = PokerRules()

    /** "Baraja" que no baraja: deja las cartas en el orden dado, seguidas del resto. */
    private class StackedDeck(private val first: List<Card>) : RandomGenerator {
        override fun nextInt(until: Int): Int = 0

        @Suppress("UNCHECKED_CAST")
        override fun <T> shuffled(items: List<T>): List<T> = (first + (items as List<Card>).filter { it !in first }) as List<T>
    }

    private fun table(vararg stacks: Long, button: Int = 0) = PokerState(
        rules = rules,
        seats = stacks.mapIndexed { i, stack -> Seat(name = "P$i", isHuman = i == 0, stack = stack) },
        button = button,
    )

    private fun PokerState.start(deck: List<Card> = emptyList()): PokerState =
        assertIs<Outcome.Success<PokerState>>(PokerEngine.startHand(this, StackedDeck(deck))).value

    private fun PokerState.act(action: PokerAction): PokerState {
        val seat = toAct ?: fail("Nadie tiene el turno")
        return when (val result = PokerEngine.apply(this, seat, action)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> fail("${seats[seat].name} no puede $action: ${result.error}")
        }
    }

    @Test
    fun blindsArePostedAndTheNextPlayerActs() {
        val state = table(1_000, 1_000, 1_000, 1_000).start()

        assertEquals(10, state.seats[1].bet) // ciega pequeña a la izquierda del botón
        assertEquals(20, state.seats[2].bet)
        assertEquals(3, state.toAct)
        assertEquals(30, state.pot)
        state.seats.forEach { assertEquals(2, it.hole.size) }
        assertEquals(52 - 8, state.deck.size)
    }

    @Test
    fun headsUpTheButtonPostsTheSmallBlindAndActsFirst() {
        val state = table(1_000, 1_000).start()

        assertEquals(10, state.seats[0].bet)
        assertEquals(20, state.seats[1].bet)
        assertEquals(0, state.toAct)

        // Tras el flop habla primero el que no tiene el botón.
        val flop = state.act(PokerAction.Call).act(PokerAction.Check)
        assertEquals(Street.Flop, flop.street)
        assertEquals(3, flop.board.size)
        assertEquals(1, flop.toAct)
    }

    @Test
    fun ifEveryoneFoldsTheLastPlayerWinsThePot() {
        val state = table(1_000, 1_000, 1_000).start()
            .act(PokerAction.Fold) // botón
            .act(PokerAction.Fold) // ciega pequeña

        assertEquals(PokerPhase.HandOver, state.phase)
        assertEquals(listOf(1_000L, 990L, 1_010L), state.seats.map { it.stack })
        assertEquals(PotAward(30, listOf(2), null), state.awards.single())
    }

    @Test
    fun theBigBlindGetsTheOptionAndRaisesMustBeAtLeastTheLastRaise() {
        var state = table(1_000, 1_000, 1_000).start()
            .act(PokerAction.Call)
            .act(PokerAction.Call)
        assertEquals(2, state.toAct)
        assertTrue(assertNotNullLegal(state).canCheck)

        state = state.act(PokerAction.RaiseTo(60)) // sube 40
        val legal = assertNotNullLegal(state)
        assertEquals(40, legal.callAmount)
        assertEquals(100, legal.minRaiseTo)
        assertIs<Outcome.Failure<PokerError>>(PokerEngine.apply(state, state.toAct!!, PokerAction.RaiseTo(90)))
        assertIs<Outcome.Failure<PokerError>>(PokerEngine.apply(state, state.toAct!!, PokerAction.Check))
    }

    private fun assertNotNullLegal(state: PokerState) = state.legalActions(state.toAct!!) ?: fail("Sin acciones")

    @Test
    fun theBestHandWinsAtShowdown() {
        // Reparto desde la izquierda del botón (P1, P2, P0) dos vueltas; después flop, turn y river.
        val deck = cards("AS 2C KD AH 7C KH QS 8D JC 9H 3S")
        val state = table(1_000, 1_000, 1_000).start(deck).playToShowdownCheckingDown()

        assertTrue(state.showdown)
        val award = state.awards.single()
        assertEquals(listOf(1), award.winners) // P1 con pareja de ases
        assertEquals(HandCategory.OnePair, award.category)
        assertEquals(1_040, state.seats[1].stack)
    }

    private fun PokerState.playToShowdownCheckingDown(): PokerState {
        var state = this
        while (state.phase == PokerPhase.Betting) {
            state = state.act(PokerAction.Call)
        }
        return state
    }

    @Test
    fun allInsCreateSidePots() {
        // Botón en P2: P0 pone la pequeña, P1 la grande y habla P2, que va all-in; P0 (300) y
        // P1 (600) pagan con todo. P0 tiene ases, P1 reyes y P2 nada.
        val deck = cards("AS KS 2C AH KH 3C QD 8D 4S 9H 7C")
        var state = table(300, 600, 1_000, button = 2).start(deck)
        assertEquals(2, state.toAct)
        state = generateSequence(state) { s ->
            if (s.phase != PokerPhase.Betting) {
                null
            } else {
                val seat = s.toAct!!
                val legal = s.legalActions(seat)!!
                s.act(if (legal.canRaise) PokerAction.RaiseTo(legal.maxRaiseTo) else PokerAction.Call)
            }
        }.last()

        assertEquals(PokerPhase.HandOver, state.phase)
        // Bote principal 900 (300 × 3) para P0; lateral 600 (300 × 2) para P1; P2 recupera 400.
        assertEquals(listOf(900L, 600L, 400L), state.awards.map { it.amount })
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), state.awards.map { it.winners })
        assertEquals(listOf(900L, 600L, 400L), state.seats.map { it.stack })
    }

    @Test
    fun aSplitPotGivesTheOddChipToTheFirstWinnerLeftOfTheButton() {
        // P3 y P0 pagan la grande, P1 (ciega pequeña) se retira y P2 pasa: bote de 70 para tres que
        // juegan la escalera de la mesa. 7 unidades de 10 entre 3: 3 para P2 (el primero a la
        // izquierda del botón que gana) y 2 para P3 y P0.
        val deck = cards("2C 3C 4C 5C 2D 3D 4D 5D 10S JH QD KC AS")
        val state = table(1_000, 1_000, 1_000, 1_000, button = 0).start(deck)
            .act(PokerAction.Call)
            .act(PokerAction.Call)
            .act(PokerAction.Fold)
            .playToShowdownCheckingDown()

        val award = state.awards.single()
        assertEquals(70, award.amount)
        assertEquals(listOf(2, 3, 0), award.winners)
        assertEquals(listOf(1_000L, 990L, 1_010L, 1_000L), state.seats.map { it.stack })
    }

    @Test
    fun committedChipsAreKeptAfterTheHandForAccounting() {
        val state = table(1_000, 1_000, 1_000).start().act(PokerAction.RaiseTo(100)).act(PokerAction.Fold).act(PokerAction.Fold)

        assertEquals(PokerPhase.HandOver, state.phase)
        assertEquals(listOf(100L, 10L, 20L), state.seats.map { it.committed })
        assertEquals(1_030, state.seats[0].stack)
    }

    @Test
    fun equityRanksHandsSensibly() {
        val random = TestRandomGenerator(seed = 5)
        val aces = PokerBot.equity(cards("AS AH"), emptyList(), 1, random, simulations = 2_000)
        val rags = PokerBot.equity(cards("7C 2D"), emptyList(), 1, random, simulations = 2_000)
        val made = PokerBot.equity(cards("9S 9H"), cards("9D 9C 2S"), 3, random, simulations = 500)
        assertTrue(aces in 0.80..0.89, "AA $aces")
        assertTrue(rags in 0.28..0.40, "72o $rags")
        assertTrue(made > 0.97, "póker servido $made")
    }

    @Test
    fun aShortBigBlindAllInStillLetsOthersAct() {
        val state = table(1_000, 1_000, 10).start()
        assertEquals(10, state.seats[2].committed)
        assertTrue(state.seats[2].allIn)
        assertEquals(0, state.toAct)
        assertNull(state.legalActions(2))
    }

    @Test
    fun botsPlayHundredsOfHandsWithoutLosingAChip() {
        // Seis bots con la semilla fija: las fichas se conservan, son múltiplos de 10 y nadie queda
        // en negativo; cada mano termina.
        val random = TestRandomGenerator(seed = 11)
        var state = PokerTable.create(rules, "Bot", 2_000, random).let { it.copy(seats = it.seats.map { s -> s.copy(isHuman = false) }) }
        var total = state.seats.sumOf { it.stack }
        repeat(200) {
            state = PokerTable.refill(state, random)
            total = state.seats.sumOf { it.stack }
            state = when (val started = PokerEngine.startHand(state, random)) {
                is Outcome.Success -> started.value
                is Outcome.Failure -> fail("No se pudo repartir: ${started.error}")
            }
            var steps = 0
            while (state.phase == PokerPhase.Betting) {
                val seat = state.toAct!!
                val action = PokerBot.decide(state, seat, random)
                state = when (val result = PokerEngine.apply(state, seat, action)) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> fail("El bot eligió una acción ilegal: $action (${result.error})")
                }
                assertTrue(++steps < 200, "La mano no termina")
            }
            assertEquals(total, state.seats.sumOf { it.stack })
            state.seats.forEach {
                assertTrue(it.stack >= 0 && it.stack % 10 == 0L, "Pila inválida: ${it.stack}")
            }
        }
    }
}
