package com.royalchance.engine.poker

import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Suit
import com.royalchance.engine.cards.standardDeck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "AS KH 10D" → cartas (S picas, H corazones, D diamantes, C tréboles). */
internal fun cards(codes: String): List<Card> = codes.split(" ").map { code ->
    Card(Rank.entries.first { it.symbol == code.dropLast(1) }, Suit.entries.first { it.name.first() == code.last() })
}

class HandEvaluatorTest {

    private fun eval(codes: String) = HandEvaluator.evaluate(cards(codes))

    @Test
    fun recognisesEveryCategory() {
        assertEquals(HandCategory.StraightFlush, eval("10H JH QH KH AH 2C 3D").category)
        assertEquals(HandCategory.FourOfAKind, eval("9S 9H 9D 9C AH 2C 3D").category)
        assertEquals(HandCategory.FullHouse, eval("9S 9H 9D KC KH 2C 3D").category)
        assertEquals(HandCategory.Flush, eval("2H 7H 9H JH KH 2C 3D").category)
        assertEquals(HandCategory.Straight, eval("5S 6H 7D 8C 9H 2C KD").category)
        assertEquals(HandCategory.ThreeOfAKind, eval("9S 9H 9D 4C AH 2C KD").category)
        assertEquals(HandCategory.TwoPair, eval("9S 9H 4D 4C AH 2C KD").category)
        assertEquals(HandCategory.OnePair, eval("9S 9H 4D 5C AH 2C KD").category)
        assertEquals(HandCategory.HighCard, eval("9S JH 4D 5C AH 2C KD").category)
    }

    @Test
    fun theAceCanStartAStraight() {
        val wheel = eval("AS 2H 3D 4C 5H KD QS")
        val sixHigh = eval("2H 3D 4C 5H 6S KD QS")
        assertEquals(HandCategory.Straight, wheel.category)
        assertTrue(sixHigh > wheel)
        assertEquals(HandCategory.StraightFlush, eval("AH 2H 3H 4H 5H 9C 9D").category)
    }

    @Test
    fun kickersBreakTies() {
        assertTrue(eval("AS AH KD 7C 4H 3D 2S") > eval("AS AH QD 7C 4H 3D 2S"))
        // Doble pareja: el desempate puede ser la tercera pareja.
        assertTrue(eval("KS KH 9D 9C 8H 8D 2S") > eval("KS KH 9D 9C 7H 6D 2S"))
        // Full con dos tríos: el menor hace de pareja.
        assertEquals(eval("KS KH KD 9C 9H 9D 2S"), eval("KS KH KD 9C 9H 2D 3S"))
        // Las cartas que no entran en las cinco mejores no cuentan.
        assertEquals(eval("AS AH KD QC JH 3D 2S"), eval("AS AH KD QC JH 4D 2S"))
        // Color: se comparan las cinco cartas del palo.
        assertTrue(eval("AH KH 9H 6H 3H 2C 2D") > eval("AH KH 9H 6H 2H 3C 4D"))
    }

    @Test
    fun allFiveCardHandsHaveTheirKnownFrequencies() {
        // Las 2.598.960 manos de cinco cartas: frecuencias de cada categoría y 7.462 valores distintos.
        val deck = standardDeck()
        val counts = IntArray(HandCategory.entries.size)
        val distinct = HashSet<Int>()
        val hand = ArrayList<Card>(5)
        for (a in 0 until 48) for (b in a + 1 until 49) for (c in b + 1 until 50) for (d in c + 1 until 51) for (e in d + 1 until 52) {
            hand.clear()
            hand += deck[a]; hand += deck[b]; hand += deck[c]; hand += deck[d]; hand += deck[e]
            val value = HandEvaluator.evaluate(hand)
            counts[value.category.ordinal]++
            distinct += value.score
        }
        assertEquals(
            listOf(1_302_540, 1_098_240, 123_552, 54_912, 10_200, 5_108, 3_744, 624, 40),
            counts.toList(),
        )
        assertEquals(7_462, distinct.size)
    }
}
