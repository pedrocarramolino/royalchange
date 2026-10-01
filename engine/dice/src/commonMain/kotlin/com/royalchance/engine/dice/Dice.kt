package com.royalchance.engine.dice

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tirada de dos dados (1–6 cada uno). */
@Serializable
data class DiceRoll(val first: Int, val second: Int) {
    val sum: Int get() = first + second
    val isDouble: Boolean get() = first == second
}

/**
 * Apuesta sobre la suma de dos dados. [multiplierTenths] es lo que cobra por ficha, apuesta
 * incluida, en décimas (23 = 2,3×): con fichas de 10 el pago siempre es entero. Todas las
 * apuestas devuelven entre el 95,8 % y el 97,2 % (lo comprueban los tests).
 */
@Serializable
sealed interface DiceBet {

    val multiplierTenths: Long

    fun wins(roll: DiceRoll): Boolean

    /** Menor: suma de 2 a 6 (2,3×). */
    @Serializable @SerialName("low")
    data object Low : DiceBet {
        override val multiplierTenths: Long = 23
        override fun wins(roll: DiceRoll) = roll.sum <= 6
    }

    /** Mayor: suma de 8 a 12 (2,3×). */
    @Serializable @SerialName("high")
    data object High : DiceBet {
        override val multiplierTenths: Long = 23
        override fun wins(roll: DiceRoll) = roll.sum >= 8
    }

    /** Siete exacto (5,8×). */
    @Serializable @SerialName("seven")
    data object Seven : DiceBet {
        override val multiplierTenths: Long = 58
        override fun wins(roll: DiceRoll) = roll.sum == 7
    }

    /** Dobles: los dos dados iguales (5,8×). */
    @Serializable @SerialName("doubles")
    data object Doubles : DiceBet {
        override val multiplierTenths: Long = 58
        override fun wins(roll: DiceRoll) = roll.isDouble
    }

    /** Suma exacta (2–12 salvo el 7, que es [Seven]): cuanto menos probable, más paga. */
    @Serializable @SerialName("sum")
    data class Sum(val total: Int) : DiceBet {
        override val multiplierTenths: Long
            get() = when (total) {
                2, 12 -> 350
                3, 11 -> 175
                4, 10 -> 115
                5, 9 -> 87
                6, 8 -> 69
                else -> 0
            }

        override fun wins(roll: DiceRoll) = roll.sum == total
    }
}

val DiceBet.isValid: Boolean get() = multiplierTenths > 0

fun DiceBet.payoutFor(stake: Long, roll: DiceRoll): Long = if (wins(roll)) stake * multiplierTenths / 10 else 0

/** Todas las apuestas de la mesa. */
val ALL_DICE_BETS: List<DiceBet> = listOf(DiceBet.Low, DiceBet.Seven, DiceBet.High, DiceBet.Doubles) +
    (2..12).filter { it != 7 }.map { DiceBet.Sum(it) }

@Serializable
data class DiceRules(val chipUnit: Long = 10, val maximumTotalBet: Long = 25_000)

@Serializable
data class PlacedDiceBet(val bet: DiceBet, val stake: Long)

data class DiceBetResult(val bet: DiceBet, val stake: Long, val payout: Long)

data class DiceThrow(val roll: DiceRoll, val results: List<DiceBetResult>) {
    val totalStake: Long get() = results.sumOf { it.stake }
    val totalPayout: Long get() = results.sumOf { it.payout }
}

enum class DiceError { NoBets, InvalidBet, AboveTableMaximum }

object DiceEngine {

    fun validate(rules: DiceRules, bets: List<PlacedDiceBet>): DiceError? = when {
        bets.isEmpty() -> DiceError.NoBets
        bets.any { !it.bet.isValid || it.stake < rules.chipUnit || it.stake % rules.chipUnit != 0L } -> DiceError.InvalidBet
        bets.sumOf { it.stake } > rules.maximumTotalBet -> DiceError.AboveTableMaximum
        else -> null
    }

    fun roll(rules: DiceRules, bets: List<PlacedDiceBet>, random: RandomGenerator): Outcome<DiceThrow, DiceError> {
        validate(rules, bets)?.let { return failure(it) }
        return Outcome.Success(settle(bets, DiceRoll(random.nextInt(1, 7), random.nextInt(1, 7))))
    }

    fun settle(bets: List<PlacedDiceBet>, roll: DiceRoll): DiceThrow =
        DiceThrow(roll, bets.map { DiceBetResult(it.bet, it.stake, it.bet.payoutFor(it.stake, roll)) })
}
