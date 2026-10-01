package com.royalchance.data.firebase

import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.LedgerEntry
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.OpenRound
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletTransition
import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.PlayerProgress
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Documento `wallets/{uid}`. Cada cambio se escribe junto a su asiento (`wallets/{uid}/ledger/{id}`)
 * en un único lote, y las reglas de seguridad comprueban que el asiento justifica el cambio.
 *
 * @property seq número de movimientos: cada escritura debe incrementarlo exactamente en uno, así que
 *   una escritura basada en un saldo antiguo (otro dispositivo, por ejemplo) se rechaza.
 * @property lastEntryId asiento de la última escritura: une el monedero con su justificante.
 * @property lastDailyDay día del último bono diario (días desde 1970-01-01 en la zona del dispositivo).
 *
 * La progresión (Fase 6) va en el mismo documento para que una ronda siga siendo una única escritura.
 * Sus campos tienen valor por defecto: los monederos creados antes se leen sin problema.
 */
@Serializable
internal data class WalletDocument(
    val uid: String,
    val balance: Long,
    val seq: Long,
    val lastEntryId: String,
    val openRound: OpenRoundDocument? = null,
    val lastRescueAtMillis: Long? = null,
    val xp: Long = 0,
    val rounds: Long = 0,
    val wins: Long = 0,
    val losses: Long = 0,
    val pushes: Long = 0,
    val winStreak: Long = 0,
    val bestWinStreak: Long = 0,
    val highestBalance: Long = 0,
    val dailyStreak: Long = 0,
    val lastDailyDay: Long? = null,
    val lastDailyAtMillis: Long? = null,
    val unlocked: List<String> = emptyList(),
    val claimed: List<String> = emptyList(),
) {
    fun toWallet() = Wallet(
        balance = Chips(balance),
        sequence = seq,
        openRound = openRound?.toDomain(),
        lastRescueAt = lastRescueAtMillis?.let(Instant::fromEpochMilliseconds),
        progress = PlayerProgress(
            xp = xp,
            roundsPlayed = rounds,
            wins = wins,
            losses = losses,
            pushes = pushes,
            winStreak = winStreak,
            bestWinStreak = bestWinStreak,
            // Un monedero anterior a la Fase 6 no tiene máximo: como poco, es el saldo actual.
            highestBalance = Chips(maxOf(highestBalance, balance)),
            dailyStreak = dailyStreak,
            lastDailyDay = lastDailyDay?.let(LocalDate::fromEpochDays),
            lastDailyAt = lastDailyAtMillis?.let(Instant::fromEpochMilliseconds),
            unlocked = unlocked.toAchievements(),
            claimed = claimed.toAchievements(),
        ),
    )

    companion object {
        fun from(uid: String, transition: WalletTransition): WalletDocument {
            val wallet = transition.wallet
            val progress = wallet.progress
            return WalletDocument(
                uid = uid,
                balance = wallet.balance.amount,
                seq = wallet.sequence,
                lastEntryId = transition.entry.id,
                openRound = wallet.openRound?.let(OpenRoundDocument::from),
                lastRescueAtMillis = wallet.lastRescueAt?.toEpochMilliseconds(),
                xp = progress.xp,
                rounds = progress.roundsPlayed,
                wins = progress.wins,
                losses = progress.losses,
                pushes = progress.pushes,
                winStreak = progress.winStreak,
                bestWinStreak = progress.bestWinStreak,
                highestBalance = progress.highestBalance.amount,
                dailyStreak = progress.dailyStreak,
                lastDailyDay = progress.lastDailyDay?.toEpochDays()?.toLong(),
                lastDailyAtMillis = progress.lastDailyAt?.toEpochMilliseconds(),
                // Orden de catálogo: el documento no cambia si los logros no cambian.
                unlocked = progress.unlocked.sorted().map { it.name },
                claimed = progress.claimed.sorted().map { it.name },
            )
        }
    }
}

/** Ids desconocidos (de una versión más nueva de la app) se ignoran. */
private fun List<String>.toAchievements(): Set<AchievementId> =
    mapNotNull { name -> AchievementId.entries.firstOrNull { it.name == name } }.toSet()

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
    val achievementId: String? = null,
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
        achievementId = achievementId?.let(AchievementId::valueOf),
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
            achievementId = entry.achievementId?.name,
        )
    }
}
