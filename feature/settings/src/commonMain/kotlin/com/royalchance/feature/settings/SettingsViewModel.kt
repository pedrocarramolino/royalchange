package com.royalchance.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
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

data class SettingsUiState(
    val user: AuthUser? = null,
    val theme: ThemePreference = ThemePreference.Dark,
    val pendingConfirmation: PendingConfirmation? = null,
    val isBusy: Boolean = false,
    val showError: Boolean = false,
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
            pendingConfirmation = local.pending,
            isBusy = local.busy,
            showError = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun sendVerification() = runAction { authRepository.sendEmailVerification() is Outcome.Success }

    /** Las acciones destructivas siempre piden confirmación antes de ejecutarse. */
    fun request(confirmation: PendingConfirmation) {
        local.update { it.copy(pending = confirmation, error = false) }
    }

    fun dismissConfirmation() {
        local.update { it.copy(pending = null) }
    }

    fun confirm() {
        val pending = local.value.pending ?: return
        local.update { it.copy(pending = null) }
        when (pending) {
            PendingConfirmation.SignOut -> runAction {
                authRepository.signOut()
                true
            }
            PendingConfirmation.DeleteAccount -> runAction { authRepository.deleteAccount() is Outcome.Success }
        }
    }

    private fun runAction(action: suspend () -> Boolean) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = false) }
        viewModelScope.launch {
            val succeeded = action()
            local.update { it.copy(busy = false, error = !succeeded) }
        }
    }

    private data class LocalState(
        val pending: PendingConfirmation? = null,
        val busy: Boolean = false,
        val error: Boolean = false,
    )
}
