package com.uno.engine.infrastructure.redis

object RedisKeys {
    fun roomMeta(roomId: String): String = "room:$roomId:meta"
    fun roomDrawDeck(roomId: String): String = "room:$roomId:deck:draw"
    fun roomDiscardDeck(roomId: String): String = "room:$roomId:deck:discard"
    fun playerHand(roomId: String, userId: String): String = "room:$roomId:hand:$userId"
    fun roomPenalties(roomId: String): String = "room:$roomId:penalties"
    fun roomLock(roomId: String): String = "room:$roomId:lock"
    fun userSession(userId: String): String = "user:$userId:session"
    fun rateLimit(key: String): String = "rate_limit:$key"
}
