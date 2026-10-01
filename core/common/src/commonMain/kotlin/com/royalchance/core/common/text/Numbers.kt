package com.royalchance.core.common.text

/**
 * Entero con separador de miles: `25450` → `"25.450"`.
 *
 * Se agrupa siempre, también con cuatro cifras (`1.700`), para que los saldos se lean de un vistazo
 * y no cambien de anchura al cruzar los 10.000. Kotlin común no tiene formateo con configuración
 * regional, y la app solo está en español.
 */
fun formatGrouped(value: Long, separator: Char = '.'): String {
    // Long.MIN_VALUE no tiene opuesto positivo: se trabaja con los dígitos del texto.
    val digits = value.toString().removePrefix("-")
    val grouped = buildString(digits.length + digits.length / 3) {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(separator)
            append(digit)
        }
    }
    return if (value < 0) "-$grouped" else grouped
}
