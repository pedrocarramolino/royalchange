package com.royalchance.feature.roulette

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.royalchance.core.designsystem.component.feltBackground
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.ChipBalance
import com.royalchance.core.ui.HoldProgressEvents
import com.royalchance.core.ui.ProgressEventGate
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.Chips
import com.royalchance.feature.roulette.resources.Res
import com.royalchance.feature.roulette.resources.roulette_back
import com.royalchance.feature.roulette.resources.roulette_chip
import com.royalchance.feature.roulette.resources.roulette_clear
import com.royalchance.feature.roulette.resources.roulette_dismiss
import com.royalchance.feature.roulette.resources.roulette_history
import com.royalchance.feature.roulette.resources.roulette_history_empty
import com.royalchance.feature.roulette.resources.roulette_history_title
import com.royalchance.feature.roulette.resources.roulette_limits
import com.royalchance.feature.roulette.resources.roulette_net_even
import com.royalchance.feature.roulette.resources.roulette_net_lost
import com.royalchance.feature.roulette.resources.roulette_net_won
import com.royalchance.feature.roulette.resources.roulette_notice_funds
import com.royalchance.feature.roulette.resources.roulette_notice_maximum
import com.royalchance.feature.roulette.resources.roulette_notice_wallet
import com.royalchance.feature.roulette.resources.roulette_place_bets
import com.royalchance.feature.roulette.resources.roulette_repeat
import com.royalchance.feature.roulette.resources.roulette_result
import com.royalchance.feature.roulette.resources.roulette_spin
import com.royalchance.feature.roulette.resources.roulette_spinning
import com.royalchance.feature.roulette.resources.roulette_title
import com.royalchance.feature.roulette.resources.roulette_total_bet
import com.royalchance.feature.roulette.resources.roulette_undo
import com.royalchance.feature.roulette.resources.roulette_wheel
import org.jetbrains.compose.resources.stringResource

/** Fichas para apostar. */
private val CHIP_VALUES = listOf(10L, 50L, 100L, 500L, 1_000L, 5_000L)

@Composable
internal fun RouletteScreen(viewModel: RouletteViewModel, eventGate: ProgressEventGate, onBack: () -> Unit) {
    // Las mesas se juegan en horizontal (en el móvil).
    RequireLandscape(onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val casino = RoyalTheme.casinoColors
    // Los avisos de logros esperan a que la bola se pare: no deben adelantar el resultado.
    HoldProgressEvents(eventGate, held = state.spinning)

    val spin = state.lastSpin
    SoundOnChange(if (state.spinning) spin?.id else null, Sound.Spin)
    val bigWin = spin != null && spin.spin.totalPayout >= BIG_WIN_MULTIPLIER * spin.spin.totalStake
    val resultKey = if (state.showResult) spin?.id else null
    SoundOnChange(resultKey, spin?.let { resultSound(it.spin.totalPayout - it.spin.totalStake, bigWin) })

    GameTableLayout(
        modifier = Modifier
            .fillMaxSize()
            .feltBackground(casino.feltBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            RoyalTopBar(
                title = stringResource(Res.string.roulette_title),
                onBack = onBack,
                backDescription = stringResource(Res.string.roulette_back),
                windowInsets = WindowInsets(0),
                actions = { state.balance?.let { ChipBalance(it, color = casino.gold, modifier = Modifier.padding(end = RoyalSpacing.l)) } },
            )
        },
        controls = { Controls(state, viewModel) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            // Rueda y tapete lado a lado si hay anchura (escritorio, tablet o móvil en horizontal).
            if (width >= 840.dp || width > height) {
                val rowHeight = ((height - RoyalSpacing.l * 2) / 14).coerceIn(28.dp, 44.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.xl),
                    modifier = Modifier.fillMaxSize().padding(RoyalSpacing.l),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l, Alignment.CenterVertically),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Wheel(state, viewModel::onSpinShown, (height * 0.55f).coerceAtMost(360.dp))
                        ResultPanel(state, Alignment.CenterHorizontally)
                        History(state.history)
                    }
                    Box(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                        RouletteBoard(state, viewModel::place, rowHeight)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.l),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.s),
                    ) {
                        Wheel(state, viewModel::onSpinShown, if (width < 400.dp) 140.dp else 180.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s), modifier = Modifier.weight(1f)) {
                            ResultPanel(state, Alignment.Start)
                            History(state.history)
                        }
                    }
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = RoyalSpacing.l),
                    ) {
                        RouletteBoard(state, viewModel::place, rowHeight = 34.dp, modifier = Modifier.padding(bottom = RoyalSpacing.m))
                    }
                }
            }
            WinCelebration(trigger = resultKey?.takeIf { bigWin }, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun Wheel(state: RouletteUiState, onSpinShown: (Long) -> Unit, size: Dp) {
    val number = state.lastSpin?.spin?.number
    val description = stringResource(Res.string.roulette_wheel)
    RouletteWheelView(
        number = number,
        spinId = state.lastSpin?.id,
        spinning = state.spinning,
        durationMillis = RouletteViewModel.SPIN_DURATION.inWholeMilliseconds.toInt(),
        onSpinShown = onSpinShown,
        modifier = Modifier.size(size).clearAndSetSemantics { contentDescription = description },
    )
}

/** Estado de la ronda: apuestas, bola en juego o el número que ha salido con el balance. */
@Composable
private fun ResultPanel(state: RouletteUiState, alignment: Alignment.Horizontal) {
    val casino = RoyalTheme.casinoColors
    Column(
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        val spin = state.lastSpin?.spin
        when {
            state.spinning -> Text(stringResource(Res.string.roulette_spinning), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
            state.showResult && spin != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
                    NumberBadge(spin.number, 40.dp)
                    Text(
                        text = stringResource(Res.string.roulette_result, spin.number, colorName(spin.number)),
                        style = MaterialTheme.typography.titleMedium,
                        color = casino.onFelt,
                    )
                }
                val net = spin.totalPayout - spin.totalStake
                val (text, color) = when {
                    net > 0 -> stringResource(Res.string.roulette_net_won, chipsText(Chips(net))) to casino.success
                    net < 0 -> stringResource(Res.string.roulette_net_lost, chipsText(Chips(-net))) to casino.suitRed
                    else -> stringResource(Res.string.roulette_net_even) to casino.onFelt
                }
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            }
            state.bets.isNotEmpty() -> Text(
                stringResource(Res.string.roulette_total_bet, chipsText(Chips(state.totalBet))),
                style = MaterialTheme.typography.titleMedium,
                color = casino.onFelt,
            )
            else -> Text(stringResource(Res.string.roulette_place_bets), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun History(history: List<Int>) {
    val casino = RoyalTheme.casinoColors
    val description = if (history.isEmpty()) {
        stringResource(Res.string.roulette_history_empty)
    } else {
        stringResource(Res.string.roulette_history, history.joinToString(", "))
    }
    Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs), modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        if (history.isNotEmpty()) {
            Text(stringResource(Res.string.roulette_history_title), style = MaterialTheme.typography.labelSmall, color = casino.onFelt.copy(alpha = 0.75f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                history.forEach { NumberBadge(it, 24.dp) }
            }
        }
    }
}

@Composable
private fun NumberBadge(number: Int, size: Dp) {
    Surface(shape = CircleShape, color = pocketColor(number), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                number.toString(),
                style = if (size >= 32.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(state: RouletteUiState, viewModel: RouletteViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier.padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.m).fillMaxWidth(),
        ) {
            state.notice?.let { notice ->
                InfoBanner(
                    message = notice.message(),
                    tone = BannerTone.Error,
                    actionLabel = stringResource(Res.string.roulette_dismiss),
                    onAction = viewModel::dismissNotice,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s), verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
                CHIP_VALUES.forEach { value ->
                    BetChip(
                        value = value,
                        description = stringResource(Res.string.roulette_chip, chipsText(Chips(value))),
                        onClick = { viewModel.selectChip(value) },
                        selected = value == state.chip,
                        size = 46.dp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoyalTextButton(text = stringResource(Res.string.roulette_undo), onClick = viewModel::undo, enabled = state.canBet && state.canUndo)
                RoyalTextButton(text = stringResource(Res.string.roulette_clear), onClick = viewModel::clear, enabled = state.canBet && state.bets.isNotEmpty())
                RoyalTextButton(
                    text = stringResource(Res.string.roulette_repeat),
                    onClick = viewModel::repeat,
                    enabled = state.canBet && state.bets.isEmpty() && state.lastBets.isNotEmpty(),
                )
                Spacer(Modifier.size(RoyalSpacing.s))
                RoyalPrimaryButton(
                    text = stringResource(Res.string.roulette_spin),
                    onClick = viewModel::spin,
                    enabled = state.canBet && state.bets.isNotEmpty(),
                    loading = state.busy || state.spinning,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(Res.string.roulette_limits, formatGrouped(state.rules.chipUnit), formatGrouped(state.rules.maximumTotalBet)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouletteNotice.message(): String = when (this) {
    RouletteNotice.InsufficientFunds -> stringResource(Res.string.roulette_notice_funds)
    is RouletteNotice.AboveTableMaximum -> stringResource(Res.string.roulette_notice_maximum, chipsText(Chips(maximum)))
    RouletteNotice.WalletUnavailable -> stringResource(Res.string.roulette_notice_wallet)
}
