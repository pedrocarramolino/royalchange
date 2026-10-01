package com.royalchance.feature.profile

import com.royalchance.core.testing.time.TestClock
import com.royalchance.data.auth.InMemoryAuthRepository
import com.royalchance.data.economy.InMemoryEconomyRepository
import com.royalchance.domain.auth.AvatarId
import com.royalchance.domain.auth.LegalConsents
import com.royalchance.domain.auth.NewAccount
import com.royalchance.domain.auth.PlayerProfile
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.game.GameType
import com.royalchance.domain.progression.AchievementId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = TestClock("2026-10-01T10:00:00Z")
    private val auth = InMemoryAuthRepository(simulatedLatency = Duration.ZERO)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.(InMemoryEconomyRepository, ProgressViewModel) -> Unit) = runTest(dispatcher) {
        val economy = InMemoryEconomyRepository(auth, backgroundScope, clock, TimeZone.UTC)
        val profile = PlayerProfile("Ana", AvatarId.SpadeGold, "ES", 1990, false, LegalConsents("t", "p", clock.now()))
        auth.register(NewAccount("ana@example.com", "Secreto123", profile))
        val viewModel = ProgressViewModel(economy, clock, TimeZone.UTC)
        backgroundScope.launch { viewModel.state.collect {} }
        block(economy, viewModel)
    }

    private fun ProgressViewModel.ready(): ProgressUiState.Ready = assertIs<ProgressUiState.Ready>(state.value)

    @Test
    fun aNewPlayerSeesEveryAchievementLockedWithItsProgress() = test { _, viewModel ->
        val state = viewModel.ready()

        assertEquals(1, state.level.level)
        assertEquals(0, state.unlockedCount)
        assertEquals(AchievementId.entries.size, state.achievements.size)
        val rounds = state.achievements.first { it.id == AchievementId.Rounds10 }
        assertEquals(AchievementState.Locked(current = 0, target = 10), rounds.state)
    }

    @Test
    fun unlockedRewardsComeFirstAndCanBeClaimedOnce() = test { economy, viewModel ->
        economy.playInstantRound(GameType.Roulette, Chips(100), Chips(200))
        assertEquals(AchievementUi(AchievementId.FirstWin, Chips(250), AchievementState.Claimable), viewModel.ready().achievements.first())

        viewModel.claim(AchievementId.FirstWin)

        val state = viewModel.ready()
        assertEquals(AchievementState.Claimed, state.achievements.last().state)
        assertEquals(AchievementId.FirstWin, state.achievements.last().id)
        assertEquals(1, state.stats.wins)
        assertEquals(Chips(10_350), state.stats.highestBalance)
    }

    @Test
    fun aBrokenDailyStreakShowsAsZero() = test { economy, viewModel ->
        economy.claimDailyBonus()
        assertEquals(1, viewModel.ready().stats.dailyStreak)

        clock.advanceBy(72.hours)
        // Cualquier cambio del monedero recalcula el estado con el día actual.
        economy.playInstantRound(GameType.Dice, Chips(10), Chips.ZERO)

        assertEquals(0, viewModel.ready().stats.dailyStreak)
    }
}
