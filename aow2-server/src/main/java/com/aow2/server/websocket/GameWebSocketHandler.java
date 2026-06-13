package com.aow2.server.websocket;

import com.aow2.server.model.GameSession;
import com.aow2.server.security.JwtUtil;
import com.aow2.server.service.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for in-game signaling: P2P connection setup, command relay, desync detection.
 * Routes lockstep commands between players and monitors state hash consistency.
 * REF: multiplayer_architecture.md - Lockstep P2P model, sender/receiver threads
 * REF: protocol_specification.md - Type 30 GAME_STATE, Type 101 GAME_TICK
 * REF: protocol_specification.md - Desync: turn sequence validation, state hash comparison
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Active game WebSocket sessions keyed by session ID */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    /**
     * Constructs the GameWebSocketHandler.
     *
     * @param sessionService the session management service
     * @param jwtUtil        the JWT utility for token validation
     */
    public GameWebSocketHandler(SessionService sessionService, JwtUtil jwtUtil) {
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("Game WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "Invalid JSON payload");
            return;
        }

        String type = payload.has("type") ? payload.get("type").asText() : "";
        switch (type) {
            case "auth" -> handleAuth(session, payload);
            case "command" -> handleCommand(session, payload);
            case "sync_hash" -> handleSyncHash(session, payload);
            case "game_over" -> handleGameOver(session, payload);
            case "ping" -> sendMessage(session, Map.of("type", "pong"));
            default -> sendError(session, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId != null) {
            // Check if player was in an active session and mark as disconnected
            var gameSession = sessionService.getSessionForPlayer(playerId);
            if (gameSession.isPresent()
                    && gameSession.get().getState() == GameSession.SessionState.ACTIVE) {
                sessionService.disconnectSession(gameSession.get().getSessionUuid(), playerId);
                // Notify opponent
                String opponentWs = sessionService.getOpponentWsSession(playerId);
                if (opponentWs != null) {
                    sendToSessionId(opponentWs, Map.of(
                            "type", "opponent_disconnected",
                            "playerId", playerId
                    ));
                }
            }
            log.info("Player {} disconnected from game session", playerId);
        }
        sessionService.unregisterWebSocketSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Game WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Handles authentication via WebSocket.
     * Associates the game WebSocket session with a player.
     *
     * @param session the WebSocket session
     * @param payload the auth message containing a "token" field
     */
    private void handleAuth(WebSocketSession session, JsonNode payload) throws IOException {
        String token = payload.has("token") ? payload.get("token").asText() : "";
        Long playerId = jwtUtil.getPlayerId(token);
        if (playerId == null) {
            sendError(session, "Invalid authentication token");
            return;
        }

        sessionService.registerWebSocketSession(session.getId(), playerId);
        sendMessage(session, Map.of("type", "auth_ok", "playerId", playerId));
        log.info("Game WebSocket authenticated: player {} on session {}", playerId, session.getId());
    }

    /**
     * Relays a player command to their opponent.
     * Commands are forwarded unmodified to ensure lockstep determinism.
     * REF: multiplayer_architecture.md - Both clients run identical game state, exchanging only commands
     *
     * @param session the sending player's WebSocket session
     * @param payload the command message to relay
     */
    private void handleCommand(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        String opponentWs = sessionService.getOpponentWsSession(playerId);
        if (opponentWs == null) {
            sendError(session, "No opponent connected");
            return;
        }

        // Relay the command to the opponent with the sender's player ID
        Map<String, Object> relayMsg = Map.of(
                "type", "command",
                "fromPlayerId", playerId,
                "command", payload.get("command")
        );
        sendToSessionId(opponentWs, relayMsg);
    }

    /**
     * Processes a sync hash report from a player.
     * Compares with the opponent's hash to detect desync conditions.
     * REF: multiplayer_architecture.md - Data integrity: turn sequence validation
     * REF: protocol_specification.md - Desync detection via state comparison
     *
     * @param session the reporting player's WebSocket session
     * @param payload contains "sessionUuid", "tick", and "hash" fields
     */
    private void handleSyncHash(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        String sessionUuid = payload.has("sessionUuid") ? payload.get("sessionUuid").asText() : "";
        long hash = payload.has("hash") ? payload.get("hash").asLong() : 0;
        long tick = payload.has("tick") ? payload.get("tick").asLong() : 0;

        boolean desync = sessionService.reportSyncHash(sessionUuid, playerId, hash);
        if (desync) {
            // Notify both players of the desync
            var gameSession = sessionService.getSessionForPlayer(playerId);
            if (gameSession.isPresent()) {
                String ws1 = sessionService.getOpponentWsSession(gameSession.get().getPlayer1Id());
                String ws2 = sessionService.getOpponentWsSession(gameSession.get().getPlayer2Id());

                Map<String, Object> desyncMsg = Map.of(
                        "type", "desync",
                        "tick", tick,
                        "sessionUuid", sessionUuid
                );

                if (ws1 != null) sendToSessionId(ws1, desyncMsg);
                if (ws2 != null) sendToSessionId(ws2, desyncMsg);
            }
        }
    }

    /**
     * Handles a game_over signal from a player.
     * Records the match result and notifies the opponent.
     * REF: protocol_specification.md - Type 33 GAME_RESULT
     *
     * @param session the reporting player's WebSocket session
     * @param payload contains "sessionUuid", "winnerId", and "durationSeconds"
     */
    private void handleGameOver(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        String sessionUuid = payload.has("sessionUuid") ? payload.get("sessionUuid").asText() : "";
        Long winnerId = payload.has("winnerId") ? payload.get("winnerId").asLong() : null;
        int durationSeconds = payload.has("durationSeconds") ? payload.get("durationSeconds").asInt() : 0;

        sessionService.completeSession(sessionUuid, winnerId, durationSeconds);

        // Notify opponent
        String opponentWs = sessionService.getOpponentWsSession(playerId);
        if (opponentWs != null) {
            sendToSessionId(opponentWs, Map.of(
                    "type", "game_over",
                    "winnerId", winnerId,
                    "sessionUuid", sessionUuid
            ));
        }
    }

    /**
     * Sends a JSON message to the specified WebSocket session ID.
     *
     * @param sessionId the target WebSocket session ID
     * @param data      the data to send
     */
    private void sendToSessionId(String sessionId, Map<String, Object> data) {
        try {
            WebSocketSession ws = sessions.get(sessionId);
            if (ws != null && ws.isOpen()) {
                sendMessage(ws, data);
            }
        } catch (IOException e) {
            log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Sends a JSON message to a WebSocket session.
     *
     * @param session the target session
     * @param data    the data map to serialize and send
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Sends an error message to a WebSocket session.
     *
     * @param session the target session
     * @param error   the error description
     */
    private void sendError(WebSocketSession session, String error) throws IOException {
        sendMessage(session, Map.of("type", "error", "message", error));
    }
}
