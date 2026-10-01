package com.royalchance.domain.economy

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.common.result.failure
import com.royalchance.domain.economy.EconomyRules.MAXIMUM_PAYOUT_MULTIPLIER
import com.royalchance.domain.economy.EconomyRules.MAXIMUM_ROUND_STAKE
import com.royalchance.domain.economy.EconomyRules.MINIMUM_BET
import com.royalchance.domain.game.GameType
import kotlin.time.Instant

/** Resultado de una operación: el monedero nuevo y el asiento que lo justifica. */
data class WalletTransition(val wallet: Wallet, val entry: LedgerEntry)

/**
 * Lógica pura del monedero, común a todas las implementaciones de [EconomyRepository]. Las reglas
 * de seguridad de Firestore comprueban lo mismo en el servidor.
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
                    entryId = entryId,
                    kind = LedgerEntryKind.Bet,
                    now = now,
                    game = operation.game,
                    roundId = roundId,
                    stake = operation.stake,
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
            updated = copy(balance = balance + operation.payout, openRound = null),
            entryId = entryId,
            kind = LedgerEntryKind.Settlement,
            now = now,
            game = round.game,
            roundId = round.id,
            payout = operation.payout,
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
            updated = copy(balance = balance - operation.stake + operation.payout),
            entryId = entryId,
            kind = LedgerEntryKind.InstantRound,
            now = now,
            game = operation.game,
            roundId = entryId,
            stake = operation.stake,
            payout = operation.payout,
        )
    }

    private fun Wallet.claimRescue(entryId: String, now: Instant): Outcome<WalletTransition, EconomyError> =
        when (val status = rescueStatus(now)) {
            RescueStatus.NotNeeded -> failure(EconomyError.RescueNotNeeded)
            is RescueStatus.CoolingDown -> failure(EconomyError.RescueCoolingDown(status.availableAt))
            RescueStatus.Available -> record(
                updated = copy(balance = balance + EconomyRules.RESCUE_GRANT, lastRescueAt = now),
                entryId = entryId,
                kind = LedgerEntryKind.Rescue,
                now = now,
            )
        }

    /** Numera el movimiento y crea su asiento con la variación de saldo exacta. */
    private fun Wallet.record(
        updated: Wallet,
        entryId: String,
        kind: LedgerEntryKind,
        now: Instant,
        game: GameType? = null,
        roundId: String? = null,
        stake: Chips? = null,
        payout: Chips? = null,
    ): Outcome<WalletTransition, Nothing> {
        val next = updated.copy(sequence = sequence + 1)
        return Outcome.Success(
            WalletTransition(
                wallet = next,
                entry = LedgerEntry(
                    id = entryId,
                    sequence = next.sequence,
                    kind = kind,
                    amount = next.balance.amount - balance.amount,
                    balanceAfter = next.balance,
                    createdAt = now,
                    game = game,
                    roundId = roundId,
                    stake = stake,
                    payout = payout,
                ),
            ),
        )
    }
}
