package com.royalchance.feature.lobby

import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.RescueStatus
import com.royalchance.domain.game.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.(InMemoryEconomyRepository, LobbyViewModel) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        val viewModel = LobbyViewModel(auth, economy, clock)
        // El estado se comparte mientras alguien lo observa, como hace la pantalla.
        backgroundScope.launch { viewModel.state.collect {} }
        block(economy, viewModel)
    }

    @Test
    fun showsTheBalanceAndExplainsTheWelcomeGrantOnce() = test { _, viewModel ->
        assertEquals(BalanceUi.Ready(Chips(10_000)), viewModel.state.value.balance)
        assertTrue(viewModel.state.value.showWelcomeGrant)

        viewModel.dismissWelcomeGrant()

        assertFalse(viewModel.state.value.showWelcomeGrant)
    }

    @Test
    fun welcomeNoticeDisappearsAfterTheFirstMovement() = test { economy, viewModel ->
        economy.playInstantRound(GameType.Roulette, Chips(100), Chips(200))

        assertFalse(viewModel.state.value.showWelcomeGrant)
        assertEquals(BalanceUi.Ready(Chips(10_100)), viewModel.state.value.balance)
    }

    @Test
    fun anEmptyWalletOffersTheRescueAndClaimingItRefillsIt() = test { economy, viewModel ->
        assertEquals(RescueStatus.NotNeeded, viewModel.state.value.rescue)
        economy.playInstantRound(GameType.Slots, Chips(10_000), Chips.ZERO)
        assertEquals(RescueStatus.Available, viewModel.state.value.rescue)

        viewModel.claimRescue()

        assertEquals(BalanceUi.Ready(Chips(1_000)), viewModel.state.value.balance)
        assertEquals(RescueStatus.NotNeeded, viewModel.state.value.rescue)
        assertFalse(viewModel.state.value.rescueFailed)
    }

    @Test
    fun theCountdownUpdatesItselfUntilTheNextRescue() = test { economy, viewModel ->
        economy.playInstantRound(GameType.Slots, Chips(10_000), Chips.ZERO)
        viewModel.claimRescue()
        economy.playInstantRound(GameType.Slots, Chips(1_000), Chips.ZERO)
        assertIs<RescueStatus.CoolingDown>(viewModel.state.value.rescue)

        clock.advanceBy(4.hours)
        advanceTimeBy(31.seconds)

        assertEquals(RescueStatus.Available, viewModel.state.value.rescue)
    }

    @Test
    fun remainingMinutesRoundUp() {
        val now = Instant.parse("2026-10-01T10:00:00Z")

        assertEquals(0, minutesUntil(now, now))
        assertEquals(0, minutesUntil(now, now - 5.minutes))
        assertEquals(1, minutesUntil(now, now + 1.seconds))
        assertEquals(60, minutesUntil(now, now + 1.hours))
        assertEquals(240, minutesUntil(now, now + 3.hours + 59.minutes + 30.seconds))
    }
}
