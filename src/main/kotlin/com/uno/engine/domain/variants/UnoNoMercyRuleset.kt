package com.uno.engine.domain.variants

import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.DiscardAllEvent
import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.events.HandsPassedEvent
import com.uno.engine.domain.events.HandsSwappedEvent
import com.uno.engine.domain.events.PlayerSkippedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room

class UnoNoMercyRuleset : VariantRuleset {
    override val variant: GameVariant = GameVariant.NO_MERCY
    override val initialHandSize: Int = 7
    override val allowsStacking: Boolean = true
    override val has70PassingRules: Boolean = true
    override val mercyEliminationThreshold: Int = 25

    override fun buildDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        val baseColors = listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE)

        for (color in baseColors) {
            // One '0' per color (7-0 passing)
            deck.add(GameCard.simple(color, CardValue.ZERO, CardType.NUMBER))

            // Three of 1-9 per color (27 cards per color = 108 cards)
            for (value in listOf(
                CardValue.ONE, CardValue.TWO, CardValue.THREE, CardValue.FOUR,
                CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT, CardValue.NINE
            )) {
                repeat(3) {
                    deck.add(GameCard.simple(color, value, CardType.NUMBER))
                }
            }

            // Actions (2 each per color: 10 per color = 40 cards)
            repeat(2) {
                deck.add(GameCard.simple(color, CardValue.SKIP, CardType.ACTION))
                deck.add(GameCard.simple(color, CardValue.REVERSE, CardType.ACTION))
                deck.add(GameCard.simple(color, CardValue.DISCARD_ALL, CardType.ACTION))
                deck.add(GameCard.simple(color, CardValue.DRAW_TWO, CardType.ACTION, power = 2, drawPenalty = 2))
                deck.add(GameCard.simple(color, CardValue.DRAW_FOUR, CardType.ACTION, power = 4, drawPenalty = 4))
            }
        }

        // Wilds (16 cards)
        repeat(4) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_DRAW_SIX, CardType.WILD, power = 6, drawPenalty = 6))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.DRAW_TEN, CardType.WILD, power = 10, drawPenalty = 10))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_REVERSE_DRAW_FOUR, CardType.WILD, power = 4, drawPenalty = 4))
        }

        return deck // Exactly 168 cards (4 + 108 + 40 + 16)
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
        // In No Mercy, any draw card equal or greater in power can be stacked
        // Stacking +2 on +2, +4 on +2 or +4, +6 on +4, +10 on +6, etc.
        return candidateFace.power >= currentPenaltyPower && candidateFace.drawPenalty > 0
    }

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
            CardValue.ZERO -> {
                // 7-0 Hand Passing: Pass all hands in current play direction
                executePassHandsInDirection(room)
                events.add(HandsPassedEvent(room.id, room.direction))
                room.addActionLog("Played 0: All players passed hands in direction ${room.direction}")
            }
            CardValue.SEVEN -> {
                // 7-0 Hand Swap: Swap hand with target player
                val target = targetPlayerId?.let { room.getPlayer(it) }
                if (target != null && target.id != player.id && target.status == com.uno.engine.domain.model.PlayerStatus.ACTIVE) {
                    val tempHand = ArrayList(player.hand)
                    player.hand.clear()
                    player.hand.addAll(target.hand)
                    target.hand.clear()
                    target.hand.addAll(tempHand)
                    events.add(HandsSwappedEvent(room.id, player.id, target.id))
                    room.addActionLog("${player.username} swapped hands with ${target.username}")
                }
            }
            CardValue.DISCARD_ALL -> {
                // Discard all cards of matching color from player's hand
                val matchColor = activeFace.color
                val matchingCards = player.hand.filter {
                    val face = it.getActiveFace(room.activeSide)
                    face.color == matchColor && !face.isWild()
                }
                player.hand.removeAll(matchingCards)
                room.discardPile.addAll(matchingCards)
                events.add(DiscardAllEvent(room.id, player.id, matchingCards.size, matchColor))
                room.addActionLog("${player.username} discarded all ${matchingCards.size} cards of color $matchColor")
            }
            CardValue.WILD_REVERSE_DRAW_FOUR -> {
                room.direction = room.direction.reverse()
                events.add(DirectionReversedEvent(room.id, room.direction))
                val penalty = 4
                room.penaltyState.accumulatedDrawCount += penalty
                room.penaltyState.activePenaltyType = CardValue.WILD_REVERSE_DRAW_FOUR
                room.penaltyState.power = maxOf(room.penaltyState.power, 4)
                room.addActionLog("Wild Reverse Draw 4 played! Direction reversed, accumulated penalty: ${room.penaltyState.accumulatedDrawCount}")
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
            CardValue.DRAW_TWO, CardValue.DRAW_FOUR, CardValue.DRAW_SIX, CardValue.DRAW_TEN, CardValue.WILD_DRAW_SIX -> {
                val penalty = activeFace.drawPenalty
                room.penaltyState.accumulatedDrawCount += penalty
                room.penaltyState.activePenaltyType = activeFace.value
                room.penaltyState.power = maxOf(room.penaltyState.power, activeFace.power)
                room.addActionLog("Stacked ${activeFace.value} (+${penalty}). Total penalty: ${room.penaltyState.accumulatedDrawCount}")
            }
            else -> {}
        }

        return events
    }

    private fun executePassHandsInDirection(room: Room) {
        val activePlayers = room.getActivePlayers()
        if (activePlayers.size <= 1) return

        val hands = activePlayers.map { ArrayList(it.hand) }
        val n = activePlayers.size
        for (i in activePlayers.indices) {
            val sourceIndex = if (room.direction == com.uno.engine.domain.model.PlayDirection.CLOCKWISE) {
                (i - 1 + n) % n
            } else {
                (i + 1) % n
            }
            activePlayers[i].hand.clear()
            activePlayers[i].hand.addAll(hands[sourceIndex])
        }
    }
}
