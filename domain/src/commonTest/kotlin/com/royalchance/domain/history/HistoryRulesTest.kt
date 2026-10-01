package com.royalchance.domain.history

import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.LedgerEntry
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.game.GameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class HistoryRulesTest {

    private val at = Instant.parse("2026-10-01T10:00:00Z")

    private fun entry(seq: Long, kind: LedgerEntryKind, amount: Long, game: GameType? = null, round: String? = null, stake: Long? = null, payout: Long? = null) =
        LedgerEntry("e$seq", seq, kind, amount, Chips(10_000), at, game, round, stake?.let(::Chips), payout?.let(::Chips))

    private val ledger = listOf(
        entry(1, LedgerEntryKind.Welcome, 10_000),
        entry(2, LedgerEntryKind.Bet, -100, GameType.Blackjack, "r1", stake = 100),
        entry(3, LedgerEntryKind.Bet, -100, GameType.Blackjack, "r1", stake = 100),
        entry(4, LedgerEntryKind.Settlement, 400, GameType.Blackjack, "r1", stake = 200, payout = 400),
        entry(5, LedgerEntryKind.InstantRound, -100, GameType.Roulette, "e5", stake = 100, payout = 0),
        entry(6, LedgerEntryKind.InstantRound, 3_500, GameType.Roulette, "e6", stake = 100, payout = 3_600),
        entry(7, LedgerEntryKind.DailyBonus, 500),
    )

    @Test
    fun betsAreFoldedIntoTheirSettlement() {
        val items = HistoryRules.items(ledger.reversed())

        assertEquals(listOf(7L, 6L, 5L, 4L, 1L), items.map { it.sequence })
        val blackjack = assertIs<HistoryItem.Round>(items[3])
        assertEquals(GameType.Blackjack, blackjack.game)
        assertEquals(200L, blackjack.net)
        assertIs<HistoryItem.Movement>(items[0])
    }

    @Test
    fun statisticsArePerGame() {
        val stats = HistoryRules.accumulate(StatsSnapshot(), ledger)

        assertEquals(7, stats.lastSequence)
        assertEquals(GameStats(rounds = 1, wins = 1, staked = 200, returned = 400, biggestWin = 200), stats.games[GameType.Blackjack])
        assertEquals(GameStats(rounds = 2, wins = 1, losses = 1, staked = 200, returned = 3_600, biggestWin = 3_500), stats.games[GameType.Roulette])
        assertEquals(3_400, stats.games.getValue(GameType.Roulette).net)
        assertEquals(emptyMap(), stats.openStakes)
    }

    @Test
    fun theSummaryGrowsWithNewEntriesOnly() {
        val first = HistoryRules.accumulate(StatsSnapshot(), ledger.take(3))
        assertEquals(mapOf("r1" to 200L), first.openStakes)

        // Al volver a pasar asientos ya contados, no se cuentan dos veces.
        val second = HistoryRules.accumulate(first, ledger)

        assertEquals(HistoryRules.accumulate(StatsSnapshot(), ledger), second)
    }

    @Test
    fun oldSettlementsWithoutStakeUseTheirBets() {
        val legacy = listOf(
            entry(1, LedgerEntryKind.Bet, -500, GameType.Blackjack, "r", stake = 500),
            entry(2, LedgerEntryKind.Settlement, 0, GameType.Blackjack, "r", payout = 0),
        )

        val stats = HistoryRules.accumulate(StatsSnapshot(), legacy)

        assertEquals(GameStats(rounds = 1, losses = 1, staked = 500), stats.games[GameType.Blackjack])
        assertEquals(null, assertIs<HistoryItem.Round>(HistoryRules.items(legacy).single()).net)
    }
}
