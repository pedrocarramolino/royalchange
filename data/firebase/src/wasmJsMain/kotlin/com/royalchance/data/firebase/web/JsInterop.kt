package com.royalchance.data.firebase.web

import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.data.firebase.gateway.FirebaseGatewayException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise

/**
 * Espera una promesa del SDK web. Si se rechaza, conserva el código de error de Firebase
 * (`auth/wrong-password`, `permission-denied`…) para traducirlo a un error de dominio.
 */
internal suspend fun <T : JsAny?> Promise<T>.awaitFirebase(): T = suspendCancellableCoroutine { continuation ->
    then(
        onFulfilled = { value ->
            continuation.resume(value)
            null
        },
        onRejected = { error ->
            continuation.resumeWithException(error.toGatewayException())
            null
        },
    )
}

internal fun JsAny?.toGatewayException(): FirebaseGatewayException =
    FirebaseGatewayException(FirebaseErrorCode.fromPlatformCode(errorCode(this)))

private fun errorCode(error: JsAny?): String? = js("(error && error.code) ? String(error.code) : null")

internal fun jsonParse(json: String): JsAny = js("JSON.parse(json)")

internal fun jsonStringify(value: JsAny): String = js("JSON.stringify(value)")

internal fun firebaseOptions(
    apiKey: String,
    authDomain: String,
    projectId: String,
    appId: String,
    messagingSenderId: String,
): JsAny = js(
    "({ apiKey: apiKey, authDomain: authDomain, projectId: projectId, appId: appId, messagingSenderId: messagingSenderId })",
)

internal fun persistentCacheSettings(tabManager: JsAny): JsAny = js("({ tabManager: tabManager })")

internal fun firestoreSettings(localCache: JsAny): JsAny = js("({ localCache: localCache })")

internal fun authEmulatorOptions(): JsAny = js("({ disableWarnings: true })")

internal fun aliasDocumentJs(uid: String, alias: String): JsAny = js("({ uid: uid, alias: alias })")
