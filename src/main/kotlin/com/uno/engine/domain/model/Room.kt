package com.uno.engine.domain.model

import java.util.concurrent.ConcurrentHashMap

enum class GameVariant {
    CLASSIC,
    FLIP,
    NO_MERCY,
    PARTY,
    SHOWDOWN,
    ALL_WILD,
    THEMED_JURASSIC,
    THEMED_MINECRAFT
}

enum class RoomStatus {
    LOBBY_WAITING,
    DEALING,
    AWAITING_MOVE,
    EVALUATING_PLAY,
    PENALTY_WINDOW,
    ACTION_RESOLVE,
    SHOWDOWN_DUEL,
    CHECK_VICTORY,
    MATCH_OVER
}

enum class PlayDirection(val multiplier: Int) {
    CLOCKWISE(1),
    COUNTER_CLOCKWISE(-1);

    fun reverse(): PlayDirection = if (this == CLOCKWISE) COUNTER_CLOCKWISE else CLOCKWISE
}

data class DiscardTop(
    val card: GameCard,
    val activeColor: CardColor,
    val activeFace: CardFace,
    val playedByPlayerId: String
)

data class PenaltyState(
    var accumulatedDrawCount: Int = 0,
    var activePenaltyType: CardValue? = null,
    var targetPlayerId: String? = null,
    var power: Int = 0
) {
    fun reset() {
        accumulatedDrawCount = 0
        activePenaltyType = null
        targetPlayerId = null
        power = 0
    }

    fun hasPenalty(): Boolean = accumulatedDrawCount > 0
}

data class ShowdownState(
    val initiatorPlayerId: String,
    val targetPlayerId: String,
    val duelStartTime: Long = System.currentTimeMillis(),
    val duelDurationMs: Long = 3000,
    val initiatorReactions: MutableList<Long> = mutableListOf(),
    val targetReactions: MutableList<Long> = mutableListOf(),
    val cardsAtStake: Int = 2
)

data class Room(
    val id: String,
    var hostId: String,
    val variant: GameVariant,
    var status: RoomStatus = RoomStatus.LOBBY_WAITING,
    val players: MutableList<Player> = mutableListOf(),
    val drawPile: MutableList<GameCard> = mutableListOf(),
    val secondaryDrawPile: MutableList<GameCard> = mutableListOf(), // For Party mode dual piles
    val discardPile: MutableList<GameCard> = mutableListOf(),
    var discardTop: DiscardTop? = null,
    var currentTurnIndex: Int = 0,
    var direction: PlayDirection = PlayDirection.CLOCKWISE,
    var activeSide: CardSide = CardSide.LIGHT,
    val penaltyState: PenaltyState = PenaltyState(),
    var showdownState: ShowdownState? = null,
    var winnerPlayerId: String? = null,
    val actionLogs: MutableList<String> = mutableListOf(),
    var turnExpiresAt: Long = 0,
    var version: Long = 0,
    var createdAt: Long = System.currentTimeMillis()
) {
    fun getActivePlayers(): List<Player> = players.filter { it.status == PlayerStatus.ACTIVE }

    fun getCurrentPlayer(): Player? {
        val active = getActivePlayers()
        if (active.isEmpty()) return null
        val normalizedIndex = ((currentTurnIndex % active.size) + active.size) % active.size
        return active[normalizedIndex]
    }

    fun getPlayer(playerId: String): Player? = players.firstOrNull { it.id == playerId }

    fun addActionLog(log: String) {
        actionLogs.add(log)
        if (actionLogs.size > 50) {
            actionLogs.removeAt(0)
        }
    }
}
