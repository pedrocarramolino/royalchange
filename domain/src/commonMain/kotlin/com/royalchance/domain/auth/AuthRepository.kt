package com.royalchance.domain.auth

import com.royalchance.core.common.result.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Autenticación y cuenta del jugador.
 *
 * La UI solo conoce este contrato. En la Fase 3 lo implementa un repositorio en memoria; en la
 * Fase 4, Firebase Auth. Cambiar la implementación no afecta a ninguna pantalla.
 */
interface AuthRepository {

    /** Estado de sesión observable. La raíz de la app decide qué mostrar a partir de él. */
    val authState: StateFlow<AuthState>

    suspend fun signIn(email: String, password: String): Outcome<Unit, AuthError>

    /** Crea una cuenta con email. Si hay una sesión de invitado activa, conserva su progreso. */
    suspend fun register(account: NewAccount): Outcome<Unit, AuthError>

    suspend fun signInWithGoogle(): Outcome<Unit, AuthError>

    suspend fun continueAsGuest(consents: LegalConsents): Outcome<Unit, AuthError>

    /** Completa el perfil de una cuenta que aún no lo tiene (p. ej. tras entrar con Google). */
    suspend fun completeProfile(profile: PlayerProfile): Outcome<Unit, AuthError>

    /** Siempre responde con éxito si el email es válido, exista o no la cuenta (evita revelar usuarios). */
    suspend fun sendPasswordReset(email: String): Outcome<Unit, AuthError>

    suspend fun sendEmailVerification(): Outcome<Unit, AuthError>

    suspend fun signOut()

    /** Elimina la cuenta y sus datos de forma permanente. */
    suspend fun deleteAccount(): Outcome<Unit, AuthError>
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

/** Fallos esperados de autenticación. Cada uno tiene un mensaje concreto en la UI. */
enum class AuthError {
    /** Email o contraseña incorrectos. Deliberadamente no distingue cuál de los dos. */
    InvalidCredentials,
    EmailAlreadyInUse,
    AliasTaken,
    WeakPassword,
    TooManyAttempts,
    Network,
    Cancelled,
    NotSignedIn,
    Unknown,
}
