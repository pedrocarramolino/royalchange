package com.royalchance.engine.cards

import com.royalchance.core.common.random.RandomGenerator
import kotlinx.serialization.Serializable

/** Palos de la baraja francesa. Los nombres son estables (se guardan). */
enum class Suit {
    Spades,
    Hearts,
    Diamonds,
    Clubs,
    ;

    val isRed: Boolean get() = this == Hearts || this == Diamonds
}

/** Valores, del 2 al as. [symbol] es lo que se pinta en la carta. */
enum class Rank(val symbol: String) {
    Two("2"),
    Three("3"),
    Four("4"),
    Five("5"),
    Six("6"),
    Seven("7"),
    Eight("8"),
    Nine("9"),
    Ten("10"),
    Jack("J"),
    Queen("Q"),
    King("K"),
    Ace("A"),
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    override fun toString(): String = "${rank.symbol}${suit.name.first()}"
}

/** Una baraja de 52 cartas en orden fijo. */
fun standardDeck(): List<Card> = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(rank, suit) } }

/**
 * Zapato de varias barajas. Se baraja entero con Fisher–Yates ([RandomGenerator.shuffled]) y se
 * reparte desde el principio. Al pasar la carta de corte ([cutIndex]) se termina la ronda en
 * curso y se baraja de nuevo antes de la siguiente, como en un casino.
 *
 * Inmutable: robar devuelve un zapato nuevo. Así el estado de una partida se guarda y se reanuda.
 */
@Serializable
data class Shoe(
    val cards: List<Card>,
    val cutIndex: Int,
    val position: Int = 0,
) {
    init {
        require(cutIndex in 1..cards.size) { "Carta de corte fuera del zapato: $cutIndex de ${cards.size}" }
        require(position in 0..cards.size) { "Posición fuera del zapato: $position" }
    }

    val remaining: Int get() = cards.size - position

    /** Se ha pasado la carta de corte: toca barajar antes de la próxima ronda. */
    val needsShuffle: Boolean get() = position >= cutIndex

    /** Roba la siguiente carta. Lanza [IllegalStateException] si el zapato está vacío. */
    fun draw(): Pair<Card, Shoe> {
        check(position < cards.size) { "Zapato vacío" }
        return cards[position] to copy(position = position + 1)
    }

    companion object {
        /**
         * @param penetration fracción del zapato que se reparte antes de barajar (0,75 = el 75 %).
         */
        fun shuffled(decks: Int, penetration: Double, random: RandomGenerator): Shoe {
            require(decks >= 1) { "Al menos una baraja" }
            require(penetration > 0.0 && penetration < 1.0) { "Penetración fuera de rango: $penetration" }
            val cards = random.shuffled(List(decks) { standardDeck() }.flatten())
            return Shoe(cards = cards, cutIndex = (cards.size * penetration).toInt())
        }
    }
}
