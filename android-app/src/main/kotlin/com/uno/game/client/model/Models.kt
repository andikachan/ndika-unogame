package com.uno.game.client.model

import com.google.gson.annotations.SerializedName

enum class CardColor {
    RED, BLUE, GREEN, YELLOW,
    ORANGE, PINK, TEAL, PURPLE,
    WILD, MULTI
}

enum class CardValue {
    ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE,
    SKIP, REVERSE, DRAW_TWO,
    WILD, WILD_DRAW_FOUR,
    FLIP, DRAW_ONE, WILD_DRAW_TWO,
    DARK_DRAW_FIVE, DARK_SKIP_EVERYONE, DARK_WILD_DRAW_COLOR,
    DRAW_SIX, DRAW_TEN, DISCARD_ALL, WILD_REVERSE_DRAW_FOUR,
    POINT_TAKEN, DRAWN_TOGETHER, WILD_DRAW_FOUR_ALL,
    SHOWDOWN,
    WILD_SKIP, WILD_SKIP_TWO, WILD_DRAW_TWO_ALL, WILD_TARGETED_DRAW_FOUR, WILD_FORCED_SWAP,
    DANGER_RAPTOR, ESCAPE_BALL, CREEPER_EXPLOSION, SHIELD_DEFENSE
}

enum class CardType {
    NUMBER, ACTION, WILD
}

enum class CardSide {
    LIGHT, DARK
}

data class CardDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("color") val color: CardColor = CardColor.RED,
    @SerializedName("value") val value: CardValue = CardValue.ZERO,
    @SerializedName("type") val type: CardType = CardType.NUMBER,
    @SerializedName("side") val side: CardSide = CardSide.LIGHT,
    @SerializedName("power") val power: Int = 0
)

data class PlayerDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("cardCount") val cardCount: Int = 0,
    @SerializedName("unoCalled") val unoCalled: Boolean = false,
    @SerializedName("isEliminated") val isEliminated: Boolean = false,
    @SerializedName("linkedPlayerId") val linkedPlayerId: String? = null
)

data class GameStateDto(
    @SerializedName("roomId") val roomId: String = "",
    @SerializedName("variant") val variant: String = "CLASSIC",
    @SerializedName("status") val status: String = "AWAITING_MOVE",
    @SerializedName("activeSide") val activeSide: CardSide = CardSide.LIGHT,
    @SerializedName("activeColor") val activeColor: CardColor = CardColor.RED,
    @SerializedName("currentTurnPlayerId") val currentTurnPlayerId: String = "",
    @SerializedName("turnDirection") val turnDirection: Int = 1,
    @SerializedName("topDiscardCard") val topDiscardCard: CardDto? = null,
    @SerializedName("drawPileCount") val drawPileCount: Int = 0,
    @SerializedName("accumulatedDrawPenalty") val accumulatedDrawPenalty: Int = 0,
    @SerializedName("unoPenaltyWindowActive") val unoPenaltyWindowActive: Boolean = false,
    @SerializedName("players") val players: List<PlayerDto> = emptyList(),
    @SerializedName("myHand") val myHand: List<CardDto> = emptyList(),
    @SerializedName("winnerPlayerId") val winnerPlayerId: String? = null
)

data class InboundMessage(
    @SerializedName("seqId") val seqId: Long = 1,
    @SerializedName("type") val type: String = "PLAY_CARD",
    @SerializedName("playerId") val playerId: String = "",
    @SerializedName("roomId") val roomId: String = "",
    @SerializedName("payload") val payload: Map<String, Any?> = emptyMap()
)

data class OutboundBroadcast(
    @SerializedName("type") val type: String = "STATE_UPDATE",
    @SerializedName("roomId") val roomId: String = "",
    @SerializedName("gameState") val gameState: GameStateDto? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
