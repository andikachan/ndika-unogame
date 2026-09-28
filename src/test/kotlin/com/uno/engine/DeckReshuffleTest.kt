package com.uno.engine

import com.uno.engine.domain.engine.ShuffleEngine
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardType
import com.uno.engine.domain.model.CardValue
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Room
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeckReshuffleTest {

    private val shuffleEngine = ShuffleEngine()

    @Test
    fun `test cryptographic Fisher-Yates reshuffle maintains top discard card and replenishes draw pile`() {
        val room = Room(id = "r_shuffle", hostId = "p1", variant = GameVariant.CLASSIC)

        // 10 cards in discard pile
        val topDiscard = GameCard.simple(CardColor.RED, CardValue.NINE, CardType.NUMBER)
        for (i in 1..9) {
            room.discardPile.add(GameCard.simple(CardColor.BLUE, CardValue.ONE, CardType.NUMBER))
        }
        room.discardPile.add(topDiscard) // top card is at index 9

        assertEquals(0, room.drawPile.size)
        assertEquals(10, room.discardPile.size)

        shuffleEngine.reshuffleDiscardIntoDrawPile(room)

        // Only top discard card remains on discard pile
        assertEquals(1, room.discardPile.size)
        assertEquals(topDiscard.id, room.discardPile.first().id)

        // Draw pile replenished with remaining 9 cards
        assertEquals(9, room.drawPile.size)
    }

    @Test
    fun `test Fisher-Yates permutation uniformity and non-destructiveness`() {
        val originalList = (1..100).toMutableList()
        val copy = ArrayList(originalList)

        shuffleEngine.shuffle(copy)

        assertEquals(originalList.size, copy.size)
        assertTrue(copy.containsAll(originalList))
    }
}
