package com.royalchance.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.validation.EmailRules
import com.royalchance.domain.auth.validation.FieldError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String,
    val emailError: FieldError? = null,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
    val authError: AuthError? = null,
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
    initialEmail: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState(email = initialEmail))
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailError = null, authError = null, sent = false) }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        EmailRules.validate(current.email)?.let { error ->
            _state.update { it.copy(emailError = error) }
            return
        }
        _state.update { it.copy(isSubmitting = true, authError = null) }
        viewModelScope.launch {
            when (val result = authRepository.sendPasswordReset(EmailRules.normalize(current.email))) {
                is Outcome.Success -> _state.update { it.copy(isSubmitting = false, sent = true) }
                is Outcome.Failure -> _state.update { it.copy(isSubmitting = false, authError = result.error) }
            }
        }
    }
}
