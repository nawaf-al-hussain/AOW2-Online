package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business logic for matchmaking: queue management, match creation, ELO-based pairing.
 * Uses an in-memory queue with ELO-range matching to pair players of similar skill.
 * REF: protocol_specification.md - Matchmaking request construction (r=36 quick match, r=38 search)
 * REF: session_lifecycle.md - Matchmaking state machine (aO=55/56)
 * ASSUMPTION: ELO range expands over time if no match is found (widening window)
 */
@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

    /** Initial ELO range for matching (±100) */
    private static final int INITIAL_ELO_RANGE = 100;
    /** Maximum ELO range expansion per second waited (±50/second) */
    private static final int ELO_RANGE_EXPANSION_PER_SEC = 50;
    /** Maximum ELO range cap */
    private static final int MAX_ELO_RANGE = 500;

    /** In-memory matchmaking queue: playerId → queue entry */
    private final Map<Long, QueueEntry> queue = new ConcurrentHashMap<>();

    private final PlayerRepository playerRepository;

    /**
     * Constructs the MatchmakingService.
     *
     * @param playerRepository repository for player lookups
     */
    public MatchmakingService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Adds a player to the matchmaking queue.
     * If a suitable opponent is already in the queue, a match is created immediately.
     *
     * @param playerId the ID of the player joining the queue
     * @return a match result if an opponent was found, or a queue status
     */
    public Map<String, Object> joinQueue(Long playerId) {
        if (queue.containsKey(playerId)) {
            return Map.of("status", "already_queued", "playerId", playerId);
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        // Try to find a match immediately
        Optional<Long> opponentId = findMatch(playerId, player.getEloRating());
        if (opponentId.isPresent()) {
            queue.remove(opponentId.get());
            log.info("Match found: player {} vs player {}", playerId, opponentId.get());
            return Map.of(
                    "status", "match_found",
                    "player1Id", playerId,
                    "player2Id", opponentId.get()
            );
        }

        // No match found, add to queue
        queue.put(playerId, new QueueEntry(playerId, player.getEloRating(), Instant.now()));
        log.info("Player {} joined matchmaking queue (ELO: {})", playerId, player.getEloRating());

        return Map.of("status", "queued", "playerId", playerId, "eloRating", player.getEloRating());
    }

    /**
     * Removes a player from the matchmaking queue.
     *
     * @param playerId the ID of the player leaving the queue
     * @return a status map indicating the result
     */
    public Map<String, Object> leaveQueue(Long playerId) {
        QueueEntry removed = queue.remove(playerId);
        if (removed != null) {
            log.info("Player {} left matchmaking queue", playerId);
            return Map.of("status", "removed", "playerId", playerId);
        }
        return Map.of("status", "not_queued", "playerId", playerId);
    }

    /**
     * Checks the current matchmaking status for a player.
     *
     * @param playerId the ID of the player checking status
     * @return a status map with queue position and wait time
     */
    public Map<String, Object> getStatus(Long playerId) {
        QueueEntry entry = queue.get(playerId);
        if (entry == null) {
            return Map.of("status", "not_queued", "playerId", playerId);
        }

        long waitSeconds = Instant.now().getEpochSecond() - entry.joinedAt.getEpochSecond();
        int currentRange = calculateEloRange(waitSeconds);

        return Map.of(
                "status", "queued",
                "playerId", playerId,
                "eloRating", entry.eloRating,
                "waitSeconds", waitSeconds,
                "currentEloRange", currentRange,
                "queueSize", queue.size()
        );
    }

    /**
     * Finds a suitable opponent for the given player based on ELO rating.
     * The ELO range widens over time to ensure players eventually get matched.
     *
     * @param playerId  the player seeking a match
     * @param eloRating the player's ELO rating
     * @return an Optional containing the opponent's player ID, or empty if no match
     */
    private Optional<Long> findMatch(Long playerId, int eloRating) {
        List<Long> candidates = new ArrayList<>();
        for (QueueEntry entry : queue.values()) {
            if (!entry.playerId.equals(playerId)) {
                long waitSeconds = Instant.now().getEpochSecond() - entry.joinedAt.getEpochSecond();
                int entryRange = calculateEloRange(waitSeconds);
                int range = Math.max(INITIAL_ELO_RANGE, entryRange);
                if (Math.abs(entry.eloRating - eloRating) <= range) {
                    candidates.add(entry.playerId);
                }
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Pick the closest ELO match
        candidates.sort((a, b) -> {
            int eloA = queue.get(a).eloRating;
            int eloB = queue.get(b).eloRating;
            return Integer.compare(
                    Math.abs(eloA - eloRating),
                    Math.abs(eloB - eloRating)
            );
        });

        return Optional.of(candidates.getFirst());
    }

    /**
     * Calculates the ELO search range based on time spent in the queue.
     * Range expands by ELO_RANGE_EXPANSION_PER_SEC per second, capped at MAX_ELO_RANGE.
     *
     * @param waitSeconds seconds the player has been in the queue
     * @return the current ELO search range
     */
    private int calculateEloRange(long waitSeconds) {
        return Math.min(
                INITIAL_ELO_RANGE + (int) (waitSeconds * ELO_RANGE_EXPANSION_PER_SEC),
                MAX_ELO_RANGE
        );
    }

    /**
     * Returns the current number of players in the matchmaking queue.
     *
     * @return queue size
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Record representing a player's entry in the matchmaking queue.
     *
     * @param playerId   the player's ID
     * @param eloRating  the player's ELO rating at queue time
     * @param joinedAt   the time the player joined the queue
     */
    private record QueueEntry(Long playerId, int eloRating, Instant joinedAt) {}
}
