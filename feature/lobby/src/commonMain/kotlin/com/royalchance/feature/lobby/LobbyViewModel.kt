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

data class LobbyUiState(
    val user: AuthUser? = null,
    val isSendingVerification: Boolean = false,
    val verificationJustConfirmed: Boolean = false,
    val games: List<GameType> = GameType.entries,
) {
    val showGuestBanner: Boolean get() = user?.isGuest == true
    val showVerificationBanner: Boolean get() = user != null && !user.isGuest && user.email != null && !user.isEmailVerified
}

class LobbyViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val verification = MutableStateFlow(VerificationState())

    val state: StateFlow<LobbyUiState> = combine(authRepository.authState, verification) { auth, verification ->
        LobbyUiState(
            user = (auth as? AuthState.SignedIn)?.user,
            isSendingVerification = verification.sending,
            verificationJustConfirmed = verification.confirmed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LobbyUiState())

    fun sendVerification() {
        if (verification.value.sending) return
        verification.update { it.copy(sending = true) }
        viewModelScope.launch {
            val result = authRepository.sendEmailVerification()
            verification.update { VerificationState(sending = false, confirmed = result is Outcome.Success) }
        }
    }

    private data class VerificationState(val sending: Boolean = false, val confirmed: Boolean = false)
}
