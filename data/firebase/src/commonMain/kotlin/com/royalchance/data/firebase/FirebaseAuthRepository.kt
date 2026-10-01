package com.royalchance.data.firebase

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.core.common.result.success
import com.royalchance.data.firebase.gateway.AuthGateway
import com.royalchance.data.firebase.gateway.FirebaseErrorCode
import com.royalchance.data.firebase.gateway.FirebaseGatewayException
import com.royalchance.data.firebase.gateway.GatewayUser
import com.royalchance.data.firebase.gateway.PlayerStore
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Autenticación con email y contraseña (Firebase Auth) y perfil en Firestore, independiente de la
 * plataforma.
 *
 * Datos en Firestore:
 * - `players/{uid}`: perfil del jugador.
 * - `aliases/{alias en minúsculas}`: reserva del alias; garantiza que sea único.
 */
internal class FirebaseAuthRepository(
    private val auth: AuthGateway,
    private val players: PlayerStore,
    private val clock: Clock,
    scope: CoroutineScope,
) : AuthRepository {

    /**
     * Al crear o borrar una cuenta, Firebase pasa por un estado intermedio: usuario sin perfil.
     * Durante la operación se congela el estado publicado, para que la app no muestre "completar
     * perfil" ni cierre la pantalla que la está ejecutando.
     */
    private val accountChangeInProgress = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observedState = auth.users.flatMapLatest { user ->
        if (user == null) {
            flowOf(AuthState.SignedOut)
        } else {
            players.observe(user.uid)
                .map<PlayerDocument?, AuthState> { document -> AuthState.SignedIn(user.toAuthUser(document)) }
                // Sin acceso al perfil (p. ej. sesión revocada) se muestra la cuenta sin él: nunca se bloquea el arranque.
                .catch { emit(AuthState.SignedIn(user.toAuthUser(null))) }
        }
    }

    override val authState: StateFlow<AuthState> =
        combine(observedState, accountChangeInProgress) { state, changing -> state to changing }
            .scan<Pair<AuthState, Boolean>, AuthState>(AuthState.Loading) { previous, (state, changing) ->
                if (changing) previous else state
            }
            .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    override suspend fun signIn(email: String, password: String) = attempt {
        auth.signInWithEmail(email.trim(), password)
    }

    override suspend fun register(account: NewAccount) = changingAccount {
        if (players.aliasExists(PlayerDocument.aliasKey(account.profile.alias))) {
            return@changingAccount failure(AuthError.AliasTaken)
        }
        auth.createAccount(account.email, account.password)
        val saved = saveProfile(account.profile)
        if (saved is Outcome.Success) {
            // El email de verificación es un extra: si falla, la cuenta ya existe y el jugador puede reenviarlo.
            runCatching { auth.sendEmailVerification() }
        }
        saved
    }

    override suspend fun completeProfile(profile: PlayerProfile) = changingAccount {
        if (players.aliasExists(PlayerDocument.aliasKey(profile.alias))) {
            return@changingAccount failure(AuthError.AliasTaken)
        }
        saveProfile(profile)
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit, AuthError> =
        when (val result = attempt { auth.sendPasswordReset(email.trim()) }) {
            // Misma respuesta exista o no la cuenta: no se revela qué emails están registrados.
            is Outcome.Failure -> if (result.error == AuthError.InvalidCredentials) success() else result
            is Outcome.Success -> result
        }

    override suspend fun sendEmailVerification() = attempt { auth.sendEmailVerification() }

    override suspend fun refreshUser() = attempt { auth.reload() }

    override suspend fun signOut() {
        runCatching { auth.signOut() }
    }

    override suspend fun deleteAccount(password: String) = changingAccount {
        val user = auth.currentUser() ?: throw FirebaseGatewayException(FirebaseErrorCode.UserNotFound)
        auth.reauthenticate(password)
        val aliasKey = (authState.value as? AuthState.SignedIn)?.user?.profile?.alias?.let(PlayerDocument::aliasKey)
        // Una vez empezado, el borrado termina aunque se cierre la pantalla que lo pidió: nunca
        // queda una cuenta a medio borrar por una cancelación.
        withContext(NonCancellable) {
            players.deleteAccountData(user.uid, aliasKey)
            auth.deleteUser()
        }
        success()
    }

    private suspend fun saveProfile(profile: PlayerProfile): Outcome<Unit, AuthError> {
        val uid = auth.currentUser()?.uid ?: return failure(AuthError.NotSignedIn)
        return try {
            players.saveProfile(PlayerDocument.from(uid, profile, clock.now()))
            success()
        } catch (e: FirebaseGatewayException) {
            // Otro jugador reservó el alias entre la comprobación y la escritura: las reglas lo rechazan.
            if (e.code == FirebaseErrorCode.PermissionDenied) failure(AuthError.AliasTaken) else failure(e.code.toAuthError())
        }
    }

    private suspend fun changingAccount(block: suspend () -> Outcome<Unit, AuthError>): Outcome<Unit, AuthError> {
        accountChangeInProgress.value = true
        return try {
            attemptOutcome(block)
        } finally {
            accountChangeInProgress.value = false
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): Outcome<Unit, AuthError> = attemptOutcome {
        block()
        success()
    }

    private suspend fun attemptOutcome(block: suspend () -> Outcome<Unit, AuthError>): Outcome<Unit, AuthError> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseGatewayException) {
            failure(e.code.toAuthError())
        } catch (e: Exception) {
            failure(AuthError.Unknown)
        }
}

internal fun FirebaseErrorCode.toAuthError(): AuthError = when (this) {
    // "Usuario no encontrado" se presenta igual que credenciales incorrectas: no se revela qué emails existen.
    FirebaseErrorCode.InvalidCredential, FirebaseErrorCode.UserNotFound -> AuthError.InvalidCredentials
    FirebaseErrorCode.EmailAlreadyInUse -> AuthError.EmailAlreadyInUse
    FirebaseErrorCode.WeakPassword -> AuthError.WeakPassword
    FirebaseErrorCode.TooManyRequests -> AuthError.TooManyAttempts
    FirebaseErrorCode.Network -> AuthError.Network
    FirebaseErrorCode.RequiresRecentLogin -> AuthError.RequiresRecentLogin
    FirebaseErrorCode.OperationNotAllowed -> AuthError.Unavailable
    FirebaseErrorCode.PermissionDenied, FirebaseErrorCode.Unknown -> AuthError.Unknown
}

internal fun GatewayUser.toAuthUser(document: PlayerDocument?) = AuthUser(
    id = uid,
    email = email,
    isEmailVerified = emailVerified,
    profile = document?.toProfile(),
)
