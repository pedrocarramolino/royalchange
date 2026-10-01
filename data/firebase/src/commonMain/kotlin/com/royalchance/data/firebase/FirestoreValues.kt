package com.royalchance.data.firebase

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Colecciones de Firestore. */
internal object FirestorePaths {
    const val PLAYERS = "players"
    const val ALIASES = "aliases"
}

/** Documento de `aliases/{aliasKey}`: a quién pertenece el alias. */
internal fun aliasDocument(uid: String, alias: String): Map<String, Any?> = mapOf("uid" to uid, "alias" to alias)

/** Documento → mapa de valores de Firestore (SDK de Android). */
internal fun PlayerDocument.toFirestoreMap(): Map<String, Any?> {
    val element = PlayerDocument.json.encodeToJsonElement(PlayerDocument.serializer(), this)
    @Suppress("UNCHECKED_CAST")
    return element.toFirestoreValue() as Map<String, Any?>
}

/** Mapa de valores de Firestore (SDK de Android) → documento. */
internal fun playerDocumentFromFirestore(data: Map<String, Any?>): PlayerDocument =
    PlayerDocument.json.decodeFromJsonElement(PlayerDocument.serializer(), data.toJsonElement())

/** Documento → JSON (SDK web, que recibe objetos JavaScript). */
internal fun PlayerDocument.toJson(): String = PlayerDocument.json.encodeToString(PlayerDocument.serializer(), this)

/** JSON (SDK web) → documento. */
internal fun playerDocumentFromJson(json: String): PlayerDocument =
    PlayerDocument.json.decodeFromString(PlayerDocument.serializer(), json)

private fun JsonElement.toFirestoreValue(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull
    }
    is JsonObject -> mapValues { it.value.toFirestoreValue() }
    is JsonArray -> map { it.toFirestoreValue() }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is List<*> -> JsonArray(map { it.toJsonElement() })
    // Tipos propios de Firestore (Timestamp, GeoPoint…) no se usan en este documento.
    else -> JsonPrimitive(toString())
}
