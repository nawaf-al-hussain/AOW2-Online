package com.aow2.server.controller;

import com.aow2.server.model.MatchResult;
import com.aow2.server.repository.MatchResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
 * REST controller for replay management endpoints.
 * Handles replay upload and retrieval for match recordings.
 * REF: multiplayer_architecture.md - Data integrity: turn sequence validation
 * REF: protocol_specification.md - Type 30 GAME_STATE (replay data source)
 */
@RestController
@RequestMapping("/api/replays")
public class ReplayController {

    private static final Logger log = LoggerFactory.getLogger(ReplayController.class);

    private final MatchResultRepository matchResultRepository;

    /**
     * Constructs the ReplayController.
     *
     * @param matchResultRepository repository for match result persistence
     */
    public ReplayController(MatchResultRepository matchResultRepository) {
        this.matchResultRepository = matchResultRepository;
    }

    /**
     * Uploads a replay file for a completed match.
     * POST /api/replays
     *
     * @param authentication the authenticated player
     * @param request        must contain "matchId" and "replayData" (base64 encoded)
     * @return 201 on success, or 400 on validation error
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadReplay(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        Long playerId = (Long) authentication.getPrincipal();
        String matchIdStr = request.get("matchId");
        String replayData = request.get("replayData");

        if (matchIdStr == null || replayData == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "matchId and replayData are required"));
        }

        try {
            Long matchId = Long.parseLong(matchIdStr);
            return matchResultRepository.findById(matchId)
                    .map(result -> {
                        if (!result.getPlayer1Id().equals(playerId)
                                && !result.getPlayer2Id().equals(playerId)) {
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .<Map<String, Object>>body(Map.of("error", "Not a participant in this match"));
                        }
                        // ASSUMPTION: replay file stored as path; in production, upload to object storage
                        result.setReplayFilePath("replays/" + matchId + ".aow2rep");
                        matchResultRepository.save(result);
                        log.info("Replay uploaded for match {} by player {}", matchId, playerId);
                        return ResponseEntity.status(HttpStatus.CREATED)
                                .<Map<String, Object>>body(Map.of("matchId", matchId, "status", "uploaded"));
                    })
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .<Map<String, Object>>body(Map.of("error", "Match not found: " + matchId)));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid matchId format"));
        }
    }

    /**
     * Lists recent replay metadata.
     * GET /api/replays
     *
     * @return 200 with a list of replay metadata
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listReplays() {
        List<MatchResult> results = matchResultRepository.findAllByOrderByPlayedAtDesc();
        List<Map<String, Object>> replays = results.stream()
                .filter(r -> r.getReplayFilePath() != null)
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "player1Id", r.getPlayer1Id(),
                        "player2Id", r.getPlayer2Id(),
                        "winnerId", r.getWinnerId() != null ? r.getWinnerId() : 0L,
                        "mapName", r.getMapName() != null ? r.getMapName() : "unknown",
                        "durationSeconds", r.getDurationSeconds() != null ? r.getDurationSeconds() : 0,
                        "playedAt", r.getPlayedAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(replays);
    }

    /**
     * Downloads a specific replay.
     * GET /api/replays/{id}
     *
     * @param id the match result ID
     * @return 200 with replay file path info, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> downloadReplay(@PathVariable Long id) {
        return matchResultRepository.findById(id)
                .filter(r -> r.getReplayFilePath() != null)
                .map(r -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", r.getId(),
                        "replayFilePath", r.getReplayFilePath(),
                        "player1Id", r.getPlayer1Id(),
                        "player2Id", r.getPlayer2Id(),
                        "mapName", r.getMapName() != null ? r.getMapName() : "unknown",
                        "durationSeconds", r.getDurationSeconds() != null ? r.getDurationSeconds() : 0
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Replay not found: " + id)));
    }
}
