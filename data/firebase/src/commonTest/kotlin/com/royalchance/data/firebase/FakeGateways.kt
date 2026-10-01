package com.royalchance.data.firebase

import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.data.firebase.gateway.FirebaseGatewayException
import com.royalchance.data.firebase.gateway.GatewayUser
import com.royalchance.data.firebase.gateway.PlayerStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Doble de Firebase Auth con su comportamiento esencial: emails únicos, uids estables, reautenticación… */
internal class FakeAuthGateway : AuthGateway {

    private data class Account(val uid: String, val email: String, val password: String, val verified: Boolean)

    private val accounts = mutableMapOf<String, Account>()
    private var nextUid = 1
    private val current = MutableStateFlow<Account?>(null)

    var verificationEmailsSent = 0
        private set
    var deletedUids = mutableListOf<String>()
        private set

    /** Simula que el jugador pulsa el enlace del email (fuera de la app). */
    fun confirmEmailOutsideApp() {
        current.value?.let { accounts[it.uid] = it.copy(verified = true) }
    }

    override val users: Flow<GatewayUser?> = current.map { it?.toUser() }

    override fun currentUser(): GatewayUser? = current.value?.toUser()

    override suspend fun signInWithEmail(email: String, password: String) {
        val account = accounts.values.firstOrNull { it.email == email }
            ?: throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
        if (account.password != password) throw FirebaseGatewayException(FirebaseErrorCode.InvalidCredential)
        current.value = account
    }

    override suspend fun createAccount(email: String, password: String) {
        if (accounts.values.any { it.email == email }) throw FirebaseGatewayException(FirebaseErrorCode.EmailAlreadyInUse)
        val account = Account("uid-${nextUid++}", email, password, verified = false)
        accounts[account.uid] = account
        current.value = account
    }

    override suspend fun reauthenticate(password: String) {
        val account = current.value ?: throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
        if (account.password != password) throw FirebaseGatewayException(FirebaseErrorCode.InvalidCredential)
    }

    override suspend fun sendPasswordReset(email: String) {
        if (accounts.values.none { it.email == email }) throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
    }

    override suspend fun sendEmailVerification() {
        verificationEmailsSent++
    }

    override suspend fun reload() {
        current.value = current.value?.let { accounts[it.uid] }
    }

    override suspend fun deleteUser() {
        val account = current.value ?: throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
        accounts.remove(account.uid)
        deletedUids += account.uid
        current.value = null
    }

    override suspend fun signOut() {
        current.value = null
    }

    private fun Account.toUser() = GatewayUser(uid, email, emailVerified = verified)
}

/** Doble de Firestore que aplica las mismas reglas de unicidad de alias que las reglas de seguridad. */
internal class FakePlayerStore : PlayerStore {

    val players = MutableStateFlow<Map<String, PlayerDocument>>(emptyMap())
    val aliases = mutableMapOf<String, String>()

    /** Simula que otro jugador reserva un alias entre la comprobación y la escritura. */
    var reserveBeforeNextSave: String? = null

    override fun observe(uid: String): Flow<PlayerDocument?> = players.map { it[uid] }

    override suspend fun aliasExists(aliasKey: String): Boolean = aliasKey in aliases

    override suspend fun saveProfile(document: PlayerDocument) {
        reserveBeforeNextSave?.let { aliases[it] = "otro-jugador" }
        reserveBeforeNextSave = null
        val owner = aliases[document.aliasKey]
        if (owner != null && owner != document.uid) throw FirebaseGatewayException(FirebaseErrorCode.PermissionDenied)
        aliases[document.aliasKey] = document.uid
        players.value = players.value + (document.uid to document)
    }

    override suspend fun delete(uid: String, aliasKey: String?) {
        players.value = players.value - uid
        aliasKey?.let(aliases::remove)
    }
}
