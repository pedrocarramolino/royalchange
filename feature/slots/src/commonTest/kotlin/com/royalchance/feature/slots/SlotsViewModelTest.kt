package com.royalchance.feature.slots

import com.royalchance.core.testing.random.ScriptedRandomGenerator
import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.data.games.KeyValueGameSessionStore
import com.royalchance.data.settings.InMemoryKeyValueStore
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.engine.slots.SlotEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SlotsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)
    private val sessions = KeyValueGameSessionStore(InMemoryKeyValueStore())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Machine(val economy: InMemoryEconomyRepository, val newViewModel: (stops: IntArray) -> SlotsViewModel)

    private fun test(block: suspend TestScope.(Machine) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        block(Machine(economy) { stops -> SlotsViewModel(auth, economy, sessions, ScriptedRandomGenerator(*stops)) })
    }

    private fun Machine.wallet(): Wallet = assertIs<WalletState.Ready>(economy.wallet.value).wallet

    private fun playerId() = assertIs<AuthState.SignedIn>(auth.authState.value).user.id

    /** Paradas con premio para que el test no dependa de la suerte. */
    private val winningStops: IntArray = (0 until 32).firstNotNullOf { stop ->
        intArrayOf(stop, stop, stop, stop, stop).takeIf { SlotEngine.settle(it.toList(), 10).totalPayout > 0 }
    }

    @Test
    fun aSpinIsChargedAndPaidInOneRoundAndTheBalanceWaitsForTheReels() = test { machine ->
        val viewModel = machine.newViewModel(winningStops)
        val expected = SlotEngine.settle(winningStops.toList(), 10)

        viewModel.spin()

        val balance = 10_000 - 100 + expected.totalPayout
        assertEquals(Chips(balance), machine.wallet().balance)
        assertEquals(Chips(10_000), viewModel.state.value.balance)
        assertTrue(viewModel.state.value.spinning)

        viewModel.onSpinShown(assertNotNull(viewModel.state.value.lastSpin).id)

        val state = viewModel.state.value
        assertFalse(state.spinning)
        assertTrue(state.showResult)
        assertEquals(Chips(balance), state.balance)
        assertEquals(expected.wins, state.lastSpin?.spin?.wins)
        assertEquals(listOf(LedgerEntryKind.Welcome, LedgerEntryKind.InstantRound), machine.economy.ledger(playerId()).map { it.kind })
    }

    @Test
    fun withoutTheScreenTheResultIsShownAfterATimeout() = test { machine ->
        val viewModel = machine.newViewModel(intArrayOf(1, 2, 3, 4, 5))
        viewModel.spin()

        advanceUntilIdle()

        assertFalse(viewModel.state.value.spinning)
    }

    @Test
    fun theBetMovesWithinTheTableAndIsRemembered() = test { machine ->
        val viewModel = machine.newViewModel(intArrayOf())
        assertEquals(100, viewModel.state.value.totalBet)

        repeat(20) { viewModel.decreaseBet() }
        assertEquals(10, viewModel.state.value.totalBet)
        repeat(20) { viewModel.increaseBet() }
        assertEquals(5_000, viewModel.state.value.totalBet)
        viewModel.decreaseBet()

        val reopened = machine.newViewModel(intArrayOf())
        assertEquals(2_500, reopened.state.value.totalBet)
    }

    @Test
    fun aSpinAboveTheBalanceIsNotPlayed() = test { machine ->
        machine.economy.playInstantRound(GameType.Dice, Chips(9_950), Chips.ZERO) // saldo: 50
        val viewModel = machine.newViewModel(intArrayOf())

        viewModel.spin()

        assertEquals(SlotsNotice.InsufficientFunds, viewModel.state.value.notice)
        assertNull(viewModel.state.value.lastSpin)
        assertEquals(Chips(50), machine.wallet().balance)
    }
}
