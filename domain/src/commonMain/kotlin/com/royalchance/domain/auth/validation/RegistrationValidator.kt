package com.royalchance.domain.auth.validation

import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.LegalDocumentVersions
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.profile.Countries
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Datos introducidos en el formulario de registro, tal cual los escribe el usuario. */
data class RegistrationForm(
    val alias: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val birthDate: LocalDate? = null,
    val countryCode: String? = null,
    val avatar: AvatarId = AvatarId.SpadeGold,
    val acceptsTerms: Boolean = false,
    val acknowledgesVirtualChips: Boolean = false,
    val marketingOptIn: Boolean = false,
)

enum class RegistrationField {
    Alias,
    Email,
    Password,
    PasswordConfirmation,
    BirthDate,
    Country,
    Terms,
    VirtualChips,
}

enum class RegistrationMode {
    /** Registro completo con email y contraseña. */
    EmailAccount,

    /** Cuenta ya autenticada (p. ej. con Google) que solo necesita su perfil. */
    CompleteProfile,
}

sealed interface RegistrationValidation {
    /** Formulario correcto, ya normalizado y listo para enviar. */
    data class Valid(val profile: PlayerProfile, val account: NewAccount?) : RegistrationValidation

    data class Invalid(val errors: Map<RegistrationField, FieldError>) : RegistrationValidation
}

/**
 * Valida el formulario de registro completo. Lógica pura: el reloj y la zona horaria se inyectan,
 * así que la mayoría de edad se prueba con fechas exactas.
 */
class RegistrationValidator(
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    fun validate(form: RegistrationForm, mode: RegistrationMode): RegistrationValidation {
        val errors = buildMap {
            AliasRules.validate(form.alias)?.let { put(RegistrationField.Alias, it) }

            if (mode == RegistrationMode.EmailAccount) {
                EmailRules.validate(form.email)?.let { put(RegistrationField.Email, it) }
                PasswordPolicy.validate(form.password)?.let { put(RegistrationField.Password, it) }
                when {
                    form.passwordConfirmation.isEmpty() ->
                        put(RegistrationField.PasswordConfirmation, FieldError.Required)
                    form.passwordConfirmation != form.password ->
                        put(RegistrationField.PasswordConfirmation, FieldError.PasswordsDoNotMatch)
                }
            }

            AgeRules.validate(form.birthDate, clock.todayIn(timeZone))?.let { put(RegistrationField.BirthDate, it) }
            if (form.countryCode == null || form.countryCode !in Countries.isoCodes) {
                put(RegistrationField.Country, FieldError.Required)
            }
            if (!form.acceptsTerms) put(RegistrationField.Terms, FieldError.MustAccept)
            if (!form.acknowledgesVirtualChips) put(RegistrationField.VirtualChips, FieldError.MustAccept)
        }

        if (errors.isNotEmpty()) return RegistrationValidation.Invalid(errors)

        val profile = PlayerProfile(
            alias = AliasRules.normalize(form.alias),
            avatar = form.avatar,
            countryCode = requireNotNull(form.countryCode),
            birthYear = requireNotNull(form.birthDate).year,
            marketingOptIn = form.marketingOptIn,
            consents = LegalConsents(
                termsVersion = LegalDocumentVersions.TERMS,
                privacyVersion = LegalDocumentVersions.PRIVACY,
                acceptedAt = clock.now(),
            ),
        )
        val account = when (mode) {
            RegistrationMode.EmailAccount -> NewAccount(EmailRules.normalize(form.email), form.password, profile)
            RegistrationMode.CompleteProfile -> null
        }
        return RegistrationValidation.Valid(profile, account)
    }
}

/** Validación mínima del login: no se revelan requisitos de contraseña al iniciar sesión. */
object LoginValidator {
    enum class Field { Email, Password }

    fun validate(email: String, password: String): Map<Field, FieldError> = buildMap {
        EmailRules.validate(email)?.let { put(Field.Email, it) }
        if (password.isEmpty()) put(Field.Password, FieldError.Required)
    }
}
