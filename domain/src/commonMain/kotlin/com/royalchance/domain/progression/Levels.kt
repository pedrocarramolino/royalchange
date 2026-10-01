package com.royalchance.domain.progression

import com.royalchance.domain.economy.Chips

/**
 * Niveles por experiencia. Para alcanzar el nivel `n` hacen falta `50 · (n − 1)²` puntos: el
 * nivel 5 llega en unas 60 rondas, el 10 (VIP) en unas 300 y el 25 (High Roller) en unas 2.000.
 *
 * El nivel no se guarda: se deriva siempre de la experiencia.
 */
object Levels {

    const val MAX_LEVEL: Int = 99

    private const val XP_FACTOR = 50L

    /** Experiencia total necesaria para alcanzar [level]. */
    fun xpForLevel(level: Int): Long {
        require(level in 1..MAX_LEVEL) { "Nivel fuera de rango: $level" }
        val steps = (level - 1).toLong()
        return XP_FACTOR * steps * steps
    }

    fun levelFor(xp: Long): Int {
        require(xp >= 0) { "Experiencia negativa: $xp" }
        // Raíz entera exacta (sin coma flotante): el mayor nivel cuyo umbral no supera xp.
        var level = 1
        while (level < MAX_LEVEL && xpForLevel(level + 1) <= xp) level++
        return level
    }

    fun progress(xp: Long): LevelProgress {
        val level = levelFor(xp)
        val start = xpForLevel(level)
        val next = if (level < MAX_LEVEL) xpForLevel(level + 1) else null
        return LevelProgress(level = level, xpIntoLevel = xp - start, xpForNextLevel = next?.minus(start))
    }

    fun titleFor(level: Int): PlayerTitle = PlayerTitle.entries.last { level >= it.minimumLevel }
}

/**
 * @property xpForNextLevel experiencia que separa este nivel del siguiente; `null` en el máximo.
 */
data class LevelProgress(
    val level: Int,
    val xpIntoLevel: Long,
    val xpForNextLevel: Long?,
) {
    /** Fracción completada del nivel actual, entre 0 y 1. */
    val fraction: Float
        get() = xpForNextLevel?.let { (xpIntoLevel.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 1f

    val title: PlayerTitle get() = Levels.titleFor(level)
}

/** Títulos de la progresión. Los nombres son estables. */
enum class PlayerTitle(val minimumLevel: Int) {
    Novice(1),
    Player(5),
    Vip(10),
    HighRoller(25),
    Legend(50),
}

/**
 * Experiencia por ronda: una parte fija y otra que crece por tramos con la apuesta, casi como su
 * logaritmo. Así no compensa apostar solo el máximo ni encadenar apuestas mínimas, y el resultado
 * de la ronda no influye: no hay nada que ganar falseando victorias.
 *
 * Las reglas de seguridad de Firestore repiten estos tramos.
 */
object ExperienceRules {

    const val BASE_XP: Long = 10

    /** Apuesta mínima de cada tramo y su experiencia adicional, de mayor a menor. */
    private val stakeTiers: List<Pair<Long, Long>> = listOf(
        25_000L to 12L,
        5_000L to 10L,
        1_000L to 8L,
        500L to 6L,
        100L to 4L,
        50L to 2L,
    )

    fun xpForRound(stake: Chips): Long =
        BASE_XP + (stakeTiers.firstOrNull { (minimum, _) -> stake.amount >= minimum }?.second ?: 0L)
}
