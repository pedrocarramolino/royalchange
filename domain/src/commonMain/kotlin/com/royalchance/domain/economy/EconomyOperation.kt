package com.royalchance.domain.economy

import com.royalchance.domain.game.GameType
import kotlin.time.Instant

/** Operaciones con intención que admite el monedero. No existe "fijar el saldo". */
sealed interface EconomyOperation {

    /** Abre una ronda por turnos o añade fichas a la ronda abierta del mismo juego. */
    data class PlaceBet(val game: GameType, val stake: Chips) : EconomyOperation

    /** Cierra la ronda abierta. [payout] incluye la apuesta devuelta; 0 si se pierde. */
    data class SettleRound(val payout: Chips) : EconomyOperation

    /** Ronda instantánea: el resultado ya se conoce y se contabiliza de una vez, antes de animarlo. */
    data class InstantRound(val game: GameType, val stake: Chips, val payout: Chips) : EconomyOperation

    data object ClaimRescue : EconomyOperation
}

sealed interface EconomyError {
    /** Monedero aún sin cargar, sin sesión o sin acceso a los datos. */
    data object WalletUnavailable : EconomyError

    data object BelowMinimumBet : EconomyError

    /** La ronda superaría [EconomyRules.MAXIMUM_ROUND_STAKE]. */
    data object AboveMaximumStake : EconomyError

    data object InsufficientFunds : EconomyError

    /** Hay una ronda abierta de otro juego: debe liquidarse antes. */
    data class RoundInProgress(val game: GameType) : EconomyError

    data object NoOpenRound : EconomyError

    /** El pago supera el máximo posible para la apuesta: indica un error del juego. */
    data object PayoutTooHigh : EconomyError

    /** Hay fichas suficientes (o en juego): no procede la recarga. */
    data object RescueNotNeeded : EconomyError

    data class RescueCoolingDown(val availableAt: Instant) : EconomyError
}
