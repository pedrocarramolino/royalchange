package com.royalchance.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthError
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.settings.SettingsRepository
import com.royalchance.domain.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PendingConfirmation { SignOut, DeleteAccount }

enum class SettingsError { Generic, WrongPassword, RequiresRecentLogin }

data class SettingsUiState(
    val user: AuthUser? = null,
    val theme: ThemePreference = ThemePreference.Dark,
    val soundEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val pendingConfirmation: PendingConfirmation? = null,
    val isBusy: Boolean = false,
    val error: SettingsError? = null,
    val verificationSent: Boolean = false,
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<SettingsUiState> = combine(
        authRepository.authState,
        settingsRepository.settings,
        local,
    ) { auth, settings, local ->
        SettingsUiState(
            user = (auth as? AuthState.SignedIn)?.user,
            theme = settings.theme,
            soundEnabled = settings.soundEnabled,
            reducedMotion = settings.reducedMotion,
            pendingConfirmation = local.pending,
            isBusy = local.busy,
            error = local.error,
            verificationSent = local.verificationSent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReducedMotion(enabled) }
    }

    fun sendVerification() = runAction {
        val result = authRepository.sendEmailVerification()
        if (result is Outcome.Success) local.update { it.copy(verificationSent = true) }
        result
    }

    /** Las acciones destructivas siempre piden confirmación antes de ejecutarse. */
    fun request(confirmation: PendingConfirmation) {
        local.update { it.copy(pending = confirmation, error = null) }
    }

    fun dismissConfirmation() {
        local.update { it.copy(pending = null, error = null) }
    }

    fun confirmSignOut() {
        if (local.value.pending != PendingConfirmation.SignOut) return
        local.update { it.copy(pending = null) }
        runAction {
            authRepository.signOut()
            Outcome.Success(Unit)
        }
    }

    /** Al corregir la contraseña desaparece el aviso de contraseña incorrecta. */
    fun onDeletionPasswordChange() {
        local.update { if (it.error == SettingsError.WrongPassword) it.copy(error = null) else it }
    }

    /** Firebase exige una autenticación reciente para borrar la cuenta: se confirma con la contraseña. */
    fun confirmDeletion(password: String) {
        if (local.value.pending != PendingConfirmation.DeleteAccount) return
        runAction { authRepository.deleteAccount(password) }
    }

    private fun runAction(action: suspend () -> Outcome<Unit, AuthError>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = action()
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(busy = false, pending = null)
                    // Contraseña incorrecta: el diálogo sigue abierto para corregirla.
                    is Outcome.Failure -> when (result.error) {
                        AuthError.InvalidCredentials -> state.copy(busy = false, error = SettingsError.WrongPassword)
                        AuthError.RequiresRecentLogin -> state.copy(busy = false, pending = null, error = SettingsError.RequiresRecentLogin)
                        else -> state.copy(busy = false, pending = null, error = SettingsError.Generic)
                    }
                }
            }
        }
    }

    private data class LocalState(
        val pending: PendingConfirmation? = null,
        val busy: Boolean = false,
        val error: SettingsError? = null,
        val verificationSent: Boolean = false,
    )
}
