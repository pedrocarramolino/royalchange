package com.royalchance.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.EmptyState
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.icon.RoyalIcons
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.achievementTitle
import com.royalchance.core.ui.chipsText
import com.royalchance.core.ui.gameName
import com.royalchance.domain.economy.Chips
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.LedgerEntryKind
import com.royalchance.domain.game.GameType
import com.royalchance.domain.history.GameStats
import com.royalchance.domain.history.HistoryItem
import com.royalchance.domain.history.HistoryRepository
import com.royalchance.feature.history.resources.Res
import com.royalchance.feature.history.resources.history_balance_after
import com.royalchance.feature.history.resources.history_biggest_win
import com.royalchance.feature.history.resources.history_by_game
import com.royalchance.feature.history.resources.history_empty_message
import com.royalchance.feature.history.resources.history_empty_title
import com.royalchance.feature.history.resources.history_failed
import com.royalchance.feature.history.resources.history_load_more
import com.royalchance.feature.history.resources.history_movement_achievement
import com.royalchance.feature.history.resources.history_movement_daily
import com.royalchance.feature.history.resources.history_movement_rescue
import com.royalchance.feature.history.resources.history_movement_welcome
import com.royalchance.feature.history.resources.history_net
import com.royalchance.feature.history.resources.history_no_rounds
import com.royalchance.feature.history.resources.history_recent
import com.royalchance.feature.history.resources.history_retry
import com.royalchance.feature.history.resources.history_round_detail
import com.royalchance.feature.history.resources.history_rounds_wins
import com.royalchance.feature.history.resources.history_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

@Serializable
data object HistoryRoute : NavKey

fun PolymorphicModuleBuilder<NavKey>.historyRoutes() {
    subclass(HistoryRoute::class, HistoryRoute.serializer())
}

class HistoryDependencies(
    val historyRepository: HistoryRepository,
    val economyRepository: EconomyRepository,
    val timeZone: TimeZone,
)

fun EntryProviderScope<NavKey>.historyEntry(dependencies: HistoryDependencies) {
    entry<HistoryRoute> {
        HistoryScreen(
            viewModel = viewModel { HistoryViewModel(dependencies.historyRepository, dependencies.economyRepository) },
            timeZone = dependencies.timeZone,
        )
    }
}

@Composable
internal fun HistoryScreen(viewModel: HistoryViewModel, timeZone: TimeZone) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        RoyalTopBar(title = stringResource(Res.string.history_title), windowInsets = WindowInsets(0))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() && !state.failed -> EmptyState(
                icon = RoyalIcons.History,
                title = stringResource(Res.string.history_empty_title),
                message = stringResource(Res.string.history_empty_message),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(RoyalSpacing.l),
                verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.failed) {
                    item {
                        InfoBanner(
                            message = stringResource(Res.string.history_failed),
                            tone = BannerTone.Error,
                            actionLabel = stringResource(Res.string.history_retry),
                            onAction = viewModel::retry,
                        )
                    }
                }
                item { SectionTitle(stringResource(Res.string.history_by_game)) }
                item { GameStatsGrid(state.stats) }
                item { SectionTitle(stringResource(Res.string.history_recent)) }
                items(state.items, key = { it.sequence }) { item -> HistoryRow(item, timeZone) }
                if (state.nextBefore != null) {
                    item {
                        RoyalSecondaryButton(
                            text = stringResource(Res.string.history_load_more),
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = RoyalTheme.casinoColors.gold,
        modifier = Modifier.padding(top = RoyalSpacing.m).semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameStatsGrid(stats: Map<GameType, GameStats>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s), verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        GameType.entries.forEach { game -> GameStatsCard(game, stats[game] ?: GameStats()) }
    }
}

@Composable
private fun GameStatsCard(game: GameType, stats: GameStats) {
    val casino = RoyalTheme.casinoColors
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.widthIn(min = 150.dp, max = 220.dp)) {
        Column(Modifier.padding(RoyalSpacing.m), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(gameName(game), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (stats.rounds == 0L) {
                Text(stringResource(Res.string.history_no_rounds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val winRate = (stats.wins * 100.0 / stats.rounds).roundToInt()
                Text(
                    stringResource(Res.string.history_rounds_wins, formatGrouped(stats.rounds), winRate),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(Res.string.history_net, signedChips(stats.net)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = netColor(stats.net),
                )
                if (stats.biggestWin > 0) {
                    Text(
                        stringResource(Res.string.history_biggest_win, chipsText(Chips(stats.biggestWin))),
                        style = MaterialTheme.typography.labelSmall,
                        color = casino.gold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, timeZone: TimeZone) {
    val title: String
    val detail: String?
    val amount: Long?
    when (item) {
        is HistoryItem.Round -> {
            title = gameName(item.game)
            detail = item.stake?.let { stringResource(Res.string.history_round_detail, chipsText(it), chipsText(item.payout)) }
            amount = item.net
        }
        is HistoryItem.Movement -> {
            title = when (item.kind) {
                LedgerEntryKind.Welcome -> stringResource(Res.string.history_movement_welcome)
                LedgerEntryKind.Rescue -> stringResource(Res.string.history_movement_rescue)
                LedgerEntryKind.DailyBonus -> stringResource(Res.string.history_movement_daily)
                LedgerEntryKind.AchievementReward -> item.achievementId
                    ?.let { stringResource(Res.string.history_movement_achievement, achievementTitle(it)) }
                    ?: stringResource(Res.string.history_movement_achievement, "")
                else -> item.kind.name
            }
            detail = null
            amount = item.amount
        }
    }
    val time = formatTime(item.at, timeZone)
    val balance = stringResource(Res.string.history_balance_after, chipsText(item.balanceAfter))
    val description = listOfNotNull(title, detail, amount?.let { signedChips(it) }, time, balance).joinToString(". ")

    Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = RoyalSpacing.s)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(time, detail).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (amount != null) {
                    Text(signedChips(amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = netColor(amount))
                }
                Text(balance, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun netColor(amount: Long) = when {
    amount > 0 -> RoyalTheme.casinoColors.success
    amount < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun signedChips(amount: Long): String = when {
    amount > 0 -> "+" + chipsText(Chips(amount))
    amount < 0 -> "−" + chipsText(Chips(-amount))
    else -> chipsText(Chips.ZERO)
}

/** "01/10 18:42". */
private fun formatTime(at: Instant, timeZone: TimeZone): String {
    val local = at.toLocalDateTime(timeZone)
    fun two(value: Int) = value.toString().padStart(2, '0')
    return "${two(local.day)}/${two(local.month.ordinal + 1)} ${two(local.hour)}:${two(local.minute)}"
}
