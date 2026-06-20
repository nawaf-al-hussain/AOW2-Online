package com.aow2.server.service;

import com.aow2.server.model.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the SessionService game session lifecycle management.
 * Verifies session creation, state transitions, and desync detection.
 * REF: session_lifecycle.md - Complete session lifecycle
 */
class SessionServiceTest {

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService();
    }

    @Test
    @DisplayName("Create session sets WAITING state")
    void createSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        assertNotNull(session.getSessionUuid());
        assertEquals(GameSession.SessionState.WAITING, session.getState());
        assertEquals(1L, session.getPlayer1Id());
        assertEquals(2L, session.getPlayer2Id());
    }

    @Test
    @DisplayName("Start session transitions to STARTING")
    void startSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        Optional<GameSession> started = sessionService.startSession(session.getSessionUuid());
        assertTrue(started.isPresent());
        assertEquals(GameSession.SessionState.STARTING, started.get().getState());
        assertNotNull(started.get().getStartedAt());
    }

    @Test
    @DisplayName("Cannot start non-WAITING session")
    void cannotStartNonWaitingSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        sessionService.startSession(session.getSessionUuid());
        Optional<GameSession> result = sessionService.startSession(session.getSessionUuid());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Activate session transitions to ACTIVE")
    void activateSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        sessionService.startSession(session.getSessionUuid());
        Optional<GameSession> active = sessionService.activateSession(session.getSessionUuid());
        assertTrue(active.isPresent());
        assertEquals(GameSession.SessionState.ACTIVE, active.get().getState());
    }

    @Test
    @DisplayName("Complete session sets winner and duration")
    void completeSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        sessionService.startSession(session.getSessionUuid());
        sessionService.activateSession(session.getSessionUuid());
        Optional<GameSession> completed = sessionService.completeSession(
                session.getSessionUuid(), 1L, 600);
        assertTrue(completed.isPresent());
        assertEquals(GameSession.SessionState.COMPLETED, completed.get().getState());
        assertEquals(1L, completed.get().getWinnerId());
        assertEquals(600, completed.get().getDurationSeconds());
    }

    @Test
    @DisplayName("Disconnect session sets opponent as winner")
    void disconnectSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        session.setState(GameSession.SessionState.ACTIVE);
        Optional<GameSession> disconnected = sessionService.disconnectSession(
                session.getSessionUuid(), 1L);
        assertTrue(disconnected.isPresent());
        assertEquals(GameSession.SessionState.DISCONNECTED, disconnected.get().getState());
        assertEquals(2L, disconnected.get().getWinnerId());
    }

    @Test
    @DisplayName("Desync detection when hashes differ")
    void desyncDetection() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        boolean desync = sessionService.reportSyncHash(session.getSessionUuid(), 1L, 10, 111L);
        assertFalse(desync, "No desync when only one hash reported");

        desync = sessionService.reportSyncHash(session.getSessionUuid(), 2L, 10, 222L);
        assertTrue(desync, "Desync when hashes differ");
        assertTrue(session.isDesyncDetected());
    }

    @Test
    @DisplayName("No desync when hashes match")
    void noDesyncWhenMatch() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        sessionService.reportSyncHash(session.getSessionUuid(), 1L, 20, 555L);
        boolean desync = sessionService.reportSyncHash(session.getSessionUuid(), 2L, 20, 555L);
        assertFalse(desync);
        assertFalse(session.isDesyncDetected());
    }

    @Test
    @DisplayName("Get session for player")
    void getSessionForPlayer() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        Optional<GameSession> found = sessionService.getSessionForPlayer(1L);
        assertTrue(found.isPresent());
        assertEquals(session.getSessionUuid(), found.get().getSessionUuid());
    }

    @Test
    @DisplayName("Get session for non-participating player returns empty")
    void getSessionForNonParticipant() {
        sessionService.createSession(1L, 2L, "test_map");
        Optional<GameSession> found = sessionService.getSessionForPlayer(3L);
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Register and lookup WebSocket sessions")
    void webSocketSessionManagement() {
        sessionService.registerWebSocketSession("ws-1", 1L);
        assertEquals(1L, sessionService.getPlayerForWsSession("ws-1"));

        sessionService.unregisterWebSocketSession("ws-1");
        assertEquals(null, sessionService.getPlayerForWsSession("ws-1"));
    }

    @Test
    @DisplayName("Link players for P2P relay")
    void linkPlayers() {
        sessionService.registerWebSocketSession("ws-1", 1L);
        sessionService.registerWebSocketSession("ws-2", 2L);
        sessionService.linkPlayers(1L, 2L, "ws-1", "ws-2");

        assertEquals("ws-2", sessionService.getOpponentWsSession(1L));
        assertEquals("ws-1", sessionService.getOpponentWsSession(2L));
    }

    @Test
    @DisplayName("Remove session cleans up all mappings")
    void removeSession() {
        GameSession session = sessionService.createSession(1L, 2L, "test_map");
        sessionService.registerWebSocketSession("ws-1", 1L);
        sessionService.registerWebSocketSession("ws-2", 2L);
        sessionService.linkPlayers(1L, 2L, "ws-1", "ws-2");

        sessionService.removeSession(session.getSessionUuid());

        assertTrue(sessionService.getSessionForPlayer(1L).isEmpty());
        assertTrue(sessionService.getSessionForPlayer(2L).isEmpty());
    }
}
