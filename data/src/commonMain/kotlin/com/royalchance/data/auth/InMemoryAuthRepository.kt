package com.royalchance.data.auth

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.core.common.result.success
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.auth.LegalConsents
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
 * Autenticación simulada en memoria, con las mismas reglas que tendrá Firebase:
 * emails y alias únicos sin distinguir mayúsculas, errores que no revelan qué cuentas existen y
 * conversión de invitado a cuenta conservando su identificador.
 *
 * Se usa en la Fase 3, en el escritorio de desarrollo y en tests. Nada persiste al cerrar la app.
 * La latencia simulada permite ver los estados de carga de la UI; en tests se pasa [Duration.ZERO].
 */
class InMemoryAuthRepository(
    private val simulatedLatency: Duration = 600.milliseconds,
) : AuthRepository {

    private data class Account(
        val id: String,
        val email: String?,
        val password: String?,
        val provider: Provider,
        val emailVerified: Boolean,
        val profile: PlayerProfile?,
        val guestConsents: LegalConsents? = null,
    ) {
        fun toUser() = AuthUser(
            id = id,
            email = email,
            isGuest = provider == Provider.Guest,
            isEmailVerified = emailVerified,
            profile = profile,
        )
    }

    private enum class Provider { Email, Google, Guest }

    private val mutex = Mutex()
    private val accounts = mutableMapOf<String, Account>()
    private var nextId = 1
    private var currentId: String? = null

    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    override suspend fun signIn(email: String, password: String) = operation {
        val account = findByEmail(email)?.takeIf { it.provider == Provider.Email && it.password == password }
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
                // Un invitado que se registra conserva su identificador y, por tanto, su progreso.
                val guestId = current()?.takeIf { it.provider == Provider.Guest }?.id
                val created = Account(
                    id = guestId ?: newId(),
                    email = account.email,
                    password = account.password,
                    provider = Provider.Email,
                    emailVerified = false,
                    profile = account.profile,
                )
                accounts[created.id] = created
                signInAs(created)
                success()
            }
        }
    }

    override suspend fun signInWithGoogle() = operation {
        // Identidad de Google simulada; en la Fase 4 la proporciona el SDK de la plataforma.
        val account = findByEmail(SIMULATED_GOOGLE_EMAIL) ?: Account(
            id = newId(),
            email = SIMULATED_GOOGLE_EMAIL,
            password = null,
            provider = Provider.Google,
            emailVerified = true,
            profile = null,
        ).also { accounts[it.id] = it }
        signInAs(account)
        success()
    }

    override suspend fun continueAsGuest(consents: LegalConsents) = operation {
        val guest = Account(
            id = newId(),
            email = null,
            password = null,
            provider = Provider.Guest,
            emailVerified = false,
            profile = null,
            guestConsents = consents,
        )
        accounts[guest.id] = guest
        signInAs(guest)
        success()
    }

    override suspend fun completeProfile(profile: PlayerProfile) = operation {
        val account = current()
        when {
            account == null || account.provider == Provider.Guest -> failure(AuthError.NotSignedIn)
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
        if (account?.email == null) {
            failure(AuthError.NotSignedIn)
        } else {
            // Simulación: se considera que el jugador abre el enlace de inmediato.
            signInAs(account.copy(emailVerified = true).also { accounts[it.id] = it })
            success()
        }
    }

    override suspend fun signOut() {
        mutex.withLock {
            // La sesión de invitado no se puede recuperar al salir: sus datos se descartan.
            current()?.takeIf { it.provider == Provider.Guest }?.let { accounts.remove(it.id) }
            currentId = null
            state.value = AuthState.SignedOut
        }
    }

    override suspend fun deleteAccount() = operation {
        val account = current()
        if (account == null) {
            failure(AuthError.NotSignedIn)
        } else {
            accounts.remove(account.id)
            currentId = null
            state.value = AuthState.SignedOut
            success()
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

    private fun newId(): String = "local-${nextId++}"

    private fun signInAs(account: Account) {
        currentId = account.id
        state.value = AuthState.SignedIn(account.toUser())
    }

    companion object {
        const val SIMULATED_GOOGLE_EMAIL = "jugador.google@example.com"
    }
}
