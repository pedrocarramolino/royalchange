package com.royalchance.engine.slots

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure

/** Símbolos, del más común al más valioso. [Wild] sustituye a cualquier otro. */
enum class SlotSymbol { Cherry, Club, Heart, Spade, Diamond, Bar, Seven, Wild }

/**
 * Máquina de 5 rodillos × 3 filas con 10 líneas de premio fijas. Cada rodillo se para en una
 * posición uniforme de su tira; las filas visibles son esa posición y las dos siguientes.
 *
 * Una línea paga por la racha más larga de símbolos iguales desde el primer rodillo (el comodín
 * cuenta como cualquiera); si la racha de comodines solos paga más, se cobra esa. Los pagos son
 * múltiplos de la apuesta por línea. Retorno teórico: 97,3 % (lo calculan los tests).
 */
object SlotMachine {

    const val REELS: Int = 5
    const val ROWS: Int = 3

    /** Tira de cada rodillo (32 posiciones): la frecuencia de cada símbolo fija las probabilidades. */
    val STRIPS: List<List<SlotSymbol>> = run {
        val s = SlotSymbol.entries
        fun strip(vararg indices: Int) = indices.map { s[it] }
        listOf(
            strip(0, 1, 2, 7, 0, 3, 1, 4, 0, 2, 5, 0, 1, 6, 3, 0, 2, 7, 4, 1, 0, 3, 2, 5, 0, 1, 4, 6, 0, 2, 7, 3),
            strip(1, 0, 3, 2, 7, 0, 4, 1, 0, 5, 2, 0, 3, 6, 1, 0, 7, 2, 4, 0, 1, 3, 5, 0, 2, 6, 1, 0, 4, 7, 3, 2),
            strip(2, 0, 1, 4, 0, 7, 3, 2, 0, 6, 1, 0, 5, 3, 2, 0, 4, 7, 1, 0, 2, 3, 6, 0, 1, 5, 0, 2, 4, 3, 1, 7),
            strip(0, 3, 1, 2, 6, 0, 7, 4, 1, 0, 2, 5, 3, 0, 1, 7, 2, 0, 4, 6, 1, 3, 0, 2, 5, 0, 4, 1, 7, 0, 3, 2),
            strip(3, 0, 2, 1, 5, 0, 4, 7, 2, 0, 1, 6, 3, 0, 2, 4, 1, 0, 7, 3, 2, 0, 5, 1, 6, 0, 4, 2, 1, 0, 7, 3),
        )
    }

    /** Fila de cada rodillo por la que pasa cada línea (0 = arriba). */
    val LINES: List<List<Int>> = listOf(
        listOf(1, 1, 1, 1, 1),
        listOf(0, 0, 0, 0, 0),
        listOf(2, 2, 2, 2, 2),
        listOf(0, 1, 2, 1, 0),
        listOf(2, 1, 0, 1, 2),
        listOf(0, 0, 1, 2, 2),
        listOf(2, 2, 1, 0, 0),
        listOf(1, 0, 0, 0, 1),
        listOf(1, 2, 2, 2, 1),
        listOf(0, 1, 1, 1, 0),
    )

    /** Pago por línea (× apuesta por línea) para rachas de 2, 3, 4 y 5. Solo las cerezas pagan con 2. */
    val PAYTABLE: Map<SlotSymbol, List<Long>> = mapOf(
        SlotSymbol.Cherry to listOf(1, 3, 8, 25),
        SlotSymbol.Club to listOf(0, 4, 12, 40),
        SlotSymbol.Heart to listOf(0, 4, 12, 40),
        SlotSymbol.Spade to listOf(0, 6, 20, 60),
        SlotSymbol.Diamond to listOf(0, 10, 30, 100),
        SlotSymbol.Bar to listOf(0, 12, 40, 150),
        SlotSymbol.Seven to listOf(0, 20, 80, 300),
        SlotSymbol.Wild to listOf(0, 30, 120, 500),
    )

    fun pay(symbol: SlotSymbol, run: Int): Long = if (run < 2) 0 else PAYTABLE.getValue(symbol)[run - 2]

    /** Ventana visible: `window[rodillo][fila]` para las posiciones de parada [stops]. */
    fun window(stops: List<Int>): List<List<SlotSymbol>> =
        stops.mapIndexed { reel, stop -> List(ROWS) { row -> STRIPS[reel][(stop + row) % STRIPS[reel].size] } }

    /** Premio de una línea (× apuesta por línea) y cuántos rodillos cubre, o `null` si no paga. */
    fun evaluateLine(symbols: List<SlotSymbol>): Pair<SlotSymbol, Int>? {
        val wildRun = symbols.takeWhile { it == SlotSymbol.Wild }.size
        val first = symbols.firstOrNull { it != SlotSymbol.Wild }
        val run = first?.let { symbol -> symbols.takeWhile { it == symbol || it == SlotSymbol.Wild }.size } ?: 0
        val wildPay = pay(SlotSymbol.Wild, wildRun)
        val symbolPay = first?.let { pay(it, run) } ?: 0
        return when {
            wildPay == 0L && symbolPay == 0L -> null
            wildPay >= symbolPay -> SlotSymbol.Wild to wildRun
            else -> first!! to run
        }
    }
}

/** Apuestas de la máquina: la apuesta por línea × 10 líneas. */
data class SlotRules(
    val lineBets: List<Long> = listOf(1, 2, 5, 10, 20, 50, 100, 250, 500),
) {
    val totalBets: List<Long> get() = lineBets.map { it * SlotMachine.LINES.size }
}

/** Una línea premiada: índice de línea, símbolo, rodillos que cubre y lo que cobra. */
data class LineWin(val line: Int, val symbol: SlotSymbol, val count: Int, val payout: Long)

data class SlotSpin(val stops: List<Int>, val lineBet: Long, val wins: List<LineWin>) {
    val window: List<List<SlotSymbol>> get() = SlotMachine.window(stops)
    val totalBet: Long get() = lineBet * SlotMachine.LINES.size
    val totalPayout: Long get() = wins.sumOf { it.payout }
}

enum class SlotError { InvalidBet }

object SlotEngine {

    fun spin(rules: SlotRules, lineBet: Long, random: RandomGenerator): Outcome<SlotSpin, SlotError> {
        if (lineBet !in rules.lineBets) return failure(SlotError.InvalidBet)
        val stops = SlotMachine.STRIPS.map { random.nextInt(it.size) }
        return Outcome.Success(settle(stops, lineBet))
    }

    /** Resultado de parar los rodillos en [stops]. */
    fun settle(stops: List<Int>, lineBet: Long): SlotSpin {
        val window = SlotMachine.window(stops)
        val wins = SlotMachine.LINES.mapIndexedNotNull { index, rows ->
            val symbols = rows.mapIndexed { reel, row -> window[reel][row] }
            SlotMachine.evaluateLine(symbols)?.let { (symbol, count) ->
                LineWin(index, symbol, count, SlotMachine.pay(symbol, count) * lineBet)
            }
        }
        return SlotSpin(stops, lineBet, wins)
    }
}
