package com.royalchance.core.common.result

/**
 * Resultado de una operación que puede fallar por motivos esperados y tipados
 * (credenciales incorrectas, saldo insuficiente, sin conexión…).
 *
 * Los fallos esperados se modelan como valores, no como excepciones: el compilador obliga a
 * tratarlos y la UI puede mostrar un mensaje concreto para cada uno.
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>
    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

/** Atajo para operaciones que no devuelven valor. */
fun <E> success(): Outcome<Unit, E> = Outcome.Success(Unit)

fun <E> failure(error: E): Outcome<Nothing, E> = Outcome.Failure(error)
