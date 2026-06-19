package com.aow2.server.config;

import com.aow2.server.websocket.ChatWebSocketHandler;
import com.aow2.server.websocket.GameWebSocketHandler;
import com.aow2.server.websocket.LobbyWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for the AOW2 multiplayer server.
 * Registers WebSocket endpoints for lobby matchmaking, in-game signaling, and chat.
 * REF: multiplayer_architecture.md - TCP sender/receiver thread architecture
 * REF: protocol_specification.md - Type 12 MATCH_START, Type 30 GAME_STATE
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LobbyWebSocketHandler lobbyWebSocketHandler;
    private final GameWebSocketHandler gameWebSocketHandler;
    private final ChatWebSocketHandler chatWebSocketHandler;

    @Value("${aow2.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Constructs the WebSocket configuration with the required handlers.
     *
     * @param lobbyWebSocketHandler handler for lobby/matchmaking events
     * @param gameWebSocketHandler  handler for in-game lockstep signaling
     * @param chatWebSocketHandler  handler for real-time chat messages
     */
    public WebSocketConfig(LobbyWebSocketHandler lobbyWebSocketHandler,
                           GameWebSocketHandler gameWebSocketHandler,
                           ChatWebSocketHandler chatWebSocketHandler) {
        this.lobbyWebSocketHandler = lobbyWebSocketHandler;
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // REF: session_lifecycle.md - Lobby state (aO=14), matchmaking events
        registry.addHandler(lobbyWebSocketHandler, "/ws/lobby")
                .setAllowedOrigins(allowedOrigins);

        // REF: multiplayer_architecture.md - Game signaling for P2P lockstep
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOrigins(allowedOrigins);

        // REF: protocol_specification.md - Chat messages between players
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .setAllowedOrigins(allowedOrigins);
    }
}
