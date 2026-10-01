package com.royalchance.feature.roulette

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
import com.royalchance.engine.roulette.PlacedBet
import com.royalchance.engine.roulette.RouletteBet
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
class RouletteViewModelTest {

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

    private class Table(val economy: InMemoryEconomyRepository, val newViewModel: (numbers: IntArray) -> RouletteViewModel)

    private fun test(block: suspend TestScope.(Table) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        block(Table(economy) { numbers -> RouletteViewModel(auth, economy, sessions, ScriptedRandomGenerator(*numbers)) })
    }

    private fun Table.wallet(): Wallet = assertIs<WalletState.Ready>(economy.wallet.value).wallet

    private fun playerId() = assertIs<AuthState.SignedIn>(auth.authState.value).user.id

    @Test
    fun chipsStackOnTheSameBet() = test { table ->
        val viewModel = table.newViewModel(intArrayOf())
        viewModel.selectChip(50)

        viewModel.place(RouletteBet.Red)
        viewModel.place(RouletteBet.Red)
        viewModel.place(RouletteBet.Straight(7))

        val state = viewModel.state.value
        assertEquals(listOf(PlacedBet(RouletteBet.Red, 100), PlacedBet(RouletteBet.Straight(7), 50)), state.bets)
        assertEquals(150, state.totalBet)
    }

    @Test
    fun aWinningSpinIsPaidInOneRoundAndTheBalanceWaitsForTheBall() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(17))
        viewModel.place(RouletteBet.Straight(17)) // 100
        viewModel.place(RouletteBet.Red) // 100: el 17 es negro

        viewModel.spin()

        // Ya está contabilizado (10.000 − 200 + 3.600), pero la pantalla espera a que pare la bola.
        assertEquals(Chips(13_400), table.wallet().balance)
        assertEquals(Chips(10_000), viewModel.state.value.balance)
        assertTrue(viewModel.state.value.spinning)
        assertTrue(viewModel.state.value.history.isEmpty())

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.spinning)
        assertTrue(state.showResult)
        assertEquals(Chips(13_400), state.balance)
        assertEquals(17, state.lastSpin?.spin?.number)
        assertEquals(listOf(17), state.history)
        assertTrue(state.bets.isEmpty())
        assertEquals(listOf(LedgerEntryKind.Welcome, LedgerEntryKind.InstantRound), table.economy.ledger(playerId()).map { it.kind })
        assertEquals(1L, table.wallet().progress.roundsPlayed)
    }

    @Test
    fun theResultIsShownWhenTheWheelStops() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(9))
        viewModel.place(RouletteBet.Odd)
        viewModel.spin()
        val id = assertNotNull(viewModel.state.value.lastSpin).id

        viewModel.onSpinShown(id + 1) // otro giro: no cuenta
        assertTrue(viewModel.state.value.spinning)

        viewModel.onSpinShown(id)

        assertFalse(viewModel.state.value.spinning)
        assertEquals(Chips(10_100), viewModel.state.value.balance)
    }

    @Test
    fun withZeroEvenMoneyBetsAreLost() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(0))
        viewModel.place(RouletteBet.Black)

        viewModel.spin()
        advanceUntilIdle()

        assertEquals(Chips(9_900), table.wallet().balance)
        assertEquals(0, viewModel.state.value.lastSpin?.spin?.totalPayout)
    }

    @Test
    fun betsCannotExceedTheBalanceOrTheTableMaximum() = test { table ->
        val viewModel = table.newViewModel(intArrayOf())
        viewModel.selectChip(5_000)
        repeat(2) { viewModel.place(RouletteBet.Red) }

        viewModel.place(RouletteBet.Black)

        assertEquals(RouletteNotice.InsufficientFunds, viewModel.state.value.notice)
        assertEquals(10_000, viewModel.state.value.totalBet)

        table.economy.playInstantRound(GameType.Dice, Chips(100), Chips(30_100)) // saldo: 40.000
        viewModel.clear()
        repeat(5) { viewModel.place(RouletteBet.Even) }
        viewModel.place(RouletteBet.Odd)

        assertEquals(RouletteNotice.AboveTableMaximum(25_000), viewModel.state.value.notice)
        assertEquals(25_000, viewModel.state.value.totalBet)
    }

    @Test
    fun undoClearAndRepeat() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(5))
        viewModel.place(RouletteBet.Dozen(1))
        viewModel.place(RouletteBet.Column(2))

        viewModel.undo()
        assertEquals(listOf(PlacedBet(RouletteBet.Dozen(1), 100)), viewModel.state.value.bets)

        viewModel.clear()
        assertTrue(viewModel.state.value.bets.isEmpty())
        viewModel.undo()
        assertEquals(listOf(PlacedBet(RouletteBet.Dozen(1), 100)), viewModel.state.value.bets)

        viewModel.spin()
        advanceUntilIdle()
        viewModel.repeat()

        assertEquals(listOf(PlacedBet(RouletteBet.Dozen(1), 100)), viewModel.state.value.bets)
        assertFalse(viewModel.state.value.showResult)
    }

    @Test
    fun aFailedChargeKeepsTheBetsAndShowsNoNumber() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(3))
        viewModel.selectChip(1_000)
        viewModel.place(RouletteBet.Odd)
        table.economy.playInstantRound(GameType.Dice, Chips(9_500), Chips.ZERO) // saldo: 500

        viewModel.spin()

        val state = viewModel.state.value
        assertEquals(RouletteNotice.InsufficientFunds, state.notice)
        assertNull(state.lastSpin)
        assertEquals(1_000, state.totalBet)
        assertEquals(Chips(500), table.wallet().balance)
    }

    @Test
    fun historyAndLastBetsAreKeptOnTheDevice() = test { table ->
        val first = table.newViewModel(intArrayOf(32, 15))
        first.place(RouletteBet.High)
        first.spin()
        advanceUntilIdle()
        first.place(RouletteBet.Low)
        first.spin()
        advanceUntilIdle()

        val reopened = table.newViewModel(intArrayOf())

        assertEquals(listOf(15, 32), reopened.state.value.history)
        assertEquals(listOf(PlacedBet(RouletteBet.Low, 100)), reopened.state.value.lastBets)
        assertNull(reopened.state.value.lastSpin)
    }
}
