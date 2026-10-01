package com.royalchance.data.economy

import com.royalchance.core.common.random.ProductionRandomGenerator
import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.random.nextId
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.playerId
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyOperation
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.LedgerEntry
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.economy.WalletTransition
import com.royalchance.domain.economy.WalletTransitions
import com.royalchance.domain.game.GameType
import com.royalchance.domain.history.HistoryError
import com.royalchance.domain.history.LedgerSource
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.ProgressEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Monederos en memoria, con la misma lógica ([WalletTransitions]) que la versión de Firebase.
 * Se usa en el escritorio de desarrollo y en tests: nada persiste al cerrar la app.
 *
 * @param scope ámbito de vida de la app: crea el monedero de cada jugador al entrar.
 */
class InMemoryEconomyRepository(
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val random: RandomGenerator = ProductionRandomGenerator(),
) : EconomyRepository, LedgerSource {

    private val mutex = Mutex()
    private val progressEvents = MutableSharedFlow<ProgressEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<ProgressEvent> = progressEvents.asSharedFlow()
    private val wallets = MutableStateFlow<Map<String, Wallet>>(emptyMap())
    private val ledgers = mutableMapOf<String, List<LedgerEntry>>()

    override val wallet: StateFlow<WalletState> =
        combine(authRepository.authState, wallets) { auth, wallets ->
            when (val playerId = auth.playerId) {
                null -> if (auth == AuthState.Loading) WalletState.Loading else WalletState.Unavailable
                else -> wallets[playerId]?.let(WalletState::Ready) ?: WalletState.Loading
            }
        }.stateIn(scope, SharingStarted.Eagerly, WalletState.Loading)

    init {
        authRepository.authState
            .map { it.playerId }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { playerId ->
                mutex.withLock {
                    if (playerId !in wallets.value) record(playerId, WalletTransitions.open(random.nextId(), clock.now()))
                }
            }
            .launchIn(scope)
    }

    /** Asientos de un jugador, del primero al último. Para tests y depuración. */
    fun ledger(playerId: String): List<LedgerEntry> = ledgers[playerId].orEmpty()

    override suspend fun ledgerBefore(playerId: String, beforeSequence: Long?, limit: Int): Outcome<List<LedgerEntry>, HistoryError> =
        Outcome.Success(ledger(playerId).asReversed().filter { beforeSequence == null || it.sequence < beforeSequence }.take(limit))

    override suspend fun ledgerAfter(playerId: String, afterSequence: Long, limit: Int): Outcome<List<LedgerEntry>, HistoryError> =
        Outcome.Success(ledger(playerId).filter { it.sequence > afterSequence }.take(limit))

    override suspend fun placeBet(game: GameType, stake: Chips) = execute(EconomyOperation.PlaceBet(game, stake))

    override suspend fun settleRound(payout: Chips) = execute(EconomyOperation.SettleRound(payout))

    override suspend fun playInstantRound(game: GameType, stake: Chips, payout: Chips) =
        execute(EconomyOperation.InstantRound(game, stake, payout))

    override suspend fun claimRescue() = execute(EconomyOperation.ClaimRescue)

    override suspend fun claimDailyBonus() = execute(EconomyOperation.ClaimDailyBonus(clock.todayIn(timeZone)))

    override suspend fun claimAchievement(id: AchievementId) = execute(EconomyOperation.ClaimAchievement(id))

    private suspend fun execute(operation: EconomyOperation): Outcome<Wallet, EconomyError> = mutex.withLock {
        val playerId = authRepository.authState.value.playerId ?: return failure(EconomyError.WalletUnavailable)
        val current = wallets.value[playerId] ?: return failure(EconomyError.WalletUnavailable)
        when (val result = WalletTransitions.apply(current, operation, random.nextId(), clock.now())) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                record(playerId, result.value)
                Outcome.Success(result.value.wallet)
            }
        }
    }

    private fun record(playerId: String, transition: WalletTransition) {
        ledgers[playerId] = ledger(playerId) + transition.entry
        wallets.update { it + (playerId to transition.wallet) }
        transition.events.forEach(progressEvents::tryEmit)
    }

    private companion object {
        const val EVENT_BUFFER = 16
    }
}
