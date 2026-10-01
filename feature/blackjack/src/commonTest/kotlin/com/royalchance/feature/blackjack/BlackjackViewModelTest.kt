package com.royalchance.feature.blackjack

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
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.engine.blackjack.BlackjackPhase
import com.royalchance.engine.blackjack.BlackjackState
import com.royalchance.engine.blackjack.HandOutcome
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Shoe
import com.royalchance.engine.cards.Suit
import com.royalchance.engine.cards.standardDeck
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class BlackjackViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)
    private val sessions = KeyValueGameSessionStore(InMemoryKeyValueStore())
    private val random = TestRandomGenerator(seed = 3)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** "AS" = as de picas, "10H" = diez de corazones… */
    private fun card(code: String) = Card(
        Rank.entries.first { it.symbol == code.dropLast(1) },
        Suit.entries.first { it.name.first() == code.last() },
    )

    /** Orden: jugador, crupier, jugador, crupier (oculta) y después lo que se pida. */
    private fun shoe(vararg codes: String): BlackjackState {
        val cards = codes.map(::card) + standardDeck()
        return BlackjackState(shoe = Shoe(cards, cutIndex = cards.size))
    }

    private class Table(val economy: InMemoryEconomyRepository, val newViewModel: (BlackjackState) -> BlackjackViewModel)

    private fun test(block: suspend TestScope.(Table) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        block(Table(economy) { table -> BlackjackViewModel(auth, economy, sessions, random, initialTable = table) })
    }

    private fun Table.wallet(): Wallet = assertIs<WalletState.Ready>(economy.wallet.value).wallet

    private fun playerId() = assertIs<AuthState.SignedIn>(auth.authState.value).user.id

    @Test
    fun dealingChargesTheBetAndOpensARound() = test { table ->
        val viewModel = table.newViewModel(shoe("10S", "9D", "8H", "7C"))

        viewModel.deal()

        assertEquals(BlackjackPhase.PlayerTurn, viewModel.state.value.table.phase)
        assertEquals(Chips(9_900), table.wallet().balance)
        assertEquals(Chips(100), table.wallet().openRound?.stake)
    }

    @Test
    fun aWonRoundIsSettledAndTheBalanceWaitsForTheDealer() = test { table ->
        val viewModel = table.newViewModel(shoe("10S", "10D", "8H", "6C", "KH"))
        viewModel.deal()

        viewModel.stand()

        // Ya está contabilizado, pero la pantalla no lo enseña hasta que el crupier termina.
        assertEquals(Chips(10_100), table.wallet().balance)
        assertEquals(Chips(9_900), viewModel.state.value.balance)
        assertTrue(viewModel.state.value.animating)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(Chips(10_100), state.balance)
        assertTrue(state.showResults)
        assertEquals(3, state.dealerCardsShown)
        assertNull(table.wallet().openRound)
        assertEquals(
            listOf(LedgerEntryKind.Welcome, LedgerEntryKind.Bet, LedgerEntryKind.Settlement),
            table.economy.ledger(playerId()).map { it.kind },
        )
    }

    @Test
    fun aNaturalBlackjackPaysThreeToTwo() = test { table ->
        val viewModel = table.newViewModel(shoe("AS", "9D", "KH", "7C"))

        viewModel.deal()
        advanceUntilIdle()

        assertEquals(HandOutcome.Blackjack, viewModel.state.value.table.results.single().outcome)
        assertEquals(Chips(10_150), table.wallet().balance)
    }

    @Test
    fun doublingChargesTheSameStakeAgain() = test { table ->
        val viewModel = table.newViewModel(shoe("6S", "10D", "5H", "7C", "10H"))
        viewModel.deal()

        viewModel.double()
        advanceUntilIdle()

        assertEquals(200, viewModel.state.value.table.hands.single().stake)
        assertEquals(Chips(10_200), table.wallet().balance)
    }

    @Test
    fun withoutChipsToDoubleTheHandContinues() = test { table ->
        table.economy.playInstantRound(GameType.Dice, Chips(9_900), Chips.ZERO)
        val viewModel = table.newViewModel(shoe("6S", "10D", "5H", "7C", "10H"))
        viewModel.deal()

        viewModel.double()

        assertEquals(BlackjackNotice.InsufficientFunds, viewModel.state.value.notice)
        assertEquals(BlackjackPhase.PlayerTurn, viewModel.state.value.table.phase)
        assertEquals(100, viewModel.state.value.table.hands.single().stake)
    }

    @Test
    fun theBetCannotExceedTheBalanceOrTheTableLimit() = test { table ->
        val viewModel = table.newViewModel(shoe("10S", "9D", "8H", "7C"))
        viewModel.clearBet()

        repeat(3) { viewModel.addChip(5_000) }
        assertEquals(10_000, viewModel.state.value.bet)

        table.economy.playInstantRound(GameType.Dice, Chips(9_500), Chips.ZERO)
        viewModel.clearBet()
        viewModel.addChip(1_000)
        assertEquals(500, viewModel.state.value.bet)
    }

    @Test
    fun aHandInProgressIsResumedWhereItWas() = test { table ->
        val first = table.newViewModel(shoe("8S", "10D", "8H", "7C", "3H", "KC"))
        first.deal()
        first.split()
        val inProgress = first.state.value.table

        val reopened = table.newViewModel(BlackjackState())

        assertEquals(inProgress, reopened.state.value.table)
        assertEquals(BlackjackPhase.PlayerTurn, reopened.state.value.table.phase)
        assertNull(reopened.state.value.notice)
    }

    @Test
    fun anOpenRoundWithoutItsTableIsForfeited() = test { table ->
        table.economy.placeBet(GameType.Blackjack, Chips(500))

        val viewModel = table.newViewModel(BlackjackState())

        assertEquals(BlackjackNotice.HandForfeited, viewModel.state.value.notice)
        assertNull(table.wallet().openRound)
        assertEquals(Chips(9_500), table.wallet().balance)
    }
}
