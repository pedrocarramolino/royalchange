package com.royalchance.core.common.random

import kotlin.random.Random

/**
 * Generador de producción respaldado por el generador criptográfico de cada plataforma
 * (`SecureRandom` en JVM/Android, `crypto.getRandomValues` en el navegador).
 *
 * Delega en [Random.nextInt], que usa muestreo por rechazo: los resultados acotados no tienen
 * sesgo de módulo.
 */
class ProductionRandomGenerator internal constructor(
    private val source: Random,
) : RandomGenerator {

    constructor() : this(platformSecureRandom())

    override fun nextInt(until: Int): Int = source.nextInt(until)
}

/** Fuente criptográficamente segura de la plataforma, adaptada a [Random]. */
internal expect fun platformSecureRandom(): Random
