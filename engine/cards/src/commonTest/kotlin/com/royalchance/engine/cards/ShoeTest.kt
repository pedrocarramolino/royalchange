package com.royalchance.engine.cards

import com.royalchance.core.testing.random.TestRandomGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShoeTest {

    @Test
    fun aDeckHasEveryCardOnce() {
        val deck = standardDeck()
        assertEquals(52, deck.size)
        assertEquals(52, deck.toSet().size)
    }

    @Test
    fun sixDeckShoeContainsSixOfEachCardAndCutsAtThePenetration() {
        val shoe = Shoe.shuffled(decks = 6, penetration = 0.75, random = TestRandomGenerator(seed = 1))

        assertEquals(312, shoe.cards.size)
        assertTrue(shoe.cards.groupingBy { it }.eachCount().values.all { it == 6 })
        assertEquals(234, shoe.cutIndex)
        assertFalse(shoe.needsShuffle)
    }

    @Test
    fun drawingAdvancesUntilTheCutCard() {
        var shoe = Shoe(cards = standardDeck(), cutIndex = 2)
        val (first, afterFirst) = shoe.draw()
        assertEquals(Card(Rank.Two, Suit.Spades), first)
        shoe = afterFirst.draw().second

        assertEquals(50, shoe.remaining)
        assertTrue(shoe.needsShuffle)
    }

    @Test
    fun anEmptyShoeCannotDeal() {
        val empty = Shoe(cards = standardDeck().take(1), cutIndex = 1, position = 1)
        assertFailsWith<IllegalStateException> { empty.draw() }
    }
}
