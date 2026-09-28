package com.uno.engine.domain.variants

import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.events.PlayerSkippedEvent
import com.uno.engine.domain.events.ShowdownStartedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import com.uno.engine.domain.model.RoomStatus
import com.uno.engine.domain.model.ShowdownState

class UnoShowdownRuleset : VariantRuleset {
    override val variant: GameVariant = GameVariant.SHOWDOWN
    override val initialHandSize: Int = 7
    override val allowsStacking: Boolean = false
    override val hasShowdownMechanic: Boolean = true

    override fun buildDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        val baseColors = listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE)

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
            // Showdown action cards
            deck.add(GameCard.simple(color, CardValue.SHOWDOWN, CardType.ACTION))
        }

        repeat(4) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_DRAW_FOUR, CardType.WILD, power = 4, drawPenalty = 4))
        }

        return deck // 112 cards
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
            CardValue.SHOWDOWN -> {
                val activePlayers = room.getActivePlayers()
                val nextIndex = (room.currentTurnIndex + room.direction.multiplier + activePlayers.size) % activePlayers.size
                val targetPlayer = activePlayers[nextIndex]

                room.showdownState = ShowdownState(
                    initiatorPlayerId = player.id,
                    targetPlayerId = targetPlayer.id,
                    duelStartTime = System.currentTimeMillis() + 1000, // 1 second countdown
                    duelDurationMs = 3000,
                    cardsAtStake = 2
                )
                room.status = RoomStatus.SHOWDOWN_DUEL
                events.add(ShowdownStartedEvent(room.id, player.id, targetPlayer.id, 3000))
                room.addActionLog("SHOWDOWN DUEL initiated between ${player.username} and ${targetPlayer.username}!")
            }
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
            else -> {}
        }

        return events
    }
}
