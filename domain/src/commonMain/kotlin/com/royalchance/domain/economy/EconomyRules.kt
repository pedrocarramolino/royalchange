package com.royalchance.domain.economy

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Reglas de la economía. Las reglas de seguridad de Firestore (`firebase/firestore.rules`) repiten
 * estos valores para validarlos en el servidor: si cambia uno, hay que cambiar los dos y sus tests.
 */
object EconomyRules {

    /** Fichas de bienvenida al crear el monedero. */
    val WELCOME_GRANT: Chips = Chips(10_000)

    /** Apuesta mínima. Con fichas de 10, 50, 100, 500, 1K, 5K y 25K, el pago 3:2 siempre es entero. */
    val MINIMUM_BET: Chips = Chips(10)

    /**
     * Máximo de fichas en juego en una ronda, sumando dobles, separaciones o las distintas apuestas
     * de un giro de ruleta. Cada juego fija después sus propios límites de mesa, nunca mayores.
     */
    val MAXIMUM_ROUND_STAKE: Chips = Chips(100_000)

    /**
     * Techo global: ninguna ronda paga más que su apuesta por este factor. Cada juego paga mucho
     * menos (la ruleta, como mucho 36 veces); el techo limita el daño de un cliente manipulado.
     */
    const val MAXIMUM_PAYOUT_MULTIPLIER: Long = 1_000

    /** Recarga gratuita cuando el saldo no llega a la apuesta mínima… */
    val RESCUE_GRANT: Chips = Chips(1_000)

    /** …como mucho una vez cada este intervalo. */
    val RESCUE_COOLDOWN: Duration = 4.hours
}
