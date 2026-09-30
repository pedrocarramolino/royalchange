package com.royalchance.core.testing.random

import com.royalchance.core.common.random.RandomGenerator

/**
 * Devuelve exactamente los valores indicados, en orden, para forzar resultados concretos
 * (p. ej. "la bola cae en el 0" o "el crupier recibe un as").
 *
 * Falla de inmediato si un valor no encaja en el rango pedido o si se agotan los valores:
 * un test mal preparado nunca pasa en silencio.
 */
class ScriptedRandomGenerator(vararg values: Int) : RandomGenerator {

    private val pending = ArrayDeque(values.toList())

    /** Valores que aún no se han consumido. */
    val remaining: Int get() = pending.size

    override fun nextInt(until: Int): Int {
        require(until > 0) { "Rango vacío: [0, $until)" }
        val value = pending.removeFirstOrNull()
            ?: error("ScriptedRandomGenerator agotado: se pidió un valor en [0, $until)")
        check(value in 0 until until) { "Valor guionizado $value fuera de [0, $until)" }
        return value
    }
}
