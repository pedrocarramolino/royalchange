package com.royalchance.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.validation.FieldError
import com.royalchance.domain.auth.validation.RegistrationField
import com.royalchance.domain.auth.validation.RegistrationForm
import com.royalchance.domain.auth.validation.RegistrationMode
import com.royalchance.domain.auth.validation.RegistrationValidation
import com.royalchance.domain.auth.validation.RegistrationValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class RegisterUiState(
    val mode: RegistrationMode,
    val form: RegistrationForm,
    val today: LocalDate,
    val errors: Map<RegistrationField, FieldError> = emptyMap(),
    /** Tras el primer intento de envío, los errores se recalculan a cada cambio. */
    val validateLive: Boolean = false,
    /** Se incrementa en cada envío con errores: la pantalla desplaza la vista al primero. */
    val failedSubmissions: Int = 0,
    val isSubmitting: Boolean = false,
    val authError: AuthError? = null,
    val completed: Boolean = false,
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val validator: RegistrationValidator,
    mode: RegistrationMode,
    clock: Clock,
    timeZone: TimeZone,
    defaultCountry: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RegisterUiState(
            mode = mode,
            form = RegistrationForm(countryCode = defaultCountry),
            today = clock.todayIn(timeZone),
        ),
    )
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun updateForm(transform: (RegistrationForm) -> RegistrationForm) {
        _state.update { current ->
            val form = transform(current.form)
            current.copy(
                form = form,
                errors = if (current.validateLive) currentErrors(form, current.mode) else current.errors,
                authError = null,
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        when (val validation = validator.validate(current.form, current.mode)) {
            is RegistrationValidation.Invalid -> _state.update {
                it.copy(errors = validation.errors, validateLive = true, failedSubmissions = it.failedSubmissions + 1)
            }
            is RegistrationValidation.Valid -> send(validation)
        }
    }

    /** Permite salir de "completar perfil" (p. ej. si se entró con la cuenta de Google equivocada). */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun send(validation: RegistrationValidation.Valid) {
        _state.update { it.copy(isSubmitting = true, errors = emptyMap(), authError = null) }
        viewModelScope.launch {
            val result = validation.account?.let { authRepository.register(it) }
                ?: authRepository.completeProfile(validation.profile)
            _state.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(isSubmitting = false, completed = true)
                    is Outcome.Failure -> when (result.error) {
                        // Errores que pertenecen a un campo concreto: se muestran junto a él.
                        AuthError.EmailAlreadyInUse -> state.withFieldError(RegistrationField.Email, FieldError.EmailInUse)
                        AuthError.AliasTaken -> state.withFieldError(RegistrationField.Alias, FieldError.AliasTaken)
                        else -> state.copy(isSubmitting = false, authError = result.error)
                    }
                }
            }
        }
    }

    private fun RegisterUiState.withFieldError(field: RegistrationField, error: FieldError) = copy(
        isSubmitting = false,
        errors = mapOf(field to error),
        validateLive = true,
        failedSubmissions = failedSubmissions + 1,
    )

    private fun currentErrors(form: RegistrationForm, mode: RegistrationMode): Map<RegistrationField, FieldError> =
        (validator.validate(form, mode) as? RegistrationValidation.Invalid)?.errors.orEmpty()
}
