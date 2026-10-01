package com.royalchance.feature.blackjack

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.royalchance.core.common.text.formatGrouped
import com.royalchance.core.designsystem.component.BannerTone
import com.royalchance.core.designsystem.component.BetChip
import com.royalchance.core.designsystem.component.InfoBanner
import com.royalchance.core.designsystem.component.PlayingCard
import com.royalchance.core.designsystem.component.RoyalPrimaryButton
import com.royalchance.core.designsystem.component.RoyalSecondaryButton
import com.royalchance.core.designsystem.component.RoyalTextButton
import com.royalchance.core.designsystem.component.RoyalTopBar
import com.royalchance.core.designsystem.component.feltBackground
import com.royalchance.core.designsystem.graphics.SuitShape
import com.royalchance.core.designsystem.theme.RoyalSpacing
import com.royalchance.core.designsystem.theme.RoyalTheme
import com.royalchance.core.ui.ChipBalance
import com.royalchance.core.ui.HoldProgressEvents
import com.royalchance.core.ui.ProgressEventGate
import com.royalchance.core.ui.chipsText
import com.royalchance.domain.economy.Chips
import com.royalchance.engine.blackjack.BlackjackMove
import com.royalchance.engine.blackjack.BlackjackPhase
import com.royalchance.engine.blackjack.HandOutcome
import com.royalchance.engine.blackjack.HandResult
import com.royalchance.engine.blackjack.PlayerHand
import com.royalchance.engine.blackjack.handValue
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Suit
import com.royalchance.feature.blackjack.resources.Res
import com.royalchance.feature.blackjack.resources.blackjack_add_chip
import com.royalchance.feature.blackjack.resources.blackjack_back
import com.royalchance.feature.blackjack.resources.blackjack_bet
import com.royalchance.feature.blackjack.resources.blackjack_card_name
import com.royalchance.feature.blackjack.resources.blackjack_clear_bet
import com.royalchance.feature.blackjack.resources.blackjack_deal
import com.royalchance.feature.blackjack.resources.blackjack_dealer
import com.royalchance.feature.blackjack.resources.blackjack_dealer_total
import com.royalchance.feature.blackjack.resources.blackjack_dismiss
import com.royalchance.feature.blackjack.resources.blackjack_double
import com.royalchance.feature.blackjack.resources.blackjack_doubled
import com.royalchance.feature.blackjack.resources.blackjack_hand_number
import com.royalchance.feature.blackjack.resources.blackjack_hidden_card
import com.royalchance.feature.blackjack.resources.blackjack_hit
import com.royalchance.feature.blackjack.resources.blackjack_limits
import com.royalchance.feature.blackjack.resources.blackjack_notice_forfeited
import com.royalchance.feature.blackjack.resources.blackjack_notice_funds
import com.royalchance.feature.blackjack.resources.blackjack_notice_other_round
import com.royalchance.feature.blackjack.resources.blackjack_notice_settlement
import com.royalchance.feature.blackjack.resources.blackjack_notice_wallet
import com.royalchance.feature.blackjack.resources.blackjack_place_bet
import com.royalchance.feature.blackjack.resources.blackjack_result_blackjack
import com.royalchance.feature.blackjack.resources.blackjack_result_loss
import com.royalchance.feature.blackjack.resources.blackjack_result_push
import com.royalchance.feature.blackjack.resources.blackjack_result_win
import com.royalchance.feature.blackjack.resources.blackjack_rules_hint
import com.royalchance.feature.blackjack.resources.blackjack_split
import com.royalchance.feature.blackjack.resources.blackjack_stand
import com.royalchance.feature.blackjack.resources.blackjack_status_blackjack
import com.royalchance.feature.blackjack.resources.blackjack_status_bust
import com.royalchance.feature.blackjack.resources.blackjack_summary_even
import com.royalchance.feature.blackjack.resources.blackjack_summary_lost
import com.royalchance.feature.blackjack.resources.blackjack_summary_won
import com.royalchance.feature.blackjack.resources.blackjack_title
import com.royalchance.feature.blackjack.resources.blackjack_your_hand
import com.royalchance.feature.blackjack.resources.rank_ace
import com.royalchance.feature.blackjack.resources.rank_eight
import com.royalchance.feature.blackjack.resources.rank_five
import com.royalchance.feature.blackjack.resources.rank_four
import com.royalchance.feature.blackjack.resources.rank_jack
import com.royalchance.feature.blackjack.resources.rank_king
import com.royalchance.feature.blackjack.resources.rank_nine
import com.royalchance.feature.blackjack.resources.rank_queen
import com.royalchance.feature.blackjack.resources.rank_seven
import com.royalchance.feature.blackjack.resources.rank_six
import com.royalchance.feature.blackjack.resources.rank_ten
import com.royalchance.feature.blackjack.resources.rank_three
import com.royalchance.feature.blackjack.resources.rank_two
import com.royalchance.feature.blackjack.resources.suit_clubs
import com.royalchance.feature.blackjack.resources.suit_diamonds
import com.royalchance.feature.blackjack.resources.suit_hearts
import com.royalchance.feature.blackjack.resources.suit_spades
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Fichas para preparar la apuesta. */
private val CHIP_VALUES = listOf(10L, 50L, 100L, 500L, 1_000L, 5_000L)

@Composable
internal fun BlackjackScreen(viewModel: BlackjackViewModel, eventGate: ProgressEventGate, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val casino = RoyalTheme.casinoColors
    // Los avisos de logros esperan a que el crupier termine: no deben adelantar el resultado.
    HoldProgressEvents(eventGate, held = state.animating)

    Column(
        Modifier
            .fillMaxSize()
            .feltBackground(casino.feltBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        RoyalTopBar(
            title = stringResource(Res.string.blackjack_title),
            onBack = onBack,
            backDescription = stringResource(Res.string.blackjack_back),
            windowInsets = WindowInsets(0),
            actions = { state.balance?.let { ChipBalance(it, color = casino.gold, modifier = Modifier.padding(end = RoyalSpacing.l)) } },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardWidth = if (maxWidth < 600.dp) 56.dp else 76.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxSize().padding(horizontal = RoyalSpacing.l),
            ) {
                DealerArea(state, cardWidth)
                Text(
                    text = stringResource(Res.string.blackjack_rules_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = casino.gold.copy(alpha = 0.8f),
                )
                PlayerArea(state, cardWidth)
            }
        }
        Controls(state, viewModel)
    }
}

@Composable
private fun DealerArea(state: BlackjackUiState, cardWidth: Dp) {
    val table = state.table
    val shown = table.dealer.take(state.dealerCardsShown)
    val visibleCards = if (state.holeCardShown) shown else shown.take(1)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        TableLabel(
            text = if (visibleCards.isEmpty()) {
                stringResource(Res.string.blackjack_dealer)
            } else {
                stringResource(Res.string.blackjack_dealer_total, totalText(visibleCards))
            },
        )
        CardRow(
            cards = shown,
            hiddenIndex = if (state.holeCardShown) null else 1,
            cardWidth = cardWidth,
        )
    }
}

@Composable
private fun PlayerArea(state: BlackjackUiState, cardWidth: Dp) {
    val table = state.table
    if (table.hands.isEmpty()) {
        TableLabel(stringResource(Res.string.blackjack_place_bet))
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.l, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        table.hands.forEachIndexed { index, hand ->
            val active = table.phase == BlackjackPhase.PlayerTurn && index == table.activeHand && !state.animating
            HandView(
                hand = hand,
                result = table.results.getOrNull(index)?.takeIf { state.showResults },
                active = active,
                label = if (table.hands.size == 1) {
                    stringResource(Res.string.blackjack_your_hand)
                } else {
                    stringResource(Res.string.blackjack_hand_number, index + 1)
                },
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
private fun HandView(hand: PlayerHand, result: HandResult?, active: Boolean, label: String, cardWidth: Dp) {
    val casino = RoyalTheme.casinoColors
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Black.copy(alpha = if (active) 0.28f else 0.14f),
        border = if (active) BorderStroke(2.dp, casino.gold) else null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
            modifier = Modifier.padding(RoyalSpacing.m).widthIn(min = cardWidth * 2),
        ) {
            TableLabel("$label · ${handStatus(hand)}")
            CardRow(cards = hand.cards, hiddenIndex = null, cardWidth = cardWidth)
            Text(
                text = chipsText(Chips(hand.stake)) + if (hand.doubled) " · " + stringResource(Res.string.blackjack_doubled) else "",
                style = MaterialTheme.typography.labelMedium,
                color = casino.onFelt.copy(alpha = 0.85f),
            )
            result?.let { ResultBadge(it) }
        }
    }
}

@Composable
private fun ResultBadge(result: HandResult) {
    val casino = RoyalTheme.casinoColors
    val (text, color) = when (result.outcome) {
        HandOutcome.Blackjack -> stringResource(Res.string.blackjack_result_blackjack, chipsText(Chips(result.payout))) to casino.gold
        HandOutcome.Win -> stringResource(Res.string.blackjack_result_win, chipsText(Chips(result.payout))) to casino.success
        HandOutcome.Push -> stringResource(Res.string.blackjack_result_push) to casino.onFelt
        HandOutcome.Loss -> stringResource(Res.string.blackjack_result_loss) to casino.suitRed
    }
    Surface(shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.35f), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = RoyalSpacing.m, vertical = RoyalSpacing.xs),
        )
    }
}

@Composable
private fun CardRow(cards: List<Card>, hiddenIndex: Int?, cardWidth: Dp) {
    // Cartas solapadas, como sobre la mesa.
    Row(horizontalArrangement = Arrangement.spacedBy(-cardWidth * 0.45f), modifier = Modifier.heightIn(min = cardWidth * 1.4f)) {
        cards.forEachIndexed { index, card ->
            val hidden = index == hiddenIndex
            PlayingCard(
                rank = card.rank.symbol,
                suit = card.suit.shape,
                description = if (hidden) stringResource(Res.string.blackjack_hidden_card) else cardName(card),
                faceDown = hidden,
                width = cardWidth,
            )
        }
    }
}

@Composable
private fun TableLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = RoyalTheme.casinoColors.onFelt)
}

@Composable
private fun Controls(state: BlackjackUiState, viewModel: BlackjackViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(RoyalSpacing.m),
            modifier = Modifier.padding(RoyalSpacing.l).fillMaxWidth(),
        ) {
            state.notice?.let { notice ->
                InfoBanner(
                    message = stringResource(notice.message),
                    tone = BannerTone.Error,
                    actionLabel = stringResource(Res.string.blackjack_dismiss),
                    onAction = viewModel::dismissNotice,
                )
            }
            if (state.table.phase == BlackjackPhase.PlayerTurn) {
                MoveButtons(state, viewModel)
            } else {
                if (state.showResults && state.table.results.isNotEmpty()) RoundSummary(state)
                BetControls(state, viewModel)
            }
        }
    }
}

@Composable
private fun RoundSummary(state: BlackjackUiState) {
    val table = state.table
    val net = table.totalPayout - table.totalStake
    Text(
        text = when {
            net > 0 -> stringResource(Res.string.blackjack_summary_won, chipsText(Chips(net)))
            net < 0 -> stringResource(Res.string.blackjack_summary_lost, chipsText(Chips(-net)))
            else -> stringResource(Res.string.blackjack_summary_even)
        },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BetControls(state: BlackjackUiState, viewModel: BlackjackViewModel) {
    val rules = state.table.rules
    Text(
        text = stringResource(Res.string.blackjack_bet, chipsText(Chips(state.bet))),
        style = MaterialTheme.typography.titleMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s), verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s)) {
        CHIP_VALUES.forEach { value ->
            BetChip(
                value = value,
                description = stringResource(Res.string.blackjack_add_chip, chipsText(Chips(value))),
                onClick = { viewModel.addChip(value) },
                enabled = state.canBet,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoyalTextButton(text = stringResource(Res.string.blackjack_clear_bet), onClick = viewModel::clearBet, enabled = state.canBet && state.bet > 0)
        Spacer(Modifier.size(RoyalSpacing.m))
        RoyalPrimaryButton(
            text = stringResource(Res.string.blackjack_deal),
            onClick = viewModel::deal,
            enabled = state.canBet && state.bet >= rules.minimumBet,
            loading = state.busy,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = stringResource(Res.string.blackjack_limits, formatGrouped(rules.minimumBet), formatGrouped(rules.maximumBet)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveButtons(state: BlackjackUiState, viewModel: BlackjackViewModel) {
    val moves = state.moves
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RoyalSpacing.s),
        maxItemsInEachRow = 4,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RoyalPrimaryButton(stringResource(Res.string.blackjack_hit), viewModel::hit, Modifier.weight(1f), enabled = BlackjackMove.Hit in moves)
        RoyalPrimaryButton(stringResource(Res.string.blackjack_stand), viewModel::stand, Modifier.weight(1f), enabled = BlackjackMove.Stand in moves)
        RoyalSecondaryButton(stringResource(Res.string.blackjack_double), viewModel::double, Modifier.weight(1f), enabled = BlackjackMove.Double in moves)
        RoyalSecondaryButton(stringResource(Res.string.blackjack_split), viewModel::split, Modifier.weight(1f), enabled = BlackjackMove.Split in moves)
    }
}

// ── Textos ────────────────────────────────────────────────────────────────────────────────────

/**
 * Total de una mano. Mientras el jugador aún decide, una mano blanda muestra sus dos lecturas
 * ("7/17"); una mano cerrada y la del crupier, solo la que cuenta.
 */
private fun totalText(cards: List<Card>, showSoft: Boolean = false): String {
    val value = handValue(cards)
    return if (showSoft && value.soft && value.total < 21) "${value.total - 10}/${value.total}" else value.total.toString()
}

@Composable
private fun handStatus(hand: PlayerHand): String = when {
    hand.isBlackjack -> stringResource(Res.string.blackjack_status_blackjack)
    hand.isBust -> stringResource(Res.string.blackjack_status_bust, hand.value.total)
    else -> totalText(hand.cards, showSoft = !hand.finished)
}

@Composable
private fun cardName(card: Card): String = stringResource(Res.string.blackjack_card_name, stringResource(card.rank.nameRes), stringResource(card.suit.nameRes))

private val Suit.shape: SuitShape
    get() = when (this) {
        Suit.Spades -> SuitShape.Spades
        Suit.Hearts -> SuitShape.Hearts
        Suit.Diamonds -> SuitShape.Diamonds
        Suit.Clubs -> SuitShape.Clubs
    }

private val Suit.nameRes: StringResource
    get() = when (this) {
        Suit.Spades -> Res.string.suit_spades
        Suit.Hearts -> Res.string.suit_hearts
        Suit.Diamonds -> Res.string.suit_diamonds
        Suit.Clubs -> Res.string.suit_clubs
    }

private val Rank.nameRes: StringResource
    get() = when (this) {
        Rank.Two -> Res.string.rank_two
        Rank.Three -> Res.string.rank_three
        Rank.Four -> Res.string.rank_four
        Rank.Five -> Res.string.rank_five
        Rank.Six -> Res.string.rank_six
        Rank.Seven -> Res.string.rank_seven
        Rank.Eight -> Res.string.rank_eight
        Rank.Nine -> Res.string.rank_nine
        Rank.Ten -> Res.string.rank_ten
        Rank.Jack -> Res.string.rank_jack
        Rank.Queen -> Res.string.rank_queen
        Rank.King -> Res.string.rank_king
        Rank.Ace -> Res.string.rank_ace
    }

private val BlackjackNotice.message: StringResource
    get() = when (this) {
        BlackjackNotice.InsufficientFunds -> Res.string.blackjack_notice_funds
        BlackjackNotice.WalletUnavailable -> Res.string.blackjack_notice_wallet
        BlackjackNotice.OtherRoundInProgress -> Res.string.blackjack_notice_other_round
        BlackjackNotice.HandForfeited -> Res.string.blackjack_notice_forfeited
        BlackjackNotice.SettlementPending -> Res.string.blackjack_notice_settlement
    }
