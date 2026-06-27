package com.aow2.server.controller;

import com.aow2.server.repository.MatchResultRepository;
import com.aow2.server.repository.PlayerRepository;
import com.aow2.server.repository.UploadedMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for public server-statistics endpoints.
 * <p>
 * FIX (H8 from CRITICAL_ANALYSIS_REPORT.md): Previously the web dashboard's Quick
 * Stats panel showed hardcoded numbers (1,247 / 89 / 342 / 56). This controller
 * exposes real counts from the database so the dashboard can fetch live data.
 * <p>
 * All endpoints are public (see {@code SecurityConfig}) so unauthenticated visitors
 * can see server activity on the landing page.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final PlayerRepository playerRepository;
    private final MatchResultRepository matchResultRepository;
    private final UploadedMapRepository uploadedMapRepository;

    /**
     * Constructs the StatsController.
     *
     * @param playerRepository       repository for player counts
     * @param matchResultRepository  repository for match counts
     * @param uploadedMapRepository  repository for uploaded-map counts
     */
    public StatsController(PlayerRepository playerRepository,
                           MatchResultRepository matchResultRepository,
                           UploadedMapRepository uploadedMapRepository) {
        this.playerRepository = playerRepository;
        this.matchResultRepository = matchResultRepository;
        this.uploadedMapRepository = uploadedMapRepository;
    }

    /**
     * Gets aggregate server statistics for the dashboard landing page.
     * <p>
     * Returns the following fields:
     * <ul>
     *   <li>{@code totalPlayers} — all registered players</li>
     *   <li>{@code matchesToday} — matches played in the last 24 hours</li>
     *   <li>{@code matchesThisWeek} — matches played in the last 7 days</li>
     *   <li>{@code totalMatches} — all matches ever recorded</li>
     *   <li>{@code totalMaps} — community-uploaded maps</li>
     *   <li>{@code newPlayersToday} — players who registered in the last 24 hours</li>
     *   <li>{@code serverTime} — current server epoch millis (for client clock-sync debugging)</li>
     * </ul>
     * <p>
     * GET /api/stats
     *
     * @return 200 with a JSON object containing server statistics
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(24, ChronoUnit.HOURS);
        Instant lastWeek = now.minus(7, ChronoUnit.DAYS);

        long totalPlayers = playerRepository.count();
        long matchesToday = matchResultRepository.countByPlayedAtAfter(yesterday);
        long matchesThisWeek = matchResultRepository.countByPlayedAtAfter(lastWeek);
        long totalMatches = matchResultRepository.count();
        long totalMaps;
        try {
            totalMaps = uploadedMapRepository.count();
        } catch (Exception e) {
            // Defensive: the maps table is created by Flyway migration but may not
            // exist in some test profiles. Fail soft to 0 rather than 500-ing the
            // entire stats endpoint.
            log.warn("Failed to query uploaded_maps count: {}", e.getMessage());
            totalMaps = 0;
        }
        long newPlayersToday = playerRepository.countByCreatedAtAfter(yesterday);

        // Use LinkedHashMap to preserve a stable JSON field order for clients.
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPlayers", totalPlayers);
        stats.put("matchesToday", matchesToday);
        stats.put("matchesThisWeek", matchesThisWeek);
        stats.put("totalMatches", totalMatches);
        stats.put("totalMaps", totalMaps);
        stats.put("newPlayersToday", newPlayersToday);
        stats.put("serverTime", now.toEpochMilli());

        log.debug("Stats: {}", stats);
        return ResponseEntity.ok(stats);
    }
}
