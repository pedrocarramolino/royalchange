package com.royalchance.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.AuthUser
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.RescueStatus
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.economy.rescueStatus
import com.royalchance.domain.game.GameType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Estado del aviso de verificación de email del lobby. */
enum class VerificationBanner {
    /** El email no está verificado y aún no se ha enviado el enlace en esta sesión. */
    NotSent,

    /** Enlace enviado: el jugador debe abrirlo y volver para comprobarlo. */
    Pending,

    /** Se acaba de confirmar: se agradece una vez. */
    JustVerified,
}

/** Saldo mostrado en el lobby. */
sealed interface BalanceUi {
    data object Loading : BalanceUi

    data class Ready(val chips: Chips) : BalanceUi

    data object Unavailable : BalanceUi
}

data class LobbyUiState(
    val user: AuthUser? = null,
    val balance: BalanceUi = BalanceUi.Loading,
    /** Monedero recién creado: se explica que las fichas de bienvenida son virtuales. */
    val showWelcomeGrant: Boolean = false,
    val rescue: RescueStatus = RescueStatus.NotNeeded,
    /** Hora de referencia de la cuenta atrás de la recarga. */
    val now: Instant = Instant.DISTANT_PAST,
    val isClaimingRescue: Boolean = false,
    val rescueFailed: Boolean = false,
    val verificationBanner: VerificationBanner? = null,
    val isVerificationBusy: Boolean = false,
    val games: List<GameType> = GameType.entries,
)

class LobbyViewModel(
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    /** La cuenta atrás de la recarga se actualiza sola. */
    private val ticks = flow {
        while (true) {
            emit(clock.now())
            delay(30.seconds)
        }
    }

    val state: StateFlow<LobbyUiState> = combine(
        authRepository.authState,
        economyRepository.wallet,
        local,
        ticks,
    ) { auth, wallet, local, now ->
        val user = (auth as? AuthState.SignedIn)?.user
        val needsVerification = user != null && !user.isEmailVerified
        val ready = (wallet as? WalletState.Ready)?.wallet
        LobbyUiState(
            user = user,
            balance = when (wallet) {
                WalletState.Loading -> BalanceUi.Loading
                is WalletState.Ready -> BalanceUi.Ready(wallet.wallet.balance)
                WalletState.Unavailable -> BalanceUi.Unavailable
            },
            showWelcomeGrant = ready?.sequence == 1L && !local.welcomeDismissed,
            rescue = ready?.rescueStatus(now) ?: RescueStatus.NotNeeded,
            now = now,
            isClaimingRescue = local.claimingRescue,
            rescueFailed = local.rescueFailed,
            verificationBanner = when {
                needsVerification && local.verificationSent -> VerificationBanner.Pending
                needsVerification -> VerificationBanner.NotSent
                local.verificationSent -> VerificationBanner.JustVerified
                else -> null
            },
            isVerificationBusy = local.verificationBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LobbyUiState())

    init {
        // El jugador puede haber confirmado su email fuera de la app desde la última vez.
        viewModelScope.launch { authRepository.refreshUser() }
    }

    fun dismissWelcomeGrant() {
        local.update { it.copy(welcomeDismissed = true) }
    }

    fun claimRescue() {
        if (local.value.claimingRescue) return
        local.update { it.copy(claimingRescue = true, rescueFailed = false) }
        viewModelScope.launch {
            val result = economyRepository.claimRescue()
            local.update { it.copy(claimingRescue = false, rescueFailed = result.isUnexpectedFailure()) }
        }
    }

    fun sendVerification() = runVerification {
        if (authRepository.sendEmailVerification() is Outcome.Success) {
            local.update { it.copy(verificationSent = true) }
        }
    }

    fun checkVerification() = runVerification { authRepository.refreshUser() }

    private fun runVerification(action: suspend () -> Unit) {
        if (local.value.verificationBusy) return
        local.update { it.copy(verificationBusy = true) }
        viewModelScope.launch {
            action()
            local.update { it.copy(verificationBusy = false) }
        }
    }

    private data class LocalState(
        val verificationSent: Boolean = false,
        val verificationBusy: Boolean = false,
        val welcomeDismissed: Boolean = false,
        val claimingRescue: Boolean = false,
        val rescueFailed: Boolean = false,
    )
}

/**
 * Si la recarga ya no procede (recogida desde otro dispositivo, por ejemplo), el monedero lo
 * refleja por sí solo: no se muestra como error.
 */
private fun Outcome<*, EconomyError>.isUnexpectedFailure(): Boolean =
    this is Outcome.Failure && error != EconomyError.RescueNotNeeded && error !is EconomyError.RescueCoolingDown
