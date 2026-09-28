package com.uno.engine

import com.uno.engine.domain.engine.MatchEngine
import com.uno.engine.domain.engine.ShuffleEngine
import com.uno.engine.domain.engine.TurnStateMachine
import com.uno.engine.domain.events.DirectionReversedEvent
import com.uno.engine.domain.events.HandsPassedEvent
import com.uno.engine.domain.events.HandsSwappedEvent
import com.uno.engine.domain.events.PlayerEliminatedEvent
import com.uno.engine.domain.events.SideFlippedEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.CardSide
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.DiscardTop
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.PlayDirection
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room
import com.uno.engine.domain.variants.ThemedUnoRuleset
import com.uno.engine.domain.variants.UnoAllWildRuleset
import com.uno.engine.domain.variants.UnoClassicRuleset
import com.uno.engine.domain.variants.UnoFlipRuleset
import com.uno.engine.domain.variants.UnoNoMercyRuleset
import com.uno.engine.domain.variants.UnoPartyRuleset
import com.uno.engine.domain.variants.UnoShowdownRuleset
import com.uno.engine.domain.variants.VariantRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VariantsRuleTest {

    private lateinit var variantRegistry: VariantRegistry
    private lateinit var matchEngine: MatchEngine
    private lateinit var shuffleEngine: ShuffleEngine
    private lateinit var turnStateMachine: TurnStateMachine

    @BeforeEach
    fun setUp() {
        variantRegistry = VariantRegistry()
        matchEngine = MatchEngine()
        shuffleEngine = ShuffleEngine()
        turnStateMachine = TurnStateMachine(variantRegistry, matchEngine, shuffleEngine)
    }

    @Test
    fun `test 1 Uno Classic 108 cards and reverse direction`() {
        val ruleset = UnoClassicRuleset()
        val deck = ruleset.buildDeck()
        assertEquals(108, deck.size)

        val room = Room(id = "r1", hostId = "p1", variant = GameVariant.CLASSIC)
        room.players.add(Player(id = "p1", username = "Alice"))
        room.players.add(Player(id = "p2", username = "Bob"))
        room.players.add(Player(id = "p3", username = "Charlie"))

        turnStateMachine.startGame(room)
        assertEquals(PlayDirection.CLOCKWISE, room.direction)

        val reverseCard = GameCard.simple(room.discardTop!!.activeColor, CardValue.REVERSE, CardType.ACTION)
        val p1 = room.players[0]
        p1.addCard(reverseCard)

        val events = turnStateMachine.playCard(room, p1.id, reverseCard.id, null)
        assertTrue(events.any { it is DirectionReversedEvent })
        assertEquals(PlayDirection.COUNTER_CLOCKWISE, room.direction)
    }

    @Test
    fun `test 2 Uno Flip dual-sided deck and flip mechanic`() {
        val ruleset = UnoFlipRuleset()
        val deck = ruleset.buildDeck()
        assertEquals(112, deck.size)

        // Verify cards have both light and dark sides
        for (card in deck) {
            assertNotNull(card.lightSide)
            assertNotNull(card.darkSide)
        }

        val room = Room(id = "r2", hostId = "p1", variant = GameVariant.FLIP)
        room.players.add(Player(id = "p1", username = "Alice"))
        room.players.add(Player(id = "p2", username = "Bob"))

        turnStateMachine.startGame(room)
        assertEquals(CardSide.LIGHT, room.activeSide)

        val flipCard = GameCard.dual(
            light = CardFace(room.discardTop!!.activeColor, CardValue.FLIP, CardType.ACTION),
            dark = CardFace(CardColor.DARK_TEAL, CardValue.FLIP, CardType.ACTION)
        )
        val p1 = room.players[0]
        p1.addCard(flipCard)

        val events = turnStateMachine.playCard(room, p1.id, flipCard.id, null)
        assertTrue(events.any { it is SideFlippedEvent })
        assertEquals(CardSide.DARK, room.activeSide)
    }

    @Test
    fun `test 3 Uno Show Em No Mercy 7-0 passing, discard all, and mercy elimination`() {
        val ruleset = UnoNoMercyRuleset()
        val deck = ruleset.buildDeck()
        assertEquals(168, deck.size)

        val room = Room(id = "r3", hostId = "p1", variant = GameVariant.NO_MERCY)
        val p1 = Player(id = "p1", username = "Alice")
        val p2 = Player(id = "p2", username = "Bob")
        val p3 = Player(id = "p3", username = "Charlie")
        room.players.addAll(listOf(p1, p2, p3))

        turnStateMachine.startGame(room)

        // 7-0 Passing test (playing 0 passes all hands in direction)
        val p1CardsBefore = ArrayList(p1.hand)
        val p2CardsBefore = ArrayList(p2.hand)
        val p3CardsBefore = ArrayList(p3.hand)

        val zeroCard = GameCard.simple(room.discardTop!!.activeColor, CardValue.ZERO, CardType.NUMBER)
        p1.addCard(zeroCard)
        turnStateMachine.playCard(room, p1.id, zeroCard.id, null)

        // Hands passed clockwise: p2 receives p1's hand, p3 receives p2's hand, p1 receives p3's hand
        assertTrue(p2.hand.containsAll(p1CardsBefore.filter { it.id != zeroCard.id }))

        // Mercy Elimination: give p2 25 cards
        repeat(25) {
            p2.addCard(GameCard.simple(CardColor.RED, CardValue.ONE, CardType.NUMBER))
        }
        val dummyCard = GameCard.simple(room.discardTop!!.activeColor, CardValue.ONE, CardType.NUMBER)
        val activeP = room.getCurrentPlayer()!!
        activeP.addCard(dummyCard)
        val events = turnStateMachine.playCard(room, activeP.id, dummyCard.id, null)

        assertTrue(events.any { it is PlayerEliminatedEvent && (it as PlayerEliminatedEvent).playerId == p2.id })
        assertEquals(com.uno.engine.domain.model.PlayerStatus.ELIMINATED, p2.status)
    }

    @Test
    fun `test 4 Uno Party drawn together linking`() {
        val room = Room(id = "r4", hostId = "p1", variant = GameVariant.PARTY)
        val p1 = Player(id = "p1", username = "Alice")
        val p2 = Player(id = "p2", username = "Bob")
        room.players.addAll(listOf(p1, p2))

        turnStateMachine.startGame(room)

        // Play Drawn Together targeting p2
        val drawnTogetherCard = GameCard.simple(room.discardTop!!.activeColor, CardValue.DRAWN_TOGETHER, CardType.ACTION)
        p1.addCard(drawnTogetherCard)
        turnStateMachine.playCard(room, p1.id, drawnTogetherCard.id, null, targetPlayerId = p2.id)

        assertEquals(p2.id, p1.linkedPartnerId)
        assertEquals(p1.id, p2.linkedPartnerId)

        // When p2 draws a card, p1 also automatically draws!
        val p1CountBefore = p1.cardCount
        val p2CountBefore = p2.cardCount
        val drawEvents = turnStateMachine.drawCardsForPlayer(room, p2.id)

        assertEquals(p2CountBefore + 1, p2.cardCount)
        assertEquals(p1CountBefore + 1, p1.cardCount)
    }

    @Test
    fun `test 5 Uno Showdown mini-duel initiation`() {
        val ruleset = UnoShowdownRuleset()
        val deck = ruleset.buildDeck()
        assertEquals(112, deck.size)

        val room = Room(id = "r5", hostId = "p1", variant = GameVariant.SHOWDOWN)
        val p1 = Player(id = "p1", username = "Alice")
        val p2 = Player(id = "p2", username = "Bob")
        room.players.addAll(listOf(p1, p2))

        turnStateMachine.startGame(room)

        val showdownCard = GameCard.simple(room.discardTop!!.activeColor, CardValue.SHOWDOWN, CardType.ACTION)
        p1.addCard(showdownCard)
        turnStateMachine.playCard(room, p1.id, showdownCard.id, null)

        assertNotNull(room.showdownState)
        assertEquals(p1.id, room.showdownState?.initiatorPlayerId)
        assertEquals(p2.id, room.showdownState?.targetPlayerId)
        assertEquals(com.uno.engine.domain.model.RoomStatus.SHOWDOWN_DUEL, room.status)
    }

    @Test
    fun `test 6 Uno All Wild actions`() {
        val ruleset = UnoAllWildRuleset()
        val deck = ruleset.buildDeck()
        assertEquals(112, deck.size)

        val room = Room(id = "r6", hostId = "p1", variant = GameVariant.ALL_WILD)
        val p1 = Player(id = "p1", username = "Alice")
        val p2 = Player(id = "p2", username = "Bob")
        room.players.addAll(listOf(p1, p2))

        turnStateMachine.startGame(room)

        // Forced Swap
        val forcedSwap = GameCard.simple(CardColor.WILD, CardValue.WILD_FORCED_SWAP, CardType.WILD)
        p1.addCard(forcedSwap)
        val p1HandSize = p1.cardCount
        val p2HandSize = p2.cardCount

        turnStateMachine.playCard(room, p1.id, forcedSwap.id, CardColor.RED, targetPlayerId = p2.id)
        // Hands swapped
        assertEquals(p2HandSize, p1.cardCount)
    }

    @Test
    fun `test 7 Themed Uno Jurassic and Minecraft`() {
        val jurassicRuleset = ThemedUnoRuleset(GameVariant.THEMED_JURASSIC)
        val jurassicDeck = jurassicRuleset.buildDeck()
        assertTrue(jurassicDeck.any { it.lightSide.value == CardValue.DANGER_RAPTOR })

        val mcRuleset = ThemedUnoRuleset(GameVariant.THEMED_MINECRAFT)
        val mcDeck = mcRuleset.buildDeck()
        assertTrue(mcDeck.any { it.lightSide.value == CardValue.CREEPER })
        assertTrue(mcDeck.any { it.lightSide.value == CardValue.SHIELD_DEFENSE })

        // Test Shield blocks penalty
        val shieldFace = CardFace(CardColor.BLUE, CardValue.SHIELD_DEFENSE, CardType.ACTION)
        assertTrue(mcRuleset.canCounterPenalty(shieldFace, 3))
    }
}
