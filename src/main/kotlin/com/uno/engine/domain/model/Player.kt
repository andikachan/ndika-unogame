package com.uno.engine.domain.model

enum class PlayerStatus {
    ACTIVE,
    ELIMINATED,
    SPECTATING,
    DISCONNECTED
}

data class Player(
    val id: String,
    val username: String,
    val hand: MutableList<GameCard> = mutableListOf(),
    var status: PlayerStatus = PlayerStatus.ACTIVE,
    var isUnoCalled: Boolean = false,
    var unoWindowOpenUntilTimestamp: Long? = null,
    var linkedPartnerId: String? = null,
    var pointTargetId: String? = null,
    var lastSequenceId: Long = 0,
    var lastActivityTimestamp: Long = System.currentTimeMillis()
) {
    val cardCount: Int
        get() = hand.size

    fun addCard(card: GameCard) {
        hand.add(card)
        // If player previously had 1 card and called UNO, drawing a card resets UNO call status
        if (hand.size != 1) {
            isUnoCalled = false
            unoWindowOpenUntilTimestamp = null
        }
    }

    fun addCards(cards: List<GameCard>) {
        hand.addAll(cards)
        if (hand.size != 1) {
            isUnoCalled = false
            unoWindowOpenUntilTimestamp = null
        }
    }

    fun removeCard(cardId: String): GameCard? {
        val index = hand.indexOfFirst { it.id == cardId }
        return if (index != -1) {
            hand.removeAt(index)
        } else null
    }

    fun hasCard(cardId: String): Boolean {
        return hand.any { it.id == cardId }
    }

    fun findCard(cardId: String): GameCard? {
        return hand.firstOrNull { it.id == cardId }
    }
}
