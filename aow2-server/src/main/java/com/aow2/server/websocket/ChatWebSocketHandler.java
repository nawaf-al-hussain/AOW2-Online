package com.aow2.server.websocket;

import com.aow2.server.model.ChatMessage;
import com.aow2.server.repository.ChatMessageRepository;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time chat messages during multiplayer matches.
 * Broadcasts chat messages to all connected players in the same session.
 * Message format: {"type":"chat","playerId":0,"message":"hello","timestamp":123456}
 * REF: protocol_specification.md - Chat messages between players
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Active chat WebSocket sessions keyed by session ID */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** Maps WebSocket session ID to the match/session UUID they are chatting in */
    private final Map<String, String> sessionToMatch = new ConcurrentHashMap<>();

    /** Maps WebSocket session ID to the player ID */
    private final Map<String, Long> sessionToPlayer = new ConcurrentHashMap<>();

    /** Tracks chat participants per match UUID for efficient broadcasting */
    private final Map<String, Set<String>> matchParticipants = new ConcurrentHashMap<>();

    private final ChatMessageRepository chatMessageRepository;
    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    /**
     * Constructs the ChatWebSocketHandler.
     *
     * @param chatMessageRepository repository for persisting chat messages
     * @param sessionService        the session management service
     * @param jwtUtil               the JWT utility for token validation
     */
    public ChatWebSocketHandler(ChatMessageRepository chatMessageRepository,
                                SessionService sessionService,
                                JwtUtil jwtUtil) {
        this.chatMessageRepository = chatMessageRepository;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("Chat WebSocket connected: {}", session.getId());
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
            case "chat" -> handleChat(session, payload);
            case "join" -> handleJoin(session, payload);
            case "ping" -> sendMessage(session, Map.of("type", "pong"));
            default -> sendError(session, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        String matchId = sessionToMatch.remove(session.getId());
        sessionToPlayer.remove(session.getId());

        if (matchId != null) {
            Set<String> participants = matchParticipants.get(matchId);
            if (participants != null) {
                participants.remove(session.getId());
                if (participants.isEmpty()) {
                    matchParticipants.remove(matchId);
                }
            }
        }

        log.info("Chat WebSocket disconnected: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Chat WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    /**
     * Handles authentication via WebSocket.
     * Associates the chat WebSocket session with a player.
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

        sessionToPlayer.put(session.getId(), playerId);
        sendMessage(session, Map.of("type", "auth_ok", "playerId", playerId));
        log.info("Chat WebSocket authenticated: player {} on session {}", playerId, session.getId());
    }

    /**
     * Handles a join request to associate a chat session with a specific match.
     * This allows the handler to broadcast messages only to players in the same match.
     *
     * @param session the WebSocket session
     * @param payload contains "matchId" field
     */
    private void handleJoin(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionToPlayer.get(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        String matchId = payload.has("matchId") ? payload.get("matchId").asText() : null;
        if (matchId == null || matchId.isBlank()) {
            sendError(session, "matchId is required");
            return;
        }

        // FIX (C-NEW-6): Validate that this player is a participant in the match.
        // Prevents eavesdropping on arbitrary match chats.
        var gameSession = sessionService.getSessionForPlayer(playerId);
        if (gameSession.isEmpty() || !gameSession.get().getSessionUuid().equals(matchId)) {
            sendError(session, "Not a participant in this match");
            return;
        }

        // Remove from previous match if any
        String prevMatch = sessionToMatch.get(session.getId());
        if (prevMatch != null) {
            Set<String> prev = matchParticipants.get(prevMatch);
            if (prev != null) {
                prev.remove(session.getId());
            }
        }

        sessionToMatch.put(session.getId(), matchId);
        matchParticipants.computeIfAbsent(matchId, k -> ConcurrentHashMap.newKeySet())
                .add(session.getId());

        sendMessage(session, Map.of("type", "join_ok", "matchId", matchId));
        log.info("Player {} joined chat for match {}", playerId, matchId);
    }

    /**
     * Handles a chat message from a player.
     * Persists the message and broadcasts it to all players in the same match.
     * Message format: {"type":"chat","playerId":0,"message":"hello","timestamp":123456}
     * REF: protocol_specification.md - Chat messages
     *
     * @param session the sending player's WebSocket session
     * @param payload the chat message containing "message" field
     */
    private void handleChat(WebSocketSession session, JsonNode payload) throws IOException {
        Long playerId = sessionToPlayer.get(session.getId());
        if (playerId == null) {
            sendError(session, "Not authenticated");
            return;
        }

        String matchId = sessionToMatch.get(session.getId());
        if (matchId == null) {
            sendError(session, "Not joined to a match chat");
            return;
        }

        String message = payload.has("message") ? payload.get("message").asText() : null;
        if (message == null || message.isBlank()) {
            sendError(session, "message is required");
            return;
        }
        if (message.length() > 500) {
            sendError(session, "message must be 500 characters or less");
            return;
        }

        // Persist the message
        ChatMessage chatMessage = new ChatMessage(matchId, playerId, message);
        chatMessageRepository.save(chatMessage);

        // Broadcast to all participants in the match
        long timestamp = chatMessage.getTimestamp().toEpochMilli();
        Map<String, Object> broadcastMsg = Map.of(
                "type", "chat",
                "playerId", playerId,
                "message", message,
                "timestamp", timestamp
        );

        broadcastToMatch(matchId, broadcastMsg);
        log.debug("Chat broadcast: player={} match={} msg={}", playerId, matchId, message);
    }

    /**
     * Broadcasts a message to all WebSocket sessions in the same match.
     *
     * @param matchId the match UUID
     * @param data    the data to broadcast
     */
    private void broadcastToMatch(String matchId, Map<String, Object> data) {
        Set<String> participants = matchParticipants.get(matchId);
        if (participants == null) return;

        for (String sessionId : participants) {
            WebSocketSession ws = sessions.get(sessionId);
            if (ws != null && ws.isOpen()) {
                try {
                    sendMessage(ws, data);
                } catch (IOException e) {
                    log.warn("Failed to broadcast to session {}: {}", sessionId, e.getMessage());
                }
            }
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
