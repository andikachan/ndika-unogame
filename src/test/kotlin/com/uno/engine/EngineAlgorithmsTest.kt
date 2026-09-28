package com.uno.engine

import com.uno.engine.domain.engine.MatchEngine
import com.uno.engine.domain.engine.MatchResult
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.DiscardTop
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.PenaltyState
import com.uno.engine.domain.variants.UnoClassicRuleset
import com.uno.engine.domain.variants.UnoNoMercyRuleset
import com.uno.engine.domain.variants.UnoPartyRuleset
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EngineAlgorithmsTest {

    private val matchEngine = MatchEngine()
    private val classicRuleset = UnoClassicRuleset()
    private val noMercyRuleset = UnoNoMercyRuleset()
    private val partyRuleset = UnoPartyRuleset()

    @Test
    fun `test matching by color and value`() {
        val topCard = GameCard.simple(CardColor.RED, CardValue.FIVE, CardType.NUMBER)
        val discardTop = DiscardTop(
            card = topCard,
            activeColor = CardColor.RED,
            activeFace = topCard.lightSide,
            playedByPlayerId = "p1"
        )
        val penaltyState = PenaltyState()

        // Same color, different value -> Valid
        val sameColorCard = GameCard.simple(CardColor.RED, CardValue.NINE, CardType.NUMBER)
        val res1 = matchEngine.evaluatePlay(sameColorCard, discardTop, penaltyState, CardSide.LIGHT, classicRuleset)
        assertInstanceOf(MatchResult.Valid::class.java, res1)

        // Different color, same value -> Valid
        val sameValueCard = GameCard.simple(CardColor.BLUE, CardValue.FIVE, CardType.NUMBER)
        val res2 = matchEngine.evaluatePlay(sameValueCard, discardTop, penaltyState, CardSide.LIGHT, classicRuleset)
        assertInstanceOf(MatchResult.Valid::class.java, res2)

        // Wild card -> Valid
        val wildCard = GameCard.simple(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD)
        val res3 = matchEngine.evaluatePlay(wildCard, discardTop, penaltyState, CardSide.LIGHT, classicRuleset)
        assertInstanceOf(MatchResult.Valid::class.java, res3)

        // Different color, different value -> Invalid
        val diffCard = GameCard.simple(CardColor.GREEN, CardValue.THREE, CardType.NUMBER)
        val res4 = matchEngine.evaluatePlay(diffCard, discardTop, penaltyState, CardSide.LIGHT, classicRuleset)
        assertInstanceOf(MatchResult.Invalid::class.java, res4)
    }

    @Test
    fun `test stacking rules in Classic vs No Mercy`() {
        val topCard = GameCard.simple(CardColor.RED, CardValue.DRAW_TWO, CardType.ACTION, power = 2, drawPenalty = 2)
        val discardTop = DiscardTop(
            card = topCard,
            activeColor = CardColor.RED,
            activeFace = topCard.lightSide,
            playedByPlayerId = "p1"
        )
        val penaltyState = PenaltyState(accumulatedDrawCount = 2, power = 2, activePenaltyType = CardValue.DRAW_TWO)

        val counterCardTwo = GameCard.simple(CardColor.BLUE, CardValue.DRAW_TWO, CardType.ACTION, power = 2, drawPenalty = 2)
        val counterCardFour = GameCard.simple(CardColor.GREEN, CardValue.DRAW_FOUR, CardType.ACTION, power = 4, drawPenalty = 4)

        // In Classic: Stacking is disabled -> Invalid
        val classicRes = matchEngine.evaluatePlay(counterCardTwo, discardTop, penaltyState, CardSide.LIGHT, classicRuleset)
        assertInstanceOf(MatchResult.Invalid::class.java, classicRes)

        // In No Mercy: Stacking +2 on +2 is Valid
        val noMercyRes2 = matchEngine.evaluatePlay(counterCardTwo, discardTop, penaltyState, CardSide.LIGHT, noMercyRuleset)
        assertInstanceOf(MatchResult.Valid::class.java, noMercyRes2)

        // In No Mercy: Stacking +4 on +2 is Valid (power 4 >= 2)
        val noMercyRes4 = matchEngine.evaluatePlay(counterCardFour, discardTop, penaltyState, CardSide.LIGHT, noMercyRuleset)
        assertInstanceOf(MatchResult.Valid::class.java, noMercyRes4)
    }

    @Test
    fun `test jump in algorithm for Party variant`() {
        val topCard = GameCard.simple(CardColor.BLUE, CardValue.SEVEN, CardType.NUMBER)
        val discardTop = DiscardTop(
            card = topCard,
            activeColor = CardColor.BLUE,
            activeFace = topCard.lightSide,
            playedByPlayerId = "p1"
        )
        val penaltyState = PenaltyState()

        // Exact match (BLUE SEVEN) played out of turn in Party mode -> Valid Jump In
        val exactCard = GameCard.simple(CardColor.BLUE, CardValue.SEVEN, CardType.NUMBER)
        val jumpInRes = matchEngine.evaluatePlay(exactCard, discardTop, penaltyState, CardSide.LIGHT, partyRuleset, isOutOfTurn = true)
        assertInstanceOf(MatchResult.Valid::class.java, jumpInRes)

        // Same number but different color (RED SEVEN) out of turn in Party mode -> Invalid Jump In
        val diffColorCard = GameCard.simple(CardColor.RED, CardValue.SEVEN, CardType.NUMBER)
        val invalidJumpIn = matchEngine.evaluatePlay(diffColorCard, discardTop, penaltyState, CardSide.LIGHT, partyRuleset, isOutOfTurn = true)
        assertInstanceOf(MatchResult.Invalid::class.java, invalidJumpIn)

        // Exact match out of turn in Classic mode -> Invalid (Jump in disabled)
        val classicJumpIn = matchEngine.evaluatePlay(exactCard, discardTop, penaltyState, CardSide.LIGHT, classicRuleset, isOutOfTurn = true)
        assertInstanceOf(MatchResult.Invalid::class.java, classicJumpIn)
    }
}
