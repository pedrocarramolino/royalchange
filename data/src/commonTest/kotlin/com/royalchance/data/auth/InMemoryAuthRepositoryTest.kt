package com.royalchance.data.auth

import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Instant

class InMemoryAuthRepositoryTest {

    private val repository = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)

    private val consents = LegalConsents("t1", "p1", Instant.parse("2026-09-30T10:00:00Z"))

    private fun profile(alias: String) = PlayerProfile(
        alias = alias,
        avatar = AvatarId.SpadeGold,
        countryCode = "ES",
        birthYear = 1990,
        marketingOptIn = false,
        consents = consents,
    )

    private fun account(email: String = "ana@example.com", alias: String = "Ana") =
        NewAccount(email = email, password = "Secreto123", profile = profile(alias))

    private fun signedInUser(): AuthUser = assertIs<AuthState.SignedIn>(repository.authState.value).user

    @Test
    fun registerSignsInWithUnverifiedEmail() = runTest {
        assertEquals(Outcome.Success(Unit), repository.register(account()))

        val user = signedInUser()
        assertEquals("ana@example.com", user.email)
        assertEquals("Ana", user.profile?.alias)
        assertFalse(user.isEmailVerified)
        assertFalse(user.needsProfileCompletion)
    }

    @Test
    fun emailsAndAliasesAreUniqueIgnoringCase() = runTest {
        repository.register(account())
        repository.signOut()

        assertEquals(Outcome.Failure(AuthError.EmailAlreadyInUse), repository.register(account(email = "ANA@example.com", alias = "Otra")))
        assertEquals(Outcome.Failure(AuthError.AliasTaken), repository.register(account(email = "otra@example.com", alias = "ana")))
    }

    @Test
    fun signInChecksCredentialsWithoutRevealingWhichOneFailed() = runTest {
        repository.register(account())
        repository.signOut()

        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), repository.signIn("ana@example.com", "Incorrecta1"))
        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), repository.signIn("nadie@example.com", "Secreto123"))
        assertIs<AuthState.SignedOut>(repository.authState.value)

        assertEquals(Outcome.Success(Unit), repository.signIn(" Ana@Example.com ", "Secreto123"))
        assertEquals("Ana", signedInUser().profile?.alias)
    }

    @Test
    fun passwordResetNeverRevealsWhetherAccountExists() = runTest {
        assertEquals(Outcome.Success(Unit), repository.sendPasswordReset("nadie@example.com"))
    }

    @Test
    fun emailVerificationIsDetectedOnRefresh() = runTest {
        repository.register(account())
        assertFalse(signedInUser().isEmailVerified)

        repository.refreshUser()

        assertTrue(signedInUser().isEmailVerified)
    }

    @Test
    fun deletingEmailAccountRequiresPassword() = runTest {
        repository.register(account())

        assertEquals(Outcome.Failure(AuthError.InvalidCredentials), repository.deleteAccount(password = "Incorrecta1"))
        assertIs<AuthState.SignedIn>(repository.authState.value)
    }

    @Test
    fun deletingAccountFreesEmailAndAlias() = runTest {
        repository.register(account())

        assertEquals(Outcome.Success(Unit), repository.deleteAccount(password = "Secreto123"))
        assertIs<AuthState.SignedOut>(repository.authState.value)

        assertEquals(Outcome.Success(Unit), repository.register(account()))
    }

    @Test
    fun operationsWithoutSessionFail() = runTest {
        assertEquals(Outcome.Failure(AuthError.NotSignedIn), repository.deleteAccount(password = "Secreto123"))
        assertEquals(Outcome.Failure(AuthError.NotSignedIn), repository.sendEmailVerification())
        assertEquals(Outcome.Failure(AuthError.NotSignedIn), repository.completeProfile(profile("Nadie")))
    }

    @Test
    fun completeProfileRejectsAliasOfAnotherPlayerButNotItsOwn() = runTest {
        repository.register(account(email = "otra@example.com", alias = "Otra"))
        repository.signOut()
        repository.register(account())
        val id = signedInUser().id

        assertEquals(Outcome.Failure(AuthError.AliasTaken), repository.completeProfile(profile("otra")))
        assertEquals(Outcome.Success(Unit), repository.completeProfile(profile("ANA")))
        assertEquals("ANA", signedInUser().profile?.alias)
        assertEquals(id, signedInUser().id)
    }
}
