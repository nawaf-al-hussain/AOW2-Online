package com.aow2.server.controller;

import com.aow2.server.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Tests for the LeaderboardController REST endpoints.
 * <p>
 * REF: FULL_ANALYSIS.md §3 [CRITICAL] F-02 — /api/leaderboard/me NPE on
 * unauthenticated access. SecurityConfig requires authentication for /me,
 * and the controller defensively null-checks the Authentication parameter.
 *
 * @see LeaderboardController#getMyRanking(Authentication)
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    @Mock
    private RankingService rankingService;

    @Mock
    private Authentication authentication;

    private LeaderboardController leaderboardController;

    @BeforeEach
    void setUp() {
        leaderboardController = new LeaderboardController(rankingService);
    }

    @Test
    @DisplayName("F-02: getMyRanking with null Authentication returns 401, not 500")
    void getMyRankingWithNullAuthenticationReturns401() {
        // Simulate an unauthenticated request reaching the controller
        // (should not happen with SecurityConfig, but the controller must be defensive)
        ResponseEntity<Map<String, Object>> response = leaderboardController.getMyRanking(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "Unauthenticated request must return 401, not 500");
        assertNotNull(response.getBody(), "Response body must contain error message");
        assertEquals("Authentication required", response.getBody().get("error"));
    }

    @Test
    @DisplayName("F-02: getMyRanking with Authentication having null principal returns 401")
    void getMyRankingWithNullPrincipalReturns401() {
        when(authentication.getPrincipal()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = leaderboardController.getMyRanking(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody().get("error"));
    }

    @Test
    @DisplayName("F-02: getMyRanking with valid Authentication returns 200 with ranking")
    void getMyRankingWithValidAuthenticationReturns200() {
        when(authentication.getPrincipal()).thenReturn(42L);
        Map<String, Object> ranking = Map.of("playerId", 42, "rank", 5, "elo", 1500);
        when(rankingService.getPlayerRanking(42L)).thenReturn(ranking);

        ResponseEntity<Map<String, Object>> response = leaderboardController.getMyRanking(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(42, response.getBody().get("playerId"));
        assertEquals(5, response.getBody().get("rank"));
    }

    @Test
    @DisplayName("F-02: getMyRanking with valid Authentication but unknown player returns 404")
    void getMyRankingWithUnknownPlayerReturns404() {
        when(authentication.getPrincipal()).thenReturn(999L);
        when(rankingService.getPlayerRanking(999L))
                .thenThrow(new IllegalArgumentException("Player not found"));

        ResponseEntity<Map<String, Object>> response = leaderboardController.getMyRanking(authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Player not found", response.getBody().get("error"));
    }
}
