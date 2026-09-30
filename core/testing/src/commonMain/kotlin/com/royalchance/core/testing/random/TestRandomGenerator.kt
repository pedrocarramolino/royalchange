package com.royalchance.core.testing.random

import com.royalchance.core.common.random.RandomGenerator
import kotlin.random.Random

/**
 * Generador determinista: la misma semilla produce siempre la misma secuencia.
 *
 * Útil para simulaciones largas reproducibles (p. ej. miles de manos para comprobar que el saldo
 * nunca es negativo): si un test falla, basta con repetirlo con la misma semilla.
 */
class TestRandomGenerator(seed: Long) : RandomGenerator {

    private val source = Random(seed)

    override fun nextInt(until: Int): Int = source.nextInt(until)
}
