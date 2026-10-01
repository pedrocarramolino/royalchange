package com.royalchance.data.firebase

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.time.TestClock
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAuthRepositoryTest {

    private val clock = TestClock("2026-09-30T10:00:00Z")
    private val auth = FakeAuthGateway()
    private val store = FakePlayerStore()
    private val consents = LegalConsents("t1", "p1", clock.now())

    private fun profile(alias: String) = PlayerProfile(
        alias = alias,
        avatar = AvatarId.HeartRuby,
        countryCode = "ES",
        birthYear = 1990,
        marketingOptIn = true,
        consents = consents,
    )

    private fun TestScope.repository() = FirebaseAuthRepository(auth, store, clock, backgroundScope)

    private fun FirebaseAuthRepository.user(): AuthUser = assertIs<AuthState.SignedIn>(authState.value).user

    private fun test(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun startsSignedOutWhenThereIsNoSession() = test {
        assertIs<AuthState.SignedOut>(repository().authState.value)
    }

    @Test
    fun registerCreatesAccountReservesAliasAndSendsVerification() = test {
        val repository = repository()

        val result = repository.register(NewAccount("ana@example.com", "Secreto123", profile("AsDePicas")))

        assertEquals(Outcome.Success(Unit), result)
        val user = repository.user()
        assertEquals("AsDePicas", user.profile?.alias)
        assertEquals("ana@example.com", user.email)
        assertEquals(user.id, store.aliases["asdepicas"])
        assertEquals(1, auth.verificationEmailsSent)
    }

    @Test
    fun registerNeverExposesAnAccountWithoutProfile() = test {
        val repository = repository()
        val states = mutableListOf<AuthState>()
        backgroundScope.launch { repository.authState.toList(states) }

        repository.register(NewAccount("ana@example.com", "Secreto123", profile("AsDePicas")))

        // Firebase crea el usuario antes que el perfil; la app nunca debe ver ese estado intermedio.
        assertTrue(states.none { it is AuthState.SignedIn && it.user.needsProfileCompletion }, "estados: $states")
        assertFalse(repository.user().needsProfileCompletion)
    }

    @Test
    fun takenAliasIsRejectedBeforeCreatingTheAccount() = test {
        store.aliases["asdepicas"] = "otro-jugador"
        val repository = repository()

        val result = repository.register(NewAccount("ana@example.com", "Secreto123", profile("AsDePicas")))

        assertEquals(Outcome.Failure(AuthError.AliasTaken), result)
        assertIs<AuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun aliasTakenAtTheLastMomentLeavesAccountAskingForProfile() = test {
        store.reserveBeforeNextSave = "asdepicas"
        val repository = repository()

        val result = repository.register(NewAccount("ana@example.com", "Secreto123", profile("AsDePicas")))

        assertEquals(Outcome.Failure(AuthError.AliasTaken), result)
        assertTrue(repository.user().needsProfileCompletion)

        // La cuenta ya existe: el jugador elige otro alias sin repetir el registro.
        assertEquals(Outcome.Success(Unit), repository.completeProfile(profile("ReyDeOros")))
        assertEquals("ReyDeOros", repository.user().profile?.alias)
        assertEquals(repository.user().id, store.aliases["reydeoros"])
    }

    @Test
    fun duplicateEmailIsReported() = test {
        val repository = repository()
        repository.register(NewAccount("ana@example.com", "Secreto123", profile("Ana")))
        repository.signOut()

        val result = repository.register(NewAccount("ana@example.com", "Secreto123", profile("Otra")))

        assertEquals(Outcome.Failure(AuthError.EmailAlreadyInUse), result)
    }

    @Test
    fun wrongPasswordAndUnknownEmailLookTheSame() = test {
        val repository = repository()
        repository.register(NewAccount("ana@example.com", "Secreto123", profile("Ana")))
        repository.signOut()

        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), repository.signIn("ana@example.com", "Incorrecta1"))
        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), repository.signIn("nadie@example.com", "Secreto123"))
    }

    @Test
    fun passwordResetDoesNotRevealUnknownEmails() = test {
        assertEquals(Outcome.Success(Unit), repository().sendPasswordReset("nadie@example.com"))
    }

    @Test
    fun emailVerificationIsPickedUpOnRefresh() = test {
        val repository = repository()
        repository.register(NewAccount("ana@example.com", "Secreto123", profile("Ana")))
        assertFalse(repository.user().isEmailVerified)

        auth.confirmEmailOutsideApp()
        repository.refreshUser()

        assertTrue(repository.user().isEmailVerified)
    }

    @Test
    fun deletingTheAccountNeverExposesAnAccountWithoutProfile() = test {
        val repository = repository()
        repository.register(NewAccount("ana@example.com", "Secreto123", profile("Ana")))
        val states = mutableListOf<AuthState>()
        backgroundScope.launch { repository.authState.toList(states) }

        repository.deleteAccount("Secreto123")

        // El perfil se borra antes que el usuario; la app nunca debe ver ese estado intermedio.
        assertTrue(states.none { it is AuthState.SignedIn && it.user.needsProfileCompletion }, "estados: $states")
        assertIs<AuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun deletionFinishesEvenIfTheScreenThatStartedItGoesAway() = test {
        val repository = repository()
        repository.register(NewAccount("ana@example.com", "Secreto123", profile("Ana")))
        val uid = repository.user().id
        val slowNetwork = CompletableDeferred<Unit>().also { store.afterDeletingData = it }

        val deletion = launch { repository.deleteAccount("Secreto123") }
        deletion.cancel()
        slowNetwork.complete(Unit)

        assertEquals(listOf(uid), auth.deletedUids)
        assertIs<AuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun deleteAccountRequiresPasswordAndRemovesAllData() = test {
        val repository = repository()
        repository.register(NewAccount("ana@example.com", "Secreto123", profile("Ana")))
        val uid = repository.user().id

        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), repository.deleteAccount("Incorrecta1"))
        assertTrue(uid in store.players.value)

        assertEquals(Outcome.Success(Unit), repository.deleteAccount("Secreto123"))
        assertIs<AuthState.SignedOut>(repository.authState.value)
        assertTrue(store.players.value.isEmpty())
        assertTrue(store.aliases.isEmpty())
        assertEquals(listOf(uid), auth.deletedUids)
    }
}
