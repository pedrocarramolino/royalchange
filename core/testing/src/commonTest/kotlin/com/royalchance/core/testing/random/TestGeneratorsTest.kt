package com.royalchance.core.testing.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TestGeneratorsTest {

    @Test
    fun sameSeedProducesSameSequence() {
        val first = TestRandomGenerator(seed = 42)
        val second = TestRandomGenerator(seed = 42)

        assertEquals(List(100) { first.nextInt(1_000) }, List(100) { second.nextInt(1_000) })
    }

    @Test
    fun differentSeedsProduceDifferentSequences() {
        val first = TestRandomGenerator(seed = 1)
        val second = TestRandomGenerator(seed = 2)

        assertNotEquals(List(100) { first.nextInt(1_000) }, List(100) { second.nextInt(1_000) })
    }

    @Test
    fun scriptedReturnsValuesInOrder() {
        val random = ScriptedRandomGenerator(0, 36, 17)

        assertEquals(listOf(0, 36, 17), List(3) { random.nextInt(37) })
        assertEquals(0, random.remaining)
    }

    @Test
    fun scriptedRejectsValuesOutsideRequestedRange() {
        val random = ScriptedRandomGenerator(37)

        assertFailsWith<IllegalStateException> { random.nextInt(37) }
    }

    @Test
    fun scriptedFailsWhenExhausted() {
        val random = ScriptedRandomGenerator(5)
        random.nextInt(10)

        assertFailsWith<IllegalStateException> { random.nextInt(10) }
    }
}
