package com.uno.engine.domain.variants

import com.uno.engine.domain.model.GameVariant
import org.springframework.stereotype.Component

@Component
class VariantRegistry {
    private val rulesetMap: Map<GameVariant, VariantRuleset> = mapOf(
        GameVariant.CLASSIC to UnoClassicRuleset(),
        GameVariant.FLIP to UnoFlipRuleset(),
        GameVariant.NO_MERCY to UnoNoMercyRuleset(),
        GameVariant.PARTY to UnoPartyRuleset(),
        GameVariant.SHOWDOWN to UnoShowdownRuleset(),
        GameVariant.ALL_WILD to UnoAllWildRuleset(),
        GameVariant.THEMED_JURASSIC to ThemedUnoRuleset(GameVariant.THEMED_JURASSIC),
        GameVariant.THEMED_MINECRAFT to ThemedUnoRuleset(GameVariant.THEMED_MINECRAFT)
    )

    fun getRuleset(variant: GameVariant): VariantRuleset {
        return rulesetMap[variant] ?: throw IllegalArgumentException("Unsupported game variant: $variant")
    }
}
