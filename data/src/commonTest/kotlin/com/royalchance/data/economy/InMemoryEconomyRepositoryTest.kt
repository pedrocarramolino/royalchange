package com.royalchance.data.economy

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.ProgressEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryEconomyRepositoryTest {

    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)

    private fun test(block: suspend TestScope.(InMemoryEconomyRepository) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        block(InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC))
    }

    private suspend fun register(email: String = "ana@example.com", alias: String = "Ana") {
        val profile = PlayerProfile(alias, AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount(email, "Secreto123", profile))
    }

    private fun InMemoryEconomyRepository.current(): Wallet = assertIs<WalletState.Ready>(wallet.value).wallet

    private fun playerId(): String = assertIs<AuthState.SignedIn>(auth.authState.value).user.id

    @Test
    fun withoutSessionThereIsNoWallet() = test { economy ->
        assertEquals(WalletState.Unavailable, economy.wallet.value)
        assertEquals(Outcome.Failure(EconomyError.WalletUnavailable), economy.claimRescue())
    }

    @Test
    fun aNewPlayerGetsTheWelcomeGrantOnce() = test { economy ->
        register()

        assertEquals(Chips(10_000), economy.current().balance)
        auth.signOut()
        auth.signIn("ana@example.com", "Secreto123")
        assertEquals(Chips(10_000), economy.current().balance)
        assertEquals(listOf(LedgerEntryKind.Welcome), economy.ledger(playerId()).map { it.kind })
    }

    @Test
    fun operationsUpdateTheWalletAndTheLedger() = test { economy ->
        register()

        economy.placeBet(GameType.Blackjack, Chips(500))
        economy.settleRound(Chips(1_000))
        economy.playInstantRound(GameType.Slots, Chips(100), Chips.ZERO)

        assertEquals(Chips(10_400), economy.current().balance)
        val entries = economy.ledger(playerId())
        assertEquals(
            listOf(LedgerEntryKind.Welcome, LedgerEntryKind.Bet, LedgerEntryKind.Settlement, LedgerEntryKind.InstantRound),
            entries.map { it.kind },
        )
        assertEquals(economy.current().balance.amount, entries.sumOf { it.amount })
    }

    @Test
    fun rejectedOperationsChangeNothing() = test { economy ->
        register()

        assertEquals(Outcome.Failure(EconomyError.InsufficientFunds), economy.playInstantRound(GameType.Dice, Chips(20_000), Chips.ZERO))
        assertEquals(Chips(10_000), economy.current().balance)
        assertEquals(1, economy.ledger(playerId()).size)
    }

    @Test
    fun concurrentOperationsNeverOverspend() = test { economy ->
        register()

        // 30 apuestas de 500 a la vez con 10.000 de saldo: exactamente 20 deben entrar.
        val results = List(30) { async { economy.playInstantRound(GameType.Roulette, Chips(500), Chips.ZERO) } }.awaitAll()

        assertEquals(20, results.count { it is Outcome.Success })
        assertEquals(Chips.ZERO, economy.current().balance)
    }

    @Test
    fun rescueRefillsAnEmptyWalletEveryFourHours() = test { economy ->
        register()
        economy.playInstantRound(GameType.Roulette, Chips(10_000), Chips.ZERO)

        assertIs<Outcome.Success<Wallet>>(economy.claimRescue())
        economy.playInstantRound(GameType.Roulette, Chips(1_000), Chips.ZERO)
        assertIs<Outcome.Failure<EconomyError.RescueCoolingDown>>(economy.claimRescue())

        clock.advanceBy(4.hours)
        assertIs<Outcome.Success<Wallet>>(economy.claimRescue())
        assertEquals(Chips(1_000), economy.current().balance)
    }

    @Test
    fun dailyBonusUsesTheDeviceDayAndBuildsTheStreak() = test { economy ->
        register()

        assertIs<Outcome.Success<Wallet>>(economy.claimDailyBonus())
        assertEquals(Outcome.Failure(EconomyError.DailyBonusAlreadyClaimed), economy.claimDailyBonus())

        clock.advanceBy(24.hours)
        economy.claimDailyBonus()

        assertEquals(Chips(10_000 + 500 + 700), economy.current().balance)
        assertEquals(2, economy.current().progress.dailyStreak)
    }

    @Test
    fun roundsAnnounceAchievementsThatCanThenBeClaimed() = test { economy ->
        register()
        val events = mutableListOf<ProgressEvent>()
        backgroundScope.launch { economy.events.toList(events) }

        economy.playInstantRound(GameType.Roulette, Chips(100), Chips(200))

        assertEquals(listOf<ProgressEvent>(ProgressEvent.AchievementUnlocked(AchievementId.FirstWin)), events)
        assertIs<Outcome.Success<Wallet>>(economy.claimAchievement(AchievementId.FirstWin))
        assertEquals(Chips(10_100 + 250), economy.current().balance)
        assertEquals(LedgerEntryKind.AchievementReward, economy.ledger(playerId()).last().kind)
    }

    @Test
    fun eachPlayerHasTheirOwnWallet() = test { economy ->
        register()
        economy.playInstantRound(GameType.Dice, Chips(1_000), Chips.ZERO)
        auth.signOut()

        register(email = "luis@example.com", alias = "Luis")

        assertEquals(Chips(10_000), economy.current().balance)
    }
}
