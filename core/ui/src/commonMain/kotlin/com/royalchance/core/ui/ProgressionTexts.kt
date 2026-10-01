package com.royalchance.core.ui

import androidx.compose.runtime.Composable
import com.royalchance.core.ui.resources.Res
import com.royalchance.core.ui.resources.achievement_balance_250k
import com.royalchance.core.ui.resources.achievement_balance_250k_description
import com.royalchance.core.ui.resources.achievement_balance_50k
import com.royalchance.core.ui.resources.achievement_balance_50k_description
import com.royalchance.core.ui.resources.achievement_daily_streak_30
import com.royalchance.core.ui.resources.achievement_daily_streak_30_description
import com.royalchance.core.ui.resources.achievement_daily_streak_7
import com.royalchance.core.ui.resources.achievement_daily_streak_7_description
import com.royalchance.core.ui.resources.achievement_first_win
import com.royalchance.core.ui.resources.achievement_first_win_description
import com.royalchance.core.ui.resources.achievement_level_10
import com.royalchance.core.ui.resources.achievement_level_10_description
import com.royalchance.core.ui.resources.achievement_level_25
import com.royalchance.core.ui.resources.achievement_level_25_description
import com.royalchance.core.ui.resources.achievement_level_5
import com.royalchance.core.ui.resources.achievement_level_5_description
import com.royalchance.core.ui.resources.achievement_rounds_10
import com.royalchance.core.ui.resources.achievement_rounds_1000
import com.royalchance.core.ui.resources.achievement_rounds_1000_description
import com.royalchance.core.ui.resources.achievement_rounds_100
import com.royalchance.core.ui.resources.achievement_rounds_100_description
import com.royalchance.core.ui.resources.achievement_rounds_10_description
import com.royalchance.core.ui.resources.achievement_win_streak_10
import com.royalchance.core.ui.resources.achievement_win_streak_10_description
import com.royalchance.core.ui.resources.achievement_win_streak_5
import com.royalchance.core.ui.resources.achievement_win_streak_5_description
import com.royalchance.core.ui.resources.achievement_wins_50
import com.royalchance.core.ui.resources.achievement_wins_50_description
import com.royalchance.core.ui.resources.level_label
import com.royalchance.core.ui.resources.title_high_roller
import com.royalchance.core.ui.resources.title_legend
import com.royalchance.core.ui.resources.title_novice
import com.royalchance.core.ui.resources.title_player
import com.royalchance.core.ui.resources.title_vip
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.PlayerTitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Textos de la progresión compartidos por el lobby, la pestaña de progreso y los avisos globales.

@Composable
fun levelLabel(level: Int): String = stringResource(Res.string.level_label, level)

@Composable
fun titleName(title: PlayerTitle): String = stringResource(
    when (title) {
        PlayerTitle.Novice -> Res.string.title_novice
        PlayerTitle.Player -> Res.string.title_player
        PlayerTitle.Vip -> Res.string.title_vip
        PlayerTitle.HighRoller -> Res.string.title_high_roller
        PlayerTitle.Legend -> Res.string.title_legend
    },
)

@Composable
fun achievementTitle(id: AchievementId): String = stringResource(id.texts.first)

@Composable
fun achievementDescription(id: AchievementId): String = stringResource(id.texts.second)

private val AchievementId.texts: Pair<StringResource, StringResource>
    get() = when (this) {
        AchievementId.FirstWin -> Res.string.achievement_first_win to Res.string.achievement_first_win_description
        AchievementId.Rounds10 -> Res.string.achievement_rounds_10 to Res.string.achievement_rounds_10_description
        AchievementId.Rounds100 -> Res.string.achievement_rounds_100 to Res.string.achievement_rounds_100_description
        AchievementId.Rounds1000 -> Res.string.achievement_rounds_1000 to Res.string.achievement_rounds_1000_description
        AchievementId.Wins50 -> Res.string.achievement_wins_50 to Res.string.achievement_wins_50_description
        AchievementId.WinStreak5 -> Res.string.achievement_win_streak_5 to Res.string.achievement_win_streak_5_description
        AchievementId.WinStreak10 -> Res.string.achievement_win_streak_10 to Res.string.achievement_win_streak_10_description
        AchievementId.Level5 -> Res.string.achievement_level_5 to Res.string.achievement_level_5_description
        AchievementId.Level10 -> Res.string.achievement_level_10 to Res.string.achievement_level_10_description
        AchievementId.Level25 -> Res.string.achievement_level_25 to Res.string.achievement_level_25_description
        AchievementId.Balance50k -> Res.string.achievement_balance_50k to Res.string.achievement_balance_50k_description
        AchievementId.Balance250k -> Res.string.achievement_balance_250k to Res.string.achievement_balance_250k_description
        AchievementId.DailyStreak7 -> Res.string.achievement_daily_streak_7 to Res.string.achievement_daily_streak_7_description
        AchievementId.DailyStreak30 -> Res.string.achievement_daily_streak_30 to Res.string.achievement_daily_streak_30_description
    }
