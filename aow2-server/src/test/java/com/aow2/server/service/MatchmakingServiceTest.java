package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the MatchmakingService ELO-based matchmaking logic.
 * Verifies queue management, match creation, and ELO-range pairing.
 * REF: protocol_specification.md - Matchmaking state machine (Section 7)
 */
@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private MatchmakingService matchmakingService;

    @BeforeEach
    void setUp() {
        matchmakingService = new MatchmakingService(playerRepository);
    }

    private Player createPlayer(Long id, int elo) {
        Player p = new Player("player" + id, "hash");
        p.setId(id);
        p.setEloRating(elo);
        return p;
    }

    @Test
    @DisplayName("Join empty queue results in queued status")
    void joinEmptyQueue() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(createPlayer(1L, 1000)));
        Map<String, Object> result = matchmakingService.joinQueue(1L);
        assertEquals("queued", result.get("status"));
        assertEquals(1, matchmakingService.getQueueSize());
    }

    @Test
    @DisplayName("Join queue twice returns already_queued")
    void joinQueueTwice() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(createPlayer(1L, 1000)));
        matchmakingService.joinQueue(1L);
        Map<String, Object> result = matchmakingService.joinQueue(1L);
        assertEquals("already_queued", result.get("status"));
    }

    @Test
    @DisplayName("Match found with similar ELO players")
    void matchFoundSimilarElo() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(createPlayer(1L, 1000)));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(createPlayer(2L, 1020)));

        matchmakingService.joinQueue(1L);
        Map<String, Object> result = matchmakingService.joinQueue(2L);

        assertEquals("match_found", result.get("status"));
        assertEquals(0, matchmakingService.getQueueSize());
    }

    @Test
    @DisplayName("Leave queue removes player")
    void leaveQueue() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(createPlayer(1L, 1000)));
        matchmakingService.joinQueue(1L);

        Map<String, Object> result = matchmakingService.leaveQueue(1L);
        assertEquals("removed", result.get("status"));
        assertEquals(0, matchmakingService.getQueueSize());
    }

    @Test
    @DisplayName("Leave queue when not queued returns not_queued")
    void leaveQueueWhenNotQueued() {
        Map<String, Object> result = matchmakingService.leaveQueue(1L);
        assertEquals("not_queued", result.get("status"));
    }

    @Test
    @DisplayName("Get status for queued player")
    void getStatusQueued() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(createPlayer(1L, 1000)));
        matchmakingService.joinQueue(1L);

        Map<String, Object> result = matchmakingService.getStatus(1L);
        assertEquals("queued", result.get("status"));
        assertNotNull(result.get("waitSeconds"));
    }

    @Test
    @DisplayName("Get status for non-queued player")
    void getStatusNotQueued() {
        Map<String, Object> result = matchmakingService.getStatus(1L);
        assertEquals("not_queued", result.get("status"));
    }
}
