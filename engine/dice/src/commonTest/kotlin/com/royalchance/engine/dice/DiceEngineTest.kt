package com.royalchance.engine.dice

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.random.ScriptedRandomGenerator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiceEngineTest {

    private val rules = DiceRules()
    private val allRolls = (1..6).flatMap { a -> (1..6).map { b -> DiceRoll(a, b) } }

    @Test
    fun everyBetReturnsBetweenNinetyFiveAndNinetySevenPercent() {
        // Recorre las 36 tiradas posibles, todas equiprobables.
        ALL_DICE_BETS.forEach { bet ->
            val returned = allRolls.sumOf { bet.payoutFor(100, it) } / 36.0 / 100.0
            assertTrue(returned in 0.95..0.975, "$bet devuelve $returned")
        }
    }

    @Test
    fun betsWinOnTheirSums() {
        assertTrue(DiceBet.Low.wins(DiceRoll(2, 4)))
        assertFalse(DiceBet.Low.wins(DiceRoll(3, 4)))
        assertFalse(DiceBet.High.wins(DiceRoll(3, 4)))
        assertTrue(DiceBet.High.wins(DiceRoll(6, 2)))
        assertTrue(DiceBet.Seven.wins(DiceRoll(1, 6)))
        assertTrue(DiceBet.Doubles.wins(DiceRoll(5, 5)))
        assertFalse(DiceBet.Doubles.wins(DiceRoll(5, 4)))
        assertTrue(DiceBet.Sum(11).wins(DiceRoll(5, 6)))
    }

    @Test
    fun payoutsIncludeTheStakeAndAreWholeChips() {
        assertEquals(23, DiceBet.Low.payoutFor(10, DiceRoll(1, 1)))
        assertEquals(58, DiceBet.Seven.payoutFor(10, DiceRoll(3, 4)))
        assertEquals(350, DiceBet.Sum(2).payoutFor(10, DiceRoll(1, 1)))
        assertEquals(0, DiceBet.High.payoutFor(10, DiceRoll(1, 1)))
    }

    @Test
    fun theRollUsesTwoDice() {
        val bets = listOf(PlacedDiceBet(DiceBet.Doubles, 50), PlacedDiceBet(DiceBet.High, 100))

        val thrown = assertIs<Outcome.Success<DiceThrow>>(DiceEngine.roll(rules, bets, ScriptedRandomGenerator(3, 3))).value

        assertEquals(DiceRoll(4, 4), thrown.roll) // nextInt(1, 7) = 1 + valor
        assertEquals(150, thrown.totalStake)
        assertEquals(290 + 230, thrown.totalPayout)
    }

    @Test
    fun betsAreValidatedBeforeRolling() {
        val random = ScriptedRandomGenerator()
        assertEquals(Outcome.Failure(DiceError.NoBets), DiceEngine.roll(rules, emptyList(), random))
        assertEquals(Outcome.Failure(DiceError.InvalidBet), DiceEngine.roll(rules, listOf(PlacedDiceBet(DiceBet.Sum(7), 10)), random))
        assertEquals(Outcome.Failure(DiceError.InvalidBet), DiceEngine.roll(rules, listOf(PlacedDiceBet(DiceBet.Low, 15)), random))
        assertEquals(
            Outcome.Failure(DiceError.AboveTableMaximum),
            DiceEngine.roll(rules, listOf(PlacedDiceBet(DiceBet.Low, 25_000), PlacedDiceBet(DiceBet.High, 10)), random),
        )
        assertEquals(0, random.remaining)
    }

    @Test
    fun betsSurviveSerialization() {
        val serializer = ListSerializer(PlacedDiceBet.serializer())
        val bets = ALL_DICE_BETS.map { PlacedDiceBet(it, 10) }
        assertEquals(bets, Json.decodeFromString(serializer, Json.encodeToString(serializer, bets)))
    }
}
