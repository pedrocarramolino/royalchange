package com.royalchance.data.firebase.gateway

import com.royalchance.data.firebase.LedgerEntryDocument
import com.royalchance.data.firebase.PlayerDocument
import com.royalchance.data.firebase.WalletDocument
import kotlinx.coroutines.flow.Flow

/**
 * Pasarelas hacia los SDK de Firebase. Cada plataforma las implementa con su SDK oficial y la
 * lógica de negocio ([com.royalchance.data.firebase.FirebaseAuthRepository]) se escribe una sola vez
 * sobre ellas. En tests se sustituyen por dobles en memoria.
 */
internal interface AuthGateway {

    /** Usuario actual; emite en cada cambio de sesión y tras [reload]. */
    val users: Flow<GatewayUser?>

    fun currentUser(): GatewayUser?

    suspend fun signInWithEmail(email: String, password: String)

    /** Crea una cuenta con email y contraseña e inicia sesión con ella. */
    suspend fun createAccount(email: String, password: String)

    /** Autenticación reciente con la contraseña, exigida para operaciones sensibles. */
    suspend fun reauthenticate(password: String)

    suspend fun sendPasswordReset(email: String)

    suspend fun sendEmailVerification()

    suspend fun reload()

    suspend fun deleteUser()

    suspend fun signOut()
}

internal interface PlayerStore {

    /** Documento del jugador; `null` si no existe. Nunca emite "no existe" solo por venir de caché. */
    fun observe(uid: String): Flow<PlayerDocument?>

    /** Consulta al servidor (no a la caché) si un alias ya está reservado. */
    suspend fun aliasExists(aliasKey: String): Boolean

    /** Reserva el alias y guarda el perfil en una única escritura atómica. */
    suspend fun saveProfile(document: PlayerDocument)

    /**
     * Borra todos los datos del jugador: perfil, alias, monedero y asientos. Las reglas solo
     * permiten borrar el monedero junto con el perfil, y los asientos cuando ya no hay monedero.
     */
    suspend fun deleteAccountData(uid: String, aliasKey: String?)
}

internal interface WalletStore {

    /**
     * Monedero con las escrituras pendientes ya aplicadas; `null` solo cuando el servidor confirma
     * que no existe (una caché vacía no cuenta como "no existe").
     */
    fun observe(uid: String): Flow<WalletDocument?>

    /** Versión local más reciente (caché más escrituras pendientes); `null` si no está en caché. */
    suspend fun readLocal(uid: String): WalletDocument?

    /**
     * Guarda el monedero y su asiento en una escritura atómica. Firestore la aplica en local al
     * instante, también sin conexión, y una lectura local posterior ya la ve; el envío al servidor
     * sigue en segundo plano y se espera con [PendingWrite.awaitServer].
     */
    fun write(wallet: WalletDocument, entry: LedgerEntryDocument): PendingWrite

    /** Asientos con número menor que [beforeSeq] (todos si es `null`), de mayor a menor. */
    suspend fun ledgerBefore(uid: String, beforeSeq: Long?, limit: Int): List<LedgerEntryDocument>

    /** Asientos con número mayor que [afterSeq], de menor a mayor. */
    suspend fun ledgerAfter(uid: String, afterSeq: Long, limit: Int): List<LedgerEntryDocument>
}

/** Escritura ya aplicada en local, pendiente de confirmar por el servidor. */
internal fun interface PendingWrite {
    /** Lanza [FirebaseGatewayException] si el servidor la rechaza (Firestore deshace entonces el cambio local). */
    suspend fun awaitServer()
}

internal data class GatewayUser(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
)

/** Fallo de Firebase ya traducido a un código independiente de la plataforma. */
internal class FirebaseGatewayException(
    val code: FirebaseErrorCode,
    cause: Throwable? = null,
) : Exception("Firebase: $code", cause)

internal enum class FirebaseErrorCode {
    InvalidCredential,
    UserNotFound,
    EmailAlreadyInUse,
    WeakPassword,
    TooManyRequests,
    Network,
    RequiresRecentLogin,
    PermissionDenied,
    OperationNotAllowed,
    Unknown,
    ;

    companion object {
        /**
         * Traduce los códigos del SDK web ("auth/wrong-password", "permission-denied"…) y de
         * Android ("ERROR_WRONG_PASSWORD"…). Ambos describen los mismos casos con otra ortografía.
         */
        fun fromPlatformCode(raw: String?): FirebaseErrorCode {
            val code = raw.orEmpty().substringAfter('/').removePrefix("ERROR_").lowercase().replace('_', '-')
            return when (code) {
                "invalid-credential", "wrong-password", "invalid-login-credentials", "invalid-email",
                "user-mismatch", "invalid-password" -> InvalidCredential
                "user-not-found", "user-disabled" -> UserNotFound
                "email-already-in-use" -> EmailAlreadyInUse
                "weak-password" -> WeakPassword
                "too-many-requests" -> TooManyRequests
                "network-request-failed", "unavailable", "deadline-exceeded" -> Network
                "requires-recent-login" -> RequiresRecentLogin
                "permission-denied" -> PermissionDenied
                // Email/contraseña desactivado o Authentication sin iniciar en el proyecto.
                "operation-not-allowed", "configuration-not-found" -> OperationNotAllowed
                else -> Unknown
            }
        }
    }
}
