package com.royalchance.feature.dice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.royalchance.core.designsystem.orientation.RequireLandscape
import com.royalchance.core.designsystem.motion.WinCelebration
import com.royalchance.core.audio.BIG_WIN_MULTIPLIER
import com.royalchance.core.audio.resultSound
import com.royalchance.core.audio.SoundOnChange
import com.royalchance.core.audio.Sound
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.BetChip
import com.royalchance.core.designsystem.component.GameTableLayout
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.component.StakeMarker
import com.royalchance.core.designsystem.component.feltBackground
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.ChipBalance
import com.royalchance.core.ui.HoldProgressEvents
import com.royalchance.core.ui.ProgressEventGate
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.Chips
import com.royalchance.engine.dice.DiceBet
import com.royalchance.feature.dice.resources.Res
import com.royalchance.feature.dice.resources.dice_back
import com.royalchance.feature.dice.resources.dice_bet_doubles
import com.royalchance.feature.dice.resources.dice_bet_high
import com.royalchance.feature.dice.resources.dice_bet_low
import com.royalchance.feature.dice.resources.dice_bet_seven
import com.royalchance.feature.dice.resources.dice_bet_sum
import com.royalchance.feature.dice.resources.dice_cell_description
import com.royalchance.feature.dice.resources.dice_cell_lost
import com.royalchance.feature.dice.resources.dice_cell_staked
import com.royalchance.feature.dice.resources.dice_cell_won
import com.royalchance.feature.dice.resources.dice_chip
import com.royalchance.feature.dice.resources.dice_clear
import com.royalchance.feature.dice.resources.dice_dismiss
import com.royalchance.feature.dice.resources.dice_exact_sums
import com.royalchance.feature.dice.resources.dice_history
import com.royalchance.feature.dice.resources.dice_history_empty
import com.royalchance.feature.dice.resources.dice_history_title
import com.royalchance.feature.dice.resources.dice_label_doubles
import com.royalchance.feature.dice.resources.dice_label_high
import com.royalchance.feature.dice.resources.dice_label_low
import com.royalchance.feature.dice.resources.dice_label_seven
import com.royalchance.feature.dice.resources.dice_limits
import com.royalchance.feature.dice.resources.dice_net_even
import com.royalchance.feature.dice.resources.dice_net_lost
import com.royalchance.feature.dice.resources.dice_net_won
import com.royalchance.feature.dice.resources.dice_notice_funds
import com.royalchance.feature.dice.resources.dice_notice_maximum
import com.royalchance.feature.dice.resources.dice_notice_wallet
import com.royalchance.feature.dice.resources.dice_place_bets
import com.royalchance.feature.dice.resources.dice_repeat
import com.royalchance.feature.dice.resources.dice_result
import com.royalchance.feature.dice.resources.dice_roll
import com.royalchance.feature.dice.resources.dice_rolling
import com.royalchance.feature.dice.resources.dice_title
import com.royalchance.feature.dice.resources.dice_total_bet
import com.royalchance.feature.dice.resources.dice_undo
import com.royalchance.feature.dice.resources.dice_view
import org.jetbrains.compose.resources.stringResource

/** Fichas para apostar. */
private val CHIP_VALUES = listOf(10L, 50L, 100L, 500L, 1_000L, 5_000L)

@Composable
internal fun DiceScreen(viewModel: DiceViewModel, eventGate: ProgressEventGate, onBack: () -> Unit) {
    // Las mesas se juegan en horizontal (en el móvil).
    RequireLandscape(onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val casino = RoyalTheme.casinoColors
    // Los avisos de logros esperan a que se paren los dados.
    HoldProgressEvents(eventGate, held = state.rolling)

    val thrown = state.lastThrow
    SoundOnChange(if (state.rolling) thrown?.id else null, Sound.Dice)
    val bigWin = thrown != null && thrown.result.totalPayout >= BIG_WIN_MULTIPLIER * thrown.result.totalStake
    val resultKey = if (state.showResult) thrown?.id else null
    SoundOnChange(resultKey, thrown?.let { resultSound(it.result.totalPayout - it.result.totalStake, bigWin) })

    GameTableLayout(
        modifier = Modifier
            .fillMaxSize()
            .feltBackground(casino.feltBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            RoyalTopBar(
                title = stringResource(Res.string.dice_title),
                onBack = onBack,
                backDescription = stringResource(Res.string.dice_back),
                windowInsets = WindowInsets(0),
                actions = { state.balance?.let { ChipBalance(it, color = casino.gold, modifier = Modifier.padding(end = RoyalSpacing.l)) } },
            )
        },
        controls = { Controls(state, viewModel) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val dieSize = if (maxWidth < 600.dp) 64.dp else 88.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(RoyalSpacing.l),
            ) {
                val description = stringResource(Res.string.dice_view)
                Box(Modifier.height(dieSize * 1.5f), contentAlignment = Alignment.BottomCenter) {
                    DicePair(
                        roll = state.lastThrow?.result?.roll,
                        throwId = state.lastThrow?.id,
                        rolling = state.rolling,
                        durationMillis = DiceViewModel.ROLL_DURATION.inWholeMilliseconds.toInt(),
                        onRollShown = viewModel::onRollShown,
                        size = dieSize,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                    )
                }
                ResultPanel(state)
                History(state)
                DiceBoard(state, viewModel::place, Modifier.widthIn(max = 560.dp))
            }
            WinCelebration(trigger = resultKey?.takeIf { bigWin }, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ResultPanel(state: DiceUiState) {
    val casino = RoyalTheme.casinoColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs),
        modifier = Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        val result = state.lastThrow?.result
        when {
            state.rolling -> Text(stringResource(Res.string.dice_rolling), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
            state.showResult && result != null -> {
                Text(
                    stringResource(Res.string.dice_result, result.roll.first, result.roll.second, result.roll.sum),
                    style = MaterialTheme.typography.titleMedium,
                    color = casino.onFelt,
                )
                val net = result.totalPayout - result.totalStake
                val (text, color) = when {
                    net > 0 -> stringResource(Res.string.dice_net_won, chipsText(Chips(net))) to casino.success
                    net < 0 -> stringResource(Res.string.dice_net_lost, chipsText(Chips(-net))) to casino.suitRed
                    else -> stringResource(Res.string.dice_net_even) to casino.onFelt
                }
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            }
            state.bets.isNotEmpty() -> Text(
                stringResource(Res.string.dice_total_bet, chipsText(Chips(state.totalBet))),
                style = MaterialTheme.typography.titleMedium,
                color = casino.onFelt,
            )
            else -> Text(stringResource(Res.string.dice_place_bets), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun History(state: DiceUiState) {
    val casino = RoyalTheme.casinoColors
    val history = state.history
    val description = if (history.isEmpty()) {
        stringResource(Res.string.dice_history_empty)
    } else {
        stringResource(Res.string.dice_history, history.joinToString(", ") { it.sum.toString() })
    }
    if (history.isEmpty()) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(stringResource(Res.string.dice_history_title), style = MaterialTheme.typography.labelSmall, color = casino.onFelt.copy(alpha = 0.75f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            history.forEach { roll ->
                Surface(
                    shape = CircleShape,
                    color = if (roll.isDouble) casino.goldDeep else Color.Black.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, casino.onFelt.copy(alpha = 0.4f)),
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(roll.sum.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = casino.onFelt)
                    }
                }
            }
        }
    }
}

/** Tapete de dados: apuestas de suma, siete y dobles arriba; sumas exactas abajo. */
@Composable
private fun DiceBoard(state: DiceUiState, onPlace: (DiceBet) -> Unit, modifier: Modifier) {
    val casino = RoyalTheme.casinoColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            BetCell(DiceBet.Low, stringResource(Res.string.dice_label_low), state, onPlace, Modifier.weight(1f).fillMaxHeight())
            BetCell(DiceBet.Seven, stringResource(Res.string.dice_label_seven), state, onPlace, Modifier.weight(1f).fillMaxHeight())
            BetCell(DiceBet.High, stringResource(Res.string.dice_label_high), state, onPlace, Modifier.weight(1f).fillMaxHeight())
        }
        BetCell(DiceBet.Doubles, stringResource(Res.string.dice_label_doubles), state, onPlace, Modifier.fillMaxWidth())
        Text(
            stringResource(Res.string.dice_exact_sums),
            style = MaterialTheme.typography.labelMedium,
            color = casino.onFelt.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = RoyalSpacing.s),
        )
        listOf(listOf(2, 3, 4, 5, 6), listOf(8, 9, 10, 11, 12)).forEach { sums ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sums.forEach { total -> BetCell(DiceBet.Sum(total), total.toString(), state, onPlace, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BetCell(bet: DiceBet, label: String, state: DiceUiState, onPlace: (DiceBet) -> Unit, modifier: Modifier) {
    val casino = RoyalTheme.casinoColors
    val result = if (state.showResult) state.lastThrow?.result?.results?.firstOrNull { it.bet == bet } else null
    val stake = if (state.showResult) result?.stake ?: 0 else state.stakeOn(bet)
    val winning = state.showResult && state.lastThrow?.result?.roll?.let(bet::wins) == true
    val multiplier = multiplierText(bet.multiplierTenths)

    val description = buildString {
        append(stringResource(Res.string.dice_cell_description, betName(bet), multiplier))
        if (stake > 0) {
            append(". ")
            append(
                when {
                    result == null -> stringResource(Res.string.dice_cell_staked, chipsText(Chips(stake)))
                    result.payout > 0 -> stringResource(Res.string.dice_cell_won, chipsText(Chips(result.payout)))
                    else -> stringResource(Res.string.dice_cell_lost, chipsText(Chips(stake)))
                },
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 56.dp)
            .background(Color.Black.copy(alpha = if (winning) 0.35f else 0.18f), MaterialTheme.shapes.small)
            .border(
                if (winning) BorderStroke(3.dp, casino.gold) else BorderStroke(1.dp, casino.onFelt.copy(alpha = 0.35f)),
                MaterialTheme.shapes.small,
            )
            .clickable(enabled = state.canBet) { onPlace(bet) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (state.canBet) onClick { onPlace(bet); true }
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = RoyalSpacing.s, horizontal = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (winning) casino.gold else casino.onFelt, textAlign = TextAlign.Center)
            Text(multiplier, style = MaterialTheme.typography.labelSmall, color = casino.gold.copy(alpha = 0.9f))
        }
        if (stake > 0) {
            StakeMarker(
                amount = if (result != null && result.payout > 0) result.payout else stake,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).alpha(if (result != null && result.payout == 0L) 0.35f else 1f),
                size = 22.dp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(state: DiceUiState, viewModel: DiceViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier.padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.m).fillMaxWidth(),
        ) {
            state.notice?.let { notice ->
                InfoBanner(
                    message = notice.message(),
                    tone = BannerTone.Error,
                    actionLabel = stringResource(Res.string.dice_dismiss),
                    onAction = viewModel::dismissNotice,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s), verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
                CHIP_VALUES.forEach { value ->
                    BetChip(
                        value = value,
                        description = stringResource(Res.string.dice_chip, chipsText(Chips(value))),
                        onClick = { viewModel.selectChip(value) },
                        selected = value == state.chip,
                        size = 46.dp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoyalTextButton(text = stringResource(Res.string.dice_undo), onClick = viewModel::undo, enabled = state.canBet && state.canUndo)
                RoyalTextButton(text = stringResource(Res.string.dice_clear), onClick = viewModel::clear, enabled = state.canBet && state.bets.isNotEmpty())
                RoyalTextButton(
                    text = stringResource(Res.string.dice_repeat),
                    onClick = viewModel::repeat,
                    enabled = state.canBet && state.bets.isEmpty() && state.lastBets.isNotEmpty(),
                )
                Spacer(Modifier.size(RoyalSpacing.s))
                RoyalPrimaryButton(
                    text = stringResource(Res.string.dice_roll),
                    onClick = viewModel::roll,
                    enabled = state.canBet && state.bets.isNotEmpty(),
                    loading = state.busy || state.rolling,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(Res.string.dice_limits, formatGrouped(state.rules.chipUnit), formatGrouped(state.rules.maximumTotalBet)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 23 → "×2,3"; 350 → "×35". */
private fun multiplierText(tenths: Long): String =
    if (tenths % 10 == 0L) "×${tenths / 10}" else "×${tenths / 10},${tenths % 10}"

@Composable
private fun betName(bet: DiceBet): String = when (bet) {
    DiceBet.Low -> stringResource(Res.string.dice_bet_low)
    DiceBet.High -> stringResource(Res.string.dice_bet_high)
    DiceBet.Seven -> stringResource(Res.string.dice_bet_seven)
    DiceBet.Doubles -> stringResource(Res.string.dice_bet_doubles)
    is DiceBet.Sum -> stringResource(Res.string.dice_bet_sum, bet.total)
}

@Composable
private fun DiceNotice.message(): String = when (this) {
    DiceNotice.InsufficientFunds -> stringResource(Res.string.dice_notice_funds)
    is DiceNotice.AboveTableMaximum -> stringResource(Res.string.dice_notice_maximum, chipsText(Chips(maximum)))
    DiceNotice.WalletUnavailable -> stringResource(Res.string.dice_notice_wallet)
}
