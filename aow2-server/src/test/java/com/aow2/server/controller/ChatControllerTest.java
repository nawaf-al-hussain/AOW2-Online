package com.aow2.server.controller;

import com.aow2.server.model.ChatMessage;
import com.aow2.server.repository.ChatMessageRepository;
import com.aow2.server.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the ChatController REST endpoints.
 * Verifies sending and retrieving chat messages.
 * REF: protocol_specification.md - Chat messages
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private org.springframework.security.core.Authentication authentication;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatMessageRepository, sessionService);
    }

    @Test
    @DisplayName("Send chat message returns 200 with saved message")
    void sendMessageSuccess() {
        when(authentication.getPrincipal()).thenReturn(1L);

        ChatMessage saved = new ChatMessage("match-uuid-1", 1L, "hello");
        saved.setId(1L);
        saved.setTimestamp(Instant.now());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(saved);

        ResponseEntity<Map<String, Object>> response = chatController.sendMessage(
                authentication,
                Map.of("matchId", "match-uuid-1", "message", "hello")
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().get("id"));
        assertEquals("match-uuid-1", response.getBody().get("matchId"));
        assertEquals(1, response.getBody().get("playerId"));
        assertEquals("hello", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Send message without matchId returns 400")
    void sendMessageNoMatchId() {
        when(authentication.getPrincipal()).thenReturn(1L);

        ResponseEntity<Map<String, Object>> response = chatController.sendMessage(
                authentication,
                Map.of("message", "hello")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Send message without message content returns 400")
    void sendMessageNoMessage() {
        when(authentication.getPrincipal()).thenReturn(1L);

        ResponseEntity<Map<String, Object>> response = chatController.sendMessage(
                authentication,
                Map.of("matchId", "match-uuid-1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Send message exceeding 500 characters returns 400")
    void sendMessageTooLong() {
        when(authentication.getPrincipal()).thenReturn(1L);

        String longMessage = "a".repeat(501);
        ResponseEntity<Map<String, Object>> response = chatController.sendMessage(
                authentication,
                Map.of("matchId", "match-uuid-1", "message", longMessage)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Get chat history returns messages for match")
    void getChatHistory() {
        ChatMessage msg1 = new ChatMessage("match-uuid-1", 1L, "hello");
        msg1.setTimestamp(Instant.now().minusSeconds(10));
        ChatMessage msg2 = new ChatMessage("match-uuid-1", 2L, "hi there");
        msg2.setTimestamp(Instant.now());

        when(chatMessageRepository.findByMatchIdOrderByTimestampAsc("match-uuid-1"))
                .thenReturn(List.of(msg1, msg2));

        when(sessionService.getSessionByUuid("match-uuid-1")).thenReturn(java.util.Optional.empty());

        ResponseEntity<List<com.aow2.common.model.ChatMessageRecord>> response =
                chatController.getChatHistory(authentication, "match-uuid-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("hello", response.getBody().get(0).message());
        assertEquals("hi there", response.getBody().get(1).message());
    }

    @Test
    @DisplayName("Get chat history for empty match returns empty list")
    void getChatHistoryEmpty() {
        when(chatMessageRepository.findByMatchIdOrderByTimestampAsc("empty-match"))
                .thenReturn(List.of());

        when(sessionService.getSessionByUuid("empty-match")).thenReturn(java.util.Optional.empty());

        ResponseEntity<List<com.aow2.common.model.ChatMessageRecord>> response =
                chatController.getChatHistory(authentication, "empty-match");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
    }
}
