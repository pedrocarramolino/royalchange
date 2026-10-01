package com.royalchance.feature.poker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.playerId
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameSessionStore
import com.royalchance.domain.game.GameType
import com.royalchance.engine.poker.PokerAction
import com.royalchance.engine.poker.PokerBot
import com.royalchance.engine.poker.PokerEngine
import com.royalchance.engine.poker.PokerPhase
import com.royalchance.engine.poker.PokerRules
import com.royalchance.engine.poker.PokerState
import com.royalchance.engine.poker.PokerTable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

sealed interface PokerNotice {
    data object InsufficientFunds : PokerNotice

    data object WalletUnavailable : PokerNotice

    /** Hay fichas en otra mesa (otro juego): hay que terminar esa ronda antes. */
    data object OtherRoundInProgress : PokerNotice

    /** Se encontró una mano a medias sin su estado guardado: se da por perdida. */
    data object HandForfeited : PokerNotice

    /** La mano terminó pero no se pudo registrar el resultado: se reintentará. */
    data object SettlementPending : PokerNotice
}

/** Índice del jugador en la mesa: siempre el asiento de abajo. */
const val HERO: Int = 0

data class PokerUiState(
    val loading: Boolean = true,
    /** Mesa en la que está sentado el jugador; `null` si aún no se ha sentado. */
    val table: PokerState? = null,
    val balance: Chips? = null,
    /** Mesa elegida para sentarse ([PokerRules.TABLES]) y fichas que se lleva. */
    val selectedTable: Int = 0,
    val buyIn: Long = PokerRules.TABLES[0].maxBuyIn / 2,
    val busy: Boolean = false,
    val notice: PokerNotice? = null,
) {
    val seated: Boolean get() = table != null

    val heroTurn: Boolean get() = table?.toAct == HERO && table.phase == PokerPhase.Betting && !busy

    val inHand: Boolean get() = table?.phase == PokerPhase.Betting

    val canDeal: Boolean get() = table != null && table.phase != PokerPhase.Betting && !busy

    /** El jugador no tiene fichas para la ciega grande: tiene que recargar o levantarse. */
    val needsRebuy: Boolean get() = table != null && table.seats[HERO].stack < table.rules.bigBlind && table.phase != PokerPhase.Betting
}

/** Lo que se guarda tras cada acción para reanudar la mano. */
@Serializable
internal data class PokerSession(
    val table: PokerState,
    /** Fichas del jugador ya cargadas en el monedero en esta mano. */
    val accounted: Long = 0,
    /** Ronda abierta en el monedero a la que pertenece la mano. */
    val roundId: String? = null,
    /** El resultado del jugador en esta mano ya se registró (o no puso fichas). */
    val settled: Boolean = true,
    /** Pila del jugador al empezar la mano. */
    val stackAtStart: Long = 0,
)

/**
 * Mesa de póker contra cinco bots.
 *
 * Contabilidad por mano, como en el blackjack: cada ficha que el jugador mete en el bote (ciegas,
 * igualar, subir) se apuesta en el monedero antes de mostrarse en la mesa, y al terminar la mano
 * (o al retirarse) se liquida lo que gana. Las fichas "en la mesa" son el tope de lo que el jugador
 * arriesga por mano; siguen siendo parte de su saldo.
 */
class PokerViewModel(
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val sessions: GameSessionStore,
    private val random: RandomGenerator,
    /** Dónde calculan los bots (en tests, el planificador del test). */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(PokerUiState())
    val state: StateFlow<PokerUiState> = _state.asStateFlow()

    private var session: PokerSession? = null
    private var playerId: String? = null
    private var playerName: String = ""
    private var latestBalance: Chips? = null
    private var botsJob: Job? = null

    init {
        viewModelScope.launch {
            economyRepository.wallet.collect { wallet ->
                latestBalance = (wallet as? WalletState.Ready)?.wallet?.balance
                _state.update { it.copy(balance = latestBalance) }
            }
        }
        viewModelScope.launch { restore() }
    }

    // ── Sentarse y levantarse ────────────────────────────────────────────────────────────────

    fun selectTable(index: Int) = _state.update {
        if (it.seated) return@update it
        val rules = PokerRules.TABLES[index]
        it.copy(selectedTable = index, buyIn = clampBuyIn(rules, (rules.minBuyIn + rules.maxBuyIn) / 2))
    }

    fun changeBuyIn(amount: Long) = _state.update {
        if (it.seated) it else it.copy(buyIn = clampBuyIn(PokerRules.TABLES[it.selectedTable], amount))
    }

    /** Se sienta con [PokerUiState.buyIn] fichas y reparte la primera mano. */
    fun sitDown() {
        val current = _state.value
        if (current.seated || current.busy || current.loading) return
        val rules = PokerRules.TABLES[current.selectedTable]
        val balance = latestBalance?.amount ?: 0
        if (current.buyIn < rules.minBuyIn || current.buyIn > balance) {
            _state.update { it.copy(notice = PokerNotice.InsufficientFunds) }
            return
        }
        val table = PokerTable.create(rules, playerName, current.buyIn, random)
        session = PokerSession(table)
        save()
        _state.update { it.copy(table = table, notice = null) }
        dealNextHand()
    }

    /** Recarga la pila del jugador entre manos (hasta el máximo de la mesa y su saldo). */
    fun rebuy() {
        val table = _state.value.table ?: return
        if (table.phase == PokerPhase.Betting || _state.value.busy) return
        val rules = table.rules
        val target = clampBuyIn(rules, rules.maxBuyIn)
        if (target < rules.minBuyIn) {
            _state.update { it.copy(notice = PokerNotice.InsufficientFunds) }
            return
        }
        commit(table.copy(seats = table.seats.mapIndexed { i, seat -> if (i == HERO) seat.copy(stack = maxOf(seat.stack, target)) else seat }))
    }

    /** Se levanta de la mesa. Solo entre manos o tras retirarse (sus fichas ya están contabilizadas). */
    fun standUp() {
        val table = _state.value.table ?: return
        val hero = table.seats[HERO]
        if (table.phase == PokerPhase.Betting && hero.inHand) return
        botsJob?.cancel()
        session = null
        playerId?.let { sessions.clear(it, GameType.Poker) }
        _state.update { it.copy(table = null, busy = false, notice = null) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ── Manos ────────────────────────────────────────────────────────────────────────────────

    fun dealNextHand() {
        val table = _state.value.table ?: return
        if (table.phase == PokerPhase.Betting || _state.value.busy) return
        act {
            // La pila en la mesa nunca supera el saldo real (pudo bajar jugando en otro dispositivo).
            val balance = latestBalance?.amount ?: 0
            val unit = table.rules.chipUnit
            val refilled = PokerTable.refill(table, random).let { t ->
                t.copy(seats = t.seats.mapIndexed { i, seat -> if (i == HERO) seat.copy(stack = minOf(seat.stack, balance / unit * unit)) else seat })
            }
            if (refilled.seats[HERO].stack < refilled.rules.bigBlind) {
                commit(refilled)
                return@act
            }
            val started = when (val result = PokerEngine.startHand(refilled, random)) {
                is Outcome.Failure -> return@act
                is Outcome.Success -> result.value
            }
            session = PokerSession(table = refilled, stackAtStart = refilled.seats[HERO].stack, settled = false)
            if (!account(started)) return@act
            commit(started)
            runBots()
        }
    }

    fun fold() = heroAction(PokerAction.Fold)

    fun checkOrCall() = heroAction(PokerAction.Call)

    fun raiseTo(amount: Long) = heroAction(PokerAction.RaiseTo(amount))

    private fun heroAction(action: PokerAction) {
        if (!_state.value.heroTurn) return
        act {
            val table = session?.table ?: return@act
            val next = when (val result = PokerEngine.apply(table, HERO, action)) {
                is Outcome.Failure -> return@act
                is Outcome.Success -> result.value
            }
            if (!account(next)) return@act
            commit(next)
        }
        runBots()
    }

    /** Juegan los bots, uno a uno y con una pausa, hasta que le toque al jugador o acabe la mano. */
    private fun runBots() {
        botsJob?.cancel()
        botsJob = viewModelScope.launch {
            while (true) {
                _state.first { !it.busy }
                val table = session?.table ?: return@launch
                if (table.phase == PokerPhase.HandOver) {
                    finishHand()
                    return@launch
                }
                val seat = table.toAct ?: return@launch
                if (seat == HERO) return@launch
                delay(BOT_THINKING)
                // Las simulaciones del bot, fuera del hilo de la interfaz (en Android y escritorio).
                val action = withContext(computeDispatcher) { PokerBot.decide(table, seat, random) }
                val next = (PokerEngine.apply(table, seat, action) as? Outcome.Success)?.value ?: return@launch
                commit(next)
                // Pausa al repartir una calle nueva para que se vean las cartas.
                if (next.street != table.street || next.phase == PokerPhase.HandOver) delay(STREET_PAUSE)
            }
        }
    }

    private suspend fun finishHand() {
        val current = session ?: return
        if (!current.settled) settle(current.table)
    }

    // ── Contabilidad ─────────────────────────────────────────────────────────────────────────

    /**
     * Apuesta en el monedero lo que el jugador ha metido en el bote desde la última vez. Si la mano
     * termina (o el jugador se retira), liquida lo que gana. Devuelve `false` si no se pudo apostar:
     * en ese caso la acción no se aplica.
     */
    private suspend fun account(next: PokerState): Boolean {
        val current = session ?: return false
        val delta = next.seats[HERO].committed - current.accounted
        if (delta > 0) {
            when (val placed = economyRepository.placeBet(GameType.Poker, Chips(delta))) {
                is Outcome.Failure -> {
                    notify(placed.error)
                    return false
                }
                is Outcome.Success -> session = current.copy(accounted = current.accounted + delta, roundId = placed.value.openRound?.id)
            }
        }
        val hero = next.seats[HERO]
        val heroDone = next.phase == PokerPhase.HandOver || hero.folded
        if (heroDone && session?.settled == false) settle(next)
        return true
    }

    /** Liquida la mano del jugador: lo que recupera del bote (0 si se retiró o perdió). */
    private suspend fun settle(table: PokerState) {
        val current = session ?: return
        if (current.accounted == 0L) {
            session = current.copy(settled = true)
            save()
            return
        }
        val hero = table.seats[HERO]
        val payout = if (hero.folded || table.phase != PokerPhase.HandOver) 0 else hero.stack - current.stackAtStart + current.accounted
        val result = economyRepository.settleRound(Chips(payout))
        val done = result is Outcome.Success || (result as? Outcome.Failure)?.error == EconomyError.NoOpenRound
        if (done) {
            session = current.copy(settled = true, roundId = null)
            save()
        } else {
            _state.update { it.copy(notice = PokerNotice.SettlementPending) }
        }
    }

    // ── Reanudar ─────────────────────────────────────────────────────────────────────────────

    private suspend fun restore() {
        val wallet = (economyRepository.wallet.first { it !is WalletState.Loading } as? WalletState.Ready)?.wallet
        val auth = authRepository.authState.value
        val id = auth.playerId
        if (wallet == null || id == null) {
            _state.update { it.copy(loading = false, notice = PokerNotice.WalletUnavailable) }
            return
        }
        playerId = id
        playerName = (auth as? AuthState.SignedIn)?.user?.profile?.alias.orEmpty()
        val saved = sessions.load(id, GameType.Poker)?.let(::decode)
        val open = wallet.openRound?.takeIf { it.game == GameType.Poker }
        // La sesión guardada corresponde a la ronda abierta en el monedero.
        val owned = saved?.takeIf { open != null && it.roundId == open.id && !it.settled }
        session = when {
            // La mano sigue: se reanuda donde estaba.
            owned != null && owned.table.phase == PokerPhase.Betting -> owned
            // La mano terminó pero no llegó a liquidarse: se liquida ahora.
            owned != null -> {
                session = owned
                settle(owned.table)
                session
            }
            // Fichas en una mano de póker sin su estado (otro dispositivo, datos borrados).
            open != null -> {
                economyRepository.settleRound(Chips.ZERO)
                _state.update { it.copy(notice = PokerNotice.HandForfeited) }
                saved?.let(::abandonHand)
            }
            // Mano a medias sin fichas del jugador en juego (no puso nada o ya se retiró): sigue.
            saved != null && saved.table.phase == PokerPhase.Betting && (saved.settled || saved.accounted == 0L) -> saved
            // Puso fichas, pero la ronda ya se cerró en otro sitio: la mano no puede continuar.
            saved != null && saved.table.phase == PokerPhase.Betting -> abandonHand(saved)
            else -> saved
        }
        save()
        _state.update { it.copy(loading = false, table = session?.table) }
        if (session?.table?.phase == PokerPhase.Betting) runBots()
    }

    /** Descarta una mano que no se puede terminar: el jugador pierde lo que aportó y los bots recuperan lo suyo. */
    private fun abandonHand(saved: PokerSession): PokerSession {
        val table = saved.table
        val seats = table.seats.mapIndexed { i, seat ->
            if (i == HERO) {
                seat.copy(stack = saved.stackAtStart - saved.accounted, hole = emptyList(), bet = 0, committed = 0, folded = false)
            } else {
                seat.copy(stack = seat.stack + seat.committed, hole = emptyList(), bet = 0, committed = 0, folded = false)
            }
        }
        return PokerSession(table.copy(seats = seats, phase = PokerPhase.Waiting, toAct = null, board = emptyList(), awards = emptyList()))
    }

    // ── Utilidades ───────────────────────────────────────────────────────────────────────────

    private fun commit(table: PokerState) {
        session = (session ?: PokerSession(table)).copy(table = table)
        save()
        _state.update { it.copy(table = table) }
    }

    private fun act(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun notify(error: EconomyError) {
        val notice = when (error) {
            EconomyError.InsufficientFunds, EconomyError.AboveMaximumStake -> PokerNotice.InsufficientFunds
            is EconomyError.RoundInProgress -> PokerNotice.OtherRoundInProgress
            else -> PokerNotice.WalletUnavailable
        }
        _state.update { it.copy(notice = notice) }
    }

    private fun clampBuyIn(rules: PokerRules, amount: Long): Long {
        val balance = latestBalance?.amount ?: rules.maxBuyIn
        val max = minOf(rules.maxBuyIn, balance / rules.chipUnit * rules.chipUnit)
        return amount.coerceAtMost(max).coerceAtLeast(minOf(rules.minBuyIn, max)) / rules.chipUnit * rules.chipUnit
    }

    private fun save() {
        val id = playerId ?: return
        val current = session ?: return
        sessions.save(id, GameType.Poker, json.encodeToString(PokerSession.serializer(), current))
    }

    private fun decode(text: String): PokerSession? =
        runCatching { json.decodeFromString(PokerSession.serializer(), text) }.getOrNull()

    internal companion object {
        val BOT_THINKING = 750.milliseconds
        val STREET_PAUSE = 650.milliseconds
        private val json = Json { ignoreUnknownKeys = true }
    }
}
