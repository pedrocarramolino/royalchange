@file:JsModule("firebase/firestore")

package com.royalchance.data.firebase.web.externals

import kotlin.js.Promise

external interface Firestore : JsAny

external interface DocumentReference : JsAny

external interface CollectionReference : Query

/** Consulta de Firestore (una colección también lo es). */
external interface Query : JsAny

external interface QueryConstraint : JsAny

external interface SnapshotMetadata : JsAny {
    val fromCache: Boolean
}

external interface DocumentSnapshot : JsAny {
    val metadata: SnapshotMetadata
    fun exists(): Boolean
    fun data(): JsAny?
}

external interface QueryDocumentSnapshot : DocumentSnapshot {
    val ref: DocumentReference
}

external interface QuerySnapshot : JsAny {
    val docs: JsArray<QueryDocumentSnapshot>
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

external fun collection(firestore: Firestore, path: String): CollectionReference

external fun getDocFromServer(reference: DocumentReference): Promise<DocumentSnapshot>

/** Lee la caché local (con escrituras pendientes). Se rechaza con "unavailable" si no está en caché. */
external fun getDocFromCache(reference: DocumentReference): Promise<DocumentSnapshot>

external fun getDocsFromServer(query: CollectionReference): Promise<QuerySnapshot>

/** Del servidor si hay conexión; si no, de la caché local. */
external fun getDocs(query: Query): Promise<QuerySnapshot>

// `query` admite cualquier número de restricciones; se declaran las aridades que se usan.
external fun query(query: Query, first: QueryConstraint, second: QueryConstraint): Query

external fun query(query: Query, first: QueryConstraint, second: QueryConstraint, third: QueryConstraint): Query

/** [operator]: "<", ">"… */
external fun where(field: String, operator: String, value: JsAny): QueryConstraint

/** [direction]: "asc" o "desc". */
external fun orderBy(field: String, direction: String): QueryConstraint

external fun limit(count: Int): QueryConstraint

external fun writeBatch(firestore: Firestore): WriteBatch

/** Devuelve la función para cancelar la suscripción. [options]: `{ includeMetadataChanges }`. */
external fun onSnapshot(
    reference: DocumentReference,
    options: JsAny,
    onNext: (DocumentSnapshot) -> Unit,
    onError: (JsAny) -> Unit,
): () -> Unit
