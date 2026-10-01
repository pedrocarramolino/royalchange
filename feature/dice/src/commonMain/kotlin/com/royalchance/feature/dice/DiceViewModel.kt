package com.royalchance.feature.dice

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
import com.royalchance.engine.dice.DiceBet
import com.royalchance.engine.dice.DiceEngine
import com.royalchance.engine.dice.DiceError
import com.royalchance.engine.dice.DiceRoll
import com.royalchance.engine.dice.DiceRules
import com.royalchance.engine.dice.DiceThrow
import com.royalchance.engine.dice.PlacedDiceBet
import com.royalchance.engine.dice.isValid
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
sealed interface DiceNotice {
    data object InsufficientFunds : DiceNotice

    data class AboveTableMaximum(val maximum: Long) : DiceNotice

    data object WalletUnavailable : DiceNotice
}

/** Una tirada para la pantalla: [id] distingue dos tiradas seguidas iguales. */
data class ThrowView(val id: Long, val result: DiceThrow)

data class DiceUiState(
    val loading: Boolean = true,
    val rules: DiceRules = DiceRules(),
    /** Ficha elegida: cada toque en el tapete apuesta este valor. */
    val chip: Long = DEFAULT_CHIP,
    /** Apuestas preparadas para la próxima tirada. */
    val bets: List<PlacedDiceBet> = emptyList(),
    /** Apuestas de la última tirada, para repetirlas. */
    val lastBets: List<PlacedDiceBet> = emptyList(),
    /** Saldo mostrado: se congela mientras ruedan los dados para no adelantar el resultado. */
    val balance: Chips? = null,
    /** Última tirada (se anima mientras [rolling]). */
    val lastThrow: ThrowView? = null,
    val rolling: Boolean = false,
    /** Tiradas anteriores, la más reciente primero. */
    val history: List<DiceRoll> = emptyList(),
    val busy: Boolean = false,
    val canUndo: Boolean = false,
    val notice: DiceNotice? = null,
) {
    val totalBet: Long get() = bets.sumOf { it.stake }

    val canBet: Boolean get() = !loading && !busy && !rolling

    /** El resultado de la última tirada está a la vista (los dados pararon y aún no hay apuestas nuevas). */
    val showResult: Boolean get() = lastThrow != null && !rolling && bets.isEmpty()

    fun stakeOn(bet: DiceBet): Long = bets.firstOrNull { it.bet == bet }?.stake ?: 0
}

/** Lo que se guarda en el dispositivo: las últimas tiradas y la última apuesta. */
@Serializable
internal data class DiceSession(
    val history: List<DiceRoll> = emptyList(),
    val lastBets: List<PlacedDiceBet> = emptyList(),
    val chip: Long = DEFAULT_CHIP,
)

/**
 * Mesa de dados. El motor decide la tirada y la economía contabiliza apuesta y pago en una sola
 * operación **antes** de animar los dados: cerrar la app a mitad de la tirada no deshace nada.
 * Mientras ruedan, el saldo mostrado espera al final de la animación.
 */
class DiceViewModel(
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val sessions: GameSessionStore,
    private val random: RandomGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow(DiceUiState())
    val state: StateFlow<DiceUiState> = _state.asStateFlow()

    private var playerId: String? = null
    private var latestBalance: Chips? = null
    private var nextThrowId = 1L

    /** Giros que la pantalla ya ha terminado de animar. */
    private val rollShown = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Estados anteriores del tapete, para deshacer. */
    private val undoStack = ArrayDeque<List<PlacedDiceBet>>()

    init {
        viewModelScope.launch {
            economyRepository.wallet.collect { wallet ->
                latestBalance = (wallet as? WalletState.Ready)?.wallet?.balance
                if (!_state.value.rolling) _state.update { it.copy(balance = latestBalance) }
            }
        }
        viewModelScope.launch { restore() }
    }

    // ── Apuestas ─────────────────────────────────────────────────────────────────────────────

    fun selectChip(value: Long) = _state.update { it.copy(chip = value) }

    /** Añade la ficha elegida a [bet]. */
    fun place(bet: DiceBet) {
        val current = _state.value
        if (!current.canBet || !bet.isValid) return
        val total = current.totalBet + current.chip
        limitNotice(total)?.let { notice -> return _state.update { it.copy(notice = notice) } }
        val existing = current.stakeOn(bet)
        val bets = if (existing == 0L) {
            current.bets + PlacedDiceBet(bet, current.chip)
        } else {
            current.bets.map { if (it.bet == bet) it.copy(stake = it.stake + current.chip) else it }
        }
        update(bets)
    }

    /** Vuelve a poner las apuestas de la última tirada. */
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

    /** La pantalla avisa de que los dados de la tirada [id] se han parado. */
    fun onRollShown(id: Long) {
        rollShown.tryEmit(id)
    }

    private fun update(bets: List<PlacedDiceBet>) {
        undoStack.addLast(_state.value.bets)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        _state.update { it.copy(bets = bets, canUndo = true, notice = null) }
    }

    /** Aviso si un total de [total] fichas no cabe en la mesa o en el saldo; `null` si cabe. */
    private fun limitNotice(total: Long): DiceNotice? {
        val rules = _state.value.rules
        return when {
            total > rules.maximumTotalBet -> DiceNotice.AboveTableMaximum(rules.maximumTotalBet)
            total > (latestBalance?.amount ?: 0) -> DiceNotice.InsufficientFunds
            else -> null
        }
    }

    // ── Giro ─────────────────────────────────────────────────────────────────────────────────

    fun roll() {
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

    private suspend fun play(bets: List<PlacedDiceBet>) {
        val thrown = when (val result = DiceEngine.roll(_state.value.rules, bets, random)) {
            is Outcome.Failure -> return _state.update { it.copy(notice = result.error.toNotice()) }
            is Outcome.Success -> result.value
        }
        // Primero se contabiliza; si falla, no se ha cobrado nada y las apuestas siguen en la mesa.
        val played = economyRepository.playInstantRound(GameType.Dice, Chips(thrown.totalStake), Chips(thrown.totalPayout))
        if (played is Outcome.Failure) return _state.update { it.copy(notice = played.error.toNotice()) }

        val history = (listOf(thrown.roll) + _state.value.history).take(HISTORY_SIZE)
        save(DiceSession(history, bets, _state.value.chip))
        undoStack.clear()
        _state.update { it.copy(rolling = true, lastThrow = ThrowView(nextThrowId++, thrown), lastBets = bets, canUndo = false) }

        // Se espera a que los dados se paren en pantalla; si no llega el aviso (pantalla en segundo
        // plano, sin fotogramas), el resultado se muestra igualmente tras un margen.
        val id = nextThrowId - 1
        withTimeoutOrNull(ROLL_DURATION + ROLL_GRACE) { rollShown.first { it == id } }
        _state.update { it.copy(rolling = false, bets = emptyList(), history = history, balance = latestBalance) }
    }

    // ── Reanudar ─────────────────────────────────────────────────────────────────────────────

    private suspend fun restore() {
        val wallet = economyRepository.wallet.first { it !is WalletState.Loading }
        val id = authRepository.authState.value.playerId
        if (wallet !is WalletState.Ready || id == null) {
            _state.update { it.copy(loading = false, notice = DiceNotice.WalletUnavailable) }
            return
        }
        playerId = id
        val saved = sessions.load(id, GameType.Dice)?.let(::decode) ?: DiceSession()
        _state.update {
            it.copy(loading = false, history = saved.history, lastBets = saved.lastBets.filter { bet -> bet.bet.isValid }, chip = saved.chip)
        }
    }

    private fun save(session: DiceSession) {
        val id = playerId ?: return
        sessions.save(id, GameType.Dice, json.encodeToString(DiceSession.serializer(), session))
    }

    private fun decode(text: String): DiceSession? =
        runCatching { json.decodeFromString(DiceSession.serializer(), text) }.getOrNull()

    private fun DiceError.toNotice(): DiceNotice = when (this) {
        DiceError.AboveTableMaximum -> DiceNotice.AboveTableMaximum(_state.value.rules.maximumTotalBet)
        DiceError.NoBets, DiceError.InvalidBet -> DiceNotice.InsufficientFunds
    }

    private fun EconomyError.toNotice(): DiceNotice = when (this) {
        EconomyError.InsufficientFunds, EconomyError.BelowMinimumBet -> DiceNotice.InsufficientFunds
        EconomyError.AboveMaximumStake -> DiceNotice.AboveTableMaximum(_state.value.rules.maximumTotalBet)
        else -> DiceNotice.WalletUnavailable
    }

    internal companion object {
        /** Duración de la tirada: la pantalla anima los dados en este tiempo. */
        val ROLL_DURATION = 1_600.milliseconds
        private val ROLL_GRACE = 2_000.milliseconds
        const val HISTORY_SIZE = 12
        private const val MAX_UNDO = 50
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private const val DEFAULT_CHIP = 100L
