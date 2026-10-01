package com.royalchance.feature.blackjack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.playerId
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameSessionStore
import com.royalchance.domain.game.GameType
import com.royalchance.engine.blackjack.BlackjackAction
import com.royalchance.engine.blackjack.BlackjackEngine
import com.royalchance.engine.blackjack.BlackjackMove
import com.royalchance.engine.blackjack.BlackjackPhase
import com.royalchance.engine.blackjack.BlackjackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

/** Avisos de la mesa. */
sealed interface BlackjackNotice {
    data object InsufficientFunds : BlackjackNotice

    data object WalletUnavailable : BlackjackNotice

    /** Hay fichas en otra mesa (otro juego): hay que terminar esa ronda antes. */
    data object OtherRoundInProgress : BlackjackNotice

    /** Se encontró una mano a medias sin su estado guardado: se da por perdida. */
    data object HandForfeited : BlackjackNotice

    /** La ronda terminó pero no se pudo liquidar: se reintentará al volver. */
    data object SettlementPending : BlackjackNotice
}

data class BlackjackUiState(
    val loading: Boolean = true,
    val table: BlackjackState = BlackjackState(),
    /** Apuesta preparada para la siguiente ronda. */
    val bet: Long = DEFAULT_BET,
    /** Saldo mostrado: se congela mientras el crupier juega para no adelantar el resultado. */
    val balance: Chips? = null,
    /** Cartas del crupier ya mostradas y si la oculta ya se ha volteado (animación del final). */
    val dealerCardsShown: Int = 0,
    val holeCardShown: Boolean = false,
    val showResults: Boolean = false,
    val animating: Boolean = false,
    val busy: Boolean = false,
    val notice: BlackjackNotice? = null,
) {
    val canBet: Boolean get() = !loading && !busy && !animating && table.phase != BlackjackPhase.PlayerTurn

    val moves: Set<BlackjackMove> get() = if (busy || animating) emptySet() else table.availableMoves
}

/** Lo que se guarda en el dispositivo tras cada acción, para reanudar la mano. */
@Serializable
internal data class BlackjackSession(
    val table: BlackjackState,
    /** Ronda abierta en el monedero a la que pertenece esta mano. */
    val roundId: String? = null,
    /** La mano terminó en la mesa pero el monedero aún no la ha liquidado. */
    val settlementPending: Boolean = false,
    val lastBet: Long = DEFAULT_BET,
)

/**
 * Mesa de Blackjack. Une el motor (reglas) con la economía (fichas):
 * - Antes de repartir, doblar o separar se apuesta en el monedero; si no hay fichas, no se juega.
 * - Al terminar la ronda se liquida enseguida (antes de animar) y el saldo mostrado espera a que
 *   el crupier termine de destapar sus cartas.
 * - El estado se guarda tras cada acción; al volver, la mano se reanuda donde estaba.
 *
 * @param initialTable mesa de partida (en tests, con el zapato preparado).
 */
class BlackjackViewModel(
    private val authRepository: AuthRepository,
    private val economyRepository: EconomyRepository,
    private val sessions: GameSessionStore,
    private val random: RandomGenerator,
    private val initialTable: BlackjackState = BlackjackState(),
) : ViewModel() {

    private val _state = MutableStateFlow(BlackjackUiState(table = initialTable))
    val state: StateFlow<BlackjackUiState> = _state.asStateFlow()

    private var session = BlackjackSession(initialTable)
    private var playerId: String? = null
    private var latestBalance: Chips? = null

    init {
        viewModelScope.launch {
            economyRepository.wallet.collect { wallet ->
                latestBalance = (wallet as? WalletState.Ready)?.wallet?.balance
                if (!_state.value.animating) _state.update { it.copy(balance = latestBalance) }
            }
        }
        viewModelScope.launch { restore() }
    }

    // ── Apuesta ──────────────────────────────────────────────────────────────────────────────

    fun addChip(value: Long) = _state.update { current ->
        if (!current.canBet) return@update current
        current.copy(bet = minOf(current.bet + value, maximumAffordableBet()), notice = null)
    }

    fun clearBet() = _state.update { if (it.canBet) it.copy(bet = 0, notice = null) else it }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ── Acciones ─────────────────────────────────────────────────────────────────────────────

    fun deal() = act {
        val bet = _state.value.bet
        if (!BlackjackEngine.validateBet(session.table.rules, bet)) return@act
        val wallet = when (val placed = economyRepository.placeBet(GameType.Blackjack, Chips(bet))) {
            is Outcome.Failure -> return@act notify(placed.error)
            is Outcome.Success -> placed.value
        }
        session = session.copy(lastBet = bet, roundId = wallet.openRound?.id)
        play(BlackjackAction.Deal(bet))
    }

    fun hit() = act { play(BlackjackAction.Hit) }

    fun stand() = act { play(BlackjackAction.Stand) }

    fun double() = act { stakeAndPlay(BlackjackAction.Double) }

    fun split() = act { stakeAndPlay(BlackjackAction.Split) }

    private suspend fun stakeAndPlay(action: BlackjackAction) {
        val stake = session.table.stakeRequiredFor(action)
        when (val placed = economyRepository.placeBet(GameType.Blackjack, Chips(stake))) {
            is Outcome.Failure -> notify(placed.error)
            is Outcome.Success -> play(action)
        }
    }

    private suspend fun play(action: BlackjackAction) {
        val step = when (val result = BlackjackEngine.apply(session.table, action, random)) {
            is Outcome.Failure -> return
            is Outcome.Success -> result.value
        }
        session = session.copy(table = step.state)
        if (step.state.phase == BlackjackPhase.RoundOver) {
            // Primero se contabiliza y después se anima: cerrar la app a mitad no deshace nada.
            _state.update { it.copy(animating = true) }
            session = session.copy(settlementPending = true)
            save()
            settle()
            animateRoundEnd(step.state)
        } else {
            save()
            _state.update { it.copy(table = step.state, dealerCardsShown = step.state.dealer.size, holeCardShown = false, showResults = false) }
        }
    }

    /** Liquida en el monedero el pago total de la ronda. `false` si queda pendiente. */
    private suspend fun settle(): Boolean {
        val result = economyRepository.settleRound(Chips(session.table.totalPayout))
        // Sin ronda abierta, ya se liquidó (otro dispositivo o un intento anterior).
        val done = result is Outcome.Success || (result as? Outcome.Failure)?.error == EconomyError.NoOpenRound
        if (done) {
            session = session.copy(settlementPending = false, roundId = null)
            save()
        } else {
            _state.update { it.copy(notice = BlackjackNotice.SettlementPending) }
        }
        return done
    }

    /** El crupier voltea su carta y roba las que necesite, una a una; después, los resultados. */
    private suspend fun animateRoundEnd(table: BlackjackState) {
        _state.update { it.copy(table = table, dealerCardsShown = 2, holeCardShown = false, showResults = false, animating = true) }
        delay(DEALER_STEP)
        _state.update { it.copy(holeCardShown = true) }
        for (shown in 3..table.dealer.size) {
            delay(DEALER_STEP)
            _state.update { it.copy(dealerCardsShown = shown) }
        }
        delay(DEALER_STEP)
        _state.update {
            it.copy(
                showResults = true,
                animating = false,
                balance = latestBalance,
                bet = minOf(session.lastBet, maximumAffordableBet()),
            )
        }
    }

    // ── Reanudar ─────────────────────────────────────────────────────────────────────────────

    private suspend fun restore() {
        val wallet = (economyRepository.wallet.first { it !is WalletState.Loading } as? WalletState.Ready)?.wallet
        val id = authRepository.authState.value.playerId
        if (wallet == null || id == null) {
            _state.update { it.copy(loading = false, notice = BlackjackNotice.WalletUnavailable) }
            return
        }
        playerId = id
        val saved = sessions.load(id, GameType.Blackjack)?.let(::decode)
        val open = wallet.openRound?.takeIf { it.game == GameType.Blackjack }
        session = when {
            open != null && saved?.roundId == open.id && saved.table.phase == BlackjackPhase.PlayerTurn -> saved
            open != null && saved?.roundId == open.id && saved.settlementPending -> {
                // La mano terminó pero no llegó a liquidarse: se liquida ahora.
                session = saved
                settle()
                session
            }
            open != null -> forfeit(saved)
            // Sin ronda abierta, una mano a medias guardada ya no vale (se resolvió en otro sitio).
            saved != null -> saved.copy(table = saved.table.cleared(), roundId = null, settlementPending = false)
            else -> BlackjackSession(initialTable)
        }
        save()
        val table = session.table
        _state.update {
            it.copy(
                loading = false,
                table = table,
                dealerCardsShown = table.dealer.size,
                holeCardShown = table.holeCardRevealed,
                showResults = table.phase == BlackjackPhase.RoundOver,
                bet = minOf(session.lastBet, maximumAffordableBet(wallet)),
            )
        }
    }

    /**
     * Hay fichas en una mano de Blackjack sin su estado (otro dispositivo, datos borrados): no se
     * puede terminar con las reglas, así que se da por perdida. Devolverla permitiría ver las
     * cartas, borrar los datos y recuperar la apuesta de una mala mano.
     */
    private suspend fun forfeit(saved: BlackjackSession?): BlackjackSession {
        economyRepository.settleRound(Chips.ZERO)
        _state.update { it.copy(notice = BlackjackNotice.HandForfeited) }
        return BlackjackSession(table = saved?.table?.cleared() ?: initialTable, lastBet = saved?.lastBet ?: DEFAULT_BET)
    }

    // ── Utilidades ───────────────────────────────────────────────────────────────────────────

    private fun act(block: suspend () -> Unit) {
        if (_state.value.busy || _state.value.animating || _state.value.loading) return
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
            EconomyError.InsufficientFunds, EconomyError.AboveMaximumStake -> BlackjackNotice.InsufficientFunds
            is EconomyError.RoundInProgress -> BlackjackNotice.OtherRoundInProgress
            else -> BlackjackNotice.WalletUnavailable
        }
        _state.update { it.copy(notice = notice) }
    }

    private fun maximumAffordableBet(wallet: Wallet? = null): Long {
        val rules = session.table.rules
        val balance = wallet?.balance?.amount ?: latestBalance?.amount ?: rules.maximumBet
        val affordable = minOf(rules.maximumBet, balance)
        return affordable - affordable % rules.betStep
    }

    private fun save() {
        val id = playerId ?: return
        sessions.save(id, GameType.Blackjack, json.encodeToString(BlackjackSession.serializer(), session))
    }

    private fun decode(text: String): BlackjackSession? =
        runCatching { json.decodeFromString(BlackjackSession.serializer(), text) }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val DEALER_STEP = 550.milliseconds
    }
}

private const val DEFAULT_BET = 100L

/** Mesa vacía para una ronda nueva, conservando el zapato (y su carta de corte). */
private fun BlackjackState.cleared() = BlackjackState(rules = rules, shoe = shoe)
