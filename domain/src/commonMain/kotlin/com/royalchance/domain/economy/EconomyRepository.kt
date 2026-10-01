package com.royalchance.domain.economy

import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.game.GameType
import kotlinx.coroutines.flow.StateFlow

/**
 * Único punto que mueve fichas. Los juegos y las pantallas piden operaciones con intención; nadie
 * fija el saldo directamente. Cada operación se valida con [WalletTransitions], se aplica en orden
 * (nunca dos a la vez) y deja un asiento contable.
 *
 * El monedero se crea solo, con las fichas de bienvenida, la primera vez que el jugador entra con
 * su perfil completo.
 */
interface EconomyRepository {

    val wallet: StateFlow<WalletState>

    /** Abre una ronda por turnos (Blackjack) o añade fichas a la ronda abierta: doblar, separar. */
    suspend fun placeBet(game: GameType, stake: Chips): Outcome<Wallet, EconomyError>

    /** Liquida la ronda abierta. [payout] incluye la apuesta devuelta; 0 si se pierde. */
    suspend fun settleRound(payout: Chips): Outcome<Wallet, EconomyError>

    /**
     * Ronda instantánea (ruleta, slots, dados): apuesta y pago en una sola operación. Se llama con el
     * resultado ya decidido por el motor y antes de animarlo, así que cerrar la app a mitad de la
     * animación no deshace nada.
     */
    suspend fun playInstantRound(game: GameType, stake: Chips, payout: Chips): Outcome<Wallet, EconomyError>

    /** Recarga gratuita cuando el saldo no llega a la apuesta mínima (ver [rescueStatus]). */
    suspend fun claimRescue(): Outcome<Wallet, EconomyError>
}
