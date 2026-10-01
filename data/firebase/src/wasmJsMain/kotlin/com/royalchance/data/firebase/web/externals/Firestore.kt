@file:JsModule("firebase/firestore")

package com.royalchance.data.firebase.web.externals

import kotlin.js.Promise

external interface Firestore : JsAny

external interface DocumentReference : JsAny

external interface SnapshotMetadata : JsAny {
    val fromCache: Boolean
}

external interface DocumentSnapshot : JsAny {
    val metadata: SnapshotMetadata
    fun exists(): Boolean
    fun data(): JsAny?
}

external interface WriteBatch : JsAny {
    fun set(documentRef: DocumentReference, data: JsAny): WriteBatch
    fun delete(documentRef: DocumentReference): WriteBatch
    fun commit(): Promise<JsAny?>
}

external fun initializeFirestore(app: FirebaseApp, settings: JsAny): Firestore

external fun persistentLocalCache(settings: JsAny?): JsAny

external fun persistentMultipleTabManager(): JsAny

external fun memoryLocalCache(): JsAny

external fun connectFirestoreEmulator(firestore: Firestore, host: String, port: Int)

external fun doc(firestore: Firestore, path: String): DocumentReference

external fun getDocFromServer(reference: DocumentReference): Promise<DocumentSnapshot>

external fun writeBatch(firestore: Firestore): WriteBatch

/** Devuelve la función para cancelar la suscripción. */
external fun onSnapshot(
    reference: DocumentReference,
    onNext: (DocumentSnapshot) -> Unit,
    onError: (JsAny) -> Unit,
): () -> Unit
