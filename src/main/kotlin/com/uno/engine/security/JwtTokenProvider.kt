package com.uno.engine.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${uno.security.jwt-secret:dGhpc2lzYXZlcnlzZWN1cmVqd3RzZWNyZXRrZXlmb3J1bm9lbmdpbmUyMDI2MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=}")
    private val secretString: String,
    @Value("\${uno.security.jwt-expiration-ms:900000}")
    private val expirationMs: Long
) {
    private val key: SecretKey by lazy {
        val bytes = secretString.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size < 32) {
            // Pad if necessary for HMAC-SHA256
            Keys.hmacShaKeyFor(secretString.padEnd(32, '0').toByteArray(StandardCharsets.UTF_8))
        } else {
            Keys.hmacShaKeyFor(bytes)
        }
    }

    fun generateToken(playerId: String, username: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)

        return Jwts.builder()
            .subject(playerId)
            .claim("username", username)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = parseClaims(token)
            claims.expiration.after(Date())
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun getPlayerIdFromToken(token: String): String {
        return parseClaims(token).subject
    }

    fun getUsernameFromToken(token: String): String {
        val claims = parseClaims(token)
        return claims.get("username", String::class.java) ?: claims.subject
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
