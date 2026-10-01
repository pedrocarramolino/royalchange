package com.royalchance.domain.auth

import com.royalchance.core.common.result.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Autenticación y cuenta del jugador.
 *
 * La UI solo conoce este contrato. Lo implementan Firebase (Android y web) y un repositorio en
 * memoria (escritorio de desarrollo y tests). Cambiar la implementación no afecta a ninguna pantalla.
 */
interface AuthRepository {

    /** Estado de sesión observable. La raíz de la app decide qué mostrar a partir de él. */
    val authState: StateFlow<AuthState>

    suspend fun signIn(email: String, password: String): Outcome<Unit, AuthError>

    /** Crea una cuenta con email y contraseña y guarda su perfil. */
    suspend fun register(account: NewAccount): Outcome<Unit, AuthError>

    /** Completa el perfil de una cuenta que no llegó a guardarlo durante el registro. */
    suspend fun completeProfile(profile: PlayerProfile): Outcome<Unit, AuthError>

    /** Siempre responde con éxito si el email es válido, exista o no la cuenta (evita revelar usuarios). */
    suspend fun sendPasswordReset(email: String): Outcome<Unit, AuthError>

    suspend fun sendEmailVerification(): Outcome<Unit, AuthError>

    /**
     * Vuelve a leer el usuario del servidor. Necesario para detectar que el jugador ha confirmado
     * su email desde el enlace recibido (ocurre fuera de la app).
     */
    suspend fun refreshUser(): Outcome<Unit, AuthError>

    suspend fun signOut()

    /**
     * Elimina la cuenta y sus datos de forma permanente. Por seguridad exige confirmar la
     * [password] (Firebase pide una autenticación reciente para esta operación).
     */
    suspend fun deleteAccount(password: String): Outcome<Unit, AuthError>
}

data class NewAccount(
    val email: String,
    val password: String,
    val profile: PlayerProfile,
)

sealed interface AuthState {
    /** Restaurando la sesión guardada al arrancar. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val user: AuthUser) : AuthState
}

/** Id del jugador con sesión y perfil completo (el único que puede jugar); `null` en otro caso. */
val AuthState.playerId: String?
    get() = (this as? AuthState.SignedIn)?.user?.takeUnless { it.needsProfileCompletion }?.id

/** Fallos esperados de autenticación. Cada uno tiene un mensaje concreto en la UI. */
enum class AuthError {
    /** Email o contraseña incorrectos. Deliberadamente no distingue cuál de los dos. */
    InvalidCredentials,
    EmailAlreadyInUse,
    AliasTaken,
    WeakPassword,
    TooManyAttempts,
    Network,
    NotSignedIn,

    /** La operación exige haber iniciado sesión hace poco: hay que volver a identificarse. */
    RequiresRecentLogin,

    /** El servicio no está disponible en esta configuración (p. ej. proveedor desactivado en Firebase). */
    Unavailable,
    Unknown,
}
