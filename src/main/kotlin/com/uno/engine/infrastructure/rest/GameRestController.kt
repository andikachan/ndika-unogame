package com.uno.engine.infrastructure.rest

import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import com.uno.engine.infrastructure.redis.RedisRoomRepository
import com.uno.engine.security.JwtTokenProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AuthRequest(val username: String)
data class AuthResponse(val token: String, val playerId: String, val username: String)
data class CreateRoomRequest(val variant: GameVariant, val hostPlayerId: String, val hostUsername: String)
data class CreateRoomResponse(val roomId: String, val variant: GameVariant, val hostId: String)

@RestController
@RequestMapping("/api")
class GameRestController(
    private val jwtTokenProvider: JwtTokenProvider,
    private val roomRepository: RedisRoomRepository
) {

    @PostMapping("/auth/token")
    fun generateAuthToken(@RequestBody request: AuthRequest): ResponseEntity<AuthResponse> {
        val playerId = UUID.randomUUID().toString()
        val token = jwtTokenProvider.generateToken(playerId, request.username)
        return ResponseEntity.ok(AuthResponse(token, playerId, request.username))
    }

    @PostMapping("/rooms")
    fun createRoom(@RequestBody request: CreateRoomRequest): ResponseEntity<CreateRoomResponse> {
        val roomId = UUID.randomUUID().toString().substring(0, 8).uppercase()
        val room = roomRepository.createRoom(roomId, request.hostPlayerId, request.variant)
        room.players.add(Player(id = request.hostPlayerId, username = request.hostUsername))
        roomRepository.saveRoom(room)
        return ResponseEntity.ok(CreateRoomResponse(roomId, room.variant, room.hostId))
    }

    @GetMapping("/rooms/{roomId}")
    fun getRoomMeta(@PathVariable roomId: String): ResponseEntity<Map<String, Any?>> {
        val room = roomRepository.findRoomById(roomId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            mapOf(
                "roomId" to room.id,
                "variant" to room.variant,
                "status" to room.status,
                "playerCount" to room.players.size,
                "hostId" to room.hostId
            )
        )
    }
}
