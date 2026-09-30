package com.royalchance.feature.auth

import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.auth.validation.FieldError
import com.royalchance.domain.auth.validation.RegistrationField
import com.royalchance.domain.auth.validation.RegistrationForm
import com.royalchance.domain.auth.validation.RegistrationMode
import com.royalchance.domain.auth.validation.RegistrationValidator
import com.royalchance.feature.auth.login.LoginViewModel
import com.royalchance.feature.auth.register.RegisterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private val clock = TestClock("2026-09-30T10:00:00Z")
    private val repository = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun existingAccount() {
        val profile = PlayerProfile(
            alias = "Ana",
            avatar = AvatarId.SpadeGold,
            countryCode = "ES",
            birthYear = 1990,
            marketingOptIn = false,
            consents = LegalConsents("t", "p", clock.now()),
        )
        repository.register(NewAccount("ana@example.com", "Secreto123", profile))
        repository.signOut()
    }

    private fun registerViewModel(mode: RegistrationMode = RegistrationMode.EmailAccount) = RegisterViewModel(
        authRepository = repository,
        validator = RegistrationValidator(clock, TimeZone.UTC),
        mode = mode,
        clock = clock,
        timeZone = TimeZone.UTC,
        defaultCountry = "ES",
    )

    @Test
    fun loginShowsFieldErrorsWithoutCallingRepository() {
        val viewModel = LoginViewModel(repository)

        viewModel.onEmailChange("no-es-un-email")
        viewModel.submit()

        assertEquals(FieldError.EmailInvalid, viewModel.state.value.emailError)
        assertEquals(FieldError.Required, viewModel.state.value.passwordError)
        assertIs<AuthState.SignedOut>(repository.authState.value)
    }

    @Test
    fun loginReportsInvalidCredentials() = runTest {
        existingAccount()
        val viewModel = LoginViewModel(repository)

        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("Incorrecta1")
        viewModel.submit()

        assertEquals(AuthError.InvalidCredentials, viewModel.state.value.authError)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun loginSignsIn() = runTest {
        existingAccount()
        val viewModel = LoginViewModel(repository)

        viewModel.onEmailChange("ana@example.com")
        viewModel.onPasswordChange("Secreto123")
        viewModel.submit()

        assertNull(viewModel.state.value.authError)
        assertIs<AuthState.SignedIn>(repository.authState.value)
    }

    @Test
    fun registerValidatesLiveAfterFirstAttempt() {
        val viewModel = registerViewModel()
        assertEquals("ES", viewModel.state.value.form.countryCode)
        assertTrue(viewModel.state.value.errors.isEmpty())

        viewModel.submit()
        assertEquals(1, viewModel.state.value.failedSubmissions)
        assertEquals(FieldError.Required, viewModel.state.value.errors[RegistrationField.Alias])

        viewModel.updateForm { it.copy(alias = "ab") }
        assertEquals(FieldError.AliasTooShort, viewModel.state.value.errors[RegistrationField.Alias])

        viewModel.updateForm { it.copy(alias = "AsDePicas") }
        assertNull(viewModel.state.value.errors[RegistrationField.Alias])
    }

    @Test
    fun registerCreatesAccount() {
        val viewModel = registerViewModel()

        viewModel.updateForm { validForm(it, email = "nuevo@example.com", alias = "Nuevo") }
        viewModel.submit()

        assertTrue(viewModel.state.value.completed)
        val user = assertIs<AuthState.SignedIn>(repository.authState.value).user
        assertEquals("Nuevo", user.profile?.alias)
    }

    @Test
    fun registerMapsDuplicateEmailToTheEmailField() = runTest {
        existingAccount()
        val viewModel = registerViewModel()

        viewModel.updateForm { validForm(it, email = "ana@example.com", alias = "Otra") }
        viewModel.submit()

        assertFalse(viewModel.state.value.completed)
        assertEquals(mapOf(RegistrationField.Email to FieldError.EmailInUse), viewModel.state.value.errors)
    }

    private fun validForm(
        form: RegistrationForm,
        email: String,
        alias: String,
    ) = form.copy(
        alias = alias,
        email = email,
        password = "Secreto123",
        passwordConfirmation = "Secreto123",
        birthDate = LocalDate(1990, 5, 17),
        acceptsTerms = true,
        acknowledgesVirtualChips = true,
    )
}
