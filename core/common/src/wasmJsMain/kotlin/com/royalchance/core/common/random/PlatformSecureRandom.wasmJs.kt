package com.royalchance.core.common.random

import kotlin.random.Random

internal actual fun platformSecureRandom(): Random = WebCryptoRandom

/** [Random] respaldado por la Web Crypto API (disponible en navegadores y en Node.js 19+). */
private object WebCryptoRandom : Random() {
    override fun nextBits(bitCount: Int): Int {
        // Toma los `bitCount` bits altos de un entero aleatorio de 32 bits (0 → 0).
        return secureRandomInt32().ushr(32 - bitCount) and (-bitCount).shr(31)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun secureRandomInt32(): Int = js("crypto.getRandomValues(new Int32Array(1))[0]")
