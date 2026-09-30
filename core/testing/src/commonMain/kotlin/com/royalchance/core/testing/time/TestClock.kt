package com.royalchance.core.testing.time

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Reloj controlado por el test: parte de un instante fijo y solo avanza cuando se le pide.
 * Imprescindible para probar mayoría de edad, bono diario y rachas sin depender de la fecha real.
 */
class TestClock(start: Instant) : Clock {

    constructor(isoInstant: String) : this(Instant.parse(isoInstant))

    var current: Instant = start
        private set

    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }
}
