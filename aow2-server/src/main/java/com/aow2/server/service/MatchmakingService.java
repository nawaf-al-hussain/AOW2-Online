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

    /** Lock for atomic matchmaking queue operations (find+remove). */
    private final Object queueLock = new Object();

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
        void notifyMatched(Long player1Id, Long player2Id, String mapName);
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
     * This complements the immediate-match logic in {@link #joinQueue(Long, List)}.
     * Prefers pairing players with overlapping map preferences.
     */
    private void backgroundMatchSweep() {
        if (queue.size() < 2) {
            return;
        }

        synchronized (queueLock) {
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
                    String selectedMap = selectMatchMap(a.playerId, b.playerId);
                    queue.remove(a.playerId);
                    queue.remove(b.playerId);
                    matched.add(a.playerId);
                    matched.add(b.playerId);
                    log.info("Background match found: player {} (ELO:{}) vs player {} (ELO:{}) on map '{}'",
                            a.playerId, a.eloRating, b.playerId, b.eloRating, selectedMap);
                    // Notify matched players via WebSocket callback
                    if (notificationCallback != null) {
                        notificationCallback.notifyMatched(a.playerId, b.playerId, selectedMap);
                    }
                    break;
                }
            }
        }
        } // end synchronized(queueLock)
    }

    /**
     * Adds a player to the matchmaking queue with no map preference.
     * If a suitable opponent is already in the queue, a match is created immediately.
     *
     * @param playerId the ID of the player joining the queue
     * @return a match result if an opponent was found, or a queue status
     */
    public Map<String, Object> joinQueue(Long playerId) {
        return joinQueue(playerId, null);
    }

    /**
     * Adds a player to the matchmaking queue with optional map preferences.
     * If a suitable opponent is already in the queue, a match is created immediately.
     * Players with overlapping map preferences are prioritized for matching.
     *
     * @param playerId       the ID of the player joining the queue
     * @param preferredMaps  optional list of preferred map names (null/empty for any map)
     * @return a match result if an opponent was found, or a queue status
     */
    public Map<String, Object> joinQueue(Long playerId, List<String> preferredMaps) {
        synchronized (queueLock) {
        if (queue.containsKey(playerId)) {
            return Map.of("status", "already_queued", "playerId", playerId);
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        List<String> maps = (preferredMaps != null && !preferredMaps.isEmpty())
                ? List.copyOf(preferredMaps)
                : List.of();

        log.info("Player {} joining queue with map preferences: {}",
                playerId, maps.isEmpty() ? "<any map>" : maps);

        // Try to find a match immediately
        Optional<Long> opponentId = findMatch(playerId, player.getEloRating(), maps);
        if (opponentId.isPresent()) {
            Long opponent = opponentId.get();
            queue.remove(opponent);
            String selectedMap = selectMatchMap(playerId, opponent);
            log.info("Match found: player {} vs player {} on map '{}'", playerId, opponent, selectedMap);
            return Map.of(
                    "status", "match_found",
                    "player1Id", playerId,
                    "player2Id", opponent,
                    "mapName", selectedMap
            );
        }

        // No match found, add to queue
        queue.put(playerId, new QueueEntry(playerId, player.getEloRating(), Instant.now(), maps, false));
        log.info("Player {} joined matchmaking queue (ELO: {}, preferredMaps: {})",
                playerId, player.getEloRating(), maps);

        return Map.of("status", "queued", "playerId", playerId, "eloRating", player.getEloRating());
        } // end synchronized(queueLock)
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
     * Finds a suitable opponent for the given player based on ELO rating and map preferences.
     * Prefers opponents with overlapping map preferences; falls back to pure ELO matching.
     * The ELO range widens over time to ensure players eventually get matched.
     *
     * @param playerId       the player seeking a match
     * @param eloRating      the player's ELO rating
     * @param preferredMaps  the player's preferred map names (empty list = no preference)
     * @return an Optional containing the opponent's player ID, or empty if no match
     */
    private Optional<Long> findMatch(Long playerId, int eloRating, List<String> preferredMaps) {
        List<Long> eloCandidates = new ArrayList<>();
        List<Long> mapOverlapCandidates = new ArrayList<>();

        for (QueueEntry entry : queue.values()) {
            if (!entry.playerId.equals(playerId)) {
                long waitSeconds = Instant.now().getEpochSecond() - entry.joinedAt.getEpochSecond();
                int entryRange = calculateEloRange(waitSeconds);
                int range = Math.max(initialEloRange, entryRange);
                if (Math.abs(entry.eloRating - eloRating) <= range) {
                    eloCandidates.add(entry.playerId);
                    // Check for overlapping map preferences
                    if (!preferredMaps.isEmpty() && !entry.preferredMaps().isEmpty()) {
                        List<String> intersection = preferredMaps.stream()
                                .filter(entry.preferredMaps()::contains)
                                .toList();
                        if (!intersection.isEmpty()) {
                            mapOverlapCandidates.add(entry.playerId);
                        }
                    }
                }
            }
        }

        // Prefer candidates with overlapping map preferences
        List<Long> candidates = !mapOverlapCandidates.isEmpty() ? mapOverlapCandidates : eloCandidates;

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        log.debug("Match candidates for player {}: {} ELO-eligible, {} with map overlap",
                playerId, eloCandidates.size(), mapOverlapCandidates.size());

        // Pick the closest ELO match from preferred candidate list
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
     * Selects a map for a match between two players.
     * Finds the intersection of both players' preferred maps and picks randomly from it.
     * If either player has no preference (empty list), falls back to "default".
     *
     * @param player1Id the first player's ID
     * @param player2Id the second player's ID
     * @return the selected map name
     */
    public String selectMatchMap(Long player1Id, Long player2Id) {
        QueueEntry entry1 = queue.get(player1Id);
        QueueEntry entry2 = queue.get(player2Id);

        List<String> maps1 = (entry1 != null) ? entry1.preferredMaps() : List.of();
        List<String> maps2 = (entry2 != null) ? entry2.preferredMaps() : List.of();

        // If either player has no preference, use "test_map" (the only bundled map)
        if (maps1.isEmpty() || maps2.isEmpty()) {
            String fallback = "test_map";
            log.info("Map selection: player {} maps={}, player {} maps={} → using '{}' (no preference)",
                    player1Id, maps1, player2Id, maps2, fallback);
            return fallback;
        }

        // Find intersection of preferred maps
        List<String> intersection = maps1.stream()
                .filter(maps2::contains)
                .toList();

        if (intersection.isEmpty()) {
            String fallback = "test_map";
            log.info("Map selection: no overlap between player {} maps={} and player {} maps={} → using '{}' (no overlap)",
                    player1Id, maps1, player2Id, maps2, fallback);
            return fallback;
        }

        // Pick a map from the intersection.
        // FIX (M7 from CRITICAL_ANALYSIS_REPORT.md): Previously used
        // ThreadLocalRandom.current().nextInt(intersection.size()), which made the
        // map selection non-deterministic. While this is server-side only (doesn't
        // affect lockstep determinism), reproducibility matters for debugging and
        // for tournament audits. Now seeded from the two player IDs so the same
        // pair of players always gets the same map for the same intersection set.
        int seed = Math.floorMod(player1Id + player2Id, intersection.size());
        String selected = intersection.get(seed);
        log.info("Map selection: overlap {} → picked '{}' (seed={} from playerIds {}+{})",
            intersection, selected, seed, player1Id, player2Id);
        return selected;
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
     * @param playerId       the player's ID
     * @param eloRating      the player's ELO rating at queue time
     * @param joinedAt       the time the player joined the queue
     * @param preferredMaps  list of map names the player wants to play (empty = any map)
     * @param mapVetoPhase   true when the player is in the map veto phase
     */
    private record QueueEntry(Long playerId, int eloRating, Instant joinedAt,
                               List<String> preferredMaps, boolean mapVetoPhase) {}
}