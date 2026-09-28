package com.uno.engine.infrastructure.redis

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class RateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate? = null,
    @Value("\${uno.rate-limit.capacity:10}") private val maxRequestsPerSecond: Int = 10
) {
    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)
    private val localBuckets = ConcurrentHashMap<String, TokenBucket>()

    data class TokenBucket(
        val windowSecond: Long,
        val counter: AtomicInteger
    )

    fun allowRequest(key: String): Boolean {
        val currentSecond = System.currentTimeMillis() / 1000
        val redisKey = RedisKeys.rateLimit("$key:$currentSecond")

        return try {
            if (redisTemplate != null) {
                val count = redisTemplate.opsForValue()
                    .increment(redisKey)
                    .flatMap { c ->
                        if (c == 1L) {
                            redisTemplate.expire(redisKey, Duration.ofSeconds(2)).thenReturn(c)
                        } else {
                            reactor.core.publisher.Mono.just(c)
                        }
                    }
                    .block(Duration.ofMillis(200)) ?: 1L

                count <= maxRequestsPerSecond
            } else {
                allowLocalRequest(key, currentSecond)
            }
        } catch (e: Exception) {
            logger.debug("Redis rate limiter falling back to in-memory check for $key: ${e.message}")
            allowLocalRequest(key, currentSecond)
        }
    }

    private fun allowLocalRequest(key: String, currentSecond: Long): Boolean {
        val bucket = localBuckets.compute(key) { _, existing ->
            if (existing == null || existing.windowSecond != currentSecond) {
                TokenBucket(currentSecond, AtomicInteger(1))
            } else {
                existing.counter.incrementAndGet()
                existing
            }
        }
        return (bucket?.counter?.get() ?: 1) <= maxRequestsPerSecond
    }
}
