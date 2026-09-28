package com.uno.engine.domain.model

import java.util.UUID

enum class CardColor {
    RED,
    YELLOW,
    GREEN,
    BLUE,
    WILD,
    // UNO Flip Dark Side Colors
    DARK_TEAL,
    DARK_ORANGE,
    DARK_PINK,
    DARK_PURPLE
}

enum class CardType {
    NUMBER,
    ACTION,
    WILD
}

enum class CardSide {
    LIGHT,
    DARK
}

enum class CardValue(val numericValue: Int? = null) {
    // Number cards
    ZERO(0), ONE(1), TWO(2), THREE(3), FOUR(4),
    FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9),

    // Standard & Flip Actions
    SKIP,
    REVERSE,
    DRAW_ONE,
    DRAW_TWO,
    DRAW_FOUR,
    DRAW_FIVE,
    SKIP_EVERYONE,
    FLIP,

    // Show 'Em No Mercy Actions
    DRAW_SIX,
    DRAW_TEN,
    DISCARD_ALL,
    WILD_DRAW_SIX,
    WILD_REVERSE_DRAW_FOUR,

    // All Wild Actions
    WILD_STANDARD,
    WILD_DRAW_TWO,
    WILD_DRAW_FOUR,
    WILD_DRAW_COLOR,
    WILD_SKIP,
    WILD_SKIP_TWO,
    WILD_TARGETED_DRAW_FOUR,
    WILD_FORCED_SWAP,

    // Party Actions
    POINT_TAKEN,
    DRAWN_TOGETHER,

    // Showdown Actions
    SHOWDOWN,

    // Themed Actions (Jurassic / Minecraft)
    DANGER_RAPTOR,
    CREEPER,
    SHIELD_DEFENSE
}

data class CardFace(
    val color: CardColor,
    val value: CardValue,
    val type: CardType,
    val power: Int = 0,
    val drawPenalty: Int = 0
) {
    fun isWild(): Boolean = type == CardType.WILD || color == CardColor.WILD
    fun isNumber(): Boolean = type == CardType.NUMBER
    fun isAction(): Boolean = type == CardType.ACTION
}

data class GameCard(
    val id: String = UUID.randomUUID().toString(),
    val lightSide: CardFace,
    val darkSide: CardFace? = null
) {
    fun getActiveFace(side: CardSide): CardFace {
        return if (side == CardSide.DARK && darkSide != null) {
            darkSide
        } else {
            lightSide
        }
    }

    companion object {
        fun simple(color: CardColor, value: CardValue, type: CardType, power: Int = 0, drawPenalty: Int = 0): GameCard {
            return GameCard(
                lightSide = CardFace(color, value, type, power, drawPenalty)
            )
        }

        fun dual(light: CardFace, dark: CardFace): GameCard {
            return GameCard(
                lightSide = light,
                darkSide = dark
            )
        }
    }
}
