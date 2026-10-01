package com.royalchance.domain.economy

import kotlin.jvm.JvmInline

/**
 * Cantidad de fichas virtuales (nunca negativa). Entero sobre `Long`: sin decimales ni redondeos,
 * porque la apuesta mínima y las fichas son múltiplos de 10 y todos los pagos salen enteros.
 *
 * Las variaciones de saldo, que sí pueden ser negativas, se expresan como `Long` en los asientos.
 */
@JvmInline
value class Chips(val amount: Long) : Comparable<Chips> {

    init {
        require(amount >= 0) { "Las fichas no pueden ser negativas: $amount" }
    }

    operator fun plus(other: Chips): Chips = Chips(amount + other.amount)

    /** Lanza [IllegalArgumentException] si el resultado fuera negativo: hay que validar antes. */
    operator fun minus(other: Chips): Chips = Chips(amount - other.amount)

    operator fun times(factor: Long): Chips = Chips(amount * factor)

    override fun compareTo(other: Chips): Int = amount.compareTo(other.amount)

    override fun toString(): String = "$amount fichas"

    companion object {
        val ZERO: Chips = Chips(0)
    }
}
