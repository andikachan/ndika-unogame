package com.uno.engine.domain.variants

import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.DrawnTogetherLinkedEvent
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

class UnoPartyRuleset : VariantRuleset {
    override val variant: GameVariant = GameVariant.PARTY
    override val initialHandSize: Int = 7
    override val allowsStacking: Boolean = false
    override val jumpInEnabled: Boolean = true
    override val hasPartyMechanics: Boolean = true

    override fun buildDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        val baseColors = listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE)

        // Party deck has standard cards x 2 sets for high player counts + Party action cards
        repeat(2) {
            for (color in baseColors) {
                deck.add(GameCard.simple(color, CardValue.ZERO, CardType.NUMBER))
                for (value in listOf(
                    CardValue.ONE, CardValue.TWO, CardValue.THREE, CardValue.FOUR,
                    CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT, CardValue.NINE
                )) {
                    deck.add(GameCard.simple(color, value, CardType.NUMBER))
                    deck.add(GameCard.simple(color, value, CardType.NUMBER))
                }
                deck.add(GameCard.simple(color, CardValue.SKIP, CardType.ACTION))
                deck.add(GameCard.simple(color, CardValue.REVERSE, CardType.ACTION))
                deck.add(GameCard.simple(color, CardValue.DRAW_TWO, CardType.ACTION, power = 2, drawPenalty = 2))
                deck.add(GameCard.simple(color, CardValue.POINT_TAKEN, CardType.ACTION))
                deck.add(GameCard.simple(color, CardValue.DRAWN_TOGETHER, CardType.ACTION))
            }
        }

        repeat(8) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_DRAW_FOUR, CardType.WILD, power = 4, drawPenalty = 4))
        }

        return deck
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

    override fun canCounterPenalty(candidateFace: CardFace, currentPenaltyPower: Int): Boolean = false

    override fun calculateDrawPenalty(face: CardFace): Int = face.drawPenalty

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
                room.currentTurnIndex = nextIndex
                events.add(PlayerSkippedEvent(room.id, skipped.id, isAllSkipped = false))
                room.addActionLog("Player ${skipped.username} was skipped")
            }
            CardValue.DRAW_TWO -> {
                room.penaltyState.accumulatedDrawCount = 2
                room.penaltyState.activePenaltyType = CardValue.DRAW_TWO
                room.penaltyState.power = 2
            }
            CardValue.WILD_DRAW_FOUR -> {
                room.penaltyState.accumulatedDrawCount = 4
                room.penaltyState.activePenaltyType = CardValue.WILD_DRAW_FOUR
                room.penaltyState.power = 4
            }
            CardValue.DRAWN_TOGETHER -> {
                // Link active player with chosen target player
                val target = targetPlayerId?.let { room.getPlayer(it) }
                if (target != null && target.id != player.id) {
                    player.linkedPartnerId = target.id
                    target.linkedPartnerId = player.id
                    events.add(DrawnTogetherLinkedEvent(room.id, player.id, target.id))
                    room.addActionLog("${player.username} and ${target.username} are now Drawn Together!")
                }
            }
            CardValue.POINT_TAKEN -> {
                room.addActionLog("Point Taken played! All players point to a target.")
            }
            else -> {}
        }

        return events
    }
}
