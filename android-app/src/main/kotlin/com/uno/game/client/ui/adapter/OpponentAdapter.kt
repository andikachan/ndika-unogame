package com.uno.game.client.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.uno.game.client.R
import com.uno.game.client.model.PlayerDto

class OpponentAdapter(
    private var opponents: List<PlayerDto> = emptyList(),
    private var currentTurnPlayerId: String = "",
    private val onOpponentClicked: (PlayerDto) -> Unit
) : RecyclerView.Adapter<OpponentAdapter.OpponentViewHolder>() {

    fun updateOpponents(newOpponents: List<PlayerDto>, activeTurnId: String) {
        this.opponents = newOpponents
        this.currentTurnPlayerId = activeTurnId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OpponentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_opponent, parent, false)
        return OpponentViewHolder(view)
    }

    override fun onBindViewHolder(holder: OpponentViewHolder, position: Int) {
        val player = opponents[position]
        holder.bind(player, player.id == currentTurnPlayerId, onOpponentClicked)
    }

    override fun getItemCount(): Int = opponents.size

    class OpponentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOpponentName: TextView = itemView.findViewById(R.id.tvOpponentName)
        private val tvOpponentCards: TextView = itemView.findViewById(R.id.tvOpponentCards)
        private val tvTurnIndicator: TextView = itemView.findViewById(R.id.tvTurnIndicator)

        fun bind(player: PlayerDto, isTurn: Boolean, onClick: (PlayerDto) -> Unit) {
            tvOpponentName.text = if (player.unoCalled) "${player.name} [UNO!]" else player.name
            tvOpponentCards.text = if (player.isEliminated) "☠️ ELIMINATED" else "🎴 ${player.cardCount} Kartu"

            if (isTurn) {
                tvTurnIndicator.visibility = View.VISIBLE
                itemView.setBackgroundColor(Color.parseColor("#1B4332"))
            } else {
                tvTurnIndicator.visibility = View.GONE
                itemView.setBackgroundColor(Color.parseColor("#2A344A"))
            }

            itemView.setOnClickListener {
                onClick(player)
            }
        }
    }
}
