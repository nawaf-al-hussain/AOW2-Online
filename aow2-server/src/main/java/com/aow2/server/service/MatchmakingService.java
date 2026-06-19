package com.aow2.server.service;

import com.aow2.server.model.Player;
import com.aow2.server.repository.PlayerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    /** Initial ELO range for matching */
    @Value("${aow2.matchmaking.initial-elo-range:100}")
    private int initialEloRange;

    /** Maximum ELO range expansion per second waited */
    private static final int ELO_RANGE_EXPANSION_PER_SEC = 50;

    /** Maximum ELO range cap */
    @Value("${aow2.matchmaking.max-elo-range:500}")
    private int maxEloRange;

    /** In-memory matchmaking queue: playerId → queue entry */
    private final Map<Long, QueueEntry> queue = new ConcurrentHashMap<>();

    private final PlayerRepository playerRepository;

    /** Callback for notifying matched players via WebSocket. */
    private MatchNotificationCallback notificationCallback;

    /** Background scheduler for periodic matchmaking sweeps */
    private ScheduledExecutorService matchmakingScheduler;

    /**
     * Functional interface for match notification callbacks.
     * Implementations should send WebSocket messages to matched players.
     */
    @FunctionalInterface
    public interface MatchNotificationCallback {
        void notifyMatched(Long player1Id, Long player2Id);
    }

    /**
     * Sets the match notification callback.
     * Called by LobbyWebSocketHandler after construction to wire up WebSocket notifications.
     *
     * @param callback the notification callback
     */
    public void setNotificationCallback(MatchNotificationCallback callback) {
        this.notificationCallback = callback;
    }

    /**
     * Constructs the MatchmakingService.
     *
     * @param playerRepository repository for player lookups
     */
    public MatchmakingService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Starts the background matchmaking thread after dependency injection.
     * Every 3 seconds, scans the queue and tries to match any pending pairs.
     * This handles cases where two players are already waiting and neither
     * triggered a match on join (e.g., ELO ranges expanded enough).
     */
    @PostConstruct
    public void startBackgroundMatchmaking() {
        matchmakingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matchmaking-sweeper");
            t.setDaemon(true);
            return t;
        });
        matchmakingScheduler.scheduleAtFixedRate(
                this::backgroundMatchSweep,
                3, 3, TimeUnit.SECONDS
        );
        log.info("Background matchmaking sweeper started (interval=3s)");
    }

    /**
     * Shuts down the background matchmaking executor on bean destruction.
     */
    @PreDestroy
    public void shutdownBackgroundMatchmaking() {
        if (matchmakingScheduler != null) {
            matchmakingScheduler.shutdownNow();
            log.info("Background matchmaking sweeper shut down");
        }
    }

    /**
     * Background sweep: iterate all queued players and attempt to pair them.
     * This complements the immediate-match logic in {@link #joinQueue(Long)}.
     */
    private void backgroundMatchSweep() {
        if (queue.size() < 2) {
            return;
        }

        List<QueueEntry> entries = new ArrayList<>(queue.values());
        List<Long> matched = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            if (matched.contains(entries.get(i).playerId)) {
                continue;
            }
            for (int j = i + 1; j < entries.size(); j++) {
                if (matched.contains(entries.get(j).playerId)) {
                    continue;
                }
                QueueEntry a = entries.get(i);
                QueueEntry b = entries.get(j);
                long waitA = Instant.now().getEpochSecond() - a.joinedAt.getEpochSecond();
                long waitB = Instant.now().getEpochSecond() - b.joinedAt.getEpochSecond();
                int rangeA = calculateEloRange(waitA);
                int rangeB = calculateEloRange(waitB);
                int range = Math.max(rangeA, rangeB);

                if (Math.abs(a.eloRating - b.eloRating) <= range) {
                    queue.remove(a.playerId);
                    queue.remove(b.playerId);
                    matched.add(a.playerId);
                    matched.add(b.playerId);
                    log.info("Background match found: player {} (ELO:{}) vs player {} (ELO:{})",
                            a.playerId, a.eloRating, b.playerId, b.eloRating);
                    // Notify matched players via WebSocket callback
                    if (notificationCallback != null) {
                        notificationCallback.notifyMatched(a.playerId, b.playerId);
                    }
                    break;
                }
            }
        }
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
                int range = Math.max(initialEloRange, entryRange);
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
     * Range expands by ELO_RANGE_EXPANSION_PER_SEC per second, capped at maxEloRange.
     *
     * @param waitSeconds seconds the player has been in the queue
     * @return the current ELO search range
     */
    private int calculateEloRange(long waitSeconds) {
        return Math.min(
                initialEloRange + (int) (waitSeconds * ELO_RANGE_EXPANSION_PER_SEC),
                maxEloRange
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