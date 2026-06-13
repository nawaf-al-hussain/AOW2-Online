package com.aow2.server.controller;

import com.aow2.server.service.MatchmakingService;
import com.aow2.server.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for matchmaking endpoints.
 * Manages player queue operations for ELO-based matchmaking.
 * REF: protocol_specification.md - Matchmaking state machine (Section 7)
 * REF: session_lifecycle.md - Matchmaking states (aO=55/56)
 */
@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingController.class);

    private final MatchmakingService matchmakingService;
    private final SessionService sessionService;

    /**
     * Constructs the MatchmakingController.
     *
     * @param matchmakingService the matchmaking business logic
     * @param sessionService     the session management service
     */
    public MatchmakingController(MatchmakingService matchmakingService,
                                  SessionService sessionService) {
        this.matchmakingService = matchmakingService;
        this.sessionService = sessionService;
    }

    /**
     * Joins the matchmaking queue.
     * If a suitable opponent is found, a game session is created immediately.
     * POST /api/matchmaking/queue
     *
     * @param authentication the authenticated player
     * @return 200 with match status (queued or match_found)
     */
    @PostMapping("/queue")
    public ResponseEntity<Map<String, Object>> joinQueue(Authentication authentication) {
        Long playerId = (Long) authentication.getPrincipal();
        Map<String, Object> result = matchmakingService.joinQueue(playerId);

        if ("match_found".equals(result.get("status"))) {
            Long player1Id = (Long) result.get("player1Id");
            Long player2Id = (Long) result.get("player2Id");
            var session = sessionService.createSession(player1Id, player2Id, "default");
            result = Map.of(
                    "status", "match_found",
                    "sessionUuid", session.getSessionUuid(),
                    "player1Id", player1Id,
                    "player2Id", player2Id
            );
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Leaves the matchmaking queue.
     * DELETE /api/matchmaking/queue
     *
     * @param authentication the authenticated player
     * @return 200 with removal status
     */
    @DeleteMapping("/queue")
    public ResponseEntity<Map<String, Object>> leaveQueue(Authentication authentication) {
        Long playerId = (Long) authentication.getPrincipal();
        Map<String, Object> result = matchmakingService.leaveQueue(playerId);
        return ResponseEntity.ok(result);
    }

    /**
     * Checks the current matchmaking status.
     * GET /api/matchmaking/status
     *
     * @param authentication the authenticated player
     * @return 200 with queue status details
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(Authentication authentication) {
        Long playerId = (Long) authentication.getPrincipal();
        Map<String, Object> result = matchmakingService.getStatus(playerId);
        return ResponseEntity.ok(result);
    }
}
