package com.royalchance.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.game.GameType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del aviso de verificación de email del lobby. */
enum class VerificationBanner {
    /** El email no está verificado y aún no se ha enviado el enlace en esta sesión. */
    NotSent,

    /** Enlace enviado: el jugador debe abrirlo y volver para comprobarlo. */
    Pending,

    /** Se acaba de confirmar: se agradece una vez. */
    JustVerified,
}

data class LobbyUiState(
    val user: AuthUser? = null,
    val verificationBanner: VerificationBanner? = null,
    val isVerificationBusy: Boolean = false,
    val games: List<GameType> = GameType.entries,
)

class LobbyViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val verification = MutableStateFlow(VerificationState())

    val state: StateFlow<LobbyUiState> = combine(authRepository.authState, verification) { auth, verification ->
        val user = (auth as? AuthState.SignedIn)?.user
        val needsVerification = user != null && !user.isEmailVerified
        LobbyUiState(
            user = user,
            verificationBanner = when {
                needsVerification && verification.sent -> VerificationBanner.Pending
                needsVerification -> VerificationBanner.NotSent
                verification.sent -> VerificationBanner.JustVerified
                else -> null
            },
            isVerificationBusy = verification.busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LobbyUiState())

    init {
        // El jugador puede haber confirmado su email fuera de la app desde la última vez.
        viewModelScope.launch { authRepository.refreshUser() }
    }

    fun sendVerification() = runVerification {
        if (authRepository.sendEmailVerification() is Outcome.Success) {
            verification.update { it.copy(sent = true) }
        }
    }

    fun checkVerification() = runVerification { authRepository.refreshUser() }

    private fun runVerification(action: suspend () -> Unit) {
        if (verification.value.busy) return
        verification.update { it.copy(busy = true) }
        viewModelScope.launch {
            action()
            verification.update { it.copy(busy = false) }
        }
    }

    private data class VerificationState(val sent: Boolean = false, val busy: Boolean = false)
}
