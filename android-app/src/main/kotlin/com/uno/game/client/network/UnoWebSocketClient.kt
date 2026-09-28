package com.uno.game.client.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.uno.game.client.model.CardColor
import com.uno.game.client.model.GameStateDto
import com.uno.game.client.model.InboundMessage
import com.uno.game.client.model.OutboundBroadcast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class UnoWebSocketClient(
    private val onStateUpdated: (GameStateDto) -> Unit,
    private val onLogMessage: (String) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String) -> Unit
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val seqCounter = AtomicLong(1)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPlayerId: String = ""
    private var currentRoomId: String = ""

    fun connect(serverUrl: String, playerId: String, roomId: String) {
        this.currentPlayerId = playerId
        this.currentRoomId = roomId

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("UnoWebSocket", "Connected to game server: $serverUrl")
                mainHandler.post {
                    onConnected()
                    onLogMessage("Connected to server. Joining room $roomId...")
                    joinRoom()
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val broadcast = gson.fromJson(text, OutboundBroadcast::class.java)
                    mainHandler.post {
                        broadcast.gameState?.let { state ->
                            onStateUpdated(state)
                        }
                        broadcast.message?.let { msg ->
                            onLogMessage(msg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UnoWebSocket", "Failed to parse message: $text", e)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    onDisconnected("Connection closing: $reason ($code)")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    onDisconnected("Connection error: ${t.message}")
                }
            }
        })
    }

    private fun joinRoom() {
        send("JOIN_ROOM", mapOf("playerId" to currentPlayerId, "roomId" to currentRoomId))
    }

    fun playCard(cardId: String, chosenColor: CardColor? = null, targetPlayerId: String? = null) {
        val payload = mutableMapOf<String, Any?>(
            "cardId" to cardId
        )
        chosenColor?.let { payload["chosenColor"] = it.name }
        targetPlayerId?.let { payload["targetPlayerId"] = it }

        send("PLAY_CARD", payload)
    }

    fun drawCard() {
        send("DRAW_CARD", emptyMap())
    }

    fun callUno() {
        send("CALL_UNO", emptyMap())
    }

    fun challengeUno(targetPlayerId: String? = null) {
        val payload = mutableMapOf<String, Any?>()
        targetPlayerId?.let { payload["targetPlayerId"] = it }
        send("CHALLENGE_UNO", payload)
    }

    fun discardAll(color: CardColor) {
        send("DISCARD_ALL", mapOf("color" to color.name))
    }

    private fun send(actionType: String, payload: Map<String, Any?>) {
        val msg = InboundMessage(
            seqId = seqCounter.getAndIncrement(),
            type = actionType,
            playerId = currentPlayerId,
            roomId = currentRoomId,
            payload = payload
        )
        val json = gson.toJson(msg)
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }
}
