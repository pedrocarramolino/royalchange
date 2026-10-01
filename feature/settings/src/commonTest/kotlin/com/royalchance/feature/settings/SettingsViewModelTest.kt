package com.royalchance.feature.settings

import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.settings.InMemorySettingsRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val authRepository = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.signedInViewModel(): SettingsViewModel {
        val profile = PlayerProfile(
            alias = "Ana",
            avatar = AvatarId.SpadeGold,
            countryCode = "ES",
            birthYear = 1990,
            marketingOptIn = false,
            consents = LegalConsents("t", "p", Instant.parse("2026-09-30T10:00:00Z")),
        )
        authRepository.register(NewAccount("ana@example.com", "Secreto123", profile))
        return SettingsViewModel(authRepository, InMemorySettingsRepository()).also { viewModel ->
            // El estado se comparte mientras alguien lo observa, como hace la pantalla.
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        }
    }

    @Test
    fun wrongPasswordKeepsDialogOpenUntilThePasswordIsEdited() = runTest {
        val viewModel = signedInViewModel()
        viewModel.request(PendingConfirmation.DeleteAccount)

        viewModel.confirmDeletion("Incorrecta1")
        assertEquals(SettingsError.WrongPassword, viewModel.state.value.error)
        assertEquals(PendingConfirmation.DeleteAccount, viewModel.state.value.pendingConfirmation)

        viewModel.onDeletionPasswordChange()
        assertNull(viewModel.state.value.error)
        assertEquals(PendingConfirmation.DeleteAccount, viewModel.state.value.pendingConfirmation)
    }

    @Test
    fun correctPasswordDeletesTheAccount() = runTest {
        val viewModel = signedInViewModel()
        viewModel.request(PendingConfirmation.DeleteAccount)

        viewModel.confirmDeletion("Secreto123")

        assertIs<AuthState.SignedOut>(authRepository.authState.value)
        assertNull(viewModel.state.value.pendingConfirmation)
    }

    @Test
    fun deletionIsIgnoredWithoutConfirmationRequest() = runTest {
        val viewModel = signedInViewModel()

        viewModel.confirmDeletion("Secreto123")

        assertIs<AuthState.SignedIn>(authRepository.authState.value)
    }
}
