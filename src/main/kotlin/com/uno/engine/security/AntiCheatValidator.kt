package com.uno.engine.security

import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import com.uno.engine.dto.ClientInboundMessage
import com.uno.engine.infrastructure.redis.RateLimiter
import org.springframework.stereotype.Component

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

@Component
class AntiCheatValidator(
    private val rateLimiter: RateLimiter
) {

    fun validateInboundMessage(
        message: ClientInboundMessage,
        authenticatedPlayerId: String,
        playerIp: String,
        room: Room?
    ): ValidationResult {
        // 1. Rate Limiting check (Token bucket: 10 req/s per authenticated player/IP)
        val rateLimitKey = "rate_limit:$authenticatedPlayerId:$playerIp"
        if (!rateLimiter.allowRequest(rateLimitKey)) {
            return ValidationResult.Invalid("Rate limit exceeded (10 requests/sec max)")
        }

        // 2. Room check
        if (room == null) {
            return ValidationResult.Invalid("Room ${message.roomId} does not exist")
        }

        // 3. Player membership check
        val player = room.getPlayer(authenticatedPlayerId)
            ?: return ValidationResult.Invalid("Player $authenticatedPlayerId is not in room ${room.id}")

        // 4. Replay attack & sequence monotonicity check
        if (message.seqId <= player.lastSequenceId) {
            return ValidationResult.Invalid("Replay or out-of-order sequence detected (seqId ${message.seqId} <= last ${player.lastSequenceId})")
        }
        player.lastSequenceId = message.seqId

        // 5. Card possession validation for play action
        if (message.type == "PLAY_CARD") {
            val cardId = message.cardId ?: return ValidationResult.Invalid("cardId is required for PLAY_CARD action")
            if (!player.hasCard(cardId)) {
                return ValidationResult.Invalid("Anti-Cheat: Player does not hold card $cardId in their secret hand")
            }
        }

        return ValidationResult.Valid
    }
}
