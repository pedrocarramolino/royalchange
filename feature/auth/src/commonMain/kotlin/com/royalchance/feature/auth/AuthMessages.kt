package com.royalchance.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.validation.FieldError
import com.royalchance.domain.auth.validation.PasswordRequirement
import com.royalchance.domain.auth.validation.PasswordStrength
import com.royalchance.feature.auth.resources.Res
import com.royalchance.feature.auth.resources.auth_error_alias_taken
import com.royalchance.feature.auth.resources.auth_error_cancelled
import com.royalchance.feature.auth.resources.auth_error_email_in_use
import com.royalchance.feature.auth.resources.auth_error_invalid_credentials
import com.royalchance.feature.auth.resources.auth_error_network
import com.royalchance.feature.auth.resources.auth_error_not_signed_in
import com.royalchance.feature.auth.resources.auth_error_too_many_attempts
import com.royalchance.feature.auth.resources.auth_error_unknown
import com.royalchance.feature.auth.resources.auth_error_weak_password
import com.royalchance.feature.auth.resources.error_alias_characters
import com.royalchance.feature.auth.resources.error_alias_start_letter
import com.royalchance.feature.auth.resources.error_alias_taken
import com.royalchance.feature.auth.resources.error_alias_too_long
import com.royalchance.feature.auth.resources.error_alias_too_short
import com.royalchance.feature.auth.resources.error_birth_date_future
import com.royalchance.feature.auth.resources.error_birth_date_implausible
import com.royalchance.feature.auth.resources.error_email_in_use
import com.royalchance.feature.auth.resources.error_email_invalid
import com.royalchance.feature.auth.resources.error_must_accept
import com.royalchance.feature.auth.resources.error_password_requirements
import com.royalchance.feature.auth.resources.error_password_too_long
import com.royalchance.feature.auth.resources.error_passwords_mismatch
import com.royalchance.feature.auth.resources.error_required
import com.royalchance.feature.auth.resources.error_underage
import com.royalchance.feature.auth.resources.password_requirement_digit
import com.royalchance.feature.auth.resources.password_requirement_length
import com.royalchance.feature.auth.resources.password_requirement_lowercase
import com.royalchance.feature.auth.resources.password_requirement_uppercase
import com.royalchance.feature.auth.resources.password_strength_fair
import com.royalchance.feature.auth.resources.password_strength_good
import com.royalchance.feature.auth.resources.password_strength_strong
import com.royalchance.feature.auth.resources.password_strength_weak
import org.jetbrains.compose.resources.stringResource

// Traducción de los errores y reglas del dominio a textos de interfaz.

@Composable
internal fun FieldError.message(): String = stringResource(
    when (this) {
        FieldError.Required -> Res.string.error_required
        FieldError.AliasTooShort -> Res.string.error_alias_too_short
        FieldError.AliasTooLong -> Res.string.error_alias_too_long
        FieldError.AliasMustStartWithLetter -> Res.string.error_alias_start_letter
        FieldError.AliasInvalidCharacters -> Res.string.error_alias_characters
        FieldError.AliasTaken -> Res.string.error_alias_taken
        FieldError.EmailInvalid -> Res.string.error_email_invalid
        FieldError.EmailInUse -> Res.string.error_email_in_use
        FieldError.PasswordRequirementsNotMet -> Res.string.error_password_requirements
        FieldError.PasswordTooLong -> Res.string.error_password_too_long
        FieldError.PasswordsDoNotMatch -> Res.string.error_passwords_mismatch
        FieldError.BirthDateInFuture -> Res.string.error_birth_date_future
        FieldError.BirthDateImplausible -> Res.string.error_birth_date_implausible
        FieldError.Underage -> Res.string.error_underage
        FieldError.MustAccept -> Res.string.error_must_accept
    },
)

@Composable
internal fun AuthError.message(): String = stringResource(
    when (this) {
        AuthError.InvalidCredentials -> Res.string.auth_error_invalid_credentials
        AuthError.EmailAlreadyInUse -> Res.string.auth_error_email_in_use
        AuthError.AliasTaken -> Res.string.auth_error_alias_taken
        AuthError.WeakPassword -> Res.string.auth_error_weak_password
        AuthError.TooManyAttempts -> Res.string.auth_error_too_many_attempts
        AuthError.Network -> Res.string.auth_error_network
        AuthError.Cancelled -> Res.string.auth_error_cancelled
        AuthError.NotSignedIn -> Res.string.auth_error_not_signed_in
        AuthError.Unknown -> Res.string.auth_error_unknown
    },
)

@Composable
internal fun PasswordRequirement.label(): String = stringResource(
    when (this) {
        PasswordRequirement.MinLength -> Res.string.password_requirement_length
        PasswordRequirement.Lowercase -> Res.string.password_requirement_lowercase
        PasswordRequirement.Uppercase -> Res.string.password_requirement_uppercase
        PasswordRequirement.Digit -> Res.string.password_requirement_digit
    },
)

@Composable
internal fun PasswordStrength.label(): String = stringResource(
    when (this) {
        PasswordStrength.Weak -> Res.string.password_strength_weak
        PasswordStrength.Fair -> Res.string.password_strength_fair
        PasswordStrength.Good -> Res.string.password_strength_good
        PasswordStrength.Strong -> Res.string.password_strength_strong
    },
)

internal val PasswordStrength.segments: Int get() = ordinal + 1

@Composable
internal fun PasswordStrength.color(): Color {
    val casino = RoyalTheme.casinoColors
    return when (this) {
        PasswordStrength.Weak -> casino.suitRed
        PasswordStrength.Fair -> casino.warning
        PasswordStrength.Good, PasswordStrength.Strong -> casino.success
    }
}
