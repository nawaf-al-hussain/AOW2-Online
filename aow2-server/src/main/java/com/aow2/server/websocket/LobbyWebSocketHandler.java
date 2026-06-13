package com.aow2.server.websocket;

import com.aow2.server.model.GameSession;
import com.aow2.server.security.JwtUtil;
import com.aow2.server.service.MatchmakingService;
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
 * WebSocket handler for lobby events: join queue, match found, game start signal.
 * Manages the real-time matchmaking communication between the server and clients.
 * REF: session_lifecycle.md - Lobby state (aO=14), matchmaking state machine
 * REF: protocol_specification.md - Type 3 PLAYER_COUNT, Type 12 MATCH_START
 */
@Component
public class LobbyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LobbyWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Active lobby WebSocket sessions keyed by session ID */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final MatchmakingService matchmakingService;
    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    /**
     * Constructs the LobbyWebSocketHandler.
     *
     * @param matchmakingService the matchmaking business logic
     * @param sessionService     the session management service
     * @param jwtUtil            the JWT utility for token validation
     */
    public LobbyWebSocketHandler(MatchmakingService matchmakingService,
                                  SessionService sessionService,
                                  JwtUtil jwtUtil) {
        this.matchmakingService = matchmakingService;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("Lobby WebSocket connected: {}", session.getId());
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
            case "join_queue" -> handleJoinQueue(session);
            case "leave_queue" -> handleLeaveQueue(session);
            case "ready" -> handleReady(session);
            default -> sendError(session, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId != null) {
            matchmakingService.leaveQueue(playerId);
            log.info("Player {} left lobby (session: {})", playerId, session.getId());
        }
        sessionService.unregisterWebSocketSession(session.getId());
    }

    /**
     * Handles authentication via WebSocket.
     * Validates the JWT token and associates the WebSocket session with a player.
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
        log.info("Lobby WebSocket authenticated: player {} on session {}", playerId, session.getId());
    }

    /**
     * Handles a join_queue request.
     * Adds the player to the matchmaking queue and notifies if a match is found.
     *
     * @param session the WebSocket session
     */
    private void handleJoinQueue(WebSocketSession session) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        Map<String, Object> result = matchmakingService.joinQueue(playerId);
        if ("match_found".equals(result.get("status"))) {
            Long player1Id = (Long) result.get("player1Id");
            Long player2Id = (Long) result.get("player2Id");
            var gameSession = sessionService.createSession(player1Id, player2Id, "default");

            // Notify both players
            String ws1 = getSessionForPlayer(player1Id);
            String ws2 = getSessionForPlayer(player2Id);

            Map<String, Object> matchMsg = Map.of(
                    "type", "match_found",
                    "sessionUuid", gameSession.getSessionUuid(),
                    "player1Id", player1Id,
                    "player2Id", player2Id,
                    "mapName", gameSession.getMapName()
            );

            if (ws1 != null) sendToSessionId(ws1, matchMsg);
            if (ws2 != null) sendToSessionId(ws2, matchMsg);
        } else {
            sendMessage(session, Map.of("type", "queued", "playerId", playerId));
        }
    }

    /**
     * Handles a leave_queue request.
     *
     * @param session the WebSocket session
     */
    private void handleLeaveQueue(WebSocketSession session) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }
        matchmakingService.leaveQueue(playerId);
        sendMessage(session, Map.of("type", "left_queue", "playerId", playerId));
    }

    /**
     * Handles a ready signal from a player.
     * When both players signal ready, the game session transitions to STARTING.
     * REF: session_lifecycle.md - Both players confirm → match begins
     *
     * @param session the WebSocket session
     */
    private void handleReady(WebSocketSession session) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        var gameSession = sessionService.getSessionForPlayer(playerId);
        if (gameSession.isEmpty()) {
            sendError(session, "No active session");
            return;
        }

        var gs = gameSession.get();
        if (gs.getState() == GameSession.SessionState.WAITING) {
            sessionService.startSession(gs.getSessionUuid());

            // Notify both players that the game is starting
            String ws1 = getSessionForPlayer(gs.getPlayer1Id());
            String ws2 = getSessionForPlayer(gs.getPlayer2Id());

            Map<String, Object> startMsg = Map.of(
                    "type", "game_start",
                    "sessionUuid", gs.getSessionUuid(),
                    "player1Id", gs.getPlayer1Id(),
                    "player2Id", gs.getPlayer2Id()
            );

            if (ws1 != null) sendToSessionId(ws1, startMsg);
            if (ws2 != null) sendToSessionId(ws2, startMsg);

            // Link players for P2P relay
            if (ws1 != null && ws2 != null) {
                sessionService.linkPlayers(gs.getPlayer1Id(), gs.getPlayer2Id(), ws1, ws2);
            }
        }
    }

    /**
     * Gets the WebSocket session ID for a specific player.
     *
     * @param playerId the player's ID
     * @return the WebSocket session ID, or null
     */
    private String getSessionForPlayer(Long playerId) {
        for (var entry : sessions.entrySet()) {
            Long pid = sessionService.getPlayerForWsSession(entry.getKey());
            if (playerId.equals(pid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Sends a JSON message to the specified WebSocket session ID.
     *
     * @param sessionId the target WebSocket session ID
     * @param data      the data to send
     */
    private void sendToSessionId(String sessionId, Map<String, Object> data) throws IOException {
        WebSocketSession ws = sessions.get(sessionId);
        if (ws != null && ws.isOpen()) {
            sendMessage(ws, data);
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
