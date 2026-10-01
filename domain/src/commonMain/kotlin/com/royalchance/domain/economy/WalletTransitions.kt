package com.royalchance.domain.economy

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.domain.economy.EconomyRules.MAXIMUM_PAYOUT_MULTIPLIER
import com.royalchance.domain.economy.EconomyRules.MAXIMUM_ROUND_STAKE
import com.royalchance.domain.economy.EconomyRules.MINIMUM_BET
import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.Achievements
import com.royalchance.domain.progression.DailyBonusStatus
import com.royalchance.domain.progression.ProgressEvent
import com.royalchance.domain.progression.afterRound
import com.royalchance.domain.progression.dailyBonusStatus
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Resultado de una operación: el monedero nuevo, el asiento que lo justifica y los avisos para la
 * interfaz (subida de nivel, logros desbloqueados).
 */
data class WalletTransition(
    val wallet: Wallet,
    val entry: LedgerEntry,
    val events: List<ProgressEvent> = emptyList(),
)

/**
 * Lógica pura del monedero y de la progresión, común a todas las implementaciones de
 * [EconomyRepository]. Las reglas de seguridad de Firestore comprueban lo mismo en el servidor.
 */
object WalletTransitions {

    /** Monedero nuevo con las fichas de bienvenida. */
    fun open(entryId: String, now: Instant): WalletTransition {
        val grant = EconomyRules.WELCOME_GRANT
        return WalletTransition(
            wallet = Wallet(balance = grant, sequence = 1),
            entry = LedgerEntry(
                id = entryId,
                sequence = 1,
                kind = LedgerEntryKind.Welcome,
                amount = grant.amount,
                balanceAfter = grant,
                createdAt = now,
            ),
        )
    }

    /**
     * Aplica [operation] a [wallet] o explica por qué no es posible. Nunca deja el saldo en negativo.
     *
     * @param entryId id del asiento nuevo; también identifica la ronda que abra.
     */
    fun apply(
        wallet: Wallet,
        operation: EconomyOperation,
        entryId: String,
        now: Instant,
    ): Outcome<WalletTransition, EconomyError> = when (operation) {
        is EconomyOperation.PlaceBet -> wallet.placeBet(operation, entryId, now)
        is EconomyOperation.SettleRound -> wallet.settleRound(operation, entryId, now)
        is EconomyOperation.InstantRound -> wallet.instantRound(operation, entryId, now)
        EconomyOperation.ClaimRescue -> wallet.claimRescue(entryId, now)
        is EconomyOperation.ClaimDailyBonus -> wallet.claimDailyBonus(operation.today, entryId, now)
        is EconomyOperation.ClaimAchievement -> wallet.claimAchievement(operation.id, entryId, now)
    }

    private fun Wallet.placeBet(
        operation: EconomyOperation.PlaceBet,
        entryId: String,
        now: Instant,
    ): Outcome<WalletTransition, EconomyError> {
        val round = openRound
        val roundStake = (round?.stake ?: Chips.ZERO) + operation.stake
        return when {
            operation.stake < MINIMUM_BET -> failure(EconomyError.BelowMinimumBet)
            round != null && round.game != operation.game -> failure(EconomyError.RoundInProgress(round.game))
            roundStake > MAXIMUM_ROUND_STAKE -> failure(EconomyError.AboveMaximumStake)
            operation.stake > balance -> failure(EconomyError.InsufficientFunds)
            else -> {
                val roundId = round?.id ?: entryId
                record(
                    updated = copy(
                        balance = balance - operation.stake,
                        openRound = OpenRound(roundId, operation.game, roundStake),
                    ),
                    entry = Draft(entryId, LedgerEntryKind.Bet, now, game = operation.game, roundId = roundId, stake = operation.stake),
                )
            }
        }
    }

    private fun Wallet.settleRound(
        operation: EconomyOperation.SettleRound,
        entryId: String,
        now: Instant,
    ): Outcome<WalletTransition, EconomyError> {
        val round = openRound ?: return failure(EconomyError.NoOpenRound)
        if (operation.payout > round.stake * MAXIMUM_PAYOUT_MULTIPLIER) return failure(EconomyError.PayoutTooHigh)
        return record(
            updated = copy(
                balance = balance + operation.payout,
                openRound = null,
                // La ronda cuenta al cerrarse, con todas sus apuestas (dobles y separaciones incluidas).
                progress = progress.afterRound(stake = round.stake, payout = operation.payout),
            ),
            entry = Draft(entryId, LedgerEntryKind.Settlement, now, game = round.game, roundId = round.id, payout = operation.payout),
        )
    }

    private fun Wallet.instantRound(
        operation: EconomyOperation.InstantRound,
        entryId: String,
        now: Instant,
    ): Outcome<WalletTransition, EconomyError> = when {
        operation.stake < MINIMUM_BET -> failure(EconomyError.BelowMinimumBet)
        operation.stake > MAXIMUM_ROUND_STAKE -> failure(EconomyError.AboveMaximumStake)
        operation.stake > balance -> failure(EconomyError.InsufficientFunds)
        operation.payout > operation.stake * MAXIMUM_PAYOUT_MULTIPLIER -> failure(EconomyError.PayoutTooHigh)
        else -> record(
            // Una ronda abierta de otro juego no se toca: sus fichas siguen en la mesa.
            updated = copy(
                balance = balance - operation.stake + operation.payout,
                progress = progress.afterRound(stake = operation.stake, payout = operation.payout),
            ),
            entry = Draft(
                entryId,
                LedgerEntryKind.InstantRound,
                now,
                game = operation.game,
                roundId = entryId,
                stake = operation.stake,
                payout = operation.payout,
            ),
        )
    }

    private fun Wallet.claimRescue(entryId: String, now: Instant): Outcome<WalletTransition, EconomyError> =
        when (val status = rescueStatus(now)) {
            RescueStatus.NotNeeded -> failure(EconomyError.RescueNotNeeded)
            is RescueStatus.CoolingDown -> failure(EconomyError.RescueCoolingDown(status.availableAt))
            RescueStatus.Available -> record(
                updated = copy(balance = balance + EconomyRules.RESCUE_GRANT, lastRescueAt = now),
                entry = Draft(entryId, LedgerEntryKind.Rescue, now),
            )
        }

    private fun Wallet.claimDailyBonus(today: LocalDate, entryId: String, now: Instant): Outcome<WalletTransition, EconomyError> =
        when (val status = progress.dailyBonusStatus(today, now)) {
            is DailyBonusStatus.ClaimedToday -> failure(EconomyError.DailyBonusAlreadyClaimed)
            DailyBonusStatus.ClockMovedBack -> failure(EconomyError.DailyBonusClockMovedBack)
            is DailyBonusStatus.Available -> record(
                updated = copy(
                    balance = balance + status.reward,
                    progress = progress.copy(dailyStreak = status.streakDay, lastDailyDay = today, lastDailyAt = now),
                ),
                entry = Draft(entryId, LedgerEntryKind.DailyBonus, now),
            )
        }

    private fun Wallet.claimAchievement(id: AchievementId, entryId: String, now: Instant): Outcome<WalletTransition, EconomyError> =
        when (id) {
            in progress.claimed -> failure(EconomyError.AchievementAlreadyClaimed)
            !in progress.unlocked -> failure(EconomyError.AchievementLocked)
            else -> record(
                updated = copy(
                    balance = balance + Achievements.of(id).reward,
                    progress = progress.copy(claimed = progress.claimed + id),
                ),
                entry = Draft(entryId, LedgerEntryKind.AchievementReward, now, achievementId = id),
            )
        }

    /** Datos del asiento que no dependen del saldo resultante. */
    private class Draft(
        val id: String,
        val kind: LedgerEntryKind,
        val createdAt: Instant,
        val game: GameType? = null,
        val roundId: String? = null,
        val stake: Chips? = null,
        val payout: Chips? = null,
        val achievementId: AchievementId? = null,
    )

    /**
     * Cierra cualquier operación: numera el movimiento, actualiza el saldo máximo, desbloquea los
     * logros que se cumplan ahora y crea el asiento con la variación de saldo exacta.
     */
    private fun Wallet.record(updated: Wallet, entry: Draft): Outcome<WalletTransition, Nothing> {
        val withHighest = updated.progress.copy(highestBalance = maxOf(updated.progress.highestBalance, updated.balance))
        val unlockedNow = Achievements.newlyUnlocked(withHighest)
        val next = updated.copy(
            sequence = sequence + 1,
            progress = withHighest.copy(unlocked = withHighest.unlocked + unlockedNow),
        )
        val events = buildList {
            if (next.progress.level > progress.level) add(ProgressEvent.LevelUp(next.progress.level))
            unlockedNow.forEach { add(ProgressEvent.AchievementUnlocked(it)) }
        }
        return Outcome.Success(
            WalletTransition(
                wallet = next,
                entry = LedgerEntry(
                    id = entry.id,
                    sequence = next.sequence,
                    kind = entry.kind,
                    amount = next.balance.amount - balance.amount,
                    balanceAfter = next.balance,
                    createdAt = entry.createdAt,
                    game = entry.game,
                    roundId = entry.roundId,
                    stake = entry.stake,
                    payout = entry.payout,
                    achievementId = entry.achievementId,
                ),
                events = events,
            ),
        )
    }
}
