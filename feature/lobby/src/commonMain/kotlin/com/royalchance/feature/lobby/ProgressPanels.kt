package com.royalchance.feature.lobby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalProgressBar
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.designsystem.theme.tabularNumbers
import com.royalchance.core.ui.chipsText
import com.royalchance.core.ui.levelLabel
import com.royalchance.core.ui.titleName
import com.royalchance.domain.progression.DailyBonusStatus
import com.royalchance.domain.progression.LevelProgress
import com.royalchance.feature.lobby.resources.Res
import com.royalchance.feature.lobby.resources.lobby_achievements_claimable_many
import com.royalchance.feature.lobby.resources.lobby_achievements_claimable_one
import com.royalchance.feature.lobby.resources.lobby_achievements_open
import com.royalchance.feature.lobby.resources.lobby_daily_available_subtitle
import com.royalchance.feature.lobby.resources.lobby_daily_available_title
import com.royalchance.feature.lobby.resources.lobby_daily_claim
import com.royalchance.feature.lobby.resources.lobby_daily_clock_subtitle
import com.royalchance.feature.lobby.resources.lobby_daily_failed
import com.royalchance.feature.lobby.resources.lobby_daily_next_subtitle
import com.royalchance.feature.lobby.resources.lobby_daily_streak_many
import com.royalchance.feature.lobby.resources.lobby_daily_streak_one
import com.royalchance.feature.lobby.resources.lobby_daily_title
import com.royalchance.feature.lobby.resources.lobby_level_max
import com.royalchance.feature.lobby.resources.lobby_level_xp
import com.royalchance.feature.lobby.resources.lobby_open_progress
import org.jetbrains.compose.resources.stringResource

/** Nivel, título y experiencia hacia el siguiente nivel. Abre la pestaña de progreso. */
@Composable
internal fun LevelSummary(level: LevelProgress, onOpenProgress: () -> Unit) {
    val casino = RoyalTheme.casinoColors
    val xpText = level.xpForNextLevel
        ?.let { stringResource(Res.string.lobby_level_xp, formatGrouped(level.xpIntoLevel), formatGrouped(it)) }
        ?: stringResource(Res.string.lobby_level_max)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(Res.string.lobby_open_progress), role = Role.Button, onClick = onOpenProgress),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(RoyalSpacing.l)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = level.level.toString(),
                        style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(RoyalSpacing.l))
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs), modifier = Modifier.weight(1f)) {
                Text(
                    text = "${levelLabel(level.level)} · ${titleName(level.title)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                RoyalProgressBar(fraction = level.fraction, description = xpText, color = casino.gold)
                Text(xpText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Bono diario: disponible, ya cobrado (con lo que toca mañana) o bloqueado por el reloj. */
@Composable
internal fun DailyBonusCard(
    status: DailyBonusStatus,
    claiming: Boolean,
    failed: Boolean,
    onClaim: () -> Unit,
) {
    val casino = RoyalTheme.casinoColors
    val available = status is DailyBonusStatus.Available
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (available) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m), modifier = Modifier.padding(RoyalSpacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(RoyalIcons.Flame, contentDescription = null, tint = casino.gold, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(RoyalSpacing.m))
                Column(Modifier.weight(1f)) {
                    Text(dailyTitle(status), style = MaterialTheme.typography.titleMedium)
                    Text(dailySubtitle(status), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (status is DailyBonusStatus.Available) {
                RoyalPrimaryButton(
                    text = stringResource(Res.string.lobby_daily_claim),
                    onClick = onClaim,
                    loading = claiming,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (failed) {
                Text(
                    text = stringResource(Res.string.lobby_daily_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun dailyTitle(status: DailyBonusStatus): String = when (status) {
    is DailyBonusStatus.Available -> stringResource(Res.string.lobby_daily_available_title, status.streakDay)
    is DailyBonusStatus.ClaimedToday -> streakText(status.streakDay)
    DailyBonusStatus.ClockMovedBack -> stringResource(Res.string.lobby_daily_title)
}

@Composable
private fun dailySubtitle(status: DailyBonusStatus): String = when (status) {
    is DailyBonusStatus.Available -> stringResource(Res.string.lobby_daily_available_subtitle, chipsText(status.reward))
    is DailyBonusStatus.ClaimedToday -> stringResource(Res.string.lobby_daily_next_subtitle, chipsText(status.nextReward))
    DailyBonusStatus.ClockMovedBack -> stringResource(Res.string.lobby_daily_clock_subtitle)
}

@Composable
private fun streakText(days: Long): String =
    if (days == 1L) stringResource(Res.string.lobby_daily_streak_one) else stringResource(Res.string.lobby_daily_streak_many, days)

/** Aviso de recompensas de logros pendientes de recoger. */
@Composable
internal fun ClaimableAchievementsBanner(count: Int, onOpenProgress: () -> Unit) {
    InfoBanner(
        message = if (count == 1) {
            stringResource(Res.string.lobby_achievements_claimable_one)
        } else {
            stringResource(Res.string.lobby_achievements_claimable_many, count)
        },
        tone = BannerTone.Success,
        icon = RoyalIcons.Trophy,
        actionLabel = stringResource(Res.string.lobby_achievements_open),
        onAction = onOpenProgress,
    )
}
