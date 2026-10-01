package com.royalchance.data.firebase

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.random.TestRandomGenerator
import com.royalchance.core.testing.time.TestClock
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseEconomyRepositoryTest {

    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val authGateway = FakeAuthGateway()
    private val players = FakePlayerStore()
    private val wallets = FakeWalletStore()

    private fun test(block: suspend TestScope.(FirebaseRepositories) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        block(FirebaseRepositories.create(authGateway, players, wallets, clock, TestRandomGenerator(seed = 7), backgroundScope))
    }

    private suspend fun FirebaseRepositories.register() {
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
    }

    private fun FirebaseRepositories.balance(): Chips = assertIs<WalletState.Ready>(economy.wallet.value).wallet.balance

    private fun entries(): List<LedgerEntryDocument> = wallets.ledger["uid-1"].orEmpty()

    @Test
    fun firstAccessCreatesTheWalletWithTheWelcomeGrant() = test { firebase ->
        firebase.register()

        assertEquals(Chips(10_000), firebase.balance())
        val entry = entries().single()
        assertEquals(LedgerEntryKind.Welcome.name, entry.kind)
        assertEquals(WalletDocument("uid-1", balance = 10_000, seq = 1, lastEntryId = entry.id), wallets.wallets.value["uid-1"])
    }

    @Test
    fun anExistingWalletIsNeverRecreated() = test { firebase ->
        wallets.wallets.value = mapOf("uid-1" to WalletDocument("uid-1", balance = 2_500, seq = 9, lastEntryId = "x"))

        firebase.register()

        assertEquals(Chips(2_500), firebase.balance())
        assertEquals(0, wallets.writes)
    }

    @Test
    fun everyOperationWritesTheWalletTogetherWithItsEntry() = test { firebase ->
        firebase.register()

        firebase.economy.placeBet(GameType.Blackjack, Chips(500))
        firebase.economy.settleRound(Chips(1_250))
        firebase.economy.playInstantRound(GameType.Roulette, Chips(100), Chips(200))

        assertEquals(Chips(10_850), firebase.balance())
        val document = wallets.wallets.value.getValue("uid-1")
        assertEquals(entries().last().id, document.lastEntryId)
        assertEquals(listOf(1L, 2L, 3L, 4L), entries().map { it.seq })
        assertEquals(document.balance, entries().sumOf { it.amount })
    }

    @Test
    fun aRejectedWriteFallsBackToTheLastValidBalance() = test { firebase ->
        firebase.register()
        wallets.rejectNextWrite = true

        // Se aplica en local al instante; al rechazarlo el servidor, Firestore lo deshace.
        assertIs<Outcome.Success<Wallet>>(firebase.economy.playInstantRound(GameType.Slots, Chips(100), Chips(5_000)))

        assertEquals(Chips(10_000), firebase.balance())
        assertEquals(1, entries().size)
    }

    @Test
    fun aRejectedWalletCreationIsNotRetriedInALoop() = test { firebase ->
        wallets.rejectNextWrite = true

        firebase.register()

        assertEquals(WalletState.Unavailable, firebase.economy.wallet.value)
        assertEquals(1, wallets.writes)
    }

    @Test
    fun aWalletThatDisappearsIsNeverRecreated() = test { firebase ->
        // Monedero de una sesión anterior (la app se abrió con él ya creado).
        wallets.wallets.value = mapOf("uid-1" to WalletDocument("uid-1", balance = 2_500, seq = 9, lastEntryId = "x"))
        firebase.register()

        // Así se ve desde el monedero el borrado de la cuenta.
        wallets.wallets.value = emptyMap()

        assertEquals(WalletState.Unavailable, firebase.economy.wallet.value)
        assertEquals(0, wallets.writes)
    }

    @Test
    fun validationErrorsWriteNothing() = test { firebase ->
        firebase.register()

        assertEquals(Outcome.Failure(EconomyError.InsufficientFunds), firebase.economy.placeBet(GameType.Blackjack, Chips(10_010)))
        assertEquals(Outcome.Failure(EconomyError.RescueNotNeeded), firebase.economy.claimRescue())
        assertEquals(1, wallets.writes)
    }

    @Test
    fun withoutSessionOrLocalDataThereIsNoWallet() = test { firebase ->
        assertEquals(WalletState.Unavailable, firebase.economy.wallet.value)
        assertEquals(Outcome.Failure(EconomyError.WalletUnavailable), firebase.economy.claimRescue())

        firebase.register()
        wallets.failLocalReads = true
        assertEquals(Outcome.Failure(EconomyError.WalletUnavailable), firebase.economy.placeBet(GameType.Blackjack, Chips(10)))

        firebase.auth.signOut()
        assertEquals(WalletState.Unavailable, firebase.economy.wallet.value)
    }

    @Test
    fun concurrentOperationsAreAppliedOneAtATime() = test { firebase ->
        firebase.register()

        val results = List(25) { async { firebase.economy.playInstantRound(GameType.Dice, Chips(500), Chips.ZERO) } }.awaitAll()

        assertEquals(20, results.count { it is Outcome.Success })
        assertEquals(Chips.ZERO, firebase.balance())
        assertTrue(entries().zipWithNext().all { (a, b) -> b.seq == a.seq + 1 })
    }

    @Test
    fun walletDocumentsRoundTripWithoutEmptyFields() {
        val wallet = WalletDocument("uid-1", balance = 10, seq = 3, lastEntryId = "e3")
        val entry = LedgerEntryDocument("e3", seq = 3, kind = "Rescue", amount = 1_000, balanceAfter = 1_010, createdAtMillis = 5)

        // Las reglas exigen una forma exacta: los campos opcionales vacíos no se escriben.
        assertEquals(setOf("uid", "balance", "seq", "lastEntryId"), wallet.toFirestoreMap().keys)
        assertEquals(setOf("id", "seq", "kind", "amount", "balanceAfter", "createdAtMillis"), entry.toFirestoreMap().keys)
        assertEquals(wallet, fromFirestoreMap<WalletDocument>(wallet.toFirestoreMap()))
        assertEquals(entry, fromJson<LedgerEntryDocument>(entry.toJson()))
    }
}
