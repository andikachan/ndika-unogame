package com.uno.engine.domain.variants

import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.events.PlayerSkippedEvent
import com.uno.engine.domain.events.SideFlippedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room

class UnoFlipRuleset : VariantRuleset {
    override val variant: GameVariant = GameVariant.FLIP
    override val initialHandSize: Int = 7
    override val allowsStacking: Boolean = false
    override val hasFlipMechanic: Boolean = true

    override fun buildDeck(): List<GameCard> {
        val deck = mutableListOf<GameCard>()
        val lightColors = listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE)
        val darkColors = listOf(CardColor.DARK_TEAL, CardColor.DARK_ORANGE, CardColor.DARK_PINK, CardColor.DARK_PURPLE)

        for (i in lightColors.indices) {
            val lColor = lightColors[i]
            val dColor = darkColors[i]

            // 1-9 dual sided (2 each per color = 18 * 4 = 72 cards)
            val numValues = listOf(
                CardValue.ONE, CardValue.TWO, CardValue.THREE, CardValue.FOUR,
                CardValue.FIVE, CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT, CardValue.NINE
            )
            for (value in numValues) {
                repeat(2) {
                    deck.add(
                        GameCard.dual(
                            light = CardFace(lColor, value, CardType.NUMBER),
                            dark = CardFace(dColor, value, CardType.NUMBER)
                        )
                    )
                }
            }

            // Light: Draw One (+1), Dark: Draw Five (+5) (2 each)
            repeat(2) {
                deck.add(
                    GameCard.dual(
                        light = CardFace(lColor, CardValue.DRAW_ONE, CardType.ACTION, power = 1, drawPenalty = 1),
                        dark = CardFace(dColor, CardValue.DRAW_FIVE, CardType.ACTION, power = 5, drawPenalty = 5)
                    )
                )
            }

            // Light: Reverse, Dark: Reverse (2 each)
            repeat(2) {
                deck.add(
                    GameCard.dual(
                        light = CardFace(lColor, CardValue.REVERSE, CardType.ACTION),
                        dark = CardFace(dColor, CardValue.REVERSE, CardType.ACTION)
                    )
                )
            }

            // Light: Skip, Dark: Skip Everyone (2 each)
            repeat(2) {
                deck.add(
                    GameCard.dual(
                        light = CardFace(lColor, CardValue.SKIP, CardType.ACTION),
                        dark = CardFace(dColor, CardValue.SKIP_EVERYONE, CardType.ACTION)
                    )
                )
            }

            // Light: Flip, Dark: Flip (2 each)
            repeat(2) {
                deck.add(
                    GameCard.dual(
                        light = CardFace(lColor, CardValue.FLIP, CardType.ACTION),
                        dark = CardFace(dColor, CardValue.FLIP, CardType.ACTION)
                    )
                )
            }
        }

        // 4 Wild (Light: Wild, Dark: Wild)
        repeat(4) {
            deck.add(
                GameCard.dual(
                    light = CardFace(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD),
                    dark = CardFace(CardColor.WILD, CardValue.WILD_STANDARD, CardType.WILD)
                )
            )
        }

        // 4 Wild Draw 2 (Light: Wild Draw Two, Dark: Wild Draw Color)
        repeat(4) {
            deck.add(
                GameCard.dual(
                    light = CardFace(CardColor.WILD, CardValue.WILD_DRAW_TWO, CardType.WILD, power = 2, drawPenalty = 2),
                    dark = CardFace(CardColor.WILD, CardValue.WILD_DRAW_COLOR, CardType.WILD, power = 99, drawPenalty = 1)
                )
            )
        }

        return deck // 112 cards total
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
            CardValue.FLIP -> {
                room.activeSide = if (room.activeSide == CardSide.LIGHT) CardSide.DARK else CardSide.LIGHT
                events.add(SideFlippedEvent(room.id, room.activeSide))
                room.addActionLog("Side flipped to ${room.activeSide}")
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
            CardValue.SKIP_EVERYONE -> {
                // Skips everyone, so it's the same player's turn again!
                // We accomplish this by advancing turn back to the active player
                events.add(PlayerSkippedEvent(room.id, player.id, isAllSkipped = true))
                room.addActionLog("All other players were skipped! ${player.username} plays again.")
            }
            CardValue.DRAW_ONE -> {
                room.penaltyState.accumulatedDrawCount = 1
                room.penaltyState.activePenaltyType = CardValue.DRAW_ONE
                room.penaltyState.power = 1
            }
            CardValue.WILD_DRAW_TWO -> {
                room.penaltyState.accumulatedDrawCount = 2
                room.penaltyState.activePenaltyType = CardValue.WILD_DRAW_TWO
                room.penaltyState.power = 2
            }
            CardValue.DRAW_FIVE -> {
                room.penaltyState.accumulatedDrawCount = 5
                room.penaltyState.activePenaltyType = CardValue.DRAW_FIVE
                room.penaltyState.power = 5
            }
            CardValue.WILD_DRAW_COLOR -> {
                // Wild Draw Color: victim draws until they pull a card of the chosen color
                room.penaltyState.accumulatedDrawCount = -1 // Special flag for draw until color
                room.penaltyState.activePenaltyType = CardValue.WILD_DRAW_COLOR
                room.penaltyState.power = 99
            }
            else -> {}
        }

        return events
    }
}
