package com.royalchance.engine.roulette

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.random.ScriptedRandomGenerator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RouletteEngineTest {

    private val rules = RouletteRules()

    /** Todas las apuestas posibles del tapete. */
    private val allBets: List<RouletteBet> = buildList {
        (0..36).forEach { add(RouletteBet.Straight(it)) }
        for (a in 0..36) for (b in a + 1..36) RouletteBet.Split(a, b).takeIf { it.isValid }?.let(::add)
        (1..12).forEach { add(RouletteBet.Street(it)) }
        (1..32).forEach { n -> RouletteBet.Corner(n).takeIf { it.isValid }?.let(::add) }
        (1..11).forEach { add(RouletteBet.SixLine(it)) }
        (1..3).forEach { add(RouletteBet.Column(it)); add(RouletteBet.Dozen(it)) }
        addAll(listOf(RouletteBet.Red, RouletteBet.Black, RouletteBet.Even, RouletteBet.Odd, RouletteBet.Low, RouletteBet.High))
    }

    @Test
    fun theWheelHasEveryNumberOnceAndAlternatesColors() {
        assertEquals((0..36).toSet(), RouletteWheel.ORDER.toSet())
        assertEquals(37, RouletteWheel.ORDER.size)
        RouletteWheel.ORDER.drop(1).zipWithNext().forEach { (a, b) ->
            assertTrue(RouletteWheel.colorOf(a) != RouletteWheel.colorOf(b), "$a y $b son del mismo color")
        }
        assertEquals(18, (1..36).count { RouletteWheel.colorOf(it) == PocketColor.Red })
    }

    @Test
    fun theTableHasTheStandardNumberOfBets() {
        // 60 caballos (57 en el tapete + 3 con el 0) y 22 cuadros.
        assertEquals(60, allBets.count { it is RouletteBet.Split })
        assertEquals(22, allBets.count { it is RouletteBet.Corner })
    }

    @Test
    fun invalidPositionsCoverNothing() {
        listOf(
            RouletteBet.Straight(37),
            RouletteBet.Split(3, 4), // distinta fila
            RouletteBet.Split(0, 4),
            RouletteBet.Split(5, 5),
            RouletteBet.Street(13),
            RouletteBet.Corner(3), // última columna
            RouletteBet.Corner(33),
            RouletteBet.SixLine(12),
            RouletteBet.Column(4),
            RouletteBet.Dozen(0),
        ).forEach { assertFalse(it.isValid, "$it no debería ser válida") }
    }

    @Test
    fun eachBetPaysItsOdds() {
        val cases = mapOf(
            RouletteBet.Straight(17) to 360L,
            RouletteBet.Split(17, 20) to 180L,
            RouletteBet.Street(6) to 120L,
            RouletteBet.Corner(16) to 90L,
            RouletteBet.SixLine(5) to 60L,
            RouletteBet.Column(2) to 30L,
            RouletteBet.Dozen(2) to 30L,
            RouletteBet.Black to 20L,
            RouletteBet.Odd to 20L,
            RouletteBet.Low to 20L,
        )
        cases.forEach { (bet, payout) -> assertEquals(payout, bet.payoutFor(10, 17), "$bet") }
        assertEquals(0, RouletteBet.Red.payoutFor(10, 17))
    }

    @Test
    fun withZeroEvenMoneyBetsLoseAndOnlyZeroBetsWin() {
        val bets = listOf(RouletteBet.Red, RouletteBet.Even, RouletteBet.Low, RouletteBet.Dozen(1), RouletteBet.Straight(0), RouletteBet.Split(0, 2))
            .map { PlacedBet(it, 100) }

        val spin = RouletteEngine.settle(bets, 0)

        assertEquals(listOf(0L, 0L, 0L, 0L, 3_600L, 1_800L), spin.results.map { it.payout })
    }

    @Test
    fun everyBetReturnsThirtySixThirtySeventhsOnAverage() {
        // La ventaja de la casa es 1/37 en todas las apuestas: en los 37 resultados posibles, cada
        // una devuelve exactamente 36 veces lo apostado.
        allBets.forEach { bet ->
            val returned = (0..36).sumOf { bet.payoutFor(10, it) }
            assertEquals(360L, returned, "$bet")
        }
    }

    @Test
    fun theSpinUsesTheRandomNumberAndAddsUpTheBets() {
        val bets = listOf(PlacedBet(RouletteBet.Straight(7), 50), PlacedBet(RouletteBet.Red, 100), PlacedBet(RouletteBet.High, 30))

        val spin = assertIs<Outcome.Success<RouletteSpin>>(RouletteEngine.spin(rules, bets, ScriptedRandomGenerator(7))).value

        assertEquals(7, spin.number)
        assertEquals(180, spin.totalStake)
        assertEquals(1_800 + 200, spin.totalPayout)
    }

    @Test
    fun betsAreValidatedBeforeSpinning() {
        val random = ScriptedRandomGenerator()
        assertEquals(Outcome.Failure(RouletteError.NoBets), RouletteEngine.spin(rules, emptyList(), random))
        assertEquals(Outcome.Failure(RouletteError.InvalidBet), RouletteEngine.spin(rules, listOf(PlacedBet(RouletteBet.Red, 5)), random))
        assertEquals(Outcome.Failure(RouletteError.InvalidBet), RouletteEngine.spin(rules, listOf(PlacedBet(RouletteBet.Red, 15)), random))
        assertEquals(Outcome.Failure(RouletteError.InvalidBet), RouletteEngine.spin(rules, listOf(PlacedBet(RouletteBet.Corner(3), 10)), random))
        assertEquals(
            Outcome.Failure(RouletteError.AboveTableMaximum),
            RouletteEngine.spin(rules, listOf(PlacedBet(RouletteBet.Red, 20_000), PlacedBet(RouletteBet.Black, 5_010)), random),
        )
        assertEquals(0, random.remaining)
    }

    @Test
    fun theMaximumPayoutFitsTheEconomyCeiling() {
        // El pago máximo de un giro (todo a un pleno) es 36 veces lo apostado: muy por debajo del
        // techo de 1.000× que comprueba el servidor.
        val spin = RouletteEngine.settle(listOf(PlacedBet(RouletteBet.Straight(5), rules.maximumTotalBet)), 5)
        assertEquals(36 * rules.maximumTotalBet, spin.totalPayout)
    }

    @Test
    fun betsSurviveSerialization() {
        val serializer = ListSerializer(PlacedBet.serializer())
        val bets = allBets.map { PlacedBet(it, 10) }
        assertEquals(bets, Json.decodeFromString(serializer, Json.encodeToString(serializer, bets)))
    }
}
