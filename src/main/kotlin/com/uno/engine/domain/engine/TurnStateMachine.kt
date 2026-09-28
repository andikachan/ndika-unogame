package com.uno.engine.domain.engine

import com.uno.engine.domain.events.CardDrawnEvent
import com.uno.engine.domain.events.CardPlayedEvent
import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.events.GameOverEvent
import com.uno.engine.domain.events.GameStartedEvent
import com.uno.engine.domain.events.PlayerEliminatedEvent
import com.uno.engine.domain.events.TurnChangedEvent
import com.uno.engine.domain.events.UnoCalledEvent
import com.uno.engine.domain.events.UnoPenaltyAppliedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.DiscardTop
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.PlayerStatus
import com.uno.engine.domain.model.Room
import com.uno.engine.domain.model.RoomStatus
import com.uno.engine.domain.variants.VariantRegistry
import org.springframework.stereotype.Component

@Component
class TurnStateMachine(
    private val variantRegistry: VariantRegistry,
    private val matchEngine: MatchEngine,
    private val shuffleEngine: ShuffleEngine
) {
    companion object {
        const val DEFAULT_TURN_TIMEOUT_MS = 15_000L
        const val UNO_CHALLENGE_WINDOW_MS = 3_000L
    }

    fun startGame(room: Room): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val ruleset = variantRegistry.getRuleset(room.variant)

        room.status = RoomStatus.DEALING
        room.activeSide = CardSide.LIGHT
        room.direction = com.uno.engine.domain.model.PlayDirection.CLOCKWISE
        room.penaltyState.reset()
        room.actionLogs.clear()

        // 1. Build and shuffle deck
        val fullDeck = ruleset.buildDeck().toMutableList()
        shuffleEngine.shuffle(fullDeck)
        room.drawPile.clear()
        room.drawPile.addAll(fullDeck)
        room.discardPile.clear()

        // 2. Deal hands to all active players
        val activePlayers = room.getActivePlayers()
        require(activePlayers.size >= 2) { "At least 2 players are required to start a game" }

        for (player in activePlayers) {
            player.hand.clear()
            player.status = PlayerStatus.ACTIVE
            player.isUnoCalled = false
            player.unoWindowOpenUntilTimestamp = null
            player.linkedPartnerId = null

            val dealtCards = mutableListOf<GameCard>()
            repeat(ruleset.initialHandSize) {
                if (room.drawPile.isNotEmpty()) {
                    dealtCards.add(room.drawPile.removeAt(0))
                }
            }
            player.addCards(dealtCards)
        }

        // 3. Flip initial card for Discard Pile (must not be an action or wild for clean start, or choose safe top)
        var initialCard = room.drawPile.removeAt(0)
        var initialFace = initialCard.getActiveFace(room.activeSide)

        // Reshuffle if initial card is wild or high penalty in classic/flip to ensure standard opening
        var attempts = 0
        while (initialFace.isWild() && attempts < 10 && room.drawPile.isNotEmpty()) {
            room.drawPile.add(initialCard)
            shuffleEngine.shuffle(room.drawPile)
            initialCard = room.drawPile.removeAt(0)
            initialFace = initialCard.getActiveFace(room.activeSide)
            attempts++
        }

        val initialColor = if (initialFace.color != CardColor.WILD) initialFace.color else CardColor.RED
        room.discardPile.add(initialCard)
        room.discardTop = DiscardTop(
            card = initialCard,
            activeColor = initialColor,
            activeFace = initialFace,
            playedByPlayerId = "SYSTEM"
        )

        room.currentTurnIndex = 0
        room.status = RoomStatus.AWAITING_MOVE
        room.turnExpiresAt = System.currentTimeMillis() + DEFAULT_TURN_TIMEOUT_MS

        val firstPlayer = activePlayers[0]
        events.add(GameStartedEvent(room.id, initialFace, initialColor, firstPlayer.id))
        room.addActionLog("Game started with variant ${room.variant}. Top card is $initialColor ${initialFace.value}. Turn: ${firstPlayer.username}")

        return events
    }

    fun playCard(
        room: Room,
        playerId: String,
        cardId: String,
        chosenColor: CardColor?,
        targetPlayerId: String? = null,
        isUnoCalledWithPlay: Boolean = false
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val ruleset = variantRegistry.getRuleset(room.variant)
        val player = room.getPlayer(playerId) ?: throw IllegalArgumentException("Player $playerId not found in room")

        if (player.status != PlayerStatus.ACTIVE) {
            throw IllegalStateException("Player is not active in this game")
        }

        val currentPlayer = room.getCurrentPlayer()
        val isOutOfTurn = currentPlayer?.id != playerId

        // Check if Jump-In is allowed
        if (isOutOfTurn && !ruleset.jumpInEnabled) {
            throw IllegalStateException("It is not player ${player.username}'s turn")
        }

        val card = player.findCard(cardId) ?: throw IllegalArgumentException("Player does not hold card $cardId")
        val discardTop = room.discardTop ?: throw IllegalStateException("No top card on discard pile")

        // Validate play with match engine
        val matchResult = matchEngine.evaluatePlay(
            candidateCard = card,
            discardTop = discardTop,
            penaltyState = room.penaltyState,
            activeSide = room.activeSide,
            ruleset = ruleset,
            isOutOfTurn = isOutOfTurn
        )

        when (matchResult) {
            is MatchResult.Invalid -> throw IllegalStateException(matchResult.reason)
            is MatchResult.Valid -> {}
        }

        // Active face of played card
        val activeFace = card.getActiveFace(room.activeSide)

        // Determine active color for the discard pile
        val finalActiveColor = if (activeFace.isWild()) {
            chosenColor ?: CardColor.RED // Default fallback if not specified
        } else {
            activeFace.color
        }

        // Pop card from player's hand and push to discard pile
        player.removeCard(cardId)
        room.discardPile.add(card)
        room.discardTop = DiscardTop(
            card = card,
            activeColor = finalActiveColor,
            activeFace = activeFace,
            playedByPlayerId = playerId
        )

        if (isUnoCalledWithPlay) {
            player.isUnoCalled = true
            events.add(UnoCalledEvent(room.id, player.id))
            room.addActionLog("${player.username} called UNO!")
        }

        events.add(
            CardPlayedEvent(
                id = room.id,
                playerId = playerId,
                cardFace = activeFace,
                chosenColor = finalActiveColor,
                remainingCardsCount = player.cardCount,
                isJumpIn = isOutOfTurn
            )
        )
        room.addActionLog("${player.username} played $finalActiveColor ${activeFace.value} (cards left: ${player.cardCount})")

        // Handle card effects and ruleset actions
        val actionEvents = ruleset.handleCardAction(room, player, card, activeFace, finalActiveColor, targetPlayerId)
        events.addAll(actionEvents)

        // Evaluate Mercy Rule: Eliminate player if holding >= 25 cards
        ruleset.mercyEliminationThreshold?.let { threshold ->
            for (p in room.getActivePlayers()) {
                if (p.cardCount >= threshold) {
                    p.status = PlayerStatus.ELIMINATED
                    events.add(PlayerEliminatedEvent(room.id, p.id, "MERCY_RULE_EXCEEDED_${threshold}_CARDS"))
                    room.addActionLog("${p.username} was eliminated by the Mercy Rule (held ${p.cardCount} cards)!")
                }
            }
        }

        // Check Victory Condition
        val remainingActive = room.getActivePlayers()
        if (player.cardCount == 0 || remainingActive.size <= 1) {
            val winner = if (player.cardCount == 0) player else remainingActive.firstOrNull() ?: player
            room.status = RoomStatus.MATCH_OVER
            room.winnerPlayerId = winner.id
            events.add(GameOverEvent(room.id, winner.id, remainingActive.map { it.id }))
            room.addActionLog("Match Over! Winner is ${winner.username}!")
            return events
        }

        // Check UNO penalty window (1 card left and didn't call UNO)
        if (player.cardCount == 1 && !player.isUnoCalled) {
            player.unoWindowOpenUntilTimestamp = System.currentTimeMillis() + UNO_CHALLENGE_WINDOW_MS
            room.addActionLog("UNO Challenge Window OPEN for ${player.username} (3 seconds)!")
        }

        // Advance turn pointer
        if (isOutOfTurn) {
            // Jump-in stole turn: advance from jump-in player index
            val jumpInIndex = remainingActive.indexOfFirst { it.id == playerId }
            if (jumpInIndex != -1) {
                room.currentTurnIndex = (jumpInIndex + room.direction.multiplier + remainingActive.size) % remainingActive.size
            }
        } else if (activeFace.value != CardValue.SKIP_EVERYONE) {
            // Advance turn normally (unless Skip Everyone was played in Flip)
            val currentIdx = remainingActive.indexOfFirst { it.id == playerId }
            val nextIdx = if (currentIdx != -1) {
                (currentIdx + room.direction.multiplier + remainingActive.size) % remainingActive.size
            } else {
                (room.currentTurnIndex + room.direction.multiplier + remainingActive.size) % remainingActive.size
            }
            room.currentTurnIndex = nextIdx
        }

        val nextPlayer = room.getCurrentPlayer()
        if (nextPlayer != null) {
            room.turnExpiresAt = System.currentTimeMillis() + DEFAULT_TURN_TIMEOUT_MS
            events.add(TurnChangedEvent(room.id, nextPlayer.id, room.direction, DEFAULT_TURN_TIMEOUT_MS))
        }

        return events
    }

    fun drawCardsForPlayer(room: Room, playerId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val ruleset = variantRegistry.getRuleset(room.variant)
        val player = room.getPlayer(playerId) ?: throw IllegalArgumentException("Player $playerId not found")

        val currentPlayer = room.getCurrentPlayer()
        if (currentPlayer?.id != playerId) {
            throw IllegalStateException("It is not player ${player.username}'s turn to draw")
        }

        // Check if drawing due to accumulated penalty
        if (room.penaltyState.hasPenalty()) {
            val drawCount = if (room.penaltyState.accumulatedDrawCount == -1) {
                // Wild Draw Color (Flip): Draw until target matches top active color
                executeDrawUntilColor(room, player, room.discardTop?.activeColor ?: CardColor.RED)
            } else {
                room.penaltyState.accumulatedDrawCount
            }

            val drawn = drawFromPile(room, drawCount)
            player.addCards(drawn)
            events.add(CardDrawnEvent(room.id, player.id, drawn.size, player.cardCount, "PENALTY_DRAW"))
            room.addActionLog("${player.username} drew ${drawn.size} penalty cards (Total in hand: ${player.cardCount})")

            // Handle Drawn Together partner in Party mode
            handleLinkedPartnerDraw(room, player, drawn.size, events)

            // Reset penalty state
            room.penaltyState.reset()

            // After penalty draw, turn passes to next player
            advanceTurn(room, events)
            return events
        }

        // Normal draw (1 card)
        val drawn = drawFromPile(room, 1)
        player.addCards(drawn)
        events.add(CardDrawnEvent(room.id, player.id, drawn.size, player.cardCount, "NORMAL_DRAW"))
        room.addActionLog("${player.username} drew 1 card (Total in hand: ${player.cardCount})")

        // Handle Drawn Together partner
        handleLinkedPartnerDraw(room, player, 1, events)

        // Check if the drawn card can be played immediately
        val drawnCard = drawn.firstOrNull()
        val discardTop = room.discardTop
        var canPlayDrawn = false
        if (drawnCard != null && discardTop != null) {
            val match = matchEngine.evaluatePlay(drawnCard, discardTop, room.penaltyState, room.activeSide, ruleset, isOutOfTurn = false)
            canPlayDrawn = match is MatchResult.Valid
        }

        // If drawn card cannot be played or auto-pass, advance turn
        if (!canPlayDrawn) {
            advanceTurn(room, events)
        }

        return events
    }

    fun callUno(room: Room, playerId: String): List<GameEvent> {
        val player = room.getPlayer(playerId) ?: throw IllegalArgumentException("Player $playerId not found")
        player.isUnoCalled = true
        player.unoWindowOpenUntilTimestamp = null
        room.addActionLog("${player.username} called UNO!")
        return listOf(UnoCalledEvent(room.id, playerId))
    }

    fun challengeUno(room: Room, challengerPlayerId: String, targetPlayerId: String): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val challenger = room.getPlayer(challengerPlayerId) ?: throw IllegalArgumentException("Challenger not found")
        val target = room.getPlayer(targetPlayerId) ?: throw IllegalArgumentException("Target player not found")

        if (target.cardCount == 1 && !target.isUnoCalled) {
            // Target caught! Penalty: Draw 2 cards
            val penaltyCards = drawFromPile(room, 2)
            target.addCards(penaltyCards)
            target.isUnoCalled = false
            target.unoWindowOpenUntilTimestamp = null

            events.add(UnoPenaltyAppliedEvent(room.id, target.id, challenger.id, 2))
            events.add(CardDrawnEvent(room.id, target.id, 2, target.cardCount, "UNO_PENALTY_DRAW"))
            room.addActionLog("SUCCESSFUL UNO CHALLENGE! ${challenger.username} caught ${target.username}. ${target.username} drew 2 penalty cards.")
        } else {
            room.addActionLog("UNSUCCESSFUL UNO CHALLENGE: ${target.username} already called UNO or does not have 1 card.")
        }

        return events
    }

    fun handleTurnTimeout(room: Room): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val currentPlayer = room.getCurrentPlayer() ?: return events

        room.addActionLog("Turn timed out for ${currentPlayer.username}. Auto-drawing 1 card.")
        val drawn = drawFromPile(room, 1)
        currentPlayer.addCards(drawn)
        events.add(CardDrawnEvent(room.id, currentPlayer.id, drawn.size, currentPlayer.cardCount, "TIMEOUT_DRAW"))

        advanceTurn(room, events)
        return events
    }

    private fun advanceTurn(room: Room, events: MutableList<GameEvent>) {
        val activePlayers = room.getActivePlayers()
        if (activePlayers.isEmpty()) return

        room.currentTurnIndex = (room.currentTurnIndex + room.direction.multiplier + activePlayers.size) % activePlayers.size
        val nextPlayer = room.getCurrentPlayer()
        if (nextPlayer != null) {
            room.turnExpiresAt = System.currentTimeMillis() + DEFAULT_TURN_TIMEOUT_MS
            events.add(TurnChangedEvent(room.id, nextPlayer.id, room.direction, DEFAULT_TURN_TIMEOUT_MS))
            room.addActionLog("Turn is now on ${nextPlayer.username}")
        }
    }

    private fun handleLinkedPartnerDraw(room: Room, player: Player, count: Int, events: MutableList<GameEvent>) {
        val partnerId = player.linkedPartnerId ?: return
        val partner = room.getPlayer(partnerId) ?: return
        if (partner.status == PlayerStatus.ACTIVE && count > 0) {
            val partnerCards = drawFromPile(room, count)
            partner.addCards(partnerCards)
            events.add(CardDrawnEvent(room.id, partner.id, partnerCards.size, partner.cardCount, "DRAWN_TOGETHER_PARTNER_DRAW"))
            room.addActionLog("Drawn Together triggered! Linked partner ${partner.username} also drew $count cards.")
        }
    }

    private fun executeDrawUntilColor(room: Room, player: Player, targetColor: CardColor): Int {
        var count = 0
        while (count < 20) { // Cap safety limit
            val drawn = drawFromPile(room, 1)
            if (drawn.isEmpty()) break
            val card = drawn.first()
            player.addCard(card)
            count++
            val face = card.getActiveFace(room.activeSide)
            if (face.color == targetColor || face.isWild()) {
                break
            }
        }
        return count
    }

    private fun drawFromPile(room: Room, count: Int): List<GameCard> {
        val drawn = mutableListOf<GameCard>()
        for (i in 0 until count) {
            if (room.drawPile.isEmpty()) {
                shuffleEngine.reshuffleDiscardIntoDrawPile(room)
            }
            if (room.drawPile.isNotEmpty()) {
                drawn.add(room.drawPile.removeAt(0))
            }
        }
        return drawn
    }
}
