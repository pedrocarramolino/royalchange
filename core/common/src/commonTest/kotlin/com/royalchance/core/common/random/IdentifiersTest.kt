package com.royalchance.core.common.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdentifiersTest {

    @Test
    fun idsAreAlphanumericWithTheRequestedLength() {
        val random = ProductionRandomGenerator()

        repeat(1_000) {
            val id = random.nextId()
            assertEquals(DEFAULT_ID_LENGTH, id.length)
            assertTrue(id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }, id)
        }
        assertEquals(8, random.nextId(length = 8).length)
    }

    @Test
    fun everySymbolOfTheAlphabetIsReachable() {
        // Generador que recorre 0, 1, 2…: el id muestra el alfabeto completo en orden.
        val sequential = object : RandomGenerator {
            private var next = 0
            override fun nextInt(until: Int): Int = next++ % until
        }

        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789", sequential.nextId(length = 62))
    }

    @Test
    fun idsDoNotRepeat() {
        val random = ProductionRandomGenerator()
        val ids = List(10_000) { random.nextId() }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun rejectsEmptyIds() {
        assertFailsWith<IllegalArgumentException> { ProductionRandomGenerator().nextId(length = 0) }
    }
}
