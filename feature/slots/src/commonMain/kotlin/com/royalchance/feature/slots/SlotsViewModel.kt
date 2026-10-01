package com.royalchance.feature.slots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.playerId
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameSessionStore
import com.royalchance.domain.game.GameType
import com.royalchance.engine.slots.SlotEngine
import com.royalchance.engine.slots.SlotRules
import com.royalchance.engine.slots.SlotSpin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

sealed interface SlotsNotice {
    data object InsufficientFunds : SlotsNotice

    data object WalletUnavailable : SlotsNotice
}

/** Un giro para la pantalla: [id] distingue dos giros seguidos con las mismas paradas. */
data class SlotSpinView(val id: Long, val spin: SlotSpin)

data class SlotsUiState(
    val loading: Boolean = true,
    val rules: SlotRules = SlotRules(),
    /** Posición en [SlotRules.lineBets] de la apuesta elegida. */
    val betIndex: Int = DEFAULT_BET_INDEX,
    /** Saldo mostrado: se congela mientras giran los rodillos para no adelantar el resultado. */
    val balance: Chips? = null,
    val lastSpin: SlotSpinView? = null,
    val spinning: Boolean = false,
    val busy: Boolean = false,
    val notice: SlotsNotice? = null,
) {
    val lineBet: Long get() = rules.lineBets[betIndex]

    val totalBet: Long get() = rules.totalBets[betIndex]

    val canSpin: Boolean get() = !loading && !busy && !spinning

    val showResult: Boolean get() = lastSpin != null && !spinning
}

@Serializable
internal data class SlotsSession(val lineBet: Long)

/**
 * Tragaperras. El motor decide las paradas y la economía contabiliza apuesta y premio en una sola
 * operación **antes** de animar los rodillos; el resultado se muestra cuando la pantalla avisa de
 * que se han parado.
 */
class SlotsViewModel(
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val sessions: GameSessionStore,
    private val random: RandomGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow(SlotsUiState())
    val state: StateFlow<SlotsUiState> = _state.asStateFlow()

    private var playerId: String? = null
    private var latestBalance: Chips? = null
    private var nextSpinId = 1L
    private val spinShown = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            economyRepository.wallet.collect { wallet ->
                latestBalance = (wallet as? WalletState.Ready)?.wallet?.balance
                if (!_state.value.spinning) _state.update { it.copy(balance = latestBalance) }
            }
        }
        viewModelScope.launch { restore() }
    }

    fun increaseBet() = changeBet(+1)

    fun decreaseBet() = changeBet(-1)

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** La pantalla avisa de que los rodillos del giro [id] se han parado. */
    fun onSpinShown(id: Long) {
        spinShown.tryEmit(id)
    }

    private fun changeBet(step: Int) {
        val current = _state.value
        if (!current.canSpin) return
        val index = (current.betIndex + step).coerceIn(0, current.rules.lineBets.lastIndex)
        _state.update { it.copy(betIndex = index, notice = null) }
        save()
    }

    fun spin() {
        val current = _state.value
        if (!current.canSpin) return
        if (current.totalBet > (latestBalance?.amount ?: 0)) {
            _state.update { it.copy(notice = SlotsNotice.InsufficientFunds) }
            return
        }
        _state.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                play(current.rules, current.lineBet)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun play(rules: SlotRules, lineBet: Long) {
        val spin = when (val result = SlotEngine.spin(rules, lineBet, random)) {
            is Outcome.Failure -> return
            is Outcome.Success -> result.value
        }
        val played = economyRepository.playInstantRound(GameType.Slots, Chips(spin.totalBet), Chips(spin.totalPayout))
        if (played is Outcome.Failure) {
            val notice = if (played.error == EconomyError.InsufficientFunds) SlotsNotice.InsufficientFunds else SlotsNotice.WalletUnavailable
            return _state.update { it.copy(notice = notice) }
        }
        val id = nextSpinId++
        _state.update { it.copy(spinning = true, lastSpin = SlotSpinView(id, spin)) }
        // Se espera a que los rodillos se paren en pantalla, con un margen por si no se anima.
        withTimeoutOrNull(SPIN_DURATION + SPIN_GRACE) { spinShown.first { it == id } }
        _state.update { it.copy(spinning = false, balance = latestBalance) }
    }

    private suspend fun restore() {
        val wallet = economyRepository.wallet.first { it !is WalletState.Loading }
        val id = authRepository.authState.value.playerId
        if (wallet !is WalletState.Ready || id == null) {
            _state.update { it.copy(loading = false, notice = SlotsNotice.WalletUnavailable) }
            return
        }
        playerId = id
        val saved = sessions.load(id, GameType.Slots)
            ?.let { runCatching { json.decodeFromString(SlotsSession.serializer(), it) }.getOrNull() }
        val index = saved?.let { _state.value.rules.lineBets.indexOf(it.lineBet) }?.takeIf { it >= 0 } ?: DEFAULT_BET_INDEX
        _state.update { it.copy(loading = false, betIndex = index) }
    }

    private fun save() {
        val id = playerId ?: return
        sessions.save(id, GameType.Slots, json.encodeToString(SlotsSession.serializer(), SlotsSession(_state.value.lineBet)))
    }

    internal companion object {
        /** Duración del giro: el último rodillo se para al final. */
        val SPIN_DURATION = 2_600.milliseconds
        private val SPIN_GRACE = 2_000.milliseconds
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** Apuesta inicial: 10 por línea (100 en total). */
private const val DEFAULT_BET_INDEX = 3
