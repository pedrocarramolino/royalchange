package com.royalchance.data.firebase

import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FirebaseMappingTest {

    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val profile = PlayerProfile(
        alias = "AsDePicas",
        avatar = AvatarId.ClubEmerald,
        countryCode = "MX",
        birthYear = 1988,
        marketingOptIn = false,
        consents = LegalConsents("2026-09-30", "2026-09-30", now),
    )

    @Test
    fun webAndAndroidErrorCodesMapToTheSameCase() {
        val cases = mapOf(
            "auth/wrong-password" to FirebaseErrorCode.InvalidCredential,
            "ERROR_WRONG_PASSWORD" to FirebaseErrorCode.InvalidCredential,
            "auth/invalid-credential" to FirebaseErrorCode.InvalidCredential,
            "ERROR_INVALID_CREDENTIAL" to FirebaseErrorCode.InvalidCredential,
            "auth/email-already-in-use" to FirebaseErrorCode.EmailAlreadyInUse,
            "ERROR_EMAIL_ALREADY_IN_USE" to FirebaseErrorCode.EmailAlreadyInUse,
            "auth/requires-recent-login" to FirebaseErrorCode.RequiresRecentLogin,
            "ERROR_REQUIRES_RECENT_LOGIN" to FirebaseErrorCode.RequiresRecentLogin,
            "permission-denied" to FirebaseErrorCode.PermissionDenied,
            "PERMISSION_DENIED" to FirebaseErrorCode.PermissionDenied,
            "auth/network-request-failed" to FirebaseErrorCode.Network,
            "UNAVAILABLE" to FirebaseErrorCode.Network,
            "auth/operation-not-allowed" to FirebaseErrorCode.OperationNotAllowed,
            "auth/configuration-not-found" to FirebaseErrorCode.OperationNotAllowed,
            "ERROR_OPERATION_NOT_ALLOWED" to FirebaseErrorCode.OperationNotAllowed,
            "auth/algo-nuevo" to FirebaseErrorCode.Unknown,
        )
        cases.forEach { (raw, expected) -> assertEquals(expected, FirebaseErrorCode.fromPlatformCode(raw), raw) }
        assertEquals(FirebaseErrorCode.Unknown, FirebaseErrorCode.fromPlatformCode(null))
    }

    @Test
    fun registeredDocumentRoundTripsThroughBothSdkFormats() {
        val document = PlayerDocument.from("uid-1", profile, now)

        assertEquals(document, fromFirestoreMap<PlayerDocument>(document.toFirestoreMap()))
        assertEquals(document, fromJson<PlayerDocument>(document.toJson()))
        assertEquals(profile, document.toProfile())
        assertEquals("asdepicas", document.aliasKey)
    }

    @Test
    fun documentHasExactlyTheFieldsTheSecurityRulesExpect() {
        val map = PlayerDocument.from("uid-1", profile, now).toFirestoreMap()

        assertEquals(
            setOf("uid", "alias", "aliasKey", "avatar", "countryCode", "birthYear", "marketingOptIn", "consents", "createdAtMillis"),
            map.keys,
        )
        assertEquals(setOf("termsVersion", "privacyVersion", "acceptedAtMillis"), (map["consents"] as Map<*, *>).keys)
    }

    @Test
    fun unknownAvatarFallsBackInsteadOfLosingTheProfile() {
        val document = PlayerDocument.from("uid-1", profile, now).copy(avatar = "AvatarRetirado")

        assertEquals(AvatarId.SpadeGold, document.toProfile().avatar)
    }

    @Test
    fun integersStayIntegersForFirestore() {
        val map = PlayerDocument.from("uid-1", profile, now).toFirestoreMap()

        assertEquals(1988L, map["birthYear"])
        assertEquals(now.toEpochMilliseconds(), map["createdAtMillis"])
    }
}
