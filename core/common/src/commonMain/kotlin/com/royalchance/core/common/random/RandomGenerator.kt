package com.royalchance.core.common.random

/**
 * Única fuente de aleatoriedad del juego.
 *
 * Los motores (barajar, girar la ruleta, parar rodillos, tirar dados) la reciben inyectada y nunca
 * usan `kotlin.random.Random` directamente. Así:
 * - en producción la fuente es criptográficamente segura ([ProductionRandomGenerator]);
 * - en tests la fuente es determinista y cada resultado se puede forzar.
 *
 * Las implementaciones solo aportan [nextInt]; el resto de operaciones se construyen sobre él
 * para que todas compartan los mismos algoritmos sin sesgo.
 */
interface RandomGenerator {

    /** Entero uniforme en `[0, until)`. Lanza [IllegalArgumentException] si `until <= 0`. */
    fun nextInt(until: Int): Int

    /** Entero uniforme en `[from, until)`. Lanza [IllegalArgumentException] si el rango está vacío. */
    fun nextInt(from: Int, until: Int): Int {
        val span = until - from
        require(span > 0) { "Rango vacío o desbordado: [$from, $until)" }
        return from + nextInt(span)
    }

    /**
     * Copia barajada de [items] mediante Fisher–Yates: todas las permutaciones son equiprobables.
     * No modifica la lista original.
     */
    fun <T> shuffled(items: List<T>): List<T> {
        val result = items.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            if (i != j) {
                result[i] = result[j].also { result[j] = result[i] }
            }
        }
        return result
    }
}
