package com.royalchance.data.firebase

import com.royalchance.core.common.random.RandomGenerator
import com.royalchance.core.common.random.nextId
import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.data.firebase.gateway.PendingWrite
import com.royalchance.data.firebase.gateway.WalletStore
import com.royalchance.domain.auth.AuthRepository
import com.royalchance.domain.auth.AuthState
import com.royalchance.domain.auth.playerId
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyOperation
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.economy.WalletTransition
import com.royalchance.domain.economy.WalletTransitions
import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.ProgressEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Economía sobre Firestore, independiente de la plataforma.
 *
 * - Cada operación se valida con [WalletTransitions] contra la versión local más reciente del
 *   monedero y se escribe junto a su asiento en un único lote. Las operaciones van en serie.
 * - No se espera al servidor: Firestore aplica el lote en local al instante, también sin conexión,
 *   y lo sincroniza después. Si el servidor lo rechazara (reglas de seguridad), Firestore deshace el
 *   cambio local y [wallet] vuelve al último saldo válido.
 */
internal class FirebaseEconomyRepository(
    private val authRepository: AuthRepository,
    private val store: WalletStore,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val random: RandomGenerator,
    private val scope: CoroutineScope,
) : EconomyRepository {

    private val mutex = Mutex()
    private val progressEvents = MutableSharedFlow<ProgressEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: Flow<ProgressEvent> = progressEvents.asSharedFlow()
    private val state = MutableStateFlow<WalletState>(WalletState.Loading)
    override val wallet: StateFlow<WalletState> = state.asStateFlow()

    /** Jugadores cuyo monedero ya se intentó crear en esta sesión: un rechazo no se reintenta en bucle. */
    private val creationAttempted = mutableSetOf<String>()

    init {
        scope.launch {
            authRepository.authState
                .map { it.playerId to (it == AuthState.Loading) }
                .distinctUntilChanged()
                .collectLatest { (playerId, authLoading) ->
                    if (playerId == null) {
                        state.value = if (authLoading) WalletState.Loading else WalletState.Unavailable
                    } else {
                        state.value = WalletState.Loading
                        observeWallet(playerId)
                    }
                }
        }
    }

    override suspend fun placeBet(game: GameType, stake: Chips) = execute(EconomyOperation.PlaceBet(game, stake))

    override suspend fun settleRound(payout: Chips) = execute(EconomyOperation.SettleRound(payout))

    override suspend fun playInstantRound(game: GameType, stake: Chips, payout: Chips) =
        execute(EconomyOperation.InstantRound(game, stake, payout))

    override suspend fun claimRescue() = execute(EconomyOperation.ClaimRescue)

    override suspend fun claimDailyBonus() = execute(EconomyOperation.ClaimDailyBonus(clock.todayIn(timeZone)))

    override suspend fun claimAchievement(id: AchievementId) = execute(EconomyOperation.ClaimAchievement(id))

    private suspend fun observeWallet(playerId: String) {
        var seen = false
        store.observe(playerId)
            .map { it?.toWallet() }
            .catch { state.value = WalletState.Unavailable }
            .collect { wallet ->
                when {
                    wallet != null -> {
                        seen = true
                        state.value = WalletState.Ready(wallet)
                    }
                    // Desaparece uno que existía: la cuenta se está borrando. Nunca se recrea.
                    seen -> state.value = WalletState.Unavailable
                    else -> createWallet(playerId)
                }
            }
    }

    /** Primer acceso del jugador: monedero con las fichas de bienvenida. */
    private suspend fun createWallet(playerId: String) {
        if (!creationAttempted.add(playerId)) {
            state.value = WalletState.Unavailable
            return
        }
        mutex.withLock {
            val transition = WalletTransitions.open(random.nextId(), clock.now())
            persist(playerId, transition) ?: run { state.value = WalletState.Unavailable }
        }
    }

    private suspend fun execute(operation: EconomyOperation): Outcome<Wallet, EconomyError> = mutex.withLock {
        val playerId = authRepository.authState.value.playerId ?: return failure(EconomyError.WalletUnavailable)
        val current = readLocal(playerId) ?: return failure(EconomyError.WalletUnavailable)
        when (val result = WalletTransitions.apply(current, operation, random.nextId(), clock.now())) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                persist(playerId, result.value) ?: return failure(EconomyError.WalletUnavailable)
                result.value.events.forEach(progressEvents::tryEmit)
                Outcome.Success(result.value.wallet)
            }
        }
    }

    private suspend fun readLocal(playerId: String): Wallet? = try {
        store.readLocal(playerId)?.toWallet()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Escribe la transición (aplicada en local al volver) y sigue su envío al servidor en segundo plano. */
    private fun persist(playerId: String, transition: WalletTransition): PendingWrite? {
        val pending = try {
            store.write(WalletDocument.from(playerId, transition), LedgerEntryDocument.from(transition.entry))
        } catch (e: Exception) {
            return null
        }
        scope.launch {
            try {
                pending.awaitServer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Rechazo del servidor: Firestore ya ha deshecho el cambio local y el monedero
                // observado vuelve al último saldo válido. No hay nada más que reparar aquí.
            }
        }
        return pending
    }

    private companion object {
        const val EVENT_BUFFER = 16
    }
}
