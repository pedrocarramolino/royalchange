package com.royalchance.data.firebase

import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.data.firebase.gateway.FirebaseGatewayException
import com.royalchance.data.firebase.gateway.GatewayUser
import com.royalchance.data.firebase.gateway.PlayerStore
import com.royalchance.data.firebase.web.aliasDocumentJs
import com.royalchance.data.firebase.web.authEmulatorOptions
import com.royalchance.data.firebase.web.awaitFirebase
import com.royalchance.data.firebase.web.externals.Auth
import com.royalchance.data.firebase.web.externals.EmailAuthProvider
import com.royalchance.data.firebase.web.externals.Firestore
import com.royalchance.data.firebase.web.externals.User
import com.royalchance.data.firebase.web.externals.connectAuthEmulator
import com.royalchance.data.firebase.web.externals.connectFirestoreEmulator
import com.royalchance.data.firebase.web.externals.createUserWithEmailAndPassword
import com.royalchance.data.firebase.web.externals.doc
import com.royalchance.data.firebase.web.externals.getAuth
import com.royalchance.data.firebase.web.externals.getDocFromServer
import com.royalchance.data.firebase.web.externals.initializeApp
import com.royalchance.data.firebase.web.externals.initializeFirestore
import com.royalchance.data.firebase.web.externals.memoryLocalCache
import com.royalchance.data.firebase.web.externals.onAuthStateChanged
import com.royalchance.data.firebase.web.externals.onSnapshot
import com.royalchance.data.firebase.web.externals.persistentLocalCache
import com.royalchance.data.firebase.web.externals.persistentMultipleTabManager
import com.royalchance.data.firebase.web.externals.reauthenticateWithCredential
import com.royalchance.data.firebase.web.externals.sendPasswordResetEmail
import com.royalchance.data.firebase.web.externals.signInWithEmailAndPassword
import com.royalchance.data.firebase.web.externals.writeBatch
import com.royalchance.data.firebase.web.firebaseOptions
import com.royalchance.data.firebase.web.firestoreSettings
import com.royalchance.data.firebase.web.jsonParse
import com.royalchance.data.firebase.web.jsonStringify
import com.royalchance.data.firebase.web.persistentCacheSettings
import com.royalchance.data.firebase.web.toGatewayException
import com.royalchance.domain.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.time.Clock
import com.royalchance.data.firebase.web.externals.deleteUser as jsDeleteUser
import com.royalchance.data.firebase.web.externals.reload as jsReload
import com.royalchance.data.firebase.web.externals.sendEmailVerification as jsSendEmailVerification
import com.royalchance.data.firebase.web.externals.signOut as jsSignOut

/** Punto de entrada de Firebase en la web (SDK oficial de JavaScript). */
object WebFirebase {

    /**
     * @param persistentCache caché de Firestore en IndexedDB (la app funciona sin conexión).
     *   Solo se desactiva en entornos sin IndexedDB.
     */
    fun authRepository(
        environment: FirebaseEnvironment,
        scope: CoroutineScope,
        clock: Clock = Clock.System,
        persistentCache: Boolean = true,
    ): AuthRepository {
        val app = initializeApp(
            firebaseOptions(
                apiKey = FirebaseProjectConfig.API_KEY,
                authDomain = FirebaseProjectConfig.AUTH_DOMAIN,
                projectId = FirebaseProjectConfig.PROJECT_ID,
                appId = FirebaseProjectConfig.WEB_APP_ID,
                messagingSenderId = FirebaseProjectConfig.MESSAGING_SENDER_ID,
            ),
        )
        val auth = getAuth(app)
        val cache = if (persistentCache) {
            persistentLocalCache(persistentCacheSettings(persistentMultipleTabManager()))
        } else {
            memoryLocalCache()
        }
        val firestore = initializeFirestore(app, firestoreSettings(cache))
        if (environment is FirebaseEnvironment.Emulators) {
            connectAuthEmulator(auth, "http://${environment.host}:${FirebaseEnvironment.AUTH_EMULATOR_PORT}", authEmulatorOptions())
            connectFirestoreEmulator(firestore, environment.host, FirebaseEnvironment.FIRESTORE_EMULATOR_PORT)
        }
        return FirebaseAuthRepository(
            auth = WebAuthGateway(auth),
            players = WebPlayerStore(firestore),
            clock = clock,
            scope = scope,
        )
    }
}

internal class WebAuthGateway(private val auth: Auth) : AuthGateway {

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val users: Flow<GatewayUser?> = merge(
        callbackFlow {
            val unsubscribe = onAuthStateChanged(auth) { user -> trySend(user?.toGatewayUser()) }
            awaitClose { unsubscribe() }
        },
        // onAuthStateChanged no avisa de cambios como la verificación del email: se emite tras reload().
        refreshes.map { auth.currentUser?.toGatewayUser() },
    ).distinctUntilChanged()

    override fun currentUser(): GatewayUser? = auth.currentUser?.toGatewayUser()

    override suspend fun signInWithEmail(email: String, password: String) {
        signInWithEmailAndPassword(auth, email, password).awaitFirebase()
    }

    override suspend fun createAccount(email: String, password: String) {
        createUserWithEmailAndPassword(auth, email, password).awaitFirebase()
    }

    override suspend fun reauthenticate(password: String) {
        val user = requireUser()
        val email = user.email ?: throw FirebaseGatewayException(FirebaseErrorCode.InvalidCredential)
        reauthenticateWithCredential(user, EmailAuthProvider.credential(email, password)).awaitFirebase()
    }

    override suspend fun sendPasswordReset(email: String) {
        sendPasswordResetEmail(auth, email).awaitFirebase()
    }

    override suspend fun sendEmailVerification() {
        jsSendEmailVerification(requireUser()).awaitFirebase()
    }

    override suspend fun reload() {
        jsReload(requireUser()).awaitFirebase()
        refreshes.tryEmit(Unit)
    }

    override suspend fun deleteUser() {
        jsDeleteUser(requireUser()).awaitFirebase()
    }

    override suspend fun signOut() {
        jsSignOut(auth).awaitFirebase()
    }

    private fun requireUser(): User = auth.currentUser ?: throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
}

internal class WebPlayerStore(private val db: Firestore) : PlayerStore {

    override fun observe(uid: String): Flow<PlayerDocument?> = callbackFlow {
        val unsubscribe = onSnapshot(
            reference = doc(db, "${FirestorePaths.PLAYERS}/$uid"),
            onNext = { snapshot ->
                // Sin conexión y sin caché, Firestore responde "no existe": se espera a la respuesta del servidor.
                if (snapshot.exists() || !snapshot.metadata.fromCache) {
                    runCatching { snapshot.data()?.let { playerDocumentFromJson(jsonStringify(it)) } }
                        .onSuccess { trySend(it) }
                        .onFailure { close(it) }
                }
            },
            onError = { error -> close(error.toGatewayException()) },
        )
        awaitClose { unsubscribe() }
    }

    override suspend fun aliasExists(aliasKey: String): Boolean =
        getDocFromServer(doc(db, "${FirestorePaths.ALIASES}/$aliasKey")).awaitFirebase().exists()

    override suspend fun saveProfile(document: PlayerDocument) {
        val batch = writeBatch(db)
        batch.set(doc(db, "${FirestorePaths.ALIASES}/${document.aliasKey}"), aliasDocumentJs(document.uid, document.alias))
        batch.set(doc(db, "${FirestorePaths.PLAYERS}/${document.uid}"), jsonParse(document.toJson()))
        batch.commit().awaitFirebase()
    }

    override suspend fun delete(uid: String, aliasKey: String?) {
        val batch = writeBatch(db)
        batch.delete(doc(db, "${FirestorePaths.PLAYERS}/$uid"))
        if (aliasKey != null) batch.delete(doc(db, "${FirestorePaths.ALIASES}/$aliasKey"))
        batch.commit().awaitFirebase()
    }
}

private fun User.toGatewayUser() = GatewayUser(
    uid = uid,
    email = email.orEmpty(),
    emailVerified = emailVerified,
)
