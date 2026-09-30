package com.royalchance.core.common.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifica el generador REAL de cada plataforma (JVM y navegador/Node).
 * Se ejecuta en todos los targets, así que un `actual` roto en cualquiera de ellos falla aquí.
 */
class ProductionRandomGeneratorTest {

    private val random = ProductionRandomGenerator()

    @Test
    fun nextIntStaysWithinBounds() {
        repeat(10_000) {
            val value = random.nextInt(37)
            assertTrue(value in 0..36, "fuera de rango: $value")
        }
    }

    @Test
    fun nextIntWithLowerBoundStaysWithinBounds() {
        repeat(10_000) {
            val value = random.nextInt(2, 13)
            assertTrue(value in 2..12, "fuera de rango: $value")
        }
    }

    @Test
    fun rejectsEmptyRanges() {
        assertFailsWith<IllegalArgumentException> { random.nextInt(0) }
        assertFailsWith<IllegalArgumentException> { random.nextInt(-5) }
        assertFailsWith<IllegalArgumentException> { random.nextInt(5, 5) }
        assertFailsWith<IllegalArgumentException> { random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE) }
    }

    @Test
    fun shuffledIsAPermutationAndLeavesSourceUntouched() {
        val deck = (1..52).toList()

        val shuffled = random.shuffled(deck)

        assertEquals(deck, shuffled.sorted())
        assertEquals((1..52).toList(), deck)
        // Probabilidad de que 52 cartas queden en su orden original: 1/52! ≈ 1e-68.
        assertNotEquals(deck, shuffled)
    }

    @Test
    fun distributionIsUniformAcrossRoulettePockets() {
        val pockets = 37
        val expectedPerPocket = 1_000
        val counts = IntArray(pockets)
        repeat(pockets * expectedPerPocket) { counts[random.nextInt(pockets)]++ }

        val chiSquare = counts.sumOf { observed ->
            val diff = (observed - expectedPerPocket).toDouble()
            diff * diff / expectedPerPocket
        }

        // Chi-cuadrado con 36 grados de libertad: media 36. Superar 100 tiene probabilidad ≈ 1e-7
        // con una fuente uniforme, así que el test solo falla si la fuente está realmente sesgada.
        assertTrue(chiSquare < 100.0, "distribución sesgada: chi² = $chiSquare, conteos = ${counts.toList()}")
    }
}
