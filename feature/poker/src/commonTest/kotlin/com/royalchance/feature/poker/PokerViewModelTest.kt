package com.royalchance.feature.poker

import com.royalchance.core.testing.random.TestRandomGenerator
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
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.engine.poker.PokerPhase
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class PokerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)
    private val store = InMemoryKeyValueStore()
    private val sessions = KeyValueGameSessionStore(store)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Room(val economy: InMemoryEconomyRepository, val newViewModel: (seed: Long) -> PokerViewModel)

    private fun test(block: suspend TestScope.(Room) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        block(Room(economy) { seed -> PokerViewModel(auth, economy, sessions, TestRandomGenerator(seed), computeDispatcher = dispatcher) })
    }

    private fun Room.wallet(): Wallet = assertIs<WalletState.Ready>(economy.wallet.value).wallet

    @Test
    fun sittingDownDealsAHandAndChargesOnlyWhatThePlayerPutsIn() = test { room ->
        val viewModel = room.newViewModel(1)
        viewModel.changeBuyIn(2_000)

        viewModel.sitDown()

        val table = assertNotNull(viewModel.state.value.table)
        assertEquals("Ana", table.seats[HERO].name)
        val committed = table.seats[HERO].committed
        assertEquals(Chips(10_000 - committed), room.wallet().balance)
        if (committed > 0) assertEquals(Chips(committed), room.wallet().openRound?.stake)
    }

    @Test
    fun theWalletAlwaysMatchesTheChipsAtTheTable() = test { room ->
        // El jugador iguala siempre. Tras cada mano: saldo = lo que no llevó a la mesa + su pila.
        val viewModel = room.newViewModel(7)
        viewModel.changeBuyIn(1_000)
        viewModel.sitDown()
        var hands = 0
        while (hands < 25 && !viewModel.state.value.needsRebuy) {
            advanceUntilIdle()
            while (viewModel.state.value.heroTurn) {
                viewModel.checkOrCall()
                advanceUntilIdle()
            }
            val table = assertNotNull(viewModel.state.value.table)
            assertEquals(PokerPhase.HandOver, table.phase)
            assertNull(room.wallet().openRound)
            assertEquals(Chips(10_000 - 1_000 + table.seats[HERO].stack), room.wallet().balance)
            hands++
            viewModel.dealNextHand()
        }
        assertTrue(hands > 0)
    }

    @Test
    fun foldingSettlesTheHandAtOnce() = test { room ->
        val viewModel = room.newViewModel(3)
        viewModel.sitDown()
        // Hasta que le toque al jugador en una mano en la que haya puesto fichas (una ciega).
        var guard = 0
        while (!(viewModel.state.value.heroTurn && viewModel.state.value.table!!.seats[HERO].committed > 0)) {
            advanceUntilIdle()
            if (viewModel.state.value.heroTurn) viewModel.checkOrCall() else viewModel.dealNextHand()
            advanceUntilIdle()
            assertTrue(++guard < 60, "No llegó una mano con ciega")
        }
        val committed = viewModel.state.value.table!!.seats[HERO].committed

        viewModel.fold()

        assertNull(room.wallet().openRound)
        assertTrue(room.wallet().balance.amount <= 10_000 - committed)
    }

    @Test
    fun aHandInProgressIsResumedAndAnOrphanRoundIsForfeited() = test { room ->
        val first = room.newViewModel(5)
        first.sitDown()
        advanceUntilIdle()
        val table = assertNotNull(first.state.value.table)

        val reopened = room.newViewModel(6)
        assertEquals(table, reopened.state.value.table)

        if (room.wallet().openRound != null) {
            // Sin el estado guardado (otro dispositivo), la mano a medias se da por perdida.
            store.remove("session.${GameType.Poker.name}.${(auth.authState.value as AuthState.SignedIn).user.id}")
            val elsewhere = room.newViewModel(8)
            assertEquals(PokerNotice.HandForfeited, elsewhere.state.value.notice)
            assertNull(room.wallet().openRound)
        }
    }
}
