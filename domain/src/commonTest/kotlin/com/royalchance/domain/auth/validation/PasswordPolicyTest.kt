package com.royalchance.domain.auth.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasswordPolicyTest {

    @Test
    fun reportsEachUnmetRequirement() {
        assertEquals(
            setOf(
                PasswordRequirement.MinLength,
                PasswordRequirement.Uppercase,
                PasswordRequirement.Digit,
            ),
            PasswordPolicy.unmetRequirements("abc"),
        )
        assertEquals(emptySet(), PasswordPolicy.unmetRequirements("Secreto123"))
    }

    @Test
    fun validation() {
        assertEquals(FieldError.Required, PasswordPolicy.validate(""))
        assertEquals(FieldError.PasswordRequirementsNotMet, PasswordPolicy.validate("SECRETO123"))
        assertEquals(FieldError.PasswordTooLong, PasswordPolicy.validate("Aa1" + "x".repeat(200)))
        assertNull(PasswordPolicy.validate("Secreto123"))
    }

    @Test
    fun strengthGrowsWithLengthAndSymbols() {
        assertEquals(PasswordStrength.Weak, PasswordPolicy.strength("secreto123"))
        assertEquals(PasswordStrength.Fair, PasswordPolicy.strength("Secreto123"))
        assertEquals(PasswordStrength.Good, PasswordPolicy.strength("Secreto123!"))
        assertEquals(PasswordStrength.Good, PasswordPolicy.strength("SecretoLargo123"))
        assertEquals(PasswordStrength.Strong, PasswordPolicy.strength("SecretoLargo123!"))
    }
}
