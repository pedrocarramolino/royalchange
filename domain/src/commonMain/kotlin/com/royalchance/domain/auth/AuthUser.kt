package com.royalchance.domain.auth

import kotlin.time.Instant

/** Usuario autenticado con email y contraseña. */
data class AuthUser(
    val id: String,
    val email: String,
    val isEmailVerified: Boolean,
    /**
     * `null` solo si el registro se interrumpió después de crear la cuenta y antes de guardar el
     * perfil (p. ej. otro jugador reservó el alias en ese instante): se pide completarlo.
     */
    val profile: PlayerProfile?,
) {
    val needsProfileCompletion: Boolean get() = profile == null
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
