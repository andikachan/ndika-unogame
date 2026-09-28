package com.uno.engine.domain.variants

import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.events.HandsSwappedEvent
import com.uno.engine.domain.events.PlayerSkippedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.PlayerStatus
import com.uno.engine.domain.model.Room

class UnoAllWildRuleset : VariantRuleset {
    override val variant: GameVariant = GameVariant.ALL_WILD
    override val initialHandSize: Int = 7
    override val allowsStacking: Boolean = false

    override fun buildDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()

        repeat(68) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD))
        }
        repeat(8) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.REVERSE, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.SKIP, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.DRAW_TWO, CardType.WILD, power = 2, drawPenalty = 2))
        }
        repeat(6) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_SKIP_TWO, CardType.WILD))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_DRAW_FOUR, CardType.WILD, power = 4, drawPenalty = 4))
        }
        repeat(4) {
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_TARGETED_DRAW_FOUR, CardType.WILD, power = 4, drawPenalty = 4))
            deck.add(GameCard.simple(CardColor.WILD, CardValue.WILD_FORCED_SWAP, CardType.WILD))
        }

        return deck // 112 cards total
    }

    override fun isValidMatch(
        topFace: CardFace,
        topActiveColor: CardColor,
        candidateFace: CardFace
    ): Boolean {
        // In All Wild, any card can legally be played at any time
        return true
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
            CardValue.WILD_SKIP_TWO -> {
                val activePlayers = room.getActivePlayers()
                // Advance past 2 players
                val step1 = (room.currentTurnIndex + room.direction.multiplier + activePlayers.size) % activePlayers.size
                val step2 = (step1 + room.direction.multiplier + activePlayers.size) % activePlayers.size
                val skipped1 = activePlayers[step1]
                val skipped2 = activePlayers[step2]
                room.currentTurnIndex = step2
                events.add(PlayerSkippedEvent(room.id, skipped1.id, isAllSkipped = false))
                events.add(PlayerSkippedEvent(room.id, skipped2.id, isAllSkipped = false))
                room.addActionLog("Skip Two! ${skipped1.username} and ${skipped2.username} were skipped")
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
            CardValue.WILD_TARGETED_DRAW_FOUR -> {
                // Target player receives penalty +4 immediately or on next move
                val target = targetPlayerId?.let { room.getPlayer(it) }
                if (target != null && target.status == PlayerStatus.ACTIVE) {
                    room.penaltyState.accumulatedDrawCount = 4
                    room.penaltyState.activePenaltyType = CardValue.WILD_TARGETED_DRAW_FOUR
                    room.penaltyState.targetPlayerId = target.id
                    room.penaltyState.power = 4
                    room.addActionLog("${player.username} targeted ${target.username} with +4 penalty!")
                }
            }
            CardValue.WILD_FORCED_SWAP -> {
                val target = targetPlayerId?.let { room.getPlayer(it) }
                if (target != null && target.id != player.id && target.status == PlayerStatus.ACTIVE) {
                    val tempHand = ArrayList(player.hand)
                    player.hand.clear()
                    player.hand.addAll(target.hand)
                    target.hand.clear()
                    target.hand.addAll(tempHand)
                    events.add(HandsSwappedEvent(room.id, player.id, target.id))
                    room.addActionLog("${player.username} forced a hand swap with ${target.username}!")
                }
            }
            else -> {}
        }

        return events
    }
}
