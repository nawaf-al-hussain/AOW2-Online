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

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Tracks which players have signaled ready, keyed by session UUID */
    private final Map<String, Set<String>> readyPlayers = new ConcurrentHashMap<>();

    /** Lock for atomic ready-check to prevent duplicate session starts. */
    private final Object readyLock = new Object();

    /** Tracks map vetoes per session, keyed by session UUID. Value is the set of vetoed map names. */
    private final Map<String, Set<String>> mapVetoes = new ConcurrentHashMap<>();

    /** Scheduled executor for cleaning up stale readyPlayers entries. */
    private final java.util.concurrent.ScheduledExecutorService readyCleanupExecutor =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ready-cleanup");
                t.setDaemon(true);
                return t;
            });

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

    /**
     * Wires up the background matchmaking callback after bean construction.
     * This ensures background sweep matches are properly notified via WebSocket.
     */
    @PostConstruct
    public void init() {
        matchmakingService.setNotificationCallback((player1Id, player2Id, mapName) -> {
            log.info("Background match callback: player {} vs player {} on map '{}'",
                    player1Id, player2Id, mapName);
            try {
                var gameSession = sessionService.createSession(player1Id, player2Id, mapName);
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
            } catch (Exception e) {
                log.error("Failed to handle background match for {} vs {}", player1Id, player2Id, e);
            }
        });
        // FIX (M-NEW-21): Periodically clean up stale readyPlayers entries.
        // If a session stays in WAITING state with partial ready signals for > 5 minutes,
        // the ready set is removed. This prevents memory accumulation from players who
        // disconnect before both players signal ready.
        readyCleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                readyPlayers.entrySet().removeIf(entry -> {
                    var gameSession = sessionService.getSessionByUuid(entry.getKey());
                    if (gameSession.isEmpty()) {
                        return true; // Session gone, clean up
                    }
                    var gs = gameSession.get();
                    return gs.getState() != GameSession.SessionState.WAITING;
                });
            } catch (Exception e) {
                log.warn("Error in readyPlayers cleanup", e);
            }
        }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);
        log.info("LobbyWebSocketHandler: matchmaking notification callback wired");
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
            case "join_queue" -> handleJoinQueue(session, payload);
            case "leave_queue" -> handleLeaveQueue(session);
            case "ready" -> handleReady(session);
            case "map_veto" -> handleMapVeto(session, payload);
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
     * Accepts an optional "maps" array field containing preferred map names.
     * Adds the player to the matchmaking queue and notifies if a match is found.
     *
     * @param session the WebSocket session
     * @param payload the JSON payload which may contain a "maps" array
     */
    private void handleJoinQueue(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        // Parse optional maps preference from payload
        List<String> preferredMaps = null;
        if (payload.has("maps") && payload.get("maps").isArray()) {
            preferredMaps = new ArrayList<>();
            for (JsonNode mapNode : payload.get("maps")) {
                String mapName = mapNode.asText();
                if (!mapName.isBlank()) {
                    preferredMaps.add(mapName);
                }
            }
            if (preferredMaps.isEmpty()) {
                preferredMaps = null;
            }
        }

        Map<String, Object> result = matchmakingService.joinQueue(playerId, preferredMaps);
        if ("match_found".equals(result.get("status"))) {
            Long player1Id = (Long) result.get("player1Id");
            Long player2Id = (Long) result.get("player2Id");
            String mapName = (String) result.get("mapName");
            var gameSession = sessionService.createSession(player1Id, player2Id, mapName);

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
     * Handles a map_veto request.
     * Logs the veto for future enhancement (full veto UI is not yet implemented).
     * The client can send { "type": "map_veto", "mapName": "some_map" } to veto a proposed map.
     *
     * @param session the WebSocket session
     * @param payload the JSON payload containing a "mapName" field
     */
    private void handleMapVeto(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionService.getPlayerForWsSession(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        String mapName = payload.has("mapName") ? payload.get("mapName").asText() : null;
        if (mapName == null || mapName.isBlank()) {
            sendError(session, "mapName is required");
            return;
        }

        // Look up the player's current session to key the veto
        var gameSession = sessionService.getSessionForPlayer(playerId);
        if (gameSession.isEmpty()) {
            sendError(session, "No active session");
            return;
        }

        String sessionUuid = gameSession.get().getSessionUuid();
        mapVetoes.computeIfAbsent(sessionUuid, k -> ConcurrentHashMap.newKeySet()).add(mapName);
        log.info("Player {} vetoed map '{}' for session {}", playerId, mapName, sessionUuid);
        sendMessage(session, Map.of(
                "type", "map_veto_ack",
                "playerId", playerId,
                "vetoedMap", mapName,
                "sessionUuid", sessionUuid,
                "message", "Veto recorded."
        ));
    }

    /**
     * Gets the set of vetoed map names for a given session.
     *
     * @param sessionUuid the session UUID
     * @return unmodifiable set of vetoed map names, or empty set if no vetoes exist
     */
    public Set<String> getMapVetoes(String sessionUuid) {
        return Set.copyOf(mapVetoes.getOrDefault(sessionUuid, Set.of()));
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
     * When BOTH players signal ready, the game session transitions to STARTING.
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
        if (gs.getState() != GameSession.SessionState.WAITING) {
            sendError(session, "Session is not in WAITING state");
            return;
        }

        // Track ready state per session UUID
        String sessionUuid = gs.getSessionUuid();
        boolean shouldStart;
        synchronized (readyLock) {
            Set<String> ready = readyPlayers.computeIfAbsent(sessionUuid, k -> ConcurrentHashMap.newKeySet());
            ready.add(playerId.toString());

            // Send acknowledgement to the player who just readied
            sendMessage(session, Map.of(
                    "type", "ready_ack",
                    "playerId", playerId,
                    "sessionUuid", sessionUuid
            ));

            // Only transition when BOTH players have signaled ready
            if (ready.size() < 2) {
                log.info("Player {} ready for session {}, waiting for opponent", playerId, sessionUuid);
                return;
            }

            // Both players ready — clean up ready tracking
            readyPlayers.remove(sessionUuid);
            shouldStart = true;
        }

        if (shouldStart) {
            sessionService.startSession(sessionUuid);

            // Notify both players that the game is starting
            String ws1 = getSessionForPlayer(gs.getPlayer1Id());
            String ws2 = getSessionForPlayer(gs.getPlayer2Id());

            Map<String, Object> startMsg = Map.of(
                    "type", "game_start",
                    "sessionUuid", sessionUuid,
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
     * Uses the O(1) reverse lookup in SessionService instead of scanning all sessions.
     *
     * @param playerId the player's ID
     * @return the WebSocket session ID, or null
     */
    private String getSessionForPlayer(Long playerId) {
        return sessionService.getWsSessionForPlayer(playerId);
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
