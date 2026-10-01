package com.royalchance.engine.slots

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.random.ScriptedRandomGenerator
import com.royalchance.engine.slots.SlotSymbol.Bar
import com.royalchance.engine.slots.SlotSymbol.Cherry
import com.royalchance.engine.slots.SlotSymbol.Club
import com.royalchance.engine.slots.SlotSymbol.Diamond
import com.royalchance.engine.slots.SlotSymbol.Heart
import com.royalchance.engine.slots.SlotSymbol.Seven
import com.royalchance.engine.slots.SlotSymbol.Spade
import com.royalchance.engine.slots.SlotSymbol.Wild
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlotEngineTest {

    @Test
    fun everyReelHasTheSameSymbolFrequencies() {
        val expected = mapOf(Cherry to 8, Club to 5, Heart to 5, Spade to 4, Diamond to 3, Bar to 2, Seven to 2, Wild to 3)
        SlotMachine.STRIPS.forEach { strip ->
            assertEquals(32, strip.size)
            assertEquals(expected, strip.groupingBy { it }.eachCount())
        }
    }

    @Test
    fun theTheoreticalReturnIsAboutNinetySevenPercent() {
        // Cada celda de una línea muestra un símbolo con la frecuencia de su tira, y los rodillos
        // son independientes: se recorren las 8⁵ combinaciones de una línea con su probabilidad.
        // Todas las líneas tienen la misma esperanza, así que el retorno total es el de una línea.
        val frequencies = SlotMachine.STRIPS.map { strip -> strip.groupingBy { it }.eachCount().mapValues { it.value / 32.0 } }
        var expected = 0.0
        var hits = 0.0
        fun visit(reel: Int, symbols: List<SlotSymbol>, probability: Double) {
            if (reel == SlotMachine.REELS) {
                val win = SlotMachine.evaluateLine(symbols) ?: return
                expected += probability * SlotMachine.pay(win.first, win.second)
                hits += probability
                return
            }
            frequencies[reel].forEach { (symbol, p) -> visit(reel + 1, symbols + symbol, probability * p) }
        }
        visit(0, emptyList(), 1.0)

        assertTrue(abs(expected - 0.9727) < 0.0005, "retorno $expected")
        assertTrue(hits in 0.15..0.18, "frecuencia de premio por línea $hits")
    }

    @Test
    fun linesPayTheLongestRunFromTheLeft() {
        assertEquals(Cherry to 2, SlotMachine.evaluateLine(listOf(Cherry, Cherry, Club, Cherry, Cherry)))
        assertEquals(Seven to 4, SlotMachine.evaluateLine(listOf(Seven, Wild, Seven, Seven, Bar)))
        assertEquals(Club to 5, SlotMachine.evaluateLine(listOf(Wild, Club, Wild, Club, Club)))
        assertNull(SlotMachine.evaluateLine(listOf(Club, Club, Heart, Club, Club)))
        assertNull(SlotMachine.evaluateLine(listOf(Heart, Club, Club, Club, Club)))
    }

    @Test
    fun aRunOfWildsPaysItselfWhenWorthMore() {
        // Tres comodines (30) pagan más que cuatro tréboles (12).
        assertEquals(Wild to 3, SlotMachine.evaluateLine(listOf(Wild, Wild, Wild, Club, Spade)))
        // Cinco sietes con comodines (300) pagan más que dos comodines (0).
        assertEquals(Seven to 5, SlotMachine.evaluateLine(listOf(Wild, Wild, Seven, Seven, Seven)))
        assertEquals(Wild to 5, SlotMachine.evaluateLine(List(5) { Wild }))
    }

    @Test
    fun aSpinAddsUpEveryWinningLine() {
        // Parada 0 en cada tira: filas = posiciones 0, 1 y 2 de cada rodillo.
        val spin = SlotEngine.settle(listOf(0, 0, 0, 0, 0), lineBet = 10)
        val window = spin.window

        SlotMachine.LINES.forEachIndexed { index, rows ->
            val symbols = rows.mapIndexed { reel, row -> window[reel][row] }
            val win = spin.wins.firstOrNull { it.line == index }
            assertEquals(SlotMachine.evaluateLine(symbols)?.let { SlotMachine.pay(it.first, it.second) * 10 }, win?.payout)
        }
        assertEquals(spin.wins.sumOf { it.payout }, spin.totalPayout)
        assertEquals(100, spin.totalBet)
    }

    @Test
    fun theStopsComeFromTheRandomGeneratorAndWrapAround() {
        val result = SlotEngine.spin(SlotRules(), lineBet = 5, random = ScriptedRandomGenerator(31, 0, 7, 30, 15))
        val spin = assertIs<Outcome.Success<SlotSpin>>(result).value

        assertEquals(listOf(31, 0, 7, 30, 15), spin.stops)
        assertEquals(SlotMachine.STRIPS[0][31], spin.window[0][0])
        assertEquals(SlotMachine.STRIPS[0][0], spin.window[0][1])
        assertEquals(SlotMachine.STRIPS[3][0], spin.window[3][2])
    }

    @Test
    fun onlyTheTableBetsAreAccepted() {
        assertEquals(Outcome.Failure(SlotError.InvalidBet), SlotEngine.spin(SlotRules(), 3, ScriptedRandomGenerator()))
        assertEquals(listOf(10L, 20L, 50L, 100L, 200L, 500L, 1_000L, 2_500L, 5_000L), SlotRules().totalBets)
    }

    @Test
    fun theBestPossibleSpinStaysUnderTheEconomyCeiling() {
        // Cinco comodines en todas las líneas: 500 × apuesta por línea × 10 = 500 × apuesta total,
        // por debajo del techo de 1.000× que comprueba el servidor.
        val best = SlotMachine.PAYTABLE.values.maxOf { it.max() } * SlotMachine.LINES.size
        assertTrue(best / SlotMachine.LINES.size <= 1_000)
    }
}
