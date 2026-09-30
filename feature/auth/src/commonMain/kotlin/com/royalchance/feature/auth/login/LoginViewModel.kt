package com.royalchance.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.validation.FieldError
import com.royalchance.domain.auth.validation.LoginValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val authError: AuthError? = null,
    val isSubmitting: Boolean = false,
    val isGoogleLoading: Boolean = false,
) {
    val isBusy: Boolean get() = isSubmitting || isGoogleLoading
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailError = null, authError = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, authError = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isBusy) return
        val errors = LoginValidator.validate(current.email, current.password)
        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    emailError = errors[LoginValidator.Field.Email],
                    passwordError = errors[LoginValidator.Field.Password],
                )
            }
            return
        }
        _state.update { it.copy(isSubmitting = true, authError = null) }
        viewModelScope.launch {
            // Si tiene éxito, la raíz de la app cambia sola al casino: aquí solo se gestiona el fallo.
            val result = authRepository.signIn(current.email, current.password)
            _state.update { it.copy(isSubmitting = false, authError = (result as? Outcome.Failure)?.error) }
        }
    }

    fun signInWithGoogle() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isGoogleLoading = true, authError = null) }
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle()
            _state.update { it.copy(isGoogleLoading = false, authError = (result as? Outcome.Failure)?.error) }
        }
    }
}
