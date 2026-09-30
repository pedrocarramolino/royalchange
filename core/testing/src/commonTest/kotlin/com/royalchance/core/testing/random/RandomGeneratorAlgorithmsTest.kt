package com.royalchance.core.testing.random

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Comprueba los algoritmos por defecto de `RandomGenerator` con valores guionizados:
 * si alguien cambia Fisher–Yates por un algoritmo sesgado, este test falla.
 */
class RandomGeneratorAlgorithmsTest {

    @Test
    fun nextIntWithLowerBoundOffsetsTheDraw() {
        val random = ScriptedRandomGenerator(3)

        assertEquals(13, random.nextInt(10, 20))
    }

    @Test
    fun shuffledFollowsFisherYatesFromTheEnd() {
        // i = 2 → j ∈ [0, 3) = 0: [A, B, C] → [C, B, A]
        // i = 1 → j ∈ [0, 2) = 0: [C, B, A] → [B, C, A]
        val random = ScriptedRandomGenerator(0, 0)

        assertEquals(listOf("B", "C", "A"), random.shuffled(listOf("A", "B", "C")))
        assertEquals(0, random.remaining)
    }

    @Test
    fun shuffledWithIdentityDrawsKeepsOrder() {
        // j == i en cada paso: ningún intercambio.
        val random = ScriptedRandomGenerator(3, 2, 1)

        assertEquals(listOf(1, 2, 3, 4), random.shuffled(listOf(1, 2, 3, 4)))
    }
}
