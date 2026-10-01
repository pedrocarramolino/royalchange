package com.royalchance.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyError
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.Achievements
import com.royalchance.domain.progression.LevelProgress
import com.royalchance.domain.progression.PlayerProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

sealed interface AchievementState {
    data class Locked(val current: Long, val target: Long) : AchievementState {
        val fraction: Float get() = if (target == 0L) 1f else (current.toDouble() / target).toFloat().coerceIn(0f, 1f)
    }

    data object Claimable : AchievementState

    data object Claimed : AchievementState
}

data class AchievementUi(val id: AchievementId, val reward: Chips, val state: AchievementState)

data class PlayerStats(
    val roundsPlayed: Long,
    val wins: Long,
    val losses: Long,
    val pushes: Long,
    val bestWinStreak: Long,
    val highestBalance: Chips,
    /** Racha de bono diario aún viva: 0 si ya se ha roto (no se cobró ayer ni hoy). */
    val dailyStreak: Long,
)

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data object Unavailable : ProgressUiState

    data class Ready(
        val level: LevelProgress,
        val stats: PlayerStats,
        /** Primero lo que se puede recoger, luego lo pendiente y al final lo ya cobrado. */
        val achievements: List<AchievementUi>,
        val unlockedCount: Int,
        val claiming: AchievementId? = null,
        val claimFailed: Boolean = false,
    ) : ProgressUiState
}

class ProgressViewModel(
    private val economyRepository: EconomyRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<ProgressUiState> = combine(economyRepository.wallet, local) { wallet, local ->
        when (wallet) {
            WalletState.Loading -> ProgressUiState.Loading
            WalletState.Unavailable -> ProgressUiState.Unavailable
            is WalletState.Ready -> wallet.wallet.progress.toUiState(local)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    fun claim(id: AchievementId) {
        if (local.value.claiming != null) return
        local.update { it.copy(claiming = id, claimFailed = false) }
        viewModelScope.launch {
            val result = economyRepository.claimAchievement(id)
            // Ya cobrado (desde otro dispositivo, por ejemplo): el estado lo refleja, no es un error.
            val failed = result is Outcome.Failure && result.error != EconomyError.AchievementAlreadyClaimed
            local.update { it.copy(claiming = null, claimFailed = failed) }
        }
    }

    private fun PlayerProgress.toUiState(local: LocalState): ProgressUiState.Ready {
        val today = clock.todayIn(timeZone)
        val lastDay = lastDailyDay
        val streakAlive = lastDay != null && lastDay >= today.minus(1, DateTimeUnit.DAY)
        val achievements = Achievements.catalog.map { achievement ->
            val state = when (achievement.id) {
                in claimed -> AchievementState.Claimed
                in unlocked -> AchievementState.Claimable
                else -> achievement.condition.progressOf(this).let { (current, target) -> AchievementState.Locked(current, target) }
            }
            AchievementUi(achievement.id, achievement.reward, state)
        }
        return ProgressUiState.Ready(
            level = levelProgress,
            stats = PlayerStats(
                roundsPlayed = roundsPlayed,
                wins = wins,
                losses = losses,
                pushes = pushes,
                bestWinStreak = bestWinStreak,
                highestBalance = highestBalance,
                dailyStreak = if (streakAlive) dailyStreak else 0,
            ),
            achievements = achievements.sortedBy { it.state.order },
            unlockedCount = unlocked.size,
            claiming = local.claiming,
            claimFailed = local.claimFailed,
        )
    }

    private val AchievementState.order: Int
        get() = when (this) {
            AchievementState.Claimable -> 0
            is AchievementState.Locked -> 1
            AchievementState.Claimed -> 2
        }

    private data class LocalState(val claiming: AchievementId? = null, val claimFailed: Boolean = false)
}
