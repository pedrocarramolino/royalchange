package com.royalchance.data.firebase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer

/** Colecciones de Firestore. */
internal object FirestorePaths {
    const val PLAYERS = "players"
    const val ALIASES = "aliases"
    const val WALLETS = "wallets"

    /** Subcolección de cada monedero: `wallets/{uid}/ledger/{entryId}`. */
    const val LEDGER = "ledger"

    fun ledger(uid: String): String = "$WALLETS/$uid/$LEDGER"
}

/** Máximo de escrituras en un único lote de Firestore. */
internal const val MAX_BATCH_WRITES = 500

/** Documento de `aliases/{aliasKey}`: a quién pertenece el alias. */
internal fun aliasDocument(uid: String, alias: String): Map<String, Any?> = mapOf("uid" to uid, "alias" to alias)

/**
 * Formato de los documentos: los campos opcionales vacíos no se escriben (las reglas exigen una
 * forma exacta y no admiten nulos) y los campos desconocidos se ignoran al leer.
 */
@PublishedApi
internal val FirestoreJson: Json = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** Documento → mapa de valores de Firestore (SDK de Android). */
internal inline fun <reified T> T.toFirestoreMap(): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return FirestoreJson.encodeToJsonElement(serializer<T>(), this).toFirestoreValue() as Map<String, Any?>
}

/** Mapa de valores de Firestore (SDK de Android) → documento. */
internal inline fun <reified T> fromFirestoreMap(data: Map<String, Any?>): T =
    FirestoreJson.decodeFromJsonElement(serializer<T>(), data.toJsonElement())

/** Documento → JSON (SDK web, que recibe objetos JavaScript). */
internal inline fun <reified T> T.toJson(): String = FirestoreJson.encodeToString(serializer<T>(), this)

/** JSON (SDK web) → documento. */
internal inline fun <reified T> fromJson(json: String): T = FirestoreJson.decodeFromString(serializer<T>(), json)

@PublishedApi
internal fun JsonElement.toFirestoreValue(): Any? = when (this) {
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

@PublishedApi
internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is List<*> -> JsonArray(map { it.toJsonElement() })
    // Tipos propios de Firestore (Timestamp, GeoPoint…) no se usan en estos documentos.
    else -> JsonPrimitive(toString())
}
