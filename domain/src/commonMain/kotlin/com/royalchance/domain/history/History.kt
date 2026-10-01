package com.royalchance.domain.history

import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.LedgerEntry
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import kotlin.time.Instant

/**
 * Lectura del libro contable del jugador (`wallets/{uid}/ledger`), por número de asiento. Los
 * asientos son inmutables: lo leído una vez ya no cambia.
 */
interface LedgerSource {

    /** Asientos anteriores a [beforeSequence] (todos si es `null`), del más reciente al más antiguo. */
    suspend fun ledgerBefore(playerId: String, beforeSequence: Long?, limit: Int): Outcome<List<LedgerEntry>, HistoryError>

    /** Asientos posteriores a [afterSequence], del más antiguo al más reciente. */
    suspend fun ledgerAfter(playerId: String, afterSequence: Long, limit: Int): Outcome<List<LedgerEntry>, HistoryError>
}

enum class HistoryError {
    /** Sin sesión o sin perfil. */
    Unavailable,

    /** Sin conexión o sin permiso para leer. */
    ReadFailed,
}

/** Una línea del historial: una ronda jugada o un movimiento de fichas ajeno al juego. */
sealed interface HistoryItem {
    val sequence: Long
    val at: Instant
    val balanceAfter: Chips

    /**
     * Ronda terminada: instantánea (ruleta, slots, dados) o liquidación de una por turnos
     * (blackjack, una mano de póker). [stake] es `null` en liquidaciones antiguas que no la guardaban.
     */
    data class Round(
        override val sequence: Long,
        override val at: Instant,
        override val balanceAfter: Chips,
        val game: GameType,
        val stake: Chips?,
        val payout: Chips,
    ) : HistoryItem {
        /** Resultado neto, si se conoce lo apostado. */
        val net: Long? get() = stake?.let { payout.amount - it.amount }
    }

    /** Bienvenida, recarga, bono diario o recompensa de logro. */
    data class Movement(
        override val sequence: Long,
        override val at: Instant,
        override val balanceAfter: Chips,
        val kind: LedgerEntryKind,
        val amount: Long,
        val achievementId: AchievementId? = null,
    ) : HistoryItem
}

/** Página del historial: sus líneas y desde dónde pedir la siguiente (`null` si no hay más). */
data class HistoryPage(val items: List<HistoryItem>, val nextBefore: Long?)

/** Estadísticas de un juego. */
data class GameStats(
    val rounds: Long = 0,
    val wins: Long = 0,
    val losses: Long = 0,
    val pushes: Long = 0,
    val staked: Long = 0,
    val returned: Long = 0,
    /** Mayor ganancia neta en una ronda. */
    val biggestWin: Long = 0,
) {
    val net: Long get() = returned - staked

    /** Parte de lo apostado que ha vuelto (1,0 = recuperado todo); `null` sin rondas. */
    val returnRate: Double? get() = if (staked > 0) returned.toDouble() / staked else null
}

/**
 * Resumen acumulado del libro: estadísticas por juego y lo apostado en rondas aún sin liquidar.
 * Se actualiza solo con los asientos nuevos ([lastSequence] es el último ya contado).
 */
data class StatsSnapshot(
    val lastSequence: Long = 0,
    val games: Map<GameType, GameStats> = emptyMap(),
    /** Apuestas por ronda abierta (solo para liquidaciones antiguas sin la apuesta en el asiento). */
    val openStakes: Map<String, Long> = emptyMap(),
)

interface HistoryRepository {

    /** Historial del jugador, del más reciente al más antiguo. */
    suspend fun page(before: Long? = null, limit: Int = HISTORY_PAGE_SIZE): Outcome<HistoryPage, HistoryError>

    /** Estadísticas por juego de toda la cuenta. */
    suspend fun statistics(): Outcome<Map<GameType, GameStats>, HistoryError>
}

const val HISTORY_PAGE_SIZE: Int = 30

/** Conversión de asientos en historial y estadísticas. Funciones puras. */
object HistoryRules {

    /** Líneas del historial; las apuestas sueltas de una ronda por turnos se ven en su liquidación. */
    fun items(entries: List<LedgerEntry>): List<HistoryItem> = entries.mapNotNull { entry ->
        when (entry.kind) {
            LedgerEntryKind.Bet -> null
            LedgerEntryKind.InstantRound, LedgerEntryKind.Settlement -> entry.game?.let { game ->
                HistoryItem.Round(
                    sequence = entry.sequence,
                    at = entry.createdAt,
                    balanceAfter = entry.balanceAfter,
                    game = game,
                    stake = entry.stake,
                    payout = entry.payout ?: Chips(entry.amount.coerceAtLeast(0)),
                )
            }
            else -> HistoryItem.Movement(entry.sequence, entry.createdAt, entry.balanceAfter, entry.kind, entry.amount, entry.achievementId)
        }
    }

    /** Suma al resumen los asientos [entries] posteriores a [StatsSnapshot.lastSequence], en orden. */
    fun accumulate(snapshot: StatsSnapshot, entries: List<LedgerEntry>): StatsSnapshot {
        var games = snapshot.games
        var open = snapshot.openStakes
        var last = snapshot.lastSequence
        for (entry in entries.sortedBy { it.sequence }) {
            if (entry.sequence <= last) continue
            last = entry.sequence
            val game = entry.game ?: continue
            when (entry.kind) {
                LedgerEntryKind.Bet -> {
                    val round = entry.roundId ?: continue
                    open = open + (round to (open[round] ?: 0) + (entry.stake?.amount ?: -entry.amount))
                }
                LedgerEntryKind.InstantRound, LedgerEntryKind.Settlement -> {
                    val stake = entry.stake?.amount ?: entry.roundId?.let { open[it] } ?: continue
                    val payout = entry.payout?.amount ?: continue
                    entry.roundId?.let { open = open - it }
                    games = games + (game to (games[game] ?: GameStats()).plusRound(stake, payout))
                }
                else -> Unit
            }
        }
        return StatsSnapshot(last, games, open)
    }

    private fun GameStats.plusRound(stake: Long, payout: Long): GameStats {
        val net = payout - stake
        return copy(
            rounds = rounds + 1,
            wins = wins + if (net > 0) 1 else 0,
            losses = losses + if (net < 0) 1 else 0,
            pushes = pushes + if (net == 0L) 1 else 0,
            staked = staked + stake,
            returned = returned + payout,
            biggestWin = maxOf(biggestWin, net),
        )
    }
}
