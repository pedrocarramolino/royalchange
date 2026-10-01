package com.royalchance.data.auth

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.core.common.result.success
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Autenticación simulada en memoria, con las mismas reglas que Firebase: emails y alias únicos sin
 * distinguir mayúsculas y errores que no revelan qué cuentas existen.
 *
 * Se usa en el escritorio de desarrollo y en tests. Nada persiste al cerrar la app.
 * La latencia simulada permite ver los estados de carga de la UI; en tests se pasa [Duration.ZERO].
 */
class InMemoryAuthRepository(
    private val simulatedLatency: Duration = 600.milliseconds,
) : AuthRepository {

    private data class Account(
        val id: String,
        val email: String,
        val password: String,
        val emailVerified: Boolean,
        val profile: PlayerProfile?,
        /** Se ha enviado el email de verificación; al refrescar se considera confirmado. */
        val verificationSent: Boolean = false,
    ) {
        fun toUser() = AuthUser(id = id, email = email, isEmailVerified = emailVerified, profile = profile)
    }

    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, Account>()
    private var nextId = 1
    private var currentId: String? = null

    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    override suspend fun signIn(email: String, password: String) = operation {
        val account = findByEmail(email)?.takeIf { it.password == password }
        if (account == null) {
            failure(AuthError.InvalidCredentials)
        } else {
            signInAs(account)
            success()
        }
    }

    override suspend fun register(account: NewAccount) = operation {
        when {
            findByEmail(account.email) != null -> failure(AuthError.EmailAlreadyInUse)
            isAliasTaken(account.profile.alias, exceptId = null) -> failure(AuthError.AliasTaken)
            else -> {
                val created = Account(
                    id = "local-${nextId++}",
                    email = account.email,
                    password = account.password,
                    emailVerified = false,
                    profile = account.profile,
                    // Como en Firebase, el registro envía el email de verificación.
                    verificationSent = true,
                )
                accounts[created.id] = created
                signInAs(created)
                success()
            }
        }
    }

    override suspend fun completeProfile(profile: PlayerProfile) = operation {
        val account = current()
        when {
            account == null -> failure(AuthError.NotSignedIn)
            isAliasTaken(profile.alias, exceptId = account.id) -> failure(AuthError.AliasTaken)
            else -> {
                signInAs(account.copy(profile = profile).also { accounts[it.id] = it })
                success()
            }
        }
    }

    override suspend fun sendPasswordReset(email: String) = operation {
        // Misma respuesta exista o no la cuenta: no se revela qué emails están registrados.
        success()
    }

    override suspend fun sendEmailVerification() = operation {
        val account = current()
        if (account == null) {
            failure(AuthError.NotSignedIn)
        } else {
            accounts[account.id] = account.copy(verificationSent = true)
            success()
        }
    }

    override suspend fun refreshUser() = operation {
        val account = current()
        if (account == null) {
            failure(AuthError.NotSignedIn)
        } else {
            // Simulación: si se envió el email, se considera que el jugador ya pulsó el enlace.
            val refreshed = if (account.verificationSent) account.copy(emailVerified = true) else account
            signInAs(refreshed.also { accounts[it.id] = it })
            success()
        }
    }

    override suspend fun signOut() {
        mutex.withLock {
            currentId = null
            state.value = AuthState.SignedOut
        }
    }

    override suspend fun deleteAccount(password: String) = operation {
        val account = current()
        when {
            account == null -> failure(AuthError.NotSignedIn)
            // Igual que en Firebase: el borrado se confirma con la contraseña.
            account.password != password -> failure(AuthError.InvalidCredentials)
            else -> {
                accounts.remove(account.id)
                currentId = null
                state.value = AuthState.SignedOut
                success()
            }
        }
    }

    private suspend fun operation(block: () -> Outcome<Unit, AuthError>): Outcome<Unit, AuthError> {
        delay(simulatedLatency)
        return mutex.withLock { block() }
    }

    private fun current(): Account? = currentId?.let(accounts::get)

    private fun findByEmail(email: String): Account? =
        accounts.values.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

    private fun isAliasTaken(alias: String, exceptId: String?): Boolean =
        accounts.values.any { it.id != exceptId && it.profile?.alias.equals(alias.trim(), ignoreCase = true) }

    private fun signInAs(account: Account) {
        currentId = account.id
        state.value = AuthState.SignedIn(account.toUser())
    }
}
