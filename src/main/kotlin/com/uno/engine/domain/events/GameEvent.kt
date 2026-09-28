package com.uno.engine.domain.events

import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.PlayDirection

sealed class GameEvent(
    val roomId: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GameStartedEvent(
    val id: String,
    val initialCardFace: CardFace,
    val initialColor: CardColor,
    val firstTurnPlayerId: String
) : GameEvent(id)

data class CardPlayedEvent(
    val id: String,
    val playerId: String,
    val cardFace: CardFace,
    val chosenColor: CardColor,
    val remainingCardsCount: Int,
    val isJumpIn: Boolean = false
) : GameEvent(id)

data class CardDrawnEvent(
    val id: String,
    val playerId: String,
    val drawCount: Int,
    val totalRemainingInHand: Int,
    val reason: String = "NORMAL_DRAW"
) : GameEvent(id)

data class TurnChangedEvent(
    val id: String,
    val nextPlayerId: String,
    val direction: PlayDirection,
    val turnTimeoutMs: Long
) : GameEvent(id)

data class DirectionReversedEvent(
    val id: String,
    val newDirection: PlayDirection
) : GameEvent(id)

data class PlayerSkippedEvent(
    val id: String,
    val skippedPlayerId: String,
    val isAllSkipped: Boolean = false
) : GameEvent(id)

data class SideFlippedEvent(
    val id: String,
    val newSide: CardSide
) : GameEvent(id)

data class HandsPassedEvent(
    val id: String,
    val direction: PlayDirection
) : GameEvent(id)

data class HandsSwappedEvent(
    val id: String,
    val player1Id: String,
    val player2Id: String
) : GameEvent(id)

data class PlayerEliminatedEvent(
    val id: String,
    val playerId: String,
    val reason: String
) : GameEvent(id)

data class UnoCalledEvent(
    val id: String,
    val playerId: String
) : GameEvent(id)

data class UnoPenaltyAppliedEvent(
    val id: String,
    val targetPlayerId: String,
    val challengerPlayerId: String,
    val cardsDrawn: Int
) : GameEvent(id)

data class DiscardAllEvent(
    val id: String,
    val playerId: String,
    val discardedCount: Int,
    val color: CardColor
) : GameEvent(id)

data class DrawnTogetherLinkedEvent(
    val id: String,
    val player1Id: String,
    val player2Id: String
) : GameEvent(id)

data class ShowdownStartedEvent(
    val id: String,
    val player1Id: String,
    val player2Id: String,
    val durationMs: Long
) : GameEvent(id)

data class ShowdownResolvedEvent(
    val id: String,
    val winnerPlayerId: String,
    val loserPlayerId: String,
    val cardsTransferred: Int
) : GameEvent(id)

data class GameOverEvent(
    val id: String,
    val winnerPlayerId: String,
    val rankings: List<String>
) : GameEvent(id)
