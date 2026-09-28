package com.uno.engine.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.uno.engine.domain.engine.TurnStateMachine
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.PlayerStatus
import com.uno.engine.domain.model.RoomStatus
import com.uno.engine.dto.ClientInboundMessage
import com.uno.engine.dto.ErrorResponseDto
import com.uno.engine.dto.ServerOutboundMessage
import com.uno.engine.infrastructure.redis.DistributedLock
import com.uno.engine.infrastructure.redis.RedisKeys
import com.uno.engine.infrastructure.redis.RedisRoomRepository
import com.uno.engine.security.AntiCheatValidator
import com.uno.engine.security.JwtTokenProvider
import com.uno.engine.security.ValidationResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class InboundMessageRouter(
    private val objectMapper: ObjectMapper,
    private val jwtTokenProvider: JwtTokenProvider,
    private val antiCheatValidator: AntiCheatValidator,
    private val turnStateMachine: TurnStateMachine,
    private val roomRepository: RedisRoomRepository,
    private val sessionManager: WebSocketSessionManager,
    private val distributedLock: DistributedLock
) {
    private val logger = LoggerFactory.getLogger(InboundMessageRouter::class.java)

    fun handleRawMessage(rawPayload: String, clientIp: String) {
        val inbound = try {
            objectMapper.readValue(rawPayload, ClientInboundMessage::class.java)
        } catch (e: Exception) {
            logger.warn("Failed to parse inbound JSON message: ${e.message}")
            return
        }

        // Authenticate JWT Token
        val token = inbound.token
        if (token.isNullOrBlank() || !jwtTokenProvider.validateToken(token)) {
            logger.warn("Rejecting message with invalid/missing token from IP $clientIp")
            return
        }

        val playerId = jwtTokenProvider.getPlayerIdFromToken(token)
        val username = jwtTokenProvider.getUsernameFromToken(token)

        try {
            when (inbound.type) {
                "JOIN_ROOM" -> handleJoinRoom(inbound, playerId, username)
                "START_GAME" -> handleStartGame(inbound, playerId, clientIp)
                "PLAY_CARD" -> handlePlayCard(inbound, playerId, clientIp)
                "DRAW_CARD" -> handleDrawCard(inbound, playerId, clientIp)
                "CALL_UNO" -> handleCallUno(inbound, playerId, clientIp)
                "CHALLENGE_UNO" -> handleChallengeUno(inbound, playerId, clientIp)
                "HEARTBEAT" -> handleHeartbeat(playerId, inbound.seqId)
                else -> {
                    sendError(playerId, "UNKNOWN_ACTION", "Unrecognized action type: ${inbound.type}", inbound.seqId)
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing action ${inbound.type} for player $playerId: ${e.message}", e)
            sendError(playerId, "ACTION_FAILED", e.message ?: "Internal error", inbound.seqId)
        }
    }

    private fun handleJoinRoom(inbound: ClientInboundMessage, playerId: String, username: String) {
        val room = roomRepository.findRoomById(inbound.roomId)
            ?: throw IllegalArgumentException("Room ${inbound.roomId} not found")

        val existingPlayer = room.getPlayer(playerId)
        if (existingPlayer == null) {
            if (room.status != RoomStatus.LOBBY_WAITING) {
                throw IllegalStateException("Cannot join room: game is already in progress")
            }
            room.players.add(Player(id = playerId, username = username))
            room.addActionLog("Player $username joined the room.")
        } else {
            existingPlayer.status = PlayerStatus.ACTIVE
            room.addActionLog("Player $username reconnected.")
        }

        roomRepository.saveRoom(room)
        sessionManager.broadcastSanitizedRoomState(room, inbound.seqId)
    }

    private fun handleStartGame(inbound: ClientInboundMessage, playerId: String, clientIp: String) {
        val lockKey = RedisKeys.roomLock(inbound.roomId)
        distributedLock.withLock(lockKey) {
            val room = roomRepository.findRoomById(inbound.roomId)
                ?: throw IllegalArgumentException("Room ${inbound.roomId} not found")

            if (room.hostId != playerId) {
                throw IllegalStateException("Only the room host can start the game")
            }

            turnStateMachine.startGame(room)
            roomRepository.saveRoom(room)
            sessionManager.broadcastSanitizedRoomState(room, inbound.seqId)
        }
    }

    private fun handlePlayCard(inbound: ClientInboundMessage, playerId: String, clientIp: String) {
        val lockKey = RedisKeys.roomLock(inbound.roomId)
        distributedLock.withLock(lockKey) {
            val room = roomRepository.findRoomById(inbound.roomId)
            val validation = antiCheatValidator.validateInboundMessage(inbound, playerId, clientIp, room)
            if (validation is ValidationResult.Invalid) {
                sendError(playerId, "SECURITY_VIOLATION", validation.reason, inbound.seqId)
                return@withLock
            }

            val cardId = inbound.cardId ?: throw IllegalArgumentException("cardId is required")
            turnStateMachine.playCard(
                room = room!!,
                playerId = playerId,
                cardId = cardId,
                chosenColor = inbound.chosenColor,
                targetPlayerId = inbound.targetPlayerId,
                isUnoCalledWithPlay = inbound.isUnoCalled
            )

            roomRepository.saveRoom(room)
            sessionManager.broadcastSanitizedRoomState(room, inbound.seqId)
        }
    }

    private fun handleDrawCard(inbound: ClientInboundMessage, playerId: String, clientIp: String) {
        val lockKey = RedisKeys.roomLock(inbound.roomId)
        distributedLock.withLock(lockKey) {
            val room = roomRepository.findRoomById(inbound.roomId)
            val validation = antiCheatValidator.validateInboundMessage(inbound, playerId, clientIp, room)
            if (validation is ValidationResult.Invalid) {
                sendError(playerId, "SECURITY_VIOLATION", validation.reason, inbound.seqId)
                return@withLock
            }

            turnStateMachine.drawCardsForPlayer(room = room!!, playerId = playerId)
            roomRepository.saveRoom(room)
            sessionManager.broadcastSanitizedRoomState(room, inbound.seqId)
        }
    }

    private fun handleCallUno(inbound: ClientInboundMessage, playerId: String, clientIp: String) {
        val lockKey = RedisKeys.roomLock(inbound.roomId)
        distributedLock.withLock(lockKey) {
            val room = roomRepository.findRoomById(inbound.roomId) ?: return@withLock
            turnStateMachine.callUno(room, playerId)
            roomRepository.saveRoom(room)
            sessionManager.broadcastSanitizedRoomState(room, inbound.seqId)
        }
    }

    private fun handleChallengeUno(inbound: ClientInboundMessage, playerId: String, clientIp: String) {
        val lockKey = RedisKeys.roomLock(inbound.roomId)
        distributedLock.withLock(lockKey) {
            val room = roomRepository.findRoomById(inbound.roomId) ?: return@withLock
            val targetId = inbound.targetPlayerId ?: throw IllegalArgumentException("targetPlayerId required for UNO challenge")
            turnStateMachine.challengeUno(room, challengerPlayerId = playerId, targetPlayerId = targetId)
            roomRepository.saveRoom(room)
            sessionManager.broadcastSanitizedRoomState(room, inbound.seqId)
        }
    }

    private fun handleHeartbeat(playerId: String, seqId: Long) {
        sessionManager.sendToPlayer(
            playerId,
            ServerOutboundMessage(
                type = "PONG",
                seqId = seqId,
                payload = mapOf("status" to "OK", "timestamp" to System.currentTimeMillis())
            )
        )
    }

    private fun sendError(playerId: String, error: String, message: String, seqId: Long) {
        sessionManager.sendToPlayer(
            playerId,
            ServerOutboundMessage(
                type = "ERROR",
                seqId = seqId,
                payload = ErrorResponseDto(error = error, message = message, seqId = seqId)
            )
        )
    }
}
