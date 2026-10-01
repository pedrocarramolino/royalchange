package com.royalchance.data.firebase

import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.LedgerEntry
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.OpenRound
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletTransition
import com.royalchance.domain.game.GameType
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Documento `wallets/{uid}`. Cada cambio se escribe junto a su asiento (`wallets/{uid}/ledger/{id}`)
 * en un único lote, y las reglas de seguridad comprueban que el asiento justifica el cambio.
 *
 * @property seq número de movimientos: cada escritura debe incrementarlo exactamente en uno, así que
 *   una escritura basada en un saldo antiguo (otro dispositivo, por ejemplo) se rechaza.
 * @property lastEntryId asiento de la última escritura: une el monedero con su justificante.
 */
@Serializable
internal data class WalletDocument(
    val uid: String,
    val balance: Long,
    val seq: Long,
    val lastEntryId: String,
    val openRound: OpenRoundDocument? = null,
    val lastRescueAtMillis: Long? = null,
) {
    fun toWallet() = Wallet(
        balance = Chips(balance),
        sequence = seq,
        openRound = openRound?.toDomain(),
        lastRescueAt = lastRescueAtMillis?.let(Instant::fromEpochMilliseconds),
    )

    companion object {
        fun from(uid: String, transition: WalletTransition): WalletDocument {
            val wallet = transition.wallet
            return WalletDocument(
                uid = uid,
                balance = wallet.balance.amount,
                seq = wallet.sequence,
                lastEntryId = transition.entry.id,
                openRound = wallet.openRound?.let(OpenRoundDocument::from),
                lastRescueAtMillis = wallet.lastRescueAt?.toEpochMilliseconds(),
            )
        }
    }
}

@Serializable
internal data class OpenRoundDocument(
    val id: String,
    val game: String,
    val stake: Long,
) {
    fun toDomain() = OpenRound(id = id, game = GameType.valueOf(game), stake = Chips(stake))

    companion object {
        fun from(round: OpenRound) = OpenRoundDocument(id = round.id, game = round.game.name, stake = round.stake.amount)
    }
}

/** Documento `wallets/{uid}/ledger/{id}`: asiento inmutable. Los nombres de [kind] y [game] son estables. */
@Serializable
internal data class LedgerEntryDocument(
    val id: String,
    val seq: Long,
    val kind: String,
    val amount: Long,
    val balanceAfter: Long,
    val createdAtMillis: Long,
    val game: String? = null,
    val roundId: String? = null,
    val stake: Long? = null,
    val payout: Long? = null,
) {
    fun toDomain() = LedgerEntry(
        id = id,
        sequence = seq,
        kind = LedgerEntryKind.valueOf(kind),
        amount = amount,
        balanceAfter = Chips(balanceAfter),
        createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
        game = game?.let(GameType::valueOf),
        roundId = roundId,
        stake = stake?.let(::Chips),
        payout = payout?.let(::Chips),
    )

    companion object {
        fun from(entry: LedgerEntry) = LedgerEntryDocument(
            id = entry.id,
            seq = entry.sequence,
            kind = entry.kind.name,
            amount = entry.amount,
            balanceAfter = entry.balanceAfter.amount,
            createdAtMillis = entry.createdAt.toEpochMilliseconds(),
            game = entry.game?.name,
            roundId = entry.roundId,
            stake = entry.stake?.amount,
            payout = entry.payout?.amount,
        )
    }
}
