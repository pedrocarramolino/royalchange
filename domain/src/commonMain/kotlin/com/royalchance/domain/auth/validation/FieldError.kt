package com.royalchance.domain.auth.validation

/** Motivo por el que un campo de formulario no es válido. La UI lo traduce a un mensaje. */
enum class FieldError {
    Required,

    AliasTooShort,
    AliasTooLong,
    AliasMustStartWithLetter,
    AliasInvalidCharacters,
    AliasTaken,

    EmailInvalid,
    EmailInUse,

    PasswordRequirementsNotMet,
    PasswordTooLong,
    PasswordsDoNotMatch,

    BirthDateInFuture,
    BirthDateImplausible,
    Underage,

    MustAccept,
}
