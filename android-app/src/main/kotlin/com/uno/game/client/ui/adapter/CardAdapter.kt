package com.uno.game.client.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uno.game.client.R
import com.uno.game.client.model.CardColor
import com.uno.game.client.model.CardDto
import com.uno.game.client.model.CardSide
import com.uno.game.client.model.CardType
import com.uno.game.client.model.CardValue

class CardAdapter(
    private var cards: List<CardDto> = emptyList(),
    private val onCardClicked: (CardDto) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    fun updateCards(newCards: List<CardDto>) {
        this.cards = newCards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position], onCardClicked)
    }

    override fun getItemCount(): Int = cards.size

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardBackground: RelativeLayout = itemView.findViewById(R.id.cardBackground)
        private val tvTopValue: TextView = itemView.findViewById(R.id.tvTopValue)
        private val tvCenterValue: TextView = itemView.findViewById(R.id.tvCenterValue)
        private val tvCardTypeBadge: TextView = itemView.findViewById(R.id.tvCardTypeBadge)

        fun bind(card: CardDto, onClick: (CardDto) -> Unit) {
            val colorHex = getCardColorHex(card.color, card.side)
            cardBackground.setBackgroundColor(Color.parseColor(colorHex))

            val displayLabel = getCardDisplayLabel(card)
            tvTopValue.text = displayLabel
            tvCenterValue.text = displayLabel
            tvCardTypeBadge.text = if (card.type == CardType.WILD) "★ WILD" else card.type.name

            itemView.setOnClickListener {
                onClick(card)
            }
        }

        private fun getCardColorHex(color: CardColor, side: CardSide): String {
            return if (side == CardSide.DARK) {
                when (color) {
                    CardColor.ORANGE -> "#FF6600"
                    CardColor.PINK -> "#E6007E"
                    CardColor.TEAL -> "#00A896"
                    CardColor.PURPLE -> "#662D91"
                    CardColor.WILD -> "#1A1A1A"
                    else -> "#333333"
                }
            } else {
                when (color) {
                    CardColor.RED -> "#ED1C24"
                    CardColor.BLUE -> "#0054A6"
                    CardColor.GREEN -> "#00A651"
                    CardColor.YELLOW -> "#FFB800"
                    CardColor.WILD -> "#222222"
                    else -> "#444444"
                }
            }
        }

        private fun getCardDisplayLabel(card: CardDto): String {
            return when (card.value) {
                CardValue.ZERO -> "0"
                CardValue.ONE -> "1"
                CardValue.TWO -> "2"
                CardValue.THREE -> "3"
                CardValue.FOUR -> "4"
                CardValue.FIVE -> "5"
                CardValue.SIX -> "6"
                CardValue.SEVEN -> "7"
                CardValue.EIGHT -> "8"
                CardValue.NINE -> "9"
                CardValue.SKIP -> "⊘"
                CardValue.REVERSE -> "⇄"
                CardValue.DRAW_TWO -> "+2"
                CardValue.WILD -> "★"
                CardValue.WILD_DRAW_FOUR -> "+4"
                CardValue.FLIP -> "🔄"
                CardValue.DRAW_ONE -> "+1"
                CardValue.WILD_DRAW_TWO -> "+2"
                CardValue.DARK_DRAW_FIVE -> "+5"
                CardValue.DARK_SKIP_EVERYONE -> "⊘ ALL"
                CardValue.DARK_WILD_DRAW_COLOR -> "🎨"
                CardValue.DRAW_SIX -> "+6"
                CardValue.DRAW_TEN -> "+10"
                CardValue.DISCARD_ALL -> "ALL"
                CardValue.WILD_REVERSE_DRAW_FOUR -> "⇄+4"
                CardValue.POINT_TAKEN -> "👉"
                CardValue.DRAWN_TOGETHER -> "🔗"
                CardValue.SHOWDOWN -> "⚔️"
                CardValue.WILD_SKIP -> "★⊘"
                CardValue.WILD_SKIP_TWO -> "★⊘2"
                CardValue.WILD_TARGETED_DRAW_FOUR -> "🎯+4"
                CardValue.WILD_FORCED_SWAP -> "🔀"
                CardValue.DANGER_RAPTOR -> "🦖"
                CardValue.ESCAPE_BALL -> "🛡️"
                CardValue.CREEPER_EXPLOSION -> "💥"
                CardValue.SHIELD_DEFENSE -> "🛡️"
                else -> "?"
            }
        }
    }
}
