package com.uno.engine.infrastructure.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Repository
class RedisRoomRepository(
    private val redisTemplate: ReactiveStringRedisTemplate? = null,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(RedisRoomRepository::class.java)
    private val memoryCache = ConcurrentHashMap<String, Room>()

    fun saveRoom(room: Room) {
        room.version++
        memoryCache[room.id] = room

        try {
            if (redisTemplate != null) {
                val metaKey = RedisKeys.roomMeta(room.id)
                val metaMap = mapOf(
                    "status" to room.status.name,
                    "variant" to room.variant.name,
                    "direction" to room.direction.name,
                    "hostId" to room.hostId,
                    "currentTurnIndex" to room.currentTurnIndex.toString(),
                    "activeSide" to room.activeSide.name,
                    "version" to room.version.toString()
                )

                redisTemplate.opsForHash<String, String>().putAll(metaKey, metaMap)
                    .flatMap { redisTemplate.expire(metaKey, Duration.ofHours(6)) }
                    .subscribe()

                // Save player secret hands
                for (player in room.players) {
                    val handKey = RedisKeys.playerHand(room.id, player.id)
                    val cardJson = objectMapper.writeValueAsString(player.hand)
                    redisTemplate.opsForValue().set(handKey, cardJson, Duration.ofHours(6)).subscribe()
                }

                // Save penalties
                val penaltyKey = RedisKeys.roomPenalties(room.id)
                val penaltyMap = mapOf(
                    "accumulatedDrawCount" to room.penaltyState.accumulatedDrawCount.toString(),
                    "power" to room.penaltyState.power.toString(),
                    "activePenaltyType" to (room.penaltyState.activePenaltyType?.name ?: "NONE")
                )
                redisTemplate.opsForHash<String, String>().putAll(penaltyKey, penaltyMap)
                    .flatMap { redisTemplate.expire(penaltyKey, Duration.ofHours(6)) }
                    .subscribe()
            }
        } catch (e: Exception) {
            logger.warn("Failed to persist room ${room.id} to Upstash Redis: ${e.message}")
        }
    }

    fun findRoomById(roomId: String): Room? {
        return memoryCache[roomId]
    }

    fun createRoom(roomId: String, hostId: String, variant: GameVariant): Room {
        val room = Room(
            id = roomId,
            hostId = hostId,
            variant = variant
        )
        saveRoom(room)
        return room
    }

    fun deleteRoom(roomId: String) {
        memoryCache.remove(roomId)
        try {
            redisTemplate?.delete(RedisKeys.roomMeta(roomId))?.subscribe()
            redisTemplate?.delete(RedisKeys.roomDrawDeck(roomId))?.subscribe()
            redisTemplate?.delete(RedisKeys.roomDiscardDeck(roomId))?.subscribe()
            redisTemplate?.delete(RedisKeys.roomPenalties(roomId))?.subscribe()
        } catch (e: Exception) {
            logger.warn("Failed to delete room $roomId from Redis: ${e.message}")
        }
    }

    fun getAllRooms(): List<Room> = memoryCache.values.toList()
}
