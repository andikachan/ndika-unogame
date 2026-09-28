package com.uno.engine.domain.engine

import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.DiscardTop
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.PenaltyState
import com.uno.engine.domain.variants.VariantRuleset
import org.springframework.stereotype.Component

sealed class MatchResult {
    data object Valid : MatchResult()
    data class Invalid(val reason: String) : MatchResult()
}

@Component
class MatchEngine {

    fun evaluatePlay(
        candidateCard: GameCard,
        discardTop: DiscardTop,
        penaltyState: PenaltyState,
        activeSide: CardSide,
        ruleset: VariantRuleset,
        isOutOfTurn: Boolean = false
    ): MatchResult {
        val candFace = candidateCard.getActiveFace(activeSide)
        val topFace = discardTop.activeFace
        val activeColor = discardTop.activeColor

        // 1. Check penalty stacking state
        if (penaltyState.hasPenalty()) {
            if (ruleset.allowsStacking) {
                if (ruleset.canCounterPenalty(candFace, penaltyState.power)) {
                    return MatchResult.Valid
                } else {
                    return MatchResult.Invalid(
                        "Cannot play ${candFace.value}: Active draw penalty of +${penaltyState.accumulatedDrawCount} requires equal or higher counter card"
                    )
                }
            } else {
                return MatchResult.Invalid(
                    "Stacking is disabled in ${ruleset.variant}. Must draw penalty of +${penaltyState.accumulatedDrawCount}"
                )
            }
        }

        // 2. Out-of-turn play / Jump-In evaluation
        if (isOutOfTurn) {
            if (ruleset.jumpInEnabled) {
                // Exact match: Color AND Value must match the top discard card exactly
                val exactColorMatch = (candFace.color == activeColor || (candFace.isWild() && topFace.isWild()))
                val exactValueMatch = (candFace.value == topFace.value)
                if (exactColorMatch && exactValueMatch) {
                    return MatchResult.Valid
                } else {
                    return MatchResult.Invalid("Jump-In requires exact color and value match ($activeColor ${topFace.value})")
                }
            } else {
                return MatchResult.Invalid("Jump-In / out-of-turn play is disabled in ${ruleset.variant}")
            }
        }

        // 3. Normal in-turn matching
        if (candFace.isWild()) {
            return MatchResult.Valid
        }

        if (ruleset.isValidMatch(topFace, activeColor, candFace)) {
            return MatchResult.Valid
        }

        return MatchResult.Invalid(
            "Card ${candFace.color} ${candFace.value} does not match active color $activeColor or value ${topFace.value}"
        )
    }
}
