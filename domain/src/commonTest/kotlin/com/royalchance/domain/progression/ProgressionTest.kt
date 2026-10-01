package com.royalchance.domain.progression

import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyOperation
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.economy.Wallet
import com.royalchance.domain.economy.WalletTransition
import com.royalchance.domain.economy.WalletTransitions
import com.royalchance.domain.game.GameType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ProgressionTest {

    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val today = LocalDate(2026, 10, 1)
    private var nextEntry = 0

    private fun Wallet.ok(operation: EconomyOperation, at: Instant = now): WalletTransition =
        assertIs<Outcome.Success<WalletTransition>>(WalletTransitions.apply(this, operation, "e${++nextEntry}", at)).value

    private fun Wallet.fails(operation: EconomyOperation, at: Instant = now): EconomyError =
        assertIs<Outcome.Failure<EconomyError>>(WalletTransitions.apply(this, operation, "e${++nextEntry}", at)).error

    private fun wallet(balance: Long = 10_000, progress: PlayerProgress = PlayerProgress(highestBalance = Chips(balance))) =
        Wallet(balance = Chips(balance), sequence = 3, progress = progress)

    private fun spin(stake: Long, payout: Long) = EconomyOperation.InstantRound(GameType.Roulette, Chips(stake), Chips(payout))

    // ── Niveles y experiencia ────────────────────────────────────────────────────────────────

    @Test
    fun levelThresholdsGrowQuadratically() {
        assertEquals(0, Levels.xpForLevel(1))
        assertEquals(50, Levels.xpForLevel(2))
        assertEquals(800, Levels.xpForLevel(5))
        assertEquals(4_050, Levels.xpForLevel(10))
        assertEquals(28_800, Levels.xpForLevel(25))
        assertFailsWith<IllegalArgumentException> { Levels.xpForLevel(0) }
    }

    @Test
    fun levelIsDerivedFromExperience() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(49))
        assertEquals(2, Levels.levelFor(50))
        assertEquals(9, Levels.levelFor(4_049))
        assertEquals(10, Levels.levelFor(4_050))
        assertEquals(Levels.MAX_LEVEL, Levels.levelFor(Long.MAX_VALUE / 2))
    }

    @Test
    fun levelProgressAndTitles() {
        val progress = Levels.progress(900)
        assertEquals(5, progress.level)
        assertEquals(100, progress.xpIntoLevel)
        assertEquals(450, progress.xpForNextLevel)
        assertEquals(PlayerTitle.Player, progress.title)

        assertEquals(PlayerTitle.Novice, Levels.titleFor(4))
        assertEquals(PlayerTitle.Vip, Levels.titleFor(24))
        assertEquals(PlayerTitle.HighRoller, Levels.titleFor(25))
        assertEquals(PlayerTitle.Legend, Levels.titleFor(99))
        assertEquals(1f, Levels.progress(Levels.xpForLevel(Levels.MAX_LEVEL)).fraction)
    }

    @Test
    fun experienceGrowsSlowlyWithTheStake() {
        assertEquals(10, ExperienceRules.xpForRound(Chips(10)))
        assertEquals(12, ExperienceRules.xpForRound(Chips(50)))
        assertEquals(14, ExperienceRules.xpForRound(Chips(499)))
        assertEquals(16, ExperienceRules.xpForRound(Chips(500)))
        assertEquals(18, ExperienceRules.xpForRound(Chips(1_000)))
        assertEquals(20, ExperienceRules.xpForRound(Chips(5_000)))
        assertEquals(22, ExperienceRules.xpForRound(Chips(100_000)))
    }

    // ── Rondas ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun aRoundUpdatesCountersStreaksAndExperience() {
        var current = wallet()
        current = current.ok(spin(stake = 100, payout = 200)).wallet
        current = current.ok(spin(stake = 100, payout = 200)).wallet
        current = current.ok(spin(stake = 100, payout = 100)).wallet // empate: la racha sigue
        current = current.ok(spin(stake = 100, payout = 200)).wallet

        with(current.progress) {
            assertEquals(4, roundsPlayed)
            assertEquals(3, wins)
            assertEquals(1, pushes)
            assertEquals(3, winStreak)
            assertEquals(56, xp)
        }

        val afterLoss = current.ok(spin(stake = 100, payout = 0)).wallet.progress
        assertEquals(0, afterLoss.winStreak)
        assertEquals(3, afterLoss.bestWinStreak)
        assertEquals(1, afterLoss.losses)
    }

    @Test
    fun aTurnBasedRoundCountsOnceWithAllItsBets() {
        val opened = wallet().ok(EconomyOperation.PlaceBet(GameType.Blackjack, Chips(500))).wallet
        val doubled = opened.ok(EconomyOperation.PlaceBet(GameType.Blackjack, Chips(500))).wallet
        assertEquals(0, doubled.progress.roundsPlayed)

        val settled = doubled.ok(EconomyOperation.SettleRound(Chips(2_000))).wallet.progress

        assertEquals(1, settled.roundsPlayed)
        assertEquals(1, settled.wins)
        // La experiencia se calcula sobre todo lo apostado en la ronda (1.000).
        assertEquals(18, settled.xp)
    }

    @Test
    fun levelUpAndAchievementsAreAnnounced() {
        val almost = wallet(progress = PlayerProgress(xp = 45, highestBalance = Chips(10_000)))

        val transition = almost.ok(spin(stake = 100, payout = 200))

        assertEquals(
            listOf(ProgressEvent.LevelUp(2), ProgressEvent.AchievementUnlocked(AchievementId.FirstWin)),
            transition.events,
        )
        assertTrue(AchievementId.FirstWin in transition.wallet.progress.unlocked)
    }

    @Test
    fun highestBalanceUnlocksBalanceAchievements() {
        val transition = wallet(balance = 49_000).ok(spin(stake = 1_000, payout = 3_000))

        assertEquals(Chips(51_000), transition.wallet.progress.highestBalance)
        assertTrue(ProgressEvent.AchievementUnlocked(AchievementId.Balance50k) in transition.events)
    }

    // ── Logros ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun achievementRewardsAreClaimedOnce() {
        val unlocked = wallet().ok(spin(stake = 100, payout = 200)).wallet
        assertEquals(setOf(AchievementId.FirstWin), unlocked.progress.claimable)

        val (claimed, entry) = unlocked.ok(EconomyOperation.ClaimAchievement(AchievementId.FirstWin))

        assertEquals(unlocked.balance + Chips(250), claimed.balance)
        assertEquals(LedgerEntryKind.AchievementReward, entry.kind)
        assertEquals(AchievementId.FirstWin, entry.achievementId)
        assertEquals(emptySet(), claimed.progress.claimable)
        assertEquals(EconomyError.AchievementAlreadyClaimed, claimed.fails(EconomyOperation.ClaimAchievement(AchievementId.FirstWin)))
        assertEquals(EconomyError.AchievementLocked, claimed.fails(EconomyOperation.ClaimAchievement(AchievementId.Rounds1000)))
    }

    @Test
    fun conditionsReportTheirProgress() {
        val progress = PlayerProgress(roundsPlayed = 42, bestWinStreak = 3)

        assertEquals(42L to 100L, Achievements.of(AchievementId.Rounds100).condition.progressOf(progress))
        assertEquals(3L to 5L, Achievements.of(AchievementId.WinStreak5).condition.progressOf(progress))
        assertEquals(Achievements.catalog.size, AchievementId.entries.size)
    }

    // ── Bono diario ──────────────────────────────────────────────────────────────────────────

    @Test
    fun dailyBonusRewardGrowsUntilDaySeven() {
        assertEquals(listOf(500L, 700L, 900L, 1_100L, 1_300L, 1_500L, 1_700L, 1_700L), (1L..8L).map { DailyBonusRules.rewardFor(it).amount })
    }

    @Test
    fun consecutiveDaysBuildTheStreakAndMissingOneResetsIt() {
        val day1 = wallet().ok(EconomyOperation.ClaimDailyBonus(today)).wallet
        assertEquals(Chips(10_500), day1.balance)
        assertEquals(1, day1.progress.dailyStreak)

        val day2 = day1.ok(EconomyOperation.ClaimDailyBonus(LocalDate(2026, 10, 2)), at = now + 24.hours).wallet
        assertEquals(2, day2.progress.dailyStreak)
        assertEquals(Chips(11_200), day2.balance)

        val afterGap = day2.ok(EconomyOperation.ClaimDailyBonus(LocalDate(2026, 10, 4)), at = now + 72.hours).wallet
        assertEquals(1, afterGap.progress.dailyStreak)
    }

    @Test
    fun dailyBonusIsOncePerDayAndBlockedIfTheClockGoesBack() {
        val claimed = wallet().ok(EconomyOperation.ClaimDailyBonus(today)).wallet

        assertEquals(EconomyError.DailyBonusAlreadyClaimed, claimed.fails(EconomyOperation.ClaimDailyBonus(today), at = now + 1.hours))
        assertEquals(
            EconomyError.DailyBonusClockMovedBack,
            claimed.fails(EconomyOperation.ClaimDailyBonus(LocalDate(2026, 10, 2)), at = now - 1.hours),
        )
        assertIs<DailyBonusStatus.ClaimedToday>(claimed.progress.dailyBonusStatus(today, now))
        assertEquals(
            DailyBonusStatus.Available(streakDay = 2, reward = Chips(700)),
            claimed.progress.dailyBonusStatus(LocalDate(2026, 10, 2), now + 20.hours),
        )
    }

    @Test
    fun aWeekOfDailyBonusesUnlocksItsAchievement() {
        val sixDays = wallet(progress = PlayerProgress(dailyStreak = 6, lastDailyDay = LocalDate(2026, 9, 30), highestBalance = Chips(10_000)))

        val transition = sixDays.ok(EconomyOperation.ClaimDailyBonus(today))

        assertEquals(Chips(11_700), transition.wallet.balance)
        assertEquals(listOf(ProgressEvent.AchievementUnlocked(AchievementId.DailyStreak7)), transition.events)
    }
}
