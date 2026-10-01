package com.royalchance.core.common.random

private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

/** Longitud por defecto: 62^20 ≈ 7·10^35 combinaciones, como los ids automáticos de Firestore. */
const val DEFAULT_ID_LENGTH: Int = 20

/**
 * Identificador aleatorio alfanumérico generado en el cliente (asientos contables, rondas…).
 * Con una fuente segura, la probabilidad de colisión es despreciable incluso sin servidor.
 */
fun RandomGenerator.nextId(length: Int = DEFAULT_ID_LENGTH): String {
    require(length > 0) { "Longitud no válida: $length" }
    return buildString(length) {
        repeat(length) { append(ID_ALPHABET[nextInt(ID_ALPHABET.length)]) }
    }
}
