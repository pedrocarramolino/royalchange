package com.royalchance.domain.economy

import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.game.GameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WalletTransitionsTest {

    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private var nextEntry = 0

    private fun Wallet.run(operation: EconomyOperation, at: Instant = now): Outcome<WalletTransition, EconomyError> =
        WalletTransitions.apply(this, operation, entryId = "e${++nextEntry}", now = at)

    private fun Wallet.ok(operation: EconomyOperation, at: Instant = now): WalletTransition =
        assertIs<Outcome.Success<WalletTransition>>(run(operation, at)).value

    private fun Wallet.fails(operation: EconomyOperation, at: Instant = now): EconomyError =
        assertIs<Outcome.Failure<EconomyError>>(run(operation, at)).error

    private fun wallet(balance: Long, openRound: OpenRound? = null, lastRescueAt: Instant? = null) =
        Wallet(balance = Chips(balance), sequence = 7, openRound = openRound, lastRescueAt = lastRescueAt)

    private fun bet(stake: Long, game: GameType = GameType.Blackjack) = EconomyOperation.PlaceBet(game, Chips(stake))
    private fun settle(payout: Long) = EconomyOperation.SettleRound(Chips(payout))
    private fun spin(stake: Long, payout: Long) = EconomyOperation.InstantRound(GameType.Roulette, Chips(stake), Chips(payout))

    @Test
    fun newWalletStartsWithTheWelcomeGrant() {
        val (wallet, entry) = WalletTransitions.open(entryId = "w1", now = now)

        assertEquals(Chips(10_000), wallet.balance)
        assertEquals(1, wallet.sequence)
        assertEquals(LedgerEntryKind.Welcome, entry.kind)
        assertEquals(10_000, entry.amount)
        assertEquals(wallet.balance, entry.balanceAfter)
    }

    @Test
    fun aBetMovesChipsFromTheBalanceToTheTable() {
        val (wallet, entry) = wallet(10_000).ok(bet(500))

        assertEquals(Chips(9_500), wallet.balance)
        assertEquals(OpenRound(id = entry.id, game = GameType.Blackjack, stake = Chips(500)), wallet.openRound)
        assertEquals(LedgerEntryKind.Bet, entry.kind)
        assertEquals(-500, entry.amount)
        assertEquals(Chips(500), entry.stake)
        assertEquals(8, entry.sequence)
        assertEquals(8, wallet.sequence)
    }

    @Test
    fun winningPaysStakePlusWinnings() {
        // Ejemplo del enunciado: apuesta 500, gana 500.
        val afterBet = wallet(10_000).ok(bet(500)).wallet

        val (wallet, entry) = afterBet.ok(settle(1_000))

        assertEquals(Chips(10_500), wallet.balance)
        assertNull(wallet.openRound)
        assertEquals(LedgerEntryKind.Settlement, entry.kind)
        assertEquals(1_000, entry.amount)
        assertEquals(afterBet.openRound?.id, entry.roundId)
    }

    @Test
    fun losingKeepsTheStakeAndPushReturnsIt() {
        val afterBet = wallet(10_000).ok(bet(500)).wallet

        assertEquals(Chips(9_500), afterBet.ok(settle(0)).wallet.balance)
        assertEquals(Chips(10_000), afterBet.ok(settle(500)).wallet.balance)
    }

    @Test
    fun doublingAddsToTheSameRound() {
        val opened = wallet(10_000).ok(bet(500)).wallet
        val (doubled, entry) = opened.ok(bet(500))

        assertEquals(Chips(9_000), doubled.balance)
        assertEquals(Chips(1_000), doubled.openRound?.stake)
        assertEquals(opened.openRound?.id, doubled.openRound?.id)
        assertEquals(opened.openRound?.id, entry.roundId)
    }

    @Test
    fun aRoundOfAnotherGameMustBeSettledFirst() {
        val opened = wallet(10_000).ok(bet(500, GameType.Blackjack)).wallet

        assertEquals(EconomyError.RoundInProgress(GameType.Blackjack), opened.fails(bet(500, GameType.Poker)))
    }

    @Test
    fun invalidBetsAreRejected() {
        assertEquals(EconomyError.BelowMinimumBet, wallet(10_000).fails(bet(5)))
        assertEquals(EconomyError.BelowMinimumBet, wallet(10_000).fails(bet(0)))
        assertEquals(EconomyError.AboveMaximumStake, wallet(1_000_000).fails(bet(100_010)))
        assertEquals(EconomyError.InsufficientFunds, wallet(400).fails(bet(500)))
    }

    @Test
    fun theWholeBalanceCanBeBet() {
        assertEquals(Chips.ZERO, wallet(500).ok(bet(500)).wallet.balance)
    }

    @Test
    fun roundStakeLimitCountsEveryBetOfTheRound() {
        val opened = wallet(1_000_000).ok(bet(60_000)).wallet

        assertEquals(EconomyError.AboveMaximumStake, opened.fails(bet(60_000)))
        assertEquals(Chips(100_000), opened.ok(bet(40_000)).wallet.openRound?.stake)
    }

    @Test
    fun settlingNeedsAnOpenRoundAndAPlausiblePayout() {
        assertEquals(EconomyError.NoOpenRound, wallet(10_000).fails(settle(100)))

        val opened = wallet(10_000).ok(bet(10)).wallet
        assertEquals(EconomyError.PayoutTooHigh, opened.fails(settle(10_001)))
        assertEquals(Chips(9_990 + 10_000), opened.ok(settle(10_000)).wallet.balance)
    }

    @Test
    fun instantRoundRecordsStakeAndPayoutAtOnce() {
        // Pleno en la ruleta: 100 al 17, paga 35:1 más la apuesta.
        val (wallet, entry) = wallet(10_000).ok(spin(stake = 100, payout = 3_600))

        assertEquals(Chips(13_500), wallet.balance)
        assertEquals(LedgerEntryKind.InstantRound, entry.kind)
        assertEquals(3_500, entry.amount)
        assertEquals(Chips(100), entry.stake)
        assertEquals(Chips(3_600), entry.payout)
        assertEquals(entry.id, entry.roundId)
        assertNull(wallet.openRound)
    }

    @Test
    fun losingInstantRoundOnlyCostsTheStake() {
        assertEquals(Chips(9_900), wallet(10_000).ok(spin(stake = 100, payout = 0)).wallet.balance)
    }

    @Test
    fun invalidInstantRoundsAreRejected() {
        assertEquals(EconomyError.BelowMinimumBet, wallet(10_000).fails(spin(stake = 9, payout = 0)))
        assertEquals(EconomyError.AboveMaximumStake, wallet(1_000_000).fails(spin(stake = 100_001, payout = 0)))
        assertEquals(EconomyError.InsufficientFunds, wallet(50).fails(spin(stake = 100, payout = 0)))
        assertEquals(EconomyError.PayoutTooHigh, wallet(10_000).fails(spin(stake = 10, payout = 10_010)))
    }

    @Test
    fun instantRoundLeavesAnOpenRoundOfAnotherGameUntouched() {
        val opened = wallet(10_000).ok(bet(500)).wallet

        val afterSpin = opened.ok(spin(stake = 100, payout = 200)).wallet

        assertEquals(opened.openRound, afterSpin.openRound)
        assertEquals(Chips(9_600), afterSpin.balance)
    }

    @Test
    fun rescueRefillsAnEmptyWallet() {
        val (wallet, entry) = wallet(5).ok(EconomyOperation.ClaimRescue)

        assertEquals(Chips(1_005), wallet.balance)
        assertEquals(now, wallet.lastRescueAt)
        assertEquals(LedgerEntryKind.Rescue, entry.kind)
        assertEquals(1_000, entry.amount)
    }

    @Test
    fun rescueIsNotGivenWhileThePlayerCanStillBet() {
        assertEquals(EconomyError.RescueNotNeeded, wallet(10).fails(EconomyOperation.ClaimRescue))
        // Sin saldo pero con fichas en la mesa: puede ganarlas todavía.
        val playingLastChips = wallet(10).ok(bet(10)).wallet
        assertEquals(EconomyError.RescueNotNeeded, playingLastChips.fails(EconomyOperation.ClaimRescue))
    }

    @Test
    fun rescueWaitsForTheCooldown() {
        val rescuedAt = now - 3.hours
        val empty = wallet(0, lastRescueAt = rescuedAt)

        assertEquals(EconomyError.RescueCoolingDown(rescuedAt + 4.hours), empty.fails(EconomyOperation.ClaimRescue))
        assertIs<RescueStatus.CoolingDown>(empty.rescueStatus(now + 59.minutes))
        assertEquals(RescueStatus.Available, empty.rescueStatus(now + 1.hours))
        assertEquals(Chips(1_000), empty.ok(EconomyOperation.ClaimRescue, at = now + 1.hours).wallet.balance)
    }

    @Test
    fun everyEntryExplainsTheBalanceChange() {
        var current = WalletTransitions.open("w", now).wallet
        val operations = listOf(bet(500), bet(500), settle(2_000), spin(100, 0), spin(1_000, 2_000))
        for (operation in operations) {
            val (next, entry) = current.ok(operation)
            assertEquals(next.balance.amount, current.balance.amount + entry.amount)
            assertEquals(next.balance, entry.balanceAfter)
            assertEquals(current.sequence + 1, entry.sequence)
            current = next
        }
        assertEquals(Chips(11_900), current.balance)
    }

    @Test
    fun chipsAreNeverNegative() {
        assertFailsWith<IllegalArgumentException> { Chips(-1) }
        assertFailsWith<IllegalArgumentException> { Chips(10) - Chips(20) }
    }
}
