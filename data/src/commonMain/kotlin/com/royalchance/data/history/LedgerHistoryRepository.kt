package com.royalchance.data.history

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.data.settings.KeyValueStore
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.playerId
import com.royalchance.domain.game.GameType
import com.royalchance.domain.history.GameStats
import com.royalchance.domain.history.HistoryError
import com.royalchance.domain.history.HistoryItem
import com.royalchance.domain.history.HistoryPage
import com.royalchance.domain.history.HistoryRepository
import com.royalchance.domain.history.HistoryRules
import com.royalchance.domain.history.LedgerSource
import com.royalchance.domain.history.StatsSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Historial y estadísticas a partir del libro contable.
 *
 * Las estadísticas por juego se guardan resumidas en el dispositivo junto al último asiento
 * contado: cada vez solo se leen los asientos nuevos (los anteriores son inmutables). En un
 * dispositivo nuevo se lee el libro entero una vez.
 */
class LedgerHistoryRepository(
    private val authRepository: AuthRepository,
    private val ledger: LedgerSource,
    private val store: KeyValueStore,
) : HistoryRepository {

    private val statsLock = Mutex()

    override suspend fun page(before: Long?, limit: Int): Outcome<HistoryPage, HistoryError> {
        val playerId = authRepository.authState.value.playerId ?: return failure(HistoryError.Unavailable)
        val items = mutableListOf<HistoryItem>()
        var cursor = before
        // Las apuestas sueltas no se muestran: se piden más asientos hasta llenar la página.
        while (items.size < limit) {
            val entries = when (val result = ledger.ledgerBefore(playerId, cursor, limit)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
            items += HistoryRules.items(entries)
            if (entries.size < limit) return Outcome.Success(HistoryPage(items, nextBefore = null))
            cursor = entries.last().sequence
        }
        return Outcome.Success(HistoryPage(items, nextBefore = cursor))
    }

    override suspend fun statistics(): Outcome<Map<GameType, GameStats>, HistoryError> = statsLock.withLock {
        val playerId = authRepository.authState.value.playerId ?: return failure(HistoryError.Unavailable)
        var snapshot = load(playerId)
        while (true) {
            val entries = when (val result = ledger.ledgerAfter(playerId, snapshot.lastSequence, STATS_BATCH)) {
                is Outcome.Failure -> {
                    // Sin conexión: se enseña lo ya contado, si hay algo.
                    return if (snapshot.games.isEmpty()) result else Outcome.Success(snapshot.games)
                }
                is Outcome.Success -> result.value
            }
            snapshot = HistoryRules.accumulate(snapshot, entries)
            if (entries.size < STATS_BATCH) break
        }
        save(playerId, snapshot)
        Outcome.Success(snapshot.games)
    }

    private fun load(playerId: String): StatsSnapshot =
        store.getString(key(playerId))
            ?.let { runCatching { json.decodeFromString(StoredSnapshot.serializer(), it) }.getOrNull() }
            ?.toDomain()
            ?: StatsSnapshot()

    private fun save(playerId: String, snapshot: StatsSnapshot) {
        store.putString(key(playerId), json.encodeToString(StoredSnapshot.serializer(), StoredSnapshot.from(snapshot)))
    }

    private fun key(playerId: String) = "stats.$playerId"

    private companion object {
        const val STATS_BATCH = 500
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredSnapshot(
    val version: Int = 1,
    val lastSequence: Long,
    val games: Map<String, StoredStats>,
    val openStakes: Map<String, Long>,
) {
    fun toDomain() = StatsSnapshot(
        lastSequence = lastSequence,
        games = games.mapNotNull { (name, stats) -> GameType.entries.firstOrNull { it.name == name }?.let { it to stats.toDomain() } }.toMap(),
        openStakes = openStakes,
    )

    companion object {
        fun from(snapshot: StatsSnapshot) = StoredSnapshot(
            lastSequence = snapshot.lastSequence,
            games = snapshot.games.map { (game, stats) -> game.name to StoredStats.from(stats) }.toMap(),
            openStakes = snapshot.openStakes,
        )
    }
}

@Serializable
private data class StoredStats(
    val rounds: Long,
    val wins: Long,
    val losses: Long,
    val pushes: Long,
    val staked: Long,
    val returned: Long,
    val biggestWin: Long,
) {
    fun toDomain() = GameStats(rounds, wins, losses, pushes, staked, returned, biggestWin)

    companion object {
        fun from(stats: GameStats) = StoredStats(stats.rounds, stats.wins, stats.losses, stats.pushes, stats.staked, stats.returned, stats.biggestWin)
    }
}
