package com.uno.engine.domain.model

sealed class PlayerAction(
    open val roomId: String,
    open val playerId: String,
    open val sequenceId: Long
)

data class StartGameAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long
) : PlayerAction(roomId, playerId, sequenceId)

data class PlayCardAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long,
    val cardId: String,
    val chosenColor: CardColor? = null,
    val targetPlayerId: String? = null // For 7-swap, Targeted +4, Showdown target, etc.
) : PlayerAction(roomId, playerId, sequenceId)

data class DrawCardAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long
) : PlayerAction(roomId, playerId, sequenceId)

data class PassTurnAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long
) : PlayerAction(roomId, playerId, sequenceId)

data class CallUnoAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long
) : PlayerAction(roomId, playerId, sequenceId)

data class ChallengeUnoAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long,
    val targetPlayerId: String
) : PlayerAction(roomId, playerId, sequenceId)

data class JumpInAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long,
    val cardId: String,
    val chosenColor: CardColor? = null
) : PlayerAction(roomId, playerId, sequenceId)

data class PointTakenAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long,
    val targetPlayerId: String
) : PlayerAction(roomId, playerId, sequenceId)

data class ShowdownReactionAction(
    override val roomId: String,
    override val playerId: String,
    override val sequenceId: Long,
    val reactionTimestamp: Long
) : PlayerAction(roomId, playerId, sequenceId)
