package com.royalchance.feature.auth.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.LegalDocumentVersions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class WelcomeUiState(
    val isGoogleLoading: Boolean = false,
    val isGuestLoading: Boolean = false,
    val showGuestConsent: Boolean = false,
    val error: AuthError? = null,
) {
    val isBusy: Boolean get() = isGoogleLoading || isGuestLoading
}

class WelcomeViewModel(
    private val authRepository: AuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    fun signInWithGoogle() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isGoogleLoading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle()
            _state.update { it.copy(isGoogleLoading = false, error = (result as? Outcome.Failure)?.error) }
        }
    }

    /** El modo invitado exige confirmar la mayoría de edad y las condiciones antes de jugar. */
    fun requestGuestAccess() {
        if (!_state.value.isBusy) _state.update { it.copy(showGuestConsent = true, error = null) }
    }

    fun dismissGuestConsent() {
        _state.update { it.copy(showGuestConsent = false) }
    }

    fun confirmGuestAccess() {
        if (_state.value.isBusy) return
        _state.update { it.copy(showGuestConsent = false, isGuestLoading = true) }
        viewModelScope.launch {
            val consents = LegalConsents(
                termsVersion = LegalDocumentVersions.TERMS,
                privacyVersion = LegalDocumentVersions.PRIVACY,
                acceptedAt = clock.now(),
            )
            val result = authRepository.continueAsGuest(consents)
            _state.update { it.copy(isGuestLoading = false, error = (result as? Outcome.Failure)?.error) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
