package com.royalchance.feature.dice

import com.royalchance.core.testing.random.ScriptedRandomGenerator
import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.data.games.KeyValueGameSessionStore
import com.royalchance.data.settings.InMemoryKeyValueStore
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.engine.dice.DiceBet
import com.royalchance.engine.dice.DiceRoll
import com.royalchance.engine.dice.PlacedDiceBet
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
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class DiceViewModelTest {

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

    private class Table(val economy: InMemoryEconomyRepository, val newViewModel: (values: IntArray) -> DiceViewModel)

    /** Los valores guionizados son `dado − 1` (el motor pide `nextInt(1, 7)`). */
    private fun test(block: suspend TestScope.(Table) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        block(Table(economy) { values -> DiceViewModel(auth, economy, sessions, ScriptedRandomGenerator(*values)) })
    }

    private fun Table.wallet(): Wallet = assertIs<WalletState.Ready>(economy.wallet.value).wallet

    @Test
    fun aRollPaysEveryWinningBetAndTheBalanceWaitsForTheDice() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(5, 5)) // 6 y 6
        viewModel.place(DiceBet.Doubles) // 100 × 5,8
        viewModel.place(DiceBet.Sum(12)) // 100 × 35
        viewModel.place(DiceBet.Low) // pierde

        viewModel.roll()

        assertEquals(Chips(10_000 - 300 + 580 + 3_500), table.wallet().balance)
        assertEquals(Chips(10_000), viewModel.state.value.balance)
        assertTrue(viewModel.state.value.rolling)

        viewModel.onRollShown(assertNotNull(viewModel.state.value.lastThrow).id)

        val state = viewModel.state.value
        assertFalse(state.rolling)
        assertEquals(Chips(13_780), state.balance)
        assertEquals(listOf(DiceRoll(6, 6)), state.history)
        assertTrue(state.bets.isEmpty())
    }

    @Test
    fun lastBetsAndHistorySurviveReopening() = test { table ->
        val viewModel = table.newViewModel(intArrayOf(0, 1))
        viewModel.selectChip(50)
        viewModel.place(DiceBet.Seven)
        viewModel.place(DiceBet.Seven)
        viewModel.roll()
        advanceUntilIdle()

        val reopened = table.newViewModel(intArrayOf())

        assertEquals(listOf(DiceRoll(1, 2)), reopened.state.value.history)
        assertEquals(listOf(PlacedDiceBet(DiceBet.Seven, 100)), reopened.state.value.lastBets)
        assertEquals(50, reopened.state.value.chip)
        reopened.repeat()
        assertEquals(100, reopened.state.value.totalBet)
    }

    @Test
    fun betsCannotExceedTheTableMaximum() = test { table ->
        table.economy.playInstantRound(GameType.Slots, Chips(100), Chips(30_000))
        val viewModel = table.newViewModel(intArrayOf())
        viewModel.selectChip(5_000)

        repeat(6) { viewModel.place(DiceBet.High) }

        assertEquals(DiceNotice.AboveTableMaximum(25_000), viewModel.state.value.notice)
        assertEquals(25_000, viewModel.state.value.totalBet)
    }
}
