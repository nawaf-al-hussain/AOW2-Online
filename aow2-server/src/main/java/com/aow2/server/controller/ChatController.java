package com.aow2.server.controller;

import com.aow2.common.model.ChatMessageRecord;
import com.aow2.server.model.ChatMessage;
import com.aow2.server.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for chat message persistence and retrieval.
 * Provides endpoints for sending messages and retrieving chat history.
 * Uses Spring's SimpMessagingTemplate for WebSocket broadcast in tandem with ChatWebSocketHandler.
 * REF: protocol_specification.md - Chat messages
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatMessageRepository chatMessageRepository;

    /**
     * Constructs the ChatController.
     *
     * @param chatMessageRepository repository for chat message persistence
     */
    public ChatController(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Sends a chat message and persists it to the database.
     * POST /api/chat/send
     * Body: {"matchId": "uuid", "message": "hello"}
     * The playerId is derived from the authenticated user.
     *
     * @param authentication the authenticated player
     * @param body           the request body containing matchId and message
     * @return 200 with the saved chat message, or 400 if invalid
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(
            Authentication authentication,
            @RequestBody Map<String, Object> body
    ) {
        Long playerId = (Long) authentication.getPrincipal();

        String matchId = body.get("matchId") != null ? body.get("matchId").toString() : null;
        String message = body.get("message") != null ? body.get("message").toString() : null;

        if (matchId == null || matchId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "matchId is required"));
        }
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        if (message.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "message must be 500 characters or less"));
        }

        ChatMessage chatMessage = new ChatMessage(matchId, playerId.intValue(), message);
        chatMessage = chatMessageRepository.save(chatMessage);

        log.debug("Chat message saved: player={} match={} msg={}", playerId, matchId, message);

        return ResponseEntity.ok(Map.of(
                "id", chatMessage.getId(),
                "matchId", chatMessage.getMatchId(),
                "playerId", chatMessage.getPlayerId(),
                "message", chatMessage.getMessage(),
                "timestamp", chatMessage.getTimestamp().toEpochMilli()
        ));
    }

    /**
     * Retrieves chat history for a specific match.
     * GET /api/chat/history/{matchId}
     *
     * @param matchId the match/session UUID
     * @return 200 with a list of chat messages in chronological order
     */
    @GetMapping("/history/{matchId}")
    public ResponseEntity<List<ChatMessageRecord>> getChatHistory(@PathVariable String matchId) {
        List<ChatMessage> messages = chatMessageRepository.findByMatchIdOrderByTimestampAsc(matchId);

        List<ChatMessageRecord> records = messages.stream()
                .map(msg -> new ChatMessageRecord(
                        msg.getMatchId(),
                        msg.getPlayerId(),
                        msg.getMessage(),
                        msg.getTimestamp().toEpochMilli()
                ))
                .toList();

        return ResponseEntity.ok(records);
    }
}
