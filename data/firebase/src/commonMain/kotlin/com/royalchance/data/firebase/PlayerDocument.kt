package com.royalchance.data.firebase

import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.PlayerProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Documento `players/{uid}` de Firestore. Las fechas se guardan como milisegundos UTC (números),
 * que ambos SDK tratan igual. Su forma exacta la validan las reglas de seguridad.
 */
@Serializable
internal data class PlayerDocument(
    val uid: String,
    val alias: String,
    /** Alias en minúsculas: clave del documento `aliases/{aliasKey}` que lo reserva. */
    val aliasKey: String,
    val avatar: String,
    val countryCode: String,
    val birthYear: Int,
    val marketingOptIn: Boolean,
    val consents: ConsentsDocument,
    val createdAtMillis: Long,
) {
    fun toProfile() = PlayerProfile(
        alias = alias,
        // Un avatar retirado en una versión futura no debe dejar al jugador sin perfil.
        avatar = AvatarId.entries.firstOrNull { it.name == avatar } ?: AvatarId.SpadeGold,
        countryCode = countryCode,
        birthYear = birthYear,
        marketingOptIn = marketingOptIn,
        consents = consents.toDomain(),
    )

    companion object {
        fun from(uid: String, profile: PlayerProfile, now: Instant) = PlayerDocument(
            uid = uid,
            alias = profile.alias,
            aliasKey = aliasKey(profile.alias),
            avatar = profile.avatar.name,
            countryCode = profile.countryCode,
            birthYear = profile.birthYear,
            marketingOptIn = profile.marketingOptIn,
            consents = ConsentsDocument.from(profile.consents),
            createdAtMillis = now.toEpochMilliseconds(),
        )

        /** Los alias son únicos sin distinguir mayúsculas. */
        fun aliasKey(alias: String): String = alias.trim().lowercase()

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class ConsentsDocument(
    val termsVersion: String,
    val privacyVersion: String,
    val acceptedAtMillis: Long,
) {
    fun toDomain() = LegalConsents(termsVersion, privacyVersion, Instant.fromEpochMilliseconds(acceptedAtMillis))

    companion object {
        fun from(consents: LegalConsents) = ConsentsDocument(
            termsVersion = consents.termsVersion,
            privacyVersion = consents.privacyVersion,
            acceptedAtMillis = consents.acceptedAt.toEpochMilliseconds(),
        )
    }
}
