package com.royalchance.feature.roulette

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
import com.royalchance.engine.roulette.PlacedBet
import com.royalchance.engine.roulette.RouletteBet
import com.royalchance.engine.roulette.RouletteEngine
import com.royalchance.engine.roulette.RouletteError
import com.royalchance.engine.roulette.RouletteRules
import com.royalchance.engine.roulette.RouletteSpin
import com.royalchance.engine.roulette.isValid
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

/** Avisos de la mesa. */
sealed interface RouletteNotice {
    data object InsufficientFunds : RouletteNotice

    data class AboveTableMaximum(val maximum: Long) : RouletteNotice

    data object WalletUnavailable : RouletteNotice
}

/** Un giro para la pantalla: [id] distingue dos giros seguidos al mismo número. */
data class SpinView(val id: Long, val spin: RouletteSpin)

data class RouletteUiState(
    val loading: Boolean = true,
    val rules: RouletteRules = RouletteRules(),
    /** Ficha elegida: cada toque en el tapete apuesta este valor. */
    val chip: Long = DEFAULT_CHIP,
    /** Apuestas preparadas para el próximo giro. */
    val bets: List<PlacedBet> = emptyList(),
    /** Apuestas del último giro, para repetirlas. */
    val lastBets: List<PlacedBet> = emptyList(),
    /** Saldo mostrado: se congela mientras gira la bola para no adelantar el resultado. */
    val balance: Chips? = null,
    /** Último giro (se anima mientras [spinning]). */
    val lastSpin: SpinView? = null,
    val spinning: Boolean = false,
    /** Números anteriores, el más reciente primero. */
    val history: List<Int> = emptyList(),
    val busy: Boolean = false,
    val canUndo: Boolean = false,
    val notice: RouletteNotice? = null,
) {
    val totalBet: Long get() = bets.sumOf { it.stake }

    val canBet: Boolean get() = !loading && !busy && !spinning

    /** El resultado del último giro está a la vista (terminó de girar y aún no hay apuestas nuevas). */
    val showResult: Boolean get() = lastSpin != null && !spinning && bets.isEmpty()

    fun stakeOn(bet: RouletteBet): Long = bets.firstOrNull { it.bet == bet }?.stake ?: 0
}

/** Lo que se guarda en el dispositivo: los últimos números y la última apuesta. */
@Serializable
internal data class RouletteSession(
    val history: List<Int> = emptyList(),
    val lastBets: List<PlacedBet> = emptyList(),
    val chip: Long = DEFAULT_CHIP,
)

/**
 * Mesa de ruleta. El motor decide el número y la economía contabiliza apuesta y pago en una sola
 * operación **antes** de animar la rueda: cerrar la app a mitad del giro no deshace nada. Mientras
 * gira, el saldo mostrado espera al final de la animación.
 */
class RouletteViewModel(
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val sessions: GameSessionStore,
    private val random: RandomGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow(RouletteUiState())
    val state: StateFlow<RouletteUiState> = _state.asStateFlow()

    private var playerId: String? = null
    private var latestBalance: Chips? = null
    private var nextSpinId = 1L

    /** Giros que la pantalla ya ha terminado de animar. */
    private val spinShown = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Estados anteriores del tapete, para deshacer. */
    private val undoStack = ArrayDeque<List<PlacedBet>>()

    init {
        viewModelScope.launch {
            economyRepository.wallet.collect { wallet ->
                latestBalance = (wallet as? WalletState.Ready)?.wallet?.balance
                if (!_state.value.spinning) _state.update { it.copy(balance = latestBalance) }
            }
        }
        viewModelScope.launch { restore() }
    }

    // ── Apuestas ─────────────────────────────────────────────────────────────────────────────

    fun selectChip(value: Long) = _state.update { it.copy(chip = value) }

    /** Añade la ficha elegida a [bet]. */
    fun place(bet: RouletteBet) {
        val current = _state.value
        if (!current.canBet || !bet.isValid) return
        val total = current.totalBet + current.chip
        limitNotice(total)?.let { notice -> return _state.update { it.copy(notice = notice) } }
        val existing = current.stakeOn(bet)
        val bets = if (existing == 0L) {
            current.bets + PlacedBet(bet, current.chip)
        } else {
            current.bets.map { if (it.bet == bet) it.copy(stake = it.stake + current.chip) else it }
        }
        update(bets)
    }

    /** Vuelve a poner las apuestas del último giro. */
    fun repeat() {
        val current = _state.value
        if (!current.canBet || current.lastBets.isEmpty()) return
        limitNotice(current.lastBets.sumOf { it.stake })?.let { notice -> return _state.update { it.copy(notice = notice) } }
        update(current.lastBets)
    }

    fun undo() {
        if (!_state.value.canBet) return
        val previous = undoStack.removeLastOrNull() ?: return
        _state.update { it.copy(bets = previous, canUndo = undoStack.isNotEmpty(), notice = null) }
    }

    fun clear() {
        val current = _state.value
        if (!current.canBet || current.bets.isEmpty()) return
        update(emptyList())
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** La pantalla avisa de que la bola se ha parado en el giro [id]. */
    fun onSpinShown(id: Long) {
        spinShown.tryEmit(id)
    }

    private fun update(bets: List<PlacedBet>) {
        undoStack.addLast(_state.value.bets)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        _state.update { it.copy(bets = bets, canUndo = true, notice = null) }
    }

    /** Aviso si un total de [total] fichas no cabe en la mesa o en el saldo; `null` si cabe. */
    private fun limitNotice(total: Long): RouletteNotice? {
        val rules = _state.value.rules
        return when {
            total > rules.maximumTotalBet -> RouletteNotice.AboveTableMaximum(rules.maximumTotalBet)
            total > (latestBalance?.amount ?: 0) -> RouletteNotice.InsufficientFunds
            else -> null
        }
    }

    // ── Giro ─────────────────────────────────────────────────────────────────────────────────

    fun spin() {
        val current = _state.value
        if (!current.canBet || current.bets.isEmpty()) return
        _state.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                play(current.bets)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun play(bets: List<PlacedBet>) {
        val spin = when (val result = RouletteEngine.spin(_state.value.rules, bets, random)) {
            is Outcome.Failure -> return _state.update { it.copy(notice = result.error.toNotice()) }
            is Outcome.Success -> result.value
        }
        // Primero se contabiliza; si falla, no se ha cobrado nada y las apuestas siguen en la mesa.
        val played = economyRepository.playInstantRound(GameType.Roulette, Chips(spin.totalStake), Chips(spin.totalPayout))
        if (played is Outcome.Failure) return _state.update { it.copy(notice = played.error.toNotice()) }

        val history = (listOf(spin.number) + _state.value.history).take(HISTORY_SIZE)
        save(RouletteSession(history, bets, _state.value.chip))
        undoStack.clear()
        _state.update { it.copy(spinning = true, lastSpin = SpinView(nextSpinId++, spin), lastBets = bets, canUndo = false) }

        // Se espera a que la rueda se pare en pantalla; si no llega el aviso (pantalla en segundo
        // plano, sin fotogramas), el resultado se muestra igualmente tras un margen.
        val id = nextSpinId - 1
        withTimeoutOrNull(SPIN_DURATION + SPIN_GRACE) { spinShown.first { it == id } }
        _state.update { it.copy(spinning = false, bets = emptyList(), history = history, balance = latestBalance) }
    }

    // ── Reanudar ─────────────────────────────────────────────────────────────────────────────

    private suspend fun restore() {
        val wallet = economyRepository.wallet.first { it !is WalletState.Loading }
        val id = authRepository.authState.value.playerId
        if (wallet !is WalletState.Ready || id == null) {
            _state.update { it.copy(loading = false, notice = RouletteNotice.WalletUnavailable) }
            return
        }
        playerId = id
        val saved = sessions.load(id, GameType.Roulette)?.let(::decode) ?: RouletteSession()
        _state.update {
            it.copy(loading = false, history = saved.history, lastBets = saved.lastBets.filter { bet -> bet.bet.isValid }, chip = saved.chip)
        }
    }

    private fun save(session: RouletteSession) {
        val id = playerId ?: return
        sessions.save(id, GameType.Roulette, json.encodeToString(RouletteSession.serializer(), session))
    }

    private fun decode(text: String): RouletteSession? =
        runCatching { json.decodeFromString(RouletteSession.serializer(), text) }.getOrNull()

    private fun RouletteError.toNotice(): RouletteNotice = when (this) {
        RouletteError.AboveTableMaximum -> RouletteNotice.AboveTableMaximum(_state.value.rules.maximumTotalBet)
        RouletteError.NoBets, RouletteError.InvalidBet -> RouletteNotice.InsufficientFunds
    }

    private fun EconomyError.toNotice(): RouletteNotice = when (this) {
        EconomyError.InsufficientFunds, EconomyError.BelowMinimumBet -> RouletteNotice.InsufficientFunds
        EconomyError.AboveMaximumStake -> RouletteNotice.AboveTableMaximum(_state.value.rules.maximumTotalBet)
        else -> RouletteNotice.WalletUnavailable
    }

    internal companion object {
        /** Duración del giro: la pantalla anima la rueda en este tiempo. */
        val SPIN_DURATION = 4_500.milliseconds
        private val SPIN_GRACE = 2_000.milliseconds
        const val HISTORY_SIZE = 12
        private const val MAX_UNDO = 50
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private const val DEFAULT_CHIP = 100L
