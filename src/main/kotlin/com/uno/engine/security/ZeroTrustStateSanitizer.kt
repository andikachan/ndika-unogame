package com.uno.engine.security

import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import com.uno.engine.dto.CardDto
import com.uno.engine.dto.DiscardTopDto
import com.uno.engine.dto.PlayerSummaryDto
import com.uno.engine.dto.SanitizedGameStateDto
import org.springframework.stereotype.Component

@Component
class ZeroTrustStateSanitizer {

    fun sanitizeForPlayer(room: Room, requestingPlayerId: String): SanitizedGameStateDto {
        val requestingPlayer = room.getPlayer(requestingPlayerId)
        val currentPlayer = room.getCurrentPlayer()

        // 1. Decrypt / serialize ONLY requesting player's own hand
        val myHand = requestingPlayer?.hand?.map { card ->
            CardDto.fromGameCard(card, room.activeSide)
        } ?: emptyList()

        // 2. Strip secret hands from opponents, providing only public summary
        val opponents = room.players
            .filter { it.id != requestingPlayerId }
            .map { opp ->
                PlayerSummaryDto(
                    id = opp.id,
                    username = opp.username,
                    cardCount = opp.cardCount,
                    isUnoCalled = opp.isUnoCalled,
                    status = opp.status,
                    isCurrentTurn = opp.id == currentPlayer?.id,
                    isLinkedPartner = requestingPlayer?.linkedPartnerId == opp.id
                )
            }

        // 3. Top card on discard pile
        val discardTopDto = room.discardTop?.let { top ->
            DiscardTopDto(
                cardId = top.card.id,
                activeColor = top.activeColor,
                value = top.activeFace.value,
                type = top.activeFace.type,
                playedByPlayerId = top.playedByPlayerId
            )
        }

        val unoChallengeWindowOpen = requestingPlayer?.unoWindowOpenUntilTimestamp?.let {
            it > System.currentTimeMillis()
        } ?: false

        return SanitizedGameStateDto(
            roomId = room.id,
            variant = room.variant,
            status = room.status,
            activeSide = room.activeSide,
            direction = room.direction,
            currentTurnPlayerId = currentPlayer?.id,
            turnExpiresAt = room.turnExpiresAt,
            myHand = myHand,
            opponents = opponents,
            discardTop = discardTopDto,
            drawPileCount = room.drawPile.size,
            discardPileCount = room.discardPile.size,
            accumulatedDrawPenalty = room.penaltyState.accumulatedDrawCount,
            actionLogs = ArrayList(room.actionLogs),
            winnerPlayerId = room.winnerPlayerId,
            isMyTurn = currentPlayer?.id == requestingPlayerId,
            unoChallengeWindowOpen = unoChallengeWindowOpen
        )
    }
}
