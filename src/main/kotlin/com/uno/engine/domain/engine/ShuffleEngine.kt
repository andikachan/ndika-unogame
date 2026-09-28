package com.uno.engine.domain.engine

import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.Room
import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class ShuffleEngine {
    private val secureRandom = SecureRandom()

    fun <T> shuffle(list: MutableList<T>) {
        val n = list.size
        for (i in n - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    fun reshuffleDiscardIntoDrawPile(room: Room) {
        if (room.discardPile.size <= 1) return

        val topDiscard = room.discardPile.last()
        val cardsToShuffle = mutableListOf<GameCard>()

        // Keep C_top on Discard Pile, collect remaining
        for (i in 0 until room.discardPile.size - 1) {
            cardsToShuffle.add(room.discardPile[i])
        }

        room.discardPile.clear()
        room.discardPile.add(topDiscard)

        // Fisher-Yates cryptographic shuffle
        shuffle(cardsToShuffle)

        // Add to draw pile
        room.drawPile.addAll(cardsToShuffle)
        room.addActionLog("Draw pile was reshuffled from discard pile (${cardsToShuffle.size} cards).")
    }
}
