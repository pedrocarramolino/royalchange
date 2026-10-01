package com.royalchance.domain.economy

import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.PlayerProgress
import kotlin.time.Instant

/**
 * Monedero del jugador. Solo cambia mediante las operaciones de [EconomyRepository], y cada cambio
 * deja un [LedgerEntry].
 *
 * @property balance fichas disponibles. Las apostadas en una ronda abierta ya no cuentan.
 * @property sequence número de movimientos registrados (el asiento de bienvenida es el 1).
 * @property progress experiencia, contadores, rachas y logros: cambian con las mismas operaciones
 *   que el saldo y se guardan con él, en una única escritura.
 */
data class Wallet(
    val balance: Chips,
    val sequence: Long,
    val openRound: OpenRound? = null,
    val lastRescueAt: Instant? = null,
    val progress: PlayerProgress = PlayerProgress(highestBalance = balance),
)

/**
 * Ronda de un juego por turnos (Blackjack) con fichas en la mesa: se abrió con una apuesta y aún
 * no se ha liquidado. Solo puede haber una a la vez.
 */
data class OpenRound(
    val id: String,
    val game: GameType,
    val stake: Chips,
)

sealed interface WalletState {
    data object Loading : WalletState

    data class Ready(val wallet: Wallet) : WalletState

    /** Sin sesión, sin perfil o sin acceso a los datos. */
    data object Unavailable : WalletState
}

/** Situación de la recarga gratuita. */
sealed interface RescueStatus {
    /** Hay fichas suficientes para apostar, o fichas en juego en una ronda abierta. */
    data object NotNeeded : RescueStatus

    data object Available : RescueStatus

    data class CoolingDown(val availableAt: Instant) : RescueStatus
}

fun Wallet.rescueStatus(now: Instant): RescueStatus {
    if (balance >= EconomyRules.MINIMUM_BET || openRound != null) return RescueStatus.NotNeeded
    val availableAt = lastRescueAt?.plus(EconomyRules.RESCUE_COOLDOWN)
    return if (availableAt == null || now >= availableAt) RescueStatus.Available else RescueStatus.CoolingDown(availableAt)
}
