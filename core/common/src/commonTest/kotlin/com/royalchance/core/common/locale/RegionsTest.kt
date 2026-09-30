package com.royalchance.core.common.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Comprueba que JVM (y por tanto Android) y la web resuelven los mismos nombres CLDR. */
class RegionsTest {

    @Test
    fun resolvesSpanishNames() {
        assertEquals("España", regionDisplayName("ES", "es"))
        assertEquals("México", regionDisplayName("MX", "es"))
        assertEquals("Estados Unidos", regionDisplayName("US", "es"))
    }

    @Test
    fun resolvesNamesInOtherLanguages() {
        assertEquals("Spain", regionDisplayName("ES", "en"))
    }

    @Test
    fun unknownCodeReturnsNull() {
        // "AA" es sintácticamente válido pero no está asignado a ningún país.
        assertNull(regionDisplayName("AA", "es"))
    }
}
