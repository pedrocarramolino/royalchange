package com.royalchance.engine.roulette

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Color de una casilla. */
enum class PocketColor { Green, Red, Black }

/** La rueda europea: 37 casillas (0–36). */
object RouletteWheel {

    const val POCKETS: Int = 37

    /** Orden de las casillas en la rueda, en el sentido de las agujas del reloj desde el 0. */
    val ORDER: List<Int> = listOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
    )

    private val RED: Set<Int> = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

    fun colorOf(number: Int): PocketColor = when {
        number == 0 -> PocketColor.Green
        number in RED -> PocketColor.Red
        else -> PocketColor.Black
    }

    /** Posición de [number] en [ORDER]. */
    fun indexOf(number: Int): Int = ORDER.indexOf(number)
}

/**
 * Apuesta de la ruleta europea. Cada una cubre un conjunto de números y paga
 * `36 / números cubiertos` veces lo apostado (apuesta incluida): pleno 35:1, caballo 17:1,
 * transversal 11:1, cuadro 8:1, seisena 5:1, columna y docena 2:1, sencillas 1:1. Con el 0 solo
 * gana lo que lo cubre: las sencillas pierden enteras.
 *
 * En el tapete, la fila `r` (1–12) contiene `3r − 2`, `3r − 1` y `3r`.
 */
@Serializable
sealed interface RouletteBet {

    /** Números que cubre; vacío si la apuesta no es válida. */
    val numbers: Set<Int>

    /** Pleno: un número (0–36). */
    @Serializable @SerialName("straight")
    data class Straight(val number: Int) : RouletteBet {
        override val numbers: Set<Int> get() = if (number in 0..36) setOf(number) else emptySet()
    }

    /** Caballo: dos números contiguos en el tapete, o el 0 con el 1, el 2 o el 3. */
    @Serializable @SerialName("split")
    data class Split(val first: Int, val second: Int) : RouletteBet {
        override val numbers: Set<Int>
            get() {
                val (low, high) = minOf(first, second) to maxOf(first, second)
                val valid = when {
                    low == 0 -> high in 1..3
                    low < 1 || high > 36 -> false
                    high - low == 3 -> true
                    high - low == 1 -> low % 3 != 0
                    else -> false
                }
                return if (valid) setOf(low, high) else emptySet()
            }
    }

    /** Transversal: los tres números de una fila (1–12). */
    @Serializable @SerialName("street")
    data class Street(val row: Int) : RouletteBet {
        override val numbers: Set<Int> get() = if (row in 1..12) rowNumbers(row) else emptySet()
    }

    /** Cuadro: cuatro números en cuadrado; [topLeft] es el menor y no puede estar en la última columna. */
    @Serializable @SerialName("corner")
    data class Corner(val topLeft: Int) : RouletteBet {
        override val numbers: Set<Int>
            get() = if (topLeft in 1..32 && topLeft % 3 != 0) setOf(topLeft, topLeft + 1, topLeft + 3, topLeft + 4) else emptySet()
    }

    /** Seisena: dos filas consecutivas; [row] es la primera (1–11). */
    @Serializable @SerialName("line")
    data class SixLine(val row: Int) : RouletteBet {
        override val numbers: Set<Int> get() = if (row in 1..11) rowNumbers(row) + rowNumbers(row + 1) else emptySet()
    }

    /** Columna (1–3): la 1 es 1, 4, 7… 34. */
    @Serializable @SerialName("column")
    data class Column(val index: Int) : RouletteBet {
        override val numbers: Set<Int> get() = if (index in 1..3) (index..36 step 3).toSet() else emptySet()
    }

    /** Docena (1–3): la 1 es 1–12. */
    @Serializable @SerialName("dozen")
    data class Dozen(val index: Int) : RouletteBet {
        override val numbers: Set<Int> get() = if (index in 1..3) (index * 12 - 11..index * 12).toSet() else emptySet()
    }

    @Serializable @SerialName("red")
    data object Red : RouletteBet {
        override val numbers: Set<Int> = (1..36).filter { RouletteWheel.colorOf(it) == PocketColor.Red }.toSet()
    }

    @Serializable @SerialName("black")
    data object Black : RouletteBet {
        override val numbers: Set<Int> = (1..36).filter { RouletteWheel.colorOf(it) == PocketColor.Black }.toSet()
    }

    @Serializable @SerialName("even")
    data object Even : RouletteBet {
        override val numbers: Set<Int> = (2..36 step 2).toSet()
    }

    @Serializable @SerialName("odd")
    data object Odd : RouletteBet {
        override val numbers: Set<Int> = (1..35 step 2).toSet()
    }

    /** Falta: 1–18. */
    @Serializable @SerialName("low")
    data object Low : RouletteBet {
        override val numbers: Set<Int> = (1..18).toSet()
    }

    /** Pasa: 19–36. */
    @Serializable @SerialName("high")
    data object High : RouletteBet {
        override val numbers: Set<Int> = (19..36).toSet()
    }
}

val RouletteBet.isValid: Boolean get() = numbers.isNotEmpty()

/** Lo que cobra [stake] si sale [number], apuesta incluida (0 si pierde). */
fun RouletteBet.payoutFor(stake: Long, number: Int): Long =
    if (number in numbers) stake * (RouletteWheel.POCKETS - 1) / numbers.size else 0

private fun rowNumbers(row: Int): Set<Int> = setOf(3 * row - 2, 3 * row - 1, 3 * row)

/** Límites de la mesa. */
@Serializable
data class RouletteRules(
    /** Valor de la ficha más pequeña: cada apuesta es múltiplo de ella. */
    val chipUnit: Long = 10,
    /** Máximo sumando todas las apuestas de un giro. */
    val maximumTotalBet: Long = 25_000,
)

/** Una apuesta colocada y su importe. */
@Serializable
data class PlacedBet(val bet: RouletteBet, val stake: Long)

data class BetResult(val bet: RouletteBet, val stake: Long, val payout: Long) {
    val won: Boolean get() = payout > 0
}

data class RouletteSpin(val number: Int, val results: List<BetResult>) {
    val totalStake: Long get() = results.sumOf { it.stake }
    val totalPayout: Long get() = results.sumOf { it.payout }
}

enum class RouletteError {
    /** No hay ninguna apuesta. */
    NoBets,

    /** Una apuesta no existe en el tapete o su importe no es válido. */
    InvalidBet,

    /** La suma supera el máximo de la mesa. */
    AboveTableMaximum,
}

/**
 * Motor de la ruleta: el giro es el único punto con azar (un número uniforme en 0–36); el resto
 * es aritmética pura.
 */
object RouletteEngine {

    fun validate(rules: RouletteRules, bets: List<PlacedBet>): RouletteError? = when {
        bets.isEmpty() -> RouletteError.NoBets
        bets.any { !it.bet.isValid || it.stake < rules.chipUnit || it.stake % rules.chipUnit != 0L } -> RouletteError.InvalidBet
        bets.sumOf { it.stake } > rules.maximumTotalBet -> RouletteError.AboveTableMaximum
        else -> null
    }

    fun spin(rules: RouletteRules, bets: List<PlacedBet>, random: RandomGenerator): Outcome<RouletteSpin, RouletteError> {
        validate(rules, bets)?.let { return failure(it) }
        return Outcome.Success(settle(bets, random.nextInt(RouletteWheel.POCKETS)))
    }

    /** Resultado de [bets] si sale [number]. */
    fun settle(bets: List<PlacedBet>, number: Int): RouletteSpin =
        RouletteSpin(number, bets.map { BetResult(it.bet, it.stake, it.bet.payoutFor(it.stake, number)) })
}
