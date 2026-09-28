package com.uno.game.client.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.uno.game.client.R
import com.uno.game.client.model.CardColor
import com.uno.game.client.model.CardDto
import com.uno.game.client.model.CardSide
import com.uno.game.client.model.CardType
import com.uno.game.client.model.CardValue
import com.uno.game.client.model.GameStateDto
import com.uno.game.client.network.UnoWebSocketClient
import com.uno.game.client.ui.adapter.CardAdapter
import com.uno.game.client.ui.adapter.OpponentAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var lobbyLayout: ScrollView
    private lateinit var gameBoardLayout: RelativeLayout

    private lateinit var etServerUrl: EditText
    private lateinit var etPlayerName: EditText
    private lateinit var etRoomId: EditText
    private lateinit var spinnerVariant: Spinner
    private lateinit var btnConnect: Button

    private lateinit var tvRoomInfo: TextView
    private lateinit var tvSideInfo: TextView
    private lateinit var tvPenaltyAccumulated: TextView
    private lateinit var tvTurnStatus: TextView
    private lateinit var tvGameLog: TextView

    private lateinit var cardDrawPile: CardView
    private lateinit var viewTopCard: View
    private lateinit var btnCallUno: Button
    private lateinit var btnChallengeUno: Button
    private lateinit var btnDiscardAll: Button

    private lateinit var rvOpponents: RecyclerView
    private lateinit var rvPlayerHand: RecyclerView

    private lateinit var cardAdapter: CardAdapter
    private lateinit var opponentAdapter: OpponentAdapter

    private var currentMyPlayerId: String = ""
    private var currentRoomId: String = ""
    private var lastGameState: GameStateDto? = null

    private val wsClient = UnoWebSocketClient(
        onStateUpdated = { state -> handleStateUpdate(state) },
        onLogMessage = { msg -> logMessage(msg) },
        onConnected = {
            lobbyLayout.visibility = View.GONE
            gameBoardLayout.visibility = View.VISIBLE
        },
        onDisconnected = { reason ->
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        }
    )

    private val variantOptions = listOf(
        "CLASSIC" to "UNO Classic (108 Cards)",
        "FLIP" to "UNO Flip! (112 Dual-Sided Cards)",
        "NO_MERCY" to "UNO Show 'Em No Mercy (168 Cards)",
        "PARTY" to "UNO Party! (Jump-In & Linking)",
        "SHOWDOWN" to "UNO Showdown (112 Cards Duel)",
        "ALL_WILD" to "UNO All Wild (112 Cards)",
        "THEMED_JURASSIC" to "Themed UNO (Jurassic World)",
        "THEMED_MINECRAFT" to "Themed UNO (Minecraft)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupAdapters()
        setupListeners()
    }

    private fun initViews() {
        lobbyLayout = findViewById(R.id.lobbyLayout)
        gameBoardLayout = findViewById(R.id.gameBoardLayout)

        etServerUrl = findViewById(R.id.etServerUrl)
        etPlayerName = findViewById(R.id.etPlayerName)
        etRoomId = findViewById(R.id.etRoomId)
        spinnerVariant = findViewById(R.id.spinnerVariant)
        btnConnect = findViewById(R.id.btnConnect)

        tvRoomInfo = findViewById(R.id.tvRoomInfo)
        tvSideInfo = findViewById(R.id.tvSideInfo)
        tvPenaltyAccumulated = findViewById(R.id.tvPenaltyAccumulated)
        tvTurnStatus = findViewById(R.id.tvTurnStatus)
        tvGameLog = findViewById(R.id.tvGameLog)

        cardDrawPile = findViewById(R.id.cardDrawPile)
        viewTopCard = findViewById(R.id.viewTopCard)
        btnCallUno = findViewById(R.id.btnCallUno)
        btnChallengeUno = findViewById(R.id.btnChallengeUno)
        btnDiscardAll = findViewById(R.id.btnDiscardAll)

        rvOpponents = findViewById(R.id.rvOpponents)
        rvPlayerHand = findViewById(R.id.rvPlayerHand)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            variantOptions.map { it.second }
        )
        spinnerVariant.adapter = adapter
    }

    private fun setupAdapters() {
        cardAdapter = CardAdapter { card ->
            onCardSelected(card)
        }
        rvPlayerHand.adapter = cardAdapter

        opponentAdapter = OpponentAdapter(
            onOpponentClicked = { opponent ->
                wsClient.challengeUno(opponent.id)
            }
        )
        rvOpponents.adapter = opponentAdapter
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            val playerName = etPlayerName.text.toString().trim()
            val roomId = etRoomId.text.toString().trim()

            if (url.isEmpty() || playerName.isEmpty() || roomId.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentMyPlayerId = playerName
            currentRoomId = roomId

            wsClient.connect(url, currentMyPlayerId, currentRoomId)
        }

        cardDrawPile.setOnClickListener {
            wsClient.drawCard()
        }

        btnCallUno.setOnClickListener {
            wsClient.callUno()
            Toast.makeText(this, "🔥 UNO Called!", Toast.LENGTH_SHORT).show()
        }

        btnChallengeUno.setOnClickListener {
            wsClient.challengeUno()
        }

        btnDiscardAll.setOnClickListener {
            lastGameState?.topDiscardCard?.color?.let { color ->
                wsClient.discardAll(color)
            }
        }
    }

    private fun onCardSelected(card: CardDto) {
        if (card.type == CardType.WILD || card.value == CardValue.WILD || card.value == CardValue.WILD_DRAW_FOUR) {
            promptColorSelection(card)
        } else if (card.value == CardValue.SEVEN || card.value == CardValue.WILD_FORCED_SWAP) {
            promptTargetPlayerSelection(card)
        } else {
            wsClient.playCard(card.id)
        }
    }

    private fun promptColorSelection(card: CardDto) {
        val isDarkSide = lastGameState?.activeSide == CardSide.DARK
        val colors = if (isDarkSide) {
            arrayOf("ORANGE", "PINK", "TEAL", "PURPLE")
        } else {
            arrayOf("RED", "BLUE", "GREEN", "YELLOW")
        }

        AlertDialog.Builder(this)
            .setTitle("Choose Active Color")
            .setItems(colors) { _, which ->
                val chosenColor = CardColor.valueOf(colors[which])
                wsClient.playCard(card.id, chosenColor = chosenColor)
            }
            .setCancelable(false)
            .show()
    }

    private fun promptTargetPlayerSelection(card: CardDto) {
        val opponents = lastGameState?.players?.filter { it.id != currentMyPlayerId } ?: emptyList()
        if (opponents.isEmpty()) {
            wsClient.playCard(card.id)
            return
        }

        val names = opponents.map { "${it.name} (${it.cardCount} cards)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Player to Swap Hands")
            .setItems(names) { _, which ->
                val target = opponents[which]
                wsClient.playCard(card.id, targetPlayerId = target.id)
            }
            .setCancelable(false)
            .show()
    }

    private fun handleStateUpdate(state: GameStateDto) {
        this.lastGameState = state

        tvRoomInfo.text = "ROOM: ${state.roomId} | ${state.variant}"
        tvSideInfo.text = "SIDE: ${state.activeSide.name}"
        tvPenaltyAccumulated.text = "PENALTY: +${state.accumulatedDrawPenalty}"

        val isMyTurn = state.currentTurnPlayerId == currentMyPlayerId
        if (isMyTurn) {
            tvTurnStatus.text = "YOUR TURN! 🎯"
            tvTurnStatus.setTextColor(Color.parseColor("#00FF66"))
        } else {
            tvTurnStatus.text = "Awaiting ${state.currentTurnPlayerId}..."
            tvTurnStatus.setTextColor(Color.parseColor("#FFC700"))
        }

        // Show/Hide Discard All button if No Mercy variant and having matching cards
        btnDiscardAll.visibility = if (state.variant.contains("NO_MERCY") && isMyTurn) View.VISIBLE else View.GONE

        // Update Top Discard Card View
        state.topDiscardCard?.let { topCard ->
            val bg = viewTopCard.findViewById<RelativeLayout>(R.id.cardBackground)
            val tvTop = viewTopCard.findViewById<TextView>(R.id.tvTopValue)
            val tvCenter = viewTopCard.findViewById<TextView>(R.id.tvCenterValue)
            val tvBadge = viewTopCard.findViewById<TextView>(R.id.tvCardTypeBadge)

            bg.setBackgroundColor(Color.parseColor(getHexForColor(state.activeColor, state.activeSide)))
            tvTop.text = topCard.value.name
            tvCenter.text = topCard.value.name.take(4)
            tvBadge.text = topCard.type.name
        }

        // Update Hand & Opponents
        cardAdapter.updateCards(state.myHand)
        val opponentList = state.players.filter { it.id != currentMyPlayerId }
        opponentAdapter.updateOpponents(opponentList, state.currentTurnPlayerId)

        // Winner Notification
        state.winnerPlayerId?.let { winnerId ->
            val msg = if (winnerId == currentMyPlayerId) "🏆 CONGRATULATIONS! YOU WON!" else "Game Over! Winner is $winnerId"
            AlertDialog.Builder(this)
                .setTitle("Match Finished")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun getHexForColor(color: CardColor, side: CardSide): String {
        return if (side == CardSide.DARK) {
            when (color) {
                CardColor.ORANGE -> "#FF6600"
                CardColor.PINK -> "#E6007E"
                CardColor.TEAL -> "#00A896"
                CardColor.PURPLE -> "#662D91"
                else -> "#333333"
            }
        } else {
            when (color) {
                CardColor.RED -> "#ED1C24"
                CardColor.BLUE -> "#0054A6"
                CardColor.GREEN -> "#00A651"
                CardColor.YELLOW -> "#FFB800"
                else -> "#444444"
            }
        }
    }

    private fun logMessage(msg: String) {
        tvGameLog.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient.disconnect()
    }
}
