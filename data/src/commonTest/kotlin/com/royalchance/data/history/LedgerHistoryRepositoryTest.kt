@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.royalchance.data.history

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.data.settings.InMemoryKeyValueStore
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.LedgerEntry
import com.royalchance.domain.game.GameType
import com.royalchance.domain.history.HistoryError
import com.royalchance.domain.history.HistoryItem
import com.royalchance.domain.history.LedgerSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration

class LedgerHistoryRepositoryTest {

    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)
    private val store = InMemoryKeyValueStore()

    /** Cuenta cuántos asientos se leen, para comprobar que las estadísticas no releen el libro. */
    private class CountingSource(private val source: LedgerSource) : LedgerSource {
        var read = 0

        override suspend fun ledgerBefore(playerId: String, beforeSequence: Long?, limit: Int): Outcome<List<LedgerEntry>, HistoryError> =
            source.ledgerBefore(playerId, beforeSequence, limit).also { read += (it as? Outcome.Success)?.value?.size ?: 0 }

        override suspend fun ledgerAfter(playerId: String, afterSequence: Long, limit: Int): Outcome<List<LedgerEntry>, HistoryError> =
            source.ledgerAfter(playerId, afterSequence, limit).also { read += (it as? Outcome.Success)?.value?.size ?: 0 }
    }

    private fun test(block: suspend (InMemoryEconomyRepository, CountingSource, LedgerHistoryRepository) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        val source = CountingSource(economy)
        block(economy, source, LedgerHistoryRepository(auth, source, store))
    }

    @Test
    fun pagesSkipBetsAndPointToTheNextPage() = test { economy, _, history ->
        repeat(4) {
            economy.placeBet(GameType.Blackjack, Chips(100))
            economy.settleRound(Chips(200))
        }

        val first = (history.page(limit = 3) as Outcome.Success).value
        // 9 asientos: bienvenida y 4 × (apuesta + liquidación). Las apuestas no se ven.
        assertEquals(3, first.items.size)
        first.items.forEach { assertEquals(100L, assertIs<HistoryItem.Round>(it).net) }

        val second = (history.page(before = first.nextBefore, limit = 3) as Outcome.Success).value
        assertEquals(2, second.items.size) // la última ronda y la bienvenida
        assertNull(second.nextBefore)
    }

    @Test
    fun statisticsOnlyReadNewEntries() = test { economy, source, history ->
        economy.playInstantRound(GameType.Dice, Chips(100), Chips(230))
        economy.playInstantRound(GameType.Dice, Chips(100), Chips.ZERO)

        val first = (history.statistics() as Outcome.Success).value
        assertEquals(2, first.getValue(GameType.Dice).rounds)
        val readFirst = source.read

        economy.playInstantRound(GameType.Slots, Chips(50), Chips(100))
        val second = (history.statistics() as Outcome.Success).value

        assertEquals(1, source.read - readFirst) // solo el asiento nuevo
        assertEquals(2, second.getValue(GameType.Dice).rounds)
        assertEquals(50, second.getValue(GameType.Slots).net)
    }
}
