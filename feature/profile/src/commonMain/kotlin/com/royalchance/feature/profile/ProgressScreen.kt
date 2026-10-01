package com.royalchance.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.EmptyState
import com.royalchance.core.designsystem.component.FullScreenLoading
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalProgressBar
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.component.SectionHeader
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.designsystem.theme.tabularNumbers
import com.royalchance.core.ui.achievementDescription
import com.royalchance.core.ui.achievementTitle
import com.royalchance.core.ui.chipsText
import com.royalchance.core.ui.formatted
import com.royalchance.core.ui.levelLabel
import com.royalchance.core.ui.titleName
import com.royalchance.domain.progression.AchievementId
import com.royalchance.domain.progression.LevelProgress
import com.royalchance.feature.profile.resources.Res
import com.royalchance.feature.profile.resources.progress_achievement_claim
import com.royalchance.feature.profile.resources.progress_achievement_claimed
import com.royalchance.feature.profile.resources.progress_achievement_progress
import com.royalchance.feature.profile.resources.progress_achievement_reward
import com.royalchance.feature.profile.resources.progress_achievements
import com.royalchance.feature.profile.resources.progress_claim_failed
import com.royalchance.feature.profile.resources.progress_level_max
import com.royalchance.feature.profile.resources.progress_level_next
import com.royalchance.feature.profile.resources.progress_level_xp
import com.royalchance.feature.profile.resources.progress_stat_best_streak
import com.royalchance.feature.profile.resources.progress_stat_daily_streak
import com.royalchance.feature.profile.resources.progress_stat_highest_balance
import com.royalchance.feature.profile.resources.progress_stat_losses
import com.royalchance.feature.profile.resources.progress_stat_pushes
import com.royalchance.feature.profile.resources.progress_stat_rounds
import com.royalchance.feature.profile.resources.progress_stat_wins
import com.royalchance.feature.profile.resources.progress_stats
import com.royalchance.feature.profile.resources.progress_title
import com.royalchance.feature.profile.resources.progress_unavailable_message
import com.royalchance.feature.profile.resources.progress_unavailable_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProgressScreen(viewModel: ProgressViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        RoyalTopBar(title = stringResource(Res.string.progress_title), windowInsets = WindowInsets(0))
        when (val current = state) {
            ProgressUiState.Loading -> FullScreenLoading()
            ProgressUiState.Unavailable -> EmptyState(
                icon = RoyalIcons.Trophy,
                title = stringResource(Res.string.progress_unavailable_title),
                message = stringResource(Res.string.progress_unavailable_message),
            )
            is ProgressUiState.Ready -> ProgressContent(current, onClaim = viewModel::claim)
        }
    }
}

@Composable
private fun ProgressContent(state: ProgressUiState.Ready, onClaim: (AchievementId) -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.s),
        ) {
            LevelCard(state.level)

            SectionHeader(stringResource(Res.string.progress_stats))
            StatsGrid(state.stats)

            SectionHeader(stringResource(Res.string.progress_achievements, state.unlockedCount, state.achievements.size))
            if (state.claimFailed) InfoBanner(message = stringResource(Res.string.progress_claim_failed), tone = BannerTone.Error)
            state.achievements.forEach { achievement ->
                AchievementRow(achievement, claiming = state.claiming == achievement.id, enabled = state.claiming == null, onClaim = onClaim)
            }
            Spacer(Modifier.size(RoyalSpacing.l))
        }
    }
}

@Composable
private fun LevelCard(level: LevelProgress) {
    val casino = RoyalTheme.casinoColors
    val next = level.xpForNextLevel
    val xpText = next?.let { stringResource(Res.string.progress_level_xp, formatGrouped(level.xpIntoLevel), formatGrouped(it)) }
        ?: stringResource(Res.string.progress_level_max)
    Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(casino.felt, casino.feltShadow),
                            center = Offset(size.width * 0.15f, size.height / 2f),
                            radius = maxOf(size.width, size.height) * 0.9f,
                        ),
                    )
                }
                .padding(RoyalSpacing.xl),
        ) {
            Text(
                text = levelLabel(level.level),
                style = MaterialTheme.typography.displaySmall,
                color = casino.gold,
                modifier = Modifier.semantics { heading() },
            )
            Text(titleName(level.title), style = MaterialTheme.typography.titleLarge, color = casino.onFelt)
            RoyalProgressBar(
                fraction = level.fraction,
                description = xpText,
                color = casino.gold,
                trackColor = casino.onFelt.copy(alpha = 0.2f),
            )
            Text(xpText, style = MaterialTheme.typography.labelLarge, color = casino.onFelt.copy(alpha = 0.85f))
            if (next != null) {
                Text(
                    text = stringResource(Res.string.progress_level_next, formatGrouped(next - level.xpIntoLevel), level.level + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = casino.onFelt.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsGrid(stats: PlayerStats) {
    val tiles = listOf(
        stringResource(Res.string.progress_stat_rounds) to formatGrouped(stats.roundsPlayed),
        stringResource(Res.string.progress_stat_wins) to formatGrouped(stats.wins),
        stringResource(Res.string.progress_stat_losses) to formatGrouped(stats.losses),
        stringResource(Res.string.progress_stat_pushes) to formatGrouped(stats.pushes),
        stringResource(Res.string.progress_stat_best_streak) to formatGrouped(stats.bestWinStreak),
        stringResource(Res.string.progress_stat_daily_streak) to formatGrouped(stats.dailyStreak),
        stringResource(Res.string.progress_stat_highest_balance) to stats.highestBalance.formatted(),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
        maxItemsInEachRow = 3,
        modifier = Modifier.fillMaxWidth(),
    ) {
        tiles.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
    ) {
        Column(Modifier.padding(RoyalSpacing.m)) {
            Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers(), maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementRow(
    achievement: AchievementUi,
    claiming: Boolean,
    enabled: Boolean,
    onClaim: (AchievementId) -> Unit,
) {
    val casino = RoyalTheme.casinoColors
    val state = achievement.state
    val unlocked = state !is AchievementState.Locked
    val reward = chipsText(achievement.reward)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (state == AchievementState.Claimable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s), modifier = Modifier.padding(RoyalSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state == AchievementState.Claimed) RoyalIcons.Check else RoyalIcons.Trophy,
                    contentDescription = null,
                    tint = if (unlocked) casino.gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(RoyalSpacing.m))
                Column(Modifier.weight(1f)) {
                    Text(achievementTitle(achievement.id), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = achievementDescription(achievement.id),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (state == AchievementState.Claimed) {
                            stringResource(Res.string.progress_achievement_claimed, reward)
                        } else {
                            stringResource(Res.string.progress_achievement_reward, reward)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state) {
                is AchievementState.Locked -> {
                    val progress = stringResource(
                        Res.string.progress_achievement_progress,
                        formatGrouped(state.current.coerceAtMost(state.target)),
                        formatGrouped(state.target),
                    )
                    RoyalProgressBar(fraction = state.fraction, description = progress)
                    Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AchievementState.Claimable -> RoyalSecondaryButton(
                    text = stringResource(Res.string.progress_achievement_claim, reward),
                    onClick = { onClaim(achievement.id) },
                    enabled = enabled,
                    loading = claiming,
                    modifier = Modifier.fillMaxWidth(),
                )
                AchievementState.Claimed -> Unit
            }
        }
    }
}
