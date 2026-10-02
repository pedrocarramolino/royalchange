package com.royalchance.feature.slots

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.designsystem.component.BannerTone
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
import com.royalchance.engine.slots.SlotMachine
import com.royalchance.engine.slots.SlotSymbol
import com.royalchance.feature.slots.resources.Res
import com.royalchance.feature.slots.resources.slots_back
import com.royalchance.feature.slots.resources.slots_bet
import com.royalchance.feature.slots.resources.slots_bet_down
import com.royalchance.feature.slots.resources.slots_bet_up
import com.royalchance.feature.slots.resources.slots_close
import com.royalchance.feature.slots.resources.slots_dismiss
import com.royalchance.feature.slots.resources.slots_line_bet
import com.royalchance.feature.slots.resources.slots_line_win
import com.royalchance.feature.slots.resources.slots_no_win
import com.royalchance.feature.slots.resources.slots_notice_funds
import com.royalchance.feature.slots.resources.slots_notice_wallet
import com.royalchance.feature.slots.resources.slots_paytable
import com.royalchance.feature.slots.resources.slots_paytable_note
import com.royalchance.feature.slots.resources.slots_partial
import com.royalchance.feature.slots.resources.slots_paytable_row
import com.royalchance.feature.slots.resources.slots_reels
import com.royalchance.feature.slots.resources.slots_spin
import com.royalchance.feature.slots.resources.slots_spinning
import com.royalchance.feature.slots.resources.slots_start
import com.royalchance.feature.slots.resources.slots_symbol_bar
import com.royalchance.feature.slots.resources.slots_symbol_cherry
import com.royalchance.feature.slots.resources.slots_symbol_club
import com.royalchance.feature.slots.resources.slots_symbol_diamond
import com.royalchance.feature.slots.resources.slots_symbol_heart
import com.royalchance.feature.slots.resources.slots_symbol_seven
import com.royalchance.feature.slots.resources.slots_symbol_spade
import com.royalchance.feature.slots.resources.slots_symbol_wild
import com.royalchance.feature.slots.resources.slots_title
import com.royalchance.feature.slots.resources.slots_won
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SlotsScreen(viewModel: SlotsViewModel, eventGate: ProgressEventGate, onBack: () -> Unit) {
    // Las mesas se juegan en horizontal (en el móvil).
    RequireLandscape(onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val casino = RoyalTheme.casinoColors
    var showPaytable by remember { mutableStateOf(false) }
    // Los avisos de logros esperan a que paren los rodillos.
    HoldProgressEvents(eventGate, held = state.spinning)

    val lastSpin = state.lastSpin
    SoundOnChange(if (state.spinning) lastSpin?.id else null, Sound.Spin)
    val bigWin = lastSpin != null && lastSpin.spin.totalPayout >= BIG_WIN_MULTIPLIER * lastSpin.spin.totalBet
    val resultKey = if (state.showResult) lastSpin?.id else null
    // En la tragaperras perder es lo habitual: solo suenan los premios.
    SoundOnChange(resultKey, lastSpin?.let { resultSound((it.spin.totalPayout - it.spin.totalBet).coerceAtLeast(0), bigWin) })

    GameTableLayout(
        modifier = Modifier
            .fillMaxSize()
            .feltBackground(casino.feltBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            RoyalTopBar(
                title = stringResource(Res.string.slots_title),
                onBack = onBack,
                backDescription = stringResource(Res.string.slots_back),
                windowInsets = WindowInsets(0),
                actions = { state.balance?.let { ChipBalance(it, color = casino.gold, modifier = Modifier.padding(end = RoyalSpacing.l)) } },
            )
        },
        controls = { Controls(state, viewModel, onShowPaytable = { showPaytable = true }) },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val cell = minOf((maxWidth - 64.dp) / SlotMachine.REELS, (maxHeight - 140.dp) / SlotMachine.ROWS, 110.dp).coerceAtLeast(44.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RoyalSpacing.l),
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(RoyalSpacing.l),
            ) {
                val spin = state.lastSpin?.spin
                val highlighted = if (state.showResult && spin != null) {
                    spin.wins.flatMap { win -> (0 until win.count).map { reel -> reel to SlotMachine.LINES[win.line][reel] } }.toSet()
                } else {
                    emptySet()
                }
                val reelsDescription = stringResource(Res.string.slots_reels)
                SlotReels(
                    stops = spin?.stops,
                    spinId = state.lastSpin?.id,
                    spinning = state.spinning,
                    durationMillis = SlotsViewModel.SPIN_DURATION.inWholeMilliseconds.toInt(),
                    highlighted = highlighted,
                    onSpinShown = viewModel::onSpinShown,
                    cellSize = cell,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = reelsDescription },
                )
                ResultPanel(state)
            }
            WinCelebration(trigger = resultKey?.takeIf { bigWin }, modifier = Modifier.matchParentSize())
        }
    }

    if (showPaytable) PaytableDialog(onDismiss = { showPaytable = false })
}

@Composable
private fun ResultPanel(state: SlotsUiState) {
    val casino = RoyalTheme.casinoColors
    val spin = state.lastSpin?.spin
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.xs),
        modifier = Modifier.heightIn(min = 72.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        when {
            state.spinning -> Text(stringResource(Res.string.slots_spinning), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
            state.showResult && spin != null && spin.totalPayout > 0 -> {
                Text(
                    if (spin.totalPayout > spin.totalBet) {
                        stringResource(Res.string.slots_won, chipsText(Chips(spin.totalPayout)))
                    } else {
                        stringResource(Res.string.slots_partial, chipsText(Chips(spin.totalPayout)), chipsText(Chips(spin.totalBet)))
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = casino.gold,
                )
                spin.wins.take(4).forEach { win ->
                    Text(
                        stringResource(Res.string.slots_line_win, win.line + 1, win.count, symbolName(win.symbol), chipsText(Chips(win.payout))),
                        style = MaterialTheme.typography.labelMedium,
                        color = casino.onFelt,
                    )
                }
            }
            state.showResult -> Text(stringResource(Res.string.slots_no_win), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
            else -> Text(stringResource(Res.string.slots_start), style = MaterialTheme.typography.titleMedium, color = casino.onFelt)
        }
    }
}

@Composable
private fun Controls(state: SlotsUiState, viewModel: SlotsViewModel, onShowPaytable: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier.padding(horizontal = RoyalSpacing.l, vertical = RoyalSpacing.m).fillMaxWidth(),
        ) {
            state.notice?.let { notice ->
                InfoBanner(
                    message = stringResource(
                        when (notice) {
                            SlotsNotice.InsufficientFunds -> Res.string.slots_notice_funds
                            SlotsNotice.WalletUnavailable -> Res.string.slots_notice_wallet
                        },
                    ),
                    tone = BannerTone.Error,
                    actionLabel = stringResource(Res.string.slots_dismiss),
                    onAction = viewModel::dismissNotice,
                )
            }
            val downDescription = stringResource(Res.string.slots_bet_down)
            val upDescription = stringResource(Res.string.slots_bet_up)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", downDescription, viewModel::decreaseBet, enabled = state.canSpin && state.betIndex > 0)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.slots_bet, chipsText(Chips(state.totalBet))), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.slots_line_bet, chipsText(Chips(state.lineBet)), SlotMachine.LINES.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StepButton("+", upDescription, viewModel::increaseBet, enabled = state.canSpin && state.betIndex < state.rules.lineBets.lastIndex)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoyalTextButton(text = stringResource(Res.string.slots_paytable), onClick = onShowPaytable)
                Spacer(Modifier.size(RoyalSpacing.s))
                RoyalPrimaryButton(
                    text = stringResource(Res.string.slots_spin),
                    onClick = viewModel::spin,
                    enabled = state.canSpin,
                    loading = state.busy || state.spinning,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Botón redondo para subir o bajar la apuesta. */
@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit, enabled: Boolean) {
    val casino = RoyalTheme.casinoColors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, if (enabled) casino.gold else casino.gold.copy(alpha = 0.3f)),
        modifier = Modifier.size(48.dp).clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            // clearAndSetSemantics borra la acción del Surface: se declara para los lectores de pantalla.
            if (enabled) onClick { onClick(); true }
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                symbol,
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) casino.gold else casino.gold.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun PaytableDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { RoyalTextButton(text = stringResource(Res.string.slots_close), onClick = onDismiss) },
        title = { Text(stringResource(Res.string.slots_paytable)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s), modifier = Modifier.verticalScroll(rememberScrollState())) {
                SlotSymbol.entries.reversed().forEach { symbol ->
                    val pays = SlotMachine.PAYTABLE.getValue(symbol)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.m)) {
                        SlotSymbolArt(symbol, 32.dp)
                        Text(
                            stringResource(Res.string.slots_paytable_row, symbolName(symbol), pays[1], pays[2], pays[3]) +
                                if (pays[0] > 0) " · 2: ×${pays[0]}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(stringResource(Res.string.slots_paytable_note), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun symbolName(symbol: SlotSymbol): String = stringResource(symbol.nameRes)

private val SlotSymbol.nameRes: StringResource
    get() = when (this) {
        SlotSymbol.Cherry -> Res.string.slots_symbol_cherry
        SlotSymbol.Club -> Res.string.slots_symbol_club
        SlotSymbol.Heart -> Res.string.slots_symbol_heart
        SlotSymbol.Spade -> Res.string.slots_symbol_spade
        SlotSymbol.Diamond -> Res.string.slots_symbol_diamond
        SlotSymbol.Bar -> Res.string.slots_symbol_bar
        SlotSymbol.Seven -> Res.string.slots_symbol_seven
        SlotSymbol.Wild -> Res.string.slots_symbol_wild
    }
