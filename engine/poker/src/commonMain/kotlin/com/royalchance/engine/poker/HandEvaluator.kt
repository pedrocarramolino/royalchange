package com.royalchance.engine.poker

import com.royalchance.engine.cards.Card

/** Categorías de mano, de menor a mayor. */
enum class HandCategory {
    HighCard,
    OnePair,
    TwoPair,
    ThreeOfAKind,
    Straight,
    Flush,
    FullHouse,
    FourOfAKind,
    StraightFlush,
}

/**
 * Valor de la mejor mano de cinco cartas: mayor [score] gana; iguales empatan. El valor codifica la
 * categoría y hasta cinco cartas de desempate (4 bits cada una).
 */
data class HandValue(val score: Int) : Comparable<HandValue> {
    val category: HandCategory get() = HandCategory.entries[score ushr 20]

    override fun compareTo(other: HandValue): Int = score.compareTo(other.score)
}

/**
 * Evalúa la mejor mano de cinco cartas entre 5, 6 o 7 cartas, sin probar las 21 combinaciones:
 * cuenta valores y palos y busca escaleras con máscaras de bits. Es lo bastante rápido para que los
 * bots simulen cientos de manos por decisión.
 */
object HandEvaluator {

    fun evaluate(cards: List<Card>): HandValue {
        require(cards.size in 5..7) { "Se evalúan de 5 a 7 cartas: ${cards.size}" }
        val counts = IntArray(15)
        val suitCounts = IntArray(4)
        val suitMasks = IntArray(4)
        var mask = 0
        for (card in cards) {
            val value = card.value
            counts[value]++
            suitCounts[card.suit.ordinal]++
            suitMasks[card.suit.ordinal] = suitMasks[card.suit.ordinal] or (1 shl value)
            mask = mask or (1 shl value)
        }

        val flushSuit = suitCounts.indexOfFirst { it >= 5 }
        if (flushSuit >= 0) {
            straightHigh(suitMasks[flushSuit])?.let { return value(HandCategory.StraightFlush, it) }
        }

        var quads = 0
        val trips = ArrayList<Int>(2)
        val pairs = ArrayList<Int>(3)
        val singles = ArrayList<Int>(7)
        for (rank in 14 downTo 2) {
            when (counts[rank]) {
                4 -> quads = rank
                3 -> trips += rank
                2 -> pairs += rank
                1 -> singles += rank
            }
        }

        if (quads > 0) {
            val kicker = (14 downTo 2).first { it != quads && counts[it] > 0 }
            return value(HandCategory.FourOfAKind, quads, kicker)
        }
        if (trips.isNotEmpty() && (trips.size >= 2 || pairs.isNotEmpty())) {
            // Con dos tríos, el segundo hace de pareja.
            val pair = maxOf(trips.getOrElse(1) { 0 }, pairs.firstOrNull() ?: 0)
            return value(HandCategory.FullHouse, trips[0], pair)
        }
        if (flushSuit >= 0) {
            val ranks = (14 downTo 2).filter { suitMasks[flushSuit] and (1 shl it) != 0 }.take(5)
            return value(HandCategory.Flush, *ranks.toIntArray())
        }
        straightHigh(mask)?.let { return value(HandCategory.Straight, it) }
        if (trips.isNotEmpty()) {
            return value(HandCategory.ThreeOfAKind, trips[0], *singles.take(2).toIntArray())
        }
        if (pairs.size >= 2) {
            // La tercera pareja, si la hay, puede ser el mejor desempate.
            val kicker = (pairs.drop(2) + singles).maxOrNull() ?: 0
            return value(HandCategory.TwoPair, pairs[0], pairs[1], kicker)
        }
        if (pairs.size == 1) {
            return value(HandCategory.OnePair, pairs[0], *singles.take(3).toIntArray())
        }
        return value(HandCategory.HighCard, *singles.take(5).toIntArray())
    }

    /** Carta más alta de la mejor escalera en [mask] (bit = valor), o `null`. El as cuenta como 1. */
    private fun straightHigh(mask: Int): Int? {
        val withLowAce = if (mask and (1 shl 14) != 0) mask or (1 shl 1) else mask
        for (high in 14 downTo 5) {
            val run = 0b11111 shl (high - 4)
            if (withLowAce and run == run) return high
        }
        return null
    }

    private fun value(category: HandCategory, vararg ranks: Int): HandValue {
        var score = category.ordinal
        for (i in 0 until 5) score = (score shl 4) or ranks.getOrElse(i) { 0 }
        return HandValue(score)
    }
}

/** Valor numérico de la carta: 2–14 (el as vale 14). */
val Card.value: Int get() = rank.ordinal + 2
