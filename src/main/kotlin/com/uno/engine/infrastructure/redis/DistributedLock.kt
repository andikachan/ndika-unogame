package com.uno.engine.infrastructure.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Component
class DistributedLock(
    private val redisTemplate: ReactiveStringRedisTemplate? = null
) {
    private val logger = LoggerFactory.getLogger(DistributedLock::class.java)
    private val localLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun tryAcquire(lockKey: String, lockValue: String = UUID.randomUUID().toString(), ttlSeconds: Long = 5): Boolean {
        return try {
            if (redisTemplate != null) {
                val acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds))
                    .block(Duration.ofSeconds(1))
                acquired ?: acquireLocal(lockKey)
            } else {
                acquireLocal(lockKey)
            }
        } catch (e: Exception) {
            logger.warn("Redis lock unavailable for key $lockKey, falling back to local lock: ${e.message}")
            acquireLocal(lockKey)
        }
    }

    fun release(lockKey: String) {
        try {
            redisTemplate?.opsForValue()?.delete(lockKey)?.block(Duration.ofMillis(500))
        } catch (e: Exception) {
            logger.warn("Failed to release Redis lock for key $lockKey: ${e.message}")
        } finally {
            releaseLocal(lockKey)
        }
    }

    final inline fun <T> withLock(lockKey: String, ttlSeconds: Long = 5, block: () -> T): T {
        val lockVal = UUID.randomUUID().toString()
        val acquired = tryAcquire(lockKey, lockVal, ttlSeconds)
        if (!acquired) {
            throw IllegalStateException("Failed to acquire distributed lock on $lockKey")
        }
        try {
            return block()
        } finally {
            release(lockKey)
        }
    }

    private fun acquireLocal(lockKey: String): Boolean {
        val lock = localLocks.computeIfAbsent(lockKey) { ReentrantLock() }
        return lock.tryLock()
    }

    private fun releaseLocal(lockKey: String) {
        val lock = localLocks[lockKey]
        if (lock != null && lock.isHeldByCurrentThread) {
            lock.unlock()
        }
    }
}
