package com.royalchance.domain.economy

import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import kotlin.time.Instant

/**
 * Asiento contable: registro inmutable de un movimiento de fichas. La suma de todos los asientos
 * de un jugador es su saldo, y cada asiento guarda además el saldo resultante.
 *
 * @property amount variación del saldo: positiva si entran fichas y negativa si salen.
 * @property stake fichas apostadas en esta operación ([LedgerEntryKind.Bet] y [LedgerEntryKind.InstantRound]).
 * @property payout fichas cobradas, apuesta devuelta incluida ([LedgerEntryKind.Settlement] y [LedgerEntryKind.InstantRound]).
 * @property achievementId logro cuya recompensa se cobra ([LedgerEntryKind.AchievementReward]).
 */
data class LedgerEntry(
    val id: String,
    val sequence: Long,
    val kind: LedgerEntryKind,
    val amount: Long,
    val balanceAfter: Chips,
    val createdAt: Instant,
    val game: GameType? = null,
    val roundId: String? = null,
    val stake: Chips? = null,
    val payout: Chips? = null,
    val achievementId: AchievementId? = null,
)

/** Tipos de asiento. Los nombres son estables: se guardan en la base de datos y las reglas los validan. */
enum class LedgerEntryKind {
    /** Fichas de bienvenida al crear el monedero. */
    Welcome,

    /** Apuesta de una ronda por turnos (abre la ronda o añade fichas: doblar, separar). */
    Bet,

    /** Liquidación de la ronda abierta. */
    Settlement,

    /** Ronda instantánea (ruleta, slots, dados): apuesta y pago en un único asiento. */
    InstantRound,

    /** Recarga gratuita por quedarse sin fichas. */
    Rescue,

    /** Bono diario. */
    DailyBonus,

    /** Recompensa de un logro desbloqueado. */
    AchievementReward,
}
