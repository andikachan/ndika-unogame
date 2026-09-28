package com.uno.engine.domain.variants

import com.uno.engine.domain.events.GameEvent
import com.uno.engine.domain.model.CardColor
import com.uno.engine.domain.model.CardFace
import com.uno.engine.domain.model.GameCard
import com.uno.engine.domain.model.GameVariant
import com.uno.engine.domain.model.Player
import com.uno.engine.domain.model.Room

interface VariantRuleset {
    val variant: GameVariant
    val initialHandSize: Int get() = 7
    val allowsStacking: Boolean get() = false
    val jumpInEnabled: Boolean get() = false
    val has70PassingRules: Boolean get() = false
    val mercyEliminationThreshold: Int? get() = null
    val hasFlipMechanic: Boolean get() = false
    val hasShowdownMechanic: Boolean get() = false
    val hasPartyMechanics: Boolean get() = false

    fun buildDeck(): List<GameCard>

    fun isValidMatch(
        topFace: CardFace,
        topActiveColor: CardColor,
        candidateFace: CardFace
    ): Boolean

    fun canCounterPenalty(
        candidateFace: CardFace,
        currentPenaltyPower: Int
    ): Boolean

    fun calculateDrawPenalty(face: CardFace): Int

    fun handleCardAction(
        room: Room,
        player: Player,
        card: GameCard,
        activeFace: CardFace,
        chosenColor: CardColor?,
        targetPlayerId: String?
    ): List<GameEvent>
}
