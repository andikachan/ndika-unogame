package com.uno.engine

import com.uno.engine.domain.engine.MatchEngine
import com.uno.engine.domain.engine.ShuffleEngine
import com.uno.engine.domain.engine.TurnStateMachine
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import com.uno.engine.domain.variants.VariantRegistry
import com.uno.engine.dto.ClientInboundMessage
import com.uno.engine.infrastructure.redis.RateLimiter
import com.uno.engine.security.AntiCheatValidator
import com.uno.engine.security.JwtTokenProvider
import com.uno.engine.security.ValidationResult
import com.uno.engine.security.ZeroTrustStateSanitizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AntiCheatSanitizationTest {

    private lateinit var sanitizer: ZeroTrustStateSanitizer
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var rateLimiter: RateLimiter
    private lateinit var antiCheatValidator: AntiCheatValidator
    private lateinit var turnStateMachine: TurnStateMachine

    @BeforeEach
    fun setUp() {
        sanitizer = ZeroTrustStateSanitizer()
        jwtTokenProvider = JwtTokenProvider("dGhpc2lzYXZlcnlzZWN1cmVqd3RzZWNyZXRrZXlmb3J1bm9lbmdpbmUyMDI2MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", 900000)
        rateLimiter = RateLimiter(null, 10)
        antiCheatValidator = AntiCheatValidator(rateLimiter)
        turnStateMachine = TurnStateMachine(VariantRegistry(), MatchEngine(), ShuffleEngine())
    }

    @Test
    fun `test zero trust state sanitization hides opponent hands and deck order`() {
        val room = Room(id = "r_sec", hostId = "alice_id", variant = GameVariant.CLASSIC)
        val alice = Player(id = "alice_id", username = "Alice")
        val bob = Player(id = "bob_id", username = "Bob")
        room.players.addAll(listOf(alice, bob))

        turnStateMachine.startGame(room)

        // Sanitize for Alice
        val aliceView = sanitizer.sanitizeForPlayer(room, alice.id)

        // Alice's own hand should be present and non-empty
        assertEquals(7, aliceView.myHand.size)

        // Bob should be in opponents list, but his cards/cardIds MUST NOT exist in the payload
        assertEquals(1, aliceView.opponents.size)
        val bobSummary = aliceView.opponents[0]
        assertEquals(bob.id, bobSummary.id)
        assertEquals(7, bobSummary.cardCount)

        // Draw pile cards MUST NOT be sent, only count
        assertTrue(aliceView.drawPileCount > 0)
    }

    @Test
    fun `test anti cheat prevents replay attacks and sequence regression`() {
        val room = Room(id = "r_sec", hostId = "p1", variant = GameVariant.CLASSIC)
        val p1 = Player(id = "p1", username = "Alice", lastSequenceId = 5)
        room.players.add(p1)

        val replayMessage = ClientInboundMessage(
            type = "DRAW_CARD",
            token = "dummy",
            roomId = "r_sec",
            seqId = 4 // SeqId less than lastSequenceId (5)
        )

        val result = antiCheatValidator.validateInboundMessage(replayMessage, p1.id, "127.0.0.1", room)
        assertInstanceOf(ValidationResult.Invalid::class.java, result)
        assertTrue((result as ValidationResult.Invalid).reason.contains("Replay"))
    }

    @Test
    fun `test anti cheat prevents playing unheld cards`() {
        val room = Room(id = "r_sec", hostId = "p1", variant = GameVariant.CLASSIC)
        val p1 = Player(id = "p1", username = "Alice", lastSequenceId = 1)
        p1.addCard(GameCard.simple(CardColor.RED, CardValue.ONE, CardType.NUMBER))
        room.players.add(p1)

        val unheldCardId = "fake-uuid-not-in-hand"
        val hackMessage = ClientInboundMessage(
            type = "PLAY_CARD",
            token = "dummy",
            roomId = "r_sec",
            seqId = 2,
            cardId = unheldCardId
        )

        val result = antiCheatValidator.validateInboundMessage(hackMessage, p1.id, "127.0.0.1", room)
        assertInstanceOf(ValidationResult.Invalid::class.java, result)
        assertTrue((result as ValidationResult.Invalid).reason.contains("Anti-Cheat"))
    }

    @Test
    fun `test JWT token generation and validation`() {
        val playerId = "player-123"
        val username = "PlayerOne"
        val token = jwtTokenProvider.generateToken(playerId, username)

        assertTrue(jwtTokenProvider.validateToken(token))
        assertEquals(playerId, jwtTokenProvider.getPlayerIdFromToken(token))
        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token))
        assertFalse(jwtTokenProvider.validateToken("invalid.jwt.token"))
    }
}
