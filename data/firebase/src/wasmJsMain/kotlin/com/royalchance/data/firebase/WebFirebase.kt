package com.royalchance.data.firebase

import com.royalchance.core.common.random.ProductionRandomGenerator
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.data.firebase.gateway.FirebaseGatewayException
import com.royalchance.data.firebase.gateway.GatewayUser
import com.royalchance.data.firebase.gateway.PendingWrite
import com.royalchance.data.firebase.gateway.PlayerStore
import com.royalchance.data.firebase.gateway.WalletStore
import com.royalchance.data.firebase.web.aliasDocumentJs
import com.royalchance.data.firebase.web.authEmulatorOptions
import com.royalchance.data.firebase.web.awaitFirebase
import com.royalchance.data.firebase.web.externals.Auth
import com.royalchance.data.firebase.web.externals.DocumentReference
import com.royalchance.data.firebase.web.externals.EmailAuthProvider
import com.royalchance.data.firebase.web.externals.Firestore
import com.royalchance.data.firebase.web.externals.User
import com.royalchance.data.firebase.web.externals.collection
import com.royalchance.data.firebase.web.externals.connectAuthEmulator
import com.royalchance.data.firebase.web.externals.connectFirestoreEmulator
import com.royalchance.data.firebase.web.externals.createUserWithEmailAndPassword
import com.royalchance.data.firebase.web.externals.doc
import com.royalchance.data.firebase.web.externals.getAuth
import com.royalchance.data.firebase.web.externals.getDocFromCache
import com.royalchance.data.firebase.web.externals.getDocFromServer
import com.royalchance.data.firebase.web.externals.getDocsFromServer
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
import com.royalchance.data.firebase.web.includeMetadataChanges
import com.royalchance.data.firebase.web.jsonParse
import com.royalchance.data.firebase.web.jsonStringify
import com.royalchance.data.firebase.web.persistentCacheSettings
import com.royalchance.data.firebase.web.toGatewayException
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
     * Crea los repositorios sobre Firebase. Llamar una sola vez por página.
     *
     * @param scope ámbito de vida de la página: mantiene la sesión y el monedero observados.
     * @param persistentCache caché de Firestore en IndexedDB (la app funciona sin conexión).
     *   Solo se desactiva en entornos sin IndexedDB.
     */
    fun repositories(
        environment: FirebaseEnvironment,
        scope: CoroutineScope,
        clock: Clock = Clock.System,
        random: RandomGenerator = ProductionRandomGenerator(),
        persistentCache: Boolean = true,
    ): FirebaseRepositories {
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
        return FirebaseRepositories.create(
            authGateway = WebAuthGateway(auth),
            playerStore = WebPlayerStore(firestore),
            walletStore = WebWalletStore(firestore),
            clock = clock,
            random = random,
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

    override fun observe(uid: String): Flow<PlayerDocument?> =
        doc(db, "${FirestorePaths.PLAYERS}/$uid").observeConfirmed { fromJson<PlayerDocument>(it) }

    override suspend fun aliasExists(aliasKey: String): Boolean =
        getDocFromServer(doc(db, "${FirestorePaths.ALIASES}/$aliasKey")).awaitFirebase().exists()

    override suspend fun saveProfile(document: PlayerDocument) {
        val batch = writeBatch(db)
        batch.set(doc(db, "${FirestorePaths.ALIASES}/${document.aliasKey}"), aliasDocumentJs(document.uid, document.alias))
        batch.set(doc(db, "${FirestorePaths.PLAYERS}/${document.uid}"), jsonParse(document.toJson()))
        batch.commit().awaitFirebase()
    }

    override suspend fun deleteAccountData(uid: String, aliasKey: String?) {
        val snapshot = getDocsFromServer(collection(db, FirestorePaths.ledger(uid))).awaitFirebase()
        val entries = (0 until snapshot.docs.length).mapNotNull { snapshot.docs[it]?.ref }
        // Primer lote: perfil, alias y monedero (y los asientos que quepan). Los asientos restantes,
        // en lotes sucesivos: las reglas solo los dejan borrar cuando el monedero ya no existe.
        val firstEntries = entries.take(MAX_BATCH_WRITES - 3)
        val first = writeBatch(db)
        first.delete(doc(db, "${FirestorePaths.PLAYERS}/$uid"))
        if (aliasKey != null) first.delete(doc(db, "${FirestorePaths.ALIASES}/$aliasKey"))
        first.delete(doc(db, "${FirestorePaths.WALLETS}/$uid"))
        firstEntries.forEach { first.delete(it) }
        first.commit().awaitFirebase()
        entries.drop(firstEntries.size).chunked(MAX_BATCH_WRITES).forEach { chunk ->
            val batch = writeBatch(db)
            chunk.forEach { batch.delete(it) }
            batch.commit().awaitFirebase()
        }
    }
}

internal class WebWalletStore(private val db: Firestore) : WalletStore {

    override fun observe(uid: String): Flow<WalletDocument?> =
        walletReference(uid).observeConfirmed { fromJson<WalletDocument>(it) }

    override suspend fun readLocal(uid: String): WalletDocument? = try {
        getDocFromCache(walletReference(uid)).awaitFirebase().data()?.let { fromJson<WalletDocument>(jsonStringify(it)) }
    } catch (e: FirebaseGatewayException) {
        // "unavailable" (traducido como Network): el documento no está en la caché local.
        if (e.code == FirebaseErrorCode.Network) null else throw e
    }

    override fun write(wallet: WalletDocument, entry: LedgerEntryDocument): PendingWrite {
        val batch = writeBatch(db)
        batch.set(walletReference(wallet.uid), jsonParse(wallet.toJson()))
        batch.set(doc(db, "${FirestorePaths.ledger(wallet.uid)}/${entry.id}"), jsonParse(entry.toJson()))
        val commit = batch.commit()
        return PendingWrite { commit.awaitFirebase() }
    }

    private fun walletReference(uid: String): DocumentReference = doc(db, "${FirestorePaths.WALLETS}/$uid")
}

/**
 * Documento observado en tiempo real, con las escrituras pendientes ya aplicadas, como JSON.
 *
 * "No existe" solo se emite cuando lo confirma el servidor: sin conexión y sin caché, Firestore
 * responde "no existe" desde la caché. Por eso se escuchan también los cambios de metadatos: si la
 * caché ya sabía que no existe, la confirmación del servidor no cambia los datos y, sin ellos, no
 * llegaría ningún aviso.
 */
private fun <T> DocumentReference.observeConfirmed(decode: (json: String) -> T): Flow<T?> = callbackFlow {
    val unsubscribe = onSnapshot(
        reference = this@observeConfirmed,
        options = includeMetadataChanges(),
        onNext = { snapshot ->
            if (snapshot.exists() || !snapshot.metadata.fromCache) {
                runCatching { snapshot.data()?.let { decode(jsonStringify(it)) } }
                    .onSuccess { trySend(it) }
                    .onFailure { close(it) }
            }
        },
        onError = { error -> close(error.toGatewayException()) },
    )
    awaitClose { unsubscribe() }
}

private fun User.toGatewayUser() = GatewayUser(
    uid = uid,
    email = email.orEmpty(),
    emailVerified = emailVerified,
)
