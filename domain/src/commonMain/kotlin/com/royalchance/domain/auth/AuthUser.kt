package com.royalchance.domain.auth

import kotlin.time.Instant

/** Usuario autenticado. Los invitados no tienen email ni perfil. */
data class AuthUser(
    val id: String,
    val email: String?,
    val isGuest: Boolean,
    val isEmailVerified: Boolean,
    /** `null` hasta que el jugador completa su perfil (p. ej. tras entrar con Google por primera vez). */
    val profile: PlayerProfile?,
) {
    /** Una cuenta registrada sin perfil debe completarlo antes de jugar. Los invitados, no. */
    val needsProfileCompletion: Boolean get() = !isGuest && profile == null
}

/** Datos públicos y de cumplimiento del jugador. */
data class PlayerProfile(
    val alias: String,
    val avatar: AvatarId,
    val countryCode: String,
    /** Solo el año: basta para acreditar la mayoría de edad (minimización de datos, RGPD). */
    val birthYear: Int,
    val marketingOptIn: Boolean,
    val consents: LegalConsents,
)

/** Constancia de qué versión de cada documento legal aceptó el jugador y cuándo. */
data class LegalConsents(
    val termsVersion: String,
    val privacyVersion: String,
    val acceptedAt: Instant,
)

/** Versiones vigentes de los documentos legales. Cambiarlas obligará a pedir de nuevo la aceptación. */
object LegalDocumentVersions {
    const val TERMS = "2026-09-30"
    const val PRIVACY = "2026-09-30"
}

/** Avatares predefinidos (subir fotos requeriría almacenamiento de pago). Los ids son estables. */
enum class AvatarId {
    SpadeGold,
    HeartRuby,
    DiamondGold,
    ClubEmerald,
    SpadeIvory,
    HeartGold,
    DiamondRuby,
    ClubGold,
}
