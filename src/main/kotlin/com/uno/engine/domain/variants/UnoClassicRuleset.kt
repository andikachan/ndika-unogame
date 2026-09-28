package com.uno.engine.domain.variants

import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.events.PlayerSkippedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room

class UnoClassicRuleset : VariantRuleset {
    override val variant: GameVariant = GameVariant.CLASSIC
    override val initialHandSize: Int = 7
    override val allowsStacking: Boolean = false
    override val jumpInEnabled: Boolean = false

    override fun buildDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        val baseColors = listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE)

        for (color in baseColors) {
            // One '0' per color
            deck.add(GameCard.simple(color, CardValue.ZERO, CardType.NUMBER))

            // Two of 1-9 per color
            for (value in listOf(
                CardValue.ONE, CardValue.TWO, CardValue.THREE, CardValue.FOUR,
                CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT, CardValue.NINE
            )) {
                deck.add(GameCard.simple(color, value, CardType.NUMBER))
                deck.add(GameCard.simple(color, value, CardType.NUMBER))
            }

            // Two Skips, Reverses, Draw Twos (+2) per color
            for (action in listOf(CardValue.SKIP, CardValue.REVERSE)) {
                deck.add(GameCard.simple(color, action, CardType.ACTION))
                deck.add(GameCard.simple(color, action, CardType.ACTION))
            }

            // Draw Two has penalty 2, power 2
            deck.add(GameCard.simple(color, CardValue.DRAW_TWO, CardType.ACTION, power = 2, drawPenalty = 2))
            deck.add(GameCard.simple(color, CardValue.DRAW_TWO, CardType.ACTION, power = 2, drawPenalty = 2))
        }

        // 4 Wild and 4 Wild Draw Four
        repeat(4) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_DRAW_FOUR, CardType.WILD, power = 4, drawPenalty = 4))
        }

        return deck // 108 cards total
    }

    override fun isValidMatch(
        topFace: CardFace,
        topActiveColor: CardColor,
        candidateFace: CardFace
    ): Boolean {
        if (candidateFace.isWild()) return true
        if (candidateFace.color == topActiveColor) return true
        if (candidateFace.value == topFace.value) return true
        return false
    }

    override fun canCounterPenalty(candidateFace: CardFace, currentPenaltyPower: Int): Boolean {
        return false // Classic does not allow stacking
    }

    override fun calculateDrawPenalty(face: CardFace): Int {
        return face.drawPenalty
    }

    override fun handleCardAction(
        room: Room,
        player: Player,
        card: GameCard,
        activeFace: CardFace,
        chosenColor: CardColor?,
        targetPlayerId: String?
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        when (activeFace.value) {
            CardValue.REVERSE -> {
                room.direction = room.direction.reverse()
                events.add(DirectionReversedEvent(room.id, room.direction))
                room.addActionLog("Direction reversed to ${room.direction}")
            }
            CardValue.SKIP -> {
                val activePlayers = room.getActivePlayers()
                val nextIndex = (room.currentTurnIndex + room.direction.multiplier + activePlayers.size) % activePlayers.size
                val skipped = activePlayers[nextIndex]
                room.currentTurnIndex = nextIndex // Skip the player
                events.add(PlayerSkippedEvent(room.id, skipped.id, isAllSkipped = false))
                room.addActionLog("Player ${skipped.username} was skipped")
            }
            CardValue.DRAW_TWO -> {
                room.penaltyState.accumulatedDrawCount = 2
                room.penaltyState.activePenaltyType = CardValue.DRAW_TWO
                room.penaltyState.power = 2
                room.addActionLog("Penalty +2 set on next player")
            }
            CardValue.WILD_DRAW_FOUR -> {
                room.penaltyState.accumulatedDrawCount = 4
                room.penaltyState.activePenaltyType = CardValue.WILD_DRAW_FOUR
                room.penaltyState.power = 4
                room.addActionLog("Wild +4 penalty set on next player")
            }
            else -> {
                // No special state changes for standard numbers and regular wild
            }
        }

        return events
    }
}
