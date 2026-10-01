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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val random: RandomGenerator = ProductionRandomGenerator(),
) : EconomyRepository {

    private val mutex = Mutex()
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

    override suspend fun placeBet(game: GameType, stake: Chips) = execute(EconomyOperation.PlaceBet(game, stake))

    override suspend fun settleRound(payout: Chips) = execute(EconomyOperation.SettleRound(payout))

    override suspend fun playInstantRound(game: GameType, stake: Chips, payout: Chips) =
        execute(EconomyOperation.InstantRound(game, stake, payout))

    override suspend fun claimRescue() = execute(EconomyOperation.ClaimRescue)

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
    }
}
