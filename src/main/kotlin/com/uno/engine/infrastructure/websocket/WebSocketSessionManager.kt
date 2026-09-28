package com.uno.engine.infrastructure.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.uno.engine.domain.model.Room
import com.uno.engine.dto.ServerOutboundMessage
import com.uno.engine.security.ZeroTrustStateSanitizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

@Component
class WebSocketSessionManager(
    private val objectMapper: ObjectMapper,
    private val stateSanitizer: ZeroTrustStateSanitizer
) {
    private val logger = LoggerFactory.getLogger(WebSocketSessionManager::class.java)

    // Player ID -> Session Sink
    private val sessionSinks = ConcurrentHashMap<String, Sinks.Many<String>>()
    private val playerRooms = ConcurrentHashMap<String, String>()

    fun registerSession(playerId: String, roomId: String, sink: Sinks.Many<String>) {
        sessionSinks[playerId] = sink
        playerRooms[playerId] = roomId
        logger.info("Registered WebSocket session for player $playerId in room $roomId")
    }

    fun unregisterSession(playerId: String) {
        sessionSinks.remove(playerId)
        playerRooms.remove(playerId)
        logger.info("Unregistered WebSocket session for player $playerId")
    }

    fun sendToPlayer(playerId: String, message: ServerOutboundMessage) {
        val sink = sessionSinks[playerId] ?: return
        try {
            val json = objectMapper.writeValueAsString(message)
            sink.tryEmitNext(json)
        } catch (e: Exception) {
            logger.error("Failed to send message to player $playerId: ${e.message}")
        }
    }

    fun broadcastSanitizedRoomState(room: Room, seqId: Long = 0) {
        for (player in room.players) {
            val sanitizedState = stateSanitizer.sanitizeForPlayer(room, player.id)
            val outbound = ServerOutboundMessage(
                type = "GAME_STATE_UPDATE",
                seqId = seqId,
                payload = sanitizedState
            )
            sendToPlayer(player.id, outbound)
        }
    }

    fun broadcastGenericEvent(room: Room, eventType: String, payload: Any?, seqId: Long = 0) {
        val outbound = ServerOutboundMessage(
            type = eventType,
            seqId = seqId,
            payload = payload
        )
        for (player in room.players) {
            sendToPlayer(player.id, outbound)
        }
    }
}
