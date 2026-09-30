package com.royalchance.domain.auth.validation

import com.royalchance.core.testing.time.TestClock
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalDocumentVersions
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrationValidatorTest {

    // "Hoy" es el 30/09/2026. Se usa UTC para que el test no dependa de la base de zonas horarias.
    private val clock = TestClock("2026-09-30T10:00:00Z")
    private val validator = RegistrationValidator(clock, TimeZone.UTC)

    private val validForm = RegistrationForm(
        alias = "  RoyalAce_7  ",
        email = " jugador@example.com ",
        password = "Secreto123",
        passwordConfirmation = "Secreto123",
        birthDate = LocalDate(1990, 5, 17),
        countryCode = "ES",
        avatar = AvatarId.HeartRuby,
        acceptsTerms = true,
        acknowledgesVirtualChips = true,
        marketingOptIn = false,
    )

    @Test
    fun validFormProducesNormalizedAccountAndProfile() {
        val result = assertIs<RegistrationValidation.Valid>(validator.validate(validForm, RegistrationMode.EmailAccount))

        val account = requireNotNull(result.account)
        assertEquals("jugador@example.com", account.email)
        assertEquals("Secreto123", account.password)
        assertEquals("RoyalAce_7", result.profile.alias)
        assertEquals(AvatarId.HeartRuby, result.profile.avatar)
        assertEquals("ES", result.profile.countryCode)
        assertEquals(1990, result.profile.birthYear)
        assertEquals(LegalDocumentVersions.TERMS, result.profile.consents.termsVersion)
        assertEquals(clock.now(), result.profile.consents.acceptedAt)
    }

    @Test
    fun emptyFormReportsEveryRequiredField() {
        val errors = invalid(RegistrationForm(), RegistrationMode.EmailAccount)

        assertEquals(
            mapOf(
                RegistrationField.Alias to FieldError.Required,
                RegistrationField.Email to FieldError.Required,
                RegistrationField.Password to FieldError.Required,
                RegistrationField.PasswordConfirmation to FieldError.Required,
                RegistrationField.BirthDate to FieldError.Required,
                RegistrationField.Country to FieldError.Required,
                RegistrationField.Terms to FieldError.MustAccept,
                RegistrationField.VirtualChips to FieldError.MustAccept,
            ),
            errors,
        )
    }

    @Test
    fun turningEighteenTodayIsAllowed() {
        val form = validForm.copy(birthDate = LocalDate(2008, 9, 30))

        assertIs<RegistrationValidation.Valid>(validator.validate(form, RegistrationMode.EmailAccount))
    }

    @Test
    fun oneDayBeforeEighteenthBirthdayIsUnderage() {
        val form = validForm.copy(birthDate = LocalDate(2008, 10, 1))

        assertEquals(FieldError.Underage, invalid(form)[RegistrationField.BirthDate])
    }

    @Test
    fun birthDateInTheFutureIsRejected() {
        val form = validForm.copy(birthDate = LocalDate(2026, 10, 1))

        assertEquals(FieldError.BirthDateInFuture, invalid(form)[RegistrationField.BirthDate])
    }

    @Test
    fun implausibleBirthDateIsRejected() {
        val form = validForm.copy(birthDate = LocalDate(1890, 1, 1))

        assertEquals(FieldError.BirthDateImplausible, invalid(form)[RegistrationField.BirthDate])
    }

    @Test
    fun mismatchedPasswordsAreRejected() {
        val form = validForm.copy(passwordConfirmation = "Secreto124")

        assertEquals(FieldError.PasswordsDoNotMatch, invalid(form)[RegistrationField.PasswordConfirmation])
    }

    @Test
    fun weakPasswordIsRejected() {
        val form = validForm.copy(password = "secreto", passwordConfirmation = "secreto")

        assertEquals(FieldError.PasswordRequirementsNotMet, invalid(form)[RegistrationField.Password])
    }

    @Test
    fun unknownCountryCodeIsRejected() {
        val form = validForm.copy(countryCode = "XX")

        assertEquals(FieldError.Required, invalid(form)[RegistrationField.Country])
    }

    @Test
    fun completeProfileModeIgnoresCredentials() {
        val form = validForm.copy(email = "", password = "", passwordConfirmation = "")

        val result = assertIs<RegistrationValidation.Valid>(validator.validate(form, RegistrationMode.CompleteProfile))
        assertNull(result.account)
    }

    @Test
    fun aliasRules() {
        assertEquals(FieldError.AliasTooShort, AliasRules.validate("ab"))
        assertEquals(FieldError.AliasTooLong, AliasRules.validate("a".repeat(21)))
        assertEquals(FieldError.AliasInvalidCharacters, AliasRules.validate("Rey de picas"))
        assertEquals(FieldError.AliasInvalidCharacters, AliasRules.validate("Peña"))
        assertEquals(FieldError.AliasMustStartWithLetter, AliasRules.validate("7Ases"))
        assertNull(AliasRules.validate("As_de_Picas"))
    }

    @Test
    fun emailRules() {
        assertEquals(FieldError.Required, EmailRules.validate("   "))
        assertEquals(FieldError.EmailInvalid, EmailRules.validate("jugador@"))
        assertEquals(FieldError.EmailInvalid, EmailRules.validate("jugador@example"))
        assertEquals(FieldError.EmailInvalid, EmailRules.validate("ju gador@example.com"))
        assertNull(EmailRules.validate("jugador.uno+casino@sub.example.es"))
    }

    @Test
    fun loginValidationDoesNotApplyPasswordPolicy() {
        assertTrue(LoginValidator.validate("jugador@example.com", "x").isEmpty())
        assertEquals(
            mapOf(LoginValidator.Field.Email to FieldError.EmailInvalid, LoginValidator.Field.Password to FieldError.Required),
            LoginValidator.validate("jugador", ""),
        )
    }

    private fun invalid(form: RegistrationForm, mode: RegistrationMode = RegistrationMode.EmailAccount) =
        assertIs<RegistrationValidation.Invalid>(validator.validate(form, mode)).errors
}
