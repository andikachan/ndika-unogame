package com.uno.engine.dto

import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.PlayDirection
import com.uno.engine.domain.model.PlayerStatus
import com.uno.engine.domain.model.RoomStatus

data class CardDto(
    val id: String,
    val color: CardColor,
    val value: CardValue,
    val type: CardType,
    val power: Int,
    val drawPenalty: Int,
    val side: CardSide
) {
    companion object {
        fun fromGameCard(card: GameCard, activeSide: CardSide): CardDto {
            val face = card.getActiveFace(activeSide)
            return CardDto(
                id = card.id,
                color = face.color,
                value = face.value,
                type = face.type,
                power = face.power,
                drawPenalty = face.drawPenalty,
                side = activeSide
            )
        }
    }
}

data class DiscardTopDto(
    val cardId: String,
    val activeColor: CardColor,
    val value: CardValue,
    val type: CardType,
    val playedByPlayerId: String
)

data class PlayerSummaryDto(
    val id: String,
    val username: String,
    val cardCount: Int,
    val isUnoCalled: Boolean,
    val status: PlayerStatus,
    val isCurrentTurn: Boolean,
    val isLinkedPartner: Boolean = false
)

data class SanitizedGameStateDto(
    val roomId: String,
    val variant: GameVariant,
    val status: RoomStatus,
    val activeSide: CardSide,
    val direction: PlayDirection,
    val currentTurnPlayerId: String?,
    val turnExpiresAt: Long,
    val myHand: List<CardDto>,
    val opponents: List<PlayerSummaryDto>,
    val discardTop: DiscardTopDto?,
    val drawPileCount: Int,
    val discardPileCount: Int,
    val accumulatedDrawPenalty: Int,
    val actionLogs: List<String>,
    val winnerPlayerId: String?,
    val isMyTurn: Boolean,
    val unoChallengeWindowOpen: Boolean
)

data class ClientInboundMessage(
    val type: String,
    val token: String?,
    val roomId: String,
    val seqId: Long,
    val cardId: String? = null,
    val chosenColor: CardColor? = null,
    val targetPlayerId: String? = null,
    val isUnoCalled: Boolean = false,
    val reactionTimestamp: Long? = null
)

data class ServerOutboundMessage(
    val type: String,
    val seqId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Any?
)

data class ErrorResponseDto(
    val error: String,
    val message: String,
    val seqId: Long
)
