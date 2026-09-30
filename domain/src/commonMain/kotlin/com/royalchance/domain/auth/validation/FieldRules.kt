package com.royalchance.domain.auth.validation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil

/** Reglas del alias público: 3–20 caracteres, empieza por letra; letras, números y guion bajo. */
object AliasRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20
    private val allowed = Regex("^[A-Za-z0-9_]+$")

    fun normalize(alias: String): String = alias.trim()

    fun validate(alias: String): FieldError? {
        val value = normalize(alias)
        return when {
            value.isEmpty() -> FieldError.Required
            value.length < MIN_LENGTH -> FieldError.AliasTooShort
            value.length > MAX_LENGTH -> FieldError.AliasTooLong
            !value.matches(allowed) -> FieldError.AliasInvalidCharacters
            !value.first().isLetter() -> FieldError.AliasMustStartWithLetter
            else -> null
        }
    }
}

/**
 * Validación de formato del email. La prueba definitiva de que existe es el enlace de verificación,
 * así que aquí solo se descartan errores evidentes.
 */
object EmailRules {
    private val pattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun normalize(email: String): String = email.trim()

    fun validate(email: String): FieldError? {
        val value = normalize(email)
        return when {
            value.isEmpty() -> FieldError.Required
            !value.matches(pattern) -> FieldError.EmailInvalid
            else -> null
        }
    }
}

/** Requisitos mínimos de la contraseña, mostrados al usuario como lista de comprobación. */
enum class PasswordRequirement {
    MinLength,
    Lowercase,
    Uppercase,
    Digit,
}

enum class PasswordStrength { Weak, Fair, Good, Strong }

object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    fun unmetRequirements(password: String): Set<PasswordRequirement> = buildSet {
        if (password.length < MIN_LENGTH) add(PasswordRequirement.MinLength)
        if (password.none { it.isLowerCase() }) add(PasswordRequirement.Lowercase)
        if (password.none { it.isUpperCase() }) add(PasswordRequirement.Uppercase)
        if (password.none { it.isDigit() }) add(PasswordRequirement.Digit)
    }

    fun validate(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.Required
        password.length > MAX_LENGTH -> FieldError.PasswordTooLong
        unmetRequirements(password).isNotEmpty() -> FieldError.PasswordRequirementsNotMet
        else -> null
    }

    /**
     * Fortaleza orientativa para el indicador visual. Una contraseña que no cumple los requisitos
     * siempre es débil; a partir de ahí suman la longitud y el uso de símbolos.
     */
    fun strength(password: String): PasswordStrength {
        if (unmetRequirements(password).isNotEmpty()) return PasswordStrength.Weak
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        val score = listOf(password.length >= 12, password.length >= 16, hasSymbol).count { it }
        return when (score) {
            0 -> PasswordStrength.Fair
            1 -> PasswordStrength.Good
            else -> PasswordStrength.Strong
        }
    }
}

/** Casino social: solo mayores de edad, aunque no haya dinero real. */
object AgeRules {
    const val MINIMUM_AGE = 18

    /** Margen para detectar fechas imposibles (errores al teclear el año). */
    const val MAXIMUM_PLAUSIBLE_AGE = 120

    fun validate(birthDate: LocalDate?, today: LocalDate): FieldError? {
        if (birthDate == null) return FieldError.Required
        if (birthDate > today) return FieldError.BirthDateInFuture
        val age = birthDate.yearsUntil(today)
        return when {
            age > MAXIMUM_PLAUSIBLE_AGE -> FieldError.BirthDateImplausible
            age < MINIMUM_AGE -> FieldError.Underage
            else -> null
        }
    }
}
