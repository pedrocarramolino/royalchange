package com.royalchance.engine.blackjack

import com.royalchance.core.common.result.Outcome
import com.royalchance.core.testing.random.TestRandomGenerator
import com.royalchance.engine.cards.Card
import com.royalchance.engine.cards.Rank
import com.royalchance.engine.cards.Shoe
import com.royalchance.engine.cards.Suit
import com.royalchance.engine.cards.standardDeck
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlackjackEngineTest {

    private val random = TestRandomGenerator(seed = 42)

    /** "AS" = as de picas, "10H" = diez de corazones, "KD" = rey de diamantes… */
    private fun card(code: String): Card {
        val rank = Rank.entries.first { it.symbol == code.dropLast(1) }
        val suit = Suit.entries.first { it.name.first() == code.last() }
        return Card(rank, suit)
    }

    /**
     * Mesa con el zapato preparado. Orden del reparto: jugador, crupier (vista), jugador, crupier
     * (oculta) y después las cartas que se pidan. Detrás va una baraja de relleno.
     */
    private fun table(vararg codes: String): BlackjackState {
        val cards = codes.map(::card) + standardDeck()
        return BlackjackState(shoe = Shoe(cards = cards, cutIndex = cards.size))
    }

    private fun BlackjackState.play(action: BlackjackAction): BlackjackStep =
        assertIs<Outcome.Success<BlackjackStep>>(BlackjackEngine.apply(this, action, random)).value

    private fun BlackjackState.fails(action: BlackjackAction): BlackjackError =
        assertIs<Outcome.Failure<BlackjackError>>(BlackjackEngine.apply(this, action, random)).error

    private fun BlackjackState.deal(bet: Long = 100) = play(BlackjackAction.Deal(bet)).state

    @Test
    fun handValuesCountAcesAsElevenWhenTheyFit() {
        assertEquals(HandValue(21, soft = true), handValue(listOf(card("AS"), card("KD"))))
        assertEquals(HandValue(17, soft = true), handValue(listOf(card("AS"), card("6H"))))
        assertEquals(HandValue(17, soft = false), handValue(listOf(card("AS"), card("6H"), card("10C"))))
        assertEquals(HandValue(12, soft = true), handValue(listOf(card("AS"), card("AH"))))
        assertEquals(HandValue(22, soft = false), handValue(listOf(card("KS"), card("QH"), card("2C"))))
    }

    @Test
    fun aNaturalBlackjackPaysThreeToTwoAtOnce() {
        val step = table("AS", "9D", "KH", "7C").play(BlackjackAction.Deal(100))

        assertEquals(BlackjackPhase.RoundOver, step.state.phase)
        assertEquals(listOf(HandResult(HandOutcome.Blackjack, 250)), step.state.results)
        assertTrue(BlackjackEvent.HoleCardRevealed in step.events)
    }

    @Test
    fun blackjackAgainstDealerBlackjackIsAPush() {
        val state = table("AS", "AD", "KH", "QC").deal()
        assertEquals(listOf(HandResult(HandOutcome.Push, 100)), state.results)
    }

    @Test
    fun dealerBlackjackEndsTheRoundBeforeThePlayerActs() {
        val state = table("KS", "AD", "QH", "JC").deal()

        assertEquals(BlackjackPhase.RoundOver, state.phase)
        assertEquals(listOf(HandResult(HandOutcome.Loss, 0)), state.results)
        assertEquals(emptySet(), state.availableMoves)
    }

    @Test
    fun bustingLosesAndTheDealerDoesNotDraw() {
        val step = table("10S", "9D", "6H", "7C", "KH").deal().play(BlackjackAction.Hit)

        assertTrue(BlackjackEvent.HandBusted(0) in step.events)
        assertEquals(listOf(HandResult(HandOutcome.Loss, 0)), step.state.results)
        assertEquals(2, step.state.dealer.size)
    }

    @Test
    fun dealerStandsOnSoftSeventeen() {
        val state = table("10S", "AD", "8H", "6C").deal().play(BlackjackAction.Stand).state

        assertEquals(2, state.dealer.size)
        assertEquals(listOf(HandResult(HandOutcome.Win, 200)), state.results)
    }

    @Test
    fun dealerHitsSixteenAndCanBust() {
        val step = table("10S", "10D", "8H", "6C", "KH").deal().play(BlackjackAction.Stand)

        assertTrue(BlackjackEvent.DealerBusted in step.events)
        assertEquals(listOf(HandResult(HandOutcome.Win, 200)), step.state.results)
    }

    @Test
    fun equalTotalsPushAndLowerLoses() {
        assertEquals(HandOutcome.Push, table("10S", "10D", "9H", "9C").deal().play(BlackjackAction.Stand).state.results.single().outcome)
        assertEquals(HandOutcome.Loss, table("10S", "10D", "8H", "9C").deal().play(BlackjackAction.Stand).state.results.single().outcome)
    }

    @Test
    fun hittingToTwentyOneStandsAutomatically() {
        val state = table("5S", "10D", "6H", "7C", "KH").deal().play(BlackjackAction.Hit).state

        assertEquals(BlackjackPhase.RoundOver, state.phase)
        assertEquals(HandOutcome.Win, state.results.single().outcome)
    }

    @Test
    fun doublingDoublesTheStakeAndDealsExactlyOneCard() {
        val dealt = table("6S", "10D", "5H", "7C", "10H").deal()
        assertEquals(100, dealt.stakeRequiredFor(BlackjackAction.Double))

        val state = dealt.play(BlackjackAction.Double).state

        assertEquals(200, state.hands.single().stake)
        assertEquals(3, state.hands.single().cards.size)
        assertEquals(listOf(HandResult(HandOutcome.Win, 400)), state.results)
    }

    @Test
    fun doublingNeedsTheFirstTwoCards() {
        val afterHit = table("2S", "10D", "3H", "7C", "4H").deal().play(BlackjackAction.Hit).state

        assertFalse(BlackjackMove.Double in afterHit.availableMoves)
        assertEquals(BlackjackError.NotAllowed, afterHit.fails(BlackjackAction.Double))
    }

    @Test
    fun splittingPlaysEachHandSeparately() {
        // 8-8 contra 10-7: primera mano 8+3 dobla con 10 (21), segunda 8+K se planta (18).
        val dealt = table("8S", "10D", "8H", "7C", "3H", "KC", "10S").deal()
        assertTrue(BlackjackMove.Split in dealt.availableMoves)

        val split = dealt.play(BlackjackAction.Split).state
        assertEquals(2, split.hands.size)
        assertEquals(listOf(card("8S"), card("3H")), split.hands[0].cards)
        assertEquals(listOf(card("8H"), card("KC")), split.hands[1].cards)

        val doubled = split.play(BlackjackAction.Double).state
        assertEquals(1, doubled.activeHand)
        val settled = doubled.play(BlackjackAction.Stand).state

        assertEquals(listOf(HandResult(HandOutcome.Win, 400), HandResult(HandOutcome.Win, 200)), settled.results)
        assertEquals(300, settled.totalStake)
    }

    @Test
    fun twentyOneAfterSplittingIsNotABlackjack() {
        val state = table("KS", "9D", "KH", "8C", "AH", "7C").deal()
            .play(BlackjackAction.Split).state
            .play(BlackjackAction.Stand).state

        // K-A tras separar paga 1:1, no 3:2.
        assertEquals(HandResult(HandOutcome.Win, 200), state.results[0])
    }

    @Test
    fun splitAcesGetOneCardEachAndCannotBeSplitAgain() {
        val state = table("AS", "9D", "AH", "8C", "AD", "5C").deal().play(BlackjackAction.Split).state

        assertEquals(BlackjackPhase.RoundOver, state.phase)
        assertEquals(listOf(2, 2), state.hands.map { it.cards.size })
        assertTrue(state.hands.all { it.fromSplit })
    }

    @Test
    fun atMostFourHands() {
        var state = table("8S", "10D", "8H", "7C", "8D", "8C", "8S", "8H", "2C", "2D").deal()
        repeat(3) { state = state.play(BlackjackAction.Split).state }

        assertEquals(4, state.hands.size)
        assertFalse(BlackjackMove.Split in state.availableMoves)
    }

    @Test
    fun betsMustRespectTheTableLimits() {
        val fresh = table("10S", "9D", "8H", "7C")
        assertEquals(BlackjackError.InvalidBet, fresh.fails(BlackjackAction.Deal(5)))
        assertEquals(BlackjackError.InvalidBet, fresh.fails(BlackjackAction.Deal(15)))
        assertEquals(BlackjackError.InvalidBet, fresh.fails(BlackjackAction.Deal(10_010)))
        assertEquals(BlackjackError.NotAllowed, fresh.fails(BlackjackAction.Hit))
        assertEquals(BlackjackError.NotAllowed, fresh.deal().fails(BlackjackAction.Deal(100)))
    }

    @Test
    fun theShoeIsShuffledOnFirstDealAndAfterTheCutCard() {
        val first = BlackjackState().play(BlackjackAction.Deal(10))
        assertEquals(BlackjackEvent.ShoeShuffled, first.events.first())
        assertEquals(312, first.state.shoe?.cards?.size)

        val pastCut = table("10S", "9D", "8H", "7C").let { it.copy(shoe = it.shoe?.copy(cutIndex = 1, position = 1)) }
        assertEquals(BlackjackEvent.ShoeShuffled, pastCut.play(BlackjackAction.Deal(10)).events.first())
    }

    @Test
    fun stateSurvivesSerialization() {
        val inProgress = table("8S", "10D", "8H", "7C", "3H", "KC").deal().play(BlackjackAction.Split).state

        val restored = Json.decodeFromString(BlackjackState.serializer(), Json.encodeToString(BlackjackState.serializer(), inProgress))

        assertEquals(inProgress, restored)
        assertEquals(inProgress.availableMoves, restored.availableMoves)
    }

    @Test
    fun thousandsOfRandomRoundsKeepTheInvariants() {
        val rng = TestRandomGenerator(seed = 7)
        var state = BlackjackState()
        repeat(5_000) {
            state = state.play(BlackjackAction.Deal(bet = 10L * (1 + rng.nextInt(100)))).state
            while (state.phase == BlackjackPhase.PlayerTurn) {
                val moves = state.availableMoves.toList()
                val action = when (moves[rng.nextInt(moves.size)]) {
                    BlackjackMove.Hit -> BlackjackAction.Hit
                    BlackjackMove.Stand -> BlackjackAction.Stand
                    BlackjackMove.Double -> BlackjackAction.Double
                    BlackjackMove.Split -> BlackjackAction.Split
                }
                state = state.play(action).state
            }
            assertEquals(state.hands.size, state.results.size)
            assertTrue(state.hands.size <= 4)
            assertTrue(state.totalStake <= 80_000)
            assertTrue(state.results.all { it.payout >= 0 && it.payout <= 3 * state.totalStake })
            assertTrue(state.hands.all { hand -> !hand.isBust || state.results[state.hands.indexOf(hand)].payout == 0L })
        }
    }
}
