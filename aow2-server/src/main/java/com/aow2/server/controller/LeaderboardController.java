package com.aow2.server.controller;

import com.aow2.server.service.RankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for leaderboard endpoints.
 * Provides ELO-ranked player listings and individual ranking queries.
 * REF: protocol_specification.md - Type 70 RANK_DATA
 * REF: multiplayer_architecture.md - ELO-based ranking for competitive play
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardController.class);

    private final RankingService rankingService;

    /**
     * Constructs the LeaderboardController.
     *
     * @param rankingService the ranking business logic
     */
    public LeaderboardController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * Gets the top players on the leaderboard.
     * GET /api/leaderboard?limit=50
     *
     * @param limit maximum number of entries to return (default 50, max 100)
     * @return 200 with a list of ranked player entries
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<Map<String, Object>> leaderboard = rankingService.getLeaderboard(limit);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Gets the authenticated player's own ranking.
     * GET /api/leaderboard/me
     *
     * @param authentication the authenticated player
     * @return 200 with the player's ranking data, or 404 if not found
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyRanking(Authentication authentication) {
        Long playerId = (Long) authentication.getPrincipal();
        try {
            Map<String, Object> ranking = rankingService.getPlayerRanking(playerId);
            return ResponseEntity.ok(ranking);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
