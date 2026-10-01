package com.royalchance.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.data.firebase.gateway.FirebaseGatewayException
import com.royalchance.data.firebase.gateway.GatewayUser
import com.royalchance.data.firebase.gateway.PlayerStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.tasks.await

internal class AndroidAuthGateway(private val auth: FirebaseAuth) : AuthGateway {

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val users: Flow<GatewayUser?> = merge(
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toGatewayUser()) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        },
        // AuthStateListener no avisa de cambios como la verificación del email: se emite tras reload().
        refreshes.map { auth.currentUser?.toGatewayUser() },
    ).distinctUntilChanged()

    override fun currentUser(): GatewayUser? = auth.currentUser?.toGatewayUser()

    override suspend fun signInWithEmail(email: String, password: String) = unit {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun createAccount(email: String, password: String) = unit {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    override suspend fun reauthenticate(password: String) = unit {
        val user = requireUser()
        val email = user.email ?: throw FirebaseGatewayException(FirebaseErrorCode.InvalidCredential)
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    override suspend fun sendPasswordReset(email: String) = unit { auth.sendPasswordResetEmail(email).await() }

    override suspend fun sendEmailVerification() = unit {
        requireUser().sendEmailVerification().await()
    }

    override suspend fun reload() = unit {
        requireUser().reload().await()
        refreshes.tryEmit(Unit)
    }

    override suspend fun deleteUser() = unit { requireUser().delete().await() }

    override suspend fun signOut() = unit { auth.signOut() }

    private fun requireUser(): FirebaseUser =
        auth.currentUser ?: throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
}

internal class AndroidPlayerStore(private val db: FirebaseFirestore) : PlayerStore {

    override fun observe(uid: String): Flow<PlayerDocument?> = callbackFlow {
        val registration = db.collection(FirestorePaths.PLAYERS).document(uid).addSnapshotListener { snapshot, error ->
            when {
                error != null -> close(error.toGatewayException())
                snapshot == null -> Unit
                // Sin conexión y sin caché, Firestore responde "no existe": se espera a la respuesta del servidor.
                !snapshot.exists() && snapshot.metadata.isFromCache -> Unit
                else -> runCatching { snapshot.data?.let(::playerDocumentFromFirestore) }
                    .onSuccess { trySend(it) }
                    .onFailure { close(it) }
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun aliasExists(aliasKey: String): Boolean = call {
        db.collection(FirestorePaths.ALIASES).document(aliasKey).get(Source.SERVER).await().exists()
    }

    override suspend fun saveProfile(document: PlayerDocument) = unit {
        db.batch()
            .set(db.collection(FirestorePaths.ALIASES).document(document.aliasKey), aliasDocument(document.uid, document.alias))
            .set(db.collection(FirestorePaths.PLAYERS).document(document.uid), document.toFirestoreMap())
            .commit()
            .await()
    }

    override suspend fun delete(uid: String, aliasKey: String?) = unit {
        val batch = db.batch().delete(db.collection(FirestorePaths.PLAYERS).document(uid))
        if (aliasKey != null) batch.delete(db.collection(FirestorePaths.ALIASES).document(aliasKey))
        batch.commit().await()
    }
}

private fun FirebaseUser.toGatewayUser() = GatewayUser(
    uid = uid,
    email = email.orEmpty(),
    emailVerified = isEmailVerified,
)

/** Como [call], para operaciones cuyo resultado no interesa. */
private suspend fun unit(block: suspend () -> Any?) {
    call(block)
}

/** Ejecuta una llamada al SDK traduciendo sus excepciones a [FirebaseGatewayException]. */
private suspend fun <T> call(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw e.toGatewayException()
}

private fun Throwable.toGatewayException(): FirebaseGatewayException = when (this) {
    is FirebaseGatewayException -> this
    is FirebaseAuthException -> FirebaseGatewayException(FirebaseErrorCode.fromPlatformCode(errorCode), this)
    is FirebaseTooManyRequestsException -> FirebaseGatewayException(FirebaseErrorCode.TooManyRequests, this)
    is FirebaseNetworkException -> FirebaseGatewayException(FirebaseErrorCode.Network, this)
    is FirebaseFirestoreException -> FirebaseGatewayException(FirebaseErrorCode.fromPlatformCode(code.name), this)
    else -> FirebaseGatewayException(FirebaseErrorCode.Unknown, this)
}
