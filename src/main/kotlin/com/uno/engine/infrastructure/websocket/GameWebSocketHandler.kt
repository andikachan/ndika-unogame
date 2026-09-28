package com.uno.engine.infrastructure.websocket

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

@Component
class GameWebSocketHandler(
    private val sessionManager: WebSocketSessionManager,
    private val inboundMessageRouter: InboundMessageRouter
) : WebSocketHandler {
    private val logger = LoggerFactory.getLogger(GameWebSocketHandler::class.java)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val clientIp = session.handshakeInfo.remoteAddress?.address?.hostAddress ?: "127.0.0.1"
        val sink = Sinks.many().multicast().onBackpressureBuffer<String>()

        val input = session.receive()
            .doOnNext { msg ->
                val payload = msg.payloadAsText
                inboundMessageRouter.handleRawMessage(payload, clientIp)
            }
            .doOnError { e ->
                logger.error("WebSocket error on session ${session.id}: ${e.message}")
            }
            .doFinally {
                // Cleanup on disconnect
                logger.info("WebSocket connection closed for session ${session.id}")
            }
            .then()

        val output = session.send(
            sink.asFlux().map { session.textMessage(it) }
        )

        return Mono.zip(input, output).then()
    }
}
