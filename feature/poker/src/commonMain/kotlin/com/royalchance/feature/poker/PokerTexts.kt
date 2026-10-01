package com.royalchance.feature.poker

import androidx.compose.runtime.Composable
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Suit
import com.royalchance.engine.poker.HandCategory
import com.royalchance.engine.poker.SeatAction
import com.royalchance.feature.poker.resources.Res
import com.royalchance.feature.poker.resources.poker_action_all_in
import com.royalchance.feature.poker.resources.poker_action_bet
import com.royalchance.feature.poker.resources.poker_action_big_blind
import com.royalchance.feature.poker.resources.poker_action_call
import com.royalchance.feature.poker.resources.poker_action_check
import com.royalchance.feature.poker.resources.poker_action_fold
import com.royalchance.feature.poker.resources.poker_action_raise
import com.royalchance.feature.poker.resources.poker_action_small_blind
import com.royalchance.feature.poker.resources.poker_card_name
import com.royalchance.feature.poker.resources.poker_hand_flush
import com.royalchance.feature.poker.resources.poker_hand_four
import com.royalchance.feature.poker.resources.poker_hand_full_house
import com.royalchance.feature.poker.resources.poker_hand_high_card
import com.royalchance.feature.poker.resources.poker_hand_pair
import com.royalchance.feature.poker.resources.poker_hand_straight
import com.royalchance.feature.poker.resources.poker_hand_straight_flush
import com.royalchance.feature.poker.resources.poker_hand_three
import com.royalchance.feature.poker.resources.poker_hand_two_pair
import com.royalchance.feature.poker.resources.poker_rank_ace
import com.royalchance.feature.poker.resources.poker_rank_jack
import com.royalchance.feature.poker.resources.poker_rank_king
import com.royalchance.feature.poker.resources.poker_rank_queen
import com.royalchance.feature.poker.resources.poker_suit_clubs
import com.royalchance.feature.poker.resources.poker_suit_diamonds
import com.royalchance.feature.poker.resources.poker_suit_hearts
import com.royalchance.feature.poker.resources.poker_suit_spades
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun actionLabel(action: SeatAction): String = stringResource(
    when (action) {
        SeatAction.SmallBlind -> Res.string.poker_action_small_blind
        SeatAction.BigBlind -> Res.string.poker_action_big_blind
        SeatAction.Fold -> Res.string.poker_action_fold
        SeatAction.Check -> Res.string.poker_action_check
        SeatAction.Call -> Res.string.poker_action_call
        SeatAction.Bet -> Res.string.poker_action_bet
        SeatAction.Raise -> Res.string.poker_action_raise
        SeatAction.AllIn -> Res.string.poker_action_all_in
    },
)

@Composable
internal fun categoryName(category: HandCategory): String = stringResource(
    when (category) {
        HandCategory.HighCard -> Res.string.poker_hand_high_card
        HandCategory.OnePair -> Res.string.poker_hand_pair
        HandCategory.TwoPair -> Res.string.poker_hand_two_pair
        HandCategory.ThreeOfAKind -> Res.string.poker_hand_three
        HandCategory.Straight -> Res.string.poker_hand_straight
        HandCategory.Flush -> Res.string.poker_hand_flush
        HandCategory.FullHouse -> Res.string.poker_hand_full_house
        HandCategory.FourOfAKind -> Res.string.poker_hand_four
        HandCategory.StraightFlush -> Res.string.poker_hand_straight_flush
    },
)

/** "As de picas", "10 de corazones". */
@Composable
internal fun cardName(card: Card): String {
    val rank = when (card.rank) {
        Rank.Ace -> stringResource(Res.string.poker_rank_ace)
        Rank.King -> stringResource(Res.string.poker_rank_king)
        Rank.Queen -> stringResource(Res.string.poker_rank_queen)
        Rank.Jack -> stringResource(Res.string.poker_rank_jack)
        else -> card.rank.symbol
    }
    return stringResource(Res.string.poker_card_name, rank, stringResource(card.suit.nameRes))
}

private val Suit.nameRes: StringResource
    get() = when (this) {
        Suit.Spades -> Res.string.poker_suit_spades
        Suit.Hearts -> Res.string.poker_suit_hearts
        Suit.Diamonds -> Res.string.poker_suit_diamonds
        Suit.Clubs -> Res.string.poker_suit_clubs
    }
