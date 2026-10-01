package com.royalchance.feature.poker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import com.royalchance.core.designsystem.motion.WinCelebration
import com.royalchance.core.audio.SoundOnIncrease
import com.royalchance.core.audio.resultSound
import com.royalchance.core.audio.SoundOnChange
import com.royalchance.core.audio.Sound
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.component.feltBackground
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.ChipBalance
import com.royalchance.core.ui.HoldProgressEvents
import com.royalchance.core.ui.ProgressEventGate
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.Chips
import com.royalchance.engine.poker.LegalActions
import com.royalchance.engine.poker.PokerPhase
import com.royalchance.engine.poker.PokerRules
import com.royalchance.engine.poker.PokerState
import com.royalchance.feature.poker.resources.Res
import com.royalchance.feature.poker.resources.poker_all_in
import com.royalchance.feature.poker.resources.poker_back
import com.royalchance.feature.poker.resources.poker_buy_in
import com.royalchance.feature.poker.resources.poker_call
import com.royalchance.feature.poker.resources.poker_check
import com.royalchance.feature.poker.resources.poker_deal
import com.royalchance.feature.poker.resources.poker_dismiss
import com.royalchance.feature.poker.resources.poker_fold
import com.royalchance.feature.poker.resources.poker_folded_waiting
import com.royalchance.feature.poker.resources.poker_half_pot
import com.royalchance.feature.poker.resources.poker_min
import com.royalchance.feature.poker.resources.poker_notice_forfeited
import com.royalchance.feature.poker.resources.poker_notice_funds
import com.royalchance.feature.poker.resources.poker_notice_other_round
import com.royalchance.feature.poker.resources.poker_notice_settlement
import com.royalchance.feature.poker.resources.poker_notice_wallet
import com.royalchance.feature.poker.resources.poker_pot_size
import com.royalchance.feature.poker.resources.poker_raise_to
import com.royalchance.feature.poker.resources.poker_rebuy
import com.royalchance.feature.poker.resources.poker_rebuy_message
import com.royalchance.feature.poker.resources.poker_rules_note
import com.royalchance.feature.poker.resources.poker_sit
import com.royalchance.feature.poker.resources.poker_sit_title
import com.royalchance.feature.poker.resources.poker_stand_up
import com.royalchance.feature.poker.resources.poker_table_option
import com.royalchance.feature.poker.resources.poker_title
import com.royalchance.feature.poker.resources.poker_turn_of
import com.royalchance.feature.poker.resources.poker_win_category
import com.royalchance.feature.poker.resources.poker_win_uncontested
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PokerScreen(viewModel: PokerViewModel, eventGate: ProgressEventGate, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val casino = RoyalTheme.casinoColors
    // Los avisos de logros esperan al final de la mano.
    HoldProgressEvents(eventGate, held = state.inHand)

    Column(
        Modifier
            .fillMaxSize()
            .feltBackground(casino.feltBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        RoyalTopBar(
            title = stringResource(Res.string.poker_title),
            onBack = onBack,
            backDescription = stringResource(Res.string.poker_back),
            windowInsets = WindowInsets(0),
            actions = { state.balance?.let { ChipBalance(it, color = casino.gold, modifier = Modifier.padding(end = RoyalSpacing.l)) } },
        )
        val table = state.table
        if (table == null) {
            SitDownPanel(state, viewModel, Modifier.weight(1f))
        } else {
            val celebration = PokerSounds(table)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PokerTableView(table, Modifier.fillMaxSize().padding(RoyalSpacing.s))
                WinCelebration(trigger = celebration, modifier = Modifier.matchParentSize())
            }
            Controls(state, table, viewModel)
        }
    }
}

/**
 * Sonidos de la mesa: cartas repartidas, fichas que pone el jugador y el resultado de su mano.
 * Devuelve la clave de la celebración si el jugador gana un bote grande.
 */
@Composable
private fun PokerSounds(table: PokerState): Long? {
    val hero = table.seats[HERO]
    SoundOnChange(table.handNumber.takeIf { hero.hole.isNotEmpty() }, Sound.Card)
    SoundOnIncrease(table.board.size, Sound.Card)
    SoundOnIncrease(hero.committed.toInt(), Sound.Chip)
    val handOver = table.phase == PokerPhase.HandOver
    val won = table.awards.filter { HERO in it.winners }.sumOf { it.amount / it.winners.size }
    val bigWin = handOver && won >= BIG_POT_IN_BIG_BLINDS * table.rules.bigBlind
    val net = when {
        !handOver -> 0L
        won > 0 -> won - hero.committed
        hero.committed > 0 && !hero.folded -> -hero.committed
        else -> 0L
    }
    SoundOnChange(if (handOver) table.handNumber else null, resultSound(net, bigWin))
    return table.handNumber.toLong().takeIf { bigWin }
}

/** Bote "grande" para celebrarlo: desde 25 ciegas grandes. */
private const val BIG_POT_IN_BIG_BLINDS = 25

@Composable
private fun SitDownPanel(state: PokerUiState, viewModel: PokerViewModel, modifier: Modifier) {
    val casino = RoyalTheme.casinoColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l),
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(RoyalSpacing.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m), modifier = Modifier.widthIn(max = 480.dp)) {
            Text(stringResource(Res.string.poker_sit_title), style = MaterialTheme.typography.headlineSmall, color = casino.onFelt)
            Text(stringResource(Res.string.poker_rules_note), style = MaterialTheme.typography.bodyMedium, color = casino.onFelt.copy(alpha = 0.85f))
            PokerRules.TABLES.forEachIndexed { index, rules ->
                val selected = index == state.selectedTable
                Surface(
                    onClick = { viewModel.selectTable(index) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (selected) 1f else 0.7f),
                    border = if (selected) BorderStroke(2.dp, casino.gold) else null,
                    modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                ) {
                    Text(
                        stringResource(
                            Res.string.poker_table_option,
                            formatGrouped(rules.smallBlind),
                            formatGrouped(rules.bigBlind),
                            formatGrouped(rules.minBuyIn),
                            formatGrouped(rules.maxBuyIn),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(RoyalSpacing.l),
                    )
                }
            }
            val rules = PokerRules.TABLES[state.selectedTable]
            Text(stringResource(Res.string.poker_buy_in, chipsText(Chips(state.buyIn))), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
            Slider(
                value = state.buyIn.toFloat(),
                onValueChange = { viewModel.changeBuyIn((it / rules.chipUnit).toLong() * rules.chipUnit) },
                valueRange = rules.minBuyIn.toFloat()..rules.maxBuyIn.toFloat(),
            )
            state.notice?.let { NoticeBanner(it, viewModel) }
            RoyalPrimaryButton(
                text = stringResource(Res.string.poker_sit),
                onClick = viewModel::sitDown,
                enabled = !state.loading && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Controls(state: PokerUiState, table: PokerState, viewModel: PokerViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier.padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.m).fillMaxWidth(),
        ) {
            state.notice?.let { NoticeBanner(it, viewModel) }
            val legal = table.legalActions(HERO)
            val hero = table.seats[HERO]
            when {
                state.heroTurn && legal != null -> HeroActions(table, legal, viewModel)
                table.phase == PokerPhase.Betting -> {
                    Text(
                        if (hero.folded) {
                            stringResource(Res.string.poker_folded_waiting)
                        } else {
                            stringResource(Res.string.poker_turn_of, table.toAct?.let { table.seats[it].name }.orEmpty())
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (hero.folded) RoyalTextButton(text = stringResource(Res.string.poker_stand_up), onClick = viewModel::standUp)
                }
                else -> BetweenHands(state, table, viewModel)
            }
        }
    }
}

@Composable
private fun BetweenHands(state: PokerUiState, table: PokerState, viewModel: PokerViewModel) {
    val casino = RoyalTheme.casinoColors
    if (table.phase == PokerPhase.HandOver) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            table.awards.forEach { award ->
                val names = award.winners.joinToString(" y ") { table.seats[it].name }
                Text(
                    award.category?.let { stringResource(Res.string.poker_win_category, names, chipsText(Chips(award.amount)), categoryName(it)) }
                        ?: stringResource(Res.string.poker_win_uncontested, names, chipsText(Chips(award.amount))),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (HERO in award.winners) casino.success else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (state.needsRebuy) {
        Text(stringResource(Res.string.poker_rebuy_message), style = MaterialTheme.typography.bodyMedium)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoyalTextButton(text = stringResource(Res.string.poker_stand_up), onClick = viewModel::standUp, enabled = !state.busy)
        Spacer(Modifier.size(RoyalSpacing.s))
        if (state.needsRebuy) {
            RoyalPrimaryButton(
                text = stringResource(Res.string.poker_rebuy, chipsText(Chips(table.rules.maxBuyIn))),
                onClick = viewModel::rebuy,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            )
        } else {
            RoyalPrimaryButton(
                text = stringResource(Res.string.poker_deal),
                onClick = viewModel::dealNextHand,
                enabled = state.canDeal,
                loading = state.busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroActions(table: PokerState, legal: LegalActions, viewModel: PokerViewModel) {
    val unit = table.rules.chipUnit
    var raiseTo by remember(table.handNumber, table.street, legal.minRaiseTo) { mutableLongStateOf(legal.minRaiseTo) }
    if (legal.canRaise) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.xs)) {
            val pot = table.pot
            fun target(amount: Long) = (amount / unit * unit).coerceIn(legal.minRaiseTo, legal.maxRaiseTo)
            PresetButton(stringResource(Res.string.poker_min)) { raiseTo = legal.minRaiseTo }
            PresetButton(stringResource(Res.string.poker_half_pot)) { raiseTo = target(table.currentBet + pot / 2) }
            PresetButton(stringResource(Res.string.poker_pot_size)) { raiseTo = target(table.currentBet + pot) }
            PresetButton(stringResource(Res.string.poker_all_in)) { raiseTo = legal.maxRaiseTo }
        }
        if (legal.maxRaiseTo > legal.minRaiseTo) {
            Slider(
                value = raiseTo.toFloat(),
                onValueChange = { raiseTo = ((it.toLong() / unit) * unit).coerceIn(legal.minRaiseTo, legal.maxRaiseTo) },
                valueRange = legal.minRaiseTo.toFloat()..legal.maxRaiseTo.toFloat(),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        RoyalSecondaryButton(stringResource(Res.string.poker_fold), viewModel::fold, Modifier.weight(1f))
        RoyalSecondaryButton(
            if (legal.canCheck) stringResource(Res.string.poker_check) else stringResource(Res.string.poker_call, chipsText(Chips(legal.callAmount))),
            viewModel::checkOrCall,
            Modifier.weight(1f),
        )
        if (legal.canRaise) {
            RoyalPrimaryButton(
                text = if (raiseTo == legal.maxRaiseTo) stringResource(Res.string.poker_all_in) else stringResource(Res.string.poker_raise_to, chipsText(Chips(raiseTo))),
                onClick = { viewModel.raiseTo(raiseTo) },
                modifier = Modifier.weight(1.3f),
            )
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    RoyalTextButton(text = label, onClick = onClick)
}

@Composable
private fun NoticeBanner(notice: PokerNotice, viewModel: PokerViewModel) {
    InfoBanner(
        message = stringResource(
            when (notice) {
                PokerNotice.InsufficientFunds -> Res.string.poker_notice_funds
                PokerNotice.WalletUnavailable -> Res.string.poker_notice_wallet
                PokerNotice.OtherRoundInProgress -> Res.string.poker_notice_other_round
                PokerNotice.HandForfeited -> Res.string.poker_notice_forfeited
                PokerNotice.SettlementPending -> Res.string.poker_notice_settlement
            },
        ),
        tone = BannerTone.Error,
        actionLabel = stringResource(Res.string.poker_dismiss),
        onAction = viewModel::dismissNotice,
    )
}
